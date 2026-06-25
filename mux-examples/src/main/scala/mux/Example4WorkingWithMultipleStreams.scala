package mux

import zio.blocks.mux._

/**
 * Title: Working with Multiple Streams Description: Demonstrate that multiple
 * streams are independent: each has its own queues with no crosstalk, and
 * messages stay on their originating stream. Run: sbt "mux-examples/runMain
 * mux.example4WorkingWithMultipleStreams"
 */
@main def example4WorkingWithMultipleStreams(): Unit = {
  println("=== Working with Multiple Streams ===\n")

  val mux = Mux[Int, String, String](100)

  println("--- Opening three streams ---")
  // Open three streams
  val stream1 = mux.open(1) match {
    case s: MuxStream[Int, String, String] => s
    case e: MuxError                       => throw new RuntimeException(s"Failed to open stream 1: $e")
  }
  val stream2 = mux.open(2) match {
    case s: MuxStream[Int, String, String] => s
    case e: MuxError                       => throw new RuntimeException(s"Failed to open stream 2: $e")
  }
  val stream3 = mux.open(3) match {
    case s: MuxStream[Int, String, String] => s
    case e: MuxError                       => throw new RuntimeException(s"Failed to open stream 3: $e")
  }
  println("✓ Opened streams 1, 2, 3")

  println("\n--- Application sends messages on each stream ---")
  // Send different messages on each stream
  stream1.send("Message for stream 1") match { case () => (); case e: MuxError => println(s"Stream 1 send error: $e") }
  stream2.send("Message for stream 2") match { case () => (); case e: MuxError => println(s"Stream 2 send error: $e") }
  stream3.send("Message for stream 3") match { case () => (); case e: MuxError => println(s"Stream 3 send error: $e") }
  println("✓ Sent outbound messages on all streams")

  println("\n--- Protocol extracts messages (no crosstalk) ---")
  // Extract messages — they stay on their own stream
  val out1 = stream1.takeOutbound() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  val out2 = stream2.takeOutbound() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  val out3 = stream3.takeOutbound() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  println(s"Stream 1 outbound: '$out1'")
  println(s"Stream 2 outbound: '$out2'")
  println(s"Stream 3 outbound: '$out3'")

  println("\n--- Protocol delivers responses to different streams ---")
  // Deliver responses to different streams
  stream1.offerInbound("Response for stream 1") match {
    case () => (); case e: MuxError => println(s"Stream 1 offer error: $e")
  }
  stream2.offerInbound("Response for stream 2") match {
    case () => (); case e: MuxError => println(s"Stream 2 offer error: $e")
  }
  stream3.offerInbound("Response for stream 3") match {
    case () => (); case e: MuxError => println(s"Stream 3 offer error: $e")
  }
  println("✓ Delivered inbound responses on all streams")

  println("\n--- Application receives on each stream (no crosstalk) ---")
  // Receive on each stream — no crosstalk
  val in1 = stream1.receive() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  val in2 = stream2.receive() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  val in3 = stream3.receive() match {
    case Some(msg)   => msg
    case None        => "(empty)"
    case e: MuxError => s"error: $e"
  }
  println(s"Stream 1 inbound: '$in1'")
  println(s"Stream 2 inbound: '$in2'")
  println(s"Stream 3 inbound: '$in3'")

  println("\n=== Summary ===")
  println("Each stream is independent:")
  println("  - Own outbound and inbound queues")
  println("  - No message crosstalk between streams")
  println("  - Closing one stream doesn't affect others")
}
