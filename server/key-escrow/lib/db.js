// Хранилище: SQLite (better-sqlite3, синхронный). Масштаб крошечный — десятки аккаунтов,
// поэтому одного файла с WAL достаточно.

import Database from 'better-sqlite3';

export function openDb(path = process.env.DB_PATH || '/data/escrow.db') {
  const db = new Database(path);
  db.pragma('journal_mode = WAL');

  // keys — ключ восстановления на master-ключе сервиса (v1, «восстановление через сервер»).
  // opt_in = 1: человек сам включил его в настройках (v2). 0 — ключ остался со времён v1, его
  // удалит переход на вариант B (см. legacyKeysUntil в app.js).
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
    -- Кому /key/session уже отдавал ключ: первая выдача на устройство (окно повтора — в app.js).
    CREATE TABLE IF NOT EXISTS session_issues (
      user_id   TEXT NOT NULL,
      device_id TEXT NOT NULL,
      first_at  INTEGER NOT NULL,
      PRIMARY KEY (user_id, device_id)
    );
    -- Вариант B: ключ восстановления, запертый паролем на клиенте. Сервер его открыть не может,
    -- blob — JSON, который клиент прислал (формат проверяет blobProblem в rules.js).
    CREATE TABLE IF NOT EXISTS blobs (
      user_id    TEXT PRIMARY KEY,
      blob       TEXT NOT NULL,
      device_id  TEXT,
      updated_at INTEGER NOT NULL
    );
  `);
  const keyColumns = db.prepare('PRAGMA table_info(keys)').all().map((c) => c.name);
  if (!keyColumns.includes('opt_in')) {
    db.exec('ALTER TABLE keys ADD COLUMN opt_in INTEGER NOT NULL DEFAULT 0');
  }

  return {
    close: () => db.close(),
    // v1 PUT /key не трогает opt_in у существующей строки: старый клиент не выключит тумблер.
    putKey: db.prepare(
      `INSERT INTO keys(user_id, key_enc, created_at) VALUES(?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET key_enc = excluded.key_enc, created_at = excluded.created_at`
    ),
    putOptInKey: db.prepare(
      `INSERT INTO keys(user_id, key_enc, created_at, opt_in) VALUES(?, ?, ?, 1)
       ON CONFLICT(user_id) DO UPDATE SET key_enc = excluded.key_enc, created_at = excluded.created_at, opt_in = 1`
    ),
    getKey: db.prepare('SELECT key_enc, opt_in FROM keys WHERE user_id = ?'),
    deleteKey: db.prepare('DELETE FROM keys WHERE user_id = ?'),
    deleteLegacyKeys: db.prepare('DELETE FROM keys WHERE opt_in = 0'),

    upsertCode: db.prepare(
      `INSERT INTO codes(user_id, code_hash, expires_at, attempts, last_sent_at) VALUES(?, ?, ?, 0, ?)
       ON CONFLICT(user_id) DO UPDATE SET
         code_hash = excluded.code_hash,
         expires_at = excluded.expires_at,
         attempts = 0,
         last_sent_at = excluded.last_sent_at`
    ),
    getCode: db.prepare('SELECT * FROM codes WHERE user_id = ?'),
    bumpAttempts: db.prepare('UPDATE codes SET attempts = attempts + 1 WHERE user_id = ?'),
    deleteCode: db.prepare('DELETE FROM codes WHERE user_id = ?'),

    getSessionIssue: db.prepare('SELECT first_at FROM session_issues WHERE user_id = ? AND device_id = ?'),
    putSessionIssue: db.prepare('INSERT OR IGNORE INTO session_issues(user_id, device_id, first_at) VALUES(?, ?, ?)'),
    deleteSessionIssues: db.prepare('DELETE FROM session_issues WHERE user_id = ?'),

    putBlob: db.prepare(
      `INSERT INTO blobs(user_id, blob, device_id, updated_at) VALUES(?, ?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET blob = excluded.blob, device_id = excluded.device_id, updated_at = excluded.updated_at`
    ),
    getBlob: db.prepare('SELECT blob, updated_at FROM blobs WHERE user_id = ?'),
    deleteBlob: db.prepare('DELETE FROM blobs WHERE user_id = ?'),
  };
}
