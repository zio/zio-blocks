import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { findViolations } from '../../scripts/lib/brand-guard.mjs';

function sources(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'data' ? [] : sources(path);
    return /\.(css|[cm]?[jt]sx?)$/.test(name) ? [path] : [];
  });
}

test('website/src never uses a decoration the brand rules forbid', () => {
  const root = fileURLToPath(new URL('../../src', import.meta.url));
  const offenders = [];
  for (const file of sources(root)) {
    for (const v of findViolations(readFileSync(file, 'utf8'))) offenders.push(`${file}: ${v}`);
  }
  assert.deepEqual(offenders, []);
});
