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

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

import zio.blocks.async._

/**
 * JS-native micro-benchmark harness for [[zio.blocks.async.Async]].
 *
 * JMH is JVM-only, so this is a hand-rolled Node benchmark: it measures
 * throughput (ops/sec) via `System.nanoTime` and per-op allocation via Node
 * heap deltas around `global.gc()` (requires the Node `--expose-gc` flag, which
 * the sbt `jsEnv` for this module supplies). Numbers are noisier than JMH — we
 * take the best (lowest-allocation / highest-throughput) of several trials and
 * report that, which is the appropriate gate for "this hot path does/doesn't
 * allocate" questions.
 *
 * It exercises the SAME ready-value hot paths the JVM JMH gate does (`succeed`
 * / `map` / `flatMap` / direct-style `.await`), so the JS-native
 * `js.async`/`js.await` cell (Scala 3.8+) and the DCA cell (Scala 3.3.x JS) can
 * each be measured by selecting the Scala version (`++3.8.3` vs `++3.3.7`).
 * Every benchmark drives its `Async` to a value with `.block`; on JS `.block`
 * only succeeds for already-resolved values, which is exactly the zero-cost hot
 * path under test (no genuinely-suspended work, no thrown "cannot block").
 */
object AsyncJsBench {

  // ---- environment shims ----------------------------------------------------

  private def heapUsed(): Double =
    g.process.memoryUsage().heapUsed.asInstanceOf[Double]

  /**
   * Force a full GC when Node was started with `--expose-gc`; else best-effort.
   */
  private def forceGc(): Unit = {
    val gc = g.selectDynamic("gc")
    if (!js.isUndefined(gc)) {
      val f = gc.asInstanceOf[js.Function0[Any]]
      // Two passes: the first frees, the second compacts young-gen survivors.
      f(); f(); ()
    }
  }

  private val hasGc: Boolean = !js.isUndefined(g.selectDynamic("gc"))

  // ---- the benchmarks (ready-value hot paths) -------------------------------
  // A volatile-ish sink to defeat dead-code elimination.

  private var sink: Int = 0

  private def benchSucceed(): Int = {
    val a: Async[Int] = Async.succeed(1)
    a.block
  }

  private def benchMap1(): Int = {
    val a: Async[Int] = Async.succeed(1)
    a.map(_ + 1).block
  }

  private def benchFlatMap1(): Int = {
    val a: Async[Int] = Async.succeed(1)
    a.flatMap(x => Async.succeed(x + 1)).block
  }

  private def benchAsyncAwait1(): Int =
    Async.async(Async.succeed(1).await + 1).block

  private def benchAsyncAwaitSeq(): Int =
    Async.async {
      val a = Async.succeed(1).await
      val b = Async.succeed(2).await
      val c = Async.succeed(3).await
      a + b + c
    }.block

  /** N sync awaits then a fold — amortizes the `async` block setup over N. */
  private def benchAsyncAwaitChainN(n: Int): Int =
    Async.async {
      var acc = 0
      var i   = 0
      while (i < n) {
        acc += Async.succeed(i).await
        i += 1
      }
      acc
    }.block

  // ---- suspended-path harness -----------------------------------------------
  // The ready benchmarks above all settle synchronously inside `.block`. The
  // GENUINELY-suspended driver — where each wakeup re-enters the poll loop on a
  // microtask — is a different path (`AsyncInterop.toFuture` / `start`), and is
  // the one a per-wakeup allocation change touches. `HopChain` suspends `hops`
  // times, re-arming its waker each poll; driving it to a value costs `hops`
  // microtask hops. We drive `n` chains SEQUENTIALLY (one in flight at a time,
  // to bound the microtask queue) and report hops/sec across the batch. This is
  // async, so it completes on a callback after `main` returns.

  private final class HopChain(value: Int, private var remaining: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (remaining <= 0) Async.succeed(value)
      else { remaining -= 1; onComplete.run(); this }
  }

