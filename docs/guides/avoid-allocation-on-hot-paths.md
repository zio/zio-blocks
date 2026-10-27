---
id: avoid-allocation-on-hot-paths
title: "How to Avoid Allocation on Hot Paths"
description: "Keep streams on primitive lanes, read and build Chunks without boxing, swap Option for Maybe, pre-bind metric labels, and measure each change."
keywords:
  - "Allocation"
  - "Boxing"
  - "Garbage Collection"
  - "Primitive Lanes"
  - "Maybe"
  - "BoundCounter"
---

In this guide, we take a price-tick handler that runs for every trade on a market data feed and remove the allocations from its hot loop, one at a time. By the end, you will know how to keep a ZIO Blocks `Stream` of `Long` prices on its primitive lane, how to read and build a `Chunk` of prices without boxing them, how to keep per-tick state in a `Maybe` instead of an `Option`, and how to record a metric with fixed labels without building an `Attributes` value on every call. Every claim comes with a number we measured, and the last section shows how to measure your own code the same way.

Each section is independent: pick the one that matches the allocation you have. This guide covers:

- Measuring the bytes a loop allocates per element
- Keeping stream elements on a primitive lane, and reading them in a custom `Sink`
- Wrapping, reading and building `Chunk`s without boxing
- Replacing `Option` with `Maybe` for state written on every tick
- Pre-binding metric labels with `Counter#bind`
- Checking the results with JMH and its GC profiler

## The Problem

A loop that runs millions of times per second turns every small allocation into garbage-collector work. On a 64-bit JVM with compressed pointers, a boxed `java.lang.Long` takes 24 bytes, a `Some` 16, and a tuple key for a metrics map another 24; at a million ticks per second that is tens of megabytes of short-lived objects per second, which the JVM pays for in young-generation collections, cache misses and lost throughput.

Generic Scala code allocates this way without saying so. A tick handler written with the standard library often looks like this:

```scala mdoc:compile-only
final case class Tick(symbol: String, priceMicros: Long, size: Int)

final class TickHandler {
  var lastTick: Option[Tick] = None
  val counts                 = scala.collection.mutable.HashMap.empty[(String, String), Long]

  // Called for every trade
  def onTick(tick: Tick): Unit = {
    lastTick = Some(tick)
    val key = (tick.symbol, "XNAS")
    counts.update(key, counts.getOrElse(key, 0L) + 1L)
  }

  // Called for every batch of prices, in micro-dollars
  def turnoverCents(prices: Array[Long]): Long =
    prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
}
```

Nothing here looks expensive, yet on this machine `TickHandler#onTick` allocates 40 to 64 bytes per tick and `turnoverCents` about 62 bytes per price. The tick handler pays for the `Some`, the boxed count and, unless the JIT removes it, the tuple key; the turnover pays for a boxed `Long` at every step of `Iterator#map`, `Iterator#filter` and `Iterator#foldLeft`. None of it is visible in the source.

ZIO Blocks has a tool for each of these allocations. In this guide, we replace them one by one and measure what each change saves.

## Prerequisites

We use four modules: `streams`, `chunk`, `maybe` and `telemetry`. On Scala 2.13 and Scala 3.3 to 3.8, add:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-streams"   % "@VERSION@",
  "dev.zio" %% "zio-blocks-chunk"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-maybe"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-telemetry" % "@VERSION@"
)
```

Scala 3.9 and newer use the `zio-blocks-next-*` artifact names and need `scalacOptions += "-experimental"`; [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) has the details. The `streams` and `telemetry` modules need JDK 21 or newer on the JVM. We assume you know what boxing is and have read [Your First Streaming Pipeline](./your-first-streaming-pipeline.md) or written a `Stream` before.

The examples use these imports:

```scala mdoc:silent
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.maybe._
import zio.blocks.streams._
import zio.blocks.telemetry._
```

## Measure Allocation Per Tick

Before changing anything, we need a number. The JVM counts the bytes each thread allocates, and `com.sun.management.ThreadMXBean` exposes that counter, so a few lines give us bytes per run of any block. The block must run a few thousand times first, because the JIT compiler removes some allocations once it has compiled the code, and a cold measurement reports allocations that a hot loop never makes:

```scala mdoc:silent
import java.lang.management.ManagementFactory

