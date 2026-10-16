// Re-skins the Scaladoc pages under /api so they share the site's navbar, fonts and colour mode. Scaladoc is a separate
// generator: `sbt docs/generateApiDocs` copies its HTML into static/api, and Docusaurus publishes those files untouched,
// outside its layout. scripts/theme-api.mjs applies `themeApiPage` to every page of build/api after `docusaurus build`.

// Icons, copied from the Docusaurus navbar so both bars draw the same marks.
const ICONS = {
  menu: '<svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true"><path stroke="currentColor" stroke-linecap="round" stroke-miterlimit="10" stroke-width="2" d="M4 7h22M4 15h22M4 23h22"/></svg>',
  external:
    '<svg width="13.5" height="13.5" viewBox="0 0 24 24" aria-label="(opens in new tab)" class="zb-navbar__external"><path fill="currentColor" d="M21 13v10h-21v-19h12v2h-10v15h17v-8h2zm3-12h-10.988l4.035 4-6.977 7.07 2.828 2.828 6.977-7.07 4.125 4.172v-11z"/></svg>',
  search:
    '<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M8.5 15a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM13.3 13.3 18 18"/></svg>',
  terminal:
    '<svg class="zb-onboard__idle" viewBox="0 0 576 512" width="1em" height="1em" aria-hidden="true"><path fill="currentColor" d="M9.4 86.6C-3.1 74.1-3.1 53.9 9.4 41.4s32.8-12.5 45.3 0l192 192c12.5 12.5 12.5 32.8 0 45.3l-192 192c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3L178.7 256 9.4 86.6zM256 416l288 0c17.7 0 32 14.3 32 32s-14.3 32-32 32l-288 0c-17.7 0-32-14.3-32-32s14.3-32 32-32z"/></svg>',
  check:
    '<svg class="zb-onboard__done" viewBox="0 0 448 512" width="1em" height="1em" aria-hidden="true"><path fill="currentColor" d="M438.6 105.4c12.5 12.5 12.5 32.8 0 45.3l-256 256c-12.5 12.5-32.8 12.5-45.3 0l-128-128c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0L160 338.7 393.4 105.4c12.5-12.5 32.8-12.5 45.3 0z"/></svg>',
  light:
    '<svg class="zb-mode__light" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M12,9c1.65,0,3,1.35,3,3s-1.35,3-3,3s-3-1.35-3-3S10.35,9,12,9 M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5s5-2.24,5-5 S14.76,7,12,7L12,7z M2,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S1.45,13,2,13z M20,13l2,0c0.55,0,1-0.45,1-1 s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S19.45,13,20,13z M11,2v2c0,0.55,0.45,1,1,1s1-0.45,1-1V2c0-0.55-0.45-1-1-1S11,1.45,11,2z M11,20v2c0,0.55,0.45,1,1,1s1-0.45,1-1v-2c0-0.55-0.45-1-1-1C11.45,19,11,19.45,11,20z M5.99,4.58c-0.39-0.39-1.03-0.39-1.41,0 c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0s0.39-1.03,0-1.41L5.99,4.58z M18.36,16.95 c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0c0.39-0.39,0.39-1.03,0-1.41 L18.36,16.95z M19.42,5.99c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41 s1.03,0.39,1.41,0L19.42,5.99z M7.05,18.36c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06 c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L7.05,18.36z"/></svg>',
  dark: '<svg class="zb-mode__dark" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M9.37,5.51C9.19,6.15,9.1,6.82,9.1,7.5c0,4.08,3.32,7.4,7.4,7.4c0.68,0,1.35-0.09,1.99-0.27C17.45,17.19,14.93,19,12,19 c-3.86,0-7-3.14-7-7C5,9.07,6.81,6.55,9.37,5.51z M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9s9-4.03,9-9c0-0.46-0.04-0.92-0.1-1.36 c-0.98,1.37-2.58,2.26-4.4,2.26c-2.98,0-5.4-2.42-5.4-5.4c0-1.81,0.89-3.42,2.26-4.4C12.92,3.04,12.46,3,12,3L12,3z"/></svg>',
  system:
    '<svg class="zb-mode__system" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="m12 21c4.971 0 9-4.029 9-9s-4.029-9-9-9-9 4.029-9 9 4.029 9 9 9zm4.95-13.95c1.313 1.313 2.05 3.093 2.05 4.95s-0.738 3.637-2.05 4.95c-1.313 1.313-3.093 2.05-4.95 2.05v-14c1.857 0 3.637 0.737 4.95 2.05z"/></svg>',
};

