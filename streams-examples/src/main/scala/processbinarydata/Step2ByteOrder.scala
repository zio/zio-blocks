package processbinarydata

import zio.blocks.streams._

import java.nio.file.Files
import java.nio.{ByteBuffer, ByteOrder}

/**
 * How to Process Binary Data Without Boxing — Match the File's Byte Order
 *
 * Reads the little-endian sample file twice: once with the `ByteBuffer` default
 * (big-endian), which turns every sample into garbage, and once with
 * `ByteOrder.LITTLE_ENDIAN`. Also shows that `ByteBuffer#duplicate` resets the
 * byte order.
 *
 * Run with: sbt "runMain processbinarydata.Step2ByteOrder"
 */
object Step2ByteOrder {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      val bytes = Files.readAllBytes(dump.samples)

      val wrongOrder = ByteBuffer.wrap(bytes)
      println(s"big-endian:    ${NioStreams.fromByteBufferDouble(wrongOrder).take(3).runCollect}")

      val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      println(
        s"little-endian: ${NioStreams.fromByteBufferDouble(samples.duplicate().order(ByteOrder.LITTLE_ENDIAN)).take(3).runCollect}"
      )

      // duplicate() and slice() return a big-endian buffer, whatever the original's order.
      println(s"duplicate():   ${samples.duplicate().order()}")
    } finally SensorFiles.delete(dump)
  }
}
