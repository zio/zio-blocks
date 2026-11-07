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

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized
import zio.blocks.async.*
import zio.blocks.chunk.Chunk
import zio.blocks.streams.{JvmType, Sink, Stream}
import zio.blocks.streams.io.Reader

/**
 * ZIO Blocks-only low-level reader and parity fixtures. Benchmarks backed by
 * these fixtures are implementation diagnostics excluded from cross-provider
 * rankings.
 */
private[bench] object AsyncParity {
  private final class ReadyRangeReader(from: Int, until: Int) extends Reader.AsyncReader[Int] {
    private var current = from
    private var closed  = false

    override def jvmType: JvmType             = JvmType.Int
    def close(): Async[Unit]                  = Async.succeed { closed = true }
    def isClosed: Async[Boolean]              = Async.succeed(closed || current >= until)
    def readable(): Async[Boolean]            = Async.succeed(!closed && current < until)
    def read[A >: Int](sentinel: A): Async[A] = Async.succeed {
      if (closed || current >= until) sentinel
      else {
        val value = current
        current += 1
        value.asInstanceOf[A]
      }
    }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.succeed {
      if (closed || current >= until) sentinel
      else {
        val value = current.toLong
        current += 1
        value
      }
    }
    override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = Async.succeed {
      val length = math.min(math.max(n, 0), math.max(until - current, 0))
      if (closed || length == 0) Chunk.empty
      else {
        val values = Array.tabulate(length)(current + _)
        current += length
        Chunk.fromArray(values).asInstanceOf[Chunk[A]]
      }
    }
  }

  def source(from: Int, until: Int): Stream[Nothing, Int] =
    Stream.fromReaderAsync(Async.succeed(new ReadyRangeReader(from, until)))

  /** High-level one-ready-effect-per-element source for cross-library cells. */
  def readyEffectSource(from: Int, until: Int): Stream[Nothing, Int] =
    Stream.range(from, until).mapAsync(value => Async.succeed(value))

  def trackedSource(
    from: Int,
    until: Int,
    acquired: () => Unit,
    closed: () => Unit
  ): Stream[Nothing, Int] =
    Stream.fromReaderAsync {
      acquired()
      val underlying = new ReadyRangeReader(from, until)
      Async.succeed(new Reader.AsyncReader[Int] {
        override def jvmType: JvmType = underlying.jvmType
        def close(): Async[Unit]      = {
          closed()
          underlying.close()
        }
        def isClosed: Async[Boolean]                                                = underlying.isClosed
        def readable(): Async[Boolean]                                              = underlying.readable()
        def read[A >: Int](sentinel: A): Async[A]                                   = underlying.read(sentinel)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = underlying.readInt(sentinel)
        override def readUpToN[A >: Int](n: Int): Async[Chunk[A]]                   = underlying.readUpToN(n)
      })
    }

  def fold(stream: Stream[Nothing, Int]): Long =
    stream.runAsync(Sink.foldLeft[Int, Long](0L)(_ + _)).block.toOption.get

  def chainMixed(stream0: Stream[Nothing, Int], depth: Int, innerN: Int): Stream[Nothing, Int] = {
    var stream = stream0
    var i      = 0
    while (i < depth) {
      stream = stream.filter(_ % 2 != 0).flatMap(_ => source(0, innerN)).map(_ + 1)
      i += 1
    }
    stream
  }
}

/** Ready-native counterparts for StreamEvalBench's twelve ZIO Blocks cells. */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncEvalParityBench {
  @Param(Array("10000")) var N: Int = uninitialized

  private def src(from: Int, until: Int)             = AsyncParity.source(from, until)
  private def fold(stream: Stream[Nothing, Int])     = AsyncParity.fold(stream)
  private var singleton: Stream[Nothing, Int]        = uninitialized
  private var drain: Stream[Nothing, Int]            = uninitialized
  private var map1: Stream[Nothing, Int]             = uninitialized
  private var filter1: Stream[Nothing, Int]          = uninitialized
  private var flatMap1: Stream[Nothing, Int]         = uninitialized
  private var takeDrop: Stream[Nothing, Int]         = uninitialized
  private var mapFilterFlatMap: Stream[Nothing, Int] = uninitialized
  private var mixed1: Stream[Nothing, Int]           = uninitialized
  private var mixed2: Stream[Nothing, Int]           = uninitialized
  private var mixed3: Stream[Nothing, Int]           = uninitialized

  @Setup(Level.Trial) def setup(): Unit = {
    val base = src(0, N)
    singleton = src(42, 43)
    drain = src(0, N)
    map1 = base.map(_ + 1)
    filter1 = base.filter(_ % 2 != 0)
    flatMap1 = src(0, 100).flatMap(_ => src(0, 100))
    takeDrop = base.drop(N / 2).take((N / 2).toLong)
    mapFilterFlatMap = base.map(_ + 1).filter(_ % 2 != 0).flatMap(_ => src(0, 100))
    mixed1 = AsyncParity.chainMixed(src(0, 200), 1, 100)
    mixed2 = AsyncParity.chainMixed(src(0, 100), 2, 20)
    mixed3 = AsyncParity.chainMixed(src(0, 100), 3, 10)
  }

  @Benchmark def async_singleton(): Long        = fold(singleton)
  @Benchmark def async_drain(): Long            = { drain.runDrainAsync.block.toOption.get; 0L }
  @Benchmark def async_map_1(): Long            = fold(map1)
  @Benchmark def async_filter_1(): Long         = fold(filter1)
  @Benchmark def async_flatMap_1(): Long        = fold(flatMap1)
  @Benchmark def async_takeDrop(): Long         = fold(takeDrop)
  @Benchmark def async_mapFilterFlatMap(): Long = fold(mapFilterFlatMap)
  @Benchmark def async_mixed_1(): Long          = fold(mixed1)
  @Benchmark def async_mixed_2(): Long          = fold(mixed2)
  @Benchmark def async_mixed_3(): Long          = fold(mixed3)
  @Benchmark def async_nested_flatMap(): Long   = {
    var stream: Stream[Nothing, Int] = src(1, 2)
    var i                            = 0
    while (i < 10000) { stream = stream.flatMap(_ => src(1, 2)); i += 1 }
    fold(stream)
  }
  @Benchmark def async_nested_concat(): Long = {
    var stream: Stream[Nothing, Int] = src(1, 2)
    var i                            = 0
    while (i < 10000) { stream = stream ++ src(1, 2); i += 1 }
    fold(stream)
  }

  /** Untimed correctness path consumed by benchmark tooling. */
  def runOnce(id: String): Long = id match {
    case "singleton"        => async_singleton()
    case "drain"            => async_drain()
    case "map_1"            => async_map_1()
    case "filter_1"         => async_filter_1()
    case "flatMap_1"        => async_flatMap_1()
    case "takeDrop"         => async_takeDrop()
    case "mapFilterFlatMap" => async_mapFilterFlatMap()
    case "mixed_1"          => async_mixed_1()
    case "mixed_2"          => async_mixed_2()
    case "mixed_3"          => async_mixed_3()
    case "nested_flatMap"   => async_nested_flatMap()
    case "nested_concat"    => async_nested_concat()
  }
}

