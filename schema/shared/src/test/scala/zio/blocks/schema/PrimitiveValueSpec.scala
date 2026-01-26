package zio.blocks.schema

import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, assert}
import java.time._
import java.util.{Currency, UUID}

object PrimitiveValueSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveValueSpec")(
    suite("PrimitiveValue.Unit")(
      test("has correct primitiveType and typeIndex") {
        assert(PrimitiveValue.Unit.primitiveType)(equalTo(PrimitiveType.Unit)) &&
        assert(PrimitiveValue.Unit.typeIndex)(equalTo(0))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Unit.compare(PrimitiveValue.Unit))(equalTo(0)) &&
        assert(PrimitiveValue.Unit.compare(PrimitiveValue.Boolean(true)))(equalTo(-1)) &&
        assert(PrimitiveValue.Unit >= PrimitiveValue.Unit)(equalTo(true)) &&
        assert(PrimitiveValue.Unit > PrimitiveValue.Unit)(equalTo(false)) &&
        assert(PrimitiveValue.Unit <= PrimitiveValue.Unit)(equalTo(true)) &&
        assert(PrimitiveValue.Unit < PrimitiveValue.Unit)(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Boolean")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Boolean(true)
        assert(value.primitiveType)(equalTo(PrimitiveType.Boolean(Validation.None))) &&
        assert(value.typeIndex)(equalTo(1))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Boolean(true), PrimitiveValue.Boolean(false))
      }
    ),
    suite("PrimitiveValue.Byte")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Byte(1: Byte)
        assert(value.primitiveType)(equalTo(PrimitiveType.Byte(Validation.None))) &&
        assert(value.typeIndex)(equalTo(2))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Byte(1: Byte), PrimitiveValue.Byte(0: Byte))
      }
    ),
    suite("PrimitiveValue.Short")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Short(1: Short)
        assert(value.primitiveType)(equalTo(PrimitiveType.Short(Validation.None))) &&
        assert(value.typeIndex)(equalTo(3))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Short(1: Short), PrimitiveValue.Short(0: Short))
      }
    ),
    suite("PrimitiveValue.Int")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Int(1)
        assert(value.primitiveType)(equalTo(PrimitiveType.Int(Validation.None))) &&
        assert(value.typeIndex)(equalTo(4))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Int(1), PrimitiveValue.Int(0))
      }
    ),
    suite("PrimitiveValue.Long")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Long(1L)
        assert(value.primitiveType)(equalTo(PrimitiveType.Long(Validation.None))) &&
        assert(value.typeIndex)(equalTo(5))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Long(1L), PrimitiveValue.Long(0L))
      }
    ),
    suite("PrimitiveValue.Float")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Float(1.0f)
        assert(value.primitiveType)(equalTo(PrimitiveType.Float(Validation.None))) &&
        assert(value.typeIndex)(equalTo(6))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Float(1.0f), PrimitiveValue.Float(0.0f))
      }
    ),
    suite("PrimitiveValue.Double")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Double(1.0)
        assert(value.primitiveType)(equalTo(PrimitiveType.Double(Validation.None))) &&
        assert(value.typeIndex)(equalTo(7))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Double(1.0), PrimitiveValue.Double(0.0))
      }
    ),
    suite("PrimitiveValue.Char")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Char('a')
        assert(value.primitiveType)(equalTo(PrimitiveType.Char(Validation.None))) &&
        assert(value.typeIndex)(equalTo(8))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Char('b'), PrimitiveValue.Char('a'))
      }
    ),
    suite("PrimitiveValue.String")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.String("test")
        assert(value.primitiveType)(equalTo(PrimitiveType.String(Validation.None))) &&
        assert(value.typeIndex)(equalTo(9))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.String("test2"), PrimitiveValue.String("test1"))
      }
    ),
    suite("PrimitiveValue.BigInt")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigInt(BigInt(123))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigInt(Validation.None))) &&
        assert(value.typeIndex)(equalTo(10))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.BigInt(BigInt(123)), PrimitiveValue.BigInt(BigInt(0)))
      }
    ),
    suite("PrimitiveValue.BigDecimal")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigDecimal(BigDecimal(123.45))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigDecimal(Validation.None))) &&
        assert(value.typeIndex)(equalTo(11))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.BigDecimal(BigDecimal(123.45)), PrimitiveValue.BigDecimal(BigDecimal(0)))
      }
    ),
    suite("PrimitiveValue.DayOfWeek")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.DayOfWeek(Validation.None))) &&
        assert(value.typeIndex)(equalTo(12))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.DayOfWeek(DayOfWeek.TUESDAY), PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))
      }
    ),
    suite("PrimitiveValue.Duration")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Duration(Duration.ofSeconds(60))
        assert(value.primitiveType)(equalTo(PrimitiveType.Duration(Validation.None))) &&
        assert(value.typeIndex)(equalTo(13))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.Duration(Duration.ofSeconds(1)),
          PrimitiveValue.Duration(Duration.ofSeconds(0))
        )
      }
    ),
    suite("PrimitiveValue.Instant")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Instant(Instant.EPOCH)
        assert(value.primitiveType)(equalTo(PrimitiveType.Instant(Validation.None))) &&
        assert(value.typeIndex)(equalTo(14))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Instant(Instant.MAX), PrimitiveValue.Instant(Instant.MIN))
      }
    ),
    suite("PrimitiveValue.LocalDate")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDate(LocalDate.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDate(Validation.None))) &&
        assert(value.typeIndex)(equalTo(15))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.LocalDate(LocalDate.MAX), PrimitiveValue.LocalDate(LocalDate.MIN))
      }
    ),
    suite("PrimitiveValue.LocalDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDateTime(LocalDateTime.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(16))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.LocalDateTime(LocalDateTime.MAX),
          PrimitiveValue.LocalDateTime(LocalDateTime.MIN)
        )
      }
    ),
    suite("PrimitiveValue.LocalTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalTime(LocalTime.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(17))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.LocalTime(LocalTime.MAX), PrimitiveValue.LocalTime(LocalTime.MIN))
      }
    ),
    suite("PrimitiveValue.Month")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Month(Month.MAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.Month(Validation.None))) &&
        assert(value.typeIndex)(equalTo(18))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Month(Month.MAY), PrimitiveValue.Month(Month.MARCH))
      }
    ),
    suite("PrimitiveValue.MonthDay")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.MonthDay(Validation.None))) &&
        assert(value.typeIndex)(equalTo(19))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 2)),
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))
        )
      }
    ),
    suite("PrimitiveValue.OffsetDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(20))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX),
          PrimitiveValue.OffsetDateTime(OffsetDateTime.MIN)
        )
      }
    ),
    suite("PrimitiveValue.OffsetTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.OffsetTime(OffsetTime.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.OffsetTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(21))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.OffsetTime(OffsetTime.MAX), PrimitiveValue.OffsetTime(OffsetTime.MIN))
      }
    ),
    suite("PrimitiveValue.Period")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Period(Period.ofDays(1))
        assert(value.primitiveType)(equalTo(PrimitiveType.Period(Validation.None))) &&
        assert(value.typeIndex)(equalTo(22))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Period(Period.ofDays(1)), PrimitiveValue.Period(Period.ofDays(0)))
      }
    ),
    suite("PrimitiveValue.Year")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Year(Year.of(2025))
        assert(value.primitiveType)(equalTo(PrimitiveType.Year(Validation.None))) &&
        assert(value.typeIndex)(equalTo(23))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.Year(Year.of(2025)), PrimitiveValue.Year(Year.of(2024)))
      }
    ),
    suite("PrimitiveValue.YearMonth")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.YearMonth(YearMonth.of(2025, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.YearMonth(Validation.None))) &&
        assert(value.typeIndex)(equalTo(24))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.YearMonth(YearMonth.of(2025, 1)),
          PrimitiveValue.YearMonth(YearMonth.of(2024, 1))
        )
      }
    ),
    suite("PrimitiveValue.ZoneId")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZoneId(ZoneId.of("UTC"))
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneId(Validation.None))) &&
        assert(value.typeIndex)(equalTo(25))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.ZoneId(ZoneId.of("UTC")), PrimitiveValue.ZoneId(ZoneId.of("GMT")))
      }
    ),
    suite("PrimitiveValue.ZoneOffset")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZoneOffset(ZoneOffset.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneOffset(Validation.None))) &&
        assert(value.typeIndex)(equalTo(26))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.ZoneOffset(ZoneOffset.MIN), PrimitiveValue.ZoneOffset(ZoneOffset.MAX))
      }
    ),
    suite("PrimitiveValue.ZonedDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        assert(value.primitiveType)(equalTo(PrimitiveType.ZonedDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(27))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))),
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("America/Anguilla")))
        )
      }
    ),
    suite("PrimitiveValue.Currency")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Currency(Currency.getInstance("USD"))
        assert(value.primitiveType)(equalTo(PrimitiveType.Currency(Validation.None))) &&
        assert(value.typeIndex)(equalTo(28))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(
          PrimitiveValue.Currency(Currency.getInstance("USD")),
          PrimitiveValue.Currency(Currency.getInstance("GBP"))
        )
      }
    ),
    suite("PrimitiveValue.UUID")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.UUID(UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C"))
        assert(value.primitiveType)(equalTo(PrimitiveType.UUID(Validation.None))) &&
        assert(value.typeIndex)(equalTo(29))
      },
      test("has compatible compare and comparison operators") {
        checkComparisonOps(PrimitiveValue.UUID(new UUID(2L, 2L)), PrimitiveValue.UUID(new UUID(1L, 1L)))
      }
    ),
    suite("Schema roundtrip")(
      test("Unit roundtrips through DynamicValue") {
        val value: PrimitiveValue = PrimitiveValue.Unit
        val dyn                   = Schema[PrimitiveValue].toDynamicValue(value)
        val back                  = Schema[PrimitiveValue].fromDynamicValue(dyn)
        assert(back)(equalTo(Right(value)))
      },
      test("Int roundtrips") {
        val value: PrimitiveValue = PrimitiveValue.Int(42)
        val dyn                   = Schema[PrimitiveValue].toDynamicValue(value)
        val back                  = Schema[PrimitiveValue].fromDynamicValue(dyn)
        assert(back)(equalTo(Right(value)))
      },
      test("String roundtrips") {
        val value: PrimitiveValue = PrimitiveValue.String("hello")
        val dyn                   = Schema[PrimitiveValue].toDynamicValue(value)
        val back                  = Schema[PrimitiveValue].fromDynamicValue(dyn)
        assert(back)(equalTo(Right(value)))
      },
      test("Instant roundtrips") {
        val now                   = java.time.Instant.now()
        val value: PrimitiveValue = PrimitiveValue.Instant(now)
        val dyn                   = Schema[PrimitiveValue].toDynamicValue(value)
        val back                  = Schema[PrimitiveValue].fromDynamicValue(dyn)
        assert(back)(equalTo(Right(value)))
      },
      test("UUID roundtrips") {
        val uuid                  = java.util.UUID.randomUUID()
        val value: PrimitiveValue = PrimitiveValue.UUID(uuid)
        val dyn                   = Schema[PrimitiveValue].toDynamicValue(value)
        val back                  = Schema[PrimitiveValue].fromDynamicValue(dyn)
        assert(back)(equalTo(Right(value)))
      }
    )
  )

  private def checkComparisonOps(value1: PrimitiveValue, value2: PrimitiveValue) =
    assert(value1.compare(value1))(equalTo(0)) &&
      assert(value2.compare(value1))(isLessThan(0)) &&
      assert(value1.compare(value2))(isGreaterThan(0)) &&
      assert(value1.compare(PrimitiveValue.Unit))(equalTo(value1.typeIndex)) &&
      assert(value1 >= value1)(equalTo(true)) &&
      assert(value1 > value1)(equalTo(false)) &&
      assert(value1 <= value1)(equalTo(true)) &&
      assert(value1 < value1)(equalTo(false))
}
