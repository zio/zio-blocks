---
id: stream
title: "Stream"
sidebar_label: "Stream"
description: "The Stream data type: construction, transformation, resource safety, and the cross-platform async and JVM-only blocking terminal families."
keywords:
  - "Pull-Based Streams"
  - "Stateful Transformations"
  - "Async Operators"
  - "Blocking Terminals"
  - "Stream"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

`Stream[+E, +A]` is a **lazy, pull-based, typed-error stream** of elements that may fail with an error of type `E`. Nothing executes until a terminal operation is driven. Cross-platform asynchronous terminals return `Async[Either[E, Z]]`; the JVM also provides the existing blocking terminal family returning `Either[E, Z]`. Typed errors surface as `Left(e)`, while defects and cleanup failures fail the outer `Async` or propagate from a JVM blocking terminal:

```scala
abstract class Stream[+E, +A] {
  def runAsync[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E, ES, E3]
  ): Async[Either[E3, Z]]
  def runCollectAsync: Async[Either[E, Chunk[A]]]

  // JVM only
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E, ES, E3]
  ): Either[E3, Z]
}
```

`Stream` is purely functional, referentially transparent, and resource-safe:
- **Lazy**: descriptions of pipelines, not eager computations
- **Nonblocking across platforms**: `*Async` terminals drive synchronous or asynchronous readers without blocking JavaScript
- **JVM-compatible**: plain blocking terminals remain available on the JVM
- **Pull-based**: execution is driven from the sink backward through the pipeline
- **Typed errors**: distinguish recoverable errors (`E`) from untyped defects (`Throwable`)
- **Resource-safe**: RAII semantics ensure resources are released in all cases

### Asynchronous Source Constructors

The companion constructors whose names end in `Async` — `attemptAsync`, `attemptEvalAsync`, `deferAsync`, `evalAsync`, `fromAcquireReleaseAsync`, `fromIteratorAsync`, `fromReaderAsync`, and `unfoldAsync` — defer their `Async` thunk until the first reader operation is driven, and `Stream.unwrap` flattens an `Async[Stream[E, A]]` so that ordinary operators such as `flatMap`, `catchAll`, and `flatMapPar` compose with asynchronously produced streams. [Async Source Constructors](./async-execution.md#async-source-constructors) documents each of them, along with the laziness and error conventions they share; this page does not repeat them.

### Source Compatibility

`Reader` is an ordinary `abstract class`, not a sealed one, but every reader the library hands you is a `Reader.SyncReader[A]` or a `Reader.AsyncReader[A]`, so code that implements or accepts a reader must choose one kind or match both with a fallback case. Custom sinks cannot be written by subclassing `Sink`, whose two abstract drains are `private[streams]`; use `Sink.createAsync`, `Sink.createBoth`, or the JVM-only `Sink.create`. Plain terminals, `start`, `AsyncReader#toSync`, and `Sink.create` are JVM-only, so shared sources should migrate to `run*Async`, `startAsync`/`useReaderAsync`, and `createAsync`.

Async constructor callbacks remain lazy until the first drive, and managed/unmanaged names encode ownership. Do not compensate by eagerly opening a resource before constructing the stream. Cancellation closes an acquired reader and awaits its finalizer; `startAsync` is the exception because it explicitly transfers that responsibility to its caller.

## Motivation

Traditional eager sequences (like Scala `List`) fall short in **three critical dimensions**. Here's what `Stream[E, A]` solves for each:

**1. Efficiency — Wasteful Computation**

With eager evaluation, the entire dataset is processed upfront, regardless of how many elements you actually need. This example shows how much work is wasted:

```scala mdoc:compile-only
// With Scala List (eager evaluation)
val data = (1 to 1_000_000).toList
val result = data
  .map(_ * 2)          // eagerly: 1M multiplications
  .filter(_ > 10)      // eagerly: 1M comparisons
  .take(10)            // finally: keep only 10
// ❌ Wasted work: computed and discarded 999,990 elements!
```

The problem: `List` eagerly applies `.map` and `.filter` to all 1 million elements, even though only the first 10 passing elements matter. In data processing pipelines (parsing CSV files, filtering logs, transforming sensor streams), this is enormously wasteful.

With `Stream[E, A]`, the architecture is **inverted**: the **sink (consumer) pulls** from the stream. If the sink asks for only 10 elements, only ~20 calculations occur (enough to find 10 valid results after filtering):

```scala mdoc:compile-only
import zio.blocks.streams.*

// With Stream (lazy, pull-based evaluation)
val result: Either[Nothing, zio.blocks.chunk.Chunk[Int]] =
  Stream.fromRange((1 to 100))
    .map(_ * 2)
    .filter(_ > 10)
    .run(Sink.take(10))
// ✓ Computation stops after 10 valid elements are produced
// Only necessary work: ~20 multiplications, ~20 comparisons
```

This **short-circuiting** behavior is automatic and requires no special syntax.

**2. Resource Management — Error-Prone Cleanup**

When you open resources (file handles, network connections, database cursors), you must release them in **all** code paths—success, error, and even mid-stream cancellation. With eager sequences, this burden falls on the caller:

```
// ❌ With traditional Scala (manual resource management, uses var for mutable state)
import java.io.*

var file: BufferedReader = null
try {
  file = new BufferedReader(new FileReader("build.sbt"))
  var count = 0L
  var char = file.read()
  while (char != -1) {
    if (!Character.isWhitespace(char)) {
      count += 1
    }
    char = file.read()
  }
  count
} catch {
  case e: IOException =>
    throw e
} finally {
  if (file != null) file.close()  // ✓ Manual cleanup in finally
}
// ❌ Problem: You must remember the finally block
// ❌ Problem: If an exception occurs in the loop, cleanup must still run (easy to forget!)
// ❌ Problem: Scale to 10 resources? 50 resources? Manually nesting becomes error-prone
```

With `Stream[E, A]`, resource cleanup is **automatic, composable, and guaranteed**—even on error or if the sink cancels early:

```scala mdoc:compile-only
import zio.blocks.streams.*
import java.io.*

// With Stream (resource-safe RAII)
// Open a file and count non-whitespace characters
val charCount: Either[IOException, Long] =
  Stream
    .fromJavaReader(new FileReader("build.sbt"))  // lazily acquires file handle
    .filter(!_.isWhitespace)  // process only non-whitespace
    .count  // count all matching characters
// ✓ File automatically closes in finally block (success or error)
// ✓ If FileReader throws, or filter throws, or count throws—cleanup still runs
// ✓ No manual try/finally needed; no resource leak risk
// ✓ Multiple resources (files, connections, etc.) compose naturally
```

The key difference: `Stream` releases resources via **RAII** (Resource Acquisition Is Initialization) — the resource's lifetime is bound to the compiled stream's `close()` method, which the terminal operation (`run`) always calls in a `finally` block.

**3. Error Handling — Untyped Errors**

Traditional error handling conflates two categories: recoverable **domain errors** (e.g., parsing failed, validation failed) and fatal **defects** (e.g., `OutOfMemoryError`, `NullPointerException`). This makes it hard to write correct error recovery code:

```scala mdoc:compile-only
import scala.util.Try

// With Try/catch (untyped errors)
case class ParseError(msg: String)

def parseLines(lines: List[String]): Try[List[Int]] = Try {
  lines.map { line =>
    line.toInt  // throws NumberFormatException (defect, not domain error!)
  }
}

val result = parseLines(List("1", "abc", "3"))
result match {
  case util.Success(nums) => println(s"Parsed: $nums")
  case util.Failure(e) =>
    // ❌ Can't tell if 'e' is a parse error or a JVM defect
    // ❌ Must handle *all* exceptions the same way
    // ❌ Domain logic mixed with system-level exception handling
    println(s"Error: $e")
}
```

With `Stream[E, A]`, typed errors (`E`) are distinct from untyped defects (`Throwable`), enabling proper error recovery:

```scala mdoc:compile-only
import zio.blocks.streams.*

case class ParseError(msg: String)

// With Stream (typed errors)
val result: Either[ParseError, zio.blocks.chunk.Chunk[Int]] =
  Stream
    .fromIterable(List("1", "abc", "3"))
    .flatMap { line =>
      try {
        Stream.succeed(line.toInt)  // success path
      } catch {
        case _: NumberFormatException =>
          Stream.fail(ParseError(s"Not a number: $line"))  // typed error
      }
    }
    .runCollect

result match {
  case Left(parseError) =>
    // ✓ This branch is *only* for domain errors we chose to surface
    println(s"Parse error: ${parseError.msg}")
  case Right(nums) =>
    // ✓ Untyped defects (OutOfMemoryError, etc.) propagate as exceptions
    // ✓ Clear separation: Either[E, Z] is for recovery, uncaught exceptions are fatal
    println(s"Parsed: ${nums}")
}
```

The key distinction: `Either[ParseError, Z]` means domain errors are *recoverable* via `Left`; any uncaught `Throwable` defect propagates as an exception, which is correct—you cannot recover from running out of memory, only from bad input.

## Construction

Streams can be created from constants, collections, resources, and pull-based sources:

### Constant Streams

The simplest streams are single-element or empty streams.

#### `Stream.empty`

An empty stream that emits no elements and succeeds immediately:

```scala
object Stream {
  val empty: Stream[Nothing, Nothing]
}
```

The empty stream is useful as a base case in recursive stream builders or as a neutral element when concatenating:

```scala mdoc:reset
import zio.blocks.streams.*

val emptyStream = Stream.empty
val result = emptyStream.runCollect
// emptyStream contains no elements
```

#### `Stream.succeed[A]`

Wraps a single value of any type. Specialized overloads avoid boxing for primitives:

```scala
object Stream {
  def succeed[A](a: A): Stream[Nothing, A]
  def succeed(a: Int): Stream[Nothing, Int]
  def succeed(a: Long): Stream[Nothing, Long]
  def succeed(a: Double): Stream[Nothing, Double]
  // ... and Byte, Short, Char, Float, Boolean variants
}
```

When you call `Stream.succeed(value)`, the stream emits exactly one element and completes successfully. This is useful for wrapping a computed value into the stream abstraction:

```scala mdoc:reset
import zio.blocks.streams.*

val singleElement = Stream.succeed(42)
val result = singleElement.runCollect
```

The `Byte` overload remains a byte stream: `Stream.succeed(1.toByte)` has type `Stream[Nothing, Byte]`, uses the `Byte` representation lane, and compiles through `Reader.singleByte` rather than widening the element type to `Int`.

#### `Stream.fail[E]`

Creates a stream that fails immediately with a typed error:

```scala
object Stream {
  def fail[E](error: E): Stream[E, Nothing]
}
```

Use `fail` when you need to short-circuit a stream with a known error:

```scala mdoc:reset
import zio.blocks.streams.*

sealed trait ApiError
case class NotFound(id: String) extends ApiError

val failedStream = Stream.fail(NotFound("user-123"))
val result = failedStream.runDrain
// result is Left(NotFound("user-123"))
```

#### `Stream.die`

Throws an untyped defect (exception) immediately:

```scala
object Stream {
  def die(t: Throwable): Stream[Nothing, Nothing]
}
```

Use `die` for truly exceptional, unrecoverable conditions that should not be caught as typed errors:

```scala mdoc:reset
import zio.blocks.streams.*

val dieStream = Stream.die(new Exception("System failure"))
```

### From Collections

Streams can be created from existing collections and iterables, making it easy to convert `List`, `Array`, [`Chunk`](../chunk.md), or custom iterables into lazy streams:

#### `Stream.apply[A]`

Wraps a variable number of arguments into a stream:

```scala
object Stream {
  def apply[A](as: A*)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A]
}
```

This is the most natural way to lift a list of values:

```scala mdoc:reset
import zio.blocks.streams.*

val numbers = Stream(1, 2, 3, 4, 5)
val result = numbers.runCollect
```

#### `Stream.fromChunk[A]`

Converts a `Chunk` into a stream. Chunks are immutable, indexed sequences optimized for high-performance operations:

```scala
object Stream {
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Stream[Nothing, A]
}
```

Use this when you already have a `Chunk`:

```scala mdoc:reset
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
val stream = Stream.fromChunk(chunk)
val result = stream.runCollect
```

#### `Stream.fromIterable[A]`

Converts any `Iterable[A]` (List, Set, Vector, etc.) into a stream:

```scala
object Stream {
  def fromIterable[A](it: Iterable[A])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
}
```

This is useful when integrating with legacy Scala collections:

```scala mdoc:reset
import zio.blocks.streams.*

val list = List("a", "b", "c")
val stream = Stream.fromIterable(list)
val result = stream.runCollect
```

#### `Stream.fromIterator[A]`

Converts an `Iterator[A]` into a stream. The iterator is consumed lazily:

```scala
object Stream {
  def fromIterator[A](it: => Iterator[A])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
}
```

Create a stream from an iterator and collect all elements:

```scala mdoc:reset
import zio.blocks.streams.*

val iter = Iterator(10, 20, 30, 40)
val stream = Stream.fromIterator(iter)
val result = stream.runCollect
```

### From Ranges

Streams can be created from numeric ranges, providing an efficient way to generate sequences of integers without allocating memory upfront:

#### `Stream.range`

Emits integers from `from` (inclusive) to `until` (exclusive):

```scala
object Stream {
  def range(from: Int, until: Int): Stream[Nothing, Int]
}
```

This is memory-efficient (does not allocate intermediate collections):

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream.range(0, 5)
val result = nums.runCollect
```

#### `Stream.fromRange`

Converts a Scala `Range` object:

```scala
object Stream {
  def fromRange(range: Range): Stream[Nothing, Int]
}
```

Create a stream from a `Range` and collect elements:

```scala mdoc:reset
import zio.blocks.streams.*

val range = 1 to 10 by 2
val stream = Stream.fromRange(range)
val result = stream.runCollect
```

### Generators

These constructors create streams from functions and logic, useful for synthesizing infinite or computed sequences:

#### `Stream.repeat[A]`

Emits the same value infinitely:

```scala
object Stream {
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Stream[Nothing, A]
}
```

Infinite streams are safe because streams are lazy; nothing runs until you call a terminal operation with a stopping condition (like `take`):

```scala mdoc:reset
import zio.blocks.streams.*

val infinite = Stream.repeat(42)
val first5 = infinite.take(5)
val result = first5.runCollect
```

#### `Stream.unfold[S, A]`

A stateful generator that emits elements based on a fold-like transition function:

```scala
object Stream {
  def unfold[S, A](s: S)(f: S => Option[(A, S)])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
}
```

Each iteration, `f` receives the current state and returns either `None` (stop) or `Some((element, nextState))`. This is useful for generating Fibonacci numbers or other sequences defined by a recurrence relation:

```scala mdoc:reset
import zio.blocks.streams.*

val fibonacci = Stream.unfold((0, 1)) {
  case (a, b) => Some((a, (b, a + b)))
}
val first10 = fibonacci.take(10)
val result = first10.runCollect
```

### Side Effects

These constructors embed effects and deferred computation into streams, running actions at stream execution time:

#### `Stream.eval[A]`

Runs an arbitrary side effect and emits nothing:

```scala
object Stream {
  def eval(f: => Any): Stream[Nothing, Nothing]
}
```

Use `eval` when you want a side effect in a stream (e.g., logging, metrics) but no element:

```scala mdoc:reset
import zio.blocks.streams.*

val sideEffect = Stream.eval(println("Executing side effect"))
val result = sideEffect.runDrain
```

#### `Stream.attempt[A]`

Wraps a potentially throwing computation, converting non-fatal `Throwable`s into a typed error. Fatal errors (like `OutOfMemoryError`) are not caught and propagate as exceptions:

```scala
object Stream {
  def attempt[A](f: => A)(implicit jtA: JvmType.Infer[A]): Stream[Throwable, A]
}
```

Use `attempt` when you have legacy code that throws exceptions:

```scala mdoc:reset
import zio.blocks.streams.*

def unsafeJsonParse(s: String): Int = s.toInt

val parsed = Stream.attempt(unsafeJsonParse("42"))
val result = parsed.runCollect
```

#### `Stream.attemptEval`

Evaluates a side effect and converts any thrown exception into a typed `Throwable` error. Unlike `eval`, this captures exceptions and emits nothing:

```scala
object Stream {
  def attemptEval(f: => Any): Stream[Throwable, Nothing]
}
```

Use `attemptEval` when you need to safely execute an effect that might throw, but you don't need to emit any elements:

```scala mdoc:compile-only
import zio.blocks.streams.*

val effect = Stream.attemptEval {
  val file = new java.io.File("nonexistent.txt")
  if (!file.exists()) throw new java.io.FileNotFoundException("File not found")
}
val result = effect.runDrain
```

#### `Stream.defer[A]`

Defers the execution of a side effect until the stream is run:

```scala
object Stream {
  def defer(f: => Unit): Stream[Nothing, Nothing]
}
```

Defer side effects until the stream executes:

```scala mdoc:reset
import zio.blocks.streams.*

val deferred = Stream.defer(println("Effect runs when stream executes"))
val result = deferred.runDrain
```

#### `Stream.suspend[E, A]`

Defers the creation of a stream until run time, useful for recursive stream definitions:

```scala
object Stream {
  def suspend[E, A](stream: => Stream[E, A]): Stream[E, A]
}
```

Define a recursive stream safely:

```scala mdoc:reset
import zio.blocks.streams.*

def countDown(n: Int): Stream[Nothing, Int] =
  if (n <= 0) Stream.empty
  else Stream.suspend(Stream.succeed(n) ++ countDown(n - 1))

val result = countDown(5).runCollect
```

### I/O

Streams can read from external I/O sources like files and readers, automatically managing resource cleanup:

#### `Stream.fromInputStream`

Reads bytes from a Java `InputStream`, managing the resource:

```scala
object Stream {
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Byte]
}
```

The stream automatically closes the input stream when done:

```scala mdoc:reset
import zio.blocks.streams.*
import java.io.ByteArrayInputStream

