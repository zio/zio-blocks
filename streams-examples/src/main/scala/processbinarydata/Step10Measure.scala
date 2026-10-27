package processbinarydata

import zio.blocks.streams._

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.{CREATE, READ, TRUNCATE_EXISTING, WRITE}
import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.GZIPInputStream
import scala.util.Using

/**
 * How to Process Binary Data Without Boxing — Measure Bytes per Element
 *
 * Measures the bytes each step of the guide allocates per sample with
 * `Allocation.bytesPerElement`, and two steps that leave the primitive lane or
 * hit a known bug in 0.0.56 for comparison.
 *
 * Run with: sbt "runMain processbinarydata.Step10Measure"
 */
object Step10Measure {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    val n    = SensorFiles.SampleCount
    try {
      val bytes = ByteBuffer.wrap(Files.readAllBytes(dump.samples)).order(ByteOrder.LITTLE_ENDIAN)
      def heap  = NioStreams.fromByteBufferDouble(bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN))
      val out   = ByteBuffer.allocate(n * 8)

      def report(step: String)(body: => Any): Unit =
        println(f"$step%-44s ${Allocation.bytesPerElement(n)(body)}%7.2f")

      println(f"${"step"}%-44s ${"bytes per element"}")
      report("ByteBuffer, count with runFold(0L)")(heap.runFold(0L)((c, _) => c + 1L))
      report("map + filter + scan, count with runFold(0L)") {
        heap
          .filter(_ != SensorFiles.Overrange)
          .map(_ * Pump.StandardGravity)
          .scan(0.0)(math.max)
          .runFold(0L)((c, _) => c + 1L)
      }
      report("mapped file into vibrationStats")(Pump.samples(dump.samples).run(Pump.vibrationStats))
      report("gzip InputStream into vibrationStats") {
        Stream
          .fromAcquireRelease(new GZIPInputStream(Files.newInputStream(dump.samplesGz))) { in =>
            Pump.samplesFrom(in, ByteOrder.LITTLE_ENDIAN)
          }
          .run(Pump.vibrationStats)
      }
      report("map into NioSinks.fromByteBufferDouble") {
        out.clear()
        heap.map(_ * Pump.StandardGravity).run(NioSinks.fromByteBufferDouble(out))
      }
      val calibrated = dump.dir.resolve("calibrated.bin")
      Using.resource(FileChannel.open(calibrated, CREATE, TRUNCATE_EXISTING, READ, WRITE)) { ch =>
        val target = ch.map(FileChannel.MapMode.READ_WRITE, 0, n * 8L)
        report("Recorder (NioWriters), blocks of 4096") {
          val recorder = new Recorder(ch, ch, blockSize = 4096)
          var i        = 0
          while (i < n) { recorder.onSample(SensorFiles.StartMillis + i, 0.25); i += 1 }
          recorder.flush()
          ch.position(0)
        }
        report("mapped file into mapped file") {
          target.clear()
          Pump.samples(dump.samples).run(NioSinks.fromByteBufferDouble(target))
        }
      }
      report("map to a tuple (leaves the lane)")(heap.map(g => (g, g)).runFold(0L)((c, _) => c + 1L))
      report("Sink.sumDouble (a known bug in 0.0.56)")(heap.run(Sink.sumDouble))
      report("runFold(0.0) (a known bug in 0.0.56)")(heap.runFold(0.0)(_ + _))
    } finally SensorFiles.delete(dump)
  }
}
