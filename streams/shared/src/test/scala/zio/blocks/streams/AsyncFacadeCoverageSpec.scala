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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.scope.Resource
import zio.blocks.streams.io.{Reader, Writer}
import zio.test._

object AsyncFacadeCoverageSpec extends StreamsBaseSpec {
  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class Generic[A](lane: JvmType, values: List[A]) extends Reader.AsyncReader[A] {
    private var rest                          = values
    private var closed                        = false
    override def jvmType: JvmType             = lane
    def close(): Async[Unit]                  = { closed = true; Async.succeed(()) }
    def isClosed: Async[Boolean]              = Async.succeed(closed || rest.isEmpty)
    def readable(): Async[Boolean]            = Async.succeed(!closed && rest.nonEmpty)
    override private[streams] def tryReadable = if (!closed && rest.nonEmpty) Reader.Available else Reader.Unavailable
    def read[B >: A](sentinel: B): Async[B]   = rest match {
      case head :: tail if !closed => rest = tail; Async.succeed(head)
      case _                       => Async.succeed(sentinel)
    }
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      read[Any](null).map(v => if (v == null) sentinel else if (v.asInstanceOf[Boolean]) 1 else 0)
    override def readByte(): Async[Int] =
      read[Any](null).map(v => if (v == null) -1 else v.asInstanceOf[Byte].toInt & 0xff)
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Char].toInt)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Short].toInt)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Int].toLong)
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Long])
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Float].toDouble)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      read[Any](null).map(v => if (v == null) sentinel else v.asInstanceOf[Double])
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      if (length == 0) Async.succeed(0)
      else readLong(Long.MinValue).map(v => if (v == Long.MinValue) -1 else { dest(offset) = v; 1 })
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      if (length == 0) Async.succeed(0)
      else read[Any](null).map(v => if (v == null) -1 else { dest(offset) = v.asInstanceOf[Double]; 1 })
  }

  def spec = suite("async facade coverage")(
    test("default async scalar, bulk, bounded and lifecycle helpers cover every lane") {
      val bool   = new Generic(JvmType.Boolean, List(true, false))
      val byte   = new Generic(JvmType.Byte, List[Byte](-1, 2))
      val char   = new Generic(JvmType.Char, List('A'))
      val short  = new Generic(JvmType.Short, List[Short](3))
      val int    = new Generic(JvmType.Int, List(4, 5, 6))
      val long   = new Generic(JvmType.Long, List(7L))
      val float  = new Generic(JvmType.Float, List(8.5f))
      val double = new Generic(JvmType.Double, List(9.5d))
      val ref    = new Generic(JvmType.AnyRef, List("a", "b"))
      val ba     = new Array[Byte](2); val ia  = new Array[Int](2); val la = new Array[Long](1)
      val fa     = new Array[Float](1); val da = new Array[Double](1)
      for {
        z1    <- runAsync(bool.readBoolean(-1)); z2     <- runAsync(bool.readBoolean(-1)); ze <- runAsync(bool.readBoolean(-1))
        bn    <- runAsync(byte.readBytes(ba, 0, 2)); be <- runAsync(byte.readByte())
        cv    <- runAsync(char.readChar(-1)); sv        <- runAsync(short.readShort(-1))
        in    <- runAsync(int.readInts(ia, 0, 2)); ir   <- runAsync(int.readUpToN[Int](5));
        ie    <- runAsync(int.readN[Int](-1))
        ln    <- runAsync(long.readLongs(la, 0, 1)); fn <- runAsync(float.readFloats(fa, 0, 1));
        dn    <- runAsync(double.readDoubles(da, 0, 1))
        refs  <- runAsync(ref.readAll[String]()); _     <- runAsync(ref.skip(3)); reset       <- runAsync(ref.reset().either)
        limit <- runAsync(ref.setLimit(1)); repeat      <- runAsync(ref.setRepeat()); skip    <- runAsync(ref.setSkip(1))
      } yield assertTrue(
        z1 == 1,
        z2 == 0,
        ze == -1,
        bn == 2,
        ba.toList == List[Byte](-1, 2),
        be == -1,
        cv == 65,
        sv == 3,
        in == 2,
        ia.toList == List(4, 5),
        ir == Chunk(6),
        ie == Chunk.empty,
        ln == 1,
        la.toList == List(7L),
        fn == 1,
        fa.toList == List(8.5f),
        dn == 1,
        da.toList == List(9.5d),
        refs == Chunk("a", "b"),
        reset.left.exists(_.isInstanceOf[UnsupportedOperationException]),
        !limit,
        !repeat,
        !skip
      )
    },
    test("async concat and release wrappers delegate all scalar, bulk and control surfaces") {
      var releases     = 0
      val concatenated = async[Boolean](true).concatReaderWithJvmType(() => async[Boolean](false), JvmType.Boolean)
      val bytes        = async[Byte](1)
        .concatReaderWithJvmType(() => async[Byte](2), JvmType.Byte)
        .withReleaseAsync { () => releases += 1; Async.succeed(()) }
      val ints  = async(1).concatReaderWithJvmType(() => async(2), JvmType.Int).withReleaseAsync(() => Async.succeed(()))
      val longs =
        async(1L).concatReaderWithJvmType(() => async(2L), JvmType.Long).withReleaseAsync(() => Async.succeed(()))
      val floats =
        async(1f).concatReaderWithJvmType(() => async(2f), JvmType.Float).withReleaseAsync(() => Async.succeed(()))
      val doubles =
        async(1d).concatReaderWithJvmType(() => async(2d), JvmType.Double).withReleaseAsync(() => Async.succeed(()))
      val ba = new Array[Byte](2); val ia  = new Array[Int](2); val la = new Array[Long](2)
      val fa = new Array[Float](2); val da = new Array[Double](2)
      for {
        z          <- runAsync(concatenated.readAll[Boolean]()); bn <- runAsync(bytes.readBytes(ba, 0, 2))
        in         <- runAsync(ints.readInts(ia, 0, 2)); ln         <- runAsync(longs.readLongs(la, 0, 2))
        fn         <- runAsync(floats.readFloats(fa, 0, 2)); dn     <- runAsync(doubles.readDoubles(da, 0, 2))
        _          <- runAsync(bytes.close()); _                    <- runAsync(bytes.close()); closed <- runAsync(bytes.isClosed);
        ready      <- runAsync(bytes.readable())
        closedRead <- runAsync(bytes.readByte()); control           <- runAsync(bytes.setRepeat().either)
      } yield assertTrue(
        z == Chunk(true, false),
        bn == 1,
        ba.toList == List[Byte](1, 0),
        in == 1,
        ia.toList == List(1, 0),
        ln == 1,
        la.toList == List(1L, 0L),
        fn == 1,
        fa.toList == List(1f, 0f),
        dn == 1,
        da.toList == List(1d, 0d),
        releases == 1,
        closed,
        !ready,
        closedRead == -1,
        control.left.exists(_.isInstanceOf[IOException])
      )
    },
    test("async stream nodes compose, recover, repeat, zip, defer, unfold and finalize") {
      var finalized   = 0
      val transformed = Stream(1, 2, 2, 3)
        .mapAsync(n => Async.succeed(n + 1))
        .filterAsync(n => Async.succeed(n % 2 == 0))
        .collectAsync(n => Async.succeed(if (n > 0) Some(n * 10) else None))
        .distinctByAsync(Async.succeed)
        .mapAccumAsync(0)((s, n) => Async.succeed((s + n, s + n)))
        .scanAsync(0)((s, n) => Async.succeed(s + n))
        .takeWhileAsync(n => Async.succeed(n < 100))
        .ensuringAsync { finalized += 1; Async.succeed(()) }
      val recovered = Stream.fail("x").catchAll(_ => Stream.unwrap(Async.succeed(Stream(7))))
      val defect    = Stream
        .die(new RuntimeException("x"))
        .catchDefect { case _ => Stream.unwrap(Async.succeed(Stream(8))) }
      val repeated = Stream(1, 2).repeated.take(5)
      val unfolded = Stream.unfoldAsync(0)(s => Async.succeed(if (s < 3) Some((s, s + 1)) else None))
      val deferred = Stream(9) ++ Stream.deferAsync { finalized += 1; Async.succeed(()) }
      for {
        a <- runAsync(transformed.runCollectAsync); b <- runAsync(recovered.runCollectAsync);
        c <- runAsync(defect.runCollectAsync)
        d <- runAsync(repeated.runCollectAsync); e    <- runAsync(unfolded.runCollectAsync);
        f <- runAsync(deferred.runCollectAsync)
        g <- runAsync((Stream(1, 2) && Stream.fromReader[Nothing, Int](async(3, 4))).runCollectAsync)
      } yield assertTrue(
        a == Right(Chunk(0, 20, 80)),
        b == Right(Chunk(7)),
        c == Right(Chunk(8)),
        d == Right(Chunk(1, 2, 1, 2, 1)),
        e == Right(Chunk(0, 1, 2)),
        f == Right(Chunk(9)),
        g == Right(Chunk((1, 3), (2, 4))),
        finalized == 2
      )
    },
    test("async acquire/use/release and Resource wrappers cover success and failures") {
      var acquired = 0; var released = List.empty[String]
      val managed  = Stream.fromAcquireReleaseAsync(
        { acquired += 1; Async.succeed("r") },
        (r: String) => { released = released :+ r; Async.succeed(()) }
      )(r => Stream.unwrap(Async.succeed(Stream(r.length))))
      val resource   = Resource.acquireRelease("resource")(r => released = released :+ r)
      val scoped     = Stream.fromResource(resource)(r => Stream.unwrap(Async.succeed(Stream(r.length))))
      val useFailure = new RuntimeException("use")
      val failed     = Stream.fromAcquireReleaseAsync[String, Nothing, Nothing](
        Async.succeed("f"),
        (r: String) => { released = released :+ r; Async.succeed(()) }
      )(_ => Stream.unwrap(Async.fail(useFailure)))
      for {
        a <- runAsync(managed.runCollectAsync); b <- runAsync(scoped.runCollectAsync);
        c <- runAsync(failed.runCollectAsync.either)
      } yield assertTrue(
        a == Right(Chunk(1)),
        b == Right(Chunk(8)),
        c == Left(useFailure),
        acquired == 1,
        released == List("r", "resource", "f")
      )
    },
    test("every synchronous facade node is exercised on both sides of an async boundary") {
      def boundary[A: JvmType.Infer](values: A*) = Stream.fromReader[Nothing, A](async(values: _*))
      var deferred                               = 0
      var ensured                                = 0
      val left                                   = (boundary(1, 2, 3) ++ Stream(4, 5))
        .drop(1)
        .take(4)
        .takeWhile(_ < 5)
        .scan(0)(_ + _)
        .collect { case n if n != 2 => n }
        .ensuring(ensured += 1)
      val right = (Stream(1, 2) ++ boundary(3, 4, 5))
        .drop(1)
        .take(3)
        .takeWhile(_ < 5)
      val suspended     = Stream.suspend { deferred += 1; boundary(6, 7) } ++ Stream.defer(deferred += 10)
      val mappedFailure = Stream.fail("bad").mapError(_.length).orElse(boundary(8))
      for {
        a <- runAsync(left.runCollectAsync)
        b <- runAsync(right.runCollectAsync)
        c <- runAsync(suspended.runCollectAsync)
        d <- runAsync(mappedFailure.runCollectAsync)
        i <- runAsync(boundary(1, 2, 3).runFoldAsync(0)((s, n) => Async.succeed(s + n)))
        l <- runAsync(boundary(1L, 2L).runFoldAsync(0L)((s, n) => Async.succeed(s + n)))
        x <- runAsync(boundary(1, 2).runFoldAsync(0d)((s, n) => Async.succeed(s + n)))
        r <- runAsync(boundary(1, 2).runFoldAsync(List.empty[Int])((s, n) => Async.succeed(n :: s)))
      } yield assertTrue(
        a == Right(Chunk(0, 5, 9)),
        b == Right(Chunk(2, 3, 4)),
        c == Right(Chunk(6, 7)),
        d == Right(Chunk(8)),
        i == Right(6),
        l == Right(3L),
        x == Right(3d),
        r == Right(List(2, 1)),
        deferred == 11,
        ensured == 1
      )
    },
    test("sink async facades and writer mirrors preserve values and callback failures") {
      val callback = new RuntimeException("callback")
      val sink     = Sink
        .foldLeft[Int, Int](0)(_ + _)
        .contramapAsync[Int, String](s => Async.succeed(s.toInt))
        .mapAsync(n => Async.succeed(n * 2))
      val failureSink = Sink.createAsync[Nothing, Int, Unit](_ => throw callback)
      val written     = scala.collection.mutable.ListBuffer.empty[Int]
      val writer      = new Writer[Int] {
        private var closed             = false
        def close(): Unit              = closed = true
        def isClosed: Boolean          = closed
        def write(value: Int): Boolean = if (closed) false else { written += value; true }
      }
      for {
        value  <- runAsync(sink.drain(async("2", "3"))); failure <- runAsync(failureSink.drain(async(1)).either)
        one    <- runAsync(writer.writeIntAsync(1)); suffix      <- runAsync(writer.writeAllAsync(Chunk(2, 3)))
        open   <- runAsync(writer.writeableAsync()); _           <- runAsync(writer.closeAsync());
        closed <- runAsync(writer.isClosedAsync)
      } yield assertTrue(
        value == 10,
        failure == Left(callback),
        one,
        suffix == Chunk.empty,
        open,
        closed,
        written.toList == List(1, 2, 3)
      )
    }
  )
}
