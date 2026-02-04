package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.test._

import java.time._
import java.util.{Currency, UUID}

object KeyableSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("KeyableSpec")(
    suite("Keyable instances exist for all PrimitiveType types")(
      test("Unit has Keyable instance") {
        val s = Keyable[Unit]
        assertTrue(s.asKey(()) == "{}")
      },
      test("Boolean has Keyable instance") {
        check(Gen.boolean)(b => assertTrue(Keyable[Boolean].asKey(b) == b.toString))
      },
      test("Byte has Keyable instance") {
        check(Gen.byte)(b => assertTrue(Keyable[Byte].asKey(b) == b.toString))
      },
      test("Short has Keyable instance") {
        check(Gen.short)(s => assertTrue(Keyable[Short].asKey(s) == s.toString))
      },
      test("Int has Keyable instance") {
        check(Gen.int)(i => assertTrue(Keyable[Int].asKey(i) == i.toString))
      },
      test("Long has Keyable instance") {
        check(Gen.long)(l => assertTrue(Keyable[Long].asKey(l) == l.toString))
      },
      test("Float has Keyable instance") {
        check(Gen.float)(f => assertTrue(Keyable[Float].asKey(f) == f.toString))
      },
      test("Double has Keyable instance") {
        check(Gen.double)(d => assertTrue(Keyable[Double].asKey(d) == d.toString))
      },
      test("Char has Keyable instance") {
        check(Gen.char)(c => assertTrue(Keyable[Char].asKey(c) == c.toString))
      },
      test("String has Keyable instance") {
        check(Gen.string)(s => assertTrue(Keyable[String].asKey(s) == s))
      },
      test("BigInt has Keyable instance") {
        check(Gen.bigInt(BigInt("-" + "9" * 50), BigInt("9" * 50)))(bi =>
          assertTrue(Keyable[BigInt].asKey(bi) == bi.toString)
        )
      },
      test("BigDecimal has Keyable instance") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 50), BigDecimal("9" * 50)))(bd =>
          assertTrue(Keyable[BigDecimal].asKey(bd) == bd.toString)
        )
      },
      test("DayOfWeek has Keyable instance") {
        check(genDayOfWeek)(dow => assertTrue(Keyable[DayOfWeek].asKey(dow) == dow.toString))
      },
      test("Duration has Keyable instance") {
        check(genDuration)(d => assertTrue(Keyable[Duration].asKey(d) == d.toString))
      },
      test("Instant has Keyable instance") {
        check(genInstant)(i => assertTrue(Keyable[Instant].asKey(i) == i.toString))
      },
      test("LocalDate has Keyable instance") {
        check(genLocalDate)(ld => assertTrue(Keyable[LocalDate].asKey(ld) == ld.toString))
      },
      test("LocalDateTime has Keyable instance") {
        check(genLocalDateTime)(ldt => assertTrue(Keyable[LocalDateTime].asKey(ldt) == ldt.toString))
      },
      test("LocalTime has Keyable instance") {
        check(genLocalTime)(lt => assertTrue(Keyable[LocalTime].asKey(lt) == lt.toString))
      },
      test("Month has Keyable instance") {
        check(genMonth)(m => assertTrue(Keyable[Month].asKey(m) == m.toString))
      },
      test("MonthDay has Keyable instance") {
        check(genMonthDay)(md => assertTrue(Keyable[MonthDay].asKey(md) == md.toString))
      },
      test("OffsetDateTime has Keyable instance") {
        check(genOffsetDateTime)(odt => assertTrue(Keyable[OffsetDateTime].asKey(odt) == odt.toString))
      },
      test("OffsetTime has Keyable instance") {
        check(genOffsetTime)(ot => assertTrue(Keyable[OffsetTime].asKey(ot) == ot.toString))
      },
      test("Period has Keyable instance") {
        check(genPeriod)(p => assertTrue(Keyable[Period].asKey(p) == p.toString))
      },
      test("Year has Keyable instance") {
        check(genYear)(y => assertTrue(Keyable[Year].asKey(y) == y.toString))
      },
      test("YearMonth has Keyable instance") {
        check(genYearMonth)(ym => assertTrue(Keyable[YearMonth].asKey(ym) == ym.toString))
      },
      test("ZoneId has Keyable instance") {
        check(genZoneId)(zi => assertTrue(Keyable[ZoneId].asKey(zi) == zi.toString))
      },
      test("ZoneOffset has Keyable instance") {
        check(genZoneOffset)(zo => assertTrue(Keyable[ZoneOffset].asKey(zo) == zo.toString))
      },
      test("ZonedDateTime has Keyable instance") {
        check(genZonedDateTime)(zdt => assertTrue(Keyable[ZonedDateTime].asKey(zdt) == zdt.toString))
      },
      test("UUID has Keyable instance") {
        check(Gen.uuid)(u => assertTrue(Keyable[UUID].asKey(u) == u.toString))
      },
      test("Currency has Keyable instance") {
        val usd = Currency.getInstance("USD")
        val eur = Currency.getInstance("EUR")
        val jpy = Currency.getInstance("JPY")
        assertTrue(
          Keyable[Currency].asKey(usd) == "USD",
          Keyable[Currency].asKey(eur) == "EUR",
          Keyable[Currency].asKey(jpy) == "JPY"
        )
      }
    ),
    suite("asKey produces expected output")(
      test("Boolean asKey matches standard toString") {
        assertTrue(
          Keyable[Boolean].asKey(true) == "true",
          Keyable[Boolean].asKey(false) == "false"
        )
      },
      test("Int asKey handles edge cases") {
        assertTrue(
          Keyable[Int].asKey(0) == "0",
          Keyable[Int].asKey(-1) == "-1",
          Keyable[Int].asKey(Int.MaxValue) == "2147483647",
          Keyable[Int].asKey(Int.MinValue) == "-2147483648"
        )
      },
      test("Long asKey handles edge cases") {
        assertTrue(
          Keyable[Long].asKey(0L) == "0",
          Keyable[Long].asKey(Long.MaxValue) == "9223372036854775807",
          Keyable[Long].asKey(Long.MinValue) == "-9223372036854775808"
        )
      },
      test("Float asKey handles special values") {
        assertTrue(
          Keyable[Float].asKey(Float.NaN) == "NaN",
          Keyable[Float].asKey(Float.PositiveInfinity) == "Infinity",
          Keyable[Float].asKey(Float.NegativeInfinity) == "-Infinity"
        )
      },
      test("Double asKey handles special values") {
        assertTrue(
          Keyable[Double].asKey(Double.NaN) == "NaN",
          Keyable[Double].asKey(Double.PositiveInfinity) == "Infinity",
          Keyable[Double].asKey(Double.NegativeInfinity) == "-Infinity"
        )
      },
      test("String asKey is identity") {
        val testStrings = List("", "hello", "with spaces", "special chars: !@#$%", "unicode: \u00e9\u00f1")
        assertTrue(testStrings.forall(s => Keyable[String].asKey(s) == s))
      },
      test("UUID asKey produces standard format") {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(Keyable[UUID].asKey(uuid) == "550e8400-e29b-41d4-a716-446655440000")
      },
      test("LocalDate asKey produces ISO format") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(Keyable[LocalDate].asKey(date) == "2024-01-15")
      },
      test("LocalTime asKey produces ISO format") {
        val time = LocalTime.of(10, 30, 45)
        assertTrue(Keyable[LocalTime].asKey(time) == "10:30:45")
      },
      test("Instant asKey produces ISO format") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(Keyable[Instant].asKey(instant) == "2024-01-15T10:30:00Z")
      },
      test("DayOfWeek asKey produces uppercase name") {
        assertTrue(
          Keyable[DayOfWeek].asKey(DayOfWeek.MONDAY) == "MONDAY",
          Keyable[DayOfWeek].asKey(DayOfWeek.FRIDAY) == "FRIDAY"
        )
      },
      test("Month asKey produces uppercase name") {
        assertTrue(
          Keyable[Month].asKey(Month.JANUARY) == "JANUARY",
          Keyable[Month].asKey(Month.DECEMBER) == "DECEMBER"
        )
      },
      test("Duration asKey produces ISO-8601 format") {
        assertTrue(
          Keyable[Duration].asKey(Duration.ofHours(1)) == "PT1H",
          Keyable[Duration].asKey(Duration.ofMinutes(30)) == "PT30M",
          Keyable[Duration].asKey(Duration.ofSeconds(90)) == "PT1M30S"
        )
      },
      test("Period asKey produces ISO-8601 format") {
        assertTrue(
          Keyable[Period].asKey(Period.ofDays(30)) == "P30D",
          Keyable[Period].asKey(Period.ofMonths(6)) == "P6M",
          Keyable[Period].asKey(Period.of(1, 2, 3)) == "P1Y2M3D"
        )
      },
      test("ZoneOffset asKey produces offset format") {
        assertTrue(
          Keyable[ZoneOffset].asKey(ZoneOffset.UTC) == "Z",
          Keyable[ZoneOffset].asKey(ZoneOffset.ofHours(5)) == "+05:00",
          Keyable[ZoneOffset].asKey(ZoneOffset.ofHours(-8)) == "-08:00"
        )
      }
    ),
    suite("non-keyable types have no instance")(
      test("List[Int] has no Keyable instance") {
        typeCheck("Keyable[List[Int]]").map(result => assertTrue(result.isLeft))
      },
      test("Map[String, Int] has no Keyable instance") {
        typeCheck("Keyable[Map[String, Int]]").map(result => assertTrue(result.isLeft))
      },
      test("Option[Int] has no Keyable instance") {
        typeCheck("Keyable[Option[Int]]").map(result => assertTrue(result.isLeft))
      },
      test("Vector[String] has no Keyable instance") {
        typeCheck("Keyable[Vector[String]]").map(result => assertTrue(result.isLeft))
      },
      test("Set[Int] has no Keyable instance") {
        typeCheck("Keyable[Set[Int]]").map(result => assertTrue(result.isLeft))
      },
      test("Array[Int] has no Keyable instance") {
        typeCheck("Keyable[Array[Int]]").map(result => assertTrue(result.isLeft))
      },
      test("case class has no Keyable instance") {
        typeCheck("""
          case class Point(x: Int, y: Int)
          Keyable[Point]
        """).map(result => assertTrue(result.isLeft))
      },
      test("sealed trait has no Keyable instance") {
        typeCheck("""
          sealed trait Color
          case object Red extends Color
          Keyable[Color]
        """).map(result => assertTrue(result.isLeft))
      },
      test("tuple has no Keyable instance") {
        typeCheck("Keyable[(Int, String)]").map(result => assertTrue(result.isLeft))
      },
      test("Either has no Keyable instance") {
        typeCheck("Keyable[Either[String, Int]]").map(result => assertTrue(result.isLeft))
      }
    )
  )
}
