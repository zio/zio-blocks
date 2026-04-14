---
id: stream
title: "Stream"
---

`Stream[+E, +A]` is a **lazy, pull-based, typed-error stream** of elements that may fail with an error of type `E`. Nothing executes until a terminal operation is called. When you run a stream synchronously, you get `Either[E, Z]` — typed errors surface as `Left(e)`, and untyped defects propagate as exceptions:

```scala
abstract class Stream[+E, +A] {
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z]
  def runCollect: Either[E, Chunk[A]]
}
```

`Stream` is purely functional, referentially transparent, and resource-safe:
- **Lazy**: descriptions of pipelines, not eager computations
- **Synchronous**: all terminal operations return `Either[E, Z]` directly (no async effects)
- **Pull-based**: execution is driven from the sink backward through the pipeline
- **Typed errors**: distinguish recoverable errors (`E`) from untyped defects (`Throwable`)
- **Resource-safe**: RAII semantics ensure resources are released in all cases

## Overview

`Stream[E, A]` addresses three core limitations of traditional eager sequence libraries:

### Motivation

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

```scala mdoc:compile-only
import java.io.*

// ❌ With traditional Scala (manual resource management)
// Count non-whitespace characters
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

```scala
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

```scala
import zio.blocks.streams.*

val singleElement = Stream.succeed(42)
val result = singleElement.runCollect
```

#### `Stream.fail[E]`

Creates a stream that fails immediately with a typed error:

```scala
object Stream {
  def fail[E](error: E): Stream[E, Nothing]
}
```

Use `fail` when you need to short-circuit a stream with a known error:

```scala
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

```scala
import zio.blocks.streams.*

val dieStream = Stream.die(new Exception("System failure"))
```

### From Collections

Streams can be created from existing collections and iterables, making it easy to convert `List`, `Array`, `Chunk`, or custom iterables into lazy streams:

#### `Stream.apply[A]`

Wraps a variable number of arguments into a stream:

```scala
object Stream {
  def apply[A](as: A*): Stream[Nothing, A]
}
```

This is the most natural way to lift a list of values:

```scala
import zio.blocks.streams.*

val numbers = Stream(1, 2, 3, 4, 5)
val result = numbers.runCollect
```

#### `Stream.fromChunk[A]`

Converts a `Chunk` into a stream. Chunks are immutable, indexed sequences optimized for high-performance operations:

```scala
object Stream {
  def fromChunk[A](chunk: Chunk[A]): Stream[Nothing, A]
}
```

Use this when you already have a `Chunk`:

```scala
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
  def fromIterable[A](it: Iterable[A]): Stream[Nothing, A]
}
```

This is useful when integrating with legacy Scala collections:

```scala
import zio.blocks.streams.*

val list = List("a", "b", "c")
val stream = Stream.fromIterable(list)
val result = stream.runCollect
```

#### `Stream.fromIterator[A]`

Converts an `Iterator[A]` into a stream. The iterator is consumed lazily:

```scala
object Stream {
  def fromIterator[A](it: => Iterator[A]): Stream[Nothing, A]
}
```

Create a stream from an iterator and collect all elements:

```scala mdoc:compile-only
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

```scala
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

```scala mdoc:compile-only
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
  def repeat[A](a: A): Stream[Nothing, A]
}
```

Infinite streams are safe because streams are lazy; nothing runs until you call a terminal operation with a stopping condition (like `take`):

```scala
import zio.blocks.streams.*

val infinite = Stream.repeat(42)
val first5 = infinite.take(5)
val result = first5.runCollect
```

#### `Stream.unfold[S, A]`

A stateful generator that emits elements based on a fold-like transition function:

```scala
object Stream {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Stream[Nothing, A]
}
```

Each iteration, `f` receives the current state and returns either `None` (stop) or `Some((element, nextState))`. This is useful for generating Fibonacci numbers or other sequences defined by a recurrence relation:

```scala
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

```scala mdoc:compile-only
import zio.blocks.streams.*

val sideEffect = Stream.eval(println("Executing side effect"))
val result = sideEffect.runDrain
```

#### `Stream.attempt[A]`

Wraps a potentially throwing computation, converting any `Throwable` into a typed error:

```scala
object Stream {
  def attempt[A](f: => A): Stream[Throwable, A]
}
```

Use `attempt` when you have legacy code that throws exceptions:

