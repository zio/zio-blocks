package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.blocks.docs.Doc
import zio.test._

import java.nio.CharBuffer

object CsvPrimitiveCodecSpec extends SchemaBaseSpec {

  private def codec[A](pt: PrimitiveType[A]): CsvCodec[A] =
    CsvCodecDeriver
      .derivePrimitive(
        pt,
        pt.typeId,
        pt.binding,
        Doc.empty,
        Seq.empty,
        None,
        Seq.empty
      )
      .force

  private def roundTrip[A](c: CsvCodec[A], value: A): Either[SchemaError, A] = {
    val buf = CharBuffer.allocate(1024)
    c.encode(value, buf)
    buf.flip()
    c.decode(buf)
  }

  private def decodeStr[A](c: CsvCodec[A], str: String): Either[SchemaError, A] =
    c.decode(CharBuffer.wrap(str))

  def spec = suite("CsvPrimitiveCodecSpec")(
    suite("Unit")(
      test("round-trip") {
        val c      = codec(PrimitiveType.Unit)
        val result = roundTrip(c, ())
        assertTrue(result == Right(()))
      }
    ),
    suite("Boolean")(
      test("round-trip true") {
        val c = codec(PrimitiveType.Boolean(Validation.None))
        assertTrue(roundTrip(c, true) == Right(true))
      },
      test("round-trip false") {
        val c = codec(PrimitiveType.Boolean(Validation.None))
        assertTrue(roundTrip(c, false) == Right(false))
      },
      test("case-insensitive decode") {
        val c = codec(PrimitiveType.Boolean(Validation.None))
        assertTrue(
          decodeStr(c, "TRUE") == Right(true) &&
            decodeStr(c, "False") == Right(false)
        )
      },
      test("invalid input returns error") {
        val c = codec(PrimitiveType.Boolean(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("Byte")(
      test("round-trip") {
        val c = codec(PrimitiveType.Byte(Validation.None))
        assertTrue(roundTrip(c, 42.toByte) == Right(42.toByte))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Byte(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("Short")(
      test("round-trip") {
        val c = codec(PrimitiveType.Short(Validation.None))
        assertTrue(roundTrip(c, 1234.toShort) == Right(1234.toShort))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Short(Validation.None))
        assertTrue(decodeStr(c, "xyz").isLeft)
      }
    ),
    suite("Int")(
      test("round-trip") {
        val c = codec(PrimitiveType.Int(Validation.None))
        assertTrue(roundTrip(c, 42) == Right(42))
      },
      test("negative value") {
        val c = codec(PrimitiveType.Int(Validation.None))
        assertTrue(roundTrip(c, -100) == Right(-100))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Int(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("Long")(
      test("round-trip") {
        val c = codec(PrimitiveType.Long(Validation.None))
        assertTrue(roundTrip(c, 9876543210L) == Right(9876543210L))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Long(Validation.None))
        assertTrue(decodeStr(c, "nope").isLeft)
      }
    ),
    suite("Float")(
      test("round-trip") {
        val c = codec(PrimitiveType.Float(Validation.None))
        assertTrue(roundTrip(c, 3.14f) == Right(3.14f))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Float(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("Double")(
      test("round-trip") {
        val c = codec(PrimitiveType.Double(Validation.None))
        assertTrue(roundTrip(c, 2.718281828) == Right(2.718281828))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Double(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("Char")(
      test("round-trip") {
        val c = codec(PrimitiveType.Char(Validation.None))
        assertTrue(roundTrip(c, 'Z') == Right('Z'))
      },
      test("multi-char string fails") {
        val c = codec(PrimitiveType.Char(Validation.None))
        assertTrue(decodeStr(c, "ab").isLeft)
      }
    ),
    suite("String")(
      test("round-trip") {
        val c = codec(PrimitiveType.String(Validation.None))
        assertTrue(roundTrip(c, "hello world") == Right("hello world"))
      },
      test("empty string") {
        val c = codec(PrimitiveType.String(Validation.None))
        assertTrue(roundTrip(c, "") == Right(""))
      }
    ),
    suite("BigInt")(
      test("round-trip") {
        val c = codec(PrimitiveType.BigInt(Validation.None))
        assertTrue(
          roundTrip(c, BigInt("123456789012345678901234567890")) == Right(
            BigInt("123456789012345678901234567890")
          )
        )
      },
      test("invalid input") {
        val c = codec(PrimitiveType.BigInt(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("BigDecimal")(
      test("round-trip") {
        val c = codec(PrimitiveType.BigDecimal(Validation.None))
        assertTrue(roundTrip(c, BigDecimal("123.456")) == Right(BigDecimal("123.456")))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.BigDecimal(Validation.None))
        assertTrue(decodeStr(c, "abc").isLeft)
      }
    ),
    suite("DayOfWeek")(
      test("round-trip") {
        val c = codec(PrimitiveType.DayOfWeek(Validation.None))
        assertTrue(roundTrip(c, java.time.DayOfWeek.FRIDAY) == Right(java.time.DayOfWeek.FRIDAY))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.DayOfWeek(Validation.None))
        assertTrue(decodeStr(c, "NOTADAY").isLeft)
      }
    ),
    suite("Duration")(
      test("round-trip") {
        val c = codec(PrimitiveType.Duration(Validation.None))
        val d = java.time.Duration.ofHours(2).plusMinutes(30)
        assertTrue(roundTrip(c, d) == Right(d))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Duration(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("Instant")(
      test("round-trip") {
        val c = codec(PrimitiveType.Instant(Validation.None))
        val i = java.time.Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(roundTrip(c, i) == Right(i))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Instant(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("LocalDate")(
      test("round-trip") {
        val c = codec(PrimitiveType.LocalDate(Validation.None))
        val d = java.time.LocalDate.of(2024, 6, 15)
        assertTrue(roundTrip(c, d) == Right(d))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.LocalDate(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("LocalDateTime")(
      test("round-trip") {
        val c  = codec(PrimitiveType.LocalDateTime(Validation.None))
        val dt = java.time.LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        assertTrue(roundTrip(c, dt) == Right(dt))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.LocalDateTime(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("LocalTime")(
      test("round-trip") {
        val c = codec(PrimitiveType.LocalTime(Validation.None))
        val t = java.time.LocalTime.of(14, 30, 0)
        assertTrue(roundTrip(c, t) == Right(t))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.LocalTime(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("Month")(
      test("round-trip") {
        val c = codec(PrimitiveType.Month(Validation.None))
        assertTrue(roundTrip(c, java.time.Month.MARCH) == Right(java.time.Month.MARCH))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Month(Validation.None))
        assertTrue(decodeStr(c, "NOTAMONTH").isLeft)
      }
    ),
    suite("MonthDay")(
      test("round-trip") {
        val c  = codec(PrimitiveType.MonthDay(Validation.None))
        val md = java.time.MonthDay.of(12, 25)
        assertTrue(roundTrip(c, md) == Right(md))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.MonthDay(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("OffsetDateTime")(
      test("round-trip") {
        val c  = codec(PrimitiveType.OffsetDateTime(Validation.None))
        val dt = java.time.OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, java.time.ZoneOffset.ofHours(5))
        assertTrue(roundTrip(c, dt) == Right(dt))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.OffsetDateTime(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("OffsetTime")(
      test("round-trip") {
        val c = codec(PrimitiveType.OffsetTime(Validation.None))
        val t = java.time.OffsetTime.of(14, 30, 0, 0, java.time.ZoneOffset.ofHours(-3))
        assertTrue(roundTrip(c, t) == Right(t))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.OffsetTime(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("Period")(
      test("round-trip") {
        val c = codec(PrimitiveType.Period(Validation.None))
        val p = java.time.Period.of(1, 6, 15)
        assertTrue(roundTrip(c, p) == Right(p))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Period(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("Year")(
      test("round-trip") {
        val c = codec(PrimitiveType.Year(Validation.None))
        val y = java.time.Year.of(2024)
        assertTrue(roundTrip(c, y) == Right(y))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Year(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("YearMonth")(
      test("round-trip") {
        val c  = codec(PrimitiveType.YearMonth(Validation.None))
        val ym = java.time.YearMonth.of(2024, 6)
        assertTrue(roundTrip(c, ym) == Right(ym))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.YearMonth(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("ZoneId")(
      test("round-trip") {
        val c  = codec(PrimitiveType.ZoneId(Validation.None))
        val zi = java.time.ZoneId.of("America/New_York")
        assertTrue(roundTrip(c, zi) == Right(zi))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.ZoneId(Validation.None))
        assertTrue(decodeStr(c, "Not/A/Real/Zone").isLeft)
      }
    ),
    suite("ZoneOffset")(
      test("round-trip") {
        val c  = codec(PrimitiveType.ZoneOffset(Validation.None))
        val zo = java.time.ZoneOffset.ofHours(5)
        assertTrue(roundTrip(c, zo) == Right(zo))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.ZoneOffset(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("ZonedDateTime")(
      test("round-trip") {
        val c  = codec(PrimitiveType.ZonedDateTime(Validation.None))
        val zd = java.time.ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, java.time.ZoneId.of("UTC"))
        assertTrue(roundTrip(c, zd) == Right(zd))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.ZonedDateTime(Validation.None))
        assertTrue(decodeStr(c, "bad").isLeft)
      }
    ),
    suite("Currency")(
      test("round-trip") {
        val c   = codec(PrimitiveType.Currency(Validation.None))
        val usd = java.util.Currency.getInstance("USD")
        assertTrue(roundTrip(c, usd) == Right(usd))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.Currency(Validation.None))
        assertTrue(decodeStr(c, "NOTCURRENCY").isLeft)
      }
    ),
    suite("UUID")(
      test("round-trip") {
        val c = codec(PrimitiveType.UUID(Validation.None))
        val u = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(roundTrip(c, u) == Right(u))
      },
      test("invalid input") {
        val c = codec(PrimitiveType.UUID(Validation.None))
        assertTrue(decodeStr(c, "not-a-uuid").isLeft)
      }
    ),
    suite("headerNames")(
      test("all primitive codecs have single-element header") {
        val c = codec(PrimitiveType.Int(Validation.None))
        assertTrue(c.headerNames == IndexedSeq("value"))
      }
    ),
    suite("nullValue")(
      test("Int nullValue is 0") {
        val c = codec(PrimitiveType.Int(Validation.None))
        assertTrue(c.nullValue == 0)
      },
      test("Boolean nullValue is false") {
        val c = codec(PrimitiveType.Boolean(Validation.None))
        assertTrue(c.nullValue == false)
      },
      test("String nullValue is empty") {
        val c = codec(PrimitiveType.String(Validation.None))
        assertTrue(c.nullValue == "")
      }
    )
  )
}
