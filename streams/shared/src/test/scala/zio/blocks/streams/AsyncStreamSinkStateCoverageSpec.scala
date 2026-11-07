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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.scope.Resource
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncStreamSinkStateCoverageSpec extends StreamsBaseSpec {
  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def asyncStream[A: JvmType.Infer](values: A*): Stream[Nothing, A] =
    Stream.fromReader[Nothing, A](async(values: _*))

  private final class Controlled[A](lane: JvmType, result: Async[A], eof: A) extends Reader.AsyncReader[A] {
    val closes                              = new AtomicInteger
    override def jvmType: JvmType           = lane
    def close(): Async[Unit]                = { closes.incrementAndGet(); Async.succeed(()) }
    def isClosed: Async[Boolean]            = Async.succeed(closes.get() != 0)
    def readable(): Async[Boolean]          = Async.succeed(closes.get() == 0)
    def read[B >: A](sentinel: B): Async[B] = if (closes.get() != 0) Async.succeed(sentinel) else result
  }

  private def message(result: Either[Throwable, ?]): Option[String] = result.left.toOption.map(_.getMessage)

  def spec = suite("async stream and sink state coverage")(
    test("fused synchronous and asynchronous transforms materialize in order across a sync boundary") {
      val seen   = scala.collection.mutable.ListBuffer.empty[Int]
      val stream = Stream(1, 2, 3, 4)
        .map(_ + 1)
        .filter(_ % 2 == 0)
        .collect { case n if n < 5 => n * 10 }
        .mapAsync(n => Async.succeed(n + 1))
        .tapEachAsync { n => seen += n; Async.succeed(()) }
        .filterAsync(n => Async.succeed(n > 0))
        .collectAsync(n => Async.succeed(if (n == 21) Some(n * 2) else None))
        .map(_ + 1)
        .takeWhile(_ < 100)
      runAsync(stream.runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk(43)), seen.toList == List(21, 41))
      )
    },
    test("async recovery normalizes sync and async replacement readers and preserves callback defects") {
      val boom   = new IllegalStateException("recovery-callback")
      val typed  = Stream.fail("bad").catchAll(_ => Stream.unwrap(Async.succeed(asyncStream(1, 2))))
      val defect = asyncStream(1)
        .mapAsync[Int](_ => Async.fail(new RuntimeException("defect")))
        .catchDefect { case t => Stream.unwrap(Async.succeed(Stream(t.getMessage.length))) }
      val thrown = Stream.fail("bad").catchAll[Nothing, Nothing, Nothing](_ => Stream.unwrap(throw boom))
      for {
        a <- runAsync(typed.runCollectAsync)
        b <- runAsync(defect.runCollectAsync)
        c <- runAsync(thrown.runCollectAsync.either)
      } yield assertTrue(a == Right(Chunk(1, 2)), b == Right(Chunk(6)), c == Left(boom))
    },
    test("concrete async stream nodes concatenate, defer, collect, repeat and statefully wrap") {
      var deferred = 0
      val stream   = (asyncStream(1, 2).map(_ * 2).filter(_ > 2).collect { case 4 => 5 } ++
        Stream.suspend { deferred += 1; asyncStream(6) }).repeated
        .drop(1)
        .take(4)
        .buffer(2)
      runAsync(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(6, 5, 6, 5)), deferred == 2))
    },
    test("asynchronous source and acquire-release cover success, use failure and release suppression") {
      val released       = scala.collection.mutable.ListBuffer.empty[String]
      val useFailure     = new RuntimeException("use")
      val releaseFailure = new RuntimeException("release")
      val source         = Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(1, 2)))
      val success        = Stream.fromAcquireReleaseAsync(
        Async.succeed("ok"),
        (r: String) => {
          released += r; Async.succeed(())
        }
      )(r => Stream.unwrap(Async.succeed(source.map(_ + r.length))))
      val failed = Stream.fromAcquireReleaseAsync[String, Nothing, Nothing](
        Async.succeed("failed"),
        (r: String) => {
          released += r; Async.fail(releaseFailure)
        }
      )(_ => Stream.unwrap(Async.fail(useFailure)))
      for {
        a <- runAsync(success.runCollectAsync)
        b <- runAsync(failed.runCollectAsync.either)
      } yield assertTrue(
        a == Right(Chunk(3, 4)),
        b == Left(useFailure),
        released.toList == List("ok", "failed"),
        useFailure.getSuppressed.toList == List(releaseFailure)
      )
    },
    test("protected and unprotected async sources use readers directly and close exactly once") {
      val protectedCloses = new AtomicInteger
      val protectedReader = Reader.fromChunk(Chunk(1)).toAsync.withReleaseAsync { () =>
        protectedCloses.incrementAndGet(); Async.succeed(())
      }
      var protectedSeen: Reader.AsyncReader[Int] = null
      var attemptSeen: Reader.AsyncReader[Int]   = null
      val callbackFailure                        = new RuntimeException("callback")
      val failingCloses                          = new AtomicInteger
      val failingReader                          = Reader.fromChunk(Chunk(2)).toAsync.withReleaseAsync { () =>
        failingCloses.incrementAndGet(); Async.succeed(())
      }
      for {
        protectedValue <- runAsync(
                            Stream
                              .fromReaderAsync[Nothing, Int](Async.succeed(protectedReader))
                              .useReaderAsync { reader => protectedSeen = reader; reader.readInt(-1L) }
                          )
        protectedClosed <- runAsync(protectedSeen.isClosed)
        attemptValue    <- runAsync(
                          Stream.attemptAsync(Async.succeed(3)).useReaderAsync { reader =>
                            attemptSeen = reader
                            reader.readInt(-1L)
                          }
                        )
        attemptClosed <- runAsync(attemptSeen.isClosed)
        failed        <- runAsync(
                    Stream
                      .fromReaderAsync[Nothing, Int](Async.succeed(failingReader))
                      .useReaderAsync(_ => Async.fail(callbackFailure))
                      .either
                  )
      } yield assertTrue(
        protectedValue == 1L,
        protectedClosed,
        protectedCloses.get == 1,
        attemptValue == 3L,
        attemptClosed,
        failed == Left(callbackFailure),
        failingCloses.get == 1
      )
    },
    test("direct reader use normalizes protected acquisition and preserves trusted reader failures") {
      val acquisition = new StreamError("reader-use-acquisition")
      val readFailure = StreamError.source("reader-use-read")
      val reader      = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                                                    = Async.succeed(())
        override def jvmType: JvmType                                               = JvmType.Int
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.failTrusted(readFailure)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.failTrusted(readFailure)
      }
      for {
        acquisitionResult <- runAsync(
                               Stream
                                 .fromReaderAsync[Nothing, Int](throw acquisition)
                                 .useReaderAsync(_.readInt(-1L))
                                 .either
                             )
        readResult <- runAsync(
                        Stream
                          .fromReaderAsync[Nothing, Int](Async.succeed(reader: Reader[Int]))
                          .useReaderAsync(_.readInt(-1L))
                          .either
                      )
      } yield assertTrue(
        acquisitionResult.left.exists {
          case error: StreamError =>
            (error ne acquisition) && !error.isTrusted && error.value == "reader-use-acquisition"
          case _ => false
        },
        readResult.left.exists(error => (error eq readFailure) && readFailure.isTrusted)
      )
    },
    test("Int async source with a non-fold mapped sink takes the managed fallback") {
      val closes = new AtomicInteger
      val reader = Reader.fromChunk(Chunk(1, 2, 3)).toAsync.withReleaseAsync { () =>
        closes.incrementAndGet(); Async.succeed(())
      }
      val mapped = new Sink.MappedAsync(Sink.collectAll[Int], (values: Chunk[Int]) => Async.succeed(values.size))
      runAsync(Stream.fromReaderAsync[Nothing, Int](Async.succeed(reader)).runAsync(mapped)).map(result =>
        assertTrue(result == Right(3), closes.get == 1)
      )
    },
    test("managed reader callbacks launder trusted failures and suppress cleanup after the primary") {
      val trusted       = StreamError.source("forged")
      val primary       = new RuntimeException("callback")
      val closeFailure  = new RuntimeException("close")
      val closes        = new AtomicInteger
      val failingReader = Reader.fromChunk(Chunk(1)).toAsync.withReleaseAsync { () =>
        closes.incrementAndGet(); Async.fail(closeFailure)
      }
      val managed = Stream.fromReader[Nothing, Int](failingReader).map(identity)
      for {
        thrown <- runAsync(managed.useReaderAsync(_ => throw primary).either)
        forged <- runAsync(Stream(1).map(identity).useReaderAsync(_ => Async.failTrusted(trusted)).either)
      } yield assertTrue(
        thrown == Left(primary),
        primary.getSuppressed.toList == List(closeFailure),
        closes.get == 1,
        forged.left.exists {
          case error: StreamError => (error ne trusted) && error.value == "forged" && !error.isTrusted
          case _                  => false
        }
      )
    },
    test("Resource acquisition, asynchronous use and finalization compose with async streams") {
      val releases = scala.collection.mutable.ListBuffer.empty[String]
      val resource = Resource.acquireRelease("resource")(releases += _)
      val stream   = Stream
        .fromResource(resource)(r => Stream.unwrap(Async.succeed(asyncStream(r, null, "end"))))
        .collectAsync(s => Async.succeed(Option(s)))
      runAsync(stream.runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk("resource", "end")), releases.toList == List("resource"))
      )
    },
    test("async zip reports readable, rejects overlapping reads, closes once and returns sentinels after close") {
      val gate   = new Completer[Any]
      val left   = new Controlled[Any](JvmType.AnyRef, gate, null)
      val zipped = Stream.fromReader[Nothing, Any](left) && asyncStream("r")
      val reader = Stream.compileToReader(zipped).expectedAsync
      val first  = reader.read[Any]("first-eof")
      for {
        readyDuring <- runAsync(reader.readable())
        overlap     <- runAsync(reader.read[Any]("second-eof").either)
        _            = gate.succeed("l")
        value       <- runAsync(first)
        _           <- runAsync(reader.close())
        _           <- runAsync(reader.close())
        closed      <- runAsync(reader.isClosed)
        readyAfter  <- runAsync(reader.readable())
        eof         <- runAsync(reader.read[Any]("closed-eof"))
      } yield assertTrue(
        !readyDuring,
        message(overlap).contains("Only one Reader pull may be in flight"),
        value == (("l", "r")),
        closed,
        !readyAfter,
        eof == null,
        left.closes.get() == 1
      )
    },
    test("async zip records terminal failure and suppresses close failure") {
      val readFailure  = new RuntimeException("zip-read")
      val closeFailure = new RuntimeException("zip-close")
      val left         = new Reader.AsyncReader[Any] {
        override def jvmType            = JvmType.AnyRef
        def close()                     = Async.fail(closeFailure)
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Any](sentinel: A) = Async.fail(readFailure)
      }
      val reader = Stream.compileToReader(Stream.fromReader[Nothing, Any](left) && asyncStream("r")).expectedAsync
      for {
        first  <- runAsync(reader.read[Any]("eof").either)
        closed <- runAsync(reader.isClosed)
        second <- runAsync(reader.read[Any]("eof").either)
      } yield assertTrue(
        first == Left(readFailure),
        closed,
        second == Left(readFailure),
        readFailure.getSuppressed.toList == List(closeFailure)
      )
    },
    test("AsyncPuller consumes every primitive lane, references including null, and EOF") {
      def head[A: JvmType.Infer](values: A*): zio.ZIO[Any, Throwable, Either[Nothing, Option[A]]] =
        runAsync(asyncStream(values: _*).runAsync(Sink.head[A]))
      for {
        z <- head(true); b         <- head[Byte](-128); c    <- head(Char.MaxValue); s <- head[Short](Short.MinValue)
        i <- head(Int.MinValue); l <- head(Long.MinValue); f <- head(Float.NaN); d     <- head(Double.NaN)
        r <- head[String](null); e <- head[Int]()
      } yield assertTrue(
        z == Right(Some(true)),
        b == Right(Some((-128).toByte)),
        c == Right(Some(Char.MaxValue)),
        s == Right(Some(Short.MinValue)),
        i == Right(Some(Int.MinValue)),
        l == Right(Some(Long.MinValue)),
        f.exists(_.exists(_.isNaN)),
        d.exists(_.exists(_.isNaN)),
        r == Right(Some(null)),
        e == Right(None)
      )
    },
    test("async fold lanes, drain and sync-to-async sink composition preserve exact results") {
      for {
        z <- runAsync(
               asyncStream(true, false, true).runAsync(Sink.foldLeft[Boolean, Int](0)((n, v) => n + (if (v) 1 else 0)))
             )
        b    <- runAsync(asyncStream[Byte](1, 2).runAsync(Sink.foldLeft[Byte, Long](0L)(_ + _)))
        c    <- runAsync(asyncStream('a', 'b').runAsync(Sink.foldLeft[Char, Double](0d)(_ + _.toInt)))
        s    <- runAsync(asyncStream[Short](1, 2).runAsync(Sink.foldLeft[Short, Int](0)(_ + _)))
        i    <- runAsync(asyncStream(1, 2).runAsync(Sink.foldLeft[Int, Int](0)(_ + _)))
        l    <- runAsync(asyncStream(1L, 2L).runAsync(Sink.foldLeft[Long, Long](0L)(_ + _)))
        f    <- runAsync(asyncStream(1f, 2f).runAsync(Sink.foldLeft[Float, Double](0d)(_ + _)))
        d    <- runAsync(asyncStream(1d, 2d).runAsync(Sink.foldLeft[Double, Double](0d)(_ + _)))
        r    <- runAsync(asyncStream("a", "bb").runAsync(Sink.foldLeft[String, Int](0)(_ + _.length)))
        unit <- runAsync(Stream(1, 2).mapAsync(Async.succeed).runAsync(Sink.drain))
      } yield assertTrue(
        z == Right(2),
        b == Right(3L),
        c == Right(195d),
        s == Right(3),
        i == Right(3),
        l == Right(3L),
        f == Right(3d),
        d == Right(3d),
        r == Right(3),
        unit == Right(())
      )
    },
    test("async sink contramap, map and error mapping preserve success, typed failure and defects") {
      val defect = new RuntimeException("sink-map")
      val sink   = Sink
        .foldLeft[Int, Int](0)(_ + _)
        .contramapAsync[Int, String](s => Async.succeed(s.toInt))
        .mapAsync(n => Async.succeed(n * 2))
      val failed = Sink.fail("typed").mapErrorAsync(e => Async.succeed(e.length))
      val thrown = Sink.foldLeft[Int, Int](0)(_ + _).contramapAsync[Nothing, String](_ => throw defect)
      for {
        a <- runAsync(asyncStream("2", "3").runAsync(sink))
        b <- runAsync(asyncStream(1).runAsync(failed))
        c <- runAsync(asyncStream("1").runAsync(thrown).either)
      } yield assertTrue(a == Right(10), b == Left(5), c == Left(defect))
    },
    test("async sink callback marks synchronous, asynchronous, null and trusted reader failures") {
      val sync         = new RuntimeException("sync")
      val asyncFailure = new RuntimeException("async")
      val nullSink     = Sink.createAsync[Nothing, Int, Unit](_ => null)
      val syncSink     = Sink.createAsync[Nothing, Int, Unit](_ => throw sync)
      val asyncSink    = Sink.createAsync[Nothing, Int, Unit](_ => Async.fail(asyncFailure))
      val readerThrow  = Sink.createAsync[Nothing, Int, Int](_.readInt(-1L).map(_.toInt))
      val badReader    = new Reader.AsyncReader[Int] {
        override def jvmType                                           = JvmType.Int
        def close()                                                    = Async.succeed(())
        def isClosed                                                   = Async.succeed(false)
        def readable()                                                 = Async.succeed(true)
        def read[A >: Int](sentinel: A)                                = throw new IllegalArgumentException("reader-throw")
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) =
          throw new IllegalArgumentException("reader-throw")
      }
      for {
        n <- runAsync(nullSink.drain(async(1)).either)
        s <- runAsync(syncSink.drain(async(1)).either)
        a <- runAsync(asyncSink.drain(async(1)).either)
        r <- runAsync(readerThrow.drain(badReader).either)
      } yield assertTrue(
        n.left.exists(_.isInstanceOf[NullPointerException]),
        s == Left(sync),
        a == Left(asyncFailure),
        message(r).contains("reader-throw")
      )
    },
    test("async finalizers run once and suppress failure while close remains idempotent") {
      val primary = new RuntimeException("primary")
      val cleanup = new RuntimeException("cleanup")
      val count   = new AtomicInteger
      val reader  = Stream
        .compileToReader(
          asyncStream(1).mapAsync[Int](_ => Async.fail(primary)).ensuringAsync {
            count.incrementAndGet(); Async.fail(cleanup)
          }
        )
        .expectedAsync
      for {
        result <- runAsync(reader.read[Int](-1).either)
        _      <- runAsync(reader.close().either)
        _      <- runAsync(reader.close().either)
      } yield assertTrue(result == Left(primary), count.get() == 1, primary.getSuppressed.toList == List(cleanup))
    }
  )
}
