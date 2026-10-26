// Ротация master-ключа: lib/rotate.js напрямую и scripts/rotate-master-key.js целиком.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const b64 = (n) => crypto.randomBytes(n).toString('base64');
const OLD = b64(32);
const NEW = b64(32);
const OLD_KEY = Buffer.from(OLD, 'base64');
const NEW_KEY = Buffer.from(NEW, 'base64');
process.env.ESCROW_MASTER_KEY = OLD;
process.env.CODE_PEPPER = 'test-pepper';

const { createApp } = await import('../lib/app.js');
const { openDb } = await import('../lib/db.js');
const { encrypt, decrypt, parseMasterKey } = await import('../lib/crypto.js');
const { rotateMasterKey, RotationError } = await import('../lib/rotate.js');

const SCRIPT = fileURLToPath(new URL('../scripts/rotate-master-key.js', import.meta.url));
const KEY_A = 'EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT';
const KEY_B = 'EsTb 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupX';
const BLOB = JSON.stringify({ v: 1, kdf: 'argon2id', m: 65536, t: 3, p: 1, salt: b64(16), nonce: b64(12), ct: b64(64) });

function seed(db) {
  db.putOptInKey.run('@alice:example.org', encrypt(KEY_A, OLD_KEY), 1);
  db.putOptInKey.run('@bob:example.org', encrypt(KEY_B, OLD_KEY), 2);
  db.putBlob.run('@alice:example.org', BLOB, 'DEV1', 3);
}

const snapshot = (db) => ({
  keys: db.raw.prepare('SELECT * FROM keys ORDER BY user_id').all(),
  blobs: db.raw.prepare('SELECT * FROM blobs ORDER BY user_id').all(),
});

function plainKeys(db, key) {
  return db.raw.prepare('SELECT user_id, key_enc FROM keys ORDER BY user_id').all()
    .map((r) => [r.user_id, decrypt(r.key_enc, key)]);
}

test('crypto: explicit key param and parseMasterKey', () => {
  const enc = encrypt('hello', NEW_KEY);
  assert.equal(decrypt(enc, NEW_KEY), 'hello');
  assert.throws(() => decrypt(enc, OLD_KEY));
  assert.equal(decrypt(encrypt('env')), 'env'); // по умолчанию — ESCROW_MASTER_KEY
  assert.ok(parseMasterKey(OLD).equals(OLD_KEY));
  assert.throws(() => parseMasterKey(''), /не задан/);
  assert.throws(() => parseMasterKey(b64(16)), /32 байта/);
  assert.throws(() => parseMasterKey('not base64 !!'), /base64/);
});

test('happy path: every key re-encrypted, blobs untouched', () => {
  const db = openDb(':memory:');
  seed(db);
  const before = snapshot(db);
  const c = rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY });
  assert.deepEqual(c, { total: 2, rotated: 2, alreadyRotated: 0, unreadable: 0, dryRun: false });
  assert.deepEqual(plainKeys(db, NEW_KEY), [['@alice:example.org', KEY_A], ['@bob:example.org', KEY_B]]);
  const after = snapshot(db);
  assert.deepEqual(after.blobs, before.blobs);
  // Меняется только key_enc: opt_in и created_at на месте.
  assert.deepEqual(after.keys.map(({ key_enc, ...r }) => r), before.keys.map(({ key_enc, ...r }) => r));
  for (const r of after.keys) assert.throws(() => decrypt(r.key_enc, OLD_KEY));
  db.close();
});

test('dry run changes nothing', () => {
  const db = openDb(':memory:');
  seed(db);
  const before = snapshot(db);
  const c = rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY, dryRun: true });
  assert.equal(c.rotated, 2);
  assert.equal(c.dryRun, true);
  assert.deepEqual(snapshot(db), before);
  db.close();
});

test('a row under an unknown key aborts the whole rotation', () => {
  const db = openDb(':memory:');
  seed(db);
  db.putOptInKey.run('@eve:example.org', encrypt(KEY_A, crypto.randomBytes(32)), 4);
  db.putKey.run('@mallory:example.org', 'not-a-ciphertext', 5);
  const before = snapshot(db);
  assert.throws(
    () => rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY }),
    (e) => e instanceof RotationError && e.counts.unreadable === 2 && e.counts.rotated === 2
  );
  assert.deepEqual(snapshot(db), before);
  db.close();
});

test('rows already under the new key are refused unless allowed', () => {
  const db = openDb(':memory:');
  seed(db);
  db.putOptInKey.run('@carol:example.org', encrypt(KEY_A, NEW_KEY), 4);
  const before = snapshot(db);
  assert.throws(
    () => rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY }),
    (e) => e instanceof RotationError && e.counts.alreadyRotated === 1 && /allow-already-rotated/.test(e.message)
  );
  assert.deepEqual(snapshot(db), before);

  const c = rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY, allowAlreadyRotated: true });
  assert.deepEqual([c.rotated, c.alreadyRotated], [2, 1]);
  assert.equal(plainKeys(db, NEW_KEY).length, 3);

  // Повторный запуск после успешной ротации: всё уже на новом ключе, перешифровывать нечего.
  const again = rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY, allowAlreadyRotated: true });
  assert.deepEqual([again.rotated, again.alreadyRotated], [0, 3]);
  db.close();
});

