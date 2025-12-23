package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for numeric narrowing conversions (may fail at runtime).
 *
 * Covers:
 *   - Long → Int → Short → Byte
 *   - Double → Float
 *   - Overflow detection
 *   - Underflow detection
 */
object NumericNarrowingSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NumericNarrowingSpec")(
    suite("Long to Int Narrowing")(
      test("narrows Long to Int when value fits") {
        val result = Into[Long, Int].into(42L)
        assert(result)(isRight(equalTo(42)))
      },
      test("narrows Long.MaxValue that fits in Int") {
        val result = Into[Long, Int].into(Int.MaxValue.toLong)
        assert(result)(isRight(equalTo(Int.MaxValue)))
      },
      test("narrows Long.MinValue that fits in Int") {
        val result = Into[Long, Int].into(Int.MinValue.toLong)
        assert(result)(isRight(equalTo(Int.MinValue)))
      },
      test("fails when Long overflows Int (positive)") {
        val result = Into[Long, Int].into(Int.MaxValue.toLong + 1)
        assert(result)(isLeft)
      },
      test("fails when Long overflows Int (negative)") {
        val result = Into[Long, Int].into(Int.MinValue.toLong - 1)
        assert(result)(isLeft)
      },
      test("fails when Long is far above Int.MaxValue") {
        val result = Into[Long, Int].into(Long.MaxValue)
        assert(result)(isLeft)
      },
      test("fails when Long is far below Int.MinValue") {
        val result = Into[Long, Int].into(Long.MinValue)
        assert(result)(isLeft)
      }
    ),
    suite("Int to Short Narrowing")(
      test("narrows Int to Short when value fits") {
        val result = Into[Int, Short].into(1000)
        assert(result)(isRight(equalTo(1000.toShort)))
      },
      test("narrows Int at Short.MaxValue") {
        val result = Into[Int, Short].into(Short.MaxValue.toInt)
        assert(result)(isRight(equalTo(Short.MaxValue)))
      },
      test("narrows Int at Short.MinValue") {
        val result = Into[Int, Short].into(Short.MinValue.toInt)
        assert(result)(isRight(equalTo(Short.MinValue)))
      },
      test("fails when Int overflows Short (positive)") {
        val result = Into[Int, Short].into(Short.MaxValue.toInt + 1)
        assert(result)(isLeft)
      },
      test("fails when Int overflows Short (negative)") {
        val result = Into[Int, Short].into(Short.MinValue.toInt - 1)
        assert(result)(isLeft)
      }
    ),
    suite("Short to Byte Narrowing")(
      test("narrows Short to Byte when value fits") {
        val result = Into[Short, Byte].into(100.toShort)
        assert(result)(isRight(equalTo(100.toByte)))
      },
      test("narrows Short at Byte.MaxValue") {
        val result = Into[Short, Byte].into(Byte.MaxValue.toShort)
        assert(result)(isRight(equalTo(Byte.MaxValue)))
      },
      test("narrows Short at Byte.MinValue") {
        val result = Into[Short, Byte].into(Byte.MinValue.toShort)
        assert(result)(isRight(equalTo(Byte.MinValue)))
      },
      test("fails when Short overflows Byte (positive)") {
        val result = Into[Short, Byte].into((Byte.MaxValue.toShort + 1).toShort)
        assert(result)(isLeft)
      },
      test("fails when Short overflows Byte (negative)") {
        val result = Into[Short, Byte].into((Byte.MinValue.toShort - 1).toShort)
        assert(result)(isLeft)
      }
    ),
    suite("Int to Byte Narrowing")(
      test("narrows Int to Byte when value fits") {
        val result = Into[Int, Byte].into(42)
        assert(result)(isRight(equalTo(42.toByte)))
      },
      test("narrows Int at Byte.MaxValue") {
        val result = Into[Int, Byte].into(Byte.MaxValue.toInt)
        assert(result)(isRight(equalTo(Byte.MaxValue)))
      },
      test("narrows Int at Byte.MinValue") {
        val result = Into[Int, Byte].into(Byte.MinValue.toInt)
        assert(result)(isRight(equalTo(Byte.MinValue)))
      },
      test("fails when Int overflows Byte") {
        val result = Into[Int, Byte].into(200)
        assert(result)(isLeft)
      },
      test("fails when Int underflows Byte") {
        val result = Into[Int, Byte].into(-200)
        assert(result)(isLeft)
      }
    ),
    suite("Long to Short Narrowing")(
      test("narrows Long to Short when value fits") {
        val result = Into[Long, Short].into(1000L)
        assert(result)(isRight(equalTo(1000.toShort)))
      },
      test("fails when Long overflows Short") {
        val result = Into[Long, Short].into(100000L)
        assert(result)(isLeft)
      }
    ),
    suite("Long to Byte Narrowing")(
      test("narrows Long to Byte when value fits") {
        val result = Into[Long, Byte].into(100L)
        assert(result)(isRight(equalTo(100.toByte)))
      },
      test("fails when Long overflows Byte") {
        val result = Into[Long, Byte].into(1000L)
        assert(result)(isLeft)
      }
    ),
    suite("Double to Float Narrowing")(
      test("narrows Double to Float when value fits") {
        val result = Into[Double, Float].into(3.14)
        assert(result)(isRight(equalTo(3.14f)))
      },
      test("narrows small Double to Float") {
        val result = Into[Double, Float].into(0.001)
        assert(result)(isRight(equalTo(0.001f)))
      },
      test("fails for Double.NaN (out of range check fails)") {
        // The predefined doubleToFloat conversion uses range checks which fail for NaN
        val result = Into[Double, Float].into(Double.NaN)
        assert(result)(isLeft)
      },
      test("fails for Double.PositiveInfinity (out of range check fails)") {
        // The predefined doubleToFloat conversion uses range checks which fail for Infinity
        val result = Into[Double, Float].into(Double.PositiveInfinity)
        assert(result)(isLeft)
      },
      test("fails for Double.NegativeInfinity (out of range check fails)") {
        // The predefined doubleToFloat conversion uses range checks which fail for -Infinity
        val result = Into[Double, Float].into(Double.NegativeInfinity)
        assert(result)(isLeft)
      }
    ),
    suite("Narrowing in Products")(
      test("narrows Long field to Int in case class when value fits") {
        case class Source(value: Long)
        case class Target(value: Int)

        val source = Source(42L)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(42))))
      },
      test("fails when Long field overflows Int in case class") {
        case class Source(value: Long)
        case class Target(value: Int)

        val source = Source(Long.MaxValue)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isLeft)
      },
      test("narrows multiple fields successfully when all fit") {
        case class Source(a: Long, b: Int, c: Short)
        case class Target(a: Int, b: Short, c: Byte)

        val source = Source(100L, 50, 25.toShort)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(100, 50.toShort, 25.toByte))))
      },
      test("fails when any field overflows during narrowing") {
        case class Source(a: Long, b: Int, c: Short)
        case class Target(a: Int, b: Short, c: Byte)

        val source = Source(100L, 50000, 25.toShort) // b overflows Short
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Edge Cases")(
      test("zero narrows correctly") {
        assert(Into[Long, Int].into(0L))(isRight(equalTo(0))) &&
        assert(Into[Int, Short].into(0))(isRight(equalTo(0.toShort))) &&
        assert(Into[Short, Byte].into(0.toShort))(isRight(equalTo(0.toByte)))
      },
      test("negative zero in Double narrows to Float") {
        val result = Into[Double, Float].into(-0.0)
        assert(result)(isRight(equalTo(-0.0f)))
      }
    )
  )
}
