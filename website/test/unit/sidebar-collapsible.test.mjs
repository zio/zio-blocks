import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

// The docs sidebar folds: every category outside the Getting Started tour starts closed, and the sidebar has a hide
// button (docs/sidebars.js and docusaurus.config.js). Getting Started stays open: its tour is read in order.
const require = createRequire(import.meta.url);
const sidebars = require('../../../docs/sidebars.js');
const config = require('../../docusaurus.config.js');

const categoriesIn = (items) =>
  items
    .filter((item) => typeof item === 'object' && item.type === 'category')
    .flatMap((c) => [c, ...categoriesIn(c.items)]);

const foldable = [...categoriesIn(sidebars.sidebar), ...categoriesIn(sidebars.guides)];

test('the docs sidebar has a hide button', () => {
  assert.equal(config.themeConfig.docs.sidebar.hideable, true);
});

test('no category in the reference or guides sidebar starts open or is locked open', () => {
  assert.ok(foldable.length > 0);
  for (const c of foldable) {
    assert.notEqual(c.collapsed, false, c.label);
    assert.notEqual(c.collapsible, false, c.label);
  }
});
