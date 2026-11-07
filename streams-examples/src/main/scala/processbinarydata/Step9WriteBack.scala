package processbinarydata

import zio.blocks.streams._

import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.{CREATE, READ, TRUNCATE_EXISTING, WRITE}
import scala.util.Using

/**
 * How to Process Binary Data Without Boxing — Write Values Back Out
 *
 * Writes the calibrated samples (m/s², big-endian) into a memory-mapped output
 * file with `NioSinks.fromByteBufferDouble`, checks that the overrange markers
 * survive the round trip, then records pushed samples with `Recorder`, which
 * writes through `NioWriters`.
 *
 * Run with: sbt "runMain processbinarydata.Step9WriteBack"
 */
object Step9WriteBack {
  def main(args: Array[String]): Unit = {
    val dump       = SensorFiles.create()
    val calibrated = dump.dir.resolve("calibrated.bin")
    try {
      val written =
        Using.resource(FileChannel.open(calibrated, CREATE, TRUNCATE_EXISTING, READ, WRITE)) { ch =>
          val target = ch.map(FileChannel.MapMode.READ_WRITE, 0, SensorFiles.SampleCount * 8L)
          val result = Pump
            .samples(dump.samples)
            .map(g => if (g == SensorFiles.Overrange) g else g * Pump.StandardGravity)
            .run(NioSinks.fromByteBufferDouble(target))
          target.force()
          result
        }
      println(s"written:   $written")

      val markers = Stream
        .fromAcquireRelease(FileChannel.open(calibrated)) { ch =>
          NioStreams.fromByteBufferDouble(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
        }
        .filter(_ == Double.MaxValue)
        .runFold(0L)((n, _) => n + 1L)
      println(s"overrange: $markers")

      val times   = dump.dir.resolve("recorded-times.bin")
      val samples = dump.dir.resolve("recorded-samples.bin")
      Using.resources(
        FileChannel.open(times, CREATE, TRUNCATE_EXISTING, WRITE),
        FileChannel.open(samples, CREATE, TRUNCATE_EXISTING, WRITE)
      ) { (timesCh, samplesCh) =>
        val recorder = new Recorder(timesCh, samplesCh, blockSize = 4096)
        var i        = 0
        while (i < 10000) { recorder.onSample(SensorFiles.StartMillis + i, SensorFiles.sample(i)); i += 1 }
        recorder.flush()
      }
      println(s"recorded:  ${Pump.samples(samples).runFold(0L)((n, _) => n + 1L)} samples")
    } finally SensorFiles.delete(dump)
  }
}
