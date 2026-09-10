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

package zio.blocks.streams

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object PipelineAsyncSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def mapped(source: Stream[Nothing, Int], count: Int): Stream[Nothing, Int] =
    (0 until count).foldLeft(source) { (stream, index) =>
      if ((index & 1) == 0) stream.map(_ + 3)
      else stream.map(_ * 2)
    }

  private def expectedMapped(count: Int): Int =
    (0 until count).foldLeft(1) { (value, index) =>
      if ((index & 1) == 0) value + 3 else value * 2
    }

  private def asyncMapped(source: Stream[Nothing, Int], count: Int): Stream[Nothing, Int] =
    (0 until count).foldLeft(source)((stream, _) => stream.mapAsync(value => Async.succeed(value + 1)))

  def spec = suite("asynchronous Pipeline constructors")(
    test("segmentation preserves non-commutative map order at and beyond every capacity boundary") {
      val counts = Chunk(1023, 1024, 1025, 8191, 8192, 8193, 32766, 32767, 32768)
      ZIO
        .foreach(counts) { count =>
          run(mapped(Stream.succeed(1), count).runCollectAsync)
            .map(result => assertTrue(result == Right(Chunk(expectedMapped(count)))))
        }
        .map(results => results.reduce(_ && _))
    },
    test("segmented synchronous and asynchronous sources preserve primitive lane crossings") {
      def pipeline(source: Stream[Nothing, Int]) =
        mapped(source, 1024)
          .map(_.toLong)
          .map(_ + 0x100000000L)
          .map(_.toDouble)
          .map(_ + 0.25)
          .map(_.toString)
          .map(_.reverse)

      val expected = Chunk((expectedMapped(1024).toLong + 0x100000000L + 0.25).toString.reverse)
      val sync     = pipeline(Stream.succeed(1))
      val async    = pipeline(Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync))
      for {
        syncResult  <- run(sync.runCollectAsync)
        asyncResult <- run(async.runCollectAsync)
      } yield assertTrue(syncResult == Right(expected), asyncResult == Right(expected))
    },
    test("segmented map filter and collect callbacks retain source order and cardinality") {
      val source = Stream.range(0, 12)
      val deep   = mapped(source, 1025)
        .filter(value => (value & 3) != 1)
        .map(value => value ^ 0x55aa55aa)
        .collect { case value if value < 0 => value.toLong - 7L }
      val expected = Chunk.fromIterable((0 until 12).map { initial =>
        val value = (0 until 1025).foldLeft(initial)((n, index) => if ((index & 1) == 0) n + 3 else n * 2)
        value
      }.filter(value => (value & 3) != 1).map(_ ^ 0x55aa55aa).collect { case value if value < 0 => value.toLong - 7L })
      run(deep.runCollectAsync).map(result => assertTrue(result == Right(expected)))
    },
    test("deep asynchronous unary chains cross the deferred-operator boundary stack-safely") {
      val depth = 8193
      run(asyncMapped(Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync), depth).runCollectAsync).map(
        result => assertTrue(result == Right(Chunk(depth + 1)))
      )
    },
    test("segmented readers run every nested finalizer exactly once on early termination") {
      val depth      = 1025
      val finalizers = new AtomicInteger
      val stream     = (0 until depth).foldLeft(mapped(Stream.range(0, 4), 1025): Stream[Nothing, Int]) { (current, _) =>
        current.ensuring(finalizers.incrementAndGet())
      }
      run(stream.take(1).runCollectAsync).map(result =>
        assertTrue(result.exists(_.length == 1), finalizers.get == depth)
      )
    },
    test("segmented readers preserve primary and ordered cleanup failure identity") {
      val primary        = new RuntimeException("segmented-primary")
      val innerCleanup   = new RuntimeException("segmented-inner-cleanup")
      val outerCleanup   = new RuntimeException("segmented-outer-cleanup")
      val innerCloses    = new AtomicInteger
      val outerFinalizes = new AtomicInteger
      val source         = new Reader.AsyncReader[Int] {
        def close(): Async[Unit] = {
          innerCloses.incrementAndGet()
          Async.fail(innerCleanup)
        }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.fail(primary)
      }
      val stream = asyncMapped(Stream.fromReader[Nothing, Int](source), 1025).ensuring {
        outerFinalizes.incrementAndGet()
        throw outerCleanup
      }

      run(stream.runCollectAsync.either).map { result =>
        assertTrue(
          result.left.exists(_ eq primary),
          primary.getSuppressed.toList == List(innerCleanup, outerCleanup),
          innerCloses.get() == 1,
          outerFinalizes.get() == 1
        )
      }
    },
    test("cancellation inside a deeply nested flatMap inner segment closes every active scope") {
      val depth         = 101
      val cancellations = new AtomicInteger
      val closes        = new AtomicInteger
      val finalizers    = new AtomicInteger
      val entered       = new Completer[Unit]
      val pending       = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = { entered.succeed(()); this }
        protected def cancelOperation(): Async[Unit] = {
          cancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      val source = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = pending.asInstanceOf[Async[A]]
      }
      val deepest = Stream.fromReader[Nothing, Int](source)
      val nested  = (0 until depth).foldLeft(deepest) { (inner, _) =>
        Stream.succeed(1).flatMap(_ => inner.ensuring(finalizers.incrementAndGet()))
      }
      val reader = nested.compile(0, Stream.DefaultBufferSize).asInstanceOf[Reader.AsyncReader[Int]]
      val pull   = reader.read(-1)

      for {
        running <- ZIO.succeed(pull.start)
        _       <- run(entered)
        _       <- run(Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Int]]))
        value   <- run(pull)
        driven  <- run(running)
      } yield assertTrue(
        value == -1,
        driven == -1,
        cancellations.get() == 1,
        closes.get() == 1,
        finalizers.get() == depth
      )
    },
    test("reset traverses segmented synchronous and asynchronous readers") {
      val expected    = expectedMapped(1025)
      val syncReader  = mapped(Stream.succeed(1), 1025).compile(0).asInstanceOf[Reader.SyncReader[Int]]
      val asyncReader = mapped(Stream.fromReader[Nothing, Int](Reader.singleInt(1).toAsync), 1025)
        .compile(0)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val syncFirst = syncReader.read(-1)
      syncReader.reset()
      val syncSecond = syncReader.read(-1)
      syncReader.close()
      for {
        asyncFirst  <- run(asyncReader.read(-1))
        _           <- run(asyncReader.reset())
        asyncSecond <- run(asyncReader.read(-1))
        _           <- run(asyncReader.close())
      } yield assertTrue(syncFirst == expected, syncSecond == expected, asyncFirst == expected, asyncSecond == expected)
    },
    test("map, filter, and collect compose identically through streams and sinks when ready or suspended") {
      def pipeline(suspended: Boolean): Pipeline[Int, String] = {
        def callback[A](value: => A): Async[A] =
          if (suspended) Async.reschedule(() => Async.succeed(value)) else Async.succeed(value)
        Pipeline
          .mapAsync[Int, Int](value => callback(value + 1))
          .andThen(Pipeline.filterAsync[Int](value => callback((value & 1) == 0)))
          .andThen(Pipeline.collectAsync[Int, String](value => callback(if (value < 6) Some(value.toString) else None)))
      }
      def evaluate(suspended: Boolean) = {
        val pipe = pipeline(suspended)
        for {
          stream <- run(Stream(0, 1, 2, 3, 4, 5).via(pipe).runCollectAsync)
          sink   <- run(Stream(0, 1, 2, 3, 4, 5).runAsync(pipe.andThenSink(Sink.collectAll[String])))
        } yield (stream, sink)
      }
      for {
        ready     <- evaluate(suspended = false)
        suspended <- evaluate(suspended = true)
      } yield assertTrue(
        ready == (Right(Chunk("2", "4")), Right(Chunk("2", "4"))),
        suspended == ready
      )
    },
    test("asynchronous sink application reconstructs narrow primitive callback inputs") {
      def runByte = Stream
        .fromArray(Array[Byte](1, 2))
        .runAsync(
          Pipeline
            .map[Byte, Long](_.toLong)
            .andThenSink(Sink.foldLeftAsync[Long, Long](0L)((s, n) => Async.succeed(s + n)))
        )
      def runChar = Stream
        .fromArray(Array[Char]('a', 'b'))
        .runAsync(
          Pipeline
            .map[Char, Long](_.toLong)
            .andThenSink(Sink.foldLeftAsync[Long, Long](0L)((s, n) => Async.succeed(s + n)))
        )
      def runShort = Stream
        .fromArray(Array[Short](1, 2))
        .runAsync(
          Pipeline
            .map[Short, Long](_.toLong)
            .andThenSink(Sink.foldLeftAsync[Long, Long](0L)((s, n) => Async.succeed(s + n)))
        )
      for {
        byte  <- run(runByte)
        char  <- run(runChar)
        short <- run(runShort)
      } yield assertTrue(byte == Right(3L), char == Right(195L), short == Right(3L))
    },
    test("callback failures retain identity through stream and sink application") {
      val failure = new RuntimeException("pipeline-callback")
      val pipe    = Pipeline.mapAsync[Int, Int](_ => Async.fail(failure))
      for {
        stream <- run(Stream(1).via(pipe).runCollectAsync.either)
        sink   <- run(Stream(1).runAsync(pipe.andThenSink(Sink.collectAll[Int])).either)
      } yield assertTrue(stream == Left(failure), sink == Left(failure))
    },
    test("cancelling a suspended pipeline callback cancels it once and closes the derived reader") {
      val cancellations = new AtomicInteger
      val closes        = new AtomicInteger
      val entered       = new Completer[Unit]
      val pending       = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = { entered.succeed(()); this }
        protected def cancelOperation(): Async[Unit] = {
          cancellations.incrementAndGet()
          Async.succeed(())
        }
      }
      val source = Reader.fromChunk(Chunk(1)).toAsync.withReleaseAsync { () =>
        closes.incrementAndGet()
        Async.succeed(())
      }
      val reader = asyncMapped(Stream.fromReader[Nothing, Int](source), 1025)
        .via(Pipeline.mapAsync[Int, Int](_ => pending))
        .compile(0, Stream.DefaultBufferSize)
        .asInstanceOf[Reader.AsyncReader[Int]]
      val pull    = reader.read(-1)
      val running = pull.start
      for {
        _      <- run(entered)
        _      <- run(Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Int]]))
        _      <- run(reader.close())
        value  <- run(pull)
        driven <- run(running)
      } yield assertTrue(value == -1, driven == -1, cancellations.get() == 1, closes.get() == 1)
    }
  )
}
