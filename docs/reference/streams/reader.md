---
id: reader
title: "Reader"
---

`Reader[+Elem]` is a **pull-based source of elements** that yields values one at a time until closed. Elements are pulled on demand by the consumer (typically a `Sink`), making it ideal for lazy, memory-efficient data streaming. The fundamental operations are `read(sentinel)` — returns the next element or a sentinel when exhausted — and `close()` — signals stream end and releases resources.

`Reader`:
- is lazy and pull-based — nothing happens until the consumer calls `read()`
- is not thread-safe — designed for single-threaded consumption
- uses a sentinel protocol for end-of-stream signaling to avoid boxing
- dispatches on `jvmType` to use specialized, unboxed reads for primitive types
- is the compilation target of `Stream` — when a stream runs, it becomes a `Reader`

```scala
abstract class Reader[+Elem] {
  def read[A >: Elem](sentinel: A): A
  def close(): Unit
  def isClosed: Boolean
  def readable(): Boolean
  def readInt(sentinel: Long)(using Elem <:< Int): Long
  def readLong(sentinel: Long)(using Elem <:< Long): Long
  def readFloat(sentinel: Double)(using Elem <:< Float): Double
  def readDouble(sentinel: Double)(using Elem <:< Double): Double
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
  def skip(n: Long): Unit
  def readAll[A >: Elem](): Chunk[A]
  def reset(): Unit
  def setLimit(n: Long): Boolean
  def setSkip(n: Long): Boolean
  def setRepeat(): Boolean
  def withRelease(release: () => Unit): Reader[Elem]
}
```

## Overview

`Reader[+Elem]` is the low-level, pull-based interface that powers ZIO Blocks streams. When you call a terminal operation like `stream.run(sink)`, the stream is compiled into a `Reader`, which the sink then drains element-by-element. Most users never interact with `Reader` directly — but understanding it clarifies how streams work internally.

**Architecture**:

```
Stream[E, A] ──(compile)──> Reader[A]
                              │
                              └─(drain via Sink)──> Either[E, Z]
```

Key characteristics:

- **Lazy**: No work happens until `read()` is called. All stream transformations are applied during compilation or on each pull.
- **Pull-based**: The consumer (sink) controls the pace. If the consumer stops calling `read()`, no more work is done.
- **Sentinel protocol**: To avoid boxing, `read()` returns a caller-chosen sentinel value when exhausted. For primitives, specialized methods (`readInt`, `readLong`, etc.) widen the return type and use fixed sentinels.
- **Resource-safe**: Resources opened during stream execution are tracked in the compiled `Reader` and released in its `close()` method, which is always called in a `finally` block.

## Construction

### Creating Predefined Readers

`Reader.closed` — An already-closed reader that emits no elements. Useful as a base case or for empty streams.

```scala
object Reader {
  def closed: Reader[Nothing]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.closed
println(r.isClosed)        // true
println(r.read(-1))        // -1 (the sentinel)
```

### From Collections

`Reader.fromChunk` — Creates a reader backed by a `Chunk`. Dispatches on the element type to use specialized, unboxed reads for primitives.

```scala
object Reader {
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Reader[A]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
val r = Reader.fromChunk(chunk)

var v = r.read(-1)
while (v != -1) {
  println(v)
  v = r.read(-1)
}
// Output: 10, 20, 30
```

`Reader.fromIterable` — Creates a reader from any `Iterable`. Works with lists, sets, vectors, and other collections.

```scala
object Reader {
  def fromIterable[A](it: Iterable[A]): Reader[A]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val list = List("a", "b", "c")
val r = Reader.fromIterable(list)

var v = r.read(null)
while (v != null) {
  println(v)
  v = r.read(null)
}
// Output: a, b, c
```

`Reader.fromRange` — Creates a reader from a Scala `Range`. Optimized for integer ranges without allocation.

```scala
object Reader {
  def fromRange(range: Range): Reader[Int]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.fromRange(1 to 5)

var v = r.read(-1)
while (v != -1) {
  println(v)
  v = r.read(-1)
}
// Output: 1, 2, 3, 4, 5
```

### From I/O

`Reader.fromInputStream` — Wraps a `java.io.InputStream` as a `Reader[Int]`, where each byte is widened to `Int` (0–255). This avoids boxing on `.map`/`.filter` since `Function1` is specialized for `Int`.

```scala
object Reader {
  def fromInputStream(is: InputStream): Reader[Int]
}
```

`Reader.fromReader` — Wraps a `java.io.Reader` as a `Reader[Char]` for character-based I/O.

```scala
object Reader {
  def fromReader(r: java.io.Reader): Reader[Char]
}
```

### Single Element

