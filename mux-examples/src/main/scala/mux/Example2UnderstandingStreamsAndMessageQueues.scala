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
 * Title: Understanding Streams and Message Queues
 *
 * Description: Demonstrate the two-way communication pattern: application uses
 * send()/receive(), protocol uses takeOutbound()/offerInbound().
 *
 * Run: sbt "mux-examples/runMain
 * mux.example2UnderstandingStreamsAndMessageQueues"
 */
@main def example2UnderstandingStreamsAndMessageQueues(): Unit = {
  println("=== Streams and Message Queues ===\n")

  val mux    = Mux[Int, String, String](100)
  val stream = mux.open(1) match {
    case s: MuxStream[Int, String, String] => s
    case _                                 => throw new RuntimeException("Failed to open stream")
  }

  println("--- Application sends a message ---")
  // Application code: send a message
  stream.send("Hello from app") match {
    case ()              => println("✓ Message sent successfully")
    case error: MuxError => println(s"✗ Send failed: $error")
  }

  println("\n--- Protocol extracts the message ---")
  // Protocol code: extract the message to send over network
  val outbound = stream.takeOutbound() match {
    case Some(msg)       => msg
    case None            => "(no message available)"
    case error: MuxError => s"error: $error"
  }
  println(s"Protocol will send: '$outbound'")

  println("\n--- Protocol delivers a response ---")
  // Protocol code: deliver a response from the peer
  stream.offerInbound("Hello from peer") match {
    case ()              => println("✓ Response delivered successfully")
    case error: MuxError => println(s"✗ Offer failed: $error")
  }

  println("\n--- Application receives the response ---")
  // Application code: receive the response
  val inbound = stream.receive() match {
    case Some(msg)       => msg
    case None            => "(no message available)"
    case error: MuxError => s"error: $error"
  }
  println(s"Application received: '$inbound'")

  println("\n=== Summary ===")
  println("Application uses send() and receive()")
  println("Protocol uses takeOutbound() and offerInbound()")
  println("Messages stay in their FIFO queues per stream")
}
