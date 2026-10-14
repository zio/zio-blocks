# Landing Page Scroll Behaviour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the landing page's Hero and Principles sections each fill one screen and settle into place on scroll, with one signature squares hand-off motion and a section progress rail, while Deep Dives and Catalog keep scrolling freely.

**Architecture:** Pure CSS does the snapping (`scroll-snap-type: y proximity`, scoped to the landing page with `html:has(.lp-page)`) and the motion (CSS scroll-driven animations behind `@supports`). A small React component (`SectionRail`) renders a fixed side rail of anchor links and tracks the active section with `IntersectionObserver`. Section ids and labels live in one shared module so the page, the rail and the tests cannot drift.

**Tech Stack:** Docusaurus 3.10 (React 18), plain CSS in `landing.css`, `node --test` unit tests, the existing `test/dist.test.mjs` static-output checks.

**Spec:** `docs/superpowers/specs/2026-10-14-landing-scroll-design.md`

## In plain English

- **Problem:** the landing page's sections just stack, so after scrolling you often see half of one section and half of the next, and nothing feels like it "arrives".
- **Goal:** Hero and Principles each fill one screen and the browser gently pulls the page into place when you stop near them; the two tall sections scroll normally; one motion (the module squares drifting away as Principles rises in) ties the page together; a small side rail shows where you are.
- **Steps:**
  1. Build the side rail (a shared list of sections, one component, mounted on the page).
  2. Add the soft-snap layout (screen-height sections, snap points, reduced-motion switch-off).
  3. Add the motion (squares hand-off and one-time settle-in), only where the browser supports it.
  4. Document it and run the full checks plus a manual browser pass.

## Global Constraints

- Reuse existing tokens only: ink `#141a2e`, ultramarine `#2d3f8f`, lifted `#4b5fc4`, field `#1e2647`, paper-ink `#f3f5fc`; font Manrope; square corners. No new colours or fonts.
- Flat brand rules (`scripts/lib/brand-guard.mjs`): no `border-radius` other than 0, no `box-shadow`, no gradients, no rotation or skew, no `drop-shadow`. Motion uses only `opacity` and `translate*`.
- Snap is `proximity`, never `mandatory`. No JS scroll hijacking, no wheel handlers.
- Snap and the rail apply to the landing page only; docs pages are unchanged.
- Motion (`animation-timeline`) lives only inside `@supports (animation-timeline: view())` and `@media (prefers-reduced-motion: no-preference)`.
- Existing content contracts stay: section ids `hero`, `principles`, `deep-dives`, `catalog`; classes and text asserted in `website/test/dist.test.mjs` are unchanged.
- The rail renders outside `<main id="landing">` so the dist test's section parsing is unchanged.
- Rail link labels are title case (`Hero`, `Principles`, `Deep Dives`, `Catalog`).
- Run website commands from `website/`. `README.md` is generated and never edited; `docs/index.md` is not touched.
- Run `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing` before finishing (per `AGENTS.md`).

## Review Focus

Input classes or conditions the spec implies but no obvious test would exercise; each has a test in the task that owns the code:

1. **Short viewport or large text zoom:** Hero or Principles taller than the screen must grow, not clip (Task 2: CSS uses `min-height`, never a fixed `height`).
2. **Docs pages:** snap must not leak onto docs pages (Task 2: no `scroll-snap-type` outside `html:has(.lp-page)`, and none under reduced motion).
3. **Browser without scroll-driven animation, or reduced motion:** content must stay visible, never stuck at `opacity: 0` (Task 3: all motion declarations live only inside the `@supports` block).
4. **No JavaScript / server-rendered HTML:** the rail links must exist and work, with the first section marked current (Task 1: dist test on the static HTML).
5. **Jumping to `/#catalog` or clicking a rail link:** target must not hide under the sticky navbar (Task 2: `scroll-padding-top` set from the navbar height).

---

## File Structure

