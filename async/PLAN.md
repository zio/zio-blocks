# Async runtime — plan for `Async.async { ... }` + cross-platform await

This is the implementation plan for adding a proper `Async.async { ... }` await
block to the `zio-blocks-async` subproject, alongside the existing
callback-bridge form (renamed to `Async.promise`). Multi-year scope. Quality
over speed.

## 1. Goals and non-goals

**Goals**

1. Single, uniform user-facing API across JVM and JS, across Scala 2.13 and
   Scala 3 (3.3.7 LTS and 3.8.4+).
2. `Async.async { body }` lets users write straight-line code with `.await`
   calls. `.await` is gated by an `AsyncContext` given — callable only inside
   `Async.async`.
3. Where possible, `.await` is rewritten to a `flatMap` chain (no
   thread-blocking, no JS throwing). Where impossible, fall back gracefully:
   block on JVM (Loom-friendly), throw or compile-error on JS depending on
   platform capability.
4. Zero benchmark regression on the existing fast paths (`map`, `flatMap`,
   `succeed`, `zip`, `catchAll`, etc.). The new machinery only activates
   inside `Async.async { ... }` blocks.
5. Zero allocation per `.await` of a ready value. On JS 3.8.4+, zero
   allocation per `.await` of a pending value (Promise-backed encoding).
6. Tests are cross-platform and (where the API permits) cross-Scala-version.
   Divergence in user-visible behavior breaks the test suite, by design.

**Non-goals**

- Exposing any third-party API surface. We depend on dotty-cps-async (DCA)
  internally but do not re-export `cps.*`. Users see only `zio.blocks.async.*`.
- Compatibility with Scala 3.8.0–3.8.3 for the js.async/js.await path
  (Promise[Unit] bug in scala3#25342 fixed in 3.8.4 — we wait).

## 2. The public API (immutable once Phase 0 lands)

```scala
package zio.blocks.async

// ── existing operations, unchanged
object Async {
  def succeed[A](a: A): Async[A]
  def fail(t: Throwable): Async[Nothing]
  def attempt[A](body: => A): Async[A]
  val never: Async[Nothing]
  def collectAll[A](as: Iterable[Async[A]]): Async[List[A]]

  // ── renamed: was `Async.runAsync` (and the package-level callback `async`).
  // Only ONE name for the callback-bridge constructor: `promise`.
  //  Scala 3: takes a context function so `succeed(x)` / `fail(t)` are
  //           top-level helpers picking up the Completer from context.
  //  Scala 2: takes a `Completer[A] => Unit` with implicit-param helpers.
  def promise[A](body: Completer[A] ?=> Unit): Async[A]       // Scala 3
  def promise[A](body: Completer[A] => Unit): Async[A]         // Scala 2

  // ── NEW: await block. Body returns A. .await is allowed only here.
  //  Scala 3: AsyncContext is provided via context function (invisible at
  //           the use site — users just write `Async.async { ... }`).
  //  Scala 2: in Phase 1 the body is a by-name `=> A` with no AsyncContext
  //           parameter (clean DX, no `_ =>` boilerplate). When the Phase 5
  //           Scala 2 macro lands, the signature becomes
  //           `def async[A](body: A)(implicit ctx: AsyncContext): Async[A]`
  //           and the macro provides the implicit `AsyncContext` to drive
  //           its own gating — at which point the only user-visible change
  //           is that `.await` now requires being inside `Async.async`.
  def async[A](body: AsyncContext ?=> A): Async[A]             // Scala 3
  def async[A](body: => A): Async[A]                           // Scala 2 (Phase 1)
}

// ── existing inline extensions, unchanged signatures
extension [A](fa: Async[A])
  def map[B](f: A => B): Async[B]
  def flatMap[B](f: A => Async[B]): Async[B]
  def zip[B](that: Async[B])(using t: Tuples[A, B]): Async[t.Out]
  def zipWith[B, C](that: Async[B])(f: (A, B) => C): Async[C]
  def catchAll[A1 >: A](f: Throwable => Async[A1]): Async[A1]
  // ... (full existing list, untouched)

  // ── existing .await unchanged in spelling. NEW: now requires AsyncContext.
  def await(using AsyncContext): A

// ── Completer surface, also single-name
final class Completer[A]:
  def succeed(a: A): Unit      // was: complete
  def fail(t: Throwable): Unit

// Scala 3 top-level helpers inside `promise { … }` blocks
def succeed[A](a: A)(using c: Completer[A]): Unit
def fail[A](t: Throwable)(using c: Completer[A]): Unit
```

That's it. No `runAsync`. No `done`. No `cps.*` re-exports. No new combinators
in this plan — every existing op keeps its name and signature.

## 3. The 6-cell implementation matrix

|                  | **JVM**                                   | **JS**                                                       |
|------------------|-------------------------------------------|--------------------------------------------------------------|
| **Scala 2.13**   | custom Scala 2 macro + blocking fallback  | custom Scala 2 macro + `js.await`/`js.async`                 |
| **Scala 3.3.7**  | DCA + `CpsRuntimeAwait[Async]` (blocks)   | DCA + throwing fallback (no `js.await` in 3.3.x)             |
| **Scala 3.8.4+** | DCA + `CpsRuntimeAwait[Async]` (blocks)   | **no DCA** — `.await` inlines to `js.await(toJsPromise(fa))` |

Per-cell rationale:

- **Scala 3 JVM (both versions).** DCA does the rewriting. We provide
  `CpsRuntimeAwait[Async]` so HOF closures and other DCA-unsupported positions
  fall back to our existing blocking `awaitSuspended` (Loom-friendly via
  `ReentrantLock`). Identical implementation across 3.3.7 and 3.8.4+.
- **Scala 3.3.7 JS.** DCA works on JS in pure-CPS mode. We do **not** provide
  `CpsRuntimeAwait[Async]`, so HOF closures error at compile time (matches
  what Scala.js itself would enforce if we could use `js.await` here, which we
  can't because 3.3.7 doesn't support it). Real async hops still throw at
  runtime through the existing JS Pollable driver — accepted as transitional
  cost.
- **Scala 3.8.4+ JS.** No DCA. The Async encoding on this platform/version
  carries `js.Promise[A]` directly (`Async[+A] >: js.Promise[A]`). `.await`
  inlines to `js.await(toJsPromise(fa))`; `Async.async { body }` wraps body in
  `js.async { body }`. Scala.js enforces the lexical restriction. Zero
  allocation per `.await` of a pending value (the Promise IS the Async).
- **Scala 2 (both platforms).** We write our own Scala 2 def-macro that
  performs the same CPS rewriting DCA does on Scala 3. On JVM it falls back
  to blocking; on JS it emits `js.await(toJsPromise(fa))` so that the
  lexical-restriction check by Scala.js fires correctly.

### The DCA integration (Scala 3 JVM + Scala 3.3.7 JS)

We do **not** expose `cps.*`. Internal mechanism:

```scala
// type alias, so AsyncContext IS what DCA's macro and our .await both need
type AsyncContext = cps.CpsMonadContext[Async]

extension [A](inline fa: Async[A])
  // Inlines to cps.await(fa). DCA recognizes cps.await as its suspension
  // marker and rewrites it. @compileTimeOnly on cps.await fires correctly
  // when our .await is called outside any Async.async block (because the
  // inlined cps.await sits outside one too).
  inline def await(using AsyncContext): A = cps.await(fa)

object Async:
  inline def async[A](inline body: AsyncContext ?=> A): Async[A] =
    cps.async[Async] { ctx ?=> body(using ctx) }
```

Users never write `cps.*`. They write `Async.async { ... a.await ... }`. The
macro is an implementation detail. If a user reaches for `cps.async` directly
that's their choice — same as reaching for `Future`, `IO`, or anything else.

`CpsMonad[Async]`, `CpsTryMonad[Async]`, and (JVM only) `CpsRuntimeAwait[Async]`
are package-private to `zio.blocks.async`. They delegate to our existing inline
fast paths so the JIT inlines through the singleton typeclass instance and we
preserve the zero-cost map/flatMap.

### The 3.8.4+ JS path (no DCA)

```scala
// JS-specific encoding
type Async[+A] >: A | js.Promise[A] | Failure

extension [A](inline fa: Async[A])
  inline def await(using AsyncContext): A =
    val r: Any = fa
    if r.isInstanceOf[Failure] then throw r.asInstanceOf[Failure].cause
    else if r.isInstanceOf[js.Promise[?]] then js.await(r.asInstanceOf[js.Promise[A]])
    else r.asInstanceOf[A]

object Async:
  inline def async[A](inline body: AsyncContext ?=> A): Async[A] =
    given AsyncContext = AsyncContext.Instance
    js.async { body }    // returns js.Promise[A] — wears Async[A] via encoding
```

The JS encoding diverges from JVM (Pollable-based) and that's accepted. Shared
parts: `Async.succeed`, `Async.fail`, `Failure`, `AsyncContext`, the abstract
public API. Divergent: `Completer` (JS = `(resolve, reject)` wrapper, JVM =
`AtomicReference` slot), `AsyncSlowPath` (JS uses `Promise.then`, JVM uses
`Pollable.poll`), `collectAll` internals.

### The Scala 2 macro (both platforms)

A Scala 2 `def`-macro modelled on DCA's transform but smaller scope (single
known monad, no CpsMonad typeclass infrastructure). Inputs: the body tree of
`Async.async { body }`. Output: a CPS-translated tree that:

