import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matches } from '../../src/lib/filter.mjs';

const tile = { category: 'Web & HTTP', platforms: ['JVM', 'JS'], scala: ['3.x'] };
const none = { category: null, platform: null, scala: null };

test('no filters matches everything', () => {
  assert.equal(matches(tile, none), true);
});

test('each filter narrows independently and filters combine with AND', () => {
  assert.equal(matches(tile, { ...none, category: 'Web & HTTP' }), true);
  assert.equal(matches(tile, { ...none, category: 'Streams' }), false);
  assert.equal(matches(tile, { ...none, platform: 'JS' }), true);
  assert.equal(matches({ ...tile, platforms: ['JVM'] }, { ...none, platform: 'JS' }), false);
  assert.equal(matches(tile, { ...none, scala: '2.13' }), false);
  assert.equal(matches(tile, { category: 'Web & HTTP', platform: 'JVM', scala: '3.x' }), true);
  assert.equal(matches(tile, { category: 'Web & HTTP', platform: 'JVM', scala: '2.13' }), false);
});
