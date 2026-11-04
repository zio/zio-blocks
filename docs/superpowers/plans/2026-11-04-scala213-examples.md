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

## Fix patterns

1. `@main def X(): Unit = {...}` -> `object X { def main(args: Array[String]): Unit = {...} }`. The class name and `runMain` target stay the same on Scala 3. Helper definitions that sit at top level next to the `@main` move into the object.
2. `import x.*` -> `import x._`; `given`/`using` -> `implicit`; `summon[T]` -> `implicitly[T]` where the library API allows it.
3. Genuinely Scala 3-only files (`derives`, `enum`, union types, context functions `?=>`, `extension`, `inline`/`opaque`) are listed in a `scala3OnlyExamples` set in `build.sbt` and filtered out when `scalaVersion` is 2.x, as in `streams-examples/build.sbt`.
4. Real API gaps on 2.13 (async `Completer` implicit, telemetry `GlobalLogState`): skip the file on 2.x and send the coordinator a library entry. Do not rewrite the example around the gap.
5. Generated sources (openapi): skip the generated Scala 3 output on 2.x unless the generator has a 2.13 option; report.

## Per-build plan

- endpoint, http-model, mux, smithy, scope: pattern 1 for the `@main` files, then re-compile and apply 2/3 for what remains.
- schema: patterns 2 and 3 (large build, iterate on compiler output; Scala 2 reports parser errors per file before typer errors, so expect several rounds).
- async: `summon` -> `implicitly`; the `c ?=>` context-function files and `AsyncShowcaseExample` go into the skip set unless a `Completer` is reachable on 2.13 another way (investigate).
- openapi: investigate the generator options; otherwise skip the generated sources on 2.x.
- telemetry: investigate `GlobalLogState` visibility; if the 2.13 macro really needs it, skip those files and report.

## Order and rules

One commit per build. One sbt build at a time (memory is tight). For each changed build: 2.13 compile, Scala 3 compile, one main run on Scala 3, `scala-cli fmt --check` on the changed files only, mdoc for any guide that embeds a changed file. Then merge origin/main, push to `khajavi`, open the PR, wait for CI.
