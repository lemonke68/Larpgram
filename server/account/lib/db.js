// Хранилище заявок: SQLite (better-sqlite3, синхронный). Заявка живёт минуты, паролей в ней нет.

import Database from 'better-sqlite3';

export function openDb(path = process.env.DB_PATH || '/data/account.db') {
  const db = new Database(path);
  db.pragma('journal_mode = WAL');

  // kind: 'register' | 'reset'. user_id — id пользователя в MAS: у сброса известен сразу, у
  // регистрации появляется после создания аккаунта. Пустой email у сброса — заявка-пустышка
  // (аккаунта или почты нет): клиенту отвечаем так же, письмо не шлём, код не подойдёт никогда.
  db.exec(`
    CREATE TABLE IF NOT EXISTS tickets (
      id           TEXT PRIMARY KEY,
      kind         TEXT NOT NULL,
      username     TEXT NOT NULL DEFAULT '',
      email        TEXT NOT NULL DEFAULT '',
      user_id      TEXT NOT NULL DEFAULT '',
      code_hash    TEXT NOT NULL,
      expires_at   INTEGER NOT NULL,
      attempts     INTEGER NOT NULL DEFAULT 0,
      last_sent_at INTEGER NOT NULL,
      verified     INTEGER NOT NULL DEFAULT 0
    );
  `);

  // Привязка устройства по QR: вошедшее устройство создаёт предложение, новое гасит его кодом из
  // QR. Сам код не храним — только хэш. redeemed_at > 0 — код уже использован.
  db.exec(`
    CREATE TABLE IF NOT EXISTS pair_offers (
      code_hash   TEXT PRIMARY KEY,
      user_id     TEXT NOT NULL,
      expires_at  INTEGER NOT NULL,
      redeemed_at INTEGER NOT NULL DEFAULT 0
    );
  `);

  return {
    insertOffer: db.prepare('INSERT INTO pair_offers(code_hash, user_id, expires_at) VALUES(?, ?, ?)'),
    getOffer: db.prepare('SELECT * FROM pair_offers WHERE code_hash = ?'),
    // Гасим одним запросом с условием: два телефона не получат сессию по одному коду.
    redeemOffer: db.prepare('UPDATE pair_offers SET redeemed_at = ? WHERE code_hash = ? AND redeemed_at = 0 AND expires_at >= ?'),
    deleteExpiredOffers: db.prepare('DELETE FROM pair_offers WHERE expires_at < ?'),
    insert: db.prepare(
      `INSERT INTO tickets(id, kind, username, email, user_id, code_hash, expires_at, last_sent_at)
       VALUES(@id, @kind, @username, @email, @user_id, @code_hash, @expires_at, @last_sent_at)`
    ),
    get: db.prepare('SELECT * FROM tickets WHERE id = ?'),
    newCode: db.prepare(
      'UPDATE tickets SET code_hash = ?, expires_at = ?, attempts = 0, last_sent_at = ? WHERE id = ?'
    ),
    bumpAttempts: db.prepare('UPDATE tickets SET attempts = attempts + 1 WHERE id = ?'),
    // Код принят: заявка продлевается, чтобы человек успел исправить слабый пароль.
    markVerified: db.prepare('UPDATE tickets SET verified = 1, expires_at = ? WHERE id = ?'),
    setUserId: db.prepare('UPDATE tickets SET user_id = ? WHERE id = ?'),
    delete: db.prepare('DELETE FROM tickets WHERE id = ?'),
    deleteExpired: db.prepare('DELETE FROM tickets WHERE expires_at < ?'),
    close: () => db.close(),
  };
}
