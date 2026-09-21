---
id: writer
title: "Writer"
sidebar_label: "Writer"
description: "The push-based sink for elements: the write-and-close protocol, the primitive write family, and the sixteen deferred *Async mirrors."
keywords:
  - "Push-Based Writing"
  - "Deferred Effects"
  - "Writer Cancellation"
  - "Specialized Writes"
  - "Writer"
---

`Writer[-Elem]` is a **push-based sink for elements** that accepts values one at a time until closed or filled. It is the push-based counterpart to `Reader[+Elem]` (which pulls). Elements are written on demand by the producer, making it ideal for streaming, buffering, and integration with I/O subsystems. The fundamental operations are `write(elem): Boolean` — pushes an element and returns success or closure — and `close()` — signals the end of writing and releases resources.

`Writer[-Elem]` has these key properties:

- **Lazy and Push-Based** — nothing happens until the producer calls `write()`
- **Non-Thread-Safe** — designed for single-threaded production; concurrent access requires external synchronization
- **Explicit Closure Signal** — returns `false` when closed (clean closure) or throws when error-closed

Here is the structural shape of the `Writer` type:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
  def close(): Unit
  def isClosed: Boolean
  
  // concrete defaults for fail() and writeable()
  def fail(error: Throwable): Unit = close()
  def writeable(): Boolean = !isClosed
}
```

## Motivation

Imagine you're building a data pipeline where a producer feeds items to a bounded sink. The producer doesn't control the sink's internal state—how much capacity remains, whether it's busy, or if it's permanently closed. You need to know before each write: Is the sink ready? Did the write succeed? Is the sink closed?

With Java's `OutputStream`, you call `write()` and either it succeeds (void return) or throws an exception. This leaves ambiguity: Was the exception transient (try again later) or permanent (the stream is done)? If the buffer fills, the thread blocks—but you don't know how long, or even that it will block beforehand. There's no way to check capacity upfront, so you're forced to either over-allocate buffers (wasting memory) or catch exceptions and guess the right strategy.

`Writer` makes the state explicit and non-throwing. You check readiness with `writeable()`, then push with `write()`, which returns a `Boolean` indicating success or closure. The protocol is clear and exception-free: when `write()` returns `false`, the sink is permanently closed and you should stop.

## Quick Showcase

Here's how to create and push elements to a `Writer`:

```scala mdoc:reset
import zio.blocks.streams.io.Writer
import scala.collection.mutable.Buffer

val collected = Buffer[Int]()
val w = new Writer[Int] {
  private var closed = false
  
  def isClosed = closed
  def write(a: Int) = {
    if (!closed) { collected += a; true }
    else false
  }
  def close() = { closed = true }
  override def fail(error: Throwable) = close()
  override def writeable() = !isClosed
}

// Push elements, checking writeable() before each write
def pushAll(elements: List[Int]): Unit = {
  elements match {
    case Nil => ()
    case head :: tail =>
      if (w.writeable() && w.write(head)) pushAll(tail)
  }
}

pushAll(List(10, 20, 30, 40, 50))
w.close()

println(s"Collected: $collected")
println(s"Writable after close: ${w.writeable()}")
```

## Writing and Closure

The fundamental protocol is: call `write(element)` to push an element. It returns `true` on success, `false` only when the writer is **closed** (not when the buffer is full). Once `write()` returns `false`, the writer is permanently closed—all further writes return `false`. There is no recovery.

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
println(s"First write: ${w.write(42)}")      // true (accepted)
println(s"Second write: ${w.write(99)}")     // false (writer auto-closed after one element)
println(s"Third write: ${w.write(77)}")      // false (still closed)
```

## Asynchronous Writes

Every effectful member of `Writer` has a deferred mirror whose name ends in `Async` and whose result is an `Async`. There are sixteen of them, one per synchronous member, and together they are the entire asynchronous surface of the type.

A mirror does one thing. It wraps a single synchronous call in an effect that has not happened yet: constructing `writer.writeAsync(42)` performs no write at all, and driving the returned effect performs `write(42)` exactly once and yields its `Boolean`. All sixteen are `final` and delegate to one private helper, which builds them on the library's internal cancellable-defer primitive `Async.deferCancelable` (`Writer.scala:57`).

The mirrors group exactly as their synchronous twins do on this page:

| Group              | Synchronous member             | Deferred mirror                     | Result                |
|--------------------|--------------------------------|-------------------------------------|-----------------------|
| Lifecycle          | `close()`                      | `closeAsync()`                      | `Async[Unit]`         |
| Lifecycle          | `fail(error)`                  | `failAsync(error)`                  | `Async[Unit]`         |
| Single element     | `write(a)`                     | `writeAsync(a)`                     | `Async[Boolean]`      |
| Bulk               | `writeAll(chunk)`              | `writeAllAsync(chunk)`              | `Async[Chunk[Elem1]]` |
| Specialized        | `writeInt(value)`              | `writeIntAsync(value)`              | `Async[Boolean]`      |
| Specialized        | `writeLong(value)`             | `writeLongAsync(value)`             | `Async[Boolean]`      |
| Specialized        | `writeFloat(value)`            | `writeFloatAsync(value)`            | `Async[Boolean]`      |
| Specialized        | `writeDouble(value)`           | `writeDoubleAsync(value)`           | `Async[Boolean]`      |
| Byte and character | `writeByte(b)`                 | `writeByteAsync(b)`                 | `Async[Boolean]`      |
| Byte and character | `writeBytes(buf, offset, len)` | `writeBytesAsync(buf, offset, len)` | `Async[Int]`          |
| Byte and character | `writeChar(value)`             | `writeCharAsync(value)`             | `Async[Boolean]`      |
| Byte and character | `writeShort(value)`            | `writeShortAsync(value)`            | `Async[Boolean]`      |
| Byte and character | `writeBoolean(value)`          | `writeBooleanAsync(value)`          | `Async[Boolean]`      |
| State checks       | `isClosed`                     | `isClosedAsync`                     | `Async[Boolean]`      |
| State checks       | `writeable()`                  | `writeableAsync()`                  | `Async[Boolean]`      |
| State checks       | `jvmType`                      | `jvmTypeAsync`                      | `Async[JvmType]`      |

Each mirror carries the same parameters and the same implicit evidence as its twin, so the specialized mirrors still ask for the subtype witness their twin asks for:

```scala
abstract class Writer[-Elem] {
  final def closeAsync(): Async[Unit]
  final def writeAsync(a: Elem): Async[Boolean]
  final def writeAllAsync[Elem1 <: Elem](chunk: Chunk[Elem1]): Async[Chunk[Elem1]]
  final def writeIntAsync(value: Int)(implicit ev: Int <:< Elem): Async[Boolean]
  final def writeBytesAsync(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Elem): Async[Int]
}
```

`jvmTypeAsync` is the mirror of `jvmType`, the writer's element representation (`JvmType.AnyRef` unless a subclass overrides it). It is the only mirror whose twin is not otherwise documented on this page.

### What `*Async` Does and Does Not Do

These are **cancellation-aware deferral adapters, not asynchronous I/O**. The library's own scaladoc says so in as many words (`Writer.scala:51`), and it is worth repeating because sixteen methods named `*Async` invite the opposite conclusion.

What a mirror does:

- **It defers one synchronous operation.** The wrapped call is first evaluated when the effect is driven, never when it is constructed, and it runs at most once however many times the effect is composed.
- **It closes the writer on cancellation.** Every mirror installs `close()` as its cancellation hook. If cancellation wins before the operation finishes, the writer is closed and the operation's result is discarded rather than published — the run then delivers nothing at all, so a cancelled handle must never be given to `block`.

What a mirror does not do:

- **It does not move the write to another thread.** Driving `writeAsync` calls `write` on whichever thread is driving.
- **It does not make a blocking write nonblocking.** When `write` blocks — a bounded buffer with no space, a socket with a full send window — driving `writeAsync` blocks in the same place for the same duration. `writeBytesAsync` on a `Writer.fromOutputStream` is a `java.io.OutputStream.write` behind an `Async`, and that call blocks.

:::warning[These methods are not nonblocking I/O]
A `Writer` whose `write` blocks still blocks when you drive its `*Async` mirror. The mirrors buy you deferral and a cancellation hook; they do not buy you a nonblocking writer. If you need writes that genuinely suspend rather than block, that is a different writer, not a different method on this one.
:::