`Reader.single` — Creates a reader that emits exactly one element, then closes. Primitive types use specialized variants (`singleInt`, `singleLong`, etc.) for zero-boxing.

```scala
object Reader {
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): Reader[A]
  def singleInt(value: Int): Reader[Int]
  def singleLong(value: Long): Reader[Long]
  def singleFloat(value: Float): Reader[Float]
  def singleDouble(value: Double): Reader[Double]
  def singleChar(value: Char): Reader[Char]
  def singleShort(value: Short): Reader[Short]
  def singleByte(value: Byte): Reader[Int]
  def singleBoolean(value: Boolean): Reader[Boolean]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.single(42)
println(r.read(-1))        // 42
println(r.read(-1))        // -1 (sentinel, reader is closed)
```

### Infinite & Repeating

`Reader.repeat` — Creates an infinite reader that always emits the same value.

```scala
object Reader {
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Reader[A]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.repeat(1)
var count = 0
var v = r.read(-1)
while (count < 3) {
  println(v)
  v = r.read(-1)
  count += 1
}
// Output: 1, 1, 1
```

`Reader.repeated` — Restarts an inner reader each time it closes cleanly. Used by `Stream.repeated` to create indefinitely repeating streams.

```scala
object Reader {
  def repeated[A](inner: Reader[A]): Reader[A]
}
```

### Unfold (State Machine)

`Reader.unfold` — Creates a reader by unfolding state with a function. Returns `None` to signal completion, or `Some((elem, nextState))` to emit an element and advance state.

```scala
object Reader {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Reader[A]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.unfold(1) { s =>
  if (s > 3) None else Some((s, s + 1))
}

var v = r.read(-1)
while (v != -1) {
  println(v)
  v = r.read(-1)
}
// Output: 1, 2, 3
```

## Core Operations

### Pulling Elements

`read` — Pulls the next element, or returns `sentinel` if the reader is closed and empty.

```scala
abstract class Reader[+Elem] {
  def read[A >: Elem](sentinel: A): A
}
```

The sentinel value is caller-chosen and should never appear as a real element. For reference types, `null` is convenient. For primitives, use a value outside the domain (e.g., `-1` for unsigned bytes, `Long.MinValue` for `Int`).

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20))
val v1 = r.read(-1)        // 10
val v2 = r.read(-1)        // 20
val v3 = r.read(-1)        // -1 (sentinel, reader is closed)
```

### Primitive Specialization

For primitive types, specialized methods avoid boxing by widening the return type:

`readInt` — Sentinel-return `Int` pull. Returns the element widened to `Long`, or `sentinel` when closed. The sentinel must lie outside `[Int.MinValue, Int.MaxValue]` (typically `Long.MinValue`).

```scala
abstract class Reader[+Elem] {
  def readInt(sentinel: Long)(using Elem <:< Int): Long
}
```

`readLong` — Sentinel-return `Long` pull. Returns the element, or `sentinel` when closed. The sentinel must be a value that never appears in the stream (typically `Long.MaxValue`).

```scala
abstract class Reader[+Elem] {
  def readLong(sentinel: Long)(using Elem <:< Long): Long
}
```

`readFloat` — Sentinel-return `Float` pull. Returns the element widened to `Double`, or `sentinel` when closed.

```scala
abstract class Reader[+Elem] {
  def readFloat(sentinel: Double)(using Elem <:< Float): Double
}
```

`readDouble` — Sentinel-return `Double` pull. Returns the element, or `sentinel` when closed. The sentinel must be a value outside the domain (typically `Double.MaxValue`).

```scala
abstract class Reader[+Elem] {
  def readDouble(sentinel: Double)(using Elem <:< Double): Double
}
```

These specialized methods are the hot path for primitive streams — they avoid allocation and boxing entirely.

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
val sentinel = Long.MinValue

var v = r.readInt(sentinel)(using scala.compiletime.summonInline)
// Error: this would require direct evidence, but in real code it comes from dispatch
```

### Byte-Level Reading

`readByte` — Reads a single byte (0–255), widened to `Int`. Returns `-1` when the reader is closed. Dispatches on `jvmType` for zero-boxing when the reader is specialized.

```scala
abstract class Reader[+Elem] {
  def readByte(): Int
}
```

`readBytes` — Bulk byte read into a caller-supplied buffer, mirroring `java.io.InputStream#read(byte[], int, int)`.

- Blocks until at least 1 byte is available.
- Returns the number of bytes read (`1 <= r <= len`).
- Returns `-1` when closed and empty.
- Returns `0` immediately when `len == 0`.

```scala
abstract class Reader[+Elem] {
  def readBytes(buf: Array[Byte], offset: Int, len: Int): Int
}
```

