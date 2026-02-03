package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object PrimitiveConversionSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveConversion")(
    suite("basic conversions")(
      test("IntToLong converts Int to Long") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToLong(value)
        assertTrue(result == Right(DynamicValue.long(42L)))
      },
      test("LongToInt converts Long to Int") {
        val value  = DynamicValue.long(42L)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("IntToString converts Int to String") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToString(value)
        assertTrue(result == Right(DynamicValue.string("42")))
      },
      test("StringToInt converts String to Int") {
        val value  = DynamicValue.string("42")
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("DoubleToInt truncates decimal part") {
        val value  = DynamicValue.double(42.9)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("LongToString converts Long to String") {
        val value  = DynamicValue.long(9876543210L)
        val result = PrimitiveConversion.LongToString(value)
        assertTrue(result == Right(DynamicValue.string("9876543210")))
      },
      test("StringToLong converts String to Long") {
        val value  = DynamicValue.string("9876543210")
        val result = PrimitiveConversion.StringToLong(value)
        assertTrue(result == Right(DynamicValue.long(9876543210L)))
      },
      test("DoubleToString converts Double to String") {
        val value  = DynamicValue.double(3.14159)
        val result = PrimitiveConversion.DoubleToString(value)
        assertTrue(result == Right(DynamicValue.string("3.14159")))
      },
      test("StringToDouble converts String to Double") {
        val value  = DynamicValue.string("3.14159")
        val result = PrimitiveConversion.StringToDouble(value)
        assertTrue(result == Right(DynamicValue.double(3.14159)))
      },
      test("FloatToDouble converts Float to Double") {
        val value  = DynamicValue.float(3.14f)
        val result = PrimitiveConversion.FloatToDouble(value)
        assertTrue(result.isRight)
      },
      test("DoubleToFloat converts Double to Float within range") {
        val value  = DynamicValue.double(3.14)
        val result = PrimitiveConversion.DoubleToFloat(value)
        assertTrue(result.isRight)
      },
      test("IntToDouble converts Int to Double") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToDouble(value)
        assertTrue(result == Right(DynamicValue.double(42.0)))
      }
    ),
    suite("boolean conversions")(
      test("BooleanToString converts true to 'true'") {
        val value  = DynamicValue.boolean(true)
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result == Right(DynamicValue.string("true")))
      },
      test("BooleanToString converts false to 'false'") {
        val value  = DynamicValue.boolean(false)
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result == Right(DynamicValue.string("false")))
      },
      test("StringToBoolean converts 'true' to true (case-insensitive)") {
        assertTrue(
          PrimitiveConversion.StringToBoolean(DynamicValue.string("true")) == Right(DynamicValue.boolean(true)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("TRUE")) == Right(DynamicValue.boolean(true)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("True")) == Right(DynamicValue.boolean(true))
        )
      },
      test("StringToBoolean converts 'false' to false (case-insensitive)") {
        assertTrue(
          PrimitiveConversion.StringToBoolean(DynamicValue.string("false")) == Right(DynamicValue.boolean(false)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("FALSE")) == Right(DynamicValue.boolean(false)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("False")) == Right(DynamicValue.boolean(false))
        )
      },
      test("StringToBoolean fails for invalid string") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.string("yes"))
        assertTrue(result.isLeft)
      }
    ),
    suite("overflow and edge cases")(
      test("LongToInt fails when value exceeds Int.MaxValue") {
        val value  = DynamicValue.long(Int.MaxValue.toLong + 1)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt fails when value is below Int.MinValue") {
        val value  = DynamicValue.long(Int.MinValue.toLong - 1)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt succeeds at Int boundaries") {
        val maxResult = PrimitiveConversion.LongToInt(DynamicValue.long(Int.MaxValue.toLong))
        val minResult = PrimitiveConversion.LongToInt(DynamicValue.long(Int.MinValue.toLong))
        assertTrue(
          maxResult == Right(DynamicValue.int(Int.MaxValue)),
          minResult == Right(DynamicValue.int(Int.MinValue))
        )
      },
      test("LongToInt fails on overflow") {
        val result = PrimitiveConversion.LongToInt(DynamicValue.long(Long.MaxValue))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for NaN") {
        val value  = DynamicValue.double(Double.NaN)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for positive Infinity") {
        val value  = DynamicValue.double(Double.PositiveInfinity)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for negative Infinity") {
        val value  = DynamicValue.double(Double.NegativeInfinity)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails when value exceeds Int range") {
        val aboveMax = DynamicValue.double(Int.MaxValue.toDouble + 1000)
        val belowMin = DynamicValue.double(Int.MinValue.toDouble - 1000)
        assertTrue(
          PrimitiveConversion.DoubleToInt(aboveMax).isLeft,
          PrimitiveConversion.DoubleToInt(belowMin).isLeft
        )
      },
      test("DoubleToInt fails on overflow") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.double(Double.MaxValue))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails when value exceeds Float range") {
        val aboveMax = DynamicValue.double(Float.MaxValue.toDouble * 2)
        val belowMin = DynamicValue.double(-Float.MaxValue.toDouble * 2)
        assertTrue(
          PrimitiveConversion.DoubleToFloat(aboveMax).isLeft,
          PrimitiveConversion.DoubleToFloat(belowMin).isLeft
        )
      },
      test("DoubleToFloat fails on overflow") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.MaxValue))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat preserves NaN and Infinity") {
        val nan    = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NaN))
        val posInf = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.PositiveInfinity))
        val negInf = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NegativeInfinity))
        assertTrue(
          nan.isRight,
          posInf.isRight,
          negInf.isRight
        )
      },
      test("DoubleToFloat handles NaN") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NaN))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f.isNaN)
          case _ => assertTrue(false)
        }
      },
      test("DoubleToFloat handles Infinity") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.PositiveInfinity))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f.isInfinite)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("type errors")(
      test("IntToLong fails for non-Int input") {
        val value  = DynamicValue.string("not an int")
        val result = PrimitiveConversion.IntToLong(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt fails for non-Long input") {
        val value  = DynamicValue.string("not a long")
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for non-Double input") {
        val value  = DynamicValue.string("not a double")
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails for non-Double input") {
        val value  = DynamicValue.string("not a double")
        val result = PrimitiveConversion.DoubleToFloat(value)
        assertTrue(result.isLeft)
      },
      test("FloatToDouble fails for non-Float input") {
        val value  = DynamicValue.string("not a float")
        val result = PrimitiveConversion.FloatToDouble(value)
        assertTrue(result.isLeft)
      },
      test("StringToInt fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result.isLeft)
      },
      test("IntToString fails for non-Int input") {
        val value  = DynamicValue.string("not an int")
        val result = PrimitiveConversion.IntToString(value)
        assertTrue(result.isLeft)
      },
      test("BooleanToString fails for non-Boolean input") {
        val value  = DynamicValue.string("not a boolean")
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result.isLeft)
      },
      test("StringToBoolean fails for non-String input") {
        val value  = DynamicValue.boolean(true)
        val result = PrimitiveConversion.StringToBoolean(value)
        assertTrue(result.isLeft)
      },
      test("StringToInt fails for non-numeric string") {
        val value  = DynamicValue.string("hello")
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result.isLeft)
      },
      test("StringToLong fails for non-numeric string") {
        val value  = DynamicValue.string("not-a-number")
        val result = PrimitiveConversion.StringToLong(value)
        assertTrue(result.isLeft)
      },
      test("StringToDouble fails for non-numeric string") {
        val value  = DynamicValue.string("not-a-double")
        val result = PrimitiveConversion.StringToDouble(value)
        assertTrue(result.isLeft)
      },
      test("LongToString fails on non-Long") {
        val result = PrimitiveConversion.LongToString(DynamicValue.string("not a long"))
        assertTrue(result.isLeft)
      },
      test("StringToLong fails on non-String") {
        val result = PrimitiveConversion.StringToLong(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("DoubleToString fails on non-Double") {
        val result = PrimitiveConversion.DoubleToString(DynamicValue.string("not a double"))
        assertTrue(result.isLeft)
      },
      test("StringToDouble fails on non-String") {
        val result = PrimitiveConversion.StringToDouble(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("FloatToDouble fails on non-Float") {
        val result = PrimitiveConversion.FloatToDouble(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails on non-Double") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("BooleanToString fails on non-Boolean") {
        val result = PrimitiveConversion.BooleanToString(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringToBoolean fails on non-String") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("IntToDouble fails on non-Int") {
        val result = PrimitiveConversion.IntToDouble(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails on non-Double") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.string("not double"))
        assertTrue(result.isLeft)
      },
      test("IntToString fails on non-Int") {
        val result = PrimitiveConversion.IntToString(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("StringToInt fails on non-String") {
        val result = PrimitiveConversion.StringToInt(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("IntToLong fails on non-Int") {
        val result = PrimitiveConversion.IntToLong(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("LongToInt fails on non-Long") {
        val result = PrimitiveConversion.LongToInt(DynamicValue.string("not long"))
        assertTrue(result.isLeft)
      }
    )
  )
}
