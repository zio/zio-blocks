# Guides Section Organization — Design

Date: 2026-10-28
Status: Draft, awaiting review
Branch: `organize-guides`

## Problem

The guides section has grown to 74 pages, and it shows them as one flat list:

- The `guides` sidebar in `docs/sidebars.js` is a single "Guides" category with 74 unlabeled entries. The order loosely follows topics, but nothing tells the reader where one topic ends and the next begins.
- The `/guides/` index page (`docs/guides/index.md`) renders `SidebarCards`, so it shows the same 74 entries as a wall of cards.
- The pages are of different kinds (tutorials, how-to guides, migration guides, deep dives), but the reader can't tell them apart without opening each one.
- A newcomer has no visible starting point. The backlog (`docs/dev/backlog.md` §3) defines a learning path, but the site does not show it.
- A third, hand-written list of guides lives in the `## Guides` section of `docs/index.md`. It feeds the Overview page (`/overview`) and the generated `README.md`. The landing page does not show it, but its build (`website/scripts/lib/parse-index.mjs`) parses it and fails if it is empty or malformed. It has drifted: it lists 67 of the 74 guides, in no particular order.

## Goal

- Group the guides first by kind, then (for how-to guides) by topic, so a reader at work can find the guide for a task and a newcomer can find where to start.
- Keep one source of truth for the guide list: `docs/sidebars.js`. Every place that lists all guides is generated from it.
- Change no existing URL. Guide files stay where they are.

## Decisions

| Question | Decision |
|---|---|
| Top-level grouping | By kind: Tutorials, How-to guides, Migration guides, Deep dives |
| How-to grouping | By topic, inside How-to guides |
| Move files into folders? | No. Only `docs/sidebars.js` changes, so URLs stay the same and no redirects are needed |
| Tutorials order | One flat list in learning-path order (backlog §3), then electives. No stage sub-groups |
| `/guides/` page | Keeps the sidebar. The body stays an intro plus `SidebarCards`, which now shows one card per section |
| Section and topic cards | Each opens a Docusaurus generated index page that lists its items as cards, with the sidebar |
| `docs/index.md` `## Guides` list | Replaced by about six "start here" picks plus a link to the full guides index |
| "How to Add ZIO Blocks to Your Build" | First in Tutorials (every tutorial links to it for the install step) |
| "How to Process Binary Data Without Boxing" | How-to › Performance |
| OpenAPI guides | Folded into How-to › Web & HTTP (no separate topic) |
| `sql-transactions`, `sql-checked-interpolation` | How-to › SQL & persistence |

## Design

### 1. The `guides` sidebar

`docs/sidebars.js` keeps the outer "Guides" category (its link is `guides/index`). That category gives every guide its breadcrumb trail, and `SidebarCards` relies on it: on the page that is the category's link, the category's items become the cards.

Inside it there are four section categories. Each section and each how-to topic gets a generated index page:

```js
{
  type: "category",
  label: "How-to guides",
  link: {
    type: "generated-index",
    slug: "/guides/how-to",
    description: "Recipes for one task each, grouped by topic.",
  },
  collapsed: false,
  items: [
    {
      type: "category",
      label: "SQL & persistence",
      link: { type: "generated-index", slug: "/guides/how-to/sql", description: "..." },
      collapsed: true,
      items: ["guides/connect-to-postgresql-with-a-connection-pool", /* ... */],
    },
    /* ... */
  ],
}
```

The four sections start expanded (`collapsed: false`). The how-to topics start collapsed (`collapsed: true`), so the sidebar stays short. Docusaurus expands the category of the page being read on its own.

Breadcrumbs become Home › Guides › How-to guides › SQL & persistence › page.

### 2. Generated index pages (12 new URLs)

