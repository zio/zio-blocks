# Producer-Backed Streams (JVM)

## Overview

`ProducerStreams.fromProducer` bridges a push-based producer (running on any thread) into a
pull-based `Stream[E, A]`. The producer pushes elements via a `ProducerSink`; the consumer
pulls them through the stream interface. A `BlockingSpscQueue` sits between the two, providing
spin-wait followed by park-based blocking: the producer parks when the buffer is full and the
consumer parks when it is empty.

**SPSC contract:** Callers of `fromProducer` must ensure that only one thread calls
`emit` at a time. The internal `BlockingSpscQueue` is a single-producer single-consumer data
structure — concurrent `emit` calls from multiple threads will corrupt the buffer.

## API

```scala
package zio.blocks.streams

// Push-based sink handed to the producer.
trait ProducerSink[-A, -E] {
  def emit(a: A): Boolean            // false if stream is closed/cancelled
  def emit(chunk: Chunk[A]): Boolean // emit a whole chunk as a single ref; false on close
  def end(): Unit                    // signal normal completion; idempotent
  def fail(error: E): Unit           // signal typed error; idempotent
}

// JVM-only constructors (streams/jvm/).
object ProducerStreams {
  // Full control: user manages thread + cancel callback.
  def fromProducer[E, A](
    register: ProducerSink[A, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A]

  // Managed lifecycle: auto-end, auto-fail on exception, virtual thread.
  def fromProducerSimple[E, A](
    produce: ProducerSink[A, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A]

  // Byte-optimized: chunks transferred as single references, zero-boxing reads.
  def fromProducerBytes[E](
    register: ProducerSink[Byte, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte]

  // Byte-optimized with managed lifecycle on a virtual thread.
  def fromProducerBytesSimple[E](
    produce: ProducerSink[Byte, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte]
}
```

`register` is called once when the stream is compiled. It receives a `ProducerSink`, starts
the producer (typically on a separate thread), and returns a cancel callback `() => Unit`.

`fromProducerSimple` uses `Platform.startVirtualThread` to run the producer on a virtual
thread, automatically calling `end()` on normal return and `fail()` on exception. On JDK 17,
`Platform.startVirtualThread` falls back to a regular daemon thread, since virtual threads
require JDK 21+.

## Usage Example

```scala
import zio.blocks.streams.{ProducerStreams, ProducerSink}

val stream: Stream[Nothing, Int] = ProducerStreams.fromProducer { sink =>
  val thread = new Thread(() => {
    var i = 1
    while (i <= 10 && sink.emit(i)) { i += 1 }
    sink.end()
  })
  thread.start()
  () => thread.interrupt() // cancel callback
}
```

## Lifecycle Contract

| Producer action | While active | After terminal |
|-----------------|-------------|----------------|
| `emit(a)`       | enqueues, returns `true` | returns `false` |
| `emit(chunk)`   | enqueues chunk as single ref, returns `true` | returns `false` |
| `end()`         | signals completion | no-op |
| `fail(e)`       | signals error | no-op |
| consumer close  | cancel callback fired | no-op |

- `end` and `fail` are idempotent; the first call wins.
- `emit` after any terminal state returns `false` — the producer should treat this as a signal
  to stop work.
- The cancel callback is invoked exactly once, on the consumer thread, during early termination.
  It is **not** called when the stream ends normally via `end()`.

## Known-Length

Pass `knownLength = Some(n)` to expose element count as metadata:

```scala
ProducerStreams.fromProducer(register, knownLength = Some(fileSize))
```

This is a hint only. The runtime does not verify that exactly `n` elements are emitted.
`map` preserves it; `filter` clears it (standard `Stream` semantics).

## Threading Model

- **Producer thread**: pushes elements via `ProducerSink`. Parks (via `BlockingSpscQueue`)
  when the buffer is full, after a brief spin-wait phase.
- **Consumer thread**: pulls elements via the stream. Parks when the buffer is empty.
- On consumer cancel, `queue.close()` is called, which unblocks both the producer and consumer
  so they can observe the cancellation and exit cleanly.

## Byte Streams

`fromProducerBytes` and `fromProducerBytesSimple` are byte-specialized variants backed by
`ByteProducerReader`. Each `Chunk[Byte]` is transferred through the ring buffer as a single
`ChunkEnvelope` reference, and bytes are pulled without boxing via `readByte()`, `readInt()`,
and `readBytes()`.

## HTTP Integration

To back a `Body` (or similar HTTP response type) with a producer-driven byte stream:

```scala
// zio-http Body wraps Stream[Nothing, Byte]
val byteStream: Stream[Nothing, Byte] = ProducerStreams.fromProducerBytesSimple(
  produce = { sink =>
    var active = true
    readChunksFromSource { chunk =>
      if (active && !sink.emit(chunk)) active = false
    }
  },
  knownLength = contentLength
)

Body.fromStream(byteStream)
```

The `knownLength` maps directly to the `Content-Length` header when the body size is known
ahead of time.
