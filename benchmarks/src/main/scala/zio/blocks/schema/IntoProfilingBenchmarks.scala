package zio.blocks.schema

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

/**
 * Advanced profiling benchmarks for Into conversions.
 *
 * These benchmarks focus on:
 *   - Memory allocation patterns
 *   - Hot path identification
 *   - Performance regression detection
 *
 * Run with profiling: sbt "project benchmarks" "Jmh/run -prof gc -prof perf"
 */
@BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 3,
  jvmArgs = Array(
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+PrintCompilation",
    "-XX:+LogCompilation"
  )
)
class IntoProfilingBenchmarks {

  case class SimpleV1(value: Int)
  case class SimpleV2(value: Long)

  case class MediumV1(
    f1: Int,
    f2: String,
    f3: Boolean,
    f4: Long,
    f5: Double
  )
  case class MediumV2(
    f1: Long,
    f2: String,
    f3: Boolean,
    f4: Long,
    f5: Double
  )

  val simpleV1 = SimpleV1(42)
  val mediumV1 = MediumV1(1, "test", true, 2L, 3.0)

  val simpleInto = Into.derived[SimpleV1, SimpleV2]
  val mediumInto = Into.derived[MediumV1, MediumV2]

  @Benchmark
  @OperationsPerInvocation(1000)
  def simpleConversionThroughput(): Unit = {
    var i = 0
    while (i < 1000) {
      simpleInto.intoOrThrow(simpleV1)
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  def mediumConversionThroughput(): Unit = {
    var i = 0
    while (i < 1000) {
      mediumInto.intoOrThrow(mediumV1)
      i += 1
    }
  }

  @Benchmark
  def collectionConversionMemory(): Vector[Long] = {
    val into = Into.derived[List[Int], Vector[Long]]
    val list = (1 to 1000).toList
    into.intoOrThrow(list)
  }

  @Benchmark
  def nestedConversionMemory(): Any = {
    case class OuterV1(inner: InnerV1)
    case class InnerV1(value: Int)

    case class OuterV2(inner: InnerV2)
    case class InnerV2(value: Long)

    val into  = Into.derived[OuterV1, OuterV2]
    val outer = OuterV1(InnerV1(42))
    into.intoOrThrow(outer)
  }

  @Benchmark
  def errorPathPerformance(): Either[SchemaError, SimpleV2] = {
    // Test error path (should be fast - early return)
    val into   = Into.derived[Long, Int]
    val result = into.into(Long.MaxValue)
    // This should fail fast
    result.asInstanceOf[Either[SchemaError, SimpleV2]]
  }

  @Benchmark
  def successPathPerformance(): SimpleV2 =
    // Test success path (should be optimized)
    simpleInto.intoOrThrow(simpleV1)

  @Benchmark
  def largeBatchConversion(): List[SimpleV2] = {
    val into  = Into.derived[SimpleV1, SimpleV2]
    val batch = (1 to 10000).map(i => SimpleV1(i)).toList
    batch.map(p => into.intoOrThrow(p))
  }

  @Benchmark
  def mapConversionMemory(): Map[String, Long] = {
    val into = Into.derived[Map[String, Int], Map[String, Long]]
    val map  = (1 to 1000).map(i => s"key$i" -> i).toMap
    into.intoOrThrow(map)
  }

  @Benchmark
  def optionConversionMemory(): Option[Long] = {
    val into = Into.derived[Option[Int], Option[Long]]
    into.intoOrThrow(Some(42))
  }

  @Benchmark
  def eitherConversionMemory(): Either[Long, Long] = {
    val into = Into.derived[Either[Int, Int], Either[Long, Long]]
    into.intoOrThrow(Right(42))
  }
}