| File | Responsibility |
| --- | --- |
| `website/src/lib/rail.mjs` (create) | Single source of truth: ordered list of `{id, label}` for the four landing sections |
| `website/src/components/landing/SectionRail.js` (create) | Renders the rail `<nav>`; tracks the active section with `IntersectionObserver` |
| `website/src/pages/index.js` (modify) | Derive `ANCHORS` from `RAIL`; mount `<SectionRail />` after `</main>` |
| `website/src/components/landing/Principles.js` (modify) | Add the `lp-screen` class so Principles fills a screen |
| `website/src/components/landing/landing.css` (modify) | Snap rules, screen-height sections, rail styles, motion, reduced-motion override |
| `website/test/unit/rail.test.mjs` (create) | Unit test for `RAIL` |
| `website/test/unit/landing-scroll.test.mjs` (create) | CSS contract tests (snap scoping, `min-height`, `@supports` containment, reduced motion) |
| `website/test/dist.test.mjs` (modify) | Assert rail markup in the built HTML |
| `website/README.md` (modify) | Short "Scroll behaviour" section |

`ModuleField.js` needs no change: the hand-off animates the existing `.lp-field` wrapper from CSS.

---

### Task 1: Section rail

**Files:**
- Create: `website/src/lib/rail.mjs`
- Create: `website/src/components/landing/SectionRail.js`
- Create: `website/test/unit/rail.test.mjs`
- Modify: `website/src/pages/index.js`
- Modify: `website/src/components/landing/landing.css` (append rail styles)
- Modify: `website/test/dist.test.mjs` (add one test, one import)

**Interfaces:**
- Produces: `RAIL: ReadonlyArray<{id: string, label: string}>` exported from `website/src/lib/rail.mjs`, in page order `hero`, `principles`, `deep-dives`, `catalog`. `SectionRail` default export (no props). Static markup: `<nav class="lp-rail" aria-label="Sections">` containing four `<a class="lp-rail-link" href="#id" aria-label="Label" data-label="Label">`, with `aria-current="true"` on exactly one (the first, in server-rendered HTML).

- [ ] **Step 1: Write the failing unit test**

Create `website/test/unit/rail.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RAIL } from '../../src/lib/rail.mjs';
import { titleCase } from '../../src/lib/title-case.mjs';

test('the rail lists the four landing sections in page order', () => {
  assert.deepEqual(
    RAIL.map((s) => s.id),
    ['hero', 'principles', 'deep-dives', 'catalog'],
  );
});

test('rail labels are title case and unique', () => {
  const labels = RAIL.map((s) => s.label);
  assert.deepEqual(labels, ['Hero', 'Principles', 'Deep Dives', 'Catalog']);
  for (const l of labels) assert.equal(titleCase(l), l);
  assert.equal(new Set(labels).size, labels.length);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd website && node --test test/unit/rail.test.mjs`
Expected: FAIL with `Cannot find module '.../src/lib/rail.mjs'`.

- [ ] **Step 3: Create the shared list**

Create `website/src/lib/rail.mjs`:

```js
// The landing sections, in page order. The section ids are the `id` attributes in src/components/landing/*;
// the labels name the rail links.
export const RAIL = [
  { id: 'hero', label: 'Hero' },
  { id: 'principles', label: 'Principles' },
  { id: 'deep-dives', label: 'Deep Dives' },
  { id: 'catalog', label: 'Catalog' },
];
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `cd website && node --test test/unit/rail.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the failing dist test**

In `website/test/dist.test.mjs`, add to the imports (below the `titleCase` import):

```js
import { RAIL } from '../src/lib/rail.mjs';
```

and add this test after the `'principles'` test:

```js
test('the section rail links to every landing section and sits outside the landing content', () => {
  const start = html.indexOf('<nav class="lp-rail"');
  assert.notEqual(start, -1, 'missing <nav class="lp-rail"');
  const rail = html.slice(start, html.indexOf('</nav>', start));
  assert.match(rail.slice(0, rail.indexOf('>')), /aria-label="Sections"/);
  assert.equal(main.includes('lp-rail'), false, 'the rail must be outside <main id="landing">');
  const links = [...rail.matchAll(/<a [^>]*>/g)].map((m) => m[0]);
  assert.deepEqual(links.map((t) => /href="#([^"]+)"/.exec(t)[1]), RAIL.map((s) => s.id));
  assert.deepEqual(links.map((t) => /aria-label="([^"]+)"/.exec(t)[1]), RAIL.map((s) => s.label));
  // Server-rendered HTML marks the first section as current; exactly one link is.
  assert.deepEqual(links.map((t) => /aria-current="true"/.test(t)), [true, false, false, false]);
  for (const s of RAIL) assert.ok(main.includes(`<section id="${s.id}"`), `no <section id="${s.id}">`);
});
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd website && yarn check:landing`
Expected: build succeeds, then FAIL in the new test with `missing <nav class="lp-rail"`.

