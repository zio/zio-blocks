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

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncStatefulReader
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Deterministic JVM coverage for reachable asynchronous reader state-machine
 * residuals.
 */
object AsyncReaderReachableCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def poll[A](effect: Async[A]): Async[A] = effect match {
    case pending: Pollable[_] => pending.asInstanceOf[Pollable[A]].poll(new Runnable { def run(): Unit = () })
    case complete             => complete
  }

  private def closeBeforeCommit(reader: Reader.AsyncReader[_], commits: Int): Unit = {
    val remaining        = new AtomicInteger(commits)
    var hook: () => Unit = null
    hook = () =>
      if (remaining.decrementAndGet() == 0) reader.close().block
      else AsyncStatefulReader.beforeCommitForTest(reader, hook)
    AsyncStatefulReader.beforeCommitForTest(reader, hook)
  }

  private class GateReader[A](value: A, jt: JvmType = JvmType.AnyRef) extends Reader.AsyncReader[A] {
    val entered                                                               = new Completer[Unit]
    val gate                                                                  = new Completer[A]
    val closes                                                                = new AtomicInteger
    @volatile var closed                                                      = false
    override def jvmType                                                      = jt
    def close()                                                               = { closed = true; closes.incrementAndGet(); Async.succeed(()) }
    def isClosed                                                              = Async.succeed(closed)
    def readable()                                                            = { entered.succeed(()); gate.map(_ => true) }
    def read[B >: A](sentinel: B): Async[B]                                   = { entered.succeed(()); gate.map(_.asInstanceOf[B]) }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] = {
      entered.succeed(()); gate.map(_.asInstanceOf[Int].toLong)
    }
  }

  private final class CloseOnAvailability[A](value: A, jt: JvmType = JvmType.AnyRef) extends Reader.AsyncReader[A] {
    var boundary: Reader.AsyncReader[_] = null
    private var emitted                 = false
    private var closeOnAvailability     = true

    override def jvmType                      = jt
    def close()                               = Async.succeed(())
    def isClosed                              = Async.succeed(false)
    def readable()                            = Async.succeed(!emitted)
    override private[streams] def tryReadable = {
      if (closeOnAvailability) {
        closeOnAvailability = false
        boundary.close().block
      }
      Reader.Available
    }
    def read[B >: A](sentinel: B) =
      if (emitted) Async.succeed(sentinel)
      else { emitted = true; Async.succeed(value) }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      if (emitted) Async.succeed(sentinel)
      else { emitted = true; Async.succeed(value.asInstanceOf[Int].toLong) }
  }

  private final class CloseOnRead[A](value: A) extends Reader.AsyncReader[A] {
    var boundary: Reader.AsyncReader[_] = null
    private var emitted                 = false

    def close()                   = Async.succeed(())
    def isClosed                  = Async.succeed(false)
    def readable()                = Async.succeed(!emitted)
    def read[B >: A](sentinel: B) =
      if (emitted) Async.succeed(sentinel)
      else {
        emitted = true
        boundary.close().map(_ => value)
      }
  }

  private final class AvailableInts(limit: Int) extends Reader.AsyncReader[Int] {
    private var index = 0

    override def jvmType                      = JvmType.Int
    def close()                               = Async.succeed(())
    def isClosed                              = Async.succeed(index >= limit)
    def readable()                            = Async.succeed(index < limit)
    override private[streams] def tryReadable = if (index < limit) Reader.Available else Reader.Unavailable
    def read[A >: Int](sentinel: A)           =
      if (index >= limit) Async.succeed(sentinel)
      else { val value = index; index += 1; Async.succeed(value) }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      if (index >= limit) Async.succeed(sentinel)
      else { val value = index; index += 1; Async.succeed(value.toLong) }
  }

  private final class BlockingResetReader(firstCloseFailure: Throwable, secondCloseFailure: Throwable)
      extends Reader.SyncReader[Int] {
    val resetEntered = new Completer[Unit]
    val closeEntered = new Completer[Unit]
    val releaseReset = new CountDownLatch(1)
    val closes       = new AtomicInteger

    def close(): Unit = {
      val n = closes.incrementAndGet()
      closeEntered.succeed(())
      val failure = if (n == 1) firstCloseFailure else secondCloseFailure
      if (failure ne null) throw failure
    }
    def isClosed                       = false
    override def readable()            = false
    def read[A >: Int](sentinel: A): A = sentinel
    override def reset(): Unit         = {
      resetEntered.succeed(())
      releaseReset.await()
    }
  }

  def spec = suite("async reader reachable coverage")(
    test("cancelWithCleanup converts a synchronous custom Pollable cancellation throw") {
      val boom      = new IOException("cancel")
      val operation = new Async.Operation[Int] {
        def poll(onComplete: Runnable): Async[Int]   = this
        protected def cancelOperation(): Async[Unit] = throw boom
      }
      assertTrue(Async.cancelWithCleanup(operation).either.block == Left(boom))
    },
    test("primitive buffered exposes cache, EOF, close, and stale completion") {
      val cached = AsyncStatefulReader.buffered(source(1, 2, 3), 2)
      val dest   = Array.fill(3)(-1)
      val gate   = new GateReader[Int](9, JvmType.Int) {
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = {
          entered.succeed(()); this.gate.map(_.toLong)
        }
      }
      val stale   = AsyncStatefulReader.buffered(gate, 2)
      val pending = stale.readInt(-9L).start
      for {
        n1  <- runAsync(cached.readInts(dest, 0, 1)); ready <- runAsync(cached.readable())
        n2  <- runAsync(cached.readInts(dest, 1, 1)); n3    <- runAsync(cached.readInts(dest, 2, 1))
        eof <- runAsync(cached.readInts(dest, 0, 1))
        _   <- runAsync(gate.entered); _                    <- runAsync(stale.close()); _ = gate.gate.succeed(9)
        sv  <-
          runAsync(pending);
        _ <-
          runAsync(cached.close());
        closed <- runAsync(cached.readable())
      } yield assertTrue(
        n1 == 1,
        ready,
        n2 == 1,
        n3 == 1,
        eof == -1,
        dest.toVector == Vector(1, 2, 3),
        sv == -9L,
        !closed
      )
    },
    test("primitive intersperse preserves pending separators and crosses its ready budget") {
      val small = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val large = AsyncStatefulReader.intersperse(source((0 to 260): _*), -1)
      val one   = new Array[Int](1)
      for {
        _    <- runAsync(small.readInts(one, 0, 1)); pending <- runAsync(small.readable())
        tail <- runAsync(small.readN[Int](3)); values        <- runAsync(large.readAll[Int]())
        _    <- runAsync(small.close()); closed              <- runAsync(small.readable())
      } yield assertTrue(one(0) == 1, pending, tail == Chunk(0, 2), values.length == 521, values.last == 260, !closed)
    },
    test("primitive intersperse yields while draining a pending separator") {
      val reader = AsyncStatefulReader.intersperse(source((0 until 260): _*), -1)
      val prefix = new Array[Int](2)
      val rest   = new Array[Int](517)
      val last   = new Array[Int](1)
      for {
        p <- runAsync(reader.readInts(prefix, 0, prefix.length))
        n <- runAsync(reader.readInts(rest, 0, rest.length))
        l <- runAsync(reader.readInts(last, 0, 1))
        e <- runAsync(reader.readInts(last, 0, 1))
      } yield assertTrue(
        p == 2,
        prefix.sameElements(Array(0, -1)),
        n == 516,
        rest.head == 1,
        rest(n - 1) == -1,
        l == 1,
        last(0) == 259,
        e == -1
      )
    },
    test("primitive intersperse yields immediately after emitting a separator") {
      val reader = AsyncStatefulReader.intersperse(new AvailableInts(300), -1)
      val first  = new Array[Int](1)
      val rest   = new Array[Int](520)
      for {
        p <- runAsync(reader.readInts(first, 0, first.length))
        n <- runAsync(reader.readInts(rest, 0, rest.length))
      } yield assertTrue(
        p == 1,
        first(0) == 0,
        n == rest.length,
        rest(0) == -1,
        rest(1) == 1,
        rest(519) == 260
      )
    },
    test("generic stateful boundaries cover readable, EOF, budget, close, and stale reads") {
      val buffered = AsyncStatefulReader.buffered(source((0 to 260).map(_.toString): _*), 300)
      val chunked  = AsyncStatefulReader.chunked(source("a", "b", "c"), 2)
      val inter    = AsyncStatefulReader.intersperse(source("a", "b"), "|")
      val sliding  = AsyncStatefulReader.sliding(source((0 to 260).map(_.toString): _*), 257, 2)
      for {
        br <- runAsync(buffered.readable()); bv                      <- runAsync(buffered.readAll[String]())
        cv <- runAsync(chunked.readAll[Chunk[String]]()); iv         <- runAsync(inter.readAll[String]())
        sv <- runAsync(sliding.read[Chunk[String]](Chunk.empty)); se <- runAsync(sliding.readAll[Chunk[String]]())
        _  <- runAsync(buffered.close()); _                          <- runAsync(chunked.close()); _     <- runAsync(inter.close());
        _  <- runAsync(sliding.close())
        bc <- runAsync(buffered.readable()); cc                      <- runAsync(chunked.readable()); ic <- runAsync(inter.readable());
        sc <- runAsync(sliding.readable())
      } yield assertTrue(
        br,
        bv.length == 261,
        cv == Chunk(Chunk("a", "b"), Chunk("c")),
        iv == Chunk("a", "|", "b"),
        sv.length == 257,
        se.nonEmpty,
        !bc,
        !cc,
        !ic,
        !sc
      )
    },
    test("generic stateful readability distinguishes cached ready done and closed states") {
      val buffered    = AsyncStatefulReader.buffered(source("a", "b"), 2)
      val intersperse = AsyncStatefulReader.intersperse(source("a", "b"), "|")
      val sliding     = AsyncStatefulReader.sliding(source("a", "b"), 2, 1)
      for {
        initial <- runAsync(intersperse.readable())
        a       <- runAsync(intersperse.read[String]("eof"))
        sep     <- runAsync(intersperse.read[String]("eof"))
        cached  <- runAsync(intersperse.readable())
        b       <- runAsync(intersperse.read[String]("eof"))
        first   <- runAsync(buffered.read[String]("eof"))
        ready   <- runAsync(buffered.readable())
        second  <- runAsync(buffered.read[String]("eof"))
        sr      <- runAsync(sliding.readable())
        windows <- runAsync(sliding.readAll[Chunk[String]]())
        done    <- runAsync(sliding.readable())
        _       <- runAsync(intersperse.close())
        closed  <- runAsync(intersperse.readable())
      } yield assertTrue(
        initial,
        a == "a",
        sep == "|",
        cached,
        b == "b",
        first == "a",
        ready,
        second == "b",
        sr,
        windows == Chunk(Chunk("a", "b")),
        !done,
        !closed
      )
    },
    test("stateful loops reject values made stale by reentrant close") {
      val primitiveSource = new CloseOnAvailability(1, JvmType.Int)
      val primitive       = AsyncStatefulReader.intersperse(primitiveSource, 0)
      primitiveSource.boundary = primitive
      val bufferedSource = new CloseOnAvailability("value")
      val buffered       = AsyncStatefulReader.buffered(bufferedSource, 2)
      bufferedSource.boundary = buffered
      val chunkedSource = new CloseOnRead("value")
      val chunked       = AsyncStatefulReader.chunked(chunkedSource, 2)
      chunkedSource.boundary = chunked
      val ints = new Array[Int](2)
      for {
        pn <- runAsync(primitive.readInts(ints, 0, ints.length))
        bv <- runAsync(buffered.read[String]("stale"))
        cv <- runAsync(chunked.read[Chunk[String]](Chunk.empty))
      } yield assertTrue(pn == -1, bv == "stale", cv.isEmpty)
    },
    test("stateful commits reject generations closed between token acquisition and state inspection") {
      val primitive   = AsyncStatefulReader.buffered(source(1), 2)
      val intersperse = AsyncStatefulReader.intersperse(source("a", "b"), "|")
      val sliding     = AsyncStatefulReader.sliding(source("a", "b"), 2, 1)
      closeBeforeCommit(primitive, 1)
      val primitiveResult = primitive.readInt(-1L).block
      closeBeforeCommit(intersperse, 1)
      val intersperseResult = intersperse.read[String]("stale").block
      closeBeforeCommit(sliding, 3)
      val slidingResult = sliding.read[Chunk[String]](Chunk.empty).block
      assertTrue(primitiveResult == -1L, intersperseResult == "stale", slidingResult.isEmpty)
    },
    test("generic readUpToN stops at the exact requested limit") {
      val calls  = new AtomicInteger
      val reader = new Reader.AsyncReader[String] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        override private[streams] def tryReadable = Reader.Available
        def read[A >: String](sentinel: A)        = Async.succeed(calls.incrementAndGet().toString.asInstanceOf[A])
      }
      runAsync(reader.readUpToN[String](257)).map(result => assertTrue(result.length == 257, calls.get == 257))
    },
    test("sync/async inverse views preserve identity and repeated close failure") {
      val sync    = Reader.fromChunk(Chunk(1, 2))
      val inverse = sync.toAsync.toSync
      val async   = new Reader.AsyncReader[String] {
        def close()                        = Async.succeed(())
        def isClosed                       = Async.succeed(false)
        def readable()                     = Async.succeed(true)
        def read[A >: String](sentinel: A) = Async.succeed("value")
      }
      val asyncInverse = async.toSync.toAsync
      val failure      = new IOException("close")
      val bad          = new Reader.AsyncReader[Int] {
        def close()                     = Async.fail(failure); def isClosed = Async.succeed(false); def readable() = Async.succeed(false)
        def read[A >: Int](sentinel: A) = Async.succeed(sentinel)
      }.toSync
      val first = try { bad.close(); null }
      catch { case t: Throwable => t }
      val second = try { bad.close(); null }
      catch { case t: Throwable => t }
      assertTrue(inverse eq sync, asyncInverse eq async, first eq failure, second eq failure)
    },
    test("sync adapters and release wrappers delegate every residual scalar operation") {
      val bounded                                    = Reader.fromChunk(Chunk("a", "b", "c")).toAsync
      val bytes                                      = Reader.fromChunk(Chunk[Byte](0x81.toByte)).toAsync
      val releases                                   = new AtomicInteger
      def released[A](reader: Reader.AsyncReader[A]) =
        reader.withReleaseAsync { () => releases.incrementAndGet(); Async.succeed(()) }
      val refs    = released(source("r"))
      val chars   = released(source('c'))
      val shorts  = released(source[Short](2))
      val longs   = released(source(3L))
      val floats  = released(source(4.5f))
      val doubles = released(source(6.5d))
      for {
        n      <- runAsync(bounded.readN[String](2))
        upTo   <- runAsync(bounded.readUpToN[String](2))
        byte   <- runAsync(bytes.readByte())
        ref    <- runAsync(refs.read[String]("eof"))
        char   <- runAsync(chars.readChar(-1))
        short  <- runAsync(shorts.readShort(-1))
        long   <- runAsync(longs.readLong(-1L))
        float  <- runAsync(floats.readFloat(-1d))
        double <- runAsync(doubles.readDouble(-1d))
        _      <- runAsync(refs.close())
        _      <- runAsync(chars.close())
        _      <- runAsync(shorts.close())
        _      <- runAsync(longs.close())
        _      <- runAsync(floats.close())
        _      <- runAsync(doubles.close())
      } yield assertTrue(
        n == Chunk("a", "b"),
        upTo == Chunk("c"),
        byte == 129,
        refs.jvmType == JvmType.AnyRef,
        ref == "r",
        char == 'c'.toInt,
        short == 2,
        long == 3L,
        float == 4.5d,
        double == 6.5d,
        releases.get == 6
      )
    },
    test("sync adapter close joins a reset and preserves both close failures") {
      def exercise(firstFailure: Throwable, secondFailure: Throwable) = {
        val source = new BlockingResetReader(firstFailure, secondFailure)
        val reader = source.toAsync
        val reset  = reader.reset().start
        for {
          _    <- runAsync(source.resetEntered)
          close = reader.close().start
          _    <- runAsync(source.closeEntered)
          _     = source.releaseReset.countDown()
          rr   <- runAsync(reset).either
          cr   <- runAsync(close).either
        } yield (source, rr, cr)
      }
      val primary   = new IOException("primary-close")
      val secondary = new IOException("secondary-close")
      val only      = new IOException("second-close")
      val shared    = new IOException("shared-close")
      for {
        both <- exercise(primary, secondary)
        one  <- exercise(null, only)
        same <- exercise(shared, shared)
      } yield assertTrue(
        both._1.closes.get == 2,
        both._2.isLeft,
        both._3.left.exists(_ eq primary),
        primary.getSuppressed.toVector == Vector(secondary),
        one._1.closes.get == 2,
        one._2.isLeft,
        one._3.left.exists(_ eq only),
        same._1.closes.get == 2,
        same._2.isLeft,
        same._3.left.exists(_ eq shared),
        shared.getSuppressed.isEmpty
      )
    },
    test("stateful primitive availability distinguishes delegated cached pending and closed states") {
      val buffered    = AsyncStatefulReader.buffered(source(1, 2), 2)
      val intersperse = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val one         = new Array[Int](1)
      val bd          = buffered.tryReadable
      val id          = intersperse.tryReadable
      for {
        _ <- runAsync(buffered.readInts(one, 0, 1))
        bc = buffered.tryReadable
        _ <- runAsync(intersperse.readInts(one, 0, 1))
        _ <- runAsync(intersperse.readInts(one, 0, 1))
        ip = intersperse.tryReadable
        _ <- runAsync(buffered.close())
        _ <- runAsync(intersperse.close())
        bx = buffered.tryReadable
        ix = intersperse.tryReadable
      } yield assertTrue(
        bd == Reader.Available,
        id == Reader.Available,
        bc == Reader.Available,
        ip == Reader.Available,
        bx == Reader.Unavailable,
        ix == Reader.Unavailable
      )
    },
    test("bounded reads invoke EOF once and do not pull beyond their bound") {
      val calls  = new AtomicInteger
      val reader = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(()); def isClosed = Async.succeed(false); def readable() = Async.succeed(true)
        override private[streams] def tryReadable = Reader.Available
        def read[A >: Int](sentinel: A)           = {
          val n = calls.getAndIncrement(); Async.succeed((if (n < 3) n else sentinel).asInstanceOf[A])
        }
      }
      for { exact <- runAsync(reader.readN[Int](2)); rest <- runAsync(reader.readN[Int](4)) } yield assertTrue(
        exact == Chunk(0, 1),
        rest == Chunk(2),
        calls.get == 4
      )
    },
    test("concat resets transitions and memoizes construction and existing failures") {
      val constructions = new AtomicInteger
      val boom          = new IOException("tail")
      val reader        = source(1).concatAsyncWithJvmType(
        () => { constructions.incrementAndGet(); Async.succeed(source(2)) },
        JvmType.Int
      )
      val failed = source[Int]().concatAsyncWithJvmType(() => throw boom, JvmType.Int)
      val inner  = source(10).concatAsyncWithJvmType(() => Async.succeed(source(11)), JvmType.Int)
      val first  = inner.readInt(-1L).block
      val outer  = inner.concatAsyncWithJvmType(() => Async.succeed(source(12)), JvmType.Int)
      for {
        one <- runAsync(reader.readAll[Int]()); _        <- runAsync(reader.reset()); two <- runAsync(reader.readAll[Int]())
        f1  <- runAsync(failed.read[Int](-1)).either; f2 <- runAsync(failed.read[Int](-2)).either
        fr  <- runAsync(failed.reset()).either
        _   <- runAsync(outer.reset()); nested           <- runAsync(outer.readAll[Int]())
      } yield assertTrue(
        one == Chunk(1, 2),
        two == Chunk(1, 2),
        constructions.get == 2,
        f1.left.exists(_ eq boom),
        f2.left.exists(_ eq boom),
        fr.left.exists(_ eq boom),
        first == 10L,
        nested == Chunk(10, 11, 12)
      )
    },
    test("concat readable normalizes synchronous child construction failures") {
      val boom = new IOException("readable")
      val head = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable(): Async[Boolean]            = throw boom
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader = head.concatAsync(() => Async.succeed(source(1)))
      runAsync(reader.readable()).either.map(result => assertTrue(result.left.exists(_ eq boom)))
    },
    test("concat next rejects a reentrant close and an already-recorded tail failure") {
      var closingOwner: Reader.AsyncReader[Int] = null
      val closingHead                           = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = closingOwner.close().map(_ => sentinel)
      }
      val closed = closingHead.concatAsyncWithJvmType(() => Async.succeed(source(1)), JvmType.Int)
      closingOwner = closed

      val boom                                  = new IOException("recursive-tail")
      var failingOwner: Reader.AsyncReader[Int] = null
      val failingHead                           = new Reader.AsyncReader[Int] {
        private var recurse                       = true
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          if (!recurse) Async.succeed(sentinel)
          else {
            recurse = false
            failingOwner.read[A](sentinel).either.map(_ => sentinel)
          }
      }
      val failed = failingHead.concatAsyncWithJvmType(() => throw boom, JvmType.Int)
      failingOwner = failed
      for {
        closedResult <- runAsync(closed.readInt(-1L))
        failedResult <- runAsync(failed.readInt(-1L)).either
      } yield assertTrue(closedResult == -1L, failedResult.left.exists(_ eq boom))
    },
    test("concat next arbitrates close and sticky failure after accepting EOF") {
      val closed = source[Int]().concatAsyncWithJvmType(() => Async.succeed(source(1)), JvmType.Int)
      Reader.beforeConcatNextForTest(closed, () => closed.close().block)

      val boom   = new IOException("concurrent-tail")
      val failed = source[Int]().concatAsyncWithJvmType(() => throw boom, JvmType.Int)
      Reader.beforeConcatNextForTest(failed, () => { val _ = failed.read[Int](-1).either.block })

      for {
        closedResult <- runAsync(closed.read[Int](-1))
        failedResult <- runAsync(failed.read[Int](-1)).either
      } yield assertTrue(closedResult == -1, closed.isClosed.block, failedResult.left.exists(_ eq boom))
    },
    test("nested concat rejected reset closes its nested head") {
      val resetEntered = new Completer[Unit]
      val resetGate    = new Completer[Unit]
      val base         = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = { resetEntered.succeed(()); resetGate }
      }
      val inner = base.concatAsyncWithJvmType(() => Async.succeed(source(1)), JvmType.Int)
      val _     = inner.readInt(-1L).block
      val outer = inner.concatAsyncWithJvmType(() => Async.succeed(source(2)), JvmType.Int)
      val reset = outer.reset().start
      for {
        _           <- runAsync(resetEntered)
        closing      = outer.close()
        closePending = poll(closing).isInstanceOf[Pollable[_]]
        _            = resetGate.succeed(())
        rr          <- runAsync(reset).either
        _           <- runAsync(closing)
      } yield assertTrue(closePending, rr.isLeft, outer.isClosed.block)
    },
    test("concat reset finalizer tolerates an already-cleared active owner") {
      val resetEntered = new Completer[Unit]
      val resetGate    = new Completer[Unit]
      val head         = new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = { resetEntered.succeed(()); resetGate }
      }
      val reader = head.concatAsync(() => Async.succeed(source(1)))
      val reset  = reader.reset().start
      for {
        _  <- runAsync(resetEntered)
        _   = Reader.clearConcatActiveResetForTest(reader)
        _   = resetGate.succeed(())
        rr <- runAsync(reset).either
      } yield assertTrue(rr == Right(()), !reader.isClosed.block)
    },
    test("AsyncRelease delegates every primitive bulk and scalar lane") {
      val releases                              = new AtomicInteger
      def released[A](r: Reader.AsyncReader[A]) = r.withReleaseAsync { () =>
        releases.incrementAndGet(); Async.succeed(())
      }
      val z = released(source(true)); val b = released(source[Byte](1)); val c = released(source('a'));
      val s = released(source[Short](2))
      val i = released(source(3)); val l    = released(source(4L)); val f      = released(source(5f));
      val d = released(source(6d))
      for {
        zv <- runAsync(z.readBoolean(-1)); bv                   <- runAsync(b.readBytes(new Array[Byte](1), 0, 1));
        cv <- runAsync(c.readChar(-1)); sv                      <- runAsync(s.readShort(-1))
        iv <- runAsync(i.readInts(new Array[Int](1), 0, 1)); lv <- runAsync(l.readLongs(new Array[Long](1), 0, 1));
        fv <- runAsync(f.readFloats(new Array[Float](1), 0, 1));
        dv <- runAsync(d.readDoubles(new Array[Double](1), 0, 1))
        _  <- runAsync(z.close()); _                            <- runAsync(b.close()); _ <- runAsync(c.close()); _ <- runAsync(s.close());
        _  <- runAsync(i.close()); _                            <- runAsync(l.close()); _ <- runAsync(f.close()); _ <- runAsync(d.close())
      } yield assertTrue(
        zv == 1,
        bv == 1,
        cv == 'a'.toInt,
        sv == 2,
        iv == 1,
        lv == 1,
        fv == 1,
        dv == 1,
        releases.get == 8
      )
    },
    test("AsyncRepeated rejects overlapping readable/read and discards stale completion") {
      val inner    = new GateReader[String]("value")
      val reader   = Reader.repeated(inner)
      val readable = reader.readable().start
      for {
        _ <- runAsync(inner.entered); overlap <- runAsync(reader.read[String]("eof")).either
        _ <- runAsync(reader.close()); _       = inner.gate.succeed("value"); stale <- runAsync(readable)
      } yield assertTrue(overlap.left.exists(_.isInstanceOf[IllegalStateException]), !stale, inner.closes.get == 1)
    },
    test("AsyncRepeated replays terminal failure and discards a stale read") {
      val boom   = new IOException("repeat")
      val failed = Reader.repeated(new Reader.AsyncReader[Int] {
        def close()                               = Async.succeed(())
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.fail(boom)
      })
      val inner    = new GateReader[Int](1, JvmType.Int)
      val repeated = Reader.repeated(inner)
      val active   = repeated.read[Int](-1).start
      for {
        _      <- runAsync(inner.entered)
        first  <- runAsync(failed.read[Int](-1)).either
        replay <- runAsync(failed.read[Int](-1)).either
        _      <- runAsync(repeated.close())
        _       = inner.gate.succeed(1)
        stale  <- runAsync(active)
      } yield assertTrue(first == Left(boom), replay == Left(boom), stale == -1)
    },
    test("AsyncRepeated rejects readable and read admission after close wins") {
      val readable = Reader.repeated(source(1))
      val reading  = Reader.repeated(source(2))
      Reader.beforeRepeatedAdmissionForTest(readable, () => readable.close().block)
      Reader.beforeRepeatedAdmissionForTest(reading, () => reading.close().block)
      for {
        ready <- runAsync(readable.readable())
        value <- runAsync(reading.read[Int](-1))
      } yield assertTrue(!ready, value == -1)
    },
    test("AsyncRepeated observes close at loop entry") {
      val reader = Reader.repeated(source(1))
      Reader.beforeRepeatedLoopForTest(reader, () => { val _ = poll(reader.close()); () })
      for {
        value <- runAsync(reader.read[Int](-1))
        _     <- runAsync(reader.close())
      } yield assertTrue(value == -1)
    },
    test("Unfold cancellation, active close, EOF, reset, and stale settlement are stable") {
      val entered = new Completer[Unit]
      val gate    = new Completer[Option[(Int, Int)]]
      val reader  = Reader.unfoldAsync[Int, Int](0) { _ => entered.succeed(()); gate }
      val active  = reader.read[Int](-1).start
      for {
        _     <- runAsync(entered); available <- runAsync(reader.readable()); _ <- runAsync(reader.close());
        _      = gate.succeed(Some((1, 1)))
        stale <- runAsync(active); closed     <- runAsync(reader.isClosed); _   <- runAsync(reader.reset());
        ready <- runAsync(reader.readable())
      } yield assertTrue(!available, stale == -1, closed, ready)
    },
    test("Unfold immediate views cancel cleanly and EOF and terminal failure are replayed") {
      val eof    = Reader.unfoldAsync[Int, Int](0)(_ => Async.succeed(None))
      val boom   = new IOException("unfold")
      val failed = Reader.unfoldAsync[Int, Int](0)(_ => Async.fail(boom))
      val closed = eof.isClosed.asInstanceOf[Pollable[Boolean]]
      val ready  = eof.readable().asInstanceOf[Pollable[Boolean]]
      for {
        _      <- runAsync(Async.cancelWithCleanup(closed))
        _      <- runAsync(Async.cancelWithCleanup(ready))
        end    <- runAsync(eof.read[Int](-1))
        first  <- runAsync(failed.read[Int](-1)).either
        replay <- runAsync(failed.read[Int](-1)).either
      } yield assertTrue(end == -1, first == Left(boom), replay == Left(boom))
    },
    test("Unfold cancels a run when close wins registration") {
      val inactive = Reader.unfoldAsync[Int, Int](0)(_ => Async.succeed(Some((1, 1))))
      Reader.invalidateUnfoldRunRegistrationForTest(inactive)
      val reader = Reader.unfoldAsync[Int, Int](0)(_ => Async.succeed(Some((1, 1))))
      Reader.beforeUnfoldRunRegistrationForTest(reader, () => Reader.invalidateUnfoldRunRegistrationForTest(reader))
      for {
        value <- runAsync(reader.read[Int](-1))
        _     <- runAsync(reader.close())
      } yield assertTrue(value == -1, reader.isClosed.block, inactive.isClosed.block)
    },
    test("Unfold releases a pull cancelled between acquisition and use") {
      val reader   = Reader.unfoldAsync[Int, Int](0)(_ => Async.succeed(Some((1, 1))))
      val acquired = new CountDownLatch(1)
      val release  = new CountDownLatch(1)
      Reader.beforeUnfoldPullAcquiredForTest(reader, () => { acquired.countDown(); release.await() })
      val pull = reader.read[Int](-1).asInstanceOf[Pollable[Int]]
      val _    = pull.start
      acquired.await()
      val cleanup = Async.cancelWithCleanup(pull)
      release.countDown()
      for {
        _ <- runAsync(cleanup)
        _ <- runAsync(reader.close())
      } yield assertTrue(reader.isClosed.block)
    },
    test("Unfold stale settlement does not clear a newer pull") {
      val calls        = new AtomicInteger
      val firstSettle  = new CountDownLatch(1)
      val releaseFirst = new CountDownLatch(1)
      val secondCall   = new CountDownLatch(1)
      val reader       = Reader.unfoldAsync[Int, Int](0) { state =>
        if (calls.incrementAndGet() == 2) secondCall.countDown()
        Async.succeed(Some((state, state + 1)))
      }
      Reader.beforeUnfoldSettleForTest(reader, () => { firstSettle.countDown(); releaseFirst.await() })
      val first = reader.read[Int](-1).start
      firstSettle.await()
      val second = reader.read[Int](-1).start
      secondCall.await()
      releaseFirst.countDown()
      for {
        a <- runAsync(first)
        b <- runAsync(second)
      } yield assertTrue(a == 0, b == 1, calls.get == 2)
    }
  ) @@ TestAspect.sequential
}
