---
id: run-a-mux-over-a-socket
title: "How to Run a Mux over a Real Socket"
description: "Carry several independent streams over one TCP connection with a zio-blocks Mux: framing, reader and writer loops, half-close, back-pressure and shutdown."
keywords:
  - "Multiplexing"
  - "TCP Socket"
  - "Back-Pressure"
  - "Half-Close"
  - "Mux"
  - "MuxStream"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

In this guide, we run a ZIO Blocks [`Mux`](../reference/mux.mdx) over a real TCP connection. By the end, you will have a small chat server and client that carry three independent streams over one socket on `localhost`: a chat room, a file upload and a heartbeat. Their messages interleave on the wire and each one arrives at the right stream.

`Mux` keeps the streams apart, but it ships no transport. The protocol side pushes each received message into its stream with `MuxStream#offerInbound` and pulls each message to send with `MuxStream#takeOutbound`. That protocol side is what we write here, one piece at a time:

- Define a frame format that tags every message with its stream id
- Keep the bookkeeping `Mux` leaves to the protocol
- Drain every stream's outbound queue onto the socket from one writer loop
- Route incoming frames to their streams from one reader loop, opening a stream the first time its id appears
- Run several streams over the one connection at the same time
- Half-close a stream while the other direction keeps working
- Stop reading when a slow stream's queue is full, instead of dropping data or growing memory
- Refuse streams over the limit, cancel a stream with a reset, and shut the connection down cleanly

This guide assumes you have worked through [Getting Started with Mux](./getting-started-with-mux.md) and know what a TCP socket is.

## The Problem

One TCP connection is one ordered stream of bytes. A program that wants a chat, a file transfer and a heartbeat to share that connection has to cut the bytes into frames, tag each frame with the logical stream it belongs to, and route frames back to the right place on the other side, as HTTP/2 streams and SSH channels do. Without a library for the routing, every program writes its own stream table, and the parts that are easy to leave out are the ones that hurt in production: limits, flow control and stream state.

For example, this is a hand-written reader loop that routes frames to one queue per stream id:

```scala mdoc:compile-only
import java.io.DataInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}

// One queue per stream id, created the first time the id shows up.
val inboxes  = new ConcurrentHashMap[Int, LinkedBlockingQueue[String]]()
val finished = ConcurrentHashMap.newKeySet[Int]()

def readLoop(in: DataInputStream): Unit =
  while (true) {
    val id     = in.readInt()
    val kind   = in.readByte()
    val length = in.readInt()
    val bytes  = new Array[Byte](length)
    in.readFully(bytes)
    kind match {
      case 0 => inboxes.computeIfAbsent(id, _ => new LinkedBlockingQueue[String]()).put(new String(bytes, UTF_8))
      case 1 => finished.add(id) // the peer is done, but nothing stops our side from reading forever
      case _ => inboxes.remove(id) // reset: the thread reading that queue is never told
    }
  }
```

This loop works on a good day. A peer that opens a million stream ids gets a million queues, because nothing limits how many streams are open. A slow consumer makes its queue grow until the JVM runs out of memory, because the queues are unbounded. A frame that arrives after a reset quietly creates a new queue, because no stream remembers that it is closed. And the sending side, which is not even written yet, needs the same table again.

In this guide, we let `Mux` own the stream table: it limits how many streams are open, bounds every stream's queues, and tracks each stream's half-close state. We write only the glue between it and the socket.

## Prerequisites

Add the mux module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-mux" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-mux" % "@VERSION@"
```

The code in this guide uses only the JDK besides the mux module. It runs on the JVM: `Mux` is also published for Scala.js, but the examples use `java.net.Socket`, threads and blocking reads, which Scala.js does not have.

The code is written for Scala 3, because it uses the union types that `Mux#open`, `MuxStream#send`, `MuxStream#receive`, `MuxStream#offerInbound` and `MuxStream#takeOutbound` return on Scala 3, and the companion examples use top-level definitions. On Scala 2.13 the same five methods return `Either[MuxError, A]`, so each match changes shape. For example, delivering one message, as the reader loop below does:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec
import zio.blocks.mux._

@tailrec def deliver(stream: MuxStream[Int, String, String], message: String): Unit =
  stream.offerInbound(message) match {
    case Right(())                   => ()
    case Left(MuxError.QueueFull(_)) =>
      LockSupport.parkNanos(1000000L)
      deliver(stream, message)
    case Left(_) => () // closed or cancelled on our side: drop the frame
  }
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:compile-only
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec
import zio.blocks.mux._

@tailrec def deliver(stream: MuxStream[Int, String, String], message: String): Unit =
  stream.offerInbound(message) match {
    case ()                    => ()
    case MuxError.QueueFull(_) =>
      LockSupport.parkNanos(1000000L)
      deliver(stream, message)
    case _: MuxError => () // closed or cancelled on our side: drop the frame
  }
