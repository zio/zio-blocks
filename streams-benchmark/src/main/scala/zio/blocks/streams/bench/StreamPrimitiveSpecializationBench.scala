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
import org.openjdk.jmh.annotations._
import zio.blocks.async._
import zio.blocks.streams.{Pipeline, Sink, Stream}

/**
 * Package-J matrix: eight primitive lanes, four composition routes, and
 * synchronous/asynchronous execution at N=0/1/1000. This is a ZIO Blocks-only
 * specialization diagnostic excluded from cross-provider rankings.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamPrimitiveSpecializationBench {
  @Param(Array("boolean", "byte", "char", "short", "int", "long", "float", "double"))
  var primitive: String = null

  @Param(Array("stream", "pipeline-stream", "pipeline-sink", "widened"))
  var route: String = null

  @Param(Array("0", "1", "1000"))
  var N: Int = 0

  private var booleans: Array[Boolean] = null
  private var bytes: Array[Byte]       = null
  private var chars: Array[Char]       = null
  private var shorts: Array[Short]     = null
  private var ints: Array[Int]         = null
  private var longs: Array[Long]       = null
  private var floats: Array[Float]     = null
  private var doubles: Array[Double]   = null

  @Setup(Level.Trial) def setup(): Unit = {
    booleans = Array.tabulate(N)(i => (i & 1) == 0)
    bytes = Array.tabulate(N)(_.toByte)
    chars = Array.tabulate(N)(_.toChar)
    shorts = Array.tabulate(N)(_.toShort)
    ints = Array.tabulate(N)(identity)
    longs = Array.tabulate(N)(_.toLong)
    floats = Array.tabulate(N)(_.toFloat)
    doubles = Array.tabulate(N)(_.toDouble)
  }

  private def sync[A <: AnyVal](
    source: Stream[Nothing, A],
    keep: A => Boolean,
    map: A => Long,
    widenedMap: AnyVal => Long
  ): Long =
    route match {
      case "stream"          => source.filter(keep).map(map).runFold(0L)(_ + _).toOption.get
      case "pipeline-stream" =>
        Pipeline
          .filter[A](keep)
          .andThen(Pipeline.map[A, Long](map))
          .applyToStream(source)
          .runFold(0L)(_ + _)
          .toOption
          .get
      case "pipeline-sink" =>
        source
          .run(
            Pipeline
              .filter[A](keep)
              .andThen(Pipeline.map[A, Long](map))
              .applyToSink(Sink.foldLeft[Long, Long](0L)(_ + _))
          )
          .toOption
          .get
      case "widened" =>
        val widened: Stream[Nothing, AnyVal] = source.filter(keep)
        widened.map(widenedMap).runFold(0L)(_ + _).toOption.get
    }

  private def async[A <: AnyVal](
    source: Stream[Nothing, A],
    keep: A => Boolean,
    map: A => Long,
    widenedMap: AnyVal => Long
  ): Long =
    route match {
      case "stream" =>
        source.filter(keep).map(map).runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)).block.toOption.get
      case "pipeline-stream" =>
        Pipeline
          .filter[A](keep)
          .andThen(Pipeline.map[A, Long](map))
          .applyToStream(source)
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
          .block
          .toOption
          .get
      case "pipeline-sink" =>
        source
          .runAsync(
            Pipeline
              .filter[A](keep)
              .andThen(Pipeline.map[A, Long](map))
              .applyToSink(Sink.foldLeftAsync[Long, Long](0L)((sum, value) => Async.succeed(sum + value)))
          )
          .block
          .toOption
          .get
      case "widened" =>
        val widened: Stream[Nothing, AnyVal] = source.filter(keep)
        widened
          .map(widenedMap)
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
          .block
          .toOption
          .get
    }

  @Benchmark def syncPrimitiveRoute(): Long = primitive match {
    case "boolean" =>
      sync(
        Stream.fromArray(booleans),
        (_: Boolean) => true,
        value => if (value) 1L else 0L,
        { case value: Boolean =>
          if (value) 1L else 0L
        }
      )
    case "byte" =>
      sync(Stream.fromArray(bytes), (_: Byte) => true, _.toLong + 1L, { case value: Byte => value.toLong + 1L })
    case "char" =>
      sync(Stream.fromArray(chars), (_: Char) => true, _.toLong + 1L, { case value: Char => value.toLong + 1L })
    case "short" =>
      sync(Stream.fromArray(shorts), (_: Short) => true, _.toLong + 1L, { case value: Short => value.toLong + 1L })
    case "int" =>
      sync(Stream.fromArray(ints), (_: Int) => true, _.toLong + 1L, { case value: Int => value.toLong + 1L })
    case "long"  => sync(Stream.fromArray(longs), (_: Long) => true, _ + 1L, { case value: Long => value + 1L })
    case "float" =>
      sync(Stream.fromArray(floats), (_: Float) => true, _.toLong + 1L, { case value: Float => value.toLong + 1L })
    case "double" =>
      sync(Stream.fromArray(doubles), (_: Double) => true, _.toLong + 1L, { case value: Double => value.toLong + 1L })
  }

  @Benchmark def asyncPrimitiveRoute(): Long = primitive match {
    case "boolean" =>
      async(
        Stream.fromArray(booleans),
        (_: Boolean) => true,
        value => if (value) 1L else 0L,
        { case value: Boolean =>
          if (value) 1L else 0L
        }
      )
    case "byte" =>
      async(Stream.fromArray(bytes), (_: Byte) => true, _.toLong + 1L, { case value: Byte => value.toLong + 1L })
    case "char" =>
      async(Stream.fromArray(chars), (_: Char) => true, _.toLong + 1L, { case value: Char => value.toLong + 1L })
    case "short" =>
      async(Stream.fromArray(shorts), (_: Short) => true, _.toLong + 1L, { case value: Short => value.toLong + 1L })
    case "int" =>
      async(Stream.fromArray(ints), (_: Int) => true, _.toLong + 1L, { case value: Int => value.toLong + 1L })
    case "long"  => async(Stream.fromArray(longs), (_: Long) => true, _ + 1L, { case value: Long => value + 1L })
    case "float" =>
      async(Stream.fromArray(floats), (_: Float) => true, _.toLong + 1L, { case value: Float => value.toLong + 1L })
    case "double" =>
      async(Stream.fromArray(doubles), (_: Double) => true, _.toLong + 1L, { case value: Double => value.toLong + 1L })
  }
}
