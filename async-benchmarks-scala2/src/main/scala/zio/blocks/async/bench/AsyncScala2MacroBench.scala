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

import zio.blocks.async._

import java.util.concurrent.TimeUnit

/**
 * Scala-2-only JMH gate for the direct-style `Async.async { ... .await ... }`
 * macro (`internal.AsyncMacros`).
 *
 * The Scala 2 macro is a different code generator from dotty-cps-async, so this
 * exists to prove the GENERATED CODE SHAPE of the Scala 2 rewrite — not to
 * compare throughput against the Scala 3 cell. Run it with `-prof gc`; the
 * acceptance criterion is allocation behavior:
 *
 *   - ready scalar awaits collapse to the hand-written `flatMap` control (and to
 *     near-zero allocation under escape analysis);
 *   - a `var` mutated across `.await` inside a `while` allocates a FIXED
 *     (constant-in-`n`) ref-cell cost, not per-link garbage;
 *   - HOF emitters (especially the newest `Array` paths) allocate only the
 *     designed builder / collection result, with the element type preserved.
 *
 * Each direct-style row has a hand-written same-module `flatMap` control so the
 * macro output can be compared link-for-link.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncScala2MacroBench {

  @Param(Array("1", "10", "100", "1000"))
  var n: Int = 0

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  // ---- runtime fast-path sanity (no macro) ---------------------------------

  @Benchmark def zb_succeed(): Int = Async.succeed(x).block

  @Benchmark def zb_map1(): Int = Async.succeed(x).map(_ + 1).block

  @Benchmark def zb_flatMap1(): Int = Async.succeed(x).flatMap(v => Async.succeed(v + 1)).block

  @Benchmark def zb_flatMapControlN(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.flatMap(v => Async.succeed(v + 1))
      i += 1
    }
    fa.block
  }

  // ---- direct-style scalar `.await` (macro rewrite) ------------------------

  @Benchmark def zb_asyncAwait1(): Int =
    Async.async {
      Async.succeed(x).await + 1
    }.block

  @Benchmark def zb_asyncAwaitSeqVals(): Int =
    Async.async {
      val a = Async.succeed(x).await
      val b = Async.succeed(a + 1).await
      b
    }.block

  @Benchmark def zb_asyncAwaitWhileVarN(): Int =
    Async.async {
      var acc = x
      var i   = 0
      while (i < n) {
        acc = Async.succeed(acc + 1).await
        i += 1
      }
      acc
    }.block

  // ---- HOF-closure `.await` emitters ---------------------------------------
  // One row per distinct emit strategy in `AsyncMacros`. The collections are
  // fixed-size (independent of `n`) so each row measures a single emitter's
  // generated shape, not iteration count.

  @Benchmark def zb_listMap(): List[Int] =
    Async.async(List(1, 2, 3).map(i => Async.succeed(i + x).await)).block

  @Benchmark def zb_vectorMap(): Vector[Int] =
    Async.async(Vector(1, 2, 3).map(i => Async.succeed(i + x).await)).block

  @Benchmark def zb_optionMap(): Option[Int] =
    Async.async(Option(x).map(i => Async.succeed(i + 1).await)).block

  @Benchmark def zb_mapMap(): Map[Int, Int] =
    Async.async(Map(1 -> 10, 2 -> 20).map { case (k, v) => (k, Async.succeed(v + x).await) }).block

  @Benchmark def zb_foldLeft(): Int =
    Async.async(List(1, 2, 3).foldLeft(0)((a, e) => a + Async.succeed(e).await)).block

  @Benchmark def zb_collect(): List[Int] =
    Async.async(List(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i + x).await }).block

  // ---- Array emitters (newest / highest-risk paths) ------------------------
  // `emitArrayMap` (eager, primitive-result preservation), `emitArrayFlatMap`
  // (lazy drain into `Array.newBuilder`, raw-array normalization), and the
  // `Array.newBuilder`-backed `filter`.

  @Benchmark def zb_arrayMapPrimitive(): Array[Long] =
    Async.async(Array(1, 2, 3).map(i => Async.succeed(i.toLong + x).await)).block

  @Benchmark def zb_arrayFlatMap(): Array[Int] =
    Async.async(Array(1, 2, 3).flatMap(i => Array(Async.succeed(i + x).await, i * 10))).block

  @Benchmark def zb_arrayFilter(): Array[Int] =
    Async.async(Array(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await)).block
}
