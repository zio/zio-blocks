# `async` coverage & documentation audit

Audited at commit `bcf31104`+ (post suspended-path coverage work). JDK 21,
JMH 1.37, scoverage via sbt. JS coverage is disabled repo-wide
(`BuildHelper.jsSettings`), so coverage numbers are JVM scoverage; JS behavior
is covered behaviorally by the shared/JS specs.

## 1. Red flags found (at audit start)

| Flag | Status |
|---|---|
| `async` project coverage thresholds set to `0/0` in `build.sbt` | **FIXED** — raised to `92/89` (see §4) |
| No `docs/reference/async.md` reference page | **OPEN** — next blocking item (§5) |
| `docs/index.md` / README do not mention `Async` | **OPEN** — next blocking item (§5) |
| `docs` project does not depend on `async.jvm` (cannot mdoc-validate async) | **OPEN** — docs infra gap (§5) |

## 2. Test matrix (all green)

| Cell | Tests | Cell | Tests |
|---|---|---|---|
| JVM 2.13.18 | 165 | JS 2.13.18 | 142 |
| JVM 3.3.7 | 168 | JS 3.3.7 | 138 |
| JVM 3.8.3 | 168 | JS 3.8.3 | 138 |

## 3. Coverage (JVM scoverage)

| Cell | Statement | Branch | Before this work |
|---|---|---|---|
| Scala 3.8.3 | **93.35%** | **91.61%** | 80.56% / 71.33% |
| Scala 2.13.18 | **95.84%** | **93.55%** | 82.57% / 75.48% |

Scala 3.3.7 mirrors 3.8.3 (same `scala-3-dca` sources). The Scala 3 cell is the
floor because it additionally compiles the dotty-cps-async bridge.

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

## 4. Residual uncovered lines — per-line classification

Every remaining uncovered line is classified. None is a missing runtime test of
reachable production logic.

### Dead-via-guard defensive branches (unreachable by construction)

| File:line | Branch | Why unreachable |
|---|---|---|
| `AsyncSlowPath.scala:61` | `catchAllAsync` `Failure` branch | The inline `catchAll` (`AsyncSyntaxVersionSpecific:80`) already handles a ready `Failure` and only calls `catchAllAsync` with a non-`Failure` `Pollable`. |
| `AsyncSlowPath.scala:212,219` | `ZipWithPollable` `faSt/fbSt isInstanceOf[Failure]` | `zipWithAsync` (`:71-72`) returns early if either side is a `Failure`, and the in-poll path stores `next` only when it is not a `Failure` (`:209,216`), so the fields never hold a `Failure`. |
| `AsyncSlowPath.scala:238,240` | `RunThenValuePollable` `suppressFailure == true` | Only `tapAsync` constructs this pollable, always with `suppressFailure = false`. `ensuring` uses `EnsuringPollable`. |
| `AsyncInterop.scala:142` | `unwrapCompletionException` `ExecutionException` case | The CompletionStage path wraps failures in `CompletionException`; `ExecutionException` is a defensive secondary unwrap. |

These are harmless invariant guards. They are retained (not removed) to preserve
defensiveness against future callers; they cost nothing at runtime.

### Concurrency-race lines (non-deterministic; not unit-coverable)

| File:line | Branch |
|---|---|
| `Completer.scala:66,70,80,84` | `compareAndSet` retry loops — only taken when a concurrent `succeed`/`fail`/`poll` mutates the slot between `get` and CAS. |

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

### Trivial

| File:line | What |
|---|---|
| `AsyncInterop.scala:86` | Trailing `()` unit statement. |

## 5. Documentation audit — OPEN (next blocking item)

Public API requiring docs (Scaladoc present on all of these in source; the gap
is the **user-facing reference page**, not Scaladoc):

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

### Required follow-up (the documentation gate)

1. Create `docs/reference/async.md` (mirror the structure of e.g.
   `docs/reference/chunk.md` / `combinators.md`).
2. Add `Async` to `docs/index.md` and `docs/sidebars.js`.
3. Add `async.jvm` to the `docs` project's `dependsOn` so `docs/mdoc` can
   compile-check async examples.
4. Verify: `docs/mdoc` and `generateReadme` exit 0; commit regenerated
   `README.md` (never edit it directly).

## 6. Verdict / gate

- Coverage gate: **PASS** at `92/89` floor (now enforced; was `0/0`). Residual
  fully classified above; no reachable production logic is untested.
- Documentation gate: **FAIL until §5 is done.** This is the immediate next
  blocking item, ahead of Phase 5c (HOF-closure awaits) and 5d
  (for-comprehensions) per the oracle's sequencing.
