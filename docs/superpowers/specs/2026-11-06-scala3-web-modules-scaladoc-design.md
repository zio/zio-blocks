# projection, dataMigration, htmx and datastar Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-sql-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

Four Scala-3-only modules have comments on 390 of 987 public API members (40%); 597 have none.

| Module | Documented | Notes |
|---|---|---|
| `projection` | 167 / 256 | `zio.blocks.projection` 155 / 218, `.testing` 12 / 38 |
| `dataMigration` | 50 / 61 | `zio.blocks.data.migration` |
| `htmx` | 96 / 418 | `zio.http.htmx` 76 / 272, `.headers` 20 / 146 |
| `datastar` | 77 / 252 | `zio.http.datastar` |

Nobody has checked that the existing comments are true. The earlier rollouts found 15% to 42% of inherited comments wrong or imprecise. Nine `{{{` blocks (projection 5, dataMigration 1, datastar 3) are never compiled. `--drift` is empty for all four.

## Goal

Every public member has a true, short, useful comment. The tier-1 types have an overview, compiled examples and a reference-page link. CI compiles every example on Scala 3.9.0 and 3.10.0. `--drift` stays empty.

## Decisions (Milad, 2026-11-06)

- **Checker:** the four modules join `scaladoc-check-scala3` through `scaladocCheckedScala3Modules` (`projection.jvm`, `dataMigration.jvm`, `htmx.jvm`, `datastar.jvm`). Their examples need no `// scala-3-only` markers. `dataMigration`, `htmx` and `datastar` get `packageOverrides` entries (`zio.blocks.data.migration`, `zio.http.htmx`, `zio.http.datastar`), each with an extractor unit test in `scaladoc-check`. The checker only compiles examples; behaviour is probed separately with scala-cli.
- **One PR.**
- **Tier 1:**
  - projection: `Projection`, `ProjectionEngine`, `EventStore`, `ProjectionStore`, `EventTag`, `TagResolver`, `FieldUpdate`, `TestEngine`, `SQLiteProjectionStore`;
  - dataMigration: `SmallMigrator`, `LargeMigrator`, `TinyMigrator`, `ShadowTable`, `QueueTable`, `TargetStrategy`;
  - htmx: `HxSwap`, `HxTarget`, `HxTrigger`, `HxParams`, `HxSync`, `HtmxHeaders`, `HxLocation` and the attribute-value types;
  - datastar: `DatastarEvent`, `Signal`, `DataOn`, `DatastarAttributes`, `EventModifier`.
  Every other public member is tier 2 (with `@see` to its reference page when one exists) or tier 3.
- **AGENTS.md:** the `scaladoc-check-scala3` sentence names `projection`, `dataMigration`, `htmx` and `datastar`.

## Layout

- `projection`: `shared/` (21 files incl. `testing/`) and `jvm/` (`SQLiteProjectionStore`, `TransactorCacheLive`, ...). JVM only. Packages `zio.blocks.projection`, `zio.blocks.projection.testing`.
- `dataMigration`: 8 files in `shared/` and `jvm/`. Package `zio.blocks.data.migration`; one file declares `package zio.blocks.sql`, so its examples need an explicit `import zio.blocks.sql.{zio => _, _}` (checked in the first gate run).
- `htmx`: 13 files in `shared/`, packages `zio.http.htmx` and `zio.http.htmx.headers`; a package object in `zio.http`. Targets htmx 2.x. Depends on `html`, `schema` and `http-model`, whose Scaladoc is complete: link with `[[zio.blocks.html.Dom]]` and similar.
- `datastar`: 25 files in `shared/`, directory `zio/blocks/datastar` but package `zio.http.datastar`. A `scala-2/` copy of `SignalVersionSpecific` exists although the module builds for Scala 3 only; keep the text of both copies identical.
- Reference pages: `docs/reference/projection/` (7), `docs/reference/data-migration.md`, `docs/reference/htmx/` (10), `docs/reference/datastar/` (5).
- `main` equals `v0.0.56` for `dataMigration`, `htmx` and `datastar`. `projection` differs in 12 main files (1,112 inserted lines): probe projection claims about those files against a local publish (`publishLocal` on 3.9.0 with `ivy2Local`), the rest against 0.0.56.
- No test in these modules reads source text (grep for `readAllLines`, `readString`, `src/main` is empty), so a comment cannot fail their own tests.

## Known bugs to reflect (called "a known bug" in the text)

htmx (7): `HxSwapOob.using` with modifiers, `HxLocation` relative targets, non-ASCII header values, `String` URL not percent-encoded, fragment responses breaking Back, `HX-Boosted`, non-object `JsonValue`. datastar (1): `patchElements` and `patchSignalsRaw` drop lines after the first. dataMigration (4): `LargeMigrator#complete`, `LargeMigrator#run`, `ShadowTable` `LIKE`, `init()` and the queue table. projection: none recorded; any found go to the coordinator.

## Tasks

- **Tooling (controller):** list entries, three overrides and tests, the style guide's module list, AGENTS.md.
- **Task 1 (Opus), projection:** 24 files, tier 1, existing-comment fact-check, the 5 existing blocks made to compile.
- **Task 2 (Opus), htmx:** 13 files and the package object; header encoding and rendered values asserted as full strings.
- **Task 3 (Opus), datastar and dataMigration:** 25 + 8 files; SSE event output and attribute values probed; migration behaviour probed with SQLite via scala-cli.
- **Task 4 (Sonnet):** the tier-2/3 gaps still listed after Tasks 1 to 3, with a probe per behavioural claim.
- **Review:** fresh-context Opus reviewers on disjoint module groups (each re-runs its share of bug reproducers and samples untouched comments), a fix round, a Sonnet re-review of the fix diff.

Only one agent runs sbt at a time (memory); the others use scala-cli. Writers commit with explicit paths.

## Gates

1. `++3.9.0; scaladoc-check-scala3/test; ++3.10.0; scaladoc-check-scala3/test`, and `++3.9.0; scaladoc-check/test` (extractor tests).
2. `<module>/doc` for each module (JVM and JS where cross-built) on 3.9.0, no new warnings against the base (read the logs, not the exit code).
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>`: 100% per module; `--drift` empty or justified.
4. `comments-only.py 07f71de18 <module>` prints 0 code changes for all four.
5. `++3.9.0; check; headerCheckAll`; `scalafmtAll`; `scalafmtSbt` for `build.sbt`.

## Out of scope

Library code changes, reference-page rewrites (errors go to the coordinator as docs-issues entries), `docs/dev/` files, other modules, CI files.
