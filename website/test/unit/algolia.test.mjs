import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { algoliaConfig } = require('../../src/algolia.js');

const ENV = { ALGOLIA_APP_ID: 'APP', ALGOLIA_SEARCH_API_KEY: 'KEY', ALGOLIA_INDEX_NAME: 'zio-blocks' };

test('algoliaConfig is the DocSearch settings when all three values are set', () => {
  assert.deepEqual(algoliaConfig(ENV), { appId: 'APP', apiKey: 'KEY', indexName: 'zio-blocks' });
});

test('algoliaConfig trims values and treats blank ones as unset', () => {
  assert.deepEqual(algoliaConfig({ ...ENV, ALGOLIA_APP_ID: '  APP\n' }), { appId: 'APP', apiKey: 'KEY', indexName: 'zio-blocks' });
  assert.equal(algoliaConfig({ ...ENV, ALGOLIA_APP_ID: '   ' }), undefined);
});

test('algoliaConfig is undefined, and silent, when no value is set', () => {
  const warn = console.warn;
  const warnings = [];
  console.warn = (message) => warnings.push(message);
  try {
    assert.equal(algoliaConfig({}), undefined);
  } finally {
    console.warn = warn;
  }
  assert.deepEqual(warnings, []);
});

test('algoliaConfig is undefined and warns naming the missing variables when only some are set', () => {
  const warn = console.warn;
  const warnings = [];
  console.warn = (message) => warnings.push(message);
  try {
    assert.equal(algoliaConfig({ ALGOLIA_APP_ID: 'APP' }), undefined);
  } finally {
    console.warn = warn;
  }
  assert.deepEqual(warnings, ['Algolia search is off: ALGOLIA_SEARCH_API_KEY, ALGOLIA_INDEX_NAME not set.']);
});