| Category | Slug | Description |
|---|---|---|
| Tutorials | `/guides/tutorials` | Step-by-step lessons for newcomers, in the order to read them. Start with the first one. |
| How-to guides | `/guides/how-to` | Recipes for one task each, grouped by topic. |
| How-to › Schema | `/guides/how-to/schema` | Validate, compare, register and derive with `Schema`. |
| How-to › Formats & codecs | `/guides/how-to/formats` | Read and write JSON, YAML, CSV, Avro, BSON and TOON, and customize codecs. |
| How-to › SQL & persistence | `/guides/how-to/sql` | Connect, query, page, stream, migrate and test database code with the `sql` module. |
| How-to › Streams & async | `/guides/how-to/streams` | Run streams safely and without blocking, and bridge callback-based APIs. |
| How-to › Config, resources & DI | `/guides/how-to/config` | Load configuration, wire dependencies, fix Scope errors and roll out feature flags. |
| How-to › Web & HTTP | `/guides/how-to/web` | Serve requests, validate input, push events, render HTMX, secure endpoints with JWT and work with OpenAPI. |
| How-to › Observability | `/guides/how-to/observability` | Test and correlate logs, traces and metrics. |
| How-to › Performance | `/guides/how-to/performance` | Cut allocation and boxing, and tune codec throughput. |
| Migration guides | `/guides/migration` | Move code from another Scala library to ZIO Blocks. |
| Deep dives | `/guides/deep-dives` | Long-form explanations of how a part of ZIO Blocks works and why. |

No guide id clashes with these slugs (no guide is named `tutorials`, `how-to`, `migration` or `deep-dives`). The Query DSL group keeps its existing doc link, `guides/query-dsl/index`.

### 3. Guide placement (all 74 guides, each exactly once)

**Tutorials (17)**, in this order:

| # | Doc id (`guides/...`) | Learning-path stage |
|---|---|---|
| 1 | `add-zio-blocks-to-your-build` | Foundations |
| 2 | `your-first-schema` | Foundations |
| 3 | `evolve-your-data` | Foundations |
| 4 | `compile-time-resource-safety-with-scope` | Runtime blocks |
| 5 | `typed-config-loader` | Runtime blocks |
| 6 | `async-getting-started` | Runtime blocks |
| 7 | `your-first-streaming-pipeline` | Runtime blocks |
| 8 | `your-first-sqlite-repository` | Building applications |
| 9 | `your-first-html-page` | Building applications |
| 10 | `instrument-a-program-with-logs-traces-and-metrics` | Building applications |
| 11 | `small-json-backend-from-blocks` | Capstone |
| 12 | `nested-data-with-optics` | Elective |
| 13 | `one-type-many-formats` | Elective |
| 14 | `add-interactivity-with-htmx` | Elective (after 9) |
| 15 | `build-a-live-dashboard-with-datastar` | Elective (after 9) |
| 16 | `getting-started-with-mux` | Elective |
| 17 | `generate-scala-code-from-smithy` | Elective |

The stage column explains the order. It does not appear on the site.

**How-to guides (46)**, by topic:

| Topic | Doc ids (`guides/...`), in sidebar order |
|---|---|
| Schema (6) | `validated-newtypes`, `derive-your-own-type-class`, `diff-and-patch`, `build-a-schema-registry`, `use-named-tuples-with-schema`, `generate-json-schema-and-validate-payloads` |
| Formats & codecs (8) | `customize-json-encoding`, `override-codec-for-one-type`, `json-without-case-classes`, `read-and-write-yaml`, `read-and-write-csv-and-tsv-files`, `encode-and-decode-records-with-avro`, `store-case-classes-in-mongodb`, `llm-data-with-toon` |
| SQL & persistence (11) | `connect-to-postgresql-with-a-connection-pool`, `test-database-code-against-sqlite`, `sql-transactions`, `sql-checked-interpolation`, `write-joins-and-inspect-generated-sql`, `upsert-and-paginate-with-keyset-cursors`, `map-custom-column-types`, `stream-large-query-results`, `migrate-table-rows-online`, `store-and-search-vector-embeddings`, `sql-from-zio` |
| Streams & async (4) | `stream-files-and-sockets`, `run-streams-without-blocking`, `wrap-callback-and-future-apis`, `run-a-mux-over-a-socket` |
| Config, resources & DI (4) | `load-configuration-files`, `feed-config-into-a-wire-graph`, `fix-scope-and-wire-compile-errors`, `roll-out-feature-gradually` |
| Web & HTTP (8) | `serve-http-model-requests`, `validate-query-parameters-and-headers`, `send-server-sent-events`, `return-htmx-fragments-and-response-headers`, `issue-and-verify-jwts`, `protect-endpoints-with-jwt`, `write-and-export-openapi`, `generate-scala-types-from-openapi` |
| Observability (2) | `test-telemetry`, `correlate-logs-and-traces` |
| Performance (3) | `tune-json-throughput`, `avoid-allocation-on-hot-paths`, `process-binary-data-without-boxing` |

