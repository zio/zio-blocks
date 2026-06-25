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
 * Title: Thread Safety Description: Demonstrate thread-safe vs single-threaded
 * operations, correct usage patterns, and what operations can be called from
 * which context. Run: sbt "mux-examples/runMain mux.example6ThreadSafety"
 */
@main def example6ThreadSafety(): Unit = {
  println("=== Thread Safety: Operations and Constraints ===\n")

  val mux = Mux[Int, String, String](100)

  println("--- Mux-level thread-safe operations (call from any thread) ---")
  println("✓ mux.open(id) — thread-safe")
  println("✓ mux.get(id) — thread-safe")
  println("✓ mux.cancel(id, reason) — thread-safe")
  println("✓ mux.closeAll(reason) — thread-safe")

  println("\n--- Stream-level thread-safe operations (multiple threads OK) ---")
  println("✓ stream.send(msg) — thread-safe (uses lock-free ring buffer on JVM)")
  println("✓ stream.offerInbound(msg) — thread-safe (uses lock-free ring buffer on JVM)")

  println("\n--- Stream-level SINGLE-THREADED operations (ONE thread only) ---")
  println("✗ stream.receive() — single-threaded (corrupts if called concurrently)")
  println("✗ stream.takeOutbound() — single-threaded (corrupts if called concurrently)")
  println("✗ stream.halfClose() — single-threaded (state transition)")
  println("✗ stream.signalRemoteClose() — single-threaded (state transition)")
  println("✗ stream.close() — single-threaded (state transition)")

  println("\n--- Correct usage pattern ---\n")

  // Reset
  val stream2 = mux.open(2) match {
    case s: MuxStream[Int, String, String] => s
    case e: MuxError                       => throw new RuntimeException(s"Failed to open stream 2: $e")
  }

  println("Thread A (Application thread): calls send() consistently")
  stream2.send("Message 1") match {
    case ()          => println("  ✓ Sent message 1 from thread A")
    case e: MuxError => println(s"  ✗ Error: $e")
  }
  stream2.send("Message 2") match {
    case ()          => println("  ✓ Sent message 2 from thread A")
    case e: MuxError => println(s"  ✗ Error: $e")
  }

  println("\nThread B (Protocol/Read thread): calls takeOutbound() consistently")
  stream2.takeOutbound() match {
    case Some(msg)   => println(s"  ✓ Extracted from thread B: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  ✗ Error: $e")
  }
  stream2.takeOutbound() match {
    case Some(msg)   => println(s"  ✓ Extracted from thread B: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  ✗ Error: $e")
  }

  println("\nThreads C, D, E, ... (Multiple inbound delivery threads): can safely call offerInbound()")
  for (threadId <- 1 to 3) {
    stream2.offerInbound(s"Response from thread $threadId") match {
      case ()          => println(s"  ✓ Offered inbound from thread $threadId (thread-safe)")
      case e: MuxError => println(s"  ✗ Error: $e")
    }
  }

  println("\nThread A (back on Application thread): calls receive() consistently")
  stream2.receive() match {
    case Some(msg)   => println(s"  ✓ Received from thread A: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  ✗ Error: $e")
  }
  stream2.receive() match {
    case Some(msg)   => println(s"  ✓ Received from thread A: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  ✗ Error: $e")
  }
  stream2.receive() match {
    case Some(msg)   => println(s"  ✓ Received from thread A: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  ✗ Error: $e")
  }

  println("\n--- Closing streams: thread safety comparison ---\n")

  println("Single-threaded close (from dedicated thread):")
  println("  stream.halfClose() + stream.signalRemoteClose()")
  println("  OR stream.close()")

  println("\nThread-safe external cancel (from any thread):")
  println("  mux.cancel(id, reason)")
  println("  → Call this when closing from cleanup handler or different thread")
  mux.cancel(2, MuxError.Cancelled(2, "Example cleanup"))
  println("  ✓ Cancelled stream 2 from main thread (would work from any thread)")

  println("\n=== Summary ===")
  println("✓ send() and offerInbound() are thread-safe (use lock-free queues)")
  println("✓ mux operations (open, cancel, closeAll) are thread-safe")
  println("✗ receive(), takeOutbound(), and state transitions must use ONE thread")
  println("✓ Use mux.cancel(id, reason) for thread-safe external cancellation")
}
