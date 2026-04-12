---
id: chunk
title: "Chunk"
---

`Chunk[A]` is an **immutable, indexed sequence** of elements of type `A`. Unlike `Array`, `Chunk` provides a purely functional interface with optimized performance for high-level operations. It is lazy on expensive operations like repeated concatenation (which use balanced tree structures) while remaining fast on access.

`Chunk[A]`:
- Is purely functional and immutable
- Provides O(1) typical random access (O(log n) worst-case for tree-structured chunks)
- Optimizes concatenation using balanced tree structures (Conc-Trees)
- Automatically specializes primitive types for efficiency without boxing
- Lazily materializes only when necessary to maintain performance

Here is the type signature:

```scala
sealed abstract class Chunk[+A]
  extends IndexedSeq[A]
  with IndexedSeqOps[A, Chunk, Chunk[A]]
  with StrictOptimizedSeqOps[A, Chunk, Chunk[A]]
  with IterableFactoryDefaults[A, Chunk]
  with Serializable
```

## Overview

`Chunk` represents a chunk of values. The implementation is backed by arrays for small chunks but transitions to lazy concatenation trees (`Chunk.Concat`) when building large chunks through repeated concatenation. This design eliminates the O(n²) behavior of naive list concatenation while remaining efficient for both element access and transformation.

`Chunk` has four main internal representations:

```
               ┌─────────────────────────────┐ 
               │           Chunk[A]          │
               └─────────────────────────────┘
                              △
                              │
        ┌──────────────┬──────┴────────┬──────────────┐
        │              │               │              │
   ┌────▼───┐   ┌──────▼──────┐   ┌────▼──────┐   ┌───▼────┐
   │ Empty  │   │ Singleton   │   │Array-     │   │ Concat │
   │        │   │(one element)│   │Backed     │   │(tree)  │
   └────────┘   └─────────────┘   └───────────┘   └────────┘
```

Chunks automatically choose the most efficient representation:
- **Empty**: singleton instance for zero elements
- **Singleton**: single element, no array allocation
- **Array-backed**: standard array for small sequences
- **Concat tree**: balanced binary tree for composite chunks, enabling O(log n) concatenation depth

The implementation is based on [Conc-Trees for Functional and Parallel Programming](http://aleksandar-prokopec.com/resources/docs/lcpc-conc-trees.pdf) by Aleksandar Prokopec and Martin Odersky.

## The Problem

When you work with sequences of data in functional programming, you often need to do two things that seem simple on the surface but are surprisingly tricky to do efficiently at the same time: you need to access elements quickly and merge sequences together without wasting time and memory.

Consider a practical scenario. You're building a data processing pipeline where you collect results from multiple parallel operations. Each operation produces a sequence of values, and you need to combine all of them into one final sequence. With a traditional array, combining sequences requires allocating a new, larger array and copying every single element from the source arrays into it. That's O(n) work just for the merging step. If you're doing this repeatedly—merging results from 10 operations, then 20, then 100—the copying overhead adds up quickly and becomes the bottleneck in your program.

On the other hand, if you use a linked list for efficiency with concatenation, you face a different problem: accessing the millionth element requires traversing through all the previous elements one by one. That's O(n) time for a single lookup. This works fine if you rarely need random access, but in real applications where you're searching, filtering, and transforming sequences, random access happens constantly. It's a painful tradeoff.

There's another subtle but important issue: memory overhead. A linked list that holds a million integers wastes significant memory because each node must store a pointer to the next node in addition to the actual value. An array is compact and efficient, but when you concatenate arrays, you're wasting both time (on copying) and memory (on allocating temporary intermediate arrays).

The real problem isn't just performance in isolation—it's that conventional sequence types force you to choose between mutually incompatible goals. You can optimize for random access (arrays) or for concatenation (lists), but not both efficiently. In a functional programming world where immutability is a core principle, this creates a tension: we want pure, immutable sequences that behave well in parallel processing scenarios where splitting and merging are fundamental operations. Yet the standard approaches either copy too much data or traverse too slowly.

This tension becomes especially acute when you're building systems that process large streams of data, perform data-parallel operations across multiple cores, or need to concatenate sequences as part of their normal operation. Every time you reach for an array and concatenate them, you're paying a hidden tax in copying. Every time you reach for a list, you're paying a hidden tax in traversal. Neither option feels quite right for a modern, functional programming experience.

## The Solution

Chunk solves this problem by combining the strengths of both arrays and balanced trees into a single, cohesive data structure. The key insight is to use different internal representations for different purposes: arrays for small, simple sequences (where the overhead of tree structures would hurt more than help), and balanced tree structures (specifically, Conc-Trees) for composite sequences built through concatenation and transformation.

When you create a small Chunk directly—say, `Chunk(1, 2, 3, 4, 5)`—it's backed by a simple array internally. You get all the benefits: O(1) random access, compact memory, cache-friendly performance. There's no overhead from tree structures or pointers. You're just using an array, exactly as you would expect.

But here's where it gets clever. When you concatenate two Chunks, instead of eagerly copying all the data into a new array, Chunk creates a lightweight tree node that simply links the two chunks together. This is nearly free—just a few pointer assignments. If you concatenate again, you're building a small balanced tree. The beauty is that this tree structure guarantees that no matter how many times you concatenate, random access still works in O(log n) time. The tree stays balanced through careful structural invariants, so the depth never grows too much. For practical sequence sizes you'd work with, O(log n) access is nearly as fast as O(1), with much better memory usage.

Chunk also includes specialized support for incremental building through the ChunkBuilder API. When you're constructing a sequence by repeatedly appending elements, ChunkBuilder uses a buffering strategy: it accumulates elements in a small array, and only when the array fills up does it append it to the growing Chunk tree. This means appending is effectively O(1) amortized time—comparable to dynamically sized arrays, but without the re-copying overhead when the array grows.

There's another important optimization: Chunk is designed to handle primitive types like `Int`, `Long`, `Double`, and `Byte` without boxing them. This is a significant performance win in practice. The specialized constructors and accessors ensure that a `Chunk[Int]` really does store integers directly in memory, not wrapped in Java Integer objects.

For functional programming specifically, Chunk embraces immutability completely. When you transform a Chunk, you get a new Chunk. The original remains unchanged. This makes Chunk naturally safe to share across parallel operations without locks or coordination. Different threads can safely read from the same Chunk simultaneously, and transformations produce new, independent Chunks.

The practical result is that Chunk gives you a sequence type that's genuinely efficient across all the operations you actually need: random access is fast, concatenation is fast, transformation is efficient, and memory usage stays reasonable. There's no invisible copying happening in the background, and no painful linear-time traversals. You get array-like performance where it matters most, without sacrificing the ability to build and merge sequences efficiently. For functional programmers and anyone working with immutable data structures, Chunk eliminates the artificial tradeoff between performance and purity.

## Installation

Chunk is available in the core `zio-blocks` library:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "@VERSION@"
```

For Scala.js support:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-chunk" % "@VERSION@"
```

Supports Scala 2.13.x and 3.x.

## Factory Methods

Chunk provides comprehensive factory methods for creating instances from various sources. Each factory is optimized for its data source with different performance and safety characteristics. Choose based on your data source and use case.

### Direct Construction

The simplest ways to create chunks from individual elements or single values:

#### `Chunk.apply` — Varargs Constructor

Create a chunk from individual elements using varargs syntax:

```scala
object Chunk {
  def apply[A](as: A*): Chunk[A]
}
```

The simplest way to create chunks from individual elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)

val strings = Chunk("alice", "bob", "charlie")

val mixed = Chunk(1, "two", 3.0)
```

**Performance:** O(n) — creates array-backed chunk, optimal for small to medium sizes.

#### `Chunk.single` — Single-Element Constructor

Create an efficient single-element chunk:

```scala
object Chunk {
  def single[A](a: A): Chunk[A]
}
```

Convenient for wrapping individual values without array overhead:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val one = Chunk.single("hello")

val singleInt = Chunk.single(42)
```

**Performance:** O(1) — specialized for single-element chunks, no array allocation.

#### `Chunk.empty` — Empty Chunk

Create an empty chunk (singleton instance):

```scala
object Chunk {
  def empty[A]: Chunk[A]
}
```

Returns the shared empty chunk singleton:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val empty = Chunk.empty[Int]

empty.length

empty.isEmpty
```

**Performance:** O(1) — returns shared singleton, no allocation.

### Generation Methods

Create chunks by generating values using functions:

#### `Chunk.fill` — Repeat Element N Times

Create a chunk by repeating an element n times:

```scala
object Chunk {
  def fill[A](n: Int)(elem: => A): Chunk[A]
}
```

Useful for creating uniform chunks:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val repeated = Chunk.fill(5)("x")

val zeros = Chunk.fill(3)(0)

val ones = Chunk.fill(4)(1)
```

**Performance:** O(n) — creates array-backed chunk of size n.

#### `Chunk.iterate` — Repeatedly Apply Function

Create a chunk by repeatedly applying a function starting from an initial value:

```scala
object Chunk {
  def iterate[A](start: A, len: Int)(f: A => A): Chunk[A]
}
```

Generates sequences by function iteration:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val powers = Chunk.iterate(1, 5)(_ * 2)

val alphabet = Chunk.iterate('a', 3)(c => (c + 1).toChar)

val decreasing = Chunk.iterate(10, 4)(_ - 1)
```

**Performance:** O(n) — function applied n times sequentially.

#### `Chunk.unfold` — Generate from State

Generate a chunk by repeatedly applying a function that returns an optional value:

```scala
object Chunk {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Chunk[A]
}
```

Powerful for generating chunks from state transitions:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

// Count from 1 to 5
val counted = Chunk.unfold(1) { n =>
  if (n <= 5) Some((n, n + 1)) else None
}

// Generate fibonacci-like sequence
val fibs = Chunk.unfold((1, 1)) { case (a, b) =>
  if (a <= 50) Some((a, (b, a + b))) else None
}
```

