import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

// Netlify `_redirects` syntax: one rule per line (from, to, status, whitespace-separated); `#` starts a comment.
const rules = read('../../static/_redirects')
  .split('\n')
  .map((line) => line.trim())
  .filter((line) => line !== '' && !line.startsWith('#'))
  .map((line) => {
    const [from, to, status, ...rest] = line.split(/\s+/);
    return { from, to, status, extra: rest };
  });

const rule = (from) => rules.find((r) => r.from === from);

test('every rule has exactly a from, a to and a status', () => {
  for (const r of rules) {
    assert.ok(r.from && r.to && /^\d{3}!?$/.test(r.status ?? ''), `malformed rule: ${JSON.stringify(r)}`);
    assert.deepEqual(r.extra, [], `unexpected extra fields: ${JSON.stringify(r)}`);
  }
});

test('the old /docs links redirect to the site root, in this order', () => {
  const froms = rules.map((r) => r.from);
  assert.deepEqual(rule('/docs'), { from: '/docs', to: '/overview', status: '301', extra: [] });
  assert.deepEqual(rule('/docs/'), { from: '/docs/', to: '/overview', status: '301', extra: [] });
  assert.deepEqual(rule('/docs/*'), { from: '/docs/*', to: '/:splat', status: '301', extra: [] });
  for (const exact of ['/docs', '/docs/']) {
    assert.ok(froms.indexOf(exact) < froms.indexOf('/docs/*'), `${exact} comes before the /docs/* wildcard`);
  }
});

test('the moved install guide redirects to Getting Started, with and without a trailing slash', () => {
  for (const from of ['/guides/add-zio-blocks-to-your-build', '/guides/add-zio-blocks-to-your-build/']) {
    assert.deepEqual(rule(from), { from, to: '/getting-started/installation', status: '301', extra: [] });
  }
});

test('the netlify.app host redirects come first, so a path rule never runs on that host', () => {
  assert.equal(rules[0].from, 'https://zio-blocks.netlify.app/*');
  assert.equal(rules[1].from, 'http://zio-blocks.netlify.app/*');
  assert.equal(rules[0].status, '301!');
  assert.equal(rules[1].status, '301!');
});

test('netlify.toml declares no redirects, since the deploy jobs never upload it', () => {
  const toml = read('../../../netlify.toml');
  assert.doesNotMatch(toml.replace(/^\s*#.*$/gm, ''), /\[\[redirects\]\]/);
});
