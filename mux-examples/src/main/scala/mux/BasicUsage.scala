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
 * Mux — Basic Usage: Creating and Opening Streams
 *
 * This example demonstrates how to create a Mux instance and open streams with
 * error handling. It shows the fundamental multiplexer pattern: create once
 * with fixed capacity, then open independent streams.
 *
 * Run with: sbt "mux-examples/runMain mux.BasicUsage"
 */
@main def BasicUsage(): Unit = {
  println("=== Mux Basic Usage ===\n")

  // Create a multiplexer with capacity for 100 concurrent streams
  val mux = Mux[Int, String, String](100)
  println(s"Created mux with capacity 100")

  // Open first stream with ID 1
  val stream1Result = mux.open(1)
  val stream1       = stream1Result match {
    case s: MuxStream[?, ?, ?] =>
      println(s"✓ Stream 1 opened successfully")
      s.asInstanceOf[MuxStream[Int, String, String]]
    case error: MuxError =>
      println(s"✗ Failed to open stream 1: $error")
      sys.exit(1)
  }

  // Open second stream with ID 2
  val stream2Result = mux.open(2)
  val stream2       = stream2Result match {
    case s: MuxStream[?, ?, ?] =>
      println(s"✓ Stream 2 opened successfully")
      s.asInstanceOf[MuxStream[Int, String, String]]
    case error: MuxError =>
      println(s"✗ Failed to open stream 2: $error")
      sys.exit(1)
  }

  // Query stream info
  println(s"\nActive streams: ${mux.activeCount}")
  println(s"Stream 1 ID: ${stream1.id}")
  println(s"Stream 2 ID: ${stream2.id}")
  println(s"Stream 1 is closed: ${stream1.isClosed}")
  println(s"Stream 2 is half-closed: ${stream2.isHalfClosed}")

  // Try opening duplicate ID (should fail)
  println(s"\nAttempting to open duplicate stream with ID 1...")
  val duplicateResult = mux.open(1)
  duplicateResult match {
    case _: MuxStream[_, _, _] =>
      println("✗ Unexpected: duplicate stream opened!")
    case error: MuxError =>
      println(s"✓ Correctly rejected duplicate: $error")
  }

  println(s"\nFinal active stream count: ${mux.activeCount}")
}
