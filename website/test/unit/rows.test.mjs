import { test } from 'node:test';
import assert from 'node:assert/strict';
import { packRows } from '../../src/lib/rows.mjs';

const cat = (name, n) => ({ name, blocks: Array.from({ length: n }, (_, i) => ({ name: `${name}${i}` })) });

test('consecutive small categories share a row while their tiles fit in the columns', () => {
  const cats = [cat('Meta', 2), cat('Codecs', 8), cat('RM', 1), cat('DI', 1), cat('Config', 4), cat('Streaming', 1), cat('Telemetry', 2), cat('Persistence', 4)];
  assert.deepEqual(
    packRows(cats).map((row) => row.map((c) => c.name)),
    [['Meta'], ['Codecs'], ['RM', 'DI'], ['Config'], ['Streaming', 'Telemetry'], ['Persistence']],
  );
});

test('the column count is a parameter and order is preserved', () => {
  const cats = [cat('A', 1), cat('B', 1), cat('C', 1)];
  assert.deepEqual(packRows(cats, 2).map((r) => r.map((c) => c.name)), [['A', 'B'], ['C']]);
  assert.deepEqual(packRows([], 3), []);
});
