//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-mux:0.0.56
//> using file Frame.scala
// Run from this folder:  scala-cli run Step11QueueFullExample.scala
// Or from mux-examples:  sbt "runMain muxoverasocket.Step11QueueFullExample"

package muxoverasocket

import zio.blocks.mux._

/**
 * How to Run a Mux over a Real Socket — Handle Back-Pressure Without Dropping
 * Data
 *
 * Fills one stream's inbound queue without a socket, to show where QueueFull
 * starts and that one receive makes room again.
 */
object Step11QueueFullExample {

  def main(args: Array[String]): Unit = {
    val queueMux   = Mux[Int, Message, Message](1)
    val slowStream = queueMux.open(3) match {
      case stream: MessageStream => stream
      case error: MuxError       => throw new IllegalStateException(error.toString)
    }

    println(s"accepted: ${(1 to 256).count(n => slowStream.offerInbound(Message.Text(s"line $n")) == ())} of 256")
    println(s"offerInbound #257: ${slowStream.offerInbound(Message.Text("line 257"))}")
    println(s"receive: ${slowStream.receive()}")
    println(s"offerInbound #257 again: ${slowStream.offerInbound(Message.Text("line 257"))}")
  }
}
