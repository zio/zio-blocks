import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import {
  FAVICON,
  NAV_LINKS,
  STYLESHEET,
  breadcrumbsHtml,
  extensionless,
  extensionlessLinks,
  extensionlessSearchData,
  extensionlessUxJs,
  hoistSideMenu,
  navbarHtml,
  nestedTypePaths,
  relocatePage,
  relocateSideMenu,
  themeApiPage,
} from '../../scripts/lib/api-theme.mjs';

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
  '</input><span id="mobile-theme-toggle" class="mobile-menu-item mode"></span></div></div>' +
  '<div id="footer" class="body-small"><div class="left-container">Generated with</div><div class="right-container">' +
  '<a href="https://github.com/zio/zio-blocks"><button class="icon-button gh"></button></a><div class="text"></div></div>' +
  '<div class="text-mobile"></div></div>' +
  '<div id="main"><div class="breadcrumbs container"><a href="../../../index.html">ZIO Blocks</a>/<a href="../../../zio.html">zio</a>/' +
  '<a href="../../blocks.html">zio.blocks</a>/<a href="../schema.html">zio.blocks.schema</a>/<a href="Schema.html">Schema</a></div>' +
  '<div id="content" class="body-medium"><div><div class="cover-header"><h1 class="h600">Schema</h1></div></div>' +
  '<div id="footer" class="body-small mobile-footer"><div class="left-container">Generated with</div>' +
  '<div class="right-container"><a href="https://github.com/zio/zio-blocks"><button class="icon-button gh"></button></a>' +
  '<div class="text"></div></div><div class="text-mobile"></div></div></div></div></div></body></html>';

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
    /<nav class="zb-mobile-links"><a class="zb-mobile-link" href="\/overview">Docs<\/a><a class="zb-mobile-link" href="\/guides\/async-getting-started">Guides<\/a><a class="zb-mobile-link zb-mobile-link--active" href="\/api\/" aria-current="page">API<\/a>.*<\/nav><span id="mobile-theme-toggle"/,
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

test('the breadcrumbs read Home > API > packages > page, at the top of the text column', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.ok(!html.includes('class="breadcrumbs container"'), "Scaladoc's trail is gone");
  const nav = /<div id="content" class="body-medium"><div>(<nav class="zb-breadcrumbs" aria-label="Breadcrumbs">.*?<\/nav>)<div class="cover-header">/.exec(html);
  assert.ok(nav, 'the breadcrumbs open the text column');
  const crumbs = [...nav[1].matchAll(/<li class="([^"]*)">(?:<a (?:aria-label="Home page" )?class="zb-breadcrumbs__link" href="([^"]*)">(?:<svg[^]*?<\/svg>|<span>([^<]*)<\/span>)<\/a>|<span class="zb-breadcrumbs__link">([^<]*)<\/span>)<\/li>/g)]
    .map(([, cls, href, label, current]) => (current ? `[${current}]` : `${label ?? 'home'} ${href}`) + (cls.includes('--active') ? ' (active)' : ''));
  assert.deepEqual(crumbs, ['home /', 'API /api/', 'zio.blocks ../../blocks', 'schema ../schema', '[Schema] (active)']);
});

test('on the API index the trail ends at API', () => {
  assert.ok(
    breadcrumbsHtml([{ href: 'index.html', label: 'ZIO Blocks' }]).endsWith(
      '<li class="zb-breadcrumbs__item zb-breadcrumbs__item--active"><span class="zb-breadcrumbs__link">API</span></li></ul></nav>',
    ),
  );
});

test('the side menu starts at the packages under zio, which keep their full names', () => {
  const pkg = (name, children = []) => ({ name, location: name.split('.'), kind: 'package', children });
  const cls = (name) => ({ name, location: ['x', name], kind: 'class', children: [] });
  const menu = { docs: null, api: { name: 'ZIO Blocks', location: ['index'], kind: null, children: [
    pkg('zio', [pkg('zio.blocks', [pkg('zio.blocks.schema', [cls('Schema')])]), pkg('zio.http', [cls('Header')])]),
  ] } };
  const hoisted = hoistSideMenu(menu);
  const names = (items) => items.map((i) => [i.name, names(i.children)]);
  assert.deepEqual(names(hoisted.api.children), [
    ['zio.blocks', [['zio.blocks.schema', [['Schema', []]]]]],
    ['zio.http', [['Header', []]]],
  ]);
  assert.deepEqual(hoisted.api.children[0].children[0].location, ['zio', 'blocks', 'schema'], 'links keep their locations');
  assert.throws(() => hoistSideMenu({ api: { children: [pkg('zio'), pkg('scala')] } }), /only API root/);
});

