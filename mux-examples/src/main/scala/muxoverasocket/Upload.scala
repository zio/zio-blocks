package muxoverasocket

import scala.annotation.tailrec

import zio.blocks.mux._

// Server side, stream 3: a file upload. The first line names the file; every
// later line is file content. Storing a line takes 1 ms, so this handler is
// slower than the client. When the client half-closes, reply with a summary
// on the same stream, then half-close our side.
def upload(conn: MuxConnection, stream: MessageStream): Unit = {
  @tailrec def store(fileName: String, lines: Int, bytes: Long): Unit =
    MuxConnection.next(stream) match {
      case Message.Text(header) if fileName.isEmpty =>
        val name = header.stripPrefix("upload ")
        conn.send(stream, Message.Text(s"ready for $name"))
        store(name, lines, bytes)
      case Message.Text(line) =>
        Thread.sleep(1) // a slow disk
        if ((lines + 1) % 500 == 0) println(s"server | upload: stored ${lines + 1} lines")
        store(fileName, lines + 1, bytes + line.length)
      case Message.End =>
        println("server | upload: the client finished sending; replying on the same stream")
        conn.send(stream, Message.Text(s"stored $fileName: $lines lines, $bytes bytes"))
        conn.finish(stream)
      case error: MuxError =>
        println(s"server | upload: $error")
    }
  store("", 0, 0L)
}

// Client side: send the file, half-close, and keep reading the reply.
def uploadSession(conn: MuxConnection, upload: MessageStream): Unit = {
  val lines = 1500
  val sent  = (1 to lines).takeWhile { n =>
    conn.send(upload, Message.Text(f"$n%04d: fixed a race in the release pipeline")) match {
      case ()              => true
      case error: MuxError =>
        println(s"client | stream 3: $error")
        false
    }
  }.size
  conn.finish(upload)
  println(s"client | stream 3: sent $sent lines, then End")
  println(s"client | stream 3: ${MuxConnection.next(upload)}") // the server's summary
  println(s"client | stream 3: ${MuxConnection.next(upload)}") // End
  println(s"client | stream 3: send after both sides finished: ${conn.send(upload, Message.Text("one more line"))}")
}
