import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import site from '../src/data/site.json' with { type: 'json' };
import { titleCase } from '../src/lib/title-case.mjs';

const html = readFileSync(new URL('../build/index.html', import.meta.url), 'utf8');

const decode = (s) =>
  s.replace(/&#34;|&quot;/g, '"').replace(/&#39;|&#x27;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
/** Text content of an HTML fragment: inline tags dropped, other tags become spaces, entities decoded, whitespace collapsed. */
export const text = (fragment) =>
  decode(fragment.replace(/<!--[\s\S]*?-->/g, '').replace(/<\/?(?:span|code|strong|em|a)\b[^>]*>/g, '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();

/** The landing page content: from `<main id="landing"` to its closing tag. */
const main = (() => {
  const start = html.indexOf('<main id="landing"');
  assert.notEqual(start, -1, 'missing <main id="landing"');
  const end = html.indexOf('</main>', start);
  assert.notEqual(end, -1, 'missing </main> for the landing content');
  return html.slice(start, end);
})();

/** HTML of one landing section: from its opening tag to the next section (or the end of the landing content). */
const sec = (id) => {
  const start = main.indexOf(`<section id="${id}"`);
  assert.notEqual(start, -1, `missing <section id="${id}"`);
  const next = main.slice(start + 1).search(/<section id="/);
  return next === -1 ? main.slice(start) : main.slice(start, start + 1 + next);
};

test('document head', () => {
  assert.match(html, /^<!doctype html>/i);
  assert.match(html, /<html lang="en"/);
  assert.equal(
    text(/<title[^>]*>([\s\S]*?)<\/title>/.exec(html)[1]),
    'Type-Safe, Modular Building Blocks for Scala | ZIO Blocks',
  );
  assert.equal(
    decode(/<meta [^>]*name="description" content="([^"]*)"/.exec(html)[1]),
    'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.',
  );
  assert.match(html, /<link [^>]*rel="icon" href="\/brand\/zio-blocks-mark-favicon\.svg"/);
  assert.match(html, /<a class="[^"]*" href="#__docusaurus_skipToContent_fallback"/);
});

test('hero', () => {
  const hero = sec('hero');
  // The docs tagline is "Modular building blocks for modern Scala applications—no effect system required." The hero shows
  // the part before the dash in title case, with "Building Blocks" joined by a non-breaking space (U+00A0).
  assert.equal(site.tagline.split('—')[0], 'Modular building blocks for modern Scala applications');
  assert.equal(
    /<h1[^>]*>([\s\S]*?)<\/h1>/.exec(hero)[1],
    'Modular Building\u00a0Blocks for Modern Scala Applications',
  );
  assert.equal(text(/<p class="lp-lead"[^>]*>([\s\S]*?)<\/p>/.exec(hero)[1]), text(site.lead.replace(/\*+|`/g, '')));
  assert.equal(
    text(/<pre[\s\S]*?<\/pre>/.exec(hero)[0]),
    `${site.hero.install} ${site.hero.jsonCode} // ${site.hero.jsonResult}`,
  );
  assert.equal(text(/<p class="lp-stack"[^>]*>([\s\S]*?)<\/p>/.exec(hero)[1]), `Works with ${site.compatibility.join(' · ')}`);
  assert.match(hero, /href="\/docs\/"/);
  assert.match(hero, /href="https:\/\/github\.com\/zio\/zio-blocks"/);
});

test('the navbar and footer carry the brand lockups and the main links', () => {
  assert.match(html, /<img [^>]*src="\/brand\/zio-blocks-logo-on-dark\.svg"[^>]*alt="ZIO Blocks"|<img [^>]*alt="ZIO Blocks"[^>]*src="\/brand\/zio-blocks-logo-on-dark\.svg"/);
  const footer = html.slice(html.indexOf('<footer'), html.indexOf('</footer>'));
  assert.match(footer, /\/brand\/zio-blocks-logo-mono-white\.svg/);
  for (const href of ['/docs/', '/docs/reference/schema/', 'https://github.com/zio/zio-blocks']) {
    assert.match(footer, new RegExp(`href="${href.replace(/[./]/g, '\\$&')}"`));
  }
});

test('principles', () => {
  const s = sec('principles');
  const items = [...s.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/g)].map((m) => text(m[1]));
  assert.deepEqual(items, site.principles.map((p, i) => `0${i + 1} ${titleCase(p.name)} ${p.text}`));
});

test('deep dives render every panel visible without JavaScript', () => {
  const s = sec('deep-dives');
  const tabs = [...s.matchAll(/role="tab"[^>]*>([\s\S]*?)<\/button>/g)].map((m) => text(m[1]));
  assert.deepEqual(tabs, site.deepDives.map((d) => titleCase(d.title)));
  const panels = [...s.matchAll(/<div[^>]*role="tabpanel"[^>]*>/g)].map((m) => m[0]);
  assert.equal(panels.length, site.deepDives.length);
  for (const p of panels) assert.doesNotMatch(p, /\bhidden\b/);
  // The tablist itself is hidden until the page hydrates, so a visitor without JavaScript sees stacked panels only.
  assert.match(s, /<div[^>]*role="tablist"[^>]*\bhidden\b/);
  for (const d of site.deepDives) assert.match(s, new RegExp(`id="panel-${d.id}"[^>]*aria-labelledby="tab-${d.id}"|aria-labelledby="tab-${d.id}"[^>]*id="panel-${d.id}"`));
  // The first tab is the selected one and the only one in the tab order; each tab controls its own panel.
  const tabTags = [...s.matchAll(/<button[^>]*role="tab"[^>]*>/g)].map((m) => m[0]);
  assert.deepEqual(tabTags.map((t) => /aria-selected="(true|false)"/.exec(t)[1]), ['true', 'false', 'false', 'false']);
  assert.deepEqual(tabTags.map((t) => /tabindex="(-?\d)"/.exec(t)[1]), ['0', '-1', '-1', '-1']);
  assert.deepEqual(tabTags.map((t) => /aria-controls="([^"]*)"/.exec(t)[1]), site.deepDives.map((d) => `panel-${d.id}`));
});

test('schema panel lists the format chips derived from the catalog', () => {
  const s = sec('deep-dives');
  const chips = [...s.matchAll(/<li class="lp-chip"[^>]*>([\s\S]*?)<\/li>/g)].map((m) => text(m[1]));
  const codecs = site.categories.find((c) => c.name === 'Codecs').blocks.map((b) => b.name.replace(/ Codec$/, ''));
  assert.deepEqual(chips, ['JSON', ...codecs]);
});

test('text strips comments without inserting spaces', () => {
  assert.equal(text('<p>Sca<!-- -->la</p>'), 'Scala');
  assert.equal(text('a<!-- -->  b'), 'a b');
});

test('catalog renders every block as a visible tile with its artifact and docs link', () => {
  const s = sec('catalog');
  const tiles = [...s.matchAll(/<li class="lp-tile"[^>]*>([\s\S]*?)<\/li>/g)];
  const blocks = site.categories.flatMap((c) => c.blocks);
  assert.equal(tiles.length, blocks.length);
  assert.equal(blocks.length, site.blockCount);
  tiles.forEach((t, i) => {
    const b = blocks[i];
    assert.doesNotMatch(t[0], /^<li[^>]*\bhidden\b/);
    assert.equal(text(/<h4[^>]*>([\s\S]*?)<\/h4>/.exec(t[1])[1]), titleCase(b.name));
    assert.equal(/<h4[^>]*><a[^>]*href="([^"]*)"/.exec(t[1])[1], b.docsUrl);
    assert.equal(text(/<code class="lp-artifact"[^>]*>([\s\S]*?)<\/code>/.exec(t[1])[1]), b.artifact);
    // One "Learn More" button per tile, linking to the block's docs page; the visible text is "Learn More" and a visually hidden suffix names the block (descriptive link text for SEO).
    const learn = /<a class="lp-learn"[^>]*>([\s\S]*?)<\/a>/.exec(t[1]);
    assert.equal(/href="([^"]*)"/.exec(learn[0])[1], b.docsUrl);
    assert.equal(text(learn[1]), `Learn More about ${titleCase(b.name)}`);
    assert.match(learn[1], /^Learn More<span class="lp-sr"> about /);
    assert.equal((t[1].match(/class="lp-learn"/g) ?? []).length, 1);
    // The button shares one row with the platform / Scala badges.
    assert.equal(/<div class="lp-foot"[^>]*>\s*<p class="lp-badges"[^>]*>[\s\S]*?<\/p>\s*<a class="lp-learn"/.test(t[1]), true);
  });
});

test('consecutive small categories share a row while their tiles fit in three columns', () => {
  const s = sec('catalog');
  const rows = s.split('<div class="lp-row"').slice(1).map((chunk) => [...chunk.matchAll(/<h3[^>]*>([\s\S]*?)<\/h3>/g)].map((h) => text(h[1])));
  assert.deepEqual(rows.find((r) => r.includes('Resource Management')), ['Resource Management', 'Dependency Injection']);
  assert.deepEqual(rows.find((r) => r.includes('Streaming')), ['Streaming', 'Telemetry']);
  assert.deepEqual(rows.flat(), site.categories.map((c) => titleCase(c.name)));
  const size = new Map(site.categories.map((c) => [titleCase(c.name), c.blocks.length]));
  for (const r of rows) if (r.length > 1) assert.ok(r.reduce((n, name) => n + size.get(name), 0) <= 3, r.join(' + '));
  assert.equal(rows.filter((r) => r.length > 1).length, 2);
});

test('catalog has no filter controls, counter, empty-state message or buttons', () => {
  const s = sec('catalog');
  assert.equal(/data-filter|aria-pressed|data-status|data-empty/.test(s), false);
  assert.equal(/<button/.test(s), false);
});

test('page order, anchors, images, and leftovers', () => {
  const ids = [...main.matchAll(/<section id="([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual(ids, ['hero', 'principles', 'deep-dives', 'catalog']);
  const allIds = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  for (const [, target] of html.matchAll(/href="#([^"]+)"/g)) assert.ok(allIds.has(target), `dangling #${target}`);
  for (const img of html.matchAll(/<img\b[^>]*>/g)) assert.match(img[0], /\balt="[^"]+"/);
  assert.equal((html.match(/<h1\b/g) ?? []).length, 1);
  assert.equal(html.includes('@VERSION@'), false);
  for (const [, href] of main.matchAll(/href="(https?:[^"]+)"/g)) assert.match(href, /^https:/);
});

test('there are no copy buttons, copy script or copy live region on the landing page', () => {
  assert.equal(/data-copy|class="copy"|navigator\.clipboard|clean-btn/.test(main), false);
});

test('every title, heading and label on the landing page is in title case', () => {
  const all = (re, scope = main) => [...scope.matchAll(re)].map((m) => text(m[1]));
  assert.deepEqual(all(/<h2[^>]*>([\s\S]*?)<\/h2>/g), [
    'Use What You Need, Nothing More',
    'Four Blocks, in Code',
    'Take Only What You Need',
  ]);
  assert.deepEqual(
    all(/<p class="lp-label"[^>]*>([\s\S]*?)<\/p>/g)
      .map((l) => l.replace(/\u00a0/g, ' ').replace(/\s+/g, ' '))
      .filter((l) => !l.startsWith('The ')),
    ['01 Principles', '02 Deep Dives', 'One Schema, Many Formats', '03 Block Catalog'],
  );
  assert.deepEqual(all(/<p class="lp-label"[^>]*>(The [\s\S]*?)<\/p>/g), site.deepDives.flatMap(() => ['The Problem', 'The Solution']));
  assert.deepEqual(all(/<h3[^>]*>([\s\S]*?)<\/h3>/g), [
    ...site.deepDives.map((d) => titleCase(d.title)),
    ...site.categories.map((c) => titleCase(c.name)),
  ]);
  const blocks = site.categories.flatMap((c) => c.blocks);
  assert.deepEqual(all(/<h4[^>]*>([\s\S]*?)<\/h4>/g), blocks.map((b) => titleCase(b.name)));
  assert.deepEqual(all(/<strong[^>]*>([\s\S]*?)<\/strong>/g, sec('principles')), site.principles.map((p) => titleCase(p.name)));
});

test('there is no switching-cost section and no guides list on the landing page', () => {
  assert.equal(/switching/i.test(main), false);
});
