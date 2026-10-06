import { test } from 'node:test';
import assert from 'node:assert/strict';
import { collapseSameArtifact } from '../../scripts/lib/collapse.mjs';

const block = (name, artifact) => ({
  name, docsUrl: `https://zio.dev/zio-blocks/${name}`, artifact, platforms: ['JVM'], scala: ['3.x'], description: `${name} d`,
});

test('keeps only the first block of each artifact within a category, in order', () => {
  const categories = [
    { name: 'Resource Management', note: 'All of these ship in `zio-blocks-scope`.', blocks: [
      block('Scope', 'zio-blocks-scope'), block('Resource', 'zio-blocks-scope'), block('Finalizer', 'zio-blocks-scope'),
    ] },
  ];
  assert.deepEqual(collapseSameArtifact(categories), [
    // The note described the collapsed rows ("All of these ship in ..."), so it goes with them.
    { name: 'Resource Management', note: null, blocks: [block('Scope', 'zio-blocks-scope')] },
  ]);
});

test('keeps blocks with different artifacts, and the same artifact in different categories', () => {
  const categories = [
    { name: 'A', note: 'JSON is built in.', blocks: [block('Schema', 'zio-blocks-schema'), block('Avro Codec', 'zio-blocks-schema-avro')] },
    { name: 'B', note: null, blocks: [block('Wire', 'zio-blocks-scope'), block('Context', 'zio-blocks-context')] },
    { name: 'C', note: null, blocks: [block('Scope', 'zio-blocks-scope')] },
  ];
  assert.deepEqual(collapseSameArtifact(categories), categories);
});

test('does not mutate its input and never drops a category', () => {
  const input = [{ name: 'X', note: null, blocks: [block('One', 'a'), block('Two', 'a')] }];
  const snapshot = JSON.parse(JSON.stringify(input));
  const out = collapseSameArtifact(input);
  assert.deepEqual(input, snapshot);
  assert.equal(out.length, 1);
  assert.deepEqual(out[0].blocks.map((b) => b.name), ['One']);
});
