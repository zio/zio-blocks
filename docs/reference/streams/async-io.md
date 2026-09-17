---
id: async-io
title: "Asynchronous I/O Adapters"
sidebar_label: "Async I/O"
description: "The JVM NIO channel readers and the Scala.js ReadableStream readers: their factories, ownership rules, invariants, and hard limits."
keywords:
  - "Asynchronous I/O"
  - "NIO Channels"
  - "Readable Streams"
  - "Reader Ownership"
  - "AsyncNioReaders"
---

Each platform ships a small set of factories that turn a native asynchronous byte source into a `Reader.AsyncReader[Byte]`. On the JVM that source is a `java.nio.channels.AsynchronousByteChannel`; on Scala.js it is a Web Streams API `ReadableStream`. Once wrapped, the result is an ordinary asynchronous reader: pull from it by hand, or hand it to `Stream.fromReader` and run the pipeline with a `*Async` terminal.

This page is a lookup table for those factories — what each one wraps, who owns the native source afterwards, what each adapter guarantees, and what it does not support. The limits are as load-bearing as the features: there is no factory for a file channel, and the Scala.js adapter reads bytes only, with no BYOB reader and no buffer-size knob.

## Overview

Six factories exist across the two platforms, all of them returning `Reader.AsyncReader[Byte]` on the `JvmType.Byte` lane. They differ in what they wrap and in who owns the native source once the reader closes:

| Factory                                             | Platform | Native source               | On reader `close()`                        |
|-----------------------------------------------------|----------|-----------------------------|--------------------------------------------|
| `AsyncNioReaders.fromChannel`                       | JVM      | `AsynchronousByteChannel`   | Closes the channel                         |
| `AsyncNioReaders.fromChannelUnmanaged`              | JVM      | `AsynchronousByteChannel`   | Leaves the channel open                    |
| `AsyncNioReaders.fromSocket`                        | JVM      | `AsynchronousSocketChannel` | Closes the socket                          |
| `AsyncNioReaders.fromSocketUnmanaged`               | JVM      | `AsynchronousSocketChannel` | Leaves the socket open                     |
| `ReadableStreamReaders.fromReadableStream`          | Scala.js | `ReadableStream`            | Cancels the stream, then releases the lock |
| `ReadableStreamReaders.fromReadableStreamUnmanaged` | Scala.js | `ReadableStream`            | Releases the lock, never cancels           |

The two adapters follow the same rules, so a cross-platform consumer sees the same behaviour from either one:

| Situation                   | JVM `AsyncNioReaders`                          | Scala.js `ReadableStreamReaders`           |
|-----------------------------|------------------------------------------------|--------------------------------------------|
| A delivery carries no bytes | The buffer is cleared and the read resubmitted | The chunk is skipped and `read()` reissued |
| End of stream               | A negative completion count                    | `done = true` on the read result           |
| A source failure            | `IOException`, trusted                         | A rejected promise, trusted                |
| Read buffer size            | `bufferSize`, default 8192                     | Not configurable                           |

Neither module is a general native-I/O layer. `AsyncNioReaders` reads from channels that implement the JDK's asynchronous read protocol, and `ReadableStreamReaders` reads from a browser or Node byte stream. Everything else — files on the JVM, non-byte sources on Scala.js — is outside what these factories accept.

## JVM: `AsyncNioReaders`

`AsyncNioReaders` is the JVM factory object for genuinely non-blocking reads. Its four factories divide along two axes: the type of the native source, and whether the reader owns it.

### `AsyncNioReaders.fromChannel` and `AsyncNioReaders.fromChannelUnmanaged`

Both wrap an `AsynchronousByteChannel` and take the read buffer size as a defaulted second parameter:

```scala
object AsyncNioReaders {
  def fromChannel(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
  def fromChannelUnmanaged(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
}
```

`bufferSize` is the capacity of the single `ByteBuffer` the reader refills from the channel, and it is validated eagerly: a value of zero or less throws `IllegalArgumentException` with the message `requirement failed: bufferSize must be positive` from the factory call itself, not from the first pull.

To lift a channel into a stream and run it with a cross-platform terminal:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import java.nio.channels.AsynchronousByteChannel

def collect(channel: AsynchronousByteChannel): Async[Either[Nothing, Chunk[Byte]]] =
  Stream.fromReader[Nothing, Byte](AsyncNioReaders.fromChannel(channel, bufferSize = 4096)).runCollectAsync
