# Backlog refresh (2026-11-09): new tutorial, how-to and migration candidates

Compiled 2026-11-09 on branch `backlog-research-1109` (base `d639bff4e`, latest `origin/main`). A proposal only: nothing here is in `docs/dev/backlog.md` until the coordinator adds it. IDs are placeholders that continue the last pass's numbering (`H-new-25`.., `T-new-3`.., `M-new-3`..). Milad picks the items; the coordinator folds the picks into the backlog. New blocked-topic IDs continue the 2026-11-07 numbering: `X-new-1` (time-window grouping) and `X-new-3` (Async timeouts and retries, the same blocker the 2026-11-07 pass called `X-new-3`; there is no `X-new-2`). Backlog §7 ends at X-14.

Scores follow backlog §2: `total (D/C/G/F/O)`, each 1-3. **P0** ≥ 13, **P1** 11-12, **P2** 9-10, **P3** ≤ 8 or F = 1.

## 1. Method and sources

**What this pass did.** It checked the 2026-11-07 refresh's remaining candidates, looked for coverage gaps in the published modules, checked demand since 2026-11-07, looked up peer-library docs, and scored each candidate with the five criteria in backlog §2. It re-checked the two blocked items (H-new-3 and H-new-16). Read-only: no docs pages, no library code, no sbt.

**Sources used.**

1. Backlog: `docs/dev/backlog.md` on `khajavi/backlog-status-1109` (fetched this pass). §2 (scoring), §4-§6 (existing items), §5.12 (reference-only modules), §5.13-§5.14 (2026-10-29 and 2026-11-07 picks), §7 (blocked topics), Status table.
2. Last pass: `docs/dev/backlog-refresh-2026-11-07.md` (on `khajavi/backlog-status-1109`; not in this worktree), §2-§5.
3. Repo on `d639bff4e`: the 84 files under `docs/guides/`, `docs/sidebars.js`, `docs/reference/`, and the library code cited as `file:line`. Module imports were counted by grep.
4. `library-issues.md` (khajavi branch): the Library bugs and Usability traps headings. Status of H-64 to H-66, T-16, H-67 and M-15 comes from `khajavi/backlog-status-1109`, which this worktree's `docs/dev/backlog.md` does not have; the merged-PR state of those items was checked with `gh`, the M-15 PR was not found.
5. GitHub (`gh`, `zio/zio-blocks`): issues and merged pull requests from 2026-11-07 on; PR #1747 and PR #1981 state.
6. Peer docs (WebSearch, standard mode). **These were search-result snippets, not full pages**; each cite is what the snippet showed. No page was read in full, so treat peer evidence as a pointer, not a verified claim.

**Limits of the demand evidence (said plainly).**

- **No GitHub issue was created on `zio/zio-blocks` since 2026-11-07.** `gh issue list --search "created:>=2026-11-07"` returned nothing.
- **24 pull requests merged since 2026-11-07.** 23 are from Milad's account (`khajavi`) and 1 is from Scala Steward (#1962, `app/zio-scala-steward`). None are from outside contributors.
- **Two outside pull requests are still open:** #1747 (configurable YAML field names, last updated 2026-10-17) and #1938 (an outside fix to the html module, open since 2026-11-06). #1747 is the blocker for H-new-3 and M-new-1.
- Reddit, X and Discord were not checked in this pass.
- **So there is still almost no outside demand.** The demand score (D) rests on peer docs and on coverage gaps. D = 1 means "no evidence beyond our own backlog"; D = 2 means peer docs lead with the task or the gap is obvious.

**Scala version rule.** Every install step must show both `zio-blocks-*` and the Scala 3.9+ name `zio-blocks-next-*` (plus `-experimental`). sql, sql-zio, dataMigration, projection, datastar and htmx are Scala 3 only.

**Security rule.** No candidate or note in this file is about a private security topic. Items on such topics are listed only as "held" by ID, with no topic name. Details go to the coordinator privately.

## 2. Coverage map

Counts are approximate: a grep of `docs/guides/*.md` for each module's package or examples name, not a per-file import check. A different grep method gives different numbers (for example streams 13, async 8, mediatype 4 on this branch). "Reference" means the module has pages under `docs/reference/`.