object Allocation {
  private val threads =
    ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]

  @volatile private var sink: Int = 0

  // Runs `body` until the JIT has compiled it, then returns the average number
  // of bytes one run allocates on the current thread.
  def bytesPerRun(body: => Any): Long = {
    var i = 0
    while (i < 2000) { sink ^= System.identityHashCode(body); i += 1 }
    val runs   = 200
    val before = threads.getCurrentThreadAllocatedBytes
    i = 0
    while (i < runs) { sink ^= System.identityHashCode(body); i += 1 }
    (threads.getCurrentThreadAllocatedBytes - before) / runs
  }
}
```

The `sink` field keeps the JIT from discarding a result nobody reads. The counter is per thread, so the block must run on the calling thread, which every example in this guide does.

The running example is a batch of 10,000 trades for one symbol. Prices are fixed-point `Long`s in micro-dollars, and every one of them lies far outside the small-value cache that `java.lang.Long.valueOf` keeps for -128 to 127, so boxing one always allocates:

```scala mdoc:silent
final case class Tick(symbol: String, priceMicros: Long, size: Int)

val tickCount           = 10000
val prices: Array[Long] = Array.tabulate(tickCount)(i => 187000000L + (i % 997) * 1300L)
val sizes: Array[Int]   = Array.tabulate(tickCount)(i => 100 + i % 50)
val ticks: Array[Tick]  = Array.tabulate(tickCount)(i => Tick("ACME", prices(i), sizes(i)))
```

Dividing `Allocation.bytesPerRun` by `tickCount` gives bytes per tick. The figures in this guide come from the companion examples and from the JMH benchmark in [Check the Numbers With JMH](#check-the-numbers-with-jmh), run on one machine with JDK 25; treat them as rough sizes, not guarantees.

## Keep a Stream on a Primitive Lane

A ZIO Blocks `Stream` carries each element on a lane: one of the primitive JVM types, or `AnyRef` for objects. On a primitive lane a `Long` moves from the source through `Stream#map`, `Stream#filter` and the sink as a raw `long`, so the pipeline allocates nothing per element. The lane is picked at compile time from the element type each operator produces, so the same code that boxed with an `Iterator` stops boxing when it is a `Stream`:

```scala mdoc:compile-only
val iteratorBytes = Allocation.bytesPerRun {
  prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
}

val streamBytes = Allocation.bytesPerRun {
  Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).runFold(0L)(_ + _)
}

println(s"Iterator: ${iteratorBytes.toDouble / tickCount} bytes per tick")
println(s"Stream:   ${streamBytes.toDouble / tickCount} bytes per tick")
```

On this machine the `Iterator` version allocates about 62 bytes per tick and the `Stream` version about 0.1: some 700 to 1,000 bytes per run of 10,000 ticks, for the stream's own nodes and the `Either` that `Stream#runFold` returns. Both compute the same turnover:

```scala mdoc
val viaIterator = prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
val viaStream   = Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).runFold(0L)(_ + _)
```

`Stream.fromArray` reads straight from the array. `Stream.fromIterable` also picks the `Long` lane for a `List[Long]`, but the list already holds one boxed `Long` per element, and the stream unboxes them as it reads; build hot-path data in an array or a `Chunk` instead.

### See Which Lane a Stream Is On

`Stream` has no method that reports its lane, but the reader a sink receives does: `Reader#jvmType` names the lane the elements arrive on. A one-line sink built with `Sink.create` (JVM only) turns that into a diagnostic:

```scala mdoc:silent
def lane[A]: Sink[Nothing, A, JvmType] = Sink.create[Nothing, A, JvmType](reader => reader.jvmType)
```

Running a few pipelines into it shows which operators keep the `Long` lane and which leave it:

```scala mdoc
Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).run(lane)
Stream.fromArray(prices).map(_.toDouble / 1e6).run(lane)
Stream.fromArray(prices).scan(0L)(_ + _).run(lane)
(Stream.fromArray(prices) && Stream.fromArray(sizes)).run(lane)
Stream.fromArray(prices).grouped(100).run(lane)
```

The rules behind these results:

| Operators                                                                                       | Lane of the result                                               |
| ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `Stream#filter`, `Stream#take`, `Stream#drop`, `Stream#takeWhile`, `Stream#tapEach`             | The input's lane                                                 |
| `Stream#map`, `Stream#collect`, `Stream#scan`, `Stream#mapAccum`, `Stream#flatMap`, `Stream#++` | The lane of the result type (`Long => Double` moves to `Double`) |
| `Stream#&&` (zip), `Stream#grouped`, `Stream#chunked`, `Stream#sliding`                         | `AnyRef`: their elements are tuples or `Chunk`s                  |
| Any operator whose result type is a reference or `Any`                                          | `AnyRef`                                                         |

None of these changes is reported at compile time. A `Stream#map` whose function returns `Any` (for example, an `if` with a `Long` in one branch and a `String` in the other) quietly moves the stream to the `AnyRef` lane and boxes every element from there on.

### Replace a Zip With an Index

Zipping two primitive streams is the most common way to leave the lane by accident: `&&` pairs prices with sizes as tuples, so every element becomes an object. When both sides are arrays of the same length, a stream of indices does the same work on the `Long` lane:

```scala mdoc:compile-only
val zippedBytes = Allocation.bytesPerRun {
  (Stream.fromArray(prices) && Stream.fromArray(sizes)).map { case (p, s) => p * s }.runFold(0L)(_ + _)
}

val indexedBytes = Allocation.bytesPerRun {
  Stream.range(0, tickCount).map(i => prices(i) * sizes(i)).runFold(0L)(_ + _)
}

println(s"zipped:  ${zippedBytes.toDouble / tickCount} bytes per tick")
println(s"indexed: ${indexedBytes.toDouble / tickCount} bytes per tick")
```

The zipped version allocates about 55 bytes per tick on this machine, the indexed one under 0.05. Both compute the same notional value:

```scala mdoc
(Stream.fromArray(prices) && Stream.fromArray(sizes)).map { case (p, s) => p * s }.runFold(0L)(_ + _)
Stream.range(0, tickCount).map(i => prices(i) * sizes(i)).runFold(0L)(_ + _)
```

### Read Primitives in a Custom Sink

The built-in sinks and terminals already read primitive lanes without boxing: `Sink.sumLong`, `Sink.sumDouble`, `Sink.count`, `Sink.foldLeft` with a `Long`, `Int` or `Double` accumulator, and `Stream#runFold` with a `Long`, `Int` or `Double` start value. When we need a sink of our own, we pull from the reader with its primitive reads instead of `Reader#read`, which returns a boxed element.

For a `Long` stream, the read that never boxes and never mistakes a price for the end of the stream is `Reader#readLongs` with a one-element array: it copies the next element into the array and returns `1`, or returns `-1` once the stream has ended. A sink that tracks the highest price looks like this:

```scala mdoc:silent
val highestPrice: Sink[Nothing, Long, Long] = Sink.create[Nothing, Long, Long] { reader =>
  val one     = new Array[Long](1)
  var highest = Long.MinValue
  var n       = reader.readLongs(one, 0, 1)
  while (n >= 0) {
    if (one(0) > highest) highest = one(0)
    n = reader.readLongs(one, 0, 1)
  }
  highest
}
```

It reads every price on the `Long` lane:

```scala mdoc
Stream.fromArray(prices).run(highestPrice)
```

Measured the same way as before, a run over 10,000 ticks allocates about 224 bytes in total, all of it per run (the stream, the reader and the one-element array), none of it per tick.

`Sink.create` exists only on the JVM. On Scala.js, or to support both blocking and asynchronous runs, build the sink with `Sink.createBoth`, which takes one function for a `Reader.SyncReader` and one for a `Reader.AsyncReader`; both reader kinds have the same primitive reads. The [Sink reference](../reference/streams/core/sink.md) covers both factories.

### Choose an End-of-Stream Signal That Cannot Collide

Each primitive lane has a read that widens the element into a larger type, so a sentinel outside the element's range can mark the end of the stream and no real element can be mistaken for it. `Reader#readInt(sentinel)` returns a `Long`, and the convention is to pass `Long.MinValue`; `Reader#readFloat(sentinel)` returns a `Double`, and the convention is to pass `Double.MaxValue`; `Reader#readByte()` returns `0` to `255`, or `-1` at the end.

`Long` and `Double` have no wider type to escape into, so `Reader#readLong` and `Reader#readDouble` return the element type itself, and the sentinel we pass in can equal a real element. A real element equal to that marker ends the loop early, and nothing tells us it happened. For example, a sink that sums price changes and picks `0L` as its marker stops at the first unchanged price:

```scala mdoc:silent
val priceChanges = Array(5L, -3L, 0L, 7L)

val sumUntilMarker: Sink[Nothing, Long, Long] = Sink.create[Nothing, Long, Long] { reader =>
  var sum   = 0L
  var value = reader.readLong(0L)
  while (value != 0L) {
    sum += value
    value = reader.readLong(0L)
  }
  sum
}
```

It returns `2`, while the built-in `Sink.sumLong`, which uses the count-returning read, returns the full `9`:

```scala mdoc
Stream.fromArray(priceChanges).run(sumUntilMarker)
Stream.fromArray(priceChanges).run(Sink.sumLong)
```

No marker value is safe for every feed, so read `Long` and `Double` lanes with `Reader#readLongs` or `Reader#readDoubles` and a one-element array, as `highestPrice` does. The library's own sinks do the same, which is why `Sink.sumLong` keeps the zero. Do not add a check against the marker inside the loop to work around a collision: the count-returning read costs nothing extra per element and never collides. The [zero-boxing reference](../reference/streams/execution-and-compatibility/zero-boxing.md) lists the end-of-stream signal of every lane.

## Read and Build Chunks Without Boxing

A `Chunk` built from a primitive array stores its elements in that array, unboxed. Three habits decide whether our code keeps that advantage: how we create the chunk, how we read it, and how we build a new one.

### Wrap an Array Instead of Copying It

`Chunk.fromArray` wraps the array we already have; `Chunk.from` (like `Chunk.fromIterable`) copies it first:

```scala mdoc:compile-only
val copyBytes = Allocation.bytesPerRun(Chunk.from(prices))
val wrapBytes = Allocation.bytesPerRun(Chunk.fromArray(prices))

println(s"Chunk.from:      $copyBytes bytes per batch")
println(s"Chunk.fromArray: $wrapBytes bytes per batch")
```

On this machine `Chunk.from` allocates about 80,000 bytes per batch of 10,000 prices (8 bytes per price for the copy), and `Chunk.fromArray` allocates 32 bytes, the wrapper object alone.

:::warning
A chunk from `Chunk.fromArray` shares the array with the caller. Writing to the array afterwards changes the chunk, which breaks the promise that a `Chunk` is immutable. Wrap only arrays that nothing writes to again, such as a buffer we have just filled and handed over.
:::

### Read Elements With a Primitive Accessor

`Chunk#foldLeft` is generic: its accumulator and each element pass through `(S, A) => S` as objects, so summing 10,000 prices allocates 24 to 48 bytes per price on this machine (one or two boxed `Long`s, depending on how much of the fold the JIT inlines). An index loop with `Chunk#long` reads each `Long` straight from the backing array:

