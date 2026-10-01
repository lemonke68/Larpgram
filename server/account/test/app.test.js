// Приложение целиком: настоящие маршруты и SQLite в памяти, вместо MAS и почты — заглушки.

import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

process.env.CODE_PEPPER = 'test-pepper';

const { createApp } = await import('../lib/app.js');
const { openDb } = await import('../lib/db.js');

function fakeMas() {
  const users = new Map(); // username -> { id, username, password, email }
  let next = 1;
  const byId = (id) => [...users.values()].find((u) => u.id === id) || null;
  return {
    users,
    weakPasswords: new Set(['password']),
    async findUser(username) { return users.get(username) || null; },
    async getUser(id) { return byId(id); },
    async createUser(username) {
      if (users.has(username)) return null;
      const user = { id: `U${next++}`, username, password: null, email: null };
      users.set(username, user);
      return user;
    },
    async setPassword(id, password) {
      if (this.weakPasswords.has(password)) return false;
      byId(id).password = password;
      return true;
    },
    async findUserIdByEmail(email) {
      return [...users.values()].find((u) => u.email === email)?.id || null;
    },
    async findEmailOfUser(id) { return byId(id)?.email || null; },
    ended: [],
    async removeEmails(id) { byId(id).email = null; },
    async endDeviceSession(userId, deviceId) {
      if (deviceId === 'GHOST') return false;
      this.ended.push({ userId, deviceId });
      return true;
    },
    sessions: [],
    async createDeviceSession(userId, deviceId, humanName) {
      this.sessions.push({ userId, deviceId, humanName });
      return `mpt_${deviceId}`;
    },
    async addEmail(id, email) {
      if ([...users.values()].some((u) => u.email === email)) return false;
      byId(id).email = email;
      return true;
    },
  };
}

let server, base, mas, sent, clock, db, matrix;

async function start(config = {}) {
  mas = fakeMas();
  sent = [];
  clock = { t: 1_000_000 };
  db = openDb(':memory:');
  const mailer = { async sendCode(kind, to, code) { sent.push({ kind, to, code }); } };
  // Токен вида «token-of-<ник>» принадлежит @<ник>:example.org, прочие не годятся.
  matrix = {
    homeserverUrl: 'https://matrix.example.org',
    named: [],
    async setDeviceName(token, deviceId, name) { this.named.push({ token, deviceId, name }); return true; },
    async whoami(token) {
      const m = /^token-of-(\w+)$/.exec(token);
      return m ? { userId: `@${m[1]}:example.org`, deviceId: 'OLD' } : null;
    },
  };
  const { app } = createApp({ mas, mailer, db, matrix, now: () => clock.t, config });
  await new Promise((resolve) => { server = app.listen(0, '127.0.0.1', resolve); });
  base = `http://127.0.0.1:${server.address().port}`;
}

beforeEach(() => start());
afterEach(async () => {
  await new Promise((resolve) => server.close(resolve));
  db.close();
});

async function post(path, body, token) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

const lastCode = () => sent.at(-1).code;
const wrongCode = () => (lastCode() === '000000' ? '111111' : '000000');

test('register: code by email, then account with email and password', async () => {
  const start = await post('/register/start', { username: '@Vasya', email: 'Vasya@Example.com' });
  assert.equal(start.status, 200);
  assert.equal(start.body.resend_after, 60);
  assert.deepEqual(sent.map((m) => [m.kind, m.to]), [['register', 'vasya@example.com']]);
  assert.equal(mas.users.size, 0); // до кода аккаунта нет

  const done = await post('/register/confirm', { ticket: start.body.ticket, code: lastCode(), password: 'correct horse' });
  assert.equal(done.status, 200);
  assert.deepEqual(done.body, { username: 'vasya' });
  assert.deepEqual({ ...mas.users.get('vasya'), id: undefined }, {
    id: undefined, username: 'vasya', password: 'correct horse', email: 'vasya@example.com',
  });

  // Заявка одноразовая.
  const again = await post('/register/confirm', { ticket: start.body.ticket, code: lastCode(), password: 'correct horse' });
  assert.equal(again.status, 410);
});

test('register: bad input and taken names', async () => {
  assert.deepEqual((await post('/register/start', { username: 'ab', email: 'a@b.ru' })).body, { error: 'username_too_short' });
  assert.deepEqual((await post('/register/start', { username: 'vasya', email: 'nope' })).body, { error: 'email_invalid' });
  assert.equal((await post('/register/start', { username: 'admin', email: 'a@b.ru' })).status, 409);

  const user = await mas.createUser('petya');
  await mas.addEmail(user.id, 'petya@example.com');
  assert.deepEqual((await post('/register/start', { username: 'petya', email: 'new@example.com' })).body, { error: 'username_taken' });
  assert.deepEqual((await post('/register/start', { username: 'vasya', email: 'petya@example.com' })).body, { error: 'email_taken' });
  assert.equal(sent.length, 0);
});

