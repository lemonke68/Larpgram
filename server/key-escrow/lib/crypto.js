// Шифрование ключа восстановления на диске и хэширование кодов.
//
// Ключ восстановления лежит в БД зашифрованным на master-ключе сервиса (AES-256-GCM):
// сам файл БД в отрыве от ESCROW_MASTER_KEY бесполезен. Это осознанный компромисс модели
// (сервис МОЖЕТ расшифровать), но защищает при утечке одного лишь файла БД или бэкапа.

import crypto from 'node:crypto';

function masterKey() {
  const raw = process.env.ESCROW_MASTER_KEY;
  if (!raw) throw new Error('ESCROW_MASTER_KEY не задан');
  const key = Buffer.from(raw, 'base64');
  if (key.length !== 32) throw new Error('ESCROW_MASTER_KEY должен быть 32 байта в base64 (openssl rand -base64 32)');
  return key;
}

function pepper() {
  const p = process.env.CODE_PEPPER;
  if (!p) throw new Error('CODE_PEPPER не задан');
  return p;
}

// Формат: base64(iv[12] || tag[16] || ciphertext).
export function encrypt(plaintext) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', masterKey(), iv);
  const enc = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, enc]).toString('base64');
}

export function decrypt(b64) {
  const buf = Buffer.from(b64, 'base64');
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const enc = buf.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', masterKey(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(enc), decipher.final()]).toString('utf8');
}

// Код в БД не хранится в открытую: HMAC с пеппером. Против перебора работают короткий срок
// жизни и лимит попыток, а не стойкость хэша (энтропия 6 цифр невелика).
export function hashCode(code) {
  return crypto.createHmac('sha256', pepper()).update(code).digest('hex');
}

export function timingSafeEqualHex(a, b) {
  const ba = Buffer.from(a, 'hex');
  const bb = Buffer.from(b, 'hex');
  return ba.length === bb.length && crypto.timingSafeEqual(ba, bb);
}

// Ровно 6 цифр, включая ведущие нули.
export function genCode() {
  return String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
}