val data = new ByteArrayInputStream("Hello".getBytes)
val bytes = Stream.fromInputStream(data)
val result = bytes.runCollect
```

#### `Stream.fromJavaReader`

Reads characters from a Java `Reader`:

```scala
object Stream {
  def fromJavaReader(r: java.io.Reader): Stream[java.io.IOException, Char]
}
```

Read characters from a string reader:

```scala mdoc:reset
import zio.blocks.streams.*
import java.io.StringReader

val reader = new StringReader("hello world")
val stream = Stream.fromJavaReader(reader)
val result = stream.runCollect
```

#### `Stream.fromInputStreamUnmanaged`

Reads bytes from a Java `InputStream` without automatic resource management. The caller is responsible for closing the stream:

```scala
object Stream {
  def fromInputStreamUnmanaged(is: java.io.InputStream): Stream[java.io.IOException, Byte]
}
```

Use this when you need to manage the stream's lifecycle yourself, for example when the stream is created from a long-lived resource:

```scala mdoc:compile-only
import zio.blocks.streams.*
import java.io.ByteArrayInputStream

val data = new ByteArrayInputStream("Data".getBytes)
val bytes = Stream.fromInputStreamUnmanaged(data)
val result = bytes.runCollect
// Caller must close data when done
```

#### `Stream.fromJavaReaderUnmanaged`

Reads characters from a Java `Reader` without automatic resource management. The caller is responsible for closing the reader:

```scala
object Stream {
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[java.io.IOException, Char]
}
```

Use this when you need to manage the reader's lifecycle yourself:

```scala mdoc:compile-only
import zio.blocks.streams.*
import java.io.StringReader

val reader = new StringReader("managed externally")
val stream = Stream.fromJavaReaderUnmanaged(reader)
val result = stream.runCollect
// Caller must close reader when done
```

## Transformations

Streams provide powerful operations for transforming elements, flattening nested structures, filtering, and managing state:

### Element-wise Transformations

These operations apply functions to stream elements one-by-one, applying the transformation lazily as elements are pulled:

#### `Stream#map[B]`

Applies a function to each element:

```scala
abstract class Stream[+E, +A] {
  def map[B](f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B]
}
```

