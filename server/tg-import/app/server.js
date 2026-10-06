/*
 * Импорт стикер-паков из Telegram для Larpgram.
 *
 * Что делает: по короткому имени пака отдаёт его состав и сами картинки. Заливкой в
 * Matrix занимается приложение, поэтому здесь нет ни одного матричного токена: сервису
 * нечего охранять, кроме токена бота.
 *
 * Почему через прокси: с сервера Telegram недоступен ни по Bot API, ни по MTProto, всё
 * уходит в таймаут. Рядом уже работает sing-box, через него и ходим.
 *
 * Анимированные (.tgs, упакованный Lottie) и видео-стикеры (.webm) конвертируются в
 * анимированный WebP: Coil в Element X умеет его из коробки, поэтому клиенту всё равно,
 * что стикер был анимированным. Конвертация живёт в convert.py, результат кэшируется на
 * диске — второй импорт того же пака кем угодно уже бесплатный.
 */
const { spawn } = require('child_process')
const crypto = require('crypto')
const fs = require('fs')
const fsp = require('fs/promises')
const http = require('http')
const os = require('os')
const path = require('path')
const { ProxyAgent, setGlobalDispatcher } = require('undici')

const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN
const PORT = Number(process.env.PORT || 3000)
const PROXY_URL = process.env.TELEGRAM_PROXY_URL || 'http://172.17.0.1:7891'
const CACHE_DIR = process.env.CACHE_DIR || '/cache'
const CACHE_LIMIT_MB = Number(process.env.CACHE_LIMIT_MB || 4096)
const TG_API = 'https://api.telegram.org'
const UPSTREAM_TIMEOUT_MS = 20000
// Конвертация одного стикера занимает около полсекунды и целиком упирается в ядро.
// Ограничиваем, чтобы импорт большого пака не выел процессор у остальных сервисов.
const MAX_PARALLEL_CONVERSIONS = Number(process.env.MAX_PARALLEL_CONVERSIONS || 4)
// Меняется вместе с параметрами конвертации в convert.py: старые файлы в кэше тогда
// перестают находиться и пересчитываются, вручную чистить ничего не надо.
const CONV_VERSION = 'v2'
// Сторона, в которую вписывается сконвертированный стикер. Должна совпадать со
// STICKER_MAX_SIDE в convert.py, поэтому берётся из той же переменной окружения.
const MAX_SIDE = Number(process.env.STICKER_MAX_SIDE || 384)
// Имя пака в Telegram: латиница, цифры и подчёркивания. Проверяем сами, чтобы не
// подставлять в URL что попало.
const PACK_NAME_RE = /^[A-Za-z0-9_]{1,64}$/
// file_unique_id приходит от клиента и становится частью имени файла в кэше, поэтому
// проверяется строго: иначе получили бы обход каталога.
const UNIQUE_ID_RE = /^[A-Za-z0-9_-]{1,64}$/
// Форматы, которые Matrix-клиенты не покажут и которые поэтому конвертируем.
const CONVERTIBLE = new Set(['.tgs', '.webm'])

if (!BOT_TOKEN) {
  console.error('TELEGRAM_BOT_TOKEN не задан, импорт работать не будет')
}

// Весь исходящий трафик этого сервиса идёт через прокси: напрямую Telegram недоступен.
setGlobalDispatcher(new ProxyAgent(PROXY_URL))

async function callTelegram(method, params) {
  const url = new URL(`${TG_API}/bot${BOT_TOKEN}/${method}`)
  for (const [k, v] of Object.entries(params || {})) {
    if (v !== null && v !== undefined) url.searchParams.set(k, v)
  }
  const response = await fetch(url, { signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS) })
  const body = await response.json().catch(() => ({}))
  return { status: response.status, body }
}

/** Тип картинки по расширению файла из Telegram. */
function mimeFromPath(filePath) {
  const ext = (filePath || '').split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'png':
      return 'image/png'
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg'
    case 'gif':
      return 'image/gif'
    default:
      // Статичные стикеры Telegram это webp, как и всё, что мы сконвертировали.
      return 'image/webp'
  }
}

/**
 * Размер после конвертации: вписываем в квадрат, не растягивая.
 * Повторяет fit() из convert.py — если правится одно, правится и второе.
 */
