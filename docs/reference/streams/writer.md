---
id: writer
title: "Writer"
---

`Writer[-Elem]` is a **push-based sink for elements** that accepts values one at a time until closed or filled. Elements are written on demand by the producer, making it ideal for streaming, buffering, and integration with I/O subsystems. The fundamental operations are `write(elem): Boolean` — pushes an element and returns success or closure — and `close()` — signals the end of writing and releases resources.

`Writer`:
- is lazy and push-based — nothing happens until the producer calls `write()`
- is not thread-safe — designed for single-threaded production
- is contravariant in `Elem` — `Writer[Number]` can accept a `Writer[Int]`
- returns `false` on write when closed (clean closure) or throws when error-closed
- is the dual of `Reader` — while Reader pulls, Writer receives pushes
- supports bounded buffers and resource cleanup via `close()` and `fail()`

Here is the structural shape of the `Writer` type:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
  def close(): Unit
  def fail(error: Throwable): Unit
  def isClosed: Boolean
  def writeable(): Boolean
  def writeAll[Elem1 <: Elem](chunk: Chunk[Elem1]): Chunk[Elem1]
  def writeInt(value: Int)(using Int <:< Elem): Boolean
  def writeLong(value: Long)(using Long <:< Elem): Boolean
  def writeFloat(value: Float)(using Float <:< Elem): Boolean
  def writeDouble(value: Double)(using Double <:< Elem): Boolean
  def ++[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
  def contramap[Elem2](g: Elem2 => Elem): Writer[Elem2]
}
```

## Motivation

**The Problem**: Streaming data in the real world is often *push-based*, not pull-based. When you integrate with network sockets, event buses, sensor arrays, or Java's `OutputStream`, data flows *toward you* (the producer pushes, you receive). Meanwhile, `Reader` and `Sink` are designed around *pulling*: the consumer drains elements on demand. This mismatch creates friction: you must buffer data while waiting for the consumer to pull, handle backpressure when buffers fill, and manage state transitions cleanly when the sink closes or fails.

Worse, Java's standard I/O classes (`OutputStream`, `Writer`) are mutable and return `void` — they either succeed silently or throw exceptions. This makes it hard to compose I/O operations, test in isolation, or reason about cleanup. You end up with try-catch-finally boilerplate and tight coupling to concrete implementations.

**The Solution**: `Writer` is a push-based abstraction that inverts the data flow. Instead of the consumer pulling elements from a source, a producer *pushes* elements to the writer. The key insight is that `Writer` returns a `Boolean` from `write()`: it tells you immediately whether the write succeeded, the writer is full, or it has closed — all without throwing exceptions or blocking threads. This makes `Writer` composable, testable, and safe to use in single-threaded production scenarios.

`Writer` excels in these scenarios:

- **Event streaming**: Push-based event buses or message queues where events arrive continuously and you need to bound buffering
- **Network I/O**: Writing to sockets or channels where you control the pacing and must handle connection closure cleanly
- **Sensor/telemetry data**: Devices or APIs that push data (timestamps, measurements) and you aggregate or forward them
- **Interop with Java I/O**: Wrapping `OutputStream` or `Writer` to leverage existing code while maintaining functional semantics

## Overview

`Writer[-Elem]` is the push-based counterpart to `Reader[+Elem]`. While Reader is a pull-based source that the consumer (sink) drains, Writer is a push-based sink that a producer feeds. Most users interact with Writer through channel-based stream operations or I/O adapters, but you can also use it directly for custom push-based integration.

**Architecture**:

```
Producer ──(push via write)──> Writer[-Elem]
                                │
                                └─(closes on full/error)──> Status