`map` does not run immediately; it builds up a description of the transformation. Only when you call a terminal operation does the mapping happen:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val doubled = nums.map(_ * 2)
val result = doubled.runCollect
```

For bounded concurrency, [`Stream#mapPar`](#streammappar) applies a synchronous function with up to `n` applications active and [`Stream#mapParAsync`](#streammapparasync) keeps up to `n` `Async` callbacks in flight; both are unordered. See [Bounded Concurrency](#bounded-concurrency).

**Key point:** `Stream#map` is covariant in the output type because it preserves the error type and only transforms elements. Output-changing operations such as `map`, `collect`, `flatMap`, `mapAccum`, `scan`, and `zipWith` take `JvmType.Infer` evidence for their result type; that result evidence selects the physical output lane. Type-preserving operations retain the source's known lane, including when the static element type is widened.

#### `Stream#mapError[E2]`

Transforms typed errors without affecting elements:

```scala
abstract class Stream[+E, +A] {
  def mapError[E2](f: E => E2): Stream[E2, A]
}
```

Use `mapError` to convert one error type to another:

```scala
import zio.blocks.streams.*

sealed trait ApiError
case class ServerError(msg: String) extends ApiError
case class NetworkError() extends ApiError

val mayFail: Stream[NetworkError, String] = Stream.fail(NetworkError())
val mapped = mayFail.mapError(e => ServerError("Connection failed"))
```

#### `Stream#filter`

Emits only elements that satisfy a predicate:

```scala
abstract class Stream[+E, +A] {
  def filter(pred: A => Boolean): Stream[E, A]
}
```

Short-circuits: as soon as the sink says "stop," filtering stops:

```scala mdoc:reset
import zio.blocks.streams.Stream

val nums = Stream(1, 2, 3, 4, 5)
val evens = nums.filter(_ % 2 == 0)
val result = evens.runCollect
```

#### `Stream#collect[B]`

Applies a partial function, emitting only defined results:

```scala
abstract class Stream[+E, +A] {
  def collect[B](pf: PartialFunction[A, B])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
}
```

This combines filtering and mapping in one step:

```scala mdoc:reset
import zio.blocks.streams.*

val mixed = Stream(1, "a", 2, "b", 3)
val numbers = mixed.collect { case n: Int => n }
val result = numbers.runCollect
```

### Stateful Transformations

These operations maintain internal state while processing elements, allowing you to fold computations into the transformation:

#### `Stream#mapAccum[S, B]`

Maintains state while transforming each element:

```scala
abstract class Stream[+E, +A] {
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B))(implicit jtB: JvmType.Infer[B]): Stream[E, B]
}
```

`mapAccum` threads a state value through the transformation. At each step, you receive the current state and the element, return a new state and output element:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val indexed = nums.mapAccum(0)((idx, x) => (idx + 1, (idx, x)))
val result = indexed.runCollect
```

#### `Stream#mapAccumAsync[S, B]`

The asynchronous twin of `mapAccum`: the step returns an `Async`, and the state is still threaded strictly in order.

```scala
abstract class Stream[+E, +A] {
  def mapAccumAsync[S, B](init: S)(f: (S, A) => Async[(S, B)])(implicit
    jtB: JvmType.Infer[B]
  ): Stream[E, B]
}
```

At most one invocation of `f` is active at a time, which is what keeps the accumulator meaningful — there is no concurrency here to reorder the steps or to hand two invocations the same state. A failure inside `f` is a defect, not a typed error. The implicit `JvmType.Infer[B]` records the physical lane of the new output type and is supplied by the compiler.

```scala mdoc:compile-only
import zio.blocks.async.*
import zio.blocks.streams.*

val events   = Stream("open", "write", "close")
val numbered = events.mapAccumAsync(0L)((seq, event) => Async.succeed((seq + 1, s"$seq:$event")))
```

This operator is also catalogued with the rest of the sequential asynchronous family in [Async Operators](./async-execution.md#streammapaccumasync), and [Stateful Asynchronous Operators](#stateful-asynchronous-operators) runs it end to end alongside `scanAsync`, `takeWhileAsync`, and `ensuringAsync`.

#### `Stream#scan[S]`

Like `mapAccum`, but emits the accumulator rather than a mapped value, starting with `init` — so the output stream has one more element than the input:

```scala
abstract class Stream[+E, +A] {
  def scan[S](init: S)(f: (S, A) => S)(implicit jtS: JvmType.Infer[S]): Stream[E, S]
}
```

This is useful for computing running totals, moving averages, or other cumulative statistics:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4)
val cumsum = nums.scan(0)(_ + _)
val result = cumsum.runCollect
```

#### `Stream#scanAsync[S]`

The asynchronous twin of `scan`: the fold step returns an `Async`, and the accumulator is still emitted at each step.

```scala
abstract class Stream[+E, +A] {
  def scanAsync[S](init: S)(f: (S, A) => Async[S])(implicit jtS: JvmType.Infer[S]): Stream[E, S]
}
```

The output stream carries one more element than the input, because `init` is emitted before the first step runs. As with `mapAccumAsync`, the steps are sequential and a failure inside `f` is a defect.

```scala mdoc:compile-only
import zio.blocks.async.*
import zio.blocks.streams.*

val amounts  = Stream(120, -40, 75)
val balances = amounts.scanAsync(0L)((balance, amount) => Async.succeed(balance + amount))
```

