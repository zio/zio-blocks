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
import kyo.{Async => _, *}

/**
 * Direct-style `.await` vs hand-written `flatMap`: proves the
 * `Async.async { ... .await ... }` rewrite (dotty-cps-async on JVM Scala 3)
 * has the same cost model as a hand-written `flatMap` chain for ready values.
 *
 * The body does `n` sequential `.await`s of already-ready values inside a
 * single `Async.async` block, then `.block`s the result. The control builds
 * the equivalent `n`-link `flatMap` chain by hand and `.block`s it. Both do
 * the same logical work; the only difference is whether the chain came from
 * the macro rewrite or from explicit `flatMap` calls.
 *
 * Gate: `zb_asyncAwaitN` must be within JMH noise / ≤5% of `zb_flatMapControlN`
 * for every `n`. Any larger gap means the macro rewrite is paying overhead the
 * hand-written path is not — a Phase 2/5 regression.
 *
 * `ce_flatMapN` / `kyo_flatMapN` are the cross-runtime comparison baselines
 * (same `n`-link chain shape, each library's own driver).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncBlockBench {

  @Param(Array("1", "10", "100", "1000"))
  var n: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  // ---- hand-written flatMap chain (control) --------------------------------

  @Benchmark def zb_flatMapControlN(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.flatMap(v => Async.succeed(v + 1))
      i += 1
    }
    fa.block
  }

  // ---- direct-style .await chain (macro rewrite) ---------------------------

  @Benchmark def zb_asyncAwaitN(): Int =
    Async.async {
      var acc = x
      var i   = 0
      while (i < n) {
        acc = Async.succeed(acc + 1).await
        i += 1
      }
      acc
    }.block

  // ---- cross-runtime baselines ---------------------------------------------

  @Benchmark def ce_flatMapN(): Int = {
    var io = IO.pure(x)
    var i  = 0
    while (i < n) {
      io = io.flatMap(v => IO.pure(v + 1))
      i += 1
    }
    io.unsafeRunSync()
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
}

/**
 * Direct-style hybrid: `n` ready `.await`s, then one callback-style async hop
 * (`Async.promise` completing synchronously inside the body), then `n` more
 * ready `.await`s — all inside a single `Async.async` block. Compared against
 * the hand-written `flatMap`-chain equivalent.
 *
 * This validates the Phase 4 bet (and the Phase 2/5 rewrite) under the
 * realistic shape of effect code that touches a single callback-style resource
 * buried in a chain of pure computations.
 *
 * Gate: `zb_asyncAwaitHybrid` must be within JMH noise / ≤5% of the
 * hand-written `zb_flatMapHybridControl` for the same `n`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncBlockHybridBench {

  @Param(Array("1", "10", "100", "1000"))
  var n: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 1

  // ---- hand-written flatMap chain (control) --------------------------------

  @Benchmark def zb_flatMapHybridControl(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa = fa.flatMap(v => Async.promiseInternal[Int](c => c.succeed(v + 1)))
    i = 0
    while (i < n) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa.block
  }

  // ---- direct-style .await chain (macro rewrite) ---------------------------

  @Benchmark def zb_asyncAwaitHybrid(): Int =
    Async.async {
      var acc = x
      var i   = 0
      while (i < n) { acc = Async.succeed(acc + 1).await; i += 1 }
      acc = Async.promise[Int] { succeed(acc + 1) }.await
      i = 0
      while (i < n) { acc = Async.succeed(acc + 1).await; i += 1 }
      acc
    }.block

  // ---- cross-runtime baselines ---------------------------------------------

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
    // Pure-biased: Kyo's `<` carrier has no synchronous callback hop to pay
    // for, so the one async node collapses to a plain map. Intentionally
    // favors Kyo — there is no fairer apples-to-apples translation.
    fa = fa.map(_ + 1)
    i = 0
    while (i < n) { fa = fa.map(_ + 1); i += 1 }
    fa.eval
  }
}
