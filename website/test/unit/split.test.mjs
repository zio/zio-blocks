import { test } from 'node:test';
import assert from 'node:assert/strict';
import { splitCategories } from '../../scripts/lib/split.mjs';

const b = (name) => ({ name, artifact: `a-${name}` });
const cats = () => [
  { name: 'Meta Programming', note: 'JSON is built in.', blocks: [b('Schema'), b('Avro Codec'), b('TypeId'), b('YAML Codec')] },
  { name: 'Streams', note: null, blocks: [b('Streams')] },
];
const split = { from: 'Meta Programming', into: 'Codecs', where: (blk) => / Codec$/.test(blk.name) };

test('moves the matching blocks into a new category right after the original and moves the note with them', () => {
  assert.deepEqual(splitCategories(cats(), [split]), [
    { name: 'Meta Programming', note: null, blocks: [b('Schema'), b('TypeId')] },
    { name: 'Codecs', note: 'JSON is built in.', blocks: [b('Avro Codec'), b('YAML Codec')] },
    { name: 'Streams', note: null, blocks: [b('Streams')] },
  ]);
});

test('no splits changes nothing, and the input is not mutated', () => {
  const input = cats();
  const snapshot = JSON.parse(JSON.stringify(input));
  assert.deepEqual(splitCategories(input, []), input);
  splitCategories(input, [split]);
  assert.deepEqual(input, snapshot);
});

test('fails loudly when the category is missing or a side would be empty', () => {
  assert.throws(
    () => splitCategories(cats(), [{ ...split, from: 'Nope' }]),
    (e) => e.message === 'split of unknown category "Nope"; check CATEGORY_SPLITS in scripts/build-catalog.mjs',
  );
  assert.throws(
    () => splitCategories(cats(), [{ ...split, where: () => true }]),
    (e) => e.message === 'split of "Meta Programming" into "Codecs" leaves "Meta Programming" with no blocks',
  );
  assert.throws(
    () => splitCategories(cats(), [{ ...split, where: () => false }]),
    (e) => e.message === 'split of "Meta Programming" into "Codecs" matches no blocks',
  );
});
