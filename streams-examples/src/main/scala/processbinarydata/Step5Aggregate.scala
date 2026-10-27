package processbinarydata

import zio.blocks.streams._

/**
 * How to Process Binary Data Without Boxing — Aggregate With Built-in Sinks
 *
 * Counts overrange samples and computes the recording's time span with
 * `Stream#runFold` and `Sink.foldLeft` on a `Long` accumulator, then measures
 * them against `Stream#count` and `Sink.sumLong`, which box every element in
 * 0.0.56.
 *
 * Run with: sbt "runMain processbinarydata.Step5Aggregate"
 */
object Step5Aggregate {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      val overrange = Pump.samples(dump.samples).filter(_ == SensorFiles.Overrange).runFold(0L)((n, _) => n + 1L)
      println(s"overrange samples: $overrange")

      val first =
        Pump.timestamps(dump.timestamps).run(Sink.foldLeft(Long.MaxValue)((lo: Long, t: Long) => math.min(lo, t)))
      val last =
        Pump.timestamps(dump.timestamps).run(Sink.foldLeft(Long.MinValue)((hi: Long, t: Long) => math.max(hi, t)))
      println(s"time span (ms):    ${for { a <- first; b <- last } yield b - a}")

      val n            = SensorFiles.SampleCount
      val runFoldCount = Allocation.bytesPerElement(n)(Pump.timestamps(dump.timestamps).runFold(0L)((c, _) => c + 1L))
      val streamCount  = Allocation.bytesPerElement(n)(Pump.timestamps(dump.timestamps).count)
      val sumLong      = Allocation.bytesPerElement(n)(Pump.timestamps(dump.timestamps).run(Sink.sumLong))
      println(f"runFold(0L) count: $runFoldCount%.2f bytes per element")
      println(f"Stream#count:      $streamCount%.2f bytes per element")
      println(f"Sink.sumLong:      $sumLong%.2f bytes per element")
    } finally SensorFiles.delete(dump)
  }
}
