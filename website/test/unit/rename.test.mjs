import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renameCategories } from '../../scripts/lib/rename.mjs';

const cats = [
  { name: 'Concurrency', note: null, blocks: [] },
  { name: 'Streams', note: 'n', blocks: [{ name: 'Streams', artifact: 'zio-blocks-streams' }] },
];

test('renames only the categories named in the map and keeps everything else', () => {
  assert.deepEqual(renameCategories(cats, { Streams: 'Streaming' }), [
    { name: 'Concurrency', note: null, blocks: [] },
    { name: 'Streaming', note: 'n', blocks: [{ name: 'Streams', artifact: 'zio-blocks-streams' }] },
  ]);
});

test('an empty map changes nothing, and the input is not mutated', () => {
  const snapshot = JSON.parse(JSON.stringify(cats));
  assert.deepEqual(renameCategories(cats, {}), cats);
  renameCategories(cats, { Streams: 'Streaming' });
  assert.deepEqual(cats, snapshot);
});

test('a rename for a category that does not exist fails loudly', () => {
  assert.throws(
    () => renameCategories(cats, { Nope: 'Other' }),
    (e) => e.message === 'rename for unknown category "Nope"; check CATEGORY_RENAMES in scripts/build-catalog.mjs',
  );
});
