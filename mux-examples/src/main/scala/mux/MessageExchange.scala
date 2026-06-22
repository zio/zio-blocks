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
 * Mux — Message Exchange: Sending and Receiving Messages
 *
 * This example demonstrates the core message exchange pattern: sending messages
 * that go to the outbound queue, and receiving messages from the inbound queue.
 * Shows how two-way communication works across independent streams.
 *
 * Run with: sbt "mux-examples/runMain mux.MessageExchange"
 */
@main def MessageExchange(): Unit = {
  println("=== Mux Message Exchange ===\n")

  val mux = Mux[Int, String, String](100)

  // Open two streams: one for request, one for response
  val requestStream = mux.open(1) match {
    case s: MuxStream[Int, String, String] =>
      println("✓ Request stream opened")
      s
    case error: MuxError =>
      println(s"✗ Failed to open request stream: $error")
      sys.exit(1)
  }

  // Verify first stream opened successfully before opening second
  val responseStream = mux.open(2) match {
    case s: MuxStream[Int, String, String] =>
      println("✓ Response stream opened")
      s
    case error: MuxError =>
      println(s"✗ Failed to open response stream: $error")
      sys.exit(1)
  }

  println("Opened two streams for bidirectional communication\n")

  // --- Simulating request/response flow ---

  // Application sends a request on stream 1
  println("1. Application sends: 'GET /api/users'")
  requestStream.send("GET /api/users") match {
    case ()              => ()
    case error: MuxError =>
      println(s"   Error: Failed to send request: $error")
      sys.exit(1)
  }

  // Protocol would drain the outbound queue
  val outbound1Result = requestStream.takeOutbound()
  outbound1Result match {
    case Some(msg) =>
      println(s"   Protocol reads from outbound: Some($msg)")
    case None =>
      println(s"   Protocol reads from outbound: None")
    case error: MuxError =>
      println(s"   Protocol encountered error: $error")
  }

  // Protocol receives response and delivers it to stream 2
  println("2. Protocol receives response and delivers to stream 2")
  responseStream.offerInbound("200 OK: [user1, user2, user3]") match {
    case () =>
      println("   Inbound queue has 1 message\n")
    case error: MuxError =>
      println(s"   Error: Failed to deliver response: $error\n")
      sys.exit(1)
  }

  // Application reads response from stream 2
  println("3. Application reads from stream 2:")
  val responseResult = responseStream.receive()
  responseResult match {
    case Some(msg) =>
      println(s"   Received: Some($msg)\n")
    case None =>
      println(s"   Received: None\n")
    case error: MuxError =>
      println(s"   Received error: $error\n")
  }

  // --- Multiple message streaming ---

  println("4. Streaming multiple messages:")
  (1 to 3).foreach { i =>
    val msg = s"data-chunk-$i"
    requestStream.send(msg) match {
      case () =>
        println(s"   Sent: $msg")
      case error: MuxError =>
        println(s"   Error: Failed to send '$msg': $error")
    }
  }

  println("\n   Protocol drains all messages:")
  var count = 0
  while (count < 3) {
    requestStream.takeOutbound() match {
      case Some(msg) =>
        println(s"   Drained: $msg")
        count += 1
      case None =>
        // Queue empty but stream still open - OK to continue
        println("   (no more messages)")
        count = 3
      case _: MuxError =>
        // Actual error (stream closed) - break loop
        println("   Stream closed, cannot drain more")
        count = 3
    }
  }

  // --- Queue fullness (backpressure) ---

  println("\n5. Demonstrating backpressure (queue is 256 messages):")
  var sendCount = 0
  var done      = false
  while (!done && sendCount < 260) {
    val result = requestStream.send(s"msg-$sendCount")
    result match {
      case () =>
        sendCount += 1
      case error: MuxError =>
        println(s"   After $sendCount sends: $error")
        done = true
    }
  }

  println(f"\n   Sent $sendCount messages before queue filled")

  // Drain one message to free space
  requestStream.takeOutbound() match {
    case Some(msg) =>
      println(s"   Drained: $msg")
    case None =>
      println("   (no message in queue)")
    case error: MuxError =>
      println(s"   Error draining: $error")
  }
  println("   After draining one, can send again:")

  requestStream.send("recovery-msg") match {
    case () =>
      println("   ✓ Successfully sent after clearing one message")
    case error: MuxError =>
      println(s"   ✗ Still full: $error")
  }

  println(s"\nFinal state: ${mux.activeCount} active streams")
}
