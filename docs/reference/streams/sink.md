---
id: sink
title: "Sink"
---

`Sink[+E, -A, +Z]` is a **stream consumer** that reads elements of type `A` and produces a result of type `Z`, potentially failing with an error of type `E`. You pass a sink to [Stream.run](./stream.md) to execute the stream synchronously and get `Either[E, Z]`.

`Sink`:
- Is covariant in `E` (error) and `Z` (result) — these are outputs
- Is contravariant in `A` (input) — a `Sink[_, Any, _]` accepts any element type
- Participates in JVM primitive specialization for zero-boxing overhead
- Provides `contramap`, `map`, and `mapError` for composable transformations

Here is the structural shape of the `Sink` type:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
  def mapError[E2](f: E => E2): Sink[E2, A, Z]
}
```

## Overview

Sink is the terminal piece in the streaming architecture. A [Stream](./stream.md) describes *what* to produce, a [Pipeline](./pipeline.md) describes *how* to transform, and a Sink describes *how to consume*:

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│ Stream[E, A] │ ──→ │ Pipeline[A, B]   │ ──→ │ Sink[E, B, Z]│
└──────────────┘     └──────────────────┘     └──────────────┘
                                                      │
                                              ┌───────▼──────┐
                                              │ Either[E, Z] │
                                              └──────────────┘
```

When you call `stream.run(sink)`:
1. The stream compiles into a `Reader` (a low-level pull-based source)
2. The sink's internal `drain` method pulls elements in a tight loop until end-of-stream
3. On success, the result wraps in `Right(z)`
4. Typed errors (`E`) surface as `Left(e)`, while untyped defects propagate as exceptions
5. The reader's `close()` runs in a `finally` block, ensuring resource safety

## Predefined Sinks

These are value sinks (no factory arguments). They work on any element type.

### `Sink.drain` — Discard All Elements

Consumes every element and discards them. Returns `Unit`:

```scala
object Sink {
  val drain: Sink[Nothing, Any, Unit]
}
```

Use `drain` when you only care about side effects (e.g., via `tapEach`) and not the elements themselves:

```scala mdoc:reset
import zio.blocks.streams._
import scala.collection.mutable.Buffer

val log = Buffer[String]()
val result = Stream(1, 2, 3)
  .tapEach(x => log += s"Processing: $x")
  .run(Sink.drain)
// result is Right(())
// log contains: ["Processing: 1", "Processing: 2", "Processing: 3"]
```

### `Sink.count` — Count Elements

Counts the total number of elements consumed. Returns `Long`:

```scala
object Sink {
  val count: Sink[Nothing, Any, Long]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(1, 2, 3, 4, 5).run(Sink.count)
```

### `Sink.sumInt` / `sumLong` / `sumFloat` / `sumDouble` — Typed Numeric Sums

Returns the sum of all elements as a numeric type. Each sink accepts the corresponding primitive type:

```scala
object Sink {
  val sumInt:    Sink[Nothing, Int, Long]
  val sumLong:   Sink[Nothing, Long, Long]
  val sumFloat:  Sink[Nothing, Float, Double]
  val sumDouble: Sink[Nothing, Double, Double]
}
```

Note that `sumInt` returns `Long` (to avoid overflow) and `sumFloat` returns `Double` (to reduce rounding loss):

```scala mdoc:reset
import zio.blocks.streams._

val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)

val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
```

## Construction

Sinks are created using factory methods on the companion object. These methods fall into several categories based on what they do:

### Collecting

Gather elements into collections:

#### `Sink.collectAll[A]` — Collect into a Chunk

Collects all elements into a `Chunk[A]`:

```scala
object Sink {
  def collectAll[A]: Sink[Nothing, A, Chunk[A]]
}
```

This is the sink behind `Stream.runCollect`:

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(1, 2, 3).run(Sink.collectAll[Int])
```

#### `Sink.take[A]` — Collect First N Elements

Collects at most `n` elements into a `Chunk[A]`, then stops (short-circuiting the upstream):

```scala
object Sink {
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream.range(0, 1000).run(Sink.take(3))
```

### Aggregation and Search

These sinks combine elements into a single result or search for specific elements within a stream:

#### `Sink.foldLeft[A, Z]` — General Left Fold

Folds all elements using an accumulator function, starting from initial value `z`:

```scala
object Sink {
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z): Sink[Nothing, A, Z]
}
```

This is the most general aggregation sink:

```scala mdoc:reset
import zio.blocks.streams._