**Performance:** O(n) where n is number of generated elements.

### Collection Conversion Methods

Create chunks from existing Scala collections:

#### `Chunk.fromIterable` — From Scala Collections

Convert any Scala iterable into a chunk:

```scala
object Chunk {
  def fromIterable[A](it: Iterable[A]): Chunk[A]
}
```

Supports all Scala collection types (List, Vector, Set, Seq, etc.). For Chunk and Vector, data may be shared; for others, data is copied:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val list = List(1, 2, 3)
val chunk = Chunk.fromIterable(list)

val vector = Vector("x", "y", "z")
val chunkFromVec = Chunk.fromIterable(vector)

val set = Set(10, 20, 30)
val chunkFromSet = Chunk.fromIterable(set)
```

**Performance:** O(n) for most types; O(1) for Vector (data sharing).

#### `Chunk.from` — Generic from Iterable (Alias)

Shorter alias for `fromIterable`:

```scala
object Chunk {
  def from[A](it: Iterable[A]): Chunk[A]
}
```

Provides a concise name for generic iterable conversion:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val list = List(1, 2, 3)
val chunk = Chunk.from(list)
```

**Performance:** Same as `fromIterable`.

#### `Chunk.fromIterator` — From Iterator

Consume an iterator and collect all elements into a chunk:

```scala
object Chunk {
  def fromIterator[A](iterator: Iterator[A]): Chunk[A]
}
```

Exhausts the iterator and builds a chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val iter = Iterator(5, 10, 15)
val chunk = Chunk.fromIterator(iter)

// Iterator is now exhausted
val isEmpty = iter.hasNext
```

**Performance:** O(n) where n is number of elements in iterator.

#### `Chunk.fromArray` — From Array (Zero-Copy)

Create a chunk backed by an array without copying:

```scala
object Chunk {
  def fromArray[A](array: Array[A]): Chunk[A]
}
```

**Critical Warning:** This is a zero-copy operation that **holds a direct reference to the provided array**. The array **must not be mutated** after creating the chunk. Mutations become visible through the chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val arr = Array(10, 20, 30)
val chunk = Chunk.fromArray(arr)

chunk
```

Mutating the source array breaks immutability:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val arr = Array(10, 20, 30)
val chunk = Chunk.fromArray(arr)
arr(0) = 99  // DANGER: mutation visible through chunk!
```

The mutation is visible:

```scala mdoc
chunk
```

**Safe Alternative:** Use `fromIterable` which copies data and guarantees immutability:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val arr = Array(10, 20, 30)
val chunk = Chunk.fromIterable(arr.toList)  // Safe copy

// Now mutations don't affect chunk
```

**Performance:** O(1) zero-copy; O(n) for safe copy alternative.

### Java Interoperability Methods

Create chunks from Java collections and buffers:

#### From `java.nio` Buffers

Create chunks directly from Java NIO buffers (`ByteBuffer`, `CharBuffer`, etc.):

```scala
object Chunk {
  def fromByteBuffer(buffer: ByteBuffer): Chunk[Byte]
  def fromCharBuffer(buffer: CharBuffer): Chunk[Char]
  def fromIntBuffer(buffer: IntBuffer): Chunk[Int]
  def fromLongBuffer(buffer: LongBuffer): Chunk[Long]
  def fromFloatBuffer(buffer: FloatBuffer): Chunk[Float]
  def fromDoubleBuffer(buffer: DoubleBuffer): Chunk[Double]
  def fromShortBuffer(buffer: ShortBuffer): Chunk[Short]
}
```

Working with NIO buffers is seamless:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import java.nio.ByteBuffer

val buffer = ByteBuffer.wrap(Array[Byte](1, 2, 3))
val chunk = Chunk.fromByteBuffer(buffer)
```

**Performance:** O(n) — copies data from buffer into array-backed chunk.

#### `Chunk.fromJavaIterable` — From Java Iterable

Create a chunk from a Java `Iterable`:

```scala
object Chunk {
  def fromJavaIterable[A](iterable: java.lang.Iterable[A]): Chunk[A]
}
```

Interoperate with Java APIs that produce iterables:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import java.util.Arrays

val javaList = Arrays.asList("a", "b", "c")
val chunk = Chunk.fromJavaIterable(javaList)
```

**Performance:** O(n) — iterates and copies elements.

#### `Chunk.fromJavaIterator` — From Java Iterator

Consume a Java `Iterator` and collect its elements into a chunk:

```scala
object Chunk {
  def fromJavaIterator[A](iterator: java.util.Iterator[A]): Chunk[A]
}
```

Building a chunk from a Java iterator:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import java.util.Arrays

val javaIter = Arrays.asList(1, 2, 3).iterator()
val chunk = Chunk.fromJavaIterator(javaIter)
```

**Performance:** O(n) — exhausts iterator and builds chunk.

### Builder Methods

`ChunkBuilder[A]` is a mutable builder that accumulates elements incrementally and returns a `Chunk[A]` when complete. Use it when building chunks from elements that arrive one at a time or from streaming sources.

#### `Chunk.newBuilder` — Get a Builder

Obtain a fresh `ChunkBuilder` for incremental construction:

```scala
object Chunk {
  def newBuilder[A]: ChunkBuilder[A]
}
```

A builder integrates with Scala's collection factory pattern:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkBuilder}

val builder = Chunk.newBuilder[Int]
builder.addOne(1)
builder.addOne(2)
val result = builder.result()
```

### Using ChunkBuilder for Incremental Construction

`ChunkBuilder[A]` is a mutable builder that accumulates elements and returns a `Chunk[A]`. Use it when building chunks from elements that arrive incrementally—over time, from streaming sources, or when the total size is unknown in advance.

When constructing a chunk from multiple sources, repeatedly calling `++` creates additional tree nodes and increases access depth (O(log n) per operation). Static construction methods like `Chunk.apply` or `Chunk.from` require all elements upfront. `ChunkBuilder` solves this by using an internal buffering strategy: elements accumulate in a small array, and only when full does the array append to the growing chunk tree. This yields O(1) amortized append cost—comparable to dynamically sized arrays, but without re-copying overhead. It is also the standard Scala mutable builder interface, so it integrates with `scala.collection` builders.

| Scenario                                                                                   | Right choice                                 |
|--------------------------------------------------------------------------------------------|----------------------------------------------|
| Elements available all at once; constructing a static chunk                                | `Chunk.apply(...)` or `Chunk.from(iterable)` |
| Elements arrive incrementally; unknown size in advance; building from a stream or iterator | `ChunkBuilder` with `addOne` / `addAll`      |

The signature of `ChunkBuilder` is:

```scala
object ChunkBuilder {
  def make[A](): ChunkBuilder[A]
  def make[A](capacityHint: Int): ChunkBuilder[A]
}
```

Here's a realistic example: aggregating results from a paginated API that returns chunks of records until a sentinel response:

```scala mdoc:compile-only
import zio.blocks.chunk.{Chunk, ChunkBuilder}

case class ApiResponse(records: List[String], hasMore: Boolean)

def fetchAllRecords(): Chunk[String] = {
  @annotation.tailrec
  def loop(response: ApiResponse, builder: ChunkBuilder[String]): Chunk[String] = {
    val builderWithRecords = builder.addAll(response.records.iterator)
    if (response.hasMore) {
      // Simulate fetching next page
      loop(ApiResponse(List("record3", "record4"), false), builderWithRecords)
    } else {
      builderWithRecords.result()
    }
  }
  loop(ApiResponse(List("record1", "record2"), true), ChunkBuilder.make[String](1000))
}
```

In this scenario, the API may return 100 pages before completion. Using `Chunk.apply` would require buffering all responses in memory first. Using naive `++` concatenation repeatedly would create deep tree structures with O(log n) access overhead. `ChunkBuilder` handles pagination efficiently by maintaining a single O(1) amortized append mechanism throughout.

## Core Operations

Chunk provides a rich set of operations for accessing, transforming, and combining elements. Operations are organized by category:

### Element Access

Chunk provides several operations to inspect and retrieve individual elements efficiently. Whether you need random access by index, efficient access to boundaries, or to check the size, these methods offer fast, predictable performance:

#### `Chunk#apply` — Random Access

Access an element by index in O(log n) time (O(1) for array-backed chunks):

```scala
trait Chunk[+A] {
  def apply(index: Int): A
}
```

Access elements by index in a chunk:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40, 50)
```

Accessing by index returns individual elements:

```scala mdoc
chunk(0)
chunk(2)
chunk(4)
```

#### `Chunk#head` and `Chunk#last` — First and Last Elements

Access the first or last element:

```scala
trait Chunk[+A] {
  def head: A
  def last: A
}
```

Accessing the first or last element is efficient:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk("a", "b", "c", "d")
```

Getting the first and last elements is immediate:

```scala mdoc
chunk.head
chunk.last
```

#### `Chunk#length` and `Chunk#size` — Chunk Size

Get the number of elements (O(1) complexity):

```scala
trait Chunk[+A] {
  def length: Int
  def size: Int
}
```

Getting the chunk size is an O(1) operation:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)
```

Both `length` and `size` return the element count:

```scala mdoc
chunk.length
chunk.size
```

#### `Chunk#headOption` and `Chunk#lastOption` — Safe First and Last

Like `head` and `last`, but return `Option` to safely handle empty chunks:

```scala
trait Chunk[+A] {
  def headOption: Option[A]
  def lastOption: Option[A]
}
```

Accessing the first or last element safely yields an `Option`:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk("a", "b", "c")
val empty = Chunk.empty[Int]
```

`headOption` and `lastOption` provide safe access:

```scala mdoc
chunk.headOption
chunk.lastOption
empty.headOption
empty.lastOption
```

#### `Chunk#indexWhere` — Find Index of Matching Element

Find the index of the first element that matches a predicate:

```scala
trait Chunk[+A] {
  def indexWhere(f: A => Boolean): Int
}
```

Searching for a matching element returns its index or -1 if not found:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(10, 20, 30, 40)
```

Finding the index of the first even number:

```scala mdoc
numbers.indexWhere(_ % 25 == 0)
numbers.indexWhere(_ % 7 == 0)
```

#### `Chunk#tail` and `Chunk#init` — Rest and Initial Segments

`tail` returns all elements except the first, `init` returns all elements except the last:

```scala
trait Chunk[+A] {
  def tail: Chunk[A]
  def init: Chunk[A]
}
```

Taking the rest of the chunk after the first element, or all but the last:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4)
```

`tail` drops the first element, `init` drops the last:

```scala mdoc
chunk.tail
chunk.init
```

### Transformations

Chunk supports functional transformations that let you shape your data in powerful ways. Map applies a function to every element, flatMap chains operations and flattens results, filter keeps only elements that matter, and collect extracts values from nested structures:

#### `Chunk#map` — Transform Elements

Apply a function to each element:

```scala
trait Chunk[+A] {
  def map[B](f: A => B): Chunk[B]
}
```

Mapping a function across elements creates a new chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4)
val doubled = numbers.map(_ * 2)

val strings = Chunk("hello", "world")
val lengths = strings.map(_.length)
```

#### `Chunk#flatMap` — Flat Transformation

Map each element to an iterable collection and flatten the result:

```scala
trait Chunk[+A] {
  def flatMap[B](f: A => IterableOnce[B]): Chunk[B]
}
```

Flat-mapping chains transformations and flattens the result. The function can return any `IterableOnce` (such as `Chunk`, `List`, `Vector`, `Array`, etc.):

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3)
val expanded = numbers.flatMap(n => Chunk(n, n * 10))
```

#### `Chunk#filter` — Keep Matching Elements

Keep only elements that match a predicate:

```scala
trait Chunk[+A] {
  def filter(f: A => Boolean): Chunk[A]
}
```

Filtering by a predicate keeps only matching elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)
val evens = numbers.filter(_ % 2 == 0)

val longWords = Chunk("a", "hello", "b", "world")
val filtered = longWords.filter(_.length > 1)
```

#### `Chunk#collect` — Filter-Map Combined

Apply a partial function, keeping only successful matches:

```scala
trait Chunk[+A] {
  def collect[B](pf: PartialFunction[A, B]): Chunk[B]
}
```

Collecting combines filtering and mapping in one operation:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val values: Chunk[Any] = Chunk(1, "hello", 2, "world", 3)
val onlyInts = values.collect { case n: Int => n * 10 }
```

#### `Chunk#sorted` — Sort Elements

Sort elements using an ordering:

```scala
trait Chunk[+A] {
  def sorted[A1 >: A](implicit ord: Ordering[A1]): Chunk[A]
}
```

Sorting arranges elements in order:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val unsorted = Chunk(3, 1, 4, 1, 5, 9, 2, 6)
val sorted = unsorted.sorted

val strings = Chunk("zebra", "apple", "banana")
val sortedStrings = strings.sorted
```

#### `Chunk#sortBy` — Sort by Key

Sort elements according to a key function:

```scala
trait Chunk[+A] {
  def sortBy[B](f: A => B)(implicit ord: Ordering[B]): Chunk[A]
}
```

Sorting by a key produces a new ordered chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

case class Person(name: String, age: Int)
val people = Chunk(Person("Alice", 32), Person("Bob", 25), Person("Carol", 40))
```

Sorting by age orders the people from youngest to oldest:

```scala mdoc
people.sortBy(_.age)
```

#### `Chunk#collectFirst` — Collect First Matching Partial Function

Apply a partial function to the first matching element and return the result as an option:

```scala
trait Chunk[+A] {
  def collectFirst[B](pf: PartialFunction[A, B]): Option[B]
}
```

Collecting the first match returns an optional result:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val values = Chunk(1, "two", 3, "four")
```

Extract the first string if present:

```scala mdoc
values.collectFirst { case s: String => s }
```

#### `Chunk#filterNot` — Keep Non-Matching Elements

Keep only elements that do NOT match a predicate:

```scala
trait Chunk[+A] {
  def filterNot(f: A => Boolean): Chunk[A]
}
```

Filtering with a negated predicate removes elements that satisfy the condition:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)
```

Keep only odd numbers:

```scala mdoc
numbers.filterNot(_ % 2 == 0)
```

#### `Chunk#distinct` — Remove All Duplicates

Remove duplicate elements, keeping only one occurrence of each unique value:

```scala
trait Chunk[+A] {
  def distinct: Chunk[A]
}
```

Distinct eliminates duplicates based on equality:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val withDupes = Chunk(1, 2, 3, 2, 4, 1, 5)
```

Keeping only unique values:

```scala mdoc
withDupes.distinct
```

#### `Chunk#dedupe` — Remove Consecutive Duplicates

Remove only consecutive duplicate elements, preserving the first occurrence in each run:

```scala
trait Chunk[+A] {
  def dedupe: Chunk[A]
}
```

Deduplication differs from `distinct` by only collapsing adjacent duplicates:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val runs = Chunk(1, 1, 2, 2, 2, 3, 1, 1)
```

Removing consecutive repeats yields:

```scala mdoc
runs.dedupe
```

#### `Chunk#flatten` — Flatten Nested Chunks

Flatten a chunk of chunks into a single chunk:

```scala
trait Chunk[+A] {
  def flatten[B](implicit ev: A <:< Chunk[B]): Chunk[B]
}
```

Flattening concatenates nested chunks:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val nested = Chunk(Chunk(1, 2), Chunk(3), Chunk(4, 5))
```

A single flattened chunk results:

```scala mdoc
nested.flatten
```

#### `Chunk#mapAccum` — Stateful Map with Accumulator

Map over elements while accumulating state, returning the final state and the transformed chunk:

```scala
trait Chunk[+A] {
  def mapAccum[S, B](s0: S)(f: (S, A) => (S, B)): (S, Chunk[B])
}
```

Accumulating state during mapping threads an accumulator through:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val nums = Chunk(10, 20, 30)
```

Starting with sum zero, accumulate running totals:

```scala mdoc
val (finalSum, result) = nums.mapAccum(0) { (sum, n) => (sum + n, sum + n) }
finalSum
result
```

#### `Chunk#reverse` — Reverse Order

Reverse the order of elements in the chunk:

```scala
trait Chunk[+A] {
  def reverse: Chunk[A]
}
```

Reversing creates a new chunk with elements in opposite order:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val original = Chunk(1, 2, 3, 4)
```

Reverse produces:

```scala mdoc
original.reverse
```

### Combining Chunks

When you need to bring multiple chunks together, Chunk provides efficient operations to merge them. Concatenation uses a balanced tree structure for fast repeated joins, you can append or prepend single elements, and zip operations pair elements from parallel sequences:

#### `Chunk#++(that)` — Concatenation

Combine two chunks. Uses balanced tree structure for efficiency:

```scala
trait Chunk[+A] {
  def ++[A1 >: A](that: Chunk[A1]): Chunk[A1]
}
```

Concatenating two chunks combines them efficiently:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk1 = Chunk(1, 2, 3)
val chunk2 = Chunk(4, 5, 6)
val combined = chunk1 ++ chunk2

// Efficient even with many concatenations
val many = (1 to 100).foldLeft(Chunk.empty[Int]) { (acc, i) =>
  acc ++ Chunk(i)
}
```

#### `Chunk#:+(a)` — Append Element

Append a single element to the end:

```scala
trait Chunk[+A] {
  def :+[A1 >: A](a: A1): Chunk[A1]
}
```

Appending a single element creates a new chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
val appended = chunk :+ 4
```

#### `Chunk#+:(a)` — Prepend Element

Prepend a single element to the beginning:

```scala
trait Chunk[+A] {
  def +:[A1 >: A](a: A1): Chunk[A1]
}
```

Prepending a single element adds it to the front:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(2, 3, 4)
val prepended = 1 +: chunk
```

#### `Chunk#zip` and `Chunk#zipWith` — Combine Parallel Chunks

Combine two chunks element-wise:

```scala
trait Chunk[+A] {
  def zip[B](that: Chunk[B]): Chunk[(A, B)]
  def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C]
}
```

Zipping combines elements from two chunks:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3)
val letters = Chunk("a", "b", "c")

val zipped = numbers.zip(letters)

val combined = numbers.zipWith(letters)((n, l) => s"$l$n")
```

#### `Chunk#appended` — Append Element (Method Form)

Append a single element to the end (named method equivalent of `:+`):

```scala
trait Chunk[+A] {
  def appended[A1 >: A](a: A1): Chunk[A1]
}
```

Appending creates a new chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
val appended = chunk.appended(4)
```

#### `Chunk#prepended` — Prepend Element (Method Form)

Prepend a single element to the beginning (named method equivalent of `+:`):

```scala
trait Chunk[+A] {
  def prepended[A1 >: A](a: A1): Chunk[A1]
}
```

