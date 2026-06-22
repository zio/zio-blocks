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
 * Title: Complete Mux Example - Multiplexed Request-Response System
 *
 * Description: A comprehensive end-to-end example simulating a multiplexed
 * request-response system (similar to HTTP/2 multiplexing). Demonstrates
 * opening streams, sending requests, processing through a protocol layer,
 * receiving responses, and managing stream lifecycles with proper error
 * handling.
 *
 * Run: sbt "mux-examples/runMain mux.CompleteExample"
 */
@main def CompleteExample(): Unit = {
  println("╔═══════════════════════════════════════════════╗")
  println("║     Multiplexed Request-Response System       ║")
  println("╚═══════════════════════════════════════════════╝")
  println()

  // Create a Mux for managing request-response streams
  val mux = Mux[Int, String, String](capacity = 20)

  // Define client requests
  val clientRequests = Map(
    1 -> "GET /api/users",
    2 -> "POST /api/items",
    3 -> "DELETE /api/cache"
  )

  // ===== PHASE 1: Client Opens Streams =====
  println("PHASE 1: Client Opens Request Streams")
  println("=" * 45)

  val streams = clientRequests.keys.map { id =>
    mux.open(id) match {
      case s: MuxStream[Int, String, String] =>
        println(s"  ✓ Stream #$id opened")
        (id, s)
      case error: MuxError =>
        println(s"  ✗ Failed to open stream $id: $error")
        sys.exit(1)
    }
  }

  val streamMap = streams.toMap
  println(s"Total active streams: ${mux.activeCount}")
  println()

  // ===== PHASE 2: Client Sends Requests =====
  println("PHASE 2: Client Sends Requests on Streams")
  println("=" * 45)

  clientRequests.foreach { case (id, request) =>
    val stream = streamMap(id)
    stream.send(request) match {
      case () =>
        println(s"  → Stream #$id sends: \"$request\"")
      case error: MuxError =>
        println(s"  ✗ Send error on stream $id: $error")
        sys.exit(1)
    }
  }
  println()

  // ===== PHASE 3: Protocol Layer Drains Outbound =====
  println("PHASE 3: Protocol Layer Reads Requests")
  println("=" * 45)

  clientRequests.keys.foreach { id =>
    val stream = streamMap(id)
    stream.takeOutbound() match {
      case Some(msg) =>
        println(s"  ← Protocol reads from stream #$id: \"$msg\"")
      case None =>
        println(s"  ✗ No message on stream $id")
      case error: MuxError =>
        println(s"  ✗ Error reading stream $id: $error")
    }
  }
  println()

  // ===== PHASE 4: Server Processing (Simulated) =====
  println("PHASE 4: Server Processes Requests")
  println("=" * 45)

  val serverResponses = Map(
    1 -> "HTTP/1.1 200 OK - [user1, user2, user3]",
    2 -> "HTTP/1.1 201 Created - {id: 42}",
    3 -> "HTTP/1.1 204 No Content"
  )

  serverResponses.foreach { case (id, _) =>
    println(s"  ⚙ Server processes stream #$id")
  }
  println(s"  ⚙ Processing complete")
  println()

  // ===== PHASE 5: Protocol Layer Offers Responses =====
  println("PHASE 5: Protocol Layer Offers Responses")
  println("=" * 45)

  serverResponses.foreach { case (id, response) =>
    val stream = streamMap(id)
    stream.offerInbound(response) match {
      case () =>
        println(s"  → Protocol offers to stream #$id: \"$response\"")
      case error: MuxError =>
        println(s"  ✗ Offer error on stream $id: $error")
        sys.exit(1)
    }
  }
  println()

  // ===== PHASE 6: Client Receives Responses =====
  println("PHASE 6: Client Receives Responses")
  println("=" * 45)

  clientRequests.keys.foreach { id =>
    val stream = streamMap(id)
    stream.receive() match {
      case Some(msg) =>
        println(s"  ← Stream #$id received: \"$msg\"")
      case None =>
        println(s"  ✗ No response on stream $id")
      case error: MuxError =>
        println(s"  ✗ Receive error on stream $id: $error")
    }
  }
  println()

  // ===== PHASE 7: Stream State Management =====
  println("PHASE 7: Stream State Transitions")
  println("=" * 45)

  val stream1 = streamMap(1)
  val stream2 = streamMap(2)

  // Stream 1: Client sends additional request after receiving response
  stream1.send("GET /api/users/1") match {
    case () =>
      println(s"  → Stream #1: Can send follow-up request after response")
    case error: MuxError =>
      println(s"  ✗ Error: $error")
      sys.exit(1)
  }

  // Stream 1: Client half-closes (no more sends)
  stream1.halfClose()
  println(s"  ⊢ Stream #1: Client signals end of sending")

  // Stream 2: Server signals end of response
  stream2.signalRemoteClose()
  println(s"  ⊣ Stream #2: Server signals end of response")

  // Attempt to send on stream 2 after remote close (should still work)
  stream2.send("Additional request") match {
    case () =>
      println(s"  → Stream #2: Can still send after remote close")
    case error: MuxError =>
      println(s"  ✗ Send error: $error")
      sys.exit(1)
  }
  println()

  // ===== PHASE 8: Stream Closure =====
  println("PHASE 8: Closing Streams")
  println("=" * 45)

  // Close stream 1 completely
  stream1.close()
  println(s"  ✗ Stream #1 fully closed")

  // Cancel stream 2 with error
  mux.cancel(2, reason = MuxError.Cancelled(2, "Client timeout"))
  println(s"  ⚠ Stream #2 cancelled (timeout)")

  // Close stream 3 normally
  val stream3 = streamMap(3)
  stream3.close()
  println(s"  ✗ Stream #3 fully closed")

  println(s"  Remaining active streams: ${mux.activeCount}")
  println()

  // ===== PHASE 9: Verify Cleanup =====
  println("PHASE 9: Verifying Cleanup")
  println("=" * 45)

  mux.get(1) match {
    case Some(_) => println("  ✗ Stream #1 still exists (unexpected)")
    case None    => println("  ✓ Stream #1 properly cleaned up")
  }

  mux.get(2) match {
    case Some(_) => println("  ✗ Stream #2 still exists (unexpected)")
    case None    => println("  ✓ Stream #2 properly cleaned up")
  }

  mux.get(3) match {
    case Some(_) => println("  ✗ Stream #3 still exists (unexpected)")
    case None    => println("  ✓ Stream #3 properly cleaned up")
  }
  println()

  // ===== PHASE 10: Close Mux =====
  println("PHASE 10: Closing Mux")
  println("=" * 45)

  mux.closeAll(reason = MuxError.MuxClosed)
  println(s"  ✗ Mux closed, all streams terminated")
  println(s"  Final active streams: ${mux.activeCount}")
  println()

  println("╔═══════════════════════════════════════════════╗")
  println("║         Example Complete!                     ║")
  println("║                                               ║")
  println("║  Key Concepts Demonstrated:                   ║")
  println("║  • Creating and managing multiple streams      ║")
  println("║  • Multiplexed message passing                 ║")
  println("║  • Stream state transitions (half-close)       ║")
  println("║  • Proper stream lifecycle management          ║")
  println("║  • Error handling and cleanup                  ║")
  println("╚═══════════════════════════════════════════════╝")
}
