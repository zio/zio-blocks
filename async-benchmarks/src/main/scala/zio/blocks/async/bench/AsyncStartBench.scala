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

import java.util.concurrent.TimeUnit

/**
 * Allocation/throughput profile of `fa.start` for a SUSPENDED input — the path
 * that builds a `Running` handle backed by a background worker (JVM). The leaf
 * settles on its very first poll, so this isolates the per-`start`
 * handle/worker allocation (the `Running` object, its terminal slot, the daemon
 * `Thread`), NOT suspended-poll cost (covered by `AsyncSuspendedPollBench`). A
 * ready input is the trivial `CompletedRunning` path and is not measured here.
 *
 * Run with `-prof gc`; the `Thread` dominates, so this gate guards against
 * regressions in the handle's own object count (e.g. the cancellation-state
 * encoding).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class AsyncStartBench {

  var x: Int = 0

  @Setup
  def setup(): Unit = x = 7

  /** A suspended leaf that settles to `value` on its very first poll. */
  private final class ReadyLeaf(value: Int) extends Pollable[Int] {
    def poll(onComplete: Runnable): Async[Int] = Async.succeed(value)
  }

  @Benchmark def zb_startSuspended(): Int = {
    val fa: Async[Int] = new ReadyLeaf(x)
    fa.start.block
  }
}
