//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-mux:0.0.56
//> using file Frame.scala
//> using file StreamTable.scala
//> using file WriterLoop.scala
//> using file ReaderLoop.scala
//> using file MuxConnection.scala
//> using file ChatStreams.scala
//> using file Upload.scala
//> using file Jobs.scala
//> using file ServerAndClient.scala
// Start ChatServer first, then run from this folder:  scala-cli run ChatClient.scala -- 7070
// Or from mux-examples:                               sbt "runMain muxoverasocket.ChatClient 7070"

package muxoverasocket

/**
 * How to Run a Mux over a Real Socket — the client
 *
 * Connects to ChatServer on a fixed port, runs the client session, and exits.
 */
object ChatClient {

  def main(args: Array[String]): Unit =
    runClient(args.headOption.map(_.toInt).getOrElse(7070))
}
