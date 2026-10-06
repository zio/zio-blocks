import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('../dist/index.html', import.meta.url), 'utf8');

const decode = (s) =>
  s.replace(/&#34;|&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
/** Text content of an HTML fragment: tags dropped, entities decoded, whitespace collapsed. */
export const text = (fragment) =>
  decode(fragment.replace(/<\/?(?:span|code|strong|em|a)\b[^>]*>/g, '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
const meta = (name) => new RegExp(`<meta name="${name}" content="([^"]*)"`).exec(html)?.[1];

test('document head', () => {
  assert.match(html, /^<!DOCTYPE html>/i);
  assert.match(html, /<html lang="en"/);
  assert.equal(
    text(/<title>([\s\S]*?)<\/title>/.exec(html)[1]),
    'ZIO Blocks — Type-Safe, Modular Building Blocks for Scala',
  );
  assert.equal(
    decode(meta('description')),
    'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.',
  );
  assert.equal(meta('color-scheme'), 'light dark');
  assert.match(html, /<link rel="icon" type="image\/svg\+xml" href="\/brand\/zio-blocks-mark-favicon\.svg"/);
  assert.match(html, /<a class="skip[^"]*" href="#content"/);
});

import site from '../src/data/site.json' with { type: 'json' };
import { titleCase } from '../src/lib/title-case.mjs';

const between = (open, close) => {
  const start = html.indexOf(open);
  assert.notEqual(start, -1, `missing ${open}`);
  return html.slice(start, html.indexOf(close, start));
};
/** HTML of a top-level section: from its opening tag to the next top-level section or footer. */
const sec = (id) => {
  const start = html.indexOf(`<section id="${id}"`);
  assert.notEqual(start, -1, `missing <section id="${id}"`);
  const next = html.slice(start + 1).search(/<(?:section|footer) id="/);
  return next === -1 ? html.slice(start) : html.slice(start, start + 1 + next);
};

test('hero', () => {
  const hero = sec('hero');
  // The headline is the tagline up to the first em dash; the "— no effect system required" tail is not shown.
  // "building blocks" is joined by a non-breaking space (U+00A0) so the phrase never wraps across lines. `text()` collapses
  // whitespace (including U+00A0), so compare the raw inner HTML of the h1.
  // The docs tagline is "Modular building blocks for modern Scala applications—no effect system required." The hero
  // shows the part before the dash in title case, with "Building Blocks" joined by a
  // non-breaking space. Pinned as a literal so any change to the headline is deliberate.
  assert.equal(site.tagline.split('—')[0], 'Modular building blocks for modern Scala applications');
  assert.equal(
    /<h1[^>]*>([\s\S]*?)<\/h1>/.exec(hero)[1],
    'Modular Building\u00a0Blocks for Modern Scala Applications',
  );
  assert.equal(text(/<p class="lead[^"]*"[^>]*>([\s\S]*?)<\/p>/.exec(hero)[1]), text(site.lead.replace(/\*+|`/g, '')));
  assert.equal(
    text(/<pre[\s\S]*?<\/pre>/.exec(hero)[0]),
    `${site.hero.install} ${site.hero.jsonCode} // ${site.hero.jsonResult}`,
  );
  assert.equal(text(/<p class="stack[^"]*"[^>]*>([\s\S]*?)<\/p>/.exec(hero)[1]), `Works with ${site.compatibility.join(' · ')}`);
  assert.match(hero, /<img[^>]*src="\/brand\/zio-blocks-logo-on-dark\.svg"[^>]*alt="ZIO Blocks"/);
  assert.match(hero, /href="https:\/\/github\.com\/zio\/zio-blocks"/);
  assert.match(hero, /href="https:\/\/zio\.dev\/zio-blocks\/"/);
});

test('principles', () => {
  const s = sec('principles');
  const items = [...s.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/g)].map((m) => text(m[1]));
  assert.deepEqual(items, site.principles.map((p, i) => `0${i + 1} ${p.name} ${p.text}`));
});

test('deep dives render every panel visible without JavaScript', () => {
  const s = sec('deep-dives');
  const tabs = [...s.matchAll(/role="tab"[^>]*>([\s\S]*?)<\/button>/g)].map((m) => text(m[1]));
  assert.deepEqual(tabs, site.deepDives.map((d) => d.title));
  const panels = [...s.matchAll(/<div[^>]*role="tabpanel"[^>]*>/g)].map((m) => m[0]);
  assert.equal(panels.length, site.deepDives.length);
  for (const p of panels) assert.doesNotMatch(p, /\bhidden\b/);
  for (const d of site.deepDives) {
    assert.match(s, new RegExp(`id="panel-${d.id}"`));
    assert.match(text(s), new RegExp(d.title));
  }
  assert.match(s, /aria-selected="true"/);
});

test('schema panel lists the format chips derived from the catalog', () => {
  const s = sec('deep-dives');
  const chips = [...s.matchAll(/<li class="chip"[^>]*>([\s\S]*?)<\/li>/g)].map((m) => text(m[1]));
  const codecs = site.categories
    .find((c) => c.name === 'Meta Programming')
    .blocks.filter((b) => b.name.endsWith(' Codec'))
    .map((b) => b.name.replace(/ Codec$/, ''));
  assert.deepEqual(chips, ['JSON', ...codecs]);
});

test('catalog renders every block as a visible tile with its artifact and docs link', () => {
  const s = sec('catalog');
  const tiles = [...s.matchAll(/<li class="tile"[^>]*>([\s\S]*?)<\/li>/g)];
  const blocks = site.categories.flatMap((c) => c.blocks);
  assert.equal(tiles.length, blocks.length);
  assert.equal(blocks.length, site.blockCount);
  tiles.forEach((t, i) => {
    const b = blocks[i];
    assert.doesNotMatch(t[0], /^<li[^>]*\bhidden\b/);
    assert.equal(text(/<h4[^>]*>([\s\S]*?)<\/h4>/.exec(t[1])[1]), b.name);
    assert.equal(/<h4[^>]*><a href="([^"]*)"/.exec(t[1])[1], b.docsUrl);
    assert.equal(text(/<code class="artifact"[^>]*>([\s\S]*?)<\/code>/.exec(t[1])[1]), b.artifact);
    // One "Learn More" button per tile, linking to the block's docs page; its accessible name includes the visible text.
    const learn = /<a class="learn" href="([^"]*)" aria-label="([^"]*)"[^>]*>([\s\S]*?)<\/a>/.exec(t[1]);
    assert.deepEqual(
      [learn[1], decode(learn[2]), text(learn[3])],
      [b.docsUrl, `Learn More about ${titleCase(b.name)}`, 'Learn More'],
    );
    assert.equal((t[1].match(/class="learn"/g) ?? []).length, 1);
    // The button shares one row with the platform / Scala badges: both are children of the same footer row.
    assert.equal(/<div class="foot"[^>]*>\s*<p class="badges"[^>]*>[\s\S]*?<\/p>\s*<a class="learn"/.test(t[1]), true);
  });
});

test('consecutive single-tile categories share a row; every other category has its own row', () => {
  const s = sec('catalog');
  // Split at each row start, then read the category headings (h3) inside each row.
  const rows = s.split('<div class="row"').slice(1).map((chunk) => [...chunk.matchAll(/<h3[^>]*>([\s\S]*?)<\/h3>/g)].map((h) => text(h[1])));
  // Resource Management (Scope) and Dependency Injection (Context) are one row.
  assert.deepEqual(rows.find((r) => r.includes('Resource Management')), ['Resource Management', 'Dependency Injection']);
  // Rows keep the document order of the categories and together contain every category exactly once.
  assert.deepEqual(rows.flat(), site.categories.map((c) => titleCase(c.name)));
  // A row has several categories only when each of them has exactly one block.
  const size = new Map(site.categories.map((c) => [titleCase(c.name), c.blocks.length]));
  for (const r of rows) if (r.length > 1) assert.deepEqual(r.map((n) => size.get(n)), r.map(() => 1));
});

test('catalog has no filter controls, counter, empty-state message or buttons', () => {
  const s = sec('catalog');
  assert.equal(/data-filter|aria-pressed|data-status|data-empty|data-catalog/.test(s), false);
  assert.equal(/<button/.test(s), false);
});

test('footer is the logo and links only: no call to action, no install box', () => {
  const f = between('<footer id="footer"', '</footer>');
  assert.equal(/data-copy|class="install"|class="cta"|Add a Block/.test(f), false);
  assert.match(f, /src="\/brand\/zio-blocks-logo-mono-white\.svg"[^>]*alt="ZIO Blocks"/);
  for (const href of ['https://zio.dev/zio-blocks/', 'https://github.com/zio/zio-blocks']) {
    assert.match(f, new RegExp(`href="${href.replace(/[./]/g, '\\$&')}"`));
  }
});

test('page order, anchors, images, and leftovers', () => {
  const ids = [...html.matchAll(/<(?:section|footer) id="([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual(ids, ['hero', 'principles', 'deep-dives', 'catalog', 'footer']);
  const allIds = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  for (const [, target] of html.matchAll(/href="#([^"]+)"/g)) assert.ok(allIds.has(target), `dangling #${target}`);
  for (const img of html.matchAll(/<img\b[^>]*>/g)) assert.match(img[0], /\balt="[^"]+"/);
  assert.equal((html.match(/<h1\b/g) ?? []).length, 1);
  assert.equal(html.includes('@VERSION@'), false);
  for (const [, href] of html.matchAll(/href="(https?:[^"]+)"/g)) assert.match(href, /^https:/);
});

test('there are no copy buttons, copy script or copy live region anywhere', () => {
  assert.equal(/data-copy|class="copy"|role="status"|sr-only|navigator\.clipboard/.test(html), false);
});

test('every title, heading and label on the page is in title case', () => {
  const all = (re, scope = html) => [...scope.matchAll(re)].map((m) => text(m[1]));
  assert.equal(text(/<title>([\s\S]*?)<\/title>/.exec(html)[1]), 'ZIO Blocks — Type-Safe, Modular Building Blocks for Scala');
  assert.deepEqual(all(/<h2[^>]*>([\s\S]*?)<\/h2>/g), [
    'Use What You Need, Nothing More',
    'Four Blocks, in Code',
    'Take Only What You Need',
  ]);
  assert.deepEqual(
    all(/<p class="label[^"]*"[^>]*>([\s\S]*?)<\/p>/g)
      .map((l) => l.replace(' &nbsp; ', ' ')) // the numbered labels separate number and name with an HTML space
      .filter((l) => !l.startsWith('The ')),
    ['01 Principles', '02 Deep Dives', 'One Schema, Many Formats', '03 Block Catalog'],
  );
  assert.deepEqual(all(/<p class="label[^"]*"[^>]*>(The [\s\S]*?)<\/p>/g), site.deepDives.flatMap(() => ['The Problem', 'The Solution']));
  assert.deepEqual(all(/<h3[^>]*>([\s\S]*?)<\/h3>/g), [
    ...site.deepDives.map((d) => titleCase(d.title)),
    ...site.categories.map((c) => titleCase(c.name)),
  ]);
  const blocks = site.categories.flatMap((c) => c.blocks);
  assert.deepEqual(all(/<h4[^>]*>([\s\S]*?)<\/h4>/g), blocks.map((b) => titleCase(b.name)));
  assert.deepEqual(all(/<strong[^>]*>([\s\S]*?)<\/strong>/g, sec('principles')), site.principles.map((p) => titleCase(p.name)));
});

test('there is no switching-cost section and nothing links to one', () => {
  assert.equal(/switching/i.test(html), false);
});
