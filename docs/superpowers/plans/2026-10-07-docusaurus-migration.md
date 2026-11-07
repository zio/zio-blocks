# Unified Docusaurus Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Astro site in `landing/` with one Docusaurus app in `website/`: the landing page at `/`, the docs under `/docs/`, one brand look, built and deployed by GitHub Actions.

**Architecture:** The existing `website/` Docusaurus 3.10 app becomes the whole site. The Node scripts that turn `docs/index.md` into `src/data/site.json` (and their unit tests) move to `website/scripts` and `website/test`, with docs links now site-relative (`/docs/...`). The landing page is React (`src/pages/index.js` plus `src/components/landing/*`, global `lp-` CSS). Brand tokens are mapped onto Infima in `src/css/custom.css` so docs, navbar and footer match. `LANDING_ONLY=1` turns the docs plugin off, so landing work builds and tests with Node only.

**Tech Stack:** Docusaurus 3.10.2 (already in `website/`), React 18, `prism-react-renderer` (already a dependency), `node:test`. **No new npm dependencies.**

**Spec:** `docs/superpowers/specs/2026-10-07-docusaurus-migration-design.md`

## Global Constraints

- Brand: flat and square; no `gradient(`, shadows, radius, rotation or skew anywhere in `website/src` (enforced by a test, with `0` and `none` allowed so Infima variables can be switched off). Palette (exact): Ultramarine `#2D3F8F`, Ink `#141A2E`, Lifted blue `#4B5FC4`, Paper ink `#F3F5FC`, white `#FFFFFF`, hairline `#E4E7F0`, muted `#7C859E`. Manrope self-hosted.
- The wordmark is the logo image only; never type "ZIO Blocks" as text next to the logo. Logo `alt="ZIO Blocks"`.
- `#7C859E` only as a rule colour or on ink surfaces; text on light surfaces `#5B6580` or darker. WCAG AA in light and dark.
- All landing copy, code, catalog and release version come from `docs/index.md` through the generator; nothing hand-typed beyond the section titles and fixed UI strings already approved.
- No copy buttons on the landing page. Docs pages keep Docusaurus's normal code blocks.
- The landing page content must be present and readable in the server-rendered HTML (all deep-dive panels visible before hydration).
- Do **not** modify `docs/` content (including `docs/index.md` and `docs/sidebars.js`), README generation, or `website/yarn.lock` dependencies. `.github/workflows/ci.yml` changes only through `sbt ciGenerateGithubWorkflow`.
- No new npm dependencies (ask-first per `AGENTS.md`).
- Tests use `node:test` + `node:assert/strict`; assert full extracted values with `equal`/`deepEqual`.
- Commit after each task, conventional commits.
- `<scratchpad>` in commands means the session's scratchpad directory (the temporary-files directory named in the environment); screenshots, sbt logs and PR bodies go there, never into the repository. `.git` is a file in a worktree, so sbt logs cannot go to `.git/agent-logs`.

## Review Focus

Failure modes the spec implies that no task's headline test covers, most likely first. Each has a test in the owning task.

1. **Broken links in the full build.** The landing page links to `/docs/...`; `onBrokenLinks: 'throw'` must pass in the full (mdoc) build, and `LANDING_ONLY` must only warn. (Tasks 2, 8)
2. **JavaScript off.** All deep-dive panels and all catalog tiles are in the server HTML and not `hidden`; the tablist is hidden until hydration. (Task 5)
3. **Docs regressions from restyling.** Tables, code, admonitions, tabs, the sidebar and the docs home look right in both colour modes. (Task 8)
4. **Brand guard false positives and negatives.** Infima's `--ifm-global-radius: 0` must pass; `border-radius: 4px` and `boxShadow: '0 1px'` must fail. (Task 2)
5. **Docs content untouched.** `git diff main -- docs/` shows only `docs/superpowers/**`. (Task 10)

---

## File Structure

```
website/
  docusaurus.config.js            REWRITE: brand navbar/footer, colour mode, docs at /docs, Scala+Java Prism, env switches
  package.json                    scripts: build/start run the generator; test, check:landing, check:links
  .gitignore                      src/data/site.json, static/brand, build, .docusaurus, node_modules, docs (mdoc output)
  lighthouserc.json               95+ in four categories, chromeFlags
  README.md                       how the site works (moved from landing/README.md, updated)
  scripts/                        MOVED from landing/scripts (+ lib/brand-guard.mjs)
  test/unit/*.test.mjs            MOVED from landing/test/unit (+ rows, brand-guard)
  test/dist.test.mjs              static-output tests against build/index.html
  src/lib/                        MOVED inline.mjs, title-case.mjs; NEW rows.mjs
  src/prism-themes.js             flat light and dark Prism themes (CJS, used by config and Hero)
  src/css/custom.css              REWRITE: tokens, Manrope, Infima mapping, navbar, footer, docs polish
  src/components/landing/         ModuleField, Hero, Principles, DeepDives, Catalog (.js) + landing.css
  src/pages/index.js              the landing page
  src/data/site.json              GENERATED (gitignored)
  static/fonts/                   Manrope woff2 + OFL.txt (moved from landing/public/fonts)
  static/brand/                   GENERATED copies of assets/logo files (gitignored)
  static/_headers                 Netlify cache and security headers
landing/                          DELETED in Task 10
project/CiWorkflow.scala          MODIFIED: landing job runs on website/, buildDocs gets env + production deploy
.github/workflows/ci.yml          REGENERATED
AGENTS.md                         MODIFIED
```

## Interfaces (shared by all tasks)

`website/src/data/site.json` shape is unchanged from the Astro version (`version, tagline, lead, principles, hero, compatibility, blockCount, categories, deepDives, guides`), except every `docsUrl` and `learnMore.url` for docs pages is site-relative: `/docs/reference/schema/`, `/docs/reference/mux`, `/docs/guides/x`.

`src/lib/rows.mjs` exports `packRows(categories, columns = 3) -> Category[][]`: consecutive categories share a row while the sum of their block counts is `<= columns`.

`src/prism-themes.js` (CommonJS) exports `{ light, dark }`, both `prism-react-renderer` theme objects.

`src/lib/inline.mjs` exports `escapeHtml`, `renderCode(text)`, `renderInline(text, linkFor?)` (unchanged). `src/lib/title-case.mjs` exports `titleCase` (unchanged).

Landing DOM contract used by tests (stable, un-hashed): `<main id="landing" class="lp-page">` wrapping `<section id="hero">`, `<section id="principles">`, `<section id="deep-dives">`, `<section id="catalog">`; classes `lp-lead`, `lp-stack`, `lp-chip`, `lp-row` (+ `data-shared`), `lp-cat` (+ `data-span`), `lp-tile`, `lp-artifact`, `lp-foot`, `lp-badges`, `lp-learn`, `lp-label`; tabs `role="tab"` ids `tab-<id>`, panels `role="tabpanel"` ids `panel-<id>`.

---

### Task 1: Move the generator, helpers and unit tests into `website/`

**Files:**
- Move: `landing/scripts/**` -> `website/scripts/**`; `landing/test/unit/**` -> `website/test/unit/**`; `landing/src/lib/inline.mjs`, `landing/src/lib/title-case.mjs` -> `website/src/lib/`
- Modify: `website/scripts/lib/parse-index.mjs`, `website/scripts/build-catalog.mjs`, `website/scripts/check-links.mjs`, `website/package.json`, the moved tests
- Create: `website/.gitignore`

**Interfaces:**
- Produces: the generator writing `website/src/data/site.json` and copying brand assets into `website/static/brand/`; `docsUrl('./reference/schema/index.md') === '/docs/reference/schema/'`; yarn scripts `catalog`, `test`.

- [ ] **Step 1: Move the files with git**

```bash
cd "$(git rev-parse --show-toplevel)"
git mv landing/scripts website/scripts
git mv landing/test/unit website/test/unit 2>/dev/null || (mkdir -p website/test && git mv landing/test/unit website/test/unit)
mkdir -p website/src/lib
git mv landing/src/lib/inline.mjs website/src/lib/inline.mjs
git mv landing/src/lib/title-case.mjs website/src/lib/title-case.mjs
ls website/scripts website/scripts/lib website/test/unit website/src/lib
```

Expected: `scripts` has `build-catalog.mjs check-links.mjs lib`; `test/unit` has the 12 test files; `src/lib` has the two helpers.

- [ ] **Step 2: Make the moved tests fail for the new contract (RED)**

In `website/test/unit/parse-index.test.mjs` and `website/test/unit/build-catalog.test.mjs`, replace the expected docs URL base. Run:

```bash
cd website
sed -i 's#https://zio.dev/zio-blocks/#/docs/#g' test/unit/parse-index.test.mjs
sed -i "s#public/brand#static/brand#g" test/unit/build-catalog.test.mjs
grep -n "zio.dev/zio-blocks" test/unit/*.test.mjs | head
```

Then fix the one assertion `sed` cannot reach because it uses escaped slashes, and list what is left:

