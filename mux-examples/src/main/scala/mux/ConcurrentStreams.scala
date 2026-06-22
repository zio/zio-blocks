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

package mux

import zio.blocks.mux.*

/**
 * Title: Managing Concurrent Streams
 *
 * Description: Demonstrate how to manage multiple concurrent streams in a Mux,
 * sending and receiving messages on different streams concurrently, and
 * handling the multiplexing of independent communication channels efficiently.
 *
 * Run: sbt "mux-examples/runMain mux.ConcurrentStreams"
 */
@main def ConcurrentStreams(): Unit = {
  println("╔════════════════════════════════════════╗")
  println("║ Concurrent Streams in Mux             ║")
  println("╚════════════════════════════════════════╝")
  println()

  val mux = Mux[Int, String, String](capacity = 10)

  // Define three concurrent streams
  val streamIds = List(1, 2, 3)

  // Open all streams
  println("=== Phase 1: Opening Streams ===")

  val streams = streamIds.map { id =>
    mux.open(id) match {
      case stream: MuxStream[Int, String, String] =>
        println(s"✓ Opened stream #$id")
        stream
      case error: MuxError =>
        println(s"✗ Failed to open stream $id: $error")
        sys.exit(1)
    }
  }

  println(s"Total active streams: ${mux.activeCount}")
  println()

  // Send messages on all streams
  println("=== Phase 2: Sending Messages ===")

  val messages = Map(
    1 -> "Hello from stream 1",
    2 -> "Data packet for stream 2",
    3 -> "Request on stream 3"
  )

  streams.zipWithIndex.foreach { case (stream, idx) =>
    val id  = streamIds(idx)
    val msg = messages(id)
    stream.send(msg) match {
      case () =>
        println(s"→ Stream #$id sent: \"$msg\"")
      case error: MuxError =>
        println(s"✗ Send error on stream $id: $error")
        sys.exit(1)
    }
  }
  println()

  // Drain outbound messages (simulate protocol layer reading from streams)
  println("=== Phase 3: Protocol Layer Drains Outbound Messages ===")

  streams.zipWithIndex.foreach { case (stream, idx) =>
    val id = streamIds(idx)
    stream.takeOutbound() match {
      case Some(msg) =>
        println(s"← Protocol layer read from stream #$id: \"$msg\"")
      case None =>
        println(s"  Stream #$id has no outbound message")
      case error: MuxError =>
        println(s"✗ Error reading from stream $id: $error")
    }
  }
  println()

  // Offer inbound messages (simulate protocol layer writing to streams)
  println("=== Phase 4: Protocol Layer Offers Inbound Messages ===")

  val responses = Map(
    1 -> "Response to stream 1",
    2 -> "Data response for stream 2",
    3 -> "Result from stream 3"
  )

  streams.zipWithIndex.foreach { case (stream, idx) =>
    val id  = streamIds(idx)
    val msg = responses(id)
    stream.offerInbound(msg) match {
      case () =>
        println(s"→ Protocol offered to stream #$id: \"$msg\"")
      case error: MuxError =>
        println(s"✗ Offer error on stream $id: $error")
        sys.exit(1)
    }
  }
  println()

  // Receive messages on all streams
  println("=== Phase 5: Receiving Messages ===")

  streams.zipWithIndex.foreach { case (stream, idx) =>
    val id = streamIds(idx)
    stream.receive() match {
      case Some(msg) =>
        println(s"← Stream #$id received: \"$msg\"")
      case None =>
        println(s"  Stream #$id has no inbound message")
      case error: MuxError =>
        println(s"✗ Receive error on stream $id: $error")
    }
  }
  println()

  // Test stream state operations
  println("=== Phase 6: Stream State Transitions ===")

  println(s"Stream #1 initial state:")
  println(s"  isClosed: ${streams(0).isClosed}, isHalfClosed: ${streams(0).isHalfClosed}")

  streams(0).halfClose()
  println(s"Stream #1 after halfClose():")
  println(s"  isClosed: ${streams(0).isClosed}, isHalfClosed: ${streams(0).isHalfClosed}")

  streams(1).signalRemoteClose()
  println(s"Stream #2 after signalRemoteClose():")
  println(s"  isClosed: ${streams(1).isClosed}, isHalfClosed: ${streams(1).isHalfClosed}")
  println()

  // Close streams individually
  println("=== Phase 7: Closing Streams ===")

  streams.zipWithIndex.foreach { case (stream, idx) =>
    val id = streamIds(idx)
    stream.close()
    println(s"Stream #$id closed: ${stream.isClosed}")
  }

  println()
  println(s"Final active streams: ${mux.activeCount}")
  println("✓ Example complete!")
}
