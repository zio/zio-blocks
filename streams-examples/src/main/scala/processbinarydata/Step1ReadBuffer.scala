package processbinarydata

import zio.blocks.streams._

import java.nio.ByteBuffer
import java.nio.file.Files

/**
 * How to Process Binary Data Without Boxing — Read Values From a ByteBuffer
 *
 * Wraps the timestamp file's bytes in a `ByteBuffer` and reads them as a stream
 * of `Long` values with `NioStreams.fromByteBufferLong`.
 *
 * Run with: sbt "runMain processbinarydata.Step1ReadBuffer"
 */
object Step1ReadBuffer {
  def main(args: Array[String]): Unit = {
    val dump = SensorFiles.create()
    try {
      val timestamps: ByteBuffer = ByteBuffer.wrap(Files.readAllBytes(dump.timestamps))

      val count = NioStreams.fromByteBufferLong(timestamps.duplicate()).runFold(0L)((n, _) => n + 1L)
      val first = NioStreams.fromByteBufferLong(timestamps.duplicate()).head
      val last  = NioStreams.fromByteBufferLong(timestamps.duplicate()).last

      println(s"samples: $count")
      println(s"first:   $first")
      println(s"last:    $last")

      // A stream over a ByteBuffer moves the buffer's position as it reads.
      val consumed = NioStreams.fromByteBufferLong(timestamps).runFold(0L)((n, _) => n + 1L)
      println(s"position after a run: ${timestamps.position()} of ${timestamps.limit()} (read $consumed)")
    } finally SensorFiles.delete(dump)
  }
}
