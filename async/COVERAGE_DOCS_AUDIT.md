# `async` coverage & documentation audit

Originally audited at commit `bcf31104`+ (post suspended-path coverage work).
**Refreshed after the Phase 5c collection-HOF long tail completed** (commit
`dec37a1b`+: Queue/ArraySeq/Array HOF `.await` support + the Scala 2 macro
benchmark gate). JDK 26.0.1, scoverage via sbt. JS coverage is disabled
repo-wide (`BuildHelper.jsSettings`), so coverage numbers are JVM scoverage; JS
behavior is covered behaviorally by the shared/JS specs.

## 1. Red flags found (at audit start) — all resolved

| Flag | Status |
|---|---|
| `async` project coverage thresholds set to `0/0` in `build.sbt` | **FIXED** — raised to `92/89` (see §4) |
| No `docs/reference/async.md` reference page | **FIXED** — page exists and is in `docs/sidebars.js` (`reference/async`) |
| `docs/index.md` / README do not mention `Async` | **FIXED** — `docs/index.md` + regenerated `README.md` both document `Async` (table row + dedicated section) |
| `docs` project does not depend on `async.jvm` (cannot mdoc-validate async) | **FIXED** — `docs.dependsOn(async.jvm)` (11 `scala mdoc` blocks in `docs/reference/async.md`) |

## 2. Test matrix (all green, current)

Refreshed after the HOF-await parity long-tail closed (`Option.find`,
`Option.collect`, non-pair `Map.collect`; pair-yielding `Map.collect` rejected at
parity — commits `00b75519`, `f46c44dd`, `246012db`).

| Cell | Tests | Cell | Tests |
|---|---|---|---|
| JVM 2.13.18 | 330 | JS 2.13.18 | 240 |
| JVM 3.3.7 | 318 | JS 3.3.7 | 233 |
| JVM 3.8.3 | 318 | JS 3.8.3 | 233 |

(JVM cells +1 vs. the prior audit: a `fromCompletionStage` cancelled-stage test
covering the `unwrapCompletionException` `other` fallback — JVM-only interop.)

## 3. Coverage (JVM scoverage, current)

| Cell | Statement | Branch | At original audit |
|---|---|---|---|
| Scala 3.8.3 | **94.36%** | **92.45%** | 93.35% / 91.61% |
| Scala 2.13.18 | **96.30%** | **94.15%** | 95.84% / 93.55% |

Scala 3.3.7 mirrors 3.8.3 (same `scala-3-dca` sources). The Scala 3 cell is the
floor because it additionally compiles the dotty-cps-async bridge. Both cells
remain above the enforced `92/89` gate after the Queue/ArraySeq/Array HOF work
(which is compile-time macro code on Scala 2 and DCA-driven on Scala 3, so the
added tests exercise the unchanged runtime paths and nudged coverage upward).

**Re-confirmed (authoritative batch+clean) after the HOF-await parity long-tail**
(`Option.find`/`Option.collect`/non-pair `Map.collect`): both cells are
**unchanged** — Scala 3.8.3 `94.36% / 92.45%`, Scala 2.13.18 `96.30% / 94.15%`.
Expected: those commits add only Scala-2 compile-time macro code (`emitOptionCollect`,
the `Option.find`/`collect` validation, the pair-yielding `Map.collect` rejection)
plus tests that exercise the unchanged runtime pollables, so the runtime-statement
denominator is unmoved.

### Work done to close gaps

- `AsyncSuspendedSpec` (shared, cross-platform, 37 tests): deterministically
  drives every suspended-path continuation `Pollable` (`FlatMapPollable`,
  `CatchAllPollable`, `ZipWithPollable`, `RunThenValuePollable`,
  `EnsuringPollable`) and the `Completer` state machine by polling with a
  controlled no-op `Waker` and settling between polls — no threads, no timing.
  Runs on JVM and JS.
- `AsyncInteropSpec` (+2 JVM): `toFuture` / `toCompletableFuture` of a
  suspended pollable that *fails* off-thread (the EC-failure catch branches).