val sum = Stream(1, 2, 3, 4).run(Sink.foldLeft(0)(_ + _))

val concat = Stream("a", "b", "c").run(Sink.foldLeft("")(_ + _))
```

#### `Sink.head[A]` — First Element

Returns the first element wrapped in `Some`, or `None` for an empty stream:

```scala
object Sink {
  def head[A]: Sink[Nothing, A, Option[A]]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val first = Stream(10, 20, 30).run(Sink.head[Int])

val empty = Stream.empty.run(Sink.head[Int])
```

#### `Sink.last[A]` — Last Element

Returns the last element wrapped in `Some`, or `None` for an empty stream. Must consume all elements:

```scala
object Sink {
  def last[A]: Sink[Nothing, A, Option[A]]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(10, 20, 30).run(Sink.last[Int])
```

#### `Sink.find[A]` — First Matching Element

Returns the first element satisfying `pred`, or `None`. Short-circuits on first match:

```scala
object Sink {
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val found = Stream(1, 3, 4, 6).run(Sink.find[Int](_ % 2 == 0))
```

#### `Sink.exists[A]` — Any Element Matches

Returns `true` if any element satisfies `pred`. Short-circuits on first match:

```scala
object Sink {
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val hasNegative = Stream(1, -2, 3).run(Sink.exists[Int](_ < 0))
```

#### `Sink.forall[A]` — All Elements Match

Returns `true` if all elements satisfy `pred`. Short-circuits to `false` on first failure:

```scala
object Sink {
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val allPositive = Stream(1, 2, 3).run(Sink.forall[Int](_ > 0))

val notAll = Stream(1, -2, 3).run(Sink.forall[Int](_ > 0))
```

### Effectful

These sinks perform side effects during stream consumption:

#### `Sink.foreach[A]` — Apply Side Effect to Each Element

Applies `f` to every element for side effects. Returns `Unit`:

```scala
object Sink {
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(1, 2, 3).run(Sink.foreach[Int](x => println(s"Got: $x")))
```

### Failing

These sinks can be used to produce typed errors or fail under specific conditions:

#### `Sink.fail[E]` — Immediately Fail

Creates a sink that fails immediately with a typed error, without consuming any elements:

```scala
object Sink {
  def fail[E](e: E): Sink[E, Any, Nothing]
}
```

Use this in conditional sink construction:

```scala mdoc:reset
import zio.blocks.streams._

val sink: Sink[String, Int, Long] =
  if (false) Sink.count
  else Sink.fail("not ready")

val result = Stream(1, 2, 3).run(sink)
```

### I/O

Write elements to Java I/O destinations:

#### `Sink.fromOutputStream` — Write Bytes

Writes every `Byte` element to a `java.io.OutputStream`:

```scala
object Sink {
  def fromOutputStream(os: java.io.OutputStream): Sink[Nothing, Byte, Unit]
}
```
The sink does **not** close the stream when done. This is intentional: you own the stream's lifecycle, not the sink. You're responsible for closing it yourself (typically via try-with-resources or explicit `close()` calls) to flush buffers and release system resources. This design gives you flexibility to reuse the stream after the sink finishes, or to coordinate closing with other stream operations:

```scala mdoc:reset
import zio.blocks.streams._
import java.io.ByteArrayOutputStream

val bos = new ByteArrayOutputStream()

// Write first batch of bytes
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](72, 105)).run(Sink.fromOutputStream(bos))

// Write second batch to the same stream (reuse it)
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](33)).run(Sink.fromOutputStream(bos))

// When done writing all batches, YOU close the stream
bos.close()

// Now you can get the final bytes: bos.toByteArray() would fail here because stream is closed
// But in a real application, you'd flush and read the bytes before closing
```

#### `Sink.fromJavaWriter` — Write Characters

Writes every `Char` element to a `java.io.Writer`. Does not close the writer when done — you own its lifecycle:

```scala
object Sink {
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit]
}
```

Write a stream of characters to a StringWriter and access the accumulated text:

```scala mdoc:reset
import zio.blocks.streams._
import java.io.StringWriter

val writer = new StringWriter()

// Write a stream of individual characters
Stream('H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd')
  .run(Sink.fromJavaWriter(writer))

// Get the final string
val result = writer.toString()
```

Like `fromOutputStream`, this sink intentionally does not close the writer. This gives you control over when to flush or close, allowing you to write multiple streams to the same writer or coordinate lifecycle with other operations.

### Custom

Advanced low-level use cases with direct reader protocol access:

#### `Sink.create[E, A, Z]` — Escape Hatch

Creates a sink from a raw function that takes a `Reader[A]` and returns `Z`. This is the low-level escape hatch for writing sinks that cannot be expressed using the built-in factories:

```scala
object Sink {
  def create[E, A, Z](f: Reader[A] => Z): Sink[E, A, Z]
}
```

:::note
`create` gives you direct access to the `Reader`, so you are responsible for using the correct read protocol (`read(sentinel)` for AnyRef, `readInt(sentinel)` for Int, etc.). Prefer the built-in sinks when possible.
:::

Here's a custom sink that computes the average of all integers in a stream:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

// A custom sink that computes the average of Ints
val average = Sink.create[Nothing, Int, Double] { reader =>
  def loop(sum: Long, count: Long): (Long, Long) = {
    val v = reader.read[Any](null)
    if (v == null) (sum, count)
    else {
      val newSum = sum + v.asInstanceOf[Int]
      loop(newSum, count + 1)
    }
  }
  val (sum, count) = loop(0L, 0L)
  if (count == 0) 0.0 else sum.toDouble / count
}
```

This example shows how `create` works. The reader reads elements using `read[Any](null)` — the sentinel protocol — where `null` signals "read the next element" and the function returns `null` when the stream ends. We accumulate the sum and count via recursion, then return the average. You'd use `Sink.create` when no built-in sink provides the exact aggregation or transformation logic you need — it's powerful but requires understanding the low-level [reader protocol](./reader.md).

## Transforming Sinks

Every sink can be transformed using these instance methods:

### `Sink#contramap[A2]` — Pre-Process Input

Transforms the input elements before they reach the sink. The sink's result and error types are unchanged:

```scala
trait Sink[+E, -A, +Z] {
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
}
```

`contramap` is the dual of `map`: it transforms what goes *in*, not what comes *out*:

```scala mdoc:reset
import zio.blocks.streams._

// A sink that counts the length of strings
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramap[String](_.length)

val result = Stream("hello", "world").run(totalLength)
```

### `Sink#map[Z2]` — Transform Result

Transforms the result after the sink finishes draining:

```scala
trait Sink[+E, -A, +Z] {
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
}
```

```scala mdoc:reset
import zio.blocks.streams._

val countAsString: Sink[Nothing, Any, String] =
  Sink.count.map(n => s"Total: $n elements")

val result = Stream(1, 2, 3).run(countAsString)
```

### `Sink#mapError[E2]` — Transform Error

Transforms the error channel of a sink:

```scala
trait Sink[+E, -A, +Z] {
  inline def mapError[E2](f: E => E2): Sink[E2, A, Z]
}
```

This method uses Scala 3's `inline` + `summonFrom` to perform a compile-time check: if `E` is `Nothing` (the sink never fails), the compiler elides the wrapper entirely and returns `this` cast to the new type with zero allocation:

```scala mdoc:compile-only
import zio.blocks.streams._

// No-op: drain never fails, so mapError is free
val mapped = Sink.drain.mapError[String](_.toString)
// At compile time: this is just a cast, no wrapper allocated

// Real mapping: fail can produce errors
val failing = Sink.fail("oops").mapError[RuntimeException](new RuntimeException(_))
```

## Integration with Stream

`Stream.run(sink)` is the primary entry point. ZIO Blocks also provides convenience methods on `Stream` that delegate to built-in sinks:

| Stream method          | Equivalent Sink                   |
|------------------------|-----------------------------------|
| `stream.runCollect`    | `stream.run(Sink.collectAll)`     |
| `stream.runDrain`      | `stream.run(Sink.drain)`          |
| `stream.runForeach(f)` | `stream.run(Sink.foreach(f))`     |
| `stream.runFold(z)(f)` | `stream.run(Sink.foldLeft(z)(f))` |
| `stream.count`         | `stream.run(Sink.count)`          |
| `stream.head`          | `stream.run(Sink.head)`           |
| `stream.last`          | `stream.run(Sink.last)`           |
| `stream.find(pred)`    | `stream.run(Sink.find(pred))`     |
| `stream.exists(pred)`  | `stream.run(Sink.exists(pred))`   |
| `stream.forall(pred)`  | `stream.run(Sink.forall(pred))`   |

The `runFold` method with primitive accumulator types (`Int`, `Long`, `Double`) uses specialized internal sink classes that keep the accumulator unboxed.

See [Stream — Running Streams](./stream.md#running-streams) for more details on terminal operations.

## Integration with Pipeline

A [Pipeline](./pipeline.md) can be applied to a Sink using `andThenSink`, producing a new Sink that pre-processes input elements through the pipeline:

```scala mdoc:reset
import zio.blocks.streams._
import zio.blocks.chunk.Chunk

val cleanAndCollect: Sink[Nothing, String, Chunk[String]] =
  Pipeline.map[String, String](_.trim.toLowerCase)
    .andThenSink(Sink.collectAll[String])

val result = Stream("  Hello ", " WORLD  ").run(cleanAndCollect)
```

The equivalence law holds: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

See [Pipeline — Applying to a Sink](./pipeline.md#applying-to-a-sink) for more details.

## JVM NIO Sinks

The `NioSinks` object (JVM-only) provides sinks for Java NIO (`java.nio`) buffers and channels. These exist because NIO is the standard high-performance I/O mechanism on the JVM: non-blocking, memory-efficient, and capable of handling thousands of concurrent connections. When you're writing to network sockets, memory-mapped files, or other NIO-based resources, these sinks give you a convenient way to drain streams directly into NIO data structures without intermediate allocation or copying.

Traditional Java I/O (`OutputStream`, `Writer`) blocks threads and requires manual buffering for efficiency. NIO provides non-blocking channels, but using them directly requires buffer allocation, position management, and explicit flushing. `NioSinks` bridges this gap: `fromChannel` handles buffering automatically (default 8KB), while typed variants like `fromByteBufferInt` and `fromByteBufferLong` eliminate boxing overhead by writing primitives directly to buffers you provide.

Choose `fromChannel` when you need to write to network sockets or files and cannot afford to block threads. Choose typed variants when you control buffer allocation and are streaming millions of primitives where boxing would degrade performance. **Important:** Read the Sentinel Value Limitation section below—it describes a hard constraint that affects your choice depending on whether your data can contain specific values.

Here are the available NIO sinks:

```scala
object NioSinks {
  def fromByteBuffer      (buf: ByteBuffer): Sink[Nothing, Byte,   Unit]
  def fromByteBufferInt   (buf: ByteBuffer): Sink[Nothing, Int,    Unit]
  def fromByteBufferLong  (buf: ByteBuffer): Sink[Nothing, Long,   Unit]
  def fromByteBufferFloat (buf: ByteBuffer): Sink[Nothing, Float,  Unit]
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit]
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[IOException, Byte, Unit]
}
```

### From ByteBuffer Sinks

**`fromByteBuffer` and typed variants** — Write primitive streams directly into a pre-allocated NIO ByteBuffer:
- `fromByteBuffer` — writes individual `Byte` elements using a read sentinel of `-1`. Use only for unstructured byte data.
- `fromByteBufferInt`, `fromByteBufferLong`, `fromByteBufferFloat`, `fromByteBufferDouble` — write primitives directly using the buffer's native methods (`putInt`, `putLong`, etc.). These avoid boxing and are faster than the byte variant.

Here's an example using ByteBuffer with typed primitive writes:

```scala mdoc:reset
import zio.blocks.streams._
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer
import java.nio.ByteOrder

val buffer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)

// Write a stream of Longs to the buffer
Stream(1L, 2L, 3L, 4L).run(NioSinks.fromByteBufferLong(buffer))

// After writing, rewind to read
buffer.rewind()

val readBack = List(
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong()
)
```

This example allocates a 32-byte buffer (4 Longs × 8 bytes each), writes four `Long` values using `fromByteBufferLong` (which efficiently calls `putLong` on each element), then rewinds and reads them back to verify. The typed variant is significantly faster than `fromByteBuffer` because it operates at the primitive level — no boxing, no element-by-element byte writing.

The following example shows streaming voltage sensor readings through a calibration curve and buffering them for downstream computation. When processing sensor arrays or scientific measurements, pre-allocated buffers with typed sinks enable zero-copy batch processing.

Here is the complete example:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkScientificComputingExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkScientificComputingExample"
```

This use case is typical in scientific instrumentation, machine learning data preprocessing, and signal processing pipelines where you need to efficiently batch-process numerical streams into memory-efficient structures for downstream computation.

:::danger[Sentinel Value Limitation]

These typed sinks achieve **zero-boxing performance** by using a special "sentinel" value to signal end-of-stream, rather than allocating wrapper objects or checking for `null`. This design eliminates allocations entirely, keeping the read loop fully unboxed and primitive.

**However, this comes with a cost:** if your stream contains the exact sentinel value, the sink stops reading and silently drops all remaining elements.

**Affected Data Types and Their Sentinels:**
| Method | Input Type | Sentinel Value | Risk Level | Why? |
|--------|-----------|---|---|---|
| `fromByteBufferByte` | `Byte` | `-1` (0xFF) | Safe | Sentinel is outside valid byte range [-128, 127] |
| `fromByteBufferInt` | `Int` | `Long.MinValue` | Safe | Sentinel (-2^63) is far outside valid Int range [-2^31, 2^31-1] |
| `fromByteBufferLong` | `Long` | `Long.MaxValue` | **RISKY** | Sentinel is a valid Long value; streams can contain Long.MaxValue |
| `fromByteBufferFloat` | `Float` | `Double.MaxValue` | Safe | Sentinel (≈1.8e+308) is far outside valid Float range (±3.4e+38) |
| `fromByteBufferDouble` | `Double` | `Double.MaxValue` | **RISKY** | Sentinel is the maximum valid Double value; streams can contain Double.MaxValue |

**Concrete Risk Example:**
If you stream `[100L, 200L, Long.MaxValue, 300L, 400L]` using `fromByteBufferLong`, only `[100L, 200L]` will be written to the buffer—the sentinel triggers and the stream silently terminates, dropping the last three elements with no error.

**Safe to Always Use (No Sentinel Risk):**
- `fromByteBufferByte` — Byte sentinels are impossible
- `fromByteBufferInt` — Int sentinels are impossible (Long.MinValue is outside Int range)
- `fromByteBufferFloat` — Float sentinels are impossible (Double.MaxValue is outside Float range)

**Careful With (Sentinel Values Are Possible):**
- `fromByteBufferLong` — Avoid if your data could contain `Long.MaxValue`:
  - Unix timestamps in nanoseconds (will reach year 2262 eventually)
  - Data representing all possible Long values
  - Positive integers < Long.MaxValue (most sensor/measurement data) — safe

- `fromByteBufferDouble` — Avoid if your data could contain `Double.MaxValue`:
  - Domain-agnostic scientific computing (could need extreme values)
  - Mathematical data (infinity, extreme results)
  - Sensor data (temperatures, pressures, voltages) — safe
  - Financial data (stock prices, volumes—rarely extreme) — safe

**Safe Alternatives:** If you must support sentinel values, use:
- `Sink.create[E, A, Z](f: Reader[A] => Z)` — manual buffering without sentinels
- `Sink.foreach[A](f: A => Unit)` — processes each element individually
- `Sink.foldLeft[A, Z](z: Z)(f: (Z, A) => Z)` — accumulates without sentinel constraint
- `Sink.collectAll[A]` — collects into a Chunk using generic protocol (no sentinel)

For a complete demonstration of this limitation in action, see the Sentinel Value Limitation example below:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkSentinelLimitationExample.scala")
```


Run it with:

```bash
sbt "streams-examples/runMain sink.SinkSentinelLimitationExample"
```
:::

You might ask: **Why not use a sentinel object like generic sinks do, instead of primitive values?** The answer reveals a fundamental performance trade-off.

Typed NIO Sinks use primitive sentinels to keep loops fully unboxed:

```scala
// fromByteBufferLong - tight loop with primitives only
val s = Long.MaxValue
def loop(v: Long): Unit =
  if (v != s) {
    buf.putLong(v)
    loop(reader.readLong(s)(using unsafeEvidence))
  }
val firstValue = reader.readLong(s)(using unsafeEvidence)
loop(firstValue)
```

Generic sinks use object sentinels to signal end-of-stream:

```scala
// Sink.collectAll - uses object reference for end-of-stream
def loop(v: Any): Unit =
  if (v.asInstanceOf[AnyRef] ne EndOfStream) {
    b += v.asInstanceOf[A]
    loop(reader.read(EndOfStream))
  }
val firstValue = reader.read(EndOfStream)  // EndOfStream is an object
loop(firstValue)
```

**Performance Impact:**
- **Typed sinks:** Direct primitive comparison, zero allocations, tight loop optimizable by JVM
- **Generic sinks:** Object casting, reference equality check, one `EndOfStream` object per stream

For a stream processing **millions of elements**, the typed sink approach has measurably better performance because:
1. No casting overhead per iteration
2. Primitive values are faster than object references
3. JIT compiler can better optimize tight primitive loops
4. Zero per-element allocation pressure

**The Cost:**
- **Typed sinks:** Cannot stream sentinel values (Long.MaxValue, Double.MaxValue)
- **Generic sinks:** Universal (handle any value), but slightly slower per-element

### From Channel Sink

The **`Sink.fromChannel`**  constructor performs buffered writes to a `WritableByteChannel` (e.g., a network socket or file channel). This is the general-purpose NIO sink: it accumulates bytes in an internal buffer of size `bufSize` (default 8192), flushes when the buffer is full, and flushes again at end-of-stream.

It handles `IOException` as a typed error, so failures surface as `Left(IOException)` from `Stream.run`. Use this for network I/O or when you can't pre-allocate a buffer. The channel I/O is blocking—NIO's non-blocking advantage comes when using selectors across many channels, which this sink does not expose.

Suppose you're collecting metrics from thousands of sensors (temperature, pressure, timestamps) and need to write them to a file efficiently. Using `fromChannel` with a file's `WritableByteChannel` gives you automatic buffering and eliminates manual position management.

Here is the complete example:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkTelemetryExample.scala")
```


Run it with:

```bash
sbt "streams-examples/runMain sink.SinkTelemetryExample"
```

This pattern is common in high-throughput logging systems, time-series databases, and IoT platforms where you need to write streams of telemetry data to persistent storage without blocking or allocating excessively.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

Start by cloning the repository and navigating to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

Run individual examples with sbt:

### Basic Usage

This example demonstrates the most commonly used built-in sinks: `drain`, `count`, `collectAll`, `head`, `last`, and `take`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkBasicUsageExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkBasicUsageExample"
```

### Aggregation and Search

This example shows aggregation sinks (`foldLeft`, `sumInt`, `sumDouble`) and search sinks (`exists`, `forall`, `find`, `foreach`):

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkAggregationExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkAggregationExample"
```

### Transformations and Composition

This example demonstrates `contramap`, `map`, `mapError`, `fail`, `create`, and Pipeline integration via `andThenSink`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkTransformationExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkTransformationExample"
```

### Sentinel Value Limitation (NIO Typed Sinks)

This example demonstrates the critical sentinel value limitation of typed NIO sinks (`fromByteBufferInt`, `fromByteBufferLong`, `fromByteBufferDouble`, `fromByteBufferFloat`). It shows how streams containing sentinel values (e.g., `Long.MaxValue` for `fromByteBufferLong`) will silently truncate, causing data loss without warning:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkSentinelLimitationExample.scala")
```

Run it with this command:

```bash
sbt "streams-examples/runMain sink.SinkSentinelLimitationExample"
```
