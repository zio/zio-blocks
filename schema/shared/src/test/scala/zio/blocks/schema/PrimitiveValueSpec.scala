package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert}
import zio.test.TestAspect._
import java.time._
import java.util.{Currency, UUID}

object PrimitiveValueSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveValueSpec")(
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
        assert(PrimitiveValue.Boolean(true).compare(PrimitiveValue.Boolean(true)))(equalTo(0)) &&
        assert(PrimitiveValue.Boolean(false).compare(PrimitiveValue.Boolean(true)))(equalTo(-1)) &&
        assert(PrimitiveValue.Boolean(true).compare(PrimitiveValue.Boolean(false)))(equalTo(1)) &&
        assert(PrimitiveValue.Boolean(true).compare(PrimitiveValue.Unit))(equalTo(1)) &&
        assert(PrimitiveValue.Boolean(true) >= PrimitiveValue.Boolean(true))(equalTo(true)) &&
        assert(PrimitiveValue.Boolean(true) > PrimitiveValue.Boolean(true))(equalTo(false)) &&
        assert(PrimitiveValue.Boolean(true) <= PrimitiveValue.Boolean(true))(equalTo(true)) &&
        assert(PrimitiveValue.Boolean(true) < PrimitiveValue.Boolean(true))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Byte")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Byte(1: Byte)
        assert(value.primitiveType)(equalTo(PrimitiveType.Byte(Validation.None))) &&
        assert(value.typeIndex)(equalTo(2))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Byte(1: Byte).compare(PrimitiveValue.Byte(1: Byte)))(equalTo(0)) &&
        assert(PrimitiveValue.Byte(1: Byte).compare(PrimitiveValue.Byte(0: Byte)))(equalTo(1)) &&
        assert(PrimitiveValue.Byte(0: Byte).compare(PrimitiveValue.Byte(1: Byte)))(equalTo(-1)) &&
        assert(PrimitiveValue.Byte(1: Byte).compare(PrimitiveValue.Unit))(equalTo(2)) &&
        assert(PrimitiveValue.Byte(1: Byte) >= PrimitiveValue.Byte(1: Byte))(equalTo(true)) &&
        assert(PrimitiveValue.Byte(1: Byte) > PrimitiveValue.Byte(1: Byte))(equalTo(false)) &&
        assert(PrimitiveValue.Byte(1: Byte) <= PrimitiveValue.Byte(1: Byte))(equalTo(true)) &&
        assert(PrimitiveValue.Byte(1: Byte) < PrimitiveValue.Byte(1: Byte))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Short")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Short(1: Short)
        assert(value.primitiveType)(equalTo(PrimitiveType.Short(Validation.None))) &&
        assert(value.typeIndex)(equalTo(3))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Short(1: Short).compare(PrimitiveValue.Short(1: Short)))(equalTo(0)) &&
        assert(PrimitiveValue.Short(1: Short).compare(PrimitiveValue.Short(0: Short)))(equalTo(1)) &&
        assert(PrimitiveValue.Short(0: Short).compare(PrimitiveValue.Short(1: Short)))(equalTo(-1)) &&
        assert(PrimitiveValue.Short(1: Short).compare(PrimitiveValue.Unit))(equalTo(3)) &&
        assert(PrimitiveValue.Short(1: Short) >= PrimitiveValue.Short(1: Short))(equalTo(true)) &&
        assert(PrimitiveValue.Short(1: Short) > PrimitiveValue.Short(1: Short))(equalTo(false)) &&
        assert(PrimitiveValue.Short(1: Short) <= PrimitiveValue.Short(1: Short))(equalTo(true)) &&
        assert(PrimitiveValue.Short(1: Short) < PrimitiveValue.Short(1: Short))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Int")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Int(1)
        assert(value.primitiveType)(equalTo(PrimitiveType.Int(Validation.None))) &&
        assert(value.typeIndex)(equalTo(4))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Int(1).compare(PrimitiveValue.Int(1)))(equalTo(0)) &&
        assert(PrimitiveValue.Int(1).compare(PrimitiveValue.Int(0)))(equalTo(1)) &&
        assert(PrimitiveValue.Int(0).compare(PrimitiveValue.Int(1)))(equalTo(-1)) &&
        assert(PrimitiveValue.Int(1).compare(PrimitiveValue.Unit))(equalTo(4)) &&
        assert(PrimitiveValue.Int(1) >= PrimitiveValue.Int(1))(equalTo(true)) &&
        assert(PrimitiveValue.Int(1) > PrimitiveValue.Int(1))(equalTo(false)) &&
        assert(PrimitiveValue.Int(1) <= PrimitiveValue.Int(1))(equalTo(true)) &&
        assert(PrimitiveValue.Int(1) < PrimitiveValue.Int(1))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Long")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Long(1L)
        assert(value.primitiveType)(equalTo(PrimitiveType.Long(Validation.None))) &&
        assert(value.typeIndex)(equalTo(5))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Long(1L).compare(PrimitiveValue.Long(1L)))(equalTo(0)) &&
        assert(PrimitiveValue.Long(1L).compare(PrimitiveValue.Long(0L)))(equalTo(1)) &&
        assert(PrimitiveValue.Long(0L).compare(PrimitiveValue.Long(1L)))(equalTo(-1)) &&
        assert(PrimitiveValue.Long(1L).compare(PrimitiveValue.Unit))(equalTo(5)) &&
        assert(PrimitiveValue.Long(1L) >= PrimitiveValue.Long(1L))(equalTo(true)) &&
        assert(PrimitiveValue.Long(1L) > PrimitiveValue.Long(1L))(equalTo(false)) &&
        assert(PrimitiveValue.Long(1L) <= PrimitiveValue.Long(1L))(equalTo(true)) &&
        assert(PrimitiveValue.Long(1L) < PrimitiveValue.Long(1L))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Float")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Float(1.0f)
        assert(value.primitiveType)(equalTo(PrimitiveType.Float(Validation.None))) &&
        assert(value.typeIndex)(equalTo(6))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Float(1.0f).compare(PrimitiveValue.Float(1.0f)))(equalTo(0)) &&
        assert(PrimitiveValue.Float(1.0f).compare(PrimitiveValue.Float(0.0f)))(equalTo(1)) &&
        assert(PrimitiveValue.Float(0.0f).compare(PrimitiveValue.Float(1.0f)))(equalTo(-1)) &&
        assert(PrimitiveValue.Float(1.0f).compare(PrimitiveValue.Unit))(equalTo(6)) &&
        assert(PrimitiveValue.Float(1.0f) >= PrimitiveValue.Float(1.0f))(equalTo(true)) &&
        assert(PrimitiveValue.Float(1.0f) > PrimitiveValue.Float(1.0f))(equalTo(false)) &&
        assert(PrimitiveValue.Float(1.0f) <= PrimitiveValue.Float(1.0f))(equalTo(true)) &&
        assert(PrimitiveValue.Float(1.0f) < PrimitiveValue.Float(1.0f))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Double")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Double(1.0)
        assert(value.primitiveType)(equalTo(PrimitiveType.Double(Validation.None))) &&
        assert(value.typeIndex)(equalTo(7))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Double(1.0).compare(PrimitiveValue.Double(1.0)))(equalTo(0)) &&
        assert(PrimitiveValue.Double(1.0).compare(PrimitiveValue.Double(0.0)))(equalTo(1)) &&
        assert(PrimitiveValue.Double(0.0).compare(PrimitiveValue.Double(1.0)))(equalTo(-1)) &&
        assert(PrimitiveValue.Double(1.0).compare(PrimitiveValue.Unit))(equalTo(7)) &&
        assert(PrimitiveValue.Double(1.0) >= PrimitiveValue.Double(1.0))(equalTo(true)) &&
        assert(PrimitiveValue.Double(1.0) > PrimitiveValue.Double(1.0))(equalTo(false)) &&
        assert(PrimitiveValue.Double(1.0) <= PrimitiveValue.Double(1.0))(equalTo(true)) &&
        assert(PrimitiveValue.Double(1.0) < PrimitiveValue.Double(1.0))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Char")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Char('a')
        assert(value.primitiveType)(equalTo(PrimitiveType.Char(Validation.None))) &&
        assert(value.typeIndex)(equalTo(8))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Char('a').compare(PrimitiveValue.Char('a')))(equalTo(0)) &&
        assert(PrimitiveValue.Char('a').compare(PrimitiveValue.Char('b')))(equalTo(-1)) &&
        assert(PrimitiveValue.Char('b').compare(PrimitiveValue.Char('a')))(equalTo(1)) &&
        assert(PrimitiveValue.Char('a').compare(PrimitiveValue.Unit))(equalTo(8)) &&
        assert(PrimitiveValue.Char('a') >= PrimitiveValue.Char('a'))(equalTo(true)) &&
        assert(PrimitiveValue.Char('a') > PrimitiveValue.Char('a'))(equalTo(false)) &&
        assert(PrimitiveValue.Char('a') <= PrimitiveValue.Char('a'))(equalTo(true)) &&
        assert(PrimitiveValue.Char('a') < PrimitiveValue.Char('a'))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.String")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.String("test")
        assert(value.primitiveType)(equalTo(PrimitiveType.String(Validation.None))) &&
        assert(value.typeIndex)(equalTo(9))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.String("test").compare(PrimitiveValue.String("test")))(equalTo(0)) &&
        assert(PrimitiveValue.String("test").compare(PrimitiveValue.String("tests")))(equalTo(-1)) &&
        assert(PrimitiveValue.String("tests").compare(PrimitiveValue.String("test")))(equalTo(1)) &&
        assert(PrimitiveValue.String("test").compare(PrimitiveValue.Unit))(equalTo(9)) &&
        assert(PrimitiveValue.String("test") >= PrimitiveValue.String("test"))(equalTo(true)) &&
        assert(PrimitiveValue.String("test") > PrimitiveValue.String("test"))(equalTo(false)) &&
        assert(PrimitiveValue.String("test") <= PrimitiveValue.String("test"))(equalTo(true)) &&
        assert(PrimitiveValue.String("test") < PrimitiveValue.String("test"))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.BigInt")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigInt(BigInt(123))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigInt(Validation.None))) &&
        assert(value.typeIndex)(equalTo(10))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.BigInt(123).compare(PrimitiveValue.BigInt(123)))(equalTo(0)) &&
        assert(PrimitiveValue.BigInt(123).compare(PrimitiveValue.BigInt(0)))(equalTo(1)) &&
        assert(PrimitiveValue.BigInt(0).compare(PrimitiveValue.BigInt(123)))(equalTo(-1)) &&
        assert(PrimitiveValue.BigInt(123).compare(PrimitiveValue.Unit))(equalTo(10)) &&
        assert(PrimitiveValue.BigInt(123) >= PrimitiveValue.BigInt(123))(equalTo(true)) &&
        assert(PrimitiveValue.BigInt(123) > PrimitiveValue.BigInt(123))(equalTo(false)) &&
        assert(PrimitiveValue.BigInt(123) <= PrimitiveValue.BigInt(123))(equalTo(true)) &&
        assert(PrimitiveValue.BigInt(123) < PrimitiveValue.BigInt(123))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.BigDecimal")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.BigDecimal(BigDecimal(123.45))
        assert(value.primitiveType)(equalTo(PrimitiveType.BigDecimal(Validation.None))) &&
        assert(value.typeIndex)(equalTo(11))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.BigDecimal(123.45).compare(PrimitiveValue.BigDecimal(123.45)))(equalTo(0)) &&
        assert(PrimitiveValue.BigDecimal(123).compare(PrimitiveValue.BigDecimal(123.45)))(equalTo(-1)) &&
        assert(PrimitiveValue.BigDecimal(123.45).compare(PrimitiveValue.BigDecimal(123)))(equalTo(1)) &&
        assert(PrimitiveValue.BigDecimal(123.45).compare(PrimitiveValue.Unit))(equalTo(11)) &&
        assert(PrimitiveValue.BigDecimal(123.45) >= PrimitiveValue.BigDecimal(123.45))(equalTo(true)) &&
        assert(PrimitiveValue.BigDecimal(123.45) > PrimitiveValue.BigDecimal(123.45))(equalTo(false)) &&
        assert(PrimitiveValue.BigDecimal(123.45) <= PrimitiveValue.BigDecimal(123.45))(equalTo(true)) &&
        assert(PrimitiveValue.BigDecimal(123.45) < PrimitiveValue.BigDecimal(123.45))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.DayOfWeek")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.DayOfWeek(Validation.None))) &&
        assert(value.typeIndex)(equalTo(12))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY).compare(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)))(
          equalTo(0)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.TUESDAY).compare(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)))(
          equalTo(1)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY).compare(PrimitiveValue.DayOfWeek(DayOfWeek.TUESDAY)))(
          equalTo(-1)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY).compare(PrimitiveValue.Unit))(equalTo(12)) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY) >= PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY) > PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY) <= PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY) < PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Duration")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Duration(Duration.ofSeconds(60))
        assert(value.primitiveType)(equalTo(PrimitiveType.Duration(Validation.None))) &&
        assert(value.typeIndex)(equalTo(13))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue.Duration(Duration.ofSeconds(60)).compare(PrimitiveValue.Duration(Duration.ofSeconds(60)))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.Duration(Duration.ofSeconds(61)).compare(PrimitiveValue.Duration(Duration.ofSeconds(60)))
        )(equalTo(1)) &&
        assert(
          PrimitiveValue.Duration(Duration.ofSeconds(60)).compare(PrimitiveValue.Duration(Duration.ofSeconds(61)))
        )(equalTo(-1)) &&
        assert(PrimitiveValue.Duration(Duration.ofSeconds(60)).compare(PrimitiveValue.Unit))(equalTo(13)) &&
        assert(PrimitiveValue.Duration(Duration.ofSeconds(60)) >= PrimitiveValue.Duration(Duration.ofSeconds(60)))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.Duration(Duration.ofSeconds(60)) > PrimitiveValue.Duration(Duration.ofSeconds(60)))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.Duration(Duration.ofSeconds(60)) <= PrimitiveValue.Duration(Duration.ofSeconds(60)))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.Duration(Duration.ofSeconds(60)) < PrimitiveValue.Duration(Duration.ofSeconds(60)))(
          equalTo(false)
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
        assert(PrimitiveValue.Instant(Instant.EPOCH).compare(PrimitiveValue.Instant(Instant.EPOCH)))(equalTo(0)) &&
        assert(PrimitiveValue.Instant(Instant.MAX).compare(PrimitiveValue.Instant(Instant.EPOCH)))(equalTo(1)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH).compare(PrimitiveValue.Instant(Instant.MAX)))(equalTo(-1)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH).compare(PrimitiveValue.Unit))(equalTo(14)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH) >= PrimitiveValue.Instant(Instant.EPOCH))(equalTo(true)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH) > PrimitiveValue.Instant(Instant.EPOCH))(equalTo(false)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH) <= PrimitiveValue.Instant(Instant.EPOCH))(equalTo(true)) &&
        assert(PrimitiveValue.Instant(Instant.EPOCH) < PrimitiveValue.Instant(Instant.EPOCH))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.LocalDate")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDate(LocalDate.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDate(Validation.None))) &&
        assert(value.typeIndex)(equalTo(15))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.LocalDate(LocalDate.MAX).compare(PrimitiveValue.LocalDate(LocalDate.MAX)))(
          equalTo(0)
        ) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX).compare(PrimitiveValue.LocalDate(LocalDate.MIN)))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MIN).compare(PrimitiveValue.LocalDate(LocalDate.MAX)))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX).compare(PrimitiveValue.Unit))(equalTo(15)) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX) >= PrimitiveValue.LocalDate(LocalDate.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX) > PrimitiveValue.LocalDate(LocalDate.MAX))(equalTo(false)) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX) <= PrimitiveValue.LocalDate(LocalDate.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.LocalDate(LocalDate.MAX) < PrimitiveValue.LocalDate(LocalDate.MAX))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.LocalDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.LocalDateTime(LocalDateTime.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.LocalDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(16))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue.LocalDateTime(LocalDateTime.MAX).compare(PrimitiveValue.LocalDateTime(LocalDateTime.MAX))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.LocalDateTime(LocalDateTime.MIN).compare(PrimitiveValue.LocalDateTime(LocalDateTime.MAX))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue.LocalDateTime(LocalDateTime.MAX).compare(PrimitiveValue.LocalDateTime(LocalDateTime.MIN))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.LocalDateTime(LocalDateTime.MAX).compare(PrimitiveValue.Unit))(equalTo(16)) &&
        assert(PrimitiveValue.LocalDateTime(LocalDateTime.MAX) >= PrimitiveValue.LocalDateTime(LocalDateTime.MAX))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.LocalDateTime(LocalDateTime.MAX) > PrimitiveValue.LocalDateTime(LocalDateTime.MAX))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.LocalDateTime(LocalDateTime.MAX) <= PrimitiveValue.LocalDateTime(LocalDateTime.MAX))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.LocalDateTime(LocalDateTime.MAX) < PrimitiveValue.LocalDateTime(LocalDateTime.MAX))(
          equalTo(false)
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
        assert(PrimitiveValue.LocalTime(LocalTime.MAX).compare(PrimitiveValue.LocalTime(LocalTime.MAX)))(equalTo(0)) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MIN).compare(PrimitiveValue.LocalTime(LocalTime.MAX)))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX).compare(PrimitiveValue.LocalTime(LocalTime.MIN)))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX).compare(PrimitiveValue.Unit))(equalTo(17)) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX) >= PrimitiveValue.LocalTime(LocalTime.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX) > PrimitiveValue.LocalTime(LocalTime.MAX))(equalTo(false)) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX) <= PrimitiveValue.LocalTime(LocalTime.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.LocalTime(LocalTime.MAX) < PrimitiveValue.LocalTime(LocalTime.MAX))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Month")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Month(Month.MAY)
        assert(value.primitiveType)(equalTo(PrimitiveType.Month(Validation.None))) &&
        assert(value.typeIndex)(equalTo(18))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Month(Month.MAY).compare(PrimitiveValue.Month(Month.MAY)))(equalTo(0)) &&
        assert(PrimitiveValue.Month(Month.MARCH).compare(PrimitiveValue.Month(Month.MAY)))(isLessThan(0)) &&
        assert(PrimitiveValue.Month(Month.MAY).compare(PrimitiveValue.Month(Month.MARCH)))(isGreaterThan(0)) &&
        assert(PrimitiveValue.Month(Month.MAY).compare(PrimitiveValue.Unit))(equalTo(18)) &&
        assert(PrimitiveValue.Month(Month.MAY) >= PrimitiveValue.Month(Month.MAY))(equalTo(true)) &&
        assert(PrimitiveValue.Month(Month.MAY) > PrimitiveValue.Month(Month.MAY))(equalTo(false)) &&
        assert(PrimitiveValue.Month(Month.MAY) <= PrimitiveValue.Month(Month.MAY))(equalTo(true)) &&
        assert(PrimitiveValue.Month(Month.MAY) < PrimitiveValue.Month(Month.MAY))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.MonthDay")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.MonthDay(Validation.None))) &&
        assert(value.typeIndex)(equalTo(19))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)).compare(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)).compare(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 2)))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 2)).compare(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)).compare(PrimitiveValue.Unit))(equalTo(19)) &&
        assert(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)) >= PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))
        )(equalTo(true)) &&
        assert(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)) > PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)))(
          equalTo(false)
        ) &&
        assert(
          PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)) <= PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))
        )(equalTo(true)) &&
        assert(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)) < PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)))(
          equalTo(false)
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
        assert(
          PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX).compare(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.OffsetDateTime(OffsetDateTime.MIN).compare(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX).compare(PrimitiveValue.OffsetDateTime(OffsetDateTime.MIN))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX).compare(PrimitiveValue.Unit))(equalTo(20)) &&
        assert(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX) >= PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX) > PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX) <= PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX) < PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))(
          equalTo(false)
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
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX).compare(PrimitiveValue.OffsetTime(OffsetTime.MAX)))(
          equalTo(0)
        ) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MIN).compare(PrimitiveValue.OffsetTime(OffsetTime.MAX)))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX).compare(PrimitiveValue.OffsetTime(OffsetTime.MIN)))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX).compare(PrimitiveValue.Unit))(equalTo(21)) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX) >= PrimitiveValue.OffsetTime(OffsetTime.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX) > PrimitiveValue.OffsetTime(OffsetTime.MAX))(equalTo(false)) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX) <= PrimitiveValue.OffsetTime(OffsetTime.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.OffsetTime(OffsetTime.MAX) < PrimitiveValue.OffsetTime(OffsetTime.MAX))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Period")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Period(Period.ofDays(1))
        assert(value.primitiveType)(equalTo(PrimitiveType.Period(Validation.None))) &&
        assert(value.typeIndex)(equalTo(22))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Period(Period.ofDays(1)).compare(PrimitiveValue.Period(Period.ofDays(1))))(equalTo(0)) &&
        assert(PrimitiveValue.Period(Period.ofDays(0)).compare(PrimitiveValue.Period(Period.ofDays(1))))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)).compare(PrimitiveValue.Period(Period.ofDays(0))))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)).compare(PrimitiveValue.Unit))(equalTo(22)) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)) >= PrimitiveValue.Period(Period.ofDays(1)))(equalTo(true)) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)) > PrimitiveValue.Period(Period.ofDays(1)))(equalTo(false)) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)) <= PrimitiveValue.Period(Period.ofDays(1)))(equalTo(true)) &&
        assert(PrimitiveValue.Period(Period.ofDays(1)) < PrimitiveValue.Period(Period.ofDays(1)))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Year")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Year(Year.of(2025))
        assert(value.primitiveType)(equalTo(PrimitiveType.Year(Validation.None))) &&
        assert(value.typeIndex)(equalTo(23))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.Year(Year.of(2025)).compare(PrimitiveValue.Year(Year.of(2025))))(equalTo(0)) &&
        assert(PrimitiveValue.Year(Year.of(2024)).compare(PrimitiveValue.Year(Year.of(2025))))(isLessThan(0)) &&
        assert(PrimitiveValue.Year(Year.of(2025)).compare(PrimitiveValue.Year(Year.of(2024))))(isGreaterThan(0)) &&
        assert(PrimitiveValue.Year(Year.of(2025)).compare(PrimitiveValue.Unit))(equalTo(23)) &&
        assert(PrimitiveValue.Year(Year.of(2025)) >= PrimitiveValue.Year(Year.of(2025)))(equalTo(true)) &&
        assert(PrimitiveValue.Year(Year.of(2025)) > PrimitiveValue.Year(Year.of(2025)))(equalTo(false)) &&
        assert(PrimitiveValue.Year(Year.of(2025)) <= PrimitiveValue.Year(Year.of(2025)))(equalTo(true)) &&
        assert(PrimitiveValue.Year(Year.of(2025)) < PrimitiveValue.Year(Year.of(2025)))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.YearMonth")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.YearMonth(YearMonth.of(2025, 1))
        assert(value.primitiveType)(equalTo(PrimitiveType.YearMonth(Validation.None))) &&
        assert(value.typeIndex)(equalTo(24))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue.YearMonth(YearMonth.of(2025, 1)).compare(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.YearMonth(YearMonth.of(2024, 1)).compare(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue.YearMonth(YearMonth.of(2025, 1)).compare(PrimitiveValue.YearMonth(YearMonth.of(2024, 1)))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)).compare(PrimitiveValue.Unit))(equalTo(24)) &&
        assert(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)) >= PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)) > PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)) <= PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)) < PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))(
          equalTo(false)
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
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")).compare(PrimitiveValue.ZoneId(ZoneId.of("UTC"))))(equalTo(0)) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("GMT")).compare(PrimitiveValue.ZoneId(ZoneId.of("UTC"))))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")).compare(PrimitiveValue.ZoneId(ZoneId.of("GMT"))))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")).compare(PrimitiveValue.Unit))(equalTo(25)) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")) >= PrimitiveValue.ZoneId(ZoneId.of("UTC")))(equalTo(true)) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")) > PrimitiveValue.ZoneId(ZoneId.of("UTC")))(equalTo(false)) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")) <= PrimitiveValue.ZoneId(ZoneId.of("UTC")))(equalTo(true)) &&
        assert(PrimitiveValue.ZoneId(ZoneId.of("UTC")) < PrimitiveValue.ZoneId(ZoneId.of("UTC")))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.ZoneOffset")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZoneOffset(ZoneOffset.MAX)
        assert(value.primitiveType)(equalTo(PrimitiveType.ZoneOffset(Validation.None))) &&
        assert(value.typeIndex)(equalTo(26))
      },
      test("has compatible compare and comparison operators") {
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX).compare(PrimitiveValue.ZoneOffset(ZoneOffset.MAX)))(
          equalTo(0)
        ) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MIN).compare(PrimitiveValue.ZoneOffset(ZoneOffset.MAX)))(
          isGreaterThan(0)
        ) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX).compare(PrimitiveValue.ZoneOffset(ZoneOffset.MIN)))(
          isLessThan(0)
        ) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX).compare(PrimitiveValue.Unit))(equalTo(26)) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX) >= PrimitiveValue.ZoneOffset(ZoneOffset.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX) > PrimitiveValue.ZoneOffset(ZoneOffset.MAX))(equalTo(false)) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX) <= PrimitiveValue.ZoneOffset(ZoneOffset.MAX))(equalTo(true)) &&
        assert(PrimitiveValue.ZoneOffset(ZoneOffset.MAX) < PrimitiveValue.ZoneOffset(ZoneOffset.MAX))(equalTo(false))
      }
    ),
    suite("PrimitiveValue.ZonedDateTime")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        assert(value.primitiveType)(equalTo(PrimitiveType.ZonedDateTime(Validation.None))) &&
        assert(value.typeIndex)(equalTo(27))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
            .compare(PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("America/Anguilla")))
            .compare(PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
            .compare(
              PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("America/Anguilla")))
            )
        )(isGreaterThan(0)) &&
        assert(
          PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
            .compare(PrimitiveValue.Unit)
        )(equalTo(27)) &&
        assert(
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))) >= PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        )(equalTo(true)) &&
        assert(
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))) > PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        )(equalTo(false)) &&
        assert(
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))) <= PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        )(equalTo(true)) &&
        assert(
          PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))) < PrimitiveValue
            .ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
        )(equalTo(false))
      }
    ),
    suite("PrimitiveValue.Currency")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.Currency(Currency.getInstance("USD"))
        assert(value.primitiveType)(equalTo(PrimitiveType.Currency(Validation.None))) &&
        assert(value.typeIndex)(equalTo(28))
      } @@ jvmOnly,
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue
            .Currency(Currency.getInstance("USD"))
            .compare(PrimitiveValue.Currency(Currency.getInstance("USD")))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue
            .Currency(Currency.getInstance("GBP"))
            .compare(PrimitiveValue.Currency(Currency.getInstance("USD")))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue
            .Currency(Currency.getInstance("USD"))
            .compare(PrimitiveValue.Currency(Currency.getInstance("GBP")))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.Currency(Currency.getInstance("USD")).compare(PrimitiveValue.Unit))(equalTo(28)) &&
        assert(
          PrimitiveValue.Currency(Currency.getInstance("USD")) >= PrimitiveValue.Currency(Currency.getInstance("USD"))
        )(equalTo(true)) &&
        assert(
          PrimitiveValue.Currency(Currency.getInstance("USD")) > PrimitiveValue.Currency(Currency.getInstance("USD"))
        )(equalTo(false)) &&
        assert(
          PrimitiveValue.Currency(Currency.getInstance("USD")) <= PrimitiveValue.Currency(Currency.getInstance("USD"))
        )(equalTo(true)) &&
        assert(
          PrimitiveValue.Currency(Currency.getInstance("USD")) < PrimitiveValue.Currency(Currency.getInstance("USD"))
        )(equalTo(false))
      } @@ jvmOnly
    ),
    suite("PrimitiveValue.UUID")(
      test("has correct primitiveType and typeIndex") {
        val value = PrimitiveValue.UUID(UUID.fromString("DAD945B7-64F4-4265-BB56-4557325F701C"))
        assert(value.primitiveType)(equalTo(PrimitiveType.UUID(Validation.None))) &&
        assert(value.typeIndex)(equalTo(29))
      },
      test("has compatible compare and comparison operators") {
        assert(
          PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")).compare(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))
        )(equalTo(0)) &&
        assert(
          PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")).compare(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-1")))
        )(isLessThan(0)) &&
        assert(
          PrimitiveValue.UUID(UUID.fromString("0-0-0-0-1")).compare(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))
        )(isGreaterThan(0)) &&
        assert(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")).compare(PrimitiveValue.Unit))(equalTo(29)) &&
        assert(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")) >= PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")) > PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))(
          equalTo(false)
        ) &&
        assert(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")) <= PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))(
          equalTo(true)
        ) &&
        assert(PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")) < PrimitiveValue.UUID(UUID.fromString("0-0-0-0-0")))(
          equalTo(false)
        )
      } @@ jvmOnly
    )
  )
}
