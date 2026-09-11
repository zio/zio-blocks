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

import zio.blocks.streams.internal.AsyncInterpreter
import zio.test._

object AsyncInterpreterConversionCoverageSpec extends StreamsBaseSpec {

  private def normalized[A](value: A, target: JvmType, allowReferenceUnboxing: Boolean = true): AsyncInterpreter = {
    val interpreter = AsyncInterpreter.fromStream(Stream(value))
    interpreter.normalizeRecoveryOutput(target, allowReferenceUnboxing)
    interpreter
  }

  private def normalizedStream(stream: Stream[_, _], target: JvmType): AsyncInterpreter = {
    val interpreter = AsyncInterpreter.fromStream(stream)
    interpreter.normalizeRecoveryOutput(target)
    interpreter
  }

  private def readNormalized(interpreter: AsyncInterpreter, target: JvmType) = target match {
    case JvmType.Boolean => runAsync(interpreter.readBoolean(-1)).map(_.asInstanceOf[Any])
    case JvmType.Byte    => runAsync(interpreter.readByte()).map(_.asInstanceOf[Any])
    case JvmType.Char    => runAsync(interpreter.readChar(-1)).map(_.asInstanceOf[Any])
    case JvmType.Short   => runAsync(interpreter.readShort(-1)).map(_.asInstanceOf[Any])
    case JvmType.Int     => runAsync(interpreter.readInt(-1L)).map(_.asInstanceOf[Any])
    case JvmType.Long    => runAsync(interpreter.readLong(-1L)).map(_.asInstanceOf[Any])
    case JvmType.Float   => runAsync(interpreter.readFloat(-1.0)).map(_.asInstanceOf[Any])
    case JvmType.Double  => runAsync(interpreter.readDouble(-1.0)).map(_.asInstanceOf[Any])
    case JvmType.AnyRef  => runAsync(interpreter.read[AnyRef](null)).map(_.asInstanceOf[Any])
  }