Prepending adds an element to the front:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(2, 3, 4)
val prepended = chunk.prepended(1)
```

#### `Chunk#zipAll` — Zip with Defaults for Unequal Lengths

Combine two chunks element-wise, using default values when one chunk is shorter:

```scala
trait Chunk[+A] {
  def zipAll[B](that: Chunk[B]): Chunk[(Option[A], Option[B])]
}
```

Zipping with defaults ensures both sides always have a value:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val left = Chunk(1, 2, 3)
val right = Chunk("a", "b")
```

The shorter chunk is padded with `None` values:

```scala mdoc
left.zipAll(right)
```

#### `Chunk#zipAllWith` — Zip with Custom Combiner

Zip two chunks element-wise with a custom combiner function that handles missing elements:

```scala
trait Chunk[+A] {
  def zipAllWith[B, C](that: Chunk[B])(left: A => C, right: B => C)(both: (A, B) => C): Chunk[C]
}
```

Providing separate handlers for left-only, right-only, and both:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val left = Chunk(1, 2, 3)
val right = Chunk("a", "b")
```

Combine with default for missing elements:

```scala mdoc
left.zipAllWith(right)(
  a => s"missing:${a}",      // left only
  b => s"only:${b}"          // right only
)(
  (a, b) => s"$a:$b"         // both present
)
```

#### `Chunk#zipWithIndex` — Zip with Index Starting at Zero

Zip each element with its zero-based index:

```scala
trait Chunk[+A] {
  def zipWithIndex: Chunk[(A, Int)]
}
```

Attaching indices is useful for positional awareness:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val words = Chunk("alpha", "beta", "gamma")
```

Zipping with index:

```scala mdoc
words.zipWithIndex
```

#### `Chunk#updated` — Update Element at Index

Update the element at a given index, returning a new chunk:

```scala
trait Chunk[+A] {
  override def updated[A1 >: A](index: Int, elem: A1): Chunk[A1]
}
```

The `updated` method creates a new chunk with the element at the specified index replaced. The original chunk remains unchanged:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)

val updated = chunk.updated(2, 99)
```

### Slicing and Partitioning

Often you need to work with portions of a chunk rather than the whole. These operations let you keep elements from the ends, skip unwanted portions, extract contiguous ranges by position, and intelligently partition chunks based on predicates or conditions:

#### `Chunk#take` and `Chunk#takeRight` — Take from Ends

Take the first n elements or last n elements:

```scala
trait Chunk[+A] {
  def take(n: Int): Chunk[A]
  def takeRight(n: Int): Chunk[A]
}
```

Taking elements from the beginning or end creates a new chunk:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)
```

Taking from the beginning or end produces new chunks:

```scala mdoc
chunk.take(3)
chunk.takeRight(2)
```

#### `Chunk#drop` and `Chunk#dropRight` — Remove from Ends

Remove the first n elements or last n elements:

```scala
trait Chunk[+A] {
  def drop(n: Int): Chunk[A]
  def dropRight(n: Int): Chunk[A]
}
```

Dropping elements removes them from the beginning or end:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)
```

Dropping from beginning or end removes those elements:

```scala mdoc
chunk.drop(2)
chunk.dropRight(2)
```

#### `Chunk#slice` — Extract a Range

Extract elements from a start index to an end index:

```scala
trait Chunk[+A] {
  def slice(from: Int, until: Int): Chunk[A]
}
```

Slicing extracts a contiguous range of elements:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40, 50, 60)
```

Slicing from a start index to end index extracts the range:

```scala mdoc
chunk.slice(1, 4)
chunk.slice(2, 5)
```

#### `Chunk#split` — Split into Equally-Sized Chunks

Split the chunk into N equally-sized chunks. When the chunk size is not evenly divisible, remainder elements are distributed into earlier chunks. If n exceeds the chunk's length, the result contains at most chunk.length single-element chunks.

Note: Passing `0` throws `ArithmeticException` due to division by zero. Negative values are not rejected by the current implementation and may produce unexpected results, so `n` should always be positive in normal use.

The method signature is:

```scala
trait Chunk[+A] {
  def split(n: Int): Chunk[Chunk[A]]
}
```

Splitting divides a chunk into N equal parts:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5, 6)
val splitInto2 = chunk.split(2)

val uneven = Chunk(1, 2, 3, 4, 5, 6, 7)
val splitInto3 = uneven.split(3)
```

#### `Chunk#span` and `Chunk#splitWhere` — Partition by Predicate

`span(f)` splits the chunk into a prefix where the predicate holds, and the remainder. It stops at the first element where the predicate becomes false.

`splitWhere(f)` is similar but has inverted logic: it splits at the first element where the predicate becomes true.

The method signatures are:

```scala
trait Chunk[+A] {
  def span(f: A => Boolean): (Chunk[A], Chunk[A])
  def splitWhere(f: A => Boolean): (Chunk[A], Chunk[A])
}
```

Partitioning by predicate creates two chunks:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)
val (prefix, rest) = numbers.span(_ < 4)
val (upTo, remaining) = Chunk(1, 2, 5, 3, 4).splitWhere(_ >= 5)
```

#### `Chunk#dropWhile` — Drop While Predicate Holds

Drop elements from the beginning while a predicate is true:

```scala
trait Chunk[+A] {
  def dropWhile(f: A => Boolean): Chunk[A]
}
```

Dropping while the predicate holds removes leading elements:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)
```

Drop initial even numbers:

```scala mdoc
numbers.dropWhile(_ % 2 == 0)
```

#### `Chunk#dropUntil` — Drop Until Predicate Fails

Drop elements until the predicate becomes false (i.e., drop while predicate holds, but stopped when condition fails):

```scala
trait Chunk[+A] {
  def dropUntil(f: A => Boolean): Chunk[A]
}
```

Dropping until the predicate fails keeps the first failing element and the rest:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)
```

Drop numbers less than 3:

```scala mdoc
numbers.dropUntil(_ >= 3)
```

#### `Chunk#takeWhile` — Take While Predicate Holds

Take elements from the beginning while a predicate is true, stopping at the first failure:

```scala
trait Chunk[+A] {
  def takeWhile(f: A => Boolean): Chunk[A]
}
```

Taking while the predicate holds collects a prefix:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)
```

Take the initial odd numbers:

```scala mdoc
numbers.takeWhile(_ % 2 != 0)
```

#### `Chunk#splitAt` — Split at Index

Split the chunk into two chunks at a given index: the first contains elements `[0, until)`, the second contains the remainder.

The method signature is:

```scala
trait Chunk[+A] {
  def splitAt(n: Int): (Chunk[A], Chunk[A])
}
```

Splitting creates two chunks from a single index:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40, 50)
```

Splitting at index 3:

```scala mdoc
chunk.splitAt(3)
```

### Querying and Folding

Chunk enables you to ask questions about your data and reduce it to meaningful results. Use fold operations to accumulate values, test predicates to verify conditions hold across elements, and search for specific values that match your criteria:

#### `Chunk#foldLeft` — Left Fold

Process elements left-to-right with an accumulator:

```scala
trait Chunk[+A] {
  def foldLeft[S](s0: S)(f: (S, A) => S): S
}
```

Left-folding accumulates values from left to right:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)

val sum = numbers.foldLeft(0)(_ + _)

val product = numbers.foldLeft(1)(_ * _)

val concat = Chunk("a", "b", "c").foldLeft("")(_ + _)
```

#### `Chunk#foldRight` — Right Fold

Process elements right-to-left with an accumulator:

```scala
trait Chunk[+A] {
  def foldRight[S](s0: S)(f: (A, S) => S): S
}
```

Right-folding accumulates values from right to left:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4)
val result = numbers.foldRight(List[Int]())(_ :: _)
```

#### `Chunk#foldWhile` — Fold with Early Exit

Fold left with early termination based on a predicate on the accumulator:

```scala
trait Chunk[+A] {
  def foldWhile[S](s0: S)(pred: S => Boolean)(f: (S, A) => S): S
}
```

`foldWhile` allows you to stop folding when a condition on the accumulator becomes false, avoiding unnecessary processing of remaining elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

// Fold while accumulator is less than 20, adding elements
val result = numbers.foldWhile(0)(acc => acc < 20)((acc, n) => acc + n)
```

#### `Chunk#exists` and `Chunk#forall` — Predicates

Check if any or all elements match a predicate:

```scala
trait Chunk[+A] {
  def exists(f: A => Boolean): Boolean
  def forall(f: A => Boolean): Boolean
}
```

Testing predicates answers questions about elements:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(2, 4, 6, 8)
val mixed = Chunk(1, 2, 3)
```

Testing existence and universal predicates returns Boolean:

```scala mdoc
numbers.exists(_ > 5)
numbers.forall(_ % 2 == 0)
mixed.forall(_ > 0)
mixed.forall(_ % 2 == 0)
```

#### `Chunk#find` — First Matching Element

Find the first element matching a predicate:

```scala
trait Chunk[+A] {
  def find(f: A => Boolean): Option[A]
}
```

Finding the first matching element returns an Option:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)
val words = Chunk("apple", "banana", "cherry")
```

Finding returns Some for matches and None for no match:

```scala mdoc
numbers.find(_ > 3)
numbers.find(_ > 10)
words.find(_.startsWith("b"))
```

#### `Chunk#corresponds` — Check Parallel Correspondence

Check whether corresponding elements of two chunks satisfy a predicate in lockstep:

```scala
trait Chunk[+A] {
  def corresponds[B](that: Chunk[B])(f: (A, B) => Boolean): Boolean
}
```