Cancellation is cooperative and interrupts no thread, so the hook cannot abort a call already inside a blocking `write`. It calls `close()`, and that helps exactly when closing the writer is what releases the blocked call — which is true of a writer whose blocking wait is woken by closure, and false of one that ignores its own closed flag while parked. See [Running#cancel](../async.md#runningcancel) for what a cancelled run does and does not stop.

### Why There Is No `concatAsync` or `contramapAsync`

The structural combinators `concat` and `contramap` have no mirrors, and that is deliberate rather than an omission. The synchronous `Writer` protocol requires `write` to return its `Boolean` immediately. A composition callback that produced an `Async` would have no honest way to report that result: `write` cannot return a pending value, and inventing one — blocking on it, or guessing `true` — would break the very protocol the page opens with. Modelling asynchronous composition needs a separate async-writer architecture, not another method here.

## Capacity and Buffering

The default `writeable()` method returns `!isClosed`—it only tells you if the writer is closed, not whether the buffer has space. Bounded implementations can override `writeable()` to reflect remaining capacity, but this is not guaranteed by the interface. The important distinction:

- **`writeable()` returns `false`**: the writer is closed (permanent state)
- **`writeable()` returns `true` but `write()` would block**: the buffer is full but not closed. What happens next is implementation-defined: a writer backed by a bounded buffer may block the calling thread until space becomes available, while the writers in this library instead auto-close and return `false`.

The writers behind `NioWriters.fromByteBuffer` and its typed variants auto-close when the buffer fills, turning the full state into closure. Others may block indefinitely waiting for space.

## Error Handling

When the writer encounters an error, signal it with `fail(error)`. By default, `fail()` closes the writer; all subsequent `write()` calls return `false`.

If you override `fail()` to store the error internally, `write()` will throw it on the next call:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

class ErrorStoringWriter extends Writer[Int] {
  private var closed = false
  private var storedError: Option[Throwable] = None
  
  def isClosed = closed
  def write(a: Int): Boolean = {
    if (storedError.isDefined) throw storedError.get
    if (closed) false else true
  }
  def close() = { closed = true }
  override def fail(error: Throwable) = {
    storedError = Some(error)
    closed = true
  }
}

val w = new ErrorStoringWriter()
w.fail(new Exception("Stream error"))
try {
  w.write(42)  // throws the stored error
} catch {
  case e: Exception => println(s"Caught: ${e.getMessage}")
}
```

This gives you optional error propagation: use the default `fail()` for silent closure, or override it to propagate errors as exceptions.

## Construction

Writers are created using factory methods on the companion object, from adapters wrapping Java I/O, or by direct subclassing for custom implementations:

### Creating Predefined Writers

`Writer.closed` — A pre-closed writer that rejects all writes. Useful as a base case for empty streams:

```scala
object Writer {
  def closed: Writer[Any]
}
```

Create a pre-closed writer that rejects all writes:

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

Create a writer that accepts exactly one element, then auto-closes:

```scala mdoc:reset
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
println(w.write(42))        // true
println(w.write(99))        // false (already accepted one element)
println(w.isClosed)         // true
```

### Limited Capacity

`Writer.limited` — Creates a writer that accepts at most `n` elements from `inner`, then becomes closed. The dual of `Stream.take`. If `inner` closes before `n` elements are accepted, the limited writer also closes immediately without consuming the remaining capacity. 

:::note
The inner writer is not automatically closed—only the limited wrapper's `isClosed` returns `true` when the limit is reached. The inner writer stays open until someone explicitly calls `close()`.
:::

```scala
object Writer {
  def limited[Elem](inner: Writer[Elem], n: Long): Writer[Elem]
}
```

Limit a writer to accept at most n elements:

```scala mdoc:reset
import zio.blocks.streams.io.Writer
import scala.collection.mutable.Buffer

val collected = Buffer[Int]()
val inner = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = { collected += a; true }
  def close() = ()
}

val limited = Writer.limited(inner, 2)
println(limited.write(1))    // true
println(limited.write(2))    // true (space available)
println(limited.write(3))    // false (limit of 2 reached)
println(s"Collected: $collected")  // Collected: Buffer(1, 2)
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

Each of these operations also has a deferred mirror, listed in [Asynchronous Writes](#asynchronous-writes) above.

### Writing Elements

`Writer#write` — Pushes one element to the writer. Returns `true` on success, `false` if the writer is closed and cannot accept more elements. Throws if the writer was closed with an error via `Writer#fail`:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
}
```

Write elements and observe the return value indicating success or closure:

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

Write a chunk and observe how many elements were delivered:

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
  def writeInt(value: Int)(implicit ev: Int <:< Elem): Boolean
}
```

