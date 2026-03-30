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
   ┌────▼───┐   ┌──────▼──────┐   ┌────▼──────┐  ┌───▼────┐
   │ Empty  │   │ Singleton   │   │Array-     │  │ Concat │
   │        │   │(one element)│   │Backed     │  │(tree)  │
   └────────┘   └─────────────┘   └───────────┘  └────────┘
```

Chunks automatically choose the most efficient representation:
- **Empty**: singleton instance for zero elements
- **Singleton**: single element, no array allocation
- **Array-backed**: standard array for small sequences
- **Concat tree**: balanced binary tree for composite chunks, enabling O(log n) concatenation depth

The implementation is based on [Conc-Trees for Functional and Parallel Programming](http://aleksandar-prokopec.com/resources/docs/lcpc-conc-trees.pdf) by Aleksandar Prokopec and Martin Odersky.

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

### From Varargs with `Chunk.apply`

The simplest way to create a chunk from individual elements:

```scala
object Chunk {
  def apply[A](as: A*): Chunk[A]
}
```

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

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val arr = Array(10, 20, 30)
val chunk = Chunk.fromArray(arr)

// Do NOT mutate the array after creating the chunk
```

### From Iterable with `Chunk.fromIterable`

Convert any Scala iterable (List, Vector, Set, etc.) into a chunk. Creates a copy, so the original iterable can be mutated:

```scala
object Chunk {
  def fromIterable[A](it: Iterable[A]): Chunk[A]
}
```

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

### Element Access

#### `Chunk#apply` — Random Access

Access an element by index in O(log n) time (O(1) for array-backed chunks):

```scala
trait Chunk[+A] {
  def apply(index: Int): A
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40, 50)

chunk(0)  // 10
chunk(2)  // 30
chunk(4)  // 50
```

#### `Chunk#head` and `Chunk#last` — First and Last Elements

Access the first or last element:

```scala
trait Chunk[+A] {
  def head: A
  def last: A
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk("a", "b", "c", "d")

chunk.head  // "a"
chunk.last  // "d"
```

#### `Chunk#length` and `Chunk#size` — Chunk Size

Get the number of elements (O(1) complexity):

```scala
trait Chunk[+A] {
  def length: Int
  def size: Int
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)

chunk.length  // 5
chunk.size    // 5
```

### Transformations

#### `Chunk#map` — Transform Elements

Apply a function to each element:

```scala
trait Chunk[+A] {
  def map[B](f: A => B): Chunk[B]
}
```

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

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)

chunk.take(3)      // Chunk(1, 2, 3)
chunk.takeRight(2) // Chunk(4, 5)
```

#### `Chunk#drop` and `Chunk#dropRight` — Remove from Ends

Remove the first n elements or last n elements:

```scala
trait Chunk[+A] {
  def drop(n: Int): Chunk[A]
  def dropRight(n: Int): Chunk[A]
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5)

chunk.drop(2)       // Chunk(3, 4, 5)
chunk.dropRight(2)  // Chunk(1, 2, 3)
```

#### `Chunk#slice` — Extract a Range

Extract elements from a start index to an end index:

```scala
trait Chunk[+A] {
  def slice(from: Int, until: Int): Chunk[A]
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30, 40, 50, 60)

chunk.slice(1, 4)  // Chunk(20, 30, 40)
chunk.slice(2, 5)  // Chunk(30, 40, 50)
```

#### `Chunk#split` — Split into Chunks

Split the chunk at a given index:

```scala
trait Chunk[+A] {
  def split(n: Int): Chunk[Chunk[A]]
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4, 5, 6)

val splitAt3 = chunk.split(3)
```

#### `Chunk#span` and `Chunk#splitWhere` — Partition by Predicate

Split the chunk at the first element where a predicate fails:

```scala
trait Chunk[+A] {
  def span(f: A => Boolean): (Chunk[A], Chunk[A])
  def splitWhere(f: A => Boolean): (Chunk[A], Chunk[A])
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5, 6)

val (evens, rest) = numbers.span(_ < 4)
// evens: Chunk[Int] = Chunk(1, 2, 3)
// rest: Chunk[Int] = Chunk(4, 5, 6)

val (small, large) = Chunk(1, 2, 5, 3, 4).splitWhere(_ < 4)
// small: Chunk[Int] = Chunk(1, 2)
// large: Chunk[Int] = Chunk(5, 3, 4)
```

