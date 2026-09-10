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
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterTransformCoverageSpec extends StreamsBaseSpec {

  private final class Replacing[A](value: A) extends Pollable[A] {
    private val replacement = new Pollable[A] {
      def poll(onComplete: Runnable): Async[A] = Async.succeed(value)
    }
    def poll(onComplete: Runnable): Async[A] = replacement
  }

  private final class Reentrant[A](value: A) extends Pollable[A] {
    private var first                        = true
    def poll(onComplete: Runnable): Async[A] =
      if (first) {
        first = false
        onComplete.run()
        this
      } else Async.succeed(value)
  }

  private final class ResettableIntReader(initial: List[Int]) extends Reader.AsyncReader[Any] {
    private var values                                                          = initial
    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = Async.succeed(())
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(values.nonEmpty)
    def read[A >: Any](sentinel: A): Async[A]                                   = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
      values match {
        case head :: tail => values = tail; Async.succeed(head.toLong)
        case Nil          => Async.succeed(Long.MinValue)
      }
    override def reset(): Async[Unit] = { values = initial; Async.succeed(()) }
  }

  private def transformed[A, B](stream: Stream[Nothing, A], in: JvmType, out: JvmType)(f: A => B): AsyncInterpreter = {
    val interpreter = AsyncInterpreter.fromStream(stream)
    interpreter.addMap(in, out)(f)
    interpreter
  }

  def spec = suite("AsyncInterpreter transform coverage")(
    test("scalar results preserve every primitive kind and sentinel edge value") {
      val booleans = transformed(Stream(true, false), JvmType.Boolean, JvmType.Boolean)(identity[Boolean])
      val bytes    = transformed(Stream(Byte.MinValue, (-1).toByte), JvmType.Byte, JvmType.Byte)(identity[Byte])
      val chars    = transformed(Stream('\u0000', '\uffff'), JvmType.Char, JvmType.Char)(identity[Char])
      val shorts   = transformed(Stream(Short.MinValue), JvmType.Short, JvmType.Short)(identity[Short])
      val ints     = transformed(Stream(Int.MinValue, -1), JvmType.Int, JvmType.Int)(identity[Int])
      val longs    = transformed(Stream(Long.MaxValue), JvmType.Long, JvmType.Long)(identity[Long])
      val floats   = transformed(Stream(Float.NaN, Float.PositiveInfinity), JvmType.Float, JvmType.Float)(identity[Float])
      val doubles  = transformed(Stream(Double.NaN, Double.MaxValue), JvmType.Double, JvmType.Double)(identity[Double])
      for {
        b1 <- runAsync(booleans.readBoolean(-7)); b2 <- runAsync(booleans.readBoolean(-7));
        be <- runAsync(booleans.readBoolean(-7))
        y1 <- runAsync(bytes.readByte()); y2         <- runAsync(bytes.readByte()); ye   <- runAsync(bytes.readByte())
        c1 <- runAsync(chars.readChar(-7)); c2       <- runAsync(chars.readChar(-7)); ce <- runAsync(chars.readChar(-7))
        s1 <- runAsync(shorts.readShort(-7)); se     <- runAsync(shorts.readShort(-7))
        i1 <- runAsync(ints.readInt(99L)); i2        <- runAsync(ints.readInt(99L)); ie  <- runAsync(ints.readInt(99L))
        l1 <- runAsync(longs.readLong(99L)); le      <- runAsync(longs.readLong(99L))
        f1 <- runAsync(floats.readFloat(-7.0)); f2   <- runAsync(floats.readFloat(-7.0));
        fe <- runAsync(floats.readFloat(-7.0))
        d1 <- runAsync(doubles.readDouble(-7.0)); d2 <- runAsync(doubles.readDouble(-7.0));
        de <- runAsync(doubles.readDouble(-7.0))
      } yield assertTrue(
        b1 == 1,
        b2 == 0,
        be == -7,
        y1 == 128,
        y2 == 255,
        ye == -1,
        c1 == 0,
        c2 == 65535,
        ce == -7,
        s1 == Short.MinValue.toInt,
        se == -7,
        i1 == Int.MinValue.toLong,
        i2 == -1L,
        ie == 99L,
        l1 == Long.MaxValue,
        le == 99L,
        f1.isNaN,
        f2 == Float.PositiveInfinity.toDouble,
        fe == -7.0,
        d1.isNaN,
        d2 == Double.MaxValue,
        de == -7.0
      )
    },
    test("synchronous maps cross every physical input and output lane") {
      val types                                      = Array(JvmType.Int, JvmType.Long, JvmType.Float, JvmType.Double, JvmType.AnyRef)
      val expected                                   = Array[Any](11, 12L, 13.0f, 14.0, "15")
      def source(lane: Int): Reader.AsyncReader[Any] = lane match {
        case 0 => Reader.singleInt(7).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 1 => Reader.singleLong(7L).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 2 => Reader.singleFloat(7.0f).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case 3 => Reader.singleDouble(7.0).toAsync.asInstanceOf[Reader.AsyncReader[Any]]
        case _ => Reader.single[AnyRef]("7").toAsync.asInstanceOf[Reader.AsyncReader[Any]]
      }
      val effects = (for {
        in  <- types.indices
        out <- types.indices
      } yield {
        val interpreter = new AsyncInterpreter(source(in))
        interpreter.addMap[Any, Any](types(in), types(out))(_ => expected(out))
        runAsync(interpreter.read[Any]("eof")).map(_ == expected(out))
      }).toList
      zio.ZIO.collectAll(effects).map(results => assertTrue(results.forall(identity)))
    },
    test("async map and collect convert every output lane including logical Boolean") {
      val outputs = List[(JvmType, Any)](
        JvmType.Boolean -> true,
        JvmType.Int     -> 2,
        JvmType.Long    -> 3L,
        JvmType.Float   -> 4.0f,
        JvmType.Double  -> 5.0,
        JvmType.AnyRef  -> "six"
      )
      val effects = outputs.flatMap { case (out, value) =>
        val mapped = AsyncInterpreter.fromStream(Stream(1))
        mapped.addAsyncMap[Int, Any](JvmType.Int, out)(_ => new Replacing(value).asInstanceOf[Async[Any]])
        val collected = AsyncInterpreter.fromStream(Stream(1))
        collected.addAsyncCollect[Int, Any](JvmType.Int, out)(_ =>
          new Reentrant(Some(value)).asInstanceOf[Async[Option[Any]]]
        )
        List(runAsync(mapped.read[Any]("eof")), runAsync(collected.read[Any]("eof"))).map(_.map(_ == value))
      }
      zio.ZIO.collectAll(effects).map(results => assertTrue(results.forall(identity)))
    },
    test("filter tap takeWhile distinct and mapAccum compose") {
      val taps        = new AtomicInteger(0)
      val interpreter = AsyncInterpreter.fromStream(Stream(1, 1, 2, 3, 4))
      interpreter.addAsyncFilter[Int](JvmType.Int)(n => Async.succeed(n > 0))
      interpreter.addAsyncTap[Int](JvmType.Int)(_ => Async.succeed { taps.incrementAndGet(); () })
      interpreter.addAsyncDistinctKey[Int, Int](JvmType.Int)(n => Async.succeed(n))
      interpreter.addAsyncMapAccum[Int, Int, Long](0, JvmType.Int, JvmType.Long) { (s, n) =>
        Async.succeed((s + n, (s + n).toLong))
      }
      interpreter.addAsyncTakeWhile[Long](JvmType.Long)(n => Async.succeed(n != -3L))
      for {
        a <- runAsync(interpreter.readLong(-1L))
        b <- runAsync(interpreter.readLong(-1L))
        c <- runAsync(interpreter.readLong(-1L))
        d <- runAsync(interpreter.readLong(-1L))
        e <- runAsync(interpreter.readLong(-1L))
      } yield assertTrue(List(a, b, c, d, e) == List(1L, 3L, 6L, 10L, -1L), taps.get() == 5)
    },
    test("scan emits and resets its initial and accumulated transform state") {
      val source      = new ResettableIntReader(List(1, 2))
      val interpreter = AsyncInterpreter.fromReaderWithAsyncScanForTest[Int, Int](source, 10, JvmType.Int, JvmType.Int) {
        (state, value) => Async.succeed(state + value)
      }
      for {
        a <- runAsync(interpreter.readInt(-1L))
        b <- runAsync(interpreter.readInt(-1L))
        c <- runAsync(interpreter.readInt(-1L))
        _ <- runAsync(interpreter.reset())
        d <- runAsync(interpreter.readInt(-1L))
        e <- runAsync(interpreter.readInt(-1L))
        f <- runAsync(interpreter.readInt(-1L))
      } yield assertTrue(List(a, b, c) == List(10L, 11L, 13L), List(d, e, f) == List(10L, 11L, 13L))
    },
    test("reset clears asynchronous distinct keys and restores mapAccum state") {
      val source = new ResettableIntReader(List(1, 1, 2))
      val stream = Stream
        .fromReader[Nothing, Int](source.asInstanceOf[Reader.AsyncReader[Int]])
        .distinctByAsync(Async.succeed)
        .mapAccumAsync(0) { (state, value) =>
          val next = state + value
          Async.succeed((next, next))
        }
      val interpreter = AsyncInterpreter.fromStream(stream)
      for {
        a <- runAsync(interpreter.readInt(-1L))
        b <- runAsync(interpreter.readInt(-1L))
        c <- runAsync(interpreter.readInt(-1L))
        _ <- runAsync(interpreter.reset())
        d <- runAsync(interpreter.readInt(-1L))
        e <- runAsync(interpreter.readInt(-1L))
        f <- runAsync(interpreter.readInt(-1L))
      } yield assertTrue(List(a, b, c) == List(1L, 3L, -1L), List(d, e, f) == List(1L, 3L, -1L))
    },
    test("bulk reads honor zero length, offsets, limits, and empty input") {
      val bytes = transformed(Stream[Byte](1, 2, 3), JvmType.Byte, JvmType.Byte)(identity[Byte])
      val ints  = transformed(Stream(4, 5, 6), JvmType.Int, JvmType.Int)(identity[Int])
      val destB = Array.fill[Byte](5)(-1)
      val destI = Array.fill[Int](5)(-1)
      val empty = AsyncInterpreter.fromStream(Stream.empty)
      for {
        zero  <- runAsync(bytes.readBytes(Array.emptyByteArray, 0, 0))
        bn    <- runAsync(bytes.readBytes(destB, 1, 2))
        in    <- runAsync(ints.readInts(destI, 2, 3))
        chunk <- runAsync(empty.readUpToN[Int](Int.MaxValue))
      } yield assertTrue(
        zero == 0,
        bn == 2,
        in == 3,
        destB.toList == List[Byte](-1, 1, 2, -1, -1),
        destI.toList == List(-1, -1, 4, 5, 6),
        chunk.isEmpty
      )
    },
    test("exact chunk reads fill every specialized lane and cross cooperative yield boundaries") {
      val boolean  = AsyncInterpreter.fromStream(Stream(true, false))
      val bytes    = AsyncInterpreter.fromStream(Stream[Byte](1, 2))
      val chars    = AsyncInterpreter.fromStream(Stream('a', 'b'))
      val shorts   = AsyncInterpreter.fromStream(Stream[Short](1, 2))
      val ints     = AsyncInterpreter.fromStream(Stream(1, 2))
      val longs    = AsyncInterpreter.fromStream(Stream(1L, 2L))
      val floats   = AsyncInterpreter.fromStream(Stream(1.0f, 2.0f))
      val doubles  = AsyncInterpreter.fromStream(Stream(1.0, 2.0))
      val refs     = AsyncInterpreter.fromStream(Stream("a", "b"))
      val yielding = AsyncInterpreter.fromStream(Stream.fromIterable(0 until 4096))
      for {
        z <- runAsync(boolean.readN[Boolean](2)); b <- runAsync(bytes.readN[Byte](2))
        c <- runAsync(chars.readN[Char](2)); s      <- runAsync(shorts.readN[Short](2))
        i <- runAsync(ints.readN[Int](2)); l        <- runAsync(longs.readN[Long](2))
        f <- runAsync(floats.readN[Float](2)); d    <- runAsync(doubles.readN[Double](2))
        r <- runAsync(refs.readN[String](2)); y     <- runAsync(yielding.readN[Int](4096))
      } yield assertTrue(
        z == Chunk(true, false),
        b == Chunk[Byte](1, 2),
        c == Chunk('a', 'b'),
        s == Chunk[Short](1, 2),
        i == Chunk(1, 2),
        l == Chunk(1L, 2L),
        f == Chunk(1.0f, 2.0f),
        d == Chunk(1.0, 2.0),
        r == Chunk("a", "b"),
        y.length == 4096,
        y(0) == 0,
        y(4095) == 4095
      )
    },
    test("null, throw, failed, and null-success callback results remain callback failures") {
      val thrown                              = new RuntimeException("thrown")
      val failed                              = new RuntimeException("failed")
      def result(callback: Int => Async[Int]) = {
        val interpreter = AsyncInterpreter.fromStream(Stream(1))
        interpreter.addAsyncMap(JvmType.Int, JvmType.Int)(callback)
        runAsync(interpreter.readInt(-1L).either)
      }
      for {
        a <- result(_ => throw thrown)
        b <- result(_ => Async.fail(failed))
        c <- result(_ => null)
        d <- result(_ => Async.succeed(null.asInstanceOf[Int]))
      } yield assertTrue(a.isLeft, b.isLeft, c.isLeft, d == Right(0L))
    }
  )
}
