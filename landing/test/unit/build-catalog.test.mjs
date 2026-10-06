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
  // One tile per artifact across the whole page (blocks that ship in the same artifact are collapsed).
  const artifacts = site.categories.flatMap((c) => c.blocks.map((b) => b.artifact));
  assert.equal(new Set(artifacts).size, artifacts.length);
  // The Streams category is shown as "Streaming"; the block inside it keeps its own name.
  // Meta Programming is split: Schema and TypeId stay, the format codecs move to "Codecs" right after it.
  const names = site.categories.map((c) => c.name);
  assert.equal(names[names.indexOf('Meta Programming') + 1], 'Codecs');
  assert.deepEqual(site.categories.find((c) => c.name === 'Meta Programming').blocks.map((x) => x.name), ['Schema', 'TypeId']);
  const codecs = site.categories.find((c) => c.name === 'Codecs');
  assert.deepEqual(codecs.blocks.map((x) => x.name), ['Avro Codec', 'BSON Codec', 'CSV Codec', 'MessagePack Codec', 'Thrift Codec', 'TOON Codec', 'XML Codec', 'YAML Codec']);
  assert.equal(typeof codecs.note, 'string'); // the docs note about the format modules moved with them
  assert.deepEqual(site.categories.map((c) => c.name).filter((n) => /^Stream/.test(n)), ['Streaming']);
  assert.deepEqual(site.categories.find((c) => c.name === 'Streaming').blocks.map((x) => x.name), ['Streams']);
  // "Configuration & Feature Flags" is shown as "Configuration".
  // The Cross-Platform principle is shown with a shorter text than docs/index.md has.
  assert.equal(
    site.principles.find((p) => p.name === 'Cross-Platform').text,
    'Most blocks cross-build for JVM and Scala.js on Scala 2.13 and 3.x. Adopt Scala 3 on your timeline.',
  );
  assert.equal(names.includes('Configuration'), true);
  assert.equal(names.some((n) => /Feature Flags/.test(n)), false);
  assert.equal(JSON.stringify(site).includes('@VERSION@'), false);
  for (const file of BRAND_ASSETS) assert.ok(existsSync(join(out, 'public/brand', file)), file);
});