function fitSize(width, height) {
  if (!width || !height || width <= 0 || height <= 0) return { width: MAX_SIDE, height: MAX_SIDE }
  const scale = Math.min(MAX_SIDE / width, MAX_SIDE / height, 1)
  return {
    width: Math.max(2, Math.round((width * scale) / 2) * 2),
    height: Math.max(2, Math.round((height * scale) / 2) * 2),
  }
}

function sendJson(res, status, payload) {
  const data = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data),
    'Cache-Control': status === 200 ? 'public, max-age=600' : 'no-store',
  })
  res.end(data)
}

/**
 * Превращает ответ getStickerSet в наш формат.
 *
 * Анимированные и видео-стикеры больше не выбрасываются: на выдаче они выглядят как
 * обычные webp, потому что /file отдаёт их уже сконвертированными.
 */
function normalizePack(set) {
  const stickers = (set.stickers || []).map((s) => {
    const animated = Boolean(s.is_animated || s.is_video)
    // У сконвертированных меняется размер, а вес заранее неизвестен: пусть клиент
    // возьмёт его из самого файла.
    const size = animated ? fitSize(s.width, s.height) : { width: s.width || null, height: s.height || null }
    return {
      fileId: s.file_id,
      // Уникален внутри пака и не меняется, годится и для shortcode, и как ключ кэша.
      fileUniqueId: s.file_unique_id,
      emoji: s.emoji || '',
      mimeType: 'image/webp',
      // Подсказка клиенту, что файл поедет через конвертацию и будет тяжелее.
      animated,
      width: size.width,
      height: size.height,
      size: animated ? null : s.file_size || null,
    }
  })

  return {
    slug: `tg-${set.name}`,
    name: set.name,
    title: set.title || set.name,
    total: (set.stickers || []).length,
    stickers,
  }
}

// --- Кэш сконвертированных стикеров -----------------------------------------------

/** Уже идущие конвертации: два запроса на один стикер не должны считать его дважды. */
const inFlight = new Map()
let running = 0
const waiting = []

function acquireSlot() {
  if (running < MAX_PARALLEL_CONVERSIONS) {
    running += 1
    return Promise.resolve()
  }
  return new Promise((resolve) => waiting.push(resolve))
}

function releaseSlot() {
  const next = waiting.shift()
  if (next) next()
  else running -= 1
}

function cachePath(uniqueId) {
  return path.join(CACHE_DIR, `${CONV_VERSION}-${uniqueId}.webp`)
}

async function cachedFile(uniqueId) {
  if (!UNIQUE_ID_RE.test(uniqueId || '')) return null
  const file = cachePath(uniqueId)
  try {
    const stat = await fsp.stat(file)
    return stat.size > 0 ? file : null
  } catch {
    return null
  }
}

/** Скачивает файл из Telegram во временный файл и отдаёт путь. */
async function downloadToTmp(filePath) {
  const ext = path.extname(filePath).toLowerCase()
  const tmp = path.join(CACHE_DIR, 'tmp', `${crypto.randomUUID()}${ext}`)
  const response = await fetch(`${TG_API}/file/bot${BOT_TOKEN}/${filePath}`, {
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  })
  if (!response.ok) throw new Error(`Telegram отдал ${response.status}`)
  await fsp.writeFile(tmp, Buffer.from(await response.arrayBuffer()))
  return tmp
}