test('register: wrong code counts attempts, fifth miss kills the ticket', async () => {
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  for (let left = 4; left >= 1; left--) {
    const res = await post('/register/confirm', { ticket: body.ticket, code: wrongCode(), password: 'correct horse' });
    assert.equal(res.status, 400);
    assert.deepEqual(res.body, { error: 'wrong_code', attempts_left: left });
  }
  assert.equal((await post('/register/confirm', { ticket: body.ticket, code: wrongCode(), password: 'correct horse' })).status, 429);
  assert.equal((await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'correct horse' })).status, 410);
  assert.equal(mas.users.size, 0);
});

test('register: expired code', async () => {
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  clock.t += 601_000;
  const res = await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'correct horse' });
  assert.equal(res.status, 410);
  assert.deepEqual(res.body, { error: 'code_expired' });
});

test('register: weak password can be fixed without a new code', async () => {
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  assert.deepEqual((await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'short' })).body, { error: 'password_too_short' });

  const weak = await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'password' });
  assert.deepEqual(weak, { status: 400, body: { error: 'password_weak' } });

  // Код уже принят: второй раз его не спрашиваем.
  const done = await post('/register/confirm', { ticket: body.ticket, code: '', password: 'correct horse' });
  assert.equal(done.status, 200);
  assert.equal(mas.users.get('vasya').password, 'correct horse');
  assert.equal(mas.users.size, 1);
});

test('register: nick taken while the code was on its way', async () => {
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  await mas.createUser('vasya');
  const res = await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'correct horse' });
  assert.deepEqual(res, { status: 409, body: { error: 'username_taken' } });
});

test('resend: not sooner than a minute, new code replaces the old one', async () => {
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  const first = lastCode();
  const early = await post('/code/resend', { ticket: body.ticket });
  assert.equal(early.status, 429);
  assert.equal(early.body.error, 'too_soon');

  clock.t += 61_000;
  assert.equal((await post('/code/resend', { ticket: body.ticket })).status, 200);
  assert.equal(sent.length, 2);
  if (first !== lastCode()) {
    assert.equal((await post('/register/confirm', { ticket: body.ticket, code: first, password: 'correct horse' })).status, 400);
  }
  assert.equal((await post('/register/confirm', { ticket: body.ticket, code: lastCode(), password: 'correct horse' })).status, 200);
});

async function existingUser() {
  const user = await mas.createUser('petya');
  await mas.addEmail(user.id, 'petya@example.com');
  await mas.setPassword(user.id, 'old password');
  return user;
}

test('reset: by nick and by email', async () => {
  await existingUser();
  for (const login of ['Petya', 'petya@example.com']) {
    const start = await post('/password/forgot', { login });
    assert.equal(start.status, 200);
    assert.deepEqual(sent.at(-1), { kind: 'reset', to: 'petya@example.com', code: lastCode() });

    assert.equal((await post('/code/check', { ticket: start.body.ticket, code: lastCode() })).status, 204);
    const password = `new password ${login.length}`;
    const done = await post('/password/reset', { ticket: start.body.ticket, code: lastCode(), password });
    assert.deepEqual(done, { status: 200, body: { username: 'petya' } });
    assert.equal(mas.users.get('petya').password, password);
  }
});

test('reset: same answer for unknown account and account without email', async () => {
  await mas.createUser('noemail');
  for (const login of ['ghost', 'ghost@example.com', 'noemail']) {
    const start = await post('/password/forgot', { login });
    assert.equal(start.status, 200);
    assert.deepEqual(Object.keys(start.body), ['ticket', 'resend_after']);
    const res = await post('/password/reset', { ticket: start.body.ticket, code: '123456', password: 'correct horse' });
    assert.equal(res.status, 400);
    assert.equal(res.body.error, 'wrong_code');
  }
  assert.equal(sent.length, 0);
});

test('reset: a register ticket does not work for reset', async () => {
  await existingUser();
  const { body } = await post('/register/start', { username: 'vasya', email: 'vasya@example.com' });
  const res = await post('/password/reset', { ticket: body.ticket, code: lastCode(), password: 'correct horse' });
  assert.equal(res.status, 410);
});

test('limits: starts per IP and mails per address', async () => {
  await new Promise((resolve) => server.close(resolve));
  db.close();
  await start({ startsPerIpHour: 3, mailsPerAddressHour: 2 });

  await existingUser();
  assert.equal((await post('/password/forgot', { login: 'petya' })).status, 200);
  assert.equal((await post('/password/forgot', { login: 'petya' })).status, 200);
  assert.equal((await post('/password/forgot', { login: 'petya' })).status, 429); // адрес
  assert.equal((await post('/password/forgot', { login: 'other' })).status, 429); // IP
  clock.t += 3_600_000;
  assert.equal((await post('/password/forgot', { login: 'other' })).status, 200);
});

