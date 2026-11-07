package processbinarydata

/**
 * How to Process Binary Data Without Boxing — Map a File Instead of Copying It
 *
 * Reads the sample file through `Pump.samples`, which opens it with
 * `Stream.fromAcquireRelease`, maps it into memory with `FileChannel#map` and
 * reads the mapping as `Double` values. The stream closes the channel when the
 * run ends.
 *
 * Run with: sbt "runMain processbinarydata.Step3MapFile"
 */
object Step3MapFile {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      println(s"samples:     ${Pump.samples(dump.samples).runFold(0L)((n, _) => n + 1L)}")
      println(s"first three: ${Pump.samples(dump.samples).take(3).runCollect}")
    } finally SensorFiles.delete(dump)
  }
}
