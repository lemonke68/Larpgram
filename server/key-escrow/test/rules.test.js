import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isValidRecoveryKey, domainOf, deleteForBothRefusal, sessionKeyIssue, keyIssuedNotice, serverKeyServed, blobProblem } from '../lib/rules.js';

const KEY = 'EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT';

test('recovery key: 48 base58 characters, spaces allowed', () => {
  assert.equal(isValidRecoveryKey(KEY), true);
  assert.equal(isValidRecoveryKey(KEY.replace(/ /g, '')), true);
  assert.equal(isValidRecoveryKey('hello'), false);
  assert.equal(isValidRecoveryKey(KEY.replace('E', '0')), false); // 0 not in base58
  assert.equal(isValidRecoveryKey(`${KEY}x`), false);
  assert.equal(isValidRecoveryKey(null), false);
});

test('domainOf', () => {
  assert.equal(domainOf('@a:mango-kokos.ru'), 'mango-kokos.ru');
  assert.equal(domainOf('nope'), null);
});

const ROOM = '!dm:mango-kokos.ru';
const A = '@a:mango-kokos.ru';
const B = '@b:mango-kokos.ru';
const dm = { roomId: ROOM, name: null, canonicalAlias: null, eventsDefault: 0 };
const bothDirect = { [A]: { [B]: [ROOM] }, [B]: { [A]: [ROOM] } };

test('a real local DM can be deleted for both', () => {
  assert.equal(deleteForBothRefusal({ callerId: A, members: [A, B], room: dm, directOf: bothDirect }), null);
});

test('a DM where only the caller is left can be deleted', () => {
  assert.equal(deleteForBothRefusal({ callerId: A, members: [A], room: dm, directOf: bothDirect }), null);
});

test('refusals', () => {
  const base = { callerId: A, members: [A, B], room: dm, directOf: bothDirect };
  assert.equal(deleteForBothRefusal({ ...base, callerId: '@c:mango-kokos.ru' }), 'not-a-member');
  assert.equal(deleteForBothRefusal({ ...base, members: [A, B, '@c:mango-kokos.ru'] }), 'not-two-people');
  assert.equal(deleteForBothRefusal({ ...base, members: [A, '@b:matrix.org'] }), 'remote-member');
  assert.equal(deleteForBothRefusal({ ...base, room: { ...dm, name: 'Group' } }), 'named-room');
  assert.equal(deleteForBothRefusal({ ...base, room: { ...dm, canonicalAlias: '#x:mango-kokos.ru' } }), 'room-with-alias');
  assert.equal(deleteForBothRefusal({ ...base, room: { ...dm, eventsDefault: 50 } }), 'channel');
  // Only the caller put the room into m.direct: that is what a spoofing client would do.
  assert.equal(deleteForBothRefusal({ ...base, directOf: { [A]: bothDirect[A], [B]: {} } }), 'not-direct');
  assert.equal(deleteForBothRefusal({ ...base, directOf: { [A]: bothDirect[A] } }), 'not-direct');
});

test('session key: first issue, retry inside the window, refused after it', () => {
  const windowMs = 15 * 60 * 1000;
  assert.equal(sessionKeyIssue({ issuedAt: null, now: 1000, windowMs }), 'first');
  assert.equal(sessionKeyIssue({ issuedAt: 1000, now: 1000 + windowMs, windowMs }), 'again');
  assert.equal(sessionKeyIssue({ issuedAt: 1000, now: 1001 + windowMs, windowMs }), 'expired');
});

test('key issued notice names the device and the way', () => {
  const text = keyIssuedNotice({ deviceId: 'ABCDEF', deviceName: 'Honor', via: 'session' });
  assert.match(text, /«Honor» \(ABCDEF\)/);
  assert.match(text, /при входе/);
  assert.match(keyIssuedNotice({ deviceId: 'ABCDEF', deviceName: null, via: 'code' }), /ABCDEF.*по коду с почты/);
});

test('server key is served with the toggle, or without it only until the deadline', () => {
  assert.equal(serverKeyServed({ optIn: true, now: 5, legacyUntil: 1 }), true);
  assert.equal(serverKeyServed({ optIn: false, now: 5, legacyUntil: null }), true);
  assert.equal(serverKeyServed({ optIn: false, now: 5, legacyUntil: 5 }), true);
  assert.equal(serverKeyServed({ optIn: false, now: 6, legacyUntil: 5 }), false);
});

test('blob: ciphertext bounds', () => {
  const b64 = (n) => Buffer.alloc(n, 7).toString('base64');
  const blob = { v: 1, kdf: 'argon2id', m: 65536, t: 3, p: 1, salt: b64(16), nonce: b64(12), ct: b64(64) };
  assert.equal(blobProblem(blob), null);
  assert.equal(blobProblem({ ...blob, ct: b64(16) }), 'ciphertext');
  assert.equal(blobProblem({ ...blob, ct: b64(513) }), 'ciphertext');
  assert.equal(blobProblem({ ...blob, v: 2 }), 'version');
  assert.equal(blobProblem({ ...blob, m: 65536.5 }), 'memory');
  assert.equal(blobProblem(null), 'not-an-object');
});
