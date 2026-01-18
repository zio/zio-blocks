package zio.blocks.chunk

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ScalarSpecializedBenchmark extends BaseBenchmark {
  @Param(Array("1000", "10000"))
  var size: Int = uninitialized

  var byteArray: Array[Byte] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    byteArray = Array.fill(size)(65.toByte) // 'A'

  @Benchmark
  def checksumBaseline(): Long = {
    var sum = 0L
    var i   = 0
    while (i < size) {
      sum += byteArray(i) & 0xff
      i += 1
    }
    sum
  }

  @Benchmark
  def checksumOptimized(): Long =
    Chunk.byteChecksum(byteArray, 0, size)

  @Benchmark
  def toUpperCaseOptimized(): Chunk[Byte] =
    // Current Chunk.map is the "Target" optimized version
    Chunk.fromArray(byteArray).map(b => if (b >= 'a' && b <= 'z') (b - 32).toByte else b)

}