### Character and Numeric Specialization

`readChar` — Sentinel-return `Char` pull. Returns the element widened to `Int`, or `sentinel` when closed. Requires evidence that `Elem <:< Char`.

```scala
abstract class Reader[+Elem] {
  def readChar(sentinel: Int)(using Elem <:< Char): Int
}
```

`readShort` — Sentinel-return `Short` pull. Returns the element widened to `Int`, or `sentinel` when closed.

```scala
abstract class Reader[+Elem] {
  def readShort(sentinel: Int)(using Elem <:< Short): Int
}
```

`readBoolean` — Sentinel-return `Boolean` pull. Returns `1` for `true`, `0` for `false`, or `sentinel` when closed. The sentinel must lie outside `[0, 1]` (typically `-1`).

```scala
abstract class Reader[+Elem] {
  def readBoolean(sentinel: Int)(using Elem <:< Boolean): Int
}
```

### Bulk Operations

`readAll` — Drains the entire reader into a `Chunk`. Dispatches on `jvmType` for zero-boxing on primitive readers.

```scala
abstract class Reader[+Elem] {
  def readAll[A >: Elem](): Chunk[A]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
val all = r.readAll()
println(all)  // Chunk(10, 20, 30)
```

`skip` — Eagerly discards the first `n` elements. Dispatches on `jvmType` for zero-boxing when possible.

```scala
abstract class Reader[+Elem] {
  def skip(n: Long): Unit
}
```

### State Queries

`isClosed` — Returns `true` if the reader is closed. Monotone: once `true`, never returns `false`.

```scala
abstract class Reader[+Elem] {
  def isClosed: Boolean
}
```

`readable` — Returns `true` if the next `read()` would return a value (not the sentinel). Default implementation returns `!isClosed`. Buffered readers override for accuracy.

```scala
abstract class Reader[+Elem] {
  def readable(): Boolean
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2))
println(r.readable())      // true
r.read(-1)
println(r.readable())      // true
r.read(-1)
println(r.readable())      // false
```

## Composition

### Concatenation

`concat` — Concatenates this reader with `next`. When this reader is exhausted, it is closed and elements are pulled from `next` (evaluated lazily). Optimized for left-associative chains.

```scala
abstract class Reader[+Elem] {
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
}
```

`++` — Alias for `concat`. Syntactic sugar for composing readers.

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r1 = Reader.fromChunk(Chunk(1, 2))
val r2 = Reader.fromChunk(Chunk(3, 4))
val combined = r1 ++ r2