`corresponds` tests pairwise element compatibility:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val a = Chunk(1, 2, 3)
val b = Chunk(1, 2, 3)
```

Correspondence holds when all pairs match:

```scala mdoc
a.corresponds(b)(_ == _)
```

### Grouping

Chunk provides powerful grouping operations to organize elements by keys or into fixed-size partitions. Use `groupBy` to categorize elements into maps of chunks, `groupMap` to combine grouping and mapping in a single pass, and `grouped` to split the chunk into chunks of a specified size.

#### `Chunk#groupBy` — Group Elements by Key

Group elements by a key function, producing a map from keys to chunks:

```scala
trait Chunk[+A] {
  def groupBy[K](f: A => K): Map[K, Chunk[A]]
}
```

Grouping by a key aggregates related elements:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

case class Person(name: String, age: Int)
val people = Chunk(Person("Alice", 32), Person("Bob", 25), Person("Carol", 32))
```

Group people by age:

```scala mdoc
people.groupBy(_.age)
```

#### `Chunk#groupMap` — Group and Map in One Pass

Group elements by a key and simultaneously transform the values:

```scala
trait Chunk[+A] {
  def groupMap[K, V](key: A => K)(f: A => V): Map[K, Chunk[V]]
}
```

Grouping and mapping in a single operation can be more efficient than separate steps:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val words = Chunk("apple", "banana", "cherry", "avocado")
```

Group words by their first letter and also uppercase them:

```scala mdoc
words.groupMap(_.head)(_.toUpperCase)
```

#### `Chunk#grouped` — Partition into Fixed-Size Groups

Divide the chunk into groups of a fixed size, returning an iterator over subgroups:

```scala
trait Chunk[+A] {
  def grouped(size: Int): Iterator[Chunk[A]]
}
```

Fixed-size grouping is useful for batch processing:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6, 7, 8)
```

Groups of size 3 yield two full groups and a partial:

```scala mdoc
numbers.grouped(3).toList
```

### Equality and Iteration

Chunks support standard equality comparison and side-effect iteration:

#### `Chunk#equals` — Equality Comparison

Compare two chunks for equality:

```scala
trait Chunk[+A] {
  override def equals(that: Any): Boolean
}
```

Chunks are equal if they contain the same elements in the same order. The comparison works correctly regardless of the internal representation (array-backed vs. tree-structured):

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk1 = Chunk(1, 2, 3)
val chunk2 = Chunk(1, 2, 3)
val chunk3 = Chunk(1, 2, 4)

chunk1 == chunk2
chunk1 == chunk3
```

#### `Chunk#hashCode` — Hash Code Computation

Get the hash code of a chunk:

```scala
trait Chunk[+A] {
  override def hashCode: Int
}
```

Chunks compute their hash code using the MurmurHash3 algorithm applied to all elements, similar to Scala's collection hashing. Equal chunks always have equal hash codes, making chunks suitable for use as keys in hash-based collections:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk1 = Chunk(1, 2, 3)
val chunk2 = Chunk(1, 2, 3)

chunk1.hashCode == chunk2.hashCode

val chunkSet = Set(chunk1)
chunkSet.contains(chunk2)
```

Chunks can be reliably used in Maps and Sets because equal chunks have equal hash codes.

#### `Chunk#foreach` — Iteration with Side Effects

Perform a side effect for each element (used with `Unit`-returning functions):

```scala
trait Chunk[+A] {
  override def foreach[B](f: A => B): Unit
}
```

`foreach` iterates through all elements and applies a function, ignoring the return value. This is useful for side effects like logging or printing:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)

// Print each element
chunk.foreach(n => println(s"Element: $n"))
```

### Conversion

Sometimes you need to move data from Chunk into other Scala collections or represent it as text. These conversion operations make it easy to export your chunk into arrays, lists, sequences, or render it as a string for logging and display:

#### `Chunk#toArray` — To `Array`

Convert to an array:

```scala
trait Chunk[+A] {
  def toArray[A1 >: A: ClassTag]: Array[A1]
}
```

Converting to an array materializes all elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4)
val array: Array[Int] = chunk.toArray
```

#### `Chunk#toList` — To `List`

Convert to a `List`:

```scala
trait Chunk[+A] {
  def toList: List[A]
}
```

Converting to a list produces a sequential data structure:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk("a", "b", "c")
val list = chunk.toList
```

#### `Chunk#toSeq`, `Chunk#toIterable`, `Chunk#toIndexedSeq` — Standard Collections

Convert to various Scala collection types:

```scala
trait Chunk[+A] {
  def toSeq: Seq[A]
  def toIterable: Iterable[A]
  def toIndexedSeq: IndexedSeq[A]
}
```

Converting to standard Scala collections enables interop:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)

chunk.toSeq
chunk.toIndexedSeq
```

#### `Chunk#toString` — String Representation

Convert to a string:

```scala
trait Chunk[+A] {
  def toString: String
}
```

String representation shows all elements in a compact format:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
```

String conversion shows the chunk contents:

```scala mdoc
chunk.toString
```

#### `Chunk#toVector` — To `Vector`

Convert to a `Vector`:

```scala
trait Chunk[+A] {
  def toVector: Vector[A]
}
```

Conversion to `Vector` produces an efficient indexed sequence:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
```

```scala mdoc
chunk.toVector
```

### Specialized Accessors for Primitive Types

For primitive types, direct accessors avoid boxing:

```scala
trait Chunk[+A] {
  def byte(index: Int)(implicit ev: A <:< Byte): Byte
  def boolean(index: Int)(implicit ev: A <:< Boolean): Boolean
  def char(index: Int)(implicit ev: A <:< Char): Char
  def double(index: Int)(implicit ev: A <:< Double): Double
  def float(index: Int)(implicit ev: A <:< Float): Float
  def int(index: Int)(implicit ev: A <:< Int): Int
  def long(index: Int)(implicit ev: A <:< Long): Long
  def short(index: Int)(implicit ev: A <:< Short): Short
}
```

Accessing primitives without boxing demonstrates zero-overhead specialization:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val bytes: Chunk[Byte] = Chunk(1.toByte, 2.toByte, 3.toByte)
val ints = Chunk(10, 20, 30)
```

Accessing primitives by specialized methods avoids boxing:

```scala mdoc
bytes.byte(0)
ints.int(1)
```

### Iterator Access

Access chunks through iterators for sequential processing or collect the known size of elements:

#### `Chunk#iterator` — Get a Standard Iterator

Get a standard Scala `Iterator` to traverse the chunk sequentially:

```scala
trait Chunk[+A] {
  def iterator: Iterator[A]
}
```

The iterator is useful for sequential processing and integrates with Scala's collection ecosystem:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)
val iter = chunk.iterator

// Process elements sequentially
iter.take(3).toList
```

#### `Chunk#chunkIterator` — Get a Chunk-Specific Iterator

Get a `Chunk.ChunkIterator` for more efficient traversal within the Chunk ecosystem:

```scala
trait Chunk[+A] {
  def chunkIterator: Chunk.ChunkIterator[A]
}
```

`ChunkIterator` is optimized for Chunk operations and can be more efficient than the standard iterator in some cases:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40)
val chunkIter = chunk.chunkIterator
```

#### `Chunk#knownSize` — Get Iterator Size Hint

Get the known size of the chunk for iterator protocol compatibility:

```scala
trait Chunk[+A] {
  def knownSize: Int
}
```

The known size helps collection builders optimize allocations:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
val size = chunk.knownSize
```

### Array Operations

Perform bulk operations with arrays for interoperability with imperative code:

#### `Chunk#copyToArray` — Copy to Destination Array

Copy elements from the chunk to a destination array at a specified position:

```scala
trait Chunk[+A] {
  def copyToArray[B >: A](dest: Array[B], destPos: Int, length: Int): Int
}
```

This is useful for efficient bulk export of chunk contents to arrays, particularly in interop scenarios. The method returns the number of elements actually copied:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)
val dest = new Array[Int](10)

val numCopied = chunk.copyToArray(dest, 2, 3)
dest.take(5)
```

### Materialization and Optimization

When you build chunks through many concatenations, they internally form a tree structure for efficiency. However, if you plan to access elements many times, materialization converts that tree into a flat, optimized array-backed representation:

Force the chunk to an array-backed representation, eliminating lazy concatenation trees. Useful before performing many operations:

```scala
trait Chunk[+A] {
  def materialize[A1 >: A]: Chunk[A1]
}
```

Materializing a tree-built chunk converts it to an efficient array-backed form:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

// Build through many concatenations (creates Concat tree)
val built = (1 to 100).foldLeft(Chunk.empty[Int]) { (acc, i) =>
  acc ++ Chunk(i)
}

// Materialize to array-backed representation
val materialized = built.materialize
// Now faster for repeated access or further operations
```

## Advanced Usage

Advanced use cases include bit-level operations, working with specialized chunk types, and comparing Chunk with other data structures:

### Bit Operations

Chunk provides specialized operations to work at the bit level with numeric types. These are useful when you need to inspect the binary representation of bytes, integers, or longs while respecting endianness preferences:

#### `Chunk#asBitsByte` — Convert to Byte Bits

Convert a chunk of bytes into a chunk of individual bits. This method is only available on `Chunk[Byte]` through an implicit constraint:

```scala
trait Chunk[+A] {
  def asBitsByte(implicit ev: A <:< Byte): Chunk[Boolean]
}
```

Converting bytes to bits reveals the binary representation:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bytes = Chunk(1.toByte, 2.toByte)
val bits = bytes.asBitsByte
```

#### `Chunk#asBitsInt` — Convert to Int Bits

Convert a chunk of ints into a chunk of individual bits with a specified endianness. This method is only available on `Chunk[Int]` through an implicit constraint:

```scala
trait Chunk[+A] {
  def asBitsInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Int): Chunk[Boolean]
}
```

