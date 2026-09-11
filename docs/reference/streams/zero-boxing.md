---
id: zero-boxing
title: "Zero-Boxing Optimization"
sidebar_label: "Zero-Boxing"
---

Working with streams of primitives presents a performance challenge in languages with generic types: **boxing**. Without special care, primitive values get wrapped in objects. ZIO Blocks Streams therefore carries runtime element-type information and provides primitive physical lanes for synchronous interpretation. This removes boxing at important boundaries and hot paths; it is not a promise that every stream program, callback, terminal, or asynchronous operation is allocation-free.

## The Boxing Problem

In Scala, primitive types (`Int`, `Long`, `Double`, `Boolean`) are fundamentally different from their object counterparts (`Integer`, `Long`, `Double`, `Boolean`). When a generic class like `Stream[E, A]` works with primitives, the compiler must box them into objects to satisfy the generic contract:

```scala
// Without optimization, this boxes each Int into an Integer object
val stream: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val doubled = stream.map(_ * 2)  // Each Int is boxed → Integer → boxed result
val result = doubled.runCollect
// Result: Each element was boxed, unboxed, boxed again — wasteful!
```

**Performance cost:**
- Extra heap allocations (memory pressure, more GC)
- Cache misses (objects spread across memory)
- Slower CPU operations (dereferencing objects instead of primitive registers)

For high-throughput data processing, this overhead is unacceptable.

## ZIO Blocks Streams' Solution: JvmType Dispatch

Instead of using Scala's `@specialized` annotation (which generates separate classes for each primitive type, bloating binaries), ZIO Blocks Streams uses **compile-time type detection + runtime dispatch**. This gives you the speed of specialization without the binary bloat.

### How It Works

**Step 1: Compile-Time Detection**

When you create a stream of primitives, the compiler infers a `JvmType` implicit that identifies the element type:

```scala
val intStream: Stream[Nothing, Int] = Stream(1, 2, 3)
// Compiler infers: JvmType.Infer[Int]
// The stream records its element representation

val doubled = intStream.map(_ * 2)
// map receives JvmType.Infer evidence for its transformed result type.
// Its input representation comes from intStream, not call-site evidence.
```

**Step 2: Runtime Type Dispatch**

Stream and pipeline nodes retain their logical `JvmType`. There are eight physical primitive pull identities (`Boolean`, `Byte`, `Short`, `Char`, `Int`, `Long`, `Float`, and `Double`), plus the reference fallback. These identities matter because their read contracts and element semantics differ. The synchronous interpreter compacts them into five storage lanes: int-like (`Boolean`/`Byte`/`Short`/`Char`/`Int`), `Long`, `Float`, `Double`, and reference. Eight primitive identities therefore does not mean eight interpreter arrays. Operator tags select the correct identity-specific reads and callback shapes over those five lanes.

**Step 3: Unboxed Accessors**

Instead of a single `read()` method that returns boxed `Any` (where boxed means wrapping primitives in object wrappers like `Integer`, `Long`, `Double`), primitives use specialized accessors that operate directly on primitive values:

```scala
sealed abstract class Reader[+Elem]

object Reader {
  abstract class SyncReader[+Elem] extends Reader[Elem] {
    def read[A1 >: Elem](sentinel: A1): A1
    def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Int
    def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long
    def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double
  }

  abstract class AsyncReader[+Elem] extends Reader[Elem] {
    def read[A1 >: Elem](sentinel: A1): Async[A1]
    def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Async[Int]
    def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Async[Long]
    def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Async[Double]
  }
}
```

The complete primitive pull surface distinguishes all eight identities: `Boolean`, `Byte`, `Short`, and `Char` use `Int` carriers; `Int` uses a widened `Long`; `Long` uses `Long`; `Float` uses a widened `Double`; and `Double` uses `Double`. (`readByte` has its own exact-read/EOF contract.) These carrier signatures are deliberate even where several identities share one storage lane.

