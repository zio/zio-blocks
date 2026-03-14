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

package zio.blocks.scope

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Domain types for benchmarking DI wiring overhead.
// No AutoCloseable — we isolate Context build/get cost from finalization.
// ---------------------------------------------------------------------------

final class Dep1(val value: Int = 1)
final class Dep2(val value: Int = 2)
final class Dep3(val value: Int = 3)
final class Dep4(val value: Int = 4)
final class Dep5(val value: Int = 5)
final class Dep6(val value: Int = 6)
final class Dep7(val value: Int = 7)
final class Dep8(val value: Int = 8)
final class Dep9(val value: Int = 9)
final class Dep10(val value: Int = 10)

final class Service1(val d1: Dep1)
final class Service5(val d1: Dep1, val d2: Dep2, val d3: Dep3, val d4: Dep4, val d5: Dep5)
final class Service10(
  val d1: Dep1,
  val d2: Dep2,
  val d3: Dep3,
  val d4: Dep4,
  val d5: Dep5,
  val d6: Dep6,
  val d7: Dep7,
  val d8: Dep8,
  val d9: Dep9,
  val d10: Dep10
)

// Diamond: SharedDep used by both BranchA and BranchB
final class SharedDep(val value: Int = 42)
final class BranchA(val dep: SharedDep)
final class BranchB(val dep: SharedDep)
final class DiamondRoot(val a: BranchA, val b: BranchB)

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xnoclassgc",
    "-Xms3g",
    "-Xmx3g",
    "-Xss4m",
    "-XX:NewSize=2g",
    "-XX:MaxNewSize=2g",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:TLABSize=16m",
    "-XX:-ResizeTLAB",
    "-XX:+UseParallelGC",
    "-XX:-UseAdaptiveSizePolicy",
    "-XX:MaxInlineLevel=20",
    "-XX:InlineSmallCode=2500",
    "-XX:+AlwaysPreTouch",
    "-XX:+PerfDisableSharedMem",
    "-XX:-UsePerfData",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields"
  )
)
class WiringBenchmark {

  // Pre-created dependencies for direct construction baseline
  private val d1  = new Dep1
  private val d2  = new Dep2
  private val d3  = new Dep3
  private val d4  = new Dep4
  private val d5  = new Dep5
  private val d6  = new Dep6
  private val d7  = new Dep7
  private val d8  = new Dep8
  private val d9  = new Dep9
  private val d10 = new Dep10
  private val sd  = new SharedDep

  // Resource factories: each call returns a fresh Resource (new Shared state).
  // The macro expands at compile time; only the wrapper objects are new per call.
  private def mkRes1(): Resource[Service1] =
    Resource.from[Service1](Wire(d1))

  private def mkRes5(): Resource[Service5] =
    Resource.from[Service5](Wire(d1), Wire(d2), Wire(d3), Wire(d4), Wire(d5))

  private def mkRes10(): Resource[Service10] =
    Resource.from[Service10](
      Wire(d1),
      Wire(d2),
      Wire(d3),
      Wire(d4),
      Wire(d5),
      Wire(d6),
      Wire(d7),
      Wire(d8),
      Wire(d9),
      Wire(d10)
    )

  private def mkResDiamond(): Resource[DiamondRoot] =
    Resource.from[DiamondRoot](Wire(sd))

  // ---------------------------------------------------------------------------
  // Baseline: direct construction (no DI, no scope overhead)
  // ---------------------------------------------------------------------------

  @Benchmark
  def direct_1dep(bh: Blackhole): Unit =
    bh.consume(new Service1(d1))

  @Benchmark
  def direct_5deps(bh: Blackhole): Unit =
    bh.consume(new Service5(d1, d2, d3, d4, d5))

  @Benchmark
  def direct_10deps(bh: Blackhole): Unit =
    bh.consume(new Service10(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10))

  // ---------------------------------------------------------------------------
  // Resource.from wiring: full cycle (open scope + make + close)
  // Measures Context build + get + scope/finalizer overhead per allocation.
  // ---------------------------------------------------------------------------

  @Benchmark
  def wireup_1dep(bh: Blackhole): Unit = {
    val res = mkRes1()
    val os  = Scope.global.open()
    bh.consume(res.make(os.scope))
    bh.consume(os.close())
  }

  @Benchmark
  def wireup_5deps(bh: Blackhole): Unit = {
    val res = mkRes5()
    val os  = Scope.global.open()
    bh.consume(res.make(os.scope))
    bh.consume(os.close())
  }

  @Benchmark
  def wireup_10deps(bh: Blackhole): Unit = {
    val res = mkRes10()
    val os  = Scope.global.open()
    bh.consume(res.make(os.scope))
    bh.consume(os.close())
  }

  @Benchmark
  def wireup_diamond(bh: Blackhole): Unit = {
    val res = mkResDiamond()
    val os  = Scope.global.open()
    bh.consume(res.make(os.scope))
    bh.consume(os.close())
  }
}
