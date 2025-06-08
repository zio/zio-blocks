package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert}
import zio.test.TestAspect._

object PrimitiveValueSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveValueSpec")(
    suite("PrimitiveValue.Unit")(
      test("has correct primitiveType and typeIndex") {
        assert(PrimitiveValue.Unit.primitiveType)(equalTo(PrimitiveType.Unit)) &&
        assert(PrimitiveValue.Unit.typeIndex)(equalTo(0))
      }
    ),
    suite("PrimitiveValue.Boolean")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Boolean(true)
        assert(value.primitiveType)(equalTo(PrimitiveType.Boolean(Validation.None))) &&
        assert(value.typeIndex)(equalTo(1))
      }
    ),
    suite("PrimitiveValue.Byte")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Byte(1: Byte)
        assert(value.primitiveType)(equalTo(PrimitiveType.Byte(Validation.None))) &&
        assert(value.typeIndex)(equalTo(2))
      }
    ),
    suite("PrimitiveValue.Short")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Short(1: Short)
        assert(value.primitiveType)(equalTo(PrimitiveType.Short(Validation.None))) &&
        assert(value.typeIndex)(equalTo(3))
      }
    ),
    suite("PrimitiveValue.Int")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Int(1)
        assert(value.primitiveType)(equalTo(PrimitiveType.Int(Validation.None))) &&
        assert(value.typeIndex)(equalTo(4))
      }
    ),
    suite("PrimitiveValue.Long")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Long(1L)
        assert(value.primitiveType)(equalTo(PrimitiveType.Long(Validation.None))) &&
        assert(value.typeIndex)(equalTo(5))
      }
    ),
    suite("PrimitiveValue.Float")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Float(1.0f)
        assert(value.primitiveType)(equalTo(PrimitiveType.Float(Validation.None))) &&
        assert(value.typeIndex)(equalTo(6))
      }
    ),
    suite("PrimitiveValue.Double")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Double(1.0)
        assert(value.primitiveType)(equalTo(PrimitiveType.Double(Validation.None))) &&
        assert(value.typeIndex)(equalTo(7))
      }
    ),
    suite("PrimitiveValue.Char")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Char('a')
        assert(value.primitiveType)(equalTo(PrimitiveType.Char(Validation.None))) &&
        assert(value.typeIndex)(equalTo(8))
      }
    ),
    suite("PrimitiveValue.String")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.String("test")
        assert(value.primitiveType)(equalTo(PrimitiveType.String(Validation.None))) &&
        assert(value.typeIndex)(equalTo(9))
      }
    ),
    suite("PrimitiveValue.BigInt")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigInt(BigInt(123))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigInt(Validation.None))) &&
        assert(value.typeIndex)(equalTo(10))
      }
    ),
    suite("PrimitiveValue.BigDecimal")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigDecimal(BigDecimal(123.45))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigDecimal(Validation.None))) &&
        assert(value.typeIndex)(equalTo(11))
      }
    ),
    suite("PrimitiveValue.DayOfWeek")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.DayOfWeek(Validation.None))) &&
        assert(value.typeIndex)(equalTo(12))
      }
    ),
    suite("PrimitiveValue.Duration")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Duration(java.time.Duration.ofSeconds(60))
        assert(value.primitiveType)(equalTo(PrimitiveType.Duration(Validation.None))) &&
        assert(value.typeIndex)(equalTo(13))
      }
    ),
    suite("PrimitiveValue.Instant")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Instant(java.time.Instant.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.Instant(Validation.None))) &&
        assert(value.typeIndex)(equalTo(14))
      }
    ),
    suite("PrimitiveValue.LocalDate")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDate(java.time.LocalDate.of(2023, 1, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDate(Validation.None))) &&
        assert(value.typeIndex)(equalTo(15))
      }
    ),
    suite("PrimitiveValue.LocalDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDateTime(java.time.LocalDateTime.of(2023, 1, 1, 12, 0))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(16))
      }
    ),
    suite("PrimitiveValue.LocalTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalTime(java.time.LocalTime.of(12, 0))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(17))
      }
    ),
    suite("PrimitiveValue.Month")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Month(java.time.Month.JANUARY)
        assert(value.primitiveType)(equalTo(PrimitiveType.Month(Validation.None))) &&
        assert(value.typeIndex)(equalTo(18))
      }
    ),
    suite("PrimitiveValue.MonthDay")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.MonthDay(java.time.MonthDay.of(java.time.Month.JANUARY, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.MonthDay(Validation.None))) &&
        assert(value.typeIndex)(equalTo(19))
      }
    ),
    suite("PrimitiveValue.OffsetDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.OffsetDateTime(java.time.OffsetDateTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(20))
      }
    ),
    suite("PrimitiveValue.OffsetTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.OffsetTime(java.time.OffsetTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(21))
      }
    ),
    suite("PrimitiveValue.Period")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Period(java.time.Period.ofDays(1))
        assert(value.primitiveType)(equalTo(PrimitiveType.Period(Validation.None))) &&
        assert(value.typeIndex)(equalTo(22))
      }
    ),
    suite("PrimitiveValue.Year")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Year(java.time.Year.of(2023))
        assert(value.primitiveType)(equalTo(PrimitiveType.Year(Validation.None))) &&
        assert(value.typeIndex)(equalTo(23))
      }
    ),
    suite("PrimitiveValue.YearMonth")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.YearMonth(java.time.YearMonth.of(2023, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.YearMonth(Validation.None))) &&
        assert(value.typeIndex)(equalTo(24))
      }
    ),
    suite("PrimitiveValue.ZoneId")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZoneId(java.time.ZoneId.of("UTC"))
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneId(Validation.None))) &&
        assert(value.typeIndex)(equalTo(25))
      }
    ),
    suite("PrimitiveValue.ZoneOffset")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZoneOffset(java.time.ZoneOffset.UTC)
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneOffset(Validation.None))) &&
        assert(value.typeIndex)(equalTo(26))
      }
    ),
    suite("PrimitiveValue.ZonedDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.ZonedDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(27))
      }
    ),
    suite("PrimitiveValue.Currency")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Currency(java.util.Currency.getInstance("USD"))
        assert(value.primitiveType)(equalTo(PrimitiveType.Currency(Validation.None))) &&
        assert(value.typeIndex)(equalTo(28))
      } @@ jvmOnly
    ),
    suite("PrimitiveValue.UUID")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.UUID(java.util.UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C"))
        assert(value.primitiveType)(equalTo(PrimitiveType.UUID(Validation.None))) &&
        assert(value.typeIndex)(equalTo(29))
      }
    )
  )
}