`balances` emits `0`, `120`, `80`, `155`. See [Async Operators](./async-execution.md#streamscanasync) for the same entry alongside the rest of the asynchronous family.

### Flat-Mapping (Nested Streams)

`flatMap[E2, E3, B]` — Maps each element to a stream and flattens the results.:

```scala
abstract class Stream[+E, +A] {
  def flatMap[E2, E3, B](f: A => Stream[E2, B])(implicit
    errorConcat: Concat.WithOut[E, E2, E3],
    jtB: JvmType.Infer[B]
  ): Stream[E3, B]
}
```

`Stream#flatMap` is sequential: streams are processed one at a time, in order. This is essential for resource safety: if each inner stream acquires a resource, `Stream#flatMap` ensures they are released in proper FIFO order:

```scala mdoc:reset
import zio.blocks.streams.*

val ids = Stream(1, 2, 3)
val expanded = ids.flatMap(id => Stream(s"${id}-a", s"${id}-b"))
val result = expanded.runCollect
```

For the concurrent counterpart, which merges up to `n` inner streams at once in arrival order, see [`Stream#flatMapPar`](#streamflatmappar) in [Bounded Concurrency](#bounded-concurrency).

#### `Stream.flattenAll[E, A]`

Flattens a stream of streams into a single stream, processing them sequentially:

```scala
object Stream {
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

This is equivalent to `flatMap(identity)`. Use `flattenAll` when you already have a stream of streams and want to flatten it without applying a transformation:

```scala mdoc:reset
import zio.blocks.streams.*

val nested = Stream.fromIterable(List(
  Stream(1, 2),
  Stream(3, 4)
))
val flat = Stream.flattenAll(nested)
val result = flat.runCollect
```

To flatten a stream of streams concurrently instead of sequentially, the companion also offers [`Stream.mergeAll`](#streammergeall), which drains up to `maxOpen` inner streams at a time. See [Bounded Concurrency](#bounded-concurrency).

## Windowing

Streams can be grouped, sliced, and scanned to process data in temporal windows. These operations group elements into chunks and slide windows over the stream for batch processing:

### `Stream#grouped[A]`

Collects elements into fixed-size chunks:

```scala
abstract class Stream[+E, +A] {
  def grouped(n: Int): Stream[E, Chunk[A]]
}
```

The last chunk may contain fewer than `n` elements:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val groups = nums.grouped(2)
val result = groups.runCollect
```

### `Stream#sliding[A]`

Creates a sliding window of size `n`, optionally stepping by `step` elements:

```scala
abstract class Stream[+E, +A] {
  def sliding(n: Int, step: Int = 1): Stream[E, Chunk[A]]
}
```

This is useful for computing local statistics or detecting patterns in sequences:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val windows = nums.sliding(3, step = 1)
val result = windows.runCollect
```

## Combining Streams

Streams can be sequentially concatenated, zipped together, or merged:

### Sequential Concatenation

`++[E2, E3, A2, A3]` or `concat[E2, E3, A2, A3]` — Emits all elements of the first stream, then all elements of the second stream:

```scala
abstract class Stream[+E, +A] {
  final def ++[E2, E3, A2, A3](that: Stream[E2, A2])(implicit
    errorConcat: Concat.WithOut[E, E2, E3],
    valueConcat: Concat.WithOut[A, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3] = concat(that)
}
```

The result type follows the same widening rules as Scala 3 unions:

- identical types stay unchanged (`A ++ A => A`)
- subtypes widen to the supertype (`Dog ++ Animal => Animal`)
- siblings with a common meaningful supertype widen to that supertype (`Dog ++ Cat => Animal`, when both extend a sealed `Animal`)
- otherwise the result is a disjoint union (`String ++ Int => String | Int`)

On Scala 3, disjoint concat results are native unions. On Scala 2, the same/subtype and sibling cases collapse to the wider existing type (zero-cost, values are reused as-is); only types without a shared meaningful supertype fall back to `Either[L, R]`.

Evaluation is sequential: the second stream only starts when the first completes:

```scala mdoc:reset
import zio.blocks.streams.*

val first = Stream(1, 2)
val second = Stream(3, 4)
val combined = first ++ second
val result = combined.runCollect
```

For unrelated element types, Scala 3 produces a direct union while Scala 2 produces `Either`:

<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2.13">

```scala
val combined: Stream[Nothing, Either[String, Int]] =
  Stream.succeed("left") ++ Stream.succeed(1)
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3.x">

```scala
val combined: Stream[Nothing, String | Int] =
  Stream.succeed("left") ++ Stream.succeed(1)
```

  </TabItem>
</Tabs>

```scala mdoc
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val concatResult = (Stream.succeed("left") ++ Stream.succeed(1)).runCollect

assert(concatResult == Right(Chunk[String | Int]("left", 1)))
```

The error channel follows the same rules. Same/subtype errors collapse; unrelated errors remain disjoint:

```scala mdoc
import zio.blocks.streams.*

sealed trait LeftError
case class Boom(msg: String) extends LeftError
case class Missing(code: Int)

val left: Stream[LeftError, String] = Stream.fail(Boom("boom"))
val right = Stream.succeed(true)

left.runCollect

val failed = left ++ (Stream.fail(Missing(404)): Stream[Missing, Boolean])
failed.runCollect
```

There is no separate `choice` operator anymore. Use `++` / `concat` for all sequential combination; the result type already reflects the Scala 3-style union semantics.

### Zipping

Zips two streams together as tuples:

```scala
abstract class Stream[+E, +A] {
  def &&[E2, E3, B, C](that: Stream[E2, B])(implicit
    errorConcat: Concat.WithOut[E, E2, E3],
    zip: Stream.Zip[A, B, C],
    jtC: JvmType.Infer[C]
  ): Stream[E3, C]
}
```

The error type `E3` is the `Concat` of the two error types, and the element type `C` is chosen by the `Stream.Zip` evidence, which flattens nested pairs so that `a && b && c` produces a `Stream` of `(A, B, C)`.

The result streams have the same length as the shorter input:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val chars = Stream('a', 'b')
val zipped = nums && chars
val result = zipped.runCollect
```

## Bounded Concurrency

Four operators bound the concurrency of a stream. `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` take synchronous callbacks; `Stream#mapParAsync` takes a callback that returns an `Async`. All four run on either of two execution paths — a synchronous one backed by worker threads, or an asynchronous one — and all four carry an `n = 1` degradation guarantee.

The two execution paths are different engines with different bounds, and which one a program gets is decided by the kind of reader its pipeline compiles to — not by which operator it called.

### The Operators

| Operator                            | Element callback                               | What `n` bounds                |
|-------------------------------------|------------------------------------------------|--------------------------------|
| `Stream#mapPar(n)(f)`               | `A => B`                                       | concurrent applications of `f` |
| `Stream#mapParAsync(n)(f)`          | `A => Async[B]`                                | callbacks in flight            |
| `Stream#flatMapPar(n)(f)`           | `A => Stream[E1, B]`                           | open inner streams             |
| `Stream.mergeAll(maxOpen)(streams)` | none; `streams` is a `Stream[E, Stream[E, A]]` | open inner streams             |

Each of the four begins with `require` on its parallelism argument, so passing zero or a negative number raises `IllegalArgumentException` at description time rather than producing an empty or sequential stream.

#### `Stream#mapPar`

```scala
def mapPar[B](n: Int)(f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Applies `f` to each element with up to `n` applications active. Output is unordered: elements leave in the order their applications finish, not the order they entered. `f` is synchronous, so this is the operator for CPU-bound or blocking work on the JVM — and, as [Threading and Platform Behaviour](#threading-and-platform-behaviour) explains, the operator that overlaps nothing at all once the pipeline is on the asynchronous lane.

#### `Stream#mapParAsync`

```scala
def mapParAsync[B](n: Int)(f: A => Async[B])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Keeps at most `n` `Async` callbacks in flight and emits each result in completion order. It is the only member of the family whose callback can suspend: `f` returns a description, so the engine holds `n` unfinished effects rather than `n` busy threads. A failure inside the callback is a defect, not a typed error, and it fails the outer terminal effect.

Unlike `mapPar`, this operator has no synchronous materialization at all. It compiles to the shared asynchronous concurrent reader on both platforms, which is why its `n` counts suspended callbacks rather than workers.

#### `Stream#flatMapPar`

```scala
def flatMapPar[E1 >: E, B](n: Int)(f: A => Stream[E1, B])(implicit jtB: JvmType.Infer[B]): Stream[E1, B]
```

Applies `f` to each element to produce an inner stream, then merges up to `n` inner streams concurrently. It has no engine of its own. Past the `n = 1` branch, its body is a single delegation:

```scala
Stream.mergeAll[E1, B](n)(
  this.asInstanceOf[Stream[E1, A]].map(f)(JvmType.Infer.boxed[Stream[E1, B]])
)(jtB)
```

One fan-in engine, not two. Everything below about slots, admission, ordering, and shutdown is stated for `mergeAll`, and `flatMapPar` inherits all of it unchanged.

#### `Stream.mergeAll`

```scala
def mergeAll[E, A](maxOpen: Int)(streams: Stream[E, Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Merges up to `maxOpen` inner streams concurrently into one output stream, with elements arriving in completion order. Note the argument: `streams` is a *stream of streams*, not a varargs list, so a fixed collection of sources is fed in through a constructor such as `Stream.fromIterable`.

```scala mdoc:compile-only
import zio.blocks.streams._

val sources: Stream[Nothing, Stream[Nothing, Int]] =
  Stream.fromIterable((0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100)))

val merged: Stream[Nothing, Int] = Stream.mergeAll(4)(sources)
```

### Semantics

#### What `n` Means

`n` is a count of selector slots, not of threads. The concurrent reader allocates one `AsyncSelector` with `n + 1` entries: `n` entries for inner work, and one final entry reserved for the outer source.

```
┌────────────────────────────────────────────────────────────────────┐
│ Async.selectorWithCapacity(n + 1)      one selector, n + 1 entries │
├────────────────────────────────────────────────────────────────────┤
│ entry 0     inner work: callback in flight, or an open inner       │
│ entry 1     inner work: callback in flight, or an open inner       │
│   ...       up to n of these; `active` counts the occupied ones    │
│ entry n-1   inner work: callback in flight, or an open inner       │
├────────────────────────────────────────────────────────────────────┤
│ entry n     the outer source        re-armed only while active < n │
└────────────────────────────────────────────────────────────────────┘
```

The extra entry is what keeps the operator from pulling ahead. The source entry is re-armed only while `active < n`, so the reader stops asking upstream for elements the moment every inner slot is taken, and resumes the instant one frees. Nothing queues behind a full set of slots.

Whether a slot corresponds to a thread depends entirely on which engine materialized. On the JVM's synchronous lane each slot does have a worker thread behind it; on the asynchronous lane a slot is one pending `Async` and there are no threads involved.

#### Ordering

All four operators are unordered with respect to input position. An element leaves when its work finishes, so output is in arrival order. The property tests compare results with `.toSet` for exactly this reason: there is no input-order assertion available to make.

The single exception is `n = 1`, which is exactly sequential — and the `n = 1` tests do assert exact [`Chunk`](../chunk.md) equality, because at that value the operator is not the concurrent engine at all. See [The `n = 1` Guarantee](#the-n-1-guarantee).

:::warning[Unordered means unordered on Scala.js too]
Single-threaded execution does not restore input order. The concurrent engine runs on Scala.js with immediately-ready effects, and its arrival-order semantics are retained there. Code that depends on Scala.js emitting source order is a bug that will not reproduce on the JVM.
:::

If input order is what you need, use sequential `map`, `mapAsync`, or `flatMap`. Sorting afterwards — `.runCollectAsync.map(_.map(_.sorted))` — recovers *a* total order, but not the input one unless the elements happen to sort that way.

#### Boundedness

Two separate things are bounded, and conflating them leads to the wrong buffer size.

The first is the number of open inner streams. Admission is guarded by an active count: `occupied` is a `Boolean` array of length `n` and `active` is the number of `true` entries, and a new element is accepted only into a free index. A full set of slots stops admission at the source rather than accumulating work anywhere.

The second is the number of buffered elements, and it exists only on the JVM's synchronous lane. `ConcurrentMapParReader` allocates `n` input and `n` output `SpscRingBuffer`s, each of `bufferSize` capacity, so a `mapPar(8)` with the default buffer holds at most 1024 elements in transit. The concurrent merge readers allocate one output ring per slot on the same basis.

The asynchronous engine has no rings of its own. `AsyncConcurrentReaders.mapPar` does not even take a buffer size: each slot holds exactly one unfinished effect, so in-flight work is bounded at `n` and nothing more. `AsyncConcurrentReaders.merge` does take one, but spends it on compiling each inner stream (`stream.compile(0, bufferSize)`), where it sizes whatever buffered stages that inner stream contains.

#### Admission and Replenishment

A slot is occupied before the work in it begins, and — for merge — freed only after that work has fully closed.

```
  free
    │  outer element arrives: occupied(i) = true, active += 1
    ▼
  constructing      the Async that builds the child runs HERE, in the slot
    │  child installed
    ▼
  draining          elements reach the consumer in arrival order
    │  MapWorkerEnd: slot entry replaced by the child's close effect
    ▼
  closing           slot still held; no successor may be admitted yet
    │  MapWorkerClosed: releaseSlot(i), then armSource()
    ▼
  free
```

That two-phase ending is the part worth remembering. When an inner stream reaches its end the engine does not free the slot; it replaces the slot's selector entry with the inner reader's own close effect, which resolves to a second signal. Only then does `releaseSlot` clear `occupied(i)`, decrement `active`, and re-arm the source. A slot is therefore never handed to a successor before its predecessor's finalizers have run to completion.

`mapPar` and `mapParAsync` have the shorter version of this, because a callback has no reader to close: the slot is released in the same step that hands the value to the consumer, and the source is re-armed there.

The "constructing" phase is where the slot accounting surprises people. With `flatMapPar(n)(a => Stream.unwrap(f(a)))`, the effect `f(a)` that *produces* the child runs inside the slot the child will later occupy — they share one of the `n`, they do not get one each. [Async children and slot accounting](#async-children-and-slot-accounting) below demonstrates this with a running program.

#### The `n = 1` Guarantee {#the-n-1-guarantee}

At `n = 1` each operator degrades to its sequential twin. This is a guarantee about the code path, not an optimization note: the branch sits in the public operator body, above every allocation.

```scala
def mapPar[B](n: Int)(f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B] = {
  require(n >= 1, s"mapPar requires n >= 1, got $n")
  if (n == 1) map(f)
  else {
    jtB.jvmType
    new Stream.MapPar[E, A, B](this, n, f, elementRepresentation, jtB.jvmType)
  }
}
```

The other three are shaped identically: `mapParAsync(1)` returns `mapAsync(f)`, `flatMapPar(1)` returns `flatMap(f)`, and `mergeAll(1)` returns `streams.flatMap(identity)` — or, when the outer stream is already a `Mapped` node, the fused `mapped.self.flatMap(mapped.f)` that skips the intermediate stream entirely. No reader, no selector, no thread, and no ring is allocated in any of those cases, because the decision is made before the concurrent node is ever constructed.

The practical consequence is that `n` can be a configuration value that is allowed to be `1`. A deployment that dials concurrency down to one gets the sequential operator, with its exact input ordering, rather than a concurrent engine running at width one.

### Buffer Sizing

Concurrent readers on the synchronous lane use ring-buffer queues sized by the enclosing buffer-size region. The default is 64 (the library-internal `Stream.DefaultBufferSize`, which is not part of the public API).

```scala mdoc:compile-only
import zio.blocks.streams._

def heavyComputation(n: Int): Int = n * n

val sized: Stream[Nothing, Int] =
  Stream.bufferSize(256) {
    Stream.range(0, 1000000).mapPar(8)(heavyComputation)
  }
```

`Stream.bufferSize(n)` requires a positive power of two and rejects anything else with `IllegalArgumentException`:

```scala
require(n >= 1 && (n & (n - 1)) == 0, s"bufferSize must be a positive power of 2, got $n")
```

Nested regions use the innermost size. Larger buffers absorb bursty producers; smaller ones cut memory when many slots are open at once. The default suits most workloads. On the asynchronous lane its reach is narrower: `mapPar` and `mapParAsync` ignore it entirely, because each slot holds exactly one unfinished effect and the bound is the slot count, while `mergeAll` and `flatMapPar` still pass it down, where it sizes the buffered stages inside each compiled inner stream.

`Pipeline.buffer(n)` is a different tool for a different job: it inserts a bounded buffer between two stages rather than resizing the queues inside one concurrent reader, and it participates in the asynchronous reader graph on both platforms.

### Error Behaviour

First failure wins. The concurrent reader commits a terminal state exactly once, and the first typed source or inner-stream error to reach that commit becomes the result; the fold short-circuits and the operation surfaces as `Left(e)`.

```scala mdoc:compile-only
import zio.blocks.streams._

// A typed error anywhere upstream terminates every worker.
val fromUpstream: Stream[String, Int] =
  Stream
    .range(0, 1000)
    .flatMap(n => if (n == 500) Stream.fail("bad element") else Stream.succeed(n))
    .mapPar(4)(identity)

// A typed error in one inner stream terminates the merge.
val fromInner: Stream[String, Int] =
  Stream.mergeAll(4)(
    Stream.fromIterable(
      List(Stream.range(0, 100), Stream.fail("inner error"), Stream.range(200, 300))
    )
  )
```

Both of those collect to a `Left`. Elements that were already emitted stay emitted — ordering is arrival-based, so a consumer may well have seen output from other slots before the failing one reached the commit point.

Once a failure is committed, cleanup runs and every sibling is torn down. A failure *during* that cleanup is attached to the primary failure rather than substituted for it: the engine calls `StreamError.attachCleanupReplay(primary, secondary)`, so the error a caller sees is still the one that caused the termination, with the cleanup problem carried alongside it. The same rule holds on the successful path, where a cleanup failure with no primary becomes the failure via `StreamError.attachCleanup(null, cleanupFailure)`.

:::note[What the tests actually prove]
`MergeInnerErrorSpec` asserts `result.isLeft` across the generic, `Int`, `Long`, `Float`, and `Double` lanes under a ten-second timeout, and again with four coordinated simultaneous failures per lane under a thirty-second one. That establishes the general shape — a failing inner terminates the merge promptly and does not hang — but it does not pin down *which* failure wins when several race at `n > 1`. Do not write code that depends on a particular one of several concurrent errors being the one reported.
:::

A defect is different from a typed error. The `mapParAsync` callback failing, or an `ensuring` finalizer throwing, is a defect and fails the outer terminal effect rather than appearing in the `E` channel.

### Cancellation and Shutdown

Shutdown is cooperative and ordered, and it is driven from the consumer end. Closing, failing, or cancelling the reader runs the same owned-cleanup path.

For `mergeAll` and `flatMapPar` that path is three steps, in order: shut the selector down, close every installed inner reader, then close the outer source. The steps are joined rather than sequenced-and-abandoned, so a failure in one does not skip the others — each later close still runs, and its failure is attached to the first. For `mapPar` and `mapParAsync` there are no inner readers, so it is the selector shutdown followed by the upstream close.

Cancellation goes through the `Async` cancellation protocol rather than thread interruption: the in-flight child run is cancelled with cleanup, and the reader settles its completion from the cancellation's outcome. A cancellation that arrives after the reader is already closed is recognised as stale and does not turn a clean close into a failure.

Closing is idempotent and it waits. The reader will not report closed until the cleanup it owns has finished, which is the property the slot lifecycle above depends on — a slot's successor cannot start while the predecessor's finalizers are still running.

[Cancellation](./async-execution.md#cancellation) covers the protocol these readers participate in, and [Async.Running#cancel](../async.md#runningcancel) documents the primitive underneath it.

### Threading and Platform Behaviour

Which engine a concurrent operator materializes depends on the kind of reader its upstream compiled to, and the two engines differ far more than the two platforms do.

| Materialization                 | JVM reader                              | Scala.js reader                 | What actually overlaps                     |
|---------------------------------|-----------------------------------------|---------------------------------|--------------------------------------------|
| `mapPar`, synchronous upstream  | `ConcurrentMapParReader` family         | `Reader.MappedInt` and siblings | one worker thread per slot                 |
| `mapPar`, asynchronous upstream | `AsyncConcurrentReaders.mapPar`         | `AsyncConcurrentReaders.mapPar` | nothing; `f` is wrapped in `Async.succeed` |
| `mapParAsync`, always           | `AsyncConcurrentReaders.mapPar`         | `AsyncConcurrentReaders.mapPar` | whatever the `Async` callbacks suspend on  |
| `mergeAll` from a `Reader`      | `AsyncConcurrentReaders.merge`          | `AsyncConcurrentReaders.merge`  | whatever the inner streams suspend on      |
| `mergeAll` via the interpreter  | `IntConcurrentMergeReader` and siblings | `Reader.FlatMappedRef`          | one drainer thread per slot on the JVM     |

Read the second row before choosing an operator. Once the upstream compiles to an `AsyncReader`, `Platform.createMapParReaderFromReader` routes `mapPar` to the shared asynchronous engine with the mapping function wrapped as `Async.succeed(f(a))` — an already-complete effect. The slots and the selector are all still there, but there is nothing for them to overlap, on either platform. `mapParAsync` exists precisely because a callback that returns a real `Async` is the only way to get concurrency out of that lane.

#### The JVM

Workers on the synchronous lane run on virtual threads where the runtime provides them. `Platform.startVirtualThread` obtains `Thread.ofVirtual()` reflectively, so the module builds and runs on any supported JDK and uses virtual threads on JDK 21 and later; if the reflective lookup fails for any reason it starts a named daemon platform thread instead.

The threads are named, which makes them identifiable in a thread dump. `mapPar` names its workers `zio-blocks-mappar-worker-<n>-<index>` and its dispatcher `zio-blocks-mappar-coordinator-<n>`, where `<n>` counts reader instances and `<index>` identifies the worker within one reader; merge uses `zio-blocks-merge-drainer-<n>-<index>` and `zio-blocks-merge-coordinator-<n>` on the same scheme. Those strings are prefixes rather than final names: the virtual-thread builder is created with `Thread.ofVirtual().name(prefix, 0L)`, whose two-argument form appends a counter, so a virtual worker appears in a dump as `zio-blocks-mappar-worker-<n>-<index>0`. Only the platform-thread fallback, which calls `setName` directly, uses the name verbatim.

#### Scala.js

There is no parallelism on Scala.js either way — `Platform.supportsConcurrency` is `false` and `Platform.startVirtualThread` throws `UnsupportedOperationException`. But "no parallelism" resolves into two different mechanisms, and only one of them is sequential in the sense the scaladoc suggests.

When the pipeline compiles end to end to a `SyncReader`, the operator really is sequential: `Platform.createMapParReaderFromReader` builds an ordinary mapped reader (`Reader.MappedIntInt`, `Reader.MappedInt`, `Reader.MappedLong`, and the rest), and the synchronous merge path builds a `Reader.FlatMappedRef` — one that throws `UnsupportedOperationException` if an inner stream turns out to be asynchronous.

When the pipeline is on the asynchronous lane, the concurrent engine runs, with immediately-ready effects standing in for suspension. The effect is still sequential, but the *semantics* are the concurrent engine's: **unordered arrival is retained**.

:::warning[The scaladoc is a throughput claim, not an ordering claim]
The scaladoc on `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` says that on Scala.js each "degrades to sequential `map`" or "sequential `flatMap`". That describes the synchronous-lane case as though it were the whole story. Never read it as a promise about element order.
:::

[Platform Differences](./platform-differences.md#concurrency-and-threading) states the same split from the platform side, including the full capability surface of `Platform`.

### Laws

`ConcurrentLawsSpec` asserts three equalities, all of them as set equality (`.toSet == .toSet`), since neither side of any of them is ordered:

- `Stream.mergeAll(1)(streams)` equals `streams.flatMap(identity)`
- `stream.mapPar(1)(f)` equals `stream.map(f)`
- `stream.flatMapPar(n)(f)` equals `Stream.mergeAll(n)(stream.map(f))`

The first two additionally hold as exact equality, and not because the engine happens to preserve order — at `n = 1` the operator body *returns the sequential operator itself*, so the two sides are the same description. The third holds only as a set equality: both sides run the concurrent engine at width `n`, so both are in arrival order and neither has an input order to compare against.

The third law is also a statement about the implementation rather than a coincidence, since `flatMapPar` is defined as that right-hand side. Reading it as "there is one fan-in engine" is more useful than reading it as a property that had to be checked.

### Bounded Concurrency Examples

#### `mapPar`, `mergeAll`, and `flatMapPar` on the JVM

The three snippets below use blocking terminals, which exist only on the JVM. In cross-platform code, swap the terminal for its `*Async` twin — `runCollectAsync`, `runFoldAsync` — and the operator itself is unchanged.

Expensive per-element work across eight slots:

```scala mdoc:compile-only
import zio.blocks.streams._

val doubled = Stream
  .range(0, 1000)
  .mapPar(8) { n =>
    Thread.sleep(1)
    n * 2
  }
  .runCollect
```

`doubled` is a `Right` holding all thousand elements, in arrival order rather than `0, 2, 4, …`.

Ten sources drained four at a time:

```scala mdoc:compile-only
import zio.blocks.streams._

val sources = Stream.fromIterable((0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100)))
val summed  = Stream.mergeAll(4)(sources).runFold(0L)(_ + _)
```

A sum is order-insensitive, which is what makes it a safe thing to compute over an unordered merge: `summed` is `Right(499500)` on every run.

One sub-stream per element, eight drained at a time:

```scala mdoc:compile-only
import zio.blocks.streams._

val flattened = Stream
  .range(0, 50)
  .flatMapPar(8)(i => Stream.range(i * 20, (i + 1) * 20))
  .runFold(0L)(_ + _)
```

Again `Right(499500)`: the same 1000 integers, reached through 50 inner streams instead of 10.

#### A Worked `mapParAsync`

The two runnable files below live in the `streams-examples` module. This one makes arrival order visible rather than asserting it: every callback hands back an unresolved `Completer`, and a driver thread then settles the four of them in reverse. The collected chunk comes back reversed with respect to the input.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/MapParAsyncExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.MapParAsyncExample"
```

It prints the two orders side by side:

```
input order:   Chunk(10,20,30,40)
arrival order: Right(Chunk(40,30,20,10))
```

#### Async Children and Slot Accounting

`flatMapPar(n)(a => Stream.unwrap(f(a)))` is the idiom for asynchronously produced children, and this example pins down what a slot covers. A gauge is incremented when child construction starts and decremented by the child's finalizer, so it counts exactly the elements occupying a slot; a `CyclicBarrier(2)` forces each construction to wait for a partner, so the program can only finish if two really are in flight at once.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/FlatMapParAsyncChildrenExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.FlatMapParAsyncChildrenExample"
```

```
elements: Right(List(10, 11, 20, 21, 30, 31, 40, 41))
peak slot occupancy: 2 of 2
```

Peak occupancy is 2 and not 4, even though all four outer elements are available immediately, because the construction effect holds the slot its child will use. For sequential asynchronous children, `flatMap(a => Stream.unwrap(f(a)))` is the operator you want instead.

### Comparison with Other Libraries

The concurrent surface is small, and what distinguishes it is less the operator names than what a caller has to bring along to use them.

| Feature              | ZIO Blocks Streams                 | fs2                | Kyo               | Ox                    | Pekko                      |
|----------------------|------------------------------------|--------------------|-------------------|-----------------------|----------------------------|
| Concurrent operators | `mapPar`, `mergeAll`, `flatMapPar` | `parEvalMap`       | `mapParUnordered` | `mapPar`              | `mapAsync`, `flatMapMerge` |
| Effect system        | none required                      | cats-effect        | Kyo               | none; virtual threads | Akka                       |
| Typed errors         | `Either[E, Z]`                     | `ApplicativeError` | Kyo effects       | exceptions            | none                       |

The table compares contracts, not speed. Cross-provider throughput rankings require the specific benchmark classes that were built to compare like with like, and none of the numbers from those runs belong in a row next to a feature name.

## Other Operations

Common utilities for deduplication, draining, and error recovery:

### Filtering Duplicates

These operations remove duplicate elements, useful for deduplicating streams before processing:

#### `Stream#distinct[A]`

Emits only unique elements (using a mutable `HashSet` internally):

```scala
abstract class Stream[+E, +A] {
  def distinct: Stream[E, A]
}
```

This consumes memory proportional to the number of unique elements:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 2, 3, 3, 3)
val unique = nums.distinct
val result = unique.runCollect
```

#### `Stream#distinctBy[K]`

