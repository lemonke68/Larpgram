// Приложение целиком: настоящие маршруты и SQLite в памяти, вместо Synapse и почты — заглушки.

import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';

process.env.ESCROW_MASTER_KEY = crypto.randomBytes(32).toString('base64');
process.env.CODE_PEPPER = 'test-pepper';

const { createApp } = await import('../lib/app.js');
const { openDb } = await import('../lib/db.js');

const KEY = 'EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT';
const DAY = 24 * 60 * 60 * 1000;

const b64 = (n) => crypto.randomBytes(n).toString('base64');
const BLOB = { v: 1, kdf: 'argon2id', m: 65536, t: 3, p: 1, salt: b64(16), nonce: b64(12), ct: b64(64) };

let server, base, clock, db, notices;

async function start(config = {}) {
  clock = { t: 10 * DAY };
  notices = [];
  db = openDb(':memory:');
  // Токен вида «token-of-<ник>-<устройство>» принадлежит @<ник>:example.org, прочие не годятся.
  const matrix = {
    async whoami(token) {
      const m = /^token-of-(\w+)-(\w+)$/.exec(token);
      return m ? { userId: `@${m[1]}:example.org`, deviceId: m[2] } : null;
    },
    hasAdminToken: () => true,
    async getDeviceName() { return 'Phone'; },
    async sendServerNotice(userId, text) { notices.push({ userId, text }); return true; },
  };
  const mailer = { async sendCode() {} };
  const { app, sweep } = createApp({ db, matrix, mailer, now: () => clock.t, config });
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve); });
  base = `http://127.0.0.1:${server.address().port}`;
  return sweep;
}

beforeEach(() => start());
afterEach(async () => {
  await new Promise((resolve) => server.close(resolve));
  db.close();
});

async function call(method, path, token, body) {
  const res = await fetch(base + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const json = (res.headers.get('content-type') || '').includes('json');
  return { status: res.status, body: json && text ? JSON.parse(text) : null };
}

const A1 = 'token-of-alice-DEV1';
const A2 = 'token-of-alice-DEV2';
const B1 = 'token-of-bob-DEV1';

test('no token or a bad token is 401', async () => {
  assert.equal((await call('GET', '/v2/state')).status, 401);
  assert.equal((await call('GET', '/v2/state', 'nope')).status, 401);
});

test('blob: put, read from another device, not visible to another account', async () => {
  assert.deepEqual((await call('GET', '/v2/state', A1)).body, { blob: false, server_key: false, server_opt_in: false });
  assert.equal((await call('GET', '/v2/blob', A1)).status, 404);

  assert.equal((await call('PUT', '/v2/blob', A1, { blob: BLOB })).status, 204);
  const got = await call('GET', '/v2/blob', A2);
  assert.equal(got.status, 200);
  assert.deepEqual(got.body, { blob: BLOB, updated_at: clock.t });
  assert.equal((await call('GET', '/v2/blob', B1)).status, 404);
  assert.equal((await call('GET', '/v2/state', A2)).body.blob, true);

  assert.equal((await call('DELETE', '/v2/blob', A2)).status, 204);
  assert.equal((await call('GET', '/v2/blob', A1)).status, 404);
});

test('blob: malformed or weak blobs are rejected', async () => {
  const put = (blob) => call('PUT', '/v2/blob', A1, { blob });
  assert.deepEqual((await put({ ...BLOB, m: 1024 })).body, { error: 'memory' });
  assert.deepEqual((await put({ ...BLOB, t: 1 })).body, { error: 'iterations' });
  assert.deepEqual((await put({ ...BLOB, kdf: 'pbkdf2' })).body, { error: 'kdf' });
  assert.deepEqual((await put({ ...BLOB, nonce: b64(16) })).body, { error: 'nonce' });
  assert.deepEqual((await put({ ...BLOB, salt: 'not base64!' })).body, { error: 'salt' });
  assert.deepEqual((await put({ ...BLOB, extra: 1 })).body, { error: 'unknown-field' });
  assert.deepEqual((await put('string')).body, { error: 'not-an-object' });
  assert.equal((await call('GET', '/v2/blob', A1)).status, 404);
});

test('server key toggle: on serves /key/session, off removes the key', async () => {
  assert.equal((await call('PUT', '/v2/server-key', A1, { recovery_key: 'short' })).status, 400);
  assert.equal((await call('PUT', '/v2/server-key', A1, { recovery_key: KEY })).status, 204);
  assert.deepEqual((await call('GET', '/v2/state', A1)).body, { blob: false, server_key: true, server_opt_in: true });

  const issued = await call('GET', '/key/session', A2);
  assert.equal(issued.status, 200);
  assert.equal(issued.body.recovery_key, KEY);
  assert.equal(notices.length, 1);

  assert.equal((await call('DELETE', '/v2/server-key', A1)).status, 204);
  assert.equal((await call('GET', '/key/session', A2)).status, 404);
  assert.deepEqual((await call('GET', '/v2/state', A1)).body, { blob: false, server_key: false, server_opt_in: false });
});

test('an old client sees a stored key once the account has a blob', async () => {
  assert.equal((await call('GET', '/key', A1)).status, 404);
  await call('PUT', '/v2/blob', A2, { blob: BLOB });
  assert.equal((await call('GET', '/key', A1)).status, 200);
  assert.equal((await call('GET', '/key/session', A1)).status, 404);
});

test('a v1 PUT /key does not switch the toggle off', async () => {
  await call('PUT', '/v2/server-key', A1, { recovery_key: KEY });
  await call('PUT', '/key', A1, { recovery_key: KEY });
  assert.equal((await call('GET', '/v2/state', A1)).body.server_opt_in, true);
});

test('legacy v1 keys are served until the deadline, then refused and swept', async () => {
  await new Promise((resolve) => server.close(resolve));
  db.close();
  const sweep = await start({ legacyKeysUntil: 10 * DAY + DAY });

  await call('PUT', '/key', A1, { recovery_key: KEY }); // старый клиент
  await call('PUT', '/v2/server-key', B1, { recovery_key: KEY }); // тумблер
  assert.equal((await call('GET', '/key/session', A2)).status, 200);
  assert.equal((await call('GET', '/key', A1)).status, 200);

  clock.t += 2 * DAY;
  assert.equal((await call('GET', '/key/session', 'token-of-alice-DEV3')).status, 404);
  // Старый клиент не должен увидеть «ключа нет» и перевыпустить ключ восстановления.
  assert.equal((await call('GET', '/key', A1)).status, 200);
  assert.equal((await call('PUT', '/key', A1, { recovery_key: KEY })).status, 410);
  assert.equal((await call('GET', '/key/session', 'token-of-bob-DEV2')).status, 200);

  sweep();
  assert.deepEqual((await call('GET', '/v2/state', A1)).body, { blob: false, server_key: false, server_opt_in: false });
  assert.equal((await call('GET', '/v2/state', B1)).body.server_key, true);
});

test('session key is issued once per device with a retry window', async () => {
  await call('PUT', '/v2/server-key', A1, { recovery_key: KEY });
  assert.equal((await call('GET', '/key/session', A2)).status, 200);
  clock.t += 60_000;
  assert.equal((await call('GET', '/key/session', A2)).status, 200);
  clock.t += 900_000;
  assert.equal((await call('GET', '/key/session', A2)).status, 403);
  assert.equal(notices.length, 1);
});