For `SyncReader`, the carrier is a JVM primitive at the method boundary. `SyncInterpreter` also stores primitive state in `Long`-backed physical arrays (using raw bits where necessary) and invokes lane-adapted operators. By contrast, `AsyncReader` returns generic `Async[T]` values. `AsyncInterpreter` keeps primitive lane state internally, but completed primitive results cross the generic async carrier and may be boxed. The asynchronous API should therefore be described as lane-aware, not end-to-end zero-boxing.

## Practical Benefits

To understand the intended benefit, consider how boxing can accumulate through a pipeline. Compare a hypothetical boxed implementation with ZIO Streams' primitive-lane approach.

### Before (Hypothetical Boxed Streams)

Without optimization, each operation in a pipeline adds boxing overhead:

```scala
val nums = Stream(1, 2, 3, 4, 5)
val result = nums
  .map(_ * 2)        // boxes each Int → Integer, applies *, unboxes result
  .filter(_ > 5)     // boxes again, compares, unboxes
  .map(_ + 1)        // boxes, adds, unboxes
  .runCollect
// 5 elements × 3 operations × boxing overhead = significant waste
```

**Memory profile:** Each element is boxed/unboxed multiple times, creating temporary objects.

### With ZIO Streams (Primitive Lanes)

The synchronous interpreter can keep the same pipeline on primitive physical lanes:

```scala
val nums = Stream(1, 2, 3, 4, 5)
val result = nums
  .map(_ * 2)
  .filter(_ > 5)
  .map(_ + 1)
  .runCollect
```

This design avoids per-stage primitive wrapper storage in the fused synchronous interpreter. It does **not** prove that the whole expression allocates nothing: generic Scala function interfaces, source or terminal construction, result collection, fallback paths, and JIT decisions can still introduce boxing or allocation.

## When Zero-Boxing Applies

Primitive type inference and lane selection are automatic. Primitive streams are eligible for specialized synchronous paths, subject to the operation, source, terminal, and any fallback boundaries:

```scala mdoc:compile-only
import zio.blocks.streams.*

// Primitive-specialized logical types
val ints = Stream(1, 2, 3).map(_ * 2)
val longs = Stream(1L, 2L, 3L).filter(_ > 0L)
val doubles = Stream(1.5, 2.5, 3.5).map(_ + 1.0)
val bools = Stream(true, false, true).filter(identity)

// Reference lane: fields are primitive, but Point itself is an object
case class Point(x: Int, y: Int)
val points = Stream(Point(1, 2), Point(3, 4))
  .map(p => Point(p.x * 2, p.y * 2))

// Reference lane: tuples are objects
val pairs = Stream((1, 2), (3, 4))
  .map { case (x, y) => (x + 1, y + 1) }

// Works, but may box for non-primitive types
val strings = Stream("a", "b", "c").map(_.toUpperCase)
```

You don't need to do anything special — the compiler and runtime handle it automatically.

## Comparison: @specialized vs JvmType Dispatch

ZIO Blocks Streams' approach differs fundamentally from Scala's traditional `@specialized` annotation. Here's how they compare:

**Traditional Scala `@specialized` annotation** generates separate specialized classes at compile time:

```scala
@specialized(Int, Long, Double)
class Stream[+E, +A] { ... }
// Generates separate classes:
// - Stream$mcI$sp (specialized for Int)
// - Stream$mcJ$sp (specialized for Long)
// - Stream$mcD$sp (specialized for Double)
// - Stream (generic fallback)
// Result: additional generated classes and bytecode
```

**ZIO Blocks `JvmType` dispatch** records logical input and output types on stream and pipeline nodes. Compilation turns those types into interpreter lane and operator tags; it does not generate a separate `Stream` class for every primitive.

| Metric | `@specialized` | JvmType |
|--------|---|---|
| **Binary size** | Additional specialized classes | No class-per-primitive specialization |
| **Bytecode complexity** | High | Moderate |
| **Runtime dispatch** | Selected by specialized class | Interpreter lane and operator-tag dispatch |
| **Flexibility** | Fixed at compile time | Adaptive at runtime |
| **Primitive support** | Configurable | Boolean, Byte, Short, Char, Int, Long, Float, Double |
| **Generality** | Good for all generics | Specialized for Stream/Sink |

