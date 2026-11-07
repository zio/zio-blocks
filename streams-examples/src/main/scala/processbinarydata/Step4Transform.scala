package processbinarydata

import zio.blocks.streams._

/**
 * How to Process Binary Data Without Boxing — Transform on the Primitive Lane
 *
 * Converts samples from g to m/s² with `map`, drops overrange readings with
 * `filter`, tracks the running peak with `scan` and keeps the first second with
 * `take`. Every step keeps the `Double` lane.
 *
 * Run with: sbt "runMain processbinarydata.Step4Transform"
 */
object Step4Transform {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      val firstSecond: Stream[Nothing, Double] =
        Pump
          .samples(dump.samples)
          .take(1000)
          .filter(_ != SensorFiles.Overrange)
          .map(_ * Pump.StandardGravity)

      val peak = firstSecond.scan(0.0)((peak, a) => math.max(peak, math.abs(a)))

      println(s"first three (m/s²): ${firstSecond.take(3).runCollect}")
      println(s"peak (m/s²):        ${peak.last}")
    } finally SensorFiles.delete(dump)
  }
}
