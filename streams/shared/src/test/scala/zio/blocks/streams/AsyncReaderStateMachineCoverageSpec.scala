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

object AsyncReaderStateMachineCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class GatedInt extends Reader.AsyncReader[Int] {
    val entered                                                    = new Completer[Unit]
    val gate                                                       = new Completer[Int]
    val closes                                                     = new AtomicInteger
    override def jvmType                                           = JvmType.Int
    def close()                                                    = { closes.incrementAndGet(); gate.succeed(Int.MinValue); Async.succeed(()) }
    def isClosed                                                   = Async.succeed(closes.get != 0)
    def readable()                                                 = Async.succeed(true)
    def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = {
      entered.succeed(()); gate.map(v => if (v == Int.MinValue) sentinel else v.toLong)
    }
  }

  def spec = suite("async reader state-machine coverage")(
    test("default byte and bulk loops cover signs, zero, exact, partial, and unavailable") {
      final class Generic(bytes: List[Byte]) extends Reader.AsyncReader[Byte] {
        private var rest                          = bytes
        override def jvmType                      = JvmType.Byte
        def close()                               = { rest = Nil; Async.succeed(()) }
        def isClosed                              = Async.succeed(rest.isEmpty)
        def readable()                            = Async.succeed(rest.nonEmpty)
        override private[streams] def tryReadable = if (rest.nonEmpty) Reader.Available else Reader.Unavailable
        def read[A >: Byte](sentinel: A)          = rest match {
          case h :: t => rest = t; Async.succeed(h.asInstanceOf[A])
          case Nil    => Async.succeed(sentinel)
        }
        override def readByte(): Async[Int] = rest match {
          case h :: t => rest = t; Async.succeed(h.toInt & 0xff)
          case Nil    => Async.succeed(-1)
        }
      }
      val r = new Generic(List(0x80.toByte, 0xff.toByte, 0, 0x7f.toByte))
      val p = new Generic(List(1, 2))
      for {
        a    <- runAsync(r.readByte()); b         <- runAsync(r.readByte()); c            <- runAsync(r.readByte());
        d    <- runAsync(r.readByte()); e         <- runAsync(r.readByte())
        z    <- runAsync(p.readUpToN[Byte](0)); n <- runAsync(p.readUpToN[Byte](-2)); one <- runAsync(p.readUpToN[Byte](1))
        rest <-
          runAsync(p.readN[Byte](4));
        end <- runAsync(p.readN[Byte](1))
      } yield assertTrue(
        a == 128,
        b == 255,
        c == 0,
        d == 127,
        e == -1,
        z.isEmpty,
        n.isEmpty,
        one == Chunk[Byte](1),
        rest == Chunk[Byte](2),
        end.isEmpty
      )
    },
    test("sync adapter delegates all lanes, controls, failures, close memoization, and reset") {
      def all[A: JvmType.Infer](values: Chunk[A]) = Reader.fromChunk(values).toAsync.readAll[A]()
      val controls                                = Reader.fromChunk(Chunk(1, 2, 3)).toAsync
      val failure                                 = new RuntimeException("sync")
      val broken                                  = new Reader.SyncReader[Int] {
        override def jvmType                                                 = JvmType.Int
        def close()                                                          = throw failure
        def isClosed                                                         = false
        def read[A >: Int](sentinel: A): A                                   = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = throw failure
      }.toAsync
      for {
        z      <- runAsync(all(Chunk(true, false))); b                <- runAsync(all(Chunk[Byte](-128, 127)))
        c      <- runAsync(all(Chunk(Char.MinValue, Char.MaxValue)));
        s      <- runAsync(all(Chunk[Short](Short.MinValue, Short.MaxValue)))
        i      <- runAsync(all(Chunk(Int.MinValue, Int.MaxValue))); l <- runAsync(all(Chunk(Long.MinValue, Long.MaxValue)))
        f      <- runAsync(all(Chunk(Float.NaN, Float.PositiveInfinity)));
        d      <- runAsync(all(Chunk(Double.NaN, Double.PositiveInfinity)))
        r      <- runAsync(all(Chunk("a", null))); zero               <- runAsync(controls.readN[Int](0));
        neg    <- runAsync(controls.readUpToN[Int](-1))
        _      <- runAsync(controls.skip(1)); v                       <- runAsync(controls.readInt(-1)); _ <- runAsync(controls.reset());
        replay <- runAsync(controls.readAll[Int]())
        limit  <- runAsync(controls.setLimit(1)); skip                <- runAsync(controls.setSkip(1));
        repeat <- runAsync(controls.setRepeat())
        rf     <- runAsync(broken.readInt(-1).either); cf1            <- runAsync(broken.close().either);
        cf2    <- runAsync(broken.close().either)
      } yield assertTrue(
        z == Chunk(true, false),
        b == Chunk[Byte](-128, 127),
        c.length == 2,
        s.length == 2,
        i.length == 2,
        l.length == 2,
        f.length == 2,
        d.length == 2,
        r == Chunk("a", null),
        zero.isEmpty,
        neg.isEmpty,
        v == 2,
        replay == Chunk(1, 2, 3),
        limit,
        skip,
        !repeat,
        rf == Left(failure),
        cf1 == Left(failure),
        cf2 == Left(failure)
      )
    },
    test("concat transitions every lane, lazy tails, controls, reset, failure, and duplicate close") {
      def joined[A: JvmType.Infer](a: A, b: A): Reader.AsyncReader[A] = {
        val lane = implicitly[JvmType.Infer[A]].jvmType
        source(a).concatReaderWithJvmType(() => source(b), lane)
      }
      var forced   = 0
      val lazyTail = source(1).concatReaderWithJvmType(
        () => { forced += 1; source(2) },
        JvmType.Int
      )
      val boom   = new RuntimeException("tail")
      val failed = Reader.closed.toAsync.concatAsyncWithJvmType[Int](() => Async.fail(boom), JvmType.Int)
      for {
        z       <- runAsync(joined(true, false).readAll[Boolean]()); b  <- runAsync(joined[Byte](-128, 127).readAll[Byte]())
        c       <- runAsync(joined('a', 'b').readAll[Char]()); s        <- runAsync(joined[Short](1, 2).readAll[Short]())
        i       <- runAsync(joined(1, 2).readAll[Int]()); l             <- runAsync(joined(1L, 2L).readAll[Long]())
        f       <- runAsync(joined(1f, 2f).readAll[Float]()); d         <- runAsync(joined(1d, 2d).readAll[Double]())
        r       <- runAsync(joined("a", "b").readAll[String]()); before <- runAsync(lazyTail.readable())
        first   <- runAsync(lazyTail.readInt(-1)); second               <- runAsync(lazyTail.readInt(-1));
        end     <- runAsync(lazyTail.readInt(-1))
        _       <- runAsync(lazyTail.reset()); again                    <- runAsync(lazyTail.readAll[Int]());
        zero    <- runAsync(lazyTail.readUpToN[Int](0))
        failure <- runAsync(failed.readInt(-1).either); _               <- runAsync(lazyTail.close()); _ <- runAsync(lazyTail.close())
        closed  <- runAsync(lazyTail.isClosed); limit                   <- runAsync(lazyTail.setLimit(1));
        repeat  <- runAsync(lazyTail.setRepeat())
      } yield assertTrue(
        z == Chunk(true, false),
        b == Chunk[Byte](-128, 127),
        c == Chunk('a', 'b'),
        s == Chunk[Short](1, 2),
        i == Chunk(1, 2),
        l == Chunk(1L, 2L),
        f == Chunk(1f, 2f),
        d == Chunk(1d, 2d),
        r == Chunk("a", "b"),
        before,
        first == 1,
        second == 2,
        end == -1,
        forced == 2,
        again == Chunk(1, 2),
        zero.isEmpty,
        failure == Left(boom),
        closed,
        !limit,
        !repeat,
        lazyTail.tryReadable == Reader.Unavailable
      )
    },
    test("concat and release delegate every specialized scalar lane") {
      val z                                    = source(true).concatReaderWithJvmType(() => source(false), JvmType.Boolean)
      val c                                    = source('a').concatReaderWithJvmType(() => source('b'), JvmType.Char)
      val s                                    = source[Short](1).concatReaderWithJvmType(() => source[Short](2), JvmType.Short)
      def released[A: JvmType.Infer](value: A) = source(value).withReleaseAsync(() => Async.succeed(()))
      for {
        z1 <- runAsync(z.readBoolean(-1)); z2                <- runAsync(z.readBoolean(-1)); ze <- runAsync(z.readBoolean(-1))
        c1 <- runAsync(c.readChar(-1)); c2                   <- runAsync(c.readChar(-1)); ce    <- runAsync(c.readChar(-1))
        s1 <- runAsync(s.readShort(-1)); s2                  <- runAsync(s.readShort(-1)); se   <- runAsync(s.readShort(-1))
        rr <- runAsync(released("r").read[String](null)); rz <- runAsync(released(true).readBoolean(-1))
        rc <- runAsync(released('c').readChar(-1)); rs       <- runAsync(released[Short](3).readShort(-1))
        rl <- runAsync(released(4L).readLong(-1L)); rf       <- runAsync(released(5f).readFloat(-1d))
        rd <- runAsync(released(6d).readDouble(-1d))
      } yield assertTrue(
        z1 == 1,
        z2 == 0,
        ze == -1,
        c1 == 'a'.toInt,
        c2 == 'b'.toInt,
        ce == -1,
        s1 == 1,
        s2 == 2,
        se == -1,
        rr == "r",
        rz == 1,
        rc == 'c'.toInt,
        rs == 3,
        rl == 4L,
        rf == 5d,
        rd == 6d
      )
    },
    test("release delegates all operations and rejects late results after one reentrant release") {
      val inner                           = new GatedInt
      var releases                        = 0
      var reader: Reader.AsyncReader[Int] = null
      reader = inner.withReleaseAsync { () => releases += 1; reader.close() }
      val pull = reader.readInt(-7).start
      for {
        _     <- runAsync(inner.entered); _                  <- runAsync(reader.close()); value <- runAsync(pull);
        _     <- runAsync(reader.close())
        all   <- runAsync(reader.readAll[Int]()); n          <- runAsync(reader.readN[Int](2));
        up    <- runAsync(reader.readUpToN[Int](2))
        ready <- runAsync(reader.readable()); closed         <- runAsync(reader.isClosed);
        reset <- runAsync(reader.reset().either)
        limit <- runAsync(reader.setLimit(1).either); repeat <- runAsync(reader.setRepeat().either);
        skip  <- runAsync(reader.skip(1).either)
      } yield assertTrue(
        value == -7,
        all.isEmpty,
        n.isEmpty,
        up.isEmpty,
        !ready,
        closed,
        releases == 1,
        reset.isLeft,
        limit.isLeft,
        repeat.isLeft,
        skip.isLeft,
        inner.closes.get == 1
      )
    },
    test("repeated and unfold cover empty budgets, pending cancellation, failure, reset, and duplicate close") {
      var empties        = 0
      val emptyThenValue = Reader.unfoldAsync(0) { state =>
        empties += 1
        if (state < 257) Async.succeed(None) else Async.succeed(Some((state, state + 1)))
      }
      val repeated = Reader.repeated(source(1, 2))
      val gate     = new Completer[Option[(Int, Int)]]
      val pending  = Reader.unfoldAsync(0)(_ => gate)
      val running  = pending.read(-1).start
      val boom     = new RuntimeException("unfold")
      val failed   = Reader.unfoldAsync[Int, Int](0)(_ => Async.fail(boom))
      for {
        values <- runAsync(repeated.readN[Int](5)); ready <- runAsync(repeated.readable());
        _      <- runAsync(repeated.reset())
        replay <- runAsync(repeated.readInt(-1)); _       <- runAsync(repeated.close()); _       <- runAsync(repeated.close());
        rend   <- runAsync(repeated.readInt(-1))
        _      <- runAsync(pending.close()); cancelled    <- runAsync(running); _                <- runAsync(pending.close());
        pc     <- runAsync(pending.isClosed)
        ff     <- runAsync(failed.read(-1).either); ff2   <- runAsync(failed.read(-1).either); _ <- runAsync(failed.close())
        uc     <- runAsync(emptyThenValue.readable()); ue <- runAsync(emptyThenValue.read(-1));
        _      <- runAsync(emptyThenValue.reset()); ur    <- runAsync(emptyThenValue.readable())
      } yield assertTrue(
        values == Chunk(1, 2, 1, 2, 1),
        ready,
        replay == 1,
        rend == -1,
        cancelled == -1,
        pc,
        ff == Left(boom),
        ff2 == Left(boom),
        uc,
        ue == -1,
        ur,
        empties == 1
      )
    },
    test("buffered and intersperse expose delegated, cached, EOF, close, and every pull lane") {
      def buffered[A: JvmType.Infer](values: A*)      = AsyncStatefulReader.buffered(source(values: _*), 2)
      def inter[A: JvmType.Infer](a: A, sep: A, b: A) = AsyncStatefulReader.intersperse(source(a, b), sep)
      val ints                                        = buffered(1, 2, 3); val dest = Array.fill(4)(-1)
      val refs                                        = buffered("a", "b", "c")
      for {
        tr0    <- runAsync(ints.readable()); n0                             <- runAsync(ints.readInts(dest, 0, 0));
        n1     <- runAsync(ints.readInts(dest, 1, 1))
        tr1    <- runAsync(ints.readable()); n2                             <- runAsync(ints.readInts(dest, 2, 2));
        n3     <- runAsync(ints.readInts(dest, 0, 1))
        rv1    <- runAsync(refs.read[Any](null)); rr                        <- runAsync(refs.readable()); rv2   <- runAsync(refs.read[Any](null))
        z      <- runAsync(inter(true, false, false).readAll[Boolean]()); b <- runAsync(inter[Byte](1, 0, 2).readAll[Byte]())
        c      <- runAsync(inter('a', '|', 'b').readAll[Char]()); s         <- runAsync(inter[Short](1, 0, 2).readAll[Short]())
        i      <- runAsync(inter(1, 0, 2).readAll[Int]()); l                <- runAsync(inter(1L, 0L, 2L).readAll[Long]())
        f      <- runAsync(inter(1f, 0f, 2f).readAll[Float]()); d           <- runAsync(inter(1d, 0d, 2d).readAll[Double]())
        r       = inter("a", "|", "b"); a                                   <- runAsync(r.read[Any](null)); sep <- runAsync(r.read[Any](null));
        cached <- runAsync(r.readable())
        last   <- runAsync(r.read[Any](null)); _                            <- runAsync(r.close()); closedReady <- runAsync(r.readable())
      } yield assertTrue(
        tr0,
        n0 == 0,
        n1 == 1,
        tr1,
        n2 == 1,
        n3 == 1,
        rv1 == "a",
        rr,
        rv2 == "b",
        z == Chunk(true, false, false),
        b == Chunk[Byte](1, 0, 2),
        c == Chunk('a', '|', 'b'),
        s == Chunk[Short](1, 0, 2),
        i == Chunk(1, 0, 2),
        l == Chunk(1L, 0L, 2L),
        f == Chunk(1f, 0f, 2f),
        d == Chunk(1d, 0d, 2d),
        a == "a",
        sep == "|",
        cached,
        last == "b",
        !closedReady
      )
    }
  )
}