### Querying and Folding

#### `Chunk#foldLeft` — Left Fold

Process elements left-to-right with an accumulator:

```scala
trait Chunk[+A] {
  def foldLeft[S](s0: S)(f: (S, A) => S): S
}
```

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

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(2, 4, 6, 8)

numbers.exists(_ > 5)  // true (8 > 5)
numbers.forall(_ % 2 == 0)  // true (all even)

val mixed = Chunk(1, 2, 3)
mixed.forall(_ > 0)  // true
mixed.forall(_ % 2 == 0)  // false (1 and 3 are odd)
```

#### `Chunk#find` — First Matching Element

Find the first element matching a predicate:

```scala
trait Chunk[+A] {
  def find(f: A => Boolean): Option[A]
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val numbers = Chunk(1, 2, 3, 4, 5)

numbers.find(_ > 3)  // Some(4)
numbers.find(_ > 10)  // None

val words = Chunk("apple", "banana", "cherry")
words.find(_.startsWith("b"))  // Some(banana)
```

#### `Chunk#contains` — Check Membership

Check if the chunk contains a specific element:

```scala
trait Chunk[+A] {
  def contains(a: A)(implicit ev: A => Equals): Boolean
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3, 4)

chunk.contains(3)  // true
chunk.contains(10)  // false
```

### Conversion

#### `Chunk#toArray` — To Array

Convert to an array:

```scala
trait Chunk[+A] {
  def toArray[A1 >: A: ClassTag]: Array[A1]
}
```

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

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)

chunk.toSeq         // Vector(1, 2, 3)
chunk.toIndexedSeq  // IndexedSeq(1, 2, 3)
```

#### `Chunk#toString` — String Representation

Convert to a string:

```scala
trait Chunk[+A] {
  def toString: String
}
```

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val chunk = Chunk(1, 2, 3)
chunk.toString  // "Chunk(1, 2, 3)"
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

```scala mdoc:reset
import zio.blocks.chunk.Chunk

val bytes: Chunk[Byte] = Chunk(1.toByte, 2.toByte, 3.toByte)
bytes.byte(0)  // 1 (no boxing)

val ints = Chunk(10, 20, 30)
ints.int(1)  // 20
```

### Materialization and Optimization

#### `Chunk#materialize` — Force Materialization

Force the chunk to an array-backed representation, eliminating lazy concatenation trees. Useful before performing many operations:

```scala
trait Chunk[+A] {
  def materialize[A1 >: A]: Chunk[A1]
}
```

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

## Subtypes and Variants

### NonEmptyChunk

`NonEmptyChunk[A]` is a type-safe wrapper around `Chunk[A]` that guarantees the chunk is non-empty. It provides the same operations as `Chunk` but with methods like `head` returning `A` instead of potentially throwing an exception.

Create a `NonEmptyChunk` using varargs:

```scala
val nonEmpty = NonEmptyChunk(1, 2, 3)

// Access head safely without the risk of IndexOutOfBoundsException
val first = nonEmpty.head  // 1
```

Create from an existing `Chunk` using `fromChunk`, which returns `Option[NonEmptyChunk[A]]`:

```scala
val chunk = Chunk(1, 2, 3)
val maybeNonEmpty: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(chunk)
// maybeNonEmpty: Some(NonEmptyChunk(1, 2, 3))

val empty = Chunk.empty[Int]
val nothingHere: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(empty)
// nothingHere: None
```

Concatenate chunks and maintain the type:

```scala
val nonEmpty1 = NonEmptyChunk(1, 2, 3)
val chunk2 = Chunk(4, 5, 6)

val result = nonEmpty1 ++ chunk2
```

## Comparison with Other Sequence Types

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

val schema = Schema.derived[Event]
// Automatically derives Schema[Chunk[String]] for the tags field
```

Chunks work naturally with the [Codec](./codec.md) system for serialization and deserialization:

```scala mdoc:reset
import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json

val data = Chunk(1, 2, 3)

// Encoding to JSON
val json = Json.Array(data.map(i => Json.Number(i))*)

// Decoding from JSON
val decoded: Option[Chunk[Int]] = json match {
  case Json.Array(elements) =>
    Some(Chunk.fromIterable(elements.collect { case Json.Number(n) => n.toInt }))
  case null => None
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
