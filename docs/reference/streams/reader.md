---
id: reader
title: "Reader"
---

`Reader[+Elem]` is the **pull-based source that powers ZIO Blocks streams**. When you call a terminal operation like `stream.run(sink)`, the stream compiles into a `Reader`, which yields values one at a time on demand until closed.

`Reader` is sealed into `Reader.SyncReader[Elem]` and `Reader.AsyncReader[Elem]`. A synchronous reader's `read` and `close` return directly; an asynchronous reader's pull and lifecycle methods return `Async`. Most users never interact with either subtype directly, but understanding them clarifies how streams work internally.

The compilation and execution flow:

```
Stream[E, A] ──(compile)──> Reader[A]
                              │
                              └─(drain via Sink)──> Either[E, Z]
```

`Reader`:
- Is lazy and pull-based — `Stream` transformations don't run until `read()` is called, running in constant space one element at a time
- Is a single-consumer cursor — do not share a `SyncReader` between threads or overlap operations on an `AsyncReader`
- Uses a sentinel protocol where callers specify the end-of-stream value; all eight JVM primitives have exact physical pull methods: `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`
- Dispatches on `Reader#jvmType`, which describes the reader's physical representation and therefore the exact pull method it supports, not merely the static or logical element type
- Is the compilation target of `Stream` — when a stream runs, it becomes a `Reader`
- Transfers lifecycle responsibility explicitly: terminals and bracketed APIs close their owned reader, while callers of `startAsync` own the returned reader and must await `close()`
- Supports composition by chaining readers through transformations without materializing intermediate data

Here is the core `Reader` interface with the most essential methods:

```scala
sealed abstract class Reader[+Elem]

abstract class Reader.SyncReader[+Elem] extends Reader[Elem] {
  def read[A >: Elem](sentinel: A): A
  def readAll[A >: Elem](): Chunk[A]
  def readN[A >: Elem](n: Int): Chunk[A]
  def readUpToN[A >: Elem](n: Int): Chunk[A]
  def isClosed: Boolean
  def readable(): Boolean
  def close(): Unit
  def toAsync: Reader.AsyncReader[Elem]
}

abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def read[A >: Elem](sentinel: A): Async[A]
  def readAll[A >: Elem](): Async[Chunk[A]]
  def readN[A >: Elem](n: Int): Async[Chunk[A]]
  def readUpToN[A >: Elem](n: Int): Async[Chunk[A]]
  def isClosed: Async[Boolean]
  def readable(): Async[Boolean]
  def close(): Async[Unit]
  // JVM only: def toSync: Reader.SyncReader[Elem]
}
```

The sealed root contains only kind-independent composition and metadata (`++`, `concat`, `concatAsync`, `withReleaseAsync`, and `jvmType`); it cannot be pulled, queried, or closed directly. Those operations belong to one of the two concrete reader kinds. Every primitive, bulk, lifecycle, and pushdown method on `AsyncReader` has the same parameters as its `SyncReader` counterpart but returns its result in `Async` (for example, `readInt: Async[Long]`, `readBytes: Async[Int]`, `skip: Async[Unit]`, and `setLimit: Async[Boolean]`). The eight physical primitive methods are `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`; a primitive `jvmType` is a contract that the corresponding method works, even when covariance has widened the reader's static element type.

An `AsyncReader` permits one active operation at a time. Await each pull or control operation before starting the next; `close()` participates in the same lifecycle, cancels or joins active work, and must itself be awaited. Closing is the owner's responsibility and should happen exactly once (repeated close is tolerated by library readers). `SyncReader#toAsync` is cross-platform and returns a lifecycle-preserving view: closing either side closes the same underlying source. `AsyncReader#toSync` exists only on the JVM, blocks the calling thread, and likewise shares ownership rather than copying the reader; do not continue consuming through both views.

## Quick Showcase

Here's how to create and drain a `Reader`:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import scala.collection.mutable.Buffer

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
val collected = Buffer[Int]()

// Pull elements until sentinel
def drainAll(): Unit = {
  val elem = r.read(-1)
  if (elem != -1) {
    collected += elem
    drainAll()
  }
}
drainAll()