  private def measureSuspended(label: String, hops: Int, warmup: Int, n: Int)(next: () => Unit): Unit = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    def run(count: Int, started: Double, onDone: Double => Unit): Unit = {
      var completed   = 0
      def one(): Unit =
        AsyncInterop.toFuture((new HopChain(7, hops)): Async[Int]).onComplete { _ =>
          completed += 1
          if (completed < count) one() else onDone(now() - started)
        }
      one()
    }
    // Warmup batch, then a timed batch; print on completion, then chain `next`.
    run(
      warmup,
      now(),
      { _ =>
        val start = now()
        run(
          n,
          start,
          { elapsedNs =>
            val hopsPerSec = (n.toDouble * hops) / (elapsedNs / 1e9)
            println(f"  $label%-22s ${hopsPerSec}%16.0f ${"hops/sec"}%12s")
            next()
          }
        )
      }
    )
  }

  // `Async.start(body)` evaluates `body` on a microtask (the `startEval` path)
  // and returns a `Running` driven on further microtasks; `toFuture` joins it.
  // Measures starts/sec across a sequential batch (one in flight at a time).
  private def measureStart(label: String, warmup: Int, n: Int)(next: () => Unit): Unit = {
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    def run(count: Int, started: Double, onDone: Double => Unit): Unit = {
      var completed   = 0
      def one(): Unit =
        AsyncInterop.toFuture(Async.start(7): Async[Int]).onComplete { _ =>
          completed += 1
          if (completed < count) one() else onDone(now() - started)
        }
      one()
    }
    run(
      warmup,
      now(),
      { _ =>
        val start = now()
        run(
          n,
          start,
          { elapsedNs =>
            val startsPerSec = n.toDouble / (elapsedNs / 1e9)
            println(f"  $label%-22s ${startsPerSec}%16.0f ${"starts/sec"}%12s")
            next()
          }
        )
      }
    )
  }

  // ---- measurement engine ---------------------------------------------------

  private final case class Result(name: String, opsPerSec: Double, bytesPerOp: Double)

  private def now(): Double = System.nanoTime().toDouble

  /**
   * Measure one benchmark: `warmupIters` warmup ops, then `trials` timed/alloc
   * trials of `measureIters` ops each; report the best throughput and the
   * lowest per-op allocation observed.
   */
  private def measure(
    name: String,
    warmupIters: Int,
    measureIters: Int,
    trials: Int
  )(op: () => Int): Result = {
    // Warmup (let the JIT settle).
    var w = 0
    while (w < warmupIters) { sink ^= op(); w += 1 }

    var bestOps   = 0.0
    var bestBytes = Double.MaxValue
    var t         = 0
    while (t < trials) {
      // Throughput.
      val start = now()
      var i     = 0
      var local = 0
      while (i < measureIters) { local ^= op(); i += 1 }
      val elapsedNs = now() - start
      sink ^= local
      val ops = measureIters.toDouble / (elapsedNs / 1e9)
      if (ops > bestOps) bestOps = ops

      // Allocation (heap delta around a forced GC).
      if (hasGc) {
        forceGc()
        val before = heapUsed()
        var j      = 0
        var l2     = 0
        while (j < measureIters) { l2 ^= op(); j += 1 }
        val after = heapUsed()
        sink ^= l2
        val perOp = (after - before) / measureIters.toDouble
        if (perOp >= 0 && perOp < bestBytes) bestBytes = perOp
      }
      t += 1
    }
    Result(name, bestOps, if (hasGc) bestBytes else Double.NaN)
  }

  def main(args: Array[String]): Unit = {
    val warmup  = 200000
    val iters   = 1000000
    val trials  = 8
    val results = scala.collection.mutable.ListBuffer.empty[Result]

    results += measure("succeed", warmup, iters, trials)(() => benchSucceed())
    results += measure("map1", warmup, iters, trials)(() => benchMap1())
    results += measure("flatMap1", warmup, iters, trials)(() => benchFlatMap1())
    results += measure("asyncAwait1", warmup, iters, trials)(() => benchAsyncAwait1())
    results += measure("asyncAwaitSeq3", warmup, iters, trials)(() => benchAsyncAwaitSeq())
    results += measure("asyncAwaitChain10", warmup, iters / 4, trials)(() => benchAsyncAwaitChainN(10))
    results += measure("asyncAwaitChain100", warmup, iters / 16, trials)(() => benchAsyncAwaitChainN(100))

    println("# AsyncJsBench (zio-blocks-async, JS-native cell)")
    println(s"# Node ${g.process.version.asInstanceOf[String]}, --expose-gc=$hasGc")
    println(f"# ${"benchmark"}%-22s ${"ops/sec"}%16s ${"B/op"}%12s")
    results.foreach { r =>
      val bytes = if (r.bytesPerOp.isNaN) "n/a" else f"${r.bytesPerOp}%.1f"
      println(f"  ${r.name}%-22s ${r.opsPerSec}%16.0f ${bytes}%12s")
    }
    // Print the sink so the optimizer cannot eliminate the work.
    if (sink == Int.MinValue) println(s"sink=$sink")

    // Suspended-path + start throughput (async; print after the sync block).
    // Chained so the runs do not interleave their microtask queues.
    measureSuspended("suspend.hop8", hops = 8, warmup = 20000, n = 100000) { () =>
      measureSuspended("suspend.hop32", hops = 32, warmup = 10000, n = 50000) { () =>
        measureStart("start", warmup = 20000, n = 100000)(() => ())
      }
    }
  }
}
