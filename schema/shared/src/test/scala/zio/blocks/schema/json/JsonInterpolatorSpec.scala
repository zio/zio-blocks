package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.{containsString, isLeft}
import zio.test.TestAspect.exceptNative
import java.time._

object JsonInterpolatorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorSpec")(
    test("parses Json literal") {
      assertTrue(
        json""" "hello"""" == Json.str("hello"),
        json""""Привіт" """ == Json.str("Привіт"),
        json""" "★🎸🎧⋆｡°⋆" """ == Json.str("★🎸🎧⋆｡°⋆"),
        json"""42""" == Json.number(42),
        json"""true""" == Json.bool(true),
        json"""[1,0,-1]""" == Json.arr(Json.number(1), Json.number(0), Json.number(-1)),
        json"""{"name": "Alice", "age": 20}""" == Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(20)),
        json"""null""" == Json.Null
      )
    },
    test("supports interpolated String keys and values") {
      check(
        Gen.string(Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff)) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x),
          json"""{$x: "v"}""".get(x).string == Right("v")
        )
      ) && {
        val x = "★🎸🎧⋆｡°⋆"
        assertTrue(
          json"""{"★🎸🎧⋆｡°⋆": $x}""".get("★🎸🎧⋆｡°⋆").string == Right(x),
          json"""{$x: "★🎸🎧⋆｡°⋆"}""".get(x).string == Right("★🎸🎧⋆｡°⋆")
        )
      } && {
        val x = "★" * 100
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x),
          json"""{$x: "v"}""".get(x).string == Right("v")
        )
      }
    },
    test("supports interpolated Boolean keys and values") {
      check(Gen.boolean)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").boolean == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Byte keys and values") {
      check(Gen.byte)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").int.map(_.toByte) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Short keys and values") {
      check(Gen.short)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").int.map(_.toShort) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Int keys and values") {
      check(Gen.int)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").int == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Long keys and values") {
      check(Gen.long)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").long == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Float keys and values") {
      check(Gen.float)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").float == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Double keys and values") {
      check(Gen.double)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").double == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Char keys and values") {
      check(
        Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated BigDecimal keys and values") {
      check(Gen.bigDecimal(BigDecimal("-" + "9" * 100), BigDecimal("9" * 100)))(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").number == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated BigInt keys and values") {
      check(Gen.bigInt(BigInt("-" + "9" * 100), BigInt("9" * 100)))(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").number.map(_.toBigInt) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated DayOfWeek keys and values") {
      check(genDayOfWeek)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Duration keys and values") {
      check(genDuration)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Instant keys and values") {
      check(genInstant)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated LocalDate keys and values") {
      check(genLocalDate)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated LocalDateTime keys and values") {
      check(genLocalDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated LocalTime keys and values") {
      check(genLocalTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Month keys and values") {
      check(genMonth)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated MonthDay keys and values") {
      check(genMonthDay)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated OffsetDateTime keys and values") {
      check(genOffsetDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated OffsetTime keys and values") {
      check(genOffsetTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Period keys and values") {
      check(genPeriod)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Year values") {
      check(genYear)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string.map(_.toInt) == Right(x.getValue)
        )
      )
    },
    test("supports interpolated YearMonth values") {
      check(genYearMonth)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string.map(YearMonth.parse) == Right(x)
        )
      )
    },
    test("supports interpolated ZoneOffset keys and values") {
      check(genZoneOffset)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated ZoneId keys and values") {
      check(genZoneId)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated ZonedDateTime keys and values") {
      check(genZonedDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Currency keys and values") {
      check(Gen.currency)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated UUID keys and values") {
      check(Gen.uuid)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").string == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).string == Right("v")
        )
      )
    },
    test("supports interpolated Option values") {
      val some = Some("Alice")
      val none = None
      assertTrue(
        json"""{"x": $some}""".get("x").one == Right(Json.str(some.get)),
        json"""{"x": $none}""".get("x").one == Right(Json.Null)
      )
    },
    test("supports interpolated Null values") {
      val x: String = null
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Null))
    },
    test("supports interpolated Unit values") {
      val x: Unit = ()
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.obj()))
    },
    test("supports interpolated Json values") {
      val x = Json.obj("y" -> Json.number(1))
      assertTrue(json"""{"x": $x}""".get("x").get("y").int == Right(1))
    },
    test("supports interpolated Map values with String keys") {
      check(
        Gen.string(Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff)) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Boolean keys") {
      check(Gen.boolean)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Byte keys") {
      check(Gen.byte)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Short keys") {
      check(Gen.short)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Int keys") {
      check(Gen.int)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Long keys") {
      check(Gen.long)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Float keys") {
      check(Gen.float)(x =>
        assertTrue {
          val key = JsonBinaryCodec.floatCodec.encodeToString(x)
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(key -> Json.Null))
        }
      )
    },
    test("supports interpolated Map values with Double keys") {
      check(Gen.double)(x =>
        assertTrue {
          val key = JsonBinaryCodec.doubleCodec.encodeToString(x)
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(key -> Json.Null))
        }
      )
    },
    test("supports interpolated Map values with Char keys") {
      check(
        Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with BigDecima keys") {
      check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with BigInt keys") {
      check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with DayOfWeek keys") {
      check(genDayOfWeek)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Duration keys") {
      check(genDuration)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Instant keys") {
      check(genInstant)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalDate keys") {
      check(genLocalDate)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalDateTime keys") {
      check(genLocalDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalTime keys") {
      check(genLocalTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Month keys") {
      check(genMonth)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with MonthDay keys") {
      check(genMonthDay)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with OffsetDateTime keys") {
      check(genOffsetDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with OffsetTime keys") {
      check(genOffsetTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Period keys") {
      check(genPeriod)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZoneId keys") {
      check(genZoneId)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZoneOffset keys") {
      check(genZoneOffset)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZonedDateTime keys") {
      check(genZonedDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Currency keys") {
      check(Gen.currency)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with UUID keys") {
      check(Gen.uuid)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with 2 or more keys") {
      val x = Map(1 -> null, 2 -> null)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.obj("1" -> Json.Null, "2" -> Json.Null)))
    },
    test("supports interpolated Iterable values") {
      val x = Iterable(1, 2)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.arr(Json.number(1), Json.number(2))))
    },
    test("supports interpolated Array values") {
      val x = Array(1, 2)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.arr(Json.number(1), Json.number(2))))
    },
    test("supports interpolated keys and values of other types with overridden toString") {
      case class Person(name: String, age: Int) {
        override def toString: String = Person.jsonCodec.encodeToString(this)
      }

      object Person {
        implicit val schema: Schema[Person] = Schema.derived

        val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonBinaryCodecDeriver)
      }

      val x = Person("Alice", 20)
      assertTrue(
        json"""{"x": $x}""".get("x").one == Right(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(20))),
        json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
      )
    },
    // String literal interpolation tests
    test("supports String interpolation in string literals") {
      val name     = "Alice"
      val greeting = "Hello"
      assertTrue(
        json"""{"greeting": "Hello, $name!"}""".get("greeting").string == Right("Hello, Alice!"),
        json"""{"msg": "$greeting, World!"}""".get("msg").string == Right("Hello, World!"),
        json"""{"path": "/users/$name/profile"}""".get("path").string == Right("/users/Alice/profile")
      )
    },
    test("supports numeric types in string literals") {
      val x       = 42
      val y       = 3.14
      val big     = BigInt("12345678901234567890")
      val decimal = BigDecimal("123.456")

      assertTrue(
        json"""{"msg": "x is $x"}""".get("msg").string == Right("x is 42"),
        json"""{"msg": "y is $y"}""".get("msg").string == Right("y is 3.14"),
        json"""{"msg": "big is $big"}""".get("msg").string == Right("big is 12345678901234567890"),
        json"""{"msg": "decimal is $decimal"}""".get("msg").string == Right("decimal is 123.456")
      )
    },
    test("supports UUID and temporal types in string literals") {
      val userId  = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val date    = LocalDate.of(2024, 1, 15)
      val time    = LocalTime.of(14, 30, 0)
      val instant = Instant.parse("2024-01-15T14:30:00Z")

      assertTrue(
        json"""{"id": "user-$userId"}""".get("id").string == Right("user-550e8400-e29b-41d4-a716-446655440000"),
        json"""{"date": "Date: $date"}""".get("date").string == Right("Date: 2024-01-15"),
        json"""{"time": "Time: $time"}""".get("time").string == Right("Time: 14:30"),
        json"""{"timestamp": "At $instant"}""".get("timestamp").string == Right("At 2024-01-15T14:30:00Z")
      )
    },
    test("supports multiple interpolations in string literals") {
      val x    = 10
      val y    = 20
      val name = "Alice"
      val age  = 30

      assertTrue(
        json"""{"range": "$x to $y"}""".get("range").string == Right("10 to 20"),
        json"""{"info": "$name is $age years old"}""".get("info").string == Right("Alice is 30 years old"),
        json"""{"path": "/v$x/users/$name"}""".get("path").string == Right("/v10/users/Alice")
      )
    },
    test("supports expressions in string literals") {
      val x = 10
      assertTrue(
        json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").string == Right("20 to 30"),
        json"""{"sum": "Result: ${x + 5}"}""".get("sum").string == Right("Result: 15")
      )
    },
    test("escapes special characters in string literal interpolations") {
      val quote     = "\""
      val backslash = "\\"
      val newline   = "\n"
      val tab       = "\t"

      assertTrue(
        json"""{"quote": "Value: $quote"}""".get("quote").string == Right("Value: \""),
        json"""{"backslash": "Value: $backslash"}""".get("backslash").string == Right("Value: \\"),
        json"""{"newline": "Value: $newline"}""".get("newline").string == Right("Value: \n"),
        json"""{"tab": "Value: $tab"}""".get("tab").string == Right("Value: \t")
      )
    },
    test("supports mixed interpolation contexts") {
      val key     = "userId"
      val userId  = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val version = 3
      val data    = Map("status" -> "active")

      assertTrue(
        // key position, string literal, and value position
        json"""{$key: "user-$userId", "version": $version, "data": $data}""".get("userId").string == Right(
          "user-550e8400-e29b-41d4-a716-446655440000"
        ),
        json"""{$key: "user-$userId", "version": $version, "data": $data}""".get("version").int == Right(3),
        json"""{$key: "user-$userId", "version": $version, "data": $data}""".get("data").get("status").string == Right(
          "active"
        )
      )
    },
    test("supports DayOfWeek, Month, and Currency in string literals") {
      val day      = DayOfWeek.MONDAY
      val month    = Month.JANUARY
      val currency = java.util.Currency.getInstance("USD")

      assertTrue(
        json"""{"day": "Today is $day"}""".get("day").string == Right("Today is MONDAY"),
        json"""{"month": "Month: $month"}""".get("month").string == Right("Month: JANUARY"),
        json"""{"currency": "Currency: $currency"}""".get("currency").string == Right("Currency: USD")
      )
    },
    test("supports Duration, Period, and Zone types in string literals") {
      val duration   = Duration.ofHours(2)
      val period     = Period.ofDays(7)
      val zoneId     = ZoneId.of("America/New_York")
      val zoneOffset = ZoneOffset.ofHours(-5)

      assertTrue(
        json"""{"duration": "Duration: $duration"}""".get("duration").string == Right("Duration: PT2H"),
        json"""{"period": "Period: $period"}""".get("period").string == Right("Period: P7D"),
        json"""{"zone": "Zone: $zoneId"}""".get("zone").string == Right("Zone: America/New_York"),
        json"""{"offset": "Offset: $zoneOffset"}""".get("offset").string == Right("Offset: -05:00")
      )
    },
    test("supports Year, YearMonth, and MonthDay in string literals") {
      val year      = Year.of(2024)
      val yearMonth = YearMonth.of(2024, 1)
      val monthDay  = MonthDay.of(1, 15)

      assertTrue(
        json"""{"year": "Year: $year"}""".get("year").string == Right("Year: 2024"),
        json"""{"yearMonth": "YearMonth: $yearMonth"}""".get("yearMonth").string == Right("YearMonth: 2024-01"),
        json"""{"monthDay": "MonthDay: $monthDay"}""".get("monthDay").string == Right("MonthDay: --01-15")
      )
    },
    test("supports OffsetDateTime, OffsetTime, and ZonedDateTime in string literals") {
      val offsetDateTime = OffsetDateTime.parse("2024-01-15T14:30:00-05:00")
      val offsetTime     = OffsetTime.parse("14:30:00-05:00")
      val zonedDateTime  = ZonedDateTime.parse("2024-01-15T14:30:00-05:00[America/New_York]")

      assertTrue(
        json"""{"odt": "OffsetDateTime: $offsetDateTime"}""".get("odt").string == Right(
          "OffsetDateTime: 2024-01-15T14:30-05:00"
        ),
        json"""{"ot": "OffsetTime: $offsetTime"}""".get("ot").string == Right("OffsetTime: 14:30-05:00"),
        json"""{"zdt": "ZonedDateTime: $zonedDateTime"}""".get("zdt").string == Right(
          "ZonedDateTime: 2024-01-15T14:30-05:00[America/New_York]"
        )
      )
    },
    test("supports LocalDateTime in string literals") {
      val localDateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 0)

      assertTrue(
        json"""{"ldt": "LocalDateTime: $localDateTime"}""".get("ldt").string == Right("LocalDateTime: 2024-01-15T14:30")
      )
    },
    test("doesn't compile for invalid json") {
      typeCheck {
        """json"1e""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: unexpected end of input at: .")))) &&
      typeCheck {
        """json"[1,02]""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: illegal number with leading zero at: .at(1)"))))
    } @@ exceptNative,
    // Property-based tests for string literal interpolation
    test("property: Int values work in string literals") {
      check(Gen.int) { n =>
        assertTrue(json"""{"msg": "Value: $n"}""".get("msg").string == Right(s"Value: $n"))
      }
    },
    test("property: UUID values work in string literals") {
      check(Gen.uuid) { uuid =>
        assertTrue(json"""{"id": "user-$uuid"}""".get("id").string == Right(s"user-$uuid"))
      }
    },
    test("property: LocalDate values work in string literals") {
      check(genLocalDate) { date =>
        assertTrue(json"""{"date": "Date: $date"}""".get("date").string == Right(s"Date: $date"))
      }
    },
    test("property: Instant values work in string literals") {
      check(genInstant) { instant =>
        assertTrue(json"""{"ts": "At $instant"}""".get("ts").string == Right(s"At $instant"))
      }
    },
    // Compile-time error tests
    test("compile fails for non-stringable type in key position") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.json._
        case class Point(x: Int, y: Int)
        object Point { implicit val schema: Schema[Point] = Schema.derived }
        val p = Point(1, 2)
        json"{$p: \"value\"}"
      """).map(assert(_)(isLeft))
    } @@ exceptNative,
    test("compile fails for type without JsonEncoder in value position") {
      typeCheck("""
        import zio.blocks.schema.json._
        case class NoSchema(x: Int)
        val v = NoSchema(1)
        json"{\"value\": $v}"
      """).map(assert(_)(isLeft))
    } @@ exceptNative,
    test("compile fails for non-stringable type in string literal") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.json._
        case class Point(x: Int, y: Int)
        object Point { implicit val schema: Schema[Point] = Schema.derived }
        val p = Point(1, 2)
        json"{\"msg\": \"Point is $p\"}"
      """).map(assert(_)(isLeft))
    } @@ exceptNative
  )
}
