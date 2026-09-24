---
id: sink
title: "Sink"
sidebar_label: "Sink"
description: "The stream consumer: its dual synchronous and asynchronous drains, the three construction factories, and the async aggregation and transformation combinators."
keywords:
  - "Stream Consumer"
  - "Dual Drain"
  - "Async Sinks"
  - "Custom Sink Factories"
  - "Sink"
---

`Sink[+E, -A, +Z]` is a **stream consumer** that reads elements of type `A` and produces a result of type `Z`, potentially failing with an error of type `E`. Every sink has synchronous and asynchronous drain paths: use cross-platform `Stream.runAsync`, or the JVM-only blocking `Stream.run`.

`Sink`:
- Is covariant in `E` (error) and `Z` (result) — these are outputs
- Is contravariant in `A` (input) — a `Sink[_, Any, _]` accepts any element type
- Dispatches on the reader's physical primitive lane, so element pulls avoid boxing
- Provides `Sink#contramap`, `Sink#map`, and `Sink#mapError` for composable transformations

Built-in sinks implement both drain paths. Asynchronous constructors include `existsAsync`, `findAsync`, `forallAsync`, `foreachAsync`, and `foldLeftAsync`; callbacks are sequential and back-pressured. The result/error combinators `contramapAsync`, `mapAsync`, and `mapErrorAsync` likewise select the native async drain when used by `runAsync`. On the JVM, driving such a sink through a plain blocking terminal blocks while awaiting its async drain; Scala.js has no blocking terminal.

