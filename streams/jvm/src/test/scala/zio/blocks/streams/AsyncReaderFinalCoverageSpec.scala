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
import zio.blocks.streams.internal.AsyncStatefulReader
import zio.blocks.streams.io.Reader
import zio.test._

/** Final deterministic coverage of the small asynchronous reader adapters. */
object AsyncReaderFinalCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class LongProbe extends Async.LongStepFold[Unit] {
    var cause: Throwable        = null
    var failed                  = false
    var pending: Pollable[Long] = null
    var trusted                 = false
    var value                   = 0L

    def failureLong(error: Throwable): Unit                 = { cause = error; failed = true }
    def pendingLong(effect: Pollable[Long]): Unit           = pending = effect
    def successLong(result: Long): Unit                     = value = result
    override def trustedFailureLong(error: Throwable): Unit = { cause = error; failed = true; trusted = true }
  }

  def spec = suite("async reader final coverage")(
    test("MemoizedClose publishes one synchronous result and permits a reentrant claimant") {
      val claims                      = new AtomicInteger
      val closes                      = new AtomicInteger
      var owner: Reader.MemoizedClose = null
      owner = new Reader.MemoizedClose(
        () => claims.incrementAndGet(),
        () => { closes.incrementAndGet(); owner.closeClaimed() },
        synchronous = true,
        isReentrant = () => true
      )
      for {
        _ <- runAsync(owner.close())
        _ <- runAsync(owner.closeClaimed())
      } yield assertTrue(claims.get == 1, closes.get == 1)
    },
    test("SyncToAsyncReader delegates bounded and byte reads and memoizes EOF") {
      val bounded = Reader.fromChunk(Chunk(1, 2, 3)).toAsync
      val bytes   = Reader.fromChunk(Chunk[Byte](0x81.toByte)).toAsync
      for {
        _    <- runAsync(bounded.skip(0))
        _    <- runAsync(bounded.skip(-1))
        zero <- runAsync(bounded.readN[Int](0))
        two  <- runAsync(bounded.readUpToN[Int](2))
        one  <- runAsync(bounded.readN[Int](2))
        eof  <- runAsync(bounded.readN[Int](1))
        b    <- runAsync(bytes.readByte())
        be   <- runAsync(bytes.readByte())
      } yield assertTrue(zero.isEmpty, two == Chunk(1, 2), one == Chunk(3), eof.isEmpty, b == 129, be == -1)
    },
    test("AsyncConcatReader closes segments, installs tails, and resets to its head") {
      val tailCalls = new AtomicInteger
      val reader    = source(1).concatAsync { () =>
        tailCalls.incrementAndGet()
        Async.succeed(Reader.fromChunk(Chunk(2)))
      }
      for {
        first  <- runAsync(reader.readAll[Int]())
        _      <- runAsync(reader.reset())
        second <- runAsync(reader.readAll[Int]())
        _      <- runAsync(reader.close())
        end    <- runAsync(reader.read[Int](-1))
      } yield assertTrue(first == Chunk(1, 2), second == Chunk(1, 2), tailCalls.get == 2, end == -1)
    },
    test("primitive Int observation fuses mapped values, EOF, pending reads, and callback defects") {
      val ready     = new Reader.MappedAsyncIntInt(source(1), _ + 1)
      val value     = new LongProbe
      val eof       = new LongProbe
      val gate      = new Completer[Long]
      val suspended = new Reader.AsyncReader[Int] {
        def close()                                                    = Async.succeed(())
        override def jvmType                                           = JvmType.Int
        def isClosed                                                   = Async.succeed(false)
        def read[A >: Int](sentinel: A)                                = gate.map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = gate
        def readable()                                                 = Async.succeed(true)
      }
      val mappedPending = new Reader.MappedAsyncIntInt(suspended, _ + 1)
      val pending       = new LongProbe
      val forged        = new zio.blocks.streams.internal.StreamError("forged")
      val defective     = new LongProbe
      ready.pollIntPhysical(-1L, () => (), value)
      ready.pollIntPhysical(-1L, () => (), eof)
      mappedPending.pollIntPhysical(-1L, () => (), pending)
      new Reader.MappedAsyncIntInt(source(1), _ => throw forged).pollIntPhysical(-1L, () => (), defective)
      gate.succeed(2L)
      for {
        resumed <- runAsync(pending.pending)
      } yield assertTrue(
        value.value == 2L,
        eof.value == -1L,
        resumed == 3L,
        defective.failed,
        !defective.trusted,
        defective.cause.isInstanceOf[zio.blocks.streams.internal.StreamError],
        defective.cause ne forged
      )
    },
    test("primitive Int observation preserves concat transitions and trusted null failure") {
      val concat = source(1).concatAsyncWithJvmType(() => Async.succeed(source(2)), JvmType.Int)
      val first  = new LongProbe
      val second = new LongProbe
      concat.pollIntPhysical(-1L, () => (), first)
      concat.pollIntPhysical(-1L, () => (), second)
      val failing = new Reader.AsyncReader[Int] {
        def close()                     = Async.succeed(())
        override def jvmType            = JvmType.Int
        def isClosed                    = Async.succeed(false)
        def read[A >: Int](sentinel: A) = Async.failTrusted(null)
        override private[streams] def pollInt(
          sentinel: Long,
          onComplete: Runnable,
          observer: Async.LongStepFold[Unit]
        ): Unit        = observer.trustedFailureLong(null)
        def readable() = Async.succeed(true)
      }
      val trusted = new LongProbe
      new Reader.MappedAsyncIntInt(failing, identity).pollIntPhysical(-1L, () => (), trusted)
      val result =
        if (second.pending eq null) Async.succeed(second.value)
        else second.pending
      for {
        resumed <- runAsync(result)
      } yield assertTrue(first.value == 1L, resumed == 2L, trusted.failed, trusted.trusted, trusted.cause == null)
    },
    test("concat exact polling and bulk lanes preserve terminal and construction failures") {
      val stickyFailure = new RuntimeException("sticky")
      val sticky        = Reader.closed.toAsync.concatAsyncWithJvmType[Int](() => Async.fail(stickyFailure), JvmType.Int)
      val stickyProbe   = new LongProbe
      val pollFailure   = new RuntimeException("poll")
      val throwingPoll  = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        override def jvmType                      = JvmType.Int
        def isClosed                              = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override private[streams] def pollInt(
          sentinel: Long,
          onComplete: Runnable,
          observer: Async.LongStepFold[Unit]
        ): Unit        = throw pollFailure
        def readable() = Async.succeed(true)
      }.concatReaderWithJvmType(() => source(1), JvmType.Int)
      val pollProbe    = new LongProbe
      val bulkFailure  = new RuntimeException("bulk")
      val throwingBulk = new Reader.AsyncReader[Long] {
        def close()                                = Async.succeed(())
        override def jvmType                       = JvmType.Long
        def isClosed                               = Async.succeed(false)
        def read[A >: Long](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit
          ev: Long <:< Long
        ): Async[Int]  = throw bulkFailure
        def readable() = Async.succeed(true)
      }.concatReaderWithJvmType(() => source(1L), JvmType.Long)
      val longs        = source(1L).concatReaderWithJvmType(() => source(2L), JvmType.Long)
      val doubles      = source(1d).concatReaderWithJvmType(() => source(2d), JvmType.Double)
      val longValues   = new Array[Long](2)
      val doubleValues = new Array[Double](2)
      for {
        stickyResult <- runAsync(sticky.readInt(-1L)).either
        _             = sticky.pollIntPhysical(-1L, () => (), stickyProbe)
        _             = throwingPoll.pollIntPhysical(-1L, () => (), pollProbe)
        failedBulk   <- runAsync(throwingBulk.readLongs(new Array[Long](1), 0, 1)).either
        longZero     <- runAsync(longs.readLongs(longValues, 0, 0))
        longFirst    <- runAsync(longs.readLongs(longValues, 0, 2))
        longSecond   <- runAsync(longs.readLongs(longValues, 1, 1))
        doubleZero   <- runAsync(doubles.readDoubles(doubleValues, 0, 0))
        doubleFirst  <- runAsync(doubles.readDoubles(doubleValues, 0, 2))
        doubleSecond <- runAsync(doubles.readDoubles(doubleValues, 1, 1))
      } yield assertTrue(
        stickyResult == Left(stickyFailure),
        stickyProbe.failed,
        stickyProbe.cause eq stickyFailure,
        pollProbe.failed,
        pollProbe.cause eq pollFailure,
        failedBulk == Left(bulkFailure),
        longZero == 0,
        longFirst == 1,
        longSecond == 1,
        longValues.sameElements(Array(1L, 2L)),
        doubleZero == 0,
        doubleFirst == 1,
        doubleSecond == 1,
        doubleValues.sameElements(Array(1d, 2d))
      )
    },
    test("primitive Int to reference mapping handles ready pending EOF and direct dispatch") {
      val ready     = new Reader.MappedAsyncIntRef(source(1), (value: Int) => s"v$value")
      val entered   = new Completer[Unit]
      val gate      = new Completer[Long]
      val suspended = new Reader.AsyncReader[Int] {
        def close()                                                    = Async.succeed(())
        override def jvmType                                           = JvmType.Int
        def isClosed                                                   = Async.succeed(false)
        def read[A >: Int](sentinel: A)                                = gate.map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = { entered.succeed(()); gate }
        def readable()                                                 = Async.succeed(true)
      }
      val pending = new Reader.MappedAsyncIntRef(suspended, (value: Int) => s"v$value").read[Any](null)
      val direct  = Stream.compileToReader(
        Stream.fromReader[Nothing, Int](source(1)).map(value => s"v$value")
      )
      for {
        first   <- runAsync(ready.read[Any](null))
        eof     <- runAsync(ready.read[Any](null))
        running  = pending.start
        _       <- runAsync(entered)
        _        = gate.succeed(2L)
        resumed <- runAsync(running)
      } yield assertTrue(
        first == "v1",
        eof == null,
        resumed == "v2",
        direct.isInstanceOf[Reader.MappedAsyncIntRef[?]]
      )
    },
    test("primitive Int to reference mapping launders callback StreamErrors") {
      val forged     = new zio.blocks.streams.internal.StreamError("forged")
      val defective  = new Reader.MappedAsyncIntRef(source(1), (_: Int) => throw forged)
      val failedRead = defective.read[Any](null).either
      runAsync(failedRead).map {
        case Left(error) => assertTrue(error.isInstanceOf[zio.blocks.streams.internal.StreamError], error ne forged)
        case Right(_)    => assertTrue(false)
      }
    },
    test("primitive Int to reference mapping preserves trusted failures and delegates controls") {
      val resets        = new AtomicInteger
      val failingSource = new Reader.AsyncReader[Int] {
        def close()                                                    = Async.succeed(())
        override def jvmType                                           = JvmType.Int
        def isClosed                                                   = Async.succeed(false)
        def read[A >: Int](sentinel: A)                                = Async.failTrusted(null)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = Async.failTrusted(null)
        def readable()                                                 = Async.succeed(true)
        override def reset()                                           = { resets.incrementAndGet(); Async.succeed(()) }
        override def setLimit(n: Long)                                 = Async.succeed(n == 1L)
        override def setRepeat()                                       = Async.succeed(true)
        override def setSkip(n: Long)                                  = Async.succeed(n == 2L)
        override def skip(n: Long)                                     = { resets.addAndGet(n.toInt); Async.succeed(()) }
      }
      val mapped = new Reader.MappedAsyncIntRef(failingSource, (value: Int) => value.toString)
      for {
        failure <- runAsync(mapped.read[Any](null).either)
        _       <- runAsync(mapped.reset())
        limit   <- runAsync(mapped.setLimit(1L))
        repeat  <- runAsync(mapped.setRepeat())
        setSkip <- runAsync(mapped.setSkip(2L))
        _       <- runAsync(mapped.skip(3L))
      } yield assertTrue(failure == Left(null), limit, repeat, setSkip, resets.get == 4)
    },
    test("AsyncReleaseReader delegates uncovered primitive lanes and releases exactly once") {
      val releases                                   = new AtomicInteger
      def released[A](reader: Reader.AsyncReader[A]) =
        reader.withReleaseAsync { () => releases.incrementAndGet(); Async.succeed(()) }
      val chars   = released(source('x'))
      val shorts  = released(source[Short](7))
      val longs   = released(source(8L))
      val floats  = released(source(1.5f))
      val doubles = released(source(2.5d))
      val refs    = released(source("x"))
      for {
        c <- runAsync(chars.readChar(-1)); s   <- runAsync(shorts.readShort(-1)); l <- runAsync(longs.readLong(-1))
        f <- runAsync(floats.readFloat(-1)); d <- runAsync(doubles.readDouble(-1));
        r <- runAsync(refs.read[String]("eof"))
        _ <- runAsync(chars.close()); _        <- runAsync(shorts.close()); _       <- runAsync(longs.close())
        _ <- runAsync(floats.close()); _       <- runAsync(doubles.close()); _      <- runAsync(refs.close());
        _ <- runAsync(refs.close())
      } yield assertTrue(c == 'x'.toInt, s == 7, l == 8L, f == 1.5d, d == 2.5d, r == "x", releases.get == 6)
    },
    test("AsyncRepeated reports an overlapping pull and repeats after EOF") {
      val gate    = new Completer[Any]
      val entered = new Completer[Unit]
      val inner   = new Reader.AsyncReader[Int] {
        def close()                     = { gate.succeed(new Object); Async.succeed(()) }
        override def reset()            = Async.succeed(())
        def isClosed                    = Async.succeed(false)
        def readable()                  = Async.succeed(true)
        def read[A >: Int](sentinel: A) = { entered.succeed(()); gate.map(_.asInstanceOf[A]) }
      }
      val reader = Reader.repeated(inner)
      val first  = reader.read[Any](null).start
      for {
        _       <- runAsync(entered)
        overlap <- runAsync(reader.read[Any](null)).either
        _       <- runAsync(reader.close())
        _       <- runAsync(first)
      } yield assertTrue(overlap.left.exists(_.isInstanceOf[IllegalStateException]))
    },
    test("UnfoldAsync PullResource settles success, EOF, reset, and unpolled cancellation") {
      val calls  = new AtomicInteger
      val reader = Reader.unfoldAsync(0) { n =>
        calls.incrementAndGet()
        Async.succeed(if (n < 2) Some((n, n + 1)) else None)
      }
      val abandoned = reader.read[Int](-9).asInstanceOf[Pollable[Int]]
      for {
        _      <- runAsync(Async.cancelWithCleanup(abandoned))
        values <- runAsync(reader.readAll[Int]())
        _      <- runAsync(reader.reset())
        again  <- runAsync(reader.read[Int](-1))
        _      <- runAsync(reader.close())
      } yield assertTrue(values == Chunk(0, 1), again == 0, calls.get == 4)
    },
    test("AsyncStatefulReader exercises primitive pending availability and generic cache EOF") {
      val primitive = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val generic   = AsyncStatefulReader.buffered(source("a", "b"), 2)
      val one       = new Array[Int](1)
      for {
        n  <- runAsync(primitive.readInts(one, 0, 1))
        pr <- runAsync(primitive.readable())
        pv <- runAsync(primitive.readN[Int](3))
        ga <- runAsync(generic.read[String]("eof")); gr <- runAsync(generic.readable())
        gb <- runAsync(generic.read[String]("eof")); ge <- runAsync(generic.read[String]("eof"))
        _  <- runAsync(primitive.close()); pc           <- runAsync(primitive.readable())
      } yield assertTrue(n == 1, one(0) == 1, pr, pv == Chunk(0, 2), ga == "a", gr, gb == "b", ge == "eof", !pc)
    }
  ) @@ TestAspect.sequential
}
