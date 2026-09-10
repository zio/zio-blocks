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

import scala.collection.mutable.ListBuffer

import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Route-level proof that dynamic Stream nodes retain the physical primitive
 * pull contract. The probe deliberately has no usable generic or wrong-lane
 * operation; Long and Double additionally reject scalar sentinel reads.
 */
object RouteSpecializationProofSpec extends StreamsBaseSpec {
  private final class Probe[A](override val jvmType: JvmType, value: A) extends Reader.SyncReader[A] {
    private var available = true
    val calls             = ListBuffer.empty[String]

    def close(): Unit                = ()
    def isClosed: Boolean            = !available
    override def readable(): Boolean = available
    override def reset(): Unit       = available = true
    def read[B >: A](eof: B): B      = fail("read")

    private def fail[B](method: String): B =
      throw new AssertionError(s"$jvmType probe received $method")
    private def one[B](lane: JvmType, method: String, eof: B)(f: A => B): B =
      if (jvmType != lane) fail(method)
      else if (!available) eof
      else {
        available = false
        calls += method
        f(value)
      }

    override def readBoolean(eof: Int)(implicit ev: A <:< Boolean): Int =
      one(JvmType.Boolean, "readBoolean", eof)(a => if (ev(a)) 1 else 0)
    override def readByte(): Int                                  = one(JvmType.Byte, "readByte", -1)(_.asInstanceOf[Byte].toInt & 0xff)
    override def readChar(eof: Int)(implicit ev: A <:< Char): Int =
      one(JvmType.Char, "readChar", eof)(a => ev(a).toInt)
    override def readShort(eof: Int)(implicit ev: A <:< Short): Int =
      one(JvmType.Short, "readShort", eof)(a => ev(a).toInt)
    override def readInt(eof: Long)(implicit ev: A <:< Int): Long =
      one(JvmType.Int, "readInt", eof)(a => ev(a).toLong)
    override def readLong(eof: Long)(implicit ev: A <:< Long): Long                                   = fail("readLong(sentinel)")
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
      if (jvmType != JvmType.Long) fail("readLongs")
      else if (length == 0) 0
      else if (!available) -1
      else { available = false; calls += "readLongs"; dest(offset) = ev(value); 1 }
    override def readFloat(eof: Double)(implicit ev: A <:< Float): Double =
      one(JvmType.Float, "readFloat", eof)(a => ev(a).toDouble)
    override def readDouble(eof: Double)(implicit ev: A <:< Double): Double                                 = fail("readDouble(sentinel)")
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
      if (jvmType != JvmType.Double) fail("readDoubles")
      else if (length == 0) 0
      else if (!available) -1
      else { available = false; calls += "readDoubles"; dest(offset) = ev(value); 1 }
  }

  private def expectedMethod(lane: JvmType): String = lane match {
    case JvmType.Boolean => "readBoolean"
    case JvmType.Byte    => "readByte"
    case JvmType.Char    => "readChar"
    case JvmType.Short   => "readShort"
    case JvmType.Int     => "readInt"
    case JvmType.Long    => "readLongs"
    case JvmType.Float   => "readFloat"
    case JvmType.Double  => "readDoubles"
    case other           => throw new AssertionError(s"not a primitive lane: $other")
  }

  private def pullValue[A](reader: Reader[A], lane: JvmType): ZIO[Any, Throwable, A] = {
    if (reader.jvmType != lane) return ZIO.fail(new AssertionError(s"expected $lane, got ${reader.jvmType}"))
    reader match {
      case r: Reader.SyncReader[A @unchecked] =>
        ZIO.attempt {
          (lane match {
            case JvmType.Boolean => r.readBooleanPhysical(-1) != 0
            case JvmType.Byte    => r.readBytePhysical().toByte
            case JvmType.Char    => r.readCharPhysical(-1).toChar
            case JvmType.Short   => r.readShortPhysical(-1).toShort
            case JvmType.Int     => r.readIntPhysical(-1L).toInt
            case JvmType.Long    => val values = new Array[Long](1); r.readLongsPhysical(values, 0, 1); values(0)
            case JvmType.Float   => r.readFloatPhysical(Double.NaN).toFloat
            case JvmType.Double  => val values = new Array[Double](1); r.readDoublesPhysical(values, 0, 1); values(0)
            case other           => throw new AssertionError(s"not a primitive lane: $other")
          }).asInstanceOf[A]
        }
      case r: Reader.AsyncReader[A @unchecked] =>
        lane match {
          case JvmType.Boolean => runAsync(r.readBooleanPhysical(-1)).map(value => (value != 0).asInstanceOf[A])
          case JvmType.Byte    => runAsync(r.readBytePhysical()).map(value => value.toByte.asInstanceOf[A])
          case JvmType.Char    => runAsync(r.readCharPhysical(-1)).map(value => value.toChar.asInstanceOf[A])
          case JvmType.Short   => runAsync(r.readShortPhysical(-1)).map(value => value.toShort.asInstanceOf[A])
          case JvmType.Int     => runAsync(r.readIntPhysical(-1L)).map(value => value.toInt.asInstanceOf[A])
          case JvmType.Long    =>
            val values = new Array[Long](1)
            runAsync(r.readLongsPhysical(values, 0, 1)).map(_ => values(0).asInstanceOf[A])
          case JvmType.Float  => runAsync(r.readFloatPhysical(Double.NaN)).map(value => value.toFloat.asInstanceOf[A])
          case JvmType.Double =>
            val values = new Array[Double](1)
            runAsync(r.readDoublesPhysical(values, 0, 1)).map(_ => values(0).asInstanceOf[A])
          case other => ZIO.fail(new AssertionError(s"not a primitive lane: $other"))
        }
    }
  }

