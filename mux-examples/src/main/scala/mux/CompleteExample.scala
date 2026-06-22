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

import zio.blocks.mux._

/**
 * Mux — Complete Example: HTTP/2-like Stream Multiplexer
 *
 * This realistic example simulates an HTTP/2-like protocol with multiple
 * concurrent streams, capacity limits, request/response patterns, and proper
 * shutdown. Demonstrates how to manage many independent streams over a shared
 * connection.
 *
 * Run with: sbt "mux-examples/runMain mux.CompleteExample"
 */
@main def CompleteExample(): Unit = {
  println("=== Mux Complete Example: HTTP/2-like Protocol ===\n")

  // Simulate HTTP/2 with limited concurrent streams (e.g., 4)
  val mux = Mux[Int, String, String](4)

  println("Protocol initialized with capacity for 4 concurrent streams\n")

  // Open 4 requests (some will be for different routes)
  val requests = (1 to 4).flatMap { streamId =>
    val route = streamId match {
      case 1 => "GET /api/users"
      case 2 => "GET /api/posts"
      case 3 => "POST /api/users"
      case 4 => "GET /api/comments"
    }
    mux.open(streamId) match {
      case s: MuxStream[Int, String, String] =>
        Some((streamId, route, s))
      case error: MuxError =>
        println(s"✗ Failed to open stream $streamId: $error")
        None
    }
  }

  // Validate that all streams opened successfully
  if (requests.isEmpty) {
    println("✗ Failed to open any streams")
    sys.exit(1)
  }

  println(s"Opened ${requests.length} streams for concurrent requests:")
  requests.foreach { (id, route, _) =>
    println(f"  Stream $id: $route")
  }

  // Send requests
  println("\nSending HTTP requests:")
  requests.foreach { (id, route, stream) =>
    stream.send(route) match {
      case () =>
        println(f"  Stream $id: sent '$route'")
      case error: MuxError =>
        println(f"  Stream $id: failed to send '$route': $error")
    }
  }

  // Simulate protocol draining outbound queue and sending responses
  println("\nProtocol processes outbound and sends responses:")
  requests.foreach { (id, _, stream) =>
    stream.takeOutbound() match {
      case Some(request) =>
        println(f"  Stream $id: transmitting: $request")
        // Simulate receiving response
        val response = id match {
          case 1 => "200 OK: [Alice, Bob, Charlie]"
          case 2 => "200 OK: [post1, post2, post3]"
          case 3 => "201 Created: User 4 added"
          case 4 => "200 OK: [comment1, comment2]"
        }
        stream.offerInbound(response) match {
          case () =>
            println(f"  Stream $id: response delivered")
          case error: MuxError =>
            println(f"  Stream $id: failed to deliver response: $error")
        }
      case None =>
        println(f"  Stream $id: no message in queue")
      case _: MuxError =>
        println(f"  Stream $id: stream is closed")
    }
  }

  // Application reads responses
  println("\nApplication receives responses:")
  requests.foreach { (id, _, stream) =>
    stream.receive() match {
      case Some(response) =>
        println(f"  Stream $id: received: $response")
      case None =>
        println(f"  Stream $id: no response yet")
      case error: MuxError =>
        println(f"  Stream $id: error receiving: $error")
    }
  }

  // Close some streams to free capacity
  println(f"\nActive streams before cleanup: ${mux.activeCount}")
  val streamsToClose = requests.take(2)
  streamsToClose.foreach { case (_, _, stream) =>
    stream.close()
  }
  println(f"Closed ${streamsToClose.length} streams")
  println(f"Active streams after cleanup: ${mux.activeCount}\n")

  // Now we can open new requests (because we freed capacity)
  println("Capacity freed - opening new streams:")
  mux.open(5) match {
    case stream5: MuxStream[Int, String, String] =>
      stream5.send("PATCH /api/users/3") match {
        case () =>
          println(f"  Opened stream 5: sent PATCH /api/users/3")
        case error: MuxError =>
          println(f"  Opened stream 5 but failed to send: $error")
      }
    case error: MuxError =>
      println(f"  Failed to open stream 5: $error")
  }

  mux.open(6) match {
    case stream6: MuxStream[Int, String, String] =>
      stream6.send("DELETE /api/posts/2") match {
        case () =>
          println(f"  Opened stream 6: sent DELETE /api/posts/2")
        case error: MuxError =>
          println(f"  Opened stream 6 but failed to send: $error")
      }
    case error: MuxError =>
      println(f"  Failed to open stream 6: $error")
  }

  println(f"Active streams: ${mux.activeCount}\n")

  // Attempt to exceed capacity
  println("Attempting to exceed capacity (4 open -> 5th request):")
  val overCapacityResult = mux.open(7)
  overCapacityResult match {
    case _: MuxStream[_, _, _] =>
      println("  ✗ Unexpected: opened beyond capacity!")
    case error: MuxError =>
      println(f"  ✓ Capacity enforced: $error\n")
  }

  // Simulate backpressure on a single stream
  println("Demonstrating backpressure handling:")
  println("  Filling stream 3's outbound queue with many messages...")
  val (_, _, stream3) = requests(2)
  var sendCount       = 0
  var backpressured   = false
  while (!backpressured) {
    stream3.send(f"data-chunk-$sendCount") match {
      case () =>
        sendCount += 1
      case _: MuxError =>
        backpressured = true
        println(f"  Backpressure: queue full after $sendCount messages\n")
    }
  }

  // Clear some messages
  println("Draining messages to relieve backpressure:")
  var drainCount = 0
  (0 until 5).foreach { _ =>
    stream3.takeOutbound() match {
      case Some(_) =>
        drainCount += 1
      case None =>
        // Queue empty
        ()
      case _: MuxError =>
        // Error during drain
        ()
    }
  }
  println(f"  Drained $drainCount messages")

  stream3.send("resumed-sending") match {
    case () =>
      println("  ✓ Can send again after draining\n")
    case error: MuxError =>
      println(f"  Still blocked: $error\n")
  }

  // Graceful shutdown
  println("Initiating graceful shutdown...")
  println(f"Active streams before closeAll: ${mux.activeCount}")

  mux.closeAll(MuxError.MuxClosed)
  println("Called closeAll()")
  println(f"Active streams after closeAll: ${mux.activeCount}")

  // Verify cannot open new streams
  val afterShutdown = mux.open(99)
  println(f"Attempting to open stream after closeAll: $afterShutdown")

  println("\n=== Example Complete ===")
}
