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
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Exact Reader.scala residual checklist from /tmp/coverage-gaps-current3.txt:
 * 201-202, 204-205, 208; 855, 894, 916, 922, 924-937, 954, 960, 967; 1090;
 * 1134-1136, 1211, 1214, 1216-1217, 1222, 1365-1372; 1401, 1461, 1494,
 * 1509-1510, 1631, 1634, 1653-1654, 1669, 1716; 1759, 1761, 1764, 1767, 1769,
 * 1772-1777, 1798, 1803-1805; 4298, 4310, 4356, 4359, 4370, 4374, 4392, 4394,
 * 4398, 4405, 4430, 4434; 4914, 4928, 4935, 4999, 5003, 5088, 5100.
 */
object AsyncReaderFacadeExhaustiveCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private def scalarPair[A: JvmType.Infer](value: A): Async[(Any, Any)] = {
    val r                     = source(value)
    def readOne(): Async[Any] = r.jvmType match {
      case JvmType.Boolean => r.asInstanceOf[Reader.AsyncReader[Boolean]].readBoolean(-1)
      case JvmType.Byte    => r.asInstanceOf[Reader.AsyncReader[Byte]].readByte()
      case JvmType.Char    => r.asInstanceOf[Reader.AsyncReader[Char]].readChar(-1)
      case JvmType.Short   => r.asInstanceOf[Reader.AsyncReader[Short]].readShort(-1)
      case JvmType.Int     => r.asInstanceOf[Reader.AsyncReader[Int]].readInt(-1L)
      case JvmType.Long    => r.asInstanceOf[Reader.AsyncReader[Long]].readLong(-1L)
      case JvmType.Float   => r.asInstanceOf[Reader.AsyncReader[Float]].readFloat(-1d)
      case JvmType.Double  => r.asInstanceOf[Reader.AsyncReader[Double]].readDouble(-1d)
      case JvmType.AnyRef  => r.asInstanceOf[Reader.AsyncReader[Any]].read[Any](null)
    }
    readOne().flatMap(a => readOne().map(b => (a, b)))
  }

  private def defaultScalarPair(lane: JvmType, value: Any): Async[(Any, Any)] = {
    val reader = new Reader.AsyncReader[Any] {
      private var next: Any                     = value
      override def jvmType: JvmType             = lane
      def close(): Async[Unit]                  = { next = null; Async.succeed(()) }
      def isClosed: Async[Boolean]              = Async.succeed(next.asInstanceOf[AnyRef] eq null)
      def readable(): Async[Boolean]            = Async.succeed(next.asInstanceOf[AnyRef] ne null)
      def read[A >: Any](sentinel: A): Async[A] =
        if (next.asInstanceOf[AnyRef] eq null) Async.succeed(sentinel)
        else { val current = next; next = null; Async.succeed(current.asInstanceOf[A]) }
      override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int] =
        read[Any](null).map(v => if (v == null) sentinel else if (v.asInstanceOf[Boolean]) 1 else 0)
      override def readByte(): Async[Int] = read[Any](null).map {
        case null       => -1
        case v: Boolean => if (v) 1 else 0
        case v: Char    => v.toInt & 0xff
        case v: Number  => v.intValue() & 0xff
      }
      override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Char].toInt)
      override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Short].toInt)
      override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Int].toLong)
      override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Long])
      override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Float].toDouble)
      override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double] =
        read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Double])
    }
    def readOne(): Async[Any] = lane match {
      case JvmType.Boolean => reader.asInstanceOf[Reader.AsyncReader[Boolean]].readBoolean(-1)
      case JvmType.Byte    => reader.asInstanceOf[Reader.AsyncReader[Byte]].readByte()
      case JvmType.Char    => reader.asInstanceOf[Reader.AsyncReader[Char]].readChar(-1)
      case JvmType.Short   => reader.asInstanceOf[Reader.AsyncReader[Short]].readShort(-1)
      case JvmType.Int     => reader.asInstanceOf[Reader.AsyncReader[Int]].readInt(-1L)
      case JvmType.Long    => reader.asInstanceOf[Reader.AsyncReader[Long]].readLong(-1L)
      case JvmType.Float   => reader.asInstanceOf[Reader.AsyncReader[Float]].readFloat(-1d)
      case JvmType.Double  => reader.asInstanceOf[Reader.AsyncReader[Double]].readDouble(-1d)
      case JvmType.AnyRef  => reader.read[Any](null)
    }
    readOne().flatMap(first => readOne().map(second => (first, second)))
  }

  def spec = suite("AsyncReader facade exhaustive coverage")(
    test("readByte dispatches every JvmType and readUpToN takes every exit") {
      final class Unknown(private var xs: List[Any], available: Boolean) extends Reader.AsyncReader[Any] {
        def close()                               = { xs = Nil; Async.succeed(()) }
        def isClosed                              = Async.succeed(xs.isEmpty)
        def readable()                            = Async.succeed(xs.nonEmpty)
        override private[streams] def tryReadable =
          if (available && xs.nonEmpty) Reader.Available else Reader.Unavailable
        def read[B >: Any](sentinel: B) = xs match {
          case h :: t => xs = t; Async.succeed(h.asInstanceOf[B])
          case Nil    => Async.succeed(sentinel)
        }
      }
      val limited = new Unknown(List(1, 2), available = true)
      val stopped = new Unknown(List(3, 4), available = false)
      for {
        z           <- runAsync(scalarPair(true)); b                       <- runAsync(scalarPair[Byte](-1)); c <- runAsync(scalarPair('\u0141'))
        s           <- runAsync(scalarPair[Short](-2)); i                  <- runAsync(scalarPair(0x181)); l    <- runAsync(scalarPair(0x182L))
        f           <- runAsync(scalarPair(387.9f)); d                     <- runAsync(scalarPair(388.9));
        r           <- runAsync(scalarPair[Any](Integer.valueOf(389)))
        dz          <- runAsync(defaultScalarPair(JvmType.Boolean, true));
        db          <- runAsync(defaultScalarPair(JvmType.Byte, (-1).toByte))
        dc          <- runAsync(defaultScalarPair(JvmType.Char, '\u0141'));
        ds          <- runAsync(defaultScalarPair(JvmType.Short, (-2).toShort))
        di          <- runAsync(defaultScalarPair(JvmType.Int, 0x181)); dl <- runAsync(defaultScalarPair(JvmType.Long, 0x182L))
        df          <- runAsync(defaultScalarPair(JvmType.Float, 387.9f));
        dd          <- runAsync(defaultScalarPair(JvmType.Double, 388.9d))
        dr          <- runAsync(defaultScalarPair(JvmType.AnyRef, Integer.valueOf(389)))
        zero        <- runAsync(limited.readUpToN[Any](0)); exact          <- runAsync(limited.readUpToN[Any](2))
        unavailable <- runAsync(stopped.readUpToN[Any](9)); eof            <- runAsync(new Unknown(Nil, true).readUpToN[Any](2))
      } yield assertTrue(
        z == ((1, -1)),
        b == ((255, -1)),
        c == ((321, -1)),
        s == ((-2, -1)),
        i == ((385L, -1L)),
        l == ((386L, -1L)),
        f == ((387.9f.toDouble, -1d)),
        d == ((388.9d, -1d)),
        r == ((Integer.valueOf(389), null)),
        dz == z,
        db == b,
        dc == c,
        ds == s,
        di == i,
        dl == l,
        df == f,
        dd == d,
        dr == r,
        zero.isEmpty,
        exact == Chunk[Any](1, 2),
        unavailable == Chunk[Any](3),
        eof.isEmpty
      )
    },
    test("sync facade forces lazy factories, every deferred closure, EOF and closed values") {
      var tails  = 0; var releases = 0
      val sync   = Reader.fromChunk(Chunk(1, 2))
      val concat = sync.toAsync.concatAsyncWithJvmType(
        () => { tails += 1; Async.succeed(Reader.singleInt(3)) },
        JvmType.Int
      )
      val released = Reader.singleInt(1).withReleaseAsync { () => releases += 1; Async.succeed(()) }
      val adapted  = Reader.fromChunk(Chunk(1, 2)).toAsync
      val arr      = Array.fill(2)(0)
      for {
        all         <- runAsync(adapted.readAll[Int]()); _         <- runAsync(adapted.reset()); n      <- runAsync(adapted.readN[Int](1))
        up          <- runAsync(adapted.readUpToN[Int](1)); end    <- runAsync(adapted.readInt(-1L)); _ <- runAsync(adapted.reset())
        _           <- runAsync(adapted.readInts(arr, 0, 2)); _    <- runAsync(adapted.setRepeat()); _  <- runAsync(adapted.close())
        closedRead  <- runAsync(adapted.readInt(-7)); closedAll    <- runAsync(adapted.readAll[Int]())
        closedReady <- runAsync(adapted.readable()); closedState   <- runAsync(adapted.isClosed)
        control     <- runAsync(adapted.setSkip(1).either); values <- runAsync(concat.readAll[Int]())
        _           <- runAsync(released.close()); _               <- runAsync(released.close())
      } yield assertTrue(
        all == Chunk(1, 2),
        n == Chunk(1),
        up == Chunk(2),
        end == -1,
        arr.sameElements(Array(1, 2)),
        closedRead == -7,
        closedAll.isEmpty,
        !closedReady,
        closedState,
        control.isLeft,
        values == Chunk(1, 2, 3),
        tails == 1,
        releases == 1
      )
    },
    test("concat transitions tails, reset, controls, failures, null and close deterministically") {
      val failure = new RuntimeException("tail")
      var forced  = 0
      val chain   = source(1)
        .concatAsyncWithJvmType(() => { forced += 1; Async.succeed(source(2)) }, JvmType.Int)
        .concatAsyncWithJvmType(() => { forced += 1; Async.succeed(Reader.singleInt(3)) }, JvmType.Int)
      val bad = source[Int]().concatAsyncWithJvmType(() => Async.fail(failure), JvmType.Int)
      val nul = source[String]().concatAsync(() => Async.succeed(source[String](null)))
      for {
        ready  <- runAsync(chain.readable()); values   <- runAsync(chain.readAll[Int]());
        end    <- runAsync(chain.readInt(-1))
        _      <- runAsync(chain.reset()); again       <- runAsync(chain.readN[Int](4)); _  <- runAsync(chain.skip(1))
        limit  <- runAsync(chain.setLimit(1)); repeat  <- runAsync(chain.setRepeat()); skip <- runAsync(chain.setSkip(1))
        failed <- runAsync(bad.readInt(-1).either); nv <- runAsync(nul.read[Any](new Object))
        _      <- runAsync(chain.close()); _           <- runAsync(chain.close()); closed   <- runAsync(chain.isClosed);
        post   <- runAsync(chain.readInt(-9))
      } yield assertTrue(
        ready,
        values == Chunk(1, 2, 3),
        end == -1,
        again == Chunk(1, 2, 3),
        !limit,
        !repeat,
        !skip,
        failed == Left(failure),
        nv == null,
        closed,
        post == -9,
        forced == 4
      )
    },
    test("release delegation, repeated lifecycle, and unfold cancellation/reentrancy settle") {
      val releaseCount                  = new AtomicInteger
      val release                       = source(1L, 2L).withReleaseAsync { () => releaseCount.incrementAndGet(); Async.succeed(()) }
      val repeated                      = Reader.repeated(source(1, 2))
      val gate                          = new Completer[Option[(Int, Int)]]; val entered = new Completer[Unit]
      val unfold                        = Reader.unfoldAsync(0) { _ => entered.succeed(()); gate }
      val pull                          = unfold.readInt(-1).start
      var self: Reader.AsyncReader[Int] = null
      self = Reader.unfoldAsync(0) { _ => self.close().block; Async.succeed(Some((1, 1))) }
      for {
        l           <- runAsync(release.readLong(-1)); _        <- runAsync(release.reset());
        f           <- runAsync(source(1f).withReleaseAsync(() => Async.succeed(())).readFloat(-1))
        d           <- runAsync(source(1d).withReleaseAsync(() => Async.succeed(())).readDouble(-1));
        _           <- runAsync(release.close())
        closedReady <- runAsync(release.readable()); closedSkip <- runAsync(release.setSkip(1).either)
        values      <- runAsync(repeated.readN[Int](5)); ready  <- runAsync(repeated.readable());
        _           <- runAsync(repeated.reset())
        replay      <- runAsync(repeated.readInt(-1)); _        <- runAsync(repeated.close()); end <- runAsync(repeated.readInt(-1))
        _           <- runAsync(entered); _                     <- runAsync(unfold.close()); stale <- runAsync(pull);
        _            = gate.succeed(Some((99, 100)))
        selfValue   <- runAsync(self.readInt(-1)); selfClosed   <- runAsync(self.isClosed)
      } yield assertTrue(
        l == 1L,
        f == 1d,
        d == 1d,
        !closedReady,
        closedSkip.isLeft,
        releaseCount.get == 1,
        values == Chunk(1, 2, 1, 2, 1),
        ready,
        replay == 1,
        end == -1,
        stale == -1,
        selfValue == -1,
        selfClosed
      )
    },
    test("release wrapper delegates every residual scalar, collection, and control method") {
      def released[A: JvmType.Infer](values: A*) =
        source(values: _*).withReleaseAsync(() => Async.succeed(()))
      val refs     = released("a", "b")
      val controls = released(1, 2)
      for {
        ref   <- runAsync(refs.read[Any](null)); rest             <- runAsync(refs.readN[String](2))
        bool  <- runAsync(released(true).readBoolean(-1)); char   <- runAsync(released('x').readChar(-1))
        short <- runAsync(released[Short](2).readShort(-1)); long <- runAsync(released(3L).readLong(-1))
        float <- runAsync(released(4f).readFloat(-1)); double     <- runAsync(released(5d).readDouble(-1))
        _     <- runAsync(controls.skip(1)); repeat               <- runAsync(controls.setRepeat()); _ <- runAsync(controls.setSkip(1))
        ready <- runAsync(controls.readable()); _                 <- runAsync(controls.close())
      } yield assertTrue(
        ref == "a",
        rest == Chunk("b"),
        bool == 1,
        char == 'x'.toInt,
        short == 2,
        long == 3L,
        float == 4d,
        double == 5d,
        !repeat,
        !ready
      )
    },
    test("default scalar overloads, sync adaptation, nested concat reset, and closed controls") {
      final class Default[A](lane: JvmType, private var values: List[A]) extends Reader.AsyncReader[A] {
        override def jvmType          = lane
        def close()                   = { values = Nil; Async.succeed(()) }
        def isClosed                  = Async.succeed(values.isEmpty)
        def readable()                = Async.succeed(values.nonEmpty)
        def read[B >: A](sentinel: B) = values match {
          case head :: tail => values = tail; Async.succeed(head)
          case Nil          => Async.succeed(sentinel)
        }
        override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] = values match {
          case head :: tail => values = tail; Async.succeed(head.asInstanceOf[Long])
          case Nil          => Async.succeed(sentinel)
        }
        override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
          if (length == 0) Async.succeed(0)
          else readLong(Long.MinValue).map(v => if (v == Long.MinValue) -1 else { dest(offset) = v; 1 })
        override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] = values match {
          case head :: tail => values = tail; Async.succeed(head.asInstanceOf[Double])
          case Nil          => Async.succeed(sentinel)
        }
        override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
          if (length == 0) Async.succeed(0)
          else readDouble(Double.NaN).map(v => if (v.isNaN) -1 else { dest(offset) = v; 1 })
      }
      val sync          = Reader.fromChunk(Chunk(1, 2))
      val adapted       = sync.toAsync
      val nested        = source(1).concatAsync(() => Async.succeed(source(2).concatAsync(() => Async.succeed(source(3)))))
      val closed        = source(1)
      val closedRelease = source(true).withReleaseAsync(() => Async.succeed(()))
      for {
        long       <- runAsync(new Default[Long](JvmType.Long, List(9L)).readLong(-1L))
        double     <- runAsync(new Default[Double](JvmType.Double, List(2.5d)).readDouble(-1d))
        bulkLong   <- runAsync(new Default[Long](JvmType.Long, List(1L, 2L)).readUpToN[Long](2))
        bulkDouble <- runAsync(new Default[Double](JvmType.Double, List(1d, 2d)).readUpToN[Double](2))
        iv         <- runAsync(adapted.readAll[Int]())
        first      <- runAsync(nested.readAll[Int]()); _ <- runAsync(nested.reset());
        second     <- runAsync(nested.readAll[Int]())
        _          <- runAsync(closed.close()); limit    <- runAsync(closed.setLimit(1).either);
        repeat     <- runAsync(closed.setRepeat().either)
        _          <- runAsync(closed.skip(1).either); _ <- runAsync(closedRelease.close())
        rb         <- runAsync(closedRelease.readBoolean(-7).either)
      } yield assertTrue(
        long == 9L,
        double == 2.5d,
        bulkLong == Chunk(1L),
        bulkDouble == Chunk(1d),
        iv == Chunk(1, 2),
        first == Chunk(1, 2, 3),
        second == Chunk(1, 2, 3),
        limit.isLeft,
        repeat.isLeft,
        rb == Right(-7)
      )
    }
  ) @@ TestAspect.sequential
}
