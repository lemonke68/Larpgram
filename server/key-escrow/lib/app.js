// Маршруты сервиса. Зависимости (база, Synapse, почта, часы) приходят снаружи, чтобы тесты гоняли
// приложение целиком без сети.

import express from 'express';
import { encrypt, decrypt, hashCode, genCode, timingSafeEqualHex } from './crypto.js';
import {
  isValidRecoveryKey, deleteForBothRefusal, sessionKeyIssue, keyIssuedNotice, blobProblem, serverKeyServed,
} from './rules.js';

export function createApp({ db, matrix, mailer, now = Date.now, config = {} }) {
  const codeTtlMs = (config.codeTtlSeconds ?? 600) * 1000;
  const resendMs = (config.codeResendSeconds ?? 60) * 1000;
  const maxAttempts = config.maxAttempts ?? 5;
  // Сколько устройство может повторять /key/session после первой выдачи (сбой recover() на клиенте).
  const sessionKeyWindowMs = (config.sessionKeyWindowSeconds ?? 900) * 1000;
  // До какого момента (мс) сервер отдаёт ключи v1 без тумблера. null — без срока.
  const legacyKeysUntil = config.legacyKeysUntil ?? null;

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
      who = await matrix.whoami(token);
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
    console.log(JSON.stringify({ audit: action, user: req.userId, device: req.deviceId, at: new Date(now()).toISOString(), ...extra }));
  }

  // Ключ на master-ключе сервиса, если его сейчас можно отдавать (тумблер или срок перехода).
  function servedKey(userId) {
    const row = db.getKey.get(userId);
    if (!row) return null;
    return serverKeyServed({ optIn: row.opt_in === 1, now: now(), legacyUntil: legacyKeysUntil }) ? row : null;
  }

  // Сообщить владельцу в Server Notices, что устройство получило ключ. Ключ отдаём в любом случае:
  // без admin-токена или при сбое Synapse выдача не блокируется, только пишется в журнал.
  async function noticeKeyIssued(req, via) {
    if (!matrix.hasAdminToken()) return audit(req, 'notice-skipped', { via });
    try {
      const deviceName = await matrix.getDeviceName(req.userId, req.deviceId);
      const ok = await matrix.sendServerNotice(req.userId, keyIssuedNotice({ deviceId: req.deviceId, deviceName, via }));
      audit(req, ok ? 'notice-sent' : 'notice-failed', { via });
    } catch (e) {
      console.error('server notice упал:', e.message);
      audit(req, 'notice-failed', { via });
    }
  }

  app.get('/health', (_req, res) => res.json({ ok: true }));

  // --- v1: ключ на master-ключе сервиса ---

  const legacyOver = () => legacyKeysUntil != null && now() > legacyKeysUntil;

  // Лежит ли ключ этого аккаунта. 200 — да, 404 — нет. Клиент до варианта B на 404 перевыпускает
  // ключ восстановления (бэкфилл), а это ломает блоб устройств с B. Поэтому «да» и тогда, когда
  // есть блоб, и после срока перехода: старому клиенту больше нечего сюда класть.
  app.get('/key', auth, (req, res) => {
    if (servedKey(req.userId) || db.getBlob.get(req.userId) || legacyOver()) res.json({ stored: true });
    else res.sendStatus(404);
  });

  // Залить/перезаписать ключ восстановления (клиенты до варианта B). После срока перехода — 410.
  app.put('/key', auth, (req, res) => {
    if (legacyOver()) return res.sendStatus(410);
    const key = req.body?.recovery_key;
    if (!isValidRecoveryKey(key)) {
      audit(req, 'put-key-rejected');
      return res.status(400).json({ error: 'recovery_key must be a Matrix recovery key' });
    }
    db.putKey.run(req.userId, encrypt(key.trim()), now());
    audit(req, 'put-key');
    res.sendStatus(204);
  });

  // Удалить ключ: клиент зовёт, когда ключ из хранилища не подошёл (его сменили в другом клиенте).
  app.delete('/key', auth, (req, res) => {
    db.deleteKey.run(req.userId);
    audit(req, 'delete-key');
    res.sendStatus(204);
  });

  // Прислать код на почту аккаунта.
  app.post('/code', auth, async (req, res) => {
    let email;
    try {
      email = await matrix.getEmail(req.token);
    } catch (e) {
      console.error('3pid упал:', e.message);
      return res.sendStatus(502);
    }
    if (!email) return res.sendStatus(404); // NoEmail

    const existing = db.getCode.get(req.userId);
    if (existing && now() - existing.last_sent_at < resendMs) {
      return res.sendStatus(429); // слишком часто
    }

    const code = genCode();
    db.upsertCode.run(req.userId, hashCode(code), now() + codeTtlMs, now());

    try {
      await mailer.sendCode(email, code);
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

    if (row.attempts >= maxAttempts) {
      db.deleteCode.run(req.userId);
      return res.sendStatus(429); // TooManyAttempts
    }
    if (now() > row.expires_at) {
      db.deleteCode.run(req.userId);
      return res.sendStatus(410); // Expired
    }

    const correct = /^\d{6}$/.test(code) && timingSafeEqualHex(row.code_hash, hashCode(code));
    if (!correct) {
      db.bumpAttempts.run(req.userId);
      const left = maxAttempts - (row.attempts + 1);
      if (left <= 0) {
        db.deleteCode.run(req.userId);
        return res.sendStatus(429);
      }
      return res.status(400).json({ attempts_left: left });
    }

    const keyRow = servedKey(req.userId);
    db.deleteCode.run(req.userId); // код одноразовый
    if (!keyRow) return res.sendStatus(404); // NoStoredKey

    audit(req, 'get-key-code');
    noticeKeyIssued(req, 'code');
    res.json({ recovery_key: decrypt(keyRow.key_enc) });
  });

  // Отдать ключ восстановления без кода с почты — любой сессии, которая смогла войти в аккаунт
  // (решение юзера 2026-09-24, «как в TG»). С вариантом B — только тем, кто включил тумблер
  // «восстановление через сервер», и старым ключам до конца срока перехода.
  // Каждому устройству ключ отдаётся один раз, с окном на повтор, и владелец получает уведомление.
  // Потом 403: клиент на не-200 просто не разблокирует сессию, подтверждать её придётся вручную.
  app.get('/key/session', auth, (req, res) => {
    const keyRow = servedKey(req.userId);
    if (!keyRow) return res.sendStatus(404);
    if (!req.deviceId) return res.sendStatus(403);

    const issue = sessionKeyIssue({
      issuedAt: db.getSessionIssue.get(req.userId, req.deviceId)?.first_at ?? null,
      now: now(),
      windowMs: sessionKeyWindowMs,
    });
    if (issue === 'expired') {
      audit(req, 'get-key-session-refused');
      return res.sendStatus(403);
    }
    if (issue === 'first') {
      db.putSessionIssue.run(req.userId, req.deviceId, now());
      noticeKeyIssued(req, 'session');
    }
    audit(req, 'get-key-session', { issue });
    res.set('Cache-Control', 'no-store');
    res.json({ recovery_key: decrypt(keyRow.key_enc) });
  });

  // --- v2: вариант B, ключ запертый паролем на клиенте ---

  // Что лежит у аккаунта: блоб, ключ сервера и включён ли тумблер. Клиент решает по этому,
  // нужно ли перезапереть ключ и показывать ли тумблер включённым.
  app.get('/v2/state', auth, (req, res) => {
    const key = db.getKey.get(req.userId);
    res.set('Cache-Control', 'no-store');
    res.json({
      blob: Boolean(db.getBlob.get(req.userId)),
      server_key: Boolean(key),
      server_opt_in: key?.opt_in === 1,
    });
  });

  // Блоб для своей сессии. Открыть его может только тот, кто знает пароль.
  app.get('/v2/blob', auth, (req, res) => {
    const row = db.getBlob.get(req.userId);
    if (!row) return res.sendStatus(404);
    audit(req, 'get-blob');
    res.set('Cache-Control', 'no-store');
    res.json({ blob: JSON.parse(row.blob), updated_at: row.updated_at });
  });

  app.put('/v2/blob', auth, (req, res) => {
    const blob = req.body?.blob;
    const problem = blobProblem(blob);
    if (problem) {
      audit(req, 'put-blob-rejected', { problem });
      return res.status(400).json({ error: problem });
    }
    db.putBlob.run(req.userId, JSON.stringify(blob), req.deviceId, now());
    audit(req, 'put-blob');
    res.sendStatus(204);
  });

  app.delete('/v2/blob', auth, (req, res) => {
    db.deleteBlob.run(req.userId);
    audit(req, 'delete-blob');
    res.sendStatus(204);
  });

  // Тумблер «восстановление через сервер»: включить — положить ключ на master-ключе сервиса.
  app.put('/v2/server-key', auth, (req, res) => {
    const key = req.body?.recovery_key;
    if (!isValidRecoveryKey(key)) {
      audit(req, 'put-server-key-rejected');
      return res.status(400).json({ error: 'recovery_key must be a Matrix recovery key' });
    }
    db.putOptInKey.run(req.userId, encrypt(key.trim()), now());
    audit(req, 'put-server-key');
    res.sendStatus(204);
  });

  // Выключить тумблер (или убрать старый ключ v1 после перехода на блоб): у сервера ключа больше нет.
  app.delete('/v2/server-key', auth, (req, res) => {
    db.deleteKey.run(req.userId);
    db.deleteSessionIssues.run(req.userId);
    audit(req, 'delete-server-key');
    res.sendStatus(204);
  });

  // «Удалить у обоих» для ЛС: серверный Synapse purge комнаты. Matrix не даёт удалить чужую
  // сторону, поэтому это делает сервис своим admin-токеном — но только для локальной лички и только
  // если вызывающий сам в ней состоит. Клиент лишь просит; власти у него нет.
  app.post('/room/delete', auth, async (req, res) => {
    if (!matrix.hasAdminToken()) return res.sendStatus(503); // admin-токен не настроен
    const roomId = String(req.body?.room_id || '').trim();
    if (!roomId.startsWith('!')) return res.status(400).json({ error: 'room_id required' });

    let members;
    try {
      members = await matrix.getRoomMembers(roomId);
    } catch (e) {
      console.error('room members упал:', e.message);
      return res.sendStatus(502);
    }
    if (!members) return res.sendStatus(502);
    if (!members.includes(req.userId)) return res.sendStatus(403); // не участник — нельзя

    // Всё проверяем данными сервера, а не клиента (см. deleteForBothRefusal): раньше хватало m.direct
    // вызывающего, а его клиент пишет сам — так можно было снести группу или канал из двоих.
    const room = await matrix.getRoomDetails(roomId);
    if (!room) return res.sendStatus(502);
    const directOf = {};
    for (const member of members) {
      const direct = await matrix.getDirectRoomsOf(member);
      if (direct === null) return res.sendStatus(502);
      directOf[member] = direct;
    }
    const refusal = deleteForBothRefusal({ callerId: req.userId, members, room, directOf });
    if (refusal) {
      audit(req, 'delete-room-refused', { room: roomId, reason: refusal });
      return res.status(409).json({ error: refusal });
    }

    const ok = await matrix.deleteRoom(roomId).catch((e) => {
      console.error('delete room упал:', e.message);
      return false;
    });
    if (!ok) return res.sendStatus(502);
    audit(req, 'delete-room', { room: roomId });
    res.sendStatus(202);
  });

  // Уборка: по окончании срока перехода ключи v1 без тумблера удаляются из базы.
  const sweep = () => {
    if (legacyKeysUntil == null || now() <= legacyKeysUntil) return;
    const removed = db.deleteLegacyKeys.run().changes;
    if (removed > 0) console.log(JSON.stringify({ audit: 'legacy-keys-removed', count: removed, at: new Date(now()).toISOString() }));
  };

  return { app, sweep };
}

function maskEmail(email) {
  const at = email.lastIndexOf('@');
  if (at <= 0) return '***';
  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  const head = local.length <= 1 ? local : local[0];
  return `${head}***@${domain}`;
}
