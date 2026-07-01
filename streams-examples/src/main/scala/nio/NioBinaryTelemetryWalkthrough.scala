/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nio

import zio.blocks.chunk.Chunk
import zio.blocks.streams.*

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Pipe

/**
 * Semi-realistic walkthrough: a binary telemetry pipeline over NIO.
 *
 * A fleet of sensors emits `Long` readings. Readings are encoded into binary
 * buffers, shipped over a byte channel, decoded back, aggregated, and the
 * pipeline recovers from a flaky transport. Composes: NioSinks, NioStreams,
 * NioWriters, NioReaders, Stream map/filter/scan/take, catchAll, and manual
 * pull-based consumption.
 *
 * Each section prints EXPECTED vs ACTUAL; a mismatch localizes a defect.
 * (Discovered via this walkthrough + extracted minimal regressions in NioSpec
 * "adversarial regressions": ByteBuffer Long/Double readUpToN sentinel
 * truncation; fromChannel/ChannelWriter close-failure swallowing.)
 */
object NioBinaryTelemetryWalkthrough extends App {
  private var failures = 0

  private def section[A](name: String)(expected: A)(actual: => A): Unit = {
    val a  = actual
    val ok = a == expected
    if (!ok) failures += 1
    println(s"${if (ok) "  OK " else "  FAIL"} $name")
    if (!ok) {
      println(s"        expected: $expected")
      println(s"        actual:   $a")
    }
  }

  println("=== NIO Binary Telemetry Walkthrough ===\n")

  // --------------------------------------------------------------------------
  println("Section 1: encode readings to a ByteBuffer, decode, and aggregate")
  // --------------------------------------------------------------------------
  locally {
    val readings = Chunk(120L, 95L, 130L, 88L, 142L)
    val buf      = ByteBuffer.allocate(readings.length * 8)
    val wrote    = Stream.fromChunk(readings).run(NioSinks.fromByteBufferLong(buf))
    buf.flip()
    val decoded = NioStreams.fromByteBufferLong(buf).filter(_ >= 100L).map(_ - 100L).runCollect
    section("write completes")(Right(()): Either[Nothing, Unit])(wrote)
    section("decode + filter + map")(Right(Chunk(20L, 30L, 42L)): Either[Nothing, Chunk[Long]])(decoded)
  }

  // --------------------------------------------------------------------------
  println("\nSection 2: ship bytes over a channel and decode on the far side")
  // --------------------------------------------------------------------------
  locally {
    val payload = Chunk[Byte](1, 2, 3, 4, 5, 6, 7, 8)
    val pipe    = Pipe.open()
    val writer  = NioWriters.fromChannel(pipe.sink(), bufSize = 1024)
    val left    = writer.writeAll(payload)
    writer.close() // flushes and closes the sink
    val received = NioStreams.fromChannel(pipe.source()).runCollect
    section("writer accepts the full payload")(Chunk.empty[Byte])(left)
    section("far side receives all bytes")(Right(payload): Either[IOException, Chunk[Byte]])(received)
  }

  // --------------------------------------------------------------------------
  println("\nSection 3: flaky transport — close failure must not vanish")
  // --------------------------------------------------------------------------
  locally {
    // The transport delivers its data, but its close() fails (e.g. socket
    // reset during shutdown). Per the library's lossless-error principle the
    // close failure must SURFACE (thrown or Left), never silent success.
    val closeFailure = new IOException("transport close failed")
    val ch           = new java.nio.channels.ReadableByteChannel {
      private var open               = true
      def read(dst: ByteBuffer): Int = -1
      def isOpen: Boolean            = open
      def close(): Unit              = { open = false; throw closeFailure }
    }
    val outcome =
      try {
        val r = NioStreams.fromChannel(ch).runDrain
        r match {
          case Left(e)  => s"surfaced as Left(${e.getMessage})"
          case Right(_) => "silent success (close failure swallowed)"
        }
      } catch {
        case e: IOException => s"surfaced as thrown ${e.getMessage}"
      }
    section("close failure is observable")("surfaced as thrown transport close failed")(outcome)
  }

  // --------------------------------------------------------------------------
  println("\nSection 4: manual pull with an in-band heartbeat marker")
  // --------------------------------------------------------------------------
  locally {
    // The telemetry protocol reserves Long.MaxValue as a heartbeat marker that
    // is a legitimate element of the stream. Bulk manual pulls must not
    // confuse it with end-of-stream.
    val frame = Chunk(7L, Long.MaxValue, 9L)
    val buf   = ByteBuffer.allocate(frame.length * 8)
    frame.foreach(buf.putLong)
    buf.flip()

    val collectOracle = {
      val b2 = buf.duplicate()
      NioStreams.fromByteBufferLong(b2).runCollect
    }
    section("runCollect keeps the heartbeat marker")(Right(frame): Either[Nothing, Chunk[Long]])(collectOracle)

    val manual = NioReaders.fromByteBufferLong(buf.duplicate()).readUpToN[Long](16)
    section("manual readUpToN keeps the heartbeat marker")(frame)(manual)
  }

  // --------------------------------------------------------------------------
  println("\nSection 5: end-to-end — encode, transport, decode, running stats")
  // --------------------------------------------------------------------------
  locally {
    val readings = Chunk(10L, 20L, 30L)
    val binary   = ByteBuffer.allocate(readings.length * 8)
    val _        = Stream.fromChunk(readings).run(NioSinks.fromByteBufferLong(binary))
    binary.flip()

    val pipe   = Pipe.open()
    val writer = NioWriters.fromChannel(pipe.sink(), bufSize = 256)
    val bytes  = new Array[Byte](binary.remaining())
    binary.get(bytes)
    writer.writeBytes(bytes, 0, bytes.length)
    writer.close()

    val rawBytes = NioStreams.fromChannel(pipe.source()).runCollect
    val stats    = rawBytes.map { bs =>
      val back = ByteBuffer.wrap(bs.toArray)
      NioStreams.fromByteBufferLong(back).scan(0L)(_ + _).runCollect
    }
    section("running totals after full round-trip")(
      Right(Right(Chunk(0L, 10L, 30L, 60L))): Either[IOException, Either[Nothing, Chunk[Long]]]
    )(stats)
  }

  // --------------------------------------------------------------------------
  println("\nSection 6: replayed calibration slice (take/drop window under repeated)")
  // --------------------------------------------------------------------------
  // A fixed calibration frame of 8 readings is decoded as the slice
  // take(5).drop(3) — readings 3 and 4 — and replayed for three calibration
  // passes. The ByteBuffer reader must restore the SAME composed window on
  // every replay cycle (Reader.setLimit/setSkip contract: store derived window
  // bounds, not raw skip/limit values); re-deriving [skip, skip+limit) on
  // reset() leaks readings that were outside the slice.
  // Originally exposed BUG-R8-03 (since fixed); minimal regressions in
  // NioSpec "origin, window and accuracy (round 8)" [AdversarialWindowResetSpec].
  locally {
    val frame = ByteBuffer.allocate(8 * 8)
    (0 until 8).foreach(i => frame.putLong(i.toLong * 10L))
    frame.flip()
    val replayed = NioStreams.fromByteBufferLong(frame).take(5).drop(3).repeated.take(6).runCollect
    section("three calibration passes over the [3,5) slice")(
      Right(Chunk(30L, 40L, 30L, 40L, 30L, 40L)): Either[Nothing, Chunk[Long]]
    )(replayed)
  }

  println()
  if (failures == 0) println("All walkthrough sections completed as expected.")
  else println(s"$failures walkthrough section(s) FAILED — see minimal regressions in NioSpec.")
}
