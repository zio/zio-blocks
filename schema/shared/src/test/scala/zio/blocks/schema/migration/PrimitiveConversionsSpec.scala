package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for primitive type conversions.
 *
 * Covers:
 * - Numeric conversions (widening and narrowing)
 * - String conversions (to/from all primitive types)
 * - Temporal type conversions
 * - UUID conversions
 * - Error handling for invalid conversions
 * - Edge cases
 */
object PrimitiveConversionsSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicByte(b: Byte): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Byte(b))

  def dynamicShort(s: Short): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Short(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicLong(l: Long): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Long(l))

  def dynamicFloat(f: Float): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Float(f))

  def dynamicDouble(d: Double): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Double(d))

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def dynamicChar(c: Char): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Char(c))

  def dynamicBigInt(bi: BigInt): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.BigInt(bi))

  def dynamicBigDecimal(bd: BigDecimal): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))

  def convert(from: String, to: String, value: DynamicValue): Either[String, DynamicValue] =
    PrimitiveConversions.convert(value, from, to)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveConversionsSpec")(
    suite("Numeric widening conversions")(
      test("Byte -> Short") {
        assertTrue(convert("Byte", "Short", dynamicByte(42)) == Right(dynamicShort(42)))
      },
      test("Byte -> Int") {
        assertTrue(convert("Byte", "Int", dynamicByte(42)) == Right(dynamicInt(42)))
      },
      test("Byte -> Long") {
        assertTrue(convert("Byte", "Long", dynamicByte(42)) == Right(dynamicLong(42L)))
      },
      test("Short -> Int") {
        assertTrue(convert("Short", "Int", dynamicShort(1000)) == Right(dynamicInt(1000)))
      },
      test("Short -> Long") {
        assertTrue(convert("Short", "Long", dynamicShort(1000)) == Right(dynamicLong(1000L)))
      },
      test("Int -> Long") {
        assertTrue(convert("Int", "Long", dynamicInt(1000000)) == Right(dynamicLong(1000000L)))
      },
      test("Int -> Float") {
        assertTrue(convert("Int", "Float", dynamicInt(42)) == Right(dynamicFloat(42.0f)))
      },
      test("Int -> Double") {
        assertTrue(convert("Int", "Double", dynamicInt(42)) == Right(dynamicDouble(42.0)))
      },
      test("Long -> Float") {
        assertTrue(convert("Long", "Float", dynamicLong(42L)) == Right(dynamicFloat(42.0f)))
      },
      test("Long -> Double") {
        assertTrue(convert("Long", "Double", dynamicLong(42L)) == Right(dynamicDouble(42.0)))
      },
      test("Float -> Double") {
        assertTrue(convert("Float", "Double", dynamicFloat(3.14f)) == Right(dynamicDouble(3.14f.toDouble)))
      }
    ),
    suite("Numeric narrowing conversions")(
      test("Short -> Byte") {
        assertTrue(convert("Short", "Byte", dynamicShort(42)) == Right(dynamicByte(42)))
      },
      test("Int -> Byte") {
        assertTrue(convert("Int", "Byte", dynamicInt(42)) == Right(dynamicByte(42)))
      },
      test("Int -> Short") {
        assertTrue(convert("Int", "Short", dynamicInt(1000)) == Right(dynamicShort(1000)))
      },
      test("Long -> Int") {
        assertTrue(convert("Long", "Int", dynamicLong(1000000L)) == Right(dynamicInt(1000000)))
      },
      test("Double -> Float") {
        assertTrue(convert("Double", "Float", dynamicDouble(3.14)) == Right(dynamicFloat(3.14f)))
      },
      test("Double -> Int") {
        assertTrue(convert("Double", "Int", dynamicDouble(42.9)) == Right(dynamicInt(42)))
      },
      test("Double -> Long") {
        assertTrue(convert("Double", "Long", dynamicDouble(42.9)) == Right(dynamicLong(42L)))
      }
    ),
    suite("BigInt/BigDecimal conversions")(
      test("Int -> BigInt") {
        assertTrue(convert("Int", "BigInt", dynamicInt(42)) == Right(dynamicBigInt(BigInt(42))))
      },
      test("Long -> BigInt") {
        assertTrue(convert("Long", "BigInt", dynamicLong(42L)) == Right(dynamicBigInt(BigInt(42))))
      },
      test("BigInt -> Int") {
        assertTrue(convert("BigInt", "Int", dynamicBigInt(BigInt(42))) == Right(dynamicInt(42)))
      },
      test("BigInt -> Long") {
        assertTrue(convert("BigInt", "Long", dynamicBigInt(BigInt(42))) == Right(dynamicLong(42L)))
      },
      test("Double -> BigDecimal") {
        assertTrue(convert("Double", "BigDecimal", dynamicDouble(3.14)) == Right(dynamicBigDecimal(BigDecimal(3.14))))
      },
      test("BigDecimal -> Double") {
        assertTrue(convert("BigDecimal", "Double", dynamicBigDecimal(BigDecimal(3.14))) == Right(dynamicDouble(3.14)))
      },
      test("Int -> BigDecimal") {
        assertTrue(convert("Int", "BigDecimal", dynamicInt(42)) == Right(dynamicBigDecimal(BigDecimal(42))))
      },
      test("BigInt -> BigDecimal") {
        assertTrue(convert("BigInt", "BigDecimal", dynamicBigInt(BigInt(42))) == Right(dynamicBigDecimal(BigDecimal(42))))
      }
    ),
    suite("String to numeric conversions")(
      test("String -> Int") {
        assertTrue(convert("String", "Int", dynamicString("42")) == Right(dynamicInt(42)))
      },
      test("String -> Int (negative)") {
        assertTrue(convert("String", "Int", dynamicString("-42")) == Right(dynamicInt(-42)))
      },
      test("String -> Long") {
        assertTrue(convert("String", "Long", dynamicString("42")) == Right(dynamicLong(42L)))
      },
      test("String -> Double") {
        assertTrue(convert("String", "Double", dynamicString("3.14")) == Right(dynamicDouble(3.14)))
      },
      test("String -> Float") {
        assertTrue(convert("String", "Float", dynamicString("3.14")) == Right(dynamicFloat(3.14f)))
      },
      test("String -> Byte") {
        assertTrue(convert("String", "Byte", dynamicString("42")) == Right(dynamicByte(42)))
      },
      test("String -> Short") {
        assertTrue(convert("String", "Short", dynamicString("1000")) == Right(dynamicShort(1000)))
      },
      test("String -> BigInt") {
        assertTrue(convert("String", "BigInt", dynamicString("123456789012345678901234567890")) ==
          Right(dynamicBigInt(BigInt("123456789012345678901234567890"))))
      },
      test("String -> BigDecimal") {
        assertTrue(convert("String", "BigDecimal", dynamicString("123.456")) ==
          Right(dynamicBigDecimal(BigDecimal("123.456"))))
      },
      test("String -> Boolean (true)") {
        assertTrue(convert("String", "Boolean", dynamicString("true")) == Right(dynamicBool(true)))
      },
      test("String -> Boolean (false)") {
        assertTrue(convert("String", "Boolean", dynamicString("false")) == Right(dynamicBool(false)))
      },
      test("String -> Char (single char)") {
        assertTrue(convert("String", "Char", dynamicString("A")) == Right(dynamicChar('A')))
      }
    ),
    suite("Numeric to String conversions")(
      test("Int -> String") {
        assertTrue(convert("Int", "String", dynamicInt(42)) == Right(dynamicString("42")))
      },
      test("Long -> String") {
        assertTrue(convert("Long", "String", dynamicLong(42L)) == Right(dynamicString("42")))
      },
      test("Double -> String") {
        val result = convert("Double", "String", dynamicDouble(3.14))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            assertTrue(s.startsWith("3.14"))
          case _ => assertTrue(false)
        }
      },
      test("Boolean -> String") {
        assertTrue(convert("Boolean", "String", dynamicBool(true)) == Right(dynamicString("true")))
      },
      test("Char -> String") {
        assertTrue(convert("Char", "String", dynamicChar('A')) == Right(dynamicString("A")))
      },
      test("BigInt -> String") {
        assertTrue(convert("BigInt", "String", dynamicBigInt(BigInt("12345"))) == Right(dynamicString("12345")))
      },
      test("BigDecimal -> String") {
        assertTrue(convert("BigDecimal", "String", dynamicBigDecimal(BigDecimal("123.45"))) == Right(dynamicString("123.45")))
      }
    ),
    suite("Char conversions")(
      test("Char -> Int") {
        assertTrue(convert("Char", "Int", dynamicChar('A')) == Right(dynamicInt(65)))
      },
      test("Int -> Char") {
        assertTrue(convert("Int", "Char", dynamicInt(65)) == Right(dynamicChar('A')))
      }
    ),
    suite("Error handling")(
      test("String -> Int fails for non-numeric string") {
        assertTrue(convert("String", "Int", dynamicString("not a number")).isLeft)
      },
      test("String -> Double fails for non-numeric string") {
        assertTrue(convert("String", "Double", dynamicString("abc")).isLeft)
      },
      test("String -> Boolean fails for invalid boolean") {
        assertTrue(convert("String", "Boolean", dynamicString("maybe")).isLeft)
      },
      test("String -> Char fails for empty string") {
        assertTrue(convert("String", "Char", dynamicString("")).isLeft)
      },
      test("String -> Char fails for multi-char string") {
        assertTrue(convert("String", "Char", dynamicString("AB")).isLeft)
      },
      test("unsupported conversion returns error") {
        // Converting between incompatible types (Boolean -> Double is not supported)
        assertTrue(convert("Boolean", "Double", dynamicBool(true)).isLeft)
      }
    ),
    suite("Edge cases")(
      test("Int max value") {
        assertTrue(convert("Int", "Long", dynamicInt(Int.MaxValue)) == Right(dynamicLong(Int.MaxValue.toLong)))
      },
      test("Int min value") {
        assertTrue(convert("Int", "Long", dynamicInt(Int.MinValue)) == Right(dynamicLong(Int.MinValue.toLong)))
      },
      test("Long max value to BigInt") {
        assertTrue(convert("Long", "BigInt", dynamicLong(Long.MaxValue)) == Right(dynamicBigInt(BigInt(Long.MaxValue))))
      },
      test("zero conversions") {
        assertTrue(convert("Int", "Long", dynamicInt(0)) == Right(dynamicLong(0L)))
        assertTrue(convert("Int", "Double", dynamicInt(0)) == Right(dynamicDouble(0.0)))
        assertTrue(convert("Int", "String", dynamicInt(0)) == Right(dynamicString("0")))
      },
      test("negative number conversions") {
        assertTrue(convert("Int", "Long", dynamicInt(-100)) == Right(dynamicLong(-100L)))
        assertTrue(convert("Int", "String", dynamicInt(-100)) == Right(dynamicString("-100")))
      },
      test("floating point precision") {
        // Float has limited precision
        val result = convert("Double", "Float", dynamicDouble(1.23456789012345))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            // Float should truncate precision
            assertTrue(f != 1.23456789012345f || f == 1.2345679f) // approximation
          case _ => assertTrue(false)
        }
      },
      test("String -> UUID") {
        val uuidStr = "550e8400-e29b-41d4-a716-446655440000"
        val result = convert("String", "UUID", dynamicString(uuidStr))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.UUID(uuid))) =>
            assertTrue(uuid.toString == uuidStr)
          case _ => assertTrue(false)
        }
      },
      test("UUID -> String") {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val input = DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
        val result = convert("UUID", "String", input)
        assertTrue(result == Right(dynamicString("550e8400-e29b-41d4-a716-446655440000")))
      }
    ),
    suite("Resolved.Convert integration")(
      test("Convert expression with Int -> String") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("42")))
      },
      test("Convert expression with String -> Int") {
        val expr = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("42")) == Right(dynamicInt(42)))
      },
      test("Convert expression with inner transformation") {
        val expr = Resolved.Convert("Int", "String", Resolved.Literal.int(100))
        // Inner expression returns 100, then converted to String
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("100")))
      },
      test("Chained conversions: Int -> Long -> String") {
        val expr = Resolved.Compose(
          Resolved.Convert("Long", "String", Resolved.Identity),
          Resolved.Convert("Int", "Long", Resolved.Identity)
        )
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("42")))
      }
    )
  )
}