Converting integers to bits with endianness control provides bit-level access:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val ints = Chunk(1, 2, 3)
val bits = ints.asBitsInt(Chunk.BitChunk.Endianness.BigEndian)
```

#### `Chunk#asBitsLong` — Convert to Long Bits

Convert a chunk of longs into a chunk of individual bits with a specified endianness. This method is only available on `Chunk[Long]` through an implicit constraint:

```scala
trait Chunk[+A] {
  def asBitsLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Long): Chunk[Boolean]
}
```

Converting longs to bits supports both big-endian and little-endian representations:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val longs = Chunk(1L, 2L, 3L)
val bits = longs.asBitsLong(Chunk.BitChunk.Endianness.LittleEndian)
```

#### `Chunk#toBinaryString` — Convert Boolean Chunk to Binary String

Convert a chunk of booleans (representing bits) into a binary string representation:

```scala
trait Chunk[+A] {
  def toBinaryString(implicit ev: A <:< Boolean): String
}
```

This operation yields a string of '0' and '1' characters corresponding to the bit values:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val bits = Chunk(true, false, true, false)
```

The binary string shows the sequence of bits:

```scala mdoc
bits.toBinaryString
```

#### `Chunk#toPackedByte` — Pack Bits into Bytes

Pack a chunk of booleans (representing individual bits) into a packed byte representation:

```scala
trait Chunk[+A] {
  def toPackedByte(implicit ev: A <:< Boolean): Chunk[Byte]
}
```

This is useful when you need a space-efficient representation of individual bits by packing them eight per byte. The bits are packed from left to right, with the first bit becoming the most significant bit of the first byte:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits = Chunk(true, false, true, false, true, false, true, false)
val packed = bits.toPackedByte
```

#### `Chunk#toPackedInt` — Pack Bits into Integers

Pack a chunk of booleans into a packed integer representation with specified endianness:

```scala
trait Chunk[+A] {
  def toPackedInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Boolean): Chunk[Int]
}
```

Packing bits into integers is efficient for storing large bit arrays. The endianness parameter controls how bits are ordered within each integer:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits = Chunk(true, false, true, false, true, false, true, false)
val packed = bits.toPackedInt(Chunk.BitChunk.Endianness.BigEndian)
```

#### `Chunk#toPackedLong` — Pack Bits into Longs

Pack a chunk of booleans into a packed long representation with specified endianness:

```scala
trait Chunk[+A] {
  def toPackedLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Boolean): Chunk[Long]
}
```

Packing bits into longs is the most space-efficient approach for bit storage, packing 64 bits per long. Choose the endianness that matches your serialization format:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits = Chunk(true, false, true, false, true, false, true, false)
val packed = bits.toPackedLong(Chunk.BitChunk.Endianness.LittleEndian)
```

#### `Chunk#&` — Bitwise AND

Perform bitwise AND between two boolean chunks:

```scala
trait Chunk[+A] {
  def &(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte
}
```

Bitwise AND operates on boolean chunks, returning a `BitChunkByte` representing the packed result:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits1 = Chunk(true, true, false, false)
val bits2 = Chunk(true, false, true, false)
val result = bits1 & bits2
```

#### `Chunk#|` — Bitwise OR

Perform bitwise OR between two boolean chunks:

```scala
trait Chunk[+A] {
  def |(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte
}
```

Bitwise OR combines boolean chunks with an OR operation:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits1 = Chunk(true, false, false, false)
val bits2 = Chunk(false, true, false, false)
val result = bits1 | bits2
```

#### `Chunk#^` — Bitwise XOR

Perform bitwise XOR between two boolean chunks:

```scala
trait Chunk[+A] {
  def ^(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte
}
```

Bitwise XOR returns true when bits differ:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits1 = Chunk(true, false, true, false)
val bits2 = Chunk(true, true, false, false)
val result = bits1 ^ bits2
```

#### `Chunk#negate` — Bitwise NOT

Perform bitwise NOT (negation) on a boolean chunk:

```scala
trait Chunk[+A] {
  def negate(implicit ev: A <:< Boolean): Chunk.BitChunkByte
}
```

Bitwise NOT inverts all bits in the chunk:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bits = Chunk(true, false, true, false)
val inverted = bits.negate
```

### Text Operations

Chunk makes it convenient to work with text by converting byte or character chunks into readable strings. You can also encode chunks as Base64 for safe transmission or storage in text-based formats:

#### `Chunk#asString` — Convert to String

Convert a chunk of bytes or characters into a string. Supports `Chunk[Byte]`, `Chunk[Char]`, and `Chunk[String]`:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chars = Chunk('H', 'i')
val bytes = Chunk(72.toByte, 105.toByte)
```

Converting byte and character chunks to strings:

```scala mdoc
val str = chars.asString
val strFromBytes = bytes.asString
```

#### `Chunk#asString(charset)` — Convert to String with Charset

Convert a chunk of bytes into a string using a specified character encoding:

```scala
trait Chunk[+A] {
  def asString(charset: Charset)(implicit ev: A <:< Byte): String
}
```

When working with byte chunks from external sources, you may need to specify the character encoding. Use UTF-8 for most cases, but other encodings are available:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import java.nio.charset.StandardCharsets

val utf8Bytes = Chunk(72.toByte, 101.toByte, 108.toByte, 108.toByte, 111.toByte)
val stringUTF8 = utf8Bytes.asString(StandardCharsets.UTF_8)
```

#### `Chunk#asBase64String` — Encode as Base64

Convert a chunk of bytes or characters to a Base64-encoded string:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bytes = Chunk(1.toByte, 2.toByte, 3.toByte)
val base64 = bytes.asBase64String
```

### Advanced Transformations

Chunk supports powerful transformation patterns for complex use cases. Collect elements while a condition holds, partition results into success and failure paths using Either, and attach indices to track element positions:

#### `Chunk#collectWhile` — Collect with Early Exit

Apply a partial function while a condition holds, stopping at the first non-match:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val values: Chunk[Any] = Chunk(1, 2, "x", 3, 4)
val collected = values.collectWhile { case n: Int => n * 10 }
```

#### `Chunk#partitionMap` — Partition into Two Chunks by Either Result

Partition elements by applying a total function that returns `Either`:

```scala
trait Chunk[+A] {
  def partitionMap[B, C](f: A => Either[B, C]): (Chunk[B], Chunk[C])
}
```

Partitioning elements by an Either result separates success and failure cases:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)
val (evens, odds) = numbers.partitionMap { n =>
  if (n % 2 == 0) Left(n) else Right(n)
}
```

#### `Chunk#zipWithIndexFrom` — Zip with Index Starting at Custom Value

Zip elements with indices starting from a custom value:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk("a", "b", "c")
```

Zipping with a custom starting index pairs each element:

```scala mdoc
chunk.zipWithIndexFrom(10)
```

### Safe Operations

Chunks can be empty, and these operations help you handle that case gracefully. Rather than throwing exceptions or returning null, these methods provide type-safe fallbacks and alternatives when working with potentially empty chunks:

Return the chunk if non-empty, otherwise return an alternative:

```scala
trait Chunk[+A] {
  def nonEmptyOrElse[B](ifEmpty: => B)(fn: NonEmptyChunk[A] => B): B
}
```

Safely handling empty and non-empty cases provides type-safe alternatives:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(1, 2, 3)
val nonEmpty = NonEmptyChunk(1, 2, 3)
val result = nonEmpty.nonEmptyOrElse(Chunk.empty[Int])(identity)

val empty = Chunk.empty[Int]
val result2 = empty match {
  case c if c.isEmpty => Chunk(99)
  case c => c
}
```

## NonEmptyChunk

`NonEmptyChunk[A]` is a **type-safe wrapper** around `Chunk[A]` that **guarantees the chunk is non-empty**. It provides the same operations as `Chunk` but with methods like `head` and `last` returning `A` directly instead of `Option[A]` or throwing an exception. This eliminates the need for runtime checks on common operations and makes empty-case handling explicit at the type level.

`NonEmptyChunk[A]`:
- Is a purely functional, immutable sequence of at least one element
- Provides type-safe access to first and last elements
- Supports all Chunk operations while maintaining the non-empty guarantee
- Allows safe reduction operations without requiring a default value
- Integrates seamlessly with Chunk for interoperability

### Construction

Create a `NonEmptyChunk` directly using varargs syntax:

```scala
object NonEmptyChunk {
  def apply[A](a: A, as: A*): NonEmptyChunk[A]
}
```

Creating a non-empty chunk with direct constructor:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val nonEmpty = NonEmptyChunk(1, 2, 3)
```

Create a `NonEmptyChunk` from an existing `Chunk` using `fromChunk`, which returns `Option[NonEmptyChunk[A]]`:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(1, 2, 3)
val maybeNonEmpty: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(chunk)

val empty = Chunk.empty[Int]
val nothingHere: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(empty)
```

Create from a Scala cons list using `fromCons` (requires a non-empty cons list):

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val list = 1 :: 2 :: 3 :: Nil
val nonEmpty = list match {
  case cons: scala.collection.immutable.::[Int] => NonEmptyChunk.fromCons(cons)
  case _ => throw new IllegalArgumentException("list must be non-empty")
}
```

Create from an iterable with at least one guaranteed element using `fromIterable`:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val nonEmpty = NonEmptyChunk.fromIterable(5, List(1, 2, 3, 4))
```

### Safe Access to Endpoints

Access the first and last elements without risk of exception:

```scala
trait NonEmptyChunk[+A] {
  def head: A
  def last: A
}
```

