// Larpgram key-escrow: депонирование ключа восстановления, чтобы новую сессию можно было
// верифицировать кодом с почты вместо второго устройства.
//
// Клиент (libraries/keyescrow) ходит на push.mango-kokos.ru/escrow, Traefik срезает префикс
// /escrow, поэтому маршруты тут короткие: /key, /code, /key/redeem. Авторизация — Bearer с
// access-токеном Matrix, который сервис проверяет через Synapse whoami.

import express from 'express';
import * as db from './lib/db.js';
import { encrypt, decrypt, hashCode, genCode, timingSafeEqualHex } from './lib/crypto.js';
import { whoami, getEmail, getRoomMembers, deleteRoom, hasAdminToken, getDirectRoomsOf, getRoomDetails } from './lib/matrix.js';
import { isValidRecoveryKey, deleteForBothRefusal } from './lib/rules.js';
import { sendCode } from './lib/mail.js';

const CODE_TTL_MS = Number(process.env.CODE_TTL_SECONDS || 600) * 1000;
const RESEND_MS = Number(process.env.CODE_RESEND_SECONDS || 60) * 1000;
const MAX_ATTEMPTS = Number(process.env.MAX_ATTEMPTS || 5);
const PORT = Number(process.env.PORT || 8080);

// Падаем на старте, если секреты не заданы: лучше не подняться, чем работать без шифрования.
encrypt('warmup');
hashCode('warmup');

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '16kb' }));

function bearer(req) {
  const header = req.get('authorization') || '';
  return header.startsWith('Bearer ') ? header.slice(7).trim() : null;
}

// Мидлварь авторизации: токен -> whoami -> user_id. Токен и ключи в лог не пишем.
async function auth(req, res, next) {
  const token = bearer(req);
  if (!token) return res.sendStatus(401);
  let who;
  try {
    who = await whoami(token);
  } catch (e) {
    console.error('whoami упал:', e.message);
    return res.sendStatus(502);
  }
  if (!who) return res.sendStatus(401);
  req.userId = who.userId;
  req.deviceId = who.deviceId;
  req.token = token;
  next();
}

// Журнал обращений к ключу: кто, с какого устройства, что сделал. Ключей и токенов тут нет.
function audit(req, action, extra = {}) {
  console.log(JSON.stringify({ audit: action, user: req.userId, device: req.deviceId, at: new Date().toISOString(), ...extra }));
}

app.get('/health', (_req, res) => res.json({ ok: true }));

// Лежит ли ключ этого аккаунта. 200 — да, 404 — нет.
app.get('/key', auth, (req, res) => {
  const row = db.getKey.get(req.userId);
  if (row) res.json({ stored: true });
  else res.sendStatus(404);
});

// Залить/перезаписать ключ восстановления.
app.put('/key', auth, (req, res) => {
  const key = req.body?.recovery_key;
  if (!isValidRecoveryKey(key)) {
    audit(req, 'put-key-rejected');
    return res.status(400).json({ error: 'recovery_key must be a Matrix recovery key' });
  }
  db.putKey.run(req.userId, encrypt(key.trim()), Date.now());
  audit(req, 'put-key');
  res.sendStatus(204);
});

// Удалить ключ: клиент зовёт, когда ключ из хранилища не подошёл (его сменили в другом клиенте).
// Тогда, как только сессию подтвердят вручную, клиент выпустит и сохранит свежий ключ.
app.delete('/key', auth, (req, res) => {
  db.deleteKey.run(req.userId);
  audit(req, 'delete-key');
  res.sendStatus(204);
});

// Прислать код на почту аккаунта.
app.post('/code', auth, async (req, res) => {
  let email;
  try {
    email = await getEmail(req.token);
  } catch (e) {
    console.error('3pid упал:', e.message);
    return res.sendStatus(502);
  }
  if (!email) return res.sendStatus(404); // NoEmail

  const existing = db.getCode.get(req.userId);
  if (existing && Date.now() - existing.last_sent_at < RESEND_MS) {
    return res.sendStatus(429); // слишком часто
  }

  const code = genCode();
  db.upsertCode.run(req.userId, hashCode(code), Date.now() + CODE_TTL_MS, Date.now());

  try {
    await sendCode(email, code);
  } catch (e) {
    console.error('отправка письма упала:', e.message);
    return res.sendStatus(502);
  }
  res.json({ masked_email: maskEmail(email) });
});

