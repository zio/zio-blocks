/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams.bench

import java.util.concurrent.{CountDownLatch, ExecutorService, Executors, TimeUnit}
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized
import zio.blocks.async.*
import zio.blocks.chunk.Chunk
import zio.blocks.streams.{Sink, Stream}
import zio.blocks.streams.io.Reader

// ZIO Blocks-only runtime and reader diagnostics, excluded from cross-provider rankings.

private[bench] final class Handshake[A] {
  private val completer = new Completer[A]
  private val polled    = new CountDownLatch(1)
  val async: Async[A]   = new Pollable[A] {
    def poll(onComplete: Runnable): Async[A] = {
      polled.countDown()
      completer.poll(onComplete)
    }
  }
  def awaitPoll(): Unit =
    if (!polled.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("pending operation was not polled")
  def succeed(value: A): Unit = completer.succeed(value)
}

private[bench] trait WorkerHandshake {
  protected var pool: ExecutorService = uninitialized

  @Setup(Level.Trial) def setupWorker(): Unit       = pool = Executors.newFixedThreadPool(16)
  @TearDown(Level.Trial) def teardownWorker(): Unit = {
    pool.shutdown()
    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("worker did not terminate")
  }

  protected final def pending[A](value: A): Async[A] = {
    val handshake = new Handshake[A]
    pool.execute { () => handshake.awaitPoll(); handshake.succeed(value) }
    handshake.async
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncStateAllocationBench {
  @Param(Array("0", "1", "1000")) var N: Int = uninitialized
  private var byteSource: Array[Byte]        = uninitialized
  private var byteDestination: Array[Byte]   = uninitialized

  @Setup(Level.Trial) def setup(): Unit = {
    byteSource = Array.tabulate(N)(_.toByte)
    byteDestination = new Array[Byte](N)
  }

  private def sum(stream: Stream[Nothing, Int]): Long = AsyncParity.fold(stream)

  @Benchmark def dynamicAllSync(): Long = sum(Stream.range(0, N).flatMap(Stream.succeed))
  @Benchmark def liftedSync(): Long     = sum(Stream.range(0, N))
  @Benchmark def nativeReady(): Long    = sum(AsyncParity.source(0, N))
  @Benchmark def readyRead(): Int       =
    AsyncParity.source(0, N).useReaderAsync(_.readInt(-1L).map(_.toInt)).block
  @Benchmark def readyMap(): Long =
    sum(AsyncParity.source(0, N).mapAsync(value => Async.succeed(value + 1)))
  @Benchmark def readyFilter(): Long =
    sum(AsyncParity.source(0, N).filterAsync(value => Async.succeed((value & 1) == 0)))
  @Benchmark def bulkChunk(): Int      = AsyncParity.source(0, N).runCollectAsync.block.toOption.get.length
  @Benchmark def directByteBulk(): Int =
    Stream.fromArray(byteSource).useReaderAsync(_.readBytes(byteDestination, 0, N)).block
  @Benchmark def syncAsyncTerminal(): Long = sum(Stream.range(0, N))

  def runOnce(id: String): Long = id match {
    case "dynamic-all-sync"    => dynamicAllSync()
    case "lifted-sync"         => liftedSync()
    case "native-ready"        => nativeReady()
    case "ready-read"          => readyRead().toLong
    case "ready-map"           => readyMap()
    case "ready-filter"        => readyFilter()
    case "bulk-chunk"          => bulkChunk().toLong
    case "direct-byte-bulk"    => directByteBulk().toLong
    case "sync-async-terminal" => syncAsyncTerminal()
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncResumeBench {
  private var handshake: Handshake[Int]                     = uninitialized
  private var running: Async.Running[Either[Nothing, Long]] = uninitialized

  @Setup(Level.Invocation) def setup(): Unit = {
    handshake = new Handshake[Int]
    running = AsyncParity
      .source(0, 1)
      .mapAsync(_ => handshake.async)
      .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      .start
    handshake.awaitPoll()
  }

  @Benchmark def isolatedResume(): Long = {
    handshake.succeed(1)
    running.block match {
      case Right(value) => value
      case Left(_)      => throw new AssertionError("impossible Nothing failure")
    }
  }

  def runOnce(): Long = {
    setup()
    isolatedResume()
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncCooperativeYieldBench {
  @Param(Array("2049")) var N: Int = uninitialized

  @Benchmark def cooperativeYield(): Long =
    AsyncParity.fold(AsyncParity.source(0, N).mapAsync(Async.succeed))

  def runOnce(): Long = cooperativeYield()
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Benchmark)
class StreamAsyncSuspensionBench extends WorkerHandshake {
  @Param(Array("1000")) var N: Int = uninitialized

  private def chunkReader(): Reader.AsyncReader[Int] = new Reader.AsyncReader[Int] {
    private var done                          = false
    def close(): Async[Unit]                  = Async.succeed { done = true }
    def isClosed: Async[Boolean]              = Async.succeed(done)
    def readable(): Async[Boolean]            = Async.succeed(!done)
    def read[A >: Int](sentinel: A): Async[A] =
      if (done) Async.succeed(sentinel)
      else { done = true; pending(0.asInstanceOf[A]) }
    override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] =
      if (done || n <= 0) Async.succeed(Chunk.empty)
      else {
        done = true
        val length = math.min(n, N)
        val array  = new Array[Int](length)
        var i      = 0
        while (i < length) {
          array(i) = i
          i += 1
        }
        pending(Chunk.fromArray(array).asInstanceOf[Chunk[A]])
      }
  }

  @Benchmark def firstSuspension(): Long =
    AsyncParity.fold(Stream.succeed(1).mapAsync(pending))
  @Benchmark def perElement(): Long =
    AsyncParity.fold(AsyncParity.source(0, N).mapAsync(pending))
  @Benchmark def perChunk(): Int =
    chunkReader().readUpToN[Int](N).block.length
  @Benchmark def sequentialCallback(): Long =
    AsyncParity.fold(Stream.range(0, N).mapAsync(value => pending(value + 1)))
  @Benchmark def boundedParallelCallback(): Long =
    AsyncParity.fold(Stream.range(0, N).mapParAsync(8)(value => pending(value + 1)))
  @Benchmark def liftedAfterAsync(): Long =
    AsyncParity.fold(AsyncParity.source(0, N).mapAsync(Async.succeed).map(_ + 1))
  @Benchmark def dynamicFlatMap(): Long =
    AsyncParity.fold(AsyncParity.source(0, N).flatMap(value => Stream.unwrap(Async.succeed(Stream.succeed(value)))))
  @Benchmark def syncReadyCallback(): Long =
    AsyncParity.fold(Stream.range(0, N).mapAsync(Async.succeed))
  @Benchmark def asyncReadyCallback(): Long =
    AsyncParity.fold(AsyncParity.source(0, N).mapAsync(Async.succeed))
  @Benchmark def asyncAcquireFinalize(): Long =
    AsyncParity.fold(
      Stream.fromAcquireReleaseAsync(Async.succeed(1), (_: Int) => Async.succeed(()))(_ => AsyncParity.source(0, N))
    )

  def runOnce(id: String): Long = id match {
    case "first-suspension"          => firstSuspension()
    case "per-element"               => perElement()
    case "per-chunk"                 => perChunk().toLong
    case "sequential-callback"       => sequentialCallback()
    case "bounded-parallel-callback" => boundedParallelCallback()
    case "lifted-after-async"        => liftedAfterAsync()
    case "dynamic-flat-map"          => dynamicFlatMap()
    case "sync-ready-callback"       => syncReadyCallback()
    case "async-ready-callback"      => asyncReadyCallback()
    case "async-acquire-finalize"    => asyncAcquireFinalize()
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncPrimitiveTerminalBench {
  @Param(Array("0", "1", "1000")) var N: Int = uninitialized
  private def base                           = AsyncParity.source(0, N)

  @Benchmark def intReader(): Long  = AsyncParity.fold(base)
  @Benchmark def longReader(): Long =
    base.map(_.toLong).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).block.toOption.get
  @Benchmark def floatReader(): Float =
    base.map(_.toFloat).runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value)).block.toOption.get
  @Benchmark def doubleReader(): Double =
    base.map(_.toDouble).runFoldAsync(0d)((sum, value) => Async.succeed(sum + value)).block.toOption.get
  @Benchmark def intCallback(): Long  = AsyncParity.fold(base.mapAsync(value => Async.succeed(value + 1)))
  @Benchmark def longCallback(): Long =
    base
      .map(_.toLong)
      .mapAsync(value => Async.succeed(value + 1))
      .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
      .block
      .toOption
      .get
  @Benchmark def floatCallback(): Float =
    base
      .map(_.toFloat)
      .mapAsync(value => Async.succeed(value + 1.0f))
      .runFoldAsync[Float](0.0f)((sum, value) => Async.succeed(sum + value))
      .block
      .toOption
      .get
  @Benchmark def doubleCallback(): Double =
    base
      .map(_.toDouble)
      .mapAsync(value => Async.succeed(value + 1d))
      .runFoldAsync(0d)((sum, value) => Async.succeed(sum + value))
      .block
      .toOption
      .get

  @Benchmark def terminalDrain(): Boolean        = base.runDrainAsync.block.isRight
  @Benchmark def terminalCollect(): Int          = base.runCollectAsync.block.toOption.get.length
  @Benchmark def terminalPrimitiveFold(): Long   = AsyncParity.fold(base)
  @Benchmark def terminalReferenceFold(): String =
    base.take(10).runFoldAsync("")((sum, value) => Async.succeed(sum + value.toString)).block.toOption.get
  @Benchmark def terminalForeachCallback(): Boolean =
    base.runForeachAsync(value => Async.succeed(value + 1)).block.isRight
  @Benchmark def terminalShortCircuit(): Boolean =
    base.filterAsync(value => Async.succeed(value == N / 2)).take(1).runCollectAsync.block.toOption.get.nonEmpty
  @Benchmark def terminalSinkMapAsync(): Long =
    base.runAsync(Sink.foldLeft[Int, Long](0L)(_ + _).mapAsync(value => Async.succeed(value + 1L))).block.toOption.get

  @Benchmark def semanticBoolean(): Int =
    base
      .map(value => (value & 1) == 0)
      .runFoldAsync(0)((sum, value) => Async.succeed(sum + (if (value) 1 else 0)))
      .block
      .toOption
      .get
  @Benchmark def semanticByte(): Int =
    base.map(_.toByte).runFoldAsync(0)((sum, value) => Async.succeed(sum + value)).block.toOption.get
  @Benchmark def semanticChar(): Int =
    base.map(_.toChar).runFoldAsync(0)((sum, value) => Async.succeed(sum + value.toInt)).block.toOption.get
  @Benchmark def semanticShort(): Int =
    base.map(_.toShort).runFoldAsync(0)((sum, value) => Async.succeed(sum + value)).block.toOption.get
  @Benchmark def semanticInt(): Long      = intCallback()
  @Benchmark def semanticLong(): Long     = longCallback()
  @Benchmark def semanticFloat(): Float   = floatCallback()
  @Benchmark def semanticDouble(): Double = doubleCallback()
}
