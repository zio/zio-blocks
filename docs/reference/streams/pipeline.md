---
id: pipeline
title: "Pipeline"
---

`Pipeline[-In, +Out]` is a **reusable, composable stream transformation** that converts elements of type `In` into elements of type `Out`. Pipelines are first-class values: you can define them once, compose them with `andThen`, and apply them to any [Stream](./stream.md) via `stream.via(pipe)` or to any `Sink` via `pipe.andThenSink(sink)`.

`Pipeline`:
- Is contravariant in `In` and covariant in `Out` (like a function `In => Out`)
- Can be applied to a **Stream** (transforming the output) or a **Sink** (pre-processing the input)
- Participates in JVM primitive specialization to avoid boxing

Here is the structural shape of the `Pipeline` type:

```scala
abstract class Pipeline[-In, +Out] {
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C]
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

## Overview

Pipelines solve the problem of reusing stream transformations across different streams and sinks. Without pipelines, you repeat filtering and mapping logic for every stream. With pipelines, you define transformations once as first-class values, compose them freely, and apply them anywhere.

### The Problem

When you build stream processing logic, you often write chains like:

```scala
stream
  .filter(_ > 0)
  .map(_ * 2)
  .take(100)
```

This works, but the transformation is tied to a specific stream. If you want to apply the same logic to a different stream, or to a sink instead, you have to repeat yourself. You cannot pass the chain around as a value, store it in a variable, or compose it with other transformations.

### The Solution

`Pipeline[-In, +Out]` lifts stream transformations into first-class values. You define a pipeline once, compose it with other pipelines using `andThen`, and apply it wherever you need:

```scala mdoc:compile-only
import zio.blocks.streams.*

// Define once
val normalize: Pipeline[Int, Int] =
  Pipeline.filter[Int](_ > 0)
    .andThen(Pipeline.map[Int, Int](_ * 2))
    .andThen(Pipeline.take(100))

// Apply to any stream
val stream1 = Stream(1, 2, 3, 4, 5)
val stream2 = Stream(10, 20, 30, 40, 50)
val stream3 = Stream(-5, 3, 7, 2, 8, 1, 9)

val result1 = stream1.via(normalize).runCollect
val result2 = stream2.via(normalize).runCollect

// Apply to a sink (pre-process input before the sink sees it)
val normalizedSink = normalize.andThenSink(Sink.collectAll[Int])
val result3 = stream3.run(normalizedSink)
```

`Pipeline` forms a **category** in the mathematical sense:

| Law            | Statement                                            |
|----------------|------------------------------------------------------|
| Left identity  | `Pipeline.identity andThen p == p`                   |
| Right identity | `p andThen Pipeline.identity == p`                   |
| Associativity  | `(p andThen q) andThen r == p andThen (q andThen r)` |

These laws guarantee that pipelines compose predictably, regardless of how you parenthesize.

### Architecture

`Pipeline` sits between `Stream` and `Sink`, mediating how elements flow:

```
  Applying to a Stream (via):
  ┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
  │ Stream[E, In]│ ──→ │ Pipeline[In, Out]│ ──→ │Stream[E, Out]│
  └──────────────┘     └──────────────────┘     └──────────────┘

  Applying to a Sink (andThenSink):
  ┌──────────────────┐     ┌────────────────┐     ┌──────────────┐
  │ Pipeline[In, Out]│ ──→ │ Sink[E, Out, Z]│ ──→ │Sink[E, In, Z]│
  └──────────────────┘     └────────────────┘     └──────────────┘

  Composing two Pipelines (andThen):
  ┌──────────────────┐     ┌──────────────────┐     ┌─────────────────┐
  │ Pipeline[A, B]   │ ──→ │ Pipeline[B, C]   │ ──→ │ Pipeline[A, C]  │
  └──────────────────┘     └──────────────────┘     └─────────────────┘
```

## Construction

Pipelines are built using factory methods on the `Pipeline` companion object. Each factory creates a pipeline that performs a specific transformation: mapping elements, filtering, collecting, or controlling flow. All factories support JVM primitive specialization through implicit `JvmType.Infer` parameters.

### `Pipeline.map[A, B]` — Transform Each Element

Applies a function to every element, producing a new element type. Here is the signature:

```scala
object Pipeline {
  def map[A, B](f: A => B)(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B]
}
```

This is the most common pipeline constructor:

```scala mdoc:reset
import zio.blocks.streams.*

val doubler = Pipeline.map[Int, Int](_ * 2)
val toStr = Pipeline.map[Int, String](_.toString)

val result = Stream(1, 2, 3).via(doubler).runCollect
```

### `Pipeline.filter[A]` — Keep Matching Elements

Keeps only elements that satisfy a predicate. Here is the signature:

```scala
object Pipeline {
  def filter[A](pred: A => Boolean)(implicit jtA: JvmType.Infer[A]): Pipeline[A, A]
}
```

Note that the output type is the same as the input type — filtering does not change the element type:

```scala mdoc:reset
import zio.blocks.streams.*

val positives = Pipeline.filter[Int](_ > 0)

