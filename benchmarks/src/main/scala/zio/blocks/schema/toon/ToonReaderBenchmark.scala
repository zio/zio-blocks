package zio.blocks.schema.toon

import org.openjdk.jmh.annotations._
import java.nio.charset.StandardCharsets.UTF_8

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ToonReaderBenchmark {

  @Param(Array("100", "1000", "10000", "100000"))
  var size: Int = 0

  var toonContent: String        = null
  var toonBytes: Array[Byte]     = null
  var readerConfig: ReaderConfig = null

  var recordBytes: Array[Byte] = null

  @Setup
  def setup(): Unit = {
    val writer = ToonWriter(WriterConfig)
    writer.writeArrayHeader("numbers", size)
    writer.newLine()
    var i = 0
    while (i < size) {
      writer.writeInt(i)
      writer.newLine()
      i += 1
    }
    toonBytes = writer.toByteArray
    toonContent = new String(toonBytes, UTF_8)
    readerConfig = ReaderConfig

    val rw = ToonWriter(WriterConfig)
    var j  = 0
    while (j < 100) {
      rw.writeKey("field_" + j)
      rw.writeInt(j)
      rw.newLine()
      j += 1
    }
    recordBytes = rw.toByteArray
  }

  @Benchmark
  def parseIntegers(): Unit = {
    val reader = ToonReader(readerConfig)
    reader.reset(toonBytes, 0, toonBytes.length)

    // Parse "numbers[100]:"
    val header = reader.parseArrayHeader()
    if (header.key == "numbers") {
      var i = 0
      while (i < size) {
        reader.readInt()
        i += 1
      }
    }
    reader.endUse()
  }

  @Benchmark
  def parseRecord(): Unit = {
    val reader = ToonReader(readerConfig)
    reader.reset(recordBytes, 0, recordBytes.length)
    var i = 0
    while (i < 100) {
      reader.readKey()
      reader.readInt()
      i += 1
    }
    reader.endUse()
  }
}
