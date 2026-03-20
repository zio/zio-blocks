package zio.blocks.schema.yaml

import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema.json._
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object YamlKeyableSpec extends YamlBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("YamlKeyableSpec")(
    suite("Keyable instances exist for all PrimitiveType types")(
      test("Unit has Keyable instance") {
        assertTrue(YamlKeyable[Unit].asKey(()) == "{}")
      },
      test("Boolean has Keyable instance") {
        check(Gen.boolean)(b => assertTrue(YamlKeyable[Boolean].asKey(b) == b.toString))
      },
      test("Byte has Keyable instance") {
        check(Gen.byte)(b => assertTrue(YamlKeyable[Byte].asKey(b) == b.toString))
      },
      test("Short has Keyable instance") {
        check(Gen.short)(s => assertTrue(YamlKeyable[Short].asKey(s) == s.toString))
      },
      test("Int has Keyable instance") {
        check(Gen.int)(i => assertTrue(YamlKeyable[Int].asKey(i) == i.toString))
      },
      test("Long has Keyable instance") {
        check(Gen.long)(l => assertTrue(YamlKeyable[Long].asKey(l) == l.toString))
      },
      test("Float has Keyable instance") {
        check(Gen.float.filter(_.isFinite)) { f =>
          assertTrue(YamlKeyable[Float].asKey(f) == JsonBinaryCodec.floatCodec.encodeToString(f))
        }
      },
      test("Double has Keyable instance") {
        check(Gen.double.filter(_.isFinite)) { d =>
          assertTrue(YamlKeyable[Double].asKey(d) == JsonBinaryCodec.doubleCodec.encodeToString(d))
        }
      },
      test("Char has Keyable instance") {
        check(Gen.char)(c => assertTrue(YamlKeyable[Char].asKey(c) == c.toString))
      },
      test("String has Keyable instance") {
        check(Gen.string)(s => assertTrue(YamlKeyable[String].asKey(s) == s))
      },
      test("BigInt has Keyable instance") {
        check(Gen.bigInt(BigInt("-" + "9" * 50), BigInt("9" * 50)))(bi =>
          assertTrue(YamlKeyable[BigInt].asKey(bi) == bi.toString)
        )
      },
      test("BigDecimal has Keyable instance") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 50), BigDecimal("9" * 50)))(bd =>
          assertTrue(YamlKeyable[BigDecimal].asKey(bd) == bd.toString)
        )
      },
      test("DayOfWeek has Keyable instance") {
        check(genDayOfWeek)(dow => assertTrue(YamlKeyable[DayOfWeek].asKey(dow) == dow.toString))
      },
      test("Duration has Keyable instance") {
        check(genDuration)(d => assertTrue(YamlKeyable[Duration].asKey(d) == d.toString))
      },
      test("Instant has Keyable instance") {
        check(genInstant)(i => assertTrue(YamlKeyable[Instant].asKey(i) == i.toString))
      },
      test("LocalDate has Keyable instance") {
        check(genLocalDate)(ld => assertTrue(YamlKeyable[LocalDate].asKey(ld) == ld.toString))
      },
      test("LocalDateTime has Keyable instance") {
        check(genLocalDateTime)(ldt => assertTrue(YamlKeyable[LocalDateTime].asKey(ldt) == ldt.toString))
      },
      test("LocalTime has Keyable instance") {
        check(genLocalTime)(lt => assertTrue(YamlKeyable[LocalTime].asKey(lt) == lt.toString))
      },
      test("Month has Keyable instance") {
        check(genMonth)(m => assertTrue(YamlKeyable[Month].asKey(m) == m.toString))
      },
      test("MonthDay has Keyable instance") {
        check(genMonthDay)(md => assertTrue(YamlKeyable[MonthDay].asKey(md) == md.toString))
      },
      test("OffsetDateTime has Keyable instance") {
        check(genOffsetDateTime)(odt => assertTrue(YamlKeyable[OffsetDateTime].asKey(odt) == odt.toString))
      },
      test("OffsetTime has Keyable instance") {
        check(genOffsetTime)(ot => assertTrue(YamlKeyable[OffsetTime].asKey(ot) == ot.toString))
      },
      test("Period has Keyable instance") {
        check(genPeriod)(p => assertTrue(YamlKeyable[Period].asKey(p) == p.toString))
      },
      test("Year has Keyable instance") {
        check(genYear)(y => assertTrue(YamlKeyable[Year].asKey(y) == y.toString))
      },
      test("YearMonth has Keyable instance") {
        check(genYearMonth)(ym => assertTrue(YamlKeyable[YearMonth].asKey(ym) == ym.toString))
      },
      test("ZoneId has Keyable instance") {
        check(genZoneId)(zi => assertTrue(YamlKeyable[ZoneId].asKey(zi) == zi.toString))
      },
      test("ZoneOffset has Keyable instance") {
        check(genZoneOffset)(zo => assertTrue(YamlKeyable[ZoneOffset].asKey(zo) == zo.toString))
      },
      test("ZonedDateTime has Keyable instance") {
        check(genZonedDateTime)(zdt => assertTrue(YamlKeyable[ZonedDateTime].asKey(zdt) == zdt.toString))
      },
      test("UUID has Keyable instance") {
        check(Gen.uuid)(u => assertTrue(YamlKeyable[UUID].asKey(u) == u.toString))
      },
      test("Currency has Keyable instance") {
        val usd = Currency.getInstance("USD")
        val eur = Currency.getInstance("EUR")
        val jpy = Currency.getInstance("JPY")
        assertTrue(
          YamlKeyable[Currency].asKey(usd) == "USD",
          YamlKeyable[Currency].asKey(eur) == "EUR",
          YamlKeyable[Currency].asKey(jpy) == "JPY"
        )
      }
    ),
    suite("asKey produces expected output")(
      test("Boolean asKey matches standard toString") {
        assertTrue(
          YamlKeyable[Boolean].asKey(true) == "true",
          YamlKeyable[Boolean].asKey(false) == "false"
        )
      },
      test("Int asKey handles edge cases") {
        assertTrue(
          YamlKeyable[Int].asKey(0) == "0",
          YamlKeyable[Int].asKey(-1) == "-1",
          YamlKeyable[Int].asKey(Int.MaxValue) == "2147483647",
          YamlKeyable[Int].asKey(Int.MinValue) == "-2147483648"
        )
      },
      test("Long asKey handles edge cases") {
        assertTrue(
          YamlKeyable[Long].asKey(0L) == "0",
          YamlKeyable[Long].asKey(Long.MaxValue) == "9223372036854775807",
          YamlKeyable[Long].asKey(Long.MinValue) == "-9223372036854775808"
        )
      },
      test("String asKey is identity") {
        val testStrings = List("", "hello", "with spaces", "special chars: !@#$%", "unicode: \u00e9\u00f1")
        assertTrue(testStrings.forall(s => YamlKeyable[String].asKey(s) == s))
      },
      test("UUID asKey produces standard format") {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(YamlKeyable[UUID].asKey(uuid) == "550e8400-e29b-41d4-a716-446655440000")
      },
      test("LocalDate asKey produces ISO format") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(YamlKeyable[LocalDate].asKey(date) == "2024-01-15")
      },
      test("LocalTime asKey produces ISO format") {
        val time = LocalTime.of(10, 30, 45)
        assertTrue(YamlKeyable[LocalTime].asKey(time) == "10:30:45")
      },
      test("Instant asKey produces ISO format") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(YamlKeyable[Instant].asKey(instant) == "2024-01-15T10:30:00Z")
      },
      test("DayOfWeek asKey produces uppercase name") {
        assertTrue(
          YamlKeyable[DayOfWeek].asKey(DayOfWeek.MONDAY) == "MONDAY",
          YamlKeyable[DayOfWeek].asKey(DayOfWeek.FRIDAY) == "FRIDAY"
        )
      },
      test("Month asKey produces uppercase name") {
        assertTrue(
          YamlKeyable[Month].asKey(Month.JANUARY) == "JANUARY",
          YamlKeyable[Month].asKey(Month.DECEMBER) == "DECEMBER"
        )
      },
      test("Duration asKey produces ISO-8601 format") {
        assertTrue(
          YamlKeyable[Duration].asKey(Duration.ofHours(1)) == "PT1H",
          YamlKeyable[Duration].asKey(Duration.ofMinutes(30)) == "PT30M",
          YamlKeyable[Duration].asKey(Duration.ofSeconds(90)) == "PT1M30S"
        )
      },
      test("Period asKey produces ISO-8601 format") {
        assertTrue(
          YamlKeyable[Period].asKey(Period.ofDays(30)) == "P30D",
          YamlKeyable[Period].asKey(Period.ofMonths(6)) == "P6M",
          YamlKeyable[Period].asKey(Period.of(1, 2, 3)) == "P1Y2M3D"
        )
      },
      test("ZoneOffset asKey produces offset format") {
        assertTrue(
          YamlKeyable[ZoneOffset].asKey(ZoneOffset.UTC) == "Z",
          YamlKeyable[ZoneOffset].asKey(ZoneOffset.ofHours(5)) == "+05:00",
          YamlKeyable[ZoneOffset].asKey(ZoneOffset.ofHours(-8)) == "-08:00"
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
