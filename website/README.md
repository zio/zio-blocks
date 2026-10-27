# ZIO Blocks website

One Docusaurus app serves the whole site: the landing page at `/` and the documentation at the site root (`/reference/...`, `/guides/...`; its index page is `/overview`). The docs
content lives in `../docs/` (rendered through mdoc); the landing page content is generated from
`../docs/index.md`, and brand SVGs are copied from `../assets/logo/`. The API reference (Scaladoc) is served at `/api`.

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
`## What Is ZIO Blocks?` (its second paragraph becomes the hero `lead`), `## Core Principles`, `## All Blocks` (one `###` per category, with
a five-column table `Block | Artifact | Platform | Scala | Description`), `## Schema`/`Scope`/`Async`/`SQL` (each
with `### The Problem`, `### The Solution`, `### Installation` (its `libraryDependencies` block is shown in the deep-dive tab),
`### Learn More`), `## Compatibility`, and `## Guides` sections.
Links in prose (descriptions, notes, deep-dive text) must be `https://` URLs or `./` docs paths; docs paths are
rewritten to site-relative URLs (`/reference/...`) and checked against `docs/` at build time. Platforms are `JVM`/`JS`; Scala versions are `2.13`/`3.x`. On the page there is one tile per artifact: rows that share an
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

## Scroll behaviour

On the landing page one wheel gesture, or one PageUp/PageDown/arrow/Space key press, moves to the adjacent section and
lands it under the navbar (`src/components/landing/useSectionScroll.js`; the decision rules are the pure, unit-tested
`src/lib/sectionStep.mjs`). A section taller than the screen (Catalog, or any section on a short window) scrolls natively
until its edge, then the next gesture advances; nested scrollers such as code blocks keep their own wheel. An 800 ms lock
absorbs trackpad inertia. Touch scrolling is left to the browser, docs pages are unaffected, and with
`prefers-reduced-motion: reduce` (or no JavaScript) nothing is intercepted. Hero, Principles and Deep Dives fill one screen
(`min-height`, so they grow rather than clip). The Hero squares drift away as Principles rises in, and Principles rows and Catalog
headings fade up once; both use CSS scroll-driven animation inside `@supports (animation-timeline: view())`, so other
browsers see a static page (the guard is asserted against the built stylesheet in `test/dist.test.mjs`). A fixed rail of five squares on the
right (`src/components/landing/SectionRail.js`, wider than 62.5rem only) links to the sections; the section list lives in
`src/lib/rail.mjs`. The rules live in `src/components/landing/landing.css` and are pinned by
`test/unit/landing-scroll.test.mjs`.

## Who is using ZIO Blocks

The last landing section lists organizations that use ZIO Blocks, one logo tile each. The list lives in
`src/lib/users.mjs` (name, link, logo path and size, one-line description, and an optional dark-mode logo for a logo drawn in one dark colour, and an optional `logoScale` to enlarge a logo whose wordmark fills only part of its height) and the logos in `static/img/users/`, one SVG per
organization; `test/dist.test.mjs` checks every entry renders with its link and logo. The section's "join the list" link
opens GitHub's editor on `src/lib/users.mjs`, so adding an organization is a one-entry pull request plus its logo.

## Footer

Every page ends with the same footer: the logo (linking home) and the site tagline on the left, the link columns from
`themeConfig.footer.links` in `docusaurus.config.js` on the right, and the credit line under a rule. The layout is
`src/theme/Footer/Layout` (Docusaurus' own layout puts the logo under the links) and the styles are `.zb-footer` in
`src/css/custom.css`: ink in light mode, the deeper code-card tone in dark mode, on the landing page's measure.

## Index pages for `/guides/` and `/reference/`

`docs/guides/index.md` and `docs/reference/index.md` give the two folders an index page each. They are not entries in
`docs/sidebars.js`: `displayed_sidebar` shows the guides or the main sidebar beside them, and `docs/SidebarCards.jsx`
draws one card per entry of that sidebar (only the categories on `/reference/`) with Docusaurus' `DocCardList`, so a
new guide or block group appears there without editing the page. `src/clientModules/folderIndexes.js` gives `/guides`
and `/reference` their trailing slash in place (hosts serve the same page for both forms); a unit test checks each
folder it lists has an index page. The cards are flattened to the brand (no shadow, square, no link underline) in
`src/css/custom.css`.

## API reference at `/api`

`sbt docs/generateApiDocs` builds one unified Scaladoc (sbt-unidoc, Scala 3) for every published module and copies it
into `static/api` (gitignored), so `yarn build` publishes it at `/api/` next to the docs, the way
[fs2-kafka](https://typelevel.org/fs2-kafka/api/) serves its API. Cross-built modules contribute their JVM side, since
JVM and JS share the public API. The module list is `ScalaUnidoc / unidoc / unidocProjectFilter` on the `docs` project
in `../build.sbt`; a new published module must be added there. Source links point at the commit the docs were built
from.

The task also fixes the generated output in three ways (all in `generateApiDocs`):

- Links to other zio-blocks types stay on `/api`. sbt-api-mappings, which comes with zio-sbt-website, would otherwise
  send them to javadoc.io, which has no page for a snapshot.
- Scaladoc's `-dynamic-side-menu` (without it, every page repeats the whole package tree and the output grows from
  about 100 MB to 1.5 GB) comes with bugs in `scripts/ux.js` that break navigation, patched in place: menu links joined
  with commas under a sub-path (`,api/zio.html`), link clicks calling jQuery's `$.get` though Scaladoc no longer ships
  jQuery (no link navigated), and Back/Forward leaving the menu highlight on the page you left. Each patch fails the task
  when its target text disappears, so a Scaladoc upgrade gets them reviewed. A fourth keeps a URL that names a section
  rather than a member (`#attributes`) from aborting the page setup, which skipped code highlighting.

Scaladoc is a separate generator with its own HTML, stylesheets and scripts, so after `docusaurus build`
`scripts/theme-api.mjs` gives every page in `build/api` the site's look (`scripts/lib/api-theme.mjs`, assets in
`src/api-theme/`). It reads each page from `static/api` and writes it over its copy in `build/api`, so it can be rerun:

- Scaladoc's header is replaced by a copy of the site navbar (same logo, links, onboard-agent button and colour-mode
  toggle, 60px ink bar, collapsing into the mobile menu at 996px), keeping the ids Scaladoc's scripts look up
  (`#search-toggle`, `#mobile-menu-toggle`, a hidden version dropdown). Scaladoc's search button stays in the bar. A unit
  test keeps its links in step with `navbar.items` in `docusaurus.config.js`.
- URLs have no `.html`, like the docs pages: `/api/zio/blocks/chunk/Chunk`, not `Chunk.html` (an index page is its
  folder). The files keep their names, since Netlify and `docusaurus serve` serve `Chunk.html` for `/…/Chunk`; the
  relative links in every page, the search index (`scripts/searchData.js`) and the side menu's link builder in
  `scripts/ux.js` drop the extension. Old `.html` links still work.
- Nested types get folders: Scaladoc files `ChunkBuilder.Int` as `ChunkBuilder$$Int.html`, the site serves it at
  `/api/zio/blocks/chunk/ChunkBuilder/Int` (`nestedTypePaths` reads each nested type's parent off the side menu;
  `relocatePage` rewrites a moved page's links, root path and location for its new folder, and every link to it).
  Objects keep Scaladoc's `$` (`Schema$` is the companion of `Schema`, `MediaTypes/application$` an object).
- The site favicon (`favicon` in `docusaurus.config.js`) replaces Scaladoc's `favicon.ico`.
- The window scrolls, as on a docs page, so its scrollbar runs the whole height beside a fixed navbar. Scaladoc locks
  the body and scrolls `#main` as a pane under its header; `api.css` puts `#main` back in the page flow, fixes the
  navbar, sidebar and overlays (search, mobile menu, sidebar button), and `theme.js` sends Scaladoc's `#main.scrollTo`
  calls (to a `#section` on load, to the top after an in-place page load) to the window.
- The index page sends `/api` to `/api/` first thing in `<head>` (with `<base href="/api/">` for the requests the
  browser starts before that runs): without the slash, Scaladoc's relative paths resolve against the site root and the
  page comes up unstyled. A Netlify redirect rule cannot do it, since a rule is skipped when a file answers the path.
- Scaladoc's "Generated with Scaladoc" footers (one for wide screens, one at the end of the text) are removed, and the
  sidebar runs to the bottom of the window.
- The breadcrumbs become the docs breadcrumbs (home icon, API, the packages as the side menu names them, the page) at
  the top of the text column.
- The side menu (`dynamicSideMenu.json`) starts at `zio.blocks` and `zio.http` rather than the single `zio` package
  (`hoistSideMenu`), and `theme.js` keeps that top level unfolded across page loads unless the reader folds it. It is
  drawn as the docs sidebar: Infima's rows, colours, chevron caret on the right, 12px per level, Scaladoc's kind icons, and the
  browser's own scrollbars (Scaladoc hides them; setting `scrollbar-color` overrides its `::-webkit-scrollbar` rules).
  Its Docs/API switcher is hidden, since `/api` has no Scaladoc docs section.
- The columns follow the docs page grid: a 300px sidebar shown down to 997px, the container's width steps (40, 48, 64,
  80 and 96rem), a 75%/25% text/contents split with 1rem gutters, and the contents hidden below 997px, so text starts at
  the same place as on a docs page. In the text column, the title comes before its class/object badge and attribute
  labels are flush left.