Safely access endpoints of a guaranteed non-empty chunk:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val chunk = NonEmptyChunk(10, 20, 30, 40)

chunk.head
chunk.last
chunk.size
```

### Reduction and Aggregation

Reduce a non-empty chunk without requiring a starting value, since the chunk is guaranteed to have at least one element:

```scala
trait NonEmptyChunk[+A] {
  def reduce[B >: A](op: (B, B) => B): B
  def reduceMapLeft[B](map: A => B)(reduce: (B, A) => B): B
  def reduceMapRight[B](map: A => B)(reduce: (A, B) => B): B
}
```

Reduction operations always produce a value (no Optional needed):

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val numbers = NonEmptyChunk(1, 2, 3, 4, 5)

val sum = numbers.reduce(_ + _)

val product = numbers.reduceMapLeft[Int](identity)(_ * _)
```

### Transformations

Map and transform elements while preserving the non-empty guarantee:

```scala
trait NonEmptyChunk[+A] {
  def map[B](f: A => B): NonEmptyChunk[B]
  def flatMap[B](f: A => NonEmptyChunk[B]): NonEmptyChunk[B]
  def flatten[B](implicit ev: A <:< NonEmptyChunk[B]): NonEmptyChunk[B]
  def sorted[B >: A](implicit ord: Ordering[B]): NonEmptyChunk[B]
  def sortBy[B](f: A => B)(implicit ord: Ordering[B]): NonEmptyChunk[A]
  def distinct: NonEmptyChunk[A]
  def reverse: NonEmptyChunk[A]
}
```

Transformations maintain the non-empty property for map, flatMap, and other structure-preserving operations:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val numbers = NonEmptyChunk(3, 1, 4, 1, 5)

val doubled = numbers.map(_ * 2)

val sorted = numbers.sorted
```

### Grouping and Aggregation

Group elements while maintaining non-empty chunks in each group:

```scala
trait NonEmptyChunk[+A] {
  def groupBy[K](f: A => K): Map[K, NonEmptyChunk[A]]
  def groupMap[K, V](key: A => K)(f: A => V): Map[K, NonEmptyChunk[V]]
  def grouped(size: Int): Iterator[NonEmptyChunk[A]]
}
```

Grouping operations guarantee non-empty result chunks:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

case class Person(name: String, age: Int)
val people = NonEmptyChunk(Person("Alice", 30), Person("Bob", 25), Person("Carol", 30))

people.groupBy(_.age)
```

### Combining and Concatenating

Extend a non-empty chunk with additional elements or other chunks:

```scala
trait NonEmptyChunk[+A] {
  def appended[A1 >: A](a: A1): NonEmptyChunk[A1]
  def prepended[A1 >: A](a: A1): NonEmptyChunk[A1]
  def ++[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1]
  def :+[A1 >: A](a: A1): NonEmptyChunk[A1]  // alias for appended
  def +:[A1 >: A](a: A1): NonEmptyChunk[A1]  // alias for prepended
}
```

Concatenation operations preserve the non-empty guarantee:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val nonEmpty = NonEmptyChunk(1, 2, 3)
val chunk = Chunk(4, 5, 6)

val appended = nonEmpty :+ 4

val concatenated = nonEmpty ++ chunk

val prepended = 0 +: nonEmpty
```

### Conversion to Chunk

Convert a `NonEmptyChunk` back to a regular `Chunk`, losing the non-empty guarantee but gaining compatibility with Chunk APIs:

```scala
trait NonEmptyChunk[+A] {
  def toChunk: Chunk[A]
}
```

Converting back to a `Chunk` for use with generic Chunk operations:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val nonEmpty = NonEmptyChunk(1, 2, 3)
val chunk: Chunk[Int] = nonEmpty.toChunk

// Now you can use Chunk operations that return Chunk[A] instead of NonEmptyChunk[A]
val filtered: Chunk[Int] = chunk.filter(_ > 1)
```

### Conversion to Scala Cons List

Convert a `NonEmptyChunk` to a Scala cons list (`::[A]`), maintaining the non-empty guarantee:

```scala
trait NonEmptyChunk[+A] {
  def toCons[A1 >: A]: ::[A1]
}
```

Since `NonEmptyChunk` is guaranteed to be non-empty, conversion to Scala's cons list is safe and always returns a `::` (non-empty list) rather than `Nil`:

```scala mdoc:reset
import zio.blocks.chunk.NonEmptyChunk

val nonEmpty = NonEmptyChunk(1, 2, 3)
val consList: scala.collection.immutable.::[Int] = nonEmpty.toCons

// Access head and tail like a normal cons cell
consList.head
consList.tail
```

**Use case:** Interoperability with code expecting Scala cons lists; recursive list processing that relies on the structure being non-empty.

### Integration with Chunk Operations

Many Chunk operations are available on `NonEmptyChunk` and return `NonEmptyChunk` when the structure is guaranteed to remain non-empty:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = NonEmptyChunk(1, 2, 3, 4, 5)

// These return NonEmptyChunk[A]
val mapped = chunk.map(_ * 2)

val sorted = chunk.sorted

// These return Chunk[A] (since size might change)
val filtered = chunk.toChunk.filter(_ > 2)
```

## ChunkMap

`ChunkMap[K, V]` is an order-preserving immutable map backed by parallel chunks. It maintains insertion order during iteration:

The type defines the following members:

```scala
object ChunkMap {
  def empty[K, V]: ChunkMap[K, V]
  def apply[K, V](elems: (K, V)*): ChunkMap[K, V]
  def fromChunk[K, V](chunk: Chunk[(K, V)]): ChunkMap[K, V]
  def fromChunks[K, V](keys: Chunk[K], values: Chunk[V]): ChunkMap[K, V]
  def newBuilder[K, V]: Builder[(K, V), ChunkMap[K, V]]
  class Indexed[K, V](val underlying: ChunkMap[K, V]) { ... }
}
```

We can create a simple map using the `apply` method and then perform basic operations like retrieving a value by key, updating a key with a new value, or removing a key:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkMap}

val map = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
map.get("b")
map.updated("d", 4)
map.removed("b")
```

### Creating ChunkMap

`ChunkMap` provides several factory methods for construction from different sources:

The companion object defines these construction methods:

```scala
object ChunkMap {
  def empty[K, V]: ChunkMap[K, V]
  def apply[K, V](elems: (K, V)*): ChunkMap[K, V]
  def fromChunk[K, V](chunk: Chunk[(K, V)]): ChunkMap[K, V]
  def fromChunks[K, V](keys: Chunk[K], values: Chunk[V]): ChunkMap[K, V]
}
```

Create an empty map for a specific key and value type:

```scala mdoc:reset
import zio.blocks.chunk.ChunkMap

val empty = ChunkMap.empty[String, Int]
```

Construct a map directly from key-value pairs:

```scala mdoc
val fromPairs = ChunkMap("x" -> 1, "y" -> 2)
```

Build a map from a `Chunk` containing tuple pairs:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkMap}

val fromChunk = ChunkMap.fromChunk(Chunk(("a", 1), ("b", 2)))
```

Construct a map from parallel chunks of keys and values:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkMap}

val keys = Chunk("a", "b")
val values = Chunk(1, 2)
val fromChunks = ChunkMap.fromChunks(keys, values)
```

### Indexed Access

Use positional access to retrieve entries by their insertion order index:

The `ChunkMap` class exposes these methods:

```scala
trait ChunkMap[K, V] {
  def atIndex(idx: Int): (K, V)
  def keyAtIndex(idx: Int): K
  def valueAtIndex(idx: Int): V
  def keysChunk: Chunk[K]
  def valuesChunk: Chunk[V]
}
```

Create a map and experiment with positional access:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkMap}

val map = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
```

Retrieve the complete key-value pair at a given index:

```scala mdoc
map.atIndex(0)
```

Retrieve just the key at a specific position:

```scala mdoc
map.keyAtIndex(1)
```

Retrieve just the value at a specific position:

```scala
map.valueAtIndex(2)
```

Access the underlying chunks of keys and values:

```scala mdoc
val keys: Chunk[String] = map.keysChunk
val values: Chunk[Int] = map.valuesChunk
```

### Optimized Lookup

The standard `ChunkMap` uses O(n) linear search for key lookups. For frequent lookups, convert to an indexed version that provides O(1) key access:

The indexed wrapper type is nested inside `ChunkMap`:

```scala
trait ChunkMap[K, V] {
  def indexed: ChunkMap.Indexed[K, V]
}
```

Construction of the indexed wrapper builds an internal hash map for constant-time lookups, using extra memory proportional to the number of entries:

```scala mdoc:reset
import zio.blocks.chunk.ChunkMap