```

</TabItem>
</Tabs>

## How the Pieces Fit

Each side of the connection runs the same machinery. Application threads talk to their own `MuxStream`; two protocol threads move messages between the streams and the socket:

```text
 application threads            Mux (one per connection)              protocol threads          network
 -------------------            ------------------------              ----------------          -------
 chat thread    --send-------->  stream 1 outbound queue  --+
 upload thread  --send-------->  stream 3 outbound queue  --+--takeOutbound--> writer loop --frames--> socket
 heartbeat      --send-------->  stream 5 outbound queue  --+

 chat thread    <--receive----  stream 1 inbound queue   <--+
 upload thread  <--receive----  stream 3 inbound queue   <--+--offerInbound--- reader loop <--frames-- socket
 heartbeat      <--receive----  stream 5 inbound queue   <--+
```

The arrows also show the threading rule this design follows. `MuxStream#send` and `MuxStream#offerInbound` are safe to call from many threads at once. `MuxStream#receive` and `MuxStream#takeOutbound` are single-consumer: on each stream, only one thread at a time may call each of them. In this guide, one application thread per stream calls `MuxStream#receive`, and the writer loop is the only code that calls `MuxStream#takeOutbound`.

## Define a Frame Format

The socket carries bytes, so every message needs a header that says which stream it belongs to and how long it is. `Mux` doesn't care what the bytes look like; the frame format below is glue, and any format with a stream id and a length works.

