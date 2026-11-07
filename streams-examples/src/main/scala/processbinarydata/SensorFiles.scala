package processbinarydata

import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.nio.file.{Files, Path}
import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.GZIPOutputStream
import scala.util.Using

/**
 * How to Process Binary Data Without Boxing — Sample Sensor Files
 *
 * Writes the files a vibration sensor on a pump produces: one `Long` timestamp
 * per sample (big-endian, milliseconds since the epoch), one `Double`
 * acceleration per sample (little-endian, in g, with `Double.MaxValue` marking
 * an overrange reading), and a gzip copy of the samples. Every example writes
 * its own copy under a temporary directory and deletes it afterwards.
 */
object SensorFiles {

  val SampleCount: Int = 100000

  // 2026-01-01T00:00:00Z, one sample per millisecond (1 kHz).
  val StartMillis: Long = 1767225600000L

  // The sensor writes Double.MaxValue when the acceleration is out of range.
  val Overrange: Double = Double.MaxValue

  final case class SensorDump(dir: Path, timestamps: Path, samples: Path, samplesGz: Path)

  def sample(i: Int): Double =
    if (i > 0 && i % 25000 == 0) Overrange
    else 0.2 * math.sin(2 * math.Pi * 50 * i / 1000.0) + 0.05 * math.sin(2 * math.Pi * 120 * i / 1000.0)

  def create(): SensorDump = {
    val dir        = Files.createTempDirectory("pump-sensor")
    val timestamps = dir.resolve("timestamps.bin")
    val samples    = dir.resolve("samples.bin")
    val samplesGz  = dir.resolve("samples.bin.gz")

    val times = ByteBuffer.allocate(SampleCount * 8) // big-endian, the ByteBuffer default
    val accel = ByteBuffer.allocate(SampleCount * 8).order(ByteOrder.LITTLE_ENDIAN)
    var i     = 0
    while (i < SampleCount) {
      times.putLong(StartMillis + i)
      accel.putDouble(sample(i))
      i += 1
    }
    write(timestamps, times)
    write(samples, accel)
    Using.resource(new GZIPOutputStream(Files.newOutputStream(samplesGz)): OutputStream) { out =>
      out.write(Files.readAllBytes(samples))
    }
    SensorDump(dir, timestamps, samples, samplesGz)
  }

  def delete(files: SensorDump): Unit = {
    val it = Files.list(files.dir)
    try it.forEach(p => Files.delete(p))
    finally it.close()
    Files.delete(files.dir)
  }

  private def write(path: Path, buf: ByteBuffer): Unit = {
    buf.flip()
    Using.resource(FileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE)) { ch =>
      while (buf.hasRemaining) ch.write(buf)
    }
  }
}
