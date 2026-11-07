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

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.{AsyncConcurrentReaders, AsyncStatefulReader}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Exhaustive residual coverage for the two native asynchronous reader
 * boundaries.
 */
object AsyncInternalReaderExhaustiveCoverageSpec extends ZIOSpecDefault {
  private def runAsync[A](effect: Async[A]): ZIO[Any, Throwable, A] = {
    implicit val executionContext: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.parasitic
    ZIO.fromFuture(_ => effect.toFuture)
  }

  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private class ControlledInt(
    closeResult: Async[Unit] = Async.succeed(()),
    resetResult: Async[Unit] = Async.succeed(())
  ) extends Reader.AsyncReader[Int] {
    val entered                               = new Completer[Unit]
    val value                                 = new Completer[Long]
    val closes                                = new AtomicInteger
    override def jvmType                      = JvmType.Int
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeResult }
    override def reset(): Async[Unit]         = resetResult
    def isClosed: Async[Boolean]              = Async.succeed(closes.get != 0)
    def readable(): Async[Boolean]            = Async.succeed(true)
    def read[A >: Int](sentinel: A): Async[A] =
      readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = { entered.succeed(()); value }
  }

  private def pullThroughChunk[A: JvmType.Infer](a: A, b: A): Async[Chunk[Chunk[A]]] =
    AsyncStatefulReader.chunked(source(a, b), 1).readAll[Chunk[A]]()

  def spec = suite("async internal reader exhaustive coverage")(
    test("Pull dispatches every JvmType and observes both value and end") {
      for {
        z <- runAsync(pullThroughChunk(true, false))
        b <- runAsync(pullThroughChunk[Byte](-128, 127))
        c <- runAsync(pullThroughChunk(Char.MinValue, Char.MaxValue))
        s <- runAsync(pullThroughChunk[Short](Short.MinValue, Short.MaxValue))
        i <- runAsync(pullThroughChunk(Int.MinValue, Int.MaxValue))
        l <- runAsync(pullThroughChunk(Long.MinValue, Long.MaxValue))
        f <- runAsync(pullThroughChunk(Float.NaN, Float.PositiveInfinity))
        d <- runAsync(pullThroughChunk(Double.NaN, Double.PositiveInfinity))
        r <- runAsync(pullThroughChunk("a", "b"))
      } yield assertTrue(
        z == Chunk(Chunk(true), Chunk(false)),
        b == Chunk(Chunk[Byte](-128), Chunk[Byte](127)),
        c == Chunk(Chunk(Char.MinValue), Chunk(Char.MaxValue)),
        s == Chunk(Chunk(Short.MinValue), Chunk(Short.MaxValue)),
        i == Chunk(Chunk(Int.MinValue), Chunk(Int.MaxValue)),
        l == Chunk(Chunk(Long.MinValue), Chunk(Long.MaxValue)),
        f.head.head.isNaN,
        f.last.head == Float.PositiveInfinity,
        d.head.head.isNaN,
        d.last.head == Double.PositiveInfinity,
        r == Chunk(Chunk("a"), Chunk("b"))
      )
    },
    test("primitive buffered and interspersed availability covers delegated cached and closed states") {
      val buffered = AsyncStatefulReader.buffered(source(1, 2, 3), 3)
      val inter    = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val one      = new Array[Int](1)
      for {
        delegatedB <- runAsync(buffered.readable()); delegatedI      <- runAsync(inter.readable())
        _          <- runAsync(buffered.readInts(one, 0, 1)); cachedB = buffered.tryReadable;
        cachedBR   <- runAsync(buffered.readable())
        _          <- runAsync(inter.readInts(one, 0, 1)); _         <- runAsync(inter.readInts(one, 0, 1))
        cachedI     = inter.tryReadable; cachedIR                    <- runAsync(inter.readable())
        _          <- runAsync(buffered.close()); _                  <- runAsync(inter.close())
        closedB     = buffered.tryReadable; closedI                   = inter.tryReadable
        closedBR   <- runAsync(buffered.readable()); closedIR        <- runAsync(inter.readable())
      } yield assertTrue(
        delegatedB,
        delegatedI,
        cachedB == Reader.Available,
        cachedBR,
        cachedI == Reader.Available,
        cachedIR,
        closedB == Reader.Unavailable,
        closedI == Reader.Unavailable,
        !closedBR,
        !closedIR
      )
    },
    test("reference buffering covers delegated cached closed and cooperative fill branches") {
      val reader = AsyncStatefulReader.buffered(source((0 until 258).map(_.toString): _*), 258)
      for {
        delegated <- runAsync(reader.readable()); delegatedTry    = reader.tryReadable
        first     <- runAsync(reader.read[String](null)); cached <- runAsync(reader.readable());
        cachedTry  = reader.tryReadable
        rest      <- runAsync(reader.readN[String](257)); end    <- runAsync(reader.read[String]("end"))
        _         <- runAsync(reader.close()); closed            <- runAsync(reader.readable()); closedTry = reader.tryReadable
      } yield assertTrue(
        delegated,
        delegatedTry == Reader.Available,
        first == "0",
        cached,
        cachedTry == Reader.Available,
        rest == Chunk.fromIterable((1 until 258).map(_.toString)),
        end == "end",
        !closed,
        closedTry == Reader.Unavailable
      )
    },
    test("primitive carrier fill and intersperse loops cross their reschedule budgets") {
      val booleans = AsyncStatefulReader.buffered(source((0 until 258).map(_ % 2 == 0): _*), 258)
      val chars    = AsyncStatefulReader.buffered(source((0 until 258).map(_.toChar): _*), 258)
      val shorts   = AsyncStatefulReader.buffered(source((0 until 258).map(_.toShort): _*), 258)
      val inter    = AsyncStatefulReader.intersperse(source((0 until 258): _*), -1)
      val dest     = new Array[Int](515)
      for {
        z <- runAsync(booleans.readN[Boolean](258)); c <- runAsync(chars.readN[Char](258));
        s <- runAsync(shorts.readN[Short](258))
        n <-
          runAsync(inter.readInts(dest, 0, dest.length));
        last <- runAsync(inter.readInt(Long.MinValue))
      } yield assertTrue(
        z.length == 258,
        c.length == 258,
        s.length == 258,
        n == 514,
        dest.take(n).count(_ == -1) == 257,
        last == 257L
      )
    },
    test("close owns an active boundary read and reset failure joins cleanup failure") {
      val pendingSource = new ControlledInt
      val pending       = AsyncStatefulReader.buffered(pendingSource, 2)
      val operation     = pending.readInt(-7L).start
      val primary       = new IllegalStateException("reset")
      val secondary     = new IllegalArgumentException("close")
      val closeCount    = new AtomicInteger
      val failingSource = new ControlledInt(Async.succeed(()), Async.fail(primary)) {
        override def close(): Async[Unit] =
          if (closeCount.getAndIncrement() == 0) Async.succeed(()) else Async.fail(secondary)
      }
      val resetting = AsyncStatefulReader.buffered(failingSource, 2)
      for {
        _       <- runAsync(pendingSource.entered); _ <- runAsync(pending.close()); value <- runAsync(operation)
        failure <- runAsync(resetting.reset()).either
      } yield assertTrue(
        value == -7L,
        pendingSource.closes.get == 1,
        failure.left.exists(c => (c eq primary) && c.getSuppressed.contains(secondary)),
        closeCount.get == 2
      )
    },
    test("concurrent scalar byte conversion reaches Boolean Character and Number low-byte lanes") {
      def mapped[A: JvmType.Infer](value: A) =
        AsyncConcurrentReaders.mapPar[A, A](source(value), 1, Async.succeed, implicitly[JvmType.Infer[A]].jvmType)
      def boxed(value: Any): Reader.AsyncReader[Any] =
        AsyncConcurrentReaders.mapPar[Int, Any](source(1), 1, _ => Async.succeed(value), JvmType.AnyRef)
      for {
        z  <- runAsync(mapped(true).readByte()); z0              <- runAsync(mapped(false).readByte());
        c  <- runAsync(mapped('\u1234').readByte())
        b  <- runAsync(mapped(0x81.toByte).readByte()); s        <- runAsync(mapped(0x182.toShort).readByte())
        i  <- runAsync(mapped(0x183).readByte()); l              <- runAsync(mapped(0x184L).readByte())
        f  <- runAsync(mapped(389.0f).readByte()); d             <- runAsync(mapped(390.0d).readByte())
        rz <- runAsync(boxed(Boolean.box(true)).readByte()); rz0 <- runAsync(boxed(Boolean.box(false)).readByte())
        rc <- runAsync(boxed(Char.box('\u1234')).readByte()); rn <- runAsync(boxed(Int.box(0x187)).readByte())
      } yield assertTrue(
        z == 1,
        z0 == 0,
        c == 0x34,
        b == 0x81,
        s == 0x82,
        i == 0x83,
        l == 0x84,
        f == 0x85,
        d == 0x86,
        rz == 1,
        rz0 == 0,
        rc == 0x34,
        rn == 0x87
      )
    },
    test("intersperse drains a continuously available source across its reschedule boundary") {
      final class AvailableInts extends Reader.AsyncReader[Int] {
        private var next                                               = 0
        override def jvmType                                           = JvmType.Int
        override private[streams] def tryReadable                      = if (next < 258) Reader.Available else Reader.Unavailable
        def close()                                                    = Async.succeed { next = 258; () }
        def isClosed                                                   = Async.succeed(next >= 258)
        def readable()                                                 = Async.succeed(next < 258)
        def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = Async.succeed {
          if (next >= 258) sentinel
          else { val value = next; next += 1; value.toLong }
        }
      }
      val reader = AsyncStatefulReader.intersperse(new AvailableInts, -1)
      val dest   = new Array[Int](515)
      for {
        count <- runAsync(reader.readInts(dest, 0, dest.length))
        last  <- runAsync(reader.readInt(Long.MinValue))
        end   <- runAsync(reader.readInt(Long.MinValue))
      } yield assertTrue(
        count == 514,
        dest.head == 0,
        dest.take(count).last == -1,
        dest.take(count).count(_ == -1) == 257,
        last == 257L,
        end == Long.MinValue
      )
    },
    test("concurrent bulk entry points cover zero negative exact complete and EOF results") {
      val bytes   = AsyncConcurrentReaders.mapPar[Byte, Byte](source[Byte](1, 2), 1, Async.succeed, JvmType.Byte)
      val ints    = AsyncConcurrentReaders.mapPar[Int, Int](source(1, 2), 1, Async.succeed, JvmType.Int)
      val longs   = AsyncConcurrentReaders.mapPar[Long, Long](source(1L, 2L), 1, Async.succeed, JvmType.Long)
      val floats  = AsyncConcurrentReaders.mapPar[Float, Float](source(1f, 2f), 1, Async.succeed, JvmType.Float)
      val doubles = AsyncConcurrentReaders.mapPar[Double, Double](source(1d, 2d), 1, Async.succeed, JvmType.Double)
      val ba      = new Array[Byte](2); val ia  = new Array[Int](2); val la = new Array[Long](2)
      val fa      = new Array[Float](2); val da = new Array[Double](2)
      for {
        neg <- runAsync(ints.readN[Int](-1)); zero         <- runAsync(ints.readUpToN[Int](0))
        bz  <- runAsync(bytes.readBytes(ba, 0, 0)); b1     <- runAsync(bytes.readBytes(ba, 0, 2));
        b2  <- runAsync(bytes.readBytes(ba, 1, 1)); be     <- runAsync(bytes.readBytes(ba, 0, 1))
        i1  <- runAsync(ints.readInts(ia, 0, 2)); i2       <- runAsync(ints.readInts(ia, 1, 1));
        ie  <- runAsync(ints.readInts(ia, 0, 1))
        l1  <- runAsync(longs.readLongs(la, 0, 2)); l2     <- runAsync(longs.readLongs(la, 1, 1));
        le  <- runAsync(longs.readLongs(la, 0, 1))
        f1  <- runAsync(floats.readFloats(fa, 0, 2)); f2   <- runAsync(floats.readFloats(fa, 1, 1));
        fe  <- runAsync(floats.readFloats(fa, 0, 1))
        d1  <- runAsync(doubles.readDoubles(da, 0, 2)); d2 <- runAsync(doubles.readDoubles(da, 1, 1));
        de  <- runAsync(doubles.readDoubles(da, 0, 1))
      } yield assertTrue(
        neg.isEmpty,
        zero.isEmpty,
        bz == 0,
        b1 == 1,
        b2 == 1,
        be == -1,
        ba.sameElements(Array[Byte](1, 2)),
        i1 == 1,
        i2 == 1,
        ie == -1,
        ia.sameElements(Array(1, 2)),
        l1 == 1,
        l2 == 1,
        le == -1,
        la.sameElements(Array(1L, 2L)),
        f1 == 1,
        f2 == 1,
        fe == -1,
        fa.sameElements(Array(1f, 2f)),
        d1 == 1,
        d2 == 1,
        de == -1,
        da.sameElements(Array(1d, 2d))
      )
    },
    test("stateful primitive bulk methods cover zero complete cached and EOF loops") {
      val bytes   = AsyncStatefulReader.buffered(source[Byte](1, 2, 3), 3)
      val ints    = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val doubles = AsyncStatefulReader.buffered(source(1d, 2d), 2)
      val ba      = new Array[Byte](3); val ia = new Array[Int](3); val da = new Array[Double](2)
      for {
        bz <- runAsync(bytes.readBytes(ba, 0, 0)); bn     <- runAsync(bytes.readBytes(ba, 0, 3));
        be <- runAsync(bytes.readBytes(ba, 0, 1))
        iz <- runAsync(ints.readInts(ia, 0, 0)); i1       <- runAsync(ints.readInts(ia, 0, 3));
        i2 <- runAsync(ints.readInts(ia, 2, 1)); ie       <- runAsync(ints.readInts(ia, 0, 1))
        dz <- runAsync(doubles.readDoubles(da, 0, 0)); dn <- runAsync(doubles.readDoubles(da, 0, 2));
        de <- runAsync(doubles.readDoubles(da, 0, 1))
      } yield assertTrue(
        bz == 0,
        bn == 3,
        be == -1,
        ba.sameElements(Array[Byte](1, 2, 3)),
        iz == 0,
        i1 == 2,
        i2 == 1,
        ie == -1,
        ia.sameElements(Array(1, 0, 2)),
        dz == 0,
        dn == 2,
        de == -1,
        da.sameElements(Array(1d, 2d))
      )
    },
    test("concurrent cancellation is idempotent before start and after finish") {
      val beforeReader = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.Int)
      val before       = beforeReader.readInt(-1L)
      val doneReader   = AsyncConcurrentReaders.mapPar[Int, Int](source(3), 1, Async.succeed, JvmType.Int)
      val done         = doneReader.readInt(-3L).start
      for {
        _  <- runAsync(Async.cancelWithCleanup(before.asInstanceOf[Pollable[Long]]))
        _  <- runAsync(Async.cancelWithCleanup(before.asInstanceOf[Pollable[Long]])); bv <- runAsync(before)
        dv <- runAsync(done); _                                                          <- runAsync(Async.cancelWithCleanup(done)); _ <- runAsync(doneReader.close())
      } yield assertTrue(bv == -1L, dv == 3L)
    },
    test("synchronous worker failure is protected and terminal state rejects later commits") {
      val boom   = new IllegalStateException("boom")
      val reader = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, _ => throw boom, JvmType.Int)
      for {
        first  <- runAsync(reader.readInt(-1L)).either; second <- runAsync(reader.readInt(-2L)).either
        closed <- runAsync(reader.isClosed); readable          <- runAsync(reader.readable()); _ <- runAsync(reader.close())
      } yield assertTrue(first.left.exists(_ eq boom), second.left.exists(_ eq boom), closed, !readable)
    },
    test("reentrant close converts a subsequently failing concurrent worker to the read sentinel") {
      val boom                            = new IllegalStateException("after-close")
      var reader: Reader.AsyncReader[Int] = null
      reader = AsyncConcurrentReaders
        .mapPar[Int, Int](source(1), 1, _ => reader.close().flatMap(_ => Async.fail(boom)), JvmType.Int)
      for {
        value    <- runAsync(reader.readInt(-17L))
        closed   <- runAsync(reader.isClosed)
        readable <- runAsync(reader.readable())
      } yield assertTrue(value == -17L, closed, !readable)
    }
  ) @@ TestAspect.sequential
}
