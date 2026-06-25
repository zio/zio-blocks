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
 * Title: Creating a Mux Description: Create a mux that carries String messages
 * in both directions with integer stream IDs, then open a stream and handle
 * both success and failure cases. Run: sbt "mux-examples/runMain
 * mux.example1CreatingAMux"
 */
@main def example1CreatingAMux(): Unit = {
  println("=== Creating a Mux ===")

  // Create a mux that carries String messages in both directions
  val mux = Mux[Int, String, String](100)
  println("Created mux with capacity for 100 concurrent streams")

  println("\n=== Opening a Stream ===")

  // Try to open stream 1
  val streamOrError = mux.open(1)
  streamOrError match {
    case stream: MuxStream[Int, String, String] =>
      println(s"✓ Opened stream with ID 1: $stream")
      println(s"  Stream is closed: ${stream.isClosed}")
      println(s"  Stream is half-closed: ${stream.isHalfClosed}")
    case error: MuxError =>
      println(s"✗ Failed to open stream: $error")
  }
}
