package muxoverasocket

import java.net.{InetSocketAddress, ServerSocket, Socket}

import scala.annotation.tailrec

// Port 0 picks any free port.
def listen(port: Int): ServerSocket = {
  val server = new ServerSocket()
  server.setReceiveBufferSize(4096) // small buffers make back-pressure show up within seconds
  server.setSoTimeout(60000)        // stop waiting for a client after a minute
  server.bind(new InetSocketAddress("localhost", port))
  server
}

// Serves one client connection: one handler thread per stream the client opens,
// at most three streams at a time. Returns when the client closes the connection.
def serve(server: ServerSocket): Unit = {
  println(s"server | listening on port ${server.getLocalPort}")
  val conn = new MuxConnection(server.accept(), "server", maxStreams = 3, acceptStreams = true)

  @tailrec def startHandlers(started: List[Thread]): List[Thread] =
    conn.accept() match {
      case Some(stream) =>
        println(s"server | client opened stream ${stream.id}")
        val handler = inThread(s"server-stream-${stream.id}") {
          stream.id match {
            case 1 => chatRoom(conn, stream)
            case 3 => upload(conn, stream)
            case 5 => heartbeat(conn, stream)
            case _ => job(conn, stream)
          }
        }
        startHandlers(handler :: started)
      case None => started // the client closed the connection
    }

  startHandlers(Nil).foreach(_.join())
  conn.close()
  println(s"server | connection closed; the reader paused ${conn.reader.pausedMillis.get} ms for full inbound queues")
}

def runClient(port: Int): Unit = {
  val socket = new Socket()
  socket.setSendBufferSize(4096) // small buffers make back-pressure show up within seconds
  socket.connect(new InetSocketAddress("localhost", port), 5000)
  val conn = new MuxConnection(socket, "client", maxStreams = 4, acceptStreams = false)

  // Three streams over the one connection, one request and reply on each.
  val chat      = openStream(conn, 1)
  val upload    = openStream(conn, 3)
  val heartbeat = openStream(conn, 5)
  println(s"client | stream 1: ${ask(conn, chat, "alice: hi all")}")
  println(s"client | stream 3: ${ask(conn, upload, "upload release-notes.txt")}")
  println(s"client | stream 5: ${ask(conn, heartbeat, "ping 0")}")

  refuseOverLimit(conn)

  // All three streams at once, one thread per stream.
  val sessions = Seq(
    inThread("client-chat")(chatSession(conn, chat)),
    inThread("client-upload")(uploadSession(conn, upload)),
    inThread("client-heartbeat")(heartbeatSession(conn, heartbeat))
  )
  sessions.foreach(_.join())
  println(s"client | active streams: ${conn.mux.activeCount}")

  cancelJob(conn)

  conn.close()
  println(s"client | connection closed; send waited ${conn.sendWaits.get} times for a full outbound queue")
}