- [ ] **Step 7: Create the component**

Create `website/src/components/landing/SectionRail.js`:

```js
import React, {useEffect, useState} from 'react';
import {RAIL} from '../../lib/rail.mjs';

// A section is "current" while it crosses a thin band just above the middle of the viewport; that works for
// sections shorter and taller than the screen alike. Without JavaScript the links still jump to their sections
// and the first one stays marked.
const BAND = '-45% 0px -50% 0px';

export default function SectionRail() {
  const [active, setActive] = useState(RAIL[0].id);

  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((e) => { if (e.isIntersecting) setActive(e.target.id); }),
      {rootMargin: BAND},
    );
    RAIL.forEach(({id}) => {
      const el = document.getElementById(id);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, []);

  return (
    <nav className="lp-rail" aria-label="Sections">
      {RAIL.map(({id, label}) => (
        <a
          key={id}
          className="lp-rail-link"
          href={`#${id}`}
          aria-label={label}
          data-label={label}
          {...(id === active ? {'aria-current': 'true'} : {})}
        />
      ))}
    </nav>
  );
}
```

- [ ] **Step 8: Mount it and derive the anchors**

Replace `website/src/pages/index.js` with:

```js
import React from 'react';
import Layout from '@theme/Layout';
import useBrokenLinks from '@docusaurus/useBrokenLinks';
import Hero from '../components/landing/Hero';
import Principles from '../components/landing/Principles';
import DeepDives from '../components/landing/DeepDives';
import Catalog from '../components/landing/Catalog';
import SectionRail from '../components/landing/SectionRail';
import {RAIL} from '../lib/rail.mjs';
import '../components/landing/landing.css';

const DESCRIPTION =
  'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.';

// Section ids are plain JSX attributes, which Docusaurus' anchor check cannot see; register them
// so navbar links such as `/#catalog` validate on every page.
const ANCHORS = RAIL.map((s) => s.id);

export default function Home() {
  const brokenLinks = useBrokenLinks();
  ANCHORS.forEach((id) => brokenLinks.collectAnchor(id));
  return (
    <Layout title="Type-Safe, Modular Building Blocks for Scala" description={DESCRIPTION}>
      <main id="landing" className="lp-page">
        <Hero />
        <Principles />
        <DeepDives />
        <Catalog />
      </main>
      <SectionRail />
    </Layout>
  );
}
```

- [ ] **Step 9: Append the rail styles**

Append to `website/src/components/landing/landing.css`:

```css

/* Section rail: a column of small squares on the right edge, one per landing section. Hidden on narrow screens. */
.lp-rail { display: none; }
@media (min-width: 62.5rem) {
  .lp-rail {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    position: fixed;
    right: 0.75rem;
    top: 50%;
    transform: translateY(-50%);
    z-index: 10;
  }
}
.lp-rail-link { position: relative; display: block; width: 1.25rem; height: 1.25rem; padding: 0.25rem; }
.lp-rail-link::before { content: ''; display: block; width: 100%; height: 100%; border: 2px solid var(--lifted); }
.lp-rail-link[aria-current='true']::before { background: var(--lifted); }
.lp-rail-link::after {
  content: attr(data-label);
  position: absolute;
  right: calc(100% + 0.25rem);
  top: 50%;
  transform: translateY(-50%);
  padding: 0.375rem 0.625rem;
  background: var(--ink);
  color: var(--paper-ink);
  font: 600 0.75rem/1 var(--ifm-font-family-base);
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
}
.lp-rail-link:hover::after, .lp-rail-link:focus-visible::after { opacity: 1; }
```

- [ ] **Step 10: Run all website checks**

Run: `cd website && yarn test && yarn check:landing`
Expected: all unit tests and the dist tests PASS (including the new rail test and the existing title-case test, which only scans `<main>`).

- [ ] **Step 11: Commit**

```bash
git add website/src/lib/rail.mjs website/src/components/landing/SectionRail.js website/src/pages/index.js website/src/components/landing/landing.css website/test/unit/rail.test.mjs website/test/dist.test.mjs
git commit -m "feat(website): add a section progress rail to the landing page"
```

---

### Task 2: Soft snap and screen-height sections

**Files:**
- Create: `website/test/unit/landing-scroll.test.mjs`
- Modify: `website/src/components/landing/landing.css`
- Modify: `website/src/components/landing/Principles.js`

**Interfaces:**
- Consumes: section ids `hero`, `principles`, `deep-dives`, `catalog` (unchanged).
- Produces: class `lp-screen` (a section that fills the viewport under the navbar); CSS contract asserted by `landing-scroll.test.mjs`, whose `block(source, head)` helper Task 3 reuses.

- [ ] **Step 1: Write the failing CSS contract test**

Create `website/test/unit/landing-scroll.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const css = readFileSync(fileURLToPath(new URL('../../src/components/landing/landing.css', import.meta.url)), 'utf8');

