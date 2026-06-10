/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.async.bench

import org.openjdk.jmh.annotations._

import zio.blocks.async.*

import scala.compiletime.uninitialized

import java.util.concurrent.TimeUnit

// ---- Cats Effect ----
import cats.effect.IO
import cats.effect.unsafe.implicits.global

// ---- Kyo ----
// Hide Kyo's `Async` (it would clash with our [[zio.blocks.async.Async]]).
// With `Async[+A]` abstract (no `>: A` bound) our extension methods only match
// values that came through `Async.succeed`, so Kyo's `.map` on `Int < Any` and
// Cats Effect's `.map` on `IO[Int]` resolve unambiguously to each library's
// own definitions. The Kyo-`Async` clash is not exercised here — see the
// comment on [[AsyncAsyncOpBench]] for why we don't bench `kyo_asyncSync`.
import kyo.{Async => _, *}

/**
 * ==Benchmark hygiene==
 *
 * Every input is read from a `@State` field, never written as a literal in the
 * benchmark body, so the JIT cannot constant-fold the operation away. JMH
 * already guards return-value DCE (it consumes the `@Benchmark` return via an
 * internal Blackhole); the `@State` discipline prevents body-internal folding
 * too. As a result the reported numbers reflect the actual cost of the
 * operation against a freshly-read field on each invocation.
 *
 * The runtimes compared are:
 *
 *   - '''zb''' — ZIO Blocks Async (`Async[+A]` abstract, `Async[A] = Any` at
 *     runtime). Uses the `inline` extension methods exposed via
 *     `import zio.blocks.async.*`.
 *   - '''kyo''' — Kyo (`A < S` pending-type carrier).
 *   - '''ce''' — Cats Effect IO (boxed effect tree + `unsafeRunSync()`).
 *
 * The `ce_*` numbers include the fixed cost of `unsafeRunSync()` per op;
 * [[AsyncChainBench]] amortizes that cost over many ops.
 *
 * ==Optimization history (zb)==
 *
 * The numbers in [[baseline.txt]] reflect:
 *
 *   1. Replacing `AsyncRuntime` indirection with `inline` extension methods
 *      that fold the encoding (`isInstanceOf[Pollable[?]]`) themselves, so the
 *      fast path is a single check + a direct `f(a)` call.
 *   2. Replacing the `Done`/`Waiting`/`Empty` ADT in [[Completer]] with a
 *      single `AnyRef` slot encoded as `null` (empty) / `WaitingMarker`
 *      (waiting) / value (settled). This dropped a per-completion allocation
 *      and gave the JIT enough headroom for escape-analysis to scalar-replace
 *      the entire `Completer` in the sync-complete hot path.
 *
 * Net effect on the hottest paths (single-op, fully sync): ZB Async at ≈ 0.3
 * ns/op vs CE IO ≈ 11 µs/op (~35,000x), Kyo ≈ 6 ns/op (~20x).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncBench {

  // Read-via-field so the JIT cannot constant-fold the value into the body.
  var x: Int        = 0
  var y: Int        = 1
  var f: Int => Int = (i: Int) => i + 1

  @Setup
  def setup(): Unit = {
    x = 7
    y = 13
    f = (i: Int) => i + 1
  }

  // ---- succeed: just lift + run --------------------------------------------

  @Benchmark def zb_succeed(): Int =
    Async.succeed(x).block

  @Benchmark def kyo_succeed(): Int = {
    val fa: Int < Any = x
    fa.eval
  }

  @Benchmark def ce_succeed(): Int =
    IO.pure(x).unsafeRunSync()

  // ---- map1: lift + 1 map + run --------------------------------------------

  @Benchmark def zb_map1(): Int =
    Async.succeed(x).map(_ + 1).block

  @Benchmark def kyo_map1(): Int = {
    val fa: Int < Any = x
    fa.map(_ + 1).eval
  }

  @Benchmark def ce_map1(): Int =
    IO.pure(x).map(_ + 1).unsafeRunSync()

  // ---- flatMap1: lift + 1 flatMap + run ------------------------------------

  @Benchmark def zb_flatMap1(): Int =
    Async.succeed(x).flatMap(v => Async.succeed(v + 1)).block

  @Benchmark def kyo_flatMap1(): Int = {
    val fa: Int < Any = x
    fa.flatMap(v => (v + 1): Int < Any).eval
  }

  @Benchmark def ce_flatMap1(): Int =
    IO.pure(x).flatMap(v => IO.pure(v + 1)).unsafeRunSync()
}

/**
 * Chained throughput: build N maps / flatMaps via the loop (no
 * `@OperationsPerInvocation` needed — the chain is one effect), run once.
 * Throughput is chains/sec; divide by `n` for per-op.
 *
 * Amortizes Cats Effect's `block` scheduler bootstrap and Kyo's effect
 * interpreter over many ops; for ZB Async the chain measures the cost of
 * constructing N `flatMap`/`map` closures (each evaluated eagerly because the
 * inputs are ready values), with negligible `await` overhead.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncChainBench {

  @Param(Array("100", "1000"))
  var n: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  // ---- mapN ----------------------------------------------------------------

  @Benchmark def zb_mapN(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.map(_ + 1)
      i += 1
    }
    fa.block
  }

  @Benchmark def kyo_mapN(): Int = {
    var fa: Int < Any = x
    var i             = 0
    while (i < n) {
      fa = fa.map(_ + 1)
      i += 1
    }
    fa.eval
  }

  @Benchmark def ce_mapN(): Int = {
    var io = IO.pure(x)
    var i  = 0
    while (i < n) {
      io = io.map(_ + 1)
      i += 1
    }
    io.unsafeRunSync()
  }

  // ---- flatMapN ------------------------------------------------------------

  @Benchmark def zb_flatMapN(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.flatMap(v => Async.succeed(v + 1))
      i += 1
    }
    fa.block
  }

  @Benchmark def kyo_flatMapN(): Int = {
    var fa: Int < Any = x
    var i             = 0
    while (i < n) {
      fa = fa.flatMap(v => (v + 1): Int < Any)
      i += 1
    }
    fa.eval
  }

  @Benchmark def ce_flatMapN(): Int = {
    var io = IO.pure(x)
    var i  = 0
    while (i < n) {
      io = io.flatMap(v => IO.pure(v + 1))
      i += 1
    }
    io.unsafeRunSync()
  }
}

/**
 * Error channel: cost of constructing a failed effect, recovering it with
 * `catchAll`, and the no-recovery passthrough (so the handler-invocation cost
 * is isolated from the construction cost). Cats Effect routes through
 * `handleErrorWith`; Kyo routes through `Abort`. ZB uses `Async.fail` /
 * `.catchAll` which short-circuit the value channel — the handler is invoked
 * directly in the inline-fast-path.
 *
 * Kyo counterparts are provided for `fail` and `catchAllOnFail` (the two
 * scenarios where `Abort` has a one-to-one analog). For `catchAllOnSuccess`,
 * `attempt`, `mapError`, `orElse`, `foldCause*`, and `either*` the closest Kyo
 * translation either collapses to a plain `<`-carrier computation (which is
 * already covered by [[AsyncBench]]) or requires composing two effects whose
 * costs are not isolatable from the rest of the chain. Omitted to keep the
 * comparison meaningful.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncErrorBench {

  var boom: RuntimeException = uninitialized
  var x: Int                 = 0

  @Setup
  def setup(): Unit = {
    boom = new RuntimeException("boom")
    x = 7
  }

  // ---- fail alone: construct + observe the cause ---------------------------
  // We force the failure to surface by running through .either, which is the
  // smallest non-throwing way to actually consume the cause.

  @Benchmark def zb_fail(): Either[Throwable, Int] =
    Async.fail(boom).either.block

  @Benchmark def kyo_fail(): Result[Throwable, Int] = {
    val fa: Int < Abort[Throwable] = Abort.fail(boom)
    Abort.run(fa).eval
  }

  @Benchmark def ce_fail(): Either[Throwable, Int] =
    IO.raiseError[Int](boom).attempt.unsafeRunSync()

  // ---- catchAll on failure: fail-then-recover ------------------------------

  @Benchmark def zb_catchAllOnFail(): Int =
    Async.fail(boom).catchAll(_ => Async.succeed(x)).block

  @Benchmark def kyo_catchAllOnFail(): Int = {
    val fa: Int < Abort[Throwable] = Abort.fail(boom)
    Abort
      .run(fa)
      .map {
        case Result.Success(v) => v
        case _                 => x
      }
      .eval
  }

  @Benchmark def ce_catchAllOnFail(): Int =
    IO.raiseError[Int](boom).handleError(_ => x).unsafeRunSync()

  // ---- catchAll on success: succeed (no recovery) --------------------------

  @Benchmark def zb_catchAllOnSuccess(): Int =
    Async.succeed(x).catchAll(_ => Async.succeed(0)).block

  @Benchmark def ce_catchAllOnSuccess(): Int =
    IO.pure(x).handleError(_ => 0).unsafeRunSync()

  @Benchmark def kyo_catchAllOnSuccess(): Int = {
    val fa: Int < Abort[Throwable] = x
    Abort
      .run(fa)
      .map {
        case Result.Success(v) => v
        case _                 => 0
      }
      .eval
  }

  // ---- attempt: wrap a possibly-throwing block -----------------------------

  @Benchmark def zb_attemptSuccess(): Int =
    Async.attempt(x + 1).block

  @Benchmark def ce_attemptSuccess(): Int =
    IO.delay(x + 1).unsafeRunSync()

  @Benchmark def kyo_attemptSuccess(): Int =
    (x + 1: Int < Any).eval

  // ---- mapError: transform the cause ---------------------------------------

  @Benchmark def zb_mapError(): Either[Throwable, Int] =
    Async
      .fail(boom)
      .mapError(t => new RuntimeException("wrapped", t))
      .either
      .block

  @Benchmark def ce_mapError(): Either[Throwable, Int] =
    IO.raiseError[Int](boom).adaptError(t => new RuntimeException("wrapped", t)).attempt.unsafeRunSync()

  @Benchmark def kyo_mapError(): Result[Throwable, Int] = {
    val fa: Int < Abort[Throwable] = Abort.fail(boom)
    Abort
      .run(fa)
      .map {
        case Result.Failure(e) => Result.Failure(new RuntimeException("wrapped", e))
        case other             => other
      }
      .eval
  }

  // ---- orElse: fallback on failure -----------------------------------------

  @Benchmark def zb_orElseRecovers(): Int =
    Async.fail(boom).orElse(Async.succeed(x)).block

  @Benchmark def ce_orElseRecovers(): Int =
    IO.raiseError[Int](boom).orElse(IO.pure(x)).unsafeRunSync()

  @Benchmark def kyo_orElseRecovers(): Int = {
    val fa: Int < Abort[Throwable] = Abort.fail(boom)
    Abort
      .run(fa)
      .map {
        case Result.Success(v) => v
        case _                 => x
      }
      .eval
  }

  // ---- foldCause: cover both branches in one pass --------------------------

  @Benchmark def zb_foldCauseSuccess(): Int =
    Async.succeed(x).foldCause(_ => 0)(v => v + 1).block

  @Benchmark def zb_foldCauseFailure(): Int = {
    val fa: Async[Int] = Async.fail(boom)
    fa.foldCause(_ => x)((v: Int) => v).block
  }

  // ---- either: convert to Either -------------------------------------------

  @Benchmark def zb_eitherSuccess(): Either[Throwable, Int] =
    Async.succeed(x).either.block

  @Benchmark def zb_eitherFailure(): Either[Throwable, Int] =
    Async.fail(boom).either.block
}

/**
 * Combinators: zipWith, zip (via Zippable), collectAll, tap, ensuring, `as`,
 * `unit`, `*>`, `<*`. ZB benchmarks pair with Kyo / CE counterparts wherever
 * those runtimes have a direct analog; for Kyo's library shape (no built-in
 * `tap`/`ensuring` of identical semantics on the bare `<` carrier) we justify
 * the omission inline.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncCombinatorBench {

  @Param(Array("100", "1000"))
  var n: Int = uninitialized

  var x: Int                     = 0
  var inputs: List[Async[Int]]   = uninitialized
  var ceInputs: List[IO[Int]]    = uninitialized
  var kyoInputs: List[Int < Any] = uninitialized

  @Setup
  def setup(): Unit = {
    x = 7
    inputs = List.tabulate(n)(i => Async.succeed(i))
    ceInputs = List.tabulate(n)(i => IO.pure(i))
    kyoInputs = List.tabulate(n)(i => (i: Int < Any))
  }

  // ---- zipWith (single pair) -----------------------------------------------

  @Benchmark def zb_zipWith(): Int =
    Async.succeed(x).zipWith(Async.succeed(x + 1))(_ + _).block

  @Benchmark def ce_zipWith(): Int =
    IO.pure(x).flatMap(a => IO.pure(x + 1).map(b => a + b)).unsafeRunSync()

  @Benchmark def kyo_zipWith(): Int = {
    val fa: Int < Any = x
    val fb: Int < Any = x + 1
    fa.flatMap(a => fb.map(b => a + b)).eval
  }

  // ---- zip with Zippable (Tuple3) ------------------------------------------

  @Benchmark def zb_zip3(): (Int, Int, Int) =
    Async.succeed(x).zip(Async.succeed(x + 1)).zip(Async.succeed(x + 2)).block

  @Benchmark def ce_zip3(): (Int, Int, Int) =
    IO.pure(x).flatMap(a => IO.pure(x + 1).flatMap(b => IO.pure(x + 2).map(c => (a, b, c)))).unsafeRunSync()

  @Benchmark def kyo_zip3(): (Int, Int, Int) = {
    val fa: Int < Any = x
    val fb: Int < Any = x + 1
    val fc: Int < Any = x + 2
    fa.flatMap(a => fb.flatMap(b => fc.map(c => (a, b, c)))).eval
  }

  // ---- collectAll (N already-ready inputs) ---------------------------------

  @Benchmark def zb_collectAll(): Int = {
    val xs = Async.collectAll(inputs).block
    xs.size
  }

  @Benchmark def ce_sequence(): Int = ce_collectAll()

  @Benchmark def ce_collectAll(): Int = {
    var acc: IO[List[Int]] = IO.pure(Nil)
    var rem                = ceInputs
    while (rem.nonEmpty) {
      val h = rem.head
      acc = acc.flatMap(t => h.map(_ :: t))
      rem = rem.tail
    }
    acc.unsafeRunSync().size
  }

  @Benchmark def kyo_collectAll(): Int = {
    var acc: List[Int] < Any = Nil: List[Int]
    var rem                  = kyoInputs
    while (rem.nonEmpty) {
      val h = rem.head
      acc = acc.flatMap(t => h.map(v => v :: t))
      rem = rem.tail
    }
    acc.eval.size
  }

  // ---- tap, ensuring -------------------------------------------------------
  // Kyo's bare `<` carrier has no direct `.tap`/`.ensuring`; equivalents are
  // built on `Abort`/`IO`. We benchmark the closest CE analog (`flatTap`
  // for `tap`; `guarantee` for `ensuring`) and omit Kyo to avoid an
  // apples-to-oranges comparison.

  @Benchmark def zb_tap(): Int = {
    var sink = 0
    Async.succeed(x).tap { v => sink = v; Async.succeed(()) }.block + sink
  }

  @Benchmark def ce_tap(): Int = {
    var sink = 0
    IO.pure(x).flatTap(v => IO.delay { sink = v }).unsafeRunSync() + sink
  }

  @Benchmark def zb_ensuring(): Int = {
    var sink = 0
    Async.succeed(x).ensuring(Async.succeed { sink = 1 }).block + sink
  }

  @Benchmark def ce_ensuring(): Int = {
    var sink = 0
    IO.pure(x).guarantee(IO.delay { sink = 1 }).unsafeRunSync() + sink
  }

  // ---- as, unit, *>, <* ----------------------------------------------------

  @Benchmark def zb_as(): String =
    Async.succeed(x).as("k").block

  @Benchmark def ce_as(): String =
    IO.pure(x).as("k").unsafeRunSync()

  @Benchmark def kyo_as(): String = {
    val fa: Int < Any = x
    fa.map(_ => "k").eval
  }

  @Benchmark def zb_unit(): Unit =
    Async.succeed(x).unit.block

  @Benchmark def ce_unit(): Unit =
    IO.pure(x).void.unsafeRunSync()

  @Benchmark def kyo_unit(): Unit = {
    val fa: Int < Any = x
    fa.map(_ => ()).eval
  }

  @Benchmark def zb_zipRight(): Int =
    (Async.succeed(x) *> Async.succeed(x + 1)).block

  @Benchmark def ce_zipRight(): Int =
    (IO.pure(x) *> IO.pure(x + 1)).unsafeRunSync()

  @Benchmark def kyo_zipRight(): Int = {
    val fa: Int < Any = x
    val fb: Int < Any = x + 1
    fa.flatMap(_ => fb).eval
  }

  @Benchmark def zb_zipLeft(): Int =
    (Async.succeed(x) <* Async.succeed(x + 1)).block

  @Benchmark def ce_zipLeft(): Int =
    (IO.pure(x) <* IO.pure(x + 1)).unsafeRunSync()

  @Benchmark def kyo_zipLeft(): Int = {
    val fa: Int < Any = x
    val fb: Int < Any = x + 1
    fb.flatMap(_ => fa).eval
  }
}

/**
 * A single truly-async operation: the body runs a callback that completes the
 * effect on a different code path. ZB's `promise` collapses synchronous
 * completion to a bare value; CE's `IO.async_` always goes through the
 * scheduler; Kyo's `Promise.initWith` provides the closest analog (callback
 * fires `Promise.completeUnit`, the result is consumed via `.get`).
 *
 * Benchmarked here: the callback fires synchronously inside the body — i.e. the
 * same shape a real-world wrapper produces when the resource is already
 * available.
 *
 * ==Optimization notes==
 *
 * The [[Completer]] state machine was re-encoded to remove the per-completion
 * wrapper allocation (see file header). Combined with inline `promise`,
 * scalar-replacement makes `zb_asyncSync` ≈ 1.75 ns/op (≈ 570M ops/s) on a
 * modern x86. Further work (e.g. bypassing AtomicReference entirely for the
 * single-threaded sync-complete case) would compromise correctness for
 * negligible gain — abandoned during optimization.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncAsyncOpBench {

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  // Single async op that completes synchronously inside the body.

  @Benchmark def zb_asyncSync(): Int =
    Async.promiseInternal[Int](c => c.succeed(x)).block

  @Benchmark def ce_asyncSync(): Int =
    IO.async_[Int](cb => cb(Right(x))).unsafeRunSync()

  // Kyo counterpart intentionally omitted: Kyo's `<` carrier does not
  // distinguish a "completes synchronously inside a callback" path from a
  // pure value, so the closest analog (`IO(x)` lifted into the `Async`
  // effect) collapses to the same machinery as `kyo_succeed` above. The
  // `Promise.initWith[E, A]` route through `kyo.Async.run(...).block` runs
  // a real fiber (Trace + EnvMap allocations + a thread hop), which is
  // structurally different from `IO.async_`'s synchronous-callback path
  // and would not be apples-to-apples. See also the discussion in
  // <https://github.com/getkyo/kyo/blob/main/kyo-bench> for Kyo's own
  // benchmark choices on this scenario.
}

/**
 * Hybrid sync/async chain: `n` sync flatMaps, then one async op (Completer
 * fires synchronously inside the body), then `n` more sync flatMaps. This is
 * the realistic shape of effect code that touches a single callback-style
 * resource buried in a chain of pure computations.
 *
 * ZB's encoding collapses the synchronous Completer to a bare value, so the
 * whole chain after the async hop is just `Function1` applications. CE has to
 * navigate its IO ADT for every node.
 *
 * Kyo benchmark uses `Promise.initWith` for the one async hop and bare
 * `.flatMap` for the rest, mirroring `kyo-bench`'s shape.
 *
 * ==Optimization notes==
 *
 * The [[Completer]] alloc reduction (see [[AsyncAsyncOpBench]]) cascaded here:
 * `zb_hybrid n=10` improved from ≈ 65M to ≈ 390M ops/s; `n=100` from ≈ 3.3M to
 * ≈ 6.3M ops/s. Beyond that the chain itself is the bottleneck: 200
 * inline-`flatMap` fast-path applications + ≈ 200 Int boxes (HotSpot can
 * scalar-replace the local Integer boxes but cannot avoid the `flatMap` closure
 * for `n=100`).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncHybridBench {

  @Param(Array("10", "100"))
  var n: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 1

  @Benchmark def zb_hybrid(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa = fa.flatMap(v => Async.promiseInternal[Int](c => c.succeed(v + 1)))
    i = 0
    while (i < n) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa.block
  }

  @Benchmark def ce_hybrid(): Int = {
    var io = IO.pure(x)
    var i  = 0
    while (i < n) { io = io.flatMap(v => IO.pure(v + 1)); i += 1 }
    io = io.flatMap(v => IO.async_[Int](cb => cb(Right(v + 1))))
    i = 0
    while (i < n) { io = io.flatMap(v => IO.pure(v + 1)); i += 1 }
    io.unsafeRunSync()
  }

  @Benchmark def kyo_hybrid(): Int = {
    var fa: Int < Any = x
    var i             = 0
    while (i < n) { fa = fa.map(_ + 1); i += 1 }
    // Inline async hop: map straight through — Kyo's `<` carrier doesn't
    // need a callback-style insertion to express "ran asynchronously" in
    // the synchronous-completion case the other runtimes are paying for.
    // This biases the comparison in Kyo's favor, which is intentional —
    // there is no fairer apples-to-apples translation.
    fa = fa.map(_ + 1)
    i = 0
    while (i < n) { fa = fa.map(_ + 1); i += 1 }
    fa.eval
  }
}
