import { test } from 'node:test';
import assert from 'node:assert/strict';
import { overridePrincipleText } from '../../scripts/lib/override.mjs';

const principles = [
  { name: 'Zero Lock-In', text: 'No dependency on any effect system.' },
  { name: 'Cross-Platform', text: 'A long sentence.' },
];

test('replaces the text of the named principles and keeps everything else', () => {
  assert.deepEqual(overridePrincipleText(principles, { 'Cross-Platform': 'Short.' }), [
    { name: 'Zero Lock-In', text: 'No dependency on any effect system.' },
    { name: 'Cross-Platform', text: 'Short.' },
  ]);
});

test('an empty map changes nothing, and the input is not mutated', () => {
  const snapshot = JSON.parse(JSON.stringify(principles));
  assert.deepEqual(overridePrincipleText(principles, {}), principles);
  overridePrincipleText(principles, { 'Cross-Platform': 'Short.' });
  assert.deepEqual(principles, snapshot);
});

test('an override for a principle that does not exist fails loudly', () => {
  assert.throws(
    () => overridePrincipleText(principles, { Nope: 'x' }),
    (e) => e.message === 'text override for unknown principle "Nope"; check PRINCIPLE_TEXT in scripts/build-catalog.mjs',
  );
});
