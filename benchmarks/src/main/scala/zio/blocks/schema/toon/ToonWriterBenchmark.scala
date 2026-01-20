package zio.blocks.schema.toon

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ToonWriterBenchmark {

  @Param(Array("100", "1000", "10000"))
  var size: Int = 0

  var config: WriterConfig = null

  @Setup
  def setup(): Unit =
    config = WriterConfig
    // Pre-allocate a large writer to avoid resizing overhead during the benchmark loop itself if possible,
    // though ToonWriter resets likely resize internal buffer if needed.
    // We use a fresh writer for setup, but the benchmark method will use the object pool or fresh.
    // The benchmark tests the `ToonWriter.apply(config)` recycling validation too.

  @Benchmark
  def writeIntegers(): Array[Byte] = {
    val w = ToonWriter(config)
    w.writeArrayHeader("numbers", size)
    var i = 0
    while (i < size) {
      w.writeInt(i)
      i += 1
    }
    w.toByteArray
  }

  @Benchmark
  def writeRecord(): Array[Byte] = {
    val w = ToonWriter(config)
    var i = 0
    while (i < size) {
      w.writeKey("field_" + i)
      w.writeInt(i)
      w.newLine()
      i += 1
    }
    w.toByteArray
  }
}
