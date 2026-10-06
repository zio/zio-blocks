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
