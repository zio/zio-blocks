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
import zio.blocks.combinators.Tuples
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncZipLaneCoverageSpec extends StreamsBaseSpec {
  private val asyncInt = Int.MinValue

  private final case class Lane(make: () => Stream[Nothing, Any], value: Any)

  private def asyncInts: Stream[Nothing, Int] =
    Stream.fromReader[Nothing, Int](Reader.singleInt(asyncInt).toAsync)

  private def stream[A](reader: Reader.SyncReader[A]): Stream[Nothing, A] =
    Stream.fromReader[Nothing, A](reader)

  private def exact(actual: Any, expected: Any): Boolean = expected match {
    case value: Float =>
      actual.isInstanceOf[Float] &&
      java.lang.Float.floatToRawIntBits(actual.asInstanceOf[Float]) == java.lang.Float.floatToRawIntBits(value)
    case value: Double =>
      actual.isInstanceOf[Double] &&
      java.lang.Double.doubleToRawLongBits(actual.asInstanceOf[Double]) == java.lang.Double.doubleToRawLongBits(value)
    case _ => actual == expected
  }

  private def checkTuple[A](
    reader: Reader.SyncReader[A],
    value: A,
    laneOnLeft: Boolean
  ): ZIO[Any, Throwable, TestResult] = {
    val zipped =
      if (laneOnLeft) stream(reader) && asyncInts
      else asyncInts && stream(reader)
    val output = Stream.compileToReader(zipped).expectedAsync
    val eof    = new Object
    for {
      tuple <- runAsync(output.read[Any](eof))
      end   <- runAsync(output.read[Any](eof))
    } yield {
      val tupleIsExact = tuple match {
        case product: Product if product.productArity == 2 =>
          if (laneOnLeft) exact(product.productElement(0), value) && exact(product.productElement(1), asyncInt)
          else exact(product.productElement(0), asyncInt) && exact(product.productElement(1), value)
        case _ => false
      }
      assertTrue(tupleIsExact, end.asInstanceOf[AnyRef] eq eof)
    }
  }

  private def laneChecks(laneOnLeft: Boolean): ZIO[Any, Throwable, TestResult] =
    for {
      boolean <- checkTuple(Reader.singleBoolean(false), false, laneOnLeft)
      byte    <- checkTuple(Reader.singleByte(Byte.MinValue), Byte.MinValue, laneOnLeft)
      char    <- checkTuple(Reader.singleChar(Char.MinValue), Char.MinValue, laneOnLeft)
      short   <- checkTuple(Reader.singleShort(Short.MinValue), Short.MinValue, laneOnLeft)
      int     <- checkTuple(Reader.singleInt(Int.MinValue), Int.MinValue, laneOnLeft)
      long    <- checkTuple(Reader.singleLong(Long.MinValue), Long.MinValue, laneOnLeft)
      float   <- checkTuple(Reader.singleFloat(Float.NaN), Float.NaN, laneOnLeft)
      double  <- checkTuple(Reader.singleDouble(-0.0d), -0.0d, laneOnLeft)
      ref     <- checkTuple(Reader.single[String](null), null, laneOnLeft)
    } yield boolean && byte && char && short && int && long && float && double && ref

  private def primitiveOutput[A: JvmType.Infer](value: A): Reader.AsyncReader[A] = {
    val left   = Stream.fromReader[Nothing, Unit](Reader.single(()).toAsync)
    val right  = Stream.fromReader[Nothing, A](Reader.single(value).toAsync)
    val zipped = left && right
    Stream.compileToReader(zipped).expectedAsync
  }

  private def lanes: List[Lane] = List(
    Lane(() => Stream.fromReader(Reader.singleBoolean(false).toAsync).asInstanceOf[Stream[Nothing, Any]], false),
    Lane(
      () => Stream.fromReader(Reader.singleByte(Byte.MinValue).toAsync).asInstanceOf[Stream[Nothing, Any]],
      Byte.MinValue
    ),
    Lane(
      () => Stream.fromReader(Reader.singleChar(Char.MinValue).toAsync).asInstanceOf[Stream[Nothing, Any]],
      Char.MinValue
    ),
    Lane(
      () => Stream.fromReader(Reader.singleShort(Short.MinValue).toAsync).asInstanceOf[Stream[Nothing, Any]],
      Short.MinValue
    ),
    Lane(
      () => Stream.fromReader(Reader.singleInt(Int.MinValue).toAsync).asInstanceOf[Stream[Nothing, Any]],
      Int.MinValue
    ),
    Lane(
      () => Stream.fromReader(Reader.singleLong(Long.MinValue).toAsync).asInstanceOf[Stream[Nothing, Any]],
      Long.MinValue
    ),
    Lane(() => Stream.fromReader(Reader.singleFloat(Float.NaN).toAsync).asInstanceOf[Stream[Nothing, Any]], Float.NaN),
    Lane(() => Stream.fromReader(Reader.singleDouble(-0.0d).toAsync).asInstanceOf[Stream[Nothing, Any]], -0.0d),
    Lane(() => Stream.fromReader(Reader.single[String](null).toAsync).asInstanceOf[Stream[Nothing, Any]], null)
  )

  def spec = suite("async zip physical lanes")(
    test("all nine left lanes zip exactly with async Int and reach EOF") {
      laneChecks(laneOnLeft = true)
    },
    test("all nine right lanes zip exactly with async Int and reach EOF") {
      laneChecks(laneOnLeft = false)
    },
    test("all 81 asynchronous physical-lane pairs produce exact tuples") {
      ZIO
        .foreach(lanes) { left =>
          ZIO
            .foreach(lanes) { right =>
              val output = Stream.compileToReader(left.make() && right.make()).expectedAsync
              val eof    = new Object
              for {
                tuple <- runAsync(output.read[Any](eof))
                end   <- runAsync(output.read[Any](eof))
              } yield tuple match {
                case pair: Product =>
                  assertTrue(
                    pair.productArity == 2,
                    exact(pair.productElement(0), left.value),
                    exact(pair.productElement(1), right.value),
                    end.asInstanceOf[AnyRef] eq eof
                  )
                case _ => assertTrue(false)
              }
            }
            .map(_.foldLeft(assertTrue(true))(_ && _))
        }
        .map(_.foldLeft(assertTrue(true))(_ && _))
    },
    test("asymmetric async zip stops when the shorter right side reaches EOF") {
      val left   = Stream.fromReader[Nothing, Int](Reader.fromChunk(zio.blocks.chunk.Chunk(1, 2)).toAsync)
      val right  = Stream.fromReader[Nothing, Int](Reader.singleInt(3).toAsync)
      val output = Stream.compileToReader(left && right).expectedAsync
      for {
        first <- runAsync(output.read[(Int, Int)](null))
        end   <- runAsync(output.read[(Int, Int)](null))
      } yield assertTrue(first == ((1, 3)), end == null)
    },
    test("generic reads box every primitive zip output without losing sentinel-domain values") {
      val outputs = List[(Reader.AsyncReader[Any], Any)](
        (primitiveOutput(false).asInstanceOf[Reader.AsyncReader[Any]], false),
        (primitiveOutput(Byte.MinValue).asInstanceOf[Reader.AsyncReader[Any]], Byte.MinValue),
        (primitiveOutput(Char.MinValue).asInstanceOf[Reader.AsyncReader[Any]], Char.MinValue),
        (primitiveOutput(Short.MinValue).asInstanceOf[Reader.AsyncReader[Any]], Short.MinValue),
        (primitiveOutput(Int.MinValue).asInstanceOf[Reader.AsyncReader[Any]], Int.MinValue),
        (primitiveOutput(Long.MinValue).asInstanceOf[Reader.AsyncReader[Any]], Long.MinValue),
        (primitiveOutput(Float.NaN).asInstanceOf[Reader.AsyncReader[Any]], Float.NaN),
        (primitiveOutput(-0.0d).asInstanceOf[Reader.AsyncReader[Any]], -0.0d)
      )
      ZIO
        .foreach(outputs) { case (output, expected) =>
          val eof = new Object
          for {
            value <- runAsync(output.read[Any](eof))
            end   <- runAsync(output.read[Any](eof))
          } yield assertTrue(exact(value, expected), end.asInstanceOf[AnyRef] eq eof)
        }
        .map(_.foldLeft(assertTrue(true))(_ && _))
    },
    test("primitive zip outputs support every specialized scalar API") {
      val boolean              = primitiveOutput(false)
      val byte                 = primitiveOutput(Byte.MinValue)
      val char                 = primitiveOutput(Char.MinValue)
      val short                = primitiveOutput(Short.MinValue)
      val int                  = primitiveOutput(Int.MinValue)
      val long                 = primitiveOutput(Long.MinValue)
      val float                = primitiveOutput(Float.NaN)
      val double               = primitiveOutput(-0.0d)
      val nan: Double          = Double.NaN
      val negativeZero: Double = -0.0d
      for {
        booleanValue <- runAsync(boolean.readBoolean(-7))
        booleanEof   <- runAsync(boolean.readBoolean(-7))
        byteValue    <- runAsync(byte.readByte())
        byteEof      <- runAsync(byte.readByte())
        charValue    <- runAsync(char.readChar(-7))
        charEof      <- runAsync(char.readChar(-7))
        shortValue   <- runAsync(short.readShort(-7))
        shortEof     <- runAsync(short.readShort(-7))
        intValue     <- runAsync(int.readInt(Long.MinValue))
        intEof       <- runAsync(int.readInt(7L))
        longValue    <- runAsync(long.readLong(7L))
        longEof      <- runAsync(long.readLong(7L))
        floatValue   <- runAsync(float.readFloat(negativeZero))
        floatEof     <- runAsync(float.readFloat(negativeZero))
        doubleValue  <- runAsync(double.readDouble(nan))
        doubleEof    <- runAsync(double.readDouble(nan))
      } yield {
        val floatValueBits  = java.lang.Double.doubleToRawLongBits(floatValue)
        val floatEofBits    = java.lang.Double.doubleToRawLongBits(floatEof)
        val doubleValueBits = java.lang.Double.doubleToRawLongBits(doubleValue)
        assertTrue(
          booleanValue == 0,
          booleanEof == -7,
          byteValue == 128,
          byteEof == -1,
          charValue == Char.MinValue.toInt,
          charEof == -7,
          shortValue == Short.MinValue.toInt,
          shortEof == -7,
          intValue == Int.MinValue.toLong,
          intEof == 7L,
          longValue == Long.MinValue,
          longEof == 7L,
          floatValueBits == java.lang.Double.doubleToRawLongBits(Float.NaN.toDouble),
          floatEofBits == java.lang.Double.doubleToRawLongBits(-0.0d),
          doubleValueBits == java.lang.Double.doubleToRawLongBits(-0.0d),
          doubleEof.isNaN
        )
      }
    },
    test("Long and Double zip outputs honor zero-length, value, and EOF bulk reads") {
      val longs      = primitiveOutput(Long.MinValue)
      val doubles    = primitiveOutput(Double.NaN)
      val longDest   = Array(11L, 12L, 13L)
      val doubleDest = Array(11.0d, -0.0d, 13.0d)
      for {
        longZero    <- runAsync(longs.readLongs(longDest, 1, 0))
        longValue   <- runAsync(longs.readLongs(longDest, 1, 1))
        longEof     <- runAsync(longs.readLongs(longDest, 2, 1))
        doubleZero  <- runAsync(doubles.readDoubles(doubleDest, 1, 0))
        doubleValue <- runAsync(doubles.readDoubles(doubleDest, 1, 1))
        doubleEof   <- runAsync(doubles.readDoubles(doubleDest, 2, 1))
      } yield assertTrue(
        longZero == 0,
        longValue == 1,
        longEof == -1,
        longDest.toVector == Vector(11L, Long.MinValue, 13L),
        doubleZero == 0,
        doubleValue == 1,
        doubleEof == -1,
        doubleDest(0) == 11.0d,
        doubleDest(1).isNaN,
        doubleDest(2) == 13.0d
      )
    },
    test("a throwing custom tuple combiner fails the zip and closes both lanes") {
      val failure     = new RuntimeException("tuple-combine")
      var leftCloses  = 0
      var rightCloses = 0
      val tuples      = new Tuples.Tuples[Int, Int] {
        type Out = (Int, Int)
        def combine(left: Int, right: Int): (Int, Int) = throw failure
        def separate(out: (Int, Int)): (Int, Int)      = out
      }
      val zip    = Stream.Zip.fromTuples[Int, Int, (Int, Int)](tuples)
      val left   = Reader.singleInt(1).toAsync.withReleaseAsync { () => leftCloses += 1; Async.succeed(()) }
      val right  = Reader.singleInt(2).toAsync.withReleaseAsync { () => rightCloses += 1; Async.succeed(()) }
      val zipped = Stream
        .fromReader[Nothing, Int](left)
        .&&(Stream.fromReader[Nothing, Int](right))(
          implicitly[zio.blocks.combinators.Concat.WithOut[Nothing, Nothing, Nothing]],
          zip,
          implicitly[JvmType.Infer[(Int, Int)]]
        )
      runAsync(zipped.runCollectAsync.either).map(result =>
        assertTrue(result == Left(failure), leftCloses == 1, rightCloses == 1)
      )
    }
  )
}