function runConverter(src, dst) {
  return new Promise((resolve, reject) => {
    const proc = spawn('python3', [path.join(__dirname, 'convert.py'), src, dst], {
      stdio: ['ignore', 'ignore', 'pipe'],
    })
    let err = ''
    proc.stderr.on('data', (chunk) => {
      err += chunk
    })
    // Конвертация быстрая; если застряла — значит что-то не так с самим файлом.
    const timer = setTimeout(() => proc.kill('SIGKILL'), 60000)
    proc.on('close', (code) => {
      clearTimeout(timer)
      if (code === 0) resolve()
      else reject(new Error(err.trim().slice(0, 300) || `convert.py вышел с кодом ${code}`))
    })
    proc.on('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
  })
}

/**
 * Гарантирует, что сконвертированный стикер лежит в кэше, и отдаёт путь к нему.
 * Повторные вызовы на тот же стикер ждут первую конвертацию, а не запускают свою.
 */
async function ensureConverted(uniqueId, filePath) {
  const ready = await cachedFile(uniqueId)
  if (ready) return ready

  const existing = inFlight.get(uniqueId)
  if (existing) return existing

  const job = (async () => {
    await acquireSlot()
    let tmp = null
    // Пишем рядом и переименовываем: недосчитанный файл не должен попасть в кэш как
    // готовый, если процесс умрёт на середине.
    const partial = path.join(CACHE_DIR, 'tmp', `${crypto.randomUUID()}.webp`)
    const target = cachePath(uniqueId)
    try {
      tmp = await downloadToTmp(filePath)
      await runConverter(tmp, partial)
      await fsp.rename(partial, target)
      return target
    } finally {
      releaseSlot()
      if (tmp) await fsp.rm(tmp, { force: true }).catch(() => {})
      await fsp.rm(partial, { force: true }).catch(() => {})
    }
  })()

  inFlight.set(uniqueId, job)
  try {
    return await job
  } finally {
    inFlight.delete(uniqueId)
  }
}

/** Отдаёт файл с диска потоком. */
async function sendFile(res, file) {
  const stat = await fsp.stat(file)
  res.writeHead(200, {
    'Content-Type': 'image/webp',
    'Content-Length': stat.size,
    // Стикер по этому id не меняется.
    'Cache-Control': 'public, max-age=604800, immutable',
  })
  await new Promise((resolve, reject) => {
    const stream = fs.createReadStream(file)
    stream.on('error', reject)
    stream.on('end', resolve)
    stream.pipe(res)
  })
}

/**
 * Заранее конвертирует анимированные стикеры пака.
 *
 * Приложение скачивает и заливает стикеры по одному, последовательно, поэтому без
 * прогрева каждый /file ждал бы свою конвертацию. Запускаем в фоне и ответ не держим:
 * к тому моменту, как клиент дойдёт до середины пака, остальное уже посчитано.
 */
function prewarm(pack) {
  for (const sticker of pack.stickers) {
    if (!sticker.animated) continue
    cachedFile(sticker.fileUniqueId).then((ready) => {
      if (ready) return
      callTelegram('getFile', { file_id: sticker.fileId })
        .then((meta) => {
          const filePath = meta.body?.result?.file_path
          const uid = meta.body?.result?.file_unique_id || sticker.fileUniqueId
          if (!filePath || !UNIQUE_ID_RE.test(uid)) return
          return ensureConverted(uid, filePath)
        })
        .catch((error) => console.error('прогрев не удался:', error.message))
    })
  }
}

/**
 * Удаляет самое давнее, если кэш перерос лимит. Файлы одноразово вычисляемые, так что
 * потеря безобидна: следующий импорт посчитает заново.
 */
async function sweepCache() {
  try {
    const names = await fsp.readdir(CACHE_DIR)
    const files = []
    let total = 0
    for (const name of names) {
      if (!name.endsWith('.webp')) continue
      const file = path.join(CACHE_DIR, name)
      const stat = await fsp.stat(file).catch(() => null)
      if (!stat?.isFile()) continue
      files.push({ file, size: stat.size, atime: stat.atimeMs })
      total += stat.size
    }
    const limit = CACHE_LIMIT_MB * 1024 * 1024
    if (total <= limit) return
    files.sort((a, b) => a.atime - b.atime)
    let freed = 0
    for (const entry of files) {
      if (total - freed <= limit) break
      await fsp.rm(entry.file, { force: true }).catch(() => {})
      freed += entry.size
    }
    console.log(`кэш подчищен: освобождено ${(freed / 1048576).toFixed(1)} МБ`)
  } catch (error) {
    console.error('не удалось подчистить кэш:', error.message)
  }
}

// --- HTTP --------------------------------------------------------------------------

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')

  if (url.pathname === '/health') {
    return sendJson(res, 200, { ok: true, hasToken: Boolean(BOT_TOKEN), converting: running })
  }
  if (!BOT_TOKEN) {
    return sendJson(res, 503, { error: 'no_bot_token' })
  }

  // Состав пака по его короткому имени.
  if (url.pathname === '/pack') {
    const name = (url.searchParams.get('name') || '').trim()
    if (!PACK_NAME_RE.test(name)) {
      return sendJson(res, 400, { error: 'bad_pack_name' })
    }
    try {
      const result = await callTelegram('getStickerSet', { name })
      if (!result.body?.ok) {
        const description = result.body?.description || ''
        // Telegram не различает «нет такого пака» и «бот его не видит» кодом ответа.
        const notFound = /not found|invalid/i.test(description)
        return sendJson(res, notFound ? 404 : 502, {
          error: notFound ? 'pack_not_found' : 'upstream_error',
        })
      }
      const pack = normalizePack(result.body.result)
      if (pack.stickers.length === 0) {
        return sendJson(res, 422, { error: 'empty_pack', total: pack.total })
      }
      prewarm(pack)
      return sendJson(res, 200, pack)
    } catch (error) {
      console.error('getStickerSet не удался:', error.message)
      return sendJson(res, 502, { error: 'upstream_unreachable' })
    }
  }

  // Сам файл стикера. Токен бота наружу не выносим: клиент знает только file_id.
  if (url.pathname === '/file') {
    const fileId = (url.searchParams.get('id') || '').trim()
    if (!fileId || fileId.length > 200) {
      return sendJson(res, 400, { error: 'bad_file_id' })
    }
    try {
      // Если клиент подсказал file_unique_id и стикер уже сконвертирован, до Telegram
      // ходить не надо вообще: повторный импорт того же пака упирается только в диск.
      const hinted = await cachedFile(url.searchParams.get('uid'))
      if (hinted) return await sendFile(res, hinted)

      const meta = await callTelegram('getFile', { file_id: fileId })
      if (!meta.body?.ok || !meta.body.result?.file_path) {
        return sendJson(res, 404, { error: 'file_not_found' })
      }
      const filePath = meta.body.result.file_path
      const uniqueId = meta.body.result.file_unique_id

      if (CONVERTIBLE.has(path.extname(filePath).toLowerCase())) {
        if (!UNIQUE_ID_RE.test(uniqueId || '')) {
          return sendJson(res, 502, { error: 'bad_unique_id' })
        }
        const converted = await ensureConverted(uniqueId, filePath)
        return await sendFile(res, converted)
      }

      // Статичный стикер отдаём как есть, без лишнего круга через диск.
      const fileUrl = `${TG_API}/file/bot${BOT_TOKEN}/${filePath}`
      const upstream = await fetch(fileUrl, { signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS) })
      if (!upstream.ok || !upstream.body) {
        return sendJson(res, 502, { error: 'upstream_error', status: upstream.status })
      }
      // Telegram отдаёт стикеры как application/octet-stream. Matrix по такому типу
      // покажет безымянный файл вместо картинки, поэтому определяем сами по расширению.
      const upstreamType = upstream.headers.get('content-type')
      const contentType =
        upstreamType && upstreamType !== 'application/octet-stream'
          ? upstreamType
          : mimeFromPath(filePath)
      res.writeHead(200, {
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=604800, immutable',
      })
      const reader = upstream.body.getReader()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        res.write(Buffer.from(value))
      }
      return res.end()
    } catch (error) {
      console.error('не удалось отдать файл:', error.message)
      if (!res.headersSent) return sendJson(res, 502, { error: 'file_unreachable' })
      return res.end()
    }
  }

  return sendJson(res, 404, { error: 'not_found' })
})

async function main() {
  await fsp.mkdir(path.join(CACHE_DIR, 'tmp'), { recursive: true })
  // Обрывки прошлого запуска: недосчитанные файлы никому не нужны.
  await fsp.rm(path.join(CACHE_DIR, 'tmp'), { recursive: true, force: true }).catch(() => {})
  await fsp.mkdir(path.join(CACHE_DIR, 'tmp'), { recursive: true })
  await sweepCache()
  setInterval(sweepCache, 3600_000).unref()

  server.listen(PORT, () => {
    console.log(
      `tg-import слушает :${PORT}, токен ${BOT_TOKEN ? 'есть' : 'ОТСУТСТВУЕТ'}, ` +
        `прокси ${PROXY_URL}, кэш ${CACHE_DIR} (лимит ${CACHE_LIMIT_MB} МБ), ` +
        `конвертаций разом ${MAX_PARALLEL_CONVERSIONS} из ${os.cpus().length} ядер`
    )
  })
}

main()