Here is the structural shape of the `Sink` type:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
  def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
  def mapAsync[Z2](f: Z => Async[Z2]): Sink[E, A, Z2]
  def mapError[E2](f: E => E2)(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
  def mapErrorAsync[E2](f: E => Async[E2])(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
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
2. The sink's internal `Sink#drain` method pulls elements in a tight loop until end-of-stream
3. On success, the result wraps in `Right(z)`
4. Typed errors (`E`) surface as `Left(e)`, while untyped defects propagate as exceptions
5. The reader's `close()` runs after the drain whether it succeeded or threw, and a failure from `close()` is attached to the drain's own error rather than replacing it, ensuring resource safety

### Physical Input Lanes and Ownership

A sink consumes either kind of reader. `Reader` is not one type with a mode flag: it splits into `Reader.SyncReader[A]`, whose pulls return values, and `Reader.AsyncReader[A]`, whose pulls return [`Async`](../../async.md) values. [The reader union](../primitives/reader.md#the-reader-union) describes the split from the reader's side. From the sink's side, the consequence is that every sink is drained through one of two entry points, and the terminal decides which — the next section covers that choice.

Whichever kind arrives, a sink discovers its input representation from the materialized reader's `jvmType`, not from the sink's contravariant static input type. Generic sinks dispatch once per drain and then pull `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, and `Double` through library-internal physical pulls. The synchronous drain uses `readBooleanPhysical`, `readBytePhysical`, `readCharPhysical`, `readShortPhysical`, `readIntPhysical`, `readLongsPhysical`, `readFloatPhysical`, and `readDoublesPhysical`, respectively; the asynchronous drain differs on two lanes, pulling `Byte` through `readBytesPhysical` and `Float` through `readFloatsPhysical`. These are the internal counterparts of the public `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLongs`, `readFloat`, and `readDoubles`, which a generic sink cannot call because all but `readByte` demand `Elem <:< X` evidence it has no way to supply. Reference inputs use generic `read`. In particular, the four small primitive lanes do not share the `Int` pull.

`Long` and `Double` use one-element primitive arrays with `readLongsPhysical(scratch, 0, 1)` and `readDoublesPhysical(scratch, 0, 1)`, and on the asynchronous drain `Byte` and `Float` use one-element arrays in the same way. The returned count carries end-of-stream status out of band, so every `Long` value and every raw `Double` bit pattern—including NaN payloads—remains valid data. The scratch storage is allocated once per drain, not once per element.

The reader owns this physical representation. Consequently, widening a specialized stream (for example, from `Stream[Nothing, Int]` to `Stream[Nothing, AnyVal]`) does not erase its `Int` lane before it reaches a sink. A sink adapter preserves the wrapped reader's lane and forwards every exact pull method. It also preserves ownership: the run terminal owns and closes the materialized reader; a sink or sink adapter does not independently close it. External destinations supplied to I/O sinks remain caller-owned unless a constructor explicitly says otherwise.

## Consuming Asynchronous Readers

Every sink carries two drains, one per reader kind:

```scala
abstract class Sink[+E, -A, +Z] {
  private[streams] def drain(reader: Reader.SyncReader[_]): Z
  private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Z]
}
```

They are an overload on the argument type, not a runtime branch inside one method, so the selection is a static one made where the reader is known: at the terminal. Both are `private[streams]`, which means you never call either yourself. The terminal you already chose called one of them for you:

```
┌────────────────────────────────────────────────────────────────────┐
│      Stream#run  (JVM only)         Stream#runAsync  (JVM and JS)  │
│                 │ materializes                    │ materializes   │
│                 ▼                                 ▼                │
│       Reader.SyncReader[A]              Reader.AsyncReader[A]      │
│                 │ is handed to                    │ is handed to   │
│                 ▼                                 ▼                │
│       drain(SyncReader): Z          drain(AsyncReader): Async[Z]   │
│                 │ returns                         │ returns        │
│                 ▼                                 ▼                │
│           Either[E, Z]                   Async[Either[E, Z]]       │
└────────────────────────────────────────────────────────────────────┘
```

The asynchronous drain is a native one, not an adapter: `Stream#runAsync` hands the sink a `Reader.AsyncReader` and never converts it to a blocking reader first, so no thread is parked waiting for a pull. [Async Terminals](../execution-and-compatibility/async-execution.md#async-terminals) covers the terminal family that takes this path, and [The `Async[Either[E, Z]]` convention](../execution-and-compatibility/async-execution.md#the-asynceithere-z-convention) explains why the typed error stays inside the `Either`.

Every built-in sink implements both drains, so nothing on this page is available on only one path. A third member, `drainAsync(reader: Reader.SyncReader[_]): Async[Z]`, is likewise `private[streams]`; it presents a synchronous reader through the asynchronous contract by calling `reader.toAsync`, and exists for internal stages that must expose an asynchronous face over synchronous input.

### Choosing a Factory

Because a `Sink` has two abstract drains and both are `private[streams]`, code outside `zio.blocks.streams` cannot implement them — subclassing `Sink` directly is not a supported route, and the compiler will not let you complete the class. Build custom sinks from one of the three factories instead:

```scala
object Sink {
  // JVM and Scala.js
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](
    sync: Reader.SyncReader[A] => Z,
    async: Reader.AsyncReader[A] => Async[Z]
  ): Sink[E, A, Z]

  // JVM only — absent from the Scala.js companion entirely
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]
}
```

Each factory fills in the drain you did not write:

| Factory            | Callbacks you write               | Platforms        | Reach for it when                                                    |
|--------------------|-----------------------------------|------------------|----------------------------------------------------------------------|
| `Sink.createAsync` | one asynchronous                  | JVM and Scala.js | one implementation is enough, and awaiting per element is acceptable |
| `Sink.createBoth`  | one synchronous, one asynchronous | JVM and Scala.js | the synchronous path can be cheaper than the asynchronous one        |
| `Sink.create`      | one synchronous                   | JVM only         | the sink is JVM-only anyway and its callback wants a blocking reader |

`Sink.createAsync` writes the synchronous drain for you by awaiting the asynchronous one at a JVM blocking terminal. `Sink.create` writes the asynchronous drain for you by deferring the blocking callback into a cancellable effect, where cancellation closes the reader — the callback still runs inline on whichever thread drives that effect, so an asynchronous terminal driving a `Sink.create` sink blocks that thread for the duration of the callback. `Sink.createBoth` writes neither: it takes two independent callbacks and the terminal runs exactly one, so the two must agree on how much input they consume and what they produce — nothing compares their results.

:::warning[`Sink.create` does not exist on Scala.js]
It is declared in the JVM copy of `SinkCompanionPlatformSpecific` and simply absent from the Scala.js copy, so cross-built code that calls it fails to compile for the JS target rather than failing at runtime. [`Sink.create` and Custom Sinks](../execution-and-compatibility/platform-differences.md#sinkcreate-and-custom-sinks) states the platform rule.
:::

[Custom sink factories](#custom-sink-factories) shows a worked callback against the reader protocol.

## Predefined Sinks

These are value sinks (no factory arguments). They work on any element type.

### `Sink.drain` — Discard All Elements

Consumes every element and discards them. Returns `Unit`:

```scala
object Sink {
  val drain: Sink[Nothing, Any, Unit]
}
```

Use `Sink.drain` when you only care about side effects (e.g., via `Stream#tapEach`) and not the elements themselves:

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

Count all elements in a stream:

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(1, 2, 3, 4, 5).run(Sink.count)
```

### `Sink.sumInt` / `Sink.sumLong` / `Sink.sumFloat` / `Sink.sumDouble` — Typed Numeric Sums

Returns the sum of all elements as a numeric type. Each sink accepts the corresponding primitive type:

```scala
object Sink {
  val sumInt:    Sink[Nothing, Int, Long]
  val sumLong:   Sink[Nothing, Long, Long]
  val sumFloat:  Sink[Nothing, Float, Double]
  val sumDouble: Sink[Nothing, Double, Double]
}
```

Note that `Sink.sumInt` returns `Long` (to avoid overflow) and `Sink.sumFloat` returns `Double` (to reduce rounding loss):

```scala mdoc:reset
import zio.blocks.streams._

val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)

val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
```

## Construction

Sinks are created using factory methods on the companion object. These methods fall into several categories based on what they do:

### Collecting

Gather elements into collections:

#### `Sink.collectAll[A]` — Collect Into a Chunk

Collects all elements into a [`Chunk[A]`](../../chunk.md):

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

Collect only the first three elements from a large stream:

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
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Sink[Nothing, A, Z]
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

Get the first element from a stream, or None if empty:

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

Get the last element from a stream:

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

Find the first even number in the stream:

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

Check if any element matches a condition:

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

Test whether all elements satisfy a condition:

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

Print each element as it is processed:

```scala mdoc:reset
import zio.blocks.streams._

val result = Stream(1, 2, 3).run(Sink.foreach[Int](x => println(s"Got: $x")))
```

### Async Aggregation and Search

Five of the aggregation, search, and effectful sinks have an `*Async` twin whose callback returns `Async` instead of a value. The twin consumes the same input and returns the same result type; only the callback's shape differs:

```scala
object Sink {
  def existsAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean]
  def findAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Option[A]]
  def foldLeftAsync[A, Z](z: Z)(f: (Z, A) => Async[Z])(implicit jtZ: JvmType.Infer[Z]): Sink[Nothing, A, Z]
  def forallAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean]
  def foreachAsync[A](f: A => Async[Unit]): Sink[Nothing, A, Unit]
}
```

Each stops where its synchronous twin stops:

| Async sink           | Sync twin       | Result      | Stops pulling at                                             |
|----------------------|-----------------|-------------|--------------------------------------------------------------|
| `Sink.existsAsync`   | `Sink.exists`   | `Boolean`   | the first `true`; otherwise end-of-stream, returning `false` |
| `Sink.findAsync`     | `Sink.find`     | `Option[A]` | the first match; otherwise end-of-stream, returning `None`   |
| `Sink.foldLeftAsync` | `Sink.foldLeft` | `Z`         | end-of-stream                                                |
| `Sink.forallAsync`   | `Sink.forall`   | `Boolean`   | the first `false`; otherwise end-of-stream, returning `true` |
| `Sink.foreachAsync`  | `Sink.foreach`  | `Unit`      | end-of-stream                                                |

Three properties hold across all five. At most one callback effect runs at a time, in encounter order, so these are sequential and back-pressured rather than concurrent — for concurrency, put it in the stream with [`Stream#mapParAsync`](./stream.md#streammapparasync) before the sink. No effect is started for input the sink never pulls, so short-circuiting still costs nothing after the decisive element. And a callback that fails or is cancelled fails or cancels the run as a *defect*, not as a value in the sink's typed error channel `E` — all five have `E = Nothing`.

