package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

/**
 * Tests for PrimitiveConversions to ensure comprehensive branch coverage.
 */
object PrimitiveConversionsSpec extends ZIOSpecDefault {

  import PrimitiveConversions._

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveConversionsSpec")(
    suite("Identity conversions")(
      test("same type identity Int") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(42)),
          "Int",
          "Int"
        )
        assertTrue(result.isRight)
      },
      test("same type identity String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("test")),
          "String",
          "String"
        )
        assertTrue(result.isRight)
      }
    ),
    suite("Numeric widening conversions")(
      test("Byte to Short") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "Short"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Short(10))))
      },
      test("Byte to Int") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(10))))
      },
      test("Byte to Long") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(10L))))
      },
      test("Byte to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "Float"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))))
      },
      test("Byte to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "Double"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(10.0))))
      },
      test("Byte to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(10)))))
      },
      test("Byte to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(10)),
          "Byte",
          "BigDecimal"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10)))))
      },
      test("Short to Int") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("Short to Long") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("Short to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "Float"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(100.0f))))
      },
      test("Short to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "Double"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(100.0))))
      },
      test("Short to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100)))))
      },
      test("Short to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(100)),
          "Short",
          "BigDecimal"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(100)))))
      },
      test("Int to Long") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(1000L))))
      },
      test("Int to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "Float"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(1000.0f))))
      },
      test("Int to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "Double"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(1000.0))))
      },
      test("Int to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))))
      },
      test("Int to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "BigDecimal"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1000)))))
      },
      test("Long to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(10000L)),
          "Long",
          "Float"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(10000.0f))))
      },
      test("Long to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(10000L)),
          "Long",
          "Double"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(10000.0))))
      },
      test("Long to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(10000L)),
          "Long",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(10000L)))))
      },
      test("Long to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(10000L)),
          "Long",
          "BigDecimal"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10000L)))))
      },
      test("Float to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(3.14f)),
          "Float",
          "Double"
        )
        assertTrue(result.isRight)
      },
      test("Float to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(3.14f)),
          "Float",
          "BigDecimal"
        )
        assertTrue(result.isRight)
      },
      test("Double to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(3.14159)),
          "Double",
          "BigDecimal"
        )
        assertTrue(result.isRight)
      },
      test("BigInt to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345))),
          "BigInt",
          "BigDecimal"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(12345)))))
      }
    ),
    suite("Numeric narrowing conversions")(
      test("Short to Byte in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(50)),
          "Short",
          "Byte"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Byte(50))))
      },
      test("Short to Byte out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(200)),
          "Short",
          "Byte"
        )
        assertTrue(result.isLeft)
      },
      test("Int to Byte in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(100)),
          "Int",
          "Byte"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Byte(100))))
      },
      test("Int to Byte out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(500)),
          "Int",
          "Byte"
        )
        assertTrue(result.isLeft)
      },
      test("Int to Short in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(1000)),
          "Int",
          "Short"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Short(1000))))
      },
      test("Int to Short out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(50000)),
          "Int",
          "Short"
        )
        assertTrue(result.isLeft)
      },
      test("Long to Byte in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(50L)),
          "Long",
          "Byte"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Byte(50))))
      },
      test("Long to Byte out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(500L)),
          "Long",
          "Byte"
        )
        assertTrue(result.isLeft)
      },
      test("Long to Short in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(1000L)),
          "Long",
          "Short"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Short(1000))))
      },
      test("Long to Short out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(50000L)),
          "Long",
          "Short"
        )
        assertTrue(result.isLeft)
      },
      test("Long to Int in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(100000L)),
          "Long",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100000))))
      },
      test("Long to Int out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(Long.MaxValue)),
          "Long",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("Float to Int in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(100.5f)),
          "Float",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("Float to Int out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(Float.MaxValue)),
          "Float",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("Float to Long") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(100.5f)),
          "Float",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("Double to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(3.14)),
          "Double",
          "Float"
        )
        assertTrue(result.isRight)
      },
      test("Double to Int in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(100.0)),
          "Double",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("Double to Int out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(Double.MaxValue)),
          "Double",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("Double to Long in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(100.0)),
          "Double",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("Double to Long out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(Double.MaxValue)),
          "Double",
          "Long"
        )
        assertTrue(result.isLeft)
      },
      test("BigInt to Int in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100))),
          "BigInt",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("BigInt to Int out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(Long.MaxValue) * BigInt(2))),
          "BigInt",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("BigInt to Long in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100000L))),
          "BigInt",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100000L))))
      },
      test("BigInt to Long out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(Long.MaxValue) * BigInt(2))),
          "BigInt",
          "Long"
        )
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Int in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(100))),
          "BigDecimal",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("BigDecimal to Int out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(Long.MaxValue) * 2)),
          "BigDecimal",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Long in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(100000L))),
          "BigDecimal",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100000L))))
      },
      test("BigDecimal to Long out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(Long.MaxValue) * 2)),
          "BigDecimal",
          "Long"
        )
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14159))),
          "BigDecimal",
          "Double"
        )
        assertTrue(result.isRight)
      },
      test("BigDecimal to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(12345))),
          "BigDecimal",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))))
      }
    ),
    suite("String conversions")(
      test("Int to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(42)),
          "Int",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Boolean to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
          "Boolean",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("true"))))
      },
      test("String to Byte") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("42")),
          "String",
          "Byte"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Byte(42))))
      },
      test("String to Byte invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Byte"
        )
        assertTrue(result.isLeft)
      },
      test("String to Short") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("1000")),
          "String",
          "Short"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Short(1000))))
      },
      test("String to Short invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Short"
        )
        assertTrue(result.isLeft)
      },
      test("String to Int") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("12345")),
          "String",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(12345))))
      },
      test("String to Int invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Int"
        )
        assertTrue(result.isLeft)
      },
      test("String to Long") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("123456789")),
          "String",
          "Long"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(123456789L))))
      },
      test("String to Long invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Long"
        )
        assertTrue(result.isLeft)
      },
      test("String to Float") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("3.14")),
          "String",
          "Float"
        )
        assertTrue(result.isRight)
      },
      test("String to Float invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Float"
        )
        assertTrue(result.isLeft)
      },
      test("String to Double") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("3.14159")),
          "String",
          "Double"
        )
        assertTrue(result.isRight)
      },
      test("String to Double invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Double"
        )
        assertTrue(result.isLeft)
      },
      test("String to Boolean true") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("true")),
          "String",
          "Boolean"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("String to Boolean false") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("false")),
          "String",
          "Boolean"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("String to Boolean invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Boolean"
        )
        assertTrue(result.isLeft)
      },
      test("String to BigInt") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("12345")),
          "String",
          "BigInt"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))))
      },
      test("String to BigInt invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "BigInt"
        )
        assertTrue(result.isLeft)
      },
      test("String to BigDecimal") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("3.14159")),
          "String",
          "BigDecimal"
        )
        assertTrue(result.isRight)
      },
      test("String to BigDecimal invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "BigDecimal"
        )
        assertTrue(result.isLeft)
      },
      test("String to Char") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("A")),
          "String",
          "Char"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Char('A'))))
      },
      test("String to Char invalid (too long)") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("AB")),
          "String",
          "Char"
        )
        assertTrue(result.isLeft)
      },
      test("String to UUID") {
        val uuid   = java.util.UUID.randomUUID()
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String(uuid.toString)),
          "String",
          "UUID"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.UUID(uuid))))
      },
      test("String to UUID invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "UUID"
        )
        assertTrue(result.isLeft)
      }
    ),
    suite("Temporal conversions")(
      test("String to Instant") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01-01T12:00:00Z")),
          "String",
          "Instant"
        )
        assertTrue(result.isRight)
      },
      test("String to Instant invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Instant"
        )
        assertTrue(result.isLeft)
      },
      test("String to LocalDate") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01-01")),
          "String",
          "LocalDate"
        )
        assertTrue(result.isRight)
      },
      test("String to LocalDate invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "LocalDate"
        )
        assertTrue(result.isLeft)
      },
      test("String to LocalTime") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("12:30:00")),
          "String",
          "LocalTime"
        )
        assertTrue(result.isRight)
      },
      test("String to LocalTime invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "LocalTime"
        )
        assertTrue(result.isLeft)
      },
      test("String to LocalDateTime") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01-01T12:30:00")),
          "String",
          "LocalDateTime"
        )
        assertTrue(result.isRight)
      },
      test("String to LocalDateTime invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "LocalDateTime"
        )
        assertTrue(result.isLeft)
      },
      test("String to OffsetDateTime") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01-01T12:30:00+05:00")),
          "String",
          "OffsetDateTime"
        )
        assertTrue(result.isRight)
      },
      test("String to OffsetDateTime invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "OffsetDateTime"
        )
        assertTrue(result.isLeft)
      },
      test("String to OffsetTime") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("12:30:00+05:00")),
          "String",
          "OffsetTime"
        )
        assertTrue(result.isRight)
      },
      test("String to OffsetTime invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "OffsetTime"
        )
        assertTrue(result.isLeft)
      },
      test("String to ZonedDateTime") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01-01T12:30:00+05:00[Asia/Karachi]")),
          "String",
          "ZonedDateTime"
        )
        assertTrue(result.isRight)
      },
      test("String to ZonedDateTime invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "ZonedDateTime"
        )
        assertTrue(result.isLeft)
      },
      test("String to Duration") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("PT1H30M")),
          "String",
          "Duration"
        )
        assertTrue(result.isRight)
      },
      test("String to Duration invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Duration"
        )
        assertTrue(result.isLeft)
      },
      test("String to Period") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("P1Y2M3D")),
          "String",
          "Period"
        )
        assertTrue(result.isRight)
      },
      test("String to Period invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Period"
        )
        assertTrue(result.isLeft)
      },
      test("String to Year") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024")),
          "String",
          "Year"
        )
        assertTrue(result.isRight)
      },
      test("String to Year invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Year"
        )
        assertTrue(result.isLeft)
      },
      test("String to YearMonth") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("2024-01")),
          "String",
          "YearMonth"
        )
        assertTrue(result.isRight)
      },
      test("String to YearMonth invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "YearMonth"
        )
        assertTrue(result.isLeft)
      },
      test("String to MonthDay") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("--01-15")),
          "String",
          "MonthDay"
        )
        assertTrue(result.isRight)
      },
      test("String to MonthDay invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "MonthDay"
        )
        assertTrue(result.isLeft)
      },
      test("String to DayOfWeek") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("MONDAY")),
          "String",
          "DayOfWeek"
        )
        assertTrue(result.isRight)
      },
      test("String to DayOfWeek invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "DayOfWeek"
        )
        assertTrue(result.isLeft)
      },
      test("String to Month") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("JANUARY")),
          "String",
          "Month"
        )
        assertTrue(result.isRight)
      },
      test("String to Month invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Month"
        )
        assertTrue(result.isLeft)
      },
      test("String to ZoneId") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("America/New_York")),
          "String",
          "ZoneId"
        )
        assertTrue(result.isRight)
      },
      test("String to ZoneId invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("Invalid/Zone")),
          "String",
          "ZoneId"
        )
        assertTrue(result.isLeft)
      },
      test("String to ZoneOffset") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("+05:00")),
          "String",
          "ZoneOffset"
        )
        assertTrue(result.isRight)
      },
      test("String to ZoneOffset invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "ZoneOffset"
        )
        assertTrue(result.isLeft)
      },
      test("String to Currency") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("USD")),
          "String",
          "Currency"
        )
        assertTrue(result.isRight)
      },
      test("String to Currency invalid") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.String("invalid")),
          "String",
          "Currency"
        )
        assertTrue(result.isLeft)
      }
    ),
    suite("Char and Boolean conversions")(
      test("Char to Int") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Char('A')),
          "Char",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(65))))
      },
      test("Int to Char in range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(65)),
          "Int",
          "Char"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Char('A'))))
      },
      test("Int to Char out of range") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(-1)),
          "Int",
          "Char"
        )
        assertTrue(result.isLeft)
      },
      test("Boolean to Int true") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
          "Boolean",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      },
      test("Boolean to Int false") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Boolean(false)),
          "Boolean",
          "Int"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      },
      test("Int to Boolean non-zero") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(42)),
          "Int",
          "Boolean"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Int to Boolean zero") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Int(0)),
          "Int",
          "Boolean"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      }
    ),
    suite("Unsupported conversions")(
      test("unsupported conversion returns Left") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
          "Boolean",
          "Double"
        )
        assertTrue(result.isLeft)
      }
    ),
    suite("Helper methods")(
      test("isWideningConversion Byte to Int is true") {
        assertTrue(isWideningConversion("Byte", "Int"))
      },
      test("isWideningConversion Int to Byte is false") {
        assertTrue(!isWideningConversion("Int", "Byte"))
      },
      test("isNarrowingConversion Int to Byte is true") {
        assertTrue(isNarrowingConversion("Int", "Byte"))
      },
      test("isNarrowingConversion Byte to Int is false") {
        assertTrue(!isNarrowingConversion("Byte", "Int"))
      },
      test("supportedConversionsFrom Byte") {
        val conversions = supportedConversionsFrom("Byte")
        assertTrue(conversions.contains("Int") && conversions.contains("Long") && conversions.contains("String"))
      },
      test("supportedConversionsFrom String") {
        val conversions = supportedConversionsFrom("String")
        assertTrue(conversions.contains("Int") && conversions.contains("UUID") && conversions.contains("Instant"))
      },
      test("supportedConversionsFrom unknown type") {
        val conversions = supportedConversionsFrom("Unknown")
        assertTrue(conversions == Set("String"))
      },
      test("supportedConversionsFrom Short") {
        val conversions = supportedConversionsFrom("Short")
        assertTrue(conversions.contains("Int") && conversions.contains("Byte"))
      },
      test("supportedConversionsFrom Long") {
        val conversions = supportedConversionsFrom("Long")
        assertTrue(conversions.contains("Int") && conversions.contains("Short"))
      },
      test("supportedConversionsFrom Float") {
        val conversions = supportedConversionsFrom("Float")
        assertTrue(conversions.contains("Double"))
      },
      test("supportedConversionsFrom Double") {
        val conversions = supportedConversionsFrom("Double")
        assertTrue(conversions.contains("Float") && conversions.contains("Int"))
      },
      test("supportedConversionsFrom BigInt") {
        val conversions = supportedConversionsFrom("BigInt")
        assertTrue(conversions.contains("Int") && conversions.contains("Long"))
      },
      test("supportedConversionsFrom BigDecimal") {
        val conversions = supportedConversionsFrom("BigDecimal")
        assertTrue(conversions.contains("Int") && conversions.contains("Long") && conversions.contains("Double"))
      },
      test("supportedConversionsFrom Char") {
        val conversions = supportedConversionsFrom("Char")
        assertTrue(conversions.contains("Int") && conversions.contains("String"))
      },
      test("supportedConversionsFrom Boolean") {
        val conversions = supportedConversionsFrom("Boolean")
        assertTrue(conversions.contains("Int") && conversions.contains("String"))
      }
    ),
    suite("Primitive to String via any-to-String fallback")(
      test("Unit to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Unit),
          "Unit",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("()"))))
      },
      test("Byte to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Byte(42)),
          "Byte",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Short to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Short(1000)),
          "Short",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("1000"))))
      },
      test("Long to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Long(123456789L)),
          "Long",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123456789"))))
      },
      test("Float to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Float(3.14f)),
          "Float",
          "String"
        )
        assertTrue(result.isRight)
      },
      test("Double to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Double(3.14159)),
          "Double",
          "String"
        )
        assertTrue(result.isRight)
      },
      test("Char to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Char('X')),
          "Char",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("X"))))
      },
      test("BigInt to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("12345678901234567890"))),
          "BigInt",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("12345678901234567890"))))
      },
      test("BigDecimal to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("3.141592653589793"))),
          "BigDecimal",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("3.141592653589793"))))
      },
      test("DayOfWeek to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY)),
          "DayOfWeek",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("MONDAY"))))
      },
      test("Duration to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ofHours(1))),
          "Duration",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("PT1H"))))
      },
      test("Instant to String") {
        val instant = java.time.Instant.parse("2024-01-01T12:00:00Z")
        val result  = convert(
          DynamicValue.Primitive(PrimitiveValue.Instant(instant)),
          "Instant",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2024-01-01T12:00:00Z"))))
      },
      test("LocalDate to String") {
        val ld     = java.time.LocalDate.of(2024, 1, 15)
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.LocalDate(ld)),
          "LocalDate",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2024-01-15"))))
      },
      test("LocalDateTime to String") {
        val ldt    = java.time.LocalDateTime.of(2024, 1, 15, 12, 30, 0)
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.LocalDateTime(ldt)),
          "LocalDateTime",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2024-01-15T12:30"))))
      },
      test("LocalTime to String") {
        val lt     = java.time.LocalTime.of(12, 30, 0)
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.LocalTime(lt)),
          "LocalTime",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("12:30"))))
      },
      test("Month to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY)),
          "Month",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("JANUARY"))))
      },
      test("MonthDay to String") {
        val md     = java.time.MonthDay.of(1, 15)
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.MonthDay(md)),
          "MonthDay",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("--01-15"))))
      },
      test("OffsetDateTime to String") {
        val odt    = java.time.OffsetDateTime.of(2024, 1, 15, 12, 30, 0, 0, java.time.ZoneOffset.ofHours(5))
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(odt)),
          "OffsetDateTime",
          "String"
        )
        assertTrue(result.isRight)
      },
      test("OffsetTime to String") {
        val ot     = java.time.OffsetTime.of(12, 30, 0, 0, java.time.ZoneOffset.ofHours(5))
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.OffsetTime(ot)),
          "OffsetTime",
          "String"
        )
        assertTrue(result.isRight)
      },
      test("Period to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.of(1, 2, 3))),
          "Period",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("P1Y2M3D"))))
      },
      test("Year to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.of(2024))),
          "Year",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2024"))))
      },
      test("YearMonth to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.of(2024, 1))),
          "YearMonth",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2024-01"))))
      },
      test("ZoneId to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of("America/New_York"))),
          "ZoneId",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("America/New_York"))))
      },
      test("ZoneOffset to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.ofHours(5))),
          "ZoneOffset",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("+05:00"))))
      },
      test("ZonedDateTime to String") {
        val zdt    = java.time.ZonedDateTime.of(2024, 1, 15, 12, 30, 0, 0, java.time.ZoneId.of("America/New_York"))
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(zdt)),
          "ZonedDateTime",
          "String"
        )
        assertTrue(result.isRight)
      },
      test("Currency to String") {
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.Currency(java.util.Currency.getInstance("USD"))),
          "Currency",
          "String"
        )
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("USD"))))
      },
      test("UUID to String") {
        val uuid   = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val result = convert(
          DynamicValue.Primitive(PrimitiveValue.UUID(uuid)),
          "UUID",
          "String"
        )
        assertTrue(
          result == Right(DynamicValue.Primitive(PrimitiveValue.String("123e4567-e89b-12d3-a456-426614174000")))
        )
      }
    )
  )
}