```scala
import zio.blocks.streams.*

def unsafeJsonParse(s: String): Int = s.toInt

val parsed = Stream.attempt(unsafeJsonParse("42"))
val result = parsed.runCollect
```

#### `Stream.defer[A]`

Defers the execution of a side effect until the stream is run:

```scala
object Stream {
  def defer(f: => Unit): Stream[Nothing, Nothing]
}
```

Defer side effects until the stream executes:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Int]
}
```

The stream automatically closes the input stream when done:

```scala
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

```scala mdoc:compile-only
import zio.blocks.streams.*
import java.io.StringReader

val reader = new StringReader("hello world")
val stream = Stream.fromJavaReader(reader)
val result = stream.runCollect
```

### Resource Management

Streams can be built from resources that need to be acquired and released, ensuring cleanup happens automatically:

#### `Stream.fromAcquireRelease[R, E, A]`

Acquires a resource, uses it in a stream, and releases it afterward:

```scala
object Stream {
  def fromAcquireRelease[R, E, A](
    acquire: => R,
    release: R => Unit = (r: R) => 
      r match { 
        case ac: AutoCloseable => ac.close()
        case _ => ()
      }
  )(use: R => Stream[E, A]): Stream[E, A]
}
```

This is the fundamental pattern for safe resource handling. The resource is acquired once when the stream is compiled, and released in the `finally` block of the terminal operation:

```scala
import zio.blocks.streams.*

case class DatabaseConnection(id: String) {
  def close(): Unit = println(s"Closing connection $id")
  def query(q: String): List[String] = List("result1", "result2")
}

val managed = Stream.fromAcquireRelease(
  acquire = DatabaseConnection("db-1"),
  release = _.close()
)(conn => Stream.fromIterable(conn.query("SELECT *")))

val result = managed.runCollect
```

#### `Stream.fromResource[R, E, A]`

Uses a ZIO Blocks `Resource[R]` (a more abstract resource type) within a stream:

```scala
object Stream {
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A]
}
```

When you need a resource to live for the entire duration of the stream, `fromResource` acquires it at the stream's start and releases it when the stream terminates:

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

## Transformations

Streams provide powerful operations for transforming elements, flattening nested structures, filtering, and managing state:

### Element-wise Transformations

These operations apply functions to stream elements one-by-one, applying the transformation lazily as elements are pulled:

#### `Stream#map[B]`

Applies a function to each element:

```scala
trait Stream[+E, +A] {
  def map[B](f: A => B): Stream[E, B]
}
```

`map` does not run immediately; it builds up a description of the transformation. Only when you call a terminal operation does the mapping happen:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val doubled = nums.map(_ * 2)
val result = doubled.runCollect
```

**Key point:** `map` is covariant in the output type because it preserves the error type and only transforms elements. The implicit `JvmType.Infer[A]` and `JvmType.Infer[B]` enable compile-time dispatch to unboxed fast paths for primitive types (Int, Long, Double, etc.).

#### `Stream#mapError[E2]`

Transforms typed errors without affecting elements:

```scala
trait Stream[+E, +A] {
  inline def mapError[E2](f: E => E2): Stream[E2, A]
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
trait Stream[+E, +A] {
  def filter(pred: A => Boolean): Stream[E, A]
}
```

Short-circuits: as soon as the sink says "stop," filtering stops:

```scala
import zio.blocks.streams.Stream

val nums = Stream(1, 2, 3, 4, 5)
val evens = nums.filter(_ % 2 == 0)
val result = evens.runCollect
```

#### `Stream#collect[B]`

Applies a partial function, emitting only defined results:

```scala
trait Stream[+E, +A] {
  def collect[B](pf: PartialFunction[A, B]): Stream[E, B]
}
```

This combines filtering and mapping in one step:

```scala
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
trait Stream[+E, +A] {
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B)): Stream[E, B]
}
```

`mapAccum` threads a state value through the transformation. At each step, you receive the current state and the element, return a new state and output element:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val indexed = nums.mapAccum(0)((idx, x) => (idx + 1, (idx, x)))
val result = indexed.runCollect
```

#### `Stream#scan[S]`

Like `mapAccum`, but also emits the state at each step (not the mapped value):

```scala
trait Stream[+E, +A] {
  def scan[S](init: S)(f: (S, A) => S): Stream[E, S]
}
```