**Migration guides (6)**, in learning-path module order: `zio-schema-migration`, `json-library-migration`, `resource-and-di-migration`, `config-library-migration`, `streaming-library-migration`, `migrate-from-magnum-or-doobie`.

**Deep dives (5)**: `telemetry-guide`, then the existing "Query DSL" category (link `guides/query-dsl/index`) with `query-dsl-reified-optics`, `query-dsl-sql`, `query-dsl-extending`, `query-dsl-fluent-builder`.

Total: 17 tutorials + 46 how-tos + 6 migration guides + 5 deep dives (the telemetry guide and 4 Query DSL parts) = 74, matching the 74 guide files (`docs/guides/*.md` minus `index.md`).

### 4. The `/guides/` index page

`docs/guides/index.md` keeps `displayed_sidebar: guides` and `<SidebarCards />`. `SidebarCards` is not changed: on this page the "Guides" category's items are the four sections, so the page shows four cards, one per section. Docusaurus shows each card's item count.

The intro paragraph gains one sentence that sends newcomers to the Tutorials section first (a link to `/guides/tutorials`).

### 5. The `## Guides` list in `docs/index.md`

The 67 bullets become six "start here" bullets, in learning-path order:

1. How to Add ZIO Blocks to Your Build
2. Your First Schema: Round-Trip a Type Through JSON
3. Your First Streaming Pipeline: Turn a Sales Log into a Report
4. Build Your First Database Repository on SQLite
5. Instrument a Program with Logs, Traces and Metrics
6. Build a Small JSON Backend from Blocks

Each bullet keeps the `[Title](./guides/<id>.md) - description` shape, because `parse-index.mjs` parses that shape and fails the landing build on anything else. A plain line after the list links the full catalog at `./guides/index.md`. It links the index file, not a generated page, because the landing parser checks that every docs link points to a file under `docs/`, and generated index pages are not files.

After editing `docs/index.md`:

- Regenerate `README.md` with the version pinned to the latest release tag, as `AGENTS.md` describes (`docs/mdoc --include index.md; docs/generateReadme`).
- Run `cd website && yarn install --frozen-lockfile && yarn test && yarn check:landing`.

The `### Learn More` links elsewhere in `docs/index.md` point to single guides and are not touched.

### 6. Docs that describe the old setup

- `website/README.md`, section "Index pages for `/guides/` and `/reference/`": say that `/guides/` shows one card per section, and that sections and how-to topics have generated index pages.
- `docs/dev/research.md:127`: a new guide needs a sidebar entry under its section and topic.
- `docs/dev/backlog.md:173`: same note, and drop the stale reference to the hand-maintained list at `docs/index.md:492-502`.

## Out of scope

- Renaming, rewriting or retitling any guide (for example, giving the older SQL explainer pages "How to" titles).
- Moving guide files or changing any guide URL.
- The reference sidebar.
- `docs/superpowers/` being published as site pages (it is missing from the docs plugin's `exclude` list). This is a separate fix.

## Verification

1. A script compares the doc ids in the `guides` sidebar with `ls docs/guides/*.md` (minus `index.md`) and `docs/guides/query-dsl/index.md`. Every guide must appear exactly once, and the sidebar must name no missing file.
2. Full website build (`yarn build`, not the landing-only build), which fails on broken links.
3. In a browser: `/guides/` shows four cards. Each section card opens its generated page. Each topic card opens its topic page. A guide page shows the sidebar with its topic expanded and the breadcrumb Home › Guides › section › topic › page.
4. `yarn test` (its `parseIndex accepts the real docs/index.md` test reads the edited file) and `yarn check:landing` pass, and `/overview` and the regenerated `README.md` show the six curated guides.
5. Scoped `docs/mdoc` on `docs/index.md` passes before `docs/generateReadme`.

## Risks

- **Generated slugs vs. trailing slashes.** `src/clientModules/folderIndexes.js` adds a trailing slash to `/guides` and `/reference`. The generated pages are new paths, so check in the browser that `/guides/how-to` and `/guides/how-to/` both resolve.
- **Card count text.** Docusaurus prints "N items" on a category card. For "How-to guides" that counts the 8 topics, not the 46 guides. The description line on the generated page makes the contents clear. Accepted as is.
- **The Overview page and README shrink** from 67 guide links to 6. This is intended: they become a starting point, and `/guides/` holds the full catalog.
