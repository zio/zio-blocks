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

/**
 * Suspended-poll overhead: stresses the '''pending''' poll path of the
 * continuation pollables (`MapPollable` / `FlatMapPollable`), which is the only
 * path a per-poll change (e.g. a settled-outcome memo guarding fan-out
 * idempotency) would touch — the ready/fast path applies the user function
 * inline and never allocates these pollables at all.
 *
 * The shape ratchets the suspended work way up so any per-poll cost is visible:
 * a leaf that re-arms `rounds` times (each poll calls its waker synchronously
 * and returns itself) is wrapped in a `depth`-deep chain of `flatMap` / `map`.
 * Driving it to completion re-polls the whole chain once per round, so the
 * continuation pollables are polled on the order of `rounds * depth` times per
 * op — every one of those is a pending poll. If memoization adds a branch to
 * the top of each `poll`, this is where it shows up.
 *
 * Run with `-prof gc`. The control (`*Ready`) builds the identical chain over a
 * ready leaf (no suspension) to separate construction cost from poll cost.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncSuspendedPollBench {

  @Param(Array("1", "10", "100"))
  var depth: Int = uninitialized

  @Param(Array("100", "1000"))
  var rounds: Int = uninitialized

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  /**
   * A leaf that re-arms `n` times (synchronous wakeup) before yielding `value`.
   * Each re-arm returns `this`, so the surrounding chain is re-polled in full
   * by the driver — `rounds` pending poll rounds, each `depth` levels deep.
   */
  private final class ReArmLeaf(value: Int, var remaining: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] =
      if (remaining <= 0) Async.succeed(value)
      else {
        remaining -= 1
        onComplete.run()
        this
      }
  }

  // ---- suspended: deep flatMap chain over a many-round re-arming leaf --------

  @Benchmark def zb_flatMapChainSuspended(): Int = {
    var fa: Async[Int] = new ReArmLeaf(x, rounds)
    var i              = 0
    while (i < depth) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa.block
  }

  @Benchmark def zb_mapChainSuspended(): Int = {
    var fa: Async[Int] = new ReArmLeaf(x, rounds)
    var i              = 0
    while (i < depth) { fa = fa.map(_ + 1); i += 1 }
    fa.block
  }

  // ---- control: identical chain over a READY leaf (no suspension) -----------
  // Isolates chain construction from the suspended poll cost; `rounds` is
  // ignored here (the leaf is ready, so there is a single poll round).

  @Benchmark def zb_flatMapChainReady(): Int = {
    var fa: Async[Int] = Async.succeed(x)
    var i              = 0
    while (i < depth) { fa = fa.flatMap(v => Async.succeed(v + 1)); i += 1 }
    fa.block
  }
}