test('API URLs have no .html, and an index page is its folder', () => {
  const cases = {
    'Chunk.html': 'Chunk',
    '../chunk/Chunk.html#map-1': '../chunk/Chunk#map-1',
    'zio/blocks/html.html': 'zio/blocks/html',
    'MediaTypes$$application$.html#vnd.dtg.local.html-0': 'MediaTypes$$application$#vnd.dtg.local.html-0',
    'Other#anchor.html': 'Other#anchor.html',
    '../../../index.html': '../../../',
    'index.html#': './#',
    'Schema.scala': 'Schema.scala',
  };
  for (const [from, to] of Object.entries(cases)) assert.equal(extensionless(from), to, from);
});

test('only relative links lose .html; the site and other sites keep theirs', () => {
  assert.equal(
    extensionlessLinks(
      '<a href="Chunk.html">c</a><a href="#m">m</a><a href="/api/">api</a>' +
        '<a href="https://docs.oracle.com/javase/21/docs/api/java.base/java/lang/Object.html">o</a><a href="//cdn.example/x.html">x</a>',
    ),
    '<a href="Chunk">c</a><a href="#m">m</a><a href="/api/">api</a>' +
      '<a href="https://docs.oracle.com/javase/21/docs/api/java.base/java/lang/Object.html">o</a><a href="//cdn.example/x.html">x</a>',
  );
  assert.match(themeApiPage(PAGE, { prompt: PROMPT }), /href="\.\.\/\.\.\/blocks"><span>zio\.blocks<\/span>/);
});

test('search results and side-menu links are extensionless too', () => {
  assert.equal(
    extensionlessSearchData(
      'pages = [{"l":"index.html#","n":"ZIO Blocks"},{"l":"zio/blocks/chunk/Chunk.html#","n":"Chunk"},' +
        '{"l":"zio/blocks/chunk/Chunk.html#map-1","d":"zio.blocks.html"}]',
    ),
    'pages = [{"l":"./","n":"ZIO Blocks"},{"l":"zio/blocks/chunk/Chunk","n":"Chunk"},' +
      '{"l":"zio/blocks/chunk/Chunk#map-1","d":"zio.blocks.html"}]',
  );
  const ux = extensionlessUxJs('function linkTo(loc) {\n    return `${baseUrl.join("/")}/${loc.join("/")}.html`;\n  }');
  const linkTo = new Function('baseUrl', ux.replace(/^function linkTo\(loc\) \{|\}$/g, '').replace('return', 'return (loc) =>'));
  assert.equal(linkTo(['', 'api'])(['zio', 'blocks', 'chunk', 'Chunk']), '/api/zio/blocks/chunk/Chunk');
  assert.equal(linkTo(['', 'api'])(['index']), '/api/');
  assert.throws(() => extensionlessUxJs('return `${baseUrl}/${loc.join("/")}.html`;'), /side-menu links/);
});

// A side menu shaped like Scaladoc's: nested types are children of the type they are declared in.
const node = (location, kind, children = []) => ({ name: location.split('/').pop(), location: location.split('/'), kind, children });
const NESTED = { docs: null, api: { name: 'ZIO Blocks', location: ['index'], kind: null, children: [
  node('zio', 'package', [node('zio/blocks', 'package', [node('zio/blocks/chunk', 'package', [
    node('zio/blocks/chunk/ChunkBuilder', 'class'),
    node('zio/blocks/chunk/ChunkBuilder$', 'object', [node('zio/blocks/chunk/ChunkBuilder$$Int', 'class')]),
  ]), node('zio/blocks/schema/comptime', 'package', [
    node('zio/blocks/schema/comptime/Allows$', 'object', [node('zio/blocks/schema/comptime/Allows$$$bar$', 'object')]),
  ])]), node('zio/http', 'package', [
    node('zio/http/Header$', 'object', [node('zio/http/Header$$ContentDisposition$', 'object', [
      node('zio/http/Header$$ContentDisposition$$Attachment', 'class'),
    ])]),
    node('zio/http/Version$', 'object', [node('zio/http/Version$$HTTP$div1/0$$', 'object')]),
  ])]),
] } };

