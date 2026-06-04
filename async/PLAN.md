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
- **Phases 4–5:** Not started.

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
| `.await` inside `xs.map(_)`  closure | JVM: blocks (fallback); JS 3.8.4+: compile error; JS 3.3.7: compile error or runtime throw depending on DCA position; Scala 2 (after macro): same as Scala 3 per platform. |
| `for (x <- xs) yield x.await` | Same as HOF closure but for-comprehension shape. |
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
