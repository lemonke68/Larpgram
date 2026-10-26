#!/usr/bin/env node
// Ротация ESCROW_MASTER_KEY: перешифровать депонированные ключи со старого master-ключа на новый.
//
//   OLD_ESCROW_MASTER_KEY=… NEW_ESCROW_MASTER_KEY=… [DB_PATH=/data/escrow.db] \
//     node scripts/rotate-master-key.js [--dry-run] [--allow-already-rotated]
//
// Сервис на время ротации должен быть остановлен (иначе он может записать ключ старым master-ключом
// уже после ротации). Если хоть одна строка не открывается старым ключом — выход с кодом 1, БД не
// меняется. Печатаются только счётчики. Порядок на сервере — larpgram-infra, `just escrow-rotate`.

import Database from 'better-sqlite3';
import { parseMasterKey } from '../lib/crypto.js';
import { rotateMasterKey, RotationError } from '../lib/rotate.js';

const USAGE = 'usage: OLD_ESCROW_MASTER_KEY=… NEW_ESCROW_MASTER_KEY=… [DB_PATH=…] node scripts/rotate-master-key.js [--dry-run] [--allow-already-rotated]';

function fail(message, code = 1) {
  console.error(`rotate-master-key: ${message}`);
  process.exit(code);
}

const args = process.argv.slice(2);
const known = new Set(['--dry-run', '--allow-already-rotated']);
for (const a of args) if (!known.has(a)) fail(`неизвестный аргумент ${a}\n${USAGE}`, 2);
const dryRun = args.includes('--dry-run');
const allowAlreadyRotated = args.includes('--allow-already-rotated');

let oldKey, newKey;
try {
  oldKey = parseMasterKey(process.env.OLD_ESCROW_MASTER_KEY, 'OLD_ESCROW_MASTER_KEY');
  newKey = parseMasterKey(process.env.NEW_ESCROW_MASTER_KEY, 'NEW_ESCROW_MASTER_KEY');
} catch (e) {
  fail(`${e.message}\n${USAGE}`, 2);
}
if (oldKey.equals(newKey)) fail('OLD_ESCROW_MASTER_KEY и NEW_ESCROW_MASTER_KEY совпадают', 2);

const dbPath = process.env.DB_PATH || '/data/escrow.db';
let db;
try {
  // fileMustExist: опечатка в пути не должна молча создать пустую БД и «успешно» ничего не сделать.
  db = new Database(dbPath, { fileMustExist: true });
} catch (e) {
  fail(`не открыть БД ${dbPath}: ${e.message}`);
}

try {
  const hasKeys = db.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'keys'").get();
  if (!hasKeys) fail(`в ${dbPath} нет таблицы keys — не та БД?`);

  const c = rotateMasterKey(db, { oldKey, newKey, dryRun, allowAlreadyRotated });
  const mode = dryRun ? 'dry-run, ничего не записано' : 'записано';
  console.log(`rotate-master-key: ${mode}; всего строк ${c.total}, перешифровано ${c.rotated}, уже на новом ключе ${c.alreadyRotated}`);
  if (!dryRun) db.pragma('wal_checkpoint(TRUNCATE)');
} catch (e) {
  if (e instanceof RotationError) {
    const c = e.counts;
    const tail = c ? ` (всего ${c.total}, на старом ключе ${c.rotated}, уже на новом ${c.alreadyRotated}, не открываются ${c.unreadable})` : '';
    fail(`${e.message}${tail}`);
  }
  fail(`ошибка: ${e.message}`);
} finally {
  db.close();
}
