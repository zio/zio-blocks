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
 * Title: Error Handling in Mux Operations
 *
 * Description: Learn how to handle various error conditions in Mux, including
 * capacity exceeded, queue full (backpressure), stream already exists, and
 * operations on closed mux/streams. Demonstrates proper error recovery
 * patterns.
 *
 * Run: sbt "mux-examples/runMain mux.ErrorHandling"
 */
@main def ErrorHandling(): Unit = {
  println("╔════════════════════════════════════════╗")
  println("║ Error Handling in Mux                  ║")
  println("╚════════════════════════════════════════╝")
  println()

  // Test 1: Capacity Exceeded
  println("=== Test 1: Capacity Exceeded ===")

  val mux1 = Mux[Int, String, String](capacity = 2)
  println("Created Mux with capacity = 2")

  // Open two streams (fill capacity)
  mux1.open(1) match {
    case _: MuxStream[?, ?, ?] =>
      println("✓ Opened stream #1")
    case error =>
      println(s"✗ Failed: $error")
  }

  mux1.open(2) match {
    case _: MuxStream[?, ?, ?] =>
      println("✓ Opened stream #2 (capacity now full)")
    case error =>
      println(s"✗ Failed: $error")
  }

  // Attempt to exceed capacity
  mux1.open(3) match {
    case _: MuxStream[?, ?, ?] =>
      println("✗ Unexpected: Stream #3 opened despite full capacity")
    case MuxError.CapacityExceeded(limit) =>
      println(s"✓ Correctly rejected: CapacityExceeded(limit=$limit)")
    case error =>
      println(s"✗ Unexpected error: $error")
  }
  println()

  // Test 2: Queue Full (Backpressure)
  println("=== Test 2: Queue Full (Backpressure) ===")

  val mux2 = Mux[Int, String, String](capacity = 10)

  val stream = mux2.open(1) match {
    case s: MuxStream[Int, String, String] =>
      s
    case error =>
      println(s"Failed to open stream: $error")
      sys.exit(1)
  }

  println("Attempting to fill stream queue...")

  // Try to send 300 messages (queue capacity is 256)
  val (successCount, queueFullCount) = (1 to 300)
    .map(i => stream.send(s"Message $i"))
    .foldLeft((0, 0)) { case ((success, queueFull), result) =>
      result match {
        case ()                    => (success + 1, queueFull)
        case MuxError.QueueFull(_) => (success, queueFull + 1)
        case _                     => (success, queueFull)
      }
    }

  println(s"Sent $successCount messages successfully")
  println(s"Got $queueFullCount QueueFull errors (backpressure)")
  println()

  // Test 3: Duplicate Stream ID
  println("=== Test 3: Duplicate Stream ID ===")

  val mux3 = Mux[Int, String, String](capacity = 10)

  mux3.open(1) match {
    case _: MuxStream[?, ?, ?] =>
      println("✓ Opened stream #1")
    case error =>
      println(s"✗ Failed: $error")
  }

  mux3.open(1) match {
    case _: MuxStream[?, ?, ?] =>
      println("✗ Unexpected: Duplicate stream #1 was allowed")
    case MuxError.ProtocolError(msg) =>
      println(s"✓ Correctly rejected: ProtocolError($msg)")
    case error =>
      println(s"✗ Unexpected error: $error")
  }
  println()

  // Test 4: Operations on Closed Stream
  println("=== Test 4: Operations on Closed Stream ===")

  val mux4 = Mux[Int, String, String](capacity = 10)

  val activeStream = mux4.open(1) match {
    case s: MuxStream[Int, String, String] =>
      s
    case error =>
      println(s"Failed: $error")
      sys.exit(1)
  }

  activeStream.close()
  println(s"Stream #1 closed: ${activeStream.isClosed}")

  // Try to send on closed stream
  activeStream.send("message") match {
    case () =>
      println("✗ Unexpected: Message sent on closed stream")
    case MuxError.StreamClosed(id) =>
      println(s"✓ Correctly rejected: StreamClosed($id)")
    case error =>
      println(s"✗ Unexpected error: $error")
  }

  // Try to receive on closed stream
  activeStream.receive() match {
    case Some(_) =>
      println("✗ Unexpected: Message received on closed stream")
    case None =>
      println("✗ Got None instead of error on closed stream")
    case MuxError.StreamClosed(_) =>
      println("✓ Correctly got StreamClosed error on receive")
    case error =>
      println(s"✗ Unexpected error: $error")
  }
  println()

  // Test 5: Operations on Closed Mux
  println("=== Test 5: Operations on Closed Mux ===")

  val mux5 = Mux[Int, String, String](capacity = 10)

  mux5.closeAll(reason = MuxError.MuxClosed)
  println("Mux closed")

  mux5.open(1) match {
    case _: MuxStream[?, ?, ?] =>
      println("✗ Unexpected: Stream opened on closed Mux")
    case MuxError.MuxClosed =>
      println("✓ Correctly rejected: MuxClosed")
    case error =>
      println(s"✗ Unexpected error: $error")
  }
  println()

  // Test 6: Cancellation
  println("=== Test 6: Stream Cancellation ===")

  val mux6 = Mux[Int, String, String](capacity = 10)

  val cancelStream = mux6.open(1) match {
    case s: MuxStream[Int, String, String] =>
      s
    case error =>
      println(s"Failed: $error")
      sys.exit(1)
  }

  mux6.cancel(1, reason = MuxError.Cancelled(1, "Timeout"))
  println(s"Stream #1 cancelled")
  println(s"Stream #1 closed: ${cancelStream.isClosed}")

  mux6.get(1) match {
    case Some(_) =>
      println("✗ Unexpected: Cancelled stream still exists")
    case None =>
      println("✓ Correctly removed: Cancelled stream no longer accessible")
  }

  println()
  println("✓ All error handling tests complete!")
}