```

Key characteristics:

- **Push-based**: The producer controls the pace by calling `write()`. If the writer is full or closed, `write()` returns `false`.
- **Contravariant**: `Writer[-Elem]` is contravariant in `Elem`, meaning `Writer[Number]` can accept an `Int` write because `Int` is a subtype of `Number`.
- **State-based closure**: Writers close cleanly (return `false`) or with error (via `fail(error)`, which may cause subsequent writes to throw).
- **Bounded buffering**: Implementations can enforce per-write blocking or immediate rejection based on buffer availability.

## Construction

Writers are created using factory methods on the companion object, from adapters wrapping Java I/O, or by direct subclassing for custom implementations:

### Creating Predefined Writers

`Writer.closed` — A pre-closed writer that rejects all writes. Useful as a base case for empty streams:

```scala
object Writer {
  def closed: Writer[Any]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.closed
println(w.write(42))        // false (closed)
println(w.isClosed)         // true
```

### Single Element

`Writer.single` — Creates a writer that accepts exactly one element, then auto-closes. The dual of `Reader.single`:

```scala
object Writer {
  def single[Elem]: Writer[Elem]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
println(w.write(42))        // true
println(w.write(99))        // false (already accepted one element)
println(w.isClosed)         // true
```

### Limited Capacity

`Writer.limited` — Creates a writer that accepts at most `n` elements from `inner`, then auto-closes. The dual of `Stream.take`:

```scala
object Writer {
  def limited[Elem](inner: Writer[Elem], n: Long): Writer[Elem]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val inner = Writer.single[Int]
val limited = Writer.limited(inner, 2)
println(limited.write(1))    // true
println(limited.write(2))    // false (limit reached)
```

### I/O Adapters

`Writer.fromOutputStream` — Wraps a `java.io.OutputStream` as a `Writer[Byte]`. Calling `close()` flushes and closes the underlying stream:

```scala
object Writer {
  def fromOutputStream(os: OutputStream): Writer[Byte]
}
```

`Writer.fromWriter` — Wraps a `java.io.Writer` as a `Writer[Char]`. Calling `close()` flushes and closes the underlying writer:

```scala
object Writer {
  def fromWriter(w: java.io.Writer): Writer[Char]
}
```

## Core Operations

The fundamental operations on `Writer` cover pushing elements one at a time, bulk operations, specialized writes for primitives, and state checks:

### Writing Elements

`Writer#write` — Pushes one element to the writer. Returns `true` on success, `false` if the writer is closed and cannot accept more elements. Throws if the writer was closed with an error via `Writer#fail`:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
val result1 = w.write(42)
val result2 = w.write(99)  // false, already closed
println(s"First: $result1, Second: $result2")
```

### Bulk Writing

`Writer#writeAll` — Writes every element in a chunk. Returns the suffix not delivered. If the writer is already closed, returns the entire chunk. Exceptions from individual writes propagate to the caller:

```scala
abstract class Writer[-Elem] {
  def writeAll[Elem1 <: Elem](chunk: Chunk[Elem1]): Chunk[Elem1]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Writer
import zio.blocks.chunk.Chunk

val w = Writer.single[Int]
val chunk = Chunk(1, 2, 3)
val remaining = w.writeAll(chunk)
println(s"Remaining: $remaining")  // Chunk(2, 3)
```

### Specialized Writes

For primitive types, specialized write methods avoid boxing by using subtype witnesses.

`writeInt` — Specialized `Int` write. Requires implicit evidence that `Int` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeInt(value: Int)(using Int <:< Elem): Boolean
}
```

`writeLong` — Specialized `Long` write:

```scala
abstract class Writer[-Elem] {
  def writeLong(value: Long)(using Long <:< Elem): Boolean
}
```

`writeFloat` — Specialized `Float` write:

```scala
abstract class Writer[-Elem] {
  def writeFloat(value: Float)(using Float <:< Elem): Boolean
}
```

`writeDouble` — Specialized `Double` write:

```scala
abstract class Writer[-Elem] {
  def writeDouble(value: Double)(using Double <:< Elem): Boolean
}
```

### Byte and Character Writes

`writeByte` — Specialized byte write. Avoids boxing when `Elem = Byte`. Requires evidence that `Byte` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeByte(b: Byte)(using Byte <:< Elem): Boolean
}
```

`writeBytes` — Blocking bulk byte write. Calls `writeByte` for each byte in `buf[offset, offset+len)`, stopping early if the channel closes. Returns the number of bytes successfully written:

```scala
abstract class Writer[-Elem] {
  def writeBytes(buf: Array[Byte], offset: Int, len: Int)(using Byte <:< Elem): Int
}
```

`writeChar` — Specialized `Char` write. Requires evidence that `Char` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeChar(value: Char)(using Char <:< Elem): Boolean
}
```

`writeShort` — Specialized `Short` write. Requires evidence that `Short` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeShort(value: Short)(using Short <:< Elem): Boolean
}
```

`writeBoolean` — Specialized `Boolean` write. Requires evidence that `Boolean` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeBoolean(value: Boolean)(using Boolean <:< Elem): Boolean
}
```

### State Checks

`Writer#isClosed` — Returns `true` if the writer is closed. Monotone: once `true`, never returns `false`:

```scala
abstract class Writer[-Elem] {
  def isClosed: Boolean
}
```

`writeable` — Returns `true` if the next `write()` would accept a value without blocking (space is available and the writer is not closed). Default returns `!isClosed`. Buffered writers override for accuracy:

```scala
abstract class Writer[-Elem] {
  def writeable(): Boolean
}
```

Check writer capacity before writing:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
println(w.writeable())      // true
w.write(42)
println(w.writeable())      // false (closed after accepting one)
```

## Composition

Writers can be concatenated to chain multiple sinks together, or transformed to adapt their input types:

### Concatenation

`Writer#concat` — Returns a `Writer` that writes to `this` until it closes, then transparently switches to `next`. If `this` closes with an error, the error is propagated immediately without consulting `next`. The dual of `Reader#concat`:

```scala
abstract class Writer[-Elem] {
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
}
```

`Writer#++` — Alias for `Writer#concat`. Syntactic sugar for composing writers:

```scala
abstract class Writer[-Elem] {
  def ++[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
}
```

Here is how concatenation switches to the next writer when the first closes:

```scala mdoc:reset
import zio.blocks.streams.io.Writer
import scala.collection.mutable

val collected = mutable.ArrayBuffer[Int]()
val w1 = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = { collected += a; a < 10 }
  def close() = ()
}

val w2 = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = { collected += a * 10; true }
  def close() = ()
}

val combined = w1 ++ w2
combined.write(5)
combined.write(20)  // first writer rejects, switches to second
println(collected.toList)  // List(5, 200)
```

### Transformation

`Writer#contramap` — Returns a `Writer` that transforms incoming elements with `g` before passing them to this writer. All other operations (`Writer#isClosed`, `Writer#close`, `Writer#fail`) delegate unchanged:

```scala
abstract class Writer[-Elem] {
  def contramap[Elem2](g: Elem2 => Elem): Writer[Elem2]
}
```

Transform the input type before writing:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val stringWriter = new Writer[String] {
  def isClosed = false
  def write(a: String) = { println(s"Writing: $a"); true }
  def close() = ()
}

val intWriter = stringWriter.contramap[Int](_.toString)
intWriter.write(42)   // Prints: Writing: 42
```

## Closure and Error Handling

Writers support both clean closure and error closure, allowing you to signal end-of-stream gracefully or with an error condition:

### Clean Closure

`Writer#close` — Closes the writer cleanly. After this call, `write()` returns `false` and `Writer#isClosed` returns `true`. Idempotent:

```scala
abstract class Writer[-Elem] {
  def close(): Unit
}
```

### Error Closure

`Writer#fail` — Closes the writer with an error. After this call, `Writer#isClosed` returns `true`. Subclasses that override this method may cause `write()` to throw `error` on subsequent calls; the default simply delegates to `Writer#close`. Both `Writer#close` and `Writer#fail` are idempotent; only the first call wins:

```scala
abstract class Writer[-Elem] {
  def fail(error: Throwable): Unit
}
```

Close a writer with an error:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
w.write(42)
w.fail(new RuntimeException("Error"))
println(w.isClosed)     // true
```

## Contravariance

`Writer` is **contravariant** in `Elem`, meaning `Writer[-Elem]` can accept narrower types. If you have a `Writer[Number]`, you can use it as a `Writer[Int]` because every `Int` is a `Number`:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

trait Number
case class IntNum(value: Int) extends Number

val numberWriter = new Writer[Number] {
  def isClosed = false
  def write(a: Number) = { println(s"Number: $a"); true }
  def close() = ()
}

// numberWriter is also a Writer[IntNum] due to contravariance
val intNumWriter: Writer[IntNum] = numberWriter
intNumWriter.write(IntNum(42))
```

This is the dual of Reader's covariance: Reader is covariant (`+Elem`) because narrower elements flow out; Writer is contravariant (`-Elem`) because broader element types flow in.

## Integration with Readers and Channels

While Reader is typically used with pull-based stream operations, Writer is used internally by channel-based implementations and as an I/O adapter. The pairing is natural: a Reader pulls from a source, while a Writer pushes to a sink.

For typical stream usage, you'll see Writer indirectly when writing to files, network sockets, or other I/O resources. The `Writer.fromOutputStream` and `Writer.fromWriter` factories adapt standard Java I/O to the Writer interface.

## Implementation Notes

Understanding `Writer`'s design decisions helps you use it correctly and avoid common pitfalls:

### Push vs Pull

`Writer` is push-based (producer-driven), contrasting with `Reader` which is pull-based (consumer-driven):

| Aspect | Reader | Writer |
|--------|--------|--------|
| **Direction** | Source → Consumer (pull) | Producer → Sink (push) |
| **Variance** | Covariant (`+Elem`) | Contravariant (`−Elem`) |
| **Blocking** | `read()` may block | `write()` may block |
| **Signal end** | Returns sentinel or `null` | `close()` or `fail()` |
| **Dual** | Sink drains Reader | Producer feeds Writer |

### Thread Safety

`Writer` is **not thread-safe** by default. It is designed for single-threaded, push-based production. Do not share a `Writer` across threads without external synchronization. If you need concurrent production, wrap the writer in a thread-safe queue or use a concurrent streaming library.

### Idempotency

Both `close()` and `fail()` are idempotent: only the first call wins. Subsequent calls have no effect. This simplifies error handling in try-finally blocks.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

**1. Clone the repository and navigate to the project:**

Run these commands to set up the examples:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Writer Construction

This example demonstrates the most common writer factories: `Writer.single`, `Writer.limited`, `Writer.closed`, and custom writers via subclassing:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterBasicConstructionExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterBasicConstructionExample"
```

### Composition and Transformation

This example shows writer composition with `Writer#++` (concat), transformation with `Writer#contramap`, and bulk writes with `Writer#writeAll`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterCompositionExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterCompositionExample"
```

### I/O Adapters

This example demonstrates I/O integration with `Writer.fromOutputStream` and `Writer.fromWriter` for streaming to files or character streams:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterIOAdapterExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterIOAdapterExample"
```