This is useful for computing running totals, moving averages, or other cumulative statistics:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4)
val cumsum = nums.scan(0)(_ + _)
val result = cumsum.runCollect
```

### Flat-Mapping (Nested Streams)

`flatMap[E2, B]` — Maps each element to a stream and flattens the results.:

```scala
trait Stream[+E, +A] {
  def flatMap[E2, B](f: A => Stream[E2, B]): Stream[E | E2, B]
}
```

`flatMap` is sequential: streams are processed one at a time, in order. This is essential for resource safety: if each inner stream acquires a resource, `flatMap` ensures they are released in proper FIFO order:

```scala
import zio.blocks.streams.*

val ids = Stream(1, 2, 3)
val expanded = ids.flatMap(id => Stream(s"${id}-a", s"${id}-b"))
val result = expanded.runCollect
```

## Windowing

Streams can be grouped, sliced, and scanned to process data in temporal windows:

### Chunking

These operations group elements into chunks and slide windows over the stream for batch processing:

#### `Stream#grouped[A]`

Collects elements into fixed-size chunks:

```scala
trait Stream[+E, +A] {
  def grouped(n: Int): Stream[E, Chunk[A]]
}
```

The last chunk may contain fewer than `n` elements:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val groups = nums.grouped(2)
val result = groups.runCollect
```

#### `Stream#sliding[A]`

Creates a sliding window of size `n`, optionally stepping by `step` elements:

```scala
trait Stream[+E, +A] {
  def sliding(n: Int, step: Int = 1): Stream[E, Chunk[A]]
}
```

This is useful for computing local statistics or detecting patterns in sequences:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val windows = nums.sliding(3, step = 1)
val result = windows.runCollect
```

## Combining Streams

Streams can be sequentially concatenated, zipped together, or merged:

### Sequential Concatenation

`++[E2, A2]` or `concat[E2, A2]` — Emits all elements of the first stream, then all elements of the second stream.:

```scala
trait Stream[+E, +A] {
  def ++[E2, A2](that: Stream[E2, A2]): Stream[E | E2, A | A2] = concat(that)
}
```

The error type is the union of both streams' error types. Evaluation is sequential: the second stream only starts when the first completes:

```scala
import zio.blocks.streams.*

val first = Stream(1, 2)
val second = Stream(3, 4)
val combined = first ++ second
val result = combined.runCollect
```

### Zipping

These operations combine multiple streams element-wise, coordinating execution across them:

#### `Stream#&&[E2, B, C]`

Zips two streams together as tuples (an extension method, not an instance method):

```scala
extension [E, A](stream: Stream[E, A])
  def &&[E2, B, C](that: Stream[E2, B])(
    using Tuples[A, B] { Out = C }
  ): Stream[E | E2, C]
```

The result streams have the same length as the shorter input:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val chars = Stream('a', 'b')
val zipped = nums && chars
val result = zipped.runCollect
```

#### `Stream.flattenAll[E, A]`

Flattens a stream of streams into a single stream, processing them sequentially:

```scala
object Stream {
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]]): Stream[E, A]
}
```

This is equivalent to `flatMap(identity)`:

```scala
import zio.blocks.streams.*

val nested = Stream.fromIterable(List(
  Stream(1, 2),
  Stream(3, 4)
))
val flat = Stream.flattenAll(nested)
val result = flat.runCollect
```

## Other Operations

Common utilities for deduplication, draining, and error recovery:

### Filtering Duplicates

These operations remove duplicate elements, useful for deduplicating streams before processing:

#### `Stream#distinct[A]`

Emits only unique elements (using a mutable `HashSet` internally):

```scala
trait Stream[+E, +A] {
  def distinct(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

This consumes memory proportional to the number of unique elements:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 2, 3, 3, 3)
val unique = nums.distinct
val result = unique.runCollect
```

#### `Stream#distinctBy[K]`

Emits only elements whose key (computed by `f`) has not been seen before:

```scala
trait Stream[+E, +A] {
  def distinctBy[K](f: A => K)(implicit jtA: JvmType.Infer[A]): Stream[E, A]
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
trait Stream[+E, +A] {
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
trait Stream[+E, +A] {
  def take(n: Long): Stream[E, A]
}
```

This naturally short-circuits: the stream stops pulling from upstream:

```scala
import zio.blocks.streams.*

val nums = Stream.range(0, 1000)
val first10 = nums.take(10)
val result = first10.runCollect
```

