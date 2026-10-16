import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import { FAVICON, NAV_LINKS, STYLESHEET, navbarHtml, themeApiPage } from '../../scripts/lib/api-theme.mjs';

// The page skeleton Scaladoc 3.9.0 generates (zio/blocks/schema/Schema.html, trimmed to the parts the theme touches).
const PAGE =
  '<html><head><meta charset="utf-8"></meta><title>Schema</title><link rel="shortcut icon" type="image/x-icon" href="../../../favicon.ico">' +
  '<link rel="stylesheet" href="../../../styles/theme/bundle.css"></head>' +
  '<body><div id=""><div id="header" class="body-small"><div class="header-container-left"><a href="../../../" class="logo-container">' +
  '<span id="project-logo" class="project-logo"><img src="../../../project-logo/zio-blocks-mark.svg"></img></span>' +
  '<span class="project-name h300">ZIO Blocks</span></a><span onclick="dropdownHandler(event)" class="text-button with-arrow" id="dropdown-trigger">' +
  '<a><div class="projectVersion">0.0.56-SNAPSHOT</div></a></span><div id="version-dropdown" class="dropdown-menu"></div></div>' +
  '<div class="header-container-right"><button id="search-toggle" class="icon-button"></button><span id="theme-toggle" class="icon-button"></span>' +
  '<span id="mobile-menu-toggle" class="icon-button hamburger"></span></div></div>' +
  '<div id="mobile-menu"><div class="mobile-menu-header body-small"><span class="mobile-menu-logo">' +
  '<span id="project-logo" class="project-logo"><img src="../../../project-logo/zio-blocks-mark.svg"></img></span>' +
  '<span class="project-name h300">ZIO Blocks</span></span><button id="mobile-menu-close" class="icon-button close"></button></div>' +
  '<div class="mobile-menu-container body-medium"><input id="mobile-scaladoc-searchbar-input" class="scaladoc-searchbar-input" type="search">' +
  '</input><span id="mobile-theme-toggle" class="mobile-menu-item mode"></span></div></div><div id="main"></div></div></body></html>';

const PROMPT = 'Fetch https://example.dev/start.md and "follow" <it>';
const count = (s, needle) => s.split(needle).length - 1;

test('the API pages get the site navbar in place of the Scaladoc header', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.equal(count(html, '<div id="header"'), 1);
  assert.match(html, /<div id="header" class="zb-navbar">/);
  assert.match(html, /<a class="zb-navbar__brand" href="\/"><img src="\/brand\/zio-blocks-logo-on-dark\.svg" alt="ZIO Blocks"/);
  assert.ok(!html.includes('projectVersion'), 'the version picker is gone');
  assert.ok(!html.includes('id="theme-toggle"'), 'Scaladoc\'s two-state toggle is replaced');
  // Scaladoc's scripts look these up by id at startup and throw without them.
  for (const id of ['search-toggle', 'mobile-menu-toggle', 'dropdown-trigger', 'version-dropdown', 'mobile-theme-toggle', 'mobile-menu-close'])
    assert.equal(count(html, `id="${id}"`), 1, id);
});

test('the navbar links match the Docusaurus navbar, with API as the current page', () => {
  const { themeConfig } = createRequire(import.meta.url)('../../docusaurus.config.js');
  const site = themeConfig.navbar.items.map((i) => ({ label: i.label, href: (i.to ?? i.href).replace('pathname://', '') }));
  assert.deepEqual(NAV_LINKS.map(({ label, href }) => ({ label, href })), site);
  const nav = navbarHtml({ prompt: PROMPT });
  assert.match(nav, /<a class="zb-navbar__link zb-navbar__link--active" href="\/api\/" aria-current="page">API<\/a>/);
  assert.match(nav, /<a class="zb-navbar__link" href="https:\/\/github\.com\/zio\/zio-blocks" target="_blank" rel="noopener noreferrer">GitHub<svg/);
});

test('the onboard button carries the prompt, escaped for the attribute', () => {
  assert.ok(
    navbarHtml({ prompt: PROMPT }).includes(
      'data-onboard-prompt="Fetch https://example.dev/start.md and &quot;follow&quot; &lt;it>"',
    ),
  );
});