println(s"Collected: $collected")
```

## Motivation

Imagine you're processing a massive CSV file—millions of rows of customer data. Your first instinct is to load it all into memory as a `List[Row]`, transform it, filter it, and then write the results. This works fine for small files, but one day someone feeds you a 50GB dataset and your application crashes with `OutOfMemoryError`. You've hit the fundamental problem of eager evaluation: **you must load everything before you can do anything**, and if the data is bigger than available memory, you're stuck.

Even if you manage to fit the data in memory, you've paid the startup cost upfront. If your pipeline only needs the first 100 rows to produce a result, you've wasted time and energy materializing the other millions. And if something fails partway through—a database connection drops, a file is corrupted—you've already consumed resources and may have inconsistencies to clean up.

The streaming intuition is different: instead of pulling all data at once, what if the consumer asked the producer "give me the next element?" one at a time? This way, you never hold more than one element in memory, you only do work on elements you actually use, and you can stop immediately when you have enough.

`Reader` embodies this pull-based philosophy. Rather than materializing a `List`, a `Stream` compiles down to a `Reader`—a stateful object that produces one element each time you call `read()`. The consumer (a `Sink`) drives the pace: it calls `read()` when ready, and the `Reader` computes and returns the next value. When the stream is exhausted, `Reader` returns a sentinel—a special value you provide—signaling "no more data." No exceptions, no null, no boxing overhead.

`Reader` shines when you're processing large, unbounded, or expensive-to-produce data sources: database result sets, network streams, log files, sensor data, or any pipeline where memory or time efficiency matters. Instead of hoping your data fits in memory, you pay a constant, predictable cost per element.

## Construction

Several ways to create a `Reader`, from predefined singletons to collections and I/O sources:

Factories such as `closed`, `fromChunk`, `fromIterable`, `fromRange`, `single`, `repeat`, and `unfold` return `SyncReader`. `unfoldAsync` returns a native `AsyncReader`; its state callback is lazy, only one callback is active, and state is committed only after a successful current-generation callback. `repeated` preserves whether its input is synchronous or asynchronous. Composition also preserves asynchronous work: `concatAsync` lazily acquires the next reader and `withReleaseAsync` awaits asynchronous cleanup. Asynchronous children are supported throughout the reader graph.

### Creating Predefined Readers

`Reader.closed` — An already-closed reader that emits no elements. Useful as a base case or for empty streams:

```scala
object Reader {
  def closed: Reader.SyncReader[Nothing]
}
```

Here's how to create and use a closed reader:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.closed
println(r.isClosed)        // true
println(r.read(-1))        // -1 (the sentinel)
```

### From Collections

`Reader.fromChunk` — Creates a reader backed by a `Chunk`. Dispatches on the element type to use specialized, unboxed reads for primitives:

```scala
object Reader {
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
}
```

Create a reader from a chunk and drain its elements:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
val r = Reader.fromChunk(chunk)

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output: 10, 20, 30
```

`Reader.fromIterable` — Creates a reader from any `Iterable`. Works with lists, sets, vectors, and other collections:

```scala
object Reader {
  def fromIterable[A](it: Iterable[A]): Reader.SyncReader[A]
}
```

Create a reader from a list and consume its elements:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val list = List("a", "b", "c")
val r = Reader.fromIterable(list)

def drain(): Unit = {
  val v = r.read(null)
  if (v != null) {
    println(v)
    drain()
  }
}
drain()
// Output: a, b, c
```

`Reader.fromRange` — Creates a reader from a Scala `Range`. Optimized for integer ranges without allocation:

```scala
object Reader {
  def fromRange(range: Range): Reader.SyncReader[Int]
}
```

