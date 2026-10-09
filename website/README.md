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

## Onboard your agent button

The hero and the navbar (an icon next to the colour-mode toggle) have an "Onboard your agent to ZIO Blocks" button
that copies a prompt (`Fetch <SITE_URL>/start.md and follow the instructions ...`, built by `promptFor` in
`src/lib/prompt.mjs` from the configured `SITE_URL`) for the user's coding agent. The components in
`src/components/OnboardAgentButton/` and the navbar swizzle in `src/theme/Navbar/ColorModeToggle/` are ported from
zio/zio-http (originally zio/zio). The one deliberate difference is a square `rounded-none` button instead of a pill.

- Tailwind is used only by this component: `tailwind.config.js` has `important: true` utilities, and
  `src/css/custom.css` imports the theme and utilities layers but not preflight, so Infima is untouched.
- `docusaurus-plugin-llms` generates `llms.txt`, `llms-full.txt` and per-page markdown from `docs/` in the full build
  (it is not registered for `LANDING_ONLY`).
- `static/start.md` is what the prompt points the agent at. Update it when the skills change (today ZIO Blocks has no
  skill or MCP server of its own, only `zio-knowledge`).

## CI and deployment

GitHub Actions (`.github/workflows/ci.yml`, generated from `project/CiWorkflow.scala`) builds the site: it runs
`sbt docs/mdoc` and then `yarn build`. Pull requests upload the build as an artifact and get a Netlify deploy preview
showing both the landing page and the docs. A push to `main` and a release deploy `website/build` to the production
Netlify site. The `landing` job runs `yarn test`, `yarn check:landing`, `yarn check:links` and Lighthouse
(`@lhci/cli`, pinned to an exact version, config in `lighthouserc.json`). A 404 on a `https://zio.dev/zio-blocks/` URL
is only a warning in `check:links`, since a page may not be published until the next release.

The external link check only warns in CI (it is outage-prone and outside PR control). Unit tests, the static-output
tests and Lighthouse (95+) fail the `landing` job. Because `deploy-preview.yml` runs only when the whole CI run
succeeds, a failing `landing` job also suppresses the Netlify preview.

Required configuration (a maintainer sets these once):

| Name | Kind | Purpose |
| --- | --- | --- |
| `NETLIFY_AUTH_TOKEN` | secret | Netlify API token, shared by previews and production deploys |
| `NETLIFY_PRODUCTION_SITE_ID` | secret | The production Netlify site |
| `SITE_URL` | repository variable (optional) | Overrides the canonical site URL used by the build; defaults to `https://zioblocks.com` |

The install line shows the latest release version, read from the GitHub API at build time (`GITHUB_TOKEN` avoids the
unauthenticated rate limit) and falling back to the pinned `FALLBACK_VERSION` in `scripts/lib/version.mjs`.

### Custom domain: zioblocks.com on Netlify

This mirrors ziohttp.com (zio/zio-http), whose DNS stays at GoDaddy and points at Netlify: an apex `A` record to
`75.2.60.5`, `www` as a `CNAME` to `zio-http.netlify.app`, `www` redirecting to the apex, and a `_redirects` rule sending
the `*.netlify.app` host to the custom domain so there is one canonical origin. The differences: zio-http lets Netlify
build the site from Git (`netlify.toml` and a release webhook), but this site needs `sbt docs/mdoc`, which Netlify
cannot run, so GitHub Actions builds it and uploads `website/build` to a Netlify site that has no Git integration.

The build's canonical origin defaults to `https://zioblocks.com` (`url` in `docusaurus.config.js`; `SITE_URL`
overrides it), so canonical links, the sitemap, `robots.txt`, `llms.txt` and the onboard-agent prompt all use it.
`static/_redirects` assumes the Netlify site is named `zio-blocks` (`zio-blocks.netlify.app`); change both lines if the
name differs.

One-time setup, in this order:

1. Netlify: **Add new project > Deploy manually**, name the site `zio-blocks`, and copy its Project ID
   (**Project configuration > General**) into the repository secret `NETLIFY_PRODUCTION_SITE_ID`;
   `NETLIFY_AUTH_TOKEN` is already shared with the preview workflow.
2. Netlify: **Domain management > Add a domain** `zioblocks.com`, and make it the primary domain (Netlify then redirects
   `www.zioblocks.com` to it).
3. GoDaddy: **DNS > Manage records**. Delete the parked default `A` record (and any `www` forwarding), then add:

   | Type | Name | Value |
   | --- | --- | --- |
   | `A` | `@` | `75.2.60.5` |
   | `CNAME` | `www` | `zio-blocks.netlify.app` |

   GoDaddy has no ALIAS/ANAME record, so the apex uses Netlify's fallback `A` record. If Netlify's **Pending DNS
   verification** dialog shows different values, they win over this table.
4. Netlify: after DNS propagates (up to a day), **Domain management > HTTPS**: verify DNS, provision the certificate and
   enable Force HTTPS.
5. Push to `main` (or publish a release): the `Deploy to Netlify (production)` step publishes `website/build`.
   Do this after step 3: the `_redirects` rule sends `zio-blocks.netlify.app` to `zioblocks.com`, which must resolve first.
6. Optional: set the GitHub repository's website field to `https://zioblocks.com`, as zio/zio-http does.

Alternative: point the domain's GoDaddy nameservers at Netlify DNS (Netlify shows them in Domain management) and let
Netlify manage every record and renew certificates itself.