val result = Stream(-2, -1, 0, 1, 2).via(positives).runCollect
```

### `Pipeline.collect[A, B]` — Partial Function Transformation

Applies a partial function: only elements for which the function is defined pass through, and they are transformed to the output type. This combines filtering and mapping in one step. Here is the signature:

```scala
object Pipeline {
  def collect[A, B](pf: PartialFunction[A, B])(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B]
}
```

Use `collect` when you need to filter and transform simultaneously:

```scala mdoc:reset
import zio.blocks.streams.*

val extractInts = Pipeline.collect[Any, Int] { case n: Int => n }

val result = Stream(1, "a", 2, "b", 3).via(extractInts).runCollect
```

### `Pipeline.take[A]` — First N Elements

Passes through at most the first `n` elements, then stops. Here is the signature:

```scala
object Pipeline {
  def take[A](n: Long): Pipeline[A, A]
}
```

This naturally short-circuits — upstream stops producing once `n` elements have passed:

```scala mdoc:reset
import zio.blocks.streams.*

val firstFive = Pipeline.take[Int](5)

val result = Stream.range(0, 1000).via(firstFive).runCollect
```

### `Pipeline.drop[A]` — Skip First N Elements

Skips the first `n` elements, then passes through the rest. Here is the signature:

```scala
object Pipeline {
  def drop[A](n: Long): Pipeline[A, A]
}
```

Use `drop` to skip headers, metadata, or warm-up elements:

```scala mdoc:reset
import zio.blocks.streams.*

val skipHeader = Pipeline.drop[String](1)

val result = Stream("header", "row1", "row2").via(skipHeader).runCollect
```

### `Pipeline.identity[A]` — Pass-Through

The identity pipeline that passes all elements through unchanged. This is the neutral element for `andThen` composition. Here is the signature:

```scala
object Pipeline {
  def identity[A](implicit jtA: JvmType.Infer[A]): Pipeline[A, A]
}
```

You rarely construct `identity` explicitly, but it is important as a base case in generic pipeline-building code:

```scala mdoc:reset
import zio.blocks.streams.*

val noOp = Pipeline.identity[Int]

// These are equivalent:
// stream.via(noOp)  ==  stream
// noOp.andThen(p)   ==  p
// p.andThen(noOp)   ==  p
```

## Composing Pipelines

Pipelines compose into larger, more complex transformations using `andThen`. Because `Pipeline` forms a mathematical category, composition is associative and respects identity, so you can build pipelines incrementally or conditionally without worrying about how you parenthesize or combine them.

### `Pipeline#andThen[C]` — Sequential Composition

Composes two pipelines into one, applying `this` first and `that` second. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C]
}
```

`andThen` is the key operation that makes pipelines composable. Because `Pipeline` forms a category, composition is associative — you can group `andThen` calls however you like and get the same result:

```scala mdoc:compile-only
import zio.blocks.streams.*

// Individual steps
val filterPositive = Pipeline.filter[Int](_ > 0)
val double         = Pipeline.map[Int, Int](_ * 2)
val takeFirst10    = Pipeline.take[Int](10)

// Compose into a single reusable pipeline
val normalize = filterPositive
  .andThen(double)
  .andThen(takeFirst10)

// Apply to any stream
val result = Stream(-5, 3, -1, 7, 2, 0, 9, 4, 8, 6, 1, 10)
  .via(normalize)
  .runCollect
```

### Building Pipelines Conditionally

Because pipelines are values, you can build them dynamically:

```scala mdoc:compile-only
import zio.blocks.streams.*

def buildPipeline(limit: Option[Int], onlyPositive: Boolean): Pipeline[Int, Int] = {
  val base = if (onlyPositive) Pipeline.identity[Int].andThen(Pipeline.filter(_ > 0)) else Pipeline.identity[Int]
  limit.fold(base)(n => base.andThen(Pipeline.take(n.toLong)))
}
```

## Applying to a Stream

Apply a pipeline to a stream using `via` to transform its output elements. This is the most direct way to use a pipeline: define it once and apply it to multiple streams without repeating the transformation logic.

### `Stream#via[B]` — Apply a Pipeline to a Stream

The primary way to use a pipeline is through `Stream.via`. Here is the signature:

```scala
trait Stream[+E, +A] {
  def via[B](pipe: Pipeline[A, B]): Stream[E, B]
}
```

Under the hood, `via` calls `pipe.applyToStream(this)`. Each pipeline type delegates to a specific `Stream` node — for example, `Pipeline.map` creates a `Stream.Mapped`, and `Pipeline.filter` creates a `Stream.Filtered`.

The key advantage of `via` over inline methods is **reuse**: define the pipeline once and apply it to multiple streams:

```scala mdoc
import zio.blocks.streams.*

// A reusable cleaning pipeline for sensor data
val cleanSensorData: Pipeline[Double, Double] =
  Pipeline.filter[Double](d => !d.isNaN && !d.isInfinite)
    .andThen(Pipeline.filter(d => d >= -100.0 && d <= 100.0))

// Apply to different sensor streams
val sensorStream1 = Stream(45.5, 67.2, Double.NaN, 23.1)
val sensorStream2 = Stream(89.9, -200.0, 12.5, 55.0)

val sensor1Result = sensorStream1.via(cleanSensorData).runCollect
val sensor2Result = sensorStream2.via(cleanSensorData).runCollect
```

