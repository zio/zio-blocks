package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert}

object PrimitiveValueSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveValueSpec")(
    suite("PrimitiveValue.Unit")(
      test("has correct primitiveType") {
        assert(PrimitiveValue.Unit.primitiveType)(equalTo(PrimitiveType.Unit))
      }
    ),
    suite("PrimitiveValue.Boolean")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Boolean(true)
        assert(value.primitiveType)(equalTo(PrimitiveType.Boolean(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Byte")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Byte(1: Byte)
        assert(value.primitiveType)(equalTo(PrimitiveType.Byte(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Short")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Short(1: Short)
        assert(value.primitiveType)(equalTo(PrimitiveType.Short(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Int")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Int(1)
        assert(value.primitiveType)(equalTo(PrimitiveType.Int(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Long")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Long(1L)
        assert(value.primitiveType)(equalTo(PrimitiveType.Long(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Float")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Float(1.0f)
        assert(value.primitiveType)(equalTo(PrimitiveType.Float(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Double")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Double(1.0)
        assert(value.primitiveType)(equalTo(PrimitiveType.Double(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Char")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Char('a')
        assert(value.primitiveType)(equalTo(PrimitiveType.Char(Validation.None)))
      }
    ),
    suite("PrimitiveValue.String")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.String("test")
        assert(value.primitiveType)(equalTo(PrimitiveType.String(Validation.None)))
      }
    ),
    suite("PrimitiveValue.BigInt")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.BigInt(BigInt(123))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigInt(Validation.None)))
      }
    ),
    suite("PrimitiveValue.BigDecimal")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.BigDecimal(BigDecimal(123.45))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigDecimal(Validation.None)))
      }
    ),
    suite("PrimitiveValue.DayOfWeek")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.DayOfWeek(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Duration")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Duration(java.time.Duration.ofSeconds(60))
        assert(value.primitiveType)(equalTo(PrimitiveType.Duration(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Instant")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Instant(java.time.Instant.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.Instant(Validation.None)))
      }
    ),
    suite("PrimitiveValue.LocalDate")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.LocalDate(java.time.LocalDate.of(2023, 1, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDate(Validation.None)))
      }
    ),
    suite("PrimitiveValue.LocalDateTime")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.LocalDateTime(java.time.LocalDateTime.of(2023, 1, 1, 12, 0))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDateTime(Validation.None)))
      }
    ),
    suite("PrimitiveValue.LocalTime")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.LocalTime(java.time.LocalTime.of(12, 0))
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalTime(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Month")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Month(java.time.Month.JANUARY)
        assert(value.primitiveType)(equalTo(PrimitiveType.Month(Validation.None)))
      }
    ),
    suite("PrimitiveValue.MonthDay")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.MonthDay(java.time.MonthDay.of(java.time.Month.JANUARY, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.MonthDay(Validation.None)))
      }
    ),
    suite("PrimitiveValue.OffsetDateTime")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.OffsetDateTime(java.time.OffsetDateTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetDateTime(Validation.None)))
      }
    ),
    suite("PrimitiveValue.OffsetTime")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.OffsetTime(java.time.OffsetTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetTime(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Period")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Period(java.time.Period.ofDays(1))
        assert(value.primitiveType)(equalTo(PrimitiveType.Period(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Year")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Year(java.time.Year.of(2023))
        assert(value.primitiveType)(equalTo(PrimitiveType.Year(Validation.None)))
      }
    ),
    suite("PrimitiveValue.YearMonth")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.YearMonth(java.time.YearMonth.of(2023, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.YearMonth(Validation.None)))
      }
    ),
    suite("PrimitiveValue.ZoneId")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.ZoneId(java.time.ZoneId.of("UTC"))
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneId(Validation.None)))
      }
    ),
    suite("PrimitiveValue.ZoneOffset")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.ZoneOffset(java.time.ZoneOffset.UTC)
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneOffset(Validation.None)))
      }
    ),
    suite("PrimitiveValue.ZonedDateTime")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.now())
        assert(value.primitiveType)(equalTo(PrimitiveType.ZonedDateTime(Validation.None)))
      }
    ),
    suite("PrimitiveValue.Currency")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.Currency(java.util.Currency.getInstance("USD"))
        assert(value.primitiveType)(equalTo(PrimitiveType.Currency(Validation.None)))
      }
    ),
    suite("PrimitiveValue.UUID")(
      test("has correct primitiveType") {
        val value = PrimitiveValue.UUID(java.util.UUID.randomUUID())
        assert(value.primitiveType)(equalTo(PrimitiveType.UUID(Validation.None)))
      }
    )
  )
}