- `src/api-theme/api.css` sets Manrope and the brand colours, moves Scaladoc's layout under the 60px bar, and gives each
  section the size of its docs counterpart: sidebar as `.menu__link` (16px, weight 500), headings at Infima's 3/2/1.5/1rem
  in weight 800, text and tables 16px at line height 1.65, code at 90% in the site monospace, breadcrumbs and the table of
  contents at 0.8rem. Scaladoc sizes text with utility classes (`body-small` is 13px in the sidebar and in sentences
  alike), so the rules target sections rather than those classes.
- `src/api-theme/theme.js` replaces Scaladoc's `scripts/theme.js`: the colour mode is the one Docusaurus keeps in
  `localStorage.theme`, so switching it on either side carries over, and each click flips what the page shows, as the
  site toggle does (`src/theme/ColorModeToggle`, which replaces Docusaurus' system -> light -> dark cycle, where one
  click in three changed nothing on screen).
  It also keeps the navbar's site links out of Scaladoc's in-place page loader.

`yarn start` serves `static/api` as generated, without this skin.

The navbar and footer link to it with `pathname:///api/`: Scaladoc is plain HTML outside the Docusaurus router, and
the `pathname://` protocol makes the link a full page load and keeps it out of the broken-link checker. Without a prior
`generateApiDocs` the build still succeeds, but `/api/` is a 404.

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
- `static/start.md` is what the prompt points the agent at. Update it when the skills change. It mirrors
  ziohttp.com/start.md: the `zio-blocks-knowledge` and `zio-knowledge` skills (from zio/zio-skills) and the `zio-blocks`
  MCP server at `https://mcp.zioblocks.com/mcp`.

## CI and deployment

This follows zio/zio-http: the production site is a Netlify site connected to this Git repository. Netlify builds it
itself with `netlify.toml` and `build.sh` (which installs Java and sbt through SDKMAN, runs `sbt docs/mdoc docs/generateApiDocs`, then
`yarn build`) and publishes `website/build`. The site is rebuilt only on a library release or when someone runs the
**Deploy Website** workflow by hand (Actions > Deploy Website > Run workflow); both call a Netlify build hook from
`.github/workflows/site.yml`, so the install line on the landing page shows the new version. A push to `main` does not
rebuild it: `netlify.toml` sets `ignore = "exit 0"`, which skips every Git-triggered build, and Netlify never cancels a
build-hook build because of that command. Previews come from the CI workflow.

GitHub Actions (`.github/workflows/ci.yml`, generated from `project/CiWorkflow.scala`) checks the site on every pull
request: it runs `sbt docs/mdoc docs/generateApiDocs` and `yarn build`, uploads the build as an artifact, and `deploy-preview.yml` publishes a
Netlify deploy preview showing both the landing page and the docs. The `landing` job runs `yarn test`,
`yarn check:landing`, `yarn check:links` and Lighthouse (`@lhci/cli`, pinned to an exact version, config in
`lighthouserc.json`). A 404 on a `https://zio.dev/zio-blocks/` URL is only a warning in `check:links`, since a page may
not be published until the next release.

The external link check only warns in CI (it is outage-prone and outside PR control). Unit tests, the static-output
tests and Lighthouse (95+) fail the `landing` job. Because `deploy-preview.yml` runs only when the whole CI run
succeeds, a failing `landing` job also suppresses the Netlify preview.

Required configuration (a maintainer sets these once):

| Name | Where | Purpose |
| --- | --- | --- |
| `NETLIFY_DEPLOY_WEBHOOK` | GitHub secret | The Netlify build hook URL that `site.yml` calls on a release (as in zio-http) |
| `NETLIFY_AUTH_TOKEN`, `NETLIFY_SITE_ID` | GitHub secrets | Used only by the existing PR preview workflow (`deploy-preview.yml`) |
| `GITHUB_TOKEN` | Netlify environment variable (optional) | Avoids the unauthenticated GitHub API rate limit when the build looks up the latest release |
| `SITE_URL` | environment variable (optional) | Overrides the canonical site URL; defaults to `https://zioblocks.com` |
| `ALGOLIA_APP_ID`, `ALGOLIA_SEARCH_API_KEY`, `ALGOLIA_INDEX_NAME` | GitHub variables and Netlify environment variables (optional) | Turn on the docs search box (see Search below). Set all three or none |

The install line shows the latest release version, read from the GitHub API at build time (`GITHUB_TOKEN` avoids the
unauthenticated rate limit) and falling back to the pinned `FALLBACK_VERSION` in `scripts/lib/version.mjs`.

### Custom domain: zioblocks.com on Netlify

