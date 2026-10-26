// Ротация ESCROW_MASTER_KEY: перешифровать keys.key_enc со старого master-ключа на новый.
//
// Всё или ничего: сначала каждая строка проверяется расшифровкой, и если хоть одна не открывается
// старым ключом, в БД не меняется ничего. Запись — одной транзакцией. Наружу уходят только
// счётчики: ни ключей, ни user_id в результате нет.
//
// Таблицу blobs скрипт не трогает: там ключ, запертый паролем на клиенте, master-ключ к нему
// отношения не имеет. codes и session_issues тоже от master-ключа не зависят.

import crypto from 'node:crypto';
import { encrypt, decrypt } from './crypto.js';

function opens(keyEnc, key) {
  try {
    decrypt(keyEnc, key);
    return true;
  } catch {
    return false;
  }
}

export class RotationError extends Error {
  constructor(message, counts) {
    super(message);
    this.name = 'RotationError';
    this.counts = counts;
  }
}

// db — handle better-sqlite3; oldKey/newKey — Buffer по 32 байта.
// Строки, которые уже открываются новым ключом (повторный запуск после успешной ротации),
// считаются отдельно: без allowAlreadyRotated это ошибка, с ним — такие строки пропускаются.
// Возвращает { total, rotated, alreadyRotated, dryRun }; rotated — сколько строк перешифровано
// (при dryRun — сколько было бы).
export function rotateMasterKey(db, { oldKey, newKey, dryRun = false, allowAlreadyRotated = false }) {
  for (const [name, key] of [['старый', oldKey], ['новый', newKey]]) {
    if (!Buffer.isBuffer(key) || key.length !== 32) throw new RotationError(`${name} ключ должен быть 32 байта`);
  }
  if (crypto.timingSafeEqual(oldKey, newKey)) throw new RotationError('старый и новый ключи совпадают');

  const run = db.transaction(() => {
    const rows = db.prepare('SELECT rowid AS id, key_enc FROM keys').all();
    const toRotate = [];
    let alreadyRotated = 0;
    let unreadable = 0;
    for (const row of rows) {
      if (opens(row.key_enc, oldKey)) toRotate.push(row);
      else if (opens(row.key_enc, newKey)) alreadyRotated++;
      else unreadable++;
    }
    const counts = { total: rows.length, rotated: toRotate.length, alreadyRotated, unreadable, dryRun };

    if (unreadable > 0) {
      throw new RotationError(`${unreadable} строк(и) не открываются ни старым, ни новым ключом — ничего не изменено`, counts);
    }
    if (alreadyRotated > 0 && !allowAlreadyRotated) {
      throw new RotationError(
        `${alreadyRotated} строк(и) уже на новом ключе — ничего не изменено (повторный запуск: --allow-already-rotated)`,
        counts
      );
    }
    if (dryRun) return counts;

    const update = db.prepare('UPDATE keys SET key_enc = ? WHERE rowid = ?');
    for (const row of toRotate) {
      const plain = decrypt(row.key_enc, oldKey);
      const enc = encrypt(plain, newKey);
      // Проверка до записи: перешифрованное должно открываться новым ключом в тот же текст.
      if (decrypt(enc, newKey) !== plain) throw new RotationError('проверка перешифровки не прошла — ничего не изменено', counts);
      update.run(enc, row.id);
    }
    return counts;
  });
  // IMMEDIATE: держим запись с самого чтения, чтобы между проверкой и UPDATE никто не вклинился.
  return run.immediate();
}
