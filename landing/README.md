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
rewritten to zio.dev URLs and checked against `docs/` at build time. Platforms are `JVM`/`JS`; Scala versions are `2.13`/`3.x`. On the page, rows of one category that share an artifact
(for example Scope, Resource, Unscoped, ... in `zio-blocks-scope`) are collapsed into one tile, the first row, and that
category's note is dropped (`scripts/lib/collapse.mjs`). A new value needs a one-line change in
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