val map = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
val indexed = map.indexed
indexed.get("b")
```

## Comparison with Other Sequence Types

Understanding how Chunk compares to other sequence types helps you choose the right tool for your use case:

### Chunk vs `Array`

| Feature           | Chunk                        | Array                                |
|-------------------|------------------------------|--------------------------------------|
| **Immutability**  | Immutable, purely functional | Mutable, imperative                  |
| **Concatenation** | O(log n) via balanced trees  | O(n) requires copying                |
| **Random Access** | O(1) typical, O(log n) worst | O(1) always                          |
| **Safe API**      | Pure, no side effects        | Low-level, requires careful handling |
| **Boxing**        | Avoids boxing primitives     | Supports native primitives           |
| **Lazy Ops**      | Yes (concatenation deferred) | No (eager)                           |

**Use Chunk when**: Building sequences functionally, using in pure code, performing many concatenations, or sharing immutable data.

**Use `Array` when**: Needing low-level memory control, interfacing with Java, or maximum raw performance is critical.

### Chunk vs `List`

| Feature           | Chunk                        | List                  |
|-------------------|------------------------------|-----------------------|
| **Access**        | O(1) random access           | O(n) linear search    |
| **Prepend**       | O(log n)                     | O(1)                  |
| **Memory**        | Compact arrays               | Linked nodes          |
| **Pattern Match** | Not directly                 | Cons pattern matching |
| **Interop**       | Scala collections compatible | Standard Scala type   |

**Use Chunk when**: Frequent random access or concatenation is important.

**Use List when**: Head/tail pattern matching or traditional functional programming style.

### Chunk vs `Vector`

| Feature            | Chunk                        | Vector                    |
|--------------------|------------------------------|---------------------------|
| **Concatenation**  | O(log n) with rebalancing    | O(log₃₂ n) trie structure |
| **Mutation**       | Immutable, purely functional | Effectively immutable     |
| **Memory**         | Lower overhead for many ops  | Higher memory footprint   |
| **Specialization** | Primitive specialization     | None, uses boxing         |
| **Random Access**  | O(1) typical, O(log n) worst | O(log₃₂ n)                |

**Use Chunk when**: Primitive types matter or concatenation performance is critical.

**Use `Vector` when**: Need guaranteed access performance or already using `Vector` in codebase.

### Additional Core Operations

Chunk provides several additional methods for checking properties, materializing data, and handling non-empty conversions:

#### `Chunk#isEmpty` — Check if Chunk is Empty

Test whether the chunk contains no elements:

```scala
trait Chunk[+A] {
  def isEmpty: Boolean
}
```

Checking emptiness is a fast operation:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val full = Chunk(1, 2, 3)
val empty = Chunk.empty[Int]

full.isEmpty
empty.isEmpty
```

**Performance:** O(1) — size is cached.

#### `Chunk#materialize` — Force Full Evaluation

Force the chunk to materialize fully. Useful when you need to ensure lazy operations are evaluated:

```scala
trait Chunk[+A] {
  def materialize[A1 >: A]: Chunk[A1]
}
```

Materializing evaluates all lazy operations:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
val materialized = chunk.materialize
```

**Performance:** O(n) — may trigger deferred operations.

#### `Chunk#nonEmptyOrElse` — Safe NonEmptyChunk Conversion

Convert to a `NonEmptyChunk` with a fallback for empty chunks:

```scala
trait Chunk[+A] {
  def nonEmptyOrElse[B](ifEmpty: => B)(fn: NonEmptyChunk[A] => B): B
}
```

Converting safely to non-empty with a fallback:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(1, 2, 3)
val result = chunk.nonEmptyOrElse("empty")(ne => s"non-empty with ${ne.length} elements")

val empty = Chunk.empty[Int]
val emptyResult = empty.nonEmptyOrElse("no elements")(ne => "should not see this")
```

**Performance:** O(1) — no iteration needed.

#### `Chunk#updated` — Functional Update at Index

Create a new chunk with an element updated at a specific index:

```scala
trait Chunk[+A] {
  def updated[A1 >: A](index: Int, elem: A1): Chunk[A1]
}
```

Updating at an index returns a new chunk with the element replaced:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val original = Chunk(10, 20, 30, 40)
val updated = original.updated(1, 25)
```

**Performance:** O(log n) for tree-structured chunks; O(n) copy for large arrays.

### Type-Safe Primitive Accessors

Chunk provides type-safe accessors to extract primitive values at a specific index. These methods combine index access with type assertion in a single operation:

#### `Chunk#boolean`, `Chunk#byte`, `Chunk#char`, `Chunk#short`, `Chunk#int`, `Chunk#long`, `Chunk#float`, `Chunk#double` — Extract Primitive by Type and Index

Extract a primitive value of a specific type at a given index:

```scala
trait Chunk[+A] {
  def boolean(index: Int)(implicit ev: A <:< Boolean): Boolean
  def byte(index: Int)(implicit ev: A <:< Byte): Byte
  def char(index: Int)(implicit ev: A <:< Char): Char
  def short(index: Int)(implicit ev: A <:< Short): Short
  def int(index: Int)(implicit ev: A <:< Int): Int
  def long(index: Int)(implicit ev: A <:< Long): Long
  def float(index: Int)(implicit ev: A <:< Float): Float
  def double(index: Int)(implicit ev: A <:< Double): Double
}
```

These methods provide type-safe access to primitive values:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val boolChunk = Chunk(true, false, true)
val firstBool = boolChunk.boolean(0)

val intChunk = Chunk(10, 20, 30)
val secondInt = intChunk.int(1)

val doubleChunk = Chunk(1.5, 2.5, 3.5)
val thirdDouble = doubleChunk.double(2)
```

**Performance:** O(log n) typical; O(1) for array-backed chunks.

### Reduction Methods

Advanced reduction operations for folding with different semantics. These methods are available on `NonEmptyChunk` (which wraps a `Chunk` with the guarantee of at least one element):

#### `NonEmptyChunk#reduce` — Fold Without Initial Value

Reduce a non-empty chunk using a binary function. Since the chunk is guaranteed non-empty, no initial value is needed:

```scala
final class NonEmptyChunk[+A] {
  def reduce[B >: A](op: (B, B) => B): B
}
```

This method is useful when you want to compute an aggregate value without providing an initial state. Since the first element serves as the initial value, it only works on non-empty chunks. 

**Use case:** Computing products, finding max/min, or other associative operations on guaranteed non-empty data.

**Performance:** O(n) — processes all elements sequentially.

#### `NonEmptyChunk#reduceMapLeft` — Reduce with Left Map

Reduce elements using a function that first maps the first element, then reduces with a binary operator. This is useful when the result type differs from the element type:

```scala
final class NonEmptyChunk[+A] {
  def reduceMapLeft[B](map: A => B)(reduce: (B, A) => B): B
}
```

Example usage pattern:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(10, 20, 30, 40)
val nonEmpty = NonEmptyChunk(chunk)
val result: String = nonEmpty.reduceMapLeft(_.toString)((acc, n) => acc + ", " + n)
result
```

**Use case:** Transforming and aggregating data in a single pass with type conversion.

**Performance:** O(n) — processes all elements with mapping overhead.

#### `NonEmptyChunk#reduceMapRight` — Reduce with Right Map

Reduce elements from right to left, mapping the rightmost element first. This is right-associative:

```scala
final class NonEmptyChunk[+A] {
  def reduceMapRight[B](map: A => B)(reduce: (A, B) => B): B
}
```

Example usage pattern:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(1, 2, 3, 4)
val nonEmpty = NonEmptyChunk(chunk)
val result: String = nonEmpty.reduceMapRight(_.toString)((n, acc) => n.toString + ", " + acc)
result
```

**Use case:** Right-associative operations like building cons-lists or reverse-order processing.

**Performance:** O(n) — processes all elements right-to-left.

### Bitwise Operations

Bitwise operations work on numeric chunks and provide element-wise logical operations. These are specialized for bit chunks and numeric types:

#### `BitChunk#and`, `BitChunk#or`, `BitChunk#xor` — Bitwise Logical Operations

Combine two bit chunks element-wise using bitwise operations:

```scala
trait BitChunk {
  def and(that: BitChunk): BitChunk
  def or(that: BitChunk): BitChunk
  def xor(that: BitChunk): BitChunk
}
```

Performing bitwise operations element-wise:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val a = Chunk.fromIterable(Seq[Byte](15, -16))
val b = Chunk.fromIterable(Seq[Byte](51, -52))

val andResult = a.map(x => (x & 0xFF).toByte)
val orResult = b.map(x => (x | 0x0F).toByte)
```

**Performance:** O(n) — processes all element pairs.

#### `BitChunk#invert` — Bitwise NOT

Invert all bits in a numeric chunk:

```scala
trait BitChunk {
  def invert: BitChunk
}
```

Bitwise inversion flips all bits:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bytes = Chunk.fromIterable(Seq[Byte](0, 127))
val inverted = bytes.map(b => (~b).toByte)
```

**Performance:** O(n) — processes all elements.

### Additional Utility Methods

Beyond the core operations, `Chunk` provides utility methods for inspecting and extracting underlying data:

## Integration

Chunk integrates deeply with ZIO Blocks' schema system through the [Reflect](./schema/reflect.md) module. When deriving schemas for collection types, `Chunk` is recognized as a key sequence type alongside `List`, `Vector`, and `Set`.

Here's an example of using `Chunk` with schema derivation:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.schema._

case class Event(id: Int, tags: Chunk[String])

object Event {
  implicit val schema: Schema[Event] = Schema.derived
}
// Automatically derives Schema[Chunk[String]] for the tags field
```

Chunks work naturally with the [Codec](./schema/codec.md) system for serialization and deserialization:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json

val data = Chunk(1, 2, 3)

// Encoding to JSON
val json = Json.Array(data.map(i => Json.Number(i)))

// Decoding from JSON
val decoded: Option[Chunk[Int]] = json match {
  case Json.Array(elements) =>
    Some(Chunk.fromIterable(elements.collect { case Json.Number(n) => n.toInt }))
}
```

The [DynamicValue](./schema/dynamic-value.md) system also works with Chunk, allowing schema-driven navigation of chunk data.

## Running the Examples

All code from this guide is available as runnable examples in the appropriate example modules.

To run the examples locally, clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

Then build and test with sbt:

```bash
# Compile everything
sbt compile

# Run tests
sbt test

# Specifically test Chunk functionality
sbt chunk/test
```

Chunk examples are integrated throughout the test suites. You can also explore the test file at `chunk/shared/src/test/scala/zio/blocks/chunk/ChunkSpec.scala` to see idiomatic usage patterns.
