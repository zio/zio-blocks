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
  assert.equal(site.hero, undefined);
  assert.equal(site.deepDives[0].install, 'libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "9.9.9"');
  assert.equal(site.blockCount, site.categories.flatMap((c) => c.blocks).length);
  assert.ok(site.blockCount >= 40);
  // One tile per artifact across the whole page (blocks that ship in the same artifact are collapsed).
  const artifacts = site.categories.flatMap((c) => c.blocks.map((b) => b.artifact));
  assert.equal(new Set(artifacts).size, artifacts.length);
  // The landing page shows the docs headings as they are: they are the categories zio.dev/ecosystem shares with this
  // site (zio/zio website/ecosystem-categories.json), so there are no renames or splits.
  const names = site.categories.map((c) => c.name);
  assert.deepEqual(names, [
    'Web & APIs', 'Data Access', 'Streaming & Messaging', 'Schemas & Serialization', 'Observability', 'Configuration & CLI',
    'Resource Management', 'Dependency Injection', 'Concurrency & Resilience', 'Types & Abstractions', 'Tooling & Testing',
  ]);
  const serialization = site.categories.find((c) => c.name === 'Schemas & Serialization');
  assert.deepEqual(serialization.blocks.map((x) => x.name), [
    'Schema', 'Avro Codec', 'BSON Codec', 'CSV Codec', 'MessagePack Codec', 'Thrift Codec', 'TOON Codec', 'XML Codec', 'YAML Codec',
  ]);
  assert.equal(typeof serialization.note, 'string'); // the docs note about the format modules
  assert.deepEqual(site.categories.find((c) => c.name === 'Types & Abstractions').blocks.map((x) => x.name), ['Chunk', 'Maybe', 'Combinators', 'TypeId']);
  // Resource Management comes before Dependency Injection, so Wire (zio-blocks-scope) folds into Scope, not the reverse.
  assert.deepEqual(site.categories.find((c) => c.name === 'Resource Management').blocks.map((x) => x.name), ['Scope']);
  // The Cross-Platform principle is shown with a shorter text than docs/index.md has.
  assert.equal(
    site.principles.find((p) => p.name === 'Cross-Platform').text,
    'Most blocks build for JVM and Scala.js on Scala 3.9 and 3.10. The five foundation modules also build for Scala 2.13, on the JVM only.',
  );
  assert.equal(JSON.stringify(site).includes('@VERSION@'), false);
  // The /reference/ directory keeps the docs headings, folds the codecs into Schema and the same-artifact rows into Scope.
  const ref = Object.fromEntries(site.reference.map((g) => [g.name, g.blocks]));
  assert.deepEqual(site.reference.map((g) => g.name), names);
  const schema = ref['Schemas & Serialization'].find((x) => x.name === 'Schema');
  assert.deepEqual(ref['Schemas & Serialization'].map((x) => x.name), ['Schema']);
  assert.equal(schema.related.length, 8);
  assert.ok(schema.related.every((r) => / Codec$/.test(r.name)));
  assert.deepEqual(ref['Resource Management'].map((x) => x.name), ['Scope']);
  assert.deepEqual(ref['Dependency Injection'].map((x) => x.name), ['Wire', 'Context']);
  assert.deepEqual(ref['Configuration & CLI'][0].related.map((r) => r.name), ['Config YAML', 'Config JSON', 'Config HOCON']);
  for (const file of BRAND_ASSETS) assert.ok(existsSync(join(out, 'static/brand', file)), file);
});
