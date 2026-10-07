// Larpgram key-escrow: ключ восстановления, чтобы новая сессия открывала историю без второго
// устройства.
//
// Клиент (libraries/keyescrow) ходит на push.mango-kokos.ru/escrow, Traefik срезает префикс
// /escrow, поэтому маршруты тут короткие. Авторизация — Bearer с access-токеном Matrix, который
// сервис проверяет через Synapse whoami.
//
// v2 (вариант B, larpgram-infra этап 7): клиент запирает ключ паролем аккаунта (Argon2id) и
// кладёт сюда блоб, открыть который сервер не может. Ключ на master-ключе сервиса (v1) остаётся
// только у тех, кто сам включил «восстановление через сервер», и у старых аккаунтов до
// LEGACY_KEYS_UNTIL. Маршруты — lib/app.js.

import { createApp } from './lib/app.js';
import { openDb } from './lib/db.js';
import { encrypt, hashCode } from './lib/crypto.js';
import * as matrix from './lib/matrix.js';
import * as mailer from './lib/mail.js';

const PORT = Number(process.env.PORT || 8080);

// Падаем на старте, если секреты не заданы: лучше не подняться, чем работать без шифрования.
encrypt('warmup');
hashCode('warmup');

// LEGACY_KEYS_UNTIL — дата ISO (2026-11-15): после неё ключи v1 без тумблера не отдаются и удаляются.
const legacyRaw = (process.env.LEGACY_KEYS_UNTIL || '').trim();
const legacyKeysUntil = legacyRaw ? Date.parse(legacyRaw) : null;
if (legacyRaw && Number.isNaN(legacyKeysUntil)) throw new Error(`LEGACY_KEYS_UNTIL не дата: ${legacyRaw}`);

const { app, sweep } = createApp({
  db: openDb(),
  matrix,
  mailer,
  config: {
    codeTtlSeconds: Number(process.env.CODE_TTL_SECONDS || 600),
    codeResendSeconds: Number(process.env.CODE_RESEND_SECONDS || 60),
    maxAttempts: Number(process.env.MAX_ATTEMPTS || 5),
    sessionKeyWindowSeconds: Number(process.env.SESSION_KEY_WINDOW_SECONDS || 900),
    legacyKeysUntil,
  },
});

sweep();
setInterval(sweep, 60 * 60_000).unref();

app.listen(PORT, () => console.log(`key-escrow слушает :${PORT}`));
