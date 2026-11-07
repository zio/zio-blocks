package processbinarydata

import zio.blocks.streams._

import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.{CREATE, READ, TRUNCATE_EXISTING, WRITE}
import java.util.zip.GZIPInputStream
import scala.util.Using

/**
 * How to Process Binary Data Without Boxing
 *
 * Pump vibration report: maps a sensor's timestamp and sample files into
 * memory, computes vibration statistics with a custom primitive sink, checks
 * the gzip copy decodes to the same statistics, writes the calibrated samples
 * to a memory-mapped output file, and prints the bytes each run allocates per
 * sample.
 *
 * Run with: sbt "runMain processbinarydata.CompleteExample"
 */
object CompleteExample {

  def main(args: Array[String]): Unit = {
    val dump       = SensorFiles.create()
    val calibrated = dump.dir.resolve("calibrated.bin")
    val n          = SensorFiles.SampleCount
    try {
      // 1. Time span from the big-endian timestamps.
      val first =
        Pump.timestamps(dump.timestamps).run(Sink.foldLeft(Long.MaxValue)((lo: Long, t: Long) => math.min(lo, t)))
      val last =
        Pump.timestamps(dump.timestamps).run(Sink.foldLeft(Long.MinValue)((hi: Long, t: Long) => math.max(hi, t)))
      for { a <- first; b <- last } println(s"recording: ${n} samples over ${(b - a + 1) / 1000.0} s")

      // 2. Vibration statistics from the little-endian samples, mapped and gzipped.
      val fromFile = Pump.samples(dump.samples).run(Pump.vibrationStats)
      val fromGzip = Stream
        .fromAcquireRelease(new GZIPInputStream(Files.newInputStream(dump.samplesGz))) { in =>
          Pump.samplesFrom(in, ByteOrder.LITTLE_ENDIAN)
        }
        .run(Pump.vibrationStats)
      fromFile.foreach { s =>
        println(f"samples:   ${s.samples} (${s.overrange} overrange)")
        println(f"range (g): ${s.min}%.4f to ${s.max}%.4f, rms ${s.rms}%.4f")
      }
      println(s"gzip copy matches: ${fromFile == fromGzip}")

      // 3. Calibrated samples (m/s², big-endian) into a memory-mapped output file.
      def calibrate(target: java.nio.ByteBuffer) =
        Pump
          .samples(dump.samples)
          .map(g => if (g == SensorFiles.Overrange) g else g * Pump.StandardGravity)
          .run(NioSinks.fromByteBufferDouble(target))

      Using.resource(FileChannel.open(calibrated, CREATE, TRUNCATE_EXISTING, READ, WRITE)) { ch =>
        val target = ch.map(FileChannel.MapMode.READ_WRITE, 0, n * 8L)
        calibrate(target)
        target.force()

        // 4. What each run allocates per sample, once the JIT has compiled it.
        val statsBytes     = Allocation.bytesPerElement(n)(Pump.samples(dump.samples).run(Pump.vibrationStats))
        val calibrateBytes = Allocation.bytesPerElement(n) { target.clear(); calibrate(target) }
        println(f"stats:     $statsBytes%.2f bytes per sample")
        println(f"calibrate: $calibrateBytes%.2f bytes per sample")
      }
      println(s"calibrated file: ${Files.size(calibrated)} bytes")
    } finally SensorFiles.delete(dump)
  }
}
