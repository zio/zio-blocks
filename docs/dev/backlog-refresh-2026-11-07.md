# Backlog refresh (2026-11-07): new tutorial and how-to candidates

Compiled 2026-11-07 on branch `backlog-research-1107` (base `140ef76a6`, main). A proposal only: nothing here is in `docs/dev/backlog.md` until the coordinator adds it. IDs are placeholders (`T-new-n`, `H-new-n`). Milad picks the items; the coordinator folds the picks into the backlog.

Scores follow backlog §2: `total (D/C/G/F/O)`, each 1-3. **P0** ≥ 13, **P1** 11-12, **P2** 9-10, **P3** ≤ 8 or F = 1.

## 1. Method and sources

**What this pass did.** It re-scored the 20 candidates from the 2026-10-29 refresh that Milad did not choose. It mapped published modules to the guides that teach them. It checked recent issues and pull requests, and it looked up peer-library docs. Every candidate was scored with the five criteria in backlog §2.

**Re-check of the 2026-10-29 candidates.** The 2026-10-29 pass had 30 candidates. Ten were picked and became H-54 to H-63 (backlog §5.13). Of those ten, H-57 to H-60 are written, H-54, H-55, H-56, H-61 and H-62 were skipped by Milad, and H-63 is still open. This pass does not re-propose the five skipped items. The 20 unchosen candidates are re-scored in §3.

**Sources used.**

