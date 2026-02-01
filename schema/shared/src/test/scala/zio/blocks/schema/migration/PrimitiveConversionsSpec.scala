package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.SchemaBaseSpec
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object PrimitiveConversionsSpec extends SchemaBaseSpec {

  // Helper to extract Left value safely (avoiding deprecated .left.get)
  private def leftValue(either: Either[String, Any]): String = either match {
    case Left(msg) => msg
    case Right(_)  => ""
  }

  // Helper constructors
  def dynamicByte(v: Byte): DynamicValue             = DynamicValue.Primitive(PrimitiveValue.Byte(v))
  def dynamicShort(v: Short): DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Short(v))
  def dynamicInt(v: Int): DynamicValue               = DynamicValue.Primitive(PrimitiveValue.Int(v))
  def dynamicLong(v: Long): DynamicValue             = DynamicValue.Primitive(PrimitiveValue.Long(v))
  def dynamicFloat(v: Float): DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Float(v))
  def dynamicDouble(v: Double): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Double(v))
  def dynamicString(v: String): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.String(v))
  def dynamicBoolean(v: Boolean): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Boolean(v))
  def dynamicChar(v: Char): DynamicValue             = DynamicValue.Primitive(PrimitiveValue.Char(v))
  def dynamicBigInt(v: BigInt): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.BigInt(v))
  def dynamicBigDecimal(v: BigDecimal): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigDecimal(v))

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveConversionsSpec")(
    suite("Identity conversions")(
      test("same type returns input unchanged") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "Int")
        assertTrue(result == Right(dynamicInt(42)))
      }
    ),
    suite("Numeric widening")(
      test("Byte -> Short") {
        val result = PrimitiveConversions.convert(dynamicByte(42), "Byte", "Short")
        assertTrue(result == Right(dynamicShort(42)))
      },
      test("Byte -> Int") {
        val result = PrimitiveConversions.convert(dynamicByte(127), "Byte", "Int")
        assertTrue(result == Right(dynamicInt(127)))
      },
      test("Byte -> Long") {
        val result = PrimitiveConversions.convert(dynamicByte(-128), "Byte", "Long")
        assertTrue(result == Right(dynamicLong(-128L)))
      },
      test("Short -> Int") {
        val result = PrimitiveConversions.convert(dynamicShort(32767), "Short", "Int")
        assertTrue(result == Right(dynamicInt(32767)))
      },
      test("Short -> Long") {
        val result = PrimitiveConversions.convert(dynamicShort(-32768), "Short", "Long")
        assertTrue(result == Right(dynamicLong(-32768L)))
      },
      test("Int -> Long") {
        val result = PrimitiveConversions.convert(dynamicInt(Int.MaxValue), "Int", "Long")
        assertTrue(result == Right(dynamicLong(Int.MaxValue.toLong)))
      },
      test("Int -> Double") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "Double")
        assertTrue(result == Right(dynamicDouble(42.0)))
      },
      test("Long -> Double") {
        val result = PrimitiveConversions.convert(dynamicLong(100L), "Long", "Double")
        assertTrue(result == Right(dynamicDouble(100.0)))
      },
      test("Float -> Double") {
        val result = PrimitiveConversions.convert(dynamicFloat(3.14f), "Float", "Double")
        assertTrue(result == Right(dynamicDouble(3.14f.toDouble)))
      },
      test("Int -> BigInt") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "BigInt")
        assertTrue(result == Right(dynamicBigInt(42)))
      },
      test("Long -> BigDecimal") {
        val result = PrimitiveConversions.convert(dynamicLong(100L), "Long", "BigDecimal")
        assertTrue(result == Right(dynamicBigDecimal(BigDecimal(100L))))
      }
    ),
    suite("Numeric narrowing")(
      test("Short -> Byte (in range)") {
        val result = PrimitiveConversions.convert(dynamicShort(100), "Short", "Byte")
        assertTrue(result == Right(dynamicByte(100)))
      },
      test("Short -> Byte (out of range)") {
        val result = PrimitiveConversions.convert(dynamicShort(200), "Short", "Byte")
        assertTrue(result.isLeft && leftValue(result).contains("out of Byte range"))
      },
      test("Int -> Short (in range)") {
        val result = PrimitiveConversions.convert(dynamicInt(1000), "Int", "Short")
        assertTrue(result == Right(dynamicShort(1000)))
      },
      test("Int -> Short (out of range)") {
        val result = PrimitiveConversions.convert(dynamicInt(40000), "Int", "Short")
        assertTrue(result.isLeft && leftValue(result).contains("out of Short range"))
      },
      test("Long -> Int (in range)") {
        val result = PrimitiveConversions.convert(dynamicLong(12345L), "Long", "Int")
        assertTrue(result == Right(dynamicInt(12345)))
      },
      test("Long -> Int (out of range)") {
        val result = PrimitiveConversions.convert(dynamicLong(Long.MaxValue), "Long", "Int")
        assertTrue(result.isLeft && leftValue(result).contains("out of Int range"))
      },
      test("Double -> Float") {
        val result = PrimitiveConversions.convert(dynamicDouble(3.14), "Double", "Float")
        assertTrue(result == Right(dynamicFloat(3.14.toFloat)))
      },
      test("BigInt -> Int (in range)") {
        val result = PrimitiveConversions.convert(dynamicBigInt(42), "BigInt", "Int")
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("BigInt -> Int (out of range)") {
        val result = PrimitiveConversions.convert(
          dynamicBigInt(BigInt("99999999999999999999")),
          "BigInt",
          "Int"
        )
        assertTrue(result.isLeft && leftValue(result).contains("out of Int range"))
      }
    ),
    suite("Any -> String")(
      test("Int -> String") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "String")
        assertTrue(result == Right(dynamicString("42")))
      },
      test("Long -> String") {
        val result = PrimitiveConversions.convert(dynamicLong(123456789L), "Long", "String")
        assertTrue(result == Right(dynamicString("123456789")))
      },
      test("Double -> String") {
        val result = PrimitiveConversions.convert(dynamicDouble(3.14159), "Double", "String")
        assertTrue(result == Right(dynamicString("3.14159")))
      },
      test("Boolean -> String") {
        val result = PrimitiveConversions.convert(dynamicBoolean(true), "Boolean", "String")
        assertTrue(result == Right(dynamicString("true")))
      }
    ),
    suite("String parsing")(
      test("String -> Int (valid)") {
        val result = PrimitiveConversions.convert(dynamicString("42"), "String", "Int")
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("String -> Int (invalid)") {
        val result = PrimitiveConversions.convert(dynamicString("not-a-number"), "String", "Int")
        assertTrue(result.isLeft && leftValue(result).contains("Cannot parse"))
      },
      test("String -> Int (with whitespace)") {
        val result = PrimitiveConversions.convert(dynamicString("  42  "), "String", "Int")
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("String -> Long (valid)") {
        val result = PrimitiveConversions.convert(dynamicString("123456789"), "String", "Long")
        assertTrue(result == Right(dynamicLong(123456789L)))
      },
      test("String -> Double (valid)") {
        val result = PrimitiveConversions.convert(dynamicString("3.14"), "String", "Double")
        assertTrue(result == Right(dynamicDouble(3.14)))
      },
      test("String -> Boolean (true)") {
        val result = PrimitiveConversions.convert(dynamicString("true"), "String", "Boolean")
        assertTrue(result == Right(dynamicBoolean(true)))
      },
      test("String -> Boolean (false)") {
        val result = PrimitiveConversions.convert(dynamicString("false"), "String", "Boolean")
        assertTrue(result == Right(dynamicBoolean(false)))
      },
      test("String -> Boolean (invalid)") {
        val result = PrimitiveConversions.convert(dynamicString("maybe"), "String", "Boolean")
        assertTrue(result.isLeft)
      },
      test("String -> BigInt") {
        val result = PrimitiveConversions.convert(dynamicString("123456789012345678901234567890"), "String", "BigInt")
        assertTrue(result == Right(dynamicBigInt(BigInt("123456789012345678901234567890"))))
      },
      test("String -> BigDecimal") {
        val result = PrimitiveConversions.convert(dynamicString("3.14159265358979"), "String", "BigDecimal")
        assertTrue(result == Right(dynamicBigDecimal(BigDecimal("3.14159265358979"))))
      },
      test("String -> Char (single character)") {
        val result = PrimitiveConversions.convert(dynamicString("A"), "String", "Char")
        assertTrue(result == Right(dynamicChar('A')))
      },
      test("String -> Char (multiple characters fails)") {
        val result = PrimitiveConversions.convert(dynamicString("AB"), "String", "Char")
        assertTrue(result.isLeft && leftValue(result).contains("must be exactly 1"))
      }
    ),
    suite("String -> UUID")(
      test("valid UUID") {
        // Use a static UUID to avoid SecureRandom dependency on Scala.js
        val uuid   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val result = PrimitiveConversions.convert(dynamicString(uuid.toString), "String", "UUID")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.UUID(uuid))))
      },
      test("invalid UUID") {
        val result = PrimitiveConversions.convert(dynamicString("not-a-uuid"), "String", "UUID")
        assertTrue(result.isLeft && leftValue(result).contains("Cannot parse"))
      }
    ),
    suite("String -> Temporal types")(
      test("String -> Instant") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val result  = PrimitiveConversions.convert(dynamicString("2024-01-15T10:30:00Z"), "String", "Instant")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Instant(instant))))
      },
      test("String -> LocalDate") {
        val date   = LocalDate.parse("2024-01-15")
        val result = PrimitiveConversions.convert(dynamicString("2024-01-15"), "String", "LocalDate")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.LocalDate(date))))
      },
      test("String -> LocalTime") {
        val time   = LocalTime.parse("10:30:00")
        val result = PrimitiveConversions.convert(dynamicString("10:30:00"), "String", "LocalTime")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.LocalTime(time))))
      },
      test("String -> LocalDateTime") {
        val dt     = LocalDateTime.parse("2024-01-15T10:30:00")
        val result = PrimitiveConversions.convert(dynamicString("2024-01-15T10:30:00"), "String", "LocalDateTime")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(dt))))
      },
      test("String -> Duration") {
        val duration = Duration.parse("PT1H30M")
        val result   = PrimitiveConversions.convert(dynamicString("PT1H30M"), "String", "Duration")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Duration(duration))))
      },
      test("String -> Period") {
        val period = Period.parse("P1Y2M3D")
        val result = PrimitiveConversions.convert(dynamicString("P1Y2M3D"), "String", "Period")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Period(period))))
      },
      test("String -> DayOfWeek") {
        val result = PrimitiveConversions.convert(dynamicString("MONDAY"), "String", "DayOfWeek")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))))
      },
      test("String -> Month") {
        val result = PrimitiveConversions.convert(dynamicString("JANUARY"), "String", "Month")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Month(Month.JANUARY))))
      },
      test("String -> Year") {
        val result = PrimitiveConversions.convert(dynamicString("2024"), "String", "Year")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2024)))))
      },
      test("String -> YearMonth") {
        val result = PrimitiveConversions.convert(dynamicString("2024-01"), "String", "YearMonth")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2024, 1)))))
      },
      test("String -> ZoneId") {
        val result = PrimitiveConversions.convert(dynamicString("America/New_York"), "String", "ZoneId")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("America/New_York")))))
      },
      test("String -> ZoneOffset") {
        val result = PrimitiveConversions.convert(dynamicString("+05:00"), "String", "ZoneOffset")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.of("+05:00")))))
      },
      test("invalid temporal string fails") {
        val result = PrimitiveConversions.convert(dynamicString("not-a-date"), "String", "LocalDate")
        assertTrue(result.isLeft && leftValue(result).contains("Cannot parse"))
      }
    ),
    suite("String -> Currency")(
      test("valid currency code") {
        val result = PrimitiveConversions.convert(dynamicString("USD"), "String", "Currency")
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD")))))
      },
      test("invalid currency code") {
        val result = PrimitiveConversions.convert(dynamicString("INVALID"), "String", "Currency")
        assertTrue(result.isLeft && leftValue(result).contains("Cannot parse"))
      }
    ),
    suite("Char <-> Int")(
      test("Char -> Int") {
        val result = PrimitiveConversions.convert(dynamicChar('A'), "Char", "Int")
        assertTrue(result == Right(dynamicInt(65)))
      },
      test("Int -> Char (in range)") {
        val result = PrimitiveConversions.convert(dynamicInt(65), "Int", "Char")
        assertTrue(result == Right(dynamicChar('A')))
      },
      test("Int -> Char (out of range)") {
        val result = PrimitiveConversions.convert(dynamicInt(-1), "Int", "Char")
        assertTrue(result.isLeft && leftValue(result).contains("out of Char range"))
      }
    ),
    suite("Boolean <-> Int")(
      test("Boolean true -> Int 1") {
        val result = PrimitiveConversions.convert(dynamicBoolean(true), "Boolean", "Int")
        assertTrue(result == Right(dynamicInt(1)))
      },
      test("Boolean false -> Int 0") {
        val result = PrimitiveConversions.convert(dynamicBoolean(false), "Boolean", "Int")
        assertTrue(result == Right(dynamicInt(0)))
      },
      test("Int 1 -> Boolean true") {
        val result = PrimitiveConversions.convert(dynamicInt(1), "Int", "Boolean")
        assertTrue(result == Right(dynamicBoolean(true)))
      },
      test("Int 0 -> Boolean false") {
        val result = PrimitiveConversions.convert(dynamicInt(0), "Int", "Boolean")
        assertTrue(result == Right(dynamicBoolean(false)))
      },
      test("Int non-zero -> Boolean true") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "Boolean")
        assertTrue(result == Right(dynamicBoolean(true)))
      }
    ),
    suite("Unsupported conversions")(
      test("unsupported conversion returns error") {
        val result = PrimitiveConversions.convert(dynamicInt(42), "Int", "UUID")
        assertTrue(result.isLeft && leftValue(result).contains("Unsupported conversion"))
      }
    ),
    suite("Helper methods")(
      test("isWideningConversion returns true for valid widening") {
        assertTrue(
          PrimitiveConversions.isWideningConversion("Byte", "Int") &&
            PrimitiveConversions.isWideningConversion("Int", "Long") &&
            PrimitiveConversions.isWideningConversion("Float", "Double")
        )
      },
      test("isWideningConversion returns false for narrowing") {
        assertTrue(
          !PrimitiveConversions.isWideningConversion("Int", "Byte") &&
            !PrimitiveConversions.isWideningConversion("Long", "Int")
        )
      },
      test("isNarrowingConversion returns true for valid narrowing") {
        assertTrue(
          PrimitiveConversions.isNarrowingConversion("Int", "Byte") &&
            PrimitiveConversions.isNarrowingConversion("Long", "Short")
        )
      },
      test("supportedConversionsFrom returns expected types") {
        val intConversions = PrimitiveConversions.supportedConversionsFrom("Int")
        assertTrue(
          intConversions.contains("Long") &&
            intConversions.contains("String") &&
            intConversions.contains("Char")
        )
      }
    )
  )
}
