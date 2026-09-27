import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isValidRecoveryKey, domainOf, deleteForBothRefusal } from '../lib/rules.js';

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
