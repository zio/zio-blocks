import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { build, BRAND_ASSETS } from '../../scripts/build-catalog.mjs';

test('build writes site.json with the version substituted and copies brand assets', async () => {
  const out = mkdtempSync(join(tmpdir(), 'landing-'));
  await build({ outDir: out, version: '9.9.9' });

  const site = JSON.parse(readFileSync(join(out, 'src/data/site.json'), 'utf8'));
  assert.equal(site.version, '9.9.9');
  assert.equal(site.hero.install, 'libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "9.9.9"');
  assert.equal(site.blockCount, site.categories.flatMap((c) => c.blocks).length);
  assert.ok(site.blockCount >= 40);
  assert.equal(JSON.stringify(site).includes('@VERSION@'), false);
  for (const file of BRAND_ASSETS) assert.ok(existsSync(join(out, 'public/brand', file)), file);
});