```scala mdoc:silent
val priceChunk = Chunk.fromArray(prices)

def totalMicros(chunk: Chunk[Long]): Long = {
  var sum = 0L
  var i   = 0
  while (i < chunk.length) {
    sum += chunk.long(i)
    i += 1
  }
  sum
}
```

Both give the same total, but the loop allocates nothing per price:

```scala mdoc
priceChunk.foldLeft(0L)(_ + _)
totalMicros(priceChunk)
```

The same accessor exists for every primitive: `Chunk#int`, `Chunk#double`, `Chunk#byte`, `Chunk#float`, `Chunk#short`, `Chunk#char` and `Chunk#boolean`. They read the array directly only when the chunk is backed by a primitive array, which is what `Chunk.fromArray`, `Chunk#map` on such a chunk, and the primitive builders below produce. A chunk made by `++`, `drop` or `slice` is a view over other chunks, and its accessors go through `Chunk#apply`, which returns a boxed element; call `Chunk#materialize` once before the loop to get an array-backed copy. On an array-backed chunk, `Chunk#long` never boxes, whatever the JIT does.

### Build a Chunk With a Primitive Builder

`Chunk.newBuilder[Long]` is generic, so it boxes each price before storing it. `ChunkBuilder.Long` stores raw `long`s, and `sizeHint` sizes its array once instead of doubling it as it grows:

```scala mdoc:silent
def pricesAbove(limitMicros: Long): Chunk[Long] = {
  val builder = new ChunkBuilder.Long
  builder.sizeHint(prices.length)
  var i = 0
  while (i < prices.length) {
    if (prices(i) > limitMicros) builder.addOne(prices(i))
    i += 1
  }
  builder.result()
}
```

The result is a chunk on a primitive array:

```scala mdoc
pricesAbove(188000000L).length
```

On this machine, adding all 10,000 prices costs about 58 bytes per price through `Chunk.newBuilder[Long]` and 16 through a sized `ChunkBuilder.Long`: 8 bytes for the builder's array and 8 for the copy that `ChunkBuilder#result` makes. `ChunkBuilder.Int`, `ChunkBuilder.Double`, `ChunkBuilder.Byte` and the other primitive builders work the same way.

## Replace Option With Maybe for Per-Tick State

State that changes on every tick, such as the last trade seen, is a field written in the hot loop. With `Option`, every write allocates a new `Some`, and because the `Some` is stored in a field, the JIT cannot remove it. On Scala 3, `Maybe` stores a present reference as the reference itself, so writing it allocates nothing:

```scala mdoc:silent
final class LastTrade {
  var tick: Maybe[Tick] = Maybe.absent

  def onTick(t: Tick): Unit = tick = Maybe(t)

  def lastPriceMicros: Long = tick.fold(0L)(_.priceMicros)
}
```

We feed it a batch of ticks:

```scala mdoc:silent
val lastTrade = new LastTrade
ticks.foreach(lastTrade.onTick)
```

The state now holds the last tick's price:

```scala mdoc
lastTrade.lastPriceMicros
```

To compare it with the `Option` version, we write every tick of the batch into each kind of field:

```scala mdoc:compile-only
final class OptionLastTrade { var tick: Option[Tick] = None }

val optionTrade = new OptionLastTrade
val maybeTrade  = new LastTrade

val optionBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) { optionTrade.tick = Some(ticks(i)); i += 1 }
  optionTrade
}

val maybeBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) { maybeTrade.onTick(ticks(i)); i += 1 }
  maybeTrade
}

println(s"Option field: ${optionBytes.toDouble / tickCount} bytes per tick")
println(s"Maybe field:  ${maybeBytes.toDouble / tickCount} bytes per tick")
```