## Implementation Architecture

Primitive type metadata is carried across ZIO Blocks Streams' three core abstractions. Whether a concrete execution remains on primitive physical lanes depends on the interpreter and operation.

### Stream[E, A]

Stores element representation when a source is constructed and dispatches `Reader` accesses. Transformations request fresh evidence for their result, not for an input whose representation the stream already knows:

```scala mdoc:compile-only
import zio.blocks.streams.*

val stream: Stream[Nothing, Int] = Stream(1, 2, 3)
// JvmType.Int is inferred and available to all operations

val asLong = stream.map(_.toLong)
// Infer[Long] records the transformed result representation
```

### Sink[E, A, Z]

Accepts elements via unboxed `write` methods matched to the detected type:

```scala mdoc:compile-only
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val nums = Stream(1, 2, 3)
val sum = nums.runFold(0)(_ + _)
// The synchronous interpreter can feed the fold through an Int lane
```

### Pipeline[A, B]

Preserves the input representation and records evidence for transformed results:

```scala mdoc:compile-only
import zio.blocks.streams.*

val pipe = Pipeline.map[Int, Int](_ * 2)
// Infer[Int] describes the result. The input representation is supplied when
// the pipeline is applied to a Stream or Sink.

val throughStream = Stream(1, 2, 3).via(pipe)
val throughSink   = pipe.andThenSink(Sink.sumInt)
// Both application routes propagate the same representation information.
```

## Performance Impact

Performance falls into distinct categories:

- **Scalar CPU pipelines** (`map`, `filter`, folds) benefit most from primitive lanes because boxing, allocation, and dispatch are a large share of the work.
- **Bulk memory paths** (`readInts`, `readLongs`, `readDoubles`, collection) additionally benefit from contiguous arrays and fewer per-element calls.
- **Bounded concurrency** pays queue/selector and scheduling costs; use it when callback or child latency dominates, not for trivial arithmetic.
- **Native asynchronous I/O** is governed mainly by source latency, chunk size, and cancellation/ownership costs; zero-boxing is usually secondary.
- **Writer `*Async` adapters** defer synchronous work and may still block, so they should not be benchmarked as native async I/O.

Do not infer a universal multiplier from these categories. Results depend on JDK, Scala version, platform, element type, pipeline shape, buffer size, and terminal; use the repository JMH benchmarks with a workload representative of the application.

Repository JMH results measure throughput for named benchmark methods and configurations. Unless a run also records an allocation profiler (for example `gc.alloc.rate.norm`), it is **not** evidence of zero allocations. The lane layout and primitive reader signatures establish where boxing is avoided by construction; claims about callback invocation, complete pipelines, async carriers, or parity with handwritten loops remain unproven until measured with allocation profiling for that exact workload.

## When Polymorphism Is Necessary

If you need polymorphic behavior (e.g., different handling for different types), use `JvmType` directly:

```scala mdoc:compile-only
import zio.blocks.streams.*
import zio.blocks.streams.JvmType

def processStream[A](stream: Stream[Nothing, A])(implicit jt: JvmType.Infer[A]): Unit = {
  jt.jvmType match {
    case JvmType.Int =>
      println("Processing integers")
    case JvmType.Long =>
      println("Processing longs")
    case _ =>
      println("Processing generic type")
  }
}
```

This gives code runtime type information. It does not by itself guarantee allocation-free execution; the selected operation and interpreter still determine the physical path.

## Summary

ZIO Blocks Streams reduces primitive boxing through:

1. **Compile-time type detection** via `JvmType.Infer[A]` implicits
2. **Runtime dispatch** that selects specialized fast paths
3. **Primitive synchronous accessors** with widened sentinel carriers
4. **Lane-aware async interpretation**, while accepting that generic `Async[T]` carriers may box completed primitives

The result is specialized hot paths without `@specialized` class proliferation. Some generic callbacks, reference values, tuples/case classes, and async/concurrent coordination can still allocate or box; verify important workloads with JMH rather than assuming allocation-free execution end to end.