// Проверить код и, если верный, отдать ключ.
app.post('/key/redeem', auth, (req, res) => {
  const code = String(req.body?.code || '').trim();
  const row = db.getCode.get(req.userId);
  if (!row) return res.sendStatus(410); // кода нет — считаем истёкшим

  if (row.attempts >= MAX_ATTEMPTS) {
    db.deleteCode.run(req.userId);
    return res.sendStatus(429); // TooManyAttempts
  }
  if (Date.now() > row.expires_at) {
    db.deleteCode.run(req.userId);
    return res.sendStatus(410); // Expired
  }

  const correct = /^\d{6}$/.test(code) && timingSafeEqualHex(row.code_hash, hashCode(code));
  if (!correct) {
    db.bumpAttempts.run(req.userId);
    const left = MAX_ATTEMPTS - (row.attempts + 1);
    if (left <= 0) {
      db.deleteCode.run(req.userId);
      return res.sendStatus(429);
    }
    return res.status(400).json({ attempts_left: left });
  }

  const keyRow = db.getKey.get(req.userId);
  db.deleteCode.run(req.userId); // код одноразовый
  if (!keyRow) return res.sendStatus(404); // NoStoredKey

  res.json({ recovery_key: decrypt(keyRow.key_enc) });
});

// Отдать ключ восстановления без кода с почты — любой сессии, которая смогла войти в аккаунт
// (решение юзера 2026-09-24, «как в TG»: вошёл — история на месте). Код с почты при этом
// переезжает на шаг входа (MAS), а не на разблокировку ключа. 404 — ключа нет.
app.get('/key/session', auth, (req, res) => {
  const keyRow = db.getKey.get(req.userId);
  if (!keyRow) return res.sendStatus(404);
  audit(req, 'get-key-session');
  res.set('Cache-Control', 'no-store');
  res.json({ recovery_key: decrypt(keyRow.key_enc) });
});

// «Удалить у обоих» для ЛС: серверный Synapse purge комнаты. Matrix не даёт удалить чужую
// сторону, поэтому это делает сервис своим admin-токеном — но только для локальной лички и только
// если вызывающий сам в ней состоит. Клиент лишь просит; власти у него нет.
app.post('/room/delete', auth, async (req, res) => {
  if (!hasAdminToken()) return res.sendStatus(503); // admin-токен не настроен
  const roomId = String(req.body?.room_id || '').trim();
  if (!roomId.startsWith('!')) return res.status(400).json({ error: 'room_id required' });

  let members;
  try {
    members = await getRoomMembers(roomId);
  } catch (e) {
    console.error('room members упал:', e.message);
    return res.sendStatus(502);
  }
  if (!members) return res.sendStatus(502);
  if (!members.includes(req.userId)) return res.sendStatus(403); // не участник — нельзя

  // Всё проверяем данными сервера, а не клиента (см. deleteForBothRefusal): раньше хватало m.direct
  // вызывающего, а его клиент пишет сам — так можно было снести группу или канал из двоих.
  const room = await getRoomDetails(roomId);
  if (!room) return res.sendStatus(502);
  const directOf = {};
  for (const member of members) {
    const direct = await getDirectRoomsOf(member);
    if (direct === null) return res.sendStatus(502);
    directOf[member] = direct;
  }
  const refusal = deleteForBothRefusal({ callerId: req.userId, members, room, directOf });
  if (refusal) {
    audit(req, 'delete-room-refused', { room: roomId, reason: refusal });
    return res.status(409).json({ error: refusal });
  }

  const ok = await deleteRoom(roomId).catch((e) => {
    console.error('delete room упал:', e.message);
    return false;
  });
  if (!ok) return res.sendStatus(502);
  audit(req, 'delete-room', { room: roomId });
  res.sendStatus(202);
});

function maskEmail(email) {
  const at = email.lastIndexOf('@');
  if (at <= 0) return '***';
  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  const head = local.length <= 1 ? local : local[0];
  return `${head}***@${domain}`;
}

app.listen(PORT, () => console.log(`key-escrow слушает :${PORT}`));
