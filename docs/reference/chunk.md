---
id: chunk
title: "Chunk"
---

`Chunk[A]` is an **immutable, indexed sequence** of elements of type `A`. Unlike arrays, Chunk provides a purely functional interface with optimized performance for high-level operations. It is lazy on expensive operations like repeated concatenation (which use balanced tree structures) while remaining fast on access.

`Chunk[A]`:
- Is purely functional and immutable
- Provides O(1) random access via the `apply` method
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

Chunk represents a chunk of values. The implementation is backed by arrays for small chunks but transitions to lazy concatenation trees (`Chunk.Concat`) when building large chunks through repeated concatenation. This design eliminates the O(n²) behavior of naive list concatenation while remaining efficient for both element access and transformation.

```
           ┌─────────────────────────────┐
           │   Chunk[A]                  │
           └─────────────────────────────┘
                        △
                        │
        ┌───────────────┼───────────────┬──────────────┐
        │               │               │              │
   ┌────▼───┐    ┌──────▼──────┐   ┌────▼──────┐   ┌───▼────┐
   │ Empty  │    │ Singleton   │   │Array-     │   │ Concat │
   │        │    │(one element)│   │Backed     │   │(tree)  │
   └────────┘    └─────────────┘   └───────────┘   └────────┘
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

There's another important optimization: Chunk is designed to handle primitive types like Int, Long, Double, and Byte without boxing them. This is a significant performance win in practice. The specialized constructors and accessors ensure that a `Chunk[Int]` really does store integers directly in memory, not wrapped in Java Integer objects.

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

## Construction / Creating Instances

Chunk provides multiple factory methods for creating instances from different sources. Choose the method that best matches your data source:

### From Varargs with `Chunk.apply`

The simplest way to create a chunk from individual elements:

```scala
object Chunk {
  def apply[A](as: A*): Chunk[A]
}
```

Here's how to create chunks from varargs:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)

val strings = Chunk("alice", "bob", "charlie")
```

### Empty Chunk with `Chunk.empty`

Create an empty chunk:

```scala
object Chunk {
  def empty[A]: Chunk[A]
}
```

Checking the length confirms empty chunks have zero elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val empty = Chunk.empty[Int]

empty.length
```

### Single Element with `Chunk.single`

Efficient constructor for a single-element chunk:

```scala
object Chunk {
  def single[A](a: A): Chunk[A]
}
```

The single-element constructor is convenient for wrapping individual values:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val one = Chunk.single("hello")

val singleInt = Chunk.single(42)
```

### From Array with `Chunk.fromArray`

Create a chunk from an array (**Warning**: The array must not be mutated after creating the chunk):

```scala
object Chunk {
  def fromArray[A](array: Array[A]): Chunk[A]
}
```

Converting from an array demonstrates the safety of immutability:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val arr = Array(10, 20, 30)
val chunk = Chunk.fromArray(arr)

// Do NOT mutate the array after creating the chunk
```

### From Iterable with `Chunk.fromIterable`

Convert any Scala iterable (List, Vector, Set, etc.) into a chunk. For Chunk and Vector inputs, the underlying data may be shared; for other iterables, the data is copied:

```scala
object Chunk {
  def fromIterable[A](it: Iterable[A]): Chunk[A]
}
```

You can convert from multiple iterable types at once:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val list = List(1, 2, 3)
val chunk = Chunk.fromIterable(list)

val vector = Vector("x", "y", "z")
val chunkFromVec = Chunk.fromIterable(vector)
```

### From Iterator with `Chunk.fromIterator`

Consume an iterator and collect into a chunk:

```scala
object Chunk {
  def fromIterator[A](iterator: Iterator[A]): Chunk[A]
}
```

Consuming an iterator produces a chunk with all collected elements:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val iter = Iterator(5, 10, 15)
val chunk = Chunk.fromIterator(iter)
```

### From `java.nio` Buffers

Create chunks directly from Java NIO buffers (ByteBuffer, CharBuffer, etc.):

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

### Fill with `Chunk.fill`

Create a chunk by repeating an element n times:

```scala
object Chunk {
  def fill[A](n: Int)(elem: => A): Chunk[A]
}
```

Repeating elements creates uniform chunks:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val repeated = Chunk.fill(5)("x")

val zeros = Chunk.fill(3)(0)
```

### Iterate with `Chunk.iterate`

Create a chunk by repeatedly applying a function starting from an initial value:

```scala
object Chunk {
  def iterate[A](start: A, len: Int)(f: A => A): Chunk[A]
}
```

Iteration from a starting value generates sequences:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val powers = Chunk.iterate(1, 5)(_ * 2)

val alphabet = Chunk.iterate('a', 3)(c => (c + 1).toChar)
```

### Unfold with `Chunk.unfold`

Generate a chunk by repeatedly applying a function that returns an optional value:

```scala
object Chunk {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Chunk[A]
}
```

Unfold lets you generate chunks from state transitions:

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

### Using ChunkBuilder for Incremental Construction

For building chunks incrementally, use `ChunkBuilder`:

```scala
object ChunkBuilder {
  def make[A](): ChunkBuilder[A]
  def make[A](capacityHint: Int): ChunkBuilder[A]
}
```

Building incrementally demonstrates the buffering strategy:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, ChunkBuilder}

val builder = ChunkBuilder.make[Int](10)
builder.addOne(1)
builder.addOne(2)
builder.addOne(3)
val chunk = builder.result()

// With capacity hint for better performance
val builder2 = ChunkBuilder.make[String](100)
builder2.addAll(List("a", "b", "c").iterator)
val result = builder2.result()
```

