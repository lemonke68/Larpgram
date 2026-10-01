// Чистые правила сервиса без сети и базы — их гоняют тесты (node --test).

// Ник как в Telegram: латиница, цифры и подчёркивание, начинается с буквы. Нижний регистр —
// требование Matrix к localpart.
const USERNAME = /^[a-z][a-z0-9_]{2,31}$/;

// Имена, под которыми можно выдать себя за сервис или администратора.
const RESERVED = new Set([
  'admin', 'administrator', 'root', 'support', 'help', 'system', 'server', 'moderator',
  'larpgram', 'telegram', 'matrix', 'element', 'noreply', 'no_reply', 'postmaster', 'abuse',
  'security', 'escrow_admin', 'bot', 'official',
]);

/** Приводит ник к виду, в котором он хранится: без «@», пробелов по краям и в нижнем регистре. */
export function normalizeUsername(value) {
  if (typeof value !== 'string') return '';
  return value.trim().replace(/^@/, '').toLowerCase();
}

/** null, если ник подходит, иначе код причины для клиента. */
export function usernameProblem(username) {
  if (typeof username !== 'string' || username.length < 3) return 'username_too_short';
  if (username.length > 32) return 'username_too_long';
  if (!USERNAME.test(username)) return 'username_invalid';
  if (RESERVED.has(username)) return 'username_taken';
  return null;
}

export function normalizeEmail(value) {
  if (typeof value !== 'string') return '';
  return value.trim().toLowerCase();
}

// Не RFC целиком: один «@», непустые части без пробелов, точка в домене. Настоящая проверка —
// письмо с кодом.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function isValidEmail(email) {
  return typeof email === 'string' && email.length <= 254 && EMAIL.test(email);
}

/** null, если пароль подходит. Стойкость дополнительно проверяет MAS при установке. */
export function passwordProblem(password) {
  if (typeof password !== 'string' || password.length < 8) return 'password_too_short';
  if (password.length > 128) return 'password_too_long';
  return null;
}

/** Что ввели в «ник или почта»: почту ищем по адресу, остальное считаем ником. */
export function parseLogin(value) {
  const raw = typeof value === 'string' ? value.trim() : '';
  if (raw.includes('@') && !raw.startsWith('@')) return { email: normalizeEmail(raw) };
  // Полный Matrix ID (@nick:server) тоже принимаем — берём ник.
  return { username: normalizeUsername(raw.split(':')[0]) };
}

export function maskEmail(email) {
  const at = email.lastIndexOf('@');
  if (at <= 0) return '***';
  const local = email.slice(0, at);
  return `${local[0]}***@${email.slice(at + 1)}`;
}

/**
 * Что делать с введённым кодом. Строка тикета: { code_hash, expires_at, attempts }.
 * Возвращает 'ok' | 'wrong' | 'expired' | 'too_many'.
 */
export function judgeCode({ row, codeMatches, now, maxAttempts }) {
  if (!row) return 'expired';
  if (now > row.expires_at) return 'expired';
  if (row.attempts >= maxAttempts) return 'too_many';
  return codeMatches ? 'ok' : 'wrong';
}

/** Ник из Matrix ID (@nick:server) или null. */
export function localpartOf(userId) {
  if (typeof userId !== 'string' || !userId.startsWith('@')) return null;
  const colon = userId.indexOf(':');
  return colon > 1 ? userId.slice(1, colon) : null;
}

/** Имя устройства для списка сеансов: без управляющих символов, не длиннее 64 знаков. */
export function cleanDeviceName(value) {
  const name = typeof value === 'string' ? value.replace(/[\u0000-\u001f\u007f]/g, '').trim() : '';
  return (name || 'Larpgram').slice(0, 64);
}