Create a reader from a range and drain the integers:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.fromRange(1 to 5)

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output: 1, 2, 3, 4, 5
```

### From I/O

`Reader.fromInputStream` — Wraps a `java.io.InputStream` as a `SyncReader[Byte]`. `readByte()` exposes the unsigned `0`–`255` view and reserves `-1` exclusively for EOF; ordinary element pulls retain `Byte` values:

```scala
object Reader {
  def fromInputStream(is: InputStream): Reader.SyncReader[Byte]
}
```

`Reader.fromReader` — Wraps a `java.io.Reader` as a `SyncReader[Char]` for character-based I/O:

```scala
object Reader {
  def fromReader(r: java.io.Reader): Reader.SyncReader[Char]
}
```

### Single Element

`Reader.single` — Creates a reader that emits exactly one element, then closes. Primitive types use specialized variants for zero-boxing:

```scala
object Reader {
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
  def singleInt(value: Int): Reader.SyncReader[Int]
  def singleLong(value: Long): Reader.SyncReader[Long]
  def singleFloat(value: Float): Reader.SyncReader[Float]
  def singleDouble(value: Double): Reader.SyncReader[Double]
  def singleChar(value: Char): Reader.SyncReader[Char]
  def singleShort(value: Short): Reader.SyncReader[Short]
  def singleByte(value: Byte): Reader.SyncReader[Byte]
  def singleBoolean(value: Boolean): Reader.SyncReader[Boolean]
}
```

When you use `Reader.single`, behavior differs between reference types and primitives. The `JvmType.Infer[A]` implicit parameter enables compile-time type detection, automatically selecting the appropriate implementation (specialized primitive or reference-type generic).

For reference types like String, `Reader.single("hello")` stores the element directly and uses an internal sentinel object (`EndOfStream`) to signal end-of-stream. You read via the generic `SyncReader#read[A](sentinel)` method, passing your own sentinel value. On the first call, you get your string; on subsequent calls, you receive the sentinel you provided, allowing you to detect stream closure.

For primitive types, `Reader.single(42)` could naively box the integer, but the library avoids this penalty entirely via `SingletonPrim`—a zero-boxing specialization that stores the primitive unboxed in memory. The `JvmType.Infer` implicit detects this at compile time and routes you through specialized factory methods (`Reader.singleInt`, `Reader.singleLong`, etc.) and specialized read methods (`Reader#readInt`, `Reader#readLong`, etc.). Both storage and retrieval stay unboxed, maintaining zero-copy efficiency.

`Reader.singleByte` returns `SyncReader[Byte]` and reports `JvmType.Byte`. Its physical scalar pull is `readByte(): Int`, which returns the unsigned byte value `0`–`255` or `-1` at EOF.

Create and read from a single-element reference-type reader with a custom sentinel:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.single("hello")
val sentinel = "END"
println(r.read(sentinel))    // hello
println(r.read(sentinel))    // END (sentinel, reader is closed)
```

For primitive types, use the specialized factory and read methods. `SyncReader#readInt` takes a `Long` sentinel and returns `Long`; `AsyncReader#readInt` takes the same sentinel and returns `Async[Long]`:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.singleInt(100)
val sentinel = Long.MinValue
val v1 = r.readInt(sentinel)
println(v1)    // 100
val v2 = r.readInt(sentinel)
println(v2)    // -9223372036854775808 (sentinel, reader is closed)
```

### Infinite & Repeating

`Reader.repeat` — Creates an infinite reader that always emits the same value:

```scala
object Reader {
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
}
```

Create an infinite reader that repeatedly emits the same value:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.repeat(1)

def drainN(n: Int): Unit = {
  if (n > 0) {
    val v = r.read(-1)
    println(v)
    drainN(n - 1)
  }
}
drainN(3)
// Output: 1, 1, 1
```

`Reader.repeated` — Restarts an inner reader each time it closes cleanly. Used by `Stream.repeated` to create indefinitely repeating streams:

```scala
object Reader {
  def repeated[A](inner: SyncReader[A]): SyncReader[A]
  def repeated[A](inner: AsyncReader[A]): AsyncReader[A]
  def repeated[A](inner: Reader[A]): Reader[A]
}
```

### Unfold (State Machine)

`Reader.unfold` — Creates a reader by unfolding state with a function. Returns `None` to signal completion, or `Some((elem, nextState))` to emit an element and advance state:

```scala
object Reader {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Reader.SyncReader[A]
  def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]]): Reader.AsyncReader[A]
}
```

Create a reader that unfolds state incrementally until completion:

```scala mdoc:reset
import zio.blocks.streams.io.Reader

val r = Reader.unfold(1) { s =>
  if (s > 3) None else Some((s, s + 1))
}

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output: 1, 2, 3
```

## Core Operations

These methods form the primary interface for consuming elements and querying reader state:

### Pulling Elements

`read` pulls the next element, or produces `sentinel` if the reader is closed and empty. This is the fundamental operation. The synchronous and asynchronous signatures are distinct:

```scala
abstract class Reader.SyncReader[+Elem] {
  def read[A >: Elem](sentinel: A): A
}

abstract class Reader.AsyncReader[+Elem] {
  def read[A >: Elem](sentinel: A): Async[A]
}
```