```

Driving the reader by hand works the same way, and is what you want when the protocol is framed rather than streamed:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.AsyncNioReaders
import zio.blocks.streams.io.Reader

import java.nio.channels.AsynchronousByteChannel

def header(channel: AsynchronousByteChannel): Async[Chunk[Byte]] = {
  val reader: Reader.AsyncReader[Byte] = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 512)
  reader.readN[Byte](16).flatMap(bytes => reader.close().map(_ => bytes))
}
```

### `AsyncNioReaders.fromSocket` and `AsyncNioReaders.fromSocketUnmanaged`

The socket pair narrows the parameter type to `AsynchronousSocketChannel`, which is the `AsynchronousByteChannel` most callers actually hold:

```scala
object AsyncNioReaders {
  def fromSocket(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
  def fromSocketUnmanaged(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
}
```

There is no behavioural difference to learn: `AsyncNioReaders.fromSocket` delegates to `AsyncNioReaders.fromChannel` and `AsyncNioReaders.fromSocketUnmanaged` to `AsyncNioReaders.fromChannelUnmanaged`, with the same buffer and the same ownership rule. They exist so a socket-shaped call site reads as one.

### Managed Versus Unmanaged Ownership

Ownership is the whole of the difference between the two variants, and it is decided when you pick the factory, not later.

A managed reader — `AsyncNioReaders.fromChannel` or `AsyncNioReaders.fromSocket` — closes the underlying channel when the reader closes, and only if the channel is still open. If that channel close fails, the failure surfaces from the reader's own `close()` rather than being swallowed. An unmanaged reader releases the reader and nothing else: the channel stays open for whoever owns it, and a reader close is invisible to the rest of the program apart from the read it cancels.

Closing either kind cancels a read that is still in flight. The reader marks itself closed, cancels the underlying channel operation, and settles the pending pull with its end-of-stream answer — `readByte()` returns `-1`, `read(sentinel)` returns the sentinel — so a consumer parked on a pull is released rather than left waiting for a channel that will never answer.

`close()` is idempotent. The first caller performs the work; every later caller awaits the same memoized outcome, and the channel is closed at most once.

