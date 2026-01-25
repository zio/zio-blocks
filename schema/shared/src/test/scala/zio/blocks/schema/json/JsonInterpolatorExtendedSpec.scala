package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

import java.time._
import java.util.{Currency, UUID}

/**
 * Extended tests for JSON interpolator runtime code paths.
 *
 * These tests call JsonInterpolatorRuntime.jsonWithContexts directly to
 * exercise the runtime pattern matching in writeValue, writeKeyOnly,
 * writeInString, and writeKey methods.
 */
object JsonInterpolatorExtendedSpec extends SchemaBaseSpec {

  // Helper to call jsonWithContexts for value position
  private def jsonValue(value: Any): Json = {
    val sc = new StringContext("{\"v\": ", "}")
    JsonInterpolatorRuntime.jsonWithContexts(sc, Seq(value), Seq(InterpolationContext.Value))
  }

  // Helper to call jsonWithContexts for key position
  private def jsonKey(key: Any): Json = {
    val sc = new StringContext("{", ": \"value\"}")
    JsonInterpolatorRuntime.jsonWithContexts(sc, Seq(key), Seq(InterpolationContext.Key))
  }

  // Helper to call jsonWithContexts for in-string position
  private def jsonInString(value: Any): Json = {
    val sc = new StringContext("{\"msg\": \"prefix-", "-suffix\"}")
    JsonInterpolatorRuntime.jsonWithContexts(sc, Seq(value), Seq(InterpolationContext.InString))
  }

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorExtendedSpec")(
    suite("writeValue runtime dispatch")(
      test("String") {
        val result = jsonValue("hello")
        assertTrue(result.get("v").string == Right("hello"))
      },
      test("Boolean true") {
        val result = jsonValue(true)
        assertTrue(result.get("v").boolean == Right(true))
      },
      test("Boolean false") {
        val result = jsonValue(false)
        assertTrue(result.get("v").boolean == Right(false))
      },
      test("Byte") {
        val result = jsonValue(42.toByte)
        assertTrue(result.get("v").int == Right(42))
      },
      test("Short") {
        val result = jsonValue(42.toShort)
        assertTrue(result.get("v").int == Right(42))
      },
      test("Int") {
        val result = jsonValue(42)
        assertTrue(result.get("v").int == Right(42))
      },
      test("Long") {
        val result = jsonValue(42L)
        assertTrue(result.get("v").long == Right(42L))
      },
      test("Float") {
        val result = jsonValue(3.14f)
        assertTrue(result.get("v").float == Right(3.14f))
      },
      test("Double") {
        val result = jsonValue(3.14)
        assertTrue(result.get("v").double == Right(3.14))
      },
      test("Char") {
        val result = jsonValue('A')
        assertTrue(result.get("v").string == Right("A"))
      },
      test("BigDecimal") {
        val result = jsonValue(BigDecimal("123.456"))
        assertTrue(result.get("v").number == Right(BigDecimal("123.456")))
      },
      test("BigInt") {
        val result = jsonValue(BigInt("12345678901234567890"))
        assertTrue(result.get("v").number.map(_.toBigInt) == Right(BigInt("12345678901234567890")))
      },
      test("DayOfWeek") {
        val result = jsonValue(DayOfWeek.MONDAY)
        assertTrue(result.get("v").string == Right("MONDAY"))
      },
      test("Duration") {
        val result = jsonValue(Duration.ofHours(1))
        assertTrue(result.get("v").string == Right("PT1H"))
      },
      test("Instant") {
        val result = jsonValue(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("v").string == Right("2024-01-15T10:30:00Z"))
      },
      test("LocalDate") {
        val result = jsonValue(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("v").string == Right("2024-01-15"))
      },
      test("LocalDateTime") {
        val result = jsonValue(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("v").string == Right("2024-01-15T10:30"))
      },
      test("LocalTime") {
        val result = jsonValue(LocalTime.of(10, 30))
        assertTrue(result.get("v").string == Right("10:30"))
      },
      test("Month") {
        val result = jsonValue(Month.JANUARY)
        assertTrue(result.get("v").string == Right("JANUARY"))
      },
      test("MonthDay") {
        val result = jsonValue(MonthDay.of(1, 15))
        assertTrue(result.get("v").string == Right("--01-15"))
      },
      test("OffsetDateTime") {
        val result = jsonValue(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("v").string == Right("2024-01-15T10:30Z"))
      },
      test("OffsetTime") {
        val result = jsonValue(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("v").string == Right("10:30Z"))
      },
      test("Period") {
        val result = jsonValue(Period.ofDays(30))
        assertTrue(result.get("v").string == Right("P30D"))
      },
      test("Year") {
        val result = jsonValue(Year.of(2024))
        assertTrue(result.get("v").string == Right("2024"))
      },
      test("YearMonth") {
        val result = jsonValue(YearMonth.of(2024, 1))
        assertTrue(result.get("v").string == Right("2024-01"))
      },
      test("ZoneOffset") {
        val result = jsonValue(ZoneOffset.ofHours(5))
        assertTrue(result.get("v").string == Right("+05:00"))
      },
      test("ZoneId") {
        val result = jsonValue(ZoneId.of("UTC"))
        assertTrue(result.get("v").string == Right("UTC"))
      },
      test("ZonedDateTime") {
        val result = jsonValue(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC")))
        assertTrue(result.get("v").string.exists(_.contains("2024-01-15")))
      },
      test("Currency") {
        val result = jsonValue(Currency.getInstance("USD"))
        assertTrue(result.get("v").string == Right("USD"))
      },
      test("UUID") {
        val result = jsonValue(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("v").string == Right("550e8400-e29b-41d4-a716-446655440000"))
      },
      test("Option Some") {
        val result = jsonValue(Some(42))
        assertTrue(result.get("v").int == Right(42))
      },
      test("Option None") {
        val result = jsonValue(None)
        assertTrue(result.get("v").one == Right(Json.Null))
      },
      test("null") {
        val result = jsonValue(null)
        assertTrue(result.get("v").one == Right(Json.Null))
      },
      test("Unit") {
        val result = jsonValue(())
        assertTrue(result.get("v").one == Right(Json.obj()))
      },
      test("Map with multiple entries") {
        val result = jsonValue(Map("a" -> 1, "b" -> 2))
        assertTrue(
          result.get("v").get("a").int == Right(1),
          result.get("v").get("b").int == Right(2)
        )
      },
      test("List with multiple elements") {
        val result = jsonValue(List(1, 2, 3))
        assertTrue(
          result.get("v")(0).int == Right(1),
          result.get("v")(1).int == Right(2),
          result.get("v")(2).int == Right(3)
        )
      },
      test("Array with multiple elements") {
        val result = jsonValue(Array(1, 2, 3))
        assertTrue(
          result.get("v")(0).int == Right(1),
          result.get("v")(1).int == Right(2)
        )
      },
      test("Vector") {
        val result = jsonValue(Vector(1, 2))
        assertTrue(result.get("v")(0).int == Right(1))
      },
      test("Set") {
        val result = jsonValue(Set(42))
        assertTrue(result.get("v")(0).int == Right(42))
      },
      test("fallback toString") {
        case class Custom(value: Int) {
          override def toString: String = s"""{"custom":$value}"""
        }
        val result = jsonValue(Custom(42))
        assertTrue(result.get("v").get("custom").int == Right(42))
      }
    ),
    suite("writeKeyOnly runtime dispatch")(
      test("String key") {
        val result = jsonKey("mykey")
        assertTrue(result.get("mykey").string == Right("value"))
      },
      test("Char key") {
        val result = jsonKey('K')
        assertTrue(result.get("K").string == Right("value"))
      },
      test("Boolean key") {
        val result = jsonKey(true)
        assertTrue(result.get("true").string == Right("value"))
      },
      test("Byte key") {
        val result = jsonKey(1.toByte)
        assertTrue(result.get("1").string == Right("value"))
      },
      test("Short key") {
        val result = jsonKey(2.toShort)
        assertTrue(result.get("2").string == Right("value"))
      },
      test("Int key") {
        val result = jsonKey(42)
        assertTrue(result.get("42").string == Right("value"))
      },
      test("Long key") {
        val result = jsonKey(100L)
        assertTrue(result.get("100").string == Right("value"))
      },
      test("Float key") {
        val result = jsonKey(1.5f)
        assertTrue(result.get("1.5").string == Right("value"))
      },
      test("Double key") {
        val result = jsonKey(2.5)
        assertTrue(result.get("2.5").string == Right("value"))
      },
      test("BigDecimal key") {
        val result = jsonKey(BigDecimal("123.456"))
        assertTrue(result.get("123.456").string == Right("value"))
      },
      test("BigInt key") {
        val result = jsonKey(BigInt("12345"))
        assertTrue(result.get("12345").string == Right("value"))
      },
      test("Unit key") {
        val result = jsonKey(())
        assertTrue(result.get("()").string == Right("value"))
      },
      test("Duration key") {
        val result = jsonKey(Duration.ofHours(1))
        assertTrue(result.get("PT1H").string == Right("value"))
      },
      test("DayOfWeek key") {
        val result = jsonKey(DayOfWeek.MONDAY)
        assertTrue(result.get("MONDAY").string == Right("value"))
      },
      test("Instant key") {
        val result = jsonKey(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("2024-01-15T10:30:00Z").string == Right("value"))
      },
      test("LocalDate key") {
        val result = jsonKey(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("2024-01-15").string == Right("value"))
      },
      test("LocalDateTime key") {
        val result = jsonKey(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("2024-01-15T10:30").string == Right("value"))
      },
      test("LocalTime key") {
        val result = jsonKey(LocalTime.of(10, 30))
        assertTrue(result.get("10:30").string == Right("value"))
      },
      test("Month key") {
        val result = jsonKey(Month.JANUARY)
        assertTrue(result.get("JANUARY").string == Right("value"))
      },
      test("MonthDay key") {
        val result = jsonKey(MonthDay.of(1, 15))
        assertTrue(result.get("--01-15").string == Right("value"))
      },
      test("OffsetDateTime key") {
        val result = jsonKey(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("2024-01-15T10:30Z").string == Right("value"))
      },
      test("OffsetTime key") {
        val result = jsonKey(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("10:30Z").string == Right("value"))
      },
      test("Period key") {
        val result = jsonKey(Period.ofDays(30))
        assertTrue(result.get("P30D").string == Right("value"))
      },
      test("Year key") {
        val result = jsonKey(Year.of(2024))
        assertTrue(result.get("2024").string == Right("value"))
      },
      test("YearMonth key") {
        val result = jsonKey(YearMonth.of(2024, 1))
        assertTrue(result.get("2024-01").string == Right("value"))
      },
      test("ZoneOffset key") {
        val result = jsonKey(ZoneOffset.ofHours(5))
        assertTrue(result.get("+05:00").string == Right("value"))
      },
      test("ZoneId key") {
        val result = jsonKey(ZoneId.of("UTC"))
        assertTrue(result.get("UTC").string == Right("value"))
      },
      test("Currency key") {
        val result = jsonKey(Currency.getInstance("USD"))
        assertTrue(result.get("USD").string == Right("value"))
      },
      test("UUID key") {
        val result = jsonKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("550e8400-e29b-41d4-a716-446655440000").string == Right("value"))
      }
    ),
    suite("writeInString runtime dispatch")(
      test("String in string") {
        val result = jsonInString("hello")
        assertTrue(result.get("msg").string == Right("prefix-hello-suffix"))
      },
      test("Char in string") {
        val result = jsonInString('X')
        assertTrue(result.get("msg").string == Right("prefix-X-suffix"))
      },
      test("Boolean in string") {
        val result = jsonInString(true)
        assertTrue(result.get("msg").string == Right("prefix-true-suffix"))
      },
      test("Byte in string") {
        val result = jsonInString(42.toByte)
        assertTrue(result.get("msg").string == Right("prefix-42-suffix"))
      },
      test("Short in string") {
        val result = jsonInString(42.toShort)
        assertTrue(result.get("msg").string == Right("prefix-42-suffix"))
      },
      test("Int in string") {
        val result = jsonInString(42)
        assertTrue(result.get("msg").string == Right("prefix-42-suffix"))
      },
      test("Long in string") {
        val result = jsonInString(100L)
        assertTrue(result.get("msg").string == Right("prefix-100-suffix"))
      },
      test("Float in string") {
        val result = jsonInString(3.14f)
        assertTrue(result.get("msg").string == Right("prefix-3.14-suffix"))
      },
      test("Double in string") {
        val result = jsonInString(3.14)
        assertTrue(result.get("msg").string == Right("prefix-3.14-suffix"))
      },
      test("BigDecimal in string") {
        val result = jsonInString(BigDecimal("123.456"))
        assertTrue(result.get("msg").string == Right("prefix-123.456-suffix"))
      },
      test("BigInt in string") {
        val result = jsonInString(BigInt("12345"))
        assertTrue(result.get("msg").string == Right("prefix-12345-suffix"))
      },
      test("Unit in string") {
        val result = jsonInString(())
        assertTrue(result.get("msg").string == Right("prefix-()-suffix"))
      },
      test("Duration in string") {
        val result = jsonInString(Duration.ofHours(1))
        assertTrue(result.get("msg").string == Right("prefix-PT1H-suffix"))
      },
      test("DayOfWeek in string") {
        val result = jsonInString(DayOfWeek.MONDAY)
        assertTrue(result.get("msg").string == Right("prefix-MONDAY-suffix"))
      },
      test("Instant in string") {
        val result = jsonInString(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("msg").string == Right("prefix-2024-01-15T10:30:00Z-suffix"))
      },
      test("LocalDate in string") {
        val result = jsonInString(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("msg").string == Right("prefix-2024-01-15-suffix"))
      },
      test("LocalDateTime in string") {
        val result = jsonInString(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("msg").string == Right("prefix-2024-01-15T10:30-suffix"))
      },
      test("LocalTime in string") {
        val result = jsonInString(LocalTime.of(10, 30))
        assertTrue(result.get("msg").string == Right("prefix-10:30-suffix"))
      },
      test("Month in string") {
        val result = jsonInString(Month.JANUARY)
        assertTrue(result.get("msg").string == Right("prefix-JANUARY-suffix"))
      },
      test("MonthDay in string") {
        val result = jsonInString(MonthDay.of(1, 15))
        assertTrue(result.get("msg").string == Right("prefix---01-15-suffix"))
      },
      test("OffsetDateTime in string") {
        val result = jsonInString(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("msg").string == Right("prefix-2024-01-15T10:30Z-suffix"))
      },
      test("OffsetTime in string") {
        val result = jsonInString(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("msg").string == Right("prefix-10:30Z-suffix"))
      },
      test("Period in string") {
        val result = jsonInString(Period.ofDays(30))
        assertTrue(result.get("msg").string == Right("prefix-P30D-suffix"))
      },
      test("Year in string") {
        val result = jsonInString(Year.of(2024))
        assertTrue(result.get("msg").string == Right("prefix-2024-suffix"))
      },
      test("YearMonth in string") {
        val result = jsonInString(YearMonth.of(2024, 1))
        assertTrue(result.get("msg").string == Right("prefix-2024-01-suffix"))
      },
      test("ZoneOffset in string") {
        val result = jsonInString(ZoneOffset.ofHours(5))
        assertTrue(result.get("msg").string == Right("prefix-+05:00-suffix"))
      },
      test("ZoneId in string") {
        val result = jsonInString(ZoneId.of("UTC"))
        assertTrue(result.get("msg").string == Right("prefix-UTC-suffix"))
      },
      test("ZonedDateTime in string") {
        val result = jsonInString(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC")))
        assertTrue(result.get("msg").string.exists(_.contains("2024-01-15")))
      },
      test("Currency in string") {
        val result = jsonInString(Currency.getInstance("USD"))
        assertTrue(result.get("msg").string == Right("prefix-USD-suffix"))
      },
      test("UUID in string") {
        val result = jsonInString(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("msg").string == Right("prefix-550e8400-e29b-41d4-a716-446655440000-suffix"))
      }
    ),
    suite("writeKey via Map")(
      test("Map with String keys") {
        val result = jsonValue(Map("key1" -> 1, "key2" -> 2))
        assertTrue(
          result.get("v").get("key1").int == Right(1),
          result.get("v").get("key2").int == Right(2)
        )
      },
      test("Map with Int keys") {
        val result = jsonValue(Map(1 -> "a", 2 -> "b"))
        assertTrue(
          result.get("v").get("1").string == Right("a"),
          result.get("v").get("2").string == Right("b")
        )
      },
      test("Map with Boolean keys") {
        val result = jsonValue(Map(true -> "yes", false -> "no"))
        assertTrue(
          result.get("v").get("true").string == Right("yes"),
          result.get("v").get("false").string == Right("no")
        )
      },
      test("Map with Long keys") {
        val result = jsonValue(Map(100L -> "hundred"))
        assertTrue(result.get("v").get("100").string == Right("hundred"))
      },
      test("Map with Float keys") {
        val result = jsonValue(Map(1.5f -> "value"))
        assertTrue(result.get("v").get("1.5").string == Right("value"))
      },
      test("Map with Double keys") {
        val result = jsonValue(Map(2.5 -> "value"))
        assertTrue(result.get("v").get("2.5").string == Right("value"))
      },
      test("Map with BigDecimal keys") {
        val result = jsonValue(Map(BigDecimal("123.456") -> "value"))
        assertTrue(result.get("v").get("123.456").string == Right("value"))
      },
      test("Map with BigInt keys") {
        val result = jsonValue(Map(BigInt("12345") -> "value"))
        assertTrue(result.get("v").get("12345").string == Right("value"))
      },
      test("Map with Byte keys") {
        val result = jsonValue(Map(1.toByte -> "value"))
        assertTrue(result.get("v").get("1").string == Right("value"))
      },
      test("Map with Short keys") {
        val result = jsonValue(Map(2.toShort -> "value"))
        assertTrue(result.get("v").get("2").string == Right("value"))
      },
      test("Map with UUID keys") {
        val uuid   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val result = jsonValue(Map(uuid -> "value"))
        assertTrue(result.get("v").get(uuid.toString).string == Right("value"))
      },
      test("Map with LocalDate keys") {
        val date   = LocalDate.of(2024, 1, 15)
        val result = jsonValue(Map(date -> "value"))
        assertTrue(result.get("v").get("2024-01-15").string == Right("value"))
      },
      test("Map with Duration keys") {
        val dur    = Duration.ofHours(1)
        val result = jsonValue(Map(dur -> "value"))
        assertTrue(result.get("v").get("PT1H").string == Right("value"))
      },
      test("Map with Month keys") {
        val result = jsonValue(Map(Month.JANUARY -> "value"))
        assertTrue(result.get("v").get("JANUARY").string == Right("value"))
      },
      test("Map with DayOfWeek keys") {
        val result = jsonValue(Map(DayOfWeek.MONDAY -> "value"))
        assertTrue(result.get("v").get("MONDAY").string == Right("value"))
      },
      test("Map with ZoneId keys") {
        val result = jsonValue(Map(ZoneId.of("UTC") -> "value"))
        assertTrue(result.get("v").get("UTC").string == Right("value"))
      },
      test("Map with ZoneOffset keys") {
        val result = jsonValue(Map(ZoneOffset.ofHours(5) -> "value"))
        assertTrue(result.get("v").get("+05:00").string == Right("value"))
      },
      test("Map with Currency keys") {
        val result = jsonValue(Map(Currency.getInstance("USD") -> "value"))
        assertTrue(result.get("v").get("USD").string == Right("value"))
      },
      test("Map with Instant keys") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val result  = jsonValue(Map(instant -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30:00Z").string == Right("value"))
      },
      test("Map with LocalTime keys") {
        val result = jsonValue(Map(LocalTime.of(10, 30) -> "value"))
        assertTrue(result.get("v").get("10:30").string == Right("value"))
      },
      test("Map with LocalDateTime keys") {
        val result = jsonValue(Map(LocalDateTime.of(2024, 1, 15, 10, 30) -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30").string == Right("value"))
      },
      test("Map with MonthDay keys") {
        val result = jsonValue(Map(MonthDay.of(1, 15) -> "value"))
        assertTrue(result.get("v").get("--01-15").string == Right("value"))
      },
      test("Map with OffsetTime keys") {
        val result = jsonValue(Map(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC) -> "value"))
        assertTrue(result.get("v").get("10:30Z").string == Right("value"))
      },
      test("Map with OffsetDateTime keys") {
        val result = jsonValue(Map(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC) -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30Z").string == Right("value"))
      },
      test("Map with Period keys") {
        val result = jsonValue(Map(Period.ofDays(30) -> "value"))
        assertTrue(result.get("v").get("P30D").string == Right("value"))
      },
      test("Map with Year keys") {
        val result = jsonValue(Map(Year.of(2024) -> "value"))
        assertTrue(result.get("v").get("2024").string == Right("value"))
      },
      test("Map with YearMonth keys") {
        val result = jsonValue(Map(YearMonth.of(2024, 1) -> "value"))
        assertTrue(result.get("v").get("2024-01").string == Right("value"))
      },
      test("Map with ZonedDateTime keys") {
        val zdt    = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"))
        val result = jsonValue(Map(zdt -> "value"))
        assertTrue(result.get("v").one.isRight)
      },
      test("Map with fallback toString keys") {
        case class Custom(value: Int)
        val result = jsonValue(Map(Custom(42) -> "value"))
        assertTrue(result.get("v").one.isRight)
      }
    ),
    suite("writeRawString UTF-8 encoding")(
      test("2-byte UTF-8 characters (Latin Extended)") {
        val result = jsonInString("café")
        assertTrue(result.get("msg").string == Right("prefix-café-suffix"))
      },
      test("3-byte UTF-8 characters (CJK)") {
        val result = jsonInString("日本語")
        assertTrue(result.get("msg").string == Right("prefix-日本語-suffix"))
      },
      test("mixed UTF-8 byte lengths") {
        val result = jsonInString("Hello café 日本")
        assertTrue(result.get("msg").string == Right("prefix-Hello café 日本-suffix"))
      }
    ),
    suite("nested structures")(
      test("Option containing Map") {
        val result = jsonValue(Some(Map("x" -> 1)))
        assertTrue(result.get("v").get("x").int == Right(1))
      },
      test("List containing Maps") {
        val result = jsonValue(List(Map("a" -> 1), Map("b" -> 2)))
        assertTrue(
          result.get("v")(0).get("a").int == Right(1),
          result.get("v")(1).get("b").int == Right(2)
        )
      },
      test("Map containing Lists") {
        val result = jsonValue(Map("items" -> List(1, 2, 3)))
        assertTrue(
          result.get("v").get("items")(0).int == Right(1),
          result.get("v").get("items")(1).int == Right(2)
        )
      }
    )
  )
}
