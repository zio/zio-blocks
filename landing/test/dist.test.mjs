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
    'ZIO Blocks — Type-safe, modular building blocks for Scala',
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
  const headline = site.tagline.split('—')[0].trim().replace('building blocks', 'building blocks');
  assert.equal(/<h1[^>]*>([\s\S]*?)<\/h1>/.exec(hero)[1], headline);
  assert.equal(text(/<p class="lead[^"]*"[^>]*>([\s\S]*?)<\/p>/.exec(hero)[1]), text(site.lead.replace(/\*+|`/g, '')));
  assert.equal(
    text(/<pre[\s\S]*?<\/pre>/.exec(hero)[0]),
    `${site.hero.install} ${site.hero.jsonCode} // ${site.hero.jsonResult}`,
  );
  assert.equal(decode(/data-copy="([^"]*)"/.exec(hero)[1]), site.hero.install);
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
  const tiles = [...s.matchAll(/<li class="tile"[^>]*data-tile[^>]*>([\s\S]*?)<\/li>/g)];
  const blocks = site.categories.flatMap((c) => c.blocks);
  assert.equal(tiles.length, blocks.length);
  assert.equal(blocks.length, site.blockCount);
  tiles.forEach((t, i) => {
    const b = blocks[i];
    assert.doesNotMatch(t[0], /^<li[^>]*\bhidden\b/);
    assert.equal(text(/<h4[^>]*>([\s\S]*?)<\/h4>/.exec(t[1])[1]), b.name);
    assert.equal(/<h4[^>]*><a href="([^"]*)"/.exec(t[1])[1], b.docsUrl);
    assert.equal(text(/<code class="artifact"[^>]*>([\s\S]*?)<\/code>/.exec(t[1])[1]), b.artifact);
    assert.equal(decode(/data-copy="([^"]*)"/.exec(t[1])[1]), b.artifact);
  });
});

test('catalog tiles carry filterable data attributes', () => {
  const s = sec('catalog');
  const first = site.categories[0].blocks[0];
  assert.match(
    s,
    new RegExp(`data-category="${site.categories[0].name.replace(/&/g, '&amp;')}" data-platforms="${first.platforms.join(' ')}" data-scala="${first.scala.join(' ')}"`),
  );
});

test('catalog filter controls list each category, platform and Scala version once', () => {
  const s = sec('catalog');
  const group = (name) => {
    const g = new RegExp(`data-filter="${name}"[^>]*>([\\s\\S]*?)</div>`).exec(s)[1];
    return [...g.matchAll(/<button[^>]*>([\s\S]*?)<\/button>/g)].map((m) => text(m[1]));
  };
  assert.deepEqual(group('category'), ['All', ...site.categories.map((c) => c.name)]);
  assert.deepEqual(group('platform'), ['Any', 'JVM', 'JS']);
  assert.deepEqual(group('scala'), ['Any', '2.13', '3.x']);
  assert.equal(text(/data-status[^>]*>([\s\S]*?)<\//.exec(s)[1]), `${site.blockCount} of ${site.blockCount} blocks`);
});

test('catalog filter containers are named groups', () => {
  const s = sec('catalog');
  const open = (name) => /^<div[^>]*>/.exec(/<div[^>]*data-filter="NAME"[^>]*>/.source && new RegExp(`<div[^>]*data-filter="${name}"[^>]*>`).exec(s)[0])[0];
  const attr = (tag, a) => new RegExp(`\\b${a}="([^"]*)"`).exec(tag)?.[1];
  for (const [name, label] of [['category', 'Category'], ['platform', 'Platform'], ['scala', 'Scala version']]) {
    const tag = open(name);
    assert.equal(attr(tag, 'role'), 'group');
    assert.equal(attr(tag, 'aria-label'), label);
  }
});

test('switching cost features the migration guide and lists the rest', () => {
  const s = sec('switching');
  const migration = site.guides.find((g) => /migrat/i.test(g.title));
  const feature = /<a class="feature" href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/.exec(s);
  assert.equal(feature[1], migration.url);
  const strip = (t) => t.replace(/`/g, '');
  assert.equal(text(feature[2]), strip(`${migration.title} ${migration.description}`));
  const others = [...s.matchAll(/<li[^>]*><a href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/g)].map((m) => [m[1], text(m[2])]);
  assert.deepEqual(others, site.guides.filter((g) => g !== migration).map((g) => [g.url, g.title]));
  const descs = [...s.matchAll(/<li[^>]*><a [^>]*>[\s\S]*?<\/a>\s*<span[^>]*>([\s\S]*?)<\/span>\s*<\/li>/g)].map((m) => text(m[1]));
  assert.deepEqual(descs, site.guides.filter((g) => g !== migration).map((g) => `\u2014 ${strip(g.description)}`));
});

test('footer repeats the install line and links out', () => {
  const f = between('<footer id="footer"', '</footer>');
  assert.equal(decode(/data-copy="([^"]*)"/.exec(f)[1]), site.hero.install);
  assert.match(f, /src="\/brand\/zio-blocks-logo-mono-white\.svg"[^>]*alt="ZIO Blocks"/);
  for (const href of ['https://zio.dev/zio-blocks/', 'https://github.com/zio/zio-blocks']) {
    assert.match(f, new RegExp(`href="${href.replace(/[./]/g, '\\$&')}"`));
  }
});

test('page order, anchors, images, and leftovers', () => {
  const ids = [...html.matchAll(/<(?:section|footer) id="([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual(ids, ['hero', 'principles', 'deep-dives', 'catalog', 'switching', 'footer']);
  const allIds = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  for (const [, target] of html.matchAll(/href="#([^"]+)"/g)) assert.ok(allIds.has(target), `dangling #${target}`);
  for (const img of html.matchAll(/<img\b[^>]*>/g)) assert.match(img[0], /\balt="[^"]+"/);
  assert.equal((html.match(/<h1\b/g) ?? []).length, 1);
  assert.equal(html.includes('@VERSION@'), false);
  for (const [, href] of html.matchAll(/href="(https?:[^"]+)"/g)) assert.match(href, /^https:/);
});

test('exactly one polite live region announces copy results', () => {
  assert.equal(html.match(/data-copy-status/g).length, 1);
  assert.match(html, /<p class="sr-only" role="status" aria-live="polite" data-copy-status/);
});