/** Ready-native counterparts for all thirteen StreamPipelineBench ZB cells. */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncPipelineParityBench {
  private val N                                  = 10000
  private def src(from: Int, until: Int)         = AsyncParity.source(from, until)
  private def fold(stream: Stream[Nothing, Int]) = AsyncParity.fold(stream)
  private def maps(n: Int): Stream[Nothing, Int] = {
    var stream = src(0, N); var i = 0
    while (i < n) { stream = stream.map(_ + 1); i += 1 }
    stream
  }

  @Benchmark def async_filterMap(): Long      = fold(src(0, N).filter(_ % 2 == 0).map(_ + 1))
  @Benchmark def async_map(): Long            = fold(src(0, N).map(_ * 2))
  @Benchmark def async_filter(): Long         = fold(src(0, N).filter(_ % 2 == 0))
  @Benchmark def async_chainedMaps(): Long    = fold(maps(5))
  @Benchmark def async_flatMap(): Long        = fold(src(0, N).flatMap(i => Stream.succeed(i * 2)))
  @Benchmark def async_take(): Long           = fold(src(0, N).take((N / 2).toLong))
  @Benchmark def async_takeWhile(): Long      = fold(src(0, N).takeWhile(_ < N / 2))
  @Benchmark def async_concat(): Long         = fold(src(0, N / 2) ++ src(N / 2, N))
  @Benchmark def async_drain(): Boolean       = src(0, N).runDrainAsync.block.isRight
  @Benchmark def async_chainedMaps10(): Long  = fold(maps(10))
  @Benchmark def async_chainedMaps100(): Long = fold(maps(100))
  @Benchmark def async_filterMapChain(): Long = fold(
    src(0, N)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)
  )
  @Benchmark def async_takeDrop(): Long = fold(src(0, N).drop(N / 2).take((N / 2).toLong))

  def runOnce(id: String): Long = id match {
    case "filterMap"      => async_filterMap()
    case "map"            => async_map()
    case "filter"         => async_filter()
    case "chainedMaps"    => async_chainedMaps()
    case "flatMap"        => async_flatMap()
    case "take"           => async_take()
    case "takeWhile"      => async_takeWhile()
    case "concat"         => async_concat()
    case "drain"          => if (async_drain()) 1L else 0L
    case "chainedMaps10"  => async_chainedMaps10()
    case "chainedMaps100" => async_chainedMaps100()
    case "filterMapChain" => async_filterMapChain()
    case "takeDrop"       => async_takeDrop()
  }
}

/** Async-reader-source construction counterparts for all nine setup cells. */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamAsyncSetupParityBench {
  @Param(Array("10000")) var N: Int                           = uninitialized
  private def src                                             = AsyncParity.source(0, N)
  private def maps(n: Int)                                    = { var s = src; var i = 0; while (i < n) { s = s.map(_ + 1); i += 1 }; s }
  @Benchmark def async_map1(): Stream[Nothing, Int]           = maps(1)
  @Benchmark def async_map5(): Stream[Nothing, Int]           = maps(5)
  @Benchmark def async_map10(): Stream[Nothing, Int]          = maps(10)
  @Benchmark def async_map100(): Stream[Nothing, Int]         = maps(100)
  @Benchmark def async_filter(): Stream[Nothing, Int]         = src.filter(_ % 2 == 0)
  @Benchmark def async_filterMap(): Stream[Nothing, Int]      = src.filter(_ % 2 == 0).map(_ + 1)
  @Benchmark def async_filterMapChain(): Stream[Nothing, Int] =
    src
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)
  @Benchmark def async_takeDrop(): Stream[Nothing, Int] = src.drop(N / 2).take((N / 2).toLong)
  @Benchmark def async_drain(): Stream[Nothing, Int]    = src
  def runOnce(id: String): Long                         = AsyncParity.fold(id match {
    case "map1"           => async_map1(); case "map5"               => async_map5(); case "map10"       => async_map10()
    case "map100"         => async_map100(); case "filter"           => async_filter(); case "filterMap" => async_filterMap()
    case "filterMapChain" => async_filterMapChain(); case "takeDrop" => async_takeDrop(); case "drain"   => async_drain()
  })
}