The sentinel value is caller-chosen and should never appear as a real element. For reference types, `null` is convenient. For primitives, use a value outside the domain (e.g., `-1` for unsigned bytes, `Long.MinValue` for `Int`):

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20))
val v1 = r.read(-1)        // 10
val v2 = r.read(-1)        // 20
val v3 = r.read(-1)        // -1 (sentinel, reader is closed)
```

### Primitive Specialization

For primitive types, specialized methods avoid boxing by widening the return type.

`Reader#readInt` — Sentinel-return `Int` pull. Returns the element widened to `Long`, or `sentinel` when closed. The sentinel must lie outside `[Int.MinValue, Int.MaxValue]` (typically `Long.MinValue`):

```scala
abstract class Reader.SyncReader[+Elem] {
  def readInt(sentinel: Long)(using Elem <:< Int): Long
}

abstract class Reader.AsyncReader[+Elem] {
  def readInt(sentinel: Long)(using Elem <:< Int): Async[Long]
}
```

Why widen to `Long`? If `Reader#readInt` returned `Int`, you couldn't distinguish a real element from the sentinel—both would fit in the int range. By widening to `Long`, the sentinel (e.g., `Long.MinValue`) lies outside the possible int domain, allowing reliable end-of-stream detection. Cast the result back to `Int` if needed: `r.readInt(Long.MinValue).toInt`.

`Reader#readLong` — Sentinel-return `Long` pull. This low-level scalar method cannot distinguish EOF from a real element equal to the caller's sentinel:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readLong(sentinel: Long)(using Elem <:< Long): Long
}

abstract class Reader.AsyncReader[+Elem] {
  def readLong(sentinel: Long)(using Elem <:< Long): Async[Long]
}
```

The scalar API necessarily permits a collision with the caller's sentinel. Collision-free internal pulls preserve the complete `Long` domain by calling `readLongs` with a length-one array and using its returned count (`-1` for EOF, `1` for data) as status. Custom full-domain loops should use the same pattern.

`Reader#readFloat` — Sentinel-return `Float` pull. Returns the element widened to `Double`, or `sentinel` when closed:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readFloat(sentinel: Double)(using Elem <:< Float): Double
}

abstract class Reader.AsyncReader[+Elem] {
  def readFloat(sentinel: Double)(using Elem <:< Float): Async[Double]
}
```

Like `Reader#readInt`, widening to `Double` allows the sentinel to lie safely outside the float domain. A float value will always fit in the lower precision bits of the double result, and the sentinel (typically `Double.MaxValue`) occupies the upper range. This ensures you can reliably distinguish real float elements from end-of-stream. Cast back to `Float` if needed: `r.readFloat(Double.MaxValue).toFloat`.

`Reader#readDouble` — Sentinel-return `Double` pull. Returns the element, or `sentinel` when closed. The sentinel must be a value outside the domain (typically `Double.MaxValue`):

```scala
abstract class Reader.SyncReader[+Elem] {
  def readDouble(sentinel: Double)(using Elem <:< Double): Double
}

abstract class Reader.AsyncReader[+Elem] {
  def readDouble(sentinel: Double)(using Elem <:< Double): Async[Double]
}
```

Like scalar `readLong`, scalar `readDouble` cannot reserve a collision-free value (and NaN comparisons add another trap). Collision-free internal pulls call `readDoubles` with a length-one array and use its returned count as EOF/data status, preserving infinities, every NaN payload, and either zero. Custom full-domain loops should use the same pattern.

These specialized methods are the hot path for primitive streams — they avoid allocation and boxing entirely:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
val sentinel = Long.MinValue

val v = r.readInt(sentinel)
```

### Byte-Level Reading

`Reader#readByte` — Reads a single byte (0–255), widened to `Int`. Returns `-1` when the reader is closed. Dispatches on `Reader#jvmType` for zero-boxing when the reader is specialized:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readByte(): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readByte(): Async[Int]
}
```

Read bytes one at a time from a reader until end-of-stream:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // Hello in ASCII bytes
val is = new ByteArrayInputStream(bytes)
val r = Reader.fromInputStream(is)

def drainBytes(): Unit = {
  val b = r.readByte()
  if (b != -1) {
    println(s"Byte: $b (${b.toChar})")
    drainBytes()
  }
}
drainBytes()
// Output:
// Byte: 72 (H)
// Byte: 101 (e)
// Byte: 108 (l)
// Byte: 108 (l)
// Byte: 111 (o)
```

