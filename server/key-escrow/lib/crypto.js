// Шифрование ключа восстановления на диске и хэширование кодов.
//
// Ключ восстановления лежит в БД зашифрованным на master-ключе сервиса (AES-256-GCM):
// сам файл БД в отрыве от ESCROW_MASTER_KEY бесполезен. Это осознанный компромисс модели
// (сервис МОЖЕТ расшифровать), но защищает при утечке одного лишь файла БД или бэкапа.

import crypto from 'node:crypto';

// Разбор master-ключа из base64: ровно 32 байта. name — для текста ошибки (сам ключ в неё не попадает).
export function parseMasterKey(raw, name = 'ESCROW_MASTER_KEY') {
  if (!raw) throw new Error(`${name} не задан`);
  const trimmed = String(raw).trim();
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(trimmed)) throw new Error(`${name}: не base64`);
  const key = Buffer.from(trimmed, 'base64');
  if (key.length !== 32) throw new Error(`${name} должен быть 32 байта в base64 (openssl rand -base64 32)`);
  return key;
}

function masterKey() {
  return parseMasterKey(process.env.ESCROW_MASTER_KEY);
}

function pepper() {
  const p = process.env.CODE_PEPPER;
  if (!p) throw new Error('CODE_PEPPER не задан');
  return p;
}

// Формат: base64(iv[12] || tag[16] || ciphertext). key — Buffer на 32 байта, по умолчанию
// ESCROW_MASTER_KEY (другой ключ передаёт только скрипт ротации).
export function encrypt(plaintext, key = masterKey()) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const enc = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, enc]).toString('base64');
}

export function decrypt(b64, key = masterKey()) {
  const buf = Buffer.from(b64, 'base64');
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const enc = buf.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
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
