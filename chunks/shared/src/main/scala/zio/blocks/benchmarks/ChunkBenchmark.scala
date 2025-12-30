package zio.blocks.benchmarks

import org.openjdk.jmh.annotations._
import zio.blocks.chunk.Chunk
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkBenchmark {

  @Param(Array("100", "1000"))
  var size: Int = 0

  var chunk: Chunk[Int] = _

  @Setup
  def setup(): Unit = {
    chunk = Chunk.fromArray(Array.fill(size)(1))
  }

  @Benchmark
  def mapBenchmark(): Chunk[Int] = {
    chunk.map(_ + 1)
  }

  @Benchmark
  def filterBenchmark(): Chunk[Int] = {
    chunk.filter(_ % 2 == 0)
  }
}