`Sink.foldLeftAsync` carries the same `JvmType.Infer[Z]` evidence as `Sink.foldLeft`. It keeps the asynchronous fold aligned with the specialized synchronous one; the semantics are identical.

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

val readings = Stream(12, 7, 30, 21, 5)

// Sequential: one predicate effect at a time, stopping at 30.
val tooHot: Async[Either[Nothing, Boolean]] =
  readings.runAsync(Sink.existsAsync[Int](c => Async.succeed(c > 25)))

// The fold visits every element; the accumulator is threaded through Async.
val total: Async[Either[Nothing, Long]] =
  readings.runAsync(Sink.foldLeftAsync(0L)((sum: Long, c: Int) => Async.succeed(sum + c)))
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

// ByteArrayOutputStream ignores close(), so you can still call toByteArray()
val allBytes = bos.toByteArray()
// This works because ByteArrayOutputStream doesn't maintain any closeable resources
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

Like `Sink.fromOutputStream`, this sink intentionally does not close the writer. This gives you control over when to flush or close, allowing you to write multiple streams to the same writer or coordinate lifecycle with other operations.

### Custom

Advanced low-level use cases with direct reader protocol access:

#### Custom Sink Factories

`createAsync` is the cross-platform escape hatch for a native asynchronous drain. `createBoth` supplies independent native synchronous and asynchronous drains. The plain `create` factory is JVM-only because its callback consumes a blocking `SyncReader`:

