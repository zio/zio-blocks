package mux

import zio.blocks.mux._

/**
 * Title: Managing Capacity Description: Demonstrate mux-level and per-stream
 * capacity limits, handling QueueFull errors, and the recovery pattern of
 * draining outbound messages to free space. Run: sbt "mux-examples/runMain
 * mux.example5ManagingCapacity"
 */
@main def example5ManagingCapacity(): Unit = {
  println("=== Managing Capacity ===\n")

  println("--- Mux-level capacity (maximum concurrent streams) ---")
  // Create a tiny mux with a small capacity (for demo)
  val mux = Mux[Int, String, String](3)
  println("Created mux with capacity for 3 concurrent streams")

  // Open streams until capacity is exceeded
  println("\nOpening streams:")
  for (i <- 1 to 5) {
    mux.open(i) match {
      case _: MuxStream[Int, String, String] =>
        println(s"  ✓ Opened stream $i")
      case error: MuxError =>
        println(s"  ✗ Failed to open stream $i: $error")
    }
  }

  println("\n--- Per-stream capacity (message queue limits) ---")
  // Create a fresh mux for this demo
  val mux2   = Mux[Int, String, String](100)
  val stream = mux2.open(10) match {
    case s: MuxStream[Int, String, String] => s
    case error: MuxError                   =>
      println(s"Failed to open stream: $error")
      throw new RuntimeException("Cannot proceed")
  }

  println("Sending messages until queue fills (per-stream capacity is 256):")
  var successCount = 0
  var failureCount = 0

  for (i <- 1 to 300) {
    val result = stream.send(s"Message $i")
    result match {
      case () =>
        successCount += 1
        if (i <= 5 || i == 256) println(s"  ✓ Message $i sent")
      case error: MuxError =>
        failureCount += 1
        if (failureCount <= 3) println(s"  ✗ Message $i failed: $error")
    }
  }
  println(s"Summary: $successCount succeeded, $failureCount failed")

  println("\n--- Recovery pattern: draining to free space ---")
  // Fresh mux for recovery demo
  val mux3    = Mux[Int, String, String](100)
  val stream2 = mux3.open(11) match {
    case s: MuxStream[Int, String, String] => s
    case _                                 => ???
  }

  // Fill the queue with a few messages
  println("Filling queue with 5 messages:")
  for (i <- 1 to 5) {
    stream2.send(s"Message $i")
  }

  // Try to send one more (should succeed, we're well below capacity)
  stream2.send("Message 6") match {
    case ()          => println("✓ Message 6 sent (space available)")
    case e: MuxError => println(s"✗ Message 6 failed: $e")
  }

  // Simulate protocol draining one message
  println("\nProtocol drains one message from outbound queue:")
  stream2.takeOutbound() match {
    case Some(msg)   => println(s"  Extracted: '$msg'")
    case None        => println("  (queue empty)")
    case e: MuxError => println(s"  Error: $e")
  }

  // Try to send again after draining (should succeed)
  println("Sending another message after draining:")
  stream2.send("Message 7") match {
    case ()          => println("✓ Message 7 sent (space freed by draining)")
    case e: MuxError => println(s"✗ Message 7 failed: $e")
  }

  println("\n=== Summary ===")
  println("Capacity limits prevent memory exhaustion:")
  println("  - Mux-level: controls number of concurrent streams")
  println("  - Per-stream: limits messages in each queue (256 per direction)")
  println("Recovery pattern: drain outbound queue to free space")
}