Each frame is a 9-byte header followed by the payload: the stream id (4 bytes), the kind (1 byte: data, end or reset), the payload length (4 bytes), and then the payload as UTF-8 text. `Message` is what the application sends and receives on a stream; its `End` case is the in-band "I am done sending" marker that [Half-Close a Stream](#half-close-a-stream) explains:

```scala mdoc:silent
import java.io.{DataInputStream, DataOutputStream, EOFException, IOException}
import java.nio.charset.StandardCharsets.UTF_8

import zio.blocks.mux._

// What the application sends and receives on one stream. `End` is the
// in-band "I am done sending" marker; it travels the same FIFO as the data.
sealed trait Message
object Message {
  final case class Text(text: String) extends Message
  case object End                     extends Message
}

// Every stream in this guide carries `Message` in both directions.
type MessageStream = MuxStream[Int, Message, Message]

// One frame on the wire: stream id (4 bytes), kind (1 byte),
// payload length (4 bytes), payload (UTF-8 text).
sealed trait Frame { def streamId: Int }

object Frame {
  final case class Data(streamId: Int, text: String)    extends Frame
  final case class End(streamId: Int)                   extends Frame
  final case class Reset(streamId: Int, reason: String) extends Frame

  val MaxPayload: Int = 64 * 1024

  def write(out: DataOutputStream, frame: Frame): Unit = {
    val (kind, payload) = frame match {
      case Data(_, text)    => (0, text.getBytes(UTF_8))
      case End(_)           => (1, Array.emptyByteArray)
      case Reset(_, reason) => (2, reason.getBytes(UTF_8))
    }
    if (payload.length > MaxPayload) throw new IOException(s"payload of ${payload.length} bytes is too large")
    out.writeInt(frame.streamId)
    out.writeByte(kind)
    out.writeInt(payload.length)
    out.write(payload)
  }

  // Returns None when the peer closed the connection between two frames.
  def read(in: DataInputStream): Option[Frame] = {
    val streamId =
      try in.readInt()
      catch { case _: EOFException => return None }
    val kind   = in.readByte()
    val length = in.readInt()
    if (length < 0 || length > MaxPayload) throw new IOException(s"frame length $length is out of range")
    val payload = new Array[Byte](length)
    in.readFully(payload)
    kind match {
      case 0     => Some(Data(streamId, new String(payload, UTF_8)))
      case 1     => Some(End(streamId))
      case 2     => Some(Reset(streamId, new String(payload, UTF_8)))
      case other => throw new IOException(s"unknown frame kind $other")
    }
  }
}
```

`Frame.read` returns `None` when the connection ends cleanly between two frames, and throws when it ends in the middle of one. It also rejects a length over `Frame.MaxPayload` before allocating, so a corrupt header can't make the reader allocate gigabytes.

To check the format, we encode three frames into a byte array and open two readers on it, one of them on a copy cut off 7 bytes in, in the middle of the first frame:

```scala mdoc:silent
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, EOFException}

val bytes = new ByteArrayOutputStream()
val out   = new DataOutputStream(bytes)
Frame.write(out, Frame.Data(1, "hi"))
Frame.write(out, Frame.End(1))
Frame.write(out, Frame.Reset(3, "cancelled"))
out.flush()
val wire = bytes.toByteArray
val in   = new DataInputStream(new ByteArrayInputStream(wire))
val cut  = new DataInputStream(new ByteArrayInputStream(wire.take(7))) // a frame cut off after 7 bytes
```

Decoding the bytes gives the three frames back, then `None` at the clean end, and an `EOFException` for the cut frame:

```scala mdoc
println(s"first frame on the wire: ${wire.take(11).map(b => f"$b%02x").mkString(" ")}")
println(Frame.read(in))
println(Frame.read(in))
println(Frame.read(in))
println(Frame.read(in))
try println(Frame.read(cut))
catch { case e: EOFException => println(s"cut mid-frame: $e") }
```

The first frame is stream id `1`, kind `0` (data), length `2`, and the two bytes of `"hi"`.

## Keep the Bookkeeping the Mux Leaves to You

`Mux` tracks open streams, but a protocol needs three more things it doesn't keep. First, the writer loop has to visit every stream that may have messages to send, and `Mux` has no method that lists its streams: it offers only `Mux#get` by id and `Mux#activeCount`. Second, a stream leaves the `Mux` as soon as it is fully closed, while its outbound queue may still hold the last messages the application sent, so the writer has to keep its own reference until that queue is empty. Third, `Mux#open` accepts an id again once its stream has been closed or cancelled, so a frame that arrives late for a finished stream would open a new one unless the protocol remembers the ids it has used.

`StreamTable` holds those three things, plus a queue of control frames (resets) that belong to no stream's queue:

```scala mdoc:silent
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

// The bookkeeping a Mux leaves to the protocol:
//   draining - the streams the writer loop drains. A Mux can't list its
//              streams, and a closed stream leaves the Mux before its
//              outbound queue is empty.
//   usedIds  - every id this connection has used, so a late frame for a
//              finished stream can't reopen it.
//   control  - frames that belong to no stream's queue (RESET), waiting
//              for the writer loop.
final class StreamTable {
  val draining = new ConcurrentHashMap[Int, MessageStream]()
  val usedIds  = ConcurrentHashMap.newKeySet[Int]()
  val control  = new ConcurrentLinkedQueue[Frame]()
  val writer   = new AtomicReference[Thread]()

  def track(stream: MessageStream): Unit = {
    usedIds.add(stream.id)
    draining.put(stream.id, stream)
    wakeWriter()
  }

  def sendControl(frame: Frame): Unit = {
    control.add(frame)
    wakeWriter()
  }

  // The writer parks while it has nothing to write; wake it when there is.
  def wakeWriter(): Unit = {
    val thread = writer.get
    if (thread != null) LockSupport.unpark(thread)
  }
}
```

The writer loop parks while there is nothing to write. `StreamTable#wakeWriter` unparks it, so a new stream, a reset or a sent message reaches the socket without waiting for the next poll.

## Drain Outbound Queues onto the Socket

The writer loop is the only code that calls `MuxStream#takeOutbound`, and it runs on one thread, so every stream's outbound queue has exactly one consumer. Each pass sends pending control frames first, then takes up to 16 messages from each stream, encodes them and flushes the socket once:

```scala mdoc:silent
import java.io.{DataOutputStream, IOException}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

import zio.blocks.mux._

// The only code that calls `takeOutbound`, on one thread, so every stream's
// outbound queue has exactly one consumer.
final class WriterLoop(out: DataOutputStream, table: StreamTable, running: AtomicBoolean) extends Runnable {
  // At most this many messages per stream per pass, so one busy stream
  // can't starve the others.
  private val Batch = 16

  def run(): Unit = {
    table.writer.set(Thread.currentThread())
    try loop()
    catch {
      // The connection is gone. Closing the stream closes the socket, which
      // ends the reader loop, which closes every stream in the Mux.
      case _: IOException =>
        running.set(false)
        try out.close()
        catch { case _: IOException => () }
    }
  }

  // After `running` turns false, keep going until a pass writes nothing, so
  // messages queued before the shutdown still reach the peer.
  @tailrec private def loop(): Unit = {
    val controlFrames = writeControlFrames(0)
    val messages      = table.draining.values.asScala.foldLeft(0)((sum, stream) => sum + drain(stream, 0))
    val wrote         = controlFrames + messages > 0
    if (wrote) out.flush()
    else if (running.get) LockSupport.parkNanos(10_000_000L) // idle: wait up to 10 ms or until woken
    if (running.get || wrote) loop()
  }

  @tailrec private def writeControlFrames(written: Int): Int =
    table.control.poll() match {
      case null  => written
      case frame =>
        Frame.write(out, frame)
        frame match {
          case Frame.Reset(id, _) => table.draining.remove(id) // drop whatever the reset stream still had queued
          case _                  => ()
        }
        writeControlFrames(written + 1)
    }

  @tailrec private def drain(stream: MessageStream, written: Int): Int =
    if (written == Batch) written
    else
      stream.takeOutbound() match {
        case Some(Message.Text(text)) =>
          Frame.write(out, Frame.Data(stream.id, text))
          drain(stream, written + 1)
        case Some(Message.End) =>
          Frame.write(out, Frame.End(stream.id))
          drain(stream, written + 1)
        case None =>
          written
        case _: MuxError => // closed, and its queue is empty: nothing more will come
          table.draining.remove(stream.id)
          written
      }
}
```

Three details matter here. The batch limit keeps one busy stream from starving the others: the large file upload later in this guide gets 16 frames per pass, then the chat and heartbeat streams get their turn. A stream whose `MuxStream#takeOutbound` returns an error is closed and has an empty queue, so the loop forgets it. And after shutdown begins, the loop keeps going until a pass writes nothing, so the last messages the application sent still reach the peer.

:::warning
Never call `MuxStream#takeOutbound` (or `MuxStream#receive`) on one stream from two threads at the same time. The queues are single-consumer ring buffers, and nothing detects the mistake. In a test with two threads draining one stream while a third sent 200,000 numbered messages, the drainers took some messages twice and lost others, and then the queue stopped accepting messages for good, so the sender stalled long before it had sent them all. One writer loop per connection avoids the problem by construction.
:::

## Route Incoming Frames to Their Streams

The reader loop reads one frame at a time and hands it to its stream. It is the only code that calls `MuxStream#offerInbound` and `MuxStream#signalRemoteClose`:

```scala mdoc:silent
import java.io.{DataInputStream, IOException}
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec

import zio.blocks.mux._

// Reads frames off the socket and hands each one to its stream. It is the
// only code that calls `offerInbound` and `signalRemoteClose`.
final class ReaderLoop(
  in: DataInputStream,
  mux: Mux[Int, Message, Message],
  table: StreamTable,
  incoming: BlockingQueue[MessageStream],
  acceptStreams: Boolean,
  running: AtomicBoolean
) extends Runnable {
  // How long this loop stopped reading because a stream's inbound queue was full.
  val pausedMillis = new AtomicLong(0L)

  def run(): Unit =
    try loop()
    catch {
      case _: IOException => () // the connection broke, or we closed the socket ourselves
    } finally {
      running.set(false)
      mux.closeAll(MuxError.Cancelled("connection", "connection closed"))
      table.wakeWriter()
    }

  @tailrec private def loop(): Unit =
    Frame.read(in) match {
      case Some(frame) =>
        handle(frame)
        loop()
      case None => () // the peer closed the connection
    }

  private def handle(frame: Frame): Unit = frame match {
    case Frame.Data(id, text) =>
      streamFor(id).foreach(deliver(_, Message.Text(text)))
    case Frame.End(id) =>
      streamFor(id).foreach { stream =>
        deliver(stream, Message.End) // tell the application, in order, after the data
        stream.signalRemoteClose()   // and tell the Mux: the peer sends nothing more
      }
    case Frame.Reset(id, reason) =>
      mux.cancel(id, MuxError.Cancelled(id, reason))
      table.draining.remove(id)
  }

  // The open stream for `id`, or a new one the first time the peer uses `id`.
  private def streamFor(id: Int): Option[MessageStream] =
    mux.get(id) match {
      case found @ Some(_)                                      => found
      case None if !acceptStreams || table.usedIds.contains(id) => None // a late frame for a finished stream: drop it
      case None                                                 =>
        mux.open(id) match {
          case stream: MessageStream =>
            table.track(stream)
            incoming.put(stream)
            Some(stream)
          case error: MuxError => // CapacityExceeded: refuse the stream
            table.usedIds.add(id)
            table.sendControl(Frame.Reset(id, s"refused: $error"))
            None
        }
    }

  // Back-pressure: while the application is behind, stop reading the socket.
  // Nothing is dropped and memory stays bounded, but every stream on this
  // connection waits for the slow one.
  @tailrec private def deliver(stream: MessageStream, message: Message): Unit =
    stream.offerInbound(message) match {
      case ()                    => ()
      case MuxError.QueueFull(_) =>
        pausedMillis.incrementAndGet()
        LockSupport.parkNanos(1_000_000L)
        deliver(stream, message)
      case _: MuxError => () // closed or cancelled on our side: drop the frame
    }
}
```

`ReaderLoop#streamFor` opens a stream the first time the peer uses an id, but only on the side that accepts streams (the server here) and only for an id this connection has never used. When `Mux#open` returns `CapacityExceeded`, which happens when `Mux#activeCount` has reached the capacity given to `Mux.apply`, the loop refuses the stream with a `Frame.Reset`, as HTTP/2 refuses streams over its limit.

An end frame becomes two calls, in this order: `ReaderLoop#deliver` puts `Message.End` into the inbound queue behind the data, and then `MuxStream#signalRemoteClose` tells the `Mux` that the peer sends nothing more. The order matters, because after `MuxStream#signalRemoteClose` the stream's `MuxStream#offerInbound` returns `StreamClosed`. A reset frame becomes `Mux#cancel` with a `MuxError.Cancelled` reason, which the application sees from `MuxStream#receive` once it has read the messages already queued.

When the connection ends, for any reason, the `finally` block calls `Mux#closeAll`, so every application thread waiting on a stream sees an error instead of waiting forever.

:::note
Only the client opens streams in this guide. If both sides of your protocol open streams, split the ids between them so that the two can never pick the same one: HTTP/2 gives clients the odd ids and servers the even ids.
:::

## Run Both Loops on One Connection

`MuxConnection` puts the pieces together for one socket: it creates the `Mux`, starts the reader and writer loops on their own threads, and gives the application a small API over them:

```scala mdoc:silent
import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutputStream}
import java.net.Socket
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec

import zio.blocks.mux._

// One TCP connection carrying many streams: a Mux, a reader thread and a
// writer thread. `acceptStreams` is true on the side that lets the peer
// open streams (the server).
final class MuxConnection(socket: Socket, name: String, maxStreams: Int, acceptStreams: Boolean) {
  val mux: Mux[Int, Message, Message] = Mux[Int, Message, Message](maxStreams)

  private val table    = new StreamTable
  private val running  = new AtomicBoolean(true)
  private val incoming = new LinkedBlockingQueue[MessageStream]()
  private val in       = new DataInputStream(new BufferedInputStream(socket.getInputStream))
  private val out      = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))

  val reader: ReaderLoop = new ReaderLoop(in, mux, table, incoming, acceptStreams, running)
  // How many times `send` found the outbound queue full and waited.
  val sendWaits = new AtomicLong(0L)

  private val readerThread = new Thread(reader, s"$name-reader")
  private val writerThread = new Thread(new WriterLoop(out, table, running), s"$name-writer")
  readerThread.start()
  writerThread.start()

  // Opens a stream from this side.
  def open(id: Int): MessageStream | MuxError =
    mux.open(id) match {
      case stream: MessageStream =>
        table.track(stream)
        stream
      case error: MuxError => error
    }

  // The next stream the peer opened, or None once the connection is closed.
  @tailrec def accept(): Option[MessageStream] = {
    val stream = incoming.poll(10, TimeUnit.MILLISECONDS)
    if (stream != null) Some(stream)
    else if (!running.get && incoming.isEmpty) None
    else accept()
  }

  // Waits while the outbound queue is full instead of dropping the message.
  @tailrec def send(stream: MessageStream, message: Message): Unit | MuxError =
    stream.send(message) match {
      case MuxError.QueueFull(_) =>
        sendWaits.incrementAndGet()
        LockSupport.parkNanos(1_000_000L)
        send(stream, message)
      case result =>
        table.wakeWriter()
        result
    }

  // Half-close: queue End behind the data already sent, then stop sending.
  // Call it from the thread that sends on this stream.
  def finish(stream: MessageStream): Unit | MuxError =
    send(stream, Message.End) match {
      case ()              => stream.halfClose()
      case error: MuxError => error
    }

  // Cancels a stream on both ends: drop it here, then send RESET to the peer.
  def reset(id: Int, reason: String): Unit = {
    mux.cancel(id, MuxError.Cancelled(id, reason))
    table.sendControl(Frame.Reset(id, reason))
  }

  // Clean shutdown: fail every stream, let the writer flush what was already
  // queued, then close the socket, which ends the reader loop.
  def close(): Unit = {
    mux.closeAll(MuxError.Cancelled("connection", s"$name closed the connection"))
    running.set(false)
    table.wakeWriter()
    writerThread.join(5000)
    socket.close()
    readerThread.join(5000)
  }

  // Blocks until the peer closes the connection.
  def awaitPeerClose(): Unit = readerThread.join()
}

object MuxConnection {
  // `receive` never blocks: poll it, parking for 1 ms while the queue is empty.
  @tailrec def next(stream: MessageStream): Message | MuxError =
    stream.receive() match {
      case Some(message) => message
      case None          =>
        LockSupport.parkNanos(1_000_000L)
        next(stream)
      case error: MuxError => error
    }
}
```

`MuxConnection#send` retries while the stream's outbound queue is full, and `MuxConnection#finish` and `MuxConnection#reset` are covered in their own sections below. `MuxConnection.next` waits for the next inbound message: `MuxStream#receive` never blocks, it returns `None` when the queue is empty, so the loop polls and parks for a millisecond between tries.

The loops run on plain JDK threads, not on `Async.start` from the async module. Both loops block for long stretches (the reader in a socket read, the writer in a socket write when the peer is slow), and `Async.start` runs its body on the JVM's shared `ForkJoinPool.commonPool()`, where a handful of blocked loops can use up every worker on a machine with few cores. A thread per loop is also what a socket's blocking API expects. For the same reason, the loops read and write through `DataInputStream` and `DataOutputStream` rather than a zio-blocks `Stream`: a frame decoder needs to read exactly 4, 1, 4 and `length` bytes at a time, which `DataInputStream#readFully` does directly.

## Serve Several Streams over One Connection

With `MuxConnection` in place, the application code only sends and receives. On the server, each stream gets a handler that runs on its own thread. The chat room echoes every line back, and the heartbeat answers every ping:

```scala mdoc:silent
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
```

The client side of the same file opens streams with `MuxConnection#open`, sends a line and waits for its reply with `ask`, and runs one session per stream on its own thread. Each session thread is the only thread that calls `MuxStream#receive` on its stream, which keeps the single-consumer rule.

The client opens streams 1, 3 and 5, asks one question on each, and then runs the chat, upload and heartbeat sessions at once (the complete client is in [Shut Down Cleanly](#shut-down-cleanly)). The output of one run shows messages from the three streams interleaved on the one connection, each reply arriving on the stream that asked for it:

```text
server | listening on port 38335
server | client opened stream 1
client | stream 1: Text([room] alice: hi all)
server | client opened stream 3
client | stream 3: Text(ready for release-notes.txt)
server | client opened stream 5
client | stream 5: Text(pong 0)
client | stream 7: Cancelled(7,refused: CapacityExceeded(3))
client | stream 1: Text([room] alice: the build is green)
client | stream 5: Text(pong 1) after 2 ms
client | stream 5: Text(pong 2) after 302 ms
server | upload: stored 500 lines
client | stream 1: Text([room] alice: shipping it)
client | stream 3: sent 1500 lines, then End
client | stream 5: Text(pong 3) after 311 ms
server | upload: stored 1000 lines
client | stream 1: End
client | stream 5: Text(pong 4) after 108 ms
client | stream 5: Text(pong 5) after 2 ms
server | upload: stored 1500 lines
server | upload: the client finished sending; replying on the same stream
client | stream 3: Text(stored release-notes.txt: 1500 lines, 63000 bytes)
client | stream 3: End
client | stream 3: send after both sides finished: StreamClosed(3)
client | stream 5: Text(pong 6) after 1 ms
client | stream 5: Text(pong 7) after 1 ms
client | stream 5: Text(pong 8) after 1 ms
client | stream 5: Text(pong 9) after 1 ms
client | stream 5: Text(pong 10) after 2 ms
client | stream 5: End; slowest round trip 311 ms
```

The thread scheduling decides the exact order, so each run interleaves the lines differently.

## Half-Close a Stream

Half-closing a stream says "I am done sending" while the other direction stays open, so the peer can still answer. The upload uses it: the client sends the file, half-closes, and then reads the server's summary on the same stream.

`MuxStream#halfClose` changes what the stream allows, and so does `MuxStream#signalRemoteClose`. To see what each operation returns in each state, we open two streams on a `Mux` of their own, with no socket involved:

```scala mdoc:silent
import zio.blocks.mux._

val mux = Mux[Int, Message, Message](4)

def open(id: Int): MessageStream = mux.open(id) match {
  case stream: MessageStream => stream
  case error: MuxError       => throw new IllegalStateException(error.toString)
}

val transfer = open(3)
val room     = open(1)
```

The first stream half-closes its own side and then hears that the peer has finished too; the second hears from the peer first:

```scala mdoc
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
```

After `MuxStream#halfClose`, `MuxStream#send` fails with `StreamClosed`, while inbound messages are still accepted and received. Once both sides are done, the stream is closed and gone from the `Mux`, which frees its slot for a new stream, and `MuxStream#receive` returns the terminal error after the queue drains.

The second stream shows why this guide sends `Message.End` in-band. When the peer finishes first, `MuxStream#receive` returns `None` once the queue is empty, exactly as it does when the next message has not arrived yet, and `MuxStream#isHalfClosed` is `true` whichever side finished. Nothing on the stream tells the application that the peer is done, so the reader loop delivers `Message.End` as the last inbound message before it calls `MuxStream#signalRemoteClose`. On the sending side, `MuxConnection#finish` sends `Message.End` through the same outbound queue as the data, so it can't overtake the data, and only then calls `MuxStream#halfClose`.

Over the socket, the server's upload handler stores lines until it receives `Message.End`, then replies with a summary and finishes its own side. The client sends the file, finishes, and keeps receiving on the same stream:

```scala mdoc:silent
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
```

The upload's lines from the run above show the reply arriving after the client's half-close, and `StreamClosed` once both sides are done:

```text
server | upload: stored 500 lines
client | stream 3: sent 1500 lines, then End
server | upload: stored 1000 lines
server | upload: stored 1500 lines
server | upload: the client finished sending; replying on the same stream
client | stream 3: Text(stored release-notes.txt: 1500 lines, 63000 bytes)
client | stream 3: End
client | stream 3: send after both sides finished: StreamClosed(3)
```

## Handle Back-Pressure Without Dropping Data

Every stream's queues are bounded: each direction holds 256 messages, a limit fixed by the library. When a queue is full, `MuxStream#send` and `MuxStream#offerInbound` return `MuxError.QueueFull` instead of adding the message. To see where that starts, we open one stream with no socket involved:

```scala mdoc:silent
import zio.blocks.mux._

val queueMux   = Mux[Int, Message, Message](1)
val slowStream = queueMux.open(3) match {
  case stream: MessageStream => stream
  case error: MuxError       => throw new IllegalStateException(error.toString)
}
```

Then we fill its inbound queue, try one message more, and receive one to make room:

```scala mdoc
println(s"accepted: ${(1 to 256).count(n => slowStream.offerInbound(Message.Text(s"line $n")) == ())} of 256")
println(s"offerInbound #257: ${slowStream.offerInbound(Message.Text("line 257"))}")
println(s"receive: ${slowStream.receive()}")
println(s"offerInbound #257 again: ${slowStream.offerInbound(Message.Text("line 257"))}")
```

Over a socket, the inbound queue fills when the application reads a stream more slowly than the peer sends. The upload handler above stores one line per millisecond, while the client sends 1,500 lines as fast as it can. The reader loop then gets `QueueFull` from `MuxStream#offerInbound`, and it has to do something with the frame it is holding. These are the options:

| Strategy                        | Data | Memory    | Other streams on the connection          |
| ------------------------------- | ---- | --------- | ---------------------------------------- |
| Drop the frame                  | Lost | Bounded   | Unaffected                               |
| Buffer it outside the `Mux`     | Kept | Unbounded | Unaffected                               |
| Stop reading until there's room | Kept | Bounded   | Wait behind the slow stream              |
| Per-stream flow control         | Kept | Bounded   | Unaffected, at the cost of more protocol |

`ReaderLoop#deliver` stops reading: it parks for a millisecond and retries the same frame until the queue has room. While it waits, it reads nothing from the socket, so the kernel's receive buffer fills, TCP tells the client to stop sending, the client's writer loop blocks in its socket write, the client's outbound queues fill, and `MuxConnection#send` on the client starts to wait on `QueueFull`. The pressure travels all the way back to the code that produces the data, and no queue grows past its bound.

The price is head-of-line blocking: while the reader waits for the upload, it can't deliver the heartbeat's pings either. The run above shows both effects. The heartbeat's round trips grow from a few milliseconds to a few hundred while the upload is in flight, and drop back once it ends; the closing lines count how long each side waited:

```text
client | stream 5: Text(pong 1) after 2 ms
client | stream 5: Text(pong 2) after 302 ms
client | stream 5: Text(pong 3) after 311 ms
client | stream 5: Text(pong 4) after 108 ms
client | stream 5: Text(pong 5) after 2 ms
client | stream 5: Text(pong 6) after 1 ms
client | stream 5: Text(pong 7) after 1 ms
client | stream 5: Text(pong 8) after 1 ms
client | stream 5: Text(pong 9) after 1 ms
client | stream 5: Text(pong 10) after 2 ms
client | stream 5: End; slowest round trip 311 ms
client | connection closed; send waited 645 times for a full outbound queue
server | connection closed; the reader paused 1226 ms for full inbound queues
```

The examples shrink the socket buffers to 4 KiB (`ServerSocket#setReceiveBufferSize` and `Socket#setSendBufferSize`) so that the effect shows up within seconds; with the default buffers the same thing happens, only later.

If one slow stream must not hold up the others, the fix is per-stream flow control, as HTTP/2 does with its flow-control windows: the receiver grants each stream a credit of messages, the sender's writer loop skips a stream that has used its credit, and the receiver grants more credit as the application calls `MuxStream#receive`. It is more protocol than this guide needs, but it plugs into the same two places: the writer loop decides which streams it may drain, and the reader loop never meets a full queue.

## Refuse and Cancel Streams

Two things end a stream abruptly: the server refusing it, and either side cancelling it. Both travel as a reset frame and arrive as `MuxError.Cancelled`. The server's job handler runs any stream that isn't the chat, upload or heartbeat, and the client has one function for each case:

```scala mdoc:silent
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
```

`refuseOverLimit` opens a fourth stream while the first three are still open. The client's own `Mux` accepts it, because the client allows four streams, but the server's allows three, so the server's `Mux#open` returns `CapacityExceeded(3)` and the reader loop answers with a reset. `cancelJob` starts a job and calls `MuxConnection#reset`, which cancels the stream locally with `Mux#cancel` and sends a reset frame, so the server's handler sees the same reason:

```text
client | stream 7: Cancelled(7,refused: CapacityExceeded(3))
server | client opened stream 9
server | job 9: started 'rebuild search index'
client | stream 9: Text(accepted rebuild search index)
client | stream 9 after reset: Cancelled(9,client cancelled the job)
server | job 9: Cancelled(9,client cancelled the job)
```

The cancelled side's `MuxStream#receive` returns the reason passed to `Mux#cancel`, which is why both sides print the same `Cancelled`. `MuxStream#takeOutbound` on a cancelled stream returns `StreamClosed` instead, once its queue is empty; `MuxConnection#reset` doesn't wait for that, because the writer drops the stream as soon as it sends the reset frame.

## Shut Down Cleanly

The last piece is the program around the streams. The server listens, serves one client connection, routes each new stream to its handler, and returns when the client closes the connection. The client connects, runs every session from the previous sections, and closes the connection:

```scala mdoc:silent
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
```

`MuxConnection#close` shuts down in a fixed order. `Mux#closeAll` first fails every stream, so no new message can be queued and every waiting thread wakes up with an error. Then the writer loop flushes what was already queued and exits, and closing the socket ends the reader loop. On the server, the reader loop sees the client's close as the end of the stream, calls `Mux#closeAll` itself, and `MuxConnection#accept` returns `None`, so `serve` joins the handler threads and closes its side. Nothing is left running: both programs exit on their own, and the port is free again.

Each side prints a last line on the way out:

```text
client | connection closed; send waited 645 times for a full outbound queue
server | connection closed; the reader paused 1226 ms for full inbound queues
```

## Putting It Together

The complete example runs the server and the client from the previous sections in one JVM: the server on a background thread and the client on the main thread, over a localhost connection. It differs from the separate `ChatServer` and `ChatClient` programs in one way: the server listens on port 0, so the operating system picks a free port, and the client connects to that port:

```scala mdoc:embed:mux-examples/src/main/scala/muxoverasocket/CompleteExample.scala:show-line-numbers
```

Running it prints the whole session. The numbers and the interleaving of the middle lines change from run to run:

```text
server | listening on port 38335
server | client opened stream 1
client | stream 1: Text([room] alice: hi all)
server | client opened stream 3
client | stream 3: Text(ready for release-notes.txt)
server | client opened stream 5
client | stream 5: Text(pong 0)
client | stream 7: Cancelled(7,refused: CapacityExceeded(3))
client | stream 1: Text([room] alice: the build is green)
client | stream 5: Text(pong 1) after 2 ms
client | stream 5: Text(pong 2) after 302 ms
server | upload: stored 500 lines
client | stream 1: Text([room] alice: shipping it)
client | stream 3: sent 1500 lines, then End
client | stream 5: Text(pong 3) after 311 ms
server | upload: stored 1000 lines
client | stream 1: End
client | stream 5: Text(pong 4) after 108 ms
client | stream 5: Text(pong 5) after 2 ms
server | upload: stored 1500 lines
server | upload: the client finished sending; replying on the same stream
client | stream 3: Text(stored release-notes.txt: 1500 lines, 63000 bytes)
client | stream 3: End
client | stream 3: send after both sides finished: StreamClosed(3)
client | stream 5: Text(pong 6) after 1 ms
client | stream 5: Text(pong 7) after 1 ms
client | stream 5: Text(pong 8) after 1 ms
client | stream 5: Text(pong 9) after 1 ms
client | stream 5: Text(pong 10) after 2 ms
client | stream 5: End; slowest round trip 311 ms
client | active streams: 0
server | client opened stream 9
server | job 9: started 'rebuild search index'
client | stream 9: Text(accepted rebuild search index)
client | stream 9 after reset: Cancelled(9,client cancelled the job)
server | job 9: Cancelled(9,client cancelled the job)
client | connection closed; send waited 645 times for a full outbound queue
server | connection closed; the reader paused 1226 ms for full inbound queues
```

## Running the Examples

All runnable code from this guide is in [`mux-examples/src/main/scala/muxoverasocket/`](https://github.com/zio/zio-blocks/tree/main/mux-examples/src/main/scala/muxoverasocket). Run any file from that folder with `scala-cli run <File>.scala`, or from `mux-examples` with `sbt "runMain muxoverasocket.<ObjectName>"`. To run the server and the client as two processes, start `ChatServer` with a port (`sbt "runMain muxoverasocket.ChatServer 7070"`), then `ChatClient` with the same port in a second terminal; the server serves one client and exits.

## Going Further

- The [`Mux` reference](../reference/mux.mdx) lists every operation, error and state transition.
- [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) moves bytes over a socket with zio-blocks streams, for a single stream of data per connection.
- To serve many clients, accept connections in a loop and give each one its own `MuxConnection`; each connection has its own `Mux`, so stream ids and limits are per connection.
- To serve many streams per connection without a thread per stream, replace the per-stream handler threads with one application thread that polls `MuxStream#receive` on every stream it owns, keeping each stream's `MuxStream#receive` on that one thread.
