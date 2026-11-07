package processbinarydata

import zio.blocks.streams._

import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * How to Process Binary Data Without Boxing — Decode Values From an InputStream
 *
 * Reads the gzip copy of the samples through `GZIPInputStream`, decodes them
 * into a `Double` stream with `Pump.samplesFrom`, and runs the same statistics
 * sink as the mapped file.
 *
 * Run with: sbt "runMain processbinarydata.Step8DecodeInputStream"
 */
object Step8DecodeInputStream {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      val fromGzip: Stream[Nothing, Double] =
        Stream.fromAcquireRelease(new GZIPInputStream(Files.newInputStream(dump.samplesGz))) { in =>
          Pump.samplesFrom(in, ByteOrder.LITTLE_ENDIAN)
        }
      println(s"gzip:   ${fromGzip.run(Pump.vibrationStats)}")
      println(s"mapped: ${Pump.samples(dump.samples).run(Pump.vibrationStats)}")
    } finally SensorFiles.delete(dump)
  }
}