// The navbar items of docusaurus.config.js, in the same order, with API as the current page.
export const NAV_LINKS = [
  { label: 'Docs', href: '/overview' },
  { label: 'API', href: '/api/', current: true },
  { label: 'Blocks', href: '/#catalog' },
  { label: 'GitHub', href: 'https://github.com/zio/zio-blocks', external: true },
];

const escapeAttr = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');

function linkHtml({ label, href, current, external }, cls) {
  const attrs = [`class="${cls}${current ? ` ${cls}--active` : ''}"`, `href="${href}"`];
  if (current) attrs.push('aria-current="page"');
  if (external) attrs.push('target="_blank" rel="noopener noreferrer"');
  return `<a ${attrs.join(' ')}>${label}${external ? ICONS.external : ''}</a>`;
}

/**
 * The site navbar, built from Scaladoc's own hooks so its scripts keep working: `#search-toggle` opens the search bar,
 * `#mobile-menu-toggle` opens the mobile menu, and the version dropdown it wires at startup stays in the page, hidden
 * (its script throws without it).
 */
export function navbarHtml({ prompt }) {
  return (
    '<div id="header" class="zb-navbar">' +
    '<div class="zb-navbar__left">' +
    `<span id="mobile-menu-toggle" class="zb-navbar__toggle" role="button" tabindex="0" aria-label="Toggle navigation bar">${ICONS.menu}</span>` +
    '<a class="zb-navbar__brand" href="/"><img src="/brand/zio-blocks-logo-on-dark.svg" alt="ZIO Blocks" width="160" height="37"></a>' +
    '<span id="dropdown-trigger" hidden></span><div id="version-dropdown" hidden></div>' +
    '</div>' +
    '<div class="zb-navbar__right">' +
    NAV_LINKS.map((l) => linkHtml(l, 'zb-navbar__link')).join('') +
    `<button type="button" id="search-toggle" class="zb-navbar__icon" title="Search the API" aria-label="Search the API">${ICONS.search}</button>` +
    `<button type="button" class="zb-navbar__icon zb-onboard" data-onboard-prompt="${escapeAttr(prompt)}" title="Copy the prompt to onboard your coding agent to ZIO Blocks" aria-label="Copy the ZIO Blocks agent onboarding prompt to the clipboard">${ICONS.terminal}${ICONS.check}</button>` +
    `<button type="button" class="zb-navbar__icon zb-mode" data-color-mode-toggle title="system mode" aria-label="Switch between dark and light mode (currently system mode)">${ICONS.light}${ICONS.dark}${ICONS.system}</button>` +
    '</div>' +
    '</div>'
  );
}

const HEADER = /<div id="header"[\s\S]*?(?=<div id="mobile-menu">)/;
const MOBILE_LOGO = /<span class="mobile-menu-logo">[\s\S]*?(?=<button id="mobile-menu-close")/;
const MOBILE_THEME = '<span id="mobile-theme-toggle"';
export const STYLESHEET = '/api/styles/zio-blocks.css';
// docusaurus.config.js `favicon`, which Docusaurus serves from static/.
export const FAVICON = '/brand/zio-blocks-mark-favicon.svg';
const SCALADOC_FAVICON = /<link rel="shortcut icon"[^>]*>/;

function replaceOnce(html, pattern, replacement, what) {
  if (!(typeof pattern === 'string' ? html.includes(pattern) : pattern.test(html)))
    throw new Error(`Scaladoc page has no ${what}; did a Scaladoc upgrade change its markup?`);
  return html.replace(pattern, replacement);
}

