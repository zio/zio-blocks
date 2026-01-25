package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.test._
import zio.test.TestAspect.exceptNative

import java.time._
import java.util.{Currency, UUID}

object StringableSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("StringableSpec")(
    suite("Stringable instances exist for all PrimitiveType types")(
      test("Unit has Stringable instance") {
        val s = Stringable[Unit]
        assertTrue(s.asString(()) == "()")
      },
      test("Boolean has Stringable instance") {
        check(Gen.boolean)(b => assertTrue(Stringable[Boolean].asString(b) == b.toString))
      },
      test("Byte has Stringable instance") {
        check(Gen.byte)(b => assertTrue(Stringable[Byte].asString(b) == b.toString))
      },
      test("Short has Stringable instance") {
        check(Gen.short)(s => assertTrue(Stringable[Short].asString(s) == s.toString))
      },
      test("Int has Stringable instance") {
        check(Gen.int)(i => assertTrue(Stringable[Int].asString(i) == i.toString))
      },
      test("Long has Stringable instance") {
        check(Gen.long)(l => assertTrue(Stringable[Long].asString(l) == l.toString))
      },
      test("Float has Stringable instance") {
        check(Gen.float)(f => assertTrue(Stringable[Float].asString(f) == f.toString))
      },
      test("Double has Stringable instance") {
        check(Gen.double)(d => assertTrue(Stringable[Double].asString(d) == d.toString))
      },
      test("Char has Stringable instance") {
        check(Gen.char)(c => assertTrue(Stringable[Char].asString(c) == c.toString))
      },
      test("String has Stringable instance") {
        check(Gen.string)(s => assertTrue(Stringable[String].asString(s) == s))
      },
      test("BigInt has Stringable instance") {
        check(Gen.bigInt(BigInt("-" + "9" * 50), BigInt("9" * 50)))(bi =>
          assertTrue(Stringable[BigInt].asString(bi) == bi.toString)
        )
      },
      test("BigDecimal has Stringable instance") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 50), BigDecimal("9" * 50)))(bd =>
          assertTrue(Stringable[BigDecimal].asString(bd) == bd.toString)
        )
      },
      test("DayOfWeek has Stringable instance") {
        check(genDayOfWeek)(dow => assertTrue(Stringable[DayOfWeek].asString(dow) == dow.toString))
      },
      test("Duration has Stringable instance") {
        check(genDuration)(d => assertTrue(Stringable[Duration].asString(d) == d.toString))
      },
      test("Instant has Stringable instance") {
        check(genInstant)(i => assertTrue(Stringable[Instant].asString(i) == i.toString))
      },
      test("LocalDate has Stringable instance") {
        check(genLocalDate)(ld => assertTrue(Stringable[LocalDate].asString(ld) == ld.toString))
      },
      test("LocalDateTime has Stringable instance") {
        check(genLocalDateTime)(ldt => assertTrue(Stringable[LocalDateTime].asString(ldt) == ldt.toString))
      },
      test("LocalTime has Stringable instance") {
        check(genLocalTime)(lt => assertTrue(Stringable[LocalTime].asString(lt) == lt.toString))
      },
      test("Month has Stringable instance") {
        check(genMonth)(m => assertTrue(Stringable[Month].asString(m) == m.toString))
      },
      test("MonthDay has Stringable instance") {
        check(genMonthDay)(md => assertTrue(Stringable[MonthDay].asString(md) == md.toString))
      },
      test("OffsetDateTime has Stringable instance") {
        check(genOffsetDateTime)(odt => assertTrue(Stringable[OffsetDateTime].asString(odt) == odt.toString))
      },
      test("OffsetTime has Stringable instance") {
        check(genOffsetTime)(ot => assertTrue(Stringable[OffsetTime].asString(ot) == ot.toString))
      },
      test("Period has Stringable instance") {
        check(genPeriod)(p => assertTrue(Stringable[Period].asString(p) == p.toString))
      },
      test("Year has Stringable instance") {
        check(genYear)(y => assertTrue(Stringable[Year].asString(y) == y.toString))
      },
      test("YearMonth has Stringable instance") {
        check(genYearMonth)(ym => assertTrue(Stringable[YearMonth].asString(ym) == ym.toString))
      },
      test("ZoneId has Stringable instance") {
        check(genZoneId)(zi => assertTrue(Stringable[ZoneId].asString(zi) == zi.toString))
      },
      test("ZoneOffset has Stringable instance") {
        check(genZoneOffset)(zo => assertTrue(Stringable[ZoneOffset].asString(zo) == zo.toString))
      },
      test("ZonedDateTime has Stringable instance") {
        check(genZonedDateTime)(zdt => assertTrue(Stringable[ZonedDateTime].asString(zdt) == zdt.toString))
      },
      test("UUID has Stringable instance") {
        check(Gen.uuid)(u => assertTrue(Stringable[UUID].asString(u) == u.toString))
      },
      test("Currency has Stringable instance") {
        val usd = Currency.getInstance("USD")
        val eur = Currency.getInstance("EUR")
        val jpy = Currency.getInstance("JPY")
        assertTrue(
          Stringable[Currency].asString(usd) == "USD",
          Stringable[Currency].asString(eur) == "EUR",
          Stringable[Currency].asString(jpy) == "JPY"
        )
      }
    ),
    suite("asString produces expected output")(
      test("Boolean asString matches standard toString") {
        assertTrue(
          Stringable[Boolean].asString(true) == "true",
          Stringable[Boolean].asString(false) == "false"
        )
      },
      test("Int asString handles edge cases") {
        assertTrue(
          Stringable[Int].asString(0) == "0",
          Stringable[Int].asString(-1) == "-1",
          Stringable[Int].asString(Int.MaxValue) == "2147483647",
          Stringable[Int].asString(Int.MinValue) == "-2147483648"
        )
      },
      test("Long asString handles edge cases") {
        assertTrue(
          Stringable[Long].asString(0L) == "0",
          Stringable[Long].asString(Long.MaxValue) == "9223372036854775807",
          Stringable[Long].asString(Long.MinValue) == "-9223372036854775808"
        )
      },
      test("Float asString handles special values") {
        assertTrue(
          Stringable[Float].asString(Float.NaN) == "NaN",
          Stringable[Float].asString(Float.PositiveInfinity) == "Infinity",
          Stringable[Float].asString(Float.NegativeInfinity) == "-Infinity"
        )
      },
      test("Double asString handles special values") {
        assertTrue(
          Stringable[Double].asString(Double.NaN) == "NaN",
          Stringable[Double].asString(Double.PositiveInfinity) == "Infinity",
          Stringable[Double].asString(Double.NegativeInfinity) == "-Infinity"
        )
      },
      test("String asString is identity") {
        val testStrings = List("", "hello", "with spaces", "special chars: !@#$%", "unicode: \u00e9\u00f1")
        assertTrue(testStrings.forall(s => Stringable[String].asString(s) == s))
      },
      test("UUID asString produces standard format") {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(Stringable[UUID].asString(uuid) == "550e8400-e29b-41d4-a716-446655440000")
      },
      test("LocalDate asString produces ISO format") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(Stringable[LocalDate].asString(date) == "2024-01-15")
      },
      test("LocalTime asString produces ISO format") {
        val time = LocalTime.of(10, 30, 45)
        assertTrue(Stringable[LocalTime].asString(time) == "10:30:45")
      },
      test("Instant asString produces ISO format") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(Stringable[Instant].asString(instant) == "2024-01-15T10:30:00Z")
      },
      test("DayOfWeek asString produces uppercase name") {
        assertTrue(
          Stringable[DayOfWeek].asString(DayOfWeek.MONDAY) == "MONDAY",
          Stringable[DayOfWeek].asString(DayOfWeek.FRIDAY) == "FRIDAY"
        )
      },
      test("Month asString produces uppercase name") {
        assertTrue(
          Stringable[Month].asString(Month.JANUARY) == "JANUARY",
          Stringable[Month].asString(Month.DECEMBER) == "DECEMBER"
        )
      },
      test("Duration asString produces ISO-8601 format") {
        assertTrue(
          Stringable[Duration].asString(Duration.ofHours(1)) == "PT1H",
          Stringable[Duration].asString(Duration.ofMinutes(30)) == "PT30M",
          Stringable[Duration].asString(Duration.ofSeconds(90)) == "PT1M30S"
        )
      },
      test("Period asString produces ISO-8601 format") {
        assertTrue(
          Stringable[Period].asString(Period.ofDays(30)) == "P30D",
          Stringable[Period].asString(Period.ofMonths(6)) == "P6M",
          Stringable[Period].asString(Period.of(1, 2, 3)) == "P1Y2M3D"
        )
      },
      test("ZoneOffset asString produces offset format") {
        assertTrue(
          Stringable[ZoneOffset].asString(ZoneOffset.UTC) == "Z",
          Stringable[ZoneOffset].asString(ZoneOffset.ofHours(5)) == "+05:00",
          Stringable[ZoneOffset].asString(ZoneOffset.ofHours(-8)) == "-08:00"
        )
      }
    ),
    suite("non-stringable types have no instance")(
      test("List[Int] has no Stringable instance") {
        typeCheck("Stringable[List[Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Map[String, Int] has no Stringable instance") {
        typeCheck("Stringable[Map[String, Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Option[Int] has no Stringable instance") {
        typeCheck("Stringable[Option[Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Vector[String] has no Stringable instance") {
        typeCheck("Stringable[Vector[String]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Set[Int] has no Stringable instance") {
        typeCheck("Stringable[Set[Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Array[Int] has no Stringable instance") {
        typeCheck("Stringable[Array[Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("case class has no Stringable instance") {
        typeCheck("""
          case class Point(x: Int, y: Int)
          Stringable[Point]
        """).map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("sealed trait has no Stringable instance") {
        typeCheck("""
          sealed trait Color
          case object Red extends Color
          Stringable[Color]
        """).map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("tuple has no Stringable instance") {
        typeCheck("Stringable[(Int, String)]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative,
      test("Either has no Stringable instance") {
        typeCheck("Stringable[Either[String, Int]]").map(result => assertTrue(result.isLeft))
      } @@ exceptNative
    )
  )
}
