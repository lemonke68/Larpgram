/*
 * Прокси к Giphy для Larpgram.
 *
 * Зачем нужен: ключ нельзя класть в APK. Приложение раздаётся файлом, ключ из него
 * вытащит любой желающий, и квоту сожгут за нас. Поэтому ключ живёт только здесь.
 *
 * Наружу отдаём свой формат, а не формат Giphy. Благодаря этому смена провайдера правится
 * здесь и не требует обновления приложения у всех пользователей. Проверено на практике:
 * начинали с Tenor, но Google закрыл его публичный API 30 июня 2026, и переезд стоил
 * правки одного этого файла.
 */
const http = require('http')

const GIPHY_KEY = process.env.GIPHY_API_KEY
const PORT = Number(process.env.PORT || 3000)
const GIPHY_BASE = 'https://api.giphy.com/v1/gifs'
const DEFAULT_LIMIT = 30
const MAX_LIMIT = 50
const UPSTREAM_TIMEOUT_MS = 10000
// Giphy отдаёт и совсем взрослый контент, ограничиваем на своей стороне.
const RATING = 'pg-13'
// Публичный адрес самого прокси: через него отдаются и картинки.
const PUBLIC_BASE = (process.env.PUBLIC_BASE_URL || 'https://gifs.mango-kokos.ru').replace(/\/$/, '')
// Через /media разрешено отдавать только эти хосты. Без белого списка прокси стал бы
// открытым ретранслятором для любого трафика, и им бы воспользовались.
const ALLOWED_MEDIA_HOSTS = /(^|\.)giphy\.com$/

if (!GIPHY_KEY) {
  console.error('GIPHY_API_KEY не задан, поиск работать не будет')
}

/**
 * Приводим ответ Giphy к нашему формату.
 *
 * Берём два варианта файла: компактный для сетки и полный для отправки в чат.
 * `fixed_width_small` весит сотни килобайт вместо мегабайт, для превью этого достаточно.
 */
/**
 * Заворачивает ссылку на картинку в наш прокси.
 *
 * Напрямую CDN Giphy у пользователей в России не открывается: соединение обрывается на
 * установке TLS. Сервер его видит, поэтому картинки отдаём через себя.
 */
function proxyMediaUrl(originalUrl) {
  return `${PUBLIC_BASE}/media?url=${encodeURIComponent(originalUrl)}`
}

function normalize(giphyJson, offset, limit) {
  const data = Array.isArray(giphyJson.data) ? giphyJson.data : []
  const total = giphyJson.pagination?.total_count ?? 0
  const nextOffset = offset + data.length

  return {
    // Курсор наружу отдаём строкой: приложению всё равно, что внутри, оно вернёт его как есть.
    next: nextOffset < total && data.length === limit ? String(nextOffset) : null,
    results: data
      .map((item) => {
        const images = item.images || {}
        // Оригиналы у Giphy бывают по 5-6 МБ. Столько же ляжет в наше хранилище с каждой
        // отправки, а на мобильном интернете отправка будет мучительной. `downsized`
        // ограничен парой мегабайт и на глаз не отличается.
        const full = images.downsized || images.downsized_medium || images.original
        const preview = images.fixed_width_small || images.preview_gif || images.fixed_width || full
        if (!full?.url || !preview?.url) return null
        return {
          id: item.id,
          description: item.title || item.alt_text || '',
          url: proxyMediaUrl(full.url),
          // Giphy отдаёт размеры строками, приложению нужны числа.
          size: Number(full.size) || null,
          width: Number(full.width) || null,
          height: Number(full.height) || null,
          previewUrl: proxyMediaUrl(preview.url),
        }
      })
      .filter(Boolean),
  }
}

async function callGiphy(path, params) {
  const url = new URL(GIPHY_BASE + path)
  url.searchParams.set('api_key', GIPHY_KEY)
  for (const [k, v] of Object.entries(params)) {
    if (v !== null && v !== undefined && v !== '') url.searchParams.set(k, v)
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS)
  try {
    const response = await fetch(url, { signal: controller.signal })
    const body = await response.json().catch(() => ({}))
    return { status: response.status, body }
  } finally {
    clearTimeout(timer)
  }
}

/**
 * Кэш ответов в памяти.
 *
 * Бесплатный ключ Giphy это около сотни запросов в час НА ВСЕХ пользователей, поэтому
 * повторные одинаковые запросы (та же подборка, тот же популярный поисковый запрос)
 * нельзя пускать наружу. Заголовок Cache-Control для этого не годится: он лишь просит
 * клиента, а квоту бережёт только наша сторона.
 */
const cache = new Map()
const CACHE_TTL_MS = 5 * 60 * 1000
const CACHE_MAX_ENTRIES = 500

function cacheGet(key) {
  const hit = cache.get(key)
  if (!hit) return null
  if (Date.now() - hit.at > CACHE_TTL_MS) {
    cache.delete(key)
    return null
  }
  return hit.value
}

