import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RAIL } from '../../src/lib/rail.mjs';
import { titleCase } from '../../src/lib/title-case.mjs';

test('the rail lists the five landing sections in page order', () => {
  assert.deepEqual(
    RAIL.map((s) => s.id),
    ['hero', 'principles', 'deep-dives', 'catalog', 'users'],
  );
});

test('rail labels are title case and unique', () => {
  const labels = RAIL.map((s) => s.label);
  assert.deepEqual(labels, ['Hero', 'Principles', 'Deep Dives', 'Catalog', 'Users']);
  for (const l of labels) assert.equal(titleCase(l), l);
  assert.equal(new Set(labels).size, labels.length);
});
