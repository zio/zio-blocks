import { test } from 'node:test';
import assert from 'node:assert/strict';
import { referenceGroups } from '../../scripts/lib/reference.mjs';

const b = (name, artifact, extra = {}) => ({
  name, docsUrl: `/reference/${name.toLowerCase()}`, artifact, platforms: ['JVM'], scala: ['3.x'], description: `${name} text`, ...extra,
});
const cats = () => [
  { name: 'Meta Programming', note: 'JSON is built in.', blocks: [b('Schema', 'zio-blocks-schema'), b('Avro Codec', 'zio-blocks-schema-avro'), b('TypeId', 'zio-blocks-typeid')] },
  { name: 'Resource Management', note: null, blocks: [b('Scope', 'zio-blocks-scope'), b('Resource', 'zio-blocks-scope')] },
  { name: 'Dependency Injection', note: null, blocks: [b('Wire', 'zio-blocks-scope')] },
];
const folds = [{ into: 'Schema', where: (blk) => / Codec$/.test(blk.name) }];
const link = (name, artifact) => ({ name, url: `/reference/${name.toLowerCase()}`, artifact, description: `${name} text` });
const row = (name, artifact, related = []) => ({
  name, url: `/reference/${name.toLowerCase()}`, artifact, platforms: ['JVM'], scala: ['3.x'], description: `${name} text`, related,
});

test('folds named rows and same-artifact rows into their parent, keeping docs order and headings', () => {
  assert.deepEqual(referenceGroups(cats(), folds), [
    { name: 'Meta Programming', blocks: [row('Schema', 'zio-blocks-schema', [link('Avro Codec', 'zio-blocks-schema-avro')]), row('TypeId', 'zio-blocks-typeid')] },
    { name: 'Resource Management', blocks: [row('Scope', 'zio-blocks-scope', [link('Resource', 'zio-blocks-scope')])] },
    // Wire shares Scope's artifact but sits in another category, so it keeps its own row.
    { name: 'Dependency Injection', blocks: [row('Wire', 'zio-blocks-scope')] },
  ]);
});

test('without folds only same-artifact rows fold, and the input is not mutated', () => {
  const input = cats();
  const snapshot = JSON.parse(JSON.stringify(input));
  const groups = referenceGroups(input, []);
  assert.deepEqual(groups[0].blocks.map((x) => x.name), ['Schema', 'Avro Codec', 'TypeId']);
  assert.deepEqual(input, snapshot);
});

test('a fold into an unknown block, or matching nothing, fails loudly', () => {
  assert.throws(
    () => referenceGroups(cats(), [{ ...folds[0], into: 'Nope' }]),
    (e) => e.message === 'reference fold into unknown block "Nope"; check REFERENCE_FOLDS in scripts/build-catalog.mjs',
  );
  assert.throws(
    () => referenceGroups(cats(), [{ into: 'Schema', where: () => false }]),
    (e) => e.message === 'reference fold into "Schema" matches no blocks',
  );
});