#### `Stream#takeWhile`

Emits elements while a predicate is true, then stops:

```scala
trait Stream[+E, +A] {
  def takeWhile(pred: A => Boolean): Stream[E, A]
}
```

Taking elements while they are less than 6 stops early without processing the rest:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
val firstFive = nums.takeWhile(_ < 6)
val result = firstFive.runCollect
```

### Interspersing

`intersperse[A1 >: A]` — Inserts a separator value between every two elements.:

```scala
trait Stream[+E, +A] {
  def intersperse[A1 >: A](sep: A1): Stream[E, A1]
}
```

This is useful for rendering comma-separated lists or row delimiters:

```scala
import zio.blocks.streams.*

val items = Stream("a", "b", "c")
val separated = items.intersperse(", ")
val result = separated.runCollect
```

### Repeating

`repeated` — Repeats each element once, then emits the entire stream again, repeatedly.:

```scala
trait Stream[+E, +A] {
  def repeated: Stream[E, A]
}
```

This creates an infinite repetition of the stream:

```scala
import zio.blocks.streams.*

val original = Stream(1, 2)
val repeated = original.repeated.take(6)
val result = repeated.runCollect
```

### Side Effects

`tapEach` — Applies a function to each element for side effects, passing the element through unchanged.:

```scala
trait Stream[+E, +A] {
  def tapEach(f: A => Unit)(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

Use `tapEach` for logging or metrics:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val logged = nums.tapEach(x => println(s"Element: $x"))
val result = logged.runCollect
```

## Error Handling

Streams distinguish between recoverable domain errors and fatal defects, with flexible recovery patterns:

### Recovering from Typed Errors

These operations handle typed errors gracefully by recovering with alternative streams:

#### `Stream#catchAll[E2, A1]`

Recovers from any typed error by switching to a recovery stream:

```scala
trait Stream[+E, +A] {
  def catchAll[E2, A1](f: E => Stream[E2, A1]): Stream[E2, A | A1]
}
```

The recovery function receives the error and can return a new stream:

```scala
import zio.blocks.streams.*

sealed trait Error
case object NotFound extends Error

val mayFail: Stream[Error, String] = Stream.fail(NotFound)
val recovered = mayFail.catchAll(_ => Stream.succeed("default"))
val result = recovered.runCollect
```

#### `Stream#orElse[E2, A1]`

If this stream fails, tries the fallback stream. The fallback is evaluated lazily, only on error:

```scala
trait Stream[+E, +A] {
  def orElse[E2, A1](that: => Stream[E2, A1]): Stream[E2, A | A1]
}
```

`||` is an alias for `orElse`:

```scala
import zio.blocks.streams.*

val primary = Stream.fail("error")
val fallback = Stream.succeed(42)
val result = (primary || fallback).runCollect
```

### Recovering from Defects

`catchDefect[E1, A1]` — Catches untyped defects (exceptions not wrapped as typed errors) using a partial function.:

```scala
trait Stream[+E, +A] {
  def catchDefect[E1, A1](
    f: PartialFunction[Throwable, Stream[E1, A1]]
  ): Stream[E | E1, A | A1]
}
```

Use `catchDefect` for exception handling:

```scala
import zio.blocks.streams.*

val risky = Stream.attempt("not a number".toInt)
val safe = risky.catchDefect {
  case e: NumberFormatException => Stream.succeed(-1)
}
val result = safe.runCollect
```

## Resource Management

Streams provide RAII-based resource guarantees with finalizers and cancellation safety:

### Ensuring Cleanup

`ensuring[A]` — Runs a finalizer when the stream closes, whether cleanly or with an error.:

```scala
trait Stream[+E, +A] {
  def ensuring(finalizer: => Unit): Stream[E, A]
}
```

The finalizer always runs, in a `finally` block:

```scala mdoc:compile-only
import zio.blocks.streams.*

val managed = Stream(1, 2, 3)
  .ensuring { println("Cleaned up") }

val result = managed.runCollect
```

## Running Streams

All terminal operations are synchronous and return `Either[E, Z]`. The error type is the union of the stream's error type and any sink-specific error type.

### Collecting Results

These operations accumulate or examine stream results, running the entire stream to completion:

#### `Stream#runCollect`

Collects all elements into a `Chunk[A]`:

```scala
trait Stream[+E, +A] {
  def runCollect: Either[E, Chunk[A]]
}
```

This is the most common terminal operation for extracting results:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val result = nums.runCollect
// result is Right(Chunk(1, 2, 3, 4, 5))
```

#### `Stream#run[E2, Z]`

Runs the stream with a custom sink, producing result `Z`:

```scala
trait Stream[+E, +A] {
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z]
}
```

Use `run` when you need a specialized sink operation:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val sum = nums.run(Sink.foldLeft(0)((acc, x) => acc + x))
// sum is Right(15)
```

### Discarding Results

These operations consume streams without collecting their elements, useful when you only care about side effects:

#### `Stream#runDrain`

Consumes all elements and discards them, returning `Unit`:

```scala
trait Stream[+E, +A] {
  def runDrain: Either[E, Unit]
}
```

Use `runDrain` when you only care about side effects:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val sideEffect = nums.tapEach(x => println(s"Processing $x"))
val result = sideEffect.runDrain
```

#### `Stream#runForeach`

Applies a function to each element for side effects:

```scala
trait Stream[+E, +A] {
  def runForeach(f: A => Unit): Either[E, Unit]
}
```

Alias `foreach` also exists:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val result = nums.foreach(x => println(s"Got: $x"))
```

### Aggregations

#### `Stream#runFold[Z]`

Folds all elements using an accumulator, returning the final result:

```scala
trait Stream[+E, +A] {
  def runFold[Z](z: Z)(f: (Z, A) => Z): Either[E, Z]
}
```

This is the most general aggregation, equivalent to `reduce` or `fold` on eager sequences:

```scala
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

Returns the number of elements:

```scala
trait Stream[+E, +A] {
  def count: Either[E, Long]
}
```

Counting elements in a stream:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val total = nums.count
```

#### `Stream#head`

Returns the first element (or `None` if empty):

```scala
trait Stream[+E, +A] {
  def head: Either[E, Option[A]]
}
```

Getting the first element without collecting the entire stream:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val first = nums.head
```

#### `Stream#last`

Returns the last element (or `None` if empty):

```scala
trait Stream[+E, +A] {
  def last: Either[E, Option[A]]
}
```

Getting the final element of the stream:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val last = nums.last
```

#### `Stream#find[A]`

Returns the first element satisfying a predicate:

```scala
trait Stream[+E, +A] {
  def find(pred: A => Boolean): Either[E, Option[A]]
}
```

Finding the first element matching a condition:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val firstEven = nums.find(_ % 2 == 0)
```

#### `Stream#exists[A]`

Returns `true` if any element satisfies a predicate, short-circuiting:

```scala
trait Stream[+E, +A] {
  def exists(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if any element is greater than 35:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val hasLargeValue = nums.exists(_ > 35)
```

#### `Stream#forall[A]`

Returns `true` if all elements satisfy a predicate, short-circuiting:

```scala
trait Stream[+E, +A] {
  def forall(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if all elements are positive:

```scala mdoc:compile-only
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
val allPositive = nums.forall(_ > 0)
```

## Integration with Pipeline and Sink

Streams compose with pipelines and sinks to form complete data processing flows:

### Using Pipelines

`via[B]` — Applies a `Pipeline[A, B]` transformation to the stream.:

```scala
trait Stream[+E, +A] {
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B]
}
```

Pipelines are composable transformations that can be reused across streams and sinks. Common pipelines include `Pipeline.map`, `Pipeline.filter`, `Pipeline.take`, and `Pipeline.drop`:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val pipe = Pipeline.filter((x: Int) => x > 2).andThen(Pipeline.map(_ * 10))
val result = nums.via(pipe).runCollect
```

Pipelines are useful when you want to build reusable transformation logic:

```scala
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
- `Sink.drain: Sink[Nothing, A, Unit]` — discards all elements
- `Sink.count: Sink[Nothing, Any, Long]` — counts elements
- `Sink.foldLeft: Sink[Nothing, A, Z]` — folds elements with an accumulator
- `Sink.head: Sink[Nothing, A, Option[A]]` — takes the first element
- `Sink.foreach: Sink[Nothing, A, Unit]` — applies a function to each element

When you call `stream.run(sink)`, the stream is compiled to a `Reader` and the sink drains it, consuming all elements and producing the result.

## Low-Level Pull with Reader

`Reader[+Elem]` is the low-level, pull-based source that backs every stream at execution time. Most users never interact with `Reader` directly — it is the compilation target when a stream runs. However, you can open a stream for manual element-by-element pulling using `start` with a `Scope`.

### Manual Pull via `start`

`start` — Opens a stream for manual pulling within a `Scope`. The reader is closed automatically when the scope exits.:

```scala
trait Stream[+E, +A] {
  def start(using scope: Scope): scope.$[Reader[A]]
}
```

```scala
import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  // Open a stream for manual pulling
  val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