Emits only elements whose key (computed by `f`) has not been seen before:

```scala
abstract class Stream[+E, +A] {
  def distinctBy[K](f: A => K): Stream[E, A]
}
```

This deduplicates elements by a computed key, keeping only the first occurrence of each key:

```scala mdoc:compile-only
import zio.blocks.streams.*

case class Person(id: Int, name: String)

val people = Stream(
  Person(1, "Alice"),
  Person(2, "Bob"),
  Person(1, "Alice2"),  // same id as first, dropped
  Person(3, "Charlie")
)

val unique = people.distinctBy(_.id)
val result = unique.runCollect
```

### Skipping and Taking

These operations skip or limit elements, allowing you to keep or drop unwanted portions of the stream:

#### `Stream#drop`

Skips the first `n` elements:

```scala
abstract class Stream[+E, +A] {
  def drop(n: Long): Stream[E, A]
}
```

Dropping the first 3 elements and collecting the remainder:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
val remaining = nums.drop(3)
val result = remaining.runCollect
```

#### `Stream#take`

Emits at most the first `n` elements, then stops:

```scala
abstract class Stream[+E, +A] {
  def take(n: Long): Stream[E, A]
}
```

This naturally short-circuits: the stream stops pulling from upstream:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream.range(0, 1000)
val first10 = nums.take(10)
val result = first10.runCollect
```

#### `Stream#takeWhile`

