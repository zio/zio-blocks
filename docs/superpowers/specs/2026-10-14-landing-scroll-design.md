# Landing Page Scroll Behaviour — Design

Date: 2026-10-14
Status: Draft, awaiting review

## Problem

The landing page (`website/src/pages/index.js`) is four stacked sections: Hero, Principles, Deep Dives, Catalog. They are
plain blocks separated by a `border-top` and padding. Nothing makes a section feel like it "arrives": after scrolling you
often see half of one section and half of the next. The only motion today is the Hero squares rising once on load.

## Goal

Hero and Principles each fill one screen and settle into place automatically. Deep Dives and Catalog are taller than a
screen and keep scrolling freely. Motion explains the move from one section to the next and never gets in the way.

Success criteria:

- Stopping a scroll near the top of Hero, Principles, Deep Dives or Catalog pulls the page to that edge; a fast or
  mid-section scroll is never fought.
- No section is ever cut off or trapped, on any viewport size or text zoom.
- One memorable motion (the module squares hand-off); everything else is quiet.
- With `prefers-reduced-motion`, or without JS, or in a browser lacking the CSS features, the page is readable and
  behaves like today.
- Docs pages are unaffected.
- Existing content contracts hold: section ids, classes and text asserted by `test/dist.test.mjs` are unchanged.

## Non-goals

- No new colours, fonts or content. The existing tokens in `website/src/css/custom.css` are reused (ink `#141a2e`,
  ultramarine `#2d3f8f`, lifted `#4b5fc4`, field `#1e2647`, Manrope, square corners).
- No scroll hijacking (no JS-driven wheel handling), no full-screen snap of Deep Dives or Catalog, no per-card or
  per-hover effects.
- No changes to `docs/index.md` or the generated `site.json`.

## Design

### 1. Soft snap (CSS only)

"Snap" means the browser nudges the page to a section edge when scrolling stops nearby.

- Scope: `html:has(.lp-page) { scroll-snap-type: y proximity; scroll-padding-top: var(--ifm-navbar-height); }`. The
  `:has()` scoping keeps docs pages unaffected. `proximity` (not `mandatory`) only pulls when the scroll stops close to
  an edge.
- `#hero` and `#principles`: `min-height: calc(100svh - var(--ifm-navbar-height))` and `scroll-snap-align: start`.
  `min-height` (not `height`) lets a section grow on short viewports or large text instead of clipping. Content is
  vertically centred in the remaining space.
- `#deep-dives` and `#catalog`: `scroll-snap-align: start` on the section only. No snap points inside them.
- Fallback: browsers without scroll-snap simply scroll normally.

### 2. Signature motion: the squares hand off

The only decorative motion on the page.

- While Hero scrolls out, the module squares (`.lp-field`, `.lp-sq`) drift down and fade, and the Principles content rises
  slightly and settles into place.
- Implemented with CSS scroll-driven animation (`animation-timeline: view()` / `scroll()`), wrapped in
  `@supports (animation-timeline: view())`. No JS, no scroll listeners.
- Fallback where unsupported: the current load-time `lp-rise` animation and a still page.

### 3. Content settles in once

- Principles rows and the Catalog category headings (`.lp-cat h3`) each get one short fade-and-lift (about 400 ms) as they
  enter the viewport, also via `animation-timeline: view()` inside the same `@supports` block.
- Deep Dives has no entrance effect (tab clicks already change its content). Tiles, buttons and chips have no entrance or
  hover motion beyond what exists today.

### 4. Section progress rail

- New `website/src/components/landing/SectionRail.js`: a fixed column on the right edge with four small squares, one per
  section (Hero, Principles, Deep Dives, Catalog), reusing the module-square motif.
- Each square is a real `<a href="#id">` with an accessible name; the active one has `aria-current="true"` and is filled
  with `--lifted`. Visible focus ring.
- Active section is tracked with `IntersectionObserver` in a `useEffect`. Without JS the links still work; only the
  highlight is absent.
- Hidden below about 1000 px viewport width, where it would crowd the content.
- Mounted in `src/pages/index.js` after `</main>` (still inside `<Layout>`), so `test/dist.test.mjs`'s `<main id="landing">`
  section parsing is unchanged. Section ids come from the existing `ANCHORS` list.
- Colours adapt to both colour modes via existing tokens.

### 5. Accessibility floor

- `@media (prefers-reduced-motion: reduce)`: scroll-snap, the hand-off, the settle-in and the load animation are all off.
- Keyboard: rail links are focusable in document order; native keyboard scrolling works with proximity snap.
- Contrast: rail squares use tokens already used for text/borders in each mode.
- Mobile: rail hidden; sections keep `min-height` so nothing clips on small screens.

## Files

| File | Change |
| --- | --- |
| `website/src/components/landing/landing.css` | Snap rules, `min-height` for Hero and Principles, hand-off and settle-in animations inside `@supports`, reduced-motion overrides, rail styles |
| `website/src/components/landing/SectionRail.js` | New: progress rail component |
| `website/src/pages/index.js` | Mount `<SectionRail />` after `</main>` |
| `website/src/components/landing/ModuleField.js` | Only if the squares need extra hooks for the hand-off |
| `website/test/dist.test.mjs` | Assert the rail markup (four links, hrefs, accessible names) and that landing sections are unchanged |
| `website/README.md` | One short paragraph on scroll behaviour and the reduced-motion/`@supports` fallbacks |

## Testing and verification

1. `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing` (per `AGENTS.md`).
2. Manual browser check with the dev server (`yarn start`), at desktop and about 390 px width, in light and dark mode:
   - snap pulls only near the four section tops; fast scrolling is not fought;
   - Principles fits one screen at 1080p and 768p and grows rather than clips on a short window;
   - hand-off animation tracks scroll; rail highlights the right section and links jump correctly;
   - with reduced motion enabled in the OS or devtools, there is no snap and no motion;
   - a docs page (for example `/overview`) shows no snap and no rail.
3. Browsers without `animation-timeline` (for example Firefox at the time of writing): page is static and fully usable.

## Risks

- **Snap feel varies by browser.** Mitigation: `proximity` plus top-edge-only snap points on tall sections; tune on real
  devices.
- **Sticky navbar overlap.** Mitigation: `scroll-padding-top` and `min-height` both derive from `--ifm-navbar-height`.
- **`:has()` support.** Supported in all current evergreen browsers; where absent, no snap applies (graceful).
- **Scroll-driven animation support is uneven.** Entirely progressive enhancement behind `@supports`.
