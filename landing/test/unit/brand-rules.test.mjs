import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const FORBIDDEN = [/gradient\(/i, /shadow/i, /radius/i, /rotate/i, /skew/i];

function sources(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'data' ? [] : sources(path);
    return /\.(astro|css|js|mjs)$/.test(name) ? [path] : [];
  });
}

test('landing/src never uses a decoration the brand rules forbid', () => {
  const root = new URL('../../src', import.meta.url).pathname;
  const offenders = [];
  for (const file of sources(root)) {
    const text = readFileSync(file, 'utf8');
    for (const re of FORBIDDEN) if (re.test(text)) offenders.push(`${file}: ${re}`);
  }
  assert.deepEqual(offenders, []);
});
