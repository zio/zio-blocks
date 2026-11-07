//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-mux:0.0.56
//> using file Frame.scala
// Run from this folder:  scala-cli run Step10HalfCloseStatesExample.scala
// Or from mux-examples:  sbt "runMain muxoverasocket.Step10HalfCloseStatesExample"

package muxoverasocket

import zio.blocks.mux._

/**
 * How to Run a Mux over a Real Socket — Half-Close a Stream
 *
 * Walks one stream through halfClose and signalRemoteClose without a socket,
 * printing what each operation returns in each state.
 */
object Step10HalfCloseStatesExample {

  def main(args: Array[String]): Unit = {
    val mux = Mux[Int, Message, Message](4)

    def open(id: Int): MessageStream = mux.open(id) match {
      case stream: MessageStream => stream
      case error: MuxError       => throw new IllegalStateException(error.toString)
    }

    val transfer = open(3)
    val room     = open(1)

    transfer.halfClose() // we finished sending
    println(s"after halfClose: send = ${transfer.send(Message.Text("more"))}")
    println(s"after halfClose: offerInbound = ${transfer.offerInbound(Message.Text("summary"))}")
    println(s"after halfClose: receive = ${transfer.receive()}")
    transfer.signalRemoteClose() // the peer finished too
    println(s"after both: isClosed = ${transfer.isClosed}, mux.get(3) = ${mux.get(3)}")
    println(s"after both: receive = ${transfer.receive()}")

    room.signalRemoteClose() // the peer finished first
    println(s"peer finished first: receive = ${room.receive()}, isHalfClosed = ${room.isHalfClosed}")
    println(s"peer finished first: offerInbound = ${room.offerInbound(Message.Text("late"))}")
    println(s"peer finished first: send = ${room.send(Message.Text("still talking"))}")
  }
}
