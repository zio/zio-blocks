# ZIO Blocks: tutorials and how-to guides backlog

Compiled 2026-10-19 on branch `tutorials-and-guides-lists` (HEAD `b5f9fb71`). Status last updated 2026-10-20.

## Status

This table tracks every item that has been written or started. Items not listed here have not been started. Update the row when an item changes state.

| ID   | Title                                                                         | Status                                  | Where                                                                                   |
|------|-------------------------------------------------------------------------------|-----------------------------------------|-----------------------------------------------------------------------------------------|
| T-01 | Your first schema: round-trip a type through JSON                             | Done                                    | #1756, `guides/your-first-schema.md`                                                    |
| T-07 | Build your first database repository on SQLite                                | Done                                    | #1760, `guides/your-first-sqlite-repository.md`, `sql-examples/`                        |
| T-09 | Render your first server-side HTML page                                       | Done                                    | #1763, `guides/your-first-html-page.md`, `html-examples/`                               |
| T-05 | Build a typed configuration loader                                            | Done                                    | #1759, `guides/typed-config-loader.md`, `config-examples/`                              |
| M-05 | Coming from cats-effect `Resource`, ZIO `Scope`/`ZLayer`, macwire or `Using`  | Done                                    | #1769, `guides/resource-and-di-migration.md`                                            |
| T-03 | Navigate and update nested data with optics                                   | Done                                    | #1770, `guides/nested-data-with-optics.md`                                              |
| T-04 | One type, many formats                                                        | Done                                    | #1762, `guides/one-type-many-formats.md`                                                |
| H-05 | How to generate JSON Schema from a type and validate payloads                 | Done                                    | #1772, `guides/generate-json-schema-and-validate-payloads.md`                           |
| H-02 | How to customize JSON field names, ADT encoding and optional fields           | Done                                    | #1775, `guides/customize-json-encoding.md`                                              |
| H-03 | How to override the codec for one type everywhere                             | Done                                    | #1781, `guides/override-codec-for-one-type.md`                                          |
| H-12 | How to send compact data to an LLM with TOON and parse the reply              | Done                                    | #1774, `guides/llm-data-with-toon.md`                                                   |
| H-31 | How to fix common Scope and Wire compile errors                               | Done                                    | #1783, `guides/fix-scope-and-wire-compile-errors.md`                                    |
| M-06 | Coming from PureConfig, Typesafe Config or zio-config                         | Done                                    | #1782, `guides/config-library-migration.md`                                             |
| M-01 | Coming from circe, zio-json or jsoniter-scala                                 | Done                                    | #1785, `guides/json-library-migration.md`                                               |
| H-47 | How to test database code against SQLite                                      | Done                                    | #1786, `guides/test-database-code-against-sqlite.md`                                    |
| H-37 | How to issue and verify JWTs (plus `jwt.md` fixes)                            | Done                                    | #1789, `guides/issue-and-verify-jwts.md`, `jwt-examples/`                               |
| H-17 | How to connect to PostgreSQL with a connection pool                           | Done                                    | #1790, `guides/connect-to-postgresql-with-a-connection-pool.md`                         |
| H-32 | How to roll out a feature gradually with flags                                | Done                                    | #1788, `guides/roll-out-feature-gradually.md`                                           |
| H-08 | How to derive your own type class from a Schema                               | Done                                    | #1792, `guides/derive-your-own-type-class.md`                                           |
| T-06 | Build your first streaming pipeline                                           | Pushed, no PR                           | branch `streaming-pipeline`, `guides/your-first-streaming-pipeline.md`                  |
| H-20 | How to stream large query results                                             | Pushed, no PR                           | branch `stream-query`, `guides/stream-large-query-results.md`                           |
| H-24 | How to stream files and sockets without leaking resources                     | Pushed, no PR                           | branch `stream-resources`, `guides/stream-files-and-sockets.md`                         |
| H-11 | How to read and write CSV and TSV files                                       | In review                               | #1793 (`csv-files`), `guides/read-and-write-csv-and-tsv-files.md`                       |
| H-30 | How to feed configuration into a Wire dependency graph                        | Pushed, no PR                           | branch `config-wire`, `guides/feed-config-into-a-wire-graph.md`                         |
| M-04 | Coming from fs2, ZIO Streams or Ox                                            | Pushed, no PR                           | branch `streams-migration-guide`, `guides/streaming-library-migration.md`               |
| H-19 | How to write joins and inspect the generated SQL                              | Pushed, no PR                           | branch `sql-joins`, `guides/write-joins-and-inspect-generated-sql.md`; recommends a new `reference/sql/sql-query.md` |
| H-18 | How to upsert rows and paginate with keyset cursors                           | Pushed, no PR                           | branch `upsert-pagination`, `guides/upsert-and-paginate-with-keyset-cursors.md`; trims `query-dsl-sql.md` |
| H-21 | How to use the sql module from a ZIO application                             | Pushed, no PR                           | branch `docs/h21-sql-from-zio`, `guides/sql-from-zio.md`; adds `sql-zio` to the docs classpath |
| H-35 | How to serve http-model requests with the JDK HTTP server                     | Pushed, no PR                           | branch `http-server`, `guides/serve-http-model-requests.md`; reusable `JdkHttp` adapter in `http-model-examples/` |
| M-03 | Coming from Magnum or doobie                                                  | Committed locally, not pushed           | worktree `magnum-migration` (2 commits)                                                 |
| H-28 | How to wrap callback and Future-based APIs in Async                           | Committed locally, not pushed           | worktree `async-interop` (2 commits)                                                    |
| H-01 | How to add ZIO Blocks to your build                                           | Pushed, no PR                           | branch `adding-zio-block-to-build` (6 commits); fixes the 3.9+ rule across docs        |
| T-02 | Evolve your data from V1 to V2 with `Into`, `As` and `Migration`              | Stalled: uncommitted, session exited    | worktree `evolve-data`; `guides/evolve-your-data.md`                                    |
| H-25 | How to process a stream in parallel with bounded concurrency                  | Skipped (maintainer decision)           | —                                                                                       |
| T-08 | Describe a typed HTTP API with Endpoint                                       | Skipped (maintainer decision)           | —                                                                                       |

**Related doc-fix PRs:** #1771 (config parse-error and `EnvSource` contradictions, merged), #1784 (`codec.md` overrides, merged), #1773 (codec reference pages, open), #1787 (`type-class-derivation.md` snippets, merged). #1761 (`loadOrThrow` error count) was closed without merging.

**Unblocked since this backlog was compiled:**

