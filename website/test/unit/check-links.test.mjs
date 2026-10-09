import { test } from 'node:test';
import assert from 'node:assert/strict';
import { classify, extractExternalLinks } from '../../scripts/check-links.mjs';

test('extractExternalLinks returns unique https links in order and ignores anchors and relative paths', () => {
  const html = `
    <a href="https://zio.dev/zio-blocks/">a</a>
    <a href="#catalog">b</a>
    <a href="/brand/x.svg">c</a>
    <a href="https://github.com/zio/zio-blocks">d</a>
    <a href="https://zio.dev/zio-blocks/">e</a>
    <a href="https://zio.dev/zio-blocks/reference/schema/?a=1&amp;b=2">f</a>`;
  assert.deepEqual(extractExternalLinks(html), [
    'https://zio.dev/zio-blocks/',
    'https://github.com/zio/zio-blocks',
    'https://zio.dev/zio-blocks/reference/schema/?a=1&b=2',
  ]);
});

test('classify warns on unpublished zio.dev docs pages and rate limits, fails on other 404s and 5xx', () => {
  assert.equal(classify('https://zio.dev/zio-blocks/reference/new', 404), 'warn');
  assert.equal(classify('https://zio.dev/zio-blocks/reference/new', 410), 'warn');
  assert.equal(classify('https://example.com/a', 404), 'fail');
  assert.equal(classify('https://zio.dev/other', 404), 'fail');
  assert.equal(classify('https://example.com/a', 410), 'fail');
  assert.equal(classify('https://example.com/a', 403), 'warn');
  assert.equal(classify('https://example.com/a', 429), 'warn');
  assert.equal(classify('https://example.com/a', 500), 'fail');
  assert.equal(classify('https://example.com/a', 503), 'fail');
  assert.equal(classify('https://example.com/a', 200), 'ok');
  assert.equal(classify('https://example.com/a', 301), 'ok');
});