- Sequential val/var/statement: stays as-is when no `.await` appears;
  otherwise emits `Async.flatMap(rhs)(x => <rest>)`.
- `if`/`else`, `while`, `try/catch/finally`, `match`, `throw`: same standard
  CPS shape DCA uses.
- HOF closure containing `.await`: on JVM, leave the lambda alone and have the
  inlined `.await` block; on JS, emit a compile error pointing at the lambda
  ("await inside a closure is not supported on Scala 2 + JS").
- Plain `.await` of a ready value: degenerate case, no rewrite needed (the
  inline expansion of `.await` itself handles it).

This is multi-year work. Scoped narrowly to *our* `Async` monad — we never
try to be generic over `F[_]`. That collapses 80% of DCA's complexity.

## 4. File layout

```
async/
├── PLAN.md                                                ← this file
├── shared/src/main/scala/zio/blocks/async/
│   ├── Async.scala                                         (succeed/fail/promise/async/never/collectAll)
│   ├── AsyncContext.scala               NEW                (marker; Scala 3 = type alias to CpsMonadContext)
│   ├── AsyncEncoding.scala                                 (JVM encoding holder; JS overrides)
│   ├── Completer.scala                                     (JVM AtomicReference impl)
│   ├── Failure.scala
│   ├── Pollable.scala
│   ├── Waker.scala
│   ├── AsyncSlowPath.scala                                 (JVM-shape ops; partial reuse on JS)
│   └── package.scala
├── shared/src/main/scala-2/zio/blocks/async/
│   ├── AsyncSyntaxVersionSpecific.scala                    (Scala 2 ops + new Async.async via macro)
│   └── AsyncMacros.scala                NEW (Phase 5)      (Scala 2 def-macro CPS rewriter)
├── shared/src/main/scala-3/zio/blocks/async/
│   ├── AsyncSyntaxVersionSpecific.scala                    (inline ops + new Async.async via DCA wrapper)
│   └── CpsTypeclasses.scala             NEW (Phase 2)      (CpsMonad[Async] + CpsTryMonad[Async])
├── jvm/src/main/scala/zio/blocks/async/
│   └── internal/PlatformAsync.scala                        (existing ReentrantLock parker)
├── jvm/src/main/scala-3/zio/blocks/async/
│   └── CpsRuntimeAwaitInstance.scala    NEW (Phase 2)      (JVM-only CpsRuntimeAwait[Async])
├── js/src/main/scala/zio/blocks/async/
│   ├── internal/PlatformAsync.scala                        (legacy throwing parker for ≤3.3.7)
│   └── AsyncInterop.scala                                  (Promise/Future bridges)
├── js/src/main/scala-3.8+/zio/blocks/async/                NEW (Phase 4)
│   ├── AsyncEncoding.scala                                 (JS-3.8+ encoding: Async >: js.Promise)
│   ├── AsyncSyntaxVersionSpecific.scala                    (inline .await = js.await)
│   ├── Completer.scala                                     (Promise-backed Completer)
│   └── AsyncSlowPath.scala                                 (Promise.then-based ops)
└── js/src/main/scala-3-                                    (kept for 3.3.7: uses shared/scala-3 path + DCA)
```

The `scala-3.8+` source dir uses the project's existing
`findApplicableScala3MinorDirs` helper.

## 5. Build changes

```scala
// build.sbt — async subproject
lazy val async = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-async"))
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => Seq(
          ("io.github.dotty-cps-async" %%% "dotty-cps-async" % "1.3.3")
            .cross(CrossVersion.for3Use2_13.withCompat) // appropriate cross strategy
        )
        case _ => Seq.empty   // Scala 2 uses our own macro
      }
    }
  )
  .jsSettings(
    // Add Scala 3.8.4+ to the JS cross-build (once 3.8.4 releases)
    crossScalaVersions := crossScalaVersions.value :+ "3.8.4",
    Compile / scalaJSLinkerConfig ~= (_.withESFeatures(_.withESVersion(ESVersion.ES2017)))
  )
```

Project-wide changes deferred until needed by `async`; ES2017 set per-project.

## 6. Phases (in execution order)

Each phase is independently shippable. Tests must pass at the end of every
phase across the cells supported by that phase.

### Execution status (live)

- **Phase 0:** ✅ Complete. All 6 cells green (Scala 2.13.18 / 3.3.7 / 3.8.3
  × JVM / JS), 114 JVM tests + 106 JS tests.
- **Phase 1:** ✅ Complete (dumb `Async.async` shipped). Same 6 cells green.
- **Phase 2 (DCA on JVM):** ✅ Complete. All 6 cells green (3.8.3 JVM 118 / JS
  106, 3.3.7 JVM 118 / JS 106, 2.13.18 JVM 101 / JS 93); benchmarks compile.
- **Phase 3 (DCA on JS Scala 3.3.7):** ✅ Complete — landed together with
  Phase 2 because the rewrite macro is platform-agnostic (only the JVM blocking
  fallback `CpsRuntimeAwait[Async]` is platform-specific). JS rewrites `.await`
  to `flatMap`/`map` chains for all DCA-supported positions; unshifted-HOF
  awaits are a JS compile error (no blocking).

  Implementation notes (these supersede the speculative sketch in §6 Phase 2 /
  §9 R1 below):
    - The naive "inline our `.await` to `cps.await`" approach does **not** work:
      DCA's macro only recognizes its own `cps.await`/`cps.reflect` tokens, and
      a transparent-inline alias is not expanded before DCA inspects the body.
      We therefore use the **Kyo delegation pattern**: `Async.async` is a
      `transparent inline` that calls our own macro
      (`internal.AsyncDirectMacros.asyncImpl`), which walks the block body,
      rewrites every `qual.await` into `cps.await[Async, T, Async](qual)` (with
      a locally-supplied `CpsMonadContext[Async]` so the node elaborates), and
      splices the result into a real `cps.async[Async] { ... }`. DCA only ever
      sees `cps.await`; users only ever see `Async.async` + `.await`.
    - Bare `.await` is a marker macro (`awaitErrorImpl`) that fails to compile
      unless the enclosing `async` macro rewrote it away — this is the lexical
      gate (proven by `AsyncRewriteSpec`).
    - The eager driver was renamed `.await` → `.block` everywhere
      (ready value → unbox; pending → Loom-friendly block on JVM, throw on JS).
      Scala 2 has `.block` and an eager (attempt-style) `Async.async`
      but **no** `.await` yet (Phase 5). All shared cross-version specs are
      `.await`-free; the direct-style specs live in `src/test/scala-3/`.
    - `dotty-cps-async` 1.3.3 (`_3` artifact, built on 3.3.7) is consumed on
      3.8.3 via LTS forward compatibility — confirmed compiling and testing.
    - Files added: `internal/AsyncCpsMonad.scala` (shared scala-3,
      `CpsTryMonadInstanceContext[Async]`), `internal/AsyncDirectMacros.scala`
      (shared scala-3, the rewrite macro), `jvm/.../internal/AsyncRuntimeAwait.scala`
      (JVM scala-3, `CpsRuntimeAwait[Async]` blocking fallback),
      `jvm/.../AsyncRewriteSpec.scala` (non-blocking + gating proof).
      `AsyncContext.scala` was deleted (the marker is unnecessary with the macro
      approach).