### `Pipeline#applyToStream[E]` — Direct Application

You can also call `applyToStream` directly. This is equivalent to `via` but reads left-to-right from the pipeline's perspective. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
}
```

`stream.via(pipe)` and `pipe.applyToStream(stream)` are identical in behavior. Prefer `via` for readability in stream chains.

## Applying to a Sink

Apply a pipeline to a sink using `andThenSink` to pre-process the sink's input elements. This is the dual of `via`: instead of transforming a stream's output, you transform what the sink receives before it processes it.

### `Pipeline#andThenSink[E, Z]` — Pre-Process Sink Input

The dual of `via`: instead of transforming a stream's output, you pre-process a sink's input. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

This is an alias for `applyToSink`. After calling `andThenSink`, the resulting sink accepts `In` elements, transforms them through the pipeline, and feeds the `Out` elements to the original sink:

```scala mdoc
import zio.blocks.streams.*

// A pipeline that normalizes strings
val normalize = Pipeline.map[String, String](_.trim.toLowerCase)

// Apply to different sinks
val collectNormalized = normalize.andThenSink(Sink.collectAll[String])
val countNormalized   = normalize.andThenSink(Sink.count)

val result = Stream("  Hello ", " WORLD  ").run(collectNormalized)
```

### When to Use `andThenSink` vs `via`

Both achieve the same result. Choose based on which side you want to reuse:

| Approach                             | Use when…                                   |
|--------------------------------------|---------------------------------------------|
| `stream.via(pipe).run(sink)`         | You have a fixed pipeline and varying sinks |
| `stream.run(pipe.andThenSink(sink))` | You want a reusable "pre-processing sink"   |

The laws guarantee equivalence: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

### `Pipeline#applyToSink[E, Z]` — Direct Application

`andThenSink` is an alias for `applyToSink`. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

Prefer `andThenSink` for readability.

## Implementation Notes

Understanding how `Pipeline` is implemented helps explain its design choices: why certain methods require type parameters, how specialization avoids boxing, and what trade-offs exist when applying pipelines to sinks.

### JVM Primitive Specialization

`Pipeline.map`, `Pipeline.filter`, `Pipeline.collect`, and `Pipeline.identity` all require `JvmType.Infer[A]` implicit parameters. These are resolved at compile time and enable unboxed, specialized code paths for primitive types (`Int`, `Long`, `Float`, `Double`, etc.). You never need to provide these explicitly — the compiler infers them automatically.

`Pipeline.take` and `Pipeline.drop` do not require `JvmType.Infer` because they do not inspect or transform element values — they only count positions.

### The `RunViaSink` Mechanism

When you apply most pipeline types to a sink (via `applyToSink`), the implementation uses a `RunViaSink` adapter internally:

1. The incoming reader (raw element source) is wrapped in a synthetic stream
2. The pipeline's `applyToStream` transforms that stream
3. The transformed stream is compiled back to a reader
4. The downstream sink drains the compiled reader

This adds a small overhead compared to direct stream application, because an intermediate reader is created and closed.

### The `MapPipeline` Optimization

`Pipeline.map(f).applyToSink(sink)` is a special case: it short-circuits directly to `sink.contramap(f)` instead of going through `RunViaSink`. This is possible because mapping is the dual of contramapping — applying `f` to a sink's input is exactly `contramap`. This optimization avoids the synthetic stream round-trip entirely.

## Integration

`Pipeline` integrates seamlessly with the other core streaming primitives: `Stream` and `Sink`. Understanding these integrations shows how pipelines fit into the broader streaming architecture.

### With Stream

`Pipeline` is the mechanism behind `Stream.via`. Every call to `stream.via(pipe)` delegates to `pipe.applyToStream(stream)`, which constructs the appropriate `Stream` subtype node. See [Stream — Integration with Pipeline and Sink](./stream.md#integration-with-pipeline-and-sink) for the stream-side perspective.

### With Sink

`Pipeline.andThenSink` creates a new `Sink` that pre-processes its input through the pipeline before the original sink consumes it. The `Sink` type provides its own transformation methods (`contramap`, `map`, `mapError`) — `andThenSink` extends these with the full power of pipeline composition (filtering, taking, dropping, collecting).

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

This example demonstrates all six Pipeline factory methods: `map`, `filter`, `collect`, `take`, `drop`, and `identity`. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/pipeline/PipelineBasicUsageExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineBasicUsageExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineBasicUsageExample"
```

### Pipeline Composition

This example shows how to compose pipelines with `andThen`, apply them to multiple streams, and build pipelines conditionally. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/pipeline/PipelineCompositionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineCompositionExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineCompositionExample"
```

### Sink Integration

This example demonstrates applying pipelines to sinks with `andThenSink`, showing the equivalence between `stream.via(pipe).run(sink)` and `stream.run(pipe.andThenSink(sink))`. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/pipeline/PipelineSinkIntegrationExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineSinkIntegrationExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineSinkIntegrationExample"
```
