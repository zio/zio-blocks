package muxoverasocket

import scala.annotation.tailrec

import zio.blocks.mux._

// Server side, any other stream: a job. Accepts each command, until the client
// ends the stream or cancels it.
def job(conn: MuxConnection, stream: MessageStream): Unit = {
  @tailrec def loop(): Unit =
    MuxConnection.next(stream) match {
      case Message.Text(command) =>
        println(s"server | job ${stream.id}: started '$command'")
        conn.send(stream, Message.Text(s"accepted $command"))
        loop()
      case Message.End     => conn.finish(stream)
      case error: MuxError => println(s"server | job ${stream.id}: $error")
    }
  loop()
}

// Client side: a fourth stream is over the server's limit of three, so the
// server refuses it with a RESET.
def refuseOverLimit(conn: MuxConnection): Unit = {
  val extra = openStream(conn, 7)
  println(s"client | stream 7: ${ask(conn, extra, "hello?")}")
}

// Client side: start a job, then cancel it on both ends.
def cancelJob(conn: MuxConnection): Unit = {
  val job = openStream(conn, 9)
  println(s"client | stream 9: ${ask(conn, job, "rebuild search index")}")
  conn.reset(9, "client cancelled the job")
  println(s"client | stream 9 after reset: ${job.receive()}")
}