  def spec = suite("AsyncInterpreter recovery conversion coverage")(
    test("primitive recovery values box and widen without changing their value") {
      val boolean = normalized(true, JvmType.AnyRef)
      val byte    = normalized(Byte.MinValue, JvmType.AnyRef)
      val short   = normalized(Short.MinValue, JvmType.AnyRef)
      val char    = normalized('A', JvmType.AnyRef)
      val charInt = normalized('B', JvmType.Int)
      for {
        b <- runAsync(boolean.read[AnyRef](null))
        y <- runAsync(byte.read[AnyRef](null))
        s <- runAsync(short.read[AnyRef](null))
        c <- runAsync(char.read[AnyRef](null))
        i <- runAsync(charInt.readInt(-1L))
      } yield assertTrue(
        b == java.lang.Boolean.TRUE,
        y == Byte.box(Byte.MinValue),
        s == Short.box(Short.MinValue),
        c == Char.box('A'),
        i == 'B'.toLong
      )
    },
    test("reference recovery values unbox characters numbers and booleans") {
      val charToByte   = normalized[AnyRef](Char.box('\u0101'), JvmType.Byte)
      val charToLong   = normalized[AnyRef](Char.box('C'), JvmType.Long)
      val charToFloat  = normalized[AnyRef](Char.box('D'), JvmType.Float)
      val charToDouble = normalized[AnyRef](Char.box('E'), JvmType.Double)
      val numberToInt  = normalized[AnyRef](Int.box(42), JvmType.Int)
      val falseToInt   = normalized[AnyRef](Boolean.box(false), JvmType.Int)
      val trueToInt    = normalized[AnyRef](Boolean.box(true), JvmType.Int)
      for {
        b <- runAsync(charToByte.readByte())
        l <- runAsync(charToLong.readLong(-1L))
        f <- runAsync(charToFloat.readFloat(-1.0))
        d <- runAsync(charToDouble.readDouble(-1.0))
        i <- runAsync(numberToInt.readInt(-1L))
        z <- runAsync(falseToInt.readInt(-1L))
        o <- runAsync(trueToInt.readInt(-1L))
      } yield assertTrue(b == 1, l == 67L, f == 68.0, d == 69.0, i == 42L, z == 0L, o == 1L)
    },
    test("scalar reads convert number and boolean storage to chars and unsigned bytes") {
      val numberChar = AsyncInterpreter.fromStream(Stream(65))
      val falseChar  = AsyncInterpreter.fromStream(Stream(false))
      val trueChar   = AsyncInterpreter.fromStream(Stream(true))
      val falseByte  = AsyncInterpreter.fromStream(Stream(false))
      val trueByte   = AsyncInterpreter.fromStream(Stream(true))
      for {
        n  <- runAsync(numberChar.readChar(-1))
        zc <- runAsync(falseChar.readChar(-1))
        oc <- runAsync(trueChar.readChar(-1))
        zb <- runAsync(falseByte.readByte())
        ob <- runAsync(trueByte.readByte())
      } yield assertTrue(n == 65, zc == 0, oc == 1, zb == 0, ob == 1)
    },
    test("reference recovery unboxing can be rejected before a read") {
      val interpreter = AsyncInterpreter.fromStream(Stream[AnyRef](Int.box(1)))
      val result      = scala.util.Try(interpreter.normalizeRecoveryOutput(JvmType.Int, allowReferenceUnboxing = false))
      assertTrue(result.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
    },
    test("every legal primitive widening conversion uses the target physical lane") {
      val cases = List[(AsyncInterpreter, JvmType, Any)](
        (normalizedStream(Stream(7.toByte), JvmType.Short), JvmType.Short, 7),
        (normalizedStream(Stream(7.toByte), JvmType.Int), JvmType.Int, 7L),
        (normalizedStream(Stream(7.toByte), JvmType.Long), JvmType.Long, 7L),
        (normalizedStream(Stream(7.toByte), JvmType.Float), JvmType.Float, 7.0),
        (normalizedStream(Stream(7.toByte), JvmType.Double), JvmType.Double, 7.0),
        (normalizedStream(Stream(8.toShort), JvmType.Int), JvmType.Int, 8L),
        (normalizedStream(Stream(8.toShort), JvmType.Long), JvmType.Long, 8L),
        (normalizedStream(Stream(8.toShort), JvmType.Float), JvmType.Float, 8.0),
        (normalizedStream(Stream(8.toShort), JvmType.Double), JvmType.Double, 8.0),
        (normalizedStream(Stream('A'), JvmType.Int), JvmType.Int, 65L),
        (normalizedStream(Stream('A'), JvmType.Long), JvmType.Long, 65L),
        (normalizedStream(Stream('A'), JvmType.Float), JvmType.Float, 65.0),
        (normalizedStream(Stream('A'), JvmType.Double), JvmType.Double, 65.0),
        (normalizedStream(Stream(9), JvmType.Long), JvmType.Long, 9L),
        (normalizedStream(Stream(9), JvmType.Float), JvmType.Float, 9.0),
        (normalizedStream(Stream(9), JvmType.Double), JvmType.Double, 9.0),
        (normalizedStream(Stream(10L), JvmType.Float), JvmType.Float, 10.0),
        (normalizedStream(Stream(10L), JvmType.Double), JvmType.Double, 10.0),
        (normalizedStream(Stream(11.5f), JvmType.Double), JvmType.Double, 11.5)
      )
      zio.ZIO
        .foreach(cases) { case (interpreter, target, expected) =>
          readNormalized(interpreter, target).map(_ == expected)
        }
        .map(results => assertTrue(results.forall(identity)))
    },
    test("reference values unbox into every primitive lane") {
      val cases = List[(AnyRef, JvmType, Any)](
        (Boolean.box(true), JvmType.Boolean, 1),
        (Boolean.box(true), JvmType.Byte, 1),
        (Boolean.box(false), JvmType.Char, 0),
        (Boolean.box(true), JvmType.Short, 1),
        (Int.box(12), JvmType.Int, 12L),
        (Long.box(13L), JvmType.Long, 13L),
        (Float.box(14.5f), JvmType.Float, 14.5),
        (Double.box(15.5), JvmType.Double, 15.5)
      )
      zio.ZIO
        .foreach(cases) { case (value, target, expected) =>
          readNormalized(normalized[AnyRef](value, target), target).map(_ == expected)
        }
        .map(results => assertTrue(results.forall(identity)))
    },
    test("illegal primitive narrowing and unrelated conversions fail at normalization") {
      val cases = List(
        scala.util.Try(normalizedStream(Stream(1), JvmType.Byte)),
        scala.util.Try(normalizedStream(Stream(1L), JvmType.Int)),
        scala.util.Try(normalizedStream(Stream(1.0f), JvmType.Long)),
        scala.util.Try(normalizedStream(Stream(1.0), JvmType.Float)),
        scala.util.Try(normalizedStream(Stream(true), JvmType.Int)),
        scala.util.Try(normalizedStream(Stream('A'), JvmType.Short))
      )
      assertTrue(cases.forall(_.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException])))
    }
  )
}
