# ZIO Blocks landing page

Standalone Astro site deployed on Netlify. Copy, code, the block catalog, guides, and compatibility list are
generated from `../docs/index.md`; brand SVGs are copied from `../assets/logo/`. Production docs live on
zio.dev; this site only links to them.

## Commands

| Command | What it does |
| --- | --- |
| `npm run dev` | Regenerate `src/data/site.json`, then start the dev server |
| `npm run build` | Regenerate data, then build `dist/` |
| `npm test` | Unit tests (parser, versions, filters, brand rules) |
| `npm run check` | Build, then assert the static output |
| `npm run check:links` | Check external links in `dist/index.html` (needs network) |

## The `docs/index.md` contract

The parser fails the build, naming the row, if the page changes shape. It relies on: the bold tagline line, the
`## What Is ZIO Blocks?`, `## Core Principles`, `## Getting Started`, `## All Blocks` (one `###` per category, with
a five-column table `Block | Artifact | Platform | Scala | Description`), `## Schema`/`Scope`/`Async`/`SQL` (each
with `### The Problem`, `### The Solution`, `### Learn More`), `## Compatibility`, and `## Guides` sections.
Platforms are `JVM`/`JS`; Scala versions are `2.13`/`3.x`. A new value needs a one-line change in
`scripts/lib/parse-index.mjs`.

## Netlify setup (once, by a maintainer)

Create a site from this repository with base directory `landing`. `netlify.toml` supplies the build command,
publish directory, Node version, and cache headers. The latest release version is read from the GitHub API at
build time and falls back to the pinned `FALLBACK_VERSION` in `scripts/lib/version.mjs`.