- **Phase 4 (native `js.async`/`js.await` on JS Scala 3.8+):** ✅ Complete.
  The Scala 3 direct-style backend is split behind a swappable `AsyncDirect`:
    - JVM (all 3.x) + JS (< 3.8): dotty-cps-async (`shared/.../scala-3-dca`).
    - JS 3.8+: native `js.async`/`js.await` (`js/.../scala-3.8`), ES2017 linker
      target. `qual.await` → `js.await(toJsPromise(qual))` (ready value
      short-circuits, no Promise alloc); `Async.async { body }` →
      `fromJsPromise(js.async { body })`, or `Async.attempt(body)` when the body
      has no `.await` (preserves the zero-suspension fast path). Scala.js enforces
      the lexical `.await` restriction natively.
  Public API unchanged (`Async.async` / `.await` / `.block`). JS direct-style
  block assertions moved to JVM (`AsyncAwaitBlockSpec`); `AsyncJsAwaitSpec`
  (Future-driven) covers both JS Scala 3 cells (DCA 3.3.7 + native 3.8+).
  Validated green on the repo default Scala 3.8.3 (full matrix: 3.8.3 JVM 118 /
  JS 100, 3.3.7 JVM 118 / JS 100, 2.13.18 JVM 101 / JS 93).

  **Version decision:** kept on the repo default Scala 3.8.3 rather than bumping
  `BuildHelper.Scala3` to 3.8.4-RC1. `js.async`/`js.await` already work on 3.8.3;
  the only thing 3.8.4 adds is the `js.await(js.Promise[Unit])` fix
  (scala3#25342), which was explicitly declared ignorable. A repo-wide RC bump
  would force ~40 unrelated subprojects onto an RC compiler (unvalidatable while
  unattended), violating the zero-regression / finished-solution constraints.
  Caveat recorded in `build.sbt`: a direct `Async[Unit].await` expanding to
  `js.await(Promise[Unit])` can fail to compile on 3.8.3; revisit when 3.8.4 is
  stable.
- **Phase 5 (Scala 2 direct-style macro):** ✅ Complete (initial coverage).
  `Async.async { ... }` is a `scala-reflect` def-macro (`internal.AsyncMacros`)
  performing a single-monad CPS/ANF rewrite of `.await` into non-blocking
  `flatMap`/`map`/`catchAll` chains over our one `Async` monad. `.await` is a
  `@compileTimeOnly` marker that the macro consumes; using it outside an
  `Async.async` block is a compile error. Supported positions: sequential
  `val`/statements, `if`/`else`, `match`, `while`, `try`/`catch`/`finally`,
  `throw`, assignment, and arbitrary application spines; awaits inside function
  literals, local defs/classes, `lazy val`, `synchronized`, and pattern/catch
  guards are explicitly rejected with actionable messages. Mutable block-local
  `var`s crossing an await are boxed into `scala.runtime.*Ref` cells (pre-pass)
  so generated closures only capture immutable vals. Await element types are
  recovered from the typed `body.tree` and used to ascribe each generated
  `flatMap` lambda parameter — mandatory for awaited `Async[Nothing]` (e.g.
  `Async.fail(_).await`), which Scala 2 otherwise refuses to infer ("missing
  parameter type"). Validated green across all six cells (JVM/JS × 2.13.18 /
  3.3.7 / 3.8.3); `AsyncAwaitBlockSpec` is the cross-version JVM direct-style
  suite. The macro also preserves `val` type ascriptions (`val n: Long =
  intAsync.await`) — a Scala-2-specific behavior covered by the scala-2-only
  `AsyncAwaitValAscriptionSpec`, because dotty-cps-async instead pushes the
  val's expected type into `.await` (`await[Long](intAsync)`, which does not
  compile), so the two backends diverge here by design. The JS Scala 2
  direct-style path is covered by `js/src/test/scala-2/.../AsyncJsAwaitSpec`,
  which proves a genuinely pending await (a `Completer` fired from a queued
  microtask) suspends and resumes through the macro-generated `flatMap` chain
  via `AsyncInterop.toFuture` without `.block`.

- **Correctness — null-completion fix:** ✅ Two slow paths overloaded `null` as
  a "pending"/"empty" sentinel, so a value that legitimately resolved to `null`
  misfired. Fixed with dedicated sentinels: `Completer` stores a `NullValue`
  sentinel for `succeed(null)` (a raw `null` collided with the empty state, so
  `compareAndSet(null, null)` was a no-op and the completer never settled), and
  `EnsuringPollable` initializes `outcome` to a `NotResolved` sentinel (a `pa`
  resolving to `null` was otherwise re-read as pending and re-polled forever —
  fatal for a one-shot leaf, and it reset finalizer progress). Both handed back
  out as a bare `null` (a ready value). Three deterministic, cross-platform
  regression tests added (`AsyncSpec` sync path; `AsyncSuspendedSpec` parked-waiter
  and pending-finalizer paths). Satisfies streams-module requirement #3
  (`streams/ASYNC-MODULE-REQUIREMENTS.md`). All six cells green; Scala 3.8.3 JVM
  coverage 93.55% stmt / 91.95% branch.

- **Streams requirement #1 — `unsafeRunAsync` / `Cancelable`:** ✅ Added the
  sanctioned non-blocking runner `Async.unsafeRunAsync[A](fa)(cb): Cancelable`
  (`streams/ASYNC-MODULE-REQUIREMENTS.md` item 1). Shared public `Cancelable`
  trait (`Cancelable.noop` for synchronously-settled runs) plus platform-specific
  drivers: JVM (`async/jvm/.../internal/AsyncRunner.scala`) runs a suspended
  `Async` on a daemon worker thread via `AsyncSlowPath.awaitSuspended`, with a
  single `AtomicBoolean` terminal latch — `cancel()` flips it and `interrupt()`s
  the parked worker, giving at-most-once delivery; JS
  (`async/js/.../internal/AsyncRunner.scala`) drives a microtask poll loop with a
  `cancelled` flag checked before re-scheduling, before each poll, and before the
  callback. A `Throwable` thrown by `poll` is caught and surfaced as
  `cb(Left(t))` (the spec's missing try/catch). Ready/failed inputs settle
  synchronously on the caller thread and return `Cancelable.noop`. Cross-platform
  `AsyncRunSpec` (callback bridged through `ZIO.async` + fork) covers sync fast
  path, suspended completion/failure via a fiber, thrown-poll, and
  cancel-idempotency/suppression on all six cells; JVM-only `AsyncRunJvmSpec`
  asserts off-caller-thread delivery and worker interruption. Scala 3.8.3 JVM
  coverage 93.74% stmt / 91.30% branch. Streams requirement #2 (`raceAll` /
  `Select`) remains deferred to the concurrency phase.