/** The text between the braces of the first rule or at-rule whose head is `head` (braces balanced). */
function block(source, head) {
  const start = source.indexOf(head);
  assert.notEqual(start, -1, `landing.css must contain: ${head}`);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  return assert.fail(`unbalanced braces after: ${head}`);
}

test('snap is soft and scoped to the landing page', () => {
  const rule = block(css, 'html:has(.lp-page)');
  assert.match(rule, /scroll-snap-type:\s*y proximity;/);
  assert.doesNotMatch(css, /mandatory/);
  // Every snap-type declaration is either inside the landing-scoped rule or switches snap off.
  const uses = [...css.matchAll(/scroll-snap-type:\s*([^;]+);/g)].map((m) => m[1].trim());
  assert.deepEqual(uses.sort(), ['none', 'y proximity']);
});

test('hash targets and snap points clear the sticky navbar', () => {
  assert.match(block(css, 'html:has(.lp-page)'), /scroll-padding-top:\s*var\(--ifm-navbar-height\);/);
  const align = block(css, '#hero, #principles, #deep-dives, #catalog');
  assert.match(align, /scroll-snap-align:\s*start;/);
});

test('hero and principles fill the screen with min-height, never a fixed height', () => {
  const rule = block(css, '.lp-hero, .lp-screen');
  assert.match(rule, /min-height:\s*calc\(100svh - var\(--ifm-navbar-height\)\);/);
  assert.doesNotMatch(rule, /(^|[;\s])height:/);
});