```bash
sed -i 's#/\^https:\\/\\/zio\\.dev\\/zio-blocks\\//#/^\\/docs\\//#' test/unit/parse-index.test.mjs
sed -i 's#to zio.dev URLs#to site-relative docs URLs#' test/unit/parse-index.test.mjs
grep -n "zio\\.dev\|zio.dev" test/unit/*.test.mjs | grep -v check-links | head
```

Expected: the last command prints nothing (the two test titles that said "zio.dev URLs" are renamed, and the `b.docsUrl` assertion is now `/^\/docs\//`); the link classifier tests in `check-links.test.mjs` keep their zio.dev examples (that function is unchanged). Then:

```bash
node --test "test/unit/parse-index.test.mjs" "test/unit/build-catalog.test.mjs" 2>&1 | grep -E "^ℹ (pass|fail)"
```

Expected: `fail` is non-zero (URLs still map to zio.dev and assets still go to `public/brand`).

- [ ] **Step 3: Switch the generator to site-relative docs links and `static/brand`**

In `website/scripts/lib/parse-index.mjs` change the base constant:

```js
const DOCS_BASE = '/docs/';
```

In `website/scripts/build-catalog.mjs` replace the brand-asset destination (`public/brand` -> `static/brand`):

```js
  await mkdir(join(outDir, 'static/brand'), { recursive: true });
  for (const file of BRAND_ASSETS) {
    await copyFile(join(repoRoot, 'assets/logo', file), join(outDir, 'static/brand', file));
  }
```

In `website/scripts/check-links.mjs` change the built file it reads from `../dist/index.html` to `../build/index.html`.

- [ ] **Step 4: Package scripts and ignores**

`website/package.json` scripts become (keep the other fields and dependencies exactly as they are):

```json
  "scripts": {
    "docusaurus": "docusaurus",
    "catalog": "node scripts/build-catalog.mjs",
    "start": "node scripts/build-catalog.mjs && docusaurus start",
    "build": "node scripts/build-catalog.mjs && docusaurus build",
    "build:landing": "LANDING_ONLY=1 yarn build",
    "test": "node --test \"test/unit/*.test.mjs\"",
    "check:landing": "yarn build:landing && node --test test/dist.test.mjs",
    "check:links": "node scripts/check-links.mjs",
    "swizzle": "docusaurus swizzle",
    "deploy": "docusaurus deploy",
    "clear": "docusaurus clear",
    "serve": "docusaurus serve",
    "write-translations": "docusaurus write-translations",
    "write-heading-ids": "docusaurus write-heading-ids"
  },
```

`website/.gitignore`:

```
node_modules
build
.docusaurus
docs
src/data/site.json
static/brand
.lighthouseci
```

(`docs` here is the mdoc output directory; check `git check-ignore -v website/docs` first and, if the repository root `.gitignore` already ignores it, keep only the new lines.)

- [ ] **Step 5: Run the unit tests (GREEN) and the generator**

```bash
cd website && yarn test 2>&1 | grep -E "^ℹ (tests|pass|fail)"; yarn catalog 2>&1 | tail -1; git status --short | head
```

Expected: all unit tests pass (the same 76 as before, minus none); `yarn catalog` prints `landing: 41 blocks in 12 categories, version <x>`; `src/data/site.json` and `static/brand/*` exist but are untracked-and-ignored (`git status --short` shows only the moves and edits). If a unit test fails because it hard-codes an Astro-era path (`../../src`, `public`), fix the path in the test, not the code, and say so in the commit body.

- [ ] **Step 6: Commit**

```bash
git add -A website landing && git commit -m "build(website): move the landing generator and unit tests into website, with site-relative docs links"
```

---

### Task 2: Docusaurus config, brand theme, navbar, footer, docs at `/docs`, brand guard

**Files:**
- Rewrite: `website/docusaurus.config.js`, `website/src/css/custom.css`
- Create: `website/src/prism-themes.js`, `website/static/_headers`, `website/scripts/lib/brand-guard.mjs`, `website/test/unit/brand-guard.test.mjs`
- Move: `landing/public/fonts/*` -> `website/static/fonts/`
- Modify: `website/test/unit/brand-rules.test.mjs`

**Interfaces:**
- Produces: `findViolations(source: string): string[]` (brand-guard); the CSS tokens `--fg --fg-soft --label --rule --accent --surface --field --code-bg --ink-rule --ink-soft --seam --lp-gutter --lp-measure` and the Prism themes used by Task 3.

- [ ] **Step 1: Write the failing brand-guard tests**

`website/test/unit/brand-guard.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findViolations } from '../../scripts/lib/brand-guard.mjs';

test('flat CSS and Infima switch-offs are allowed', () => {
  const css = `
    :root { --ifm-global-radius: 0; --ifm-global-shadow-lw: none; --ifm-navbar-shadow: none; }
    .navbar { box-shadow: none !important; border-radius: 0; }
    .x { background: var(--ink); border: 1px solid var(--rule); }
  `;
  assert.deepEqual(findViolations(css), []);
});

test('radius, shadow, gradient, rotation and skew are violations', () => {
  assert.deepEqual(findViolations('.a { border-radius: 4px; }'), ['border-radius: 4px']);
  assert.deepEqual(findViolations('.a { box-shadow: 0 1px 2px #000; }'), ['box-shadow: 0 1px 2px #000']);
  assert.deepEqual(findViolations('.a { background: linear-gradient(red, blue); }'), ['gradient']);
  assert.deepEqual(findViolations('.a { transform: rotate(3deg); }'), ['rotation or skew']);
  assert.deepEqual(findViolations('.a { transform: skewX(3deg); }'), ['rotation or skew']);
  assert.deepEqual(findViolations('.a { filter: drop-shadow(0 0 2px #000); }'), ['drop-shadow']);
});

test('inline style objects in JavaScript are checked too', () => {
  assert.deepEqual(findViolations("const s = { boxShadow: '0 1px 2px #000', borderRadius: 8 };"), [
    "boxShadow: 0 1px 2px #000",
    'borderRadius: 8',
  ]);
  assert.deepEqual(findViolations("const s = { boxShadow: 'none', borderRadius: 0 };"), []);
});
```

Run `cd website && node --test test/unit/brand-guard.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|Cannot find"`. Expected: FAIL (module not found).

- [ ] **Step 2: Implement the guard and use it in the repository scan**

`website/scripts/lib/brand-guard.mjs`:

```js
/**
 * Brand rules (assets/logo/README.md): flat and square. Returns the violations found in `source` (CSS or JS):
 * gradients, rotation or skew, drop shadows, and any shadow or radius property whose value is not `none` or `0`
 * (so Infima's `--ifm-global-radius: 0` and `box-shadow: none` are fine).
 */
export function findViolations(source) {
  const out = [];
  if (/gradient\s*\(/i.test(source)) out.push('gradient');
  if (/rotate|skew/i.test(source)) out.push('rotation or skew');
  if (/drop-shadow\s*\(/i.test(source)) out.push('drop-shadow');
  // A value runs to `;`, `}` or the end of the line, or stops before `, nextKey:` (a JavaScript object property);
  // commas inside a CSS value list (`0 1px #000, 0 2px #000`) stay part of the value.
  const property = /([\w-]*(?:shadow|radius)[\w-]*)\s*:\s*([^;}\n]*?)(?=\s*,\s*[A-Za-z_$][\w$-]*\s*:|\s*[;}\n]|\s*$)/gi;
  for (const m of source.matchAll(property)) {
    const value = m[2].replace(/!important/i, '').replace(/['"]/g, '').trim();
    if (value.toLowerCase() === 'none' || value === '0') continue;
    out.push(`${m[1]}: ${value}`);
  }
  return out;
}
```

Run the guard test again; expected PASS (3 tests). If the multi-value regex makes the `boxShadow` inline-object test order differ, keep the expected array order (`boxShadow` then `borderRadius`) by iterating in source order, which `matchAll` already does.

Replace `website/test/unit/brand-rules.test.mjs` with:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { findViolations } from '../../scripts/lib/brand-guard.mjs';

function sources(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'data' ? [] : sources(path);
    return /\.(css|js|mjs)$/.test(name) ? [path] : [];
  });
}

test('website/src never uses a decoration the brand rules forbid', () => {
  const root = fileURLToPath(new URL('../../src', import.meta.url));
  const offenders = [];
  for (const file of sources(root)) {
    for (const v of findViolations(readFileSync(file, 'utf8'))) offenders.push(`${file}: ${v}`);
  }
  assert.deepEqual(offenders, []);
});
```

- [ ] **Step 3: Fonts, headers and the Prism themes**

```bash
git mv landing/public/fonts website/static/fonts
```

`website/static/_headers`:

```
/fonts/*
  Cache-Control: public, max-age=31536000, immutable
/assets/*
  Cache-Control: public, max-age=31536000, immutable
/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
```

`website/src/prism-themes.js` (flat; the dark theme is the ink code card used on the landing page and in dark docs; comments use the allowed `#7C859E`):

```js
// Flat Prism themes in the brand palette. Used by docusaurus.config.js and by the landing page's hero code card.
const dark = {
  plain: { color: '#f3f5fc', backgroundColor: '#0d1224' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#7c859e' } },
    { types: ['punctuation'], style: { color: '#f3f5fc' } },
    { types: ['keyword', 'operator', 'tag', 'deleted'], style: { color: '#ff7b72' } },
    { types: ['string', 'char', 'attr-value', 'inserted'], style: { color: '#a5d6ff' } },
    { types: ['number', 'boolean', 'constant', 'symbol'], style: { color: '#79c0ff' } },
    { types: ['function', 'class-name', 'builtin', 'attr-name'], style: { color: '#ffa657' } },
    { types: ['variable', 'parameter'], style: { color: '#f3f5fc' } },
  ],
};

const light = {
  plain: { color: '#141a2e', backgroundColor: '#f3f5fc' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#5b6580' } },
    { types: ['punctuation'], style: { color: '#141a2e' } },
    { types: ['keyword', 'operator', 'tag', 'deleted'], style: { color: '#b31d28' } },
    { types: ['string', 'char', 'attr-value', 'inserted'], style: { color: '#032f62' } },
    { types: ['number', 'boolean', 'constant', 'symbol'], style: { color: '#005cc5' } },
    { types: ['function', 'class-name', 'builtin', 'attr-name'], style: { color: '#6f42c1' } },
    { types: ['variable', 'parameter'], style: { color: '#141a2e' } },
  ],
};

module.exports = { light, dark };
```

- [ ] **Step 4: Rewrite `docusaurus.config.js`**

```js
// @ts-check
const prismThemes = require('./src/prism-themes');

// LANDING_ONLY=1 builds just the landing page (no docs plugin, so no mdoc output or sbt is needed); broken links to
// /docs only warn then. The full build, which CI deploys, never sets it.
const LANDING_ONLY = process.env.LANDING_ONLY === '1';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO Blocks',
  tagline: 'Type-safe, modular building blocks for Scala',
  url: process.env.SITE_URL || 'http://localhost:3000',
  baseUrl: '/',
  onBrokenLinks: LANDING_ONLY ? 'warn' : 'throw',
  onDuplicateRoutes: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: LANDING_ONLY ? 'warn' : 'throw',
    },
  },
  favicon: 'brand/zio-blocks-mark-favicon.svg',

  organizationName: 'zio',
  projectName: 'zio-blocks',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },
  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      {
        docs: LANDING_ONLY
          ? false
          : {
              id: 'default',
              path: './docs',
              routeBasePath: '/docs',
              sidebarPath: require.resolve('../docs/sidebars.js'),
            },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        blog: false,
      },
    ],
  ],
  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'brand/zio-blocks-social-og.png',
      colorMode: {
        defaultMode: 'light',
        respectPrefersColorScheme: true,
      },
      navbar: {
        logo: {
          alt: 'ZIO Blocks',
          src: 'brand/zio-blocks-logo-on-dark.svg',
          width: 160,
          height: 37,
        },
        items: [
          { to: '/docs/', label: 'Docs', position: 'right' },
          { to: '/#catalog', label: 'Blocks', position: 'right' },
          { href: 'https://github.com/zio/zio-blocks', label: 'GitHub', position: 'right' },
        ],
      },
      footer: {
        style: 'dark',
        logo: {
          alt: 'ZIO Blocks',
          src: 'brand/zio-blocks-logo-mono-white.svg',
          width: 160,
          height: 37,
        },
        links: [
          {
            items: [
              { label: 'Docs', to: '/docs/' },
              { label: 'Reference', to: '/docs/reference/schema/' },
              { label: 'GitHub', href: 'https://github.com/zio/zio-blocks' },
            ],
          },
        ],
      },
      prism: {
        theme: prismThemes.light,
        darkTheme: prismThemes.dark,
        // Scala extends Java in Prism, so Java must be loaded first.
        additionalLanguages: ['java', 'scala', 'bash', 'sql'],
      },
    }),
};

module.exports = config;
```

- [ ] **Step 5: Rewrite `custom.css` (tokens, Manrope, Infima mapping, navbar, footer, docs polish)**

```css
@font-face {
  font-family: 'Manrope';
  src: url('/fonts/manrope-latin-wght-normal.woff2') format('woff2');
  font-weight: 200 800;
  font-style: normal;
  font-display: swap;
}

:root {
  /* Brand palette (assets/logo/README.md) */
  --ultramarine: #2d3f8f;
  --ink: #141a2e;
  --lifted: #4b5fc4;
  --paper-ink: #f3f5fc;
  --hairline: #e4e7f0;
  --muted: #7c859e;

  /* Module-square fill of the brand social card */
  --field: #1e2647;

  /* UI tints derived from the palette (code cards, rules, secondary text, labels, surfaces); not brand marks */
  --code-bg: #0d1224;
  --ink-rule: #232b4a;
  --ink-soft: #a9b1ce;

  /* Semantic tokens, light */
  --fg: var(--ink);
  --fg-soft: #4a5470;
  --label: #5b6580;
  --rule: var(--hairline);
  --accent: var(--ultramarine);
  --surface: var(--paper-ink);

  --seam: 6px;
  --lp-gutter: clamp(1rem, 4vw, 2.5rem);
  --lp-measure: 72rem;

  /* Infima */
  --ifm-font-family-base: 'Manrope', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --ifm-font-family-monospace: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  --ifm-heading-font-weight: 800;
  --ifm-heading-color: var(--ink);
  --ifm-font-color-base: var(--ink);
  --ifm-background-color: #ffffff;
  --ifm-background-surface-color: var(--paper-ink);
  --ifm-code-font-size: 90%;
  --ifm-code-background: var(--paper-ink);

  --ifm-color-primary: #2d3f8f;
  --ifm-color-primary-dark: #293a81;
  --ifm-color-primary-darker: #273778;
  --ifm-color-primary-darkest: #202d63;
  --ifm-color-primary-light: #3144a0;
  --ifm-color-primary-lighter: #3347a9;
  --ifm-color-primary-lightest: #3d53c0;
  --ifm-link-color: var(--ultramarine);

  /* Flat and square */
  --ifm-global-radius: 0;
  --ifm-global-shadow-lw: none;
  --ifm-global-shadow-md: none;
  --ifm-global-shadow-tl: none;

  --ifm-navbar-background-color: var(--ink);
  --ifm-navbar-link-color: var(--paper-ink);
  --ifm-navbar-link-hover-color: #ffffff;
  --ifm-navbar-shadow: none;
  --ifm-footer-background-color: var(--ink);
  --ifm-footer-color: var(--paper-ink);
  --ifm-footer-link-color: var(--paper-ink);
  --ifm-footer-title-color: var(--paper-ink);
  --ifm-footer-link-hover-color: #ffffff;

  --ifm-toc-border-color: var(--hairline);
  --ifm-table-border-color: var(--hairline);
  --ifm-hr-border-color: var(--hairline);
  --docusaurus-highlighted-code-line-bg: rgba(45, 63, 143, 0.12);
}

[data-theme='dark'] {
  --fg: var(--paper-ink);
  --fg-soft: var(--ink-soft);
  --label: #9aa3c0;
  --rule: var(--ink-rule);
  --accent: var(--lifted);
  --surface: #1a2140;

  --ifm-heading-color: var(--paper-ink);
  --ifm-font-color-base: var(--paper-ink);
  --ifm-background-color: var(--ink);
  --ifm-background-surface-color: #1a2140;
  --ifm-code-background: #1a2140;

  --ifm-color-primary: #4b5fc4;
  --ifm-color-primary-dark: #3e53bf;
  --ifm-color-primary-darker: #3a4eb5;
  --ifm-color-primary-darkest: #2f3f93;
  --ifm-color-primary-light: #5a6dca;
  --ifm-color-primary-lighter: #6477ce;
  --ifm-color-primary-lightest: #8393d8;
  /* Lifted blue is 3:1 on ink: fine for borders and fills, not for link text */
  --ifm-link-color: var(--paper-ink);
  --ifm-link-hover-color: #ffffff;
  --ifm-menu-color-active: var(--paper-ink);

  --ifm-toc-border-color: var(--ink-rule);
  --ifm-table-border-color: var(--ink-rule);
  --ifm-hr-border-color: var(--ink-rule);
  --docusaurus-highlighted-code-line-bg: rgba(75, 95, 196, 0.25);
}

/* Navbar: ink in both colour modes, hairline underneath */
.navbar { border-bottom: 1px solid var(--ink-rule); }
.navbar__link { font-weight: 600; }
.navbar__brand { margin-right: auto; }
.navbar__logo img { height: 2.25rem; width: auto; }

/* Docs: editorial links, hairline rules, flat code */
.markdown a { text-decoration: underline; text-underline-offset: 0.2em; }
.markdown h1, .markdown h2, .markdown h3 { letter-spacing: -0.01em; }
.theme-code-block { box-shadow: none; }
.menu__link--active:not(.menu__link--sublist) { font-weight: 700; }
:focus-visible { outline: 2px solid var(--lifted); outline-offset: 2px; }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation: none !important;
    transition: none !important;
    scroll-behavior: auto !important;
  }
}
```

- [ ] **Step 6: Run the guard, the unit tests, and a landing-only build**

```bash
cd website && yarn test 2>&1 | grep -E "^ℹ (tests|pass|fail)"; yarn build:landing 2>&1 | grep -E "ERROR|SUCCESS|WARN.*Broken|Generated" | head
```

Expected: unit tests pass (including the new guard tests and the repository scan over `src`); the build ends with `[SUCCESS] Generated static files in "build"` (a `[WARNING] Docusaurus found broken links` for `/docs/` is expected in `LANDING_ONLY` mode and fine).

- [ ] **Step 7: Commit**

```bash
cd .. && git add -A website landing && git commit -m "feat(website): brand the Docusaurus site: navbar, footer, colour mode, tokens, docs at /docs"
```

---

### Task 3: Landing page foundation: rows helper, landing CSS, ModuleField, Hero, first static-output tests

**Files:**
- Create: `website/src/lib/rows.mjs`, `website/test/unit/rows.test.mjs`, `website/src/components/landing/landing.css`, `website/src/components/landing/ModuleField.js`, `website/src/components/landing/Hero.js`, `website/src/pages/index.js`, `website/test/dist.test.mjs`

**Interfaces:**
- Consumes: Task 2 tokens and `prism-themes`; Task 1 `site.json`, `renderInline`, `titleCase`.
- Produces: `packRows`; `<main id="landing" class="lp-page">` with `<section id="hero">`; the dist-test helpers `text`, `decode`, `main`, `sec`.

- [ ] **Step 1: Write the failing rows test and implement `packRows`**

`website/test/unit/rows.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { packRows } from '../../src/lib/rows.mjs';

const cat = (name, n) => ({ name, blocks: Array.from({ length: n }, (_, i) => ({ name: `${name}${i}` })) });

test('consecutive small categories share a row while their tiles fit in the columns', () => {
  const cats = [cat('Meta', 2), cat('Codecs', 8), cat('RM', 1), cat('DI', 1), cat('Config', 4), cat('Streaming', 1), cat('Telemetry', 2), cat('Persistence', 4)];
  assert.deepEqual(
    packRows(cats).map((row) => row.map((c) => c.name)),
    [['Meta'], ['Codecs'], ['RM', 'DI'], ['Config'], ['Streaming', 'Telemetry'], ['Persistence']],
  );
});

test('the column count is a parameter and order is preserved', () => {
  const cats = [cat('A', 1), cat('B', 1), cat('C', 1)];
  assert.deepEqual(packRows(cats, 2).map((r) => r.map((c) => c.name)), [['A', 'B'], ['C']]);
  assert.deepEqual(packRows([], 3), []);
});
```

Run `cd website && node --test test/unit/rows.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|Cannot find"`. Expected: FAIL (module not found). Then create `website/src/lib/rows.mjs`:

```js
/**
 * Groups categories into rows for the catalog. On wide screens the tile grid has `columns` columns; consecutive
 * categories share a row while their tiles together fit in those columns (for example 1 + 1, or 1 + 2), and every
 * other category has its own row.
 */
export function packRows(categories, columns = 3) {
  const rows = [];
  for (const category of categories) {
    const last = rows[rows.length - 1];
    const used = last ? last.reduce((n, c) => n + c.blocks.length, 0) : 0;
    if (last && used + category.blocks.length <= columns) last.push(category);
    else rows.push([category]);
  }
  return rows;
}
```

Re-run the rows test; expected PASS (2 tests).

- [ ] **Step 2: Write the failing static-output tests for the head and the hero**

`website/test/dist.test.mjs` (the helpers below are used by every later task; later tasks only append tests):

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import site from '../src/data/site.json' with { type: 'json' };
import { titleCase } from '../src/lib/title-case.mjs';

const html = readFileSync(new URL('../build/index.html', import.meta.url), 'utf8');

const decode = (s) =>
  s.replace(/&#34;|&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
/** Text content of an HTML fragment: inline tags dropped, other tags become spaces, entities decoded, whitespace collapsed. */
export const text = (fragment) =>
  decode(fragment.replace(/<\/?(?:span|code|strong|em|a)\b[^>]*>/g, '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();

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
```

Run `cd website && yarn build:landing 2>&1 | tail -2; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|missing"`. Expected: FAIL (`missing <main id="landing"`; the page does not exist yet).

- [ ] **Step 3: Landing CSS**

`website/src/components/landing/landing.css` (global, every class prefixed `lp-`; tokens come from `custom.css`):

```css
.lp-page { background: var(--ifm-background-color); color: var(--fg); }
.lp-page [hidden] { display: none !important; }
.lp-page code { background: none; border: 0; padding: 0; font-size: 0.9em; }
.lp-page ul, .lp-page ol { margin: 0; padding: 0; list-style: none; }

.lp-wrap { max-width: var(--lp-measure); margin-inline: auto; padding-inline: var(--lp-gutter); }
.lp-label {
  margin: 0 0 0.75rem;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--label);
}
.lp-section { border-top: 1px solid var(--rule); padding-block: clamp(3rem, 7vw, 5.5rem); }
.lp-section h2 {
  margin: 0 0 1rem;
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.01em;
  text-wrap: balance;
}

.lp-btn {
  display: inline-block;
  padding: 0.7rem 1.1rem;
  border: 2px solid transparent;
  font-weight: 700;
  font-size: 0.9375rem;
  text-decoration: none;
}
.lp-btn-primary, .lp-btn-primary:hover { color: #fff; }
.lp-btn-primary { background: var(--lifted); }
.lp-btn-primary:hover { background: #fff; color: var(--ink); text-decoration: none; }
.lp-btn-ghost { border-color: var(--lifted); color: var(--paper-ink); }
.lp-btn-ghost:hover { border-color: #fff; color: var(--paper-ink); text-decoration: none; }

/* Code surfaces: always ink, in both colour modes */
.lp-codecard { background: var(--code-bg); color: var(--paper-ink); border: 1px solid var(--ink-rule); max-width: 40rem; }
.lp-codecard pre {
  margin: 0;
  padding: 1rem 1.25rem;
  overflow-x: auto;
  font-family: var(--ifm-font-family-monospace);
  font-size: 0.8125rem;
  line-height: 1.65;
  background: transparent !important;
}

/* Hero */
.lp-hero {
  position: relative;
  overflow: hidden;
  background: var(--ink);
  color: var(--paper-ink);
  padding-bottom: clamp(3rem, 8vw, 6rem);
}
.lp-hero .lp-inner { position: relative; }
.lp-copy { max-width: 56rem; padding-top: clamp(2.5rem, 7vw, 5rem); }
.lp-hero h1 {
  margin: 0 0 1rem;
  color: var(--paper-ink);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.02em;
  text-wrap: balance;
}
.lp-lead { margin: 0 0 1.75rem; max-width: 34rem; color: var(--ink-soft); font-size: 1.0625rem; }
.lp-lead strong { color: var(--paper-ink); }
.lp-cta { display: flex; flex-wrap: wrap; gap: 0.75rem; margin: 1.5rem 0 0; }
.lp-stack { margin: 2rem 0 0; color: var(--muted); font-size: 0.8125rem; letter-spacing: 0.04em; }

/* Module field */
.lp-field {
  --m: clamp(64px, 9vw, 132px);
  position: absolute;
  inset: 0 calc(var(--m) * -0.4) 0 auto;
  width: calc(3 * var(--m) + 2 * var(--seam));
  pointer-events: none;
}
.lp-sq {
  position: absolute;
  width: var(--m);
  height: var(--m);
  left: calc(var(--c) * (var(--m) + var(--seam)));
  top: calc(var(--r) * (var(--m) + var(--seam)));
  background: var(--field);
}
@media (max-width: 720px) { .lp-field { opacity: 0.5; } }
@media (prefers-reduced-motion: no-preference) {
  .lp-sq { animation: lp-rise 0.6s both; animation-delay: calc(var(--i) * 70ms); }
  @keyframes lp-rise {
    from { opacity: 0; transform: translateY(12px); }
    to { opacity: 1; transform: none; }
  }
}
```

(Later tasks append the Principles, Deep Dives and Catalog rules to this same file.)

- [ ] **Step 4: ModuleField, Hero and the page**

`website/src/components/landing/ModuleField.js`:

```js
import React from 'react';

// Decorative field of equal squares, in the spirit of the social card: a 3-wide grid of modules bleeding off the
// right edge. `1` = a module, `0` = a gap.
const PATTERN = [
  [1, 1, 1],
  [0, 0, 1],
  [0, 0, 0],
  [0, 1, 0],
  [1, 1, 0],
  [1, 1, 1],
];
const squares = PATTERN.flatMap((row, r) => row.map((on, c) => (on ? { r, c } : null)).filter(Boolean));

export default function ModuleField() {
  return (
    <div className="lp-field" aria-hidden="true">
      {squares.map((s, i) => (
        <span key={i} className="lp-sq" style={{'--c': s.c, '--r': s.r, '--i': i}} />
      ))}
    </div>
  );
}
```

`website/src/components/landing/Hero.js`:

```js
import React from 'react';
import Link from '@docusaurus/Link';
import {Highlight} from 'prism-react-renderer';
import site from '../../data/site.json';
import {renderInline} from '../../lib/inline.mjs';
import {titleCase} from '../../lib/title-case.mjs';
import prismThemes from '../../prism-themes';
import ModuleField from './ModuleField';

// The docs tagline is "<headline>—no effect system required."; the headline is the part before the first em dash.
const dash = site.tagline.indexOf('—');
const base = (dash === -1 ? site.tagline : site.tagline.slice(0, dash)).trim();
// The headline is title-cased. A non-breaking space (U+00A0) keeps "Building Blocks" on one line.
const headline = titleCase(base).replace('Building Blocks', 'Building Blocks');
const snippet = `${site.hero.install}\n\n${site.hero.jsonCode}   // ${site.hero.jsonResult}`;

export default function Hero() {
  return (
    <section id="hero" className="lp-hero" aria-labelledby="hero-title">
      <ModuleField />
      <div className="lp-wrap lp-inner">
        <div className="lp-copy">
          <h1 id="hero-title">{headline}</h1>
          <p className="lp-lead" dangerouslySetInnerHTML={{__html: renderInline(site.lead)}} />

          <div className="lp-codecard">
            <Highlight theme={prismThemes.dark} code={snippet} language="scala">
              {({className, style, tokens, getLineProps, getTokenProps}) => (
                <pre className={className} style={style}>
                  {tokens.map((line, i) => (
                    <div key={i} {...getLineProps({line})}>
                      {line.map((token, key) => (
                        <span key={key} {...getTokenProps({token})} />
                      ))}
                    </div>
                  ))}
                </pre>
              )}
            </Highlight>
          </div>

          <p className="lp-cta">
            <Link className="lp-btn lp-btn-primary" to="/docs/">Get started</Link>
            <a className="lp-btn lp-btn-ghost" href="https://github.com/zio/zio-blocks">GitHub</a>
          </p>

          <p className="lp-stack">Works with {site.compatibility.join(' · ')}</p>
        </div>
      </div>
    </section>
  );
}
```

`website/src/pages/index.js`:

```js
import React from 'react';
import Layout from '@theme/Layout';
import Hero from '../components/landing/Hero';
import '../components/landing/landing.css';

const DESCRIPTION =
  'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.';

export default function Home() {
  return (
    <Layout title="Type-Safe, Modular Building Blocks for Scala" description={DESCRIPTION}>
      <main id="landing" className="lp-page">
        <Hero />
      </main>
    </Layout>
  );
}
```

- [ ] **Step 5: Build and run the tests (GREEN)**

```bash
cd website && yarn build:landing 2>&1 | grep -E "ERROR|SUCCESS" ; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|^✖"; yarn test 2>&1 | grep -E "^ℹ (pass|fail)"
```

Expected: build succeeds; dist tests `pass 3, fail 0`; unit tests all pass. If the `head` test fails on the favicon or description regex, print the real tag with `grep -o '<link[^>]*icon[^>]*>' build/index.html` and fix the **regex** to match the real markup (not the page).

- [ ] **Step 6: Visual check of the hero (one screenshot each: 1280 and 360, light and dark)**

Serve with `cd website && yarn serve --port 4321 &` (after the landing-only build). Capture with the system Chromium (`/nix/var/nix/profiles/default/bin/chromium --headless --no-sandbox --hide-scrollbars --virtual-time-budget=5000 --window-size=1280,760 --screenshot=<scratchpad>/hero.png http://localhost:4321/`), read the images, and fix what is wrong. Check: ink navbar with the lockup, ink hero directly under it, headline on two lines, code card with brand colours, buttons flat, module squares bleeding off the right edge. Stop the server by port: `kill $(lsof -t -i:4321)` (never `pkill -f`).

- [ ] **Step 7: Commit**

```bash
cd .. && git add -A website && git commit -m "feat(website): landing page hero on Docusaurus with static-output tests"
```

---

### Task 4: Principles and Deep Dives

**Files:**
- Create: `website/src/components/landing/Principles.js`, `website/src/components/landing/DeepDives.js`
- Modify: `website/src/pages/index.js`, `website/src/components/landing/landing.css`, `website/test/dist.test.mjs`

**Interfaces:**
- Consumes: Task 3 helpers and CSS.
- Produces: `<section id="principles">` and `<section id="deep-dives">` with `role="tablist"`, `role="tab"`, `role="tabpanel"`, chips `lp-chip`.

- [ ] **Step 1: Append the failing tests**

```js
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
```

Rebuild and run: `cd website && yarn build:landing >/dev/null 2>&1; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|missing"`. Expected: the three new tests FAIL (`missing <section id="principles"`).

- [ ] **Step 2: Principles**

`website/src/components/landing/Principles.js`:

```js
import React from 'react';
import site from '../../data/site.json';
import {titleCase} from '../../lib/title-case.mjs';

export default function Principles() {
  return (
    <section id="principles" className="lp-section" aria-labelledby="principles-title">
      <div className="lp-wrap">
        <p className="lp-label">01 &nbsp; Principles</p>
        <h2 id="principles-title">Use What You Need, Nothing More</h2>
        <ol className="lp-principles">
          {site.principles.map((p, i) => (
            <li key={p.name}>
              <span className="lp-n">0{i + 1}</span>
              <strong>{titleCase(p.name)}</strong>
              <span className="lp-t">{p.text}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
```

- [ ] **Step 3: DeepDives (React tabs; all panels in the server HTML)**

`website/src/components/landing/DeepDives.js`:

```js
import React, {useEffect, useRef, useState} from 'react';
import {Highlight} from 'prism-react-renderer';
import site from '../../data/site.json';
import {renderCode, renderInline} from '../../lib/inline.mjs';
import {titleCase} from '../../lib/title-case.mjs';
import prismThemes from '../../prism-themes';

// JSON is built into the Schema block; every block in the "Codecs" category adds one more format.
const codecs = site.categories.find((c) => c.name === 'Codecs')?.blocks.map((b) => b.name.replace(/ Codec$/, '')) ?? [];
const formats = ['JSON', ...codecs];

function Code({source, lang}) {
  return (
    <div className="lp-codecard">
      <Highlight theme={prismThemes.dark} code={source} language={lang}>
        {({className, style, tokens, getLineProps, getTokenProps}) => (
          <pre className={className} style={style}>
            {tokens.map((line, i) => (
              <div key={i} {...getLineProps({line})}>
                {line.map((token, key) => (
                  <span key={key} {...getTokenProps({token})} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  );
}

const html = (markup) => ({__html: markup});

export default function DeepDives() {
  const [active, setActive] = useState(0);
  // Server-rendered HTML shows every panel and no tablist; after hydration the tablist appears and only the
  // selected panel stays visible, so the content is readable without JavaScript.
  const [hydrated, setHydrated] = useState(false);
  const tabs = useRef([]);
  useEffect(() => setHydrated(true), []);

  const select = (index, focus = false) => {
    setActive(index);
    if (focus) tabs.current[index]?.focus();
  };
  const onKeyDown = (event, i) => {
    const last = site.deepDives.length - 1;
    const target = {ArrowRight: i === last ? 0 : i + 1, ArrowLeft: i === 0 ? last : i - 1, Home: 0, End: last}[event.key];
    if (target === undefined) return;
    event.preventDefault();
    select(target, true);
  };

  return (
    <section id="deep-dives" className="lp-section" aria-labelledby="deep-dives-title">
      <div className="lp-wrap">
        <p className="lp-label">02 &nbsp; Deep Dives</p>
        <h2 id="deep-dives-title">Four Blocks, in Code</h2>

        <div className="lp-tablist" role="tablist" aria-label="Deep dives" hidden={!hydrated}>
          {site.deepDives.map((d, i) => (
            <button
              key={d.id}
              ref={(el) => (tabs.current[i] = el)}
              role="tab"
              type="button"
              id={`tab-${d.id}`}
              aria-controls={`panel-${d.id}`}
              aria-selected={i === active}
              tabIndex={i === active ? 0 : -1}
              onClick={() => select(i)}
              onKeyDown={(e) => onKeyDown(e, i)}>
              {titleCase(d.title)}
            </button>
          ))}
        </div>

        {site.deepDives.map((d, i) => (
          <div
            key={d.id}
            className="lp-panel"
            role="tabpanel"
            id={`panel-${d.id}`}
            aria-labelledby={`tab-${d.id}`}
            hidden={hydrated && i !== active}>
            <h3 className={hydrated ? 'lp-vh' : undefined}>{titleCase(d.title)}</h3>
            <p className="lp-intro" dangerouslySetInnerHTML={html(renderInline(d.intro))} />
            <div className="lp-cols">
              <div className="lp-col">
                <p className="lp-label">The Problem</p>
                {d.problem.paragraphs.map((p, k) => <p key={k} dangerouslySetInnerHTML={html(renderInline(p))} />)}
                {d.problem.code && <Code source={d.problem.code.source} lang={d.problem.code.lang} />}
              </div>
              <div className="lp-col">
                <p className="lp-label">The Solution</p>
                {d.solution.paragraphs.map((p, k) => <p key={k} dangerouslySetInnerHTML={html(renderInline(p))} />)}
                <Code source={d.solution.code.source} lang={d.solution.code.lang} />
              </div>
            </div>
            {d.id === 'schema' && formats.length > 1 && (
              <div className="lp-formats">
                <p className="lp-label">One Schema, Many Formats</p>
                <ul>{formats.map((f) => <li key={f} className="lp-chip">{f}</li>)}</ul>
              </div>
            )}
            <ul className="lp-more">
              {d.learnMore.map((l) => (
                <li key={l.url}>
                  <a href={l.url} dangerouslySetInnerHTML={html(renderCode(l.title))} /> — <span dangerouslySetInnerHTML={html(renderInline(l.description))} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}
```

Note: `learnMore` URLs that point at docs are site-relative (`/docs/...`); a plain `<a>` is used so a docs link that is absent in `LANDING_ONLY` builds does not warn, and GitHub links stay external. The full build checks docs links through the Catalog's `Link` components and the docs plugin.

- [ ] **Step 4: CSS for both sections (append to `landing.css`)**

```css
/* Principles */
.lp-principles { margin: 2rem 0 0; border-top: 1px solid var(--rule); }
.lp-principles li {
  display: grid;
  grid-template-columns: 2.5rem 11rem 1fr;
  gap: 0.25rem 1rem;
  padding-block: 1rem;
  border-bottom: 1px solid var(--rule);
  align-items: baseline;
}
.lp-n { font-size: 0.6875rem; letter-spacing: 0.14em; color: var(--label); font-weight: 600; }
.lp-principles strong { font-weight: 800; }
.lp-t { color: var(--fg-soft); }
@media (max-width: 640px) {
  .lp-principles li { grid-template-columns: 2rem 1fr; }
  .lp-t { grid-column: 2; }
}

/* Deep dives */
.lp-tablist { display: flex; flex-wrap: wrap; margin: 1.5rem 0; border-bottom: 1px solid var(--rule); }
.lp-tablist[hidden] { display: none; }
.lp-tablist [role='tab'] {
  padding: 0.75rem 1.25rem;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--fg-soft);
  font: 700 1rem/1.2 var(--ifm-font-family-base);
  cursor: pointer;
}
.lp-tablist [role='tab'][aria-selected='true'] { color: var(--fg); border-bottom-color: var(--accent); }
.lp-panel { margin-top: 2rem; }
.lp-panel h3 { margin: 0 0 0.5rem; font-size: 1.5rem; font-weight: 800; }
.lp-vh { position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.lp-intro { max-width: 46rem; color: var(--fg-soft); font-size: 1.0625rem; }
.lp-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 2rem; margin-top: 1.5rem; }
.lp-col { min-width: 0; }
.lp-col p { color: var(--fg-soft); }
.lp-col .lp-label { color: var(--label); }
.lp-formats { margin-top: 2rem; }
.lp-formats ul { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.lp-chip { padding: 0.25rem 0.75rem; border: 1px solid var(--accent); font-size: 0.875rem; font-weight: 600; }
.lp-more { margin: 1.5rem 0 0; color: var(--fg-soft); }
.lp-more li { padding-block: 0.25rem; }
@media (max-width: 800px) { .lp-cols { grid-template-columns: 1fr; } }
```

- [ ] **Step 5: Mount and test (GREEN)**

Add `<Principles />` and `<DeepDives />` after `<Hero />` in `src/pages/index.js` (imports included), then:

```bash
cd website && yarn build:landing 2>&1 | grep -E "ERROR|SUCCESS"; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|^✖"; yarn test 2>&1 | grep -E "^ℹ (pass|fail)"
```

Expected: dist `pass 6, fail 0`; unit tests pass. `hidden` renders as the bare attribute in the server HTML; if the tablist test's regex does not match, print `grep -o '<div[^>]*role="tablist"[^>]*>' build/index.html` and fix the regex.

- [ ] **Step 6: Interaction and no-JS check**

Serve the build (`yarn serve --port 4321 &`), then with Chromium's DevTools protocol (Node 24's global `WebSocket`; start Chromium with `--remote-debugging-port=9334`) assert: after load the tablist is visible, only the first panel is visible, clicking the "Scope" tab (`#tab-scope`) shows `#panel-scope` and hides `#panel-schema`, and ArrowRight from the first tab moves focus to the second. Separately fetch the raw HTML (`curl -s http://localhost:4321/`) and confirm all four panels are present without `hidden`. Stop servers by port and Chromium by PID (never `pkill -f`).

- [ ] **Step 7: Commit**

```bash
cd .. && git add -A website && git commit -m "feat(website): principles and tabbed deep dives that stay readable without JavaScript"
```

---

### Task 5: Block catalog

**Files:**
- Create: `website/src/components/landing/Catalog.js`
- Modify: `website/src/pages/index.js`, `website/src/components/landing/landing.css`, `website/test/dist.test.mjs`

**Interfaces:**
- Consumes: `packRows`, `titleCase`, `renderInline`, `site.categories`.
- Produces: `<section id="catalog">` with `lp-row`, `lp-cat`, `lp-tile`, `lp-learn`.

- [ ] **Step 1: Append the failing tests**

```js
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
    // One "Learn More" button per tile, linking to the block's docs page; its accessible name includes the visible text.
    const learn = /<a class="lp-learn"[^>]*>([\s\S]*?)<\/a>/.exec(t[1]);
    assert.equal(/href="([^"]*)"/.exec(learn[0])[1], b.docsUrl);
    assert.equal(decode(/aria-label="([^"]*)"/.exec(learn[0])[1]), `Learn More about ${titleCase(b.name)}`);
    assert.equal(text(learn[1]), 'Learn More');
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
```

Rebuild and run. Expected: the three new tests FAIL (`missing <section id="catalog"`).

- [ ] **Step 2: Catalog**

`website/src/components/landing/Catalog.js`:

```js
import React from 'react';
import Link from '@docusaurus/Link';
import site from '../../data/site.json';
import {renderInline} from '../../lib/inline.mjs';
import {packRows} from '../../lib/rows.mjs';
import {titleCase} from '../../lib/title-case.mjs';

const COLUMNS = 3;
const html = (markup) => ({__html: markup});

export default function Catalog() {
  const rows = packRows(site.categories, COLUMNS);
  return (
    <section id="catalog" className="lp-section" aria-labelledby="catalog-title">
      <div className="lp-wrap">
        <p className="lp-label">03 &nbsp; Block Catalog</p>
        <h2 id="catalog-title">Take Only What You Need</h2>
        <p className="lp-sub">
          Each block is a separate artifact under <code>dev.zio</code>. Copy the artifact name, add it to your build, and use it.
        </p>

        {rows.map((row, r) => (
          <div key={r} className="lp-row" {...(row.length > 1 ? {'data-shared': ''} : {})}>
            {row.map((c) => (
              <section key={c.name} className="lp-cat" data-span={Math.min(c.blocks.length, COLUMNS)}>
                <h3>{titleCase(c.name)}</h3>
                {c.note && <p className="lp-note" dangerouslySetInnerHTML={html(renderInline(c.note))} />}
                <ul className="lp-grid">
                  {c.blocks.map((b) => (
                    <li key={b.artifact} className="lp-tile">
                      <h4><Link to={b.docsUrl}>{titleCase(b.name)}</Link></h4>
                      <p className="lp-desc" dangerouslySetInnerHTML={html(renderInline(b.description))} />
                      <code className="lp-artifact">{b.artifact}</code>
                      <div className="lp-foot">
                        <p className="lp-badges">{b.platforms.join(' · ')} &nbsp;/&nbsp; Scala {b.scala.join(' · ')}</p>
                        <Link className="lp-learn" to={b.docsUrl} aria-label={`Learn More about ${titleCase(b.name)}`}>Learn More</Link>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}
```

Attribute order matters for the tests: React renders `className`, then the other props in written order, so `class` first, then `href`, `aria-label`. If a rendered `<a>` has `href` before `class`, the regexes above (`<a class="lp-learn"`) must be adjusted to the real order; print one with `grep -o '<a class="lp-learn"[^>]*>' build/index.html | head -1` and align the test to what Docusaurus's `Link` really outputs.

- [ ] **Step 3: CSS (append to `landing.css`)**

```css
/* Catalog */
.lp-sub { max-width: 40rem; color: var(--fg-soft); }
/*
 * Categories that share a row sit side by side on the same columns as the tile grid below (three columns wide, two
 * on tablets); a category with several tiles spans that many columns. On phones everything stacks. Sections in a
 * shared row are equally tall and each tile fills its section.
 */
.lp-row[data-shared] .lp-cat { display: flex; flex-direction: column; }
.lp-row[data-shared] .lp-grid { flex: 1; }
@media (min-width: 36rem) {
  .lp-row[data-shared] { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0 var(--seam); align-items: stretch; }
  .lp-row[data-shared] .lp-cat[data-span='2'], .lp-row[data-shared] .lp-cat[data-span='3'] { grid-column: span 2; }
}
@media (min-width: 58rem) {
  .lp-row[data-shared] { grid-template-columns: repeat(3, 1fr); }
  .lp-row[data-shared] .lp-cat[data-span='3'] { grid-column: span 3; }
}
.lp-cat { margin-top: 2.5rem; }
.lp-cat h3 { margin: 0 0 0.25rem; font-size: 1.125rem; font-weight: 800; }
.lp-note { margin: 0 0 0.75rem; color: var(--fg-soft); font-size: 0.9375rem; }
.lp-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: var(--seam); margin: 1rem 0 0; }
.lp-tile { display: flex; flex-direction: column; gap: 0.5rem; padding: 1rem; background: var(--surface); border: 1px solid var(--rule); min-width: 0; }
.lp-tile h4 { margin: 0; font-size: 1rem; font-weight: 800; }
.lp-desc { margin: 0; color: var(--fg-soft); font-size: 0.875rem; flex: 1; }
.lp-artifact { overflow-wrap: anywhere; font-size: 0.75rem; }
/* Badges on the left, the button on the right of one row; the button wraps to its own right-aligned line only if the tile is very narrow */
.lp-foot { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem 0.75rem; margin-top: 0.25rem; }
.lp-learn {
  margin-left: auto;
  padding: 0.5rem 0.9rem;
  border: 1px solid var(--accent);
  color: var(--fg);
  font: 700 0.8125rem/1.2 var(--ifm-font-family-base);
  text-decoration: none;
}
.lp-learn:hover { background: var(--accent); color: #fff; text-decoration: none; }
.lp-badges { margin: 0; color: var(--label); font-size: 0.75rem; letter-spacing: 0.04em; }
```

- [ ] **Step 4: Mount, test (GREEN), and verify the layout in a browser**

Add `<Catalog />` after `<DeepDives />` in the page. Then `cd website && yarn build:landing 2>&1 | grep -E "ERROR|SUCCESS"; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|^✖"`. Expected: dist `pass 9, fail 0`.

Then measure with Chromium's DevTools protocol at 1280, 1024, 800 and 360 px (serve `build/` on a port, stop it by port afterwards): the Resource Management and Dependency Injection sections share a row with equal tile widths and heights; Streaming and Telemetry share a row (three equal tiles at >= 928 px); no horizontal overflow (`document.documentElement.scrollWidth <= innerWidth`); every "Learn More" button's right edge equals its tile's inner right edge.

- [ ] **Step 5: Commit**

```bash
cd .. && git add -A website && git commit -m "feat(website): block catalog with Learn More buttons and shared rows"
```

---

### Task 6: Page-wide rules: title case, no copy buttons, order, anchors, no JS

**Files:**
- Modify: `website/test/dist.test.mjs`, small fixes in components if a test finds a real defect

- [ ] **Step 1: Append the tests**

```js
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
      .map((l) => l.replace(' &nbsp; ', ' ').replace(/ /g, ' ').replace(/\s+/g, ' '))
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
```

- [ ] **Step 2: Run, and fix real defects only**

```bash
cd website && node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)|^✖"
```

Expected: `pass 13, fail 0`. If a test fails, decide whether the page or the test is wrong. Fix the page for real defects (for example a lowercase heading); fix the regex for pure markup differences (for example `&nbsp;` versus the NBSP character in labels) and say which in the commit body. The `<h4>` list must equal the tiles in order, and the principles `<strong>` list must equal the data.

- [ ] **Step 3: Commit**

```bash
cd .. && git add -A website && git commit -m "test(website): page-wide static-output rules for the landing page"
```

---

### Task 7: Lighthouse, accessibility and the landing visual pass (Node only)

**Files:**
- Create: `website/lighthouserc.json`
- Modify: components and CSS only where a check finds a real defect

- [ ] **Step 1: Lighthouse config**

`website/lighthouserc.json`:

```json
{
  "ci": {
    "collect": {
      "staticDistDir": "./build",
      "url": ["http://localhost/index.html"],
      "numberOfRuns": 3,
      "settings": { "chromeFlags": "--no-sandbox --headless=new" }
    },
    "assert": {
      "assertions": {
        "categories:performance": ["error", { "minScore": 0.95 }],
        "categories:accessibility": ["error", { "minScore": 0.95 }],
        "categories:best-practices": ["error", { "minScore": 0.95 }],
        "categories:seo": ["error", { "minScore": 0.95 }]
      }
    }
  }
}
```

- [ ] **Step 2: Run Lighthouse on the landing-only build**

```bash
cd website && yarn build:landing >/dev/null 2>&1 && CHROME_PATH=/nix/var/nix/profiles/default/bin/chromium npx --yes @lhci/cli@0.15.1 autorun 2>&1 | tail -25
```

(If `staticDistDir` with the `url` entry is rejected, drop `url` and let lhci discover `index.html`; keep `404.html` out of the assertions with `"url"` omitted and `"numberOfRuns": 3` only if it also passes. Record which form worked.)

Expected: all four categories at 0.95 or higher. **If performance is below 0.95, do not lower the threshold.** Read the failing audits, apply the cheap fixes (for example `font-display: swap` already set; preload the Manrope file with `headTags` in the config; avoid layout shift from the late-appearing tablist by reserving its height with `min-height`), re-run, and if it still fails, stop and report the exact scores and the remaining audits so the maintainer can choose between optimizing further and an agreed lower performance threshold.

- [ ] **Step 3: Accessibility and visual pass**

Using Chromium's DevTools protocol (same technique as earlier tasks), check at 1280 and 360 px in light and dark (`Emulation.setEmulatedMedia` with `prefers-color-scheme`): no horizontal overflow; text contrast is legible on the hero, labels, tiles and Learn More buttons; the focus ring is visible when tabbing (Tab through the navbar and the first tiles); with `prefers-reduced-motion: reduce` the module-square animation name computes to `none`; the Docusaurus colour-mode toggle switches the page body between white and ink while the hero stays ink. Fix real defects in CSS or components.

- [ ] **Step 4: Run all tests and commit**

```bash
cd website && yarn test 2>&1 | grep -E "^ℹ (pass|fail)"; node --test test/dist.test.mjs 2>&1 | grep -E "^ℹ (pass|fail)"; cd .. && git add -A website && git commit -m "feat(website): Lighthouse config and landing accessibility fixes"
```

---

### Task 8: Full build with the docs: links, routes and the docs restyling pass

**Files:**
- Modify: `website/src/css/custom.css` (and components) only where a real defect shows

- [ ] **Step 1: Generate the docs and run the full build**

Follow `AGENTS.md` for sbt (logs to the scratchpad directory, since `.git` is a file in a worktree):

```bash
cd "$(git rev-parse --show-toplevel)"
LOG=<scratchpad>/sbt-mdoc.log
sbt --client -Dsbt.color=false docs/mdoc >"$LOG" 2>&1; echo "Exit: $?"; tail -5 "$LOG"
cd website && yarn build 2>&1 | grep -E "ERROR|SUCCESS|Broken|broken" | head
```

Expected: mdoc exit 0 (minutes); `yarn build` ends with `[SUCCESS] Generated static files in "build"` and **no** broken-link failures (the build runs without `LANDING_ONLY`, so `onBrokenLinks: 'throw'` applies to every landing link: `/docs/`, `/docs/reference/schema/`, all 41 tile links). If a landing link fails, the docs path mapping is wrong for that block: fix the generator's mapping or report the row; never switch the check off.

- [ ] **Step 2: Check the routes**

```bash
ls build/docs | head; test -f build/docs/index.html && echo "docs home ok"; test -f build/docs/reference/schema/index.html && echo "schema ok"; test -f build/index.html && echo "landing ok"
```

Expected: all three `ok` lines.

- [ ] **Step 3: Visual pass over representative docs pages, light and dark**

Serve `build/` (`yarn serve --port 4321 &`) and capture 1280 px screenshots (light and dark via DevTools `Emulation.setEmulatedMedia`) of: `/docs/` (the docs home with its tables), `/docs/reference/schema/` (long page with code, tables and the sidebar), a page with admonitions or tabs if one exists (`grep -l ":::" website/docs -r | head -3`), and a narrow 360 px page. Read each image. Check: navbar and footer match the landing page; sidebar and table of contents are legible (active item visible); code blocks are flat with brand colours; tables have hairline borders; admonitions keep their standard semantic colours but are square and flat; links are underlined and readable in both modes; no stray rounded corners or shadows; Manrope is used.

- [ ] **Step 4: Fix defects**

Adjust `custom.css` (Infima variable overrides and the small rules at its end) for anything found. Keep the brand guard green (`yarn test`). Re-run the build and re-check the affected pages. Record in the commit body which pages were checked.

- [ ] **Step 5: Commit**

```bash
cd .. && git add -A website && git commit -m "fix(website): docs restyling fixes from the full-build visual pass"
```

(If nothing needed fixing, skip the commit and say so in the report.)

---

### Task 9: CI and deploy

**Files:**
- Modify: `project/CiWorkflow.scala`; regenerate `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: yarn scripts from Tasks 1 and 3; Netlify action usage from `deploy-preview.yml`.

- [ ] **Step 1: Edit the `landing` job (now Node-only, on `website/`)**

Replace the body of `lazy val landing` in `project/CiWorkflow.scala` with:

```scala
  /**
   * Builds and checks the landing page in `website/` on Node only (no sbt): unit tests, a `LANDING_ONLY` build,
   * static-output assertions, external link check, and Lighthouse. zio-sbt-ci triggers are workflow-wide, so this runs
   * on every pull request. `SingleStep` has no working directory, hence the `--cwd` and `cd` forms.
   */
  lazy val landing: Def.Initialize[Job] = Def.setting(
    Job(
      id = "landing",
      name = "Landing",
      jobTimeout = Some(20),
      steps = Seq(
        Checkout.value,
        SingleStep(
          name = "Setup Node.js",
          uses = Some(ActionRef("actions/setup-node@v7")),
          parameters = Map("node-version" -> Json.Str("24.12.0"))
        ),
        SingleStep(name = "Install yarn", run = Some("npm install -g yarn")),
        SingleStep(name = "Install website dependencies", run = Some("yarn install --cwd website --frozen-lockfile")),
        SingleStep(name = "Website unit tests", run = Some("yarn --cwd website test")),
        SingleStep(
          name = "Build the landing page and check the static output",
          run = Some("yarn --cwd website check:landing"),
          env = Map("GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}")
        ),
        SingleStep(name = "Check external links", run = Some("yarn --cwd website check:links")),
        SingleStep(
          name = "Landing Lighthouse (95+)",
          run = Some("cd website && npx --yes @lhci/cli@0.15.1 autorun")
        )
      )
    ).withPermissions("contents" -> "read")
  )
```

- [ ] **Step 2: Edit `buildDocs`: environment for the build and the production deploy step**

In `buildDocs`, change the "Check website build process" step to pass the site URL and token, and add the production deploy step directly after the preview artifact upload. The first replaces the existing step with `run = Some("yarn --cwd website build")`:

```scala
        SingleStep(
          name = "Check website build process",
          condition = Some(HasWebsite),
          run = Some("yarn --cwd website build"),
          env = Map(
            "SITE_URL"     -> "${{ vars.SITE_URL }}",
            "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}"
          )
        ),
```

and add after the "Upload website build artifact" step:

```scala
        // Production deploy: only for pushes to main and for releases. A release rebuilds the site, so the install line
        // shows the new version right away. Netlify is only the host; the site is built here (it needs mdoc and sbt).
        SingleStep(
          name = "Deploy to Netlify (production)",
          uses = Some(ActionRef("nwtgck/actions-netlify@v4.0")),
          condition = Some(HasWebsite && (expr("github.event_name == 'push'") || expr("github.event_name == 'release'"))),
          parameters = Map(
            "publish-dir"                -> Json.Str("./website/build"),
            "production-deploy"          -> Json.Bool(true),
            "github-token"               -> Json.Str("${{ secrets.GITHUB_TOKEN }}"),
            "enable-pull-request-comment" -> Json.Bool(false),
            "enable-commit-comment"      -> Json.Bool(false)
          ),
          env = Map(
            "NETLIFY_AUTH_TOKEN" -> "${{ secrets.NETLIFY_AUTH_TOKEN }}",
            "NETLIFY_SITE_ID"    -> "${{ secrets.NETLIFY_PRODUCTION_SITE_ID }}"
          )
        ),
```

Do not change `ciPullRequestApprovalJobs` or any other job.

- [ ] **Step 3: Regenerate, format, commit, verify**

```bash
cd "$(git rev-parse --show-toplevel)"; export PATH="$PWD/.git/bin:$PATH"
LOG=<scratchpad>/sbt-ci.log
sbt --client -Dsbt.color=false ciGenerateGithubWorkflow >"$LOG" 2>&1; echo "generate: $?"
sbt --client -Dsbt.color=false scalafmtSbt >>"$LOG" 2>&1; echo "format: $?"
git diff --stat .github/workflows project
git add -A project .github && git commit -m "ci(website): landing checks on website/, production deploy to Netlify"
sbt --client -Dsbt.color=false ciCheckGithubWorkflow >>"$LOG" 2>&1; echo "check: $?"
sbt --client -Dsbt.color=false scalafmtSbtCheck >>"$LOG" 2>&1; echo "fmtcheck: $?"
git status --short | head -3
```

Expected: `generate`, `format`, `check`, `fmtcheck` all `0`; the working tree is clean at the end. The `git diff --stat` before the commit must show only `project/CiWorkflow.scala` and `.github/workflows/ci.yml`, and the `ci.yml` diff must show **only** the `landing` job rewritten and the `buildDocs` env and deploy-step changes; if any other job changes, stop and report. (Never hand-edit `ci.yml`.)

---

### Task 10: Remove the Astro site, move the docs of the work, update the PR

**Files:**
- Delete: `landing/**`
- Create: `website/README.md`
- Modify: `AGENTS.md`, `docs/superpowers/specs/2026-10-06-homepage-design.md` (status note), `docs/superpowers/plans/2026-10-06-homepage.md` (status note)

- [ ] **Step 1: Delete `landing/` and keep its README content**

```bash
cd "$(git rev-parse --show-toplevel)"
git mv landing/README.md website/README.md
git rm -r -q landing
test -e landing && echo "STILL THERE" || echo "landing removed"
```

Rewrite `website/README.md` for the unified site (keep the "docs/index.md contract" and the landing-only rules sections, update paths and commands). It must say: what the site is (landing at `/`, docs under `/docs/`, one Docusaurus app); commands (`yarn start` and `yarn build` run the generator first; `yarn test`, `yarn check:landing`, `yarn check:links`; `LANDING_ONLY=1` builds the landing page without sbt); how CI builds and deploys (GitHub Actions builds with mdoc then deploys to Netlify on `main` and `release`; secrets `NETLIFY_AUTH_TOKEN`, `NETLIFY_PRODUCTION_SITE_ID`, repository variable `SITE_URL`); the `docs/index.md` contract and the landing-only rules (`CATEGORY_RENAMES`, `CATEGORY_SPLITS`, `PRINCIPLE_TEXT`, one tile per artifact, row packing), unchanged from the old README except the paths.

- [ ] **Step 2: `AGENTS.md`**

Replace the "Landing page lives in `landing/`" bullet with:

```markdown
- **The website lives in `website/`** (Docusaurus: landing page at `/`, docs under `/docs/`). The landing content is generated from `docs/index.md` (table shapes, section titles and the `@VERSION@` placeholder are a contract; see `website/README.md`). After touching `docs/index.md`, run `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing`. Never hand-edit `website/src/data/site.json` or `website/static/brand/` (generated, gitignored).
```

- [ ] **Step 3: Status notes on the superseded documents**

Add one line under the first heading of `docs/superpowers/specs/2026-10-06-homepage-design.md` and `docs/superpowers/plans/2026-10-06-homepage.md`:

```markdown
> **Superseded in part:** hosting, framework and deployment (Astro in `landing/`, Netlify Git builds) were replaced by the unified Docusaurus site; see `2026-10-07-docusaurus-migration-design.md`. Audience, page structure, brand and copy rules still apply.
```

Verify both files still compile as MDX (the docs build processes them): run `node` with `@mdx-js/mdx` `compile` on each (install `@mdx-js/mdx` in a scratch directory, never in the repo).

- [ ] **Step 4: Final verification**

```bash
cd website && yarn install --frozen-lockfile >/dev/null 2>&1; yarn test 2>&1 | grep -E "^ℹ (pass|fail)"; yarn check:landing 2>&1 | grep -E "^ℹ (pass|fail)"
cd .. && git diff --stat main -- docs | grep -v "docs/superpowers" | head
```

Expected: unit and static-output tests pass; the last command prints nothing (the only `docs/` changes are under `docs/superpowers/`). Run the full build once more if Task 8's full build is older than the last config change: `sbt docs/mdoc` (if needed) then `yarn build`.

- [ ] **Step 5: Commit, push, update the draft PR**

```bash
git add -A && git commit -m "feat(website): remove the Astro landing site; the Docusaurus website now serves landing and docs"
git push khajavi homepage
gh pr edit 1719 --repo zio/zio-blocks --title "feat(website): unified Docusaurus website (landing page + docs)" --body-file <scratchpad>/pr-body-docusaurus.md
```

The PR body (written to `<scratchpad>/pr-body-docusaurus.md`) must use plain-English "Root cause" and "Fix" sections (no "(plain English)" in titles): the root cause is that the project had no homepage and the earlier standalone Astro site would have meant two sites to build, deploy and keep consistent; the fix is one Docusaurus app serving the landing page at `/` and the docs under `/docs/`, built by GitHub Actions (mdoc needs sbt), deployed to Netlify on `main` and on release, previews showing both. Include: what changed, what stayed untouched (`docs/` content, zio.dev publication, README generation), the maintainer checklist (production Netlify site and secrets `NETLIFY_PRODUCTION_SITE_ID` and `SITE_URL`, canonical-URL policy, whether `landing` is a required check), and the Lighthouse scores measured in Task 7 (state the exact numbers and the date, and that CI re-runs them).

---

## Self-Review Notes

- **Spec coverage:** goal and URL map (Tasks 2, 8); data pipeline and site-relative links (Task 1); components and theming (Tasks 2-5); progressive no-JS tabs (Task 4); quality gates (Tasks 1, 2, 5, 6, 7); CI and deploy (Task 9); removal and docs (Task 10); risks: Lighthouse (Task 7), docs restyling (Task 8), docs untouched (Task 10 Step 4).
- **Open items for the maintainer** (not code): production Netlify site and `NETLIFY_PRODUCTION_SITE_ID`, `SITE_URL` variable, canonical-URL policy, required-check decision for `landing`.