```scala
object Sink {
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](sync: Reader.SyncReader[A] => Z,
                           async: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  // JVM only
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]
}
```

:::note
These factories give you direct access to the reader protocol. Await asynchronous pulls sequentially and prefer built-in sinks when possible.
:::

Here's a custom sink that computes the average of all integers in a stream:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

// A custom sink that computes the average of Ints
val average = Sink.create[Nothing, Int, Double] { reader =>
  def loop(sum: Long, count: Long): (Long, Long) = {
    val v = reader.readInt(Long.MinValue)
    if (v == Long.MinValue) (sum, count)
    else {
      val newSum = sum + v
      loop(newSum, count + 1)
    }
  }
  val (sum, count) = loop(0L, 0L)
  if (count == 0) 0.0 else sum.toDouble / count
}
```

This example shows how `Sink.create` works. `readInt` widens an `Int` to `Long`, leaving `Long.MinValue` available as an out-of-domain end marker. For full-domain `Long` and `Double` inputs, do not choose a data sentinel: allocate a reusable one-element primitive array and use `readLongs` or `readDoubles`, whose returned count reports data or end-of-stream without collisions. You'd use `Sink.create` when no built-in sink provides the exact aggregation or transformation logic you need — it is powerful but requires understanding the low-level [Reader protocol](../primitives/reader.md).

## Transforming Sinks

Every sink can be transformed using these instance methods:

### `Sink#contramap[A2]` — Pre-Process Input

Transforms the input elements before they reach the sink. The sink's result and error types are unchanged:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
}
```

The evidence describes the callback's **result** type `A0`, not the new sink input `A2`. The bound `A0 <: A` is variance-sound for contravariant `A`, while invariant `JvmType.Infer[A0]` lets the mapped reader advertise the callback's exact physical result lane. Primitive results therefore retain their exact lane; the `AnyRef` fallback deliberately uses the boxed reference lane. `contramapAsync` has the same evidence and representation rules for `A2 => Async[A0]`. Both adapters transform only elements requested by the wrapped sink, preserve short-circuiting, and leave reader closure to the run terminal.

`Sink#contramap` is the dual of `Sink#map`: it transforms what goes *in*, not what comes *out*:

```scala mdoc:reset
import zio.blocks.streams._

// A sink that counts the length of strings
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramap[Int, String](_.length)

val result = Stream("hello", "world").run(totalLength)
```

### `Sink#map[Z2]` — Transform Result

Transforms the result after the sink finishes draining:

```scala
abstract class Sink[+E, -A, +Z] {
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
}
```

Transform the result after draining:

```scala mdoc:reset
import zio.blocks.streams._

val countAsString: Sink[Nothing, Any, String] =
  Sink.count.map(n => s"Total: $n elements")

val result = Stream(1, 2, 3).run(countAsString)
```

### `Sink#mapError[E2]` — Transform Error

Transforms the error channel of a sink:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapError[E2](f: E => E2)(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
}
```

The `IsNothing` evidence records whether `E` is `Nothing`. For an infallible sink the method returns the same sink without allocating a wrapper; otherwise it maps typed errors:

```scala mdoc:compile-only
import zio.blocks.streams._

