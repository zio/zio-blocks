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

import scala.concurrent.{Await, Future, ExecutionContext, Promise}
import scala.concurrent.duration.Duration

import java.util.concurrent.{Executors, ExecutorService, TimeUnit}

// ---- Cats Effect ----
import cats.effect.IO
import cats.effect.unsafe.implicits.global

// ---- Kyo ----
import kyo.{Async => KyoAsync, *}
import kyo.AllowUnsafe.embrace.danger

/**
 * ==True-async cross-runtime benchmarks==
 *
 * Unlike [[AsyncAsyncOpBench]] / [[AsyncHybridBench]] (whose "async" callback
 * fires SYNCHRONOUSLY inside the body, i.e. the value is already ready when
 * awaited), every operation here is genuinely '''pending''' when awaited and is
 * completed from ANOTHER thread. This measures the real suspend->resume cost:
 *
 *   - '''zb''' parks the JVM thread on a [[Completer]] and is woken when the
 *     completer settles off-thread (`LockSupport.unpark`).
 *   - '''fut''' parks the JMH thread in `Await.result` until the [[Promise]] is
 *     completed off-thread, with chain steps hopping a real fixed thread pool.
 *   - '''ce''' suspends a fiber in `IO.async_`; the callback (fired off-thread)
 *     reschedules the fiber onto Cats Effect's own compute pool.
 *   - '''kyo''' suspends a fiber on `Async.fromFuture`; the Future completion
 *     (off-thread) reschedules the fiber onto Kyo's own scheduler.
 *
 * ==Fairness / deadlock safety==
 *
 * ONE shared single-thread executor (`completer`, created in `@Setup`, shut
 * down in `@TearDown`) performs ALL completions. The awaiting JMH thread never
 * occupies that executor, so the single completer thread is always free to run
 * the completion while the JMH thread blocks (zb/fut) or while the fiber is
 * parked (ce/kyo). CE and Kyo resume their fibers on their OWN runtime pools,
 * which are disjoint from `completer`, so there is no starvation.
 *
 * These ops cross threads, so throughput is microsecond-scale and noisier than
 * the eager suite — that is the intended signal.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class AsyncTrueAsyncBench {

  // Shared, long-lived completion executor (one thread). All runtimes submit
  // their off-thread completion here; the awaiting JMH thread never runs on it.
  var completer: ExecutorService = uninitialized

  // Real EC for Future chain steps that genuinely hop threads (NOT parasitic).
  var futEc: ExecutionContext = uninitialized

  // Read-via-field so the value is never a literal in the body.
  var x: Int = 0

  @Param(Array("4", "16"))
  var n: Int = uninitialized

  @Setup
  def setup(): Unit = {
    completer = Executors.newSingleThreadExecutor()
    futEc = ExecutionContext.global
    x = 7
  }

  @TearDown
  def tearDown(): Unit = {
    completer.shutdown()
    completer.awaitTermination(5, TimeUnit.SECONDS)
    ()
  }

  // ==========================================================================
  // Shape 1: single async round-trip (one suspend + one cross-thread resume)
  // ==========================================================================

  @Benchmark def zb_asyncRoundTrip(): Int = {
    val c = new Completer[Int]
    val v = x
    completer.execute(() => c.succeed(v))
    c.peek.block
  }

  @Benchmark def fut_asyncRoundTrip(): Int = {
    val p = Promise[Int]()
    val v = x
    completer.execute(() => p.success(v))
    Await.result(p.future, Duration.Inf)
  }

  @Benchmark def ce_asyncRoundTrip(): Int = {
    val v = x
    IO.async_[Int](cb => completer.execute(() => cb(Right(v)))).unsafeRunSync()
  }

  @Benchmark def kyo_asyncRoundTrip(): Int = {
    val v = x
    val p = Promise[Int]()
    completer.execute(() => p.success(v))
    val effect: Int < (KyoAsync & Abort[Throwable]) = KyoAsync.fromFuture(p.future)
    kyoRun(effect)
  }

  // ==========================================================================
  // Shape 2: n SEQUENTIAL true-async boundaries chained via monadic bind.
  // Each step produces a fresh pending value completed off-thread.
  // ==========================================================================

  @Benchmark def zb_asyncChain(): Int = {
    val v = x
    var fa: Async[Int] = {
      val c = new Completer[Int]
      completer.execute(() => c.succeed(v))
      c.peek
    }
    var i = 1
    while (i < n) {
      fa = fa.flatMap { acc =>
        val c = new Completer[Int]
        completer.execute(() => c.succeed(acc + 1))
        c.peek
      }
      i += 1
    }
    fa.block
  }

  @Benchmark def fut_asyncChain(): Int = {
    val v = x
    var fa: Future[Int] = {
      val p = Promise[Int]()
      completer.execute(() => p.success(v))
      p.future
    }
    var i = 1
    while (i < n) {
      fa = fa.flatMap { acc =>
        val p = Promise[Int]()
        completer.execute(() => p.success(acc + 1))
        p.future
      }(futEc)
      i += 1
    }
    Await.result(fa, Duration.Inf)
  }

  @Benchmark def ce_asyncChain(): Int = {
    val v = x
    var io = IO.async_[Int](cb => completer.execute(() => cb(Right(v))))
    var i  = 1
    while (i < n) {
      io = io.flatMap { acc =>
        IO.async_[Int](cb => completer.execute(() => cb(Right(acc + 1))))
      }
      i += 1
    }
    io.unsafeRunSync()
  }

  @Benchmark def kyo_asyncChain(): Int = {
    val v = x
    def step(prev: Int): Int < (KyoAsync & Abort[Throwable]) = {
      val p = Promise[Int]()
      completer.execute(() => p.success(prev))
      KyoAsync.fromFuture(p.future)
    }
    var effect: Int < (KyoAsync & Abort[Throwable]) = step(v)
    var i = 1
    while (i < n) {
      val prevEffect = effect
      effect = prevEffect.map(acc => step(acc + 1))
      i += 1
    }
    kyoRun(effect)
  }

  /**
   * Drive a Kyo `Int < (Async & Abort[Throwable])` to a blocking `Int`:
   * reify the `Abort` to a `Result`, block on the `Async` (off-thread Future
   * completion reschedules the fiber on Kyo's own scheduler), then run the
   * residual `IO` unsafely and surface any failure.
   */
  private def kyoRun(effect: Int < (KyoAsync & Abort[Throwable])): Int = {
    val reified = Abort.run(effect)                                       // Result[Throwable, Int] < (Async & IO)
    val blocked = KyoAsync.runAndBlock(kyo.Duration.Infinity)(reified)    // Result[Throwable, Int] < IO
    kyo.IO.Unsafe.evalOrThrow(blocked).getOrThrow
  }
}
