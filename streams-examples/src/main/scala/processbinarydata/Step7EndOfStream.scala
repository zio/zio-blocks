package processbinarydata

import zio.blocks.streams._

/**
 * How to Process Binary Data Without Boxing — End the Loop Without Losing
 * Values
 *
 * Counts samples with two custom sinks: one that detects the end with
 * `readDouble(Double.MaxValue)` and stops at the first overrange reading, and
 * one that detects it with the count from a one-element `readDoubles`.
 *
 * Run with: sbt "runMain processbinarydata.Step7EndOfStream"
 */
object Step7EndOfStream {

  val countWithSentinel: Sink[Nothing, Double, Long] =
    Sink.create[Nothing, Double, Long] { reader =>
      var n = 0L
      var g = reader.readDouble(Double.MaxValue)
      while (g != Double.MaxValue) { n += 1; g = reader.readDouble(Double.MaxValue) }
      n
    }

  val countWithCount: Sink[Nothing, Double, Long] =
    Sink.create[Nothing, Double, Long] { reader =>
      val one = new Array[Double](1)
      var n   = 0L
      while (reader.readDoubles(one, 0, 1) > 0) n += 1
      n
    }

  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      println(s"readDouble(Double.MaxValue): ${Pump.samples(dump.samples).run(countWithSentinel)}")
      println(s"readDoubles(one, 0, 1):      ${Pump.samples(dump.samples).run(countWithCount)}")
    } finally SensorFiles.delete(dump)
  }
}