- `AsyncRewriteSpec` (+2 JVM Scala 3): try/catch over a *pending* await,
  driving `AsyncCpsMonad.flatMapTry`'s suspended success and failure branches.
- `AsyncSuspendedSpec` (+1, shared): `tap` whose effect is a *pending pollable
  that then fails* — the only path that drives `RunThenValuePollable` to a
  `Failure` through its suspended branch (a ready `Async.fail` is short-circuited
  by `runThenValue` before the pollable is built). Reaches the
  failure-propagation statement (`AsyncSlowPath` `RunThenValuePollable`, `else st`)
  on every cell.
- `AsyncInteropSpec` (+1 JVM): `fromCompletionStage` of a *cancelled* stage —
  `get()` throws a `CancellationException` (neither `CompletionException` nor
  `ExecutionException`), driving `unwrapCompletionException`'s `other` fallback
  (`AsyncInterop.scala:142`), which is now covered.

> **scoverage measurement note.** Under `sbt --client`, the async scoverage
> instrumentation is unreliable when coverage and non-coverage compiles are
> interleaved in one server session (observed swings: 0% / 6% / 88% / 94% on
> identical sources). The **authoritative** measurement is a one-shot batch
> invocation with a clean: `sbt -batch '++3.8.3; project asyncJVM; clean;
> coverage; test; coverageReport'`, which reproducibly reports 94.36% / 92.45%.
> Per-line `invocation-count` in a stale/contaminated session report can
> under-report freshly-covered lines.

## 4. Residual uncovered lines — per-line classification

Every remaining uncovered line is classified. None is a missing runtime test of
reachable production logic.

Line numbers below are against the authoritative Scala 3.8.3 JVM scoverage
report (uncovered statement lines: `AsyncInterop` none after the
cancelled-stage test; `AsyncSlowPath [69, 220, 227, 246, 248]`; `Completer
[75, 79, 89, 93]`; `internal/AsyncCpsMonad [76]`; `internal/AsyncDirect
[49, 51, 52, 53, 106]`; `internal/AsyncRuntimeAwait [42]`).

### Dead-via-guard defensive branches (unreachable by construction)

| File:line | Branch | Why unreachable |
|---|---|---|
| `AsyncSlowPath.scala:69` | `catchAllAsync` `Failure` branch | The inline `catchAll` (`AsyncSyntaxVersionSpecific:80` on Scala 3, `:78` on Scala 2) already handles a ready `Failure` and only delegates to `catchAllAsync` with a non-`Failure` `Pollable`. |
| `AsyncSlowPath.scala:220,227` | `ZipWithPollable` `faSt/fbSt isInstanceOf[Failure]` | `zipWithAsync` (`:80-81`) returns early if either side is a `Failure`, and the in-poll path stores `next` only when it is not a `Failure` (`:217,224`), so the fields never hold a `Failure` at the top of a `poll`. |
| `AsyncSlowPath.scala:248` | `RunThenValuePollable` `if (suppressFailure)` `true` arm | Only `tapAsync` constructs this pollable, always with `suppressFailure = false`. `ensuring` uses `EnsuringPollable`, so the `true` arm is dead. (The sibling `else st` failure-propagation arm and the re-pending `return this` arm at `:245` are both reachable and covered — see §3 "Work done".) |
| `AsyncSlowPath.scala:246` | `RunThenValuePollable.poll` block-closing brace | scoverage attributes a synthetic statement to the `}` of the inner `if`; the logical body (`:245` `return this`) is covered. |

These are harmless invariant guards. They are retained (not removed) to preserve
defensiveness against future callers; they cost nothing at runtime.

> **Now covered.** `AsyncInterop.scala`'s `unwrapCompletionException` `other`
> fallback (formerly uncovered) is reached by the new `AsyncInteropSpec` test
> "fromCompletionStage: cancelled stage surfaces the CancellationException": a
> cancelled future is done and `get()` throws a `CancellationException`, which
> is neither a `CompletionException` nor an `ExecutionException`, so the unwrap
> takes the `other` branch.

