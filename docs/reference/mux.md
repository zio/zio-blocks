---
id: mux
title: "Mux"
---

`Mux[Id, In, Out]` is a zero-dependency, cross-platform multiplexed stream coordinator. It manages many independent streams over one shared transport, where each stream is keyed by an `Id`.

Use it for:
- HTTP/2 stream multiplexing
- WebSocket subprotocols
- Any ID-keyed protocol that needs independent stream lifecycles

Key properties:
- Thread-safe registry operations and multi-producer stream writes
- Lock-free on JVM for queue operations
- Virtual-thread-friendly
- JVM uses ring buffers for stream queues

## Overview

`Mux` owns the stream registry. Protocol code uses `offerInbound` and `takeOutbound`, while application code uses `send` and `receive`.

```
User code → send(In) → outbound queue → Protocol reads via takeOutbound()
Protocol  → offerInbound(Out) → inbound queue → User code reads via receive()
```

Each stream has separate inbound and outbound queues, so traffic for one ID stays isolated from the others. Half-close maps cleanly to RFC 9113 stream states, which makes the API a good fit for HTTP/2-style protocols.

## API

The `Mux` API is cross-compiled: Scala 3 uses zero-cost union return types, while Scala 2 uses `Either`.

### Scala 3

```scala
trait Mux[Id, In, Out] {
  def open(id: Id): MuxStream[Id, In, Out] | MuxError
  def get(id: Id): Option[MuxStream[Id, In, Out]]
  def cancel(id: Id, reason: MuxError): Unit
  def closeAll(reason: MuxError): Unit
  def activeCount: Int
}

trait MuxStream[Id, In, Out] {
  def id: Id
  def send(msg: In): Unit | MuxError
  def receive(): Option[Out] | MuxError
  def offerInbound(msg: Out): Unit | MuxError
  def takeOutbound(): Option[In] | MuxError
  def halfClose(): Unit
  def signalRemoteClose(): Unit
  def isClosed: Boolean
  def isHalfClosed: Boolean
  def close(): Unit
}

sealed trait MuxError
```

### Scala 2

```scala
trait Mux[Id, In, Out] {
  def open(id: Id): Either[MuxError, MuxStream[Id, In, Out]]
  def get(id: Id): Option[MuxStream[Id, In, Out]]
  def cancel(id: Id, reason: MuxError): Unit
  def closeAll(reason: MuxError): Unit
  def activeCount: Int
}

trait MuxStream[Id, In, Out] {
  def id: Id
  def send(msg: In): Either[MuxError, Unit]
  def receive(): Either[MuxError, Option[Out]]
  def offerInbound(msg: Out): Either[MuxError, Unit]
  def takeOutbound(): Either[MuxError, Option[In]]
  def halfClose(): Unit
  def signalRemoteClose(): Unit
  def isClosed: Boolean
  def isHalfClosed: Boolean
  def close(): Unit
}
```

The semantics are the same on both versions; only the surface return types differ.

### Factory

```scala
object Mux {
  def apply[Id, In, Out](capacity: Int): Mux[Id, In, Out]
}
```

### Core operations

- `open(id)` opens a new stream
- `get(id)` looks up an active stream
- `cancel(id, reason)` closes one stream with an error
- `closeAll(reason)` closes every active stream
- `activeCount` reports how many streams are open

### Per-stream operations

- `send(msg)` queues outbound data for the protocol layer
- `receive()` reads inbound data for user code
- `offerInbound(msg)` delivers data from the protocol layer
- `takeOutbound()` drains outbound data for the protocol layer
- `halfClose()` marks local send as finished
- `signalRemoteClose()` marks remote send as finished
- `close()` fully closes the stream immediately; buffered inbound messages can still be drained before the terminal error is observed

### `MuxError`

Error cases:

- `MuxError.StreamClosed(id)`
- `MuxError.CapacityExceeded(limit)` — maximum concurrent streams reached
- `MuxError.QueueFull(queueCapacity)` — per-stream message queue is full (backpressure)
- `MuxError.Cancelled(id, reason)`
- `MuxError.MuxClosed`
- `MuxError.ProtocolError(message)` — e.g., null message, invalid state transition

## Examples

### Basic usage

Scala 3:

```scala
import zio.blocks.mux.*

val mux = Mux[Int, String, String](capacity = 100)

mux.open(1) match {
  case stream: MuxStream[Int, String, String] =>
    stream.offerInbound("hello from peer")
    val msg = stream.receive() // Some("hello from peer")
    stream.send("response")
    val out = stream.takeOutbound() // Some("response")
  case err: MuxError =>
    println(s"open failed: $err")
}
```

Scala 2 uses the same flow with `Either`:

```scala
import zio.blocks.mux._

val mux = Mux[Int, String, String](capacity = 100)

mux.open(1) match {
  case Right(stream) =>
    stream.offerInbound("hello from peer")
    val msg = stream.receive() // Right(Some("hello from peer"))
    stream.send("response")
    val out = stream.takeOutbound() // Right(Some("response"))
  case Left(err) =>
    println(s"open failed: $err")
}
```

### HTTP/2-style multiplexing

```scala
import zio.blocks.mux.*

final case class Request(path: String)
final case class Response(status: Int)

val mux = Mux[Int, Request, Response](capacity = 1000)

// Demuxer receives frames, routes by stream ID
def onFrame(streamId: Int, data: Response): Unit = {
  mux.get(streamId).foreach(_.offerInbound(data))
}

// Application opens streams for requests
mux.open(7) match {
  case stream: MuxStream[Int, Request, Response] =>
    stream.send(Request("/docs"))

    // ... later
    val response = stream.receive()
    stream.halfClose()
  case err: MuxError =>
    println(s"open failed: $err")
}
```

In Scala 2, pattern match on `Right(stream)` / `Left(err)` instead.

### Half-close lifecycle

```scala
import zio.blocks.mux.*

val mux = Mux[Int, String, String](capacity = 10)

mux.open(1) match {
  case stream: MuxStream[Int, String, String] =>
    stream.send("last message")
    stream.halfClose()

    // Can still receive after half-close
    val msg = stream.receive()

    // send() after halfClose returns an error
    stream.send("nope") // MuxError.StreamClosed(1)
  case err: MuxError =>
    println(s"open failed: $err")
}
```

Scala 2 returns `Left(MuxError.StreamClosed(1))` for the final `send`.

### Graceful shutdown

```scala
import zio.blocks.mux.*

val mux = Mux[Int, String, String](capacity = 10)

// Cancel a single stream
mux.cancel(42, MuxError.Cancelled(42, "timeout"))

// Shut down everything
mux.closeAll(MuxError.MuxClosed)
assert(mux.activeCount == 0)
```

## Architecture

`Mux` keeps a registry of active streams and gives each stream its own inbound and outbound queues. The protocol layer never talks to user code directly, it only moves messages through `offerInbound` and `takeOutbound`.

Half-close models the usual protocol lifecycle:

- local side done sending
- remote side done sending
- both sides done, stream fully closed

## Performance

- JVM: `MpscRingBuffer` for inbound and outbound, lock-free and zero-alloc for multi-producer writes
- JVM: `VarHandle` CAS for stream state transitions
- JS: `ArrayDeque` fallback
- No `synchronized`, so it stays friendly to virtual threads
