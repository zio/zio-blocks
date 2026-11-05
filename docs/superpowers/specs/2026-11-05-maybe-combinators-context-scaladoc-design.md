# maybe, combinators and context Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

Three small modules have almost no Scaladoc. At `c2e4a39fd` the coverage script reports `maybe` 5 of 135 public members documented (3%), `combinators` 7 of 79 (8%) and `context` 13 of 37 (35%): 25 of 251. Nobody has checked that the 25 existing comments are true; earlier rollouts found 15–42% of inherited comments wrong or imprecise. Two `{{{` blocks (`ContextHas.scala`) are never compiled, and `maybe` and `combinators` have none. Three `maybe` members have different text in their `scala-2/` and `scala-3/` copies (`class Present`, `def present`, `object Absent`).

The two Scala versions implement these modules differently: `Maybe` is a sealed trait (`MaybeValue`) on Scala 2 and an opaque union type on Scala 3; `combinators` derives its instances with Scala 2 macros and with Scala 3 match types and givens, and `Unions` exists on Scala 3 only. One known bug already shows this: on Scala 2, `Tuples` nests a tuple on the right instead of concatenating it (filed by the async rollout, `Tuples.scala:71-121`).

## Goal

Every public member of the three modules has a true, short, useful comment, identical in every Scala-version and platform copy. Where the versions really behave differently, the shared text says so ("On Scala 2, ..."), verified by a probe on both. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty.

## Decisions (Milad, 2026-11-05)

- **One PR** (tooling, existing blocks made to compile, Scaladoc for all three modules).
- **Tier 1:** `Maybe` (with `Present` and `Absent`), `MaybeSyntax`; `Concat`, `Tuples`, `Eithers`, `Unions`; `Context`, `ContextHas`.
- **One Opus writer** for all three modules (tier 1, tier 2/3 gaps, fact-check of every existing comment, bug write-ups), then a fresh-context Opus fact-check review, a fix round and a Sonnet re-review of the fix diff.
- **Scala 2/3 differences:** identical text in both copies; each real difference is named in that text and probed on both versions.

## Layout

All three are `crossProject(JSPlatform, JVMPlatform)`, `CrossType.Full`, with the same `crossScalaVersions` as `scaladoc-check`. Packages `zio.blocks.maybe`, `zio.blocks.combinators`, `zio.blocks.context`, so the wrapper import needs no override.

- `maybe` (depends on `typeid`): `Maybe.scala` and `MaybeCompat.scala` in `scala-2/` and `scala-3/`; shared `MaybeSyntax.scala` and `package.scala`.
- `combinators`: `Tuples.scala`, `Eithers.scala`, `ConcatCompanionPlatform.scala` in `scala-2/` and `scala-3/`; `Unions.scala` in `scala-3/` only; shared `Concat.scala`.
- `context` (depends on `typeid`): shared `Context.scala`, `ContextEntries.scala`, `ContextHas.scala`, `Cache.scala`; `package.scala` in `scala-2/` and `scala-3/`; `PlatformCache.scala` in `jvm/` and `js/`.

Main sources at `c2e4a39fd` are byte-identical to `v0.0.56`, so claims are probed against the published 0.0.56 artifacts and bug citations use base lines ("lines at v0.0.56 = c2e4a39fd").

Reference pages: `docs/reference/maybe.md`, `docs/reference/combinators.md`, `docs/reference/context.md` (`https://zioblocks.com/reference/<page>/`). Errors found there go to the coordinator as `docs-issues.md` entries; the pages are not edited here.

## Claims to probe

- `Maybe`: what allocates (is `Present` allocated, nested `Maybe`, `Maybe.present(null)`), equality and `toString` per version, `Option` interop, `WithFilter`.
- `combinators`: result types per arity and per Scala version, associativity, `Unit` and `Nothing` operands, nested tuples and `Either`s, the Scala 2 right-tuple bug.
- `Context`: lookup by subtype versus exact type, the cache and its thread safety per platform, `++` override order, error messages for missing entries.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `<module>JVM/doc` and `<module>JS/doc` for each module on 3.9.0 and 2.13.18: no new warnings (read the log, not the exit code)
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>` (251/251 in total) and `--drift` (empty)
4. `comments-only.py c2e4a39fd <module>` for each module: `0 with code changes`
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes (bugs are reported, not fixed), reference-page rewrites, `docs/dev/` files, other modules.
