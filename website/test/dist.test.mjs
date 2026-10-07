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
  return html.slice(start, html.indexOf('</main>', start));
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
    'Modular Building Blocks for Modern Scala Applications',
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