test('equal or malformed keys are refused', () => {
  const db = openDb(':memory:');
  assert.throws(() => rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: Buffer.from(OLD_KEY) }), /совпадают/);
  assert.throws(() => rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: Buffer.alloc(16) }), /32 байта/);
  db.close();
});

test('the app serves the key with the new master key after rotation', async () => {
  const db = openDb(':memory:');
  const matrix = {
    async whoami(token) {
      const m = /^token-of-(\w+)-(\w+)$/.exec(token);
      return m ? { userId: `@${m[1]}:example.org`, deviceId: m[2] } : null;
    },
    hasAdminToken: () => true,
    async getDeviceName() { return 'Phone'; },
    async sendServerNotice() { return true; },
  };
  const { app } = createApp({ db, matrix, mailer: { async sendCode() {} }, now: () => 10 * 86_400_000 });
  const server = await new Promise((resolve) => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = (method, p, token, body) => fetch(base + p, {
    method,
    headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  try {
    assert.equal((await call('PUT', '/v2/server-key', 'token-of-alice-DEV1', { recovery_key: KEY_A })).status, 204);
    rotateMasterKey(db.raw, { oldKey: OLD_KEY, newKey: NEW_KEY });
    process.env.ESCROW_MASTER_KEY = NEW;
    const res = await call('GET', '/key/session', 'token-of-alice-DEV2');
    assert.equal(res.status, 200);
    assert.equal((await res.json()).recovery_key, KEY_A);
    // Со старым ключом сервис эту строку уже не откроет.
    assert.throws(() => decrypt(db.getKey.get('@alice:example.org').key_enc, OLD_KEY));
  } finally {
    process.env.ESCROW_MASTER_KEY = OLD;
    await new Promise((resolve) => server.close(resolve));
    db.close();
  }
});

// --- скрипт целиком, на файле ---

function withDbFile(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'escrow-rotate-'));
  const file = path.join(dir, 'escrow.db');
  try {
    const db = openDb(file);
    seed(db);
    db.close();
    return fn(file);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

function runScript(env, args = []) {
  const r = spawnSync(process.execPath, [SCRIPT, ...args], {
    env: { PATH: process.env.PATH, ...env },
    encoding: 'utf8',
  });
  return { code: r.status, out: r.stdout + r.stderr };
}

test('script: dry run, rotate, rerun refused, rerun allowed; prints no key material', () => {
  withDbFile((file) => {
    const env = { OLD_ESCROW_MASTER_KEY: OLD, NEW_ESCROW_MASTER_KEY: NEW, DB_PATH: file };
    const outs = [];
    const check = (r, code, re) => { outs.push(r.out); assert.equal(r.code, code, r.out); assert.match(r.out, re); };

    check(runScript(env, ['--dry-run']), 0, /dry-run.*всего строк 2, перешифровано 2/);
    let db = openDb(file);
    assert.equal(plainKeys(db, OLD_KEY).length, 2);
    db.close();

    check(runScript(env), 0, /записано; всего строк 2, перешифровано 2, уже на новом ключе 0/);
    check(runScript(env), 1, /уже на новом ключе/);
    check(runScript(env, ['--allow-already-rotated']), 0, /перешифровано 0, уже на новом ключе 2/);

    db = openDb(file);
    assert.deepEqual(plainKeys(db, NEW_KEY).map((r) => r[1]), [KEY_A, KEY_B]);
    db.close();

    for (const out of outs) {
      for (const secret of [OLD, NEW, KEY_A, KEY_B, '@alice', '@bob']) assert.ok(!out.includes(secret), out);
    }
  });
});

test('script: wrong old key exits nonzero and changes nothing', () => {
  withDbFile((file) => {
    const before = fs.readFileSync(file);
    const r = runScript({ OLD_ESCROW_MASTER_KEY: b64(32), NEW_ESCROW_MASTER_KEY: NEW, DB_PATH: file });
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /не открываются ни старым, ни новым ключом/);
    const db = openDb(file);
    assert.equal(plainKeys(db, OLD_KEY).length, 2);
    db.close();
    assert.ok(before.length > 0);
  });
});

test('script: bad arguments and environment are refused', () => {
  withDbFile((file) => {
    assert.equal(runScript({ OLD_ESCROW_MASTER_KEY: OLD, NEW_ESCROW_MASTER_KEY: OLD, DB_PATH: file }).code, 2);
    assert.equal(runScript({ OLD_ESCROW_MASTER_KEY: OLD, NEW_ESCROW_MASTER_KEY: 'short', DB_PATH: file }).code, 2);
    assert.equal(runScript({ NEW_ESCROW_MASTER_KEY: NEW, DB_PATH: file }).code, 2);
    assert.equal(runScript({ OLD_ESCROW_MASTER_KEY: OLD, NEW_ESCROW_MASTER_KEY: NEW, DB_PATH: file }, ['--force']).code, 2);
    const missing = runScript({ OLD_ESCROW_MASTER_KEY: OLD, NEW_ESCROW_MASTER_KEY: NEW, DB_PATH: file + '.nope' });
    assert.equal(missing.code, 1);
    assert.ok(!fs.existsSync(file + '.nope'));
  });
});