Emits elements while a predicate is true, then stops:

```scala
abstract class Stream[+E, +A] {
  def takeWhile(pred: A => Boolean): Stream[E, A]
}
```

Taking elements while they are less than 6 stops early without processing the rest:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
val firstFive = nums.takeWhile(_ < 6)
val result = firstFive.runCollect
```

#### `Stream#takeWhileAsync`

The asynchronous twin of `takeWhile`, for a predicate that has to await something before it can answer:

```scala
abstract class Stream[+E, +A] {
  def takeWhileAsync(pred: A => Async[Boolean]): Stream[E, A]
}
```

Elements are tested sequentially, and the first `false` closes upstream — so the element that failed the test is not emitted, and nothing beyond it is pulled. Predicate failure is a defect. No `JvmType.Infer` evidence appears here because the element type does not change.

```scala mdoc:compile-only
import zio.blocks.async.*
import zio.blocks.streams.*

val feed      = Stream(120, -40, 75, 0, 999)
val untilZero = feed.takeWhileAsync(amount => Async.succeed(amount != 0))
```

The same entry appears in [Async Operators](./async-execution.md#streamtakewhileasync).

### Interspersing

`intersperse[A2, A3]` — Inserts a separator value between every two elements.:

```scala
abstract class Stream[+E, +A] {
  def intersperse[A2, A3](sep: A2)(implicit
    valueConcat: Concat.WithOut[A, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E, A3]
}
```

This is useful for rendering comma-separated lists or row delimiters:

```scala mdoc:reset
import zio.blocks.streams.*

val items = Stream("a", "b", "c")
val separated = items.intersperse(", ")
val result = separated.runCollect
```

### Repeating

`repeated` — Rematerializes the stream after each clean completion, emitting the whole sequence again indefinitely. A typed error or a defect terminates the repetition.:

```scala
abstract class Stream[+E, +A] {
  def repeated: Stream[E, A]
}
```

This creates an infinite repetition of the stream:

```scala mdoc:reset
import zio.blocks.streams.*

val original = Stream(1, 2)
val repeated = original.repeated.take(6)
val result = repeated.runCollect
```

### Side Effects

`tapEach` — Applies a function to each element for side effects, passing the element through unchanged.:

```scala
abstract class Stream[+E, +A] {
  def tapEach(f: A => Unit): Stream[E, A]
}
```

Use `tapEach` for logging or metrics:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val logged = nums.tapEach(x => println(s"Element: $x"))
val result = logged.runCollect
```

## Error Handling

Streams distinguish between recoverable business errors and unexpected exceptions, providing separate recovery mechanisms for each:

### Typed Error Vs Untyped Defect

ZIO Blocks distinguishes two error channels:

- **Typed errors (`E`)**: Recoverable business logic errors. Returned as `Left(e)` from terminal operations.
- **Untyped defects (`Throwable`)**: Unexpected exceptions (bugs, system failures). Propagate as thrown exceptions.

Internally, typed errors are wrapped in `StreamError` (a non-fatal exception) and caught by the terminal operation to surface as `Left(e)`. Untyped `Throwable`s are not caught and propagate upward.

This separation allows you to:
- Use `catchAll` and `orElse` for business logic errors
- Use `catchDefect` or try-catch for unexpected exceptions
- Avoid accidentally silencing real bugs by catching all errors

Streams distinguish between recoverable domain errors and fatal defects, with flexible recovery patterns:

### Recovering From Typed Errors

These operations handle typed errors gracefully by recovering with alternative streams:

#### `Stream#catchAll[E2, A2, A3]`

Recovers from any typed error by switching to a recovery stream:

```scala
abstract class Stream[+E, +A] {
  def catchAll[E2, A2, A3](f: E => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E2, A3]
}
```

The recovery function receives the error and can return a new stream:

```scala mdoc:reset
import zio.blocks.streams.*

sealed trait Error
case object NotFound extends Error

val mayFail: Stream[Error, String] = Stream.fail(NotFound)
val recovered = mayFail.catchAll(_ => Stream.succeed("default"))
val result = recovered.runCollect
```

#### `Stream#orElse[E2, A2, A3]`

If this stream fails, tries the fallback stream. The fallback is evaluated lazily, only on error:

```scala
abstract class Stream[+E, +A] {
  def orElse[E2, A2, A3](that: => Stream[E2, A2])(implicit
    valueConcat: Concat.WithOut[A, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E2, A3]
}
```

`||` is an alias for `orElse`:

```scala mdoc:reset
import zio.blocks.streams._

val primary = Stream.fail("error")
val fallback = Stream.succeed(42)
val result = (primary || fallback).runCollect
```

### Recovering From Defects

`catchDefect[E2, E3, A2, A3]` — Catches untyped defects (exceptions not wrapped as typed errors) using a partial function.:

```scala
abstract class Stream[+E, +A] {
  def catchDefect[E2, E3, A2, A3](
    f: PartialFunction[Throwable, Stream[E2, A2]]
  )(implicit
    errorConcat: Concat.WithOut[E, E2, E3],
    valueConcat: Concat.WithOut[A, A2, A3],
    jtA3: JvmType.Infer[A3]
  ): Stream[E3, A3]
}
```

Use `catchDefect` when you need to handle unexpected exceptions that were not wrapped by `attempt`:

```scala mdoc:compile-only
import zio.blocks.streams.*

val risky = Stream.die(new IllegalArgumentException("Not allowed"))
val safe = risky.catchDefect {
  case e: IllegalArgumentException => Stream.succeed(-1)
}
val result = safe.runCollect
```

## Resource Management

**The Problem:** Resources like files, database connections, and network sockets must be explicitly closed after use. If you just process them in a stream and forget to close, you leak resources. If an error occurs during processing, manual cleanup code might be skipped.

**The Solution:** ZIO Blocks streams provide three patterns for safe, automatic resource cleanup:

### `Stream.fromAcquireRelease[R, E, A]`

Acquires a resource, uses it in a stream, and **guarantees cleanup regardless of success or failure**:

```scala
object Stream {
  def fromAcquireRelease[R, E, A](
    acquire: => R,                                    // How to open the resource
    release: R => Unit = (r: R) =>                    // How to close it (defaults to .close())
      r match { 
        case ac: AutoCloseable => ac.close()
        case _ => ()
      }
  )(use: R => Stream[E, A]): Stream[E, A]
}
```

This is the fundamental pattern for safe resource handling:
1. **Acquire** — opens the resource (runs once, before streaming)
2. **Use** — streams elements from the resource
3. **Release** — closes the resource in a `finally` block (always runs, even on error)

Here's an example with automatic cleanup:

```scala mdoc:compile-only
import zio.blocks.streams.*

case class DatabaseConnection(id: String) {
  def close(): Unit = println(s"Closing connection $id")
  def query(q: String): List[String] = List("result1", "result2")
}

val managed = Stream.fromAcquireRelease(
  acquire = {
    println("Opening database connection")
    DatabaseConnection("db-1")
  },
  release = _.close()  // Guaranteed to run even if streaming fails
)(conn => Stream.fromIterable(conn.query("SELECT *")))

val result = managed.runCollect
// Output:
// Opening database connection
// Closing database connection  <-- always happens
```

Even if the stream fails, cleanup runs:

```scala mdoc:compile-only
import zio.blocks.streams.*

val managed = Stream.fromAcquireRelease(
  acquire = { println("Opening"); "resource" },
  release = { r => println(s"Closing $r") }
)(_ => Stream.fail("error occurred"))

val result = managed.runCollect
// Output:
// Opening
// Closing resource  <-- cleanup still runs even with error
// result: Either[String, Chunk[Nothing]] = Left("error occurred")
```

### `Stream.fromResource[R, E, A]`

Uses a ZIO Blocks `Resource[R]` (more abstract, composable resource type) within a stream:

```scala
object Stream {
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A]
}
```

Use `fromResource` when you already have a `Resource` value, or when you need resource composition. The resource is acquired at stream start and released when the stream terminates:

```scala mdoc:compile-only
import zio.blocks.streams.*
import zio.blocks.scope.Resource

val resource = Resource.acquireRelease(acquire = {
  println("Acquiring resource")
  42
})(release = { value =>
  println(s"Releasing resource with value: $value")
})

val stream = Stream.fromResource(resource) { value =>
  Stream(value, value * 2, value * 3)
}

val result = stream.runCollect
```

### `Stream#ensuring`

Adds a **cleanup action to any stream**, regardless of how it is created. The finalizer runs in a `finally` block:

```scala
abstract class Stream[+E, +A] {
  def ensuring(finalizer: => Unit): Stream[E, A]
}
```

Use `ensuring` for simple cleanup tasks that don't fit the acquire-release pattern:

```scala mdoc:compile-only
import zio.blocks.streams.*

val stream = Stream(1, 2, 3)
  .ensuring {
    println("Stream finished (success or error)")
  }

val result = stream.runCollect
```

The finalizer always runs, in a `finally` block:

```scala mdoc:compile-only
import zio.blocks.streams.*

val managed = Stream(1, 2, 3)
  .ensuring { println("Cleaned up") }

val result = managed.runCollect
```

### `Stream#ensuringAsync`

The asynchronous twin of `ensuring`, for cleanup that is itself an `Async` — closing a socket, flushing a remote session, releasing a lease:

```scala
abstract class Stream[+E, +A] {
  def ensuringAsync(finalizer: => Async[Unit]): Stream[E, A]
}
```

The finalizer is registered lazily and awaited exactly once when the materialized stream closes: on normal completion, on failure, on early termination such as `take` or `takeWhileAsync` cutting the stream short, and on cancellation. Awaited, not merely started — the close does not complete until the finalizer does. A failure inside the finalizer is a defect, and when the stream had already failed, that defect is attached to the primary failure rather than replacing it.

```scala mdoc:compile-only
import zio.blocks.async.*
import zio.blocks.streams.*

val session = Stream(1, 2, 3)
  .ensuringAsync(Async.succeed(println("session closed")))

val closed = session.runCollectAsync
```

What drives that close is the terminal. Under `runCollectAsync` and every other terminal the library closes the reader for you, and so does `useReaderAsync`; under `startAsync` you own the reader, and the finalizer has not run until you await `close()`. [Resource Management](./async-execution.md#resource-management) works through the three cases.

## Running Streams

There are two terminal families, and which one you reach for is a platform decision rather than a stylistic one.

The **cross-platform** family is the one whose names end in `Async`: `runAsync`, `runCollectAsync`, `runDrainAsync`, `runFoldAsync`, `runForeachAsync`/`foreachAsync`, `countAsync`, `existsAsync`, `findAsync`, `forallAsync`, `headAsync`, and `lastAsync`. Each returns `Async[Either[E, Z]]`, stays lazy until driven, and awaits reader cleanup on success, failure, or cancellation. It drives a synchronous and an asynchronous pipeline alike, and it is the only family that compiles for both targets. [Async Terminals](./async-execution.md#async-terminals) documents the family and the `Async[Either[E, Z]]` convention it follows.

The **JVM-only** family is everything documented in the rest of this section — `run`, `runCollect`, `runDrain`, `runFold`, `runForeach`/`foreach`, `count`, `exists`, `find`, `forall`, `head`, and `last` — together with `start`, covered under [Manual Pull via `start`](#manual-pull-via-start). Those sixteen members, counting `runFold`'s four overloads, are the entire JVM-only surface of `Stream`: the terminals among them park a thread and return a plain `Either[E, Z]`, and `start` hands back a blocking `Reader.SyncReader[A]`. They live in `StreamPlatformSpecific`, whose Scala.js copy has an empty body, so calling one from shared code fails to compile for the JavaScript target rather than failing at runtime. The [availability matrix](./platform-differences.md#availability-matrix) lists them member by member.

Each heading below therefore carries a one-line note naming its cross-platform form.

### Collecting Results

These operations accumulate or examine stream results, running the entire stream to completion:

#### `Stream#runCollect`

JVM only. The cross-platform form is `runCollectAsync`.

Collects all elements into a `Chunk[A]`:

```scala
abstract class Stream[+E, +A] {
  def runCollect: Either[E, Chunk[A]]
}
```

This is the most common terminal operation for extracting results:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val result = nums.runCollect
// result is Right(Chunk(1, 2, 3, 4, 5))
```

#### `Stream#run[E2, Z]`

JVM only. The cross-platform form is `runAsync`.

Runs the stream with a custom sink, producing result `Z`:

```scala
abstract class Stream[+E, +A] {
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E, ES, E3]
  ): Either[E3, Z]
}
```

Use `run` when you need a specialized sink operation:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val sum = nums.run(Sink.foldLeft(0)((acc, x) => acc + x))
// sum is Right(15)
```

### Discarding Results

These operations consume streams without collecting their elements, useful when you only care about side effects:

#### `Stream#runDrain`

JVM only. The cross-platform form is `runDrainAsync`.

Consumes all elements and discards them, returning `Unit`:

```scala
abstract class Stream[+E, +A] {
  def runDrain: Either[E, Unit]
}
```

Use `runDrain` when you only care about side effects:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val sideEffect = nums.tapEach(x => println(s"Processing $x"))
val result = sideEffect.runDrain
```

#### `Stream#runForeach`

JVM only. The cross-platform forms are `runForeachAsync` and its alias `foreachAsync`.

Applies a function to each element for side effects:

```scala
abstract class Stream[+E, +A] {
  def runForeach(f: A => Unit): Either[E, Unit]
}
```

Alias `foreach` also exists:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val result = nums.foreach(x => println(s"Got: $x"))
```

### Aggregations

These operations reduce streams to single values, aggregating elements into results:

#### `Stream#runFold[Z]`

JVM only. The cross-platform form is `runFoldAsync`.

Folds all elements using an accumulator, returning the final result:

```scala
abstract class Stream[+E, +A] {
  def runFold[Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Either[E, Z]
}
```

This is the most general aggregation, equivalent to `reduce` or `fold` on eager sequences:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4)
val sum = nums.runFold(0)(_ + _)
```

Specialized overloads for primitives avoid boxing:

```scala
def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int]
def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long]
def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double]
```

#### `Stream#count`