All three facts are observable rather than asserted. The example below is a runnable file in the JVM-only `streams-examples` module of the [zio-blocks repository](https://github.com/zio/zio-blocks). It drives a scripted `AsynchronousByteChannel` — one that counts its own `close()` calls and parks a read it cannot serve — through both ownership modes, so the managed close, the untouched unmanaged channel, the memoized second close, and the cancelled pending read are all printed:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/nio/AsyncChannelReaderExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/nio/AsyncChannelReaderExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain nio.AsyncChannelReaderExample"
```

It prints:

```
managed   -> read=async, channelOpen=false, closes=1
unmanaged -> read=async, channelOpen=true, closes=0
pending   -> read=hi, cancelledRead=-1, channelOpen=true, closes=0
```

### Invariants

Four properties hold for every reader these factories produce, and each one is a rule a hand-written channel wrapper commonly gets wrong.

A zero-byte completion is not end of stream. When the channel completes a read having transferred nothing, the adapter clears its buffer and resubmits the read; only a negative completion count ends the stream. A channel that yields `0` under backpressure therefore stalls the pull, it does not truncate the stream.

`IOException`s are trusted source failures. A failure reported by the channel is wrapped as a source failure and surfaces in the typed error channel of the stream built from the reader, not as a defect, and every later pull replays it rather than pretending the source recovered.

Pulls are inert until driven. Every read method returns a deferred `Async`; building `reader.readByte()` submits nothing to the channel, and the read is issued when the effect is driven. `readable()` follows the same rule — it reports whether bytes are already buffered and never initiates I/O to find out.

One operation may be in flight at a time. A second pull started while another is active fails with `IllegalStateException`; [One Active Operation per Reader](./async-execution.md#one-active-operation-per-reader) covers the rule and the way to sequence pulls instead.

### Limitations

The asynchronous NIO surface is exactly the four factories above, and file input is not among them.

:::warning[`AsynchronousFileChannel` is not supported]
No factory accepts an `AsynchronousFileChannel`, and it is not an `AsynchronousByteChannel`, so it cannot be passed to `AsyncNioReaders.fromChannel` either. There is no asynchronous file reader in this module.
:::

File bytes are reachable only through the synchronous side: a `java.nio.channels.FileChannel` is a `ReadableByteChannel`, so `NioReaders.fromChannel` accepts it and returns a `Reader.SyncReader[Byte]`. That reader blocks, and `SyncReader#toAsync` does not change it — the resulting asynchronous reader still blocks the thread that drives it.

## JVM: `NioReaders` Stays Synchronous

`NioReaders` has no asynchronous twins, and the reason is in the type it wraps: `ReadableByteChannel#read` blocks the calling thread. No wrapper can make it non-blocking, so presenting its result as an `AsyncReader` would have promised something the channel cannot deliver. The two objects therefore split by capability — `AsyncNioReaders` for channels that implement the JDK's asynchronous read protocol, `NioReaders` for the blocking ones and for `ByteBuffer`s.

Every `NioReaders` factory states its kind in its return type:

```scala
object NioReaders {
  def fromByteBuffer(buf: ByteBuffer): Reader.SyncReader[Byte]
  def fromByteBufferDouble(buf: ByteBuffer): Reader.SyncReader[Double]
  def fromByteBufferFloat(buf: ByteBuffer): Reader.SyncReader[Float]
  def fromByteBufferInt(buf: ByteBuffer): Reader.SyncReader[Int]
  def fromByteBufferLong(buf: ByteBuffer): Reader.SyncReader[Long]
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Reader.SyncReader[Byte]
}
```

These factories return `Reader.SyncReader[Byte]` rather than the root `Reader[Byte]`, which is what makes pulling and closing available on the result: those members belong to the reader kinds, not to the root type. Two details of this object are worth noting at a call site: its channel factory names the buffer parameter `bufSize`, where the asynchronous one names it `bufferSize`, and there is no public unmanaged channel variant here — `NioReaders.fromChannel` always owns the channel it wraps.

## Scala.js: `ReadableStreamReaders`

`ReadableStreamReaders` is the Scala.js counterpart, wrapping the byte-reading side of the Web Streams API. The module declares minimal `@js.native` facades for `ReadableStream`, its reader, and a read result, so using it does not pull a DOM library into your build.

### The Factories

Two factories mirror the managed and unmanaged pair on the JVM:

```scala
object ReadableStreamReaders {
  def fromReadableStream(stream: ReadableStream): Reader.AsyncReader[Byte]
  def fromReadableStreamUnmanaged(stream: ReadableStream): Reader.AsyncReader[Byte]
}
```

Both take the *stream*, not a reader. Each factory calls `stream.getReader()` itself and keeps the acquired reader for its own lifetime, which is what makes the lock release on close well defined. Acquiring a reader yourself and passing it in is not part of the API.

### Managed Versus Unmanaged

The managed factory owns the acquired reader: closing it cancels the JavaScript stream, awaits the read that was in flight, and then releases the lock. The unmanaged factory releases the lock and never cancels, so the underlying stream remains usable by the code that created it. As on the JVM, either kind settles a pending pull with end of stream instead of leaving it outstanding, and drops whatever it had buffered.

### Invariants

The four rules the JVM adapter follows hold here too, restated in terms of `read()`, its promise, and the `done`/`value` result it yields.

An empty chunk is skipped, never treated as end of stream. A result with `done = false` and a zero-length value causes the adapter to reissue `read()`; only `done = true` ends the stream.

Buffered bytes are preserved across pulls. A chunk delivered by the stream is consumed byte by byte from the adapter's own index, so a pull that the buffer can satisfy issues no `read()` at all, and a partially consumed chunk survives until it is drained.

End of stream is observed without prefetch. The adapter never calls `read()` merely to discover whether the stream has finished; it learns that from the read that a pull actually needed.

A rejected promise is a trusted source failure. The rejection is wrapped as a source failure, surfaces in the typed error channel, and is replayed by every later pull.

### Limitations

Two of the JVM adapter's affordances have no Scala.js equivalent, and one of them cannot be worked around from user code.

:::warning[Byte-only, no BYOB, no buffer size]
Both factories produce `Reader.AsyncReader[Byte]` on the `JvmType.Byte` lane and read `Uint8Array` chunks; there is no factory for another element type. Neither factory offers BYOB support — `getReader()` is called with no arguments, so the adapter never acquires a bring-your-own-buffer reader and cannot read into a caller-supplied `ArrayBuffer`. Neither takes a buffer-size parameter: chunk sizes are whatever the underlying stream produces.
:::

## Bridging Kinds

A reader obtained from these adapters is asynchronous, and a reader obtained from `NioReaders` or a collection is synchronous. Two adapters move between the kinds, and only one of them is available everywhere.

### `SyncReader#toAsync`

`SyncReader#toAsync` is cross-platform and lifecycle-preserving. It returns a view over the original reader rather than a copy, so the two share one position and one lifecycle, and it unwraps an existing round trip instead of stacking: called on the synchronous view of an asynchronous reader, it hands back that original asynchronous reader.

It is the adapter to reach for when a helper is written against `Reader.AsyncReader` — the kind that compiles on both platforms — and the reader at the call site happens to be synchronous:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams.io.Reader

def firstByte(reader: Reader.AsyncReader[Byte]): Async[Int] = reader.readByte()

def firstByteOfSync(reader: Reader.SyncReader[Byte]): Async[Int] = firstByte(reader.toAsync)
```

What `SyncReader#toAsync` does not do is make blocking work non-blocking. Driving the view still runs the synchronous reader's pulls on the driving thread.

### `AsyncReader#toSync` (JVM Only)

`AsyncReader#toSync` runs the other way, and it exists only on the JVM: the platform trait that supplies it is empty on Scala.js, so shared source cannot call it at all. Use it at a JVM edge — an `InputStream`-shaped API, a legacy protocol loop — and not in code that cross-builds:

```scala mdoc:compile-only
import zio.blocks.streams.io.Reader

def firstByteBlocking(reader: Reader.AsyncReader[Byte]): Int = {
  val sync = reader.toSync
  try sync.readByte()
  finally sync.close()
}
```

Like `SyncReader#toAsync`, it is a lifecycle-preserving view and unwraps a round trip rather than stacking adapters. Unlike `SyncReader#toAsync`, it has hazards that belong at the call site:

:::warning[`toSync` blocks, serializes, and interrupts]
Every pull blocks the calling thread until the asynchronous work settles. Only one pull runs at a time: a second thread entering the view waits until the first pull completes, so the view is a serialization point, not a way to share a reader. Calling `close()` from another thread interrupts the thread parked in a pull — that pull then returns its closed value (`-1`, the sentinel, or an empty chunk) instead of the value it was waiting for. Closing the view also closes the underlying asynchronous reader. After a close, the control operations — `reset()`, `setLimit`, `setRepeat`, `setSkip`, and `skip` — throw `IOException("Reader is closed")`.
:::

[The `Reader` Union](./reader.md#the-reader-union) describes the two kinds and how a pipeline is classified into one of them, and the [Availability Matrix](./platform-differences.md#availability-matrix) lists `AsyncReader#toSync` among the members that simply do not exist on Scala.js.

## JVM NIO Sinks

The sink side follows the same rule. Every sink in `NioSinks` — the five `ByteBuffer` sinks and the `WritableByteChannel` sink — implements both drains, the synchronous one and the asynchronous one, so a NIO destination works under the blocking `Stream#run` and under `Stream#runAsync` alike without changing the sink you construct.

What the asynchronous drain removes is the thread parked waiting for stream *input*. The destination writes are the same `java.nio` calls on either path, so a NIO sink is not a non-blocking output layer. [JVM NIO Sinks](./sink.md#jvm-nio-sinks) documents the sinks themselves, including the way the channel sink budgets its flush loop, and this page does not duplicate that. For how each primitive lane signals end of stream to a drain, see [EOF Signalling per Lane](./zero-boxing.md#eof-signalling-per-lane).

## Downstream Example: `http-model`'s `Body`

`Body` in `http-model` is the clearest in-repo illustration of what adopting this API looks like for a cross-platform consumer, because a body is exactly a `Stream[Nothing, Byte]` that someone eventually wants as bytes or as text.

Each blocking accessor has an asynchronous twin under the library-wide naming convention, five in all: `Body#toChunkAsync`, `Body#toArrayAsync`, `Body#asStringAsync`, `Body#asStringFromContentTypeAsync`, and `Body#textAsync`. The twins are the cross-platform API. The original accessors block, so they compile on Scala.js but throw `IllegalStateException` the moment the stream actually has to suspend — see [Why Blocking Terminals Are JVM-Only](./platform-differences.md#why-blocking-terminals-are-jvm-only). `Body#toChunk` is implemented in terms of the asynchronous one, taking a known-chunk fast path first and otherwise running `runCollectAsync` and blocking on the result.

Porting a call site is the rename plus a change of result type that the rest of the migration is:

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.http.Body

// Blocking: works on the JVM; on Scala.js this throws once the stream suspends
def bytesBlocking(body: Body): Chunk[Byte] = body.toChunk

// JVM and Scala.js
def bytes(body: Body): Async[Chunk[Byte]] = body.toChunkAsync
def text(body: Body): Async[String]       = body.textAsync
```

[Body](../http-model/model.md#body) documents the type itself, its constructors, and the rest of its accessors.

## See Also

- [Reader](./reader.md) — the `SyncReader` / `AsyncReader` union, the full pull API, and custom reader implementations
- [Asynchronous Stream Execution](./async-execution.md) — the `*Async` constructor, operator, and terminal families that drive these readers
- [Platform Differences](./platform-differences.md) — what exists on the JVM, what exists on Scala.js, and what throws
- [Sink](./sink.md) — the sink side, including the NIO sinks and their two drains
