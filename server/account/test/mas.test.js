import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createMas } from '../lib/mas.js';

test('email cleanup deletes old addresses and never deletes the new address', async () => {
  const deleted = [];
  const mas = createMas({
    baseUrl: 'http://mas.test', clientId: 'test', clientSecret: 'test',
    fetchImpl: async (url, options) => {
      const path = new URL(url).pathname;
      if (path.endsWith('/oauth2/token')) return Response.json({ access_token: 'test', expires_in: 300 });
      if (options.method === 'GET') return Response.json({ data: [
        { id: 'OLD', attributes: { email: 'old@example.com' } },
        { id: 'NEW', attributes: { email: 'new@example.com' } },
      ] });
      assert.equal(options.method, 'DELETE');
      deleted.push(path.split('/').at(-1));
      return new Response(null, { status: 204 });
    },
  });
  await mas.removeEmails('USER', 'new@example.com');
  assert.deepEqual(deleted, ['OLD']);
});