const HOME_ICON =
  '<svg viewBox="0 0 24 24" class="zb-breadcrumbs__home"><path d="M10 19v-5h4v5c0 .55.45 1 1 1h3c.55 0 1-.45 1-1v-7h1.7c.46 0 .68-.57.33-.87L12.67 3.6c-.38-.34-.96-.34-1.34 0l-8.36 7.53c-.34.3-.13.87.33.87H5v7c0 .55.45 1 1 1h3c.55 0 1-.45 1-1z" fill="currentColor"></path></svg>';
const CRUMBS = /<div class="breadcrumbs container">([\s\S]*?)<\/div>/;
const CONTENT_START = /<div id="content"[^>]*><div>/;

/**
 * The docs breadcrumbs (Docusaurus' theme-doc-breadcrumbs markup, with zb- classes because Scaladoc styles
 * .breadcrumbs) for a Scaladoc trail, given as its links `[{ href, label }]`: the home icon, then API for Scaladoc's
 * index entry, then the packages under `zio` by their last name segment, as the side menu shows them, and the current
 * page last, not linked.
 */
export function breadcrumbsHtml(trail) {
  const [, ...rest] = trail; // the first entry is Scaladoc's index page, shown as API
  const packages = rest.slice(0, -1).filter((c) => c.label !== 'zio');
  const items = [
    `<li class="zb-breadcrumbs__item"><a aria-label="Home page" class="zb-breadcrumbs__link" href="/">${HOME_ICON}</a></li>`,
  ];
  const current = rest.length ? rest[rest.length - 1] : null;
  items.push(
    current
      ? '<li class="zb-breadcrumbs__item"><a class="zb-breadcrumbs__link" href="/api/"><span>API</span></a></li>'
      : '<li class="zb-breadcrumbs__item zb-breadcrumbs__item--active"><span class="zb-breadcrumbs__link">API</span></li>',
  );
  for (const { href, label } of packages)
    items.push(
      `<li class="zb-breadcrumbs__item"><a class="zb-breadcrumbs__link" href="${href}"><span>${label.split('.').pop()}</span></a></li>`,
    );
  if (current)
    items.push(
      `<li class="zb-breadcrumbs__item zb-breadcrumbs__item--active"><span class="zb-breadcrumbs__link">${current.label.split('.').pop()}</span></li>`,
    );
  return `<nav class="zb-breadcrumbs" aria-label="Breadcrumbs"><ul class="zb-breadcrumbs__list">${items.join('')}</ul></nav>`;
}

/** Puts the site navbar, breadcrumbs, favicon, stylesheet and mobile-menu links into one Scaladoc page. */
export function themeApiPage(html, { prompt }) {
  let out = replaceOnce(html, HEADER, navbarHtml({ prompt }), 'header (#header before #mobile-menu)');
  // The breadcrumbs move from above the columns to the top of the text column, where a docs page has them.
  const crumbs = CRUMBS.exec(out);
  if (!crumbs) throw new Error('Scaladoc page has no breadcrumbs; did a Scaladoc upgrade change its markup?');
  const trail = [...crumbs[1].matchAll(/<a href="([^"]*)">([^<]*)<\/a>/g)].map(([, href, label]) => ({ href, label }));
  out = out.replace(CRUMBS, '');
  out = replaceOnce(out, CONTENT_START, (open) => open + breadcrumbsHtml(trail), 'content column');
  out = replaceOnce(
    out,
    MOBILE_LOGO,
    '<a class="mobile-menu-logo" href="/"><img src="/brand/zio-blocks-logo-on-dark.svg" alt="ZIO Blocks" width="160" height="37"></a>',
    'mobile menu logo',
  );
  out = replaceOnce(
    out,
    MOBILE_THEME,
    `<nav class="zb-mobile-links">${NAV_LINKS.map((l) => linkHtml(l, 'zb-mobile-link')).join('')}</nav>${MOBILE_THEME}`,
    'mobile theme toggle',
  );
  out = extensionlessLinks(out);
  // The site favicon instead of Scaladoc's own favicon.ico, with the markup the docs pages use.
  out = replaceOnce(out, SCALADOC_FAVICON, `<link rel="icon" href="${FAVICON}">`, 'favicon link');
  return replaceOnce(out, '</head>', `<link rel="stylesheet" href="${STYLESHEET}"></head>`, '</head>');
}