  private def pull[A](reader: Reader[A], lane: JvmType): ZIO[Any, Throwable, Unit] = pullValue(reader, lane).unit

  private def sameValue(lane: JvmType, actual: Any, expected: Any): Boolean = lane match {
    case JvmType.Float =>
      java.lang.Float.floatToRawIntBits(actual.asInstanceOf[Float]) ==
        java.lang.Float.floatToRawIntBits(expected.asInstanceOf[Float])
    case JvmType.Double =>
      java.lang.Double.doubleToRawLongBits(actual.asInstanceOf[Double]) ==
        java.lang.Double.doubleToRawLongBits(expected.asInstanceOf[Double])
    case _ => actual == expected
  }

  private def route[A: JvmType.Infer](lane: JvmType, value: A): ZIO[Any, Throwable, TestResult] = {
    def check(
      name: String,
      build: Stream[Nothing, A] => Stream[_, A],
      pulls: Int = 1
    ): ZIO[Any, Throwable, TestResult] = {
      val probe  = new Probe[A](lane, value)
      val source = Stream.fromReaderAsync[Nothing, A](Async.succeed(probe))
      val stream = build(source)
      val output = AsyncInterpreter.fromStream(stream).toReader[A]
      ZIO
        .foreach(List.fill(pulls)(()))(_ => pullValue(output, lane))
        .map(values =>
          assertTrue(
            stream.elementRepresentation == ElementRepresentation.Known(lane),
            output.jvmType == lane,
            values.forall(sameValue(lane, _, value)),
            probe.calls.toList == List(expectedMethod(lane))
          )
        )
        .mapError { e =>
          val detail = e match {
            case error: zio.blocks.streams.internal.StreamError => s": typed value ${error.value}"
            case _                                              => ""
          }
          new AssertionError(s"$name/$lane failed$detail", e)
        }
    }

    for {
      buffer    <- check("buffer", _.buffer(2))
      mapPar    <- check("mapPar", _.mapPar(2)(identity))
      flatten   <- check("flatten", s => Stream.flattenAll(Stream.succeed(s)))
      merge     <- check("merge", s => Stream.mergeAll(1)(Stream.succeed(s)))
      recovery  <- check("recovery", s => Stream.fail(new RuntimeException("switch")).catchAll(_ => s))
      repeated  <- check("repeated", _.repeated.take(1))
      scan      <- check("scan", _.scan(value)((_, current) => current).buffer(1), pulls = 2)
      scanAsync <- check(
                     "scanAsync",
                     _.scanAsync(value)((_, current) => Async.succeed(current)).buffer(1),
                     pulls = 2
                   )
      takeWhile <- check("takeWhile", _.takeWhile(_ => true).buffer(1))
    } yield buffer && mapPar && flatten && merge && recovery && repeated && scan && scanAsync && takeWhile
  }

  private def scanAsyncOutput[A: JvmType.Infer](lane: JvmType, init: A, next: A): ZIO[Any, Throwable, TestResult] = {
    val probe  = new Probe[Int](JvmType.Int, 1)
    val source = Stream.fromReaderAsync[Nothing, Int](Async.succeed(probe))
    val scan   = source.scanAsync(init)((_, _) => Async.succeed(next))
    val stream = scan.buffer(1)
    val output = AsyncInterpreter.fromStream(stream).toReader[A]
    (pullValue(output, lane) <*> pullValue(output, lane)).map { case (initial, following) =>
      assertTrue(
        scan.elementRepresentation == ElementRepresentation.Known(lane),
        stream.elementRepresentation == ElementRepresentation.Known(lane),
        output.jvmType == lane,
        sameValue(lane, initial, init),
        sameValue(lane, following, next),
        probe.calls.toList == List("readInt")
      )
    }
  }