On this machine, writing `Some(tick)` into an `Option[Tick]` field costs 16 bytes per tick, and writing `Maybe(tick)` into a `Maybe[Tick]` field costs nothing.

Absence is the `Absent` singleton, not `null`: `Maybe.absent` returns it, and `Maybe.apply` turns a `null` argument into it. `Maybe.present(null)` instead returns a `Present(null)` wrapper, the one case besides nested `Maybe`s where a flat value allocates.

Three details decide whether `Maybe` saves anything:

- Read it with `Maybe#fold`, `Maybe#getOrElse`, `Maybe#isPresent` and `Maybe#get`, or `Maybe#foreach`. On Scala 3, `case Present(t)` calls `Present.unapply`, which returns an `Option` and so allocates a `Some` per match unless the JIT removes it.
- `Maybe[Long]`, `Maybe[Double]` and other primitive types still box the value, because `Maybe[A]` erases to `Object`. The companion example measures 24 bytes per tick for a `Maybe[Long]` price field against 40 for `Option[Long]`. For a primitive, keep the value in a primitive field, or keep the object it belongs to in a `Maybe`, as `LastTrade` does.
- On Scala 2.13, `Maybe` is a sealed trait, and `Maybe.present` allocates a `MaybeValue.Present` for every present value, so it saves nothing over `Option` there.

The [Maybe reference](../reference/maybe.md) covers the full API.

<details>
  <summary>Runnable version, with the <code>Maybe[Long]</code> price-field measurement: <code>Step6MaybeExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avoidallocation/Step6MaybeExample.scala:show-line-numbers
```

</details>

## Pre-Bind Metric Labels Outside the Loop

Counting ticks per symbol and venue with a telemetry `Counter` is the natural way to export the feed's rate. Passing the labels on every call makes the counter build an `Attributes` value and look it up in its table each time:

```scala mdoc:silent
val meter        = MeterProvider.builder.build().get("feed")
val ticksCounter = meter.counterBuilder("ticks").build()
```

When the label values are known before the loop, `Counter#bind` builds the `Attributes` once and returns a `BoundCounter` that adds straight to the counter's accumulator for those labels:

```scala mdoc:silent
val acmeOnXnas: BoundCounter =
  ticksCounter.bind(Attributes.builder.put("symbol", "ACME").put("venue", "XNAS").build)
```

A call with label pairs and a call on the bound counter write to the same data point, which `Counter#collect` reads back:

```scala mdoc
ticksCounter.add(1L, "symbol" -> "ACME", "venue" -> "XNAS")
acmeOnXnas.add(1L)

ticksCounter.collect() match {
  case MetricData.SumData(points) => points.map(_.value)
  case _                          => Nil
}
```

The difference is what each call allocates. We count every tick of the batch both ways:

```scala mdoc:compile-only
val pairBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) {
    ticksCounter.add(1L, "symbol" -> "ACME", "venue" -> "XNAS")
    i += 1
  }
}

val boundBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) {
    acmeOnXnas.add(1L)
    i += 1
  }
}

println(s"labels per call: ${pairBytes.toDouble / tickCount} bytes per tick")
println(s"bound counter:   ${boundBytes.toDouble / tickCount} bytes per tick")
```

On this machine the label pairs cost about 232 bytes per tick, and the bound counter 0.

The same holds for `Histogram#bind` and `Gauge#bind`, and for the labeled instruments. `Meter#labeledCounter` declares the label names once, so each call passes only the values, but `LabeledCounter#add` still builds a fresh `Attributes` on every call. On a hot path, call `LabeledCounter#bind` once and keep the handle:

```scala mdoc:silent
val labeledTicks = meter.labeledCounter("ticks.labeled", "symbol", "venue")
val acmeHandle   = labeledTicks.bind("ACME", "XNAS")
```

Measured the same way, `LabeledCounter#add` costs about 400 bytes per tick on this machine, more than the label pairs, and the handle costs nothing:

