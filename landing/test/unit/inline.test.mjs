import { test } from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, renderInline } from '../../src/lib/inline.mjs';

test('escapeHtml escapes the five significant characters', () => {
  assert.equal(escapeHtml(`<a href="x">&'</a>`), '&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;');
});

test('renderInline handles code, bold, emphasis and links', () => {
  assert.equal(
    renderInline('Use `Scope.defer` for **safe** and *quick* cleanup, see [docs](https://x.dev/a).'),
    'Use <code>Scope.defer</code> for <strong>safe</strong> and <em>quick</em> cleanup, see <a href="https://x.dev/a">docs</a>.',
  );
});

test('renderInline escapes raw HTML and leaves code spans literal', () => {
  assert.equal(
    renderInline('a <b>bold</b> & `List<Int> **not bold**`'),
    'a &lt;b&gt;bold&lt;/b&gt; &amp; <code>List&lt;Int&gt; **not bold**</code>',
  );
});

test('renderInline maps link targets through linkFor', () => {
  assert.equal(
    renderInline('[ref](./reference/a.md)', (u) => `https://zio.dev/${u.slice(2)}`),
    '<a href="https://zio.dev/reference/a.md">ref</a>',
  );
});

test('renderInline protects URLs from emphasis/code processing', () => {
  assert.equal(
    renderInline('[a](https://x/*a*/b)'),
    '<a href="https://x/*a*/b">a</a>',
  );
});

test('renderInline protects URLs containing backticks', () => {
  assert.equal(
    renderInline('[a](https://x/`code`)'),
    '<a href="https://x/`code`">a</a>',
  );
});

test('renderInline escapes linkFor return value in href attribute', () => {
  assert.equal(
    renderInline('[x](http://example.com)', (u) => 'a" onmouseover="x'),
    '<a href="a&quot; onmouseover=&quot;x">x</a>',
  );
});

test('renderInline passes raw URL to linkFor and escapes special chars in href', () => {
  assert.equal(
    renderInline('[x](http://a.b?foo=1&bar=2)', (u) => u),
    '<a href="http://a.b?foo=1&amp;bar=2">x</a>',
  );
});

test('renderInline rejects javascript: URLs', () => {
  assert.equal(
    renderInline('[click me](javascript:alert)'),
    'click me',
  );
});

test('renderInline allows safe URL schemes', () => {
  assert.equal(
    renderInline('[http](http://x) [https](https://x) [mailto](mailto:x@y) [relative](./x) [hash](#x)'),
    '<a href="http://x">http</a> <a href="https://x">https</a> <a href="mailto:x@y">mailto</a> <a href="./x">relative</a> <a href="#x">hash</a>',
  );
});
