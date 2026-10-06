# ZIO Blocks landing page

Standalone Astro site deployed on Netlify. Copy, code, the block catalog, guides, and compatibility list are
generated from `../docs/index.md`; brand SVGs are copied from `../assets/logo/`. Production docs live on
zio.dev; this site only links to them.

## Commands

| Command | What it does |
| --- | --- |
| `npm run dev` | Regenerate `src/data/site.json`, then start the dev server |
| `npm run build` | Regenerate data, then build `dist/` |
| `npm test` | Unit tests (parser, versions, brand rules) |
| `npm run check` | Build, then assert the static output |
| `npm run check:links` | Check external links in `dist/index.html` (needs network) |

## The `docs/index.md` contract

The parser fails the build, naming the row, if the page changes shape. It relies on: the bold tagline line, the
`## What Is ZIO Blocks?` (its second paragraph becomes the hero `lead`), `## Core Principles`, `## Getting Started`, `## All Blocks` (one `###` per category, with
a five-column table `Block | Artifact | Platform | Scala | Description`), `## Schema`/`Scope`/`Async`/`SQL` (each
with `### The Problem`, `### The Solution`, `### Learn More`), `## Compatibility`, and `## Guides` sections.
Links in prose (descriptions, notes, deep-dive text) must be `https://` URLs or `./` docs paths; docs paths are
rewritten to zio.dev URLs and checked against `docs/` at build time. Platforms are `JVM`/`JS`; Scala versions are `2.13`/`3.x`. On the page there is one tile per artifact: rows that share an
artifact (Scope, Resource, Unscoped, ... and Wire all live in `zio-blocks-scope`) are collapsed into the first row, a
category that lost rows loses its note, and a category left empty is dropped (`scripts/lib/collapse.mjs`). A category can be shown under a different name
than its `###` heading through `CATEGORY_RENAMES` in `scripts/build-catalog.mjs` (currently `Streams` -> `Streaming` and `Configuration & Feature Flags` -> `Configuration`); a rename
for a heading that no longer exists fails the build. `CATEGORY_SPLITS` shows one docs category as two (currently `Meta Programming` -> `Meta Programming` plus
`Codecs`, the rows whose name ends in ` Codec`), moving the docs note with the moved rows
(`scripts/lib/split.mjs`). On wide screens the tile grid has three columns, and consecutive categories share a row while
their tiles together fit in them (today Resource Management + Dependency Injection, and Streaming + Telemetry); the
rule lives in `src/components/Catalog.astro`. A new value needs a one-line change in
`scripts/lib/parse-index.mjs`.

## Netlify setup (once, by a maintainer)

Create a site from this repository with base directory `landing`. `netlify.toml` supplies the build command,
publish directory, Node version, and cache headers. The latest release version is read from the GitHub API at
build time and falls back to the pinned `FALLBACK_VERSION` in `scripts/lib/version.mjs`.

## Releases and the install line

The version in the install line is read from the GitHub API at build time and falls back to the pinned
`FALLBACK_VERSION` when the API is rate-limited. Set a `GITHUB_TOKEN` environment variable in the Netlify site
settings to avoid the unauthenticated rate limit. The Netlify ignore rule does not rebuild on releases, so the
release process must trigger a Netlify build hook (a maintainer action outside this repository).

## CI

The `landing` job in `.github/workflows/ci.yml` (generated from `project/CiWorkflow.scala`) runs `npm test`,
`npm run check`, `npm run check:links`, and Lighthouse (`@lhci/cli`, pinned to an exact version). A 404 on a
`https://zio.dev/zio-blocks/` URL is only a warning, since a new docs page may not be published until the next
release.