```scala mdoc:compile-only
val labeledBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) {
    labeledTicks.add(1L, "ACME", "XNAS")
    i += 1
  }
}

val handleBytes = Allocation.bytesPerRun {
  var i = 0
  while (i < tickCount) {
    acmeHandle.add(1L)
    i += 1
  }
}

println(s"LabeledCounter#add: ${labeledBytes.toDouble / tickCount} bytes per tick")
println(s"bound handle:       ${handleBytes.toDouble / tickCount} bytes per tick")
```

When label values change per tick but come from a small, known set (the symbols the feed carries), bind each combination once at startup and pick the handle in the loop. The [labeled instruments reference](../reference/telemetry/metrics/labeled-instruments.md) describes `Counter#bind` and the matching `bind` methods of the other instrument kinds.

## Check the Numbers With JMH

`Allocation.bytesPerRun` is enough to compare two versions of a loop, but a JMH benchmark with the GC profiler gives steadier numbers: it warms each benchmark in a fresh JVM and reports `gc.alloc.rate.norm`, the bytes allocated per call. JMH is not on the classpath of the docs or the example builds, so this benchmark runs with scala-cli. JMH rejects classes in the default package, hence the `package` line. Save it as `HotPathBench.scala`:

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-streams:0.0.56
//> using dep dev.zio::zio-blocks-next-maybe:0.0.56
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package hotpath

import org.openjdk.jmh.annotations._
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.maybe.Maybe
import zio.blocks.streams._
import zio.blocks.telemetry._

final case class Tick(symbol: String, priceMicros: Long, size: Int)

final class OptionState { var lastTick: Option[Tick] = None }
final class MaybeState  { var lastTick: Maybe[Tick]  = Maybe.absent }

@State(Scope.Thread)
class HotPathBench {
  val n                   = 10000
  val prices: Array[Long] = Array.tabulate(n)(i => 187000000L + (i % 997) * 1300L)
  val sizes: Array[Int]   = Array.tabulate(n)(i => 100 + i % 50)
  val ticks: Array[Tick]  = Array.tabulate(n)(i => Tick("ACME", prices(i), sizes(i)))
  val chunk: Chunk[Long]  = Chunk.fromArray(prices)

  val optionState = new OptionState
  val maybeState  = new MaybeState

  val ticksCounter = MeterProvider.builder.build().get("feed").counterBuilder("ticks").build()
  val acmeOnXnas   = ticksCounter.bind(Attributes.builder.put("symbol", "ACME").put("venue", "XNAS").build)