// No-op: drain never fails, so mapError is free
val mapped = Sink.drain.mapError[String](_.toString)
// At compile time: this is just a cast, no wrapper allocated

// Real mapping: fail can produce errors
val failing = Sink.fail("oops").mapError[RuntimeException](new RuntimeException(_))
```

### `Sink#contramapAsync[A2]` — Pre-Process Input Asynchronously

Applies an asynchronous function to each element before it reaches the sink:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
}
```

The evidence is the same as `Sink#contramap`'s and targets the callback's **result** type `A0`, not the new sink input `A2`. `g` runs sequentially, one effect at a time, as the wrapped sink requests elements, so short-circuiting is unchanged and `g` is never started for input the sink does not pull. A failed or cancelled `g` fails or cancels the run as a defect and stops pulling input; it does not appear in the sink's typed error channel.

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

// Resolve each identifier to a length before counting it
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramapAsync[Int, String](id => Async.succeed(id.length))

val result: Async[Either[Nothing, Long]] =
  Stream("hello", "world").runAsync(totalLength)
```

### `Sink#mapAsync[Z2]` — Transform Result Asynchronously

Transforms the result asynchronously after the sink finishes draining:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapAsync[Z2](f: Z => Async[Z2]): Sink[E, A, Z2]
}
```

`f` runs once, after the drain completes, so it does not change input consumption. When the sink fails, `f` is never started. Failure or cancellation of `f` fails or cancels the run as a defect.

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

val persistedCount: Sink[Nothing, Any, String] =
  Sink.count.mapAsync(n => Async.succeed(s"Total: $n elements"))

val result: Async[Either[Nothing, String]] =
  Stream(1, 2, 3).runAsync(persistedCount)
```

### `Sink#mapErrorAsync[E2]` — Transform Error Asynchronously

Transforms the typed error channel asynchronously:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapErrorAsync[E2](f: E => Async[E2])(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
}
```

It carries the same `IsNothing` evidence as `Sink#mapError`: for an infallible sink (`E = Nothing`) the method returns the same sink without allocating a wrapper, and `f` can never be evaluated. Successful results, upstream stream errors, and defects pass through untransformed; only the sink's own typed error reaches `f`. Failure or cancellation of `f` is a defect.

```scala mdoc:compile-only
import zio.blocks.async._
import zio.blocks.streams._

// Infallible: no wrapper is allocated and `f` can never run
val mapped = Sink.drain.mapErrorAsync[String](e => Async.succeed(e.toString))

// Fallible: the typed error is transformed before it reaches the `Left`
val failing = Sink.fail("oops").mapErrorAsync[RuntimeException](e => Async.succeed(new RuntimeException(e)))
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

A [Pipeline](./pipeline.md) can be applied to a Sink using `Pipeline#andThenSink`, producing a new Sink that pre-processes input elements through the pipeline:

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

The `NioSinks` object (JVM-only) provides sinks for Java NIO (`java.nio`) buffers and channels. NIO offers efficient buffers and both blocking and selector-based channel APIs, but these sinks use blocking channel writes; they do not expose selector-based non-blocking output. When you're writing to network sockets, files, or other NIO-based resources, these sinks give you a convenient way to drain streams directly into NIO data structures.

Traditional Java I/O (`OutputStream`, `Writer`) blocks threads and requires manual buffering for efficiency. `NioSinks.fromChannel` also blocks, but handles buffer allocation, position management, and flushing automatically (default 8KB), while typed variants like `NioSinks.fromByteBufferInt` and `NioSinks.fromByteBufferLong` eliminate boxing overhead by writing primitives directly to buffers you provide.

Choose `NioSinks.fromChannel` when blocking channel output is acceptable and you want automatic buffering for network sockets or files. Choose typed variants when you control buffer allocation and are streaming millions of primitives where boxing would degrade performance.

