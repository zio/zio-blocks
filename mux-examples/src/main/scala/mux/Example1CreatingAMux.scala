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