1. Backlog: `docs/dev/backlog.md` on the latest backlog branch `khajavi/backlog-status-1107` (this branch's copy is a little older). Sections §2 (scoring), §4-§6 (existing items), §5.12 (reference-only modules), §5.13 (the 2026-10-29 items) and §7 (blocked topics).
2. Refresh report: `docs/dev/backlog-refresh-2026-10-29.md`, §3 (candidates) and §4 (dropped).
3. Repo: the 80 files under `docs/guides/`, `docs/sidebars.js`, and the reference pages under `docs/reference/`. Each module was checked by grepping guides for its `zio.blocks.<module>` imports and its `*-examples` projects.
4. Library code on `main`, cited as `file:line`. Where a line number from the 2026-10-29 report had moved, the current line is given.
5. GitHub: `gh issue list` and `gh pr list` for `zio/zio-blocks`, from 2026-10-29 on.
6. Peer docs (WebSearch, standard mode). Search snippets were read, not full pages, so the peer cites below are "what the page says in the snippet". Each one is listed in §3.

**Limits of the demand evidence (said plainly).**

- No GitHub issue was created on `zio/zio-blocks` since 2026-10-29. The newest issue is #1780 (2026-10-20), and every issue in that list is by Milad or in the list from the 2026-10-29 pass.
- Merged pull requests since 2026-10-29: 65. Of those, 62 are by Milad and 3 by the Scala Steward bot. None are from outside contributors.
- Outside contributors are still active in open pull requests: #1938 (double-escaping in `html"..."`, by `duongynhi000005-oss`) and #1747 (configurable field name mapping in `YamlCodecDeriver`, by `fshp`). #1747 matters for H-new-3 (see §3).
- The 2026-10-29 pass also found almost no outside demand. That is still true. Reddit, X and Discord were not checked in this pass; they were unreachable on 2026-10-29.
- So the demand score (D) rests on peer docs and on coverage gaps, not on user requests. D = 1 means "no evidence beyond our own backlog".

**Scala version rule.** Every install step must show both `zio-blocks-*` and the Scala 3.9+ name `zio-blocks-next-*` (plus `-experimental`). sql, sql-zio, dataMigration, projection, datastar and htmx are Scala 3 only.

## 2. Coverage map

Counts are how many guide files under `docs/guides/` import the module or use its examples project. "Reference" means the module has pages under `docs/reference/`.

| Module | Guides (count) | Reference | Gap or note |
|---|---|---|---|
| schema | 54 | yes | Well covered. |
| schema-yaml | 4 | yes | `read-and-write-yaml`. Hand-written YAML (front matter) not taught: H-new-3. |
| schema-csv | 2 | yes | `read-and-write-csv-and-tsv-files`. |
| schema-avro | 4 | yes | `encode-and-decode-records-with-avro`. |
| schema-bson | 1 | yes | `store-case-classes-in-mongodb`. |
| schema-toon | 4 | yes | `llm-data-with-toon`. |
| schema-xml | 1 | yes | Reference-only (backlog §5.12). |
| schema-messagepack | 0 | yes | No guide. H-16 skipped by Milad. |
| http-model | 4 | yes | Serving, SSE, auth guides. CORS and static files not taught: H-new-11, H-new-12. |
| http-model-schema | 0 | yes | No guide. Not proposed here. |
| endpoint | 1 | yes | M-12 blocked (X-02 to X-04). RouteTree: H-new-15. |
| jwt | 2 | yes | `issue-and-verify-jwts`, `protect-endpoints-with-jwt`. |
| html | 6 | yes | `your-first-html-page`. |
| htmx | 2 | yes | `add-interactivity-with-htmx`, `return-htmx-fragments-and-response-headers`. |
| datastar | 1 | yes | `build-a-live-dashboard-with-datastar`. |
| streams | 9 | yes | Well covered for files, sockets and pipelines. NDJSON and paginated APIs: H-new-8, H-new-9. Error and hang troubleshooting: H-new-24. |
| async | 6 | yes | `async-getting-started`, `wrap-callback-and-future-apis`. No `race`, `timeout` or retry (X-new-3). |
| chunk | 14 | yes | Used throughout; no dedicated guide (H-45 skipped). |
| scope | 8 | yes | `compile-time-resource-safety-with-scope`, `fix-scope-and-wire-compile-errors`. |
| config (+ hocon, json, yaml) | 7 | yes | `load-configuration-files`, `typed-config-loader`. |
| typeid | 5 | yes | Reference-only (§5.12). |
| context | 1 | yes | Covered as part of Wire DI (§5.12). |
| maybe | 4 | yes | Used throughout. |
| combinators | 0 | yes | Plumbing (§5.12). |
| markdown | 0 | yes | Reference-only (§5.12). No guide. |
| mediatype | 2 | yes | Reference-only (§5.12). |
| openapi | 2 | yes | `write-and-export-openapi`, `generate-scala-types-from-openapi`. |
| codegen | 2 | yes | Same two guides. |
| smithy | 1 | yes | `generate-scala-code-from-smithy`. Service traits not generated: H-new-19. |
| mux | 2 | yes | `getting-started-with-mux`, `run-a-mux-over-a-socket`. |
| ringbuffer | 0 | yes | Six reference pages cover it (§5.12). |
| telemetry (+ otel) | 6 | yes | Logs, traces, metrics, testing, correlation. Exporting to a collector: H-new-16 (otel guides count shows 0 because the telemetry guides import `telemetry`). |
| sql | 16 | yes | Well covered. Batch writes: H-new-6. Logging SQL: partly covered (`write-joins-and-inspect-generated-sql` mentions `SqlLogger`); H-54 skipped by Milad. |
| sql-zio | 3 | yes | `sql-from-zio`. |
| projection | 0 | yes | **No guide.** Only reference pages. T-14 is blocked on #1744. T-new-1 dropped (§4). |
| data-migration | 2 | yes | `migrate-table-rows-online`. |

**Largest gaps (no guide, or reference only):** projection (no guide at all), markdown, ringbuffer, combinators, http-model-schema, schema-messagepack.

## 3. Candidates

18 candidates kept: 1 tutorial, 15 how-to guides and 2 migration guides (M-new-1, M-new-2).

Sub-scores: D = user demand, C = module centrality, G = coverage gap, F = feasibility, O = onboarding value.

| ID | Title | Module(s) | Score (D/C/G/F/O) | Priority | Peer doc cited | Evidence and status | Notes |
|---|---|---|---|---|---|---|---|
| T-new-2 | Share one model between a Scala.js page and a JVM server | schema, streams (+ http-model, Scala.js) | 10 (2/3/2/2/1) | P2 | Scala.js shared-code pattern (general; no single peer page retrieved) | Scala.js is mentioned in about 30 guides, but none is a shared-model tutorial | F = 2: the browser side needs `scala-js-dom`, which is a new example dependency. Ask first (AGENTS.md "Ask First"). Needs a cross-built examples build. |
| H-new-3 | How to read Markdown front matter and other hand-written YAML | schema-yaml | 9 (2/2/2/2/1) | P2 | None retrieved | None | Workaround today: derived YAML codecs always use kebab-case keys (`YamlCodecDeriver.scala:64`; the 2026-10-29 report cited `:121`, which has moved). Open PR #1747 adds configurable field names. **Wait for #1747 before writing.** |
| H-new-6 | How to insert and update many rows in batches | sql | 9 (2/3/1/2/1) | P2 | doobie and Magnum batch inserts (not retrieved; Magnum mapping is in `migrate-from-magnum-or-doobie`) | Partly covered: `insertBatch` appears in `migrate-table-rows-online`, `stream-large-query-results`, `query-dsl-sql` and the Magnum migration | F = 2: `insertOrUpdateBatch` needs a primary key, and `Table.createTable` emits none (`sql/shared/src/main/scala/zio/blocks/sql/Table.scala:107`, a known bug in `library-issues.md`). Scala 3 only. |
| H-new-7 | How to run Async calls in parallel and use the first result | async | 8 (2/2/1/2/1) | P3 | None retrieved | Already taught in `docs/reference/async.md:1368-1388`, which races N computations with `Async.selector` (`async/shared/src/main/scala/zio/blocks/async/Async.scala:527`). No `race`, `timeout` or `retry` method exists (grep on `Async.scala`) | G = 1 because the reference covers the first-result case. A guide would add a timeout pattern only, which is glue (X-new-3). |
| H-new-8 | How to read and write JSON Lines (NDJSON) with streams | streams, schema (json) | 10 (2/3/2/2/1) | P2 | Deno JSON Lines example, `https://docs.deno.com/examples/json_lines/`; Effect NDJSON decoder, `https://www.effect.website/docs/v4/api/effect/unstable/encoding/Ndjson` (both are other ecosystems; no fs2 page retrieved) | Line streams are taught in `your-first-streaming-pipeline` and `stream-files-and-sockets`; NDJSON is not named | F = 2: no `lines` or `utf8` helper was found under `streams/shared/src/main/scala` (a grep for `def lines` and `def utf8` found nothing). The guide writes a line splitter; say so. |
| H-new-9 | How to turn a paginated API into a stream | streams, schema | 10 (1/3/2/3/1) | P2 | fs2 unfold pattern, via community gist and Earnest blog (no official fs2 page retrieved): `https://medium.com/earnest-engineering/unfolding-paginated-apis-195f5608bcc9` (the reviewer got HTTP 403 from it, so it is unverified; treat as "not retrieved") | `streaming-library-migration.md` (around line 585) already uses `Stream.unfold` for a paged API. Only the async variant is new | F = 3: `Stream.unfold` (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:3681`) and `unfoldAsync` (`:3693`) are in v0.0.56 (checked with `git show v0.0.56`: `:3302`, `:3314`). A third-party HTTP client is needed; use the H-35 adapter. |
| H-new-11 | How to serve static files with the right Content-Type | http-model (+ H-35 adapter) | 10 (2/2/3/2/1) | P2 | Tapir "Serving static content", `https://tapir.softwaremill.com/en/stable/endpoint/static.html` (file serving lives in a separate `tapir-files` module, since 1.3.0) | No guide and no reference page; no static-file handler in http-model or endpoint (checked: no `static`/`StaticFile` in `http-model/shared` or `endpoint/shared`, except `SegmentCodec.scala`) | F = 2: glue in the JdkHttp adapter. **Security-adjacent:** file serving needs a guide-level safety review before writing. Details, if any, go to the coordinator privately, not in this file. |
| H-new-12 | How to answer CORS preflight requests and set response headers | http-model, endpoint | 10 (2/2/3/2/1) | P2 | http4s CORS middleware, `https://http4s.org/v1/docs/cors.html` (the snippet shows `CORS.policy.withAllowOriginAll(service)`) | No CORS code in `http-model/shared` or `endpoint/shared` (checked). No guide | F = 2: no middleware type exists, so the guide writes a `Handler => Handler` function. **Security-adjacent** (headers). Same coordinator review as H-new-11. |
| H-new-13 | How to edit, delete and load rows on demand with htmx | htmx (+ http-model) | 8 (2/1/2/2/1) | P3 | htmx 2 reference (`hx-trigger` polling and lazy loading) | `docs/reference/htmx/hx-sync.md`, `hx-trigger.md` (polling near line 274, lazy loading near line 386); `add-interactivity-with-htmx` and `return-htmx-fragments-and-response-headers` exist | Scala 3 only. Mostly covered by the two guides and the reference; fold any new part into them. |
| H-new-14 | How to add loading indicators, polling and lazy loading with Datastar | datastar (+ http-model) | 8 (2/1/2/2/1) | P3 | Datastar reference | `docs/reference/datastar/events.md` (`dataOnIntersect` near line 152, `dataOnInterval` near line 179); `build-a-live-dashboard-with-datastar` exists | Scala 3 only. Server-to-client only. Fold into the dashboard guide rather than a new guide. |
| H-new-15 | How to dispatch requests with RouteTree and build links from routes | endpoint | 9 (1/2/2/3/1) | P2 | None retrieved | Reference answers the task (`docs/reference/endpoint/route-tree.md`, `route-pattern.md`); one guide mentions RouteTree once | F = 3: no server needed. Value is the guide, not new API. |
| H-new-16 | How to export telemetry to an OpenTelemetry Collector | telemetry, otel | 9 (2/2/2/2/1) | P2 | OTel OTLP exporter spec, `https://opentelemetry.io/docs/specs/otel/protocol/exporter/` (default timeout 10 s; per-signal paths). No otel4s page retrieved | `telemetry-guide.md` and `instrument-a-program...` say "ship it later"; `instrument-a-program...:562` says the reader must write an exporter. The exporters are `private[otel]`: `otel/src/main/scala/zio/blocks/telemetry/otel/OtlpJsonTraceExporter.scala:26` and the other two | F = 2: glue. Blocked in part by X-01. Also fix the wording at `instrument-a-program...:562` (see §6). JDK 21+. |
| H-new-17 | How to trace a streaming pipeline and a scoped resource | telemetry, streams, scope | 8 (1/2/2/2/1) | P3 | None retrieved | `correlate-logs-and-traces` covers context; no pipeline tracing | Check before writing that the span reaches stream stages on `*Async` terminals (untested). JDK 21+. |
| H-new-19 | How to generate service traits from Smithy operations or OpenAPI paths | codegen, smithy, openapi | 9 (2/2/2/2/1) | P2 | Smithy and OpenAPI generator docs (not retrieved; Tapir's OpenAPI tutorial is the usual peer) | `generate-scala-code-from-smithy` and `generate-scala-types-from-openapi` exist; both say paths and operations are "not generated" | F = 2: glue, as in T-13 and H-44. |
| H-new-23 | How to fix Scala 2.13 compile errors and ambiguous imports | all (cross-build) | 9 (1/3/2/2/1) | P2 | None retrieved | `fix-scope-and-wire-compile-errors` (H-31) is the model; none for Scala 2 | F = 2: runtime objects needed by the fix are `private[...]` (`JsonInterpolatorRuntime.scala:28`, `YamlInterp...`, per the 2026-10-29 report). |
| H-new-24 | How to fix hangs and internal errors in streams and Async | streams, async | 10 (1/3/3/2/1) | P2 | None retrieved | No troubleshooting guide. Issues were checked on 2026-10-29 (946, 1002, 10164). `Stream.scala:3761-3764` (the lines the 2026-10-29 report cited are stale) | F = 2: depends on the bugs staying open; re-check each one before writing. Needs the proposed **Troubleshooting** sidebar topic. |
| M-new-1 | Coming from scala-yaml or circe-yaml | schema-yaml | 9 (1/2/3/2/1) | P2 | None retrieved | No YAML migration guide | F = 2: kebab-case keys only until #1747 merges. |
| M-new-2 | Coming from Quill | sql | 9 (1/2/3/2/1) | P2 | Quill docs, `https://zio.dev/zio-quill/` and `https://github.com/zio/zio-quill`: compile-time query generation, optional DB check, `quill-jdbc-zio` contexts. The Quill-to-doobie guide was not retrieved | No Quill guide. `migrate-from-magnum-or-doobie` is the nearest model | F = 2: Quill's compile-time query generation has no equivalent; say so. Scala 3 only. |

**Priority grouping:** 14 candidates are P2. Four are P3 (score 8): H-new-7, H-new-13, H-new-14 and H-new-17. H-new-13 and H-new-14 are kept rather than dropped because their reference pages cover most of the task, so a guide would only add the glue.

### 3.1 Peer-doc cites (summary)

- doobie logging, for H-54-style SQL logging (kept for the record): `https://typelevel.org/doobie/docs/10-Logging.html`.
- http4s CORS: `https://http4s.org/v1/docs/cors.html`.
- Tapir static content: `https://tapir.softwaremill.com/en/stable/endpoint/static.html`.
- Pekko Projection testing (for T-new-1, dropped): `https://pekko.apache.org/docs/pekko-projection/1.1/testing.html`.
- OTel OTLP exporter spec: `https://opentelemetry.io/docs/specs/otel/protocol/exporter/`.
- JSON Lines, other ecosystems: `https://docs.deno.com/examples/json_lines/`.
- Quill: `https://zio.dev/zio-quill/`.
- Unfolding paginated APIs (community): `https://medium.com/earnest-engineering/unfolding-paginated-apis-195f5608bcc9`.

No fs2, otel4s, smithy4s, scala-yaml or circe-yaml page was retrieved. Those cites need a second look before any guide is written.

## 4. Dropped (and why)

**Dropped by this pass (re-scored, then dropped):**

| ID | Title | Score | Reason |
|---|---|---|---|
| T-new-1 | Build and test a projection read model in memory | 7 (2/1/1/2/1), P3 | Backlog §5.3 already decided projection testing gets no separate how-to, because `docs/reference/projection/testing.md` covers `TestEngine` (`projection/shared/src/main/scala/zio/blocks/projection/testing/TestEngine.scala:177`). The in-memory engine is usable, but issue #1744 (sixteen engine problems) is still open. Revisit only if the maintainer wants a test-only tutorial. |
| H-new-18 | How to report pool and queue sizes with callback instruments | 7 (1/1/1/3/1), P3 | `docs/reference/telemetry/metrics/meter.md` already teaches `buildWithCallback` (`telemetry/shared/src/main/scala/zio/blocks/telemetry/Meter.scala:251`) with a gauge example. A guide would repeat it. Fold a short section into the telemetry guide instead. |

**Skipped by Milad in the 2026-10-29 pass (not re-proposed here):** H-54 (SQL logging with telemetry), H-55 (Schema derivation and codec errors), H-56 (sql errors: DDL, NULL columns, column mapping), H-61 (round-trip property tests and golden files), H-62 (configuration-loading errors). H-63 (AI coding assistant) is still open and is not a candidate in this pass.

**Also not proposed, on purpose:** `markdown`, `ringbuffer`, `combinators`, `typeid`, `mediatype` and `context` stay reference-only (backlog §5.12). `http-model-schema` and `schema-messagepack` have no guide, but no peer or user evidence was found this pass, so they are not candidates.

## 5. Top 10 next

Order is by score, then by the sub-scores D (demand) and then C (centrality). Items with the same D and C are listed in no particular order.

Items 1-6 score 10. Items 7-10 score 9. Items 7-10 all have D = 2 and C ≥ 2, and there are exactly four of them with that profile, so no further tie-break is needed. The 9-point items with D = 1 (H-new-23, H-new-15, M-new-1, M-new-2) fall below them.

1. **H-new-8** How to read and write JSON Lines (NDJSON) with streams (P2, 10; D2 C3). Streams is central, and the guide fills a clear format gap.
2. **T-new-2** Share one model between a Scala.js page and a JVM server (P2, 10; D2 C3). Scala.js is in about 30 guides but has no shared-model tutorial. Needs a new example dependency: ask first.
3. **H-new-11** How to serve static files with the right Content-Type (P2, 10; D2 C2). Security-adjacent: needs a safety review before writing.
4. **H-new-12** How to answer CORS preflight requests and set response headers (P2, 10; D2 C2). Security-adjacent: same review as item 3.
5. **H-new-9** How to turn a paginated API into a stream (P2, 10; D1 C3). The `unfold` API exists on v0.0.56, so this needs no library change. Overlaps `streaming-library-migration`, so write it as a short section or a pointer, not a full guide.
6. **H-new-24** How to fix hangs and internal errors in streams and Async (P2, 10; D1 C3). Needs the Troubleshooting topic. Re-check each bug before writing.
7. **H-new-6** How to insert and update many rows in batches (P2, 9; D2 C3). Core sql, but needs the primary-key bug (`Table.scala:107`) worked around.
8. **H-new-3** How to read Markdown front matter and other hand-written YAML (P2, 9; D2 C2). **Wait for #1747** (configurable YAML field names) before writing, or the guide documents a workaround that is about to go away.
9. **H-new-16** How to export telemetry to an OpenTelemetry Collector (P2, 9; D2 C2). Blocked in part by X-01; the guide would be glue code.
10. **H-new-19** How to generate service traits from Smithy operations or OpenAPI paths (P2, 9; D2 C2). Extends two existing guides; glue code.

**Runners-up (P2, 9, D1):** H-new-23, H-new-15, M-new-1 (wait for #1747), M-new-2 (Quill; Scala 3 only). **P3 (8):** H-new-7, H-new-13, H-new-14, H-new-17.

## 6. Doc errors and library pointers found on the way

These are one-line pointers. Full entries, if wanted, go to the coordinator separately.

- `docs/guides/instrument-a-program-with-logs-traces-and-metrics.md:562` says the reader must write a custom exporter to ship telemetry. The reference custom-exporter page shows the exporter path, so the sentence needs rewording (also flagged on 2026-10-29).
- Stale line references in the 2026-10-29 report: `SqlLogger.scala:21-24` is now the trait at `sql/shared/src/main/scala/zio/blocks/sql/SqlLogger.scala:47-53`; `YamlCodecDeriver.scala:121` is now `:64`; `Stream.scala` lines for hangs have moved (see H-new-24).
- Library: `Table.createTable` emits no `PRIMARY KEY` (`sql/shared/src/main/scala/zio/blocks/sql/Table.scala:107`). Already recorded in `library-issues.md`; it limits H-new-6.
- Library: the OTLP exporters are `private[otel]` (X-01), which limits H-new-16.
- No security bugs were found in this pass. Security-adjacent candidates (H-new-11, H-new-12) are flagged in §3 for a safety review.