Each of these sinks implements both drains, so NIO output works under the blocking `Stream#run` and under `Stream#runAsync` alike. What the asynchronous drain removes is the thread parked waiting for stream *input*; the destination writes are the same blocking `java.nio` calls on either path. `NioSinks.fromChannel` does budget its asynchronous flush loop: when a channel write makes no progress it yields instead of spinning, so a stalled channel does not monopolise the caller. [Reader](../primitives/reader.md#from-native-asynchronous-sources) covers the NIO adapters on the source side in full, and this page does not duplicate them.

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

**`NioSinks.fromByteBuffer` and typed variants** — Write primitive streams directly into a pre-allocated NIO ByteBuffer:
- `NioSinks.fromByteBuffer` — writes individual `Byte` elements.
- `NioSinks.fromByteBufferInt`, `NioSinks.fromByteBufferLong`, `NioSinks.fromByteBufferFloat`, `NioSinks.fromByteBufferDouble` — write primitives directly using the buffer's native methods (`putInt`, `putLong`, etc.), one buffer write per element rather than one per byte.

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

This example allocates a 32-byte buffer (4 Longs × 8 bytes each), writes four `Long` values using `NioSinks.fromByteBufferLong` (which calls `putLong` on each element), then rewinds and reads them back to verify. The typed variant writes one 8-byte buffer operation per element, where the byte variant would require the stream to carry each byte separately.

The following example shows streaming voltage sensor readings through a calibration curve and buffering them for downstream computation. When processing sensor arrays or scientific measurements, pre-allocated buffers with typed sinks let each element be written through its exact primitive lane without boxing.

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

The typed sinks dispatch through their exact primitive lanes. `Long` and `Double` use collision-free bulk-count status, so `Long.MinValue`, `Long.MaxValue`, every finite or infinite `Double`, signed zero, and every raw NaN payload are written as ordinary data. There is no sentinel-value truncation restriction. The supplied buffer remains caller-owned and is not flipped, rewound, or closed by the sink. [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md) carries the per-lane end-of-stream table this rests on.

### From Channel Sink

The **`NioSinks.fromChannel`** constructor performs buffered writes to a `WritableByteChannel` (e.g., a network socket or file channel). This is the general-purpose NIO sink: it accumulates bytes in an internal buffer of size `bufSize` (default 8192), flushes when the buffer is full, and flushes again at end-of-stream. It does not close the caller-owned channel.

It handles `IOException` as a typed error, so failures surface as `Left(IOException)` from `Stream.run`. Use this for network I/O or when you can't pre-allocate a buffer. The channel I/O is blocking—NIO's non-blocking advantage comes when using selectors across many channels, which this sink does not expose.

Suppose you're collecting metrics from thousands of sensors (temperature, pressure, timestamps) and need to write them to a file efficiently. Using `NioSinks.fromChannel` with a file's `WritableByteChannel` gives you automatic buffering and eliminates manual position management.

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

This example demonstrates the most commonly used built-in sinks: `Sink.drain`, `Sink.count`, `Sink.collectAll`, `Sink.head`, `Sink.last`, and `Sink.take`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkBasicUsageExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkBasicUsageExample"
```

### Aggregation and Search

This example shows aggregation sinks (`Sink.foldLeft`, `Sink.sumInt`, `Sink.sumDouble`) and search sinks (`Sink.exists`, `Sink.forall`, `Sink.find`, `Sink.foreach`):

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkAggregationExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkAggregationExample"
```

### Transformations and Composition

This example demonstrates `Sink#contramap`, `Sink#map`, `Sink#mapError`, `Sink.fail`, `Sink.create`, and `Pipeline#andThenSink`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkTransformationExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkTransformationExample"
```

### Async Sinks End to End

This example builds a custom mean sink with `Sink.createBoth` — a `while` loop for the synchronous drain, an `Async#flatMap` recursion for the asynchronous one — then drives it and `Sink.existsAsync` through `Stream#runAsync`, unwrapping each `Either` at the end. It is JVM-only, because it finishes with `.block` to turn the `Async` into a value for `main`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/sink/SinkAsyncExample.scala")
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkAsyncExample"
```

## See Also

- [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md#async-terminals) — the terminals that select a sink's asynchronous drain, and the `Async[Either[E, Z]]` convention
- [Platform Differences](../execution-and-compatibility/platform-differences.md#sinkcreate-and-custom-sinks) — why `Sink.create` is JVM-only and what replaces it in shared source
- [Reader](../primitives/reader.md#the-reader-union) — the two reader kinds a sink drains, from the reader's side
- [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md) — how a primitive lane is chosen, and the per-lane end-of-stream table
- [Stream](./stream.md) — the producer a sink consumes
- [Pipeline](./pipeline.md) — transformations that can be attached to a sink's input
