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

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.{AsyncConcurrentReaders, AsyncStatefulReader}
import zio.blocks.streams.io.Reader
import zio.test._

/** Exercises the native async boundaries through their Reader contract. */
object AsyncNativeBoundaryCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def buffered[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    AsyncStatefulReader.buffered(source(values: _*), 3)

  def spec = suite("native async reader boundaries")(
    test("buffered primitive readers implement scalar, bulk, cached, empty, and closed reads") {
      val b = buffered[Byte](-128, 0, 127); val ba                                 = Array.fill[Byte](4)(9)
      val i = buffered[Int](Int.MinValue, 0, Int.MaxValue); val ia                 = Array.fill[Int](4)(9)
      val l = buffered[Long](Long.MinValue, 0L, Long.MaxValue); val la             = Array.fill[Long](4)(9L)
      val f = buffered[Float](Float.NaN, -0.0f, Float.PositiveInfinity); val fa    = Array.fill[Float](4)(9f)
      val d = buffered[Double](Double.NaN, -0.0d, Double.PositiveInfinity); val da = Array.fill[Double](4)(9d)
      val z = buffered[Boolean](true, false); val c                                = buffered[Char](Char.MinValue, Char.MaxValue)
      val s = buffered[Short](Short.MinValue, Short.MaxValue)
      for {
        bn   <- runAsync(b.readBytes(ba, 1, 2)); bv   <- runAsync(b.readByte()); be           <- runAsync(b.readByte())
        in   <- runAsync(i.readInts(ia, 1, 2)); iv    <- runAsync(i.readInt(-1L)); ie         <- runAsync(i.readInt(-1L))
        ln   <- runAsync(l.readLongs(la, 1, 2)); lv   <- runAsync(l.readLong(-1L)); le        <- runAsync(l.readLong(-1L))
        fn   <- runAsync(f.readFloats(fa, 1, 2)); fv  <- runAsync(f.readFloat(-1d)); fe       <- runAsync(f.readFloat(-1d))
        dn   <- runAsync(d.readDoubles(da, 1, 2)); dv <- runAsync(d.readDouble(-1d)); de      <- runAsync(d.readDouble(-1d))
        zero <- runAsync(buffered[Int](1).readInts(new Array[Int](0), 0, 0))
        zb   <- runAsync(z.readBoolean(-1)); zr       <- runAsync(z.read[Boolean](false)); ze <- runAsync(z.readBoolean(-1))
        cb   <- runAsync(c.readChar(-1)); cr          <- runAsync(c.read[Char]('x')); ce      <- runAsync(c.readChar(-1))
        sb   <- runAsync(s.readShort(-1)); sr         <- runAsync(s.read[Short](1)); se       <- runAsync(s.readShort(-1))
        _    <- runAsync(i.close()); readable         <- runAsync(i.readable()); closed       <- runAsync(i.isClosed)
      } yield {
        val floatNaNPreserved   = fa(1).isNaN
        val floatSignPreserved  = java.lang.Float.floatToRawIntBits(fa(2)) == java.lang.Float.floatToRawIntBits(-0.0f)
        val doubleNaNPreserved  = da(1).isNaN
        val doubleSignPreserved =
          java.lang.Double.doubleToRawLongBits(da(2)) == java.lang.Double.doubleToRawLongBits(-0.0d)
        assertTrue(
          bn == 2,
          ba.toVector == Vector[Byte](9, -128, 0, 9),
          bv == 127,
          be == -1,
          in == 2,
          ia.toVector == Vector(9, Int.MinValue, 0, 9),
          iv == Int.MaxValue.toLong,
          ie == -1L,
          ln == 2,
          la.toVector == Vector(9L, Long.MinValue, 0L, 9L),
          lv == Long.MaxValue,
          le == -1L,
          fn == 2,
          floatNaNPreserved,
          floatSignPreserved,
          fv == Float.PositiveInfinity.toDouble,
          fe == -1d,
          dn == 2,
          doubleNaNPreserved,
          doubleSignPreserved,
          dv == Double.PositiveInfinity,
          de == -1d,
          zb == 1,
          !zr,
          ze == -1,
          cb == 0,
          cr == Char.MaxValue,
          ce == -1,
          sb == Short.MinValue.toInt,
          sr == Short.MaxValue,
          se == -1,
          zero == 0,
          !readable,
          closed
        )
      }
    },
    test("reference buffering fills a cache, reports availability, reaches EOF, and resets") {
      val reader = AsyncStatefulReader.buffered(source("a", "b", "c", "d"), 3)
      for {
        first    <- runAsync(reader.read[String](null)); cachedReadable <- runAsync(reader.readable())
        rest     <- runAsync(reader.readN[String](4)); eof              <- runAsync(reader.read[String](null));
        _        <- runAsync(reader.reset())
        again    <- runAsync(reader.readN[String](4)); _                <- runAsync(reader.close());
        readable <- runAsync(reader.readable())
      } yield assertTrue(
        first == "a",
        cachedReadable,
        rest == Chunk("b", "c", "d"),
        eof == null,
        again == Chunk("a", "b", "c", "d"),
        !readable
      )
    },
    test("buffered readers expose generic, EOF, cached availability, validation, and reset lanes") {
      val b       = buffered[Byte](1, 2); val i    = buffered[Int](1, 2); val l = buffered[Long](1L, 2L)
      val f       = buffered[Float](1f, 2f); val d = buffered[Double](1d, 2d)
      val z       = buffered[Boolean](true); val c = buffered[Char]('a'); val s = buffered[Short](1.toShort)
      val empty   = buffered[String]()
      val invalid = scala.util.Try(i.readInts(new Array[Int](1), 1, 1)).failed.toOption
      for {
        bg <- runAsync(b.read[Byte](9)); ig                      <- runAsync(i.read[Int](9)); lg  <- runAsync(l.read[Long](9L))
        fg <- runAsync(f.read[Float](9f)); dg                    <- runAsync(d.read[Double](9d))
        zg <- runAsync(z.read[Boolean](false)); cg               <- runAsync(c.read[Char]('x'));
        sg <- runAsync(s.read[Short](9.toShort))
        br <- runAsync(b.readable()); ba                          = b.tryReadable; _              <- runAsync(b.readByte()); be <- runAsync(b.read[Byte](9))
        ie <- runAsync(i.readInts(new Array[Int](2), 0, 2)); ie2 <- runAsync(i.readInts(new Array[Int](1), 0, 1))
        ee <- runAsync(empty.read[String]("eof")); er            <- runAsync(empty.readable()); ea = empty.tryReadable
        _  <- runAsync(l.reset()); lr                            <- runAsync(l.readLong(-1L)); _  <- runAsync(l.close());
        lc <- runAsync(l.read[Long](-1L))
      } yield assertTrue(
        bg == 1,
        ig == 1,
        lg == 1L,
        fg == 1f,
        dg == 1d,
        zg,
        cg == 'a',
        sg == 1.toShort,
        br,
        ba == Reader.Available,
        be == 9.toByte,
        ie == 1,
        ie2 == -1,
        invalid.exists(_.isInstanceOf[IndexOutOfBoundsException]),
        ee == "eof",
        !er,
        ea == Reader.Unavailable,
        lr == 1L,
        lc == -1L
      )
    },
    test("chunked and sliding boundaries cover full, partial, overlapping, and gapped windows") {
      val chunks  = AsyncStatefulReader.chunked(source(1, 2, 3, 4, 5), 2)
      val overlap = AsyncStatefulReader.sliding(source(1, 2, 3, 4), 3, 1)
      val gaps    = AsyncStatefulReader.sliding(source(1, 2, 3, 4, 5, 6), 2, 4)
      for {
        exact  <- runAsync(chunks.readN[Chunk[Int]](4)); windows <- runAsync(overlap.readN[Chunk[Int]](5))
        sparse <- runAsync(gaps.readN[Chunk[Int]](5)); cr        <- runAsync(chunks.readable());
        or     <-
          runAsync(overlap.readable());
        gr <- runAsync(gaps.readable())
      } yield assertTrue(
        exact == Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5)),
        windows == Chunk(Chunk(1, 2, 3), Chunk(2, 3, 4)),
        sparse == Chunk(Chunk(1, 2), Chunk(5, 6)),
        !cr,
        !or,
        !gr
      )
    },
    test("intersperse implements every specialized lane and reference caching") {
      def all[A: JvmType.Infer](values: Chunk[A], separator: A): ZIO[Any, Throwable, Chunk[A]] =
        runAsync(AsyncStatefulReader.intersperse(Reader.fromChunk(values).toAsync, separator).readAll[A]())
      for {
        z <- all(Chunk(true, false), true); b   <- all(Chunk[Byte](1, 2), 0.toByte)
        c <- all(Chunk[Char]('a', 'b'), '|'); s <- all(Chunk[Short](1, 2), 0.toShort)
        i <- all(Chunk(1, 2), 0); l             <- all(Chunk(1L, 2L), 0L); f <- all(Chunk(1f, 2f), 0f)
        d <- all(Chunk(1d, 2d), 0d); r          <- all(Chunk("a", "b"), "|")
      } yield assertTrue(
        z == Chunk(true, true, false),
        b == Chunk[Byte](1, 0, 2),
        c == Chunk[Char]('a', '|', 'b'),
        s == Chunk[Short](1, 0, 2),
        i == Chunk(1, 0, 2),
        l == Chunk(1L, 0L, 2L),
        f == Chunk(1f, 0f, 2f),
        d == Chunk(1d, 0d, 2d),
        r == Chunk("a", "|", "b")
      )
    },
    test("intersperse scalar, bulk, pending, EOF, close, and availability paths are complete") {
      val bytes   = AsyncStatefulReader.intersperse(source[Byte](1, 2), 0.toByte)
      val ints    = AsyncStatefulReader.intersperse(source(1, 2), 0)
      val longs   = AsyncStatefulReader.intersperse(source(1L, 2L), 0L)
      val floats  = AsyncStatefulReader.intersperse(source(1f, 2f), 0f)
      val doubles = AsyncStatefulReader.intersperse(source(1d, 2d), 0d)
      val bools   = AsyncStatefulReader.intersperse(source(true, false), true)
      val chars   = AsyncStatefulReader.intersperse(source('a', 'b'), '|')
      val shorts  = AsyncStatefulReader.intersperse(source[Short](1, 2), 0.toShort)
      val refs    = AsyncStatefulReader.intersperse(source("a", "b"), "|")
      val ba      = new Array[Byte](4); val ia  = new Array[Int](4); val la = new Array[Long](4)
      val fa      = new Array[Float](4); val da = new Array[Double](4)
      for {
        bn             <- runAsync(bytes.readBytes(ba, 0, 3)); bv      <- runAsync(bytes.readByte()); be  <- runAsync(bytes.readByte())
        in             <- runAsync(ints.readInts(ia, 0, 3)); iv        <- runAsync(ints.readInt(-1L)); ie <- runAsync(ints.readInt(-1L))
        ln             <- runAsync(longs.readLongs(la, 0, 3)); lv      <- runAsync(longs.readLong(-1L));
        le             <- runAsync(longs.readLong(-1L))
        fn             <- runAsync(floats.readFloats(fa, 0, 3)); fv    <- runAsync(floats.readFloat(-1d));
        fe             <- runAsync(floats.readFloat(-1d))
        dn             <- runAsync(doubles.readDoubles(da, 0, 3)); dv  <- runAsync(doubles.readDouble(-1d));
        de             <- runAsync(doubles.readDouble(-1d))
        zv             <- runAsync(bools.read[Boolean](false)); cv     <- runAsync(chars.read[Char]('x'));
        sv             <- runAsync(shorts.read[Short](9))
        first          <- runAsync(refs.read[String](null)); separator <- runAsync(refs.read[String](null));
        cached          = refs.tryReadable
        second         <- runAsync(refs.read[String](null)); eof       <- runAsync(refs.read[String](null));
        _              <- runAsync(refs.close())
        closedReadable <- runAsync(refs.readable()); closed            <- runAsync(refs.read[String]("closed"))
      } yield assertTrue(
        bn == 2,
        ba.take(2).toVector == Vector[Byte](1, 0),
        bv == 2,
        be == -1,
        in == 2,
        ia.take(2).toVector == Vector(1, 0),
        iv == 2L,
        ie == -1L,
        ln == 2,
        la.take(2).toVector == Vector(1L, 0L),
        lv == 2L,
        le == -1L,
        fn == 2,
        fa.take(2).toVector == Vector(1f, 0f),
        fv == 2d,
        fe == -1d,
        dn == 2,
        da.take(2).toVector == Vector(1d, 0d),
        dv == 2d,
        de == -1d,
        zv,
        cv == 'a',
        sv == 1.toShort,
        first == "a",
        separator == "|",
        cached == Reader.Unknown,
        second == "b",
        eof == null,
        !closedReadable,
        closed == "closed"
      )
    },
    test("mapPar pulls and publishes every JVM lane, including bulk and EOF paths") {
      def mapped[A: JvmType.Infer](values: Chunk[A]): ZIO[Any, Throwable, Chunk[A]] = runAsync(
        AsyncConcurrentReaders
          .mapPar[A, A](Reader.fromChunk(values), 2, Async.succeed, implicitly[JvmType.Infer[A]].jvmType)
          .readAll[A]()
      )
      val ints =
        AsyncConcurrentReaders.mapPar[Int, Int](Reader.fromChunk(Chunk(1, 2, 3)), 2, Async.succeed, JvmType.Int)
      val dest = new Array[Int](3)
      for {
        z   <- mapped(Chunk(true, false)); b                           <- mapped(Chunk[Byte](-128, 127));
        c   <- mapped(Chunk[Char](Char.MinValue, Char.MaxValue))
        s   <- mapped(Chunk[Short](Short.MinValue, Short.MaxValue)); i <- mapped(Chunk(Int.MinValue, Int.MaxValue))
        l   <- mapped(Chunk(Long.MinValue, Long.MaxValue)); f          <- mapped(Chunk(Float.NaN, Float.PositiveInfinity))
        d   <- mapped(Chunk(Double.NaN, Double.PositiveInfinity)); r   <- mapped(Chunk("a", "b"))
        n   <- runAsync(ints.readInts(dest, 0, 3)); remaining          <- runAsync(ints.readAll[Int]())
        end <- runAsync(ints.readInt(-1L)); _                          <- runAsync(ints.close()); closed <- runAsync(ints.isClosed)
      } yield assertTrue(
        z.toSet == Set(true, false),
        b.toSet == Set[Byte](-128, 127),
        c.toSet == Set(Char.MinValue, Char.MaxValue),
        s.toSet == Set(Short.MinValue, Short.MaxValue),
        i.toSet == Set(Int.MinValue, Int.MaxValue),
        l.toSet == Set(Long.MinValue, Long.MaxValue),
        f.exists(_.isNaN),
        d.exists(_.isNaN),
        r.toSet == Set("a", "b"),
        n > 0,
        n <= 3,
        (Chunk.fromArray(dest).take(n) ++ remaining).toSet == Set(1, 2, 3),
        end == -1L,
        closed
      )
    },
    test("mapPar implements every scalar, byte-conversion, bulk-validation, and zero-count path") {
      def one[A: JvmType.Infer](value: A): Reader.AsyncReader[A] =
        AsyncConcurrentReaders.mapPar[A, A](source(value), 1, Async.succeed, implicitly[JvmType.Infer[A]].jvmType)
      val z  = one(true); val b          = one(0x7f.toByte); val c    = one('\u0123'); val s = one(7.toShort)
      val i  = one(8); val l             = one(9L); val f             = one(1.5f); val d     = one(2.5d); val r = one("r")
      val ia = new Array[Int](1); val la = new Array[Long](1); val fa = new Array[Float](1);
      val da = new Array[Double](1)
      for {
        zv          <- runAsync(z.readBoolean(-1)); zb                          <- runAsync(one(false).readByte())
        bv          <- runAsync(b.readByte()); cv                               <- runAsync(c.readChar(-1)); cb                <- runAsync(one('\u0123').readByte())
        sv          <- runAsync(s.readShort(-1)); iv                            <- runAsync(i.readInt(-1L)); lv                <- runAsync(l.readLong(-1L))
        fv          <- runAsync(f.readFloat(-1d)); dv                           <- runAsync(d.readDouble(-1d)); rv             <- runAsync(r.read[String](null))
        in          <- runAsync(one(1).readInts(ia, 0, 1)); ln                  <- runAsync(one(1L).readLongs(la, 0, 1))
        fn          <- runAsync(one(1f).readFloats(fa, 0, 1)); dn               <- runAsync(one(1d).readDoubles(da, 0, 1))
        zero        <- runAsync(one(1).readInts(new Array[Int](0), 0, 0)); none <- runAsync(one(1).readN[Int](0))
        upTo        <- runAsync(one(1).readUpToN[Int](1)); invalid              <- runAsync(one(1).readInts(null, 0, 0)).either
        _           <- runAsync(z.readBoolean(-1)); zEnd                        <- runAsync(z.readBoolean(-2)); zClosed        <- runAsync(z.isClosed)
        neverStarted = one(1); _                                                <- runAsync(neverStarted.close()); closedValue <- runAsync(neverStarted.readInt(-3L))
      } yield assertTrue(
        zv == 1,
        zb == 0,
        bv == 0x7f,
        cv == 0x123,
        cb == 0x23,
        sv == 7,
        iv == 8L,
        lv == 9L,
        fv == 1.5d,
        dv == 2.5d,
        rv == "r",
        in == 1,
        ia(0) == 1,
        ln == 1,
        la(0) == 1L,
        fn == 1,
        fa(0) == 1f,
        dn == 1,
        da(0) == 1d,
        zero == 0,
        none.isEmpty,
        upTo == Chunk(1),
        invalid.left.exists(_.isInstanceOf[NullPointerException]),
        zEnd == -2,
        zClosed,
        closedValue == -3L
      )
    },
    test("parallel boundaries propagate synchronous and asynchronous failures and close idempotently") {
      val sync =
        AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, _ => throw new IllegalStateException("sync"), JvmType.Int)
      val async = AsyncConcurrentReaders
        .mapPar[Int, Int](source(1), 1, _ => Async.fail(new IllegalArgumentException("async")), JvmType.Int)
      for {
        syncFailure <- runAsync(sync.readInt(-1L)).either; asyncFailure <- runAsync(async.readInt(-1L)).either
        _           <- runAsync(sync.close()); _                        <- runAsync(sync.close()); _ <- runAsync(async.close())
        syncClosed  <-
          runAsync(sync.isClosed);
        asyncClosed <- runAsync(async.isClosed)
      } yield assertTrue(
        syncFailure.left.exists(_.getMessage == "sync"),
        asyncFailure.left.exists(_.getMessage == "async"),
        syncClosed,
        asyncClosed
      )
    },
    test("mapPar rejects overlapping pulls and cooperatively yields across a full readN budget") {
      val callbackStarted = new Completer[Unit]
      val callbackValue   = new Completer[Int]
      val pending         = AsyncConcurrentReaders.mapPar[Int, Int](
        source(1),
        1,
        _ => {
          callbackStarted.succeed(())
          callbackValue
        },
        JvmType.Int
      )
      val first = pending.readInt(-1L).start
      val many  = AsyncConcurrentReaders.mapPar[Int, Int](
        Reader.fromRange(0 until 257),
        4,
        value => Async.succeed(value),
        JvmType.Int
      )
      for {
        _           <- runAsync(callbackStarted)
        overlapping <- runAsync(pending.readInt(-2L)).either
        _            = callbackValue.succeed(11)
        value       <- runAsync(first)
        values      <- runAsync(many.readN[Int](257))
      } yield assertTrue(
        overlapping.left.exists(_.getMessage == "Cannot perform a reentrant reader operation"),
        value == 11L,
        values.length == 257,
        values.toSet == (0 until 257).toSet
      )
    },
    test("mapPar direct fold polls the first suspended callback before returning pending") {
      var callbackPolls = 0
      val callback      = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = {
          val _ = onComplete
          callbackPolls += 1
          Async.succeed(2)
        }
      }
      val reader = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, _ => callback, JvmType.Int)
      val folded = Sink.foldNativeAsyncIntLong(reader, 0L, (acc, value) => Async.succeed(acc + value))

      for {
        result <- runAsync(folded)
        _      <- runAsync(reader.close())
      } yield assertTrue(result == 2L, callbackPolls == 1)
    },
    test("stateful boundaries cooperatively yield while filling and draining large ready batches") {
      val ready            = Reader.fromRange(0 until 300).toAsync
      val buffered         = AsyncStatefulReader.buffered(ready, 300)
      val chunked          = AsyncStatefulReader.chunked(Reader.fromRange(0 until 300).toAsync, 300)
      val interspersed     = AsyncStatefulReader.intersperse(Reader.fromRange(0 until 257).toAsync, -1)
      val interspersedDest = new Array[Int](513)
      for {
        first <- runAsync(buffered.readInt(-1L))
        rest  <- runAsync(buffered.readN[Int](299))
        chunk <- runAsync(chunked.read[Chunk[Int]](Chunk.empty))
        count <- runAsync(interspersed.readInts(interspersedDest, 0, interspersedDest.length))
        last  <- runAsync(interspersed.readInt(Long.MinValue))
        end   <- runAsync(interspersed.readInt(Long.MinValue))
      } yield assertTrue(
        first == 0L,
        rest == Chunk.fromIterable(1 until 300),
        chunk == Chunk.fromIterable(0 until 300),
        count == 512,
        interspersedDest.head == 0,
        interspersedDest.take(count).last == -1,
        interspersedDest.take(count).count(_ == -1) == 256,
        last == 256L,
        end == Long.MinValue
      )
    },
    test("stateful boundaries cancel pending generic and primitive reads on close") {
      final class PendingRef extends Reader.AsyncReader[String] {
        val started                                  = new Completer[Unit]
        val value                                    = new Completer[String]
        var closes                                   = 0
        def close(): Async[Unit]                     = { closes += 1; Async.succeed(()) }
        def isClosed: Async[Boolean]                 = Async.succeed(closes > 0)
        def readable(): Async[Boolean]               = Async.succeed(true)
        def read[A >: String](sentinel: A): Async[A] = {
          started.succeed(())
          value.map(_.asInstanceOf[A])
        }
      }
      final class PendingInt extends Reader.AsyncReader[Int] {
        val started                                                                 = new Completer[Unit]
        val value                                                                   = new Completer[Long]
        var closes                                                                  = 0
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = { closes += 1; Async.succeed(()) }
        def isClosed: Async[Boolean]                                                = Async.succeed(closes > 0)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = readInt(Long.MinValue).map(_.toInt.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          started.succeed(())
          value
        }
      }
      val refSource = new PendingRef
      val ref       = AsyncStatefulReader.buffered(refSource, 2)
      val intSource = new PendingInt
      val int       = AsyncStatefulReader.intersperse(intSource, 0)
      val refRead   = ref.read[String]("closed").start
      val intRead   = int.readInt(-7L).start
      for {
        _         <- runAsync(refSource.started); _ <- runAsync(intSource.started)
        _         <- runAsync(ref.close()); _       <- runAsync(int.close())
        refResult <- runAsync(refRead); intResult   <- runAsync(intRead)
        refReady  <-
          runAsync(ref.readable());
        intReady <- runAsync(int.readable())
      } yield assertTrue(
        refResult == "closed",
        intResult == -7L,
        !refReady,
        !intReady,
        refSource.closes == 1,
        intSource.closes == 1
      )
    },
    test("mapPar closes a pending worker read and preserves terminal cleanup failures") {
      val workerStarted = new Completer[Unit]
      val workerValue   = new Completer[Int]
      val pending       = AsyncConcurrentReaders.mapPar[Int, Int](
        source(1),
        1,
        _ => { workerStarted.succeed(()); workerValue },
        JvmType.Int
      )
      val pendingRead    = pending.readInt(-5L).start
      val cleanupFailure = new IllegalStateException("cleanup")
      val failingSource  = new Reader.AsyncReader[Int] {
        override def jvmType: JvmType                                               = JvmType.Int
        def close(): Async[Unit]                                                    = Async.fail(cleanupFailure)
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.succeed(sentinel)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.succeed(sentinel)
      }
      val terminal = AsyncConcurrentReaders.mapPar[Int, Int](failingSource, 1, Async.succeed, JvmType.Int)
      for {
        _     <- runAsync(workerStarted); _           <- runAsync(pending.close()); value <- runAsync(pendingRead)
        ready <- runAsync(pending.readable()); closed <- runAsync(pending.isClosed)
        first <-
          runAsync(terminal.readInt(-1L)).either;
        second <- runAsync(terminal.readInt(-2L)).either
      } yield assertTrue(
        value == -5L,
        !ready,
        closed,
        first.left.exists(_ eq cleanupFailure),
        second.left.exists(_ eq cleanupFailure)
      )
    }
  ) @@ TestAspect.sequential
}