  $(reader) { r =>
    // Iterate through reader values using the protocol
    var current = r.read(-1)
    while (current != -1) {
      println(current)   // prints 1, 2, 3, 4, 5
      current = r.read(-1)
    }
  }
  // reader is closed automatically when scope exits
}
```

### The Reader Protocol

The pull protocol uses a **sentinel value** to signal end-of-stream:

- `read(sentinel)` — returns the next element, or `sentinel` when exhausted
- `close()` — signals the consumer is done
- `isClosed` — checks whether the reader has been closed

For primitive types, specialized methods avoid boxing:

- `readInt(sentinel: Long): Long`
- `readLong(sentinel: Long): Long`
- `readFloat(sentinel: Double): Double`
- `readDouble(sentinel: Double): Double`

:::note
Avoid holding references to a `Reader` obtained via `start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Implementation Notes

ZIO Blocks Streams achieves zero-boxing via compile-time type detection and dual compilation strategies:

### JVM Primitive Specialization

By default, Scala's type system boxes primitive values (Int, Long, Double, etc.) into objects, which wastes memory and is slower. ZIO Blocks' `Stream` uses `JvmType.Infer[A]` (a compile-time implicit) to detect primitive types at compile time and dispatch to unboxed, specialized implementations.

For example, `map`, `filter`, and `scan` all have specialized branches for `JvmType.Int` that use `readInt(Long.MinValue)` instead of boxing:

```scala
if (jvmType eq JvmType.Int) {
  val i = source.readInt(Long.MinValue)(using unsafeEvidence)
  // ... unboxed, fast path
} else {
  val o = reader.read(EndOfStream)  // generic boxed path
  // ...
}
```

This optimization is transparent: you write normal, high-level code, and the compiler and runtime automatically use the fast path for primitives.

### Dual Compilation: Recursive vs Interpreter

Each stream node compiles in two ways:

1. **Recursive (`compile`)**: Builds a tree of `Reader` objects, where each operation wraps the previous one. This is fast for shallow pipelines (< 100 operations).

2. **Flat-Array Interpreter (`compileInterpreter`)**: For deep pipelines (> 100 operations), the recursive approach hits Scala's default stack-depth limit (~100) and risks `StackOverflowError`. Instead, the interpreter compiles the entire pipeline into a flat array of operations, executed iteratively.

The switch happens at `DepthCutoff = 100`. You should never see this in normal use, but it ensures that pipelines of any depth are safe.

### Typed Error vs Untyped Defect

ZIO Blocks distinguishes two error channels:

- **Typed errors (`E`)**: Recoverable business logic errors. Returned as `Left(e)` from terminal operations.
- **Untyped defects (`Throwable`)**: Unexpected exceptions (bugs, system failures). Propagate as thrown exceptions.

Internally, typed errors are wrapped in `StreamError` (a non-fatal exception) and caught by the terminal operation to surface as `Left(e)`. Untyped `Throwable`s are not caught and propagate upward.

This separation allows you to:
- Use `catchAll` and `orElse` for business logic errors
- Use `catchDefect` or try-catch for unexpected exceptions
- Avoid accidentally silencing real bugs by catching all errors

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt.** Here are the available examples:

---

### Basic Usage

This example demonstrates constructing streams from collections, transforming elements with `map` and `filter`, and collecting results:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamBasicUsageExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/stream/StreamBasicUsageExample.scala))

To run this example:

```bash
sbt "streams-examples/runMain stream.StreamBasicUsageExample"
```

### Flat-Mapping Nested Streams

This example shows how `flatMap` sequences multiple streams and flattens the results:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamFlatMapExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/stream/StreamFlatMapExample.scala))

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

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/stream/StreamErrorHandlingExample.scala))

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

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/stream/StreamResourceExample.scala))

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

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/stream/StreamWindowingExample.scala))

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamWindowingExample"
```