## Core Operations

Chunk provides a rich set of operations for accessing, transforming, and combining elements. Operations are organized by category:

### Element Access

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

### Transformations

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

Map each element to a chunk and flatten the result:

```scala
trait Chunk[+A] {
  def flatMap[B](f: A => Chunk[B]): Chunk[B]
}
```

Flat-mapping chains transformations and flattens the result:

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

### Combining Chunks

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

### Slicing and Partitioning

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

Note: Passing n ≤ 0 throws ArithmeticException; n must be a positive integer.

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

### Querying and Folding

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

### Conversion

#### `Chunk#toArray` — To Array

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

#### `Chunk#toList` — To List

Convert to a List:

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

### Materialization and Optimization

#### `Chunk#materialize` — Force Materialization

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

### Text Operations

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

#### `Chunk#asBase64String` — Encode as Base64

Convert a chunk of bytes or characters to a Base64-encoded string:

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bytes = Chunk(1.toByte, 2.toByte, 3.toByte)
val base64 = bytes.asBase64String
```

### Advanced Transformations

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

#### `Chunk#nonEmptyOrElse` — Non-Empty Check with Alternative

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

## Subtypes and Variants

Chunk has specialized variants for specific use cases:

### NonEmptyChunk

`NonEmptyChunk[A]` is a type-safe wrapper around `Chunk[A]` that guarantees the chunk is non-empty. It provides the same operations as `Chunk` but with methods like `head` returning `A` instead of potentially throwing an exception.

Create a `NonEmptyChunk` using varargs:

```scala mdoc:silent:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val nonEmpty = NonEmptyChunk(1, 2, 3)
```

Accessing the head of a non-empty chunk is always safe:

```scala mdoc
nonEmpty.head
```

Create from an existing `Chunk` using `fromChunk`, which returns `Option[NonEmptyChunk[A]]`:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val chunk = Chunk(1, 2, 3)
val maybeNonEmpty: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(chunk)

val empty = Chunk.empty[Int]
val nothingHere: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(empty)
```

Concatenate chunks and maintain the type:

```scala mdoc:reset
import zio.blocks.chunk.{Chunk, NonEmptyChunk}

val nonEmpty1 = NonEmptyChunk(1, 2, 3)
val chunk2 = Chunk(4, 5, 6)

val result = nonEmpty1 ++ chunk2
```

## Comparison with Other Sequence Types

Understanding how Chunk compares to other sequence types helps you choose the right tool for your use case:

### Chunk vs Array

| Feature | Chunk | Array |
|---------|-------|-------|
| **Immutability** | Immutable, purely functional | Mutable, imperative |
| **Concatenation** | O(log n) via balanced trees | O(n) requires copying |
| **Random Access** | O(1) typical, O(log n) worst | O(1) always |
| **Safe API** | Pure, no side effects | Low-level, requires careful handling |
| **Boxing** | Avoids boxing primitives | Supports native primitives |
| **Lazy Ops** | Yes (concatenation deferred) | No (eager) |

**Use Chunk when**: Building sequences functionally, using in pure code, performing many concatenations, or sharing immutable data.

**Use Array when**: Needing low-level memory control, interfacing with Java, or maximum raw performance is critical.

### Chunk vs List

| Feature | Chunk | List |
|---------|-------|-------|
| **Access** | O(1) random access | O(n) linear search |
| **Prepend** | O(log n) | O(1) |
| **Memory** | Compact arrays | Linked nodes |
| **Pattern Match** | Not directly | Cons pattern matching |
| **Interop** | Scala collections compatible | Standard Scala type |

**Use Chunk when**: Frequent random access or concatenation is important.

**Use List when**: Head/tail pattern matching or traditional functional programming style.

### Chunk vs Vector

| Feature | Chunk | Vector |
|---------|-------|-------|
| **Concatenation** | O(log n) with rebalancing | O(log₃₂ n) trie structure |
| **Mutation** | Immutable, purely functional | Effectively immutable |
| **Memory** | Lower overhead for many ops | Higher memory footprint |
| **Specialization** | Primitive specialization | None, uses boxing |
| **Random Access** | O(1) typical, O(log n) worst | O(log₃₂ n) |

**Use Chunk when**: Primitive types matter or concatenation performance is critical.

**Use Vector when**: Need guaranteed access performance or already using Vector in codebase.

## Integration

Chunk integrates deeply with ZIO Blocks' schema system through the [Reflect](./reflect.md) module. When deriving schemas for collection types, `Chunk` is recognized as a key sequence type alongside List, Vector, and Set.

**Example: Schema for Chunk data**

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.schema._

case class Event(id: Int, tags: Chunk[String])

object Event {
  implicit val schema: Schema[Event] = Schema.derived
}
// Automatically derives Schema[Chunk[String]] for the tags field
```

Chunks work naturally with the [Codec](./codec.md) system for serialization and deserialization:

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

The [DynamicValue](./dynamic-value.md) system also works with Chunk, allowing schema-driven navigation of chunk data.

## Running the Examples

All code from this guide is available as runnable examples in the appropriate example modules.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Build and test with sbt:**

```bash
# Compile everything
sbt compile

# Run tests
sbt test

# Specifically test Chunk functionality
sbt chunk/test
```

Chunk examples are integrated throughout the test suites. You can also explore the test file at `chunk/shared/src/test/scala/zio/blocks/chunk/ChunkSpec.scala` to see idiomatic usage patterns.
