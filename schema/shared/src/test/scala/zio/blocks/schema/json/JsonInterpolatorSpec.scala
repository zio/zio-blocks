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
    test("supports List values (special runtime handling)") {
      val numbers = List(1, 2, 3)
      assertTrue(
        json"""{"nums": $numbers}""".get("nums").one == Right(Json.arr(Json.number(1), Json.number(2), Json.number(3)))
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

    // Comprehensive string literal interpolation tests
    suite("string literal interpolation")(
      test("supports String interpolation in strings") {
        val name = "Alice"
        assertTrue(
          json"""{"greeting": "Hello, $name!"}""".get("greeting").string == Right("Hello, Alice!")
        )
      },

      test("supports numeric types in strings") {
        val x   = 42
        val y   = 3.14
        val big = BigInt("12345678901234567890")

        assertTrue(
          json"""{"msg": "x is $x"}""".get("msg").string == Right("x is 42"),
          json"""{"msg": "y is $y"}""".get("msg").string == Right("y is 3.14"),
          json"""{"msg": "big is $big"}""".get("msg").string == Right("big is 12345678901234567890")
        )
      },

      test("supports UUID in strings") {
        val id = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ref": "user-$id"}""".get("ref").string == Right("user-550e8400-e29b-41d4-a716-446655440000")
        )
      },

      test("supports temporal types in strings") {
        val date    = LocalDate.of(2024, 1, 15)
        val time    = LocalTime.of(10, 30, 0)
        val instant = Instant.parse("2024-01-15T10:30:00Z")

        assertTrue(
          json"""{"file": "report-$date.pdf"}""".get("file").string == Right("report-2024-01-15.pdf"),
          json"""{"log": "Event at $time"}""".get("log").string == Right("Event at 10:30"),
          json"""{"ts": "Created: $instant"}""".get("ts").string == Right("Created: 2024-01-15T10:30:00Z")
        )
      },

      test("supports additional temporal types in strings") {
        val dayOfWeek      = DayOfWeek.MONDAY
        val duration       = Duration.ofHours(2)
        val month          = Month.JANUARY
        val monthDay       = MonthDay.of(3, 15)
        val offsetTime     = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val period         = Period.ofDays(7)
        val year           = Year.of(2024)
        val yearMonth      = YearMonth.of(2024, 3)
        val zoneOffset     = ZoneOffset.ofHours(5)
        val zoneId         = ZoneId.of("UTC")
        val zonedDateTime  = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"))
        val localDateTime  = LocalDateTime.of(2024, 1, 15, 10, 30)
        val offsetDateTime = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)

        assertTrue(
          json"""{"dow": "Day: $dayOfWeek"}""".get("dow").string == Right("Day: MONDAY"),
          json"""{"dur": "Duration: $duration"}""".get("dur").string == Right("Duration: PT2H"),
          json"""{"mon": "Month: $month"}""".get("mon").string == Right("Month: JANUARY"),
          json"""{"md": "Date: $monthDay"}""".get("md").string == Right("Date: --03-15"),
          json"""{"ot": "Time: $offsetTime"}""".get("ot").string == Right("Time: 10:30Z"),
          json"""{"per": "Period: $period"}""".get("per").string == Right("Period: P7D"),
          json"""{"yr": "Year: $year"}""".get("yr").string == Right("Year: 2024"),
          json"""{"ym": "YearMonth: $yearMonth"}""".get("ym").string == Right("YearMonth: 2024-03"),
          json"""{"zo": "Offset: $zoneOffset"}""".get("zo").string == Right("Offset: +05:00"),
          json"""{"zi": "Zone: $zoneId"}""".get("zi").string == Right("Zone: UTC"),
          json"""{"zdt": "ZonedDT: $zonedDateTime"}""".get("zdt").string.isRight,
          json"""{"ldt": "LocalDT: $localDateTime"}""".get("ldt").string == Right("LocalDT: 2024-01-15T10:30"),
          json"""{"odt": "OffsetDT: $offsetDateTime"}""".get("odt").string == Right("OffsetDT: 2024-01-15T10:30Z")
        )
      },

      test("supports numeric primitives in strings") {
        val byte: Byte   = 127
        val short: Short = 32000
        val char: Char   = 'A'
        val float: Float = 3.14f

        assertTrue(
          json"""{"byte": "Value: $byte"}""".get("byte").string == Right("Value: 127"),
          json"""{"short": "Value: $short"}""".get("short").string == Right("Value: 32000"),
          json"""{"char": "Char: $char"}""".get("char").string == Right("Char: A"),
          json"""{"float": "Float: $float"}""".get("float").string == Right("Float: 3.14")
        )
      },

      test("supports Currency in strings") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").string == Right("Price in USD")
        )
      },

      test("supports computed values in strings") {
        val x      = 10
        val result = x * 2
        val items  = List("a", "b", "c")
        val count  = items.size

        assertTrue(
          json"""{"result": "Result is $result"}""".get("result").string == Right("Result is 20"),
          json"""{"count": "Found $count items"}""".get("count").string == Right("Found 3 items")
        )
      },

      test("supports multiple values combined in strings") {
        val date     = LocalDate.of(2024, 1, 15)
        val filename = s"report-$date-v3.pdf"

        assertTrue(
          json"""{"file": "$filename"}""".get("file").string ==
            Right("report-2024-01-15-v3.pdf")
        )
      },

      test("handles empty interpolation results") {
        val empty = ""
        assertTrue(
          json"""{"msg": "[$empty]"}""".get("msg").string == Right("[]")
        )
      },

      test("handles special characters in interpolated strings") {
        val path = "foo/bar"

        assertTrue(
          json"""{"url": "http://example.com/$path"}""".get("url").string ==
            Right("http://example.com/foo/bar")
        )
      },

      test("handles multiple interpolations in same string literal") {
        val first  = "Hello"
        val second = "World"

        assertTrue(
          json"""{"msg": "$first $second!"}""".get("msg").string == Right("Hello World!")
        )
      },

      test("escapes JSON special characters in string literals") {
        val withQuotes    = """He said "hello""""
        val withBackslash = """path\to\file"""
        val withNewline   = "line1\nline2"
        val withTab       = "col1\tcol2"

        assertTrue(
          json"""{"quoted": "$withQuotes"}""".get("quoted").string == Right("""He said "hello""""),
          json"""{"backslash": "$withBackslash"}""".get("backslash").string == Right("""path\to\file"""),
          json"""{"newline": "$withNewline"}""".get("newline").string == Right("line1\nline2"),
          json"""{"tab": "$withTab"}""".get("tab").string == Right("col1\tcol2")
        )
      },

      test("handles control characters in string literals") {
        val withControl = "test\u0001\u001fend"

        assertTrue(
          json"""{"control": "$withControl"}""".get("control").string == Right("test\u0001\u001fend")
        )
      }
    ),

    // Mixed context tests
    suite("mixed interpolation contexts")(
      test("combines key and string interpolation") {
        val key       = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val timestamp = Instant.parse("2024-01-15T10:30:00Z")

        val result = json"""{
          $key: {
            "note": "Recorded at $timestamp"
          }
        }"""

        assertTrue(
          result.get(key.toString).get("note").string == Right("Recorded at 2024-01-15T10:30:00Z")
        )
      },

      test("multiple keys with different stringable types") {
        val intKey  = 1
        val uuidKey = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val dateKey = LocalDate.of(2024, 1, 15)

        val result = json"""{
          $intKey: "one",
          $uuidKey: "uuid",
          $dateKey: "date"
        }"""

        assertTrue(
          result.get("1").string == Right("one"),
          result.get(uuidKey.toString).string == Right("uuid"),
          result.get("2024-01-15").string == Right("date")
        )
      },

      test("array with mixed value types") {
        val num  = 42
        val str  = "hello"
        val bool = true

        val result = json"""[$num, $str, $bool]"""

        assertTrue(
          result(0).int == Right(42),
          result(1).string == Right("hello"),
          result(2).boolean == Right(true)
        )
      },

      test("handles null values in string literals") {
        val nullValue: String = null

        assertTrue(
          json"""{"msg": "Value: $nullValue"}""".get("msg").string == Right("Value: null")
        )
      },

      test("handles BigDecimal and BigInt in keys") {
        val bdKey = BigDecimal("123.456")
        val biKey = BigInt("999999999999999999")

        assertTrue(
          json"""{$bdKey: "decimal", $biKey: "bigint"}""".get("123.456").string == Right("decimal"),
          json"""{$bdKey: "decimal", $biKey: "bigint"}""".get("999999999999999999").string == Right("bigint")
        )
      }
    )
  )
}
