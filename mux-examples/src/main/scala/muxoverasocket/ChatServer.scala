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
// Run from this folder:  scala-cli run ChatServer.scala -- 7070
// Or from mux-examples:  sbt "runMain muxoverasocket.ChatServer 7070"
// Then start ChatClient with the same port. The server serves one client and exits.

package muxoverasocket

/**
 * How to Run a Mux over a Real Socket — the server
 *
 * Listens on a fixed port, serves one client connection, and exits.
 */
object ChatServer {

  def main(args: Array[String]): Unit = {
    val server = listen(args.headOption.map(_.toInt).getOrElse(7070))
    try serve(server)
    finally server.close()
  }
}
