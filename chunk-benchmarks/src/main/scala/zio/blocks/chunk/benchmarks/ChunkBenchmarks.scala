package zio.blocks.chunk.benchmarks

import org.openjdk.jmh.annotations._
import zio.blocks.chunk.Chunk
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
class ChunkBenchmarks {

  @Param(Array("1000", "10000"))
  var size: Int = _

  var chunk: Chunk[Int]   = _
  var vector: Vector[Int] = _
  var list: List[Int]     = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    chunk = Chunk.fromArray(Array.tabulate(size)(identity))
    vector = Vector.tabulate(size)(identity)
    list = List.tabulate(size)(identity)
  }

  // Append benchmarks
  @Benchmark
  def chunkAppend(): Chunk[Int] = {
    var i = 0
    var c = Chunk(1)
    while (i < size) { c = c :+ i; i += 1 }
    c
  }

  @Benchmark
  def vectorAppend(): Vector[Int] = {
    var i = 0
    var v = Vector(1)
    while (i < size) { v = v :+ i; i += 1 }
    v
  }

  // Concat benchmarks
  @Benchmark
  def chunkConcat(): Chunk[Int] = chunk ++ chunk

  @Benchmark
  def vectorConcat(): Vector[Int] = vector ++ vector

  @Benchmark
  def chunkConcatMany(): Chunk[Int] = {
    var result = Chunk.empty[Int]
    var i = 0
    while (i < 100) { result = result ++ chunk; i += 1 }
    result
  }

  // Map benchmarks
  @Benchmark
  def chunkMap(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def vectorMap(): Vector[Int] = vector.map(_ * 2)

  @Benchmark
  def chunkFlatMap(): Chunk[Int] = chunk.flatMap(x => Chunk(x, x + 1))

  @Benchmark
  def vectorFlatMap(): Vector[Int] = vector.flatMap(x => Vector(x, x + 1))

  // Fold benchmarks
  @Benchmark
  def chunkFoldLeft(): Int = chunk.foldLeft(0)(_ + _)

  @Benchmark
  def vectorFoldLeft(): Int = vector.foldLeft(0)(_ + _)

  @Benchmark
  def chunkFoldRight(): Int = chunk.foldRight(0)(_ + _)

  @Benchmark
  def vectorFoldRight(): Int = vector.foldRight(0)(_ + _)
}
