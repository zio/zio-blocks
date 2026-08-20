package mux

import zio.blocks.mux._

/**
 * Title: Putting It Together Description: Complete, runnable example that
 * demonstrates all mux concepts: creating mux, opening streams, exchanging
 * messages, handling errors, managing capacity, and closing gracefully. This is
 * the comprehensive end-to-end demonstration showing real request-response
 * communication. Run: sbt "mux-examples/runMain mux.completeExample"
 */
@main def completeExample(): Unit = {
  println("=" * 70)
  println("COMPLETE MUX EXAMPLE: Request-Response Communication")
  println("=" * 70)

  // Create a mux: stream IDs are Int, messages are String in both directions
  val mux = Mux[Int, String, String](100)
  println("\n✓ Created mux with capacity for 100 concurrent streams\n")

  println("=" * 70)
  println("OPENING STREAMS")
  println("=" * 70)

  // Open three streams, handling potential capacity errors
  val streams = (1 to 3).map { id =>
    mux.open(id) match {
      case stream: MuxStream[Int, String, String] =>
        println(s"✓ Opened stream $id")
        stream
      case error: MuxError =>
        println(s"✗ Failed to open stream $id: $error")
        throw new RuntimeException(s"Cannot proceed without stream $id")
    }
  }.toList

  println("\n" + "=" * 70)
  println("APPLICATION SENDS MESSAGES")
  println("=" * 70)

  // Application sends messages on each stream
  println()
  streams.zipWithIndex.foreach { case (stream, idx) =>
    val msg = s"Hello from stream ${idx + 1}"
    stream.send(msg) match {
      case ()          => println(s"✓ Stream ${idx + 1} sent: '$msg'")
      case _: MuxError => println(s"✗ Stream ${idx + 1} send failed")
    }
  }

  println("\n" + "=" * 70)
  println("PROTOCOL EXTRACTS OUTBOUND MESSAGES")
  println("=" * 70)

  // Protocol extracts messages to send over the network
  println()
  streams.zipWithIndex.foreach { case (stream, idx) =>
    stream.takeOutbound() match {
      case Some(msg) =>
        println(s"✓ Protocol will send on stream ${idx + 1}: '$msg'")
      case None =>
        println(s"✗ Stream ${idx + 1} has no outbound messages")
      case _: MuxError =>
        println(s"✗ Stream ${idx + 1} takeOutbound failed")
    }
  }

  println("\n" + "=" * 70)
  println("PROTOCOL DELIVERS INBOUND MESSAGES")
  println("=" * 70)

  // Protocol delivers responses received from the peer
  println()
  streams.zipWithIndex.foreach { case (stream, idx) =>
    val response = s"Response from peer on stream ${idx + 1}"
    stream.offerInbound(response) match {
      case ()          => println(s"✓ Protocol delivered on stream ${idx + 1}: '$response'")
      case _: MuxError => println(s"✗ Stream ${idx + 1} offerInbound failed")
    }
  }

  println("\n" + "=" * 70)
  println("APPLICATION RECEIVES MESSAGES")
  println("=" * 70)

  // Application receives the responses
  println()
  streams.zipWithIndex.foreach { case (stream, idx) =>
    stream.receive() match {
      case Some(msg) =>
        println(s"✓ Stream ${idx + 1} received: '$msg'")
      case None =>
        println(s"✗ Stream ${idx + 1} has no inbound messages")
      case _: MuxError =>
        println(s"✗ Stream ${idx + 1} receive failed")
    }
  }

  println("\n" + "=" * 70)
  println("GRACEFUL SHUTDOWN")
  println("=" * 70)

  // Streams close gracefully
  println()
  val stream1 = streams(0)
  println("Stream 1: signalling local half-close...")
  stream1.halfClose()
  println(s"  isClosed: ${stream1.isClosed}, isHalfClosed: ${stream1.isHalfClosed}")

  println("Stream 1: signalling remote close...")
  stream1.signalRemoteClose()
  println(s"  isClosed: ${stream1.isClosed}, isHalfClosed: ${stream1.isHalfClosed}")

  // Verify the stream is closed
  assert(stream1.isClosed, "Stream 1 should be closed")
  println("✓ Stream 1 is fully closed")

  println("\n" + "=" * 70)
  println("VERIFY INDEPENDENCE")
  println("=" * 70)

  // Streams 2 and 3 are unaffected by stream 1 closing
  println()
  println(s"Stream 2 is open: ${!streams(1).isClosed}")
  println(s"Stream 3 is open: ${!streams(2).isClosed}")

  println("\n" + "=" * 70)
  println("IMMEDIATE CLOSURE")
  println("=" * 70)

  // Demonstrate immediate closure on stream 2
  println()
  val stream2 = streams(1)
  println("Stream 2: performing immediate close()...")
  stream2.close()
  println(s"  isClosed: ${stream2.isClosed}")
  println("✓ Stream 2 is immediately closed")

  println("\n" + "=" * 70)
  println("EXTERNAL CANCELLATION")
  println("=" * 70)

  // Demonstrate external cancellation on stream 3
  println()
  println("Stream 3: performing external mux.cancel()...")
  mux.cancel(3, MuxError.Cancelled(3, "Application shutdown"))
  val stream3 = streams(2)
  println(s"  isClosed: ${stream3.isClosed}")
  println("✓ Stream 3 is cancelled")

  println("\n" + "=" * 70)
  println("EXAMPLE COMPLETE")
  println("=" * 70)
  println("\nYou've seen:")
  println("  • Creating a mux with capacity control")
  println("  • Opening multiple independent streams")
  println("  • Application sending messages (send/receive)")
  println("  • Protocol exchanging messages (takeOutbound/offerInbound)")
  println("  • Graceful shutdown (halfClose + signalRemoteClose)")
  println("  • Immediate closure (close())")
  println("  • External cancellation (mux.cancel())")
  println("  • Stream independence and isolation")
  println("\n✓ All concepts demonstrated successfully!")
}
