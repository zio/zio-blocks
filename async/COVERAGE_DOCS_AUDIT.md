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
| JVM 2.13.18 | 331 | JS 2.13.18 | 241 |
| JVM 3.3.7 | 321 | JS 3.3.7 | 234 |
| JVM 3.8.3 | 321 | JS 3.8.3 | 234 |

New tests since the prior audit:
- `AsyncInteropSpec` (+1 JVM): `fromCompletionStage` cancelled-stage → covers
  `unwrapCompletionException`'s `other` fallback.
- `AsyncSuspendedSpec` (+1, shared, all cells): ready-value `ensuring` with a
  pending-then-failing finalizer → covers `RunThenValuePollable`'s
  `if (suppressFailure) a` arm (`AsyncSlowPath.scala:248`), previously misjudged
  as dead.
- `AsyncRewriteSpec` (+2 JVM Scala 3): a re-throwing catch handler over a failed
  await, and a raw `require` failure in the post-await continuation recovered by
  a surrounding `catch` — both verify DCA exception semantics.

## 3. Coverage (JVM scoverage, current)

| Cell | Statement | Branch | At original audit |
|---|---|---|---|
| Scala 3.8.3 | **94.81%** | **93.71%** | 93.35% / 91.61% |
| Scala 2.13.18 | **96.83%** | **95.32%** | 95.84% / 93.55% |

Scala 3.3.7 mirrors 3.8.3 (same `scala-3-dca` sources). The Scala 3 cell is the
floor because it additionally compiles the dotty-cps-async bridge. Both cells
remain above the enforced `92/89` gate after the Queue/ArraySeq/Array HOF work
(which is compile-time macro code on Scala 2 and DCA-driven on Scala 3, so the
added tests exercise the unchanged runtime paths and nudged coverage upward).

**Re-confirmed (authoritative batch+clean) after the residual-line audit:** the
three new tests above lifted both cells — Scala 3.8.3 `94.36→94.81% / 92.45→93.71%`,
Scala 2.13.18 `96.30→96.83% / 94.15→95.32%`. The lift comes from covering
`AsyncSlowPath.scala:248` (ready-value `ensuring` suppression) on every cell and
the `unwrapCompletionException` `other` fallback on the JVM. Measured with the
authoritative one-shot `sbt -batch` invocation (see the measurement note below);
`sbt --client` reported `0.00%` on the same sources due to the documented
instrumentation flakiness.

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
report (uncovered statement lines: `AsyncInterop` none; `AsyncSlowPath [69, 220,
227, 246]`; `Completer [75, 79, 89, 93]`; `internal/AsyncCpsMonad [76]`;
`internal/AsyncDirect [49, 51, 52, 53, 106]`; `internal/AsyncRuntimeAwait [42]`).
Scala 2.13.18 additionally has only `AsyncSyntaxVersionSpecific:114` (the `.await`
macro stub — see below) and the same `AsyncSlowPath`/`Completer` guard lines.

### Dead-via-guard defensive branches (unreachable by construction)

| File:line | Branch | Why unreachable |
|---|---|---|
| `AsyncSlowPath.scala:69` | `catchAllAsync` `Failure` branch | The inline `catchAll` (`AsyncSyntaxVersionSpecific:80` on Scala 3, `:78` on Scala 2) already handles a ready `Failure` and only delegates to `catchAllAsync` with a non-`Failure` `Pollable`. |
| `AsyncSlowPath.scala:220,227` | `ZipWithPollable` `faSt/fbSt isInstanceOf[Failure]` | `zipWithAsync` (`:80-81`) returns early if either side is a `Failure`, and the in-poll path stores `next` only when it is not a `Failure` (`:217,224`), so the fields never hold a `Failure` at the top of a `poll`. |
| `AsyncSlowPath.scala:246` | `RunThenValuePollable.poll` block-closing brace | scoverage attributes a synthetic statement to the `}` of the inner `if`; the logical body (`:245` `return this`) is covered. |

These are harmless invariant guards. They are retained (not removed) to preserve
defensiveness against future callers; they cost nothing at runtime.

> **Corrected since the prior audit.** `AsyncSlowPath.scala:248`
> (`RunThenValuePollable`'s `if (suppressFailure) a` arm) was previously listed
> here as dead. That was **wrong**: the inline `ensuring` on a *ready value*
> calls `runThenValue(finalizer, a, suppressFailure = true)`
> (`AsyncSyntaxVersionSpecific.ensuring`), so a ready value with a
> pending-then-failing finalizer reaches it. It is now covered by the new
> `AsyncSuspendedSpec` test "ready value, ensuring finalizer pending then fails
> is suppressed". Likewise `AsyncInterop.scala:142`'s `other` fallback is now
> covered by the cancelled-stage interop test.

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
| `AsyncSyntaxVersionSpecific.scala:114` (Scala 2) | The `.await` method body that throws if it survives to runtime un-rewritten. The Scala-2 macro rewrites every in-`async` `.await`; a stray one is a compile error. The runtime throw is an unreachable belt-and-suspenders guard. |

### DCA defensive paths — empirically not reachable from idiomatic public code

These two are *registered, runtime* DCA fallbacks, but direct attempts to trigger
them from ordinary user code do not reach them — DCA's transform pre-empts them.
The evidence below was gathered this audit (not assumed):

| File:line | What | Evidence it is not reachable idiomatically |
|---|---|---|
| `internal/AsyncRuntimeAwait.scala:42` | `fa.block`, the `CpsRuntimeAwait` fallback for an `.await` DCA cannot rewrite | Attempting `Async.async { hof(i => fa.await) }` for an `hof` with no `AsyncShift` (tried both a local `def` and a stable `object` method) produces a **compile error** — `"Can't determinate qual …"` / `"Can't find AsyncShift …"` — rather than falling back to this given. DCA requires an `AsyncShift` for HOF positions; it does not consult `CpsRuntimeAwait` for them. |
| `internal/AsyncCpsMonad.scala:76` | `try f(ta) catch { case t => Async.fail(t) }` in `flatMapTry` — captures a *raw* throw from the post-await continuation | Two idiomatic throw scenarios were tested (a re-throwing `catch` handler, and a raw `require` failure after an await inside `try`). Both produce the correct result and pass, but **neither covers line 76**: DCA monadifies/handles thrown exceptions through its own try-block machinery before they reach `applyF`'s `catch`. The branch remains as defense against an `f` that throws outside DCA's view. |

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
  is 94.81/93.71 (Scala 3.8.3) and 96.83/95.32 (Scala 2.13.18). Residual
  uncovered lines are fully classified above; no reachable production logic is
  untested. One prior misclassification (`AsyncSlowPath:248`) was found and
  fixed with a real test; the DCA fallbacks (`AsyncRuntimeAwait:42`,
  `AsyncCpsMonad:76`) were empirically confirmed not reachable from idiomatic
  public code (they compile-error or are pre-empted by DCA's own transform).
- Documentation gate: **PASS** — all §5 items are complete. The reference page
  tracks the current Scala 2 macro HOF coverage (the collection long tail —
  `List`/`Option`/`Vector`/`Array`/`Set`/`Queue`/`ArraySeq`/`Map` — is done).
