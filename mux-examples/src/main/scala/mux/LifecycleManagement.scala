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
 * Mux — Stream Lifecycle: Half-Close and Graceful Shutdown
 *
 * This example demonstrates the stream state machine transitions: OPEN →
 * HALF_CLOSED_LOCAL → CLOSED. Shows how to signal end-of-stream gracefully and
 * handle the transitions.
 *
 * Run with: sbt "mux-examples/runMain mux.LifecycleManagement"
 */
@main def LifecycleManagement(): Unit = {
  println("=== Mux Stream Lifecycle ===\n")

  val mux    = Mux[Int, String, String](100)
  val stream = mux.open(1) match {
    case s: MuxStream[?, ?, ?] =>
      println("✓ Stream 1 opened")
      s.asInstanceOf[MuxStream[Int, String, String]]
    case error: MuxError =>
      println(s"✗ Failed to open stream: $error")
      sys.exit(1)
  }

  // --- Initial state: OPEN ---
  println("Initial state: OPEN")
  println(f"  isClosed: ${stream.isClosed}, isHalfClosed: ${stream.isHalfClosed}\n")

  // --- Send some messages before closing ---
  println("Sending messages...")
  stream.send("msg-1")
  stream.send("msg-2")
  println("  Sent 2 messages\n")

  // --- Local side signals it's done sending ---
  println("Local side calls halfClose() — signals we're done sending")
  stream.halfClose()
  println(f"  State: isClosed: ${stream.isClosed}, isHalfClosed: ${stream.isHalfClosed}")

  // After halfClose, we can still receive buffered messages
  println("\nCan still receive buffered messages after halfClose:")
  stream.offerInbound("buffered-response-1")
  stream.offerInbound("buffered-response-2")

  val msg1 = stream.receive()
  val msg2 = stream.receive()
  println(f"  Received: $msg1")
  println(f"  Received: $msg2\n")

  // But cannot send new messages
  println("Attempting to send after halfClose:")
  val sendResult = stream.send("new-msg")
  sendResult match {
    case () =>
      println("  ✗ Unexpected: send succeeded!")
    case error: MuxError =>
      println(f"  ✓ Correctly blocked: $error\n")
  }

  // --- Remote side signals it's done sending ---
  println("Remote side signals END_STREAM via signalRemoteClose()")
  stream.signalRemoteClose()
  println(f"  State: isClosed: ${stream.isClosed}, isHalfClosed: ${stream.isHalfClosed}\n")

  // Now fully closed
  println("Stream is now CLOSED (both sides closed)")
  println("Cannot send or offer inbound anymore:")

  val sendAfterClose = stream.send("too-late")
  println(f"  send(): $sendAfterClose")

  val offerAfterClose = stream.offerInbound("too-late")
  println(f"  offerInbound(): $offerAfterClose\n")

  // --- Explicit close example ---
  println("Example 2: Forcibly closing a stream")
  val stream2 = mux.open(2) match {
    case s: MuxStream[?, ?, ?] =>
      println(f"  Stream 2 created: isClosed: ${s.isClosed}")
      s.asInstanceOf[MuxStream[Int, String, String]]
    case error: MuxError =>
      println(s"  Failed to open stream 2: $error")
      sys.exit(1)
  }

  stream2.close()
  println(f"  Stream 2 closed: isClosed: ${stream2.isClosed}")
  println(f"  Mux active count: ${mux.activeCount}\n")

  // --- Capacity is freed after close ---
  println("Example 3: Capacity management during lifecycle")
  val mux2 = Mux[Int, String, String](3)
  println("Created mux with capacity 3\n")

  (1 to 3).foreach { i =>
    mux2.open(i)
    println(f"  Opened stream $i")
  }

  println(f"Active: ${mux2.activeCount} (at capacity)\n")

  // Try to open 4th stream
  println("Attempting to open stream 4 (capacity exceeded):")
  val result = mux2.open(4)
  println(f"  $result\n")

  // Close one and try again
  println("Closing stream 1 to free capacity...")
  mux2.get(1).foreach(_.close())
  println(f"Active: ${mux2.activeCount}\n")

  val stream4 = mux2.open(4)
  println(f"Opening stream 4 now succeeds: $stream4\n")

  // --- Graceful shutdown: closeAll ---
  println("Example 4: Graceful shutdown with closeAll()")
  val mux3 = Mux[Int, String, String](10)
  (1 to 5).foreach(i => mux3.open(i))
  println(f"Created mux with 5 active streams\n")

  mux3.closeAll(MuxError.MuxClosed)
  println("Called closeAll(MuxClosed)")
  println(f"Active count: ${mux3.activeCount}")
  println("After closeAll, cannot open new streams:")

  val afterClose = mux3.open(6)
  println(f"  open(6): $afterClose")
}
