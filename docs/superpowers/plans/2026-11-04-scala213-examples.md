# Compile the standalone example builds on Scala 2.13

Date: 2026-11-04. Base: `02856d333` (origin/main). Branch: `scala213-examples`.

## Problem

AGENTS.md says to check an example on Scala 2.13 with `sbt "++2.13.18!" compile` inside the examples build. CI compiles the examples on Scala 3 only, so nothing catches 2.13 breakage, and Scala 2.13 users who copy an example get compile errors. PR #1897 (`be371b36f`) fixed `streams-examples`. This plan does the same for the rest.

## Goal

Every standalone `*-examples` build that has a `_2.13` artifact compiles with `sbt "++2.13.18!" compile`. Scala 3 behaviour stays unchanged. Files that are genuinely Scala 3-only are skipped on 2.x by the #1897 pattern (`Compile / unmanagedSources` filter in `build.sbt`).

## Survey (2026-11-04, `02856d333`, `sbt "++2.13.18!" compile`, JDK 25)

| Build | Files | 2.13 result | First errors / cause |
|---|---|---|---|
| config | 38 | pass | |
| html | 7 | pass | |
| jwt | 18 | pass | |
| streams | 84 | pass | (#1897) |
| endpoint | 6 | fail | `@main def` in all 6 files: `expected start of definition` (19:7) |
| http-model | 51 | fail | `@main def` in 4 files (`BasicHttpRequest.scala:14:7`, ...) |
| mux | 22 | fail | `@main def` in 7 files, then cascading `expected class or object definition` |
| smithy | 13 | fail | `@main def` in 4 files; `derives` in 2 |
| scope | 20 | fail | `@main def` in 17 files; `derives Unscoped` (`ConfigReaderExample.scala:33`) |
| schema | 231 | fail | `derives Schema` (10 files), `enum`, leading `++` continuation line (`BindingResolverSchemaRegistryExample.scala:49`), `extension`, `import x.*`, `@main`-free |
| async | 15 | fail | `Async.promise { c ?=> ... }` context functions (`AsyncShowcaseExample.scala:177`, `Step4/5...`), `summon` (`CompleteExample.scala:23`) |
| openapi | 17 | fail | generated `bookstore-app/.../Bookstore.scala` uses `derives Schema` and `enum` (Scala 3 output) |
| telemetry | 29 | fail | `GlobalLogState` is `private[telemetry]` and macro-expanded log calls reach it from user code on 2.13 (29 errors, all one cause) |

No `_2.13` artifact (out of scope, skipped by the brief): datastar, htmx, sql, projection, capstone, zio-blocks-examples.

Correction to the docs-issues entry: the parser errors "expected start of definition" are `@main def`, not `import x.*`.

## Decision (Milad, 2026-11-04)

It does not matter if Scala 3-only examples are not compiled on Scala 2.13. So no example code is rewritten and no guide changes: each build skips its Scala 3-only sources on 2.x with a `Compile / unmanagedSources` filter, the #1897 mechanism. One PR, one commit per build.

## Approach actually taken

- A content regex per build (`@main`, `derives`, `enum`, `?=>`, `extension`, `given`, `(using`, `summon[`, wildcard `import x.*`, `inline`/`opaque`, top-level `def`/`val`, leading `++`) drops Scala 3-only files on 2.x. schema adds `import zio.sbt.` (same reason as #1897), whole-package and per-file skip sets for `namedtuples` and nine examples that hit 0.0.56 limits on 2.13 (`json"..."`/`yaml"..."` interpolators, migration builder, type inference). mux skips `muxoverasocket` (top-level defs shared by its files). async skips `AsyncShowcaseExample` (implicit `Completer`).
- smithy and openapi: the app subprojects (`orders-app`, `bookstore-app`) compile nothing on 2.x because the generated code uses `derives`/`enum`.
- telemetry: files that use the `log.*` macros are skipped on 2.x (the macro expands to the `private[telemetry]` `GlobalLogState`); `GlobalTelemetrySpec` is skipped with them.
- Trade-off: files that use these constructs drop out of the 2.13 compile silently. endpoint and scope end up with almost nothing compiled on 2.13.

## Order and rules

One commit per build. One sbt build at a time (memory is tight). For each changed build: 2.13 compile, Scala 3 compile, one main run on Scala 3, `scala-cli fmt --check` on the changed files only, mdoc for any guide that embeds a changed file. Then merge origin/main, push to `khajavi`, open the PR, wait for CI.