test('pair: signed-in device offers a code, new device redeems it once', async () => {
  await mas.createUser('petya');
  assert.equal((await post('/pair/offer', {})).status, 401);
  assert.equal((await post('/pair/offer', {}, 'garbage')).status, 401);

  const offer = await post('/pair/offer', {}, 'token-of-petya');
  assert.equal(offer.status, 200);
  assert.equal(offer.body.expires_in, 120);
  assert.deepEqual((await post('/pair/status', { code: offer.body.code }, 'token-of-petya')).body, { state: 'waiting' });
  // Чужой аккаунт про этот код ничего не узнает.
  assert.equal((await post('/pair/status', { code: offer.body.code }, 'token-of-vasya')).status, 404);

  const session = await post('/pair/redeem', { code: offer.body.code, device_name: 'Honor' });
  assert.equal(session.status, 200);
  assert.equal(session.body.user_id, '@petya:example.org');
  assert.match(session.body.device_id, /^[A-Z]{10}$/);
  assert.equal(session.body.access_token, `mpt_${session.body.device_id}`);
  assert.equal(session.body.homeserver_url, 'https://matrix.example.org');
  assert.deepEqual(mas.sessions, [{ userId: 'U1', deviceId: session.body.device_id, humanName: 'Honor' }]);
  assert.deepEqual(matrix.named, [{ token: session.body.access_token, deviceId: session.body.device_id, name: 'Honor' }]);

  assert.deepEqual((await post('/pair/status', { code: offer.body.code }, 'token-of-petya')).body, { state: 'redeemed' });
  assert.equal((await post('/pair/redeem', { code: offer.body.code })).status, 410);
  assert.equal(mas.sessions.length, 1);
});

test('pair: expired and unknown codes give nothing', async () => {
  await mas.createUser('petya');
  const offer = await post('/pair/offer', {}, 'token-of-petya');
  clock.t += 121_000;
  assert.deepEqual((await post('/pair/status', { code: offer.body.code }, 'token-of-petya')).body, { state: 'expired' });
  assert.deepEqual(await post('/pair/redeem', { code: offer.body.code }), { status: 410, body: { error: 'code_expired' } });
  assert.equal((await post('/pair/redeem', { code: 'nope' })).status, 410);
  assert.equal((await post('/pair/redeem', {})).status, 410);
  assert.equal(mas.sessions.length, 0);
});

test('email: signed-in user binds a new address with a code, no password asked', async () => {
  const petya = await mas.createUser('petya');
  await mas.addEmail(petya.id, 'old@example.com');
  const vasya = await mas.createUser('vasya');
  await mas.addEmail(vasya.id, 'vasya@example.com');

  assert.equal((await post('/email/start', { email: 'new@example.com' })).status, 401);
  assert.deepEqual((await post('/email/start', { email: 'nope' }, 'token-of-petya')).body, { error: 'email_invalid' });
  assert.deepEqual((await post('/email/start', { email: 'vasya@example.com' }, 'token-of-petya')).body, { error: 'email_taken' });

  const start = await post('/email/start', { email: ' New@Example.com ' }, 'token-of-petya');
  assert.equal(start.status, 200);
  assert.deepEqual(sent.at(-1), { kind: 'email', to: 'new@example.com', code: lastCode() });

  // С чужим токеном заявка не работает даже с верным кодом.
  assert.equal((await post('/email/confirm', { ticket: start.body.ticket, code: lastCode() }, 'token-of-vasya')).status, 410);
  assert.equal((await post('/email/confirm', { ticket: start.body.ticket, code: wrongCode() }, 'token-of-petya')).status, 400);

  const done = await post('/email/confirm', { ticket: start.body.ticket, code: lastCode() }, 'token-of-petya');
  assert.deepEqual(done, { status: 200, body: { email: 'new@example.com' } });
  assert.equal(mas.users.get('petya').email, 'new@example.com');
});

test('sessions: end another device of the same account', async () => {
  const petya = await mas.createUser('petya');
  assert.equal((await post('/sessions/end', { device_id: 'ABCDEFGHIJ' })).status, 401);
  assert.equal((await post('/sessions/end', { device_id: 'bad id!' }, 'token-of-petya')).status, 400);
  assert.equal((await post('/sessions/end', { device_id: 'GHOST' }, 'token-of-petya')).status, 404);
  assert.equal((await post('/sessions/end', { device_id: 'ABCDEFGHIJ' }, 'token-of-petya')).status, 204);
  assert.deepEqual(mas.ended, [{ userId: petya.id, deviceId: 'ABCDEFGHIJ' }]);
});

test('login by email: resolves the nick, unknown address is 404', async () => {
  await existingUser();
  assert.deepEqual(await post('/login/resolve', { email: ' Petya@Example.com ' }), { status: 200, body: { username: 'petya' } });
  assert.equal((await post('/login/resolve', { email: 'ghost@example.com' })).status, 404);
  assert.equal((await post('/login/resolve', { email: 'nope' })).status, 404);
});
