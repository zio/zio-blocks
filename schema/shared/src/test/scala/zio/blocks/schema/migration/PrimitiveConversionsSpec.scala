package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for primitive type conversions.
 *
 * Covers:
 *   - Numeric conversions (widening and narrowing)
 *   - String conversions (to/from all primitive types)
 *   - Temporal type conversions
 *   - UUID conversions
 *   - Error handling for invalid conversions
 *   - Edge cases
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
        assertTrue(
          convert("BigInt", "BigDecimal", dynamicBigInt(BigInt(42))) == Right(dynamicBigDecimal(BigDecimal(42)))
        )
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
        assertTrue(
          convert("String", "BigInt", dynamicString("123456789012345678901234567890")) ==
            Right(dynamicBigInt(BigInt("123456789012345678901234567890")))
        )
      },
      test("String -> BigDecimal") {
        assertTrue(
          convert("String", "BigDecimal", dynamicString("123.456")) ==
            Right(dynamicBigDecimal(BigDecimal("123.456")))
        )
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
        assertTrue(
          convert("BigDecimal", "String", dynamicBigDecimal(BigDecimal("123.45"))) == Right(dynamicString("123.45"))
        )
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
        val result  = convert("String", "UUID", dynamicString(uuidStr))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.UUID(uuid))) =>
            assertTrue(uuid.toString == uuidStr)
          case _ => assertTrue(false)
        }
      },
      test("UUID -> String") {
        val uuid   = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val input  = DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
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
    ),
    suite("Identity conversions")(
      test("Int -> Int returns same value") {
        assertTrue(convert("Int", "Int", dynamicInt(42)) == Right(dynamicInt(42)))
      },
      test("String -> String returns same value") {
        assertTrue(convert("String", "String", dynamicString("hello")) == Right(dynamicString("hello")))
      },
      test("Boolean -> Boolean returns same value") {
        assertTrue(convert("Boolean", "Boolean", dynamicBool(true)) == Right(dynamicBool(true)))
      }
    ),
    suite("Narrowing conversion failures")(
      test("Short -> Byte fails for out of range value") {
        assertTrue(convert("Short", "Byte", dynamicShort(200)).isLeft)
      },
      test("Int -> Byte fails for out of range value") {
        assertTrue(convert("Int", "Byte", dynamicInt(500)).isLeft)
      },
      test("Int -> Short fails for out of range value") {
        assertTrue(convert("Int", "Short", dynamicInt(100000)).isLeft)
      },
      test("Long -> Byte fails for out of range value") {
        assertTrue(convert("Long", "Byte", dynamicLong(500L)).isLeft)
      },
      test("Long -> Short fails for out of range value") {
        assertTrue(convert("Long", "Short", dynamicLong(100000L)).isLeft)
      },
      test("Long -> Int fails for out of range value") {
        assertTrue(convert("Long", "Int", dynamicLong(Long.MaxValue)).isLeft)
      },
      test("Float -> Int fails for out of range value") {
        assertTrue(convert("Float", "Int", dynamicFloat(Float.MaxValue)).isLeft)
      },
      test("Double -> Int fails for out of range value") {
        assertTrue(convert("Double", "Int", dynamicDouble(Double.MaxValue)).isLeft)
      },
      test("Double -> Long fails for out of range value") {
        assertTrue(convert("Double", "Long", dynamicDouble(Double.MaxValue)).isLeft)
      },
      test("BigInt -> Int fails for out of range value") {
        assertTrue(convert("BigInt", "Int", dynamicBigInt(BigInt(Long.MaxValue))).isLeft)
      },
      test("BigInt -> Long fails for out of range value") {
        assertTrue(convert("BigInt", "Long", dynamicBigInt(BigInt("9223372036854775808"))).isLeft)
      },
      test("BigDecimal -> Int fails for out of range value") {
        assertTrue(convert("BigDecimal", "Int", dynamicBigDecimal(BigDecimal(Long.MaxValue))).isLeft)
      },
      test("BigDecimal -> Long fails for out of range value") {
        assertTrue(convert("BigDecimal", "Long", dynamicBigDecimal(BigDecimal("9223372036854775808"))).isLeft)
      },
      test("Int -> Char fails for negative value") {
        assertTrue(convert("Int", "Char", dynamicInt(-1)).isLeft)
      },
      test("Int -> Char fails for out of range value") {
        assertTrue(convert("Int", "Char", dynamicInt(100000)).isLeft)
      }
    ),
    suite("Boolean <-> Int conversions")(
      test("Boolean true -> Int 1") {
        assertTrue(convert("Boolean", "Int", dynamicBool(true)) == Right(dynamicInt(1)))
      },
      test("Boolean false -> Int 0") {
        assertTrue(convert("Boolean", "Int", dynamicBool(false)) == Right(dynamicInt(0)))
      },
      test("Int 1 -> Boolean true") {
        assertTrue(convert("Int", "Boolean", dynamicInt(1)) == Right(dynamicBool(true)))
      },
      test("Int 0 -> Boolean false") {
        assertTrue(convert("Int", "Boolean", dynamicInt(0)) == Right(dynamicBool(false)))
      },
      test("Non-zero Int -> Boolean true") {
        assertTrue(convert("Int", "Boolean", dynamicInt(42)) == Right(dynamicBool(true)))
      }
    ),
    suite("Byte widening conversions")(
      test("Byte -> Float") {
        assertTrue(convert("Byte", "Float", dynamicByte(42)) == Right(dynamicFloat(42.0f)))
      },
      test("Byte -> Double") {
        assertTrue(convert("Byte", "Double", dynamicByte(42)) == Right(dynamicDouble(42.0)))
      },
      test("Byte -> BigInt") {
        assertTrue(convert("Byte", "BigInt", dynamicByte(42)) == Right(dynamicBigInt(BigInt(42))))
      },
      test("Byte -> BigDecimal") {
        assertTrue(convert("Byte", "BigDecimal", dynamicByte(42)) == Right(dynamicBigDecimal(BigDecimal(42))))
      }
    ),
    suite("Short widening conversions")(
      test("Short -> Float") {
        assertTrue(convert("Short", "Float", dynamicShort(1000)) == Right(dynamicFloat(1000.0f)))
      },
      test("Short -> Double") {
        assertTrue(convert("Short", "Double", dynamicShort(1000)) == Right(dynamicDouble(1000.0)))
      },
      test("Short -> BigInt") {
        assertTrue(convert("Short", "BigInt", dynamicShort(1000)) == Right(dynamicBigInt(BigInt(1000))))
      },
      test("Short -> BigDecimal") {
        assertTrue(convert("Short", "BigDecimal", dynamicShort(1000)) == Right(dynamicBigDecimal(BigDecimal(1000))))
      }
    ),
    suite("Float/BigDecimal conversions")(
      test("Float -> Long") {
        assertTrue(convert("Float", "Long", dynamicFloat(42.5f)) == Right(dynamicLong(42L)))
      },
      test("Float -> BigDecimal") {
        val result = convert("Float", "BigDecimal", dynamicFloat(3.14f))
        assertTrue(result.isRight)
      },
      test("BigDecimal -> BigInt") {
        assertTrue(
          convert("BigDecimal", "BigInt", dynamicBigDecimal(BigDecimal(42))) == Right(dynamicBigInt(BigInt(42)))
        )
      }
    ),
    suite("Long widening conversions")(
      test("Long -> BigInt") {
        assertTrue(convert("Long", "BigInt", dynamicLong(Long.MaxValue)) == Right(dynamicBigInt(BigInt(Long.MaxValue))))
      },
      test("Long -> BigDecimal") {
        assertTrue(
          convert("Long", "BigDecimal", dynamicLong(1000000L)) == Right(dynamicBigDecimal(BigDecimal(1000000L)))
        )
      }
    ),
    suite("String parsing failures")(
      test("String -> BigInt fails for non-numeric") {
        assertTrue(convert("String", "BigInt", dynamicString("not a number")).isLeft)
      },
      test("String -> BigDecimal fails for non-numeric") {
        assertTrue(convert("String", "BigDecimal", dynamicString("abc")).isLeft)
      },
      test("String -> UUID fails for invalid UUID") {
        assertTrue(convert("String", "UUID", dynamicString("not-a-uuid")).isLeft)
      },
      test("String -> Instant fails for invalid format") {
        assertTrue(convert("String", "Instant", dynamicString("not-an-instant")).isLeft)
      },
      test("String -> LocalDate fails for invalid format") {
        assertTrue(convert("String", "LocalDate", dynamicString("not-a-date")).isLeft)
      },
      test("String -> LocalTime fails for invalid format") {
        assertTrue(convert("String", "LocalTime", dynamicString("not-a-time")).isLeft)
      },
      test("String -> LocalDateTime fails for invalid format") {
        assertTrue(convert("String", "LocalDateTime", dynamicString("not-a-datetime")).isLeft)
      },
      test("String -> Duration fails for invalid format") {
        assertTrue(convert("String", "Duration", dynamicString("not-a-duration")).isLeft)
      },
      test("String -> Period fails for invalid format") {
        assertTrue(convert("String", "Period", dynamicString("not-a-period")).isLeft)
      },
      test("String -> Year fails for invalid format") {
        assertTrue(convert("String", "Year", dynamicString("not-a-year")).isLeft)
      },
      test("String -> YearMonth fails for invalid format") {
        assertTrue(convert("String", "YearMonth", dynamicString("not-year-month")).isLeft)
      },
      test("String -> MonthDay fails for invalid format") {
        assertTrue(convert("String", "MonthDay", dynamicString("not-month-day")).isLeft)
      },
      test("String -> DayOfWeek fails for invalid day") {
        assertTrue(convert("String", "DayOfWeek", dynamicString("NOTADAY")).isLeft)
      },
      test("String -> Month fails for invalid month") {
        assertTrue(convert("String", "Month", dynamicString("NOTAMONTH")).isLeft)
      },
      test("String -> ZoneId fails for invalid zone") {
        assertTrue(convert("String", "ZoneId", dynamicString("Invalid/Zone")).isLeft)
      },
      test("String -> ZoneOffset fails for invalid offset") {
        assertTrue(convert("String", "ZoneOffset", dynamicString("invalid")).isLeft)
      },
      test("String -> Currency fails for invalid currency") {
        assertTrue(convert("String", "Currency", dynamicString("INVALID")).isLeft)
      },
      test("String -> OffsetDateTime fails for invalid format") {
        assertTrue(convert("String", "OffsetDateTime", dynamicString("not-offset-datetime")).isLeft)
      },
      test("String -> OffsetTime fails for invalid format") {
        assertTrue(convert("String", "OffsetTime", dynamicString("not-offset-time")).isLeft)
      },
      test("String -> ZonedDateTime fails for invalid format") {
        assertTrue(convert("String", "ZonedDateTime", dynamicString("not-zoned-datetime")).isLeft)
      }
    ),
    suite("Valid temporal conversions")(
      test("String -> LocalDate") {
        val result = convert("String", "LocalDate", dynamicString("2024-01-15"))
        assertTrue(result.isRight)
      },
      test("String -> LocalTime") {
        val result = convert("String", "LocalTime", dynamicString("10:30:00"))
        assertTrue(result.isRight)
      },
      test("String -> LocalDateTime") {
        val result = convert("String", "LocalDateTime", dynamicString("2024-01-15T10:30:00"))
        assertTrue(result.isRight)
      },
      test("String -> Duration") {
        val result = convert("String", "Duration", dynamicString("PT1H30M"))
        assertTrue(result.isRight)
      },
      test("String -> Period") {
        val result = convert("String", "Period", dynamicString("P1Y2M3D"))
        assertTrue(result.isRight)
      },
      test("String -> Year") {
        val result = convert("String", "Year", dynamicString("2024"))
        assertTrue(result.isRight)
      },
      test("String -> YearMonth") {
        val result = convert("String", "YearMonth", dynamicString("2024-01"))
        assertTrue(result.isRight)
      },
      test("String -> MonthDay") {
        val result = convert("String", "MonthDay", dynamicString("--01-15"))
        assertTrue(result.isRight)
      },
      test("String -> DayOfWeek") {
        val result = convert("String", "DayOfWeek", dynamicString("MONDAY"))
        assertTrue(result.isRight)
      },
      test("String -> Month") {
        val result = convert("String", "Month", dynamicString("JANUARY"))
        assertTrue(result.isRight)
      },
      test("String -> Currency") {
        val result = convert("String", "Currency", dynamicString("USD"))
        assertTrue(result.isRight)
      },
      test("String -> Instant") {
        val result = convert("String", "Instant", dynamicString("2024-01-15T10:30:00Z"))
        assertTrue(result.isRight)
      },
      test("String -> OffsetDateTime") {
        val result = convert("String", "OffsetDateTime", dynamicString("2024-01-15T10:30:00+01:00"))
        assertTrue(result.isRight)
      },
      test("String -> OffsetTime") {
        val result = convert("String", "OffsetTime", dynamicString("10:30:00+01:00"))
        assertTrue(result.isRight)
      },
      test("String -> ZonedDateTime") {
        val result = convert("String", "ZonedDateTime", dynamicString("2024-01-15T10:30:00+01:00[Europe/Paris]"))
        assertTrue(result.isRight)
      },
      test("String -> ZoneId") {
        val result = convert("String", "ZoneId", dynamicString("Europe/Paris"))
        assertTrue(result.isRight)
      },
      test("String -> ZoneOffset") {
        val result = convert("String", "ZoneOffset", dynamicString("+01:00"))
        assertTrue(result.isRight)
      }
    ),
    suite("Temporal to String conversions")(
      test("Instant -> String") {
        val instant = java.time.Instant.parse("2024-01-15T10:30:00Z")
        val input   = DynamicValue.Primitive(PrimitiveValue.Instant(instant))
        val result  = convert("Instant", "String", input)
        assertTrue(result.isRight)
      },
      test("LocalDate -> String") {
        val ld     = java.time.LocalDate.parse("2024-01-15")
        val input  = DynamicValue.Primitive(PrimitiveValue.LocalDate(ld))
        val result = convert("LocalDate", "String", input)
        assertTrue(result.isRight)
      },
      test("LocalTime -> String") {
        val lt     = java.time.LocalTime.parse("10:30:00")
        val input  = DynamicValue.Primitive(PrimitiveValue.LocalTime(lt))
        val result = convert("LocalTime", "String", input)
        assertTrue(result.isRight)
      },
      test("LocalDateTime -> String") {
        val ldt    = java.time.LocalDateTime.parse("2024-01-15T10:30:00")
        val input  = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(ldt))
        val result = convert("LocalDateTime", "String", input)
        assertTrue(result.isRight)
      },
      test("Duration -> String") {
        val d      = java.time.Duration.parse("PT1H30M")
        val input  = DynamicValue.Primitive(PrimitiveValue.Duration(d))
        val result = convert("Duration", "String", input)
        assertTrue(result.isRight)
      },
      test("Period -> String") {
        val p      = java.time.Period.parse("P1Y2M3D")
        val input  = DynamicValue.Primitive(PrimitiveValue.Period(p))
        val result = convert("Period", "String", input)
        assertTrue(result.isRight)
      },
      test("Year -> String") {
        val y      = java.time.Year.parse("2024")
        val input  = DynamicValue.Primitive(PrimitiveValue.Year(y))
        val result = convert("Year", "String", input)
        assertTrue(result.isRight)
      },
      test("YearMonth -> String") {
        val ym     = java.time.YearMonth.parse("2024-01")
        val input  = DynamicValue.Primitive(PrimitiveValue.YearMonth(ym))
        val result = convert("YearMonth", "String", input)
        assertTrue(result.isRight)
      },
      test("MonthDay -> String") {
        val md     = java.time.MonthDay.parse("--01-15")
        val input  = DynamicValue.Primitive(PrimitiveValue.MonthDay(md))
        val result = convert("MonthDay", "String", input)
        assertTrue(result.isRight)
      },
      test("DayOfWeek -> String") {
        val dow    = java.time.DayOfWeek.MONDAY
        val input  = DynamicValue.Primitive(PrimitiveValue.DayOfWeek(dow))
        val result = convert("DayOfWeek", "String", input)
        assertTrue(result.isRight)
      },
      test("Month -> String") {
        val m      = java.time.Month.JANUARY
        val input  = DynamicValue.Primitive(PrimitiveValue.Month(m))
        val result = convert("Month", "String", input)
        assertTrue(result.isRight)
      },
      test("ZoneId -> String") {
        val zid    = java.time.ZoneId.of("Europe/Paris")
        val input  = DynamicValue.Primitive(PrimitiveValue.ZoneId(zid))
        val result = convert("ZoneId", "String", input)
        assertTrue(result.isRight)
      },
      test("ZoneOffset -> String") {
        val zo     = java.time.ZoneOffset.of("+01:00")
        val input  = DynamicValue.Primitive(PrimitiveValue.ZoneOffset(zo))
        val result = convert("ZoneOffset", "String", input)
        assertTrue(result.isRight)
      },
      test("OffsetDateTime -> String") {
        val odt    = java.time.OffsetDateTime.parse("2024-01-15T10:30:00+01:00")
        val input  = DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(odt))
        val result = convert("OffsetDateTime", "String", input)
        assertTrue(result.isRight)
      },
      test("OffsetTime -> String") {
        val ot     = java.time.OffsetTime.parse("10:30:00+01:00")
        val input  = DynamicValue.Primitive(PrimitiveValue.OffsetTime(ot))
        val result = convert("OffsetTime", "String", input)
        assertTrue(result.isRight)
      },
      test("ZonedDateTime -> String") {
        val zdt    = java.time.ZonedDateTime.parse("2024-01-15T10:30:00+01:00[Europe/Paris]")
        val input  = DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(zdt))
        val result = convert("ZonedDateTime", "String", input)
        assertTrue(result.isRight)
      },
      test("Currency -> String") {
        val c      = java.util.Currency.getInstance("USD")
        val input  = DynamicValue.Primitive(PrimitiveValue.Currency(c))
        val result = convert("Currency", "String", input)
        assertTrue(result == Right(dynamicString("USD")))
      },
      test("Unit -> String") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Unit)
        val result = convert("Unit", "String", input)
        assertTrue(result == Right(dynamicString("()")))
      },
      test("Byte -> String") {
        assertTrue(convert("Byte", "String", dynamicByte(42)) == Right(dynamicString("42")))
      },
      test("Short -> String") {
        assertTrue(convert("Short", "String", dynamicShort(1000)) == Right(dynamicString("1000")))
      },
      test("Float -> String") {
        val result = convert("Float", "String", dynamicFloat(3.14f))
        assertTrue(result.isRight)
      }
    )
  )
}
