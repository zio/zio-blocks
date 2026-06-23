package mux

import zio.blocks.mux._

/**
 * Title: The Stream Lifecycle
 *
 * Description: Demonstrate state transitions through OPEN → HALF_CLOSED →
 * CLOSED using graceful shutdown, immediate closure, and external cancellation.
 *
 * Run: sbt "mux-examples/runMain mux.example3StreamLifecycle"
 */
@main def example3StreamLifecycle(): Unit = {
  println("=== Graceful Stream Lifecycle (halfClose + signalRemoteClose) ===\n")

  val mux     = Mux[Int, String, String](100)
  val stream1 = mux.open(1) match {
    case s: MuxStream[Int, String, String] => s
    case _                                 => throw new RuntimeException("Failed to open stream 1")
  }

  // Stream starts in OPEN state
  println(s"Initial state:")
  println(s"  isClosed: ${stream1.isClosed}")
  println(s"  isHalfClosed: ${stream1.isHalfClosed}")

  // Application is done sending, signals half-close
  println(s"\nCalling halfClose()...")
  stream1.halfClose()
  println(s"After halfClose():")
  println(s"  isClosed: ${stream1.isClosed}")
  println(s"  isHalfClosed: ${stream1.isHalfClosed}")

  // Protocol receives a close signal from the peer
  println(s"\nCalling signalRemoteClose()...")
  stream1.signalRemoteClose()
  println(s"After signalRemoteClose():")
  println(s"  isClosed: ${stream1.isClosed}")
  println(s"  isHalfClosed: ${stream1.isHalfClosed}")

  println("\n=== Immediate Stream Closure (close) ===\n")

  val stream2 = mux.open(2) match {
    case s: MuxStream[Int, String, String] => s
    case _                                 => throw new RuntimeException("Failed to open stream 2")
  }

  println(s"Before close():")
  println(s"  isClosed: ${stream2.isClosed}")

  // Application decides to close immediately (e.g., due to an error)
  println(s"\nCalling close()...")
  stream2.close()

  println(s"After close():")
  println(s"  isClosed: ${stream2.isClosed}")

  println("\n=== External Stream Cancellation (mux.cancel) ===\n")

  val stream3 = mux.open(3) match {
    case s: MuxStream[Int, String, String] => s
    case _                                 => throw new RuntimeException("Failed to open stream 3")
  }

  println(s"Before cancel():")
  println(s"  isClosed: ${stream3.isClosed}")

  // From any thread, cancel the stream by ID (thread-safe)
  println(s"\nCalling mux.cancel(3, ...)...")
  mux.cancel(3, MuxError.Cancelled(3, "Cleanup: application shutting down"))

  println(s"After mux.cancel():")
  println(s"  isClosed: ${stream3.isClosed}")

  println("\n=== Summary ===")
  println("Graceful shutdown: halfClose() + signalRemoteClose() (single-threaded)")
  println("Immediate closure: close() (single-threaded)")
  println("External cancellation: mux.cancel(id, reason) (thread-safe)")
}
