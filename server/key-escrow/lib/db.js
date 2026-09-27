// Хранилище: SQLite (better-sqlite3, синхронный). Масштаб крошечный — десятки аккаунтов,
// поэтому одного файла с WAL достаточно.

import Database from 'better-sqlite3';

const path = process.env.DB_PATH || '/data/escrow.db';
const db = new Database(path);
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS keys (
    user_id    TEXT PRIMARY KEY,
    key_enc    TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS codes (
    user_id      TEXT PRIMARY KEY,
    code_hash    TEXT NOT NULL,
    expires_at   INTEGER NOT NULL,
    attempts     INTEGER NOT NULL DEFAULT 0,
    last_sent_at INTEGER NOT NULL
  );
`);

export const putKey = db.prepare(
  `INSERT INTO keys(user_id, key_enc, created_at) VALUES(?, ?, ?)
   ON CONFLICT(user_id) DO UPDATE SET key_enc = excluded.key_enc, created_at = excluded.created_at`
);
export const getKey = db.prepare('SELECT key_enc FROM keys WHERE user_id = ?');
export const deleteKey = db.prepare('DELETE FROM keys WHERE user_id = ?');

export const upsertCode = db.prepare(
  `INSERT INTO codes(user_id, code_hash, expires_at, attempts, last_sent_at) VALUES(?, ?, ?, 0, ?)
   ON CONFLICT(user_id) DO UPDATE SET
     code_hash = excluded.code_hash,
     expires_at = excluded.expires_at,
     attempts = 0,
     last_sent_at = excluded.last_sent_at`
);
export const getCode = db.prepare('SELECT * FROM codes WHERE user_id = ?');
export const bumpAttempts = db.prepare('UPDATE codes SET attempts = attempts + 1 WHERE user_id = ?');
export const deleteCode = db.prepare('DELETE FROM codes WHERE user_id = ?');

export default db;