`Reader#readBytes` — Bulk byte read into a caller-supplied buffer, mirroring `java.io.InputStream#read(byte[], int, int)`. The behavior is:

- A `SyncReader` blocks until at least 1 byte is available; an `AsyncReader` represents that wait in `Async`.
- Returns the number of bytes read (`1 <= r <= len`).
- Returns `-1` when closed and empty.
- Returns `0` immediately when `len == 0`.

The method signature is:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readBytes(buf: Array[Byte], offset: Int, len: Int)(using Elem <:< Byte): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readBytes(buf: Array[Byte], offset: Int, len: Int)(using Elem <:< Byte): Async[Int]
}
```

Read multiple bytes into a buffer in bulk with a loop pattern:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // The word Hello
val is = new ByteArrayInputStream(bytes)
val r = Reader.fromInputStream(is)

val buffer = new Array[Byte](3)

def drainBulk(): Unit = {
  val bytesRead = r.readBytes(buffer, 0, 3)
  if (bytesRead > 0) {
    val chunk = buffer.take(bytesRead).map(_.toChar).mkString
    println(s"Read $bytesRead bytes: $chunk")
    drainBulk()
  }
}
drainBulk()
// Output:
// Read 3 bytes: Hel
// Read 2 bytes: lo
```

### Character and Numeric Specialization

`Reader#readChar` — Sentinel-return `Char` pull. Returns the element widened to `Int`, or `sentinel` when closed. Requires evidence that `Elem <:< Char`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readChar(sentinel: Int)(using Elem <:< Char): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readChar(sentinel: Int)(using Elem <:< Char): Async[Int]
}
```

`Reader#readShort` — Sentinel-return `Short` pull. Returns the element widened to `Int`, or `sentinel` when closed:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readShort(sentinel: Int)(using Elem <:< Short): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readShort(sentinel: Int)(using Elem <:< Short): Async[Int]
}
```

`Reader#readBoolean` — Sentinel-return `Boolean` pull. Returns `1` for `true`, `0` for `false`, or `sentinel` when closed. The sentinel must lie outside `[0, 1]` (typically `-1`):

```scala
abstract class Reader.SyncReader[+Elem] {
  def readBoolean(sentinel: Int)(using Elem <:< Boolean): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readBoolean(sentinel: Int)(using Elem <:< Boolean): Async[Int]
}
```

### Bulk Operations

`Reader#readAll` — Drains the entire reader into a `Chunk`. Dispatches on `Reader#jvmType` for zero-boxing on primitive readers:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readAll[A >: Elem](): Chunk[A]
}

abstract class Reader.AsyncReader[+Elem] {
  def readAll[A >: Elem](): Async[Chunk[A]]
}
```

The result is a new chunk containing all remaining elements:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
val all = r.readAll()
println(all)  // Chunk(10, 20, 30)
```

`Reader#skip` — Eagerly discards the first `n` elements. Dispatches on `Reader#jvmType` for zero-boxing when possible:

```scala
abstract class Reader.SyncReader[+Elem] {
  def skip(n: Long): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def skip(n: Long): Async[Unit]
}
```

### State Queries

`isClosed` reports whether the reader is closed. Its result is monotone: once `true`, it never becomes `false`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def isClosed: Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def isClosed: Async[Boolean]
}
```

`readable` reports whether the next `read()` would produce a value (not the sentinel). On `AsyncReader` the answer itself is asynchronous. Buffered readers can override it for an accurate, non-consuming probe:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readable(): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def readable(): Async[Boolean]
}
```

Use `readable()` to check if elements are available before calling `read()`:

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

Combine multiple readers to build more complex sources:

### Concatenation

`Reader#concat` — Concatenates this reader with `next`. When this reader is exhausted, it is closed and elements are pulled from `next` (evaluated lazily). Optimized for left-associative chains:

```scala
abstract class Reader[+Elem] {
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
}
```

`Reader#++` — Alias for `Reader#concat`. Syntactic sugar for composing readers:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
}
```

Here is how concatenation chains multiple readers together:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r1 = Reader.fromChunk(Chunk(1, 2))
val r2 = Reader.fromChunk(Chunk(3, 4))
val combined = r1 ++ r2

def drain(): Unit = {
  val v = combined.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output: 1, 2, 3, 4
```

