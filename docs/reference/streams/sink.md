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

```scala mdoc:compile-only
import zio.blocks.streams.*

val result = Stream(1, 2, 3).run(Sink.drain)
```

### `Sink.count` — Count Elements

Counts the total number of elements consumed. Returns `Long`:

```scala
object Sink {
  val count: Sink[Nothing, Any, Long]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

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

```scala mdoc:compile-only
import zio.blocks.streams.*

val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)

val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
```

## Construction

### Collecting

#### `Sink.collectAll[A]` — Collect into a Chunk

Collects all elements into a `Chunk[A]`:

```scala
object Sink {
  def collectAll[A]: Sink[Nothing, A, Chunk[A]]
}
```

This is the sink behind `Stream.runCollect`:

```scala mdoc:compile-only
import zio.blocks.streams.*

val result = Stream(1, 2, 3).run(Sink.collectAll[Int])
```

#### `Sink.take[A]` — Collect First N Elements

Collects at most `n` elements into a `Chunk[A]`, then stops (short-circuiting the upstream):

```scala
object Sink {
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

val result = Stream.range(0, 1000).run(Sink.take(3))
```

### Aggregation and Search

#### `Sink.foldLeft[A, Z]` — General Left Fold

Folds all elements using an accumulator function, starting from initial value `z`:

```scala
object Sink {
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z): Sink[Nothing, A, Z]
}
```

This is the most general aggregation sink:

```scala mdoc:compile-only
import zio.blocks.streams.*

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

```scala mdoc:compile-only
import zio.blocks.streams.*

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

```scala mdoc:compile-only
import zio.blocks.streams.*