JVM only. The cross-platform form is `countAsync`.

Returns the number of elements:

```scala
abstract class Stream[+E, +A] {
  def count: Either[E, Long]
}
```

Counting elements in a stream:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val total = nums.count
```

#### `Stream#head`

JVM only. The cross-platform form is `headAsync`.

Returns the first element (or `None` if empty):

```scala
abstract class Stream[+E, +A] {
  def head: Either[E, Option[A]]
}
```

Getting the first element without collecting the entire stream:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val first = nums.head
```

#### `Stream#last`

JVM only. The cross-platform form is `lastAsync`.

Returns the last element (or `None` if empty):

```scala
abstract class Stream[+E, +A] {
  def last: Either[E, Option[A]]
}
```

Getting the final element of the stream:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val last = nums.last
```

#### `Stream#find[A]`

JVM only. The cross-platform form is `findAsync`.

Returns the first element satisfying a predicate:

```scala
abstract class Stream[+E, +A] {
  def find(pred: A => Boolean): Either[E, Option[A]]
}
```

Finding the first element matching a condition:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val firstEven = nums.find(_ % 2 == 0)
```

#### `Stream#exists[A]`

JVM only. The cross-platform form is `existsAsync`.

Returns `true` if any element satisfies a predicate, short-circuiting:

```scala
abstract class Stream[+E, +A] {
  def exists(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if any element is greater than 35:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val hasLargeValue = nums.exists(_ > 35)
```

#### `Stream#forall[A]`

JVM only. The cross-platform form is `forallAsync`.

Returns `true` if all elements satisfy a predicate, short-circuiting:

```scala
abstract class Stream[+E, +A] {
  def forall(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if all elements are positive:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val allPositive = nums.forall(_ > 0)
```

## Integration with Pipeline and Sink

Streams compose with pipelines and sinks to form complete data processing flows:

### Using Pipelines

`via[B]` — Applies a `Pipeline[A, B]` transformation to the stream.:

```scala
abstract class Stream[+E, +A] {
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B]
}
```

Pipelines are composable transformations that can be reused across streams and sinks. Common pipelines include `Pipeline.map`, `Pipeline.filter`, `Pipeline.take`, and `Pipeline.drop`:

```scala mdoc:reset
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val pipe = Pipeline.filter((x: Int) => x > 2).andThen(Pipeline.map((x: Int) => x * 10))
val result = nums.via(pipe).runCollect
```

Pipelines are useful when you want to build reusable transformation logic:

```scala mdoc:reset
import zio.blocks.streams.*

def positiveIntsPipe: Pipeline[Int, Int] =
  Pipeline.filter((x: Int) => x > 0)

val mixed = Stream(-2, -1, 0, 1, 2)
val positives = mixed.via(positiveIntsPipe)
val result = positives.runCollect
```

### Understanding Sinks

A `Sink[+E, -A, +Z]` is a consumer of elements of type `A` that produces a result `Z` or fails with `E`. Sinks are contravariant in `A` (they can accept a supertype of what they expect). Common sinks include:

- `Sink.collectAll: Sink[Nothing, A, Chunk[A]]` — collects all elements
- `Sink.drain: Sink[Nothing, Any, Unit]` — discards all elements
- `Sink.count: Sink[Nothing, Any, Long]` — counts elements
- `Sink.foldLeft: Sink[Nothing, A, Z]` — folds elements with an accumulator
- `Sink.head: Sink[Nothing, A, Option[A]]` — takes the first element
- `Sink.foreach: Sink[Nothing, A, Unit]` — applies a function to each element

When you call `stream.run(sink)`, the stream is compiled to a `Reader` and the sink drains it, consuming all elements and producing the result.

## Low-Level Pull with Reader

`Reader[+Elem]` is the low-level, pull-based source that backs every stream at execution time. Use cross-platform `startAsync` for a caller-owned `Reader.AsyncReader`, or `useReaderAsync` for bracketed access that awaits close on every outcome. The JVM additionally provides blocking `start` with a [`Scope`](../resource-management/scope.md).

### Manual Pull via `start`

`startAsync` transfers ownership to its caller, which must await `close()`. Prefer `useReaderAsync` when ownership need not escape. On the JVM, `start` opens a blocking reader within a `Scope`, which closes it when the scope exits:

```scala
abstract class Stream[+E, +A] {
  def startAsync: Async[Reader.AsyncReader[A]]
  def useReaderAsync[Z](f: Reader.AsyncReader[A] => Async[Z]): Async[Z]
  // JVM only
  def start(implicit scope: Scope): scope.$[Reader.SyncReader[A]]
}
```

`start` is JVM only, and its result type is `Reader.SyncReader[A]` rather than an undifferentiated `Reader[A]`: a pipeline with asynchronous stages still works under it, because the JVM runtime bridges those boundaries by blocking. Shared code has no such member and must use `startAsync` or `useReaderAsync` instead, both of which hand back a `Reader.AsyncReader[A]`. [Manual Pull Across Platforms](./platform-differences.md#manual-pull-across-platforms) compares the three, and [`Stream#startAsync`](./async-execution.md#streamstartasync) covers the ownership rules that come with the asynchronous form.

Use `start` to manually pull elements within a resource scope:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/ManualPullUsingStart.scala")
```

Use these methods when you need element-by-element control rather than running through a Sink. Do not overlap asynchronous pulls: an async reader allows one active pull, and its lifecycle operations must be awaited.

### The Reader Protocol

The pull protocol uses a **sentinel value** to signal end-of-stream:

- `read(sentinel)` — returns the next element, or `sentinel` when exhausted
- `close()` — signals the consumer is done
- `isClosed` — checks whether the reader is closed

For primitive types, specialized methods avoid boxing:

- `readBoolean(sentinel: Int): Int`
- `readByte(): Int`
- `readChar(sentinel: Int): Int`
- `readShort(sentinel: Int): Int`
- `readInt(sentinel: Long): Long`
- `readLong(sentinel: Long): Long`
- `readFloat(sentinel: Double): Double`
- `readDouble(sentinel: Double): Double`

These are the eight exact physical methods selected by `Reader.jvmType`. The tag describes the reader's actual representation and method contract, so a known primitive lane survives type-preserving wrappers and static widening. Operations that produce a different element type select their output lane from result `JvmType.Infer` evidence.

:::note
Avoid holding references to a `Reader` obtained via `start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Implementation Notes

ZIO Blocks Streams achieves zero-boxing via compile-time type detection and dual compilation strategies:

### JVM Primitive Specialization

By default, Scala's type system boxes primitive values into objects, which wastes memory and is slower. ZIO Blocks specializes all eight JVM primitive representations: `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, and `Double`. `JvmType.Infer[A]` records a result type's physical lane at construction and at output-changing operations; type-preserving operations carry an already-known lane forward.

For example, an `Int` pipeline uses `readInt(Long.MinValue)` instead of boxing. A `Long` or `Double` internal pull cannot use a collision-free scalar sentinel, so it calls `readLongs` or `readDoubles` with a length-one array and interprets the returned count as EOF/data status. This preserves every `Long` and `Double` value:

```scala
if (jvmType eq JvmType.Int) {
  val i = source.readInt(Long.MinValue)
  // ... unboxed, fast path
} else {
  val o = reader.read(EndOfStream)  // generic boxed path
  // ...
}
```

This optimization is transparent: you write normal, high-level code, and the compiler and runtime automatically use the fast path for primitives.

### Dual Compilation: Recursive Vs Interpreter

Each stream node compiles in two ways:

1. **Recursive (`compile`)**: Builds a tree of `Reader` objects, where each operation wraps the previous one. This is fast for shallow pipelines (< 100 operations).

2. **Flat-Array Interpreter (`compileInterpreter`)**: For deep pipelines (> 100 operations), the recursive approach hits Scala's default stack-depth limit (~100) and risks `StackOverflowError`. Instead, the interpreter compiles the entire pipeline into a flat array of operations, executed iteratively.

The switch happens at `DepthCutoff = 100`. You should never see this in normal use, but it ensures that pipelines of any depth are safe.

A second split sits alongside this one: a graph made only of synchronous nodes compiles to the synchronous engine, and a graph containing any asynchronous node compiles to a separate, heap-allocated asynchronous engine. That choice is made once per materialization, never per element, and never from an annotation you write. [Why two engines](./async-execution.md#why-two-engines) explains what it buys, and why a purely synchronous stream pays nothing for it.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt.** Here are the available examples:

---

### Basic Usage

This example demonstrates constructing streams from collections, transforming elements with `Stream#map` and `Stream#filter`, and collecting results:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamBasicUsageExample.scala")
```

To run this example:

```bash
sbt "streams-examples/runMain stream.StreamBasicUsageExample"
```

### Flat-Mapping Nested Streams

This example shows how `Stream#flatMap` sequences multiple streams and flattens the results:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamFlatMapExample.scala")
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamFlatMapExample"
```

### Error Handling

This example demonstrates typed error recovery with `fail`, `catchAll`, and `orElse`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamErrorHandlingExample.scala")
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamErrorHandlingExample"
```

### Resource Management

This example shows how `fromAcquireRelease` and `ensuring` manage resources safely:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamResourceExample.scala")
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamResourceExample"
```

### Windowing and Scanning

This example demonstrates `grouped`, `sliding`, and `scan` for windowing and stateful transformations:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamWindowingExample.scala")
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamWindowingExample"
```

### Stateful Asynchronous Operators

This example runs `takeWhileAsync`, `mapAccumAsync`, `scanAsync`, and `ensuringAsync` in a single pipeline, with every callback completing on another thread. It is JVM-only, because it ends in `.block`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamAsyncStatefulExample.scala")
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamAsyncStatefulExample"
```

## Native Asynchronous Byte Readers

Each platform ships adapters that turn a native byte source into a `Reader.AsyncReader[Byte]`, which `Stream.fromReader` then lifts into a stream: `AsyncNioReaders.fromChannel` and `fromSocket` on the JVM, `ReadableStreamReaders.fromReadableStream` on Scala.js, each with an `Unmanaged` variant that leaves the native source caller-owned. [Reader](./reader.md#from-native-asynchronous-sources) documents them, together with their chunking and EOF rules and the way source failures reach the typed error channel.

## See Also

- [Asynchronous Stream Execution](./async-execution.md) — the full asynchronous constructor, operator, and terminal API, and how one `Stream` type describes both execution modes
- [Platform Differences](./platform-differences.md) — which members exist on the JVM, which exist on Scala.js, and why the blocking family is JVM-only
- [Reader](./reader.md) — the `SyncReader` / `AsyncReader` union that decides which engine materializes behind the bounded-concurrency operators, and the adapters behind the native asynchronous byte readers
- [Async Reference](../async.md) — `Async.promise` and `Completer` bridge callback-based APIs into async values that can feed stream sources; `Async.Running` carries a synchronous cancellation handle that complements stream resource management
- [Async](../async.md#asyncselector) — `AsyncSelector`, the primitive the bounded-concurrency slot machinery is built from
- [Scope Reference](../resource-management/scope.md) — compile-time resource safety for stream acquisition and release; `fromAcquireRelease` follows the same ownership rules as Scope-managed resources