**Optimization**: If this reader is already a `ConcatReader`, the thunk is appended to its internal array and `this` is returned (mutable append, O(1) amortized). Otherwise a new `ConcatReader` is created. This ensures that left-associative chains like `a ++ b ++ c ++ d` compile into a single flat `ConcatReader` with O(1) per-element read, rather than O(n) nested wrappers.

## Resource Management

Close readers and attach cleanup callbacks:

### Closing

`close` signals end-of-stream from the consumer side and releases any held resources. Implementations set internal closed state and wake or cancel any pending work. A synchronous owner calls it directly; an asynchronous owner must run and await the returned `Async`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def close(): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def close(): Async[Unit]
}
```

`SyncReader#withRelease` wraps a synchronous reader so that `release` runs when it closes. `withReleaseAsync`, available on the sealed root, returns an `AsyncReader` and awaits asynchronous cleanup:

```scala
abstract class Reader.SyncReader[+Elem] {
  def withRelease(release: () => Unit): Reader.SyncReader[Elem]
}

sealed abstract class Reader[+Elem] {
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
}
```

Here is how cleanup logic is attached to a reader:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import scala.sys.Prop

val cleanupRef = scala.collection.mutable.ListBuffer[String]()
val r = Reader.fromChunk(Chunk(1, 2)).withRelease { () =>
  cleanupRef += "cleaned"
  println("Cleaned up")
}

r.close()
println(cleanupRef.nonEmpty)  // true
```

## Pushdown Operations

Readers can sometimes handle skip, limit, and repeat operations natively (O(1), zero per-element cost). These methods attempt that; if the reader cannot handle it natively, they return `false` and the caller must wrap the reader.

`Reader#setSkip` — Attempts to set a skip (drop) on this reader. Returns `true` if handled natively, `false` if the caller must wrap. When `true`, the next n elements are discarded before producing. After `Reader#reset()`, the skip is re-applied:

```scala
abstract class Reader.SyncReader[+Elem] {
  def setSkip(n: Long): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setSkip(n: Long): Async[Boolean]
}
```

Set a skip to discard the first two elements:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
val handled = r.setSkip(2)
println(s"Skip handled natively: $handled")

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output:
// Skip handled natively: true
// 3
// 4
// 5
```

`Reader#setLimit` — Attempts to set a limit on this reader so it produces at most `n` elements. Returns `true` if handled natively, `false` if the caller must wrap. After `reset()`, the limit is re-applied from the new start position:

```scala
abstract class Reader.SyncReader[+Elem] {
  def setLimit(n: Long): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setLimit(n: Long): Async[Boolean]
}
```

Set a limit to produce only three elements:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
val handled = r.setLimit(3)
println(s"Limit handled natively: $handled")

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// Output:
// Limit handled natively: true
// 1
// 2
// 3
```

`Reader#setRepeat` — Attempts to set this reader into repeat-forever mode, so it restarts from the beginning whenever it would otherwise close. Returns `true` if handled natively, `false` if the caller must wrap:

```scala
abstract class Reader.SyncReader[+Elem] {
  def setRepeat(): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setRepeat(): Async[Boolean]
}
```

Set repeat mode to emit elements multiple times:

```scala mdoc:reset
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2))
val handled = r.setRepeat()
println(s"Repeat handled natively: $handled")

def drain(count: Int): Unit = {
  if (count < 6) {
    val v = r.read(-1)
    println(v)
    drain(count + 1)
  }
}
drain(0)
// Output:
// Repeat handled natively: true
// 1
// 2
// 1
// 2
// 1
// 2
```

`Reader#reset` — Rewinds this reader to its initial state, as if freshly constructed. After `Reader#reset()`, all elements are available again from the beginning. Not all readers support this; readers backed by one-shot resources (InputStreams, `java.io.Reader`s) throw `UnsupportedOperationException`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def reset(): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def reset(): Async[Unit]
}
```

After rewinding, the reader starts from the beginning:

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

For cross-platform manual pulling, use caller-owned `Stream#startAsync` or bracketed `Stream#useReaderAsync`. `startAsync` transfers ownership to you, so you must await `close()` on every exit path; `useReaderAsync` retains ownership and closes automatically on success, failure, or cancellation. The JVM-only `Stream#start` returns a scoped blocking reader owned by its scope:

