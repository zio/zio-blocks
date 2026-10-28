import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';

// The guides sidebar (docs/sidebars.js) is the one list of guides: the sidebar, the /guides/ cards and the section and
// topic index pages are all drawn from it, so a guide missing here is missing from the site's navigation.
const require = createRequire(import.meta.url);
const docs = new URL('../../../docs/', import.meta.url);
const [root, ...rest] = require('../../../docs/sidebars.js').guides;

const isCategory = (item) => typeof item === 'object' && item.type === 'category';
const docIds = (items) =>
  items.flatMap((item) =>
    typeof item === 'string'
      ? [item]
      : item.type === 'doc'
        ? [item.id]
        : isCategory(item)
          ? [...(item.link?.type === 'doc' ? [item.link.id] : []), ...docIds(item.items)]
          : [],
  );
const categoriesIn = (items) => items.filter(isCategory).flatMap((c) => [c, ...categoriesIn(c.items)]);

const sections = root.items;
const howTo = sections.find((s) => s.label === 'How-to guides');

test('the guides sidebar is one Guides category that links the guides index', () => {
  assert.equal(rest.length, 0);
  assert.equal(root.label, 'Guides');
  assert.deepEqual(root.link, { type: 'doc', id: 'guides/index' });
});

test('the Guides category holds the four sections, in order, and nothing else', () => {
  assert.deepEqual(
    sections.map((s) => s.label),
    ['Tutorials', 'How-to guides', 'Migration guides', 'Deep dives'],
  );
});

test('How-to guides holds the eight topics, and each topic holds only guides', () => {
  assert.deepEqual(
    howTo.items.map((t) => t.label),
    ['Schema', 'Formats & codecs', 'SQL & persistence', 'Streams & async', 'Config, resources & DI', 'Web & HTTP',
      'Observability', 'Performance'],
  );
  for (const topic of howTo.items) assert.ok(topic.items.every((i) => typeof i === 'string'), topic.label);
});

test('every guide page appears in the sidebar exactly once', () => {
  // Recursive, so a page in a subfolder (a new part under query-dsl/, or a folder's index.md) is checked as well.
  const pages = readdirSync(new URL('guides/', docs), { recursive: true })
    .filter((f) => /\.mdx?$/.test(f) && f !== 'index.md')
    .map((f) => `guides/${f.replace(/\.mdx?$/, '')}`)
    .sort();
  const ids = docIds([root]).filter((id) => id !== 'guides/index');
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), [], 'listed twice');
  assert.deepEqual([...ids].sort(), pages);
});

test('every doc id in the sidebar names a page', () => {
  for (const id of docIds([root]))
    assert.ok(existsSync(new URL(`${id}.md`, docs)) || existsSync(new URL(`${id}.mdx`, docs)), id);
});

test('every section and topic opens a generated index page under /guides/ with a description', () => {
  const all = categoriesIn(sections);
  assert.deepEqual(all.filter((c) => c.link?.type !== 'generated-index').map((c) => c.label), ['Query DSL']);
  const generated = all.filter((c) => c.link?.type === 'generated-index');
  const slugs = generated.map((c) => c.link.slug);
  assert.equal(new Set(slugs).size, slugs.length, 'slugs are unique');
  for (const c of generated) {
    assert.match(c.link.slug, /^\/guides\/[a-z-]+(\/[a-z-]+)?$/, c.label);
    assert.ok(c.link.description?.length > 0, `${c.label} has a description`);
  }
  // Docs are served at the site root, so guides/<x> is at /guides/<x>: a generated page and a guide never share a URL.
  for (const id of docIds([root])) assert.equal(slugs.includes(`/${id}`), false, id);
});

test('the sections start expanded and the how-to topics start collapsed', () => {
  for (const s of sections) assert.equal(s.collapsed, false, s.label);
  for (const t of howTo.items) assert.equal(t.collapsed, true, t.label);
});

test('the tutorials start with installing the library, then the first schema', () => {
  assert.deepEqual(sections[0].items.slice(0, 2), ['guides/add-zio-blocks-to-your-build', 'guides/your-first-schema']);
});
