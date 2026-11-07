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
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError, SyncInterpreter}
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncStreamExhaustiveCoverageSpec extends StreamsBaseSpec {
  // Fresh current3 Stream.scala residual checklist (136 distinct non-render source lines).
  // managed terminals: 524,526,527; default materializer/kind fold: 761,762,1103,1107,1108,1109.
  // resources and recovery normalization: 1463,1464,1465,1467,1468,1476,1477,1478,
  //   1816,1817,1818,1831,1833,1842,1847,1850,1851,1852.
  // recovery nodes: 1924,1925,1926,2099,2100,2101,3161,3162,3168,3170,3171,3172.
  // concat/deferred/drop/zip: 2303,2304,2307,2310,2311,2315,2348,2349,2350,2375,2376,
  //   2523,2524,2525,2603,2604,2636,2641,2645,2665,2670,2672.
  // lifecycle/finalization/error map: 2861,2871,2887,2889,2893,2898,2909,2919,
  //   2946,2947,2981,2982.
  // filter/async unary/collect: 3072,3106,3110,3111,3112,3113,3114,3139,3140,3145,3146,
  //   3320,3322,3323,3324,3325,3326.
  // acquire/stateful/source/resource: 3476,3477,3478,3561,3562,3563,3583,3586,3715,3718,
  //   3747,3748,3750,3751,3752,3762,3763,3790,3791,3792,3793,3794,3795,3822,3823,3824.
  // map/repeat/take cycles: 3923,3927,3928,3929,3930,3931,3959,3960,3961,3963,3964,3965,
  //   3966,3969,3970,3981,4008,4009,4043,4044.

  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def asyncStream[A: JvmType.Infer](values: A*): Stream[Nothing, A] =
    Stream.fromReader[Nothing, A](async(values: _*))

  private def materialize[E, A](stream: Stream[E, A]): Reader.AsyncReader[A] =
    AsyncInterpreter.fromStream(stream).toReader[A]

  def spec = suite("Stream async exhaustive residual coverage")(
    test("public async terminals exercise managed success, typed failure, callback throw and close suppression") {
      val callback     = new RuntimeException("callback")
      val closeFailure = new RuntimeException("close")
      val reader       = new Reader.AsyncReader[Int] {
        def close()                     = Async.fail(closeFailure)
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Int](sentinel: A) = Async.succeed(1)
      }
      val thrown = Stream(1).runForeachAsync(_ => throw callback)
      for {
        collected  <- runAsync(Stream.range(1, 4).runCollectAsync)
        folded     <- runAsync(asyncStream(1, 2).runFoldAsync(0)((n, a) => Async.succeed(n + a)))
        typed      <- runAsync(Stream.fail("typed").runDrainAsync)
        defect     <- runAsync(thrown.either)
        suppressed <-
          runAsync(Stream.fromReader[Nothing, Int](reader).runForeachAsync(_ => Async.fail(callback)).either)
      } yield assertTrue(
        collected == Right(Chunk(1, 2, 3)),
        folded == Right(3),
        typed == Left("typed"),
        defect == Left(callback),
        suppressed == Left(callback),
        callback.getSuppressed.toList == List(closeFailure)
      )
    },
    test("reader-kind folds select sync and async sources through map and filter") {
      val sync         = Stream.fromReader[Nothing, Int](Reader.fromChunk(Chunk(1, 2))).map(_ + 1).filter(_ > 1)
      val asynchronous = asyncStream(1, 2).map(_ + 1).filter(_ > 1)
      val a            = sync.foldReaderKind(reader => Async.succeed(reader.readAll[Int]()), _.readAll[Int]())
      val b            = asynchronous.foldReaderKind(reader => Async.succeed(reader.readAll[Int]()), _.readAll[Int]())
      runAsync(a.zipWith(b)((_, _))).map { case (left, right) =>
        assertTrue(left == Chunk(2, 3), right == Chunk(2, 3))
      }
    },
    test("materialization traverses concat, defer, drop, collect, stateful, map, filter, repeat and take cycles") {
      var suspended = 0
      val stream    = (asyncStream(0, 1, 2)
        .map(_ + 1)
        .filter(_ % 2 == 0)
        .collect { case n if n < 3 => n * 10 } ++ Stream.suspend {
        suspended += 1
        asyncStream(30)
      }).drop(1).buffer(2).repeated.take(3).takeWhile(_ <= 30)
      runAsync(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(30, 30, 30)), suspended == 3))
    },
    test("async unary and recovery nodes normalize sync and async replacement kinds") {
      val syncRecovered  = Stream.fail("x").catchAll(_ => Stream.unwrap(Async.succeed(Stream(1, 2))))
      val asyncRecovered = Stream.fail("x").catchAll(_ => Stream.unwrap(Async.succeed(asyncStream(3, 4))))
      val defect         = asyncStream(1)
        .mapAsync[Int](_ => Async.fail(new RuntimeException("boom")))
        .catchDefect { case t => Stream.unwrap(Async.succeed(Stream(t.getMessage.length))) }
      val transformed = asyncStream(1, 2, 3)
        .mapAsync(n => Async.succeed(n + 1))
        .filterAsync(n => Async.succeed(n % 2 == 0))
        .collectAsync(n => Async.succeed(Some(n * 2)))
      for {
        a <- runAsync(syncRecovered.runCollectAsync); b <- runAsync(asyncRecovered.runCollectAsync)
        c <-
          runAsync(defect.runCollectAsync);
        d <- runAsync(transformed.runCollectAsync)
      } yield assertTrue(
        a == Right(Chunk(1, 2)),
        b == Right(Chunk(3, 4)),
        c == Right(Chunk(4)),
        d == Right(Chunk(4, 8))
      )
    },
    test("async zip covers EOF, overlap, terminal failure, cancellation, and cleanup suppression") {
      val gate    = new Completer[Any]
      val failure = new RuntimeException("read")
      val cleanup = new RuntimeException("cleanup")
      val gated   = new Reader.AsyncReader[Any] {
        def close()                     = Async.succeed(())
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Any](sentinel: A) = gate.asInstanceOf[Async[A]]
      }
      val failed = new Reader.AsyncReader[Any] {
        def close()                     = Async.fail(cleanup)
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Any](sentinel: A) = Async.fail(failure)
      }
      val firstReader  = Stream.compileToReader(Stream.fromReader[Nothing, Any](gated) && asyncStream("r")).expectedAsync
      val pull         = firstReader.read[Any]("eof")
      val failedReader =
        Stream.compileToReader(Stream.fromReader[Nothing, Any](failed) && asyncStream("r")).expectedAsync
      for {
        overlap      <- runAsync(firstReader.read[Any]("overlap").either)
        _             = gate.succeed("l")
        value        <- runAsync(pull)
        _            <- runAsync(firstReader.close())
        eof          <- runAsync(firstReader.read[Any]("eof"))
        failedResult <- runAsync(failedReader.read[Any]("eof").either)
      } yield assertTrue(
        overlap.left.exists(_.isInstanceOf[IllegalStateException]),
        value == (("l", "r")),
        eof == null,
        failedResult == Left(failure),
        failure.getSuppressed.toList == List(cleanup)
      )
    },
    test("closing an active async zip cancels its pending pull and returns its sentinel") {
      val cancels = new AtomicInteger
      val polls   = new AtomicInteger
      val pending = new Async.Operation[Any] {
        def poll(onComplete: Runnable): Async[Any]   = { polls.incrementAndGet(); this }
        protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
      }
      val left = new Reader.AsyncReader[Any] {
        def close()                     = Async.succeed(())
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(false)
        def read[A >: Any](sentinel: A) = pending.asInstanceOf[Async[A]]
      }
      val reader                                    = Stream.compileToReader(Stream.fromReader[Nothing, Any](left) && asyncStream("r")).expectedAsync
      val pull                                      = reader.read[Any]("eof")
      def awaitPending: zio.ZIO[Any, Nothing, Unit] =
        if (polls.get() > 0) zio.ZIO.unit else zio.ZIO.yieldNow *> zio.ZIO.suspendSucceed(awaitPending)
      for {
        fiber <- runAsync(pull).fork
        _     <- awaitPending
        _     <- runAsync(reader.close())
        value <- fiber.join
      } yield assertTrue(value == null, cancels.get() == 1)
    },
    test("acquire-release and scoped resources cover async success, null, use failure, and release failure") {
      val events         = scala.collection.mutable.ListBuffer.empty[String]
      val useFailure     = new RuntimeException("use")
      val releaseFailure = new RuntimeException("release")
      val managed        = Stream.fromAcquireReleaseAsync(
        Async.succeed("r"),
        (r: String) => {
          events += r; Async.succeed(())
        }
      )(r => Stream.unwrap(Async.succeed(asyncStream(r, null))))
      val failed =
        Stream.fromAcquireReleaseAsync[String, Nothing, Nothing](
          Async.succeed("f"),
          (_: String) => Async.fail(releaseFailure)
        )(_ => Stream.unwrap(Async.fail(useFailure)))
      val resource = Resource.acquireRelease("scope")(events += _)
      val scoped   = Stream.fromResource(resource)(r => Stream.unwrap(Async.succeed(asyncStream(r))))
      for {
        a <- runAsync(managed.runCollectAsync); b <- runAsync(failed.runCollectAsync.either)
        c <- runAsync(scoped.runCollectAsync)
      } yield assertTrue(
        a == Right(Chunk("r", null)),
        b == Left(useFailure),
        c == Right(Chunk("scope")),
        events.toList == List("r", "scope"),
        useFailure.getSuppressed.toList == List(releaseFailure)
      )
    },
    test("sync resources and ensuring enforce finalized lifecycle and aggregate failures") {
      var releases       = 0
      val releaseFailure = new RuntimeException("release")
      val reader         = Stream
        .fromAcquireRelease("r", (_: String) => { releases += 1; throw releaseFailure })(_ => Stream(1))
        .compile(0, Stream.DefaultBufferSize)
        .expectedSync
      val closeResult = scala.util.Try(reader.close()).failed.toOption
      val resetResult = scala.util.Try(reader.reset()).failed.toOption
      val ensured     = Stream(1).ensuring(releases += 1).compile(0, Stream.DefaultBufferSize).expectedSync
      ensured.close(); ensured.reset(); ensured.close()
      assertTrue(
        closeResult.contains(releaseFailure),
        resetResult.exists(_.isInstanceOf[UnsupportedOperationException]),
        releases == 3
      )
    },
    test("lifecycle reset rejects reentrancy and aggregates a deferred close") {
      val resetFailure                    = new RuntimeException("reset")
      val closeFailure                    = new RuntimeException("close")
      val finalizerFailure                = new RuntimeException("finalizer")
      var wrapped: Reader.SyncReader[Int] = null
      var nestedReset: Throwable          = null
      val source                          = new Reader.SyncReader[Int] {
        def isClosed                    = false
        def read[A >: Int](sentinel: A) = sentinel
        override def reset(): Unit      = {
          nestedReset = scala.util.Try(wrapped.reset()).failed.get
          wrapped.close()
          throw resetFailure
        }
        def close(): Unit = throw closeFailure
      }
      wrapped = Stream
        .fromReader[Nothing, Int](source)
        .ensuring(throw finalizerFailure)
        .compile(0, Stream.DefaultBufferSize)
        .expectedSync
      val result  = scala.util.Try(wrapped.reset()).failed.toOption
      val cleanup = resetFailure.getSuppressed.toList
      assertTrue(
        nestedReset.isInstanceOf[IllegalStateException],
        result.contains(resetFailure),
        cleanup.size == 1,
        cleanup.headOption.contains(closeFailure),
        closeFailure.getSuppressed.toList == List(finalizerFailure)
      )
    },
    test("stable and suspending async sources plus synchronous and scoped sources materialize lazily") {
      var made       = 0
      val stable     = Stream.fromReader[Nothing, Int] { made += 1; async(1) }
      val suspending = Stream.fromReaderAsync[Nothing, Int] { made += 1; Async.succeed(async(2)) }
      val direct     = Stream.fromReader[Nothing, Int](Reader.fromChunk(Chunk(3)))
      for {
        a <- runAsync(stable.runCollectAsync); b <- runAsync(suspending.runCollectAsync);
        c <- runAsync(direct.runCollectAsync)
      } yield assertTrue(a == Right(Chunk(1)), b == Right(Chunk(2)), c == Right(Chunk(3)), made == 2)
    },
    test("detached materialization peels every non-boundary structural stream node") {
      val scans        = materialize(Stream(1, 2).scan(0)(_ + _))
      val concat       = materialize(Stream(1) ++ Stream(2))
      val deferred     = materialize(Stream.suspend(Stream(3)))
      val zipped       = materialize(Stream(1) && Stream("a"))
      val acquired     = materialize(Stream.fromAcquireRelease("r", (_: String) => ())(_ => Stream(4)))
      val stateful     = materialize(Stream(5, 6).buffer(2))
      val repeated     = materialize(Stream(7).repeated.take(2))
      val asynchronous = materialize(Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(8))))
      val mapped       = materialize(Stream(9).mapAsync(n => Async.succeed(n + 1)))
      val resource     = materialize(Stream.fromResource(Resource(11))(n => Stream(n)))
      val chunked      = materialize(Stream(12, 13).chunked(2))
      for {
        a <- runAsync(scans.readAll[Int]()); b    <- runAsync(concat.readAll[Int]())
        c <- runAsync(deferred.readAll[Int]()); d <- runAsync(zipped.readAll[(Int, String)]())
        e <- runAsync(acquired.readAll[Int]()); f <- runAsync(stateful.readAll[Int]())
        g <- runAsync(repeated.readAll[Int]()); h <- runAsync(asynchronous.readAll[Int]())
        i <- runAsync(mapped.readAll[Int]()); j   <- runAsync(resource.readAll[Int]())
        k <- runAsync(chunked.readAll[Chunk[Int]]())
      } yield assertTrue(
        a == Chunk(0, 1, 3),
        b == Chunk(1, 2),
        c == Chunk(3),
        d == Chunk((1, "a")),
        e == Chunk(4),
        f == Chunk(5, 6),
        g == Chunk(7, 7),
        h == Chunk(8),
        i == Chunk(10),
        j == Chunk(11),
        k == Chunk(Chunk(12, 13))
      )
    },
    test("detached repetition materializes synchronous and asynchronous cycles") {
      val syncRepeated  = materialize(Stream(1).repeated)
      val asyncRepeated = materialize(asyncStream(2).repeated)
      for {
        a1 <- runAsync(syncRepeated.read[Int](-1)); a2  <- runAsync(syncRepeated.read[Int](-1))
        b1 <- runAsync(asyncRepeated.read[Int](-1)); b2 <- runAsync(asyncRepeated.read[Int](-1))
        _  <- runAsync(syncRepeated.close()); _         <- runAsync(asyncRepeated.close())
      } yield assertTrue(a1 == 1, a2 == 1, b1 == 2, b2 == 2)
    },
    test("reader-kind specialization and async compile boundaries select every compiler path") {
      val baseSync = Stream.fromChunk(Chunk(1)).foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val range    = Stream.range(1, 3).foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val source   = Stream
        .fromReader[Nothing, Int](Reader.fromChunk(Chunk(3)))
        .foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val filtered = Stream
        .fromReader[Nothing, Int](Reader.fromChunk(Chunk(1, 2)))
        .filter(_ > 1)
        .foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val mapped = Stream
        .fromReader[Nothing, Int](Reader.fromChunk(Chunk(4)))
        .map(_ + 1)
        .foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val mappedRange = Stream
        .range(4, 5)
        .map(_ + 1)
        .foldReaderKind(_.readAll[Int](), _.readAll[Int]().block)
      val asyncUnary           = Stream(1).mapAsync(Async.succeed)
      val asyncRecovery        = Stream.fail("x").catchAll(_ => Stream.unwrap(Async.succeed(Stream(1))))
      val asyncSource          = Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(1)))
      val stableAsyncSource    = Stream.unfoldAsync(0)(n => Async.succeed(if (n == 0) Some((1, 1)) else None))
      val unaryFrame           = asyncUnary.asInstanceOf[Stream.StackCompileNode].stackFrame(Stream.DefaultBufferSize)
      val unaryBoundary        = scala.util.Try(SyncInterpreter.fromStream(asyncUnary)).failed.toOption
      val recoveryBoundary     = scala.util.Try(SyncInterpreter.fromStream(asyncRecovery)).failed.toOption
      val sourceBoundary       = scala.util.Try(SyncInterpreter.fromStream(asyncSource)).failed.toOption
      val stableSourceBoundary = scala.util.Try(SyncInterpreter.fromStream(stableAsyncSource)).failed.toOption
      val deepUnary            = asyncUnary.compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val deepRecovery         = asyncRecovery.compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val deepFilter           = Stream(1, 2).filter(_ > 1).compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val deepCollect          = Stream(1, 2).collect { case 2 => 3 }.compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val deepAsyncFilter      = asyncStream(1, 2).filter(_ > 1).compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      val deepAsyncCollect     = asyncStream(1, 2).collect { case 2 => 3 }
        .compile(Stream.DepthCutoff, Stream.DefaultBufferSize)
      assertTrue(
        baseSync == Chunk(1),
        range == Chunk(1, 2),
        source == Chunk(3),
        filtered == Chunk(2),
        mapped == Chunk(5),
        mappedRange == Chunk(5),
        unaryBoundary.contains(Stream.AsyncBoundaryRequired),
        recoveryBoundary.contains(Stream.AsyncBoundaryRequired),
        sourceBoundary.contains(Stream.AsyncBoundaryRequired),
        stableSourceBoundary.contains(Stream.AsyncBoundaryRequired),
        unaryFrame ne null,
        deepUnary.isInstanceOf[Reader.AsyncReader[?]],
        deepRecovery.isInstanceOf[Reader.AsyncReader[?]],
        deepFilter.isInstanceOf[Reader.SyncReader[?]],
        deepCollect.isInstanceOf[Reader.SyncReader[?]],
        deepAsyncFilter.isInstanceOf[Reader.AsyncReader[?]],
        deepAsyncCollect.isInstanceOf[Reader.AsyncReader[?]]
      )
    },
    test("synchronous lifecycle reset failures remain primary without a deferred close") {
      val failure = new RuntimeException("reset")
      val source  = new Reader.SyncReader[Int] {
        def isClosed                    = false
        def read[A >: Int](sentinel: A) = sentinel
        override def reset(): Unit      = throw failure
        def close(): Unit               = ()
      }
      val reader = Stream
        .fromReader[Nothing, Int](source)
        .ensuring(())
        .compile(0, Stream.DefaultBufferSize)
        .expectedSync
      assertTrue(scala.util.Try(reader.reset()).failed.toOption.contains(failure))
    },
    test("synchronous async-transform rejection preserves primary and cleanup failures when driven") {
      val primary = new IllegalStateException("configure")
      val cleanup = new IllegalArgumentException("close")
      val source  = new Reader.SyncReader[Int] {
        def isClosed                    = false
        def read[A >: Int](sentinel: A) = sentinel
        def close(): Unit               = throw cleanup
      }
      val reader = Stream.transformSyncForTest[Int, Int](source)(_ => throw primary)
      runAsync(reader.read[Int](-1).either).map(result =>
        assertTrue(result == Left(primary), primary.getSuppressed.contains(cleanup))
      )
    },
    test("synchronous async-transform closes a source rejected before installation") {
      val primary = new IllegalStateException("jvm-type")
      val cleanup = new IllegalArgumentException("close")
      val source  = new Reader.SyncReader[Int] {
        override def jvmType: JvmType   = throw primary
        def isClosed                    = false
        def read[A >: Int](sentinel: A) = sentinel
        def close(): Unit               = throw cleanup
      }
      val reader = Stream.transformSyncForTest[Int, Int](source)(_ => ())
      runAsync(reader.read[Int](-1).either).map(result =>
        assertTrue(result == Left(primary), primary.getSuppressed.contains(cleanup))
      )
    },
    test("synchronous Resource uses an asynchronous stream and preserves finalizer failures") {
      val failure  = new RuntimeException("resource-finalizer")
      val resource = Resource.unique[Int] { scope => scope.defer(throw failure); 1 }
      runAsync(Stream.fromResource(resource)(n => asyncStream(n)).runCollectAsync.either).map(result =>
        assertTrue(result == Left(failure))
      )
    },
    test("repetition accepts a synchronous replacement after an asynchronous first cycle") {
      var cycle  = 0
      val source = Stream.fromReader[Nothing, Int] {
        cycle += 1
        if (cycle == 1) async(1) else Reader.single(2)
      }
      val reader = source.repeated.compile(0, Stream.DefaultBufferSize).expectedAsync
      for {
        a <- runAsync(reader.read[Int](-1)); b <- runAsync(reader.read[Int](-1)); _ <- runAsync(reader.close())
      } yield assertTrue(a == 1, b == 2, cycle == 2)
    },
    test("managed async terminals normalize synchronous sink throws") {
      val defect                   = new RuntimeException("sink-defect")
      val trusted                  = StreamError.sink("typed")
      def sink(failure: Throwable) = new Sink[String, Int, Unit] {
        private[streams] def drain(reader: Reader.SyncReader[_]): Unit         = ()
        private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Unit] = throw failure
      }
      for {
        a <- runAsync(Stream(1).runAsync(sink(defect)).either)
        b <- runAsync(Stream(1).runAsync(sink(trusted)).either)
      } yield assertTrue(a == Left(defect), b == Right(Left("typed")))
    },
    test("asynchronous resources preserve acquisition and multi-finalizer failures") {
      val acquireFailure  = new RuntimeException("acquire")
      val acquireCleanup1 = new RuntimeException("acquire-cleanup-1")
      val acquireCleanup2 = new RuntimeException("acquire-cleanup-2")
      val failedAcquire   = Resource.unique[Int] { scope =>
        scope.defer(throw acquireCleanup1)
        scope.defer(throw acquireCleanup2)
        throw acquireFailure
      }
      val releaseFailure1 = new RuntimeException("release-1")
      val releaseFailure2 = new RuntimeException("release-2")
      val failedRelease   = Resource.unique[Int] { scope =>
        scope.defer(throw releaseFailure1)
        scope.defer(throw releaseFailure2)
        1
      }
      for {
        acquired <-
          runAsync(
            Stream.fromResource(failedAcquire)(n => Stream.unwrap(Async.succeed(Stream(n)))).runDrainAsync.either
          )
        released <-
          runAsync(
            Stream.fromResource(failedRelease)(n => Stream.unwrap(Async.succeed(Stream(n)))).runDrainAsync.either
          )
      } yield assertTrue(
        acquired == Left(acquireFailure),
        acquireFailure.getSuppressed.toSet == Set[Throwable](acquireCleanup1, acquireCleanup2),
        released.left.toOption.contains(releaseFailure2),
        releaseFailure2.getSuppressed.toList == List(releaseFailure1)
      )
    },
    test("async zip preserves a read failure when both readers close successfully") {
      val failure = new RuntimeException("zip-read")
      val failed  = new Reader.AsyncReader[Int] {
        def close()                     = Async.succeed(())
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Int](sentinel: A) = Async.fail(failure)
      }
      val reader = Stream.compileToReader(Stream.fromReader[Nothing, Int](failed) && asyncStream(1)).expectedAsync
      for {
        first  <- runAsync(reader.read[(Int, Int)]((-1, -1)).either)
        second <- runAsync(reader.read[(Int, Int)]((-2, -2)).either)
      } yield assertTrue(first == Left(failure), second == Left(failure))
    },
    test("primitive collect after an async boundary converts the reference sentinel lane exactly") {
      val stream = Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(1, 2, 3))).collect {
        case n if n != 2 => n.toLong * 10L
      }
      runAsync(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(10L, 30L))))
    },
    test("deep async unary compilation remains stack safe and executes every transformation") {
      val depth = Stream.DepthCutoff + 32
      val deep  = (0 until depth).foldLeft(Stream(1): Stream[Nothing, Int]) { (stream, _) =>
        stream.mapAsync(n => Async.succeed(n + 1))
      }
      val transformed = deep
        .distinctByAsync(n => Async.succeed(n % 2))
        .takeWhileAsync(n => Async.succeed(n == depth + 1))
      runAsync(transformed.runCollectAsync).map(result => assertTrue(result == Right(Chunk(depth + 1))))
    },
    test("source-aware protected Int map keeps acquisition lazy and fresh across materializations") {
      var acquisitions = 0
      val stream       = Stream
        .fromReaderAsync[Nothing, Int] {
          acquisitions += 1
          Async.succeed(async(1, 2, 3): Reader[Int])
        }
        .map(_ + 1)
      val before = (stream.render, acquisitions, stream.elementRepresentation)
      for {
        first  <- runAsync(stream.runCollectAsync)
        second <- runAsync(stream.runFoldAsync(0L)((sum, value) => Async.succeed(sum + value)))
      } yield assertTrue(
        before == ("Stream.fromReaderAsync(...).map(...)", 0, ElementRepresentation.Known(JvmType.Int)),
        first == Right(Chunk(2, 3, 4)),
        second == Right(9L),
        acquisitions == 2
      )
    },
    test("fused protected async take-drop keeps acquisition lazy and fresh across materializations") {
      var acquisitions = 0
      val stream       = Stream
        .fromReaderAsync[Nothing, Int] {
          acquisitions += 1
          Async.succeed(async(1, 2, 3, 4): Reader[Int])
        }
        .drop(1)
        .take(2)
      val before = (stream.render, acquisitions, stream.elementRepresentation)
      for {
        first  <- runAsync(stream.runCollectAsync)
        second <- runAsync(stream.runCollectAsync)
      } yield assertTrue(
        before == ("Stream.fromReaderAsync(...).drop(1).take(2)", 0, ElementRepresentation.Known(JvmType.Int)),
        first == Right(Chunk(2, 3)),
        second == Right(Chunk(2, 3)),
        acquisitions == 2
      )
    },
    test("fused protected async take-drop preserves bounds and acquisition failures") {
      val thrown       = new RuntimeException("take-drop-acquire-thrown")
      val failed       = new RuntimeException("take-drop-acquire-failed")
      val negativeDrop = Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(1, 2): Reader[Int])).drop(-1).take(1)
      val negativeTake = Stream.fromReaderAsync[Nothing, Int](Async.succeed(async(1): Reader[Int])).drop(0).take(-1)
      val overflow     = Stream
        .fromReaderAsync[Nothing, Int](Async.succeed(async(1, 2): Reader[Int]))
        .drop(Long.MaxValue)
        .take(Long.MaxValue)
      for {
        a <- runAsync(negativeDrop.runCollectAsync)
        b <- runAsync(negativeTake.runCollectAsync)
        c <- runAsync(overflow.runCollectAsync)
        d <- runAsync(Stream.fromReaderAsync[Nothing, Int](throw thrown).drop(0).take(1).runDrainAsync.either)
        e <- runAsync(Stream.fromReaderAsync[Nothing, Int](Async.fail(failed)).drop(0).take(1).runDrainAsync.either)
      } yield assertTrue(
        a == Right(Chunk(1)),
        b == Right(Chunk.empty),
        c == Right(Chunk.empty),
        d == Left(thrown),
        e == Left(failed)
      )
    },
    test("synchronous scoped resources cross an async boundary and close after use failure") {
      val useFailure = new RuntimeException("resource-use")
      val releases   = new AtomicInteger
      val resource   = Resource.acquireRelease(1)(_ => releases.incrementAndGet())
      val stream     = Stream.fromResource(resource) { _ =>
        asyncStream(1).mapAsync[Int](_ => Async.fail(useFailure))
      }
      runAsync(stream.runDrainAsync.either).map(result => assertTrue(result == Left(useFailure), releases.get() == 1))
    }
  )
}