```scala
import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val reader: $[Reader.SyncReader[Int]] = Stream.range(1, 6).start(using scope)

  $(reader) { r =>
    def drain(): Unit = {
      val v = r.read(-1)
      if (v != -1) {
        println(v)   // prints 1, 2, 3, 4, 5
        drain()
      }
    }
    drain()
  }
  // reader is closed automatically when scope exits
}
```

:::caution
Avoid holding references to a `SyncReader` obtained via `Stream#start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Integration with Sink

`Reader` and `Sink` are dual: `Reader` is the source, and `Sink` is the consumer. A terminal compiles the stream to the reader kind required by the graph and gives ownership of that reader to the sink. On the JVM, plain terminals such as `run` use the blocking `SyncReader` path when the graph is synchronous and bridge genuine asynchronous boundaries at the final edge. Cross-platform `runAsync` drains an `AsyncReader` without blocking. Both terminal families close the owned reader on success, typed failure, defect, or cancellation.

The sink repeatedly pulls from its reader until end-of-stream, transforming the sequence of elements into a result of type `Z`. This kind-selected drain is an implementation detail; callers choose it through `run` or `runAsync` rather than invoking a sink drain method directly.

For example, `Sink.collectAll` drains all elements and returns them as a `Chunk`:

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream.range(1, 10)
  .run(Sink.collectAll[Int])
```

## Implementation Notes

Understand the design choices and mechanisms that power `Reader`:

### Sentinel Protocol

The `read(sentinel)` method uses a caller-chosen sentinel value to signal end-of-stream. This avoids the allocation and boxing of wrapping results in `Option` or `Either`. The sentinel must be a value that never appears as a real element.

For reference types, `null` is a common sentinel. Primitive scalar callers choose a sentinel appropriate to the widened carrier:

| Type   | Sentinel     | Method            | Return Type |
|--------|--------------|-------------------|-------------|
| `Int`  | `Long.MinValue` | `readInt(sentinel: Long)` | `Long`      |
| `Long` | `Long.MaxValue` | `readLong(sentinel: Long)` | `Long`      |
| `Float` | `Double.MaxValue` | `readFloat(sentinel: Double)` | `Double`   |
| `Double` | `Double.MaxValue` | `readDouble(sentinel: Double)` | `Double`   |

Scalar `Long` and `Double` pulls cannot avoid collisions. Library internals do not treat a numeric value as EOF for those lanes: they use length-one `readLongs` and `readDoubles` calls and inspect the returned count, so every bit pattern remains data.

### JVM Type Dispatch

`Reader` dispatches on `jvmType` to choose between unboxed and boxed pull paths. This is a physical contract: `JvmType.Byte`, for example, means `readByte` is supported and yields this reader's elements, even if covariance has widened its static type to `Reader[AnyVal]`. Type-preserving wrappers and widening operations preserve a known lane; only an actually unknown or mixed representation falls back to `AnyRef`. Subclasses with primitive specialization override `jvmType`:

```scala
abstract class Reader[+Elem] {
  def jvmType: JvmType = JvmType.AnyRef
}
```

The eight primitive tags map exactly to `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`. For example, a `SyncReader[Int]` backed by a `Chunk[Int]` reports `JvmType.Int`, so consumers may use `readInt`; asynchronous readers expose the corresponding values through `Async`.

### Thread Safety

Readers are single-consumer cursors, not concurrent work queues. In particular, do not overlap pulls on an `AsyncReader`; await one operation before beginning another.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module. Follow these steps to run them:

**Step 1** — Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**Step 2** — Run individual examples with sbt:

### Basic Reader Construction

This example demonstrates the most common reader factories: `Reader.fromChunk`, `Reader.fromIterable`, `Reader.fromRange`, and `Reader.single`. Embed the source:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/reader/ReaderBasicConstructionExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderBasicConstructionExample"
```

### Primitive Specialization and Bulk Operations

This example shows how primitive readers avoid boxing through `Reader#jvmType` dispatch, and demonstrates `Reader#readAll` and `Reader#skip` for bulk operations. Embed the source:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/reader/ReaderPrimitiveSpecializationExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderPrimitiveSpecializationExample"
```

### Composition and Resource Management

This example demonstrates reader composition with `Reader#++`, resource cleanup with `Reader#withRelease`, and integration with `Stream.start` for manual pulling. Embed the source:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/reader/ReaderCompositionExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderCompositionExample"
```
