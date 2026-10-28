# Guides Section Organization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Regroup the 74 guides into Tutorials, How-to guides (by topic), Migration guides and Deep dives, with a generated landing page per section and topic, and cut the hand-written guides list in `docs/index.md` to six start-here picks.

**Architecture:** All structure lives in the `guides` sidebar in `docs/sidebars.js`. The sidebar, the `/guides/` cards (`docs/SidebarCards.jsx`, unchanged) and the twelve `generated-index` pages are all drawn from it. No guide file moves, so no URL changes. A new Node unit test pins the sidebar shape and fails if any guide file is missing from it.

**Tech Stack:** Docusaurus 3.10.2 (`website/`), Node's built-in test runner (`node --test`), sbt + mdoc for the docs build and `README.md` generation.

**Spec:** `docs/superpowers/specs/2026-10-28-guides-organization-design.md`

## Global Constraints

- Work on the worktree's branch, `organize-guides`. Do not create a branch.
- No guide file is moved, renamed, retitled or rewritten. Only `docs/sidebars.js`, `docs/guides/index.md`, `docs/index.md`, `README.md`, `website/README.md`, `docs/dev/research.md`, `docs/dev/backlog.md` and the new test file change.
- Four sections, in this order: `Tutorials`, `How-to guides`, `Migration guides`, `Deep dives`.
- Eight how-to topics, in this order: `Schema`, `Formats & codecs`, `SQL & persistence`, `Streams & async`, `Config, resources & DI`, `Web & HTTP`, `Observability`, `Performance`.
- Sections start expanded (`collapsed: false`); how-to topics start collapsed (`collapsed: true`).
- Every section and topic links a `generated-index` page whose slug starts with `/guides/`; the Query DSL group keeps its doc link `guides/query-dsl/index`.
- `README.md` is generated, never hand-edited. Regenerate it with `version` pinned to the latest release tag, `0.0.56`.
- Every sbt call uses the `AGENTS.md` template (`sbt --client -Dsbt.color=false ... >"$LOG" 2>&1`), with `++3.9.0`.
- Commit messages: conventional style, e.g. `docs(guides): ...`. Commit, but do not push and do not open a PR without the user's go-ahead.

## Review Focus

1. **A new guide file added without a sidebar entry.** The reader expects it to show up under `/guides/`. Pinned by the "every guide page appears in the sidebar exactly once" test (Task 1).
2. **A future guide whose id equals a generated slug** (for example a new `docs/guides/tutorials.md`). The reader expects two distinct pages, not a route clash. Pinned by the "a generated page and a guide never share a URL" assertion (Task 1).
3. **A generated page opened with or without a trailing slash** (`/guides/how-to` and `/guides/how-to/`). Both should load the same page. Checked against the served build (Task 3, Step 5).
4. **A guide opened directly** (from search or a link) **whose topic starts collapsed.** The reader expects the sidebar to open on that topic with the page highlighted. Checked in the browser (Task 3, Step 6).
5. **Breadcrumbs on a guide and on a generated page.** The reader expects Home › Guides › section › topic › page, with each crumb leading to its landing page. Checked in the built HTML and the browser (Task 3, Steps 4 and 6).

---

### Task 1: Regroup the guides sidebar

**Files:**
- Create: `website/test/unit/guides-sidebar.test.mjs`
- Modify: `docs/sidebars.js:454-547` (the `guides` sidebar and the comment above it)
- Modify: `docs/guides/index.md:12` (intro paragraph)
- Modify: `website/README.md:74-77` (section "Index pages for `/guides/` and `/reference/`")
- Modify: `docs/dev/research.md:127`
- Modify: `docs/dev/backlog.md:173`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: the generated page slugs `/guides/tutorials`, `/guides/how-to`, `/guides/how-to/{schema,formats,sql,streams,config,web,observability,performance}`, `/guides/migration`, `/guides/deep-dives`. Task 3 checks these URLs.

- [ ] **Step 1: Install the website's dependencies** (the worktree has no `website/node_modules`)

Run: `cd website && yarn install --frozen-lockfile`
Expected: finishes with `Done`.

- [ ] **Step 2: Write the failing test**