| Module | Guides | Reference | Gap or note (changes since 2026-11-07 in bold) |
|---|---|---|---|
| schema | 57 | yes | Well covered. Migration from json4s, play-json and uPickle is not taught: M-new-3. |
| schema-yaml | 4 | yes | `read-and-write-yaml`. Hand-written YAML and front matter: H-new-3 (blocked). Migration from scala-yaml: M-new-1 (held). |
| schema-csv | 2 | yes | `read-and-write-csv-and-tsv-files`. Importing a CSV into a database: T-new-3. |
| schema-avro | 4 | yes | `encode-and-decode-records-with-avro`. |
| schema-bson | 1 | yes | `store-case-classes-in-mongodb`. |
| schema-toon | 4 | yes | `llm-data-with-toon`. |
| schema-xml | 1 | yes | Reference-only (backlog §5.12). |
| schema-messagepack | 2 (`one-type-many-formats`, `zio-schema-migration`; no task guide) | yes | No MessagePack task guide. H-16 skipped by Milad; not re-proposed. |
| http-model | 4 (last pass; not re-counted) | yes | Serving, SSE, auth and validation guides. |
| http-model-schema | 1 | yes | `validate-query-parameters-and-headers` (H-36). |
| endpoint | 1 | yes | M-12 blocked (X-02 to X-04). RouteTree: dropped (§4). |
| jwt | 2 | yes | `issue-and-verify-jwts`, `protect-endpoints-with-jwt`. |
| html | 6 | yes | `your-first-html-page`. |
| htmx | 2 | yes | `add-interactivity-with-htmx`, `return-htmx-fragments-and-response-headers`. |
| datastar | 1 | yes | `build-a-live-dashboard-with-datastar`. |
| streams | 11 | yes | **Now has `read-and-write-json-lines` (H-64) and `turn-a-paginated-api-into-a-stream` (H-65).** Time-window grouping: not in the library (X-new-1). Hang and error troubleshooting: H-new-24. |
| async | 7 | yes | **Now has the shared Scala.js model tutorial (T-16)** in `share-one-model-between-scala-js-and-the-jvm`. No timeout or retry API: X-new-3. |
| chunk | 14 | yes | Used throughout; no dedicated guide (H-45 skipped). |
| scope | 8 | yes | `compile-time-resource-safety-with-scope`, `fix-scope-and-wire-compile-errors`. |
| config (+ hocon, json, yaml) | 7 | yes | `load-configuration-files`, `typed-config-loader`. |
| typeid | 5 | yes | Reference-only (§5.12). |
| context | 1 | yes | Covered as part of Wire DI (§5.12). |
| maybe | 4 | yes | Used throughout. |
| combinators | 0 | yes | Plumbing (§5.12). |
| markdown | 0 | yes | Reference-only (§5.12). |
| mediatype | 2 | yes | Reference-only (§5.12). |
| openapi | 3 | yes | `write-and-export-openapi`, `generate-scala-types-from-openapi`. |
| codegen | 3 | yes | Same guides. **`generate-service-traits-from-smithy-or-openapi` (H-66) now covers service traits.** |
| smithy | 2 | yes | `generate-scala-code-from-smithy`, plus H-66. |
| mux | 2 | yes | `getting-started-with-mux`, `run-a-mux-over-a-socket`. |
| ringbuffer | 0 | yes | Six reference pages (§5.12). |
| telemetry (+ otel) | 6 | yes | Logs, traces, metrics, testing, correlation. Exporting to a collector: H-new-16 (blocked, X-01). No bridge to SLF4J or scala-logging: M-new-4. |
| sql | 16+ | yes | Well covered. Batch writes: H-67 (PR #1981, merged; not a candidate here). Import direction (CSV into a table): T-new-3. Migration from Quill: M-15 (in progress; not a candidate here). |
| sql-zio | 3 | yes | `sql-from-zio`. |
| projection | 0 | yes | **No guide.** Only reference pages. T-14 blocked on #1744 / X-07. |
| data-migration | 1+ (not re-counted) | yes | `migrate-table-rows-online`. |

**Largest gaps (no guide, or reference only):** projection (no guide), markdown, ringbuffer, combinators. Of these, only projection has a demand case, and it is blocked (T-14, X-07). None is proposed here.

**Coverage of this pass's new guides.** H-64 (JSON Lines), H-65 (paginated APIs), H-66 (service traits) and T-16 (shared Scala.js model) were merged on 2026-11-07 and 2026-11-09, so they are not candidates. H-57 (basic auth and sessions), H-58 (Iron), H-59 (optional search filters) and H-60 (new wire format) have guide files; they were not re-checked for merge state here.

## 3. Candidates

Sub-scores: D = user demand, C = module centrality, G = coverage gap, F = feasibility, O = onboarding value. Peer cites are snippets (see §1).

### 3.1 New this pass

| ID | Title | Module(s) | Goal | Priority · score (D/C/G/F/O) | Feasibility / blockers | Demand evidence and peer doc | Existing assets |
|---|---|---|---|---|---|---|---|
| M-new-3 | Coming from play-json, json4s or uPickle | schema | A mapping table from `Json.format` (play-json), `Formats` / `CustomSerializer` (json4s) and `ReadWriter` / `macroRW` (uPickle) to `Schema` plus codecs; then the changes to make, in order: field names, optional fields, sealed traits, defaults. Uses the same pattern as M-01 and M-02. | **P1 · 12 (2/3/3/3/1)** | Feasible. Before-blocks are plain fences (the other libraries are not on the docs classpath); after-blocks compile. | Peer: uPickle docs say Scala 3 users write `derives ReadWriter` and Scala 2 users write `macroRW` (Scala Toolkit JSON guide, `https://docs.scala-lang.org/toolkit/json-deserialize.html`, snippet). play-json: `Json.format` / `Json.reads` / `Json.writes` macros and `JsonConfiguration` naming strategies (`https://www.playframework.com/documentation/3.0.2/ScalaJsonAutomated`, snippet). json4s: not retrieved. | `json-library-migration` (M-01, circe / zio-json / jsoniter); `customize-json-encoding` (H-02); `override-codec-for-one-type` (H-03). |
| M-new-4 | Coming from scala-logging, log4cats or SLF4J | telemetry | A mapping table from `Logger[F].info` (log4cats), `logger.info` macros (scala-logging) and `LoggerFactory` / SLF4J calls to `log.info` and `LogRecord`; how to keep the call sites small; what does not carry over (no SLF4J backend bridge yet). | **P2 · 10 (2/2/3/2/1)** | F = 2: `telemetry` is JVM and needs JDK 21+. Today there is no bridge to SLF4J or JUL, so the guide must say that existing appenders stop receiving output: `docs/guides/telemetry-guide.md:1117` ("There's no bridge today"). The guide can only offer a custom `LogWriter` as the workaround. | Peer: log4cats README (mirror `git.rossabaker.com/typelevel/log4cats`, snippet): `Logger[F]` context bound, `log4cats-slf4j` as the recommended backend. scala-logging: not retrieved. | `test-telemetry` (H-48); `correlate-logs-and-traces` (H-43); `instrument-a-program-with-logs-traces-and-metrics` (T-12). |
| M-new-5 | Coming from refined | schema (+ validation) | A mapping table from `refineV[P](x)` / `Refined` / `refineMV` (refined) to `Schema.transform`, `Validation` and Iron (H-58); how to turn a refined type into a validated newtype with all errors collected. | **P2 · 10 (2/2/2/3/1)** | Feasible. Scala 3 side uses Iron (H-58). Compile-time literal checks differ: `refined`'s `auto` macros are Scala 2 only, per the snippet; say so. | Peer: refined README (`https://github.com/fthomas/refined`, snippet only): `refineV` returns `Either` for runtime values. Iron docs (`https://javadoc.io/static/io.github.iltotore/iron-docs_3/2.3.0/docs/overview.html`, snippet). | `use-iron-refined-types-and-typed-ids` (H-58); `validated-newtypes` (H-04). G = 2 because both guides already cover the target types; this pass adds only the mapping. |
| T-new-3 | Build a CSV importer: stream a file into a SQLite table and report bad rows | schema-csv, sql, streams (+ scope) | A tutorial that reads a CSV file as a stream, decodes each row, writes good rows in batches with `Repo#insertBatch` (`sql/shared/src/main/scala/zio/blocks/sql/Repo.scala:267`), and collects the bad rows with their line numbers. The reverse of H-20 (export). | **P2 · 10 (1/3/2/2/2)** | F = 2: needs csv, sql and streams on the examples side, and sql is Scala 3 only. The `Table.createTable` bug (no `PRIMARY KEY`, `Table.scala:107`) does not block a plain insert, so the import can avoid upsert. Uses the 0.0.56 API only. | Peer: fs2-data-csv decode API (`https://www.javadoc.io/static/org.gnieh/fs2-data-csv_3/1.5.1/fs2/data/csv.html`, snippet: decode functions for char streams with or without headers). No file-import example was retrieved. | `read-and-write-csv-and-tsv-files` (H-11); `stream-large-query-results` (H-20); `your-first-sqlite-repository` (T-07); `migrate-table-rows-online` (H-23, uses `insertBatch`). |

### 3.2 Carried from the 2026-11-07 pass (re-scored)

| ID | Title | Module(s) | Priority · score (D/C/G/F/O) | Re-check and notes |
|---|---|---|---|---|
| H-new-24 | How to find and work around hangs and internal errors in streams and Async | streams, async | **P2 · 10 (1/3/3/2/1)** | **Re-check:** the 2026-11-07 pass carried forward the 2026-10-29 check of issues 946, 1002 and 10164 without re-checking them. They are wrong. `gh` shows #946 and #1002 are merged pull requests, and 10164 does not exist on `zio/zio-blocks`. The real bugs are the Streams entries in `library-issues.md` (`### Streams: …` at lines 1083, 1139, 1211, 1350 and 1441). A guide would list each bug with a workaround and the fixed version. Needs a Troubleshooting sidebar topic, which is a docs change, so F = 2. |
| H-new-23 | How to fix Scala 2.13 compile errors and ambiguous imports | all (cross-build) | **P2 · 9 (1/3/2/2/1)** | No change. `fix-scope-and-wire-compile-errors` (H-31) is the model. Several runtime helpers are `private[...]`, as the 2026-11-07 pass said. |
| H-new-17 | How to trace a streaming pipeline and a scoped resource | telemetry, streams, scope | **P3 · 8 (1/2/2/2/1)** | No change. Check that spans reach stream stages on `*Async` terminals before writing; this has not been tested. JDK 21+. |
| M-new-1 | Coming from scala-yaml or circe-yaml | schema-yaml | **P2 · 9 (1/2/3/2/1), held** | Held behind #1747: the guide would document kebab-case keys only, and #1747 is open (last update 2026-10-17). |

### 3.3 Blocked or held (not in the Top 10)

| ID | Title | Status |
|---|---|---|
| H-new-3 | How to read Markdown front matter and other hand-written YAML | **Still blocked.** #1747 (configurable YAML field names, outside contributor) is still open; last update 2026-10-17. Re-check before writing. |
| H-new-16 | How to export telemetry to an OpenTelemetry Collector | **Still blocked by X-01.** The OTLP exporters are still `private[otel]`: `otel/src/main/scala/zio/blocks/telemetry/otel/OtlpJsonExporter.scala:20`, `OtlpJsonTraceExporter.scala:26`, `OtlpJsonMetricExporter.scala:26`, `BatchProcessor.scala:34`. `instrument-a-program…md:562` still says the reader must write an exporter. |
| H-new-11, H-new-12 | Held (coordinator decision, 2026-11-07). | Held, not re-scored. Topics withheld from this file by the security rule; see the coordinator's private message. |

### 3.4 Proposed blocked topics (for §7 of the backlog)

| ID | Topic | Blocker | What would unblock it | Items waiting |
|---|---|---|---|---|
| X-new-1 | Group stream elements by count or by time (`groupedWithin`) | `Stream#grouped(n)` is the only grouping, and it is `chunked(n)` with no time limit (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:457`). Peer: Pekko has `groupedWithin` (`https://pekko.apache.org/docs/pekko/1.0/stream/operators/Source-or-Flow/groupedWithin.html`, snippet); fs2 has `Stream.groupWithin` (`https://github.com/typelevel/fs2/discussions/3403`, snippet). | A time-window grouping operator on `Stream`. | H-new-25 |
| X-new-3 | Timeouts and retries around an `Async` call (same blocker the 2026-11-07 pass called X-new-3; also backlog §5.13 "Async retry/timeout/race") | No timer and no timeout, retry or sleep API in `async/shared` or `streams/shared` (grep, 2026-11-09). `Async.selector` at `Async.scala:527` covers "first result" only. Peer: cats-retry (`https://cb372.github.io/cats-retry/docs`, snippet); cats-effect `Temporal` timeouts (commit mirror, snippet). | A timer effect on `Async` (JVM and Scala.js), then a `timeout` and a retry-with-policy method. | H-new-27 |

**H-new-25 and H-new-27 are candidates that stay P3** until X-new-1 and X-new-3 are fixed. They are listed here so the coordinator can see the demand. Their scores are in §3.5.

### 3.5 Proposed new blocked items (scored)

| ID | Title | Module(s) | Priority · score (D/C/G/F/O) | Blocker |
|---|---|---|---|---|
| H-new-25 | How to batch a stream by count or by time | streams | **P3 · 10 (2/3/3/1/1)** | F = 1, X-new-1. Demand: peer docs (Pekko, fs2) lead with time windows; no user request. |
| H-new-27 | How to add timeouts and retries to an `Async` call | async | **P3 · 10 (2/2/3/1/2)** | F = 1, X-new-3. Peer: cats-retry and cats-effect timeouts (snippets). |

## 4. Dropped (and why)

**Dropped this pass.**

| ID | Title | Score | Reason |
|---|---|---|---|
| H-new-15 | How to dispatch requests with RouteTree and build links from routes | 9 (1/2/2/3/1), was P2 | Dropped on coordinator direction: low value. The reference (`docs/reference/endpoint/route-tree.md`, `route-pattern.md`) answers the task. |
| H-new-7 | How to run Async calls in parallel and use the first result | 8 (2/2/1/2/1), P3 | The first-result case is already in `docs/reference/async.md:1368-1388` (`Async.selector`, `Async.scala:527`). The only part left is timeouts and retries, which is now X-new-3 / H-new-27. |
| H-new-13 | How to edit, delete and load rows on demand with htmx | 8 (2/1/2/2/1), P3 | Covered by the two htmx guides and `docs/reference/htmx/`. Fold any new part into those guides. |
| H-new-14 | How to add loading indicators, polling and lazy loading with Datastar | 8 (2/1/2/2/1), P3 | Covered by the dashboard guide and `docs/reference/datastar/events.md`. Fold into that guide. |

**Taken by other items (not candidates).**

| ID | Title | Where it went |
|---|---|---|
| H-new-6 | Insert and update many rows in batches | H-67, PR #1981 (merged). |
| M-new-2 | Coming from Quill | M-15, in progress. |
| H-new-8 | JSON Lines with streams | H-64, merged (`read-and-write-json-lines`). |
| H-new-9 | Paginated API as a stream | H-65, merged (`turn-a-paginated-api-into-a-stream`). |
| H-new-19 | Service traits from Smithy or OpenAPI | H-66, merged. |
| T-new-2 | Shared Scala.js and JVM model | T-16, merged (`share-one-model-between-scala-js-and-the-jvm`). |

**Dropped in the 2026-11-07 pass (still dropped).** T-new-1 (projection test tutorial; #1744 open, and `docs/reference/projection/testing.md` covers `TestEngine`), H-new-18 (callback instruments; `docs/reference/telemetry/metrics/meter.md` covers `buildWithCallback`).

**Skipped by Milad (not re-proposed here).** H-54 (SQL logging with telemetry), H-55 (Schema derivation and codec errors), H-56 (sql errors), H-61 (round-trip property tests), H-62 (configuration-loading errors).

**Not proposed, on purpose.** `markdown`, `ringbuffer`, `combinators`, `typeid`, `mediatype` and `context` stay reference-only (backlog §5.12). `schema-messagepack` and `http-model-schema` have no new peer or user evidence.

**Considered and not written up (weak).** A tutorial that splits database rows from API DTOs: `evolve-your-data` (T-02) already shows `Into` for DTO-to-domain mapping, so G would be 1. A stream testing guide: the reference and examples test streams; it would be thin. Schema for types you don't own: `docs/reference/schema/schema.md:137-146` lists the `java.time` instances, and H-08 and H-60 cover writing instances.

## 5. Top 10 next

Order: writable items first, then blocked and low items, each group by score, then by D (demand), then by C (centrality). Items 1-5 are writable now; items 6-7 are blocked; items 8-10 are low or held. **The pool is thin.** Demand is 1 or 2 everywhere, and there is no outside request behind any item. Items 6 and 7 are blocked; items 9 and 10 are held or low.

1. **M-new-3 Coming from play-json, json4s or uPickle** (P1, 12; D2 C3 G3 F3 O1). The biggest gap with a clean fit: the schema codec core has no migration page for three large libraries. Mapping reuses M-01 and H-02.
2. **M-new-4 Coming from scala-logging, log4cats or SLF4J** (P2, 10; D2 C2 G3 F2 O1). Logging is a daily task, but the guide has to state that there is no SLF4J bridge yet.
3. **M-new-5 Coming from refined** (P2, 10; D2 C2 G2 F3 O1). Cheap to write, because Iron (H-58) and validated newtypes (H-04) exist; the value is the mapping table.
4. **H-new-24 Troubleshooting: hangs and internal errors in streams and Async** (P2, 10; D1 C3 G3 F2 O1). Needs a Troubleshooting sidebar topic. Re-check each bug before writing (see §3.2).
5. **T-new-3 Build a CSV importer into SQLite** (P2, 10; D1 C3 G2 F2 O2). Fills the import direction that H-11 and H-20 leave open. Scala 3 only.
6. **H-new-25 Batch a stream by count or by time** (P3, 10, blocked by X-new-1). Write only after the library gets a time-window operator.
7. **H-new-27 Timeouts and retries around Async** (P3, 10, blocked by X-new-3). Write only after the library gets a timer.
8. **H-new-23 Scala 2.13 compile errors and ambiguous imports** (P2, 9; D1 C3 G2 F2 O1). Useful to cross-build users; no outside request.
9. **M-new-1 Coming from scala-yaml or circe-yaml** (P2, 9; held on #1747).
10. **H-new-17 Trace a streaming pipeline and a scoped resource** (P3, 8). Only if the coordinator wants a P3 slot.

**Held, not ranked:** H-new-3 (blocked on #1747), H-new-16 (blocked on X-01), H-new-11 and H-new-12 (held).

## 6. Doc errors and library pointers found on the way

These are one-line pointers. Full entries go to the coordinator separately.

- **Refresh correction:** the 2026-11-07 pass carried forward the 2026-10-29 check of issues 946, 1002 and 10164 (`refresh.md` line 100) without re-checking them. `gh` now shows #946 and #1002 are merged pull requests, and 10164 does not exist on `zio/zio-blocks`. Correct the refresh's §3 row (done in §3.2 here).
- `docs/guides/instrument-a-program-with-logs-traces-and-metrics.md:562` says the reader must write a custom exporter to ship telemetry. The OTLP exporters are still `private[otel]`, so the sentence is true of the built-in path. The 2026-11-07 pass said the reference custom-exporter page shows the exporter path, so the sentence may need rewording; this pass did not re-check that reference page.
- `docs/guides/telemetry-guide.md:1117` says "There's no bridge today" to SLF4J or JUL. Correct as of this pass; it is the reason for F = 2 on M-new-4.
- `Table.createTable` emits no `PRIMARY KEY`. The cause is the DDL emitter, `sql/shared/src/main/scala/zio/blocks/sql/Ddl.scala:64-71` (it writes a column and `NOT NULL`, and no key); the bug is documented at `sql/shared/src/main/scala/zio/blocks/sql/Table.scala:107`. Already in `library-issues.md`; it limits anything that upserts (H-67, and any future import that needs upsert).
- `Stream#grouped` is `chunked(n)` with no time limit (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:457`). Not a doc error; it is why X-new-1 exists.
- No security bugs were found in this pass. Any security-adjacent item is withheld from this file (see §1).
