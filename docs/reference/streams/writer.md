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

`Writer.fromOutputStream` — Wraps a `java.io.OutputStream` as a `Writer[Byte]`. Calling `close()` flushes and closes the underlying stream.

```scala
object Writer {
  def fromOutputStream(os: OutputStream): Writer[Byte]
}
```

`Writer.fromWriter` — Wraps a `java.io.Writer` as a `Writer[Char]`. Calling `close()` flushes and closes the underlying writer.

```scala
object Writer {
  def fromWriter(w: java.io.Writer): Writer[Char]
}
```

## Core Operations

### Writing Elements

`write` — Pushes one element to the writer. Returns `true` on success, `false` if the writer is closed and cannot accept more elements. Throws if the writer was closed with an error via `fail()`:

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

`writeAll` — Writes every element in a chunk. Returns the suffix not delivered. If the writer is already closed, returns the entire chunk. Exceptions from individual writes propagate to the caller:

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

`isClosed` — Returns `true` if the writer is closed. Monotone: once `true`, never returns `false`:

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

### Concatenation

`concat` — Returns a `Writer` that writes to `this` until it closes, then transparently switches to `next`. If `this` closes with an error, the error is propagated immediately without consulting `next`. The dual of `Reader.concat`:

```scala
abstract class Writer[-Elem] {
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
}
```

`++` — Alias for `concat`. Syntactic sugar for composing writers:

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

`contramap` — Returns a `Writer` that transforms incoming elements with `g` before passing them to this writer. All other operations (`isClosed`, `close`, `fail`) delegate unchanged:

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

### Clean Closure

`close` — Closes the writer cleanly. After this call, `write()` returns `false` and `isClosed` returns `true`. Idempotent:

```scala
abstract class Writer[-Elem] {
  def close(): Unit
}
```

### Error Closure

`fail` — Closes the writer with an error. After this call, `isClosed` returns `true`. Subclasses that override this method may cause `write()` to throw `error` on subsequent calls; the default simply delegates to `close()`. Both `close()` and `fail()` are idempotent; only the first call wins:

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

For typical stream usage, you'll see Writer indirectly when writing to files, network sockets, or other I/O resources. The `fromOutputStream` and `fromWriter` factories adapt standard Java I/O to the Writer interface.

## Implementation Notes

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

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Writer Construction

This example demonstrates the most common writer factories: `single`, `limited`, `closed`, and custom writers via subclassing.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterBasicConstructionExample.scala")
```

```bash
sbt "streams-examples/runMain writer.WriterBasicConstructionExample"
```

### Composition and Transformation

This example shows writer composition with `++` (concat), transformation with `contramap`, and bulk writes with `writeAll`.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterCompositionExample.scala")
```

```bash
sbt "streams-examples/runMain writer.WriterCompositionExample"
```

### I/O Adapters

This example demonstrates I/O integration with `fromOutputStream` and `fromWriter` for streaming to files or character streams.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterIOAdapterExample.scala")
```

```bash
sbt "streams-examples/runMain writer.WriterIOAdapterExample"
```