function cacheSet(key, value) {
  // Без ограничения размера редкие запросы копились бы вечно.
  if (cache.size >= CACHE_MAX_ENTRIES) {
    const oldest = cache.keys().next().value
    cache.delete(oldest)
  }
  cache.set(key, { at: Date.now(), value })
}

function sendJson(res, status, payload) {
  const data = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data),
    // Гифки не портятся, а квота Giphy на бесплатном ключе очень скромная.
    'Cache-Control': status === 200 ? 'public, max-age=300' : 'no-store',
  })
  res.end(data)
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')

  if (url.pathname === '/health') {
    return sendJson(res, 200, { ok: true, hasKey: Boolean(GIPHY_KEY) })
  }

  if (url.pathname === '/media') {
    const target = url.searchParams.get('url')
    if (!target) {
      return sendJson(res, 400, { error: 'missing_url' })
    }
    let parsed
    try {
      parsed = new URL(target)
    } catch {
      return sendJson(res, 400, { error: 'bad_url' })
    }
    if (parsed.protocol !== 'https:' || !ALLOWED_MEDIA_HOSTS.test(parsed.hostname)) {
      return sendJson(res, 403, { error: 'host_not_allowed' })
    }

    try {
      const upstream = await fetch(parsed, { signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS) })
      if (!upstream.ok || !upstream.body) {
        return sendJson(res, 502, { error: 'upstream_error', status: upstream.status })
      }
      res.writeHead(200, {
        'Content-Type': upstream.headers.get('content-type') || 'image/gif',
        // Картинка по этой ссылке никогда не поменяется, поэтому кешируем надолго:
        // и клиентом, и любым промежуточным кешем.
        'Cache-Control': 'public, max-age=604800, immutable',
      })
      // Стримим, а не собираем в память: гифки бывают по паре мегабайт, а запросов много.
      const reader = upstream.body.getReader()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        res.write(Buffer.from(value))
      }
      return res.end()
    } catch (error) {
      console.error('не удалось отдать медиа:', error.message)
      if (!res.headersSent) {
        return sendJson(res, 502, { error: 'media_unreachable' })
      }
      return res.end()
    }
  }

  const isSearch = url.pathname === '/search'
  const isFeatured = url.pathname === '/featured'
  if (!isSearch && !isFeatured) {
    return sendJson(res, 404, { error: 'not_found' })
  }
  if (!GIPHY_KEY) {
    return sendJson(res, 503, { error: 'no_api_key' })
  }

  // Осторожно с Number(): Number(null) это 0, а не NaN. Из-за этого отсутствующий
  // параметр превращался в limit=1, и на любой запрос приходила ровно одна гифка.
  const limitParam = url.searchParams.get('limit')
  const limitRaw = limitParam === null ? NaN : Number(limitParam)
  const limit = Number.isFinite(limitRaw) && limitRaw > 0
    ? Math.min(Math.max(Math.floor(limitRaw), 1), MAX_LIMIT)
    : DEFAULT_LIMIT
  // Курсор приходит от нас же, но пришёл он через приложение, поэтому не доверяем.
  const posParam = url.searchParams.get('pos')
  const offsetRaw = posParam === null ? NaN : Number(posParam)
  const offset = Number.isFinite(offsetRaw) && offsetRaw > 0 ? Math.floor(offsetRaw) : 0

  const params = { limit, offset, rating: RATING, lang: url.searchParams.get('lang') || 'ru' }

  const cacheKey = `${url.pathname}?${new URLSearchParams({ ...params, q: url.searchParams.get('q') || '' })}`
  const cached = cacheGet(cacheKey)
  if (cached) {
    return sendJson(res, 200, cached)
  }

  try {
    let result
    if (isSearch) {
      const q = (url.searchParams.get('q') || '').trim()
      if (!q) return sendJson(res, 400, { error: 'empty_query' })
      result = await callGiphy('/search', { ...params, q })
    } else {
      result = await callGiphy('/trending', params)
    }

    if (result.status !== 200) {
      console.error('giphy ответил', result.status, JSON.stringify(result.body).slice(0, 200))
      // Наружу не выносим детали ответа: там может быть эхо ключа.
      return sendJson(res, 502, { error: 'upstream_error', status: result.status })
    }
    const normalized = normalize(result.body, offset, limit)
    cacheSet(cacheKey, normalized)
    return sendJson(res, 200, normalized)
  } catch (error) {
    const isTimeout = error.name === 'AbortError'
    console.error('запрос к giphy не удался:', error.message)
    return sendJson(res, isTimeout ? 504 : 502, { error: isTimeout ? 'upstream_timeout' : 'upstream_unreachable' })
  }
})

server.listen(PORT, () => {
  console.log(`gif-proxy слушает :${PORT}, ключ ${GIPHY_KEY ? 'есть' : 'ОТСУТСТВУЕТ'}`)
})
