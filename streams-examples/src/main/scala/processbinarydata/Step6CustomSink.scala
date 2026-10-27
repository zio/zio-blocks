package processbinarydata

/**
 * How to Process Binary Data Without Boxing — Write a Custom Primitive Sink
 *
 * Runs the mapped sample file into `Pump.vibrationStats`, a `Sink.create` sink
 * that keeps count, minimum, maximum and sum of squares in primitive variables
 * and reads with a one-element `readDoubles`.
 *
 * Run with: sbt "runMain processbinarydata.Step6CustomSink"
 */
object Step6CustomSink {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      println(Pump.samples(dump.samples).run(Pump.vibrationStats))
      val bytes =
        Allocation.bytesPerElement(SensorFiles.SampleCount)(Pump.samples(dump.samples).run(Pump.vibrationStats))
      println(f"$bytes%.2f bytes per element")
    } finally SensorFiles.delete(dump)
  }
}
