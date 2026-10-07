// Чистые правила сервиса без сети и базы — их гоняют тесты (node --test).

// Ключ восстановления Matrix: base58 от 35 байт (2 байта префикса, 32 байта ключа, байт
// чётности) — ровно 48 символов. Клиенты показывают его группами по 4 через пробел.
const BASE58_48 = /^[1-9A-HJ-NP-Za-km-z]{48}$/;

export function isValidRecoveryKey(value) {
  if (typeof value !== 'string') return false;
  return BASE58_48.test(value.replace(/\s+/g, ''));
}

// Домен из user_id (@user:domain) или null.
export function domainOf(userId) {
  if (typeof userId !== 'string') return null;
  const colon = userId.indexOf(':');
  return colon > 0 ? userId.slice(colon + 1) : null;
}

/**
 * Можно ли «удалить у обоих» эту комнату. Purge через admin API сносит комнату только на нашем
 * сервере, а m.direct любой клиент пишет сам, поэтому проверяем всё, что клиент подделать не может:
 *  - вызывающий — участник, участников не больше двух, все с нашего сервера;
 *  - у комнаты нет имени и адреса, писать может любой участник (не канал);
 *  - комната числится личкой в m.direct у каждого участника (читаем через admin API).
 * Возвращает null, если можно, иначе причину (для лога и кода 409/403).
 */
export function deleteForBothRefusal({ callerId, members, room, directOf }) {
  if (!members.includes(callerId)) return 'not-a-member';
  if (members.length === 0 || members.length > 2) return 'not-two-people';
  const home = domainOf(callerId);
  if (!members.every((m) => domainOf(m) === home)) return 'remote-member';
  if (room.name) return 'named-room';
  if (room.canonicalAlias) return 'room-with-alias';
  if ((room.eventsDefault ?? 0) > 0) return 'channel';
  for (const member of members) {
    const direct = directOf[member];
    if (!direct) return 'not-direct';
    const listed = Object.values(direct).some((rooms) => Array.isArray(rooms) && rooms.includes(room.roomId));
    if (!listed) return 'not-direct';
  }
  return null;
}

/**
 * Можно ли отдать ключ по /key/session этому устройству. Ключ выдаётся устройству только в
 * течение окна с первой выдачи: на случай, если recover() на клиенте упал по сети и надо
 * повторить. Потом 403, иначе украденный токен давнего устройства снова тянет ключ.
 * issuedAt — время первой выдачи этому device_id или null. Возвращает 'first', 'again' или 'expired'.
 */
export function sessionKeyIssue({ issuedAt, now, windowMs }) {
  if (issuedAt == null) return 'first';
  return now - issuedAt <= windowMs ? 'again' : 'expired';
}

// Текст уведомления владельцу о выдаче ключа новому устройству.
export function keyIssuedNotice({ deviceId, deviceName, via }) {
  const name = deviceName ? `«${deviceName}» (${deviceId})` : deviceId;
  const how = via === 'code' ? 'по коду с почты' : 'при входе';
  return `Устройство ${name} получило ключ восстановления ${how} и теперь видит всю историю переписки. ` +
    'Если это не вы, смените пароль и завершите этот сеанс в настройках.';
}

// --- Вариант B: ключ, запертый паролем ---

const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;

function base64Length(value) {
  if (typeof value !== 'string' || value.length % 4 !== 0 || !BASE64.test(value)) return -1;
  return Buffer.from(value, 'base64').length;
}

const BLOB_FIELDS = ['v', 'kdf', 'm', 't', 'p', 'salt', 'nonce', 'ct'];
const intIn = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;

/**
 * Что не так с блобом от клиента, или null. Расшифровать его сервер не может, но формат и нижнюю
 * границу Argon2id проверяет: кривой клиент не должен запереть ключ слабыми параметрами.
 * m — память в КиБ, t — проходы, p — потоки. ct — AES-256-GCM с тегом (ключ ~48 символов + 16).
 */
export function blobProblem(blob) {
  if (!blob || typeof blob !== 'object' || Array.isArray(blob)) return 'not-an-object';
  if (Object.keys(blob).some((k) => !BLOB_FIELDS.includes(k))) return 'unknown-field';
  if (blob.v !== 1) return 'version';
  if (blob.kdf !== 'argon2id') return 'kdf';
  if (!intIn(blob.m, 16 * 1024, 1024 * 1024)) return 'memory';
  if (!intIn(blob.t, 2, 16)) return 'iterations';
  if (!intIn(blob.p, 1, 8)) return 'parallelism';
  const salt = base64Length(blob.salt);
  if (salt < 16 || salt > 64) return 'salt';
  if (base64Length(blob.nonce) !== 12) return 'nonce';
  const ct = base64Length(blob.ct);
  if (ct < 17 || ct > 512) return 'ciphertext';
  return null;
}

/**
 * Отдаёт ли сервер свой ключ (v1: /key/session и /key/redeem). С тумблером «восстановление через
 * сервер» — всегда. Ключ без тумблера (остался с v1) — только до legacyUntil: это срок, за который
 * клиенты с вариантом B перезапирают ключ паролем. legacyUntil = null — срок не назначен.
 */
export function serverKeyServed({ optIn, now, legacyUntil }) {
  if (optIn) return true;
  return legacyUntil == null || now <= legacyUntil;
}
