package zio.blocks.chunk

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkBenchmarks {
  @Param(Array("16", "1024", "16384"))
  var size: Int = _

  private var ints: Chunk[Int]                 = _
  private var chunkOfChunks: Chunk[Chunk[Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    ints = Chunk.fromArray(Array.tabulate(size)(identity))
    chunkOfChunks = Chunk.fill(8)(ints)
  }

  @Benchmark
  def map(): Chunk[Int] =
    ints.map(_ + 1)

  @Benchmark
  def flatMap(): Chunk[Int] =
    ints.flatMap(i => Chunk(i, i + 1))

  @Benchmark
  def concat(): Chunk[Int] =
    chunkOfChunks.foldLeft(Chunk.empty[Int])(_ ++ _)

  @Benchmark
  def takeDrop(): Chunk[Int] =
    ints.drop(size / 4).take(size / 2)

  @Benchmark
  def filter(): Chunk[Int] =
    ints.filter(_ % 2 == 0)
}