val result = Stream(10, 20, 30).run(Sink.last[Int])
```

#### `Sink.find[A]` — First Matching Element

Returns the first element satisfying `pred`, or `None`. Short-circuits on first match:

```scala
object Sink {
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

val found = Stream(1, 3, 4, 6).run(Sink.find[Int](_ % 2 == 0))
```

#### `Sink.exists[A]` — Any Element Matches

Returns `true` if any element satisfies `pred`. Short-circuits on first match:

```scala
object Sink {
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

val hasNegative = Stream(1, -2, 3).run(Sink.exists[Int](_ < 0))
```

#### `Sink.forall[A]` — All Elements Match

Returns `true` if all elements satisfy `pred`. Short-circuits to `false` on first failure:

```scala
object Sink {
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

val allPositive = Stream(1, 2, 3).run(Sink.forall[Int](_ > 0))

val notAll = Stream(1, -2, 3).run(Sink.forall[Int](_ > 0))
```

### Effectful

#### `Sink.foreach[A]` — Apply Side Effect to Each Element

Applies `f` to every element for side effects. Returns `Unit`:

```scala
object Sink {
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*

val result = Stream(1, 2, 3).run(Sink.foreach[Int](x => println(s"Got: $x")))
```

### Failing

#### `Sink.fail[E]` — Immediately Fail

Creates a sink that fails immediately with a typed error, without consuming any elements:

```scala
object Sink {
  def fail[E](e: E): Sink[E, Any, Nothing]
}
```

Use this in conditional sink construction:

```scala mdoc:compile-only
import zio.blocks.streams.*

val sink: Sink[String, Int, Long] =
  if (false) Sink.count
  else Sink.fail("not ready")

val result = Stream(1, 2, 3).run(sink)
```

### I/O

#### `Sink.fromOutputStream` — Write Bytes

Writes every `Byte` element to a `java.io.OutputStream`. Does not close the stream when done:

```scala
object Sink {
  def fromOutputStream(os: java.io.OutputStream): Sink[Nothing, Byte, Unit]
}
```

```scala mdoc:compile-only
import zio.blocks.streams.*
import java.io.ByteArrayOutputStream

val bos = new ByteArrayOutputStream()
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](72, 105)).run(Sink.fromOutputStream(bos))
```

#### `Sink.fromJavaWriter` — Write Characters

Writes every `Char` element to a `java.io.Writer`. Does not close the writer when done:

```scala
object Sink {
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit]
}
```

### Custom

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

```scala mdoc:compile-only
import zio.blocks.streams.*
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

## Transforming Sinks

### `Sink#contramap[A2]` — Pre-Process Input

Transforms the input elements before they reach the sink. The sink's result and error types are unchanged:

```scala
trait Sink[+E, -A, +Z] {
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
}
```

`contramap` is the dual of `map`: it transforms what goes *in*, not what comes *out*:

```scala mdoc:compile-only
import zio.blocks.streams.*

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

```scala mdoc:compile-only
import zio.blocks.streams.*

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
import zio.blocks.streams.*

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

```scala mdoc:compile-only
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val cleanAndCollect: Sink[Nothing, String, Chunk[String]] =
  Pipeline.map[String, String](_.trim.toLowerCase)
    .andThenSink(Sink.collectAll[String])

val result = Stream("  Hello ", " WORLD  ").run(cleanAndCollect)
```

The equivalence law holds: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

See [Pipeline — Applying to a Sink](./pipeline.md#applying-to-a-sink) for more details.

## JVM NIO Sinks

The `NioSinks` object (JVM-only) provides sinks for NIO buffers and channels:

```scala
object NioSinks {
  def fromByteBuffer(buf: ByteBuffer): Sink[Nothing, Byte, Unit]
  def fromByteBufferInt(buf: ByteBuffer): Sink[Nothing, Int, Unit]
  def fromByteBufferLong(buf: ByteBuffer): Sink[Nothing, Long, Unit]
  def fromByteBufferFloat(buf: ByteBuffer): Sink[Nothing, Float, Unit]
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit]
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[IOException, Byte, Unit]
}
```

`fromChannel` performs buffered writes to a `WritableByteChannel`, flushing when the internal buffer is full and after end-of-stream. It wraps `IOException` as a typed error, so it surfaces as `Left(IOException)` from `Stream.run`.

## Implementation Notes

### Primitive Specialization

Every built-in sink checks `reader.jvmType` at the start of `drain` and dispatches into a fully unboxed tight loop using sentinel-based reads:

| JvmType  | Read method            | Sentinel value    |
|----------|------------------------|-------------------|
| `Int`    | `readInt(sentinel)`    | `Long.MinValue`   |
| `Long`   | `readLong(sentinel)`   | `Long.MaxValue`   |
| `Float`  | `readFloat(sentinel)`  | `Double.MaxValue` |
| `Double` | `readDouble(sentinel)` | `Double.MaxValue` |
| `AnyRef` | `read(sentinel)`       | `EndOfStream`     |

The sentinel values are chosen so they cannot appear as valid element values. For example, `readInt` returns `Long.MinValue` at end-of-stream — this is safe because all valid `Int` values fit within `Long` range and are non-negative-biased.

### Contramap Interpreter Fast Path

When a `Contramapped` sink receives a reader that is an `Interpreter` (the compiled form of a fused stream pipeline), it injects the mapping function directly into the interpreter's lane rather than wrapping the reader. This avoids an extra indirection level.

### mapError Compile-Time Elision

`mapError` uses Scala 3's `inline` + `summonFrom` to detect at compile time whether `E =:= Nothing`. If so, the method returns `this.asInstanceOf[Sink[E2, A, Z]]` — zero allocation, zero overhead. Only when `E` is a real error type does it create an `ErrorMapped` wrapper.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

This example demonstrates the most commonly used built-in sinks: `drain`, `count`, `collectAll`, `head`, `last`, and `take`.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkBasicUsageExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/sink/SinkBasicUsageExample.scala))

```bash
sbt "streams-examples/runMain sink.SinkBasicUsageExample"
```

### Aggregation and Search

This example shows aggregation sinks (`foldLeft`, `sumInt`, `sumDouble`) and search sinks (`exists`, `forall`, `find`, `foreach`).

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkAggregationExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/sink/SinkAggregationExample.scala))

```bash
sbt "streams-examples/runMain sink.SinkAggregationExample"
```

### Transformations and Composition

This example demonstrates `contramap`, `map`, `mapError`, `fail`, `create`, and Pipeline integration via `andThenSink`.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkTransformationExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/sink/SinkTransformationExample.scala))

```bash
sbt "streams-examples/runMain sink.SinkTransformationExample"
```
