# ZIO Blocks website

One Docusaurus app serves the whole site: the landing page at `/` and the documentation under `/docs/`. The docs
content lives in `../docs/` (rendered through mdoc); the landing page content is generated from
`../docs/index.md`, and brand SVGs are copied from `../assets/logo/`.

## Commands

Run from `website/` (`yarn install` once).

| Command | What it does |
| --- | --- |
| `yarn start` | Run the generator (`scripts/build-catalog.mjs`), then start the dev server |
| `yarn build` | Run the generator, then build `build/` (needs the mdoc output of `sbt docs/mdoc`) |
| `yarn test` | Unit tests (parser, versions, brand and theme rules) |
| `yarn check:landing` | Build the landing page only, then assert the static output |
| `yarn check:links` | Check external links in `build/index.html` (needs network and a prior build) |

`LANDING_ONLY=1` builds the landing page without the docs plugin, so no sbt or mdoc output is needed
(`yarn build:landing`, which `yarn check:landing` uses). Broken docs links are warnings in that mode and errors in the
full build.

## The `docs/index.md` contract

The parser fails the build, naming the row, if the page changes shape. It relies on: the bold tagline line, the
`## What Is ZIO Blocks?` (its second paragraph becomes the hero `lead`), `## Core Principles`, `## Getting Started`, `## All Blocks` (one `###` per category, with
a five-column table `Block | Artifact | Platform | Scala | Description`), `## Schema`/`Scope`/`Async`/`SQL` (each
with `### The Problem`, `### The Solution`, `### Learn More`), `## Compatibility`, and `## Guides` sections.
Links in prose (descriptions, notes, deep-dive text) must be `https://` URLs or `./` docs paths; docs paths are
rewritten to site-relative `/docs/...` URLs and checked against `docs/` at build time. Platforms are `JVM`/`JS`; Scala versions are `2.13`/`3.x`. On the page there is one tile per artifact: rows that share an
artifact (Scope, Resource, Unscoped, ... and Wire all live in `zio-blocks-scope`) are collapsed into the first row, a
category that lost rows loses its note, and a category left empty is dropped (`scripts/lib/collapse.mjs`). A category can be shown under a different name
than its `###` heading through `CATEGORY_RENAMES` in `scripts/build-catalog.mjs` (currently `Streams` -> `Streaming` and `Configuration & Feature Flags` -> `Configuration`); a rename
for a heading that no longer exists fails the build. `PRINCIPLE_TEXT` shows a principle with different text than the docs (currently `Cross-Platform`, tightened); a name
that no longer exists fails the build (`scripts/lib/override.mjs`). `CATEGORY_SPLITS` shows one docs category as two (currently `Meta Programming` -> `Meta Programming` plus
`Codecs`, the rows whose name ends in ` Codec`), moving the docs note with the moved rows
(`scripts/lib/split.mjs`). On wide screens the tile grid has three columns, and consecutive categories share a row while
their tiles together fit in them (today Resource Management + Dependency Injection, and Streaming + Telemetry); the
rule lives in `src/components/landing/Catalog.js`. A new value needs a one-line change in
`scripts/lib/parse-index.mjs`.

## CI and deployment

GitHub Actions (`.github/workflows/ci.yml`, generated from `project/CiWorkflow.scala`) builds the site: it runs
`sbt docs/mdoc` and then `yarn build`. Pull requests upload the build as an artifact and get a Netlify deploy preview
showing both the landing page and the docs. A push to `main` and a release deploy `website/build` to the production
Netlify site. The `landing` job runs `yarn test`, `yarn check:landing`, `yarn check:links` and Lighthouse
(`@lhci/cli`, pinned to an exact version, config in `lighthouserc.json`). A 404 on a `https://zio.dev/zio-blocks/` URL
is only a warning in `check:links`, since a page may not be published until the next release.

Required configuration (a maintainer sets these once):

| Name | Kind | Purpose |
| --- | --- | --- |
| `NETLIFY_AUTH_TOKEN` | secret | Netlify API token, shared by previews and production deploys |
| `NETLIFY_PRODUCTION_SITE_ID` | secret | The production Netlify site |
| `SITE_URL` | repository variable | The canonical site URL used by the build |

The install line shows the latest release version, read from the GitHub API at build time (`GITHUB_TOKEN` avoids the
unauthenticated rate limit) and falling back to the pinned `FALLBACK_VERSION` in `scripts/lib/version.mjs`.
