import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync } from 'node:fs';
import { createRequire } from 'node:module';

// Getting Started has its own sidebar (docs/sidebars.js): the overview, then the Getting Started category. Its pages are
// a tour read in order, so a page missing from the category is missing from the tour.
const require = createRequire(import.meta.url);
const docs = new URL('../../../docs/', import.meta.url);
const sidebars = require('../../../docs/sidebars.js');
const sidebar = sidebars.gettingStarted;

const docIds = (items) =>
  items.flatMap((item) =>
    typeof item === 'string'
      ? [item]
      : item.type === 'doc'
        ? [item.id]
        : item.type === 'category'
          ? [...(item.link?.type === 'doc' ? [item.link.id] : []), ...docIds(item.items)]
          : [],
  );

const gettingStarted = sidebar.find((item) => item.type === 'category' && item.label === 'Getting Started');

test('the Getting Started sidebar holds the overview, then the Getting Started category, and nothing else', () => {
  assert.equal(sidebar.length, 2);
  assert.equal(sidebar[0].id, 'index');
  assert.equal(sidebar[1], gettingStarted);
});

test('no Getting Started category folds: each one is always expanded', () => {
  const categories = (items) =>
    items.filter((i) => i.type === 'category').flatMap((c) => [c, ...categories(c.items)]);
  for (const c of categories(sidebar)) assert.equal(c.collapsible, false, c.label);
});

test('the reference sidebar holds neither the overview nor a Getting Started page', () => {
  const ids = docIds(sidebars.sidebar);
  assert.equal(ids.includes('index'), false);
  assert.deepEqual(ids.filter((id) => id.startsWith('getting-started/')), []);
});

test('the Getting Started pages appear in reading order', () => {
  assert.deepEqual(docIds([gettingStarted]), [
    'getting-started/index',
    'getting-started/installation',
    'getting-started/first-five-minutes',
    'getting-started/how-zio-blocks-thinks',
    'getting-started/concepts/schema',
    'getting-started/concepts/scope',
    'getting-started/concepts/wire-and-context',
    'getting-started/concepts/streams',
    'getting-started/use-with-effect-systems',
    'getting-started/where-next',
  ]);
});

test('every Getting Started page appears in the category exactly once', () => {
  const pages = readdirSync(new URL('getting-started/', docs), { recursive: true })
    .filter((f) => /\.mdx?$/.test(f))
    .map((f) => `getting-started/${f.replace(/\.mdx?$/, '')}`)
    .sort();
  const ids = docIds([gettingStarted]);
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), [], 'listed twice');
  assert.deepEqual([...ids].sort(), pages);
});