  private def asyncOutput[A: JvmType.Infer](
    lane: JvmType,
    value: A,
    build: Stream[Nothing, Int] => Stream[Nothing, A]
  ): ZIO[Any, Throwable, TestResult] = {
    val probe  = new Probe[Int](JvmType.Int, 1)
    val stream = build(Stream.fromReaderAsync[Nothing, Int](Async.succeed(probe))).buffer(1)
    val output = AsyncInterpreter.fromStream(stream).toReader[A]
    pullValue(output, lane).map(actual =>
      assertTrue(
        stream.elementRepresentation == ElementRepresentation.Known(lane),
        output.jvmType == lane,
        sameValue(lane, actual, value),
        probe.calls.toList == List("readInt")
      )
    )
  }

  private def asyncOutputPair[A: JvmType.Infer](lane: JvmType, value: A): ZIO[Any, Throwable, TestResult] =
    for {
      mapped    <- asyncOutput(lane, value, _.mapAsync(_ => Async.succeed(value)))
      collected <- asyncOutput(lane, value, _.collectAsync(_ => Async.succeed(Some(value))))
    } yield mapped && collected

  private def laneMatrix: ZIO[Any, Throwable, TestResult] =
    for {
      boolean <- route(JvmType.Boolean, true)
      byte    <- route(JvmType.Byte, Byte.MinValue)
      char    <- route(JvmType.Char, '\uffff')
      short   <- route(JvmType.Short, Short.MinValue)
      int     <- route(JvmType.Int, Int.MinValue)
      long    <- route(JvmType.Long, Long.MinValue)
      float   <- route(JvmType.Float, java.lang.Float.intBitsToFloat(0x7fc00001))
      double  <- route(JvmType.Double, java.lang.Double.longBitsToDouble(0x7ff8000000000001L))
    } yield boolean && byte && char && short && int && long && float && double

  private def symbolicAliases[A: JvmType.Infer](lane: JvmType, value: A): ZIO[Any, Throwable, TestResult] = {
    def source(probe: Probe[A]): Stream[Nothing, A] =
      Stream.fromReaderAsync[Nothing, A](Async.succeed(probe))

    val zipLeft     = new Probe[A](lane, value)
    val zipRight    = new Probe[A](lane, value)
    val concatLeft  = new Probe[A](lane, value)
    val concatRight = new Probe[A](lane, value)
    val fallback    = new Probe[A](lane, value)
    for {
      zipped       <- runAsync(source(zipLeft).&&(source(zipRight)).runCollectAsync)
      concatenated <- runAsync((source(concatLeft) ++ source(concatRight)).runCollectAsync)
      recovered    <- runAsync((Stream.fail("switch") || source(fallback)).runCollectAsync)
    } yield {
      val zippedValues       = zipped.toOption.get
      val concatenatedValues = concatenated.toOption.get
      val recoveredValues    = recovered.toOption.get
      assertTrue(
        zippedValues.length == 1,
        sameValue(lane, zippedValues(0)._1, value),
        sameValue(lane, zippedValues(0)._2, value),
        concatenatedValues.length == 2,
        sameValue(lane, concatenatedValues(0), value),
        sameValue(lane, concatenatedValues(1), value),
        recoveredValues.length == 1,
        sameValue(lane, recoveredValues(0), value),
        zipLeft.calls.toList == List(expectedMethod(lane)),
        zipRight.calls.toList == List(expectedMethod(lane)),
        concatLeft.calls.toList == List(expectedMethod(lane)),
        concatRight.calls.toList == List(expectedMethod(lane)),
        fallback.calls.toList == List(expectedMethod(lane))
      )
    }
  }

  private def deferred[A: JvmType.Infer](lane: JvmType, value: A): ZIO[Any, Throwable, TestResult] = {
    var started = 0
    val probe   = new Probe[A](lane, value)
    val source  = Stream.fromReaderAsync[Nothing, A] { started += 1; Async.succeed(probe) }
    val reader  = AsyncInterpreter.fromStream(source).toReader[A]
    val before  = started
    for {
      _ <- pull(reader, lane)
    } yield assertTrue(
      source.elementRepresentation == ElementRepresentation.Known(lane),
      reader.jvmType == lane,
      before == 0,
      started == 1,
      probe.calls.toList == List(expectedMethod(lane))
    )
  }

