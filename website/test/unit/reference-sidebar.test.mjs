import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { build } from '../../scripts/build-catalog.mjs';

const require = createRequire(import.meta.url);

test('the /reference/ directory groups are the reference sidebar groups, in the same order', async () => {
  const out = mkdtempSync(join(tmpdir(), 'reference-'));
  const site = await build({ outDir: out, version: '9.9.9' });
  const sidebar = require('../../../docs/sidebars.js').sidebar;
  assert.deepEqual(site.reference.map((g) => g.name), sidebar.map((g) => g.label));
});

test('the reference index page draws the directory, with data for every group', () => {
  const page = readFileSync(new URL('../../../docs/reference/index.md', import.meta.url), 'utf8');
  assert.match(page, /import ReferenceDirectory from '\.\.\/ReferenceDirectory\.jsx';/);
  assert.match(page, /^<ReferenceDirectory \/>$/m);
});
