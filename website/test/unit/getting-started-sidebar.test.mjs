import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync } from 'node:fs';
import { createRequire } from 'node:module';

// Getting Started is a category of the main sidebar (docs/sidebars.js), right after the overview: its pages are a tour
// read in order, so a page missing from the category is missing from the tour.
const require = createRequire(import.meta.url);
const docs = new URL('../../../docs/', import.meta.url);
const sidebar = require('../../../docs/sidebars.js').sidebar;

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

test('Getting Started is the first category of the main sidebar, right after the overview', () => {
  assert.equal(sidebar[0].id, 'index');
  assert.equal(sidebar[1], gettingStarted);
  assert.equal(gettingStarted.collapsed, false);
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
