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
import zio.blocks.context.Context

// Domain types for benchmarking
final case class Svc1(id: Int)
final case class Svc2(id: Int)
final case class Svc3(id: Int)
final case class Svc4(id: Int)
final case class Svc5(id: Int)
final case class Svc6(id: Int)
final case class Svc7(id: Int)
final case class Svc8(id: Int)
final case class Svc9(id: Int)
final case class Svc10(id: Int)

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
class ContextBenchmark {

  // Pre-built contexts for get benchmarks
  private val ctx1  = Context(Svc1(1))
  private val ctx5  = Context(Svc1(1), Svc2(2), Svc3(3), Svc4(4), Svc5(5))
  private val ctx10 = Context(
    Svc1(1),
    Svc2(2),
    Svc3(3),
    Svc4(4),
    Svc5(5),
    Svc6(6),
    Svc7(7),
    Svc8(8),
    Svc9(9),
    Svc10(10)
  )

  // Pre-created values
  private val s1  = Svc1(1)
  private val s2  = Svc2(2)
  private val s3  = Svc3(3)
  private val s4  = Svc4(4)
  private val s5  = Svc5(5)
  private val s6  = Svc6(6)
  private val s7  = Svc7(7)
  private val s8  = Svc8(8)
  private val s9  = Svc9(9)
  private val s10 = Svc10(10)

  // ---------------------------------------------------------------------------
  // Construction: Context.apply (multi-arg factory)
  // ---------------------------------------------------------------------------

  @Benchmark
  def construct_1(bh: Blackhole): Unit =
    bh.consume(Context(s1))

  @Benchmark
  def construct_5(bh: Blackhole): Unit =
    bh.consume(Context(s1, s2, s3, s4, s5))

  @Benchmark
  def construct_10(bh: Blackhole): Unit =
    bh.consume(Context(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10))

  // ---------------------------------------------------------------------------
  // Construction: chained .add calls
  // ---------------------------------------------------------------------------

  @Benchmark
  def add_chain_1(bh: Blackhole): Unit =
    bh.consume(Context.empty.add(s1))

  @Benchmark
  def add_chain_5(bh: Blackhole): Unit =
    bh.consume(Context.empty.add(s1).add(s2).add(s3).add(s4).add(s5))

  @Benchmark
  def add_chain_10(bh: Blackhole): Unit =
    bh.consume(
      Context.empty
        .add(s1)
        .add(s2)
        .add(s3)
        .add(s4)
        .add(s5)
        .add(s6)
        .add(s7)
        .add(s8)
        .add(s9)
        .add(s10)
    )

  // ---------------------------------------------------------------------------
  // Get: exact type lookup (first call, uncached)
  // ---------------------------------------------------------------------------

  @Benchmark
  def get_exact_from1(bh: Blackhole): Unit =
    bh.consume(Context(s1).get[Svc1])

  @Benchmark
  def get_exact_from5(bh: Blackhole): Unit =
    bh.consume(Context(s1, s2, s3, s4, s5).get[Svc1])

  @Benchmark
  def get_exact_from10(bh: Blackhole): Unit =
    bh.consume(Context(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10).get[Svc1])

  // ---------------------------------------------------------------------------
  // Get: exact type lookup (cached - reuse pre-built context)
  // ---------------------------------------------------------------------------

  @Benchmark
  def get_cached_from1(bh: Blackhole): Unit =
    bh.consume(ctx1.get[Svc1])

  @Benchmark
  def get_cached_from5(bh: Blackhole): Unit =
    bh.consume(ctx5.get[Svc3])

  @Benchmark
  def get_cached_from10(bh: Blackhole): Unit =
    bh.consume(ctx10.get[Svc5])

  // ---------------------------------------------------------------------------
  // Get all: retrieve every value from a context (uncached, fresh context)
  // ---------------------------------------------------------------------------

  @Benchmark
  def get_all_5_uncached(bh: Blackhole): Unit = {
    val c = Context(s1, s2, s3, s4, s5)
    bh.consume(c.get[Svc1])
    bh.consume(c.get[Svc2])
    bh.consume(c.get[Svc3])
    bh.consume(c.get[Svc4])
    bh.consume(c.get[Svc5])
  }

  @Benchmark
  def get_all_10_uncached(bh: Blackhole): Unit = {
    val c = Context(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10)
    bh.consume(c.get[Svc1])
    bh.consume(c.get[Svc2])
    bh.consume(c.get[Svc3])
    bh.consume(c.get[Svc4])
    bh.consume(c.get[Svc5])
    bh.consume(c.get[Svc6])
    bh.consume(c.get[Svc7])
    bh.consume(c.get[Svc8])
    bh.consume(c.get[Svc9])
    bh.consume(c.get[Svc10])
  }

  // ---------------------------------------------------------------------------
  // Full DI pattern: build context + get all (simulates Wire.make path)
  // ---------------------------------------------------------------------------

  @Benchmark
  def di_pattern_5(bh: Blackhole): Unit = {
    val c = Context.empty.add(s1).add(s2).add(s3).add(s4).add(s5)
    bh.consume(c.get[Svc1])
    bh.consume(c.get[Svc2])
    bh.consume(c.get[Svc3])
    bh.consume(c.get[Svc4])
    bh.consume(c.get[Svc5])
  }

  @Benchmark
  def di_pattern_10(bh: Blackhole): Unit = {
    val c = Context.empty
      .add(s1)
      .add(s2)
      .add(s3)
      .add(s4)
      .add(s5)
      .add(s6)
      .add(s7)
      .add(s8)
      .add(s9)
      .add(s10)
    bh.consume(c.get[Svc1])
    bh.consume(c.get[Svc2])
    bh.consume(c.get[Svc3])
    bh.consume(c.get[Svc4])
    bh.consume(c.get[Svc5])
    bh.consume(c.get[Svc6])
    bh.consume(c.get[Svc7])
    bh.consume(c.get[Svc8])
    bh.consume(c.get[Svc9])
    bh.consume(c.get[Svc10])
  }
}
