// Коды и номера заявок. Код в БД не хранится в открытую: HMAC с пеппером. Против перебора
// работают короткий срок жизни и лимит попыток, а не стойкость хэша (энтропия 6 цифр невелика).

import crypto from 'node:crypto';

function pepper() {
  const p = process.env.CODE_PEPPER;
  if (!p) throw new Error('CODE_PEPPER не задан');
  return p;
}

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

// Номер заявки: по нему клиент продолжает регистрацию или сброс. Угадать нельзя.
export function genTicket() {
  return crypto.randomBytes(24).toString('base64url');
}

// Код привязки устройства живёт в QR-коде. В базе — только его SHA-256: код длинный и случайный,
// пеппер ему не нужен.
export function genPairCode() {
  return crypto.randomBytes(32).toString('base64url');
}

export function hashPairCode(code) {
  return crypto.createHash('sha256').update(code).digest('hex');
}

// Идентификатор устройства Matrix: 10 заглавных букв, как выдаёт Synapse.
export function genDeviceId() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let id = '';
  for (let i = 0; i < 10; i++) id += alphabet[crypto.randomInt(0, alphabet.length)];
  return id;
}
