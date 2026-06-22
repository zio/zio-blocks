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
 * Title: Creating and Opening Mux Streams
 *
 * Description: Learn how to create a Mux instance, open multiple streams with
 * unique IDs, and verify basic stream operations. This foundational example
 * demonstrates the core concepts needed to multiplex concurrent streams.
 *
 * Run: sbt "mux-examples/runMain mux.CreatingAMux"
 */
@main def CreatingAMux(): Unit = {
  println("╔════════════════════════════════════════╗")
  println("║ Creating Mux and Opening Streams       ║")
  println("╚════════════════════════════════════════╝")
  println()

  // Create a Mux that can handle up to 10 concurrent streams
  // Stream IDs are Int, messages are String for both directions
  val mux = Mux[Int, String, String](capacity = 10)

  println(s"Created Mux with capacity: 10")
  println(s"Initial active streams: ${mux.activeCount}")
  println()

  // Open the first stream with ID 1
  mux.open(1) match {
    case stream: MuxStream[Int, String, String] =>
      println(s"✓ Opened stream #1")
      println(s"  Stream ID: ${stream.id}")
      println(s"  Is closed: ${stream.isClosed}")
      println(s"  Active streams: ${mux.activeCount}")
      println()

      // Open a second stream with ID 2
      mux.open(2) match {
        case stream2: MuxStream[Int, String, String] =>
          println(s"✓ Opened stream #2")
          println(s"  Stream ID: ${stream2.id}")
          println(s"  Active streams: ${mux.activeCount}")
          println()

          // Attempt to open a duplicate stream (should fail)
          println("Attempting to open duplicate stream with ID 1...")
          mux.open(1) match {
            case _: MuxStream[?, ?, ?] =>
              println(s"✗ Unexpected: Duplicate stream was allowed")
            case error: MuxError =>
              println(s"✓ Correctly rejected: $error")
          }
          println()

          // Retrieve streams by ID
          println("Retrieving streams by ID:")
          mux.get(1) match {
            case Some(s) =>
              println(s"✓ Retrieved stream #1 by ID: ${s.id}")
            case None =>
              println("✗ Could not retrieve stream #1")
          }

          mux.get(2) match {
            case Some(s) =>
              println(s"✓ Retrieved stream #2 by ID: ${s.id}")
            case None =>
              println("✗ Could not retrieve stream #2")
          }
          println()

          // Close first stream
          println("Closing stream #1...")
          stream.close()
          println(s"Stream #1 closed: ${stream.isClosed}")
          println(s"Active streams: ${mux.activeCount}")
          println()

          // Close all remaining streams
          println("Closing all remaining streams...")
          mux.closeAll(reason = MuxError.StreamClosed(2))
          println(s"All streams closed")
          println(s"Active streams: ${mux.activeCount}")
          println()
          println("✓ Example complete!")

        case error: MuxError =>
          println(s"✗ Failed to open stream 2: $error")
          sys.exit(1)
      }

    case error: MuxError =>
      println(s"✗ Failed to open stream 1: $error")
      sys.exit(1)
  }
}
