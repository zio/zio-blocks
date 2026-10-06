# ZIO Blocks Homepage — Design

Date: 2026-10-06
Status: Draft, awaiting review

## Goal

A modern, standalone landing page for ZIO Blocks, aimed at **evaluators**: Scala developers deciding whether to adopt the project over alternatives (zio-schema, Circe, Cats Effect-based stacks). Within a few seconds a visitor should see that ZIO Blocks needs no effect system, has no lock-in, covers their use case, and can be tried with one line.

Success criteria:

- The first screen shows the value claim, an install line, and a real one-line result.
- Every block in the catalog is findable by category, platform, and Scala version, with its artifact name one click from the clipboard.
- All claims and code come from `docs/index.md`. Nothing is invented.
- Lighthouse 95+ (performance, accessibility, best practices, SEO), usable without JavaScript.

## Context and constraints

- **Hosting.** `docs/` is published to npm as `@zio.dev/zio-blocks` and served by zio.dev under `/zio-blocks`. `website/` in this repo is a local Docusaurus stub used only for the CI build check and PR previews. A page added there would not reach production. The homepage is therefore a **separate site** in `landing/`, deployed on **Netlify**. The repo already uses Netlify for docs PR previews (`.github/workflows/deploy-preview.yml`).
- **Untouched.** `docs/` content, `website/`, README generation (`docs/generateReadme`), and the zio.dev docs. `docs/index.md` stays the single source of truth and the README source (PR #1717).
- **Brand** (`assets/logo/`, `assets/logo/README.md`, rendered `zio-blocks-brand-sheet.svg`):
  - The mark is a Z cut from five equal square blocks. Every shape is square and flat: no gradient, shadow, stroke, rotation, or corner radius. No single block is recoloured.
  - Palette: Ultramarine `#2D3F8F`, Ink `#141A2E`, Lifted blue `#4B5FC4`, Paper ink `#F3F5FC`, white, hairline `#E4E7F0`, muted `#7C859E`.
  - Typeface: Manrope (ExtraBold, Regular), SIL OFL.
  - The wordmark is "ZIO" with the badge standing in for the Z. Never set "ZIO Blocks" in text next to the logo.
  - The badge holds down to 24 px. Below that, use the favicon cut.
  - Editorial vocabulary from the sheet: tiny tracked uppercase numbered labels, hairline rules, muted captions.
- **Repo policy** (`AGENTS.md`). New directory and new dependencies were approved: `landing/` and Astro.

## Decisions made

| Decision | Choice |
| --- | --- |
| Primary audience | Evaluators |
| Hero | Ink ground with the module field bleeding off the right edge (as in the social card) |
| Code in hero | Install line plus a one-line JSON result; full code in the next sections |
| Compatibility | Folded into the hero stack strip (no separate section) |
| Hosting | Standalone site in `landing/`, Netlify |
| Framework | Astro (static output, islands only where needed) |
| Catalog data | Generated from `docs/index.md` at build time |

## Page structure

Ink hero, light editorial body with hairlines and numbered tracked labels, ink footer. Dark mode swaps the tokens.

| # | Section | Question it answers | Content |
| --- | --- | --- | --- |
| 00 | Hero | Is this for me? Can I try it now? | Tagline, module field, install line, one-line JSON result, Get started / GitHub, stack strip (ZIO · Cats Effect · Kyo · Ox · Akka · plain Scala) |
| 01 | Principles | What's the catch? | Zero lock-in, Modular, Cross-platform, High performance, Type safety, as a numbered row from the `docs/index.md` principles |
| 02 | Deep dives | Show me real code | Tabs: Schema, Scope, Async, SQL. Each shows the problem (old way) then the solution, plus a link to the reference. Schema tab includes the format chips. Scope tab shows the compile-time escape error |
| 03 | Block catalog | Does it cover my use case? | Tiles grouped under the 11 category headings (one tile per artifact across the page: rows that share an artifact are collapsed into the first, so Resource Management shows a single Scope tile and Wire, which also lives in `zio-blocks-scope`, has no tile), each with the artifact name, platform and Scala badges, and a Learn More button linking to its reference page. (The category / JVM-JS / Scala-version filter controls and the per-tile copy buttons in the original design were removed on request, as was the hero install-line copy button.) |
| 04 | Close and footer | What do I do now? | Install line repeated, then Docs, Reference, GitHub, and the stacked logo |

Deliberately absent: testimonials, star counts, logo walls, benchmark numbers (until a real JMH result is cited), hero illustration, and (removed on request) a switching-cost section for the migration guide and other guides; the docs parser still reads `## Guides`.

## Architecture

```
landing/
  astro.config.mjs, package.json, netlify.toml
  scripts/build-catalog.mjs         parse docs/index.md -> src/data/blocks.json
  scripts/build-catalog.test.mjs    parser tests (node:test)
  src/styles/tokens.css             palette, type scale, 100-unit module grid
  src/pages/index.astro             six sections in order, no logic
  src/components/
    Hero.astro, ModuleField.astro
    Principles.astro
    DeepDives.astro
    Catalog.astro
    Footer.astro
  src/scripts/                      tabs
  public/fonts/                     Manrope, self-hosted
```

Each component has one job and receives data as props. Brand SVGs are copied from `assets/logo/` at build time rather than duplicated in git.

## Data flow

1. `build-catalog.mjs` reads `docs/index.md` and parses the category tables into `blocks.json`: name, artifact, platform, Scala versions, description, docs URL. It also extracts the code samples for the four deep dives.
2. The latest release version comes from the GitHub releases API, with a pinned fallback when the request fails, and replaces `@VERSION@` in the install lines.
3. Astro renders static HTML. Shiki highlights Scala at build time; there is no runtime highlighter.
4. Client scripts only attach behavior (tabs, the hero animation) to HTML that is already present.

Docs links map from repo-relative paths (`./reference/schema/index.md`) to zio.dev URLs (`https://zio.dev/zio-blocks/reference/schema`). The exact mapping rules, including `.md`/`.mdx` and `index` handling, must be verified against zio.dev's live URLs before implementation.

## Error handling

The build fails, with a message naming the row, on: a missing or malformed table row, a duplicate artifact, an unmapped docs link, or a missing code sample section. The release-version lookup never fails the build; it falls back to the pinned version and logs a warning.

## Quality gates

- Unit tests for the parser (valid rows, malformed rows, duplicates, link mapping, platform/Scala parsing including `JVM · JS` and `3.x`-only rows).
- Link check on all external docs URLs in CI.
- Responsive from 360 px, keyboard navigable with visible focus, WCAG AA contrast in both themes, fully readable with JavaScript disabled, all animation disabled under `prefers-reduced-motion`.
- Lighthouse 95+ as a CI check.
- A path-filtered CI job builds `landing/` on pull requests. Netlify builds deploy previews.
- Render assertions follow `AGENTS.md`: assert full rendered output, not `.contains()`.

## Out of scope

Changes to `docs/` content, zio.dev, `website/`, or README generation. A combined docs-plus-homepage preview URL. Search. Localization. A blog.

## Open items

1. **Brand sheet inconsistency.** `assets/logo/README.md` says the Z is 4 wide by 5 tall, seam 6, tile 724x724. The rendered brand sheet says a 6x6 module field, Z in the inner 4x4, seam 4. The SVG files themselves are the source of truth; confirm which document is stale.
2. **Missing asset.** The README lists `zio-blocks-logo-bare.svg` (for wide headers and footers) but it is not in `assets/logo/`. The standard lockup and the mono files cover the navbar and footer meanwhile.
3. **Netlify site.** Creating and connecting the Netlify site, and choosing the production domain, are manual steps for the maintainer.
4. **Docs URL mapping.** To be verified as described under Data flow.
5. **Version source.** Confirm the GitHub releases API is acceptable at build time, or choose another source such as Maven Central metadata.