- **Phase 5c (HOF-closure awaits):** 🚧 In progress — `List.map` / `List.foreach`
  / `List.flatMap` landed (commits `cca71275`, `05a1f6c6`, `6ae8b458`). `.await`
  inside these `List` HOF closures is supported on **all six cells**, with the
  per-HOF semantics **each verified empirically against all three Scala 3
  backends** (DCA JVM, DCA JS 3.3.7, native `js.await` JS 3.8+) and the Scala 2
  macro made to conform exactly:
    - `List.map`: **EAGER**. All three Scala 3 backends apply the closure to every
      element first (building `List[Async[B]]`), then sequence — DCA's built-in
      `ListAsyncShift` and the JS-native backend (strict `map` invokes every
      callback synchronously, like `Array.map(async ...)` in JS, which yields an
      array of promises). The Scala 2 macro emits `recv.map(closure).collectAll`
      (`emitHofMap`). The plan's earlier "non-blocking *sequential* traversal"
      target was the denotational **outlier** (lazy) and was abandoned;
      oracle-confirmed eager is the more compositional reading of strict `map`
      (`xs.map(x => f(x).await) == Async.collectAll(xs.map(x => f(x)))`).
    - `List.foreach`: **LAZY / sequential** (closure n+1 runs only after n's await
      completes; fail-fast; result `Unit`). All three Scala 3 backends agree.
      Scala 2 emits a sequential drain loop (`emitHofForeach`).
    - `List.flatMap`: **LAZY / sequential** like `foreach` but accumulating each
      closure's `IterableOnce` into the result `List`. Scala 2 emits
      `emitHofFlatMap`. This enables multi-generator for-comprehensions.
  The HOF transform dispatches per method name; receiver type is validated as
  `List` (or a `withFilter` chain over a `List`) in the typed pass.
  **For-comprehensions over `List` (Phase 5d) work for free** because Scala
  desugars them into these methods before any backend sees them (`for…yield` →
  `map`; nested generators → `flatMap`/`map`; `for{…}` w/o yield → `foreach`;
  guard `if` → `withFilter`); covered by cross-version + cross-platform tests.
  **Guards (commit `275af367`):** the Scala 2 macro recognizes `withFilter` chains
  and materializes them to a strict `filter` before the HOF rewrite
  (`WithFilterChain` / `defilterReceiver`). Single guards behave identically on
  all six cells (DCA via `WithFilterSubstAsyncShift`; js-native via desugaring).
  *Multiple* chained guards are a **Scala-2-only superset** — DCA lacks
  `AsyncShift[WithFilter]` for a nested `withFilter` and compile-errors — covered
  by the Scala-2-only `AsyncAwaitScala2HofSpec`, divergence documented.
  A hidden custom DCA `AsyncShift` to *override* a backend's chosen semantics was
  investigated and rejected: DCA does not pick up a lexically-imported shift given
  (resolves shifts via its own macro-internal mechanism), and overriding would
  also fight the JS-native platform — so the contract is "match what the backends
  already do, per HOF."

  **`Option` HOFs landed.** `.await` inside `Option.map` / `Option.flatMap` /
  `Option.foreach` closures is supported on **all six cells**. Because an `Option`
  holds at most one element, the eager/lazy distinction that separates `List.map`
  from `List.foreach`/`flatMap` **collapses** — every HOF reduces to a single
  `Some`/`None` branch (`None` short-circuits, the closure never running; `Some(x)`
  runs the closure and, for `map`/`flatMap`, rewraps), with failures propagating.
  Verified empirically on all three Scala 3 backends (throwaway probe specs)
  before conforming the Scala 2 macro: `receiverKind` now classifies the receiver
  as `"list"` / `"option"` (recorded in `hofRecvKinds`, parallel to
  `hofElemTypes`), and the untyped dispatch selects `emitOptionMap` /
  `emitOptionFlatMap` / `emitOptionForeach` (a `Some`/`None` `if`/`else` over
  `isEmpty`/`get`). Single- and multi-generator for-comprehensions over `Option`
  work for free. **Divergence:** an `Option` for-comprehension *guard* (even a
  single one) is a **Scala-2-only superset** — DCA has no
  `AsyncShift[Option#WithFilter]` and compile-errors (unlike `List`, where a
  single guard works) — covered by the Scala-2-only `AsyncAwaitScala2HofSpec`.

  **`Vector` / immutable `Set` HOFs landed.** `.await` inside `Vector` and `Set`
  `map` / `flatMap` / `foreach` closures is supported on **all six cells**. Key
  empirical finding (probed on all three Scala 3 backends before conforming the
  Scala 2 macro): unlike `List.map` (EAGER, via DCA's special `ListAsyncShift`),
  `Vector.map` / `Set.map` are **LAZY / sequential** on every backend (the closure
  for element `n+1` runs only after element `n`'s await completes; fail-fast) —
  AND, crucially, they are lazy *consistently* across DCA-JVM, DCA-JS, and
  js-native (js-native does NOT eagerly apply `Vector.map` the way it does
  `List.map`). So there is no cross-backend divergence; the Scala 2 macro conforms
  with one generic lazy emit. The macro classifies these as `receiverKind ==
  "iterable"` (whitelisted: `Vector`, immutable `Set` — NOT a catch-all
  `Iterable`, to exclude lazy/one-shot collections), and emits a builder-drain
  (`emitCollMap` / `emitCollFlatMap`) that **preserves the result collection
  type** via the receiver's own `iterableFactory.newBuilder` (`result()` yields
  `Vector[B]`/`Set[B]`; `Set` dedups the *awaited* values, never an intermediate
  `Set[Async[B]]`). `foreach` reuses the already-collection-agnostic
  `emitHofForeach`. The full result type is recorded in `hofResultTypes` (parallel
  to `hofElemTypes`/`hofRecvKinds`) for the recursive drain `def`'s return type.
  `Set` is matched via base-type symbol (it is INVARIANT, so `Set[Int] <:<
  Set[Any]` is false). For-comprehensions over `Vector`/`Set` work for free
  (guards over them, like `Option`, are likely a Scala-2-only superset — not yet
  added as tests).

  **immutable `Map` HOFs landed.** `.await` inside immutable `Map`
  `map` / `flatMap` / `foreach` closures is supported on **all six cells**. Probed
  on all three Scala 3 backends first: `Map.map`/`flatMap`/`foreach` all support
  `.await`, a pair-returning `map`/`flatMap` preserves the `Map[K2, V2]` result
  shape, and a non-pair `map`/`flatMap` widens to an `Iterable` (the standard
  library's own overload choice) — all confirmed identical on DCA-JVM, DCA-JS, and
  js-native. Semantics are **lazy / sequential** over `(K, V)` entries, like
  `Vector`/`Set`. The macro classifies the receiver as `receiverKind == "map"`
  (matched via `MapSym` base-type symbol — immutable `Map` is INVARIANT in its
  key) and emits via `emitMapMapLike`, which chooses the receiver's
  `mapFactory.newBuilder[K2, V2]` when the result is a `Map[K2, V2]` (so the
  closure's element type is recorded as the entry tuple `(K2, V2)`, not just `K2`)
  and falls back to `iterableFactory.newBuilder` for non-pair results. The
  builder-drain core was refactored into a shared `builderDrain` helper (a `flat`
  flag selects `++=`/`IterableOnce` for `flatMap` over `+=` for `map`) now reused
  by `emitCollMap`/`emitCollFlatMap` and the `Map` emit. `foreach` reuses the
  generic `emitHofForeach`. For-comprehensions over a `Map` work for free.
  Cross-version JVM (`AsyncAwaitBlockSpec`) + cross-platform JS specs cover
  pair-returning `map`/`flatMap`/`foreach`, the non-pair `map` widening, pending
  awaits, and failure propagation.

  **Predicate-scanning HOFs landed (`find` / `exists` / `forall`).** `.await`
  inside the `A => Boolean` closure of `find`/`exists`/`forall` is supported on
  **all six cells**. Probed on all three Scala 3 backends first (alongside
  `filter`/`collect`/`takeWhile`/`foldLeft`, which remain future increments).
  These are **receiver-kind-agnostic**: every whitelisted receiver
  (`List`/`Option`/`Vector`/`Set`/`Map`) has `.iterator`, and the scan is
  inherently lazy/sequential short-circuit — so a single `emitPredicateScan`
  (added to `supportedHofMethods`, dispatched by method name *before* the
  kind-specific catch-alls) drains the iterator with a tight `while`, short-circuits
  at the first decisive element (`exists`→first `true`, `forall`→first `false`,
  `find`→first match as `Some`/else `None`), and switches to a `flatMap`
  continuation on the first suspended predicate. **Known Scala-2 gap:**
  `Option.find` resolves via the `Option`→`Iterable` implicit conversion (whose
  converted receiver is not whitelisted), so it is rejected on Scala 2 —
  `Option.exists`/`forall` work directly, and `find` works over
  `List`/`Vector`/`Set`/`Map`; documented in the tests and reference doc.

  **`foldLeft` landed.** `.await` inside the two-arg op `(B, A) => B` of
  `foldLeft(z)(op)` is supported on **all six cells** (Scala 3 DCA-JVM /
  DCA-JS 3.3.7 / native `js.await` JS 3.8+ all support it — compile- and
  run-probed first; the Scala 2 macro made to conform). A left fold is
  inherently sequential (element `n+1`'s op needs `n`'s accumulator), so it is
  **lazy / sequential** on every backend with no eager/lazy divergence to
  reconcile. The macro adds the curried-call extractor `FoldLeftAwaitCall`
  (double `Apply`, optional `TypeApply` for `[B]`; matched only when the OP BODY
  awaits — a `.await` solely in `z` stays a generic application-spine await), the
  two-arg-lambda extractor `TwoArgFunction`, a `foldResultTypes` queue recording
  the typed call node's own result type `B` (a fold returns `B` directly, not a
  collection wrapper) consumed by `dequeueFoldResult`, and `emitHofFoldLeft`,
  which threads the accumulator in a local `var` through a tight `while` while op
  results are ready (no `flatMap` alloc) and switches to a recursive `flatMap`
  drain that resumes the same iterator with the new accumulator on the first
  suspended/failed op. Receiver-agnostic via `.iterator`. The dispatch consumes
  awaits in queue order (receiver → initial accumulator `z` → op body), so awaits
  in `z` are sequenced before the fold. Cross-version JVM (`AsyncAwaitBlockSpec`)
  + cross-platform JS specs cover ready/pending awaits, a result type differing
  from the element type, an awaited initial accumulator, an empty receiver, lazy
  failure short-circuiting, and a `Vector` receiver.

  **`filter` / `filterNot` landed.** `.await` inside the `A => Boolean`
  predicate of `filter` / `filterNot` is supported with **lazy / sequential**
  semantics and **result-collection-type preservation** (probed on all three
  Scala 3 backends first — List/Vector/Set/Option all lazy, failing-await
  short-circuit, collection type preserved — then the Scala 2 macro conformed).
  Added to `supportedHofMethods`; the macro reuses the builder-drain pattern
  (`emitFilterLike`: drains the iterator, evaluates the awaiting predicate
  sequentially, appends the SOURCE element — not the predicate result — into the
  receiver's own `iterableFactory`/`mapFactory` builder) plus a single-element
  `Some`/`None` emit for `Option` (`emitOptionFilter`), dispatched by
  `emitFilter`. Supported on **all six cells** for `List`/`Vector`/`Set`/
  `Option`. **Divergence:** `Map.filter`/`filterNot` is a **Scala-2-only
  superset** — dotty-cps-async has no working `MapOpsAsyncShift.filter` (it
  crashes the macro on Scala 3, verified), so it is covered Scala-2-only in
  `AsyncAwaitScala2HofSpec` (the Scala 2 macro handles it via the generic
  `mapFactory` builder path).

  `takeWhile` / `dropWhile` ✅ **done** (collection-preserving prefix
  predicates over ordered `Seq` receivers — `List` / `Vector`): lazy /
  sequential, DCA-confirmed identical on Scala 3 JVM + JS, native `js.await`
  on 3.8+ JS, and the Scala 2 macro (`emitTakeWhile`, `drop` flag selects the
  two-phase `dropWhile` drop-then-keep-all shape). They are restricted to
  ordered `Seq` receivers in the typed pass (`SeqAnyTpe` guard): a leading-prefix
  predicate is ill-defined on an unordered `Set` / `Map` (and `Option` lacks
  them), so the Scala 2 macro rejects those with an actionable compile error
  (covered in `AsyncAwaitScala2HofSpec`).

  `reduce` / `reduceLeft` ✅ **done** (`foldLeft` seeded by the first element):
  lazy / sequential left-to-right over any whitelisted receiver via `.iterator`
  (`emitHofReduce`, `ReduceAwaitCall`, `reduceResultTypes`), DCA-confirmed
  identical on Scala 3 JVM + JS and native `js.await` on 3.8+ JS — including the
  single-element no-op-run case and the EMPTY-receiver
  `UnsupportedOperationException` (emitted as an `Async.fail` so it is catchable
  and rethrown by `.block`). Validated the same way as `foldLeft` (receiver-kind
  guard in the typed pass; non-whitelisted receivers rejected, covered in
  `AsyncAwaitScala2HofSpec`).

  `foldRight` ✅ **done** (right-associative two-arg closure): lazy / sequential
  but the op runs RIGHT-to-left (`op(x1, op(x2, ..., op(xn, z)))`), empirically
  confirmed against DCA (`seen=[3,2,1]`, result `(1+(2+(3+z)))`). The Scala 2
  macro (`emitHofFoldRight`, `FoldRightAwaitCall`, `foldRightResultTypes`)
  materializes the receiver via `.toVector` and drains it in reverse to match the
  await-ordering; an empty receiver yields `z` (op never runs). DCA-confirmed
  identical on Scala 3 JVM + JS and native `js.await` on 3.8+ JS. Validated the
  same way as `foldLeft` (receiver-kind guard; non-whitelisted receivers
  rejected, covered in `AsyncAwaitScala2HofSpec`).

  `collect` ✅ **done** (PartialFunction literal): the macro extracts the user
  `case`s from the desugared `AbstractPartialFunction` `$anonfun`
  (`PartialFunctionLiteral` / `CollectAwaitCall` unwrap `applyOrElse`'s `Match`,
  dropping the synthetic `defaultCase$` fallthrough), then `emitCollect` emits a
  builder-drain that, per element, runs a SINGLE match (user cases + a trailing
  `case _ => skip` sentinel, omitted when the PF is total) — so the guard runs
  exactly once — appending the (possibly awaited) body. Restricted to
  builder-backed receivers (`List` / `Vector` / `Set`); `.await` in a case GUARD
  is rejected. DCA-confirmed identical RESULTS on Scala 3 JVM + JS and native
  `js.await` on 3.8+ JS (the guard-eval COUNT differs — DCA may evaluate it more
  than once — so the once-per-element guarantee and the `Option`/`Map`-receiver
  and awaiting-guard rejections are asserted Scala-2-only in
  `AsyncAwaitScala2HofSpec`). `Option` / `Map` `collect` remain Scala-3-only.

  Remaining 5c: more collections (`Array` — needs `ClassTag`; `Queue`,
  `ArraySeq`, …). Per oracle review, `Array` is a distinct later pass (different
  builder/result shape concerns).

- **Benchmark gate (§8):** ✅ Complete for the JVM Scala 3 (DCA) cell.
  Added `AsyncBlockBench`, `AsyncBlockHybridBench`, and `AsyncBlockClosureBench`
  (direct-style `.await` chains, hybrid sync+async, and `.await` inside a
  `List.map` HOF closure), each with hand-written `flatMap` controls and
  `ce_*`/`kyo_*` baselines. Gate-quality numbers (5wi/5i/-f1) appended to
  `async-benchmarks/baseline.txt`.

  **Cost-model finding (gc-profiled):** the DCA direct-style rewrite is
  zero-allocation and fully JIT-elidable for straight-line code — a single
  `.await` runs at ≈ 2.2e9 ops/s and sequential `val` awaits at ≈ 1.8e9 ops/s,
  ≈ 0 B/op, matching or beating the hand-written `flatMap` control (and ≈ 18×
  faster than Kyo, ≈ 20,000× faster than Cats Effect IO on the same shape). The
  *only* allocating shape is a `var` mutated **across** a `.await` inside a
  `while` loop: DCA lifts the vars into heap ref-cells plus a recursive-flatMap
  continuation — a fixed ≈ 128 B/op, **constant in n** (not per-link). So R2's
  literal goal (zero allocation per macro-emitted `flatMap` link) is met; the
  residual is a fixed per-block cost confined to mutable-loop-state, which every
  effect system pays for that shape.

  **Perf fix landed:** DCA's default `CpsTryMonadInstanceContext.apply`
  allocated a fresh context body per `cps.async` block. Since `Async` carries no
  per-run state, `AsyncCpsMonad` now overrides `apply` to reuse a cached
  singleton context (commit `215afbee`), removing that per-block allocation.

  Remaining benchmark work (DEDICATED LATER SUB-PHASE, not a blocker — scope
  corrected 2026-06 after infra investigation + oracle review): the present gate
  covers the JVM Scala 3 (DCA) cell only. Extending it to the JS-native and
  Scala-2-macro cells is **new-infrastructure work, not a re-run**, because
  `async-benchmarks` is Scala-3.8.3-only and depends on **Kyo (Scala-3-only)**,
  and **JMH is JVM-only** (no JS harness exists):
    - *Scala-2 macro cell:* needs a separate JVM-only `async-benchmarks-scala2`
      module (2.13, JmhPlugin, dependsOn `async.jvm`, NO Kyo), benchmarking the
      macro-expansion hot paths with `-prof gc`. The acceptance criterion is
      allocation-focused (ready scalar awaits allocate no Pollable/continuation;
      HOF paths allocate only expected collection/builder/closure artifacts;
      pending paths allocate the designed continuation), NOT a cross-Scala JMH
      throughput comparison (the Scala 2 macro vs Scala 3 DCA numbers are not
      directly comparable — different CPS backends).
    - *JS-native cell:* needs an entirely new JS microbenchmark harness; deferred
      until there is a concrete JS perf concern (lower-stakes, less comparable).
  This sub-phase is triggered by any change to the `Async`/`Pollable`/`Completer`
  runtime encoding, a change in the macro's scalar ready-path code shape, or
  completion of the collection-HOF long tail — none of which the recent Scala-2-
  only HOF additions (Option/Vector/Set) constitute (they touch no Scala 3 code
  and no runtime, so the existing JVM-Scala-3 gate is definitionally unaffected).

### Phase 0 — Foundation rename (1–2 weeks)

No behavioral change. Pure rename + introduce `AsyncContext` as a marker.

- `Async.runAsync` deleted. Replaced by `Async.promise`.
- Package-object `async { c => … }` renamed to `promise { c => … }`.
- `done(x)` renamed to `succeed(x)`. `fail(t)` unchanged.
- `Completer.complete` renamed to `Completer.succeed`.
- `AsyncContext` introduced as a marker trait/object. Not yet required by
  `.await` — added in Phase 1.
- Existing tests renamed. Existing benchmarks renamed.
- **Exit criteria:** all existing tests pass on all 6 cells, all benchmarks
  produce identical numbers (within noise), README/docs updated.

### Phase 1 — `Async.async` block, dumb implementation (2–3 weeks)

The "make it work, no macro yet" pass. Scope is intentionally narrow: we
establish the API surface and the test scaffold, NOT the rewrite semantics.

- `Async.async[A](body: AsyncContext ?=> A): Async[A]` on Scala 3; the
  Scala 2 sibling form.
- **`.await` remains ungated in Phase 1.** Gating (`using AsyncContext`)
  moves to Phase 2 (where DCA actually depends on the lexical boundary for
  rewriting) and Phase 4 (where Scala.js enforces the boundary natively). The
  Phase 1 `AsyncContext.Instance` exists only as a forward-looking marker so
  the `?=>` body type stays stable across phases.
- **JVM (Scala 2 + 3):** `Async.async { body }` runs body eagerly. `.await`
  calls inside the body block on the existing `AsyncSlowPath.awaitSuspended`.
  Exceptions thrown by the body (including from a `.await` of a failed
  `Async`) are wrapped as `Async.fail`.
- **JS (all Scala versions):** same eager-body shape. `.await` on a
  truly-pending pollable throws `IllegalStateException` via the existing JS
  throwing parker; `Async.async` catches it and surfaces `Async.fail`. The
  native `js.async`/`js.await` integration is **not** in Phase 1 — it requires
  the Phase 4 Promise-backed encoding and is gated on the Scala 3.8.4 release.
- **Exit criteria:** new test file `AsyncAwaitBlockSpec` covers the
  expression-position table in §7 with the subset that does **not** depend on
  rewrite semantics (sequential / if / while / try-catch / match / throw /
  `.await` of `Async.succeed` / `.await` of `Async.fail`). HOF closures and
  the "`.await` outside `Async.async` is a compile error" assertions are
  deferred to Phases 2–5 as each cell gains its rewrite/native implementation.
  No regression on existing benchmarks. A new `AsyncBlockBench` baseline is
  added that measures the dumb path's overhead vs. hand-written
  `flatMap`/`await` (expected: identical, within JMH noise).

### Phase 2 — DCA on JVM (4–6 weeks)

- Add dotty-cps-async dependency, Scala 3 only.
- Implement `CpsMonad[Async]`, `CpsTryMonad[Async]` (delegating to inline
  fast paths).
- Implement `CpsRuntimeAwait[Async]` on JVM Scala 3 path (NOT
  `CpsRuntimeAsyncAwait`, so the macro still rewrites; the runtime-await is
  only used as fallback for HOFs without `AsyncShift`).
- `Async.async { body }` on Scala 3 wraps `cps.async[Async] { body }`.
- Validate: rewriting works for sequential awaits, if/else, while, try/catch,
  match, throw; HOFs over List/Seq/Option/Map/etc. via DCA's built-in
  AsyncShifts; HOFs without AsyncShift fall back to blocking on JVM.
- Macro-emitted `monad.flatMap(...)` must inline through our singleton
  CpsMonad instance with no allocation overhead. Verify with bytecode +
  benchmarks.
- **Exit criteria:** zero benchmark regression on existing micros. New
  `AsyncRewriteSpec` proves rewriting (no thread blocking) for supported
  positions. JMH proves macro path is within noise of hand-written flatMap.

### Phase 3 — DCA on Scala 3.3.7 JS (2–3 weeks)

- Same DCA wiring as Phase 2, but on JS Scala 3.3.7.
- No `CpsRuntimeAwait[Async]` on this cell. HOF closures = compile error.
- Real async hops at runtime still throw via the existing throwing parker
  (DCA produces flatMap chains driven by Pollable; JS can't block).
- **Exit criteria:** behavior matrix tests pass for the 3.3.7-JS cell.
  Documented: "for true async on JS use Scala 3.8.4+."

### Phase 4 — `js.async`/`js.await` on JS Scala 3.8.4+ (3–5 weeks, gated on 3.8.4 release)

- Add Scala 3.8.4 to async's JS cross-build.
- Set ES2017 target on async's JS link config.
- Implement JS-3.8+ encoding: `Async[+A] >: js.Promise[A]`. The pending
  representation IS a `js.Promise`. Completer becomes a `(resolve, reject)`
  wrapper. `AsyncSlowPath` ops use `Promise.then`. `map`/`flatMap` fast paths
  unchanged for ready values.
- `.await` inlines to `js.await(toJsPromise(fa))` — zero alloc for already-
  Promise pending values.
- `Async.async { body }` wraps body in `js.async { body }`; lexical
  restriction enforced by Scala.js.
- DCA is **not used** on this cell.
- **Exit criteria:** zero allocation per `.await` confirmed via JS allocation
  profiling. JS benchmark proves `js.async`/`js.await` outperforms the
  DCA+Pollable path of Phase 3 (the bet from §9). Tests show the lexical
  restriction errors compile-time for closures, while-/if-/try-positioned
  awaits work.

### Phase 5 — Scala 2 macro (12+ months)

- Custom Scala 2 def-macro `AsyncMacros.cpsTransform` that operates on the
  body of `Async.async { body }`.
- Single-monad: rewrites `.await` to `Async.flatMap` chains using our own
  inline `.flatMap` (so the macro output stays on the zero-cost fast path).
- Position coverage parity with DCA: sequential / if / while / try-catch /
  match / throw / val-def / for-comprehension via `withFilter` chain
  substitution / HOF via per-collection shifts.
- Fallback: JVM = blocking via existing parker; JS = compile error pointing
  at the lambda for HOFs without shifts.
- Built incrementally — Phase 5a covers sequential + if/while; 5b adds
  try-catch + match; 5c adds HOFs (most work); 5d adds for-comprehensions.
- **Exit criteria:** Scala 2 cells reach behavioral parity with Scala 3 cells
  (modulo cell-specific divergences explicitly declared in tests).

## 7. Test plan

Two suites, both kept honest by being cross-platform / cross-version where
possible.

### `AsyncAwaitBlockSpec` (shared/test/scala, cross-platform + cross-version)

A single test file containing one assertion per expression position. Each
assertion declares its expected outcome **per cell**:

| Position | Test shape |
|---|---|
| Plain `val x = a.await` | Behavior: returns value. All 6 cells. |
| Sequential awaits | Behavior: in source order. All 6 cells. |
| `if`/`else` with `.await` in branches | Behavior. All 6 cells. |
| `while` loop body containing `.await` | Behavior. All 6 cells. |
| `try`/`catch`/`finally` with `.await` in any position | Behavior. JVM all cells; JS Scala 3.8.4+ pass; JS Scala 3.3.7 may compile-error (try inside DCA needs CpsTryMonad — we provide it, so should pass). |
| `match` scrutinee or arm with `.await` | Behavior. All 6 cells. |
| `throw` inside async block | Behavior: propagates as failure. All 6 cells. |
| `.await` of a `Failure` | Behavior: throws underlying cause at the `.await` point. All 6 cells. |
| `.await` inside `xs.map(_)`  closure | **DONE for `List.map` (commit `cca71275`); semantics CORRECTED to EAGER (empirically verified, 2026-06):** all six cells produce identical eager semantics. JVM/old-JS Scala 3 (DCA): built-in `ListAsyncShift` applies the closure to every element first (building `List[Async[B]]`), then sequences. JS 3.8+ (native `js.await`): same, because strict `List.map` invokes every callback synchronously (`Array.map(async ...)` yields an array of promises). The Scala 2 macro (`AsyncMacros.emitHofMap`) was changed to conform: `recv.map(closure).collectAll` — strict eager map + fail-fast `Async.collectAll`. The earlier "non-blocking *sequential* flatMap traversal" target was the denotational outlier (lazy) and was abandoned. A hidden custom DCA shift to force lazy was rejected (DCA ignores lexically-imported shift givens; lazy would also fight the JS-native platform). Remaining: more HOFs (`foreach`/`flatMap`/`filter`, other collections). |
| `for (x <- xs) yield x.await` | **DONE for `List`.** Desugars to `List.map`/`flatMap`/`foreach` before any backend sees it, so it inherits the per-HOF await semantics (eager map, lazy foreach/flatMap). Single- and multi-generator comprehensions covered cross-version + cross-platform. |
| `.await` outside `Async.async` block | All 6 cells: **compile error** ("no implicit AsyncContext"). |
| `.await` inside a nested local def | DCA: requires the compiler plugin (we don't enable it) → compile error or surprising behavior; document. |
| `.await` inside a `lazy val` body | DCA known silent failure; we surface it via a pre-check. |
| `return` inside async block | DCA: hard compile error. Mirror this in our macro/forms. |

Each cell's outcome is declared at the test level. If any cell's behavior
diverges from declaration, the test fails. This is the contract that keeps
implementations honest across the matrix.

### `AsyncPromiseSpec` (shared/test/scala, cross-platform + cross-version)

Covers the callback-bridge form. Same shape as today's `AsyncSugarSpec`,
renamed and updated. Lives across both Scala 2 and Scala 3 source paths
because the syntax differs.

### Per-cell tests

Live in platform/version-specific source dirs. Cover things only meaningful
on that cell:

- `jvm/src/test/scala/.../AsyncBlockingSpec.scala` (exists today) — JVM
  blocking semantics, parker correctness, virtual-thread behavior.
- `js/src/test/scala-3.8+/.../AsyncJsAwaitSpec.scala` — `js.async`/`js.await`
  integration, Promise interop, lexical-restriction compile-error tests via
  `compileErrors`.
- `js/src/test/scala-3-/.../AsyncDcaJsSpec.scala` — the DCA-on-JS-3.3.7 cell.

## 8. Benchmark plan

Two benchmark goals, both blocking phase completion.

### Zero-regression on existing benchmarks

Phase 2, 4, 5: re-run `AsyncBench`, `AsyncChainBench`, `AsyncCombinatorBench`,
`AsyncErrorBench`, `AsyncHybridBench`, `AsyncScalingBench`. Numbers must be
within JMH noise of the Phase 0 baseline (which is the current `baseline.txt`).
Any regression > 5% blocks the phase.

### New benchmarks for `Async.async`

- `AsyncBlockBench` — sequential `.await` chains (1, 10, 100, 1000 awaits)
  inside `Async.async { ... }`. Compare:
  - Hand-written `flatMap` chains (control)
  - `Async.async` with DCA (JVM Scala 3)
  - `Async.async` with `js.async`/`js.await` (JS Scala 3.8.4+)
  - `Async.async` blocking (JVM Scala 2 pre-macro)
- `AsyncBlockClosureBench` — `.await` inside HOF closures. Validates the
  fallback paths (JVM blocking, JS compile-error baseline).
- `AsyncBlockHybridBench` — mix of sync ops and one real async hop inside
  `Async.async`. Validates Phase 4's performance bet.

### Phase 4's specific bet — must measure

Hypothesis: on JS Scala 3.8.4+, `js.async`/`js.await` beats DCA+Pollable for
chains with real async hops. Test: a chain of 100 awaits where every 10th hop
is a real `setTimeout`-driven Promise. Compare JS Scala 3.8.4+ path vs JS
Scala 3.3.7 path. If `js.async` doesn't win by ≥ 2×, revisit the design.

## 9. Risks and how we beat them

This is a non-negotiable goal list. We get the API described in §2 with the
behavior described in §3, on every cell of the matrix, at full performance,
no exceptions. The mitigations below are escalation ladders, not fallbacks
to degraded outcomes. We climb until the rung holds. We do not ship less.

**Extreme-mode posture.** The bottom rung of every ladder is "modify the
toolchain itself." This includes patching, forking, or maintaining our own
distribution of: dotty-cps-async, the Scala 2 compiler, the Scala 3 compiler,
the Scala.js linker, and the JDK class libraries. Every ladder ends in
"we ship the feature." None ends in "we accept less."

### R1 — DCA macro recognizing our `.await`

Goal: inside `Async.async { ... }` on Scala 3, every `.await` call is rewritten
by DCA. Outside, every `.await` is a compile error. No exceptions.

Escalation:

1. Primary path: inline our `.await` to `cps.await(fa)`. Confirm with a Phase 2
   spike that the inlined call is visible to DCA's macro and rewritten
   correctly. Verify via bytecode inspection of the macro-expanded output.
2. If macro phase ordering or symbol-recognition fails: register our `.await`
   with DCA's extension-method recognition hook (`CpsAwaitMarker` /
   per-symbol opt-in). DCA's source has the relevant infrastructure; we use
   it directly.
3. If DCA's existing recognition hooks are insufficient: submit a PR to DCA
   adding the hook we need. Ruslan accepts upstream contributions. Time cost
   is acceptable.
4. If upstream rejects or stalls: fork DCA into our build, ship the patched
   version under our coordinates. Maintain the fork.
5. If forking is infeasible for any reason: write our own Scala 3 quotes-based
   macro that does the CPS transform. We already plan a Scala 2 macro
   (Phase 5); a Scala 3 sibling is strictly easier (typed trees, mature
   `scala.quoted`). Cost: an extra few months. We pay it.
6. If `scala.quoted` itself proves insufficient for the recognition pattern
   we need: build a Scala 3 compiler plugin (same posture as R4.2 — Scala 3
   exposes `PluginPhase` precisely for this). If the plugin needs unexposed
   internals: fork the Scala 3 compiler (same posture as R4.4). We maintain
   a patched Scala 3 distribution if that is what shipment requires.

No path leads to "users have to call `cps.await` instead of `.await`." That
outcome is not on the menu.

### R2 — Zero allocation per macro-emitted `.flatMap` link

Goal: a macro-rewritten `Async.async { val x = a.await; val y = b.await; x+y }`
allocates nothing for ready values and exactly one continuation Pollable per
genuinely pending await. Function allocation per link is rejected.

Escalation:

1. Primary path: `CpsMonad[Async]` is a singleton `object` whose `flatMap`
   inlines to our existing inline `.flatMap`. Verify with JMH (no regression
   on `AsyncChainBench`) and with `-XX:+PrintInlining` / `-XX:+PrintAssembly`
   that the call chain inlines through.
2. If lambda allocation appears: rewrite `CpsMonad[Async].flatMap` as
   `inline def flatMap[A,B](inline fa: Async[A])(inline f: A => Async[B])` so
   the macro's `monad.flatMap(...)` call sites inline the lambda directly.
   This requires `CpsMonad`'s `flatMap` to permit `inline` overriding, which
   it does via Scala 3's `inline override`.
3. If DCA's macro emits a runtime `monad: CpsMonad[Async]` reference that
   prevents devirtualization: provide a *specialized* `CpsMonad[Async]`
   subtype known statically to the macro, so the emitted code targets the
   concrete type, not the trait. Pattern: `object AsyncCps extends CpsMonad[Async]`
   plus a DCA configuration knob to use it directly.
4. If DCA hides the monad reference behind an indirection we can't eliminate:
   patch DCA's emission strategy (PR upstream, fork otherwise — see R1).
5. If none of the above: take our own Scala 3 macro path from R1.5 and emit
   the exact bytecode we want.

There is no rung labelled "accept 16 B/op of lambda garbage."

### R3 — Scala 3.8.4 release timing

Goal: Phase 4's `js.async`/`js.await` path ships and performs as bet, on the
schedule we set. We do not block on upstream.

Escalation:

1. Primary path: wait for 3.8.4 release. The `Promise[Unit]` bug
   (scala3#25342) is already fixed in trunk per the issue tracker.
2. If 3.8.4 is delayed beyond a month after we're ready: pin to a nightly
   3.8.x build from the Scala 3 master that contains the fix. The project
   already uses non-LTS Scala 3 versions; one more isn't a stretch.
3. If pinning to nightly is too disruptive: vendor the specific scala3 patch
   into our build via a compiler-options shim, or compile from source against
   a tagged commit.
4. If the JS interop itself has further bugs: file them with reproductions,
   contribute fixes, repeat R1's escalation ladder for upstream blockage.

We do not ship a "documented limitation: avoid `js.Promise[Unit]`" workaround.

### R4 — Scala 2 macro position coverage parity

Goal: the Scala 2 macro reaches behavioral parity with the Scala 3 DCA path
on every position listed in §7's test table. No position permanently labelled
"Scala 2 unsupported." Scala 2.13 is supported with the same matrix richness
as Scala 3 — period.

Escalation:

1. Primary path: implement Phase 5a-d (sequential, if/while, try-catch, match,
   throw, HOFs, for-comprehensions) over the planned ~12+ months as a
   `scala.reflect.macros.blackbox` / `whitebox` def-macro. Each sub-phase has
   its own test pass.
2. If a position requires more than def-macros can deliver (cross-tree
   rewriting, post-typer transforms, lifting locals out of inner classes):
   build a **Scala 2 compiler plugin**. Plugins run at any phase of the
   compiler pipeline (`typer`, `refchecks`, `uncurry`, `erasure`, `cleanup`)
   and can rewrite any tree the compiler sees. Lightbend's original scala-async
   moved from macros to a compiler plugin for exactly this reason — we follow
   the same path if needed.
3. If a position requires capabilities the compiler-plugin interface
   doesn't expose (custom phase ordering, access to internal symbol tables,
   modifications to type-checking rules): build a **Scala 2 compiler plugin
   that uses reflection into the compiler's internals**, the way Scala
   Native's NIR plugin or Scoverage's instrumentation plugin do. Most
   "private" compiler APIs are accessible to plugins with sufficient
   determination.
4. If the necessary compiler internals are `final` / `private[scalac]`
   / sealed in a way that even an aggressive plugin can't reach: **fork
   the Scala 2 compiler.** Maintain `zio-blocks-scalac`, a patched
   distribution of 2.13.x that exposes the hooks we need. Publish it under
   our own coordinates. The Scala 2 line is in maintenance mode; the upstream
   is stable enough that we don't need to track a moving target. Users of
   the `async` subproject on Scala 2 add a `scalacOptions += "-Xplugin:..."`
   or switch their `scalaVersion` to our fork. We document it; we ship it.
5. If forking the compiler itself proves insufficient (e.g. a JVM-level
   limitation surfaces): patch the compiler to emit AST nodes that work
   around the JVM limitation, or contribute the necessary capability to
   OpenJDK upstream. We've been clear: every ladder ends in shipment.

The test matrix never grows a "skipped on Scala 2" cell. Scala 2.13 users
get the same Async they would on Scala 3.

### R5 — Lexical-restriction error UX on JS 3.8.4+

Goal: when a user writes `.await` in a forbidden position on JS 3.8.4+, the
error points at the user's code, names the user's `.await` call, and tells
them what to do. No raw `js.await` / linker-internal symbols leaking.

Escalation:

1. Primary path: rely on Scala.js's compiler-phase check. Capture the error
   text in a `compileErrors`-based test and assert it's actionable.
2. If the error is poor (references `js.await` internals, points at wrong
   line): write a pre-check that runs in our own Scala 3 macro for
   `Async.async { body }` on the 3.8.4+ JS path. It walks the body's AST,
   detects closures / by-name args / nested defs containing `.await`, and
   emits its own diagnostic *before* Scala.js's check fires. The Scala.js
   check then becomes belt-and-suspenders.
3. If our macro check itself has edge cases: contribute to Scala.js to
   improve the error position metadata. Upstream the fix.

The user does not get to see "Illegal use of js.await()" pointing at
generated symbols.

### R6 — DCA publishing for new Scala minor versions

Goal: when a new Scala 3 minor we want to support ships, our async subproject
follows within a week.

Escalation:

1. Primary path: DCA publishes promptly (their cadence has been weeks, not
   months).
2. If delay: build DCA ourselves from upstream master against the target
   Scala version, publish to a private Sonatype repo (or our GitHub Packages),
   pin until upstream catches up.
3. If permanent upstream slowness: fork DCA (per R1.4). Maintain.

### R7 — Phase 0 rename breaking changes

The async subproject is pre-1.0 with no external committed users. Phase 0
ships as a single hard break, no shim, no deprecated aliases. The plan does
not even consider migration tooling because there's nothing to migrate.
This is a stated invariant of the plan, not a risk.

### R8 — Performance regression on existing benchmarks

Goal: zero regression on the existing benchmark suite at the end of every
phase. Within JMH noise, full stop.

Escalation:

1. Each phase's exit criteria require running the full benchmark suite and
   diffing against the committed `baseline.txt`. CI enforces this.
2. If a regression appears: the phase does not ship. We diagnose with
   `-prof gc`, `-prof perfasm`, bytecode inspection, and the inline-fold
   verification techniques used in earlier work.
3. Root causes get fixed in our code or worked around at the integration
   point (e.g. R2's escalation for macro-emitted code).
4. If a regression is fundamentally tied to a Scala / Scala.js / DCA upstream
   issue: the issue gets fixed upstream (per R1, R3, R6 ladders) before the
   phase ships.

No phase ever ships with "small acceptable regression."

### R9 — DCA correctness vs our existing operator semantics

Goal: DCA-rewritten `.flatMap` chains produce values *bit-identical* to the
hand-written equivalent, including error propagation order, finalizer
execution order, and value identity through `==`.

Escalation:

1. Primary path: comprehensive property-based tests in `AsyncRewriteSpec`
   (Phase 2) comparing DCA-rewritten output to hand-written reference
   implementations.
2. If a divergence is found: fix in our `CpsMonad[Async]` /
   `CpsRuntimeAwait[Async]` implementation if the bug is on our side; PR
   upstream to DCA if the bug is in the macro; fork if upstream is slow
   (per R1.4).
3. If a category of divergences proves unfixable: replace DCA with our own
   Scala 3 macro (per R1.5). We've already committed to writing a Scala 2
   macro; doing the Scala 3 sibling is strictly cheaper.

## 10. What this plan does NOT cover

- New combinators on `Async` (e.g. `race`, `parZip`, fibers, structured
  concurrency). Separate plan.
- Scheduler / event-loop integration. The current design assumes the existing
  Pollable-driven model with user-provided `Waker` callbacks; richer
  schedulers are out of scope here.
- Cancellation. Mentioned in DCA's typeclass hierarchy
  (`CpsConcurrentMonad`); we don't pursue it in this plan.
- Direct-style (DCA's `CpsDirect`/compiler plugin). Adds complexity and is
  experimental in DCA itself. Out of scope.

---

End of plan. Next concrete action when work begins: Phase 0 rename PR.