test('the mobile menu gets the site logo and links, and the stylesheet comes last in <head>', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.match(html, /<a class="mobile-menu-logo" href="\/"><img src="\/brand\/zio-blocks-logo-on-dark\.svg"[^>]*><\/a><button id="mobile-menu-close"/);
  assert.match(
    html,
    /<nav class="zb-mobile-links"><a class="zb-mobile-link" href="\/overview">Docs<\/a><a class="zb-mobile-link zb-mobile-link--active" href="\/api\/" aria-current="page">API<\/a>.*<\/nav><span id="mobile-theme-toggle"/,
  );
  assert.ok(html.includes(`<link rel="stylesheet" href="${STYLESHEET}"></head>`));
  assert.equal(count(html, 'project-logo'), 0);
});

test('the API pages use the site favicon, the one docusaurus.config.js names', () => {
  const { favicon } = createRequire(import.meta.url)('../../docusaurus.config.js');
  assert.equal(FAVICON, `/${favicon}`);
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.equal(count(html, 'rel="icon"'), 1);
  assert.equal(count(html, 'rel="shortcut icon"'), 0);
  assert.ok(html.includes(`<link rel="icon" href="${FAVICON}">`));
  assert.ok(!html.includes('favicon.ico'));
});

test('a page whose markup no longer matches fails loudly', () => {
  assert.throws(() => themeApiPage(PAGE.replace('<div id="mobile-menu">', '<div id="menu">'), { prompt: PROMPT }), /header/);
  assert.throws(() => themeApiPage(PAGE.replace('<span id="mobile-theme-toggle"', '<span'), { prompt: PROMPT }), /mobile theme toggle/);
});

// Runs src/api-theme/theme.js against a minimal browser stand-in.
function loadThemeScript({ stored = null, systemDark = false } = {}) {
  const store = new Map(stored ? [['theme', stored]] : []);
  const classes = new Set();
  const attrs = {};
  const listeners = {};
  const toggle = { title: '', attrs: {}, setAttribute(k, v) { this.attrs[k] = v; }, addEventListener(_, f) { this.click = f; } };
  const document = {
    documentElement: {
      classList: { toggle: (c, on) => (on ? classes.add(c) : classes.delete(c)) },
      setAttribute: (k, v) => (attrs[k] = v),
    },
    querySelectorAll: () => [toggle],
    addEventListener() {},
  };
  const window = {
    matchMedia: () => ({ matches: systemDark, addEventListener() {} }),
    addEventListener: (type, f) => (listeners[type] = f),
  };
  const localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, v),
    removeItem: (k) => store.delete(k),
  };
  vm.runInNewContext(readFileSync(new URL('../../src/api-theme/theme.js', import.meta.url), 'utf8'), {
    window, document, localStorage, Element: class {},
  });
  listeners.DOMContentLoaded();
  return { store, classes, attrs, toggle };
}

test('the colour mode is the one Docusaurus stores, applied before first paint', () => {
  const dark = loadThemeScript({ stored: 'dark' });
  assert.ok(dark.classes.has('theme-dark'));
  assert.equal(dark.attrs['data-theme'], 'dark');
  assert.equal(dark.attrs['data-theme-choice'], 'dark');
  const system = loadThemeScript({ systemDark: true });
  assert.ok(system.classes.has('theme-dark'));
  assert.equal(system.attrs['data-theme-choice'], 'system');
  assert.equal(system.toggle.title, 'system mode');
});

test('the toggle cycles system -> light -> dark -> system and writes localStorage "theme" like Docusaurus', () => {
  const t = loadThemeScript({ systemDark: true });
  const seen = [];
  for (let i = 0; i < 3; i++) {
    t.toggle.click();
    seen.push([t.store.get('theme') ?? null, t.attrs['data-theme-choice'], t.classes.has('theme-dark')]);
  }
  assert.deepEqual(seen, [
    ['light', 'light', false],
    ['dark', 'dark', true],
    [null, 'system', true],
  ]);
  assert.equal(t.toggle.attrs['aria-label'], 'Switch between dark and light mode (currently system mode)');
});