  def spec = suite("route specialization proof")(
    test("dynamic preserving and branch-switching routes use every exact lane")(laneMatrix),
    test("fromReaderAsync exposes a stable lane without eagerly starting its effect") {
      for {
        b <- deferred(JvmType.Boolean, true)
        y <- deferred(JvmType.Byte, Byte.MinValue)
        c <- deferred(JvmType.Char, '\uffff')
        s <- deferred(JvmType.Short, Short.MinValue)
        i <- deferred(JvmType.Int, Int.MinValue)
        l <- deferred(JvmType.Long, Long.MinValue)
        f <- deferred(JvmType.Float, Float.NaN)
        d <- deferred(JvmType.Double, Double.NaN)
      } yield b && y && c && s && i && l && f && d
    },
    test("attemptAsync is lazy and exposes every stable primitive lane") {
      def one[A: JvmType.Infer](lane: JvmType, value: A) = {
        var started = 0
        val stream  = Stream.attemptAsync { started += 1; Async.succeed(value) }
        val reader  = AsyncInterpreter.fromStream(stream).toReader[A]
        val before  = started
        pull(reader, lane).as(
          assertTrue(
            stream.elementRepresentation == ElementRepresentation.Known(lane),
            reader.jvmType == lane,
            before == 0,
            started == 1
          )
        )
      }
      for {
        b <- one(JvmType.Boolean, true); y     <- one(JvmType.Byte, Byte.MinValue)
        c <- one(JvmType.Char, '\uffff'); s    <- one(JvmType.Short, Short.MinValue)
        i <- one(JvmType.Int, Int.MinValue); l <- one(JvmType.Long, Long.MinValue)
        f <- one(JvmType.Float, Float.NaN); d  <- one(JvmType.Double, Double.NaN)
      } yield b && y && c && s && i && l && f && d
    },
    test("scanAsync advertises and preserves an output lane different from its input across a boundary") {
      for {
        b <- scanAsyncOutput(JvmType.Boolean, false, true)
        y <- scanAsyncOutput(JvmType.Byte, 0.toByte, Byte.MinValue)
        c <- scanAsyncOutput(JvmType.Char, 0.toChar, '\uffff')
        s <- scanAsyncOutput(JvmType.Short, 0.toShort, Short.MinValue)
        i <- scanAsyncOutput(JvmType.Int, 0, Int.MinValue)
        l <- scanAsyncOutput(JvmType.Long, 0L, Long.MinValue)
        f <- scanAsyncOutput(JvmType.Float, 0.0f, java.lang.Float.intBitsToFloat(0x7fc00001))
        d <- scanAsyncOutput(JvmType.Double, 0.0d, java.lang.Double.longBitsToDouble(0x7ff8000000000001L))
      } yield b && y && c && s && i && l && f && d
    },
    test("mapAsync and collectAsync preserve all primitive output values across a boundary") {
      for {
        b <- asyncOutputPair(JvmType.Boolean, true)
        y <- asyncOutputPair(JvmType.Byte, Byte.MinValue)
        c <- asyncOutputPair(JvmType.Char, '\uffff')
        s <- asyncOutputPair(JvmType.Short, Short.MinValue)
        i <- asyncOutputPair(JvmType.Int, Int.MinValue)
        l <- asyncOutputPair(JvmType.Long, Long.MinValue)
        f <- asyncOutputPair(JvmType.Float, java.lang.Float.intBitsToFloat(0x7fc00001))
        d <- asyncOutputPair(JvmType.Double, java.lang.Double.longBitsToDouble(0x7ff8000000000001L))
      } yield b && y && c && s && i && l && f && d
    },
    test("symbolic Stream aliases drive both operands through every exact primitive lane") {
      for {
        b <- symbolicAliases(JvmType.Boolean, true)
        y <- symbolicAliases(JvmType.Byte, Byte.MinValue)
        c <- symbolicAliases(JvmType.Char, '\uffff')
        s <- symbolicAliases(JvmType.Short, Short.MinValue)
        i <- symbolicAliases(JvmType.Int, Int.MinValue)
        l <- symbolicAliases(JvmType.Long, Long.MinValue)
        f <- symbolicAliases(JvmType.Float, java.lang.Float.intBitsToFloat(0x7fc00001))
        d <- symbolicAliases(JvmType.Double, java.lang.Double.longBitsToDouble(0x7ff8000000000001L))
      } yield b && y && c && s && i && l && f && d
    }
  )
}
