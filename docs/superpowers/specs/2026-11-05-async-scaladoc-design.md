# Async module Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `async` module has comments on 105 of 144 public API members (72%: `async` 91/123, `async.internal` 14/21). The count is small, but `Async.scala` alone is 3,565 lines, and most existing comments make claims about behaviour: cancellation, interruption, ordering, which thread runs what, and how the JVM and Scala.js differ. Nobody has checked those claims. The `chunk`, `scope`, `config` and `typeid` rollouts found that 19% to 36% of the comments they inherited were wrong or imprecise, mostly the behaviour claims. Three `{{{` blocks (`package.scala` and both `AsyncCompanionVersionSpecific.scala` copies) are never compiled. Seven members have different text in their version or platform copies: `toFuture` (jvm/js and scala-2/scala-3 `AsyncSyntaxPlatformSpecific`), `fromFuture` (jvm/js `AsyncCompanionPlatformSpecific`), `attempt` (`AsyncCompanionVersionSpecific`), and `flatten`, `orElse` and `unit` (`AsyncSyntaxVersionSpecific`).

## Goal

Every public member of `async` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty. Known bugs are described as they really behave and called "a known bug".

## Decisions (Milad, 2026-11-05)

- **One PR**: the `build.sbt` checker entry, the AGENTS.md lines and all the Scaladoc.
- **Tier 1:** `Async` (the type alias and the companion), `Async.Running`, `Completer`, `Cancelable`, `Failure`, `AsyncSelector`, `AsyncInterop` (jvm and js) and `Pollable`, each with its key methods.
- **Drift:** one identical text in every copy. Each real difference is named in that text ("On Scala 2 ...; on Scala 3 ...", "On Scala.js ...") and checked by a probe on both sides. Target: an empty `--drift`.
- **Fact-check:** all 105 existing comments, including those on `private[...]` members, with a table (member, claim, verdict, evidence).

## Layout

- `async/shared/src/main/scala/` holds `Async.scala`, `AsyncSelector`, `AsyncEncoding`, `Cancelable`, `Completer`, `Failure`, `Pollable`, `package.scala` and `internal/` (`CancellationCleanup`, `CompletedRunning`, `Parker`).
- `scala-2/` and `scala-3/` hold `AsyncCompanionVersionSpecific` and `AsyncSyntaxVersionSpecific`; Scala 2 also has `internal/AsyncMacros` (the `async`/`await` def macro).
- `scala-3-dca/` (`AsyncCpsMonad`, `AsyncDcaTransform`) and `scala-3-dca-direct/` (`AsyncDirect`) are added on Scala 3 by `build.sbt`. The JVM uses both. Scala.js 3.8+ uses `scala-3-dca` plus `js/src/main/scala-3.8/` (`AsyncDirect`, `AsyncJsRuntime`).
- `jvm/` and `js/` hold `AsyncInterop`, `AsyncCompanionPlatformSpecific`, `internal/AsyncRunner` and `internal/PlatformAsync`, plus `scala-2/` and `scala-3/` `AsyncSyntaxPlatformSpecific`. The JVM also has `scala-3/internal/AsyncRuntimeAwait`.
- The package is `zio.blocks.async`, so the wrapper import needs no override. The checker compiles the JVM project's own source list, so the dca directories come along. JS-only files are never compiled by the checker, so an example there is marked `// no-check: Scala.js only` or left out.
- Reference page: `docs/reference/async.md` (`https://zioblocks.com/reference/async/`, anchors allowed).
- `async/*/src/main` at the base (3ac1121a9) equals `v0.0.56`, except a 3-line change in `scala-3/AsyncSyntaxVersionSpecific.scala` (`tap`'s `WrappedPollable` import). Probes run against the published `zio-blocks-async:0.0.56`; bug entries cite base lines as "= v0.0.56".

## Tasks

- **Task 1a (Opus, the only agent that runs sbt):** tooling (`async.jvm` in `scaladocCheckedModules`, an extractor test, the style-guide module list); make the 3 blocks compile; the shared top-level files and the `scala-2`/`scala-3` `AsyncCompanionVersionSpecific` and `AsyncSyntaxVersionSpecific` copies: unify `attempt`, `flatten`, `orElse` and `unit`; tier 1 for `Async`, its companion, `Running`, `Completer`, `Cancelable`, `Failure`, `Pollable` and `AsyncSelector`; fact-check every existing comment in these files; bug write-ups.
- **Task 1b (Opus, scala-cli probes only, no sbt), in parallel with 1a:** every `jvm/` and `js/` file, `shared/.../internal/`, `scala-2/internal/AsyncMacros`, `scala-3-dca*`; unify `toFuture` and `fromFuture`; tier 1 for `AsyncInterop` (jvm and js); fact-check every existing comment in these files, probing Scala.js claims on Scala.js; fill this group's gaps; bug write-ups.
- **Task 2 (Sonnet), after Task 1a:** the tier-2 and tier-3 gaps left in Task 1a's files (`StepFold` family members, `CancelledReplacementProvenance`/`CancelledOutputOwnership`, `object AsyncSelector`/`apply`, `Completer#poll`, `Failure#poll`, the Scala 2 implicit-class wrappers and `start`/`flatten`).
- **Review:** two fresh-context Opus fact-check reviewers on disjoint file groups (A: Tasks 1a and 2; B: Task 1b), each re-running its bug reproducers; one fix round with the authors resumed; a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`, read from the log, not the exit code.
2. `asyncJVM/doc` and `asyncJS/doc` on 3.9.0 and 2.13.18, with no more warnings than at the base.
3. `python3 -I .claude/scripts/scaladoc-coverage.py async` (144/144) and `--drift` (empty).
4. `comments-only.py 3ac1121a9 async` reports 0 files with code changes.
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`.

Before every sbt run, `free -g` must show at least 6 GB available (five other Scaladoc sessions share the machine). `sbt --client shutdown` after each gate round.

## Out of scope

Library code changes, reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files, `streams` and other modules.
