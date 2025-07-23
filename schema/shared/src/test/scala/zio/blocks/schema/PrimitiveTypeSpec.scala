package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Validation.None
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert, assertTrue}
import zio.test.TestAspect._
import java.time.DayOfWeek

object PrimitiveTypeSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveTypeSpec")(
    suite("PrimitiveType.Unit")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Unit
        assertTrue(tpe.toDynamicValue(()) == DynamicValue.Primitive(PrimitiveValue.Unit)) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Unit)))(isRight(equalTo(()))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Unit")))
        )
      },
      test("Validation is set to None") {
        assert(PrimitiveType.Unit.validation)(equalTo(None))
      }
    ),
    suite("PrimitiveType.Byte")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Byte(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))))(
          isRight(equalTo(1: Byte))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Byte")))
        )
      }
    ),
    suite("PrimitiveType.Boolean")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Boolean(None)
        assertTrue(tpe.toDynamicValue(true) == DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))(isRight(equalTo(true))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Boolean")))
        )
      }
    ),
    suite("PrimitiveType.Short")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Short(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Short(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Short(1))))(
          isRight(equalTo(1: Short))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Short")))
        )
      }
    ),
    suite("PrimitiveType.Char")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Char(None)
        assertTrue(tpe.toDynamicValue('1') == DynamicValue.Primitive(PrimitiveValue.Char('1'))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Char('1'))))(isRight(equalTo('1'))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Char")))
        )
      }
    ),
    suite("PrimitiveType.Int")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Int(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Int(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(isRight(equalTo(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Int")))
        )
      }
    ),
    suite("PrimitiveType.Float")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Float(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Float(1.0f))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))))(isRight(equalTo(1.0f))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Float")))
        )
      }
    ),
    suite("PrimitiveType.Long")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Long(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Long(1L))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(isRight(equalTo(1L))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Long")))
        )
      }
    ),
    suite("PrimitiveType.Double")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Double(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Double(1.0))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Double(1.0))))(isRight(equalTo(1.0))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Double")))
        )
      }
    ),
    suite("PrimitiveType.String")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.String(None)
        assertTrue(tpe.toDynamicValue("WWW") == DynamicValue.Primitive(PrimitiveValue.String("WWW"))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("WWW"))))(isRight(equalTo("WWW"))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected String")))
        )
      }
    ),
    suite("PrimitiveType.BigInt")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.BigInt(None)
        assertTrue(tpe.toDynamicValue(BigInt(1)) == DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))))(
          isRight(equalTo(BigInt(1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected BigInt")))
        )
      }
    ),
    suite("PrimitiveType.BigDecimal")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.BigDecimal(None)
        assertTrue(
          tpe.toDynamicValue(BigDecimal(1.0)) == DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))))(
          isRight(equalTo(BigDecimal(1.0)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected BigDecimal")))
        )
      }
    ),
    suite("PrimitiveType.UUID")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.UUID(None)
        assert(tpe.toDynamicValue(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))(
          equalTo(
            DynamicValue.Primitive(
              PrimitiveValue.UUID(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            )
          )
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(
              PrimitiveValue.UUID(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            )
          )
        )(
          isRight(equalTo(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
        ) &&
        assert(
          tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("123e4567-e89b-12d3-a456-426614174000")))
        )(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected UUID")))
        )
      }
    ),
    suite("PrimitiveType.DayOfWeek")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.DayOfWeek(None)
        assert(tpe.toDynamicValue(DayOfWeek.MONDAY))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))))(
          isRight(equalTo(DayOfWeek.MONDAY))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected DayOfWeek")))
        )
      }
    ),
    suite("PrimitiveType.Duration")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Duration(None)
        assert(tpe.toDynamicValue(java.time.Duration.ofSeconds(1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ofSeconds(1))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ofSeconds(1)))))(
          isRight(equalTo(java.time.Duration.ofSeconds(1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Duration")))
        )
      }
    ),
    suite("PrimitiveType.Instant")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Instant(None)
        assert(tpe.toDynamicValue(java.time.Instant.ofEpochMilli(1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.ofEpochMilli(1))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.ofEpochMilli(1)))))(
          isRight(equalTo(java.time.Instant.ofEpochMilli(1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Instant")))
        )
      }
    ),
    suite("PrimitiveType.LocalDate")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.LocalDate(None)
        assert(tpe.toDynamicValue(java.time.LocalDate.of(2023, 1, 1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.of(2023, 1, 1))))
        ) &&
        assert(
          tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.of(2023, 1, 1))))
        )(
          isRight(equalTo(java.time.LocalDate.of(2023, 1, 1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected LocalDate")))
        )
      }
    ),
    suite("PrimitiveType.LocalDateTime")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.LocalDateTime(None)
        assert(tpe.toDynamicValue(java.time.LocalDateTime.of(2023, 1, 1, 1, 1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.of(2023, 1, 1, 1, 1))))
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.of(2023, 1, 1, 1, 1)))
          )
        )(
          isRight(equalTo(java.time.LocalDateTime.of(2023, 1, 1, 1, 1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected LocalDateTime")))
        )
      }
    ),
    suite("PrimitiveType.LocalTime")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.LocalTime(None)
        assert(tpe.toDynamicValue(java.time.LocalTime.of(1, 1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.of(1, 1))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.of(1, 1)))))(
          isRight(equalTo(java.time.LocalTime.of(1, 1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected LocalTime")))
        )
      }
    ),
    suite("PrimitiveType.Month")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Month(None)
        assert(tpe.toDynamicValue(java.time.Month.JANUARY))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY))))(
          isRight(equalTo(java.time.Month.JANUARY))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Month")))
        )
      }
    ),
    suite("PrimitiveType.MonthDay")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.MonthDay(None)
        assert(tpe.toDynamicValue(java.time.MonthDay.of(java.time.Month.JANUARY, 1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.MonthDay(java.time.MonthDay.of(java.time.Month.JANUARY, 1))))
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(PrimitiveValue.MonthDay(java.time.MonthDay.of(java.time.Month.JANUARY, 1)))
          )
        )(
          isRight(equalTo(java.time.MonthDay.of(java.time.Month.JANUARY, 1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected MonthDay")))
        )
      }
    ),
    suite("PrimitiveType.OffsetDateTime")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.OffsetDateTime(None)
        assert(tpe.toDynamicValue(java.time.OffsetDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)))(
          equalTo(
            DynamicValue.Primitive(
              PrimitiveValue.OffsetDateTime(
                java.time.OffsetDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)
              )
            )
          )
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(
              PrimitiveValue.OffsetDateTime(
                java.time.OffsetDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)
              )
            )
          )
        )(
          isRight(equalTo(java.time.OffsetDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected OffsetDateTime")))
        )
      }
    ),
    suite("PrimitiveType.OffsetTime")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.OffsetTime(None)
        assert(tpe.toDynamicValue(java.time.OffsetTime.of(1, 1, 0, 0, java.time.ZoneOffset.UTC)))(
          equalTo(
            DynamicValue.Primitive(
              PrimitiveValue.OffsetTime(java.time.OffsetTime.of(1, 1, 0, 0, java.time.ZoneOffset.UTC))
            )
          )
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(
              PrimitiveValue.OffsetTime(java.time.OffsetTime.of(1, 1, 0, 0, java.time.ZoneOffset.UTC))
            )
          )
        )(
          isRight(equalTo(java.time.OffsetTime.of(1, 1, 0, 0, java.time.ZoneOffset.UTC)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected OffsetTime")))
        )
      }
    ),
    suite("PrimitiveType.Period")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Period(None)
        assert(tpe.toDynamicValue(java.time.Period.ofDays(1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.ofDays(1))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.ofDays(1)))))(
          isRight(equalTo(java.time.Period.ofDays(1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Period")))
        )
      }
    ),
    suite("PrimitiveType.Year")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Year(None)
        assert(tpe.toDynamicValue(java.time.Year.of(2023)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.of(2023))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.of(2023)))))(
          isRight(equalTo(java.time.Year.of(2023)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Year")))
        )
      }
    ),
    suite("PrimitiveType.YearMonth")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.YearMonth(None)
        assert(tpe.toDynamicValue(java.time.YearMonth.of(2023, 1)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.of(2023, 1))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.of(2023, 1)))))(
          isRight(equalTo(java.time.YearMonth.of(2023, 1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected YearMonth")))
        )
      }
    ),
    suite("PrimitiveType.ZoneId")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.ZoneId(None)
        assert(tpe.toDynamicValue(java.time.ZoneId.of("UTC")))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of("UTC"))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of("UTC")))))(
          isRight(equalTo(java.time.ZoneId.of("UTC")))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected ZoneId")))
        )
      }
    ),
    suite("PrimitiveType.ZoneOffset")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.ZoneOffset(None)
        assert(tpe.toDynamicValue(java.time.ZoneOffset.UTC))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.UTC)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.UTC))))(
          isRight(equalTo(java.time.ZoneOffset.UTC))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected ZoneOffset")))
        )
      }
    ),
    suite("PrimitiveType.ZonedDateTime")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.ZonedDateTime(None)
        assert(tpe.toDynamicValue(java.time.ZonedDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)))(
          equalTo(
            DynamicValue.Primitive(
              PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC))
            )
          )
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(
              PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC))
            )
          )
        )(
          isRight(equalTo(java.time.ZonedDateTime.of(2023, 1, 1, 1, 1, 0, 0, java.time.ZoneOffset.UTC)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected ZonedDateTime")))
        )
      }
    ),
    suite("PrimitiveType.UUID")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.UUID(None)
        assert(tpe.toDynamicValue(java.util.UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C")))(
          equalTo(
            DynamicValue.Primitive(
              PrimitiveValue.UUID(java.util.UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C"))
            )
          )
        ) &&
        assert(
          tpe.fromDynamicValue(
            DynamicValue.Primitive(
              PrimitiveValue.UUID(java.util.UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C"))
            )
          )
        )(
          isRight(equalTo(java.util.UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C")))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected UUID")))
        )
      }
    ),
    suite("PrimitiveType.Currency")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe          = PrimitiveType.Currency(None)
        val testCurrency = java.util.Currency.getInstance("USD")
        assert(tpe.toDynamicValue(testCurrency))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Currency(testCurrency)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Currency(testCurrency))))(
          isRight(equalTo(testCurrency))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(Nil, "Expected Currency")))
        )
      }
    )
  )
}