test('reduced motion turns snap off', () => {
  const reduced = block(css, '@media (prefers-reduced-motion: reduce)');
  assert.match(reduced, /html:has\(\.lp-page\)\s*\{\s*scroll-snap-type:\s*none;\s*\}/);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd website && node --test test/unit/landing-scroll.test.mjs`
Expected: FAIL with `landing.css must contain: html:has(.lp-page)`.

- [ ] **Step 3: Make Principles a screen-height section**

In `website/src/components/landing/Principles.js`, change the section opening tag to:

```js
    <section id="principles" className="lp-section lp-screen" aria-labelledby="principles-title">
```

- [ ] **Step 4: Add the snap and layout CSS**

In `website/src/components/landing/landing.css`, insert after the `.lp-section h2 { ... }` rule (before `.lp-btn`):

```css

/*
 * Scroll: soft snap. The page is pulled to a section edge only when scrolling stops near it (`proximity`), and only on
 * the landing page. Hero and Principles fill one screen under the sticky navbar; `min-height` lets them grow instead
 * of clipping on short windows or with large text. Deep Dives and Catalog are taller than a screen and snap only at
 * their top edge.
 */
html:has(.lp-page) {
  scroll-snap-type: y proximity;
  scroll-padding-top: var(--ifm-navbar-height);
}
#hero, #principles, #deep-dives, #catalog { scroll-snap-align: start; }
.lp-hero, .lp-screen {
  min-height: calc(100svh - var(--ifm-navbar-height));
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.lp-hero > .lp-wrap, .lp-screen > .lp-wrap { width: 100%; }
```

and append at the end of the file:

```css

@media (prefers-reduced-motion: reduce) {
  html:has(.lp-page) { scroll-snap-type: none; }
}
```

- [ ] **Step 5: Run the unit tests to verify they pass**

Run: `cd website && yarn test`
Expected: PASS, including the four new tests and the brand-guard and theme-contract suites.

- [ ] **Step 6: Run the dist checks**

Run: `cd website && yarn check:landing`
Expected: PASS (markup changed only by one extra class on the Principles section).

- [ ] **Step 7: Commit**

```bash
git add website/test/unit/landing-scroll.test.mjs website/src/components/landing/landing.css website/src/components/landing/Principles.js
git commit -m "feat(website): soft-snap the landing page's Hero and Principles to one screen"
```

---

### Task 3: Squares hand-off and settle-in motion

**Files:**
- Modify: `website/src/components/landing/landing.css`
- Modify: `website/test/unit/landing-scroll.test.mjs`

**Interfaces:**
- Consumes: `.lp-field` (the squares wrapper in `ModuleField.js`), `#hero`, `.lp-principles li`, `.lp-cat h3`; the `block(source, head)` helper in `landing-scroll.test.mjs`.
- Produces: keyframes `lp-handoff` and `lp-settle`; the named scroll timeline `--hero-out`. All of it only inside the `@supports (animation-timeline: view())` block.

- [ ] **Step 1: Write the failing tests**

Append to `website/test/unit/landing-scroll.test.mjs`:

```js

const SUPPORTS = '@supports (animation-timeline: view())';

test('every scroll-driven declaration lives inside the @supports block, so unsupported browsers see static content', () => {
  const inside = block(css, SUPPORTS);
  const outside = css.replace(inside, '');
  for (const token of ['animation-timeline', 'view-timeline', 'lp-handoff', 'lp-settle']) {
    assert.equal(outside.includes(token), false, `${token} must only appear inside ${SUPPORTS}`);
    assert.ok(inside.includes(token), `${SUPPORTS} must use ${token}`);
  }
});

test('motion only runs when the visitor has not asked for reduced motion', () => {
  const inside = block(css, SUPPORTS);
  const noPref = block(inside, '@media (prefers-reduced-motion: no-preference)');
  assert.ok(noPref.includes('animation-timeline'));
  assert.equal(inside.replace(noPref, '').includes('animation-timeline'), false);
});

test('the hand-off moves the squares wrapper and the settle-in covers principles rows and catalog headings', () => {
  const noPref = block(block(css, SUPPORTS), '@media (prefers-reduced-motion: no-preference)');
  assert.match(noPref, /#hero\s*\{\s*view-timeline:\s*--hero-out block;\s*\}/);
  assert.match(block(noPref, '#hero .lp-field'), /animation-timeline:\s*--hero-out;/);
  assert.match(noPref, /\.lp-principles li, \.lp-cat h3\s*\{/);
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd website && node --test test/unit/landing-scroll.test.mjs`
Expected: the three new tests FAIL with `landing.css must contain: @supports (animation-timeline: view())`.

- [ ] **Step 3: Add the motion CSS**

In `website/src/components/landing/landing.css`, insert immediately before the final `@media (prefers-reduced-motion: reduce)` block added in Task 2:

```css

/*
 * Motion, only where the browser can tie animation to scroll position. Elsewhere the page is static (the squares keep
 * their load-time rise above). The squares drift down and fade as Hero scrolls out; Principles rows and Catalog
 * headings fade up once as they enter. Opacity and translate only.
 */
@supports (animation-timeline: view()) {
  @media (prefers-reduced-motion: no-preference) {
    #hero { view-timeline: --hero-out block; }
    @keyframes lp-handoff { to { opacity: 0; transform: translateY(6rem); } }
    #hero .lp-field {
      animation: lp-handoff linear both;
      animation-timeline: --hero-out;
      animation-range: exit 0% exit 70%;
    }
    @keyframes lp-settle { from { opacity: 0; transform: translateY(1.5rem); } to { opacity: 1; transform: none; } }
    .lp-principles li, .lp-cat h3 {
      animation: lp-settle linear both;
      animation-timeline: view();
      animation-range: entry 0% entry 60%;
    }
  }
}
```

- [ ] **Step 4: Run the unit tests to verify they pass**

Run: `cd website && yarn test`
Expected: PASS (including brand-guard: only `opacity` and `translateY` are used).

- [ ] **Step 5: Verify the production CSS keeps the new features**

Run: `cd website && yarn check:landing && grep -l "animation-timeline" build/assets/css/*.css`
Expected: dist tests PASS and the grep prints at least one CSS file. If it prints nothing, the CSS minifier dropped the rules: stop and report (the fix is a build-config change, which needs the user's go-ahead).

Then confirm snap survived minification: `grep -o "scroll-snap-type:[^;}]*" build/assets/css/*.css | sort -u`
Expected: lines for `y proximity` and `none`.

- [ ] **Step 6: Commit**

```bash
git add website/src/components/landing/landing.css website/test/unit/landing-scroll.test.mjs
git commit -m "feat(website): hand the Hero squares off to Principles and settle content in on scroll"
```

---

### Task 4: Document and verify in a browser

**Files:**
- Modify: `website/README.md`

- [ ] **Step 1: Document the behaviour**

In `website/README.md`, insert this section directly before `## Onboard your agent button`:

```markdown
## Scroll behaviour

The landing page snaps softly (`scroll-snap-type: y proximity`, scoped by `html:has(.lp-page)` so docs pages are
unaffected): Hero and Principles fill one screen under the navbar, and all four sections snap at their top edge. Deep
Dives and Catalog scroll freely. The Hero squares drift away as Principles rises in, and Principles rows and Catalog
headings fade up once; both use CSS scroll-driven animation inside `@supports (animation-timeline: view())`, so other
browsers see a static page. `prefers-reduced-motion: reduce` turns snap and motion off. A fixed rail of four squares on the
right (`src/components/landing/SectionRail.js`, wider than 62.5rem only) links to the sections; the section list lives in
`src/lib/rail.mjs`. The rules live in `src/components/landing/landing.css` and are pinned by
`test/unit/landing-scroll.test.mjs`.
```

- [ ] **Step 2: Run the full website checks**

Run: `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing`
Expected: all PASS.

- [ ] **Step 3: Manual browser pass**

Run: `cd website && yarn start` and open the printed URL. Check each item; fix and re-run Step 2 if any fails:

- [ ] Desktop (about 1440x900), light mode: stop scrolling just short of Principles, Deep Dives and Catalog; the page pulls to each top edge under the navbar. A fast flick through the page is not fought.
- [ ] Principles fills one screen at 1080 and 768 pixel heights; shrink the window to about 500 pixels tall: it grows and nothing is clipped.
- [ ] Scrolling out of Hero: the squares drift down and fade while the Principles rows fade up; no flash of invisible content when you reload mid-page.
- [ ] Rail: four squares on the right; the highlight follows the section in the middle of the screen; clicking each jumps there, not hidden under the navbar; Tab reaches each square with a visible focus ring and its label appears. Hidden when the window is narrower than 1000 pixels.
- [ ] Dark mode: rail squares and tooltips are visible on both the ink Hero and the ink page background.
- [ ] Phone width (about 390 pixels): no rail, no horizontal scroll, sections not clipped.
- [ ] Reduced motion (devtools: Rendering, Emulate prefers-reduced-motion): no snap and no motion.
- [ ] Open `/overview` (docs): no snap, no rail.
- [ ] Firefox (if no `animation-timeline` support): the page is static and fully readable.

- [ ] **Step 4: Commit**

```bash
git add website/README.md
git commit -m "docs(website): describe the landing page scroll behaviour"
```

- [ ] **Step 5: Hand back**

Do not push. Report results to the user, including any manual-check item that failed or could not be run.

---

## Self-Review

**Spec coverage:** Soft snap, scoping and `scroll-padding-top` (Task 2). Hero/Principles `min-height` and centring (Task 2). Snap points on Deep Dives and Catalog (Task 2, `#deep-dives, #catalog` in the align rule). Squares hand-off (Task 3). Principles and Catalog heading settle-in, none on Deep Dives (Task 3). Rail with real links, `aria-current`, focus ring (global `:focus-visible` plus the label on `:focus-visible`), `IntersectionObserver`, hidden under about 1000 px, mounted after `</main>` (Task 1). Reduced motion turns off snap (Task 2) and motion (Task 3, plus the existing global rule in `custom.css`). Files table, README, tests, manual checks (Tasks 1 to 4). Spec item "`ModuleField.js` only if needed": not needed, noted above.

**Placeholders:** none; every code step has its code.

**Type and name consistency:** `RAIL` entries `{id, label}`, `lp-rail`, `lp-rail-link`, `lp-screen`, `--hero-out`, `lp-handoff`, `lp-settle` and `block()` are used consistently across tasks and tests.

**Review Focus:** items 1 to 5 map to the Task 2 and Task 3 CSS tests and the Task 1 dist test.
