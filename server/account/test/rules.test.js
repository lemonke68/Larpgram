import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizeUsername, usernameProblem, normalizeEmail, isValidEmail, passwordProblem, parseLogin,
  maskEmail, judgeCode, localpartOf, cleanDeviceName,
} from '../lib/rules.js';
import { createLimiter } from '../lib/limiter.js';

test('username: normalised to lower case without @', () => {
  assert.equal(normalizeUsername('  @Vasya_01 '), 'vasya_01');
  assert.equal(normalizeUsername(null), '');
});

test('username: Telegram-like rules', () => {
  assert.equal(usernameProblem('vasya'), null);
  assert.equal(usernameProblem('v_1'), null);
  assert.equal(usernameProblem('ab'), 'username_too_short');
  assert.equal(usernameProblem('a'.repeat(33)), 'username_too_long');
  assert.equal(usernameProblem('1vasya'), 'username_invalid');
  assert.equal(usernameProblem('vasya.p'), 'username_invalid');
  assert.equal(usernameProblem('вася'), 'username_invalid');
  assert.equal(usernameProblem('admin'), 'username_taken');
});

test('email', () => {
  assert.equal(normalizeEmail(' Vasya@Example.COM '), 'vasya@example.com');
  assert.equal(isValidEmail('vasya@example.com'), true);
  assert.equal(isValidEmail('vasya@example'), false);
  assert.equal(isValidEmail('va sya@example.com'), false);
  assert.equal(isValidEmail('a@b@example.com'), false);
  assert.equal(maskEmail('vasya@example.com'), 'v***@example.com');
});

test('password length', () => {
  assert.equal(passwordProblem('12345678'), null);
  assert.equal(passwordProblem('1234567'), 'password_too_short');
  assert.equal(passwordProblem('x'.repeat(129)), 'password_too_long');
  assert.equal(passwordProblem(undefined), 'password_too_short');
});

test('login field: email, nick, @nick and full Matrix ID', () => {
  assert.deepEqual(parseLogin('Vasya@Example.com'), { email: 'vasya@example.com' });
  assert.deepEqual(parseLogin('Vasya'), { username: 'vasya' });
  assert.deepEqual(parseLogin('@vasya'), { username: 'vasya' });
  assert.deepEqual(parseLogin('@vasya:mango-kokos.ru'), { username: 'vasya' });
  assert.deepEqual(parseLogin(''), { username: '' });
});

test('judgeCode', () => {
  const row = { expires_at: 100, attempts: 0 };
  assert.equal(judgeCode({ row, codeMatches: true, now: 50, maxAttempts: 5 }), 'ok');
  assert.equal(judgeCode({ row, codeMatches: false, now: 50, maxAttempts: 5 }), 'wrong');
  assert.equal(judgeCode({ row, codeMatches: true, now: 101, maxAttempts: 5 }), 'expired');
  assert.equal(judgeCode({ row: { ...row, attempts: 5 }, codeMatches: true, now: 50, maxAttempts: 5 }), 'too_many');
  assert.equal(judgeCode({ row: undefined, codeMatches: true, now: 50, maxAttempts: 5 }), 'expired');
});

test('limiter: N per window, then frees up', () => {
  let t = 0;
  const limiter = createLimiter({ now: () => t });
  assert.equal(limiter.allow('a', 2, 1000), true);
  assert.equal(limiter.allow('a', 2, 1000), true);
  assert.equal(limiter.allow('a', 2, 1000), false);
  assert.equal(limiter.allow('b', 2, 1000), true);
  t = 1000;
  assert.equal(limiter.allow('a', 2, 1000), true);
  t = 5000;
  limiter.sweep(1000);
  assert.equal(limiter.size(), 0);
});

test('localpartOf and cleanDeviceName', () => {
  assert.equal(localpartOf('@vasya:mango-kokos.ru'), 'vasya');
  assert.equal(localpartOf('vasya'), null);
  assert.equal(localpartOf('@:server'), null);
  assert.equal(cleanDeviceName('  Honor\n10 '), 'Honor10');
  assert.equal(cleanDeviceName(undefined), 'Larpgram');
  assert.equal(cleanDeviceName('x'.repeat(100)).length, 64);
});