Create `website/test/unit/guides-sidebar.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';

// The guides sidebar (docs/sidebars.js) is the one list of guides: the sidebar, the /guides/ cards and the section and
// topic index pages are all drawn from it, so a guide missing here is missing from the site's navigation.
const require = createRequire(import.meta.url);
const docs = new URL('../../../docs/', import.meta.url);
const [root, ...rest] = require('../../../docs/sidebars.js').guides;

const isCategory = (item) => typeof item === 'object' && item.type === 'category';
const docIds = (items) =>
  items.flatMap((item) =>
    typeof item === 'string'
      ? [item]
      : item.type === 'doc'
        ? [item.id]
        : isCategory(item)
          ? [...(item.link?.type === 'doc' ? [item.link.id] : []), ...docIds(item.items)]
          : [],
  );
const categoriesIn = (items) => items.filter(isCategory).flatMap((c) => [c, ...categoriesIn(c.items)]);

const sections = root.items;
const howTo = sections.find((s) => s.label === 'How-to guides');

test('the guides sidebar is one Guides category that links the guides index', () => {
  assert.equal(rest.length, 0);
  assert.equal(root.label, 'Guides');
  assert.deepEqual(root.link, { type: 'doc', id: 'guides/index' });
});

test('the Guides category holds the four sections, in order, and nothing else', () => {
  assert.deepEqual(
    sections.map((s) => s.label),
    ['Tutorials', 'How-to guides', 'Migration guides', 'Deep dives'],
  );
});

test('How-to guides holds the eight topics, and each topic holds only guides', () => {
  assert.deepEqual(
    howTo.items.map((t) => t.label),
    ['Schema', 'Formats & codecs', 'SQL & persistence', 'Streams & async', 'Config, resources & DI', 'Web & HTTP',
      'Observability', 'Performance'],
  );
  for (const topic of howTo.items) assert.ok(topic.items.every((i) => typeof i === 'string'), topic.label);
});

test('every guide page appears in the sidebar exactly once', () => {
  const pages = readdirSync(new URL('guides/', docs))
    .filter((f) => /\.mdx?$/.test(f) && f !== 'index.md')
    .map((f) => `guides/${f.replace(/\.mdx?$/, '')}`)
    .sort();
  const ids = docIds([root]).filter((id) => id !== 'guides/index' && id !== 'guides/query-dsl/index');
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), [], 'listed twice');
  assert.deepEqual([...ids].sort(), pages);
});

test('every doc id in the sidebar names a page', () => {
  for (const id of docIds([root]))
    assert.ok(existsSync(new URL(`${id}.md`, docs)) || existsSync(new URL(`${id}.mdx`, docs)), id);
});

test('every section and topic opens a generated index page under /guides/ with a description', () => {
  const all = categoriesIn(sections);
  assert.deepEqual(all.filter((c) => c.link?.type !== 'generated-index').map((c) => c.label), ['Query DSL']);
  const generated = all.filter((c) => c.link?.type === 'generated-index');
  const slugs = generated.map((c) => c.link.slug);
  assert.equal(new Set(slugs).size, slugs.length, 'slugs are unique');
  for (const c of generated) {
    assert.match(c.link.slug, /^\/guides\/[a-z-]+(\/[a-z-]+)?$/, c.label);
    assert.ok(c.link.description?.length > 0, `${c.label} has a description`);
  }
  // Docs are served at the site root, so guides/<x> is at /guides/<x>: a generated page and a guide never share a URL.
  for (const id of docIds([root])) assert.equal(slugs.includes(`/${id}`), false, id);
});

test('the sections start expanded and the how-to topics start collapsed', () => {
  for (const s of sections) assert.equal(s.collapsed, false, s.label);
  for (const t of howTo.items) assert.equal(t.collapsed, true, t.label);
});

test('the tutorials start with installing the library, then the first schema', () => {
  assert.deepEqual(sections[0].items.slice(0, 2), ['guides/add-zio-blocks-to-your-build', 'guides/your-first-schema']);
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd website && node --test test/unit/guides-sidebar.test.mjs`
Expected: `pass 4`, `fail 4`. Failing: "the Guides category holds the four sections…" (a diff of 75 `undefined` labels), "How-to guides holds the eight topics…", "the sections start expanded…" and "the tutorials start with installing…". Passing: "one Guides category…", "every guide page appears…" and "every doc id…" (today's flat sidebar already lists all 74 guides once), and "every section and topic opens a generated index page…" (true of nothing yet, because no sections exist). The test needs no `node_modules`.

- [ ] **Step 4: Replace the `guides` sidebar**

In `docs/sidebars.js`, replace everything from the comment line `// Guides get their own sidebar, reached from the "Guides" navbar item, ...` through the closing `],` of the `guides` array (the line before the final `};`) with:

```js
  // Guides get their own sidebar, reached from the "Guides" navbar item, so the reference sidebar stays reference-only.
  // The "Guides" category is what gives every guide its breadcrumb trail (Home > Guides > section > topic > guide), as the
  // reference pages get theirs from their categories. Its link is the guides index, so the crumb leads back there, and
  // that page (SidebarCards) shows the four sections as cards. Each section and how-to topic links a generated index
  // page that lists its items as cards, so a new guide only needs an entry here, under its section and topic
  // (website/test/unit/guides-sidebar.test.mjs fails if a guide page is missing).
  guides: [
    {
      type: "category",
      label: "Guides",
      link: { type: "doc", id: "guides/index" },
      collapsed: false,
      items: [
        {
          type: "category",
          label: "Tutorials",
          link: {
            type: "generated-index",
            slug: "/guides/tutorials",
            description: "Step-by-step lessons for newcomers, in the order to read them. Start with the first one.",
          },
          collapsed: false,
          // The learning path of docs/dev/backlog.md §3 (foundations, runtime blocks, building applications, capstone),
          // then the electives.
          items: [
            "guides/add-zio-blocks-to-your-build",
            "guides/your-first-schema",
            "guides/evolve-your-data",
            "guides/compile-time-resource-safety-with-scope",
            "guides/typed-config-loader",
            "guides/async-getting-started",
            "guides/your-first-streaming-pipeline",
            "guides/your-first-sqlite-repository",
            "guides/your-first-html-page",
            "guides/instrument-a-program-with-logs-traces-and-metrics",
            "guides/small-json-backend-from-blocks",
            "guides/nested-data-with-optics",
            "guides/one-type-many-formats",
            "guides/add-interactivity-with-htmx",
            "guides/build-a-live-dashboard-with-datastar",
            "guides/getting-started-with-mux",
            "guides/generate-scala-code-from-smithy",
          ]
        },
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
              label: "Schema",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/schema",
                description: "Validate, compare, register and derive with Schema.",
              },
              collapsed: true,
              items: [
                "guides/validated-newtypes",
                "guides/derive-your-own-type-class",
                "guides/diff-and-patch",
                "guides/build-a-schema-registry",
                "guides/use-named-tuples-with-schema",
                "guides/generate-json-schema-and-validate-payloads",
              ]
            },
            {
              type: "category",
              label: "Formats & codecs",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/formats",
                description: "Read and write JSON, YAML, CSV, Avro, BSON and TOON, and customize codecs.",
              },
              collapsed: true,
              items: [
                "guides/customize-json-encoding",
                "guides/override-codec-for-one-type",
                "guides/json-without-case-classes",
                "guides/read-and-write-yaml",
                "guides/read-and-write-csv-and-tsv-files",
                "guides/encode-and-decode-records-with-avro",
                "guides/store-case-classes-in-mongodb",
                "guides/llm-data-with-toon",
              ]
            },
            {
              type: "category",
              label: "SQL & persistence",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/sql",
                description: "Connect, query, page, stream, migrate and test database code with the sql module.",
              },
              collapsed: true,
              items: [
                "guides/connect-to-postgresql-with-a-connection-pool",
                "guides/test-database-code-against-sqlite",
                "guides/sql-transactions",
                "guides/sql-checked-interpolation",
                "guides/write-joins-and-inspect-generated-sql",
                "guides/upsert-and-paginate-with-keyset-cursors",
                "guides/map-custom-column-types",
                "guides/stream-large-query-results",
                "guides/migrate-table-rows-online",
                "guides/store-and-search-vector-embeddings",
                "guides/sql-from-zio",
              ]
            },
            {
              type: "category",
              label: "Streams & async",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/streams",
                description: "Run streams safely and without blocking, and bridge callback-based APIs.",
              },
              collapsed: true,
              items: [
                "guides/stream-files-and-sockets",
                "guides/run-streams-without-blocking",
                "guides/wrap-callback-and-future-apis",
                "guides/run-a-mux-over-a-socket",
              ]
            },
            {
              type: "category",
              label: "Config, resources & DI",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/config",
                description: "Load configuration, wire dependencies, fix Scope errors and roll out feature flags.",
              },
              collapsed: true,
              items: [
                "guides/load-configuration-files",
                "guides/feed-config-into-a-wire-graph",
                "guides/fix-scope-and-wire-compile-errors",
                "guides/roll-out-feature-gradually",
              ]
            },
            {
              type: "category",
              label: "Web & HTTP",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/web",
                description: "Serve requests, validate input, push events, render HTMX, secure endpoints with JWT and work with OpenAPI.",
              },
              collapsed: true,
              items: [
                "guides/serve-http-model-requests",
                "guides/validate-query-parameters-and-headers",
                "guides/send-server-sent-events",
                "guides/return-htmx-fragments-and-response-headers",
                "guides/issue-and-verify-jwts",
                "guides/protect-endpoints-with-jwt",
                "guides/write-and-export-openapi",
                "guides/generate-scala-types-from-openapi",
              ]
            },
            {
              type: "category",
              label: "Observability",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/observability",
                description: "Test and correlate logs, traces and metrics.",
              },
              collapsed: true,
              items: [
                "guides/test-telemetry",
                "guides/correlate-logs-and-traces",
              ]
            },
            {
              type: "category",
              label: "Performance",
              link: {
                type: "generated-index",
                slug: "/guides/how-to/performance",
                description: "Cut allocation and boxing, and tune codec throughput.",
              },
              collapsed: true,
              items: [
                "guides/tune-json-throughput",
                "guides/avoid-allocation-on-hot-paths",
                "guides/process-binary-data-without-boxing",
              ]
            },
          ]
        },
        {
          type: "category",
          label: "Migration guides",
          link: {
            type: "generated-index",
            slug: "/guides/migration",
            description: "Move code from another Scala library to ZIO Blocks.",
          },
          collapsed: false,
          items: [
            "guides/zio-schema-migration",
            "guides/json-library-migration",
            "guides/resource-and-di-migration",
            "guides/config-library-migration",
            "guides/streaming-library-migration",
            "guides/migrate-from-magnum-or-doobie",
          ]
        },
        {
          type: "category",
          label: "Deep dives",
          link: {
            type: "generated-index",
            slug: "/guides/deep-dives",
            description: "Long-form explanations of how a part of ZIO Blocks works and why.",
          },
          collapsed: false,
          items: [
            "guides/telemetry-guide",
            {
              type: "category",
              label: "Query DSL",
              link: { type: "doc", id: "guides/query-dsl/index" },
              collapsed: false,
              items: [
                "guides/query-dsl-reified-optics",
                "guides/query-dsl-sql",
                "guides/query-dsl-extending",
                "guides/query-dsl-fluent-builder",
              ]
            },
          ]
        },
      ],
    },
  ]
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd website && node --test test/unit/guides-sidebar.test.mjs`
Expected: PASS, 8 tests, 0 failures.

- [ ] **Step 6: Point newcomers to the tutorials on `/guides/`**

In `docs/guides/index.md`, replace the paragraph

```
Each guide works through one task with ZIO Blocks from start to finish. When you need the full description of a data type along the way, the [reference](../reference/index.md) has a page for it.
```

with

```
Each guide works through one task with ZIO Blocks from start to finish. New to ZIO Blocks? Start with the [tutorials](/guides/tutorials), in order: they take you from installing the library to a small working backend. When you need the full description of a data type along the way, the [reference](../reference/index.md) has a page for it.
```

Leave the front matter, the import and `<SidebarCards />` as they are.

- [ ] **Step 7: Update the docs that describe the old setup**

In `website/README.md`, replace

```
`docs/sidebars.js`: `displayed_sidebar` shows the guides or the main sidebar beside them, and `docs/SidebarCards.jsx`
draws one card per entry of that sidebar (only the categories on `/reference/`) with Docusaurus' `DocCardList`, so a
new guide or block group appears there without editing the page. `src/clientModules/folderIndexes.js` gives `/guides`
```

with

```
`docs/sidebars.js`: `displayed_sidebar` shows the guides or the main sidebar beside them, and `docs/SidebarCards.jsx`
draws one card per entry of that sidebar with Docusaurus' `DocCardList`: one per section on `/guides/` (Tutorials,
How-to guides, Migration guides, Deep dives) and one per block category on `/reference/` (`categoriesOnly`). Each guides
section and how-to topic links a `generated-index` page under `/guides/` that lists its items as cards, so a new guide
or block group appears on these pages without editing them; `test/unit/guides-sidebar.test.mjs` fails if a guide page
is missing from the guides sidebar. `src/clientModules/folderIndexes.js` gives `/guides`
```

In `docs/dev/research.md:127`, replace

```
- **Guides index.** `docs/guides/index.md` renders cards from the guides sidebar (`docs/SidebarCards.jsx`), so a new guide only needs a sidebar entry.
```

with

```
- **Guides index.** `docs/guides/index.md` renders cards from the guides sidebar (`docs/SidebarCards.jsx`), so a new guide only needs a sidebar entry, under its section (Tutorials, How-to guides, Migration guides or Deep dives) and, for a how-to guide, its topic.
```

In `docs/dev/backlog.md:173`, replace

```
- New guides need a `docs/sidebars.js` entry in the `guides` sidebar. Also update the stale hand-maintained list at `docs/index.md:492-502`.
```

with

```
- New guides need a `docs/sidebars.js` entry in the `guides` sidebar, under their section (Tutorials, How-to guides, Migration guides or Deep dives) and, for a how-to guide, its topic. The `## Guides` list in `docs/index.md` holds only six start-here picks; add a guide there only to replace one of them.
```

- [ ] **Step 8: Run the whole unit suite**

Run: `cd website && yarn test`
Expected: all tests pass, including the 8 new ones.

- [ ] **Step 9: Commit**

```bash
git add docs/sidebars.js docs/guides/index.md website/test/unit/guides-sidebar.test.mjs website/README.md docs/dev/research.md docs/dev/backlog.md
git commit -m "docs(guides): group the guides sidebar into tutorials, how-to topics, migration and deep dives"
```

---

### Task 2: Cut the `docs/index.md` guides list to six start-here picks

**Files:**
- Modify: `docs/index.md:504-572` (the `## Guides` section, up to but not including `## Full API Reference`)
- Modify: `README.md` (regenerated, never hand-edited)

**Interfaces:**
- Consumes: nothing from Task 1 (the links point at guide files, not generated pages).
- Produces: a `## Guides` section that `website/scripts/lib/parse-index.mjs` parses into six guides.

- [ ] **Step 1: Replace the section body**

In `docs/index.md`, replace everything between the line `## Guides` and the line `## Full API Reference` (both kept) with:

```markdown

- [How to Add ZIO Blocks to Your Build](./guides/add-zio-blocks-to-your-build.md) - Pick the modules you need and add them to an sbt or scala-cli build, for any Scala version and for Scala.js
- [Your First Schema: Round-Trip a Type Through JSON](./guides/your-first-schema.md) - Describe a type once with `Schema`, then encode, decode, and read errors
- [Your First Streaming Pipeline: Turn a Sales Log into a Report](./guides/your-first-streaming-pipeline.md) - Read a sales log with a `Stream`, parse, filter and batch it, sum it with a `Sink`, and recover from a bad line
- [Build Your First Database Repository on SQLite](./guides/your-first-sqlite-repository.md) - Connect to SQLite, derive a table and `Repo` from one schema, write your own queries, and roll back a transaction
- [Instrument a Program with Logs, Traces and Metrics](./guides/instrument-a-program-with-logs-traces-and-metrics.md) - Add structured logs, nested spans, a counter and a histogram to a batch job, switch to JSON logs, and test the spans
- [Build a Small JSON Backend from Blocks](./guides/small-json-backend-from-blocks.md) - Capstone: combine schema, config, scope, SQL, the HTTP model and telemetry into one small JSON backend

These six follow the learning path, from installing the library to a small working backend. The [guides index](./guides/index.md) lists every tutorial, how-to guide, migration guide and deep dive.

```

Keep each bullet as `- [Title](./guides/<file>.md) - description`: `parse-index.mjs` parses exactly that shape and fails the landing build otherwise. The closing line is a paragraph, not a bullet, so the parser skips it.

- [ ] **Step 2: Run the parser tests against the edited page**

Run: `cd website && node --test test/unit/parse-index.test.mjs`
Expected: PASS, including `parseIndex accepts the real docs/index.md`.

- [ ] **Step 3: Check the landing build**

Run: `cd website && yarn check:landing`
Expected: the landing build finishes and `test/dist.test.mjs` passes.

- [ ] **Step 4: Regenerate `README.md` at the release version**

Run from the repository root:

```bash
GD="$(git rev-parse --absolute-git-dir)" && mkdir -p "$GD/agent-logs"
LOG="$GD/agent-logs/sbt-$(date +%s)-$$.log"
sbt --client -Dsbt.color=false 'set ThisBuild / version := "0.0.56"; ++3.9.0; docs/mdoc --include index.md; docs/generateReadme' >"$LOG" 2>&1; echo "Exit: $? | Log: $LOG"
```

Expected: `Exit: 0`. If it fails, `grep -i error "$LOG"`.

- [ ] **Step 5: Check the README diff is only the guides list**

Run: `git diff --stat README.md && git diff README.md | grep '^[-+]' | grep -v 'guides/' | grep -v '^[-+][-+]'`
Expected: the only changed lines are guide bullets plus the new closing paragraph. If other lines change (for example a `SNAPSHOT` version), the version pin did not apply: stop and investigate before committing.

- [ ] **Step 6: Commit**

```bash
git add docs/index.md README.md
git commit -m "docs: list six start-here guides in the overview and link the guides index"
```

---

### Task 3: Build the full site and check the result

**Files:** none changed, unless a check fails. Then fix the file the failure points to, rerun the failing step, and commit the fix on its own (`fix(guides): ...`).

**Interfaces:**
- Consumes: Task 1's slugs and sidebar, Task 2's `docs/index.md`.
- Produces: nothing.

- [ ] **Step 1: Restart the sbt server** (Task 2 ran mdoc on `index.md`; pages that open JDBC connections pass mdoc only once per server)

Run: `sbt --client shutdown`

- [ ] **Step 2: Generate all docs with mdoc**

```bash
GD="$(git rev-parse --absolute-git-dir)"
LOG="$GD/agent-logs/sbt-$(date +%s)-$$.log"
sbt --client -Dsbt.color=false '++3.9.0; docs/mdoc' >"$LOG" 2>&1; echo "Exit: $? | Log: $LOG"
```

Expected: `Exit: 0`, with output in `website/docs/`.

- [ ] **Step 3: Full Docusaurus build**

Run: `cd website && yarn build`
Expected: the build succeeds. A full build fails on broken links, so a bad link in `docs/guides/index.md` or `docs/index.md` stops it here.

- [ ] **Step 4: Check the generated pages and breadcrumbs in the build output**

Run from `website/`:

```bash
for p in tutorials how-to how-to/schema how-to/formats how-to/sql how-to/streams how-to/config how-to/web how-to/observability how-to/performance migration deep-dives; do
  f=$(ls build/guides/$p/index.html build/guides/$p.html 2>/dev/null | head -1); echo "${f:-MISSING} <- /guides/$p"
done
node -e '
const fs = require("fs");
const f = ["build/guides/sql-transactions/index.html", "build/guides/sql-transactions.html"].find(fs.existsSync);
const crumbs = fs.readFileSync(f, "utf8").match(/<nav[^>]*breadcrumbs[\s\S]*?<\/nav>/)[0];
for (const href of ["/guides/", "/guides/how-to", "/guides/how-to/sql"])
  console.log(crumbs.includes(`href="${href}"`) ? "ok  " : "MISS", href);
'
```

Expected: no `MISSING` line, and three `ok` lines. The crumb `href` may end in `/`; if a `MISS` line shows, check whether the crumb is there with a trailing slash before treating it as a failure.

- [ ] **Step 5: Serve the build and check both slash forms**

Run: `cd website && yarn serve --port 3000` (in the background), then:

```bash
for u in /guides /guides/ /guides/tutorials /guides/tutorials/ /guides/how-to /guides/how-to/ /guides/how-to/sql /guides/how-to/sql/; do
  printf '%s %s\n' "$(curl -s -o /dev/null -w '%{http_code}' -L "http://localhost:3000$u")" "$u"
done
```

Expected: `200` for every line.

- [ ] **Step 6: Click through in a browser**

With the server from Step 5 running, open `http://localhost:3000/guides/` and confirm:

1. The sidebar shows Guides › Tutorials, How-to guides (8 collapsed topics), Migration guides, Deep dives (with Query DSL).
2. The page body shows the intro with its "tutorials" link, then four cards: Tutorials, How-to guides, Migration guides, Deep dives.
3. The Tutorials card opens `/guides/tutorials` with 17 cards, "How to Add ZIO Blocks to Your Build" first.
4. The How-to guides card opens `/guides/how-to` with 8 topic cards; the SQL & persistence card opens `/guides/how-to/sql` with 11 guide cards.
5. Opening `http://localhost:3000/guides/sql-transactions` directly shows the sidebar with SQL & persistence expanded and the page highlighted, and the breadcrumb Home › Guides › How-to guides › SQL & persistence › page, each crumb linking to its landing page.
6. `http://localhost:3000/overview` shows the six-guide list and the "guides index" link, which opens `/guides/`.

Stop the server afterwards.

- [ ] **Step 7: Record the result**

If every check passed and nothing changed, there is nothing to commit. Report the result to the user, and ask before pushing or opening a PR.
