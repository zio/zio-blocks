import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('../dist/index.html', import.meta.url), 'utf8');

const decode = (s) =>
  s.replace(/&#34;|&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
/** Text content of an HTML fragment: tags dropped, entities decoded, whitespace collapsed. */
export const text = (fragment) =>
  decode(fragment.replace(/<\/?(?:span|code|strong|em|a)\b[^>]*>/g, '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
const meta = (name) => new RegExp(`<meta name="${name}" content="([^"]*)"`).exec(html)?.[1];

test('document head', () => {
  assert.match(html, /^<!DOCTYPE html>/i);
  assert.match(html, /<html lang="en"/);
  assert.equal(
    text(/<title>([\s\S]*?)<\/title>/.exec(html)[1]),
    'ZIO Blocks — Type-safe, modular building blocks for Scala',
  );
  assert.equal(
    decode(meta('description')),
    'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.',
  );
  assert.equal(meta('color-scheme'), 'light dark');
  assert.match(html, /<link rel="icon" type="image\/svg\+xml" href="\/brand\/zio-blocks-mark-favicon\.svg"/);
  assert.match(html, /<a class="skip[^"]*" href="#main"/);
});