/**
 * Starts the API side menu at the subpackages of `zio` (blocks, http) instead of the one `zio` package above them, as
 * a docs sidebar starts at its categories. Scaladoc labels a package by its name relative to its parent, so the hoisted
 * packages are renamed relative to `zio` ("zio.blocks.schema" becomes "blocks.schema", shown as "schema" under
 * "blocks"); other entries keep their names. Fails if the menu no longer has `zio` as its only root.
 */
export function hoistSideMenu(menu) {
  const roots = menu?.api?.children;
  if (!roots || roots.length !== 1 || roots[0].name !== 'zio' || roots[0].kind !== 'package')
    throw new Error('dynamicSideMenu.json no longer has the zio package as its only API root; update hoistSideMenu');
  const rename = (item) => ({
    ...item,
    name: item.kind === 'package' && item.name.startsWith('zio.') ? item.name.slice('zio.'.length) : item.name,
    children: item.children.map(rename),
  });
  return { ...menu, api: { ...menu.api, children: roots[0].children.map(rename) } };
}

// Extensionless URLs: the site serves pages as /api/zio/blocks/chunk/Chunk, as the docs are /reference/chunk, rather
// than Scaladoc's Chunk.html. The hosts serve `Chunk.html` for `/…/Chunk` (Netlify, and `docusaurus serve`, which also
// redirects the .html form), so only the links change; the files keep their names. An index page is its folder.

/** `Chunk.html` -> `Chunk`, `Chunk.html#m` -> `Chunk#m`, `../index.html` -> `../`; `index.html` alone -> `./`. */
export function extensionless(url) {
  // Only the path: an anchor can end in .html too (MediaTypes$$application$.html#vnd.dtg.local.html-0).
  const at = url.indexOf('#');
  const [path, hash] = at < 0 ? [url, ''] : [url.slice(0, at), url.slice(at)];
  if (!path.endsWith('.html')) return url;
  const page = path.slice(0, -'.html'.length);
  const dir = /^(.*\/)?index$/.exec(page);
  return (dir ? dir[1] || './' : page) + hash;
}

// A relative link: no scheme, not protocol- or root-relative (the site's own /… links and other sites keep theirs).
const isRelative = (url) => !/^(?:[a-z][a-z0-9+.-]*:|\/|#)/i.test(url);

/** Drops `.html` from the relative links of a Scaladoc page. */
export function extensionlessLinks(html) {
  return html.replace(/href="([^"]*)"/g, (all, url) => (isRelative(url) ? `href="${extensionless(url)}"` : all));
}

/**
 * Drops `.html` from the page locations in Scaladoc's search index (scripts/searchData.js, `"l":"zio/blocks.html#"`),
 * and the empty `#` Scaladoc ends a page's own entry with.
 */
export function extensionlessSearchData(js) {
  return js.replace(/"l":"([^"]*)"/g, (all, url) => `"l":"${extensionless(url).replace(/#$/, '')}"`);
}

/**
 * The side menu builds its links in scripts/ux.js (as `docs/generateApiDocs` patched it); make them extensionless too,
 * with the index entry as the API root. Fails if the line changed.
 */
export function extensionlessUxJs(js) {
  const link = 'return `${baseUrl.join("/")}/${loc.join("/")}.html`;';
  if (!js.includes(link)) throw new Error('scripts/ux.js no longer builds side-menu links with `' + link + '`');
  return js.replace(link, 'return `${baseUrl.join("/")}/${loc.join("/") === "index" ? "" : loc.join("/")}`;');
}