var v = combined.read(-1)
while (v != -1) {
  println(v)
  v = combined.read(-1)
}
// Output: 1, 2, 3, 4
```

**Optimization**: If this reader is already a `ConcatReader`, the thunk is appended to its internal array and `this` is returned (mutable append, O(1) amortized). Otherwise a new `ConcatReader` is created. This ensures that left-associative chains like `a ++ b ++ c ++ d` compile into a single flat `ConcatReader` with O(1) per-element read, rather than O(n) nested wrappers.

## Resource Management

### Closing

`close` — Signals close from the consumer side. Implementations should set internal closed state and wake any blocked readers.

```scala
abstract class Reader[+Elem] {
  def close(): Unit
}
```

`withRelease` — Wraps this reader so that `release` runs after `close()`. Useful for attaching cleanup logic.

```scala
abstract class Reader[+Elem] {
  def withRelease(release: () => Unit): Reader[Elem]
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

var cleaned = false
val r = Reader.fromChunk(Chunk(1, 2)).withRelease { () =>
  cleaned = true
  println("Cleaned up")
}

r.close()
println(cleaned)  // true
```

## Pushdown Operations

Readers can sometimes handle skip, limit, and repeat operations natively (O(1), zero per-element cost). These methods attempt that; if the reader cannot handle it natively, they return `false` and the caller must wrap the reader.

`setSkip` — Attempts to set a skip (drop) on this reader. Returns `true` if handled natively, `false` if the caller must wrap. When `true`, the next `skip` elements are discarded before producing. After `reset()`, the skip is re-applied.

```scala
abstract class Reader[+Elem] {
  def setSkip(n: Long): Boolean
}
```

`setLimit` — Attempts to set a limit on this reader so it produces at most `n` elements. Returns `true` if handled natively, `false` if the caller must wrap. After `reset()`, the limit is re-applied from the new start position.

```scala
abstract class Reader[+Elem] {
  def setLimit(n: Long): Boolean
}
```

`setRepeat` — Attempts to set this reader into repeat-forever mode, so it restarts from the beginning whenever it would otherwise close. Returns `true` if handled natively, `false` if the caller must wrap.

```scala
abstract class Reader[+Elem] {
  def setRepeat(): Boolean
}
```

`reset` — Rewinds this reader to its initial state, as if freshly constructed. After `reset()`, all elements are available again from the beginning.

Not all readers support this. Readers backed by one-shot resources (InputStreams, `java.io.Reader`s) throw `UnsupportedOperationException`.

```scala
abstract class Reader[+Elem] {
  def reset(): Unit
}
```

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3))
println(r.read(-1))  // 1
r.reset()
println(r.read(-1))  // 1 (back to the beginning)
```

## Integration with Stream

`Reader` is the compilation target of `Stream`. When you call a terminal operation, the stream compiles to a `Reader`, which is then consumed.

You can also open a stream for manual element-by-element pulling using `Stream.start`:

```scala
import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

  $(reader) { r =>
    var v = r.read(-1)
    while (v != -1) {
      println(v)   // prints 1, 2, 3, 4, 5
      v = r.read(-1)
    }
  }
  // reader is closed automatically when scope exits
}
```

:::caution
Avoid holding references to a `Reader` obtained via `start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Integration with Sink

`Reader` and `Sink` are dual: `Reader` is the source, `Sink` is the consumer. When you call `stream.run(sink)`, the stream compiles to a `Reader`, and the sink drains it:

```scala
abstract class Sink[+E, -A, +Z] {
  def drain[A2 <: A](reader: Reader[A2]): Either[E, Z]
}
```

The sink calls `read()` repeatedly until the reader is closed, transforming the sequence of elements into a result of type `Z`.

For example, `Sink.collectAll` drains all elements and returns them as a `Chunk`:

```scala
val result = Stream.range(1, 10)
  .run(Sink.collectAll[Int])
// result: Either[Nothing, Chunk[Int]] = Right(Chunk(1, 2, 3, ..., 9))
```

## Implementation Notes

### Sentinel Protocol

The `read(sentinel)` method uses a caller-chosen sentinel value to signal end-of-stream. This avoids the allocation and boxing of wrapping results in `Option` or `Either`. The sentinel must be a value that never appears as a real element.

For reference types, `null` is the natural sentinel. For primitives, specialized methods widen the return type and use fixed sentinels:

| Type   | Sentinel     | Method            | Return Type |
|--------|--------------|-------------------|-------------|
| `Int`  | `Long.MinValue` | `readInt(sentinel: Long)` | `Long`      |
| `Long` | `Long.MaxValue` | `readLong(sentinel: Long)` | `Long`      |
| `Float` | `Double.MaxValue` | `readFloat(sentinel: Double)` | `Double`   |
| `Double` | `Double.MaxValue` | `readDouble(sentinel: Double)` | `Double`   |

:::note
The `Long.MaxValue` and `Double.MaxValue` sentinels coincide with valid data values, so streams containing exactly those values may be misinterpreted as end-of-stream in specialized paths. This is a deliberate tradeoff: clarity and simplicity over exotic corner cases.
:::

### JVM Type Dispatch

`Reader` dispatches on `jvmType` to choose between unboxed and boxed pull paths. Subclasses with primitive specialization override `jvmType`:

```scala
abstract class Reader[+Elem] {
  def jvmType: JvmType = JvmType.AnyRef
}
```

For example, a `Reader[Int]` backed by a `Chunk[Int]` overrides `jvmType` to return `JvmType.Int`. Then, methods like `readAll` check `jvmType` and dispatch to the unboxed `readInt(sentinel: Long)` path instead of boxing.

### Thread Safety

`Reader` is **not thread-safe**. It is designed for single-threaded, pull-based consumption. Do not share a `Reader` across threads without external synchronization. If you need concurrent consumption, wrap the reader in a thread-safe queue or use a concurrent streaming library.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Reader Construction

This example demonstrates the most common reader factories: `fromChunk`, `fromIterable`, `fromRange`, and `single`.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/reader/ReaderBasicConstructionExample.scala")
```

```bash
sbt "schema-examples/runMain reader.ReaderBasicConstructionExample"
```

### Primitive Specialization and Bulk Operations

This example shows how primitive readers avoid boxing through `jvmType` dispatch, and demonstrates `readAll` and `skip` for bulk operations.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/reader/ReaderPrimitiveSpecializationExample.scala")
```

```bash
sbt "schema-examples/runMain reader.ReaderPrimitiveSpecializationExample"
```

### Composition and Resource Management

This example demonstrates reader composition with `++`, resource cleanup with `withRelease`, and integration with `Stream.start` for manual pulling.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/reader/ReaderCompositionExample.scala")
```

```bash
sbt "schema-examples/runMain reader.ReaderCompositionExample"
```
