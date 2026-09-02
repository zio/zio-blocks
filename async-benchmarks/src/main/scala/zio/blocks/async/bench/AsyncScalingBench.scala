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
import org.openjdk.jmh.infra.Blackhole

import zio.blocks.async.*

import scala.compiletime.uninitialized

import java.util.concurrent.TimeUnit

/**
 * Experiment: is the n=100 → n=1000 throughput cliff caused by (H1) JIT loop
 * unrolling / constant-propagation at small n, or by (H2) super-linear cost
 * inside `flatMap` / `await` at large n?
 *
 * Method: vary n across six orders of magnitude. Compute per-op cost = (1 /
 * ops_per_sec) / n.
 *
 *   - H1 prediction: per-op cost approaches zero at small n (JIT folded the
 *     loop away), then stabilizes at the true per-flatMap cost above some
 *     unrolling threshold (typically n ≥ ~64 in HotSpot).
 *   - H2 prediction: per-op cost keeps growing with n (e.g. log n or worse), so
 *     per-call time grows super-linearly.
 *
 * Three variants:
 *
 *   1. `chain` — N flatMaps over a value, no async hop. The Async case NEVER
 *      allocates a [[Pollable]] (each step produces a bare value), so `await`
 *      is a single isInstanceOf check.
 *   2. `chainBh` — same chain, but `Blackhole.consume(fa)` is called every
 *      iteration. Defeats constant-propagation across loop iterations and
 *      forces the JIT to materialize each `fa`.
 *   3. `noop` — control: a plain `Int` accumulator add in a tight `while` loop.
 *      Establishes the cost of the loop body *without* Async at all, so we can
 *      isolate the Async-specific overhead.
 *
 * If `chain` shows H1's shape (cliff) and `chainBh` shows linear scaling from
 * the start, that's conclusive: the cliff is JIT folding the lambda
 * applications across iterations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncScalingBench {

  @Param(Array("1", "10", "100", "1000", "10000", "100000"))
  var n: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  // ---- 1) Pure chain, no Blackhole ----------------------------------------
  // This is the same shape as AsyncChainBench.zb_flatMapN. If H1 is right we
  // should see ops/sec * n grow super-linearly with n at small n, then
  // stabilize.

  @Benchmark def zb_chain(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.flatMap(v => Async.succeed(v + 1))
      i += 1
    }
    fa.block
  }

  // ---- 2) Pure chain WITH Blackhole every iteration -----------------------
  // The JIT cannot constant-propagate `fa` across loop iterations because
  // each iteration's intermediate `fa` escapes via the Blackhole. Cost per
  // iteration becomes (real flatMap cost) + (Blackhole.consume cost).
  // Compare against #1: if the cliff disappears here, it was JIT folding.

  @Benchmark def zb_chainBh(bh: Blackhole): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < n) {
      fa = fa.flatMap(v => Async.succeed(v + 1))
      bh.consume(fa)
      i += 1
    }
    fa.block
  }

  // ---- 3) Control: pure Int accumulator, no Async at all ------------------
  // Establishes the baseline cost of the loop itself (i++, compare, add).
  // The difference between `noop` and `zb_chain` at the same n is the
  // Async-specific overhead.

  @Benchmark def noop(): Int = {
    var acc = x
    var i   = 0
    while (i < n) {
      acc = acc + 1
      i += 1
    }
    acc
  }
}
