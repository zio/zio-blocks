package sink

import zio.blocks.streams._

import java.nio.ByteBuffer

/**
 * Sentinel values on the `Long` and `Double` lanes.
 *
 * The built-in sinks, `NioSinks.fromByteBufferLong` and
 * `NioSinks.fromByteBufferDouble` included, detect the end of a `Long` or
 * `Double` stream by the count that a one-element `Reader#readLongs` or
 * `Reader#readDoubles` returns, so they write `Long.MaxValue` and
 * `Double.MaxValue` like any other value. A collision can only happen in a
 * custom sink that ends its loop on a scalar read such as
 * `reader.readLong(Long.MaxValue)`: it stops at the first element equal to the
 * sentinel, without an error. The same data counted with the one-element
 * `readLongs` loop keeps every value.
 *
 * The guide "How to Process Binary Data Without Boxing"
 * (https://zioblocks.com/guides/process-binary-data-without-boxing) shows the
 * same pattern on a `Double` stream, and the zero-boxing reference
 * (https://zioblocks.com/reference/streams/execution-and-compatibility/zero-boxing#eof-signalling-per-lane)
 * lists the end-of-stream signal of every lane.
 *
 * Run with: sbt "runMain sink.SinkSentinelExample"
 *
 * Output:
 * {{{
 * fromByteBufferLong:   Right(()), 5 of 5 written: 100, 200, 9223372036854775807, 300, 400
 * fromByteBufferDouble: Right(()), 3 of 3 written: 1.5, 1.7976931348623157E308, 2.5
 * readLong(Long.MaxValue) loop counts: Right(2)
 * readLongs(one, 0, 1) loop counts:    Right(5)
 * }}}
 */
object SinkSentinelExample {

  // Ends on a scalar read, so the element Long.MaxValue looks like the end.
  val countWithSentinel: Sink[Nothing, Long, Long] =
    Sink.create[Nothing, Long, Long] { reader =>
      var n = 0L
      var v = reader.readLong(Long.MaxValue)
      while (v != Long.MaxValue) { n += 1; v = reader.readLong(Long.MaxValue) }
      n
    }

  // Ends on the count that readLongs returns (-1 at the end), so every value is data.
  val countWithCount: Sink[Nothing, Long, Long] =
    Sink.create[Nothing, Long, Long] { reader =>
      val one = new Array[Long](1)
      var n   = 0L
      while (reader.readLongs(one, 0, 1) > 0) n += 1
      n
    }

  def main(args: Array[String]): Unit = {
    val longs      = List(100L, 200L, Long.MaxValue, 300L, 400L)
    val longBuf    = ByteBuffer.allocate(longs.length * 8)
    val longResult = Stream.fromIterable(longs).run(NioSinks.fromByteBufferLong(longBuf))
    longBuf.flip()
    val writtenLongs = List.fill(longBuf.remaining / 8)(longBuf.getLong())
    println(
      s"fromByteBufferLong:   $longResult, ${writtenLongs.length} of ${longs.length} written: ${writtenLongs.mkString(", ")}"
    )

    val doubles      = List(1.5, Double.MaxValue, 2.5)
    val doubleBuf    = ByteBuffer.allocate(doubles.length * 8)
    val doubleResult = Stream.fromIterable(doubles).run(NioSinks.fromByteBufferDouble(doubleBuf))
    doubleBuf.flip()
    val writtenDoubles = List.fill(doubleBuf.remaining / 8)(doubleBuf.getDouble())
    println(
      s"fromByteBufferDouble: $doubleResult, ${writtenDoubles.length} of ${doubles.length} written: ${writtenDoubles.mkString(", ")}"
    )

    println(s"readLong(Long.MaxValue) loop counts: ${Stream.fromIterable(longs).run(countWithSentinel)}")
    println(s"readLongs(one, 0, 1) loop counts:    ${Stream.fromIterable(longs).run(countWithCount)}")
  }
}