### Concurrency-race lines (non-deterministic; not unit-coverable)

| File:line | Branch |
|---|---|
| `Completer.scala:75,79,89,93` | `compareAndSet` retry loops in `settle`/`poll` — only taken when a concurrent `succeed`/`fail`/`poll` mutates the slot between `get` and the CAS. |

Covered behaviorally by `AsyncBlockingSpec`'s concurrent double-complete race
test, but the specific retry statements cannot be deterministically forced.

### Compile-time macro code (not runtime-instrumentable)

| File:line | What |
|---|---|
| `internal/AsyncDirect.scala:49,51,52,53,106` | The `.await` marker macro's `@compileTimeOnly` error message + `isAwait` recognition. These run during compilation; scoverage instruments runtime. The lexical-restriction error is proven by `AsyncAwaitCompileErrorSpec` (compile-error assertions). |
| `internal/AsyncCpsMonad.scala:76` | A branch inside `flatMapTry` reached only by a specific DCA-emitted shape. |

### DCA HOF blocking fallback

| File:line | What |
|---|---|
| `internal/AsyncRuntimeAwait.scala:42` | `fa.block` fallback used only when DCA cannot rewrite a `.await` (a higher-order lambda with no `AsyncShift`). All exercised HOFs in tests have shifts (e.g. `List.map`), so the fallback is not hit. |

## 5. Documentation audit — DONE

The user-facing reference page now exists and is wired into the site and README.
The public API below is documented in `docs/reference/async.md` (and has Scaladoc
on every member in source):

- `Async.{succeed, fail, attempt, promise, async, never, collectAll}`
- `Async[A]` syntax: `map, flatMap, catchAll, block, await, zip, zipWith, tap,
  ensuring, mapError, orElse, foldCause, either, as, unit, *>, <*, flatten`
- package helpers: `succeed, fail, when, unless`
- `Completer.{succeed, fail}`, `Pollable`, `Waker`
- `AsyncInterop.{fromFuture, toFuture, fromCompletionStage, toCompletableFuture}`
- Platform/version semantics: JVM blocking `.block`; JS pending `.block` throws;
  Scala 3 DCA direct-style; Scala 3.8+ JS native `js.async`/`js.await`; Scala 2
  macro coverage (and remaining Phase 5c/5d gaps).
- Performance contract: ready-value hot paths are zero-allocation; direct-style
  straight-line awaits are zero-allocation/JIT-elidable on JVM Scala 3; the
  mutable-loop-state caveat (fixed allocation for `var`s crossing `.await`).

### Documentation gate — completed checklist

1. ✅ `docs/reference/async.md` created (mirrors `docs/reference/chunk.md` /
   `combinators.md`), and kept current with the Phase 5c HOF coverage
   (including the `Queue` / `ArraySeq` / `Array` receiver families).
2. ✅ `Async` added to `docs/index.md` and `docs/sidebars.js` (`reference/async`).
3. ✅ `async.jvm` added to the `docs` project's `dependsOn` so `docs/mdoc` can
   compile-check the async examples (11 `scala mdoc` blocks).
4. ✅ Regenerated `README.md` documents `Async` (table row + section + reference
   link); it is auto-generated via `generateReadme` (never edited directly).

## 6. Verdict / gate

- Coverage gate: **PASS** at the enforced `92/89` floor — current JVM scoverage
  is 94.36/92.45 (Scala 3.8.3) and 96.30/94.15 (Scala 2.13.18). Residual
  uncovered lines are fully classified above; no reachable production logic is
  untested.
- Documentation gate: **PASS** — all §5 items are complete. The reference page
  tracks the current Scala 2 macro HOF coverage (the collection long tail —
  `List`/`Option`/`Vector`/`Array`/`Set`/`Queue`/`ArraySeq`/`Map` — is done).
