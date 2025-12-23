package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for numeric narrowing validation in Into derivation.
 *
 * Narrowing conversions (Long → Int, Int → Short, etc.) can fail at runtime if
 * the source value is outside the target type's range.
 */
object NarrowingValidationSpec extends ZIOSpecDefault {

  // === Test Case Classes ===
  case class LongValues(a: Long, b: Long)
  case class IntValues(a: Int, b: Int)

  case class IntValue(value: Int)
  case class ShortValue(value: Short)
  case class ByteValue(value: Byte)

  case class DoubleValue(value: Double)
  case class FloatValue(value: Float)

  case class MixedLong(name: String, count: Long, score: Long)
  case class MixedInt(name: String, count: Int, score: Int)

  def spec: Spec[TestEnvironment, Any] = suite("NarrowingValidationSpec")(
    suite("Long to Int Narrowing")(
      test("succeeds when Long value fits in Int range") {
        val source = LongValues(100L, 200L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(100, 200))))
      },
      test("fails when Long value exceeds Int.MaxValue") {
        val source = LongValues(Int.MaxValue.toLong + 1L, 100L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isLeft)
      },
      test("fails when Long value is below Int.MinValue") {
        val source = LongValues(Int.MinValue.toLong - 1L, 100L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isLeft)
      },
      test("succeeds at Int.MaxValue boundary") {
        val source = LongValues(Int.MaxValue.toLong, Int.MinValue.toLong)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(Int.MaxValue, Int.MinValue))))
      },
      test("succeeds with negative values in range") {
        val source = LongValues(-1000L, -999L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(-1000, -999))))
      }
    ),
    suite("Int to Short Narrowing")(
      test("succeeds when Int value fits in Short range") {
        val source = IntValue(1000)
        val result = Into.derived[IntValue, ShortValue].into(source)

        assert(result)(isRight(equalTo(ShortValue(1000.toShort))))
      },
      test("fails when Int value exceeds Short.MaxValue") {
        val source = IntValue(Short.MaxValue.toInt + 1)
        val result = Into.derived[IntValue, ShortValue].into(source)

        assert(result)(isLeft)
      },
      test("fails when Int value is below Short.MinValue") {
        val source = IntValue(Short.MinValue.toInt - 1)
        val result = Into.derived[IntValue, ShortValue].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Int to Byte Narrowing")(
      test("succeeds when Int value fits in Byte range") {
        val source = IntValue(100)
        val result = Into.derived[IntValue, ByteValue].into(source)

        assert(result)(isRight(equalTo(ByteValue(100.toByte))))
      },
      test("fails when Int value exceeds Byte.MaxValue") {
        val source = IntValue(Byte.MaxValue.toInt + 1)
        val result = Into.derived[IntValue, ByteValue].into(source)

        assert(result)(isLeft)
      },
      test("fails when Int value is below Byte.MinValue") {
        val source = IntValue(Byte.MinValue.toInt - 1)
        val result = Into.derived[IntValue, ByteValue].into(source)

        assert(result)(isLeft)
      },
      test("succeeds at Byte boundaries") {
        val source = IntValue(Byte.MaxValue.toInt)
        val result = Into.derived[IntValue, ByteValue].into(source)

        assert(result)(isRight(equalTo(ByteValue(Byte.MaxValue))))
      }
    ),
    suite("Double to Float Narrowing")(
      test("succeeds when Double value fits in Float range") {
        val source = DoubleValue(3.14)
        val result = Into.derived[DoubleValue, FloatValue].into(source)

        assert(result)(isRight(anything))
      },
      test("handles Float.MaxValue boundary") {
        val source = DoubleValue(Float.MaxValue.toDouble)
        val result = Into.derived[DoubleValue, FloatValue].into(source)

        assert(result)(isRight(anything))
      }
    ),
    suite("Multiple Narrowing Fields")(
      test("succeeds when all fields fit in target range") {
        val source = MixedLong("test", 100L, 200L)
        val result = Into.derived[MixedLong, MixedInt].into(source)

        assert(result)(isRight(equalTo(MixedInt("test", 100, 200))))
      },
      test("fails fast on first overflow field") {
        val source = MixedLong("test", Long.MaxValue, 200L)
        val result = Into.derived[MixedLong, MixedInt].into(source)

        assert(result)(isLeft)
      },
      test("fails on second field overflow") {
        val source = MixedLong("test", 100L, Long.MaxValue)
        val result = Into.derived[MixedLong, MixedInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Zero and Small Values")(
      test("zero converts successfully") {
        val source = LongValues(0L, 0L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(0, 0))))
      },
      test("small positive values convert successfully") {
        val source = LongValues(1L, 2L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(1, 2))))
      },
      test("small negative values convert successfully") {
        val source = LongValues(-1L, -2L)
        val result = Into.derived[LongValues, IntValues].into(source)

        assert(result)(isRight(equalTo(IntValues(-1, -2))))
      }
    )
  )
}
