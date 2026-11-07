package muxoverasocket

import scala.annotation.tailrec

import zio.blocks.mux._

// Server side, stream 1: the chat room. Echoes every line back to the room.
def chatRoom(conn: MuxConnection, stream: MessageStream): Unit = {
  @tailrec def loop(): Unit =
    MuxConnection.next(stream) match {
      case Message.Text(line) =>
        conn.send(stream, Message.Text(s"[room] $line"))
        loop()
      case Message.End     => conn.finish(stream) // the client left: end our side too
      case error: MuxError => println(s"server | chat: $error")
    }
  loop()
}

// Server side, stream 5: the heartbeat. Answers every "ping n" with "pong n".
def heartbeat(conn: MuxConnection, stream: MessageStream): Unit = {
  @tailrec def loop(): Unit =
    MuxConnection.next(stream) match {
      case Message.Text(ping) =>
        conn.send(stream, Message.Text(ping.replace("ping", "pong")))
        loop()
      case Message.End     => conn.finish(stream)
      case error: MuxError => println(s"server | heartbeat: $error")
    }
  loop()
}

// Client side: open a stream from our end, or stop with the reason.
def openStream(conn: MuxConnection, id: Int): MessageStream =
  conn.open(id) match {
    case stream: MessageStream => stream
    case error: MuxError       => throw new IllegalStateException(s"cannot open stream $id: $error")
  }

// Client side: send one line and wait for the reply on the same stream.
def ask(conn: MuxConnection, stream: MessageStream, text: String): Message | MuxError =
  conn.send(stream, Message.Text(text)) match {
    case ()              => MuxConnection.next(stream)
    case error: MuxError => error
  }

def inThread(name: String)(body: => Unit): Thread = {
  val thread = new Thread(() => body, name)
  thread.start()
  thread
}

def chatSession(conn: MuxConnection, chat: MessageStream): Unit = {
  for (line <- Seq("alice: the build is green", "alice: shipping it")) {
    println(s"client | stream 1: ${ask(conn, chat, line)}")
    Thread.sleep(300)
  }
  conn.finish(chat)
  println(s"client | stream 1: ${MuxConnection.next(chat)}") // End: the server finished its side too
}

def heartbeatSession(conn: MuxConnection, heartbeat: MessageStream): Unit = {
  val roundTrips = (1 to 10).map { n =>
    val start  = System.nanoTime()
    val reply  = ask(conn, heartbeat, s"ping $n")
    val millis = (System.nanoTime() - start) / 1000000
    println(s"client | stream 5: $reply after $millis ms")
    Thread.sleep(200)
    millis
  }
  conn.finish(heartbeat)
  println(s"client | stream 5: ${MuxConnection.next(heartbeat)}; slowest round trip ${roundTrips.max} ms")
}