test('nested types move into folders named after the type they are declared in', () => {
  assert.deepEqual(Object.fromEntries(nestedTypePaths(NESTED)), {
    'zio/blocks/chunk/ChunkBuilder$$Int': 'zio/blocks/chunk/ChunkBuilder/Int',
    'zio/blocks/schema/comptime/Allows$$$bar$': 'zio/blocks/schema/comptime/Allows/$bar$',
    'zio/http/Header$$ContentDisposition$': 'zio/http/Header/ContentDisposition$',
    'zio/http/Header$$ContentDisposition$$Attachment': 'zio/http/Header/ContentDisposition/Attachment',
    'zio/http/Version$$HTTP$div1/0$$': 'zio/http/Version/HTTP$div1/0$$',
  });
});

test('a moved page is rewritten for its new folder, and links to moved pages follow them', () => {
  const moves = nestedTypePaths(NESTED);
  const page = (rawLocation, root, body) =>
    `<html data-pathToRoot="${root}" data-rawLocation="${rawLocation}"><head><script>var pathToRoot = "${root}";</script>` +
    `<link rel="stylesheet" href="${root}styles/a.css"></head><body>${body}</body></html>`;
  const moved = relocatePage(
    page('zio/blocks/chunk/ChunkBuilder$$Int', '../../../', '<a href="ChunkBuilder.html">up</a><a href="Chunk.html#map">c</a><a href="#m">m</a><a href="/api/">api</a>'),
    { from: 'zio/blocks/chunk/ChunkBuilder$$Int', to: 'zio/blocks/chunk/ChunkBuilder/Int', moves },
  );
  assert.equal(
    moved,
    page('zio/blocks/chunk/ChunkBuilder/Int', '../../../../', '<a href="../ChunkBuilder.html">up</a><a href="../Chunk.html#map">c</a><a href="#m">m</a><a href="/api/">api</a>'),
  );
  const staying = '<a href="ChunkBuilder$$Int.html#x">Int</a><a href="Chunk.html">c</a><a href="../../../index.html">i</a>';
  assert.equal(
    relocatePage(staying, { from: 'zio/blocks/chunk/ChunkBuilder$', to: 'zio/blocks/chunk/ChunkBuilder$', moves }),
    '<a href="ChunkBuilder/Int.html#x">Int</a><a href="Chunk.html">c</a><a href="../../../index.html">i</a>',
  );
});

test('the side menu and the search index point at the moved pages', () => {
  const moves = nestedTypePaths(NESTED);
  const locations = [];
  const walk = (i) => (locations.push(i.location.join('/')), i.children.forEach(walk));
  relocateSideMenu(NESTED, moves).api.children.forEach(walk);
  assert.ok(locations.includes('zio/blocks/chunk/ChunkBuilder/Int'));
  assert.ok(!locations.some((l) => l.includes('ChunkBuilder$$')));
  assert.equal(
    extensionlessSearchData('{"l":"zio/blocks/chunk/ChunkBuilder$$Int.html#"},{"l":"zio/blocks/chunk/ChunkBuilder$$Int.html#add-1"}', moves),
    '{"l":"zio/blocks/chunk/ChunkBuilder/Int"},{"l":"zio/blocks/chunk/ChunkBuilder/Int#add-1"}',
  );
});

test('both "Generated with Scaladoc" footers are gone, and nothing after them', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.ok(!html.includes('id="footer"'));
  assert.ok(!html.includes('Generated with'));
  assert.ok(html.endsWith('<h1 class="h600">Schema</h1></div></div></div></div></div></body></html>'));
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
