package processbinarydata

import zio.blocks.streams._

import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.{ByteBuffer, ByteOrder}

/**
 * How to Process Binary Data Without Boxing — Shared Pipeline Pieces
 *
 * The sources and the sink that several examples share: the sample and
 * timestamp files mapped into memory, samples decoded from an `InputStream`,
 * and a custom `Sink` that computes vibration statistics in one pass.
 */
object Pump {

  val StandardGravity: Double = 9.80665 // m/s² per g

  final case class VibrationStats(samples: Long, overrange: Long, min: Double, max: Double, rms: Double)

  def samples(file: Path): Stream[Nothing, Double] =
    Stream.fromAcquireRelease(FileChannel.open(file)) { ch =>
      val mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()).order(ByteOrder.LITTLE_ENDIAN)
      NioStreams.fromByteBufferDouble(mapped)
    }

  def timestamps(file: Path): Stream[Nothing, Long] =
    Stream.fromAcquireRelease(FileChannel.open(file)) { ch =>
      NioStreams.fromByteBufferLong(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
    }

  // Refills one block per step; flatMap reads the block to the end before the next refill.
  def samplesFrom(in: InputStream, order: ByteOrder): Stream[Nothing, Double] = {
    val block = ByteBuffer.allocate(64 * 1024).order(order)
    Stream
      .unfold(in) { in =>
        val n = in.readNBytes(block.array(), 0, block.capacity())
        if (n <= 0) None
        else {
          block.clear()
          block.limit(n - n % 8)
          Some((block, in))
        }
      }
      .flatMap(b => NioStreams.fromByteBufferDouble(b))
  }

  val vibrationStats: Sink[Nothing, Double, VibrationStats] =
    Sink.create[Nothing, Double, VibrationStats] { reader =>
      val one       = new Array[Double](1)
      var samples   = 0L
      var overrange = 0L
      var min       = Double.PositiveInfinity
      var max       = Double.NegativeInfinity
      var sumSq     = 0.0
      while (reader.readDoubles(one, 0, 1) > 0) {
        val g = one(0)
        if (g == SensorFiles.Overrange) overrange += 1
        else {
          samples += 1
          if (g < min) min = g
          if (g > max) max = g
          sumSq += g * g
        }
      }
      VibrationStats(samples, overrange, min, max, math.sqrt(sumSq / samples))
    }
}