  // Streams
  @Benchmark def iteratorPipeline(): Long =
    prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)

  @Benchmark def primitiveStream(): Long =
    Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).runFold(0L)(_ + _).getOrElse(0L)

  @Benchmark def zippedStream(): Long =
    (Stream.fromArray(prices) && Stream.fromArray(sizes)).map { case (p, s) => p * s }.runFold(0L)(_ + _).getOrElse(0L)

  @Benchmark def indexedStream(): Long =
    Stream.range(0, n).map(i => prices(i) * sizes(i)).runFold(0L)(_ + _).getOrElse(0L)

  // Chunk
  @Benchmark def chunkCopy(): Chunk[Long] = Chunk.from(prices)
  @Benchmark def chunkWrap(): Chunk[Long] = Chunk.fromArray(prices)

  @Benchmark def chunkFoldLeft(): Long = chunk.foldLeft(0L)(_ + _)

  @Benchmark def chunkIndexLoop(): Long = {
    var sum = 0L
    var i   = 0
    while (i < chunk.length) { sum += chunk.long(i); i += 1 }
    sum
  }

  @Benchmark def genericBuilder(): Chunk[Long] = {
    val builder = Chunk.newBuilder[Long]
    var i       = 0
    while (i < n) { builder.addOne(prices(i)); i += 1 }
    builder.result()
  }

  @Benchmark def longBuilder(): Chunk[Long] = {
    val builder = new ChunkBuilder.Long
    builder.sizeHint(n)
    var i       = 0
    while (i < n) { builder.addOne(prices(i)); i += 1 }
    builder.result()
  }

  // Maybe
  @Benchmark def optionField(): OptionState = {
    var i = 0
    while (i < n) { optionState.lastTick = Some(ticks(i)); i += 1 }
    optionState
  }

  @Benchmark def maybeField(): MaybeState = {
    var i = 0
    while (i < n) { maybeState.lastTick = Maybe(ticks(i)); i += 1 }
    maybeState
  }

  // Telemetry
  @Benchmark def labelsPerCall(): Unit = {
    var i = 0
    while (i < n) { ticksCounter.add(1L, "symbol" -> "ACME", "venue" -> "XNAS"); i += 1 }
  }

  @Benchmark def boundCounter(): Unit = {
    var i = 0
    while (i < n) { acmeOnXnas.add(1L); i += 1 }
  }
}
```

Run it with the GC profiler (`-prof gc`); the short warm-up and measurement settings keep the run to a few minutes:

```bash
scala-cli --power run --jmh HotPathBench.scala -- -wi 3 -i 3 -f 1 -w 2s -r 2s -prof gc
```

Each benchmark handles 10,000 ticks per call, so dividing `gc.alloc.rate.norm` by 10,000 gives bytes per tick. On this machine, with JDK 25, the results were roughly:

| Hot-path code                                        | Before (bytes per tick) | After (bytes per tick)     |
| ---------------------------------------------------- | ----------------------- | -------------------------- |
| `Iterator` pipeline → `Stream` on the `Long` lane    | 62                      | 0.07                       |
| `&&` zip → `Stream.range` index                      | 55                      | 0.01                       |
| `Chunk.from` copy → `Chunk.fromArray` wrap           | 8                       | 0.003 (32 bytes per batch) |
| `Chunk#foldLeft` → `Chunk#long` index loop           | 24                      | 0                          |
| `Chunk.newBuilder[Long]` → sized `ChunkBuilder.Long` | 58                      | 16                         |
| `Option` field → `Maybe` field                       | 16                      | 0                          |
| Label pairs per call → `BoundCounter`                | 232                     | 0                          |

Throughput moves with allocation but varies more between runs, so compare `gc.alloc.rate.norm` first. A benchmark is the only way to know: the JIT removes some allocations on its own (the plain `chunk(i)` loop above) and keeps others that look free, so measure before and after every change. [How to Tune JSON Encoding and Decoding for Throughput](./tune-json-throughput.md) shows how to run the repository's own benchmarks.

## Running the Examples

The runnable code from this guide lives in three example builds, one per set of modules:

- [`streams-examples/src/main/scala/avoidallocation/`](https://github.com/zio/zio-blocks/tree/main/streams-examples/src/main/scala/avoidallocation): the baseline handler, streams and `Chunk`
- [`schema-examples/src/main/scala/avoidallocation/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/avoidallocation): `Maybe`
- [`telemetry-examples/src/main/scala/avoidallocation/`](https://github.com/zio/zio-blocks/tree/main/telemetry-examples/src/main/scala/avoidallocation): bound counters

Run any file from its folder with `scala-cli run <File>.scala`, or from the build's directory with `sbt "runMain avoidallocation.<ObjectName>"`. Each example prints the bytes it allocates per tick on your machine.

## Going Further

- [Zero-Boxing Optimization](../reference/streams/execution-and-compatibility/zero-boxing.md) explains how lanes work inside the stream interpreter.
- The [Reader reference](../reference/streams/primitives/reader.md) lists every primitive read and its end-of-stream signal.
- The [Chunk reference](../reference/chunk.md) covers the other `Chunk` constructors and builders.
- [Instrument a Program with Logs, Traces and Metrics](./instrument-a-program-with-logs-traces-and-metrics.md) shows where metrics fit in a whole program.