This mirrors ziohttp.com (zio/zio-http): the domain's DNS stays at GoDaddy and points at Netlify (an apex `A` record to
`75.2.60.5`, `www` as a `CNAME` to the `*.netlify.app` host), `www` redirects to the apex, and `static/_redirects` sends
the `*.netlify.app` host to the custom domain so there is one canonical origin.

The build's canonical origin defaults to `https://zioblocks.com` (`url` in `docusaurus.config.js`), so canonical links,
the sitemap, `robots.txt`, `llms.txt` and the onboard-agent prompt all use it. `static/_redirects` assumes the Netlify
site is named `zio-blocks` (`zio-blocks.netlify.app`); change both lines if the name differs.

One-time setup, in this order:

1. Netlify: **Add new project > Import an existing project**, pick GitHub and `zio/zio-blocks`, production branch
   `main`. Name the site `zio-blocks`. The build settings come from `netlify.toml`, so leave the defaults.
2. Netlify: **Project configuration > Build & deploy > Build hooks > Add build hook** (name it `release`, branch `main`),
   and add its URL as the GitHub repository secret `NETLIFY_DEPLOY_WEBHOOK`.
3. Netlify: **Domain management > Add a domain** `zioblocks.com`, and make it the primary domain (Netlify then redirects
   `www.zioblocks.com` to it).
4. GoDaddy: **DNS > Manage records**. Delete the parked default `A` record (and any `www` forwarding), then add:

   | Type | Name | Value |
   | --- | --- | --- |
   | `A` | `@` | `75.2.60.5` |
   | `CNAME` | `www` | `zio-blocks.netlify.app` |

   GoDaddy has no ALIAS/ANAME record, so the apex uses Netlify's fallback `A` record. If Netlify's **Pending DNS
   verification** dialog shows different values, they win over this table.
5. Netlify: after DNS propagates (up to a day), **Domain management > HTTPS**: verify DNS, provision the certificate and
   enable Force HTTPS.
6. Netlify (optional): add the environment variable `GITHUB_TOKEN`.
7. Optional: set the GitHub repository's website field to `https://zioblocks.com`, as zio/zio-http does.

To see which commit is live, `curl https://zioblocks.com/version.txt`: it prints `commit: <sha>`, a `url:` link to that commit on GitHub (the sha is Netlify's `COMMIT_REF`,
`GITHUB_SHA` in Actions, or `git rev-parse HEAD` locally) and the build time. `yarn build` writes it after Docusaurus
(`scripts/write-version.mjs`); `static/_headers` keeps it from being cached.

The first build is started with Actions > Deploy Website > Run workflow (a push does not build). It runs the whole
`sbt docs/mdoc` (Java and sbt are installed by `build.sh`), so it takes a while; read its log if it fails. `.jvmopts` asks for a 12 GB heap, which may exceed what the Netlify build container has; if the
build is killed for memory, lower it for that build.

Alternative: point the domain's GoDaddy nameservers at Netlify DNS (Netlify shows them in Domain management) and let
Netlify manage every record and renew certificates itself.

### Search (Algolia)

The docs search box is Algolia DocSearch, as on zio.dev and ziohttp.com. `src/algolia.js` reads three values from the
environment and passes them to `themeConfig.algolia`:

- `ALGOLIA_APP_ID`: the Algolia application ID
- `ALGOLIA_SEARCH_API_KEY`: the search-only API key (never the admin key)
- `ALGOLIA_INDEX_NAME`: the index the crawler writes to

Set all three as GitHub Actions variables (`site.yml` passes them to the build) and as Netlify environment variables. If
none is set, the build has no search box. If only some are set, the build warns and also has none. Local check:
`ALGOLIA_APP_ID=... ALGOLIA_SEARCH_API_KEY=... ALGOLIA_INDEX_NAME=... yarn build:landing`, then look for `DocSearch-Button`
in `build/index.html`.

The index is filled by the DocSearch crawler, not by this repository. Apply for DocSearch (docsearch.algolia.com) or
ask Algolia for a crawler on `https://zioblocks.com/`, then check the crawl in the Algolia dashboard before relying on
the search box.

### Google Analytics and Search Console

`GA_MEASUREMENT_ID` at the top of `docusaurus.config.js` is the GA4 web stream for `https://zioblocks.com`. The `gtag`
tag is added only when Netlify sets `CONTEXT=production`, which it does for the production build; PR previews are built
in CI without it and send nothing. To check a build locally:
`CONTEXT=production yarn build:landing && grep gtag/js build/index.html`.

Search Console verifies the URL-prefix property `https://zioblocks.com/` through that tag (**Google Analytics** method,
which needs an admin of the GA4 property), so the site carries no separate verification token. Removing the `gtag` tag
loses the ownership. After the property is verified, submit `https://zioblocks.com/sitemap.xml` under **Sitemaps**
(`robots.txt` already points at it).
