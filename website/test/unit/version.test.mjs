import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FALLBACK_VERSION, resolveVersion } from '../../scripts/lib/version.mjs';

const json = (body, ok = true, status = 200) => async () => ({ ok, status, json: async () => body });
const run = async (fetchFn) => {
  const warnings = [];
  const version = await resolveVersion({ fetchFn, token: undefined, warn: (m) => warnings.push(m) });
  return { version, warnings };
};

test('uses the latest release tag without the leading v', async () => {
  assert.deepEqual(await run(json({ tag_name: 'v0.0.56' })), { version: '0.0.56', warnings: [] });
});

test('falls back on HTTP errors, network errors, bad JSON, and unexpected tags', async () => {
  const cases = [
    [json({}, false, 403), 'HTTP 403'],
    [async () => { throw new Error('getaddrinfo ENOTFOUND'); }, 'getaddrinfo ENOTFOUND'],
    [async () => ({ ok: true, status: 200, json: async () => { throw new SyntaxError('Unexpected token <'); } }), 'Unexpected token <'],
    [json({ tag_name: 'v0.0.57-RC1' }), 'unexpected tag "v0.0.57-RC1"'],
    [json({}), 'unexpected tag "undefined"'],
  ];
  for (const [fetchFn, reason] of cases) {
    assert.deepEqual(await run(fetchFn), {
      version: FALLBACK_VERSION,
      warnings: [`landing: could not resolve latest release (${reason}); using ${FALLBACK_VERSION}`],
    });
  }
});

test('sends the token when one is provided', async () => {
  let headers;
  await resolveVersion({
    token: 'abc',
    warn: () => {},
    fetchFn: async (_url, init) => { headers = init.headers; return { ok: true, status: 200, json: async () => ({ tag_name: 'v1.2.3' }) }; },
  });
  assert.equal(headers.authorization, 'Bearer abc');
});
