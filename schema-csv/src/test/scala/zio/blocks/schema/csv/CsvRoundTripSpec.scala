package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvRoundTripSpec extends SchemaBaseSpec {

  // Wrapper case classes for each primitive type to enable Schema derivation
  case class WByte(value: Byte)
  object WByte { implicit val schema: Schema[WByte] = Schema.derived }

  case class WShort(value: Short)
  object WShort { implicit val schema: Schema[WShort] = Schema.derived }

  case class WInt(value: Int)
  object WInt { implicit val schema: Schema[WInt] = Schema.derived }

  case class WLong(value: Long)
  object WLong { implicit val schema: Schema[WLong] = Schema.derived }

  case class WFloat(value: Float)
  object WFloat { implicit val schema: Schema[WFloat] = Schema.derived }

  case class WDouble(value: Double)
  object WDouble { implicit val schema: Schema[WDouble] = Schema.derived }

  case class WBoolean(value: Boolean)
  object WBoolean { implicit val schema: Schema[WBoolean] = Schema.derived }

  case class WChar(value: Char)
  object WChar { implicit val schema: Schema[WChar] = Schema.derived }

  case class WString(value: String)
  object WString { implicit val schema: Schema[WString] = Schema.derived }

  case class WBigInt(value: BigInt)
  object WBigInt { implicit val schema: Schema[WBigInt] = Schema.derived }

  case class WBigDecimal(value: BigDecimal)
  object WBigDecimal { implicit val schema: Schema[WBigDecimal] = Schema.derived }

  case class WLocalDate(value: java.time.LocalDate)
  object WLocalDate { implicit val schema: Schema[WLocalDate] = Schema.derived }

  case class WLocalDateTime(value: java.time.LocalDateTime)
  object WLocalDateTime { implicit val schema: Schema[WLocalDateTime] = Schema.derived }

  case class WLocalTime(value: java.time.LocalTime)
  object WLocalTime { implicit val schema: Schema[WLocalTime] = Schema.derived }

  case class WInstant(value: java.time.Instant)
  object WInstant { implicit val schema: Schema[WInstant] = Schema.derived }

  case class WDuration(value: java.time.Duration)
  object WDuration { implicit val schema: Schema[WDuration] = Schema.derived }

  case class WDayOfWeek(value: java.time.DayOfWeek)
  object WDayOfWeek { implicit val schema: Schema[WDayOfWeek] = Schema.derived }

  case class WMonth(value: java.time.Month)
  object WMonth { implicit val schema: Schema[WMonth] = Schema.derived }

  case class WMonthDay(value: java.time.MonthDay)
  object WMonthDay { implicit val schema: Schema[WMonthDay] = Schema.derived }

  case class WYear(value: java.time.Year)
  object WYear { implicit val schema: Schema[WYear] = Schema.derived }

  case class WYearMonth(value: java.time.YearMonth)
  object WYearMonth { implicit val schema: Schema[WYearMonth] = Schema.derived }

  case class WOffsetDateTime(value: java.time.OffsetDateTime)
  object WOffsetDateTime { implicit val schema: Schema[WOffsetDateTime] = Schema.derived }

  case class WOffsetTime(value: java.time.OffsetTime)
  object WOffsetTime { implicit val schema: Schema[WOffsetTime] = Schema.derived }

  case class WZonedDateTime(value: java.time.ZonedDateTime)
  object WZonedDateTime { implicit val schema: Schema[WZonedDateTime] = Schema.derived }

  case class WZoneId(value: java.time.ZoneId)
  object WZoneId { implicit val schema: Schema[WZoneId] = Schema.derived }

  case class WZoneOffset(value: java.time.ZoneOffset)
  object WZoneOffset { implicit val schema: Schema[WZoneOffset] = Schema.derived }

  case class WPeriod(value: java.time.Period)
  object WPeriod { implicit val schema: Schema[WPeriod] = Schema.derived }

  case class WUUID(value: java.util.UUID)
  object WUUID { implicit val schema: Schema[WUUID] = Schema.derived }

  case class WCurrency(value: java.util.Currency)
  object WCurrency { implicit val schema: Schema[WCurrency] = Schema.derived }

  case class WUnit(value: Unit)
  object WUnit { implicit val schema: Schema[WUnit] = Schema.derived }

  private def roundTrip[A](value: A)(implicit s: Schema[A]): Either[SchemaError, A] = {
    val codec = s.derive(CsvFormat)
    val buf   = CharBuffer.allocate(8192)
    codec.encode(value, buf)
    buf.flip()
    val encoded = buf.toString
    codec.decode(CharBuffer.wrap(encoded))
  }

  def spec = suite("CsvRoundTripSpec")(
    suite("integral types")(
      test("Byte round-trips") {
        assertTrue(roundTrip(WByte(42.toByte)) == Right(WByte(42.toByte)))
      },
      test("Byte boundary values") {
        assertTrue(
          roundTrip(WByte(Byte.MinValue)) == Right(WByte(Byte.MinValue)) &&
            roundTrip(WByte(Byte.MaxValue)) == Right(WByte(Byte.MaxValue))
        )
      },
      test("Short round-trips") {
        assertTrue(roundTrip(WShort(1234.toShort)) == Right(WShort(1234.toShort)))
      },
      test("Int round-trips") {
        assertTrue(roundTrip(WInt(42)) == Right(WInt(42)))
      },
      test("Int zero and negative") {
        assertTrue(
          roundTrip(WInt(0)) == Right(WInt(0)) &&
            roundTrip(WInt(-999)) == Right(WInt(-999))
        )
      },
      test("Long round-trips") {
        assertTrue(roundTrip(WLong(9876543210L)) == Right(WLong(9876543210L)))
      }
    ),
    suite("floating-point types")(
      test("Float round-trips") {
        assertTrue(roundTrip(WFloat(3.14f)) == Right(WFloat(3.14f)))
      },
      test("Double round-trips") {
        assertTrue(roundTrip(WDouble(2.718281828)) == Right(WDouble(2.718281828)))
      },
      test("Double zero") {
        assertTrue(roundTrip(WDouble(0.0)) == Right(WDouble(0.0)))
      }
    ),
    suite("text types")(
      test("String round-trips") {
        assertTrue(roundTrip(WString("hello world")) == Right(WString("hello world")))
      },
      test("String with special CSV chars") {
        assertTrue(roundTrip(WString("a,b,c")) == Right(WString("a,b,c")))
      },
      test("Char round-trips") {
        assertTrue(roundTrip(WChar('Z')) == Right(WChar('Z')))
      },
      test("Boolean true round-trips") {
        assertTrue(roundTrip(WBoolean(true)) == Right(WBoolean(true)))
      },
      test("Boolean false round-trips") {
        assertTrue(roundTrip(WBoolean(false)) == Right(WBoolean(false)))
      }
    ),
    suite("big number types")(
      test("BigInt round-trips") {
        val v = BigInt("123456789012345678901234567890")
        assertTrue(roundTrip(WBigInt(v)) == Right(WBigInt(v)))
      },
      test("BigDecimal round-trips") {
        val v = BigDecimal("12345.67890123456789")
        assertTrue(roundTrip(WBigDecimal(v)) == Right(WBigDecimal(v)))
      }
    ),
    suite("java.time types")(
      test("LocalDate round-trips") {
        val v = java.time.LocalDate.of(2024, 6, 15)
        assertTrue(roundTrip(WLocalDate(v)) == Right(WLocalDate(v)))
      },
      test("LocalDateTime round-trips") {
        val v = java.time.LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        assertTrue(roundTrip(WLocalDateTime(v)) == Right(WLocalDateTime(v)))
      },
      test("LocalTime round-trips") {
        val v = java.time.LocalTime.of(14, 30, 45)
        assertTrue(roundTrip(WLocalTime(v)) == Right(WLocalTime(v)))
      },
      test("Instant round-trips") {
        val v = java.time.Instant.parse("2024-06-15T10:30:00Z")
        assertTrue(roundTrip(WInstant(v)) == Right(WInstant(v)))
      },
      test("Duration round-trips") {
        val v = java.time.Duration.ofHours(2).plusMinutes(30)
        assertTrue(roundTrip(WDuration(v)) == Right(WDuration(v)))
      },
      test("DayOfWeek round-trips") {
        assertTrue(
          roundTrip(WDayOfWeek(java.time.DayOfWeek.FRIDAY)) == Right(WDayOfWeek(java.time.DayOfWeek.FRIDAY))
        )
      },
      test("Month round-trips") {
        assertTrue(roundTrip(WMonth(java.time.Month.MARCH)) == Right(WMonth(java.time.Month.MARCH)))
      },
      test("MonthDay round-trips") {
        val v = java.time.MonthDay.of(12, 25)
        assertTrue(roundTrip(WMonthDay(v)) == Right(WMonthDay(v)))
      },
      test("Year round-trips") {
        val v = java.time.Year.of(2024)
        assertTrue(roundTrip(WYear(v)) == Right(WYear(v)))
      },
      test("YearMonth round-trips") {
        val v = java.time.YearMonth.of(2024, 6)
        assertTrue(roundTrip(WYearMonth(v)) == Right(WYearMonth(v)))
      },
      test("OffsetDateTime round-trips") {
        val v = java.time.OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, java.time.ZoneOffset.ofHours(5))
        assertTrue(roundTrip(WOffsetDateTime(v)) == Right(WOffsetDateTime(v)))
      },
      test("OffsetTime round-trips") {
        val v = java.time.OffsetTime.of(14, 30, 0, 0, java.time.ZoneOffset.ofHours(-3))
        assertTrue(roundTrip(WOffsetTime(v)) == Right(WOffsetTime(v)))
      },
      test("ZonedDateTime round-trips") {
        val v = java.time.ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, java.time.ZoneId.of("UTC"))
        assertTrue(roundTrip(WZonedDateTime(v)) == Right(WZonedDateTime(v)))
      },
      test("ZoneId round-trips") {
        val v = java.time.ZoneId.of("America/New_York")
        assertTrue(roundTrip(WZoneId(v)) == Right(WZoneId(v)))
      },
      test("ZoneOffset round-trips") {
        val v = java.time.ZoneOffset.ofHours(5)
        assertTrue(roundTrip(WZoneOffset(v)) == Right(WZoneOffset(v)))
      },
      test("Period round-trips") {
        val v = java.time.Period.of(1, 6, 15)
        assertTrue(roundTrip(WPeriod(v)) == Right(WPeriod(v)))
      }
    ),
    suite("utility types")(
      test("UUID round-trips") {
        val v = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(roundTrip(WUUID(v)) == Right(WUUID(v)))
      },
      test("Currency round-trips") {
        val v = java.util.Currency.getInstance("EUR")
        assertTrue(roundTrip(WCurrency(v)) == Right(WCurrency(v)))
      },
      test("Unit round-trips") {
        assertTrue(roundTrip(WUnit(())) == Right(WUnit(())))
      }
    )
  )
}