`writeLong` — Specialized `Long` write:

```scala
abstract class Writer[-Elem] {
  def writeLong(value: Long)(implicit ev: Long <:< Elem): Boolean
}
```

`writeFloat` — Specialized `Float` write:

```scala
abstract class Writer[-Elem] {
  def writeFloat(value: Float)(implicit ev: Float <:< Elem): Boolean
}
```

`writeDouble` — Specialized `Double` write:

```scala
abstract class Writer[-Elem] {
  def writeDouble(value: Double)(implicit ev: Double <:< Elem): Boolean
}
```

### Byte and Character Writes

`writeByte` — Specialized byte write. Avoids boxing when `Elem = Byte`. Requires evidence that `Byte` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeByte(b: Byte)(implicit ev: Byte <:< Elem): Boolean
}
```

`writeBytes` — Blocking bulk byte write. Calls `writeByte` for each byte in `buf[offset, offset+len)`, stopping early if the channel closes. Returns the number of bytes successfully written:

```scala
abstract class Writer[-Elem] {
  def writeBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Elem): Int
}
```

`writeChar` — Specialized `Char` write. Requires evidence that `Char` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeChar(value: Char)(implicit ev: Char <:< Elem): Boolean
}
```

`writeShort` — Specialized `Short` write. Requires evidence that `Short` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeShort(value: Short)(implicit ev: Short <:< Elem): Boolean
}
```

`writeBoolean` — Specialized `Boolean` write. Requires evidence that `Boolean` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeBoolean(value: Boolean)(implicit ev: Boolean <:< Elem): Boolean
}
```

### State Checks

`Writer#isClosed` — Returns `true` if the writer is closed. Monotone: once `true`, never returns `false`:

```scala
abstract class Writer[-Elem] {
  def isClosed: Boolean
}
```

`writeable` — Returns `true` if the next `write()` would accept a value without blocking (space is available and the writer is not closed). Default returns `!isClosed`. Buffered writers override for accuracy. Note the spelling: it is `writeable()`, not `writable()`; `Reader`'s counterpart is `readable()`.

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
  def write(a: Int) = {
    if (a < 10) { collected += a; true; }
    else false
  }
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

### Push Vs Pull

`Writer` is push-based (producer-driven), contrasting with `Reader` which is pull-based (consumer-driven):

| Aspect         | Reader                     | Writer                  |
|----------------|----------------------------|-------------------------|
| **Direction**  | Source → Consumer (pull)   | Producer → Sink (push)  |
| **Variance**   | Covariant (`+Elem`)        | Contravariant (`−Elem`) |
| **Blocking**   | `read()` may block         | `write()` may block     |
| **Signal end** | Caller-supplied sentinel, or a negative count from a bulk read | `close()` or `fail()`   |
| **Dual**       | Sink drains Reader         | Producer feeds Writer   |

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

### Bounded Implementation

This example shows how to implement a bounded Writer that wraps a fixed-capacity container and auto-closes when full. It demonstrates the protocol: `write()` returns `false` only on closure (not buffer fullness), and `writeable()` reflects closure state:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterBoundedImplementationExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterBoundedImplementationExample"
```

### Deferred and Cancellable Writes

This example makes the `*Async` caveat concrete. It shows that constructing a mirror writes nothing while driving it writes once, composes three mirrors into one effect, and then cancels a driven `writeAsync` whose write is parked — observing that cancellation closed the writer, that no element was recorded, and that the run delivers no value at all:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/writer/WriterAsyncExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterAsyncExample"
```

## See Also

- [Asynchronous Stream Execution](./async-execution.md#cancellation) — how cancellation reaches a stream's resources, and the asynchronous stream API the deferred mirrors sit beside
- [Async Reference](../async.md#runningcancel) — what `Running#cancel` stops, why a cancelled run never delivers, and why the cancel hook cannot interrupt a blocked thread
- [Reader](./reader.md) — the pull-based dual of this type, and the reader kinds a sink drains
- [Sink](./sink.md) — the consumer side of a stream, which drains a `Reader` rather than feeding a `Writer`
- [Zero-Boxing Streams](./zero-boxing.md) — why the specialized write family exists and how a primitive lane is chosen
