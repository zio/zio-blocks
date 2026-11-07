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
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from mux-examples:  sbt "runMain muxoverasocket.CompleteExample"

package muxoverasocket

/**
 * How to Run a Mux over a Real Socket — Complete Example
 *
 * Runs the server and the client in one JVM over a localhost TCP connection on
 * a free port, and exits when both are done.
 */
object CompleteExample {

  def main(args: Array[String]): Unit = {
    val server       = listen(0)
    val serverThread = inThread("server") {
      try serve(server)
      finally server.close()
    }
    runClient(server.getLocalPort)
    serverThread.join()
  }
}