- T-07 merged, so the sql items can start: H-17 (written, unpushed), H-18 (partly covered by the `repo.md` upsert and `pageAfter` docs added in #1760), H-20 and H-47.
- T-05 merged, so H-30 (config into a `Wire` graph) and H-32 (feature-flag rollouts) can start.
- T-09 merged, so T-10 (HTMX) and T-11 (Datastar) have their prerequisite, but both still need an HTTP adapter (H-35).
- PR #1742 (projection docs) merged on 2026-10-20. T-14 now waits only on issue #1744, which has grown to sixteen engine problems.

**Unblocked by T-06 (pushed):** H-20, H-24, H-25 and M-04 can start once it merges. H-24 should cover `Stream.fromResource`, which T-06 left out.

**Unblocked by H-35 (pushed):** H-36, H-38, H-49, H-40, H-41, T-10, T-11 and the T-15 capstone can reuse its `JdkHttp` adapter (`http-model-examples/src/main/scala/servehttpmodelrequests/JdkHttp.scala`).

**Waiting on stalled items:** H-04 and M-07 need T-02.

**Library follow-ups found while writing** (full write-ups, ready to file, are in `docs/dev/issues.md`):

- config: `ProvenanceMap#dump` over an unprefixed `EnvSource` prints the whole process environment. Only names that match `Sensitive` markers are redacted.
- sql: `Table.createTable` emits no `PRIMARY KEY`, so `Repo.insertOrUpdate` fails on tables it creates. #1760 documents this; the code is unchanged.
- html: `selector"..."` never matches in `Dom#select` (#1765). `html"..."` escapes interpolated attribute values twice (#1764). `html.md` drift (#1766). On Scala 3, `import zio.blocks.html._` with `import zio.blocks.schema._` makes `p(...)` ambiguous (not filed).
- scope: a `val r = Resource.from[T](...)` can be used only once; the second use throws "Cannot allocate from a destroyed shared resource". On Scala 3, `Unscoped` is derived for case classes that extend `AutoCloseable`, so a closed resource can escape `use`. `Wire(new Db(...))` builds eagerly. `Wire.Shared.fromFunction` does not register `close()` for an `AutoCloseable` result.
- schema / JSON Schema: `Schema.fromJsonSchema` treats every property as required and ignores `"required"` (`JsonSchemaToReflect.scala:311`): issue #1776. `JsonSchema#check` enforces `pattern` only when it is anchored with `^...$`, while JSON Schema matches patterns anywhere in the string.
- schema-toon: `ArrayFormat.Tabular` with a non-primitive field writes output that cannot be decoded (#1778). A top-level tabular list cannot be decoded, even when the codec wrote it (#1779). `ArrayFormat.Auto` never picks the tabular layout for record lists. `withFieldNameMapper` is ignored in tabular headers (#1780).
- config: `loadOrThrow` reports the wrong error count when the single error is a `Composite`. #1761 was closed without merging, so this is still open.

## 1. Introduction

**Purpose.** This page lists candidate **tutorials** and **how-to guides** for the ZIO Blocks documentation site, in priority order. It is a planning document for documentation authors, not user-facing documentation.

**How the list was compiled.**

1. **Library capabilities** come from `docs/dev/research.md`, a code-grounded survey of all 41 published modules. Every topic below cites the section or module of that file it depends on. No topic relies on an API that the research did not find in the source.
2. **Existing guides** come from `docs/guides/*.md` and `docs/sidebars.js`. A topic that an existing guide already covers is left out, or kept only with a clearly different angle, which the row states.
3. **User demand** comes from about 35 web searches and fetches (October 2026): the doc sites of comparable libraries (tapir, doobie, ZIO, Pekko Projection, otel4s, chimney, the Typelevel toolkit), library issue trackers (zio-blocks, zio-schema, zio-json, MongoDB JIRA), and community posts. The sources are listed in [§10](#10-sources).
4. **Open work** was checked on GitHub. Two open PRs already cover some topics: #1742 (projection docs split, migration page, 12 runnable examples; merged 2026-10-20) and #1666 (config `Secret` docs). Issue #1744 lists seven projection rebuild bugs (sixteen engine problems as of 2026-10-20).

**Limits of the demand evidence.** The searches surfaced mostly official docs and blog posts, and very few Reddit or Stack Overflow threads. No independent community discussion of ZIO Blocks itself turned up. Demand ratings therefore measure "how much do users of comparable libraries need this task", not "how often do ZIO Blocks users ask for it". Treat them as informed judgments.

**Diátaxis definitions used** ([diataxis.fr](https://diataxis.fr/tutorials-how-to/)):

- **Tutorial:** a learning-oriented lesson for someone who is *studying*. It takes a newcomer by the hand through building something small but complete, with minimal choices and a guaranteed result. Titles read "Build …", "Your first …".
- **How-to guide:** a goal-oriented recipe for someone who is *at work* and already knows the basics. It addresses one real task and may branch on options. Titles start with "How to …", as Diátaxis recommends ([how-to guides](https://diataxis.fr/how-to-guides/)).

**How to read the tables.** Each row has an ID (`T-` for tutorials, `H-` for how-tos, `M-` for migration guides, `X-` for blocked topics), a priority with its score, a feasibility note and a demand link. "§" references point to sections of `docs/dev/research.md`.

## 2. Prioritization method

Each topic is scored from 1 (low) to 3 (high) on five criteria. The score is shown as `total (D/C/G/F/O)`.

| Code | Criterion | 3 means | 1 means |
|---|---|---|---|
| D | **User demand** | Comparable libraries lead their docs with this task, or users ask for it repeatedly | Little or no evidence of demand |
| C | **Module centrality** | Core module that many others depend on: schema, streams, sql, scope, config, HTTP | Leaf module with few users or no dependents |
| G | **Coverage gap** | No guide, and the reference does not answer the task | A guide or a task-oriented reference section already exists |
| F | **Feasibility today** | Writable now, with compiled mdoc snippets and existing examples | Needs a library change first |
| O | **Onboarding value** | Part of the "start here" path for newcomers | Only useful to experienced users |

**Feasibility 2** means "writable with caveats": the guide must include user-written glue code (and say so), targets Scala 3 only, needs a small docs-build change, or can use only APIs from the published 0.0.56 release in example projects (§9.5).

**Priority bands.**

| Priority | Rule | Meaning |
|---|---|---|
| **P0** | Score 13 or more | Write next |
| **P1** | Score 11 to 12 | Write soon |
| **P2** | Score 9 to 10 | Write later |
| **P3** | Score 8 or less, **or** F = 1 | Nice to have, or blocked until the library changes |

**Constraints that apply to every item** (§1.2, §9.5):

- Every install step must show both `zio-blocks-*` and the Scala 3.9+ name `zio-blocks-next-*` (plus `-experimental`). The earlier "3.7+" rule was wrong; see `docs/dev/issues.md`.
- sql, sql-zio, dataMigration, projection, datastar and htmx are Scala 3 only.
- `*-examples/` projects pin the published 0.0.56. A newer API can appear only in mdoc snippets until the next release.
- Telemetry needs JDK 21 or later on the JVM (the guide says 25 for `ScopedValue`). State it in the install step.
- New guides need a `docs/sidebars.js` entry in the `guides` sidebar. Also update the stale hand-maintained list at `docs/index.md:492-502`.

## 3. Recommended learning path

The ordered "start here" path below takes a newcomer from installing the library to a small working backend. Items marked *(exists)* are already published guides.

**Stage 1: Foundations (all Scala versions)**

1. **H-01** How to add ZIO Blocks to your build. It is a short how-to, but every tutorial links to it for the install step.
2. **T-01** Your first schema: describe a type once and round-trip it through JSON.
3. **T-02** Evolve your data from V1 to V2 with `Into`, `As` and `Migration`.

**Stage 2: The runtime blocks (all Scala versions)**

4. *(exists)* Compile-time resource safety with Scope (`guides/compile-time-resource-safety-with-scope`).
5. **T-05** Build a typed configuration loader.
6. *(exists)* Getting started with Async (`guides/async-getting-started`).
7. **T-06** Build your first streaming pipeline.

**Stage 3: Building applications**

8. **T-07** Build your first database repository on SQLite *(Scala 3)*.
9. **T-08** Describe a typed HTTP API with Endpoint.
10. **T-09** Render your first server-side HTML page.
11. **T-12** Instrument a program with logs, traces and metrics.

**Stage 4: Capstone**

12. **T-15** Build a small JSON backend from blocks *(Scala 3)*. It combines config, scope, sql, an HTTP adapter and telemetry.

**Electives** (any time after stage 1):

- Data: **T-03** optics, **T-04** many formats, *(exists)* the four-part Query DSL series (advanced).
- Web UI: **T-10** HTMX or **T-11** Datastar, after T-09.
- Concurrency: *(exists)* Getting started with Mux.
- Tooling: **T-13** Smithy to Scala with codegen.
- Event sourcing: **T-14** projection read models, after PR #1742 and issue #1744.

**Scala 2.13 readers** can follow the whole path except T-07, T-10, T-11, T-14 and T-15, which need Scala 3.

## 4. Tutorials backlog

Fifteen tutorials. Each one builds one small, complete thing.

| ID | Title | Module(s) | What the reader builds | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| T-01 | Your first schema: describe a type once and round-trip it through JSON | schema (+ chunk) | A `Book` catalog: `derives Schema`, `jsonCodec` encode and decode, a decode error read and fixed, a peek at `toDynamicValue` and `toJsonSchema` (the newcomer's eight operations, §3.2) | H-01 | **P0** · 15 (3/3/3/3/3) | Feasible; schema is on the mdoc classpath, all Scala versions. §9.3 notes the missing whole-library getting started | ZIO's own tutorial list leads with "How to Encode and Decode JSON Data" ([zio.dev/guides](https://zio.dev/guides/)); tapir tutorial 3 is "Using JSON bodies" ([tapir](https://tapir.softwaremill.com/en/latest/tutorials/02_openapi_docs.html)) | `docs/index.md:185-199`; `reference/schema/schema.md`; `built-in-codecs/json/json.md` |
| T-02 | Evolve your data: from V1 to V2 with `Into`, `As` and `Migration` | schema | A versioned user profile: a DTO mapped to a validated domain type with accumulated errors (`Into`), a reversible V1/V2 conversion (`As`), then a `Migration.newBuilder` with compile-time completeness checks (§3.2, scenario A8) | T-01 | **P0** · 14 (3/3/3/3/2) | Feasible. No typed `Migration` example exists yet (§3.7), so write one | The zio-schema migration system was a large bounty (zio-blocks [#519](https://github.com/zio/zio-blocks/issues/519)); chimney's docs center on DTO-to-domain "partial transformers" ([chimney](https://chimney.readthedocs.io/supported-transformations/)) | 10 unreferenced files in `schema-examples/.../as/` and `into/`; `MigrationBuildValidationSpec`; `schema-evolution/*.md` |
| T-03 | Navigate and update nested data with optics | schema | An inventory updater: `$(_.a.b)`, `.each`, `.when[B]`, `.atKey`, the `p"..."` path interpolator, `modify` and `modifyOrFail` | T-01 | **P1** · 11 (2/2/2/3/2) | Feasible. No standalone optics example exists (§3.7) | zio-blocks [#94](https://github.com/zio/zio-blocks/issues/94) "how do I integrate with Optics?" and [#111](https://github.com/zio/zio-blocks/issues/111); optics are benchmarked against monocle and quicklens (§3.9) | `optics.md` (1617 lines); `path-interpolator.md` |
| T-04 | One type, many formats: export a catalog as JSON, YAML, CSV, XML, MessagePack, TOON and Avro | schema + schema-yaml, -csv, -xml, -messagepack, -toon, -avro | A small exporter that writes the same records in seven formats with `derive(Format)` and reads them back, showing which formats need flat records (CSV) or the JVM (Avro) | T-01 | **P1** · 12 (2/2/3/3/2) | Feasible. Do not copy `format.md:59`: YAML and TOON are `BinaryFormat`. CSV supports flat records only | "One type, many formats" is the front-page pitch (scenario A1), yet no example uses a non-JSON format (§4) | `schema-examples/build.sbt` already declares the format dependencies; `built-in-codecs/*.md` |
| T-05 | Build a typed configuration loader | config, config-yaml (+ schema, maybe) | An app config read from defaults, a YAML file and environment variables, with every error reported at once, a provenance dump and redacted secrets (scenario B1) | T-01 | **P0** · 15 (3/3/3/3/3) | Feasible on JVM and JS. `fromYaml` takes a string, so the reader loads the file. Confirm config is in the 0.0.56 release before adding a `config-examples` project; otherwise use mdoc only | ZIO's tutorial list includes "How to Make a ZIO Application Configurable?" ([zio.dev/guides](https://zio.dev/guides/)); Scala 3 users still report PureConfig derivation pain ([Scala Users](https://users.scala-lang.org/t/scala-3-migration-pureconfig/12096), [alexn.org](https://alexn.org/blog/2025/10/30/scala-3-hocon-parsing/)) | `reference/config/*` (7 pages); `ConfigSourceCompositionSpec` "four-level chain" |
| T-06 | Build your first streaming pipeline | streams (+ scope, chunk) | A log analyser: read a file safely, parse lines, filter, window with `grouped`, aggregate into a `Sink`, recover from a bad line with `catchAll`, then run the same pipeline with an `*Async` terminal | Scope tutorial (optional) | **P0** · 15 (3/3/3/3/3) | Feasible, all Scala versions. Keep primitive-lane loops free of per-element sentinel checks (`AGENTS.md` sentinel policy) | fs2's getting-started example is exactly "read a file line by line, transform, write" ([fs2](https://git.rossabaker.com/typelevel/fs2/blame/branch/main/site/getstarted/example.md)); multi-part blog series on large-file streaming ([Livongo](https://techblog.livongo.com/using-scala-to-read-really-really-large-files-part-5-fs2-with-io-helpers/)) | 38 files in `streams-examples/` (`StreamBasicUsageExample`, `StreamResourceExample`, `StreamWindowingExample`, `StreamErrorHandlingExample`) |
| T-07 | Build your first database repository on SQLite | sql (+ schema, maybe) | A bookshelf: `Table.derived`, `createTable` DDL, `Repo.derived` CRUD, a hand-written `sql"..."` query, a transaction with rollback, `queryOne` returning `Maybe` (scenario D1) | T-01 | **P0** · 14 (3/3/3/2/3) | Scala 3 only. The sql core also builds for Scala.js, but `JdbcTransactor` is JVM-only, so the tutorial runs on the JVM. Verify `sqlite-jdbc` is on the docs classpath, else use `mdoc:compile-only`. No sql examples project exists | doobie's book starts "Connecting to a Database", "Selecting Data", "Parameterized Queries" ([doobie](https://typelevel.org/doobie/docs/03-Connecting.html)); Magnum, the closest API, leads with the same flow ([Magnum](https://index-dev.scala-lang.org/augustnagro/magnum)) | `docs/index.md:404-467`; `RepoIntegrationSpec`, `JdbcTransactorSQLiteSpec`; `reference/sql/*` |
| T-08 | Describe a typed HTTP API with Endpoint | endpoint, http-model (+ schema, mediatype) | A bookstore API: route patterns with typed path codecs, query and header inputs, JSON bodies, typed errors, bearer auth, a versioned prefix, and `RouteTree` lookups that resolve sample requests in-process | T-01 | **P1** · 12 (3/2/3/2/2) | Writable, but it must say that zio-blocks has no server, client or OpenAPI interpreter (§8, §9.5 #2-3). Do not repeat the false OpenAPI claims (§9.6) | tapir's tutorial track covers endpoints, JSON, errors and multiple inputs and outputs ([tapir](https://tapir.softwaremill.com/en/latest/tutorials/02_openapi_docs.html)); zio-http's Endpoint API uses the same vocabulary ([zio-http](https://zio.dev/zio-http/reference/endpoint)) | `endpoint-examples/.../CompleteApiDefinition.scala`; `reference/endpoint/*` |
| T-09 | Render your first server-side HTML page | html (+ schema) | A product page written to a file and opened in a browser: typed elements, context-aware escaping of user input, typed CSS, JSON state embedded with `ToJs`, and a `select(...)` check on the result | T-01 (for `ToJs`) | **P1** · 12 (2/2/3/3/2) | Feasible without a server. Use the typed content-model factories (three breaking changes, §8.4) | html is the #1 gap in `undocumented-report.md`; users find Scala SSR with Scalatags hard to start ([Frontend Mentor](https://frontendmentor.io/solutions/tailwind-scala-ssr-scalatags-and-cask-deployed-with-docker-image-UPzyFXyf_L)) | `reference/html.md` dashboard example |
| T-10 | Add interactivity to a page with HTMX | htmx, html, http-model | A debounced live search with category filters: full page on first load, a fragment when `HxRequest` is present | T-09, H-35 | **P2** · 9 (2/1/3/2/1) | Scala 3 only. Needs the JDK-server glue from H-35. The htmx examples pin 0.0.56, which may predate html's typed content models (unverified). Check which htmx version (2 or 4) the attributes target | Scala HTMX guides exist only for other stacks ([Tyrian SSR](https://tyrian.indigoengine.io/02-guides/ssr/)); htmx 4.0 shipped August 2026 ([flaviocopes](https://flaviocopes.com/htmx-introduction/)) | `zio-blocks-htmx-examples/.../CompleteExample.scala`; `reference/htmx/*` |
| T-11 | Build a live counter and dashboard with Datastar | datastar, html, http-model, streams | A counter and a polling dashboard: signals, `dataOn`, `patchElements` and `patchSignals` sent as Server-Sent Events | T-09, H-35, H-41 | **P2** · 9 (2/1/3/2/1) | Scala 3 only. There is no `readSignals` or script helper (§8.5), so the tutorial decodes signals with the schema JSON codec and adds the script tag by hand. It must label this as glue | Every official Datastar SDK leads with reading signals and streaming patches ([data-star.dev](https://data-star.dev/guide/backend_requests), [Stario](https://stario.dev/docs/how-tos/datastar-sdk)) | `reference/datastar/index.md` "A Counter, End to End". Do not use the orphan `reference/datastar.md` |
| T-12 | Instrument a program with logs, traces and metrics | telemetry | A batch job with structured logs, nested spans, a request counter and a latency histogram, switched to JSON logs, plus a test that asserts on `trace.collectedSpans` | H-01 | **P1** · 11 (3/2/2/2/2) | JDK 21+ (`-release 21`). Export is out of scope (blocked, X-01). The existing telemetry guide is a deep dive; this is the beginner lesson it lacks (§9.3) | ZIO's tutorial list includes "How to Enable Logging" and "How to Monitor … Using ZIO's Built-in Metric System" ([zio.dev/guides](https://zio.dev/guides/)); otel4s leads with a runnable Jaeger example ([otel4s](https://typelevel.org/otel4s/examples/jaeger-docker/)) | `guides/telemetry-guide.md` Parts 2-4; `reference/telemetry/*` |
| T-13 | Generate Scala code from a Smithy model | smithy, codegen | A small generator: parse a `.smithy` file, map structures and unions to `CaseClass` and `SealedTrait` IR, and emit both Scala 3 and Scala 2 sources | none | **P2** · 9 (2/1/3/2/1) | Glue code: no production code connects smithy and codegen (§4.10, §4.11; scenario A6). The current example builds strings with `println` | smithy4s's whole onboarding is model-to-code via its sbt plugin ([smithy4s](https://index.scala-lang.org/disneystreaming/smithy4s/artifacts/smithy4s-sbt-codegen/0.18.4)) | `smithy-examples/` (4 files; stale run commands); `reference/codegen/examples.md:485-516` pseudocode |
| T-14 | Build an event-sourced read model with projections | projection (+ schema, sql, ZIO) | A per-user profile view fed from an event store, queried by ID, then tested with `TestEngine` | T-01, ZIO basics | **P3** · 8 (2/1/2/2/1) | Scala 3, JVM, ZIO. Wait for PR #1742 (quick-start example, migration page) and avoid rebuild flows until issue [#1744](https://github.com/zio/zio-blocks/issues/1744) is fixed | Pekko Projection's getting-started guide is its main entry point ([Pekko](https://pekko.apache.org/docs/pekko-projection/1.1/getting-started/index.html)) | `projection-examples/` (in-memory only today; PR #1742 adds 12 examples) |
| T-15 | Build a small JSON backend from blocks | config, scope, sql, http-model, endpoint, telemetry | A notes service: config loaded at startup, dependencies wired with `Wire`, a SQLite repository, routes served through the H-35 JDK adapter, and request logging | T-01, T-05, T-07, T-08, H-35 | **P1** · 12 (2/3/3/2/2) | Scala 3 (sql). The HTTP layer is user glue (§9.5 #2). Write it last, after the module tutorials exist | The "building blocks for any stack" pitch on the public site ([zio.dev/zio-blocks](https://zio.dev/zio-blocks/)); ZIO's quickstart list leads with "Building a RESTful Web Service" ([zio.dev/guides](https://zio.dev/guides/)) | `scope-examples/.../LayeredWebServiceExample.scala`; `ConfigSpec` `Config.wire` tests |

## 5. How-to guides backlog

Fifty-one how-to guides, grouped by area. Within each group, rows are ordered by priority.

### 5.1 Getting set up

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-01 | How to add ZIO Blocks to your build | all | Choose modules; write sbt and scala-cli coordinates for Scala 2.13, 3.3 LTS and 3.7+ (`zio-blocks-next-*`); JVM vs Scala.js; JDK 21+ for telemetry; which modules have third-party deps | none | **P0** · 15 (3/3/3/3/3) | Feasible. Only `endpoint/index.md` mentions the `-next` names today (§1.2) | ZIO's guides open with "Hello World" and "Run Our First ZIO Project" pages ([zio.dev/guides](https://zio.dev/guides/)); Scaladex lists both artifact families ([Scaladex](https://index-dev.scala-lang.org/zio/zio-blocks)) | `project/BuildHelper.scala:169-176`; `docs/index.md:24-52` |

### 5.2 Schema and formats

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-02 | How to customize JSON field names, ADT encoding and optional fields | schema | Snake-case names, a discriminator field instead of a wrapper key, transient fields, aliases, how `Option` and defaults are written | T-01 | **P0** · 13 (3/3/2/3/2) | Feasible. Uses `NameMapper`, `DiscriminatorKind` and the `rename`/`transient`/`alias`/`discriminator`/`fieldNaming` modifiers (§3.2) | Recurring questions: zio-json [#1301](https://github.com/zio/zio-json/issues/1301) (ADT encoding), zio-schema [#523](https://github.com/zio/zio-schema/issues/523) (ADT decoding) and [#447](https://github.com/zio/zio-schema/issues/447) (maps); circe "serialize without class name" ([SO mirror](https://dev.library.kiwix.org/content/stackoverflow_en_nopic_2021-08/questions/52594416/scala-circe-case-class-serialization-without-class-name)) | `json/json-config.md`; `modifier.md` |
| H-03 | How to override the codec for one type everywhere | schema (+ any format) | Encode `LocalDate` as `dd/MM/yyyy` (or a money type as a string) in every derived codec, for JSON and other formats | T-01 | **P0** · 13 (3/3/3/3/1) | Feasible. `Deriver.withInstance` / `DerivationBuilder.instance` work for every format (#1348) | "Custom `LocalDate` format" is a classic circe question ([CoddyKit](https://www.coddykit.com/fr/courses/scala/custom-codecs-9564213)); zio-json "Better ISO-8601 support" [#1036](https://github.com/zio/zio-json/issues/1036) | Unreferenced `customcodec/CustomCodecOverrideExample.scala`, `deriveroverrides/CompleteExample.scala`; `DeriverWithInstanceCsvSpec` |
| H-04 | How to define validated newtypes and collect every validation error | schema | Wrap primitives in validated types with `Schema.transform`, attach `Validation` constraints, report all failures through `SchemaError` | T-01 | **P1** · 12 (2/3/3/3/1) | Feasible. `validation.md` has 0 mdoc blocks; write compiled snippets | chimney recommends "parse, don't validate" with newtypes and partial transformers ([chimney troubleshooting](https://chimney.readthedocs.io/en/stable/troubleshooting/)); zio-blocks [#193](https://github.com/zio/zio-blocks/issues/193) newtypes | `schemaerror/SchemaErrorExample.scala`; `into/IntoErrorAccumulationExample.scala` |
| H-05 | How to generate JSON Schema from a type and validate payloads against it | schema | Emit draft 2020-12 JSON Schema for an API contract; load an external schema with `Schema.fromJsonSchema`; validate an untyped payload | T-01 | **P1** · 11 (2/2/3/3/1) | Feasible. No JSON Schema example exists (§3.7). The official test suite passes (§3.4) | Several Scala libraries exist only for this ([scala-jsonschema](https://index-dev.scala-lang.org/andyglow/scala-jsonschema)); tapir documents it as a separate how-to ([tapir](https://tapir.softwaremill.com/en/latest/docs/json-schema.html)) | `json/json-schema.md`; `dynamicschema/DynamicSchemaValidationExample.scala` |
| H-06 | How to read and modify JSON without case classes | schema | Parse into the `Json` ADT, build with `json"..."`, navigate with `JsonSelection`, convert to and from `DynamicValue` | T-01 | **P2** · 10 (2/2/2/3/1) | Feasible | circe `Json` cursors and ujson are standard tools (§3.9) | `json/json.md`; `json/json-selection.md`; `dynamic-value.md` |
| H-07 | How to compute, store and replay changes with diff, Patch and JSON Patch | schema | Diff two values, serialize the patch, apply it later, choose a `PatchMode`; produce RFC 6902-style JSON Patches | T-01 | **P2** · 10 (1/2/3/3/1) | Feasible. `patch.md` has 0 mdoc blocks | zio-schema [#81](https://github.com/zio/zio-schema/issues/81) "Patch value with Diff"; diffson is the comparable library (§3.9) | `jsonpatch/CompleteJsonPatchExample.scala` (310 lines); `json-patch.md`, `json-differ.md` |
| H-08 | How to derive your own type class from a Schema | schema (+ markdown) | Write a `Deriver` for a `Show` or test-data generator; add per-type overrides | T-01 | **P1** · 11 (2/3/2/3/1) | Feasible. Different angle from the 2005-line reference: one task, start to finish | zio-blocks [#94](https://github.com/zio/zio-blocks/issues/94) "as a library developer, how do I integrate…", [#836](https://github.com/zio/zio-blocks/issues/836) derivation docs | `typeclassderivation/DeriveShowExample.scala`, `DeriveGenExample.scala`; `type-class-derivation.md` |
| H-09 | How to build a schema registry with DynamicSchema | schema (+ typeid) | Store a `DynamicSchema` as JSON, load it later, check data against it, rebind it to a Scala type with a `BindingResolver` | T-01 | **P2** · 10 (2/1/3/3/1) | Feasible | Scala users build registry glue for Avro and Kafka ([registravka4s](https://index-dev.scala-lang.org/maxstreese/registravka4s), [kaa](https://scaladex.scala-lang.org/davideicardi/kaa)) | Unreferenced `bindingresolvers/BindingResolverSchemaRegistryExample.scala`; `dynamicschema/*` |
| H-10 | How to use Scala 3 named tuples with Schema | schema | Derive schemas, codecs and optics for named tuples, including more than 22 fields | T-01, T-03 | **P2** · 9 (2/1/3/2/1) | Scala 3.7+ only. Evidence is tests only (§4.12); confirm against a release | zio-blocks [#213](https://github.com/zio/zio-blocks/issues/213); few JSON libraries support named tuples ([tupson](https://index-dev.scala-lang.org/sake92/tupson)) | `scalaNextTests/.../SchemaVersionSpecificScalaNextSpec.scala` |
| H-11 | How to read and write CSV and TSV files | schema-csv (+ streams) | Read a file with a header into records, report row and column errors, write CSV back; handle TSV and large files | T-01 | **P1** · 11 (3/2/3/2/1) | Flat records only. Derived codecs ignore `CsvConfig`, so TSV needs the low-level `CsvReader` and `CsvWriter` (§9.5 #9). No streams integration: large files need user glue. Do not copy `csv.md:130-147` | Two of the five Typelevel toolkit recipes are CSV read and write ([toolkit](https://typelevel.org/toolkit/examples.html)) | `built-in-codecs/csv.md`; `CsvWeatherDataSpec`, `CsvIntegrationSpec` |
| H-12 | How to send compact data to an LLM with TOON and parse the reply | schema-toon | Encode records as tabular TOON for a prompt, tune `ArrayFormat` and delimiter, decode model output back into case classes | T-01 | **P1** · 11 (3/1/3/3/1) | Feasible. This is the module's headline use case, and no guide covers it (§4.6) | TOON's token savings are widely covered, with caveats on data shape ([InfoQ](https://www.infoq.com/news/2025/11/toon-reduce-llm-cost-tokens), [Analytics Vidhya](https://www.analyticsvidhya.com/blog/2025/11/toon-token-oriented-object-notation/)) | `built-in-codecs/toon.md` (1050 lines); `schema-toon/README.md` |
| H-13 | How to encode Kafka event payloads with Avro | schema-avro | Emit `.avsc`, encode and decode records, and know the interop caveats: `Instant` as a record, `UUID` as `fixed(16)` | T-01 | **P2** · 9 (2/1/3/2/1) | JVM only. No container files, reader/writer schema resolution or Schema Registry (§4.1); registry framing is user glue (see X-09) | Many Scala libraries exist just to join case-class Avro with a registry ([registravka4s](https://index-dev.scala-lang.org/maxstreese/registravka4s), [kaa](https://scaladex.scala-lang.org/davideicardi/kaa)) | `built-in-codecs/avro.md` Patterns 1-4 |
| H-14 | How to store case classes in MongoDB with schema-bson | schema-bson | Choose the ADT encoding, use native `ObjectId` ids, convert to `BsonDocument` for the Java driver | T-01 | **P2** · 9 (2/1/3/2/1) | JVM only. No `CodecProvider` bridge: the driver step is user glue (§4.2). README cites the wrong bson version | The official Scala driver macros fail on some sealed traits ([JAVA-3545](https://jira.mongodb.org/browse/JAVA-3545), [JAVA-4966](https://jira.mongodb.com/browse/JAVA-4966)); zio-blocks [#1334](https://github.com/zio/zio-blocks/issues/1334) | `built-in-codecs/bson.md`; `BsonCodecObjectIdSpec`, `BsonCodecJsonSpec` |
| H-15 | How to read and write YAML documents | schema-yaml | Decode typed YAML, generate YAML (Kubernetes or CI files), use the `yaml"..."` interpolator, convert YAML to and from JSON | T-01 | **P2** · 10 (2/2/3/2/1) | `yaml.md` has 0 mdoc blocks and never mentions the interpolator or `YamlReader`/`YamlWriter`. Anchors and multi-document streams are probably unsupported (unverified, §4.8): test before writing | scala-yaml and circe-yaml are popular comparables (§4.8) | `YamlJsonInteropSpec`; `built-in-codecs/yaml.md` |
| H-16 | How to exchange data with non-Scala services over MessagePack or Thrift | schema-messagepack, schema-thrift | Match another language's MessagePack conventions; keep Thrift field IDs stable when case-class fields move | T-01 | **P3** · 8 (1/1/3/2/1) | Thrift field IDs are positional, with no annotation (§4.5). Interop conventions exist only in the MessagePack README | Scrooge and msgpack-java are the comparables (§9.7); no direct demand evidence found | `messagepack.md`, `thrift.md`, module READMEs |

### 5.3 Data: SQL, migration and projections

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-17 | How to connect to PostgreSQL with a connection pool | sql | Build a `JdbcTransactor.postgres` from a HikariCP `DataSource`, run on virtual threads, choose isolation levels | T-07 | **P1** · 12 (3/3/2/2/2) | Scala 3. Needs PostgreSQL; use `mdoc:compile-only`. `sql-transactions.md` has the Hikari recipe as prose with 0 mdoc blocks; this how-to is the setup angle | Magnum's setup is "driver + HikariCP + transactor" ([Magnum](https://index-dev.scala-lang.org/augustnagro/magnum)); doobie's "Connecting to a Database" chapter ([doobie](https://typelevel.org/doobie/docs/03-Connecting.html)) | `guides/sql-transactions.md:189-268`; `reference/sql/transactor.md` |
| H-18 | How to upsert rows and paginate with keyset cursors | sql | `insertOrUpdate`, `Upsert.insertDoNothing` and `insertDoUpdate`; walk a large table with `pageAfter` and `keysetAfter` | T-07 | **P1** · 12 (2/3/3/3/1) | Scala 3. Single-column cursors only (composite deferred, `Frag.scala:156`). Moves the misplaced reference out of the Query DSL Part 2 tail | Magnum documents seek pagination as a core feature ([Magnum](https://index-dev.scala-lang.org/augustnagro/magnum)) | `RepoUpsertSpec`, `RepoKeysetSpec`; `guides/query-dsl-sql.md:729-1121` |
| H-19 | How to write joins and inspect the generated SQL | sql | `SqlQuery` with `Rel.manyToOne`/`oneToMany`, `explain(dialect)`, and compile-time `Dump` files via `-Dzib.sql.dumpDir` | T-07 | **P1** · 11 (2/2/3/3/1) | Scala 3. No reference page exists for `SqlQuery`, `Rel` or `Dump` (§7.1) | Joins are a core chapter in doobie, Quill and Slick docs; see the library comparison ([pistack](https://www.pistack.xyz/posts/2026-09-19-scala-database-libraries-doobie-slick-quill-skunk-comparison/)) | `sql/shared/.../JoinsDumpExample.scala`; `ExplainDumpGoldenSpec` |
| H-20 | How to stream large query results | sql, streams, chunk | Export a big table with `queryStream`/`queryChunked` into a zio-blocks `Stream`, then into a file or CSV | T-07, T-06 | **P1** · 12 (2/3/3/3/1) | Scala 3. The sql index page does not mention the streams integration (§7.1) | Streamed export is a benchmarked scenario (`sql-benchmarks`); doobie and Skunk stream results through fs2 ([Rock the JVM](https://blog.rockthejvm.com/skunk-complete-guide/)) | `reference/sql/frag.md:182-215`; `Frag.scala:355-399` |
| H-21 | How to use the sql module from a ZIO application | sql, sql-zio | Provide a `Transactor` as a ZLayer, run effectful bodies with `transactZIO`, choose between `transact` and `transactZIO` | T-07, ZIO basics | **P1** · 11 (3/2/3/2/1) | Scala 3, JVM. **Add sql-zio to the docs mdoc classpath first** (§9.5 #4). No `ZStream` bridge exists | magnum-zio, zio-jdbc and Quill-ZIO all provide this (§7.2); ZIO's own guides cover framework integration ([zio.dev/guides](https://zio.dev/guides/)) | `reference/sql-zio.md`, `sql/transactor-zio.md` (0 mdoc); `JdbcTransactorLayersSpec`, `TransactorZIOSpec` |
| H-22 | How to map custom column types | sql | Opaque types via `As`, Scala 3 enums, `DbCodec.jsonb` columns, PostgreSQL arrays with `PgCodec`, time zones for `Instant` | T-07 | **P2** · 10 (2/2/2/3/1) | Scala 3. `PgCodec` is undocumented (§7.1) | Magnum documents enum and custom-type mapping up front ([Magnum](https://index-dev.scala-lang.org/augustnagro/magnum)) | `DbCodecOpaqueSpec`, `DbCodecSpec`, `PgCodecSpec`, `JdbcInstantTimezoneSpec`; `reference/sql/db-codec.md` |
| H-23 | How to migrate table rows to a new version online | dataMigration (+ sql, schema) | Backfill a new column with `SmallMigrator`; for a large PostgreSQL table, use `LargeMigrator` with a shadow table, capture triggers, fence, drain and swap; preview SQL first | T-02, T-07 | **P2** · 9 (2/1/3/2/1) | Scala 3, JVM. **Needs dataMigration on the mdoc classpath** (§9.5 #4). Do not copy the "Full Example" in `data-migration.md` (§9.6). SQLite cannot use shadow tables | Online shadow-table migrations are a well-known pain ([pg-osc](https://www.shayon.dev/post/2022/47/pg-osc-zero-downtime-schema-changes-in-postgres), [InfoQ](https://www.infoq.com/articles/shadow-table-strategy-data-migration)) | `PostgresMigrationIntegrationSpec.scala:145-231`, `MigrationIntegrationSpec`, `MigrationPreviewSpec`; ADR `2026-07-18-data-migration.md` |

Production projections on SQLite are listed as blocked (X-07). Projection rebuilds, renames and testing are covered by the open PR #1742 (`reference/projection/migration.md`, `testing.md`), so they get no separate how-to.

### 5.4 Streams and async

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-24 | How to stream files and sockets without leaking resources | streams, scope | Choose between `fromAcquireRelease` and `fromResource`; read NIO channels; pull by hand with JVM `start(implicit scope)` | T-06 | **P1** · 12 (2/3/2/3/2) | Feasible. No example uses `Stream.fromResource` yet (§6.7) | Resource-safe file reading is fs2's headline example ([fs2](https://git.rossabaker.com/typelevel/fs2/blame/branch/main/site/getstarted/example.md)) | `StreamResourceExample`, `ManualPullUsingStart`, `reader/ReaderCompositionExample`; `Stream.scala:3158` |
| H-25 | How to process a stream in parallel with bounded concurrency | streams (+ ringbuffer) | `mapPar`, `flatMapPar`, `mergeAll`, `buffer`: pick a parallelism level, keep or drop ordering, merge several sources | T-06 | **P1** · 12 (3/3/2/3/1) | Feasible | Concurrency combinators are a selling point of Ox Flow and fs2 ([SoftwareMill](https://softwaremill.com/direct-style-concurrent-streaming/)) | `SegmentedLedgerIngestWalkthrough`, `MapParAsyncExample`, `FlatMapParAsyncChildrenExample` |
| H-26 | How to run streams without blocking (async terminals and Scala.js) | streams, async | Use the `*Async` terminals that return `Async[Either[E, Z]]`; never block on Scala.js; read browser `ReadableStream`s | T-06, Async tutorial | **P2** · 10 (2/2/2/3/1) | Feasible | ZIO's integration guides include "Build and Run … on Scala.js" ([zio.dev/guides](https://zio.dev/guides/)) | `execution-and-compatibility/async-execution.md`, `platform-differences.md`; `StreamAsync*Example` |
| H-27 | How to process binary data without boxing | streams (JVM NIO) | Read `ByteBuffer`s and channels on primitive lanes; guard against sentinel-valued data | T-06 | **P2** · 9 (1/2/2/3/1) | Feasible. Must follow the sentinel performance policy (`AGENTS.md`): show the post-loop `lastReadWasEOF` guard, not per-element checks | Niche; the scodec and NIO crowd (§2.1) | `NioBinaryTelemetryWalkthrough`, `SinkSentinelGuardExample`, `SinkSentinelLimitation`; `zero-boxing.md` |
| H-28 | How to wrap callback and Future-based APIs in Async | async | Bridge callbacks with `Async.promise`/`Completer`, convert `Future`, `CompletionStage` and JS `Promise`, cancel running work, write a custom `Pollable` | Async tutorial | **P1** · 12 (3/2/3/3/1) | Feasible. The existing async tutorial skips interop (§9.3). Also serves as the "coming from `Future`" guide | ZIO publishes "How to Interop with Future", "… with Java", "… with JavaScript" ([zio.dev/guides](https://zio.dev/guides/)) | `async-examples/.../AsyncShowcaseExample.scala`, `gettingstarted/CallbackBridge.scala`; `AsyncInterop.scala` |
| H-29 | How to run a mux over a real socket | mux (+ ringbuffer) | Frame several logical streams over one TCP connection: `offerInbound`/`takeOutbound`, half-close, `QueueFull` back-pressure | Mux tutorial | **P3** · 8 (1/1/3/2/1) | User glue: no transport exists (§6.6). Resolve the "lock-based" contradiction in `mux.mdx` first (§9.6) | Analogies only (yamux, Netty HTTP/2) | `mux-examples/*`; `guides/getting-started-with-mux.md` |

### 5.5 Resources, configuration and context

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-30 | How to feed configuration into a Wire dependency graph | config, scope, context | Use `Config.wire[A]` with `Resource.from[App](...)` inside `Scope.global.scoped`, so services receive typed config (scenario B2) | T-05, Scope tutorial | **P1** · 12 (2/3/2/3/2) | Feasible. Do not copy `Wire.make[...]` from `context.md:484` (§9.6) | ZIO's DI docs point out that DI is optional and list macwire-style alternatives ([zio.dev DI](https://zio.dev/reference/di/index.md)) | `ConfigSpec.scala:22,81-92`; `wire.md:507`; `config/index.md` "Wiring Config into a Dependency Graph" |
| H-31 | How to fix common Scope and Wire compile errors | scope, context | A catalog of the E1-E17 macro errors: what each means and the usual fix (missing `Unscoped`, escaping `$[A]`, missing or ambiguous wires, cycles) | Scope tutorial | **P1** · 12 (2/3/3/3/1) | Feasible. No error catalog exists (§6.1) | Macro-heavy libraries draw "how do I silence or understand this diagnostic" questions (zio-blocks [#316](https://github.com/zio/zio-blocks/issues/316)) | `WireMacroErrorSpec`, `ErrorMessagesSpec`; `CircularDependencyDemoExample` |
| H-32 | How to roll out a feature gradually with flags | config (flags, rollout) | Declare `StaticFlag` and `DynamicFlag`, write a percentage or region `Rollout`, reload on a timer, catch typos with near-miss warnings, reset flags in tests with `Registry.clear` | T-05 | **P1** · 11 (2/2/3/3/1) | Feasible. Reload is manual (no polling); register flags before first touch | No Scala feature-flag library or OpenFeature Scala SDK turned up in searches; sticky percentage bucketing is the standard pattern ([FeatBit](https://home.featbit.co/blogs/Conception-Percentage-Rollout-Modern-Software-Development)) | `reference/config/flags.md`, `rollout.md`; `DynamicFlagSpec`, `RolloutSpec`, `FlagDumpSpec` |
| H-33 | How to keep secrets out of logs and config dumps | config | Mark fields `Secret` or `Sensitive`, print a redacted `ProvenanceMap.dump`, make sure error messages don't leak values | T-05 | **P2** · 10 (2/2/2/3/1) | Feasible. Coordinate with open PR #1666 (Secret docs). Secrets-dir sources are planned, not built (X-12) | ciris is built around `Secret` handling ([Typelevel blog](https://typelevel.org/blog/ciris.html)) | `reference/config/errors.md`; `FlagDumpSpec` |
| H-34 | How to load configuration files on the JVM and in the browser | config-hocon, config-json, config-yaml | HOCON files with includes, substitutions and sandboxing (`allowedBase`, `maxFileBytes`); JSON and YAML from strings; what works on Scala.js | T-05 | **P2** · 10 (2/2/2/3/1) | Feasible. File loading is JVM-only for HOCON; the JSON and YAML adapters have no file loader. Call out the lenient "malformed YAML as scalar" behaviour (§5.4) | Users moving off Typesafe Config look for HOCON parsers ([sconfig](https://index-dev.scala-lang.org/ekrich/sconfig), [alexn.org](https://alexn.org/blog/2025/10/30/scala-3-hocon-parsing/)) | `reference/config/formats.md`; `HoconConfigSourceSpec`, `JsonConfigSourceSpec`, `YamlConfigSourceSpec` |

### 5.6 HTTP, endpoint and web UI

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-35 | How to serve http-model requests with the JDK HTTP server | http-model, endpoint, mediatype | Write a small adapter from `com.sun.net.httpserver` to `Request`/`Response`, route with `RouteTree.get` and `RoutePattern.decode`, set `ContentType` | T-08 | **P1** · 12 (2/3/3/2/2) | User glue: no server or adapter exists (§9.5 #2). Many later items depend on it | Users write plain JDK or minimal servers to learn Scala web basics ([dev.to](https://dev.to/yet_anotherdev/how-to-write-a-basic-web-server-with-scala-3-90j)) | `http-model-examples/*`; `RouteTree.scala:56`, `RoutePattern.scala:50` (scenarios E2, E3) |
| H-36 | How to validate query parameters and headers and answer 400 | http-model-schema (+ schema) | Read typed params with `query[T]` and `header[T]`, derive `QueryCodec` for paging and filter objects, turn `Missing` and `Malformed` into a 400 response | T-01, H-35 | **P1** · 11 (2/2/3/3/1) | Feasible. The module builds for 2.13 too, despite `schema.md:68` (§9.6) | tapir and http4s give query and header decoding their own docs pages (§8.2) | `http-model/schema.md`, `schema-codecs.md`; `QueryCodecSpec`, `HeaderCodecSpec` |
| H-37 | How to issue and verify JWTs | jwt | Sign and verify with HMAC and with RSA, EC or EdDSA keys (PKCS8 and X.509 DER), check `iss`, `aud`, `exp` with clock skew, fix the clock in tests, set hostile-token limits | none | **P1** · 12 (3/2/3/3/1) | Feasible. PEM must be converted to DER by hand; no JWK/JWKS (X-13). Fix `jwt.md` first (§9.6). The module is new and has no MiMa baseline | RS256-with-PEM verification guides are everywhere ([SSOJet](https://ssojet.com/jwt-validation/validate-jwt-using-rs256-in-scala)); providers point users at JWKS ([Auth0 community](https://community.auth0.com/t/where-is-the-auth0-public-key-to-be-used-in-jwt-io-to-verify-the-signature-of-a-rs256-token/8455/2)) | `JwtJvmSpec`, `JwtJsSecuritySpec`; `reference/jwt.md` (partial) |
| H-38 | How to protect an endpoint with JWT bearer tokens | endpoint, jwt, http-model (+ config) | Declare `AuthType.Bearer`, extract the token in the H-35 adapter, verify it with `Jwt.decode`, choose 401 or 404, load keys from config | H-35, H-37 | **P1** · 11 (3/2/3/2/1) | Glue: `AuthType.Bearer` never verifies a JWT (§5.7, scenario E5) | Auth is a core chapter in tapir and zio-http docs ([zio-http](https://zio.dev/zio-http/concepts/endpoint)) | `endpoint-examples/.../AuthTypePatterns.scala`; `reference/endpoint/auth-type.md` |
| H-39 | How to write an OpenAPI document and export it as JSON or YAML | openapi, schema, schema-yaml (+ markdown) | Author an `OpenAPI` value in Scala, generate component schemas with `toOpenAPISchema` and `OpenAPIGen`, export through `encodeValue(api)` to `Json` and then YAML, serve it to Swagger UI | T-01 | **P1** · 11 (3/2/3/2/1) | Byte and string encoding throw (`???`, §9.5 #10): use `encodeValue` to `Json`. YAML export is glue (scenario A5). Generating from Endpoints is blocked (X-02) | tapir's tutorial #2 is "Auto-generating OpenAPI docs" ([tapir](https://tapir.softwaremill.com/en/latest/tutorials/02_openapi_docs.html)); zio-http shows `OpenAPIGen` with Swagger UI ([zio-http](https://zio.dev/zio-http/reference/endpoint)) | `reference/openapi.md` (stale intro signature); `SchemaToOpenAPISpec`; petstore fixtures |
| H-40 | How to return HTMX fragments, full pages and response headers | htmx, html, http-model | Branch on `HxRequest`, send out-of-band swaps, redirect or trigger client events with `HxRedirect`, `HxTriggerHeader` | T-10 | **P2** · 9 (2/1/3/2/1) | Scala 3. Needs the H-35 adapter | django-htmx's header helpers are the analogue; htmx has no Scala server guide (§8.6) | `reference/htmx/response-headers.md:208-221` |
| H-41 | How to send Server-Sent Events with http-model | http-model, streams | Build a `text/event-stream` `Response` from `Body.fromStream` and `ServerSentEvent` | H-35, T-06 | **P2** · 10 (2/2/3/2/1) | Needs the H-35 adapter. Fix the wrong `zio-http-model` artifact name in the source pages (§9.6) | SSE streaming is what every Datastar SDK does on the server ([data-star.dev](https://data-star.dev/guide/backend_requests)) | `reference/http-model/server-sent-event.md`; `ServerSentEventSpec`; `Body.scala:153` |

### 5.7 Observability

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-42 | How to configure log output | telemetry | JSON logs on stderr, per-package levels with `setMinSeverity`, rate-limited logging (`xxxEvery`, `xxxAtMost`), a custom or rotating file `LogWriter` | T-12 | **P1** · 11 (3/2/2/3/1) | Feasible on JDK 21+. `FileLogWriter` is private, so the file writer is user code (§5.5) | ZIO's guides include "How to Enable Logging" and "How to Create a Custom Logger" ([zio.dev/guides](https://zio.dev/guides/)) | `guides/telemetry-guide.md` Part 2; `reference/telemetry/logging/*` |
| H-43 | How to correlate logs and traces across services | telemetry, otel, context, http-model | Share `ContextStorage` so logs carry span IDs; inject and extract W3C or B3 headers through http-model `Headers`; carry span context in `Context` with `OtelContext` | T-12, H-35 | **P2** · 9 (2/2/2/2/1) | JVM only for otel. Header glue for http-model (scenario F2). Do not link the missing ADR (§9.6) | Distributed tracing walkthroughs are standard in otel4s docs ([otel4s OTLP](https://typelevel.org/otel4s-sdk/guides/otlp.html)) | `telemetry-guide.md:569`, `:971-1012`; `W3CTraceContextSpec`, `B3PropagatorSpec`, `OtelContextSpec` |

Exporting to a collector is blocked (X-01).

### 5.8 Codegen, OpenAPI and Smithy

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-44 | How to generate Scala types from an OpenAPI spec at build time | openapi, codegen | Parse a spec with the openapi codecs, map component schemas to codegen IR, emit sources from an sbt `sourceGenerators` task | T-13 | **P2** · 9 (2/1/3/2/1) | Glue (scenario A7). Spec parsing has weak test evidence (§4.9): verify on petstore first | guardrail and OpenAPI Generator serve this need; guardrail reportedly lacks Scala 3 support ([Zuplo](https://zuplo.com/blog/2025/04/11/api-first-development-in-scala), [guardrail](https://github.com/twilio/guardrail/)) | `reference/codegen/examples.md:497-512` pseudocode; openapi petstore fixture |

### 5.9 Interop with other libraries

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-45 | How to convert between Chunk and other collections | chunk | Convert to and from `zio.Chunk`, `fs2.Chunk`, `Array`, `ByteBuffer` and `Iterable` without needless copies | none | **P2** · 10 (1/2/3/3/1) | Conversions to ZIO and fs2 types are user code | `zio.Chunk` and `fs2.Chunk` users are the target audience (§2.1) | `reference/chunk.md`; `Chunk.fromArray`, `fromByteBuffer` |
| H-46 | How to connect zio-blocks streams to ZIO Streams or fs2 | streams (+ sql) | Turn a `Stream` (for example `queryStream`) into a `ZStream` or fs2 stream and back, keeping resource safety | T-06 | **P1** · 11 (2/3/3/2/1) | Glue (scenario D7). Must handle blocking pulls on the right thread pool | ZIO's guides include "How to Interop with Cats Effect" ([zio.dev/guides](https://zio.dev/guides/)) | `Stream` `start` and `Reader` APIs (§6.7) |

### 5.10 Testing

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-47 | How to test database code against SQLite | sql | In-memory or temp-file SQLite per test, schema from `createTable`, rollback-only transactions, fixtures | T-07 | **P1** · 11 (3/2/3/2/1) | Scala 3. PostgreSQL-only features (shadow tables, `PgCodec`) cannot be tested this way | doobie's book has a dedicated "Unit Testing" chapter ([doobie](https://typelevel.org/doobie/docs/index.html)) | `JdbcTransactorSQLiteSpec`, `RepoIntegrationSpec` |
| H-48 | How to test code that logs, traces and records metrics | telemetry | Assert on `trace.collectedSpans`, an `InMemorySpanProcessor` and captured log records; reset state between tests | T-12 | **P2** · 10 (2/2/2/3/1) | Feasible | Testing with in-memory telemetry is a listed gap (§5.5) | `telemetry-guide.md:332`, `:537` |
| H-49 | How to test HTTP handlers as pure functions | http-model, endpoint | Test a `Request => Response` function in-process: build requests, assert on status, headers and the full body | H-35 | **P1** · 11 (2/2/3/3/1) | Feasible without a server. Assert full rendered bodies with `==` (`AGENTS.md`) | http4s and zio-http both document in-process route testing (§9.7) | `http-model-examples/CompleteHttpExchange.scala` |

### 5.11 Performance

| ID | Title | Module(s) | Goal | Prerequisites | Priority | Feasibility / blockers | Demand evidence | Existing assets to reuse |
|---|---|---|---|---|---|---|---|---|
| H-50 | How to tune JSON encoding and decoding for throughput | schema | Encode to and decode from `ByteBuffer`, byte arrays and streams instead of `String`; tune `ReaderConfig` and `WriterConfig` | T-01 | **P2** · 10 (2/2/2/3/1) | Feasible. Publish numbers only from the repo's own benchmarks | zio-json [#1651](https://github.com/zio/zio-json/issues/1651) (decoder allocation vs jsoniter-scala) shows users care | `benchmarks/` (JSON vs jsoniter-scala and zio-json) |
| H-51 | How to avoid allocation on hot paths | streams, chunk, maybe, telemetry | Primitive stream lanes, unboxed `Chunk` accessors, `Maybe` instead of `Option`, pre-bound `LabeledInstruments` | T-06 | **P2** · 9 (1/2/2/3/1) | Feasible. Do not copy "backed by `null`" from `docs/index.md:125` | Niche; performance-minded users (§2.1, §2.2) | `zero-boxing.md`; `MaybeBoxingBenchmark`; `labeled-instruments.md` |

### 5.12 Modules kept reference-only

These published modules get no dedicated guide. The reference already answers their tasks, or the module is plumbing:

| Module | Why no guide | Where it still appears |
|---|---|---|
| combinators | Plumbing for other modules' type signatures. Fix the stale reference page (non-existent `Choices`) instead (§6.3) | Mentioned in T-06 (`++` and `&&` result types) |
| ringbuffer | Six reference pages with interactive diagrams and four examples already cover the tasks (§6.5) | H-25 (it backs parallel streams) |
| typeid | Thorough reference with a comparison section and five examples (§2.3) | H-09 (rebinding), H-30 (`Context` keys) |
| markdown | Thorough reference; the `md"..."` interpolator is self-explanatory (§2.4) | H-08, H-39 (descriptions are `Doc` values) |
| mediatype | Thorough reference for the type itself (§5.8) | H-35 (content types), T-08 (`Endpoint.in`/`out`) |
| schema-xml | Reference already includes RSS, Atom and sitemap recipes (§4.7) | T-04 |
| context | Covered as part of Wire DI | H-30, H-43 |

Every one of the 41 modules is covered somewhere: the table above lists the reference-only ones, and every other module appears in at least one `T-` or `H-` row.

## 6. Migration ("coming from X") guides

Each migration guide is a how-to for readers who know another library: a mapping table of concepts and names, then the changes to make in order.

| ID | Title | Coming from | Module(s) | Priority | Feasibility / notes | Demand evidence |
|---|---|---|---|---|---|---|
| M-01 | Coming from circe, zio-json or jsoniter-scala | JSON codec libraries | schema | **P0** · 14 (3/3/3/3/2) | Feasible. Maps `deriveCodec`/`DeriveJsonCodec`/`JsonCodecMaker` and their configuration onto `Schema` + modifiers; links H-02, H-03 | Most-used Scala JSON stack; discriminator and custom-codec questions recur ([kindlings circe docs](https://kindlings.readthedocs.io/en/stable/circe-derivation/), [zio-json config](https://zio.dev/zio-json/configuration)) |
| M-02 | Coming from ZIO Schema 1.x | zio-schema | schema | **Exists** (`guides/zio-schema-migration.md`) | Improve, don't rewrite: it has 0 mdoc blocks | — |
| M-03 | Coming from Magnum or doobie | SQL libraries | sql | **P1** · 12 (3/3/3/2/1) | Scala 3. Magnum's names are near-identical (`DbCodec`, `Repo`, `DbCon`, `Transactor`, `Frag`), so the mapping is cheap; doobie maps `Fragment`, `Read`/`Write`, `Transactor` | doobie and Magnum are the two closest comparables ([doobie](https://typelevel.org/doobie/docs/index.html), [Magnum talk](https://scaladays.org/seattle-2023/magnum-a-new-look-for-scala-3-database-clients)) |
| M-04 | Coming from fs2, ZIO Streams or Ox | Streaming libraries | streams | **P1** · 12 (2/3/3/3/1) | Feasible. `ZStream`/`ZSink`/`ZPipeline` map almost 1:1. Fix stale Kyo and Ox versions in `streams/index.md:78` | Ox shows the demand for direct-style streaming without an effect system ([SoftwareMill](https://softwaremill.com/direct-style-concurrent-streaming/), [Ox](https://ox.softwaremill.com/)) |
| M-05 | Coming from cats-effect `Resource`, ZIO `Scope`/`ZLayer`, macwire or `Using` | Resource and DI libraries | scope, context | **P1** · 12 (2/3/3/3/1) | Feasible. One page, one section per source library | The ZIO docs list macwire-style DI as an alternative to ZLayer ([zio.dev DI](https://zio.dev/reference/di/index.md), [macwire](https://index-dev.scala-lang.org/softwaremill/macwire)) |
| M-06 | Coming from PureConfig, Typesafe Config or zio-config | Config libraries | config, config-hocon | **P0** · 13 (3/3/3/3/1) | Feasible. Covers reading existing `application.conf` files with config-hocon and kebab-case key mapping | Scala 3 PureConfig derivation pain ([Scala Users](https://users.scala-lang.org/t/scala-3-migration-pureconfig/12096)); teams replacing Typesafe Config ([alexn.org](https://alexn.org/blog/2025/10/30/scala-3-hocon-parsing/)) |
| M-07 | Coming from chimney or ducktape | Data transformation | schema (`Into`, `As`) | **P1** · 11 (2/2/3/3/1) | Feasible. `Into` covers total and error-accumulating transforms; `As` covers reversible ones | chimney's docs show the demand for DTO-to-domain mapping ([chimney](https://chimney.readthedocs.io/supported-transformations/), [Baeldung](https://www.baeldung.com/scala/chimney-data-transformation-library)) |
| M-08 | Coming from monocle or quicklens | Optics | schema | **P2** · 10 (1/2/3/3/1) | Feasible. Both are benchmarked against zio-blocks optics (§3.9) | No direct evidence found |
| M-09 | Coming from Scalatags | HTML templating | html | **P2** · 10 (1/2/3/3/1) | Feasible | No direct evidence found |
| M-10 | Coming from jwt-scala | JWT | jwt | **P2** · 10 (2/1/3/3/1) | Feasible once `jwt.md` is fixed; no JWKS | RS256 verification guides target jwt-scala ([SSOJet](https://ssojet.com/jwt-validation/validate-jwt-using-rs256-in-scala)) |
| M-11 | Coming from avro4s, the MongoDB driver macros or phobos | Format codecs | schema-avro, schema-bson, schema-xml | **P3** · 8 (1/1/2/3/1) | Feasible but small audiences | [phobos](https://index-dev.scala-lang.org/tinkoff/phobos), [Mongo macros](https://mongodb.com/docs/languages/scala/scala-driver/v5.0/bson/macros) |
| M-12 | Coming from tapir or the zio-http Endpoint API | HTTP endpoints | endpoint | **P3** · blocked | F = 1: without server, client or OpenAPI interpreters, a tapir user cannot reach parity (X-02, X-03) | tapir's tutorials lead with serving and OpenAPI ([tapir](https://tapir.softwaremill.com/en/latest/tutorials/02_openapi_docs.html)) |
| M-13 | Coming from OpenTelemetry Java or otel4s | Telemetry | telemetry, otel | **P3** · blocked | F = 1: exporters are private (X-01) | [otel4s Jaeger example](https://typelevel.org/otel4s/examples/jaeger-docker/) |
| M-14 | Coming from the zio-http Datastar SDK | Datastar | datastar | **P3** · blocked | F = 1: no `readSignals` or script helper (X-10) | Every Datastar SDK leads with `read_signals` ([Stario](https://stario.dev/docs/how-tos/datastar-sdk)) |

The `Future` migration is covered by H-28, and the zio-schema migration already exists (M-02).

## 7. Blocked topics

High-value topics that need library or build work before a supported guide can be written. "§9.5 #n" refers to the blockers list in `research.md`.

| ID | Topic | Blocker | What would unblock it | Backlog items waiting |
|---|---|---|---|---|
| X-01 | Export telemetry to an OpenTelemetry Collector, Jaeger or Tempo | §9.5 #1: `OtlpJson*Exporter` and `BatchProcessor` are `private[otel]`; the metric exporter has no background thread | Public exporter constructors and a scheduled metric export. Then write "How to export telemetry to an OpenTelemetry Collector" (demand: [otel4s OTLP guide](https://typelevel.org/otel4s-sdk/guides/otlp.html)) | M-13; extends T-12, H-43 |
| X-02 | Generate an OpenAPI document from `Endpoint` values | §9.5 #3: no interpreter exists, although the docs claim one does | An endpoint-to-OpenAPI interpreter. Until then, H-39 can show hand assembly from `RoutePattern.render` + `toOpenAPISchema`, labelled as glue | M-12, H-39 |
| X-03 | Serve an `Endpoint` with typed request and response decoding | §9.5 #2: `HttpCodec` has no decode or encode, only path decoding | `HttpCodec` decode/encode plus a server adapter (JDK or zio-http) | M-12, T-15, H-38 |
| X-04 | Call an API through a typed client built from an `Endpoint` | §9.5 #2 | A client interpreter | M-12 |
| X-05 | Compiled snippets for sql-zio and dataMigration | §9.5 #4: neither is on the docs mdoc classpath | Add `sql-zio` and `dataMigration` to `lazy val docs` (`build.sbt:1698-1735`). A docs-build change, not library work | H-21, H-23 |
| X-06 | CSV with a custom delimiter (TSV) through derived codecs | §9.5 #9: `CsvCodecDeriver` hardcodes `CsvConfig.default` | Let the deriver take a `CsvConfig` | H-11 (works around it today) |
| X-07 | Run projections on SQLite in production (multiple sources, rebuilds, shards) | Issue [#1744](https://github.com/zio/zio-blocks/issues/1744): seven rebuild and migration bugs | Fix #1744; merge PR #1742 | T-14 |
| X-08 | Expose mux channels as streams with async receive | §6.6: `receive` is poll-based; no Async API (scenario C7) | An `Async`-returning receive or a `Stream` adapter | H-29 |
| X-09 | Avro with a schema registry, container files and logical types | §4.1: no registry, `DataFileWriter` or reader/writer schema resolution; `Instant` is not a logical type | Registry framing helpers and logical-type support | H-13 |
| X-10 | A typed Datastar round trip | §8.5: no `readSignals`, `datastarScript` or typed actions | Add the request-side helpers | T-11, M-14 |
| X-11 | New APIs in example projects | §9.5 #5: examples pin 0.0.56 | The next release. Until then, use mdoc-only snippets for newer APIs | All items whose examples use post-0.0.56 APIs |
| X-12 | Secrets from files and environment-only sources (`ConfigSource.fromEnv`, secrets dir, `_FILE`) | Planned in `docs/plans/config-follow-up-prs.md`, not built | Implement the planned sources | H-33, T-05 (uses the existing `EnvSource` meanwhile) |
| X-13 | Verify tokens from an OpenID provider (JWKS, `kid` lookup, PEM) | §5.7: no JWK/JWKS, PEM parsing or `kid` key resolution | JWK/JWKS support, or a documented PEM-to-DER helper | H-37, H-38, M-10 |

## 8. Doc fixes to do alongside

These inaccuracies from §9.6 will mislead a guide author who copies from the existing pages. Fix each before, or in the same PR as, the backlog items it affects.

| Inaccuracy (§9.6) | Affects |
|---|---|
| `endpoint/index.md:33,210` and `endpoint.md:298` claim OpenAPI generation from endpoints | T-08, H-39, X-02 |
| `combinators.md` documents a non-existent `Choices`, calls `Concat` "Scala 2 only", uses `Concat.Concat.WithOut` | T-06 (if it explains `++` result types) |
| `context.md:484` calls a non-existent `Wire.make[...]` companion method | H-30, M-05 |
| `jwt.md`: audience mismatch is `InvalidClaim`, not `InvalidToken`; `InvalidKey` missing; no key formats; no frontmatter | H-37, H-38, M-10 |
| `sql-transactions.md:39` says the default isolation is Serializable (it is the driver default) | T-07, H-17, H-21 |
| `data-migration.md` "Full Example" omits `ID1 =:= ID2`, the `Dialect` given and the transactor | H-23 |
| `projection.md`: wrong `.update` signature, duplicated artifact, unexplained `sql-zio` (PR #1742 rewrites this page) | T-14 |
| `docs/index.md:153,155` advertise sql and data-migration on JS | H-01, T-07 |
| `docs/index.md:125` says `Maybe` is backed by `null` | H-51 |
| `docs/index.md:492-502` guides list omits the two sql guides | Integration step of every new guide |
| Wrong artifact `zio-http-model` in `http-model/model.md`, `index.md`, `datastar/sse.md`, `datastar/index.md` | H-01, H-35, H-41, T-10, T-11 |
| `http-model/schema.md:59,65,68`: wrong artifact; "Scala 3.x only" is false | H-36 |
| `schema/format.md:59` calls YAML and TOON `TextFormat` | T-04, H-12, H-15 |
| `csv.md:130-147` claims derivation accepts a custom `CsvConfig` | H-11 |
| `messagepack.md:162` "Stream Large Datasets" shows no streaming | T-04, H-16 |
| `openapi.md:10-17` stale signature; the `???` streaming codecs are undocumented | H-39, H-44 |
| `streams/index.md:78` stale Kyo and Ox versions | M-04 |
| `mux.mdx:37` vs `:818-822` lock-based contradiction | H-29 |
| Orphan `docs/reference/datastar.md` | T-11 (link only the split pages) |
| bson and thrift READMEs cite old dependency versions | H-14, H-16 |
| Stale run commands in `smithy-examples`, `projection-examples` and `async-examples` headers (the examples are standalone builds now) | T-13, T-14, H-28 |
| `ContextStorage.scala:31` references a missing ADR | T-12, H-43 (do not link it) |

## 9. Top 10 next

The ten P0 items, in the order to write them. As of 2026-10-20 all ten are done, in review or in progress; see [Status](#status).

1. **H-01 How to add ZIO Blocks to your build.** Every tutorial needs an install step, and today only one page mentions the Scala 3.7+ `zio-blocks-next-*` artifacts.
2. **T-01 Your first schema: round-trip a type through JSON.** Schema is the hub that 22 modules build on, and the library has no getting-started lesson.
3. **H-02 How to customize JSON field names, ADT encoding and optional fields.** These are the most-asked questions across circe, zio-json and zio-schema.
4. **M-01 Coming from circe, zio-json or jsoniter-scala.** JSON users are the largest audience that would switch, and the mapping reuses H-02 and H-03.
5. **T-06 Build your first streaming pipeline.** Streams has 8,400 lines of reference and 38 examples but no lesson. Most of the examples can be reused.
6. **T-05 Build a typed configuration loader.** Every application needs config. The module has seven reference pages, zero examples, and a real Scala 3 demand gap.
7. **T-07 Build your first database repository on SQLite.** doobie and Magnum both lead with this lesson, and sql has no examples project.
8. **T-02 Evolve your data from V1 to V2.** Ten ready `Into`/`As` examples are unreferenced, and migration was a heavily funded feature.
9. **H-03 How to override the codec for one type everywhere.** Its example already exists, and it answers a classic "custom date format" question for every format at once.
10. **M-06 Coming from PureConfig, Typesafe Config or zio-config.** Scala 3 users are still stuck on PureConfig derivation, and config-hocon reads their existing files.

## 10. Sources

**Diátaxis**

- [The difference between a tutorial and how-to guide](https://diataxis.fr/tutorials-how-to/)
- [How-to guides](https://diataxis.fr/how-to-guides/)

**ZIO and ZIO Blocks**

- [ZIO guides index](https://zio.dev/guides/)
- [ZIO tutorial: Encode and Decode JSON Data](https://zio.dev/guides/tutorials/encode-and-decode-json-data)
- [ZIO dependency injection](https://zio.dev/reference/di/index.md)
- [ZIO Blocks site](https://zio.dev/zio-blocks/)
- [ZIO Blocks on Scaladex](https://index-dev.scala-lang.org/zio/zio-blocks)
- [zio-json configuration](https://zio.dev/zio-json/configuration)
- [zio-http Endpoint reference](https://zio.dev/zio-http/reference/endpoint) and [concepts](https://zio.dev/zio-http/concepts/endpoint)
- zio-blocks issues and PRs: [#94](https://github.com/zio/zio-blocks/issues/94), [#111](https://github.com/zio/zio-blocks/issues/111), [#193](https://github.com/zio/zio-blocks/issues/193), [#213](https://github.com/zio/zio-blocks/issues/213), [#316](https://github.com/zio/zio-blocks/issues/316), [#519](https://github.com/zio/zio-blocks/issues/519), [#836](https://github.com/zio/zio-blocks/issues/836), [#1334](https://github.com/zio/zio-blocks/issues/1334), [#1744](https://github.com/zio/zio-blocks/issues/1744), [PR #1742](https://github.com/zio/zio-blocks/pull/1742), [PR #1666](https://github.com/zio/zio-blocks/pull/1666)
- zio-schema issues: [#81](https://github.com/zio/zio-schema/issues/81), [#447](https://github.com/zio/zio-schema/issues/447), [#523](https://github.com/zio/zio-schema/issues/523)
- zio-json issues: [#1036](https://github.com/zio/zio-json/issues/1036), [#1301](https://github.com/zio/zio-json/issues/1301), [#1651](https://github.com/zio/zio-json/issues/1651)

**JSON, schema and data transformation**

- [Kindlings circe derivation](https://kindlings.readthedocs.io/en/stable/circe-derivation/)
- [Stack Overflow mirror: circe serialization without class name](https://dev.library.kiwix.org/content/stackoverflow_en_nopic_2021-08/questions/52594416/scala-circe-case-class-serialization-without-class-name)
- [CoddyKit: custom codecs in circe](https://www.coddykit.com/fr/courses/scala/custom-codecs-9564213)
- [chimney: supported transformations](https://chimney.readthedocs.io/supported-transformations/) and [troubleshooting](https://chimney.readthedocs.io/en/stable/troubleshooting/)
- [Baeldung: chimney](https://www.baeldung.com/scala/chimney-data-transformation-library)
- [scala-jsonschema](https://index-dev.scala-lang.org/andyglow/scala-jsonschema)
- [tapir: Generating JSON Schema](https://tapir.softwaremill.com/en/latest/docs/json-schema.html)
- [tupson (named tuples)](https://index-dev.scala-lang.org/sake92/tupson)

**Formats**

- [Typelevel toolkit examples](https://typelevel.org/toolkit/examples.html)
- [InfoQ: TOON](https://www.infoq.com/news/2025/11/toon-reduce-llm-cost-tokens)
- [Analytics Vidhya: TOON](https://www.analyticsvidhya.com/blog/2025/11/toon-token-oriented-object-notation/)
- [registravka4s](https://index-dev.scala-lang.org/maxstreese/registravka4s)
- [kaa](https://scaladex.scala-lang.org/davideicardi/kaa)
- [MongoDB Scala driver macros](https://mongodb.com/docs/languages/scala/scala-driver/v5.0/bson/macros), [JAVA-3545](https://jira.mongodb.org/browse/JAVA-3545), [JAVA-4966](https://jira.mongodb.com/browse/JAVA-4966)
- [phobos](https://index-dev.scala-lang.org/tinkoff/phobos)

**SQL and data migration**

- [Book of doobie](https://typelevel.org/doobie/docs/index.html), [Connecting](https://typelevel.org/doobie/docs/03-Connecting.html)
- [Magnum on Scaladex](https://index-dev.scala-lang.org/augustnagro/magnum)
- [Magnum talk, Scala Days 2023](https://scaladays.org/seattle-2023/magnum-a-new-look-for-scala-3-database-clients)
- [Scala database libraries comparison (2026)](https://www.pistack.xyz/posts/2026-09-19-scala-database-libraries-doobie-slick-quill-skunk-comparison/)
- [Rock the JVM: Skunk guide](https://blog.rockthejvm.com/skunk-complete-guide/)
- [pg-osc: zero-downtime schema changes](https://www.shayon.dev/post/2022/47/pg-osc-zero-downtime-schema-changes-in-postgres)
- [InfoQ: shadow table strategy](https://www.infoq.com/articles/shadow-table-strategy-data-migration)
- [Pekko Projection getting started](https://pekko.apache.org/docs/pekko-projection/1.1/getting-started/index.html)

**Streams, config and DI**

- [fs2 getting-started example (mirror)](https://git.rossabaker.com/typelevel/fs2/blame/branch/main/site/getstarted/example.md)
- [Livongo: reading large files with fs2](https://techblog.livongo.com/using-scala-to-read-really-really-large-files-part-5-fs2-with-io-helpers/)
- [SoftwareMill: direct-style concurrent streaming](https://softwaremill.com/direct-style-concurrent-streaming/)
- [Ox](https://ox.softwaremill.com/)
- [Scala Users: Scala 3 migration and PureConfig](https://users.scala-lang.org/t/scala-3-migration-pureconfig/12096)
- [alexn.org: Scala 3 HOCON parsing](https://alexn.org/blog/2025/10/30/scala-3-hocon-parsing/)
- [Typelevel blog: Validated Configurations with Ciris](https://typelevel.org/blog/ciris.html)
- [sconfig](https://index-dev.scala-lang.org/ekrich/sconfig)
- [macwire](https://index-dev.scala-lang.org/softwaremill/macwire)
- [FeatBit: percentage rollouts](https://home.featbit.co/blogs/Conception-Percentage-Rollout-Modern-Software-Development)

**HTTP, web UI, security, observability and codegen**

- [tapir: Auto-generating OpenAPI docs tutorial](https://tapir.softwaremill.com/en/latest/tutorials/02_openapi_docs.html)
- [dev.to: basic web server with Scala 3](https://dev.to/yet_anotherdev/how-to-write-a-basic-web-server-with-scala-3-90j)
- [Tyrian SSR guide](https://tyrian.indigoengine.io/02-guides/ssr/)
- [Frontend Mentor: Scalatags + Cask SSR](https://frontendmentor.io/solutions/tailwind-scala-ssr-scalatags-and-cask-deployed-with-docker-image-UPzyFXyf_L)
- [flaviocopes: HTMX introduction](https://flaviocopes.com/htmx-introduction/)
- [Datastar: backend requests](https://data-star.dev/guide/backend_requests)
- [Stario Datastar SDK](https://stario.dev/docs/how-tos/datastar-sdk)
- [SSOJet: validate RS256 JWT in Scala](https://ssojet.com/jwt-validation/validate-jwt-using-rs256-in-scala)
- [Auth0 community: RS256 public key](https://community.auth0.com/t/where-is-the-auth0-public-key-to-be-used-in-jwt-io-to-verify-the-signature-of-a-rs256-token/8455/2)
- [otel4s: Jaeger example](https://typelevel.org/otel4s/examples/jaeger-docker/)
- [otel4s SDK: OTLP export](https://typelevel.org/otel4s-sdk/guides/otlp.html)
- [smithy4s sbt codegen](https://index.scala-lang.org/disneystreaming/smithy4s/artifacts/smithy4s-sbt-codegen/0.18.4)
- [guardrail](https://github.com/twilio/guardrail/)
- [Zuplo: API-first development in Scala](https://zuplo.com/blog/2025/04/11/api-first-development-in-scala)
