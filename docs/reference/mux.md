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
- Thread-safe
- Lock-free on JVM for queue operations
- Virtual-thread-friendly
- JVM uses ring buffers for stream queues

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

sealed trait MuxError
```

## Overview

`Mux` owns the stream registry. Protocol code uses `offerInbound` and `takeOutbound`, while application code uses `send` and `receive`.

```
User code → send(In) → outbound queue → Protocol reads via takeOutbound()
Protocol  → offerInbound(Out) → inbound queue → User code reads via receive()
```

Each stream has separate inbound and outbound queues, so traffic for one ID stays isolated from the others. Half-close maps cleanly to RFC 9113 stream states, which makes the API a good fit for HTTP/2-style protocols.

## API

### `Mux[Id, In, Out]`

Factory:

```scala
object Mux {
  def apply[Id, In, Out](capacity: Int): Mux[Id, In, Out]
}
```

Core operations:

- `open(id)` opens a new stream
- `get(id)` looks up an active stream
- `cancel(id, reason)` closes one stream with an error
- `closeAll(reason)` closes every active stream
- `activeCount` reports how many streams are open

### `MuxStream[Id, In, Out]`

Per-stream operations:

- `send(msg)` queues outbound data for the protocol layer
- `receive()` reads inbound data for user code
- `offerInbound(msg)` delivers data from the protocol layer
- `takeOutbound()` drains outbound data for the protocol layer
- `halfClose()` marks local send as finished
- `signalRemoteClose()` marks remote send as finished
- `close()` fully closes the stream

### `MuxError`

Error cases:

- `MuxError.StreamClosed(id)`
- `MuxError.CapacityExceeded(limit)`
- `MuxError.Cancelled(id, reason)`
- `MuxError.MuxClosed`
- `MuxError.ProtocolError(message)`

## Examples

### Basic usage

```scala
import zio.blocks.mux.*

val mux = Mux[Int, String, String](capacity = 100)

// Open a stream
val stream = mux.open(1).toOption.get

// Protocol side: deliver inbound message
stream.offerInbound("hello from peer")

// User side: receive
val msg = stream.receive() // Right(Some("hello from peer"))

// User side: send outbound
stream.send("response")

// Protocol side: drain outbound
val out = stream.takeOutbound() // Right(Some("response"))
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
val stream = mux.open(7).toOption.get
stream.send(Request("/docs"))

// ... later
val response = stream.receive()
stream.halfClose()
```

### Half-close lifecycle

```scala
import zio.blocks.mux.*

val mux = Mux[Int, String, String](capacity = 10)
val stream = mux.open(1).toOption.get

stream.send("last message")
stream.halfClose()

// Can still receive after half-close
val msg = stream.receive()

// send() after halfClose returns error
stream.send("nope") // Left(MuxError.StreamClosed(1))
```

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

- JVM: `SpscRingBuffer` for outbound, `MpscRingBuffer` for inbound, lock-free and zero-alloc
- JVM: `VarHandle` for stream state, with explicit memory ordering
- JS: `ArrayDeque` fallback
- No `synchronized`, so it stays friendly to virtual threads
