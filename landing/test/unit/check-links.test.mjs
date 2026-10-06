import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractExternalLinks } from '../../scripts/check-links.mjs';

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
