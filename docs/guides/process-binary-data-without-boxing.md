---
id: process-binary-data-without-boxing
title: "How to Process Binary Data Without Boxing"
description: "Read binary files, buffers and InputStreams as unboxed Long and Double streams, aggregate them, write them back and measure allocation."
keywords:
  - "Binary Data"
  - "Zero Boxing"
  - "Byte Order"
  - "NioStreams"
  - "NioSinks"
  - "Sink.create"
---

In this guide, we process the binary files a vibration sensor on a pump writes: one big-endian `Long` timestamp and one little-endian `Double` acceleration per sample. We read them with ZIO Blocks Streams, so each value travels as a primitive, never as a boxed `java.lang.Long` or `java.lang.Double`. By the end, you will have a pipeline that reads a memory-mapped file or a gzip `InputStream`, transforms and aggregates 100,000 samples without allocating per sample, writes the calibrated samples to a new file, and measures what each step allocates.

We start with a `ByteBuffer` and build up to files, custom sinks and writers:

- [Reading Values From a ByteBuffer](#reading-values-from-a-bytebuffer)
- [Matching the File's Byte Order](#matching-the-files-byte-order)
- [Mapping a File Instead of Copying It](#mapping-a-file-instead-of-copying-it)
- [Transforming Values on the Primitive Lane](#transforming-values-on-the-primitive-lane)
- [Aggregating With the Built-in Folds](#aggregating-with-the-built-in-folds)
- [Writing a Custom Primitive Sink](#writing-a-custom-primitive-sink)
- [Ending the Loop Without Losing Values](#ending-the-loop-without-losing-values)
- [Decoding Values From an InputStream](#decoding-values-from-an-inputstream)
- [Writing Values Back Out](#writing-values-back-out)
- [Measuring Bytes per Element](#measuring-bytes-per-element)

## The Problem

Binary data arrives as bytes: a file of sensor samples, a market-data capture, a network packet. To compute anything we decode the bytes into numbers, and the usual Scala tools store each number as an object. A `Vector[Double]`, an `Iterator[Long]` or an `Iterator#foldLeft` over them boxes every element, so a file of 100,000 samples becomes 100,000 small objects for the garbage collector. The JDK's `DataInputStream` also reads only big-endian values, so a little-endian file needs a manual byte swap.

For example, reading the sensor's samples and computing their root mean square with the standard library looks like this:

```scala mdoc:compile-only
import java.io.{BufferedInputStream, DataInputStream, EOFException}
import java.nio.file.{Files, Path}
import scala.collection.immutable.VectorBuilder

def readSamples(file: Path): Vector[Double] = {
  val in      = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))
  val samples = new VectorBuilder[Double]
  try {
    while (true) {
      // DataInputStream is big-endian only, so swap the bytes of each little-endian value
      samples += java.lang.Double.longBitsToDouble(java.lang.Long.reverseBytes(in.readLong()))
    }
  } catch { case _: EOFException => () }
  finally in.close()
  samples.result()
}

def rms(samples: Vector[Double]): Double =
  math.sqrt(samples.map(g => g * g).sum / samples.size)
```

This works, but it holds the whole file in memory as boxed `Double`s, `Vector#map` boxes a second copy, and the end of the file is an exception. On a busy service that decodes files or packets all day, those allocations turn into garbage collection pauses.

In this guide, we read the same bytes as a stream on a primitive *lane*: ZIO Blocks Streams tags a stream of `Long` or `Double` values with its element type and pulls each value as a JVM primitive, from the source through `Stream#map` and `Stream#filter` to the sink.

## Prerequisites

Add the streams module to your `build.sbt`. With ZIO Blocks @VERSION@, the artifact name depends on the Scala version:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"

// Scala 3.9 and newer, which also needs the -experimental compiler flag
libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
scalacOptions += "-experimental"
```

The streams module needs JDK 21 or newer at runtime. `NioStreams`, `NioReaders`, `NioSinks`, `NioWriters`, `Sink.create` and the blocking terminals such as `Stream#runFold` exist only on the JVM.

This guide assumes you have built and run a [`Stream`](../reference/streams/core/stream.md) before, as in the tutorial [Your First Streaming Pipeline](./your-first-streaming-pipeline.md), and that you know `java.nio.ByteBuffer`. Every terminal returns an `Either`, with typed failures on the `Left`.

The snippets on this page share these imports:

```scala mdoc:silent
import zio.blocks.streams._

import java.nio.channels.FileChannel
import java.nio.file.{Files, Path}
import java.nio.{ByteBuffer, ByteOrder}
```

## The Sensor Files

The sensor samples at 1 kHz. For every sample it appends the time in milliseconds since the epoch to `timestamps.bin`, as a big-endian `Long`, and the acceleration in g to `samples.bin`, as a little-endian `Double`. When the acceleration is out of range, it writes `Double.MaxValue` instead of a reading. We write 100 seconds of such data to a temporary directory, with three overrange readings, so every snippet runs on any machine:

```scala mdoc:silent
val SampleCount = 100000
val StartMillis = 1767225600000L // 2026-01-01T00:00:00Z
val Overrange   = Double.MaxValue

def sample(i: Int): Double =
  if (i > 0 && i % 25000 == 0) Overrange
  else 0.2 * math.sin(2 * math.Pi * 50 * i / 1000.0) + 0.05 * math.sin(2 * math.Pi * 120 * i / 1000.0)

val sensorDir: Path      = Files.createTempDirectory("pump-sensor")
val timestampsFile: Path = sensorDir.resolve("timestamps.bin")
val samplesFile: Path    = sensorDir.resolve("samples.bin")

locally {
  val times = ByteBuffer.allocate(SampleCount * 8) // big-endian, the ByteBuffer default
  val accel = ByteBuffer.allocate(SampleCount * 8).order(ByteOrder.LITTLE_ENDIAN)
  (0 until SampleCount).foreach { i => times.putLong(StartMillis + i); accel.putDouble(sample(i)) }
  Files.write(timestampsFile, times.array())
  Files.write(samplesFile, accel.array())
}
```

## Reading Values From a ByteBuffer

`NioStreams` turns a `ByteBuffer` into a stream of primitives: `NioStreams.fromByteBufferLong` reads 8 bytes per `Long`, and `NioStreams.fromByteBufferInt`, `NioStreams.fromByteBufferFloat` and `NioStreams.fromByteBufferDouble` do the same for the other numeric types. `NioStreams.fromByteBuffer` reads plain bytes. When the file fits in memory, we read it into a buffer and stream the timestamps:

```scala mdoc:silent
val timestampBytes: ByteBuffer = ByteBuffer.wrap(Files.readAllBytes(timestampsFile))
```

`Stream#runFold` with a `Long` start value counts the timestamps without boxing, and `Stream#head` and `Stream#last` return the first and last one:

```scala mdoc
NioStreams.fromByteBufferLong(timestampBytes.duplicate()).runFold(0L)((n, _) => n + 1L)
NioStreams.fromByteBufferLong(timestampBytes.duplicate()).head
NioStreams.fromByteBufferLong(timestampBytes.duplicate()).last
```

The stream reads from the buffer's position to its limit and moves the position as it goes, so a second stream over the same buffer finds nothing left to read. Each stream above therefore reads its own `ByteBuffer#duplicate`, which shares the bytes but has its own position. A trailing group of fewer than 8 bytes is not a whole `Long` and is not emitted.

`NioReaders` has the same five `ByteBuffer` factories, for code that pulls values by hand from a [`Reader`](../reference/streams/primitives/reader.md) instead of running a stream.

## Matching the File's Byte Order

A `ByteBuffer` reads multi-byte values in its own byte order, and every new buffer starts big-endian. The timestamps are big-endian, so they decode correctly. The samples are little-endian:

```scala mdoc:silent
val sampleBytes: Array[Byte] = Files.readAllBytes(samplesFile)
```

Read with the default order, they decode to nonsense:

```scala mdoc
println(NioStreams.fromByteBufferDouble(ByteBuffer.wrap(sampleBytes)).take(3).runCollect)
```

Setting the buffer's order with `ByteBuffer#order` before the stream reads it fixes the values:

```scala mdoc
println(NioStreams.fromByteBufferDouble(ByteBuffer.wrap(sampleBytes).order(ByteOrder.LITTLE_ENDIAN)).take(3).runCollect)
```

:::warning[`ByteBuffer#duplicate` and `ByteBuffer#slice` reset the byte order]
`ByteBuffer#duplicate` and `ByteBuffer#slice` return a big-endian buffer, whatever the original's order. Call `ByteBuffer#order` on the copy every time: `buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)`. A `MappedByteBuffer` from `FileChannel#map` also starts big-endian.
:::

The same applies to writing: the typed sinks and writers later in this guide write in the target buffer's order.

## Mapping a File Instead of Copying It

`Files.readAllBytes` copies the whole file onto the heap. `FileChannel#map` maps the file into memory instead, so the operating system pages it in as the stream reads it, and nothing is copied onto the heap. We open the channel with `Stream.fromAcquireRelease`, which closes it when the run ends, whether the run finishes, fails or stops early:

```scala mdoc:silent
def samples(file: Path): Stream[Nothing, Double] =
  Stream.fromAcquireRelease(FileChannel.open(file)) { ch =>
    val mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()).order(ByteOrder.LITTLE_ENDIAN)
    NioStreams.fromByteBufferDouble(mapped)
  }

def timestamps(file: Path): Stream[Nothing, Long] =
  Stream.fromAcquireRelease(FileChannel.open(file)) { ch =>
    NioStreams.fromByteBufferLong(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
  }
```

Each run opens the file again and maps it afresh, so the stream can run any number of times:

```scala mdoc
samples(samplesFile).runFold(0L)((n, _) => n + 1L)
println(samples(samplesFile).take(3).runCollect)
```

The mapping stays valid after the channel closes, until the garbage collector frees the buffer. One mapping covers at most `Int.MaxValue` bytes (2 GiB), so a larger file needs several mappings, which `Stream#flatMap` can join into one stream; it keeps the inner streams' `Double` lane.

The guide [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) covers `Stream.fromAcquireRelease` and the other ways to close a resource.

## Transforming Values on the Primitive Lane

`Stream#map`, `Stream#filter`, `Stream#scan`, `Stream#take`, `Stream#drop`, `Stream#takeWhile` and `Stream#tapEach` keep a primitive stream on its lane, as long as the function returns a primitive. To convert the first second of samples from g to m/s², drop the overrange readings and track the running peak:

```scala mdoc:silent
val StandardGravity = 9.80665 // m/s² per g

val firstSecond: Stream[Nothing, Double] =
  samples(samplesFile)
    .take(1000)
    .filter(_ != Overrange)
    .map(_ * StandardGravity)

val peak: Stream[Nothing, Double] =
  firstSecond.scan(0.0)((peak, a) => math.max(peak, math.abs(a)))
```

The stream carries each sample as a `Double` from the mapped file to the terminal:

```scala mdoc
println(firstSecond.take(3).runCollect)
peak.last
```

A function that returns an object moves the stream to the reference lane, and from then on every element is an object. A `map(g => (g, g))` builds a tuple per sample, which measured 64 bytes per sample on our machine. Keep values primitive for as long as the pipeline runs, and build objects only for the results.

## Aggregating With the Built-in Folds

A *sink* consumes a stream and produces one result. `Stream#runFold` and `Sink.foldLeft` with a `Long` or `Int` start value fold a primitive stream without boxing either the element or the accumulator. To count the overrange readings and find the first and last timestamp:

```scala mdoc
samples(samplesFile).filter(_ == Overrange).runFold(0L)((n, _) => n + 1L)

val first = timestamps(timestampsFile).run(Sink.foldLeft(Long.MaxValue)((lo: Long, t: Long) => math.min(lo, t)))
val last  = timestamps(timestampsFile).run(Sink.foldLeft(Long.MinValue)((hi: Long, t: Long) => math.max(hi, t)))
```

:::warning[Some built-in sinks box the accumulator]
A known bug in 0.0.56: `Sink.sumLong`, `Sink.sumInt`, `Sink.sumFloat`, `Sink.sumDouble`, `Sink.count` and `Stream#count` read the elements unboxed, but box their accumulator for every element, which measured 24 to 48 bytes per element on our machine. `Stream#runFold` and `Sink.foldLeft` with a `Double` start value box the same way when the stream is on the `Long` or `Double` lane. Until the fix ships, count and sum with a `Long` start value, as above, and fold `Double` values with a custom sink, as in the next section.
:::

## Writing a Custom Primitive Sink

When no built-in sink computes what we need, `Sink.create` builds one from a function that pulls from the stream's `Reader.SyncReader`. A custom sink also helps when we want several results in one pass: a fold would need a tuple accumulator, which allocates per element, while a sink keeps each result in its own primitive `var`. This sink computes the vibration statistics of the whole recording in one pass:

```scala mdoc:silent
final case class VibrationStats(samples: Long, overrange: Long, min: Double, max: Double, rms: Double)

val vibrationStats: Sink[Nothing, Double, VibrationStats] =
  Sink.create[Nothing, Double, VibrationStats] { reader =>
    val one       = new Array[Double](1)
    var samples   = 0L
    var overrange = 0L
    var min       = Double.PositiveInfinity
    var max       = Double.NegativeInfinity
    var sumSq     = 0.0
    while (reader.readDoubles(one, 0, 1) > 0) {
      val g = one(0)
      if (g == Overrange) overrange += 1
      else {
        samples += 1
        if (g < min) min = g
        if (g > max) max = g
        sumSq += g * g
      }
    }
    VibrationStats(samples, overrange, min, max, math.sqrt(sumSq / samples))
  }
```

`Reader#readDoubles` copies the next sample into `one` and returns `1`, or returns `-1` once the stream has ended. The one-element array is allocated once per run, outside the loop. Running the sink over the mapped file allocates nothing per sample, only the result:

```scala mdoc
samples(samplesFile).run(vibrationStats)
```

The `var`s are local to one run, so the same `vibrationStats` value can run any number of times. A sink built with `Sink.create` exists only on the JVM, and at an asynchronous terminal its callback blocks the thread that drives the run; `Sink.createBoth` takes a synchronous and an asynchronous callback for code that also runs on Scala.js or must not block at asynchronous terminals. The [`Sink`](../reference/streams/core/sink.md) reference covers both.

## Ending the Loop Without Losing Values

A `Reader` also has a scalar read for each type, such as `Reader#readDouble`, which returns the next value or a *sentinel*, a value we pick to mean "the stream has ended". On the `Long` and `Double` lanes every value can be real data, so any sentinel can collide with an element. The sensor writes `Double.MaxValue` for an overrange reading, so a sink that uses it as the sentinel stops at the first overrange sample, without an error. To compare the two loops, we count the samples both ways:

```scala mdoc:silent
val countWithSentinel: Sink[Nothing, Double, Long] =
  Sink.create[Nothing, Double, Long] { reader =>
    var n = 0L
    var g = reader.readDouble(Double.MaxValue)
    while (g != Double.MaxValue) { n += 1; g = reader.readDouble(Double.MaxValue) }
    n
  }

val countWithCount: Sink[Nothing, Double, Long] =
  Sink.create[Nothing, Double, Long] { reader =>
    val one = new Array[Double](1)
    var n   = 0L
    while (reader.readDoubles(one, 0, 1) > 0) n += 1
    n
  }
```

The sentinel loop silently drops three quarters of the recording:

```scala mdoc
samples(samplesFile).run(countWithSentinel)
samples(samplesFile).run(countWithCount)
```

On the `Long` and `Double` lanes, end the loop on the count that `Reader#readLongs` or `Reader#readDoubles` returns, as `vibrationStats` does. The count is reported apart from the data, so no value is lost, and the one-element array, allocated once per run, keeps the loop free of per-element allocation. Do not add a check against the sentinel inside the loop to work around a collision. The other lanes have no such risk: `Reader#readInt` returns a `Long`, `Reader#readFloat` a `Double`, `Reader#readShort` an `Int` and `Reader#readByte` an `Int` from `0` to `255`, so their usual sentinels (`Long.MinValue`, `Double.MaxValue`, `Int.MinValue` and `-1`) lie outside every element's range. The library's own sinks, `NioSinks` included, use the count on the `Long` and `Double` lanes and these widened sentinels on the others, so they keep every value. The [zero-boxing reference](../reference/streams/execution-and-compatibility/zero-boxing.md#eof-signalling-per-lane) lists the end-of-stream signal of every lane.

## Decoding Values From an InputStream

Not every source is a file we can map. A gzip file, an HTTP response body or a socket arrives as a `java.io.InputStream`, and `Stream.fromInputStream` turns it into a stream of bytes, not of `Double`s. To decode it, we read the input in blocks into one reusable `ByteBuffer` with `Stream.unfold`, and turn each block into a `Double` stream with `Stream#flatMap`:

```scala mdoc:silent
import java.io.InputStream

def samplesFrom(in: InputStream, order: ByteOrder): Stream[Nothing, Double] = {
  val block = ByteBuffer.allocate(64 * 1024).order(order)
  Stream
    .unfold(in) { in =>
      val n = in.readNBytes(block.array(), 0, block.capacity())
      if (n <= 0) None
      else {
        block.clear()
        block.limit(n - n % 8)
        Some((block, in))
      }
    }
    .flatMap(b => NioStreams.fromByteBufferDouble(b))
}
```

`InputStream#readNBytes` fills the whole block unless the input ends, so only the last block can be shorter, and `block.limit(n - n % 8)` drops an incomplete value at the very end. `Stream#flatMap` reads each block to its end before `Stream.unfold` refills it, which is what makes one buffer per run safe. The decoded stream stays on the `Double` lane, so the same `vibrationStats` sink reads it unboxed. We write a gzip copy of the samples next to the original:

```scala mdoc:silent
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

val samplesGz: Path = sensorDir.resolve("samples.bin.gz")
locally {
  val out = new GZIPOutputStream(Files.newOutputStream(samplesGz))
  try out.write(sampleBytes)
  finally out.close()
}
```

Decoded through `GZIPInputStream`, the copy gives the same statistics as the mapped file:

```scala mdoc
Stream
  .fromAcquireRelease(new GZIPInputStream(Files.newInputStream(samplesGz))) { in =>
    samplesFrom(in, ByteOrder.LITTLE_ENDIAN)
  }
  .run(vibrationStats)
```

`Stream.fromAcquireRelease` closes the `GZIPInputStream`, and with it the file, when the run ends. An `IOException` from `InputStream#readNBytes` is not a typed error here: it ends the run as a thrown exception. A socket works the same way through its `InputStream`, or through `java.nio.channels.Channels.newInputStream` for a channel.

## Writing Values Back Out

The write side mirrors the read side. This section writes the calibrated samples with a typed sink, records values that a driver pushes one at a time with typed writers, and lists who closes which channel.

### Into a Mapped File

`NioSinks.fromByteBufferDouble` writes each `Double` of a stream into a `ByteBuffer`, 8 bytes per element in the buffer's byte order; `NioSinks.fromByteBufferLong`, `NioSinks.fromByteBufferInt`, `NioSinks.fromByteBufferFloat` and `NioSinks.fromByteBuffer` cover the other types. The buffer must have room for every element, or the sink throws `BufferOverflowException`. We know the output size, so we map an output file of that size with `FileChannel.MapMode.READ_WRITE` and let the sink fill it. The calibrated file is big-endian, the default order of the mapping:

```scala mdoc:silent
import java.nio.file.StandardOpenOption.{CREATE, READ, TRUNCATE_EXISTING, WRITE}
import scala.util.Using

val calibratedFile: Path = sensorDir.resolve("calibrated.bin")
```

The sink returns `Right(())` once every sample is in the mapping:

```scala mdoc
Using.resource(FileChannel.open(calibratedFile, CREATE, TRUNCATE_EXISTING, READ, WRITE)) { ch =>
  val target = ch.map(FileChannel.MapMode.READ_WRITE, 0, SampleCount * 8L)
  val result = samples(samplesFile)
    .map(g => if (g == Overrange) g else g * StandardGravity)
    .run(NioSinks.fromByteBufferDouble(target))
  target.force()
  result
}
```

`MappedByteBuffer#force` writes the changes to the storage device. The sink ends its loop on the count from `Reader#readDoubles`, so the `Double.MaxValue` markers are written like any other value. Reading the output back finds all three:

```scala mdoc
Stream
  .fromAcquireRelease(FileChannel.open(calibratedFile)) { ch =>
    NioStreams.fromByteBufferDouble(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
  }
  .filter(_ == Double.MaxValue)
  .runFold(0L)((n, _) => n + 1L)
```

### From Pushed Values With Writers

A sink pulls from a stream. When values are pushed to us instead, one call per sample from a device driver, a [`Writer`](../reference/streams/primitives/writer.md) accepts them: `NioWriters.fromByteBufferLong` and `NioWriters.fromByteBufferDouble` return a `Writer[Long]` and a `Writer[Double]` whose `Writer#writeLong` and `Writer#writeDouble` take the primitive unboxed. A `ByteBuffer` writer returns `false` and closes itself once its buffer is full, so this recorder writes each full block to its channel and starts a new writer on the emptied buffer:

```scala mdoc:silent
import java.nio.channels.WritableByteChannel
import zio.blocks.streams.io.Writer

final class Recorder(times: WritableByteChannel, samples: WritableByteChannel, blockSize: Int) {
  private val timeBlock   = ByteBuffer.allocate(blockSize * 8)
  private val sampleBlock = ByteBuffer.allocate(blockSize * 8).order(ByteOrder.LITTLE_ENDIAN)
  private var timeOut: Writer[Long]     = NioWriters.fromByteBufferLong(timeBlock)
  private var sampleOut: Writer[Double] = NioWriters.fromByteBufferDouble(sampleBlock)

  def onSample(millis: Long, g: Double): Unit = {
    if (!timeOut.writeLong(millis)) { flush(); timeOut.writeLong(millis) }
    if (!sampleOut.writeDouble(g)) { flush(); sampleOut.writeDouble(g) }
  }

  def flush(): Unit = {
    drain(timeBlock, times)
    drain(sampleBlock, samples)
    timeOut = NioWriters.fromByteBufferLong(timeBlock)
    sampleOut = NioWriters.fromByteBufferDouble(sampleBlock)
  }

  private def drain(block: ByteBuffer, ch: WritableByteChannel): Unit = {
    block.flip()
    while (block.hasRemaining) ch.write(block)
    block.clear()
  }
}
```

The recorder does not own the channels, so the caller opens and closes them. We record into two new files:

```scala mdoc:silent
val recordedTimes: Path   = sensorDir.resolve("recorded-times.bin")
val recordedSamples: Path = sensorDir.resolve("recorded-samples.bin")
```

Recording 10 seconds of samples and reading them back:

```scala mdoc
Using.resources(
  FileChannel.open(recordedTimes, CREATE, TRUNCATE_EXISTING, WRITE),
  FileChannel.open(recordedSamples, CREATE, TRUNCATE_EXISTING, WRITE)
) { (timesCh, samplesCh) =>
  val recorder = new Recorder(timesCh, samplesCh, blockSize = 4096)
  (0 until 10000).foreach(i => recorder.onSample(StartMillis + i, sample(i)))
  recorder.flush()
}

samples(recordedSamples).runFold(0L)((n, _) => n + 1L)
```

### Who Closes the Channel

The NIO constructors differ in who closes the channel they wrap, and a channel nobody closes leaks a file handle:

| Constructor                        | Closes the channel                               |
|------------------------------------|--------------------------------------------------|
| `NioStreams.fromChannel`           | Yes, when the run ends                           |
| `NioStreams.fromChannelUnmanaged`  | No, the caller closes it                         |
| `NioReaders.fromChannel`           | Yes, when the reader closes                      |
| `NioSinks.fromChannel`             | No, the caller closes it                         |
| `NioWriters.fromChannel`           | Yes, when the writer closes                      |
| `Stream.fromAcquireRelease`        | Yes, the release function runs when the run ends |

All five NIO channel constructors work with bytes, not with `Long` or `Double` values. A known bug in 0.0.56: after a failed write, closing a `NioWriters.fromChannel` writer, or one from `Writer.fromOutputStream` or `Writer.fromWriter`, does not close the wrapped channel or stream, so close it yourself in that case.

## Measuring Bytes per Element

The only reliable way to know that a pipeline stays on its lane is to measure what it allocates. The JVM counts the bytes each thread allocates, and `com.sun.management.ThreadMXBean` exposes that counter. The block must run a few thousand times first, so that the JIT compiler has compiled it and removed what it can:

```scala mdoc:silent
import java.lang.management.ManagementFactory

object Allocation {
  private val threads =
    ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]

  @volatile private var blackhole: Int = 0

  def bytesPerElement(elements: Int)(body: => Any): Double = {
    var i = 0
    while (i < 1000) { blackhole ^= body.hashCode; i += 1 } // warm up the JIT
    val runs   = 100
    val before = threads.getCurrentThreadAllocatedBytes
    i = 0
    while (i < runs) { blackhole ^= body.hashCode; i += 1 }
    (threads.getCurrentThreadAllocatedBytes - before).toDouble / runs / elements
  }
}
```

We measure one step at a time, such as the statistics over the mapped file:

```scala mdoc:compile-only
println(Allocation.bytesPerElement(SampleCount)(samples(samplesFile).run(vibrationStats)))
```

The companion example measures every step of this guide over the 100,000 samples. On our machine, with JDK 25, the results were:

| Step                                                                        | Bytes per sample |
|-----------------------------------------------------------------------------|------------------|
| `ByteBuffer`, counted with `runFold(0L)`                                    | 0.00             |
| `Stream#filter`, `Stream#map` and `Stream#scan`, counted with `runFold(0L)` | 0.02             |
| Mapped file into `vibrationStats`                                           | 0.02             |
| Gzip `InputStream` into `vibrationStats`                                    | 0.72             |
| `Stream#map` into `NioSinks.fromByteBufferDouble`                           | 0.00             |
| Mapped file into a mapped file                                              | 0.02             |
| `Recorder`, blocks of 4,096 samples                                         | 0.67             |
| `Stream#map` to a tuple, leaving the lane                                   | 63.97            |
| `Sink.sumDouble` (a known bug in 0.0.56)                                    | 24.00            |
| `runFold(0.0)` (a known bug in 0.0.56)                                      | 48.00            |

The small non-zero figures are per-run costs spread over 100,000 samples: the stream's own objects, the result, and in the gzip and recorder rows the 64 KB of buffers each run allocates. None of them grows with the number of samples. Treat the figures as rough sizes, not guarantees: they depend on the JDK, the JIT and the pipeline. For a JMH benchmark with `-prof gc`, which reports the same figure as `gc.alloc.rate.norm`, see the guide How to Avoid Allocation on Hot Paths.

## Putting It Together

The complete example writes the sensor files, reports the recording's time span from the timestamps, computes the vibration statistics from the mapped samples and checks that the gzip copy gives the same ones, writes the calibrated samples to a mapped output file, and prints what the statistics and the calibration allocate per sample. It takes the sources, the decoder and the statistics sink from `Pump.scala`, the sensor files from `SensorFiles.scala` and the measuring helper from `Allocation.scala`, which hold the same code as the sections above:

```scala mdoc:embed:streams-examples/src/main/scala/processbinarydata/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `streams-examples` project. Clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/streams-examples
```

Each example writes its own copy of the sensor files to a temporary directory and deletes it at the end. The code the examples share lives in `SensorFiles.scala` (the sensor files), `Allocation.scala` (the measuring helper), `Pump.scala` (the mapped-file sources, the `InputStream` decoder and the statistics sink) and `Recorder.scala`, all in `src/main/scala/processbinarydata/`.

Each section has its own example, run from the `streams-examples` directory:

| Section                                                                            | Run                                                         |
|------------------------------------------------------------------------------------|-------------------------------------------------------------|
| [Reading Values From a ByteBuffer](#reading-values-from-a-bytebuffer)              | `sbt "runMain processbinarydata.Step1ReadBuffer"`           |
| [Matching the File's Byte Order](#matching-the-files-byte-order)                   | `sbt "runMain processbinarydata.Step2ByteOrder"`            |
| [Mapping a File Instead of Copying It](#mapping-a-file-instead-of-copying-it)      | `sbt "runMain processbinarydata.Step3MapFile"`              |
| [Transforming Values on the Primitive Lane](#transforming-values-on-the-primitive-lane) | `sbt "runMain processbinarydata.Step4Transform"`           |
| [Aggregating With the Built-in Folds](#aggregating-with-the-built-in-folds)        | `sbt "runMain processbinarydata.Step5Aggregate"`            |
| [Writing a Custom Primitive Sink](#writing-a-custom-primitive-sink)                | `sbt "runMain processbinarydata.Step6CustomSink"`           |
| [Ending the Loop Without Losing Values](#ending-the-loop-without-losing-values)     | `sbt "runMain processbinarydata.Step7EndOfStream"`          |
| [Decoding Values From an InputStream](#decoding-values-from-an-inputstream)        | `sbt "runMain processbinarydata.Step8DecodeInputStream"`    |
| [Writing Values Back Out](#writing-values-back-out)                                | `sbt "runMain processbinarydata.Step9WriteBack"`            |
| [Measuring Bytes per Element](#measuring-bytes-per-element)                        | `sbt "runMain processbinarydata.Step10Measure"`             |
| [Putting It Together](#putting-it-together)                                        | `sbt "runMain processbinarydata.CompleteExample"`           |

`Step5Aggregate` also measures `Stream#count` and `Sink.sumLong` against `runFold(0L)`, and `Step10Measure` prints the table in [Measuring Bytes per Element](#measuring-bytes-per-element).

## Going Further

- The [zero-boxing reference](../reference/streams/execution-and-compatibility/zero-boxing.md) explains how a stream picks its lane and where boxing can still happen, including the asynchronous terminals.
- The [`Reader`](../reference/streams/primitives/reader.md) and [`Writer`](../reference/streams/primitives/writer.md) references list every primitive read and write, including the bulk `Reader#readLongs` with a larger array.
- [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) covers managed and unmanaged handles, sockets and `Scope` resources.
- The guide How to Avoid Allocation on Hot Paths applies the same measuring to `Chunk`, `Option` and telemetry code, and verifies the figures with JMH.
- [Coming from fs2, ZIO Streams or Ox](./streaming-library-migration.md) maps the stream operators of those libraries onto ZIO Blocks Streams.

```scala mdoc:invisible
locally {
  val files = Files.list(sensorDir)
  try files.forEach(p => Files.delete(p))
  finally files.close()
  Files.delete(sensorDir)
}
```
