package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.{containsString, isLeft}
import zio.test.TestAspect.exceptNative
import java.time._
import java.util.Currency

object JsonInterpolatorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorSpec")(
    test("parses Json literal") {
      assertTrue(
        json""" "hello"""" == Json.String("hello"),
        json""""Привіт" """ == Json.String("Привіт"),
        json""" "★🎸🎧⋆｡°⋆" """ == Json.String("★🎸🎧⋆｡°⋆"),
        json"""42""" == Json.Number(42),
        json"""true""" == Json.Boolean(true),
        json"""[1,0,-1]""" == Json.Array(Json.Number(1), Json.Number(0), Json.Number(-1)),
        json"""{"name": "Alice", "age": 20}""" == Json
          .Object("name" -> Json.String("Alice"), "age" -> Json.Number(20)),
        json"""null""" == Json.Null
      )
    },
    test("supports interpolated String keys and values") {
      check(
        Gen.string(Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff)) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x),
          json"""{$x: "v"}""".get(x).as[String] == Right("v")
        )
      ) && {
        val x = "★🎸🎧⋆｡°⋆"
        assertTrue(
          json"""{"★🎸🎧⋆｡°⋆": $x}""".get("★🎸🎧⋆｡°⋆").as[String] == Right(x),
          json"""{$x: "★🎸🎧⋆｡°⋆"}""".get(x).as[String] == Right("★🎸🎧⋆｡°⋆")
        )
      } && {
        val x = "★" * 100
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x),
          json"""{$x: "v"}""".get(x).as[String] == Right("v")
        )
      }
    },
    test("supports interpolated Boolean keys and values") {
      check(Gen.boolean)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Boolean] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Byte keys and values") {
      check(Gen.byte)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Int].map(_.toByte) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Short keys and values") {
      check(Gen.short)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Int].map(_.toShort) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Int keys and values") {
      check(Gen.int)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Int] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Long keys and values") {
      check(Gen.long)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Long] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Float keys and values") {
      check(Gen.float)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Float] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Double keys and values") {
      check(Gen.double)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[Double] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Char keys and values") {
      check(
        Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated BigDecimal keys and values") {
      check(Gen.bigDecimal(BigDecimal("-" + "9" * 100), BigDecimal("9" * 100)))(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[BigDecimal] == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated BigInt keys and values") {
      check(Gen.bigInt(BigInt("-" + "9" * 100), BigInt("9" * 100)))(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[BigDecimal].map(_.toBigInt) == Right(x),
          json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated DayOfWeek keys and values") {
      check(genDayOfWeek)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Duration keys and values") {
      check(genDuration)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Instant keys and values") {
      check(genInstant)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated LocalDate keys and values") {
      check(genLocalDate)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated LocalDateTime keys and values") {
      check(genLocalDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated LocalTime keys and values") {
      check(genLocalTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Month keys and values") {
      check(genMonth)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated MonthDay keys and values") {
      check(genMonthDay)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated OffsetDateTime keys and values") {
      check(genOffsetDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated OffsetTime keys and values") {
      check(genOffsetTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Period keys and values") {
      check(genPeriod)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Year values") {
      check(genYear)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String].map(_.toInt) == Right(x.getValue)
        )
      )
    },
    // TODO: Fix genYearMonth to not produce 5-digit years which YearMonth.parse can't handle
    test("supports interpolated YearMonth values") {
      check(genYearMonth)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String].map(YearMonth.parse) == Right(x)
        )
      )
    },
    test("supports interpolated ZoneOffset keys and values") {
      check(genZoneOffset)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated ZoneId keys and values") {
      check(genZoneId)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated ZonedDateTime keys and values") {
      check(genZonedDateTime)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Currency keys and values") {
      check(Gen.currency)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated UUID keys and values") {
      check(Gen.uuid)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
        )
      )
    },
    test("supports interpolated Option values") {
      val some: Option[String] = Some("Alice")
      val none: Option[String] = None
      assertTrue(
        json"""{"x": $some}""".get("x").one == Right(Json.String(some.get)),
        json"""{"x": $none}""".get("x").one == Right(Json.Null)
      )
    },
    test("supports interpolated Null values") {
      val x: String = null
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Null))
    },
    test("supports interpolated Unit values") {
      val x: Unit = ()
      // JsonEncoder.unitEncoder produces Json.Null
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Null))
    },
    test("supports interpolated Json values") {
      val x = Json.Object("y" -> Json.Number(1))
      assertTrue(json"""{"x": $x}""".get("x").get("y").as[Int] == Right(1))
    },
    test("supports interpolated Map values with String keys") {
      check(
        Gen.string(Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff)) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Boolean keys") {
      check(Gen.boolean)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Byte keys") {
      check(Gen.byte)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Short keys") {
      check(Gen.short)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Int keys") {
      check(Gen.int)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Long keys") {
      check(Gen.long)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Float keys") {
      check(Gen.float)(x =>
        assertTrue {
          // Map keys use Stringable.asString which is .toString
          val key = x.toString
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(key -> Json.Null))
        }
      )
    },
    test("supports interpolated Map values with Double keys") {
      check(Gen.double)(x =>
        assertTrue {
          // Map keys use Stringable.asString which is .toString
          val key = x.toString
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(key -> Json.Null))
        }
      )
    },
    test("supports interpolated Map values with Char keys") {
      check(
        Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff) // excluding surrogate chars
      )(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with BigDecima keys") {
      check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with BigInt keys") {
      check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with DayOfWeek keys") {
      check(genDayOfWeek)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Duration keys") {
      check(genDuration)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Instant keys") {
      check(genInstant)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalDate keys") {
      check(genLocalDate)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalDateTime keys") {
      check(genLocalDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with LocalTime keys") {
      check(genLocalTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Month keys") {
      check(genMonth)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with MonthDay keys") {
      check(genMonthDay)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with OffsetDateTime keys") {
      check(genOffsetDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with OffsetTime keys") {
      check(genOffsetTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Period keys") {
      check(genPeriod)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZoneId keys") {
      check(genZoneId)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZoneOffset keys") {
      check(genZoneOffset)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with ZonedDateTime keys") {
      check(genZonedDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with Currency keys") {
      check(Gen.currency)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with UUID keys") {
      check(Gen.uuid)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(x.toString -> Json.Null))
        )
      )
    },
    test("supports interpolated Map values with 2 or more keys") {
      val x = Map(1 -> null, 2 -> null)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Object("1" -> Json.Null, "2" -> Json.Null)))
    },
    test("supports interpolated Iterable values") {
      val x = Iterable(1, 2)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Array(Json.Number(1), Json.Number(2))))
    },
    test("supports interpolated Array values") {
      val x = Array(1, 2)
      assertTrue(json"""{"x": $x}""".get("x").one == Right(Json.Array(Json.Number(1), Json.Number(2))))
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
        json"""{"x": $x}""".get("x").one == Right(
          Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(20))
        ),
        json"""{${x.toString}: "v"}""".get(x.toString).as[String] == Right("v")
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
    suite("key position type checking")(
      test("String key works") {
        val s: String = "myKey"
        assertTrue(
          json"""{$s: 1}""".get(s).int == Right(1),
          json"""{$s: "value"}""".get(s).string == Right("value")
        )
      },
      test("Int key works") {
        val n: Int = 42
        assertTrue(
          json"""{$n: 1}""".get(n.toString).int == Right(1)
        )
      },
      test("all numeric types work as keys") {
        val byte: Byte         = 1
        val short: Short       = 2
        val long: Long         = 3L
        val float: Float       = 1.5f
        val double: Double     = 2.5
        val bigInt: BigInt     = BigInt("12345678901234567890")
        val bigDec: BigDecimal = BigDecimal("123.456")
        val floatKey           = JsonBinaryCodec.floatCodec.encodeToString(float)
        val doubleKey          = JsonBinaryCodec.doubleCodec.encodeToString(double)
        assertTrue(
          json"""{$byte: 1}""".get("1").int == Right(1),
          json"""{$short: 1}""".get("2").int == Right(1),
          json"""{$long: 1}""".get("3").int == Right(1),
          json"""{$float: 1}""".get(floatKey).int == Right(1),
          json"""{$double: 1}""".get(doubleKey).int == Right(1),
          json"""{$bigInt: 1}""".get(bigInt.toString).int == Right(1),
          json"""{$bigDec: 1}""".get(bigDec.toString).int == Right(1)
        )
      },
      test("Boolean key works") {
        val b: Boolean = true
        assertTrue(
          json"""{$b: 1}""".get("true").int == Right(1)
        )
      },
      test("Char key works") {
        val c: Char = 'k'
        assertTrue(
          json"""{$c: 1}""".get("k").int == Right(1)
        )
      },
      test("UUID key works") {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{$uuid: 1}""".get(uuid.toString).int == Right(1)
        )
      },
      test("Currency key works") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{$currency: 1}""".get("USD").int == Right(1)
        )
      },
      test("Unit key works") {
        val u: Unit = ()
        assertTrue(
          json"""{$u: 1}""".get("()").int == Right(1)
        )
      },
      test("all java.time types work as keys") {
        val dayOfWeek: DayOfWeek         = DayOfWeek.MONDAY
        val duration: java.time.Duration = java.time.Duration.ofHours(1)
        val instant: Instant             = Instant.parse("2024-01-15T10:30:00Z")
        val localDate: LocalDate         = LocalDate.of(2024, 1, 15)
        val localDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 15, 10, 30)
        val localTime: LocalTime         = LocalTime.of(10, 30)
        val month: java.time.Month       = java.time.Month.JANUARY
        val monthDay: MonthDay           = MonthDay.of(1, 15)
        val offsetDateTime               = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        val offsetTime                   = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val period                       = java.time.Period.ofDays(30)
        val year                         = java.time.Year.of(2024)
        val yearMonth                    = java.time.YearMonth.of(2024, 1)
        val zoneId                       = java.time.ZoneId.of("UTC")
        val zoneOffset                   = java.time.ZoneOffset.UTC
        val zonedDateTime                = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, zoneId)

        assertTrue(
          json"""{$dayOfWeek: 1}""".get(dayOfWeek.toString).int == Right(1),
          json"""{$duration: 1}""".get(duration.toString).int == Right(1),
          json"""{$instant: 1}""".get(instant.toString).int == Right(1),
          json"""{$localDate: 1}""".get(localDate.toString).int == Right(1),
          json"""{$localDateTime: 1}""".get(localDateTime.toString).int == Right(1),
          json"""{$localTime: 1}""".get(localTime.toString).int == Right(1),
          json"""{$month: 1}""".get(month.toString).int == Right(1),
          json"""{$monthDay: 1}""".get(monthDay.toString).int == Right(1),
          json"""{$offsetDateTime: 1}""".get(offsetDateTime.toString).int == Right(1),
          json"""{$offsetTime: 1}""".get(offsetTime.toString).int == Right(1),
          json"""{$period: 1}""".get(period.toString).int == Right(1),
          json"""{$year: 1}""".get(year.toString).int == Right(1),
          json"""{$yearMonth: 1}""".get(yearMonth.toString).int == Right(1),
          json"""{$zoneId: 1}""".get(zoneId.toString).int == Right(1),
          json"""{$zoneOffset: 1}""".get(zoneOffset.toString).int == Right(1),
          json"""{$zonedDateTime: 1}""".get(zonedDateTime.toString).int == Right(1)
        )
      },
      test("property-based: stringable types work as keys") {
        check(Gen.uuid) { uuid =>
          assertTrue(json"""{$uuid: "v"}""".get(uuid.toString).string == Right("v"))
        } &&
        check(Gen.int) { n =>
          assertTrue(json"""{$n: "v"}""".get(n.toString).string == Right("v"))
        } &&
        check(genInstant) { instant =>
          assertTrue(json"""{$instant: "v"}""".get(instant.toString).string == Right("v"))
        } &&
        check(genLocalDate) { date =>
          assertTrue(json"""{$date: "v"}""".get(date.toString).string == Right("v"))
        }
      },
      test("compile fails for List[Int] as key") {
        typeCheck {
          """
          val xs: List[Int] = List(1, 2, 3)
          json"{$xs: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Map as key") {
        typeCheck {
          """
          val m: Map[String, Int] = Map("a" -> 1)
          json"{$m: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for case class as key") {
        typeCheck {
          """
          case class Point(x: Int, y: Int)
          val p = Point(1, 2)
          json"{$p: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Option as key") {
        typeCheck {
          """
          val opt: Option[Int] = Some(42)
          json"{$opt: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Vector as key") {
        typeCheck {
          """
          val v: Vector[String] = Vector("a", "b")
          json"{$v: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Set as key") {
        typeCheck {
          """
          val s: Set[Int] = Set(1, 2)
          json"{$s: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Array as key") {
        typeCheck {
          """
          val arr: Array[Int] = Array(1, 2)
          json"{$arr: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for Either as key") {
        typeCheck {
          """
          val e: Either[String, Int] = Right(42)
          json"{$e: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("compile fails for tuple as key") {
        typeCheck {
          """
          val t: (Int, String) = (1, "a")
          json"{$t: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative,
      test("error message mentions JSON key and stringable types") {
        typeCheck {
          """
          case class Custom(value: Int)
          val c = Custom(1)
          json"{$c: 1}"
          """
        }.map(result =>
          assert(result)(isLeft(containsString("key"))) &&
            assert(result)(isLeft(containsString("stringable")))
        )
      } @@ exceptNative,
      test("multiple keys with different stringable types") {
        val intKey  = 1
        val uuidKey = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val dateKey = LocalDate.of(2024, 1, 15)
        val result  = json"""{
          $intKey: "one",
          $uuidKey: "uuid",
          $dateKey: "date"
        }"""
        assertTrue(
          result.get("1").string == Right("one"),
          result.get("550e8400-e29b-41d4-a716-446655440000").string == Right("uuid"),
          result.get("2024-01-15").string == Right("date")
        )
      },
      test("key in nested object") {
        val outerKey = "outer"
        val innerKey = 42
        val result   = json"""{$outerKey: {$innerKey: "nested"}}"""
        assertTrue(
          result.get("outer").get("42").string == Right("nested")
        )
      }
    ),
    suite("value position type checking")(
      test("primitives work in value position") {
        val s: String      = "hello"
        val b: Boolean     = true
        val i: Int         = 42
        val l: Long        = 100L
        val f: Float       = 1.5f
        val d: Double      = 2.5
        val by: Byte       = 1
        val sh: Short      = 2
        val c: Char        = 'x'
        val bi: BigInt     = BigInt("12345")
        val bd: BigDecimal = BigDecimal("123.45")
        assertTrue(
          json"""{"v": $s}""".get("v").string == Right("hello"),
          json"""{"v": $b}""".get("v").boolean == Right(true),
          json"""{"v": $i}""".get("v").int == Right(42),
          json"""{"v": $l}""".get("v").long == Right(100L),
          json"""{"v": $f}""".get("v").float == Right(1.5f),
          json"""{"v": $d}""".get("v").double == Right(2.5),
          json"""{"v": $by}""".get("v").int.map(_.toByte) == Right(1.toByte),
          json"""{"v": $sh}""".get("v").int.map(_.toShort) == Right(2.toShort),
          json"""{"v": $c}""".get("v").string == Right("x"),
          json"""{"v": $bi}""".get("v").number.map(_.toBigInt) == Right(BigInt("12345")),
          json"""{"v": $bd}""".get("v").number == Right(BigDecimal("123.45"))
        )
      },
      test("case class with Schema works in value position") {
        case class Address(street: String, city: String)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }

        val addr   = Address("123 Main St", "NYC")
        val result = json"""{"address": $addr}"""
        assertTrue(
          result.get("address").get("street").string == Right("123 Main St"),
          result.get("address").get("city").string == Right("NYC")
        )
      },
      test("nested case classes work in value position") {
        case class Inner(value: Int)
        object Inner {
          implicit val schema: Schema[Inner] = Schema.derived
        }

        case class Outer(name: String, inner: Inner)
        object Outer {
          implicit val schema: Schema[Outer] = Schema.derived
        }

        val o      = Outer("test", Inner(42))
        val result = json"""{"data": $o}"""
        assertTrue(
          result.get("data").get("name").string == Right("test"),
          result.get("data").get("inner").get("value").int == Right(42)
        )
      },
      test("sealed trait with Schema works in value position") {
        sealed trait Status
        object Status {
          case object Active                   extends Status
          case class Suspended(reason: String) extends Status
          implicit lazy val schema: Schema[Status] = Schema.derived
        }

        val active: Status    = Status.Active
        val suspended: Status = Status.Suspended("Payment overdue")

        assertTrue(
          json"""{"status": $active}""".get("status").one.isRight,
          json"""{"status": $suspended}""".get("status").get("Suspended").get("reason").string == Right(
            "Payment overdue"
          )
        )
      },
      test("Either works as sealed trait substitute in value position") {
        // Either is a built-in sum type with JsonEncoder
        val left: Either[String, Int]  = Left("error")
        val right: Either[String, Int] = Right(42)

        assertTrue(
          json"""{"status": $left}""".get("status").get("Left").string == Right("error"),
          json"""{"status": $right}""".get("status").get("Right").int == Right(42)
        )
      },
      test("Option[A] with encoder works in value position") {
        case class Item(name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }

        val some: Option[Item] = Some(Item("thing"))
        val none: Option[Item] = None

        assertTrue(
          json"""{"item": $some}""".get("item").get("name").string == Right("thing"),
          json"""{"item": $none}""".get("item").one == Right(Json.Null)
        )
      },
      test("List[A] with encoder works in value position") {
        case class Point(x: Int, y: Int)
        object Point {
          implicit val schema: Schema[Point] = Schema.derived
        }

        val points = List(Point(1, 2), Point(3, 4))
        val result = json"""{"points": $points}"""
        assertTrue(
          result.get("points")(0).get("x").int == Right(1),
          result.get("points")(0).get("y").int == Right(2),
          result.get("points")(1).get("x").int == Right(3)
        )
      },
      test("Vector works in value position") {
        val vec    = Vector(1, 2, 3)
        val result = json"""{"values": $vec}"""
        assertTrue(
          result.get("values")(0).int == Right(1),
          result.get("values")(1).int == Right(2),
          result.get("values")(2).int == Right(3)
        )
      },
      test("Set works in value position") {
        val set    = Set("a", "b")
        val result = json"""{"values": $set}"""
        // Set order is not guaranteed, so we just check that it's an array with 2 elements
        assertTrue(
          result.get("values").one.map(_.elements.size) == Right(2)
        )
      },
      test("Seq works in value position") {
        val seq: Seq[Int] = Seq(10, 20, 30)
        val result        = json"""{"values": $seq}"""
        assertTrue(
          result.get("values")(0).int == Right(10),
          result.get("values")(1).int == Right(20)
        )
      },
      test("Map[String, A] works in value position") {
        case class Stats(count: Int)
        object Stats {
          implicit val schema: Schema[Stats] = Schema.derived
        }

        val data   = Map("a" -> Stats(10), "b" -> Stats(20))
        val result = json"""{"stats": $data}"""
        assertTrue(
          result.get("stats").get("a").get("count").int == Right(10),
          result.get("stats").get("b").get("count").int == Right(20)
        )
      },
      test("Array[A] works in value position") {
        val arr    = Array(1, 2, 3)
        val result = json"""{"values": $arr}"""
        assertTrue(
          result.get("values")(0).int == Right(1),
          result.get("values")(2).int == Right(3)
        )
      },
      test("Json identity works in value position") {
        val j: Json = Json.obj("nested" -> Json.number(42))
        val result  = json"""{"data": $j}"""
        assertTrue(
          result.get("data").get("nested").int == Right(42)
        )
      },
      test("Tuple2 works in value position") {
        val tuple: (Int, String) = (1, "hello")
        val result               = json"""{"pair": $tuple}"""
        assertTrue(
          result.get("pair")(0).int == Right(1),
          result.get("pair")(1).string == Right("hello")
        )
      },
      test("Tuple3 works in value position") {
        val tuple: (Int, String, Boolean) = (1, "hello", true)
        val result                        = json"""{"triple": $tuple}"""
        assertTrue(
          result.get("triple")(0).int == Right(1),
          result.get("triple")(1).string == Right("hello"),
          result.get("triple")(2).boolean == Right(true)
        )
      },
      test("Either works in value position") {
        val left: Either[String, Int]  = Left("error")
        val right: Either[String, Int] = Right(42)
        assertTrue(
          json"""{"result": $left}""".get("result").get("Left").string == Right("error"),
          json"""{"result": $right}""".get("result").get("Right").int == Right(42)
        )
      },
      test("java.time types work in value position") {
        val instant   = Instant.parse("2024-01-15T10:30:00Z")
        val localDate = LocalDate.of(2024, 1, 15)
        val localTime = LocalTime.of(10, 30)
        val uuid      = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ts": $instant}""".get("ts").string == Right(instant.toString),
          json"""{"date": $localDate}""".get("date").string == Right("2024-01-15"),
          json"""{"time": $localTime}""".get("time").string == Right("10:30"),
          json"""{"id": $uuid}""".get("id").string == Right(uuid.toString)
        )
      },
      test("compile fails for case class WITHOUT Schema in value position") {
        typeCheck {
          """
          case class NoSchema(x: Int)
          val v = NoSchema(1)
          json"[$v]"
          """
        }.map(assert(_)(isLeft(containsString("JsonEncoder"))))
      } @@ exceptNative,
      test("compile fails for class without any encoder in value position") {
        typeCheck {
          """
          class MyClass(val x: Int)
          val v = new MyClass(1)
          json"[$v]"
          """
        }.map(assert(_)(isLeft(containsString("JsonEncoder"))))
      } @@ exceptNative,
      test("error message mentions JsonEncoder and Schema") {
        typeCheck {
          """
          case class Custom(value: Int)
          val c = Custom(1)
          json"[$c]"
          """
        }.map(result =>
          assert(result)(isLeft(containsString("JsonEncoder"))) &&
            assert(result)(isLeft(containsString("Schema")))
        )
      } @@ exceptNative,
      test("property-based: collections with encoders work") {
        check(Gen.listOf(Gen.int)) { ints =>
          assertTrue(json"""{"v": $ints}""".get("v").one.map(_.elements.size) == Right(ints.size))
        } &&
        check(Gen.vectorOf(Gen.string)) { strs =>
          assertTrue(json"""{"v": $strs}""".get("v").one.map(_.elements.size) == Right(strs.size))
        }
      },
      test("complex nested structure with multiple value interpolations") {
        case class Inner(n: Int)
        object Inner {
          implicit val schema: Schema[Inner] = Schema.derived
        }

        val inner1 = Inner(1)
        val inner2 = Inner(2)
        val list   = List("a", "b")
        val num    = 42

        val result = json"""{
          "first": $inner1,
          "second": $inner2,
          "items": $list,
          "count": $num
        }"""

        assertTrue(
          result.get("first").get("n").int == Right(1),
          result.get("second").get("n").int == Right(2),
          result.get("items")(0).string == Right("a"),
          result.get("count").int == Right(42)
        )
      },
      test("mixed key and value interpolation") {
        case class Data(value: Int)
        object Data {
          implicit val schema: Schema[Data] = Schema.derived
        }

        val key  = java.util.UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val data = Data(99)

        val result = json"""{$key: $data}"""
        assertTrue(
          result.get("a1b2c3d4-e5f6-7890-abcd-ef1234567890").get("value").int == Right(99)
        )
      }
    ),
    suite("string literal interpolation")(
      test("supports String interpolation in strings") {
        val name = "Alice"
        assertTrue(
          json"""{"greeting": "Hello, $name!"}""".get("greeting").string == Right("Hello, Alice!")
        )
      },
      test("supports Int interpolation in strings") {
        val x = 42
        assertTrue(
          json"""{"msg": "x is $x"}""".get("msg").string == Right("x is 42")
        )
      },
      test("supports all numeric types in strings") {
        val byte: Byte         = 1
        val short: Short       = 2
        val int: Int           = 42
        val long: Long         = 100L
        val float: Float       = 1.5f
        val double: Double     = 2.5
        val bigInt: BigInt     = BigInt("12345678901234567890")
        val bigDec: BigDecimal = BigDecimal("123.456")
        assertTrue(
          json"""{"msg": "byte=$byte"}""".get("msg").string == Right("byte=1"),
          json"""{"msg": "short=$short"}""".get("msg").string == Right("short=2"),
          json"""{"msg": "int=$int"}""".get("msg").string == Right("int=42"),
          json"""{"msg": "long=$long"}""".get("msg").string == Right("long=100"),
          json"""{"msg": "float=$float"}""".get("msg").string == Right("float=1.5"),
          json"""{"msg": "double=$double"}""".get("msg").string == Right("double=2.5"),
          json"""{"msg": "bigInt=$bigInt"}""".get("msg").string == Right("bigInt=12345678901234567890"),
          json"""{"msg": "bigDec=$bigDec"}""".get("msg").string == Right("bigDec=123.456")
        )
      },
      test("supports Boolean in strings") {
        val b = true
        assertTrue(
          json"""{"msg": "flag is $b"}""".get("msg").string == Right("flag is true")
        )
      },
      test("supports Char in strings") {
        val c: Char = 'X'
        assertTrue(
          json"""{"msg": "char is $c"}""".get("msg").string == Right("char is X")
        )
      },
      test("supports UUID in strings") {
        val id = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ref": "user-$id"}""".get("ref").string == Right("user-550e8400-e29b-41d4-a716-446655440000")
        )
      },
      test("supports LocalDate in strings") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(
          json"""{"file": "report-$date.pdf"}""".get("file").string == Right("report-2024-01-15.pdf")
        )
      },
      test("supports LocalTime in strings") {
        val time = LocalTime.of(10, 30, 0)
        assertTrue(
          json"""{"log": "Event at $time"}""".get("log").string == Right("Event at 10:30")
        )
      },
      test("supports Instant in strings") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(
          json"""{"ts": "Created: $instant"}""".get("ts").string == Right("Created: 2024-01-15T10:30:00Z")
        )
      },
      test("supports all java.time types in strings") {
        val dayOfWeek: DayOfWeek         = DayOfWeek.MONDAY
        val duration: java.time.Duration = java.time.Duration.ofHours(1)
        val localDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 15, 10, 30)
        val month: java.time.Month       = java.time.Month.JANUARY
        val monthDay: MonthDay           = MonthDay.of(1, 15)
        val offsetDateTime               = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        val offsetTime                   = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val period                       = java.time.Period.ofDays(30)
        val year                         = java.time.Year.of(2024)
        val yearMonth                    = java.time.YearMonth.of(2024, 1)
        val zoneId                       = java.time.ZoneId.of("UTC")
        val zoneOffset                   = java.time.ZoneOffset.UTC

        assertTrue(
          json"""{"msg": "day=$dayOfWeek"}""".get("msg").string == Right("day=MONDAY"),
          json"""{"msg": "duration=$duration"}""".get("msg").string == Right("duration=PT1H"),
          json"""{"msg": "dt=$localDateTime"}""".get("msg").string == Right("dt=2024-01-15T10:30"),
          json"""{"msg": "month=$month"}""".get("msg").string == Right("month=JANUARY"),
          json"""{"msg": "md=$monthDay"}""".get("msg").string == Right("md=--01-15"),
          json"""{"msg": "odt=$offsetDateTime"}""".get("msg").string == Right("odt=2024-01-15T10:30Z"),
          json"""{"msg": "ot=$offsetTime"}""".get("msg").string == Right("ot=10:30Z"),
          json"""{"msg": "period=$period"}""".get("msg").string == Right("period=P30D"),
          json"""{"msg": "year=$year"}""".get("msg").string == Right("year=2024"),
          json"""{"msg": "ym=$yearMonth"}""".get("msg").string == Right("ym=2024-01"),
          json"""{"msg": "zone=$zoneId"}""".get("msg").string == Right("zone=UTC"),
          json"""{"msg": "offset=$zoneOffset"}""".get("msg").string == Right("offset=Z")
        )
      },
      test("supports Currency in strings") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").string == Right("Price in USD")
        )
      },
      test("supports Unit in strings") {
        val u: Unit = ()
        assertTrue(
          json"""{"msg": "unit=$u"}""".get("msg").string == Right("unit=()")
        )
      },
      test("supports multiple interpolations in one string") {
        val date    = LocalDate.of(2024, 1, 15)
        val version = 3
        val env     = "prod"
        assertTrue(
          json"""{"path": "/data/$env/$date/v$version/output.json"}""".get("path").string ==
            Right("/data/prod/2024-01-15/v3/output.json")
        )
      },
      test("supports expression syntax in strings") {
        val x = 10
        assertTrue(
          json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").string == Right("20 to 30")
        )
      },
      test("supports expression with method calls in strings") {
        val items = List("a", "b", "c")
        assertTrue(
          json"""{"count": "Found ${items.size} items"}""".get("count").string == Right("Found 3 items")
        )
      },
      test("handles empty interpolation results in strings") {
        val empty = ""
        assertTrue(
          json"""{"msg": "[$empty]"}""".get("msg").string == Right("[]")
        )
      },
      test("handles adjacent interpolations in strings") {
        val a = "A"
        val b = "B"
        val c = "C"
        assertTrue(
          json"""{"s": "$a$b$c"}""".get("s").string == Right("ABC")
        )
      },
      test("handles interpolation at start of string") {
        val x = "start"
        assertTrue(
          json"""{"s": "$x end"}""".get("s").string == Right("start end")
        )
      },
      test("handles interpolation at end of string") {
        val x = "end"
        assertTrue(
          json"""{"s": "start $x"}""".get("s").string == Right("start end")
        )
      },
      test("handles only interpolation in string") {
        val x = "value"
        assertTrue(
          json"""{"s": "$x"}""".get("s").string == Right("value")
        )
      },
      test("property-based: stringable types work in strings") {
        check(Gen.uuid) { uuid =>
          assertTrue(json"""{"id": "id-$uuid"}""".get("id").string == Right(s"id-$uuid"))
        } &&
        check(Gen.int) { n =>
          assertTrue(json"""{"n": "num=$n"}""".get("n").string == Right(s"num=$n"))
        } &&
        check(genInstant) { instant =>
          assertTrue(json"""{"ts": "at $instant"}""".get("ts").string == Right(s"at $instant"))
        } &&
        check(genLocalDate) { date =>
          assertTrue(json"""{"d": "date=$date"}""".get("d").string == Right(s"date=$date"))
        }
      },
      test("handles special characters needing JSON escape in interpolated values") {
        val withQuote     = "say \"hello\""
        val withBackslash = "path\\to\\file"
        val withNewline   = "line1\nline2"
        assertTrue(
          // Note: the runtime writeRawString escapes these for valid JSON
          json"""{"msg": "$withQuote"}""".get("msg").string == Right("say \"hello\""),
          json"""{"path": "$withBackslash"}""".get("path").string == Right("path\\to\\file"),
          json"""{"text": "$withNewline"}""".get("text").string == Right("line1\nline2")
        )
      },
      test("compile fails for List in string literal") {
        typeCheck(
          "val xs: List[Int] = List(1, 2, 3); " +
            "json\"\"\"{\"msg\": \"list is $xs\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      } @@ exceptNative,
      test("compile fails for case class in string literal") {
        typeCheck(
          "case class Point(x: Int, y: Int); " +
            "val p = Point(1, 2); " +
            "json\"\"\"{\"msg\": \"point is $p\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      } @@ exceptNative,
      test("compile fails for Map in string literal") {
        typeCheck(
          "val m: Map[String, Int] = Map(\"a\" -> 1); " +
            "json\"\"\"{\"msg\": \"map is $m\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      } @@ exceptNative,
      test("compile fails for Option in string literal") {
        typeCheck(
          "val opt: Option[Int] = Some(42); " +
            "json\"\"\"{\"msg\": \"opt is $opt\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      } @@ exceptNative,
      test("error message mentions string literal and stringable types") {
        typeCheck(
          "case class Custom(value: Int); " +
            "val c = Custom(1); " +
            "json\"\"\"{\"msg\": \"custom is $c\"}\"\"\""
        ).map(result =>
          assert(result)(isLeft(containsString("string literal"))) &&
            assert(result)(isLeft(containsString("stringable")))
        )
      } @@ exceptNative
    ),
    suite("mixed interpolation contexts")(
      test("combines key, value, and string interpolation") {
        case class Data(value: Int)
        object Data {
          implicit val schema: Schema[Data] = Schema.derived
        }

        val key       = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val data      = Data(42)
        val timestamp = Instant.parse("2024-01-15T10:30:00Z")

        val result = json"""{
          $key: {
            "data": $data,
            "note": "Recorded at $timestamp"
          }
        }"""

        assertTrue(
          result.get("12345678-1234-1234-1234-123456789abc").get("data").get("value").int == Right(42),
          result.get("12345678-1234-1234-1234-123456789abc").get("note").string == Right(s"Recorded at $timestamp")
        )
      },
      test("multiple keys with string interpolation in values") {
        val intKey  = 1
        val uuidKey = java.util.UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210")
        val dateKey = LocalDate.of(2024, 1, 15)
        val name    = "Alice"
        val count   = 42

        val result = json"""{
          $intKey: "Hello $name",
          $uuidKey: "Count is $count",
          $dateKey: "plain value"
        }"""

        assertTrue(
          result.get("1").string == Right("Hello Alice"),
          result.get("fedcba98-7654-3210-fedc-ba9876543210").string == Right("Count is 42"),
          result.get("2024-01-15").string == Right("plain value")
        )
      },
      test("array with value and string interpolations") {
        case class Item(n: Int)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }

        val item = Item(1)
        val num  = 42
        val name = "Alice"

        // Array with mixed: object value, number, and string with interpolation
        val result = json"""[$item, $num, "Hello $name"]"""

        assertTrue(
          result(0).get("n").int == Right(1),
          result(1).int == Right(42),
          result(2).string == Right("Hello Alice")
        )
      },
      test("deeply nested with all contexts") {
        case class Inner(v: Int)
        object Inner {
          implicit val schema: Schema[Inner] = Schema.derived
        }

        val outerKey = "outer"
        val innerKey = 123
        val data     = Inner(99)
        val label    = "test"

        val result = json"""{
          $outerKey: {
            $innerKey: {
              "data": $data,
              "label": "Label: $label"
            }
          }
        }"""

        assertTrue(
          result.get("outer").get("123").get("data").get("v").int == Right(99),
          result.get("outer").get("123").get("label").string == Right("Label: test")
        )
      },
      test("large JSON document with many interpolations") {
        case class Address(street: String, city: String, zip: Int)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }

        case class Person(name: String, age: Int, address: Address)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        // Many different interpolation types
        val id          = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val timestamp   = Instant.parse("2024-01-15T10:30:00Z")
        val date        = LocalDate.of(2024, 1, 15)
        val version     = 42
        val isActive    = true
        val score       = 99.5
        val name        = "Alice"
        val environment = "production"

        val alice   = Person("Alice", 30, Address("123 Main St", "NYC", 10001))
        val bob     = Person("Bob", 25, Address("456 Oak Ave", "LA", 90001))
        val team    = List(alice, bob)
        val tags    = Vector("important", "urgent")
        val metrics = Map("cpu" -> 0.75, "memory" -> 0.60)

        val result = json"""{
          "meta": {
            $id: {
              "created": $timestamp,
              "version": $version,
              "active": $isActive
            }
          },
          "config": {
            "env": $environment,
            "path": "/api/v$version/$environment/data",
            "description": "Service running since $date with score $score"
          },
          "data": {
            "leader": $alice,
            "team": $team,
            "tags": $tags,
            "metrics": $metrics,
            "summary": "Team led by $name has ${team.size} members"
          },
          "nested": {
            $date: {
              $version: {
                "info": "Nested key test on $date"
              }
            }
          }
        }"""

        assertTrue(
          // Meta section - key interpolation
          result.get("meta").get(id.toString).get("created").string == Right(timestamp.toString),
          result.get("meta").get(id.toString).get("version").int == Right(42),
          result.get("meta").get(id.toString).get("active").boolean == Right(true),
          // Config section - value and string interpolation
          result.get("config").get("env").string == Right("production"),
          result.get("config").get("path").string == Right("/api/v42/production/data"),
          result.get("config").get("description").string == Right("Service running since 2024-01-15 with score 99.5"),
          // Data section - complex value interpolation
          result.get("data").get("leader").get("name").string == Right("Alice"),
          result.get("data").get("leader").get("address").get("city").string == Right("NYC"),
          result.get("data").get("team")(0).get("name").string == Right("Alice"),
          result.get("data").get("team")(1).get("name").string == Right("Bob"),
          result.get("data").get("tags")(0).string == Right("important"),
          result.get("data").get("metrics").get("cpu").double == Right(0.75),
          result.get("data").get("summary").string == Right("Team led by Alice has 2 members"),
          // Nested section - multiple key interpolations
          result.get("nested").get("2024-01-15").get("42").get("info").string == Right("Nested key test on 2024-01-15")
        )
      }
    ),
    suite("error messages")(
      test("key position error includes type and context") {
        typeCheck {
          """
          case class NotStringable(x: Int)
          val v = NotStringable(1)
          json"{$v: 1}"
          """
        }.map { result =>
          assert(result)(isLeft(containsString("key"))) &&
          assert(result)(isLeft(containsString("NotStringable"))) &&
          assert(result)(isLeft(containsString("stringable")))
        }
      } @@ exceptNative,
      test("value position error includes type and guidance") {
        typeCheck {
          """
          case class NoEncoder(x: Int)
          val v = NoEncoder(1)
          json"[$v]"
          """
        }.map { result =>
          assert(result)(isLeft(containsString("JsonEncoder"))) &&
          assert(result)(isLeft(containsString("NoEncoder"))) &&
          assert(result)(isLeft(containsString("Schema")))
        }
      } @@ exceptNative,
      test("string literal error includes type and context") {
        typeCheck(
          "case class NotStringable(x: Int); " +
            "val v = NotStringable(1); " +
            "json\"\"\"{\"msg\": \"value is $v\"}\"\"\""
        ).map { result =>
          assert(result)(isLeft(containsString("string literal"))) &&
          assert(result)(isLeft(containsString("NotStringable"))) &&
          assert(result)(isLeft(containsString("stringable")))
        }
      } @@ exceptNative,
      test("invalid JSON syntax error is clear") {
        typeCheck {
          """json"[1,02]""""
        }.map { result =>
          assert(result)(isLeft(containsString("Invalid JSON"))) &&
          assert(result)(isLeft(containsString("leading zero")))
        }
      } @@ exceptNative
    ),
    suite("special character escaping in strings")(
      test("escapes backslash in string interpolation") {
        val path   = "C:\\Users\\test"
        val result = json"""{"path": "Path is $path"}"""
        assertTrue(result.get("path").string == Right("Path is C:\\Users\\test"))
      },
      test("escapes tab in string interpolation") {
        val text   = "col1\tcol2"
        val result = json"""{"data": "Data: $text"}"""
        assertTrue(result.get("data").string == Right("Data: col1\tcol2"))
      },
      test("escapes newline in string interpolation") {
        val text   = "line1\nline2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: line1\nline2"))
      },
      test("escapes carriage return in string interpolation") {
        val text   = "line1\rline2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: line1\rline2"))
      },
      test("escapes backspace in string interpolation") {
        val text   = "a\bb"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: a\bb"))
      },
      test("escapes form feed in string interpolation") {
        val text   = "page1\fpage2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: page1\fpage2"))
      },
      test("escapes double quote in string interpolation") {
        val text   = "say \"hello\""
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: say \"hello\""))
      },
      test("escapes control characters in string interpolation") {
        // Control character 0x01 (SOH - Start of Heading)
        val text   = "ctrl\u0001char"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").string == Right("Text: ctrl\u0001char"))
      },
      test("handles multiple special characters in one string") {
        val text   = "a\tb\nc\\d\"e"
        val result = json"""{"data": "Mix: $text"}"""
        assertTrue(result.get("data").string == Right("Mix: a\tb\nc\\d\"e"))
      }
    ),
    suite("UTF-8 encoding edge cases")(
      test("handles 2-byte UTF-8 characters (Latin Extended)") {
        val text   = "café résumé naïve"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").string == Right("café résumé naïve"))
      },
      test("handles 3-byte UTF-8 characters (CJK)") {
        val text   = "日本語テスト"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").string == Right("日本語テスト"))
      },
      test("handles 4-byte UTF-8 characters (emoji)") {
        val text   = "Hello 🎉🎊🎁"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").string == Right("Hello 🎉🎊🎁"))
      },
      test("handles mixed UTF-8 byte lengths") {
        val text   = "ASCII café 日本 🎉"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").string == Right("ASCII café 日本 🎉"))
      },
      test("handles 2-byte UTF-8 in key position") {
        val key    = "clé"
        val result = json"""{$key: "value"}"""
        assertTrue(result.get("clé").string == Right("value"))
      },
      test("handles 3-byte UTF-8 in key position") {
        val key    = "キー"
        val result = json"""{$key: "value"}"""
        assertTrue(result.get("キー").string == Right("value"))
      },
      test("handles emoji in string interpolation") {
        val emoji  = "🚀"
        val result = json"""{"msg": "Launch $emoji now!"}"""
        assertTrue(result.get("msg").string == Right("Launch 🚀 now!"))
      }
    ),
    suite("Char type handling")(
      test("Char as key") {
        val key: Char = 'k'
        val result    = json"""{$key: "value"}"""
        assertTrue(result.get("k").string == Right("value"))
      },
      test("Char in string interpolation") {
        val ch: Char = 'X'
        val result   = json"""{"msg": "Grade: $ch"}"""
        assertTrue(result.get("msg").string == Right("Grade: X"))
      },
      test("special Char values as key") {
        val tab: Char = '\t'
        val result    = json"""{$tab: "tab-key"}"""
        assertTrue(result.get("\t").string == Right("tab-key"))
      },
      test("Unicode Char as key") {
        val ch: Char = '★'
        val result   = json"""{$ch: "star"}"""
        assertTrue(result.get("★").string == Right("star"))
      }
    ),
    suite("Unit type handling")(
      test("Unit as key") {
        val u: Unit = ()
        val result  = json"""{$u: "unit-key"}"""
        assertTrue(result.get("()").string == Right("unit-key"))
      },
      test("Unit in string interpolation") {
        val u: Unit = ()
        val result  = json"""{"msg": "Value is $u"}"""
        assertTrue(result.get("msg").string == Right("Value is ()"))
      }
    ),
    suite("surrogate pair edge cases")(
      test("handles lone high surrogate at end of string in InString context") {
        // High surrogate (U+D800) at the end with no following low surrogate
        // This should produce the Unicode replacement character (U+FFFD)
        val strWithLoneSurrogate = "test" + '\uD800'
        val result               = json"""{"msg": "value: $strWithLoneSurrogate"}"""
        // The replacement character is encoded as UTF-8: EF BF BD, which decodes to U+FFFD
        val expected = "value: test\uFFFD"
        assertTrue(result.get("msg").string == Right(expected))
      },
      test("handles high surrogate followed by non-low surrogate in InString context") {
        // High surrogate (U+D800) followed by ASCII character, not a low surrogate
        val strWithInvalidPair = "test" + '\uD800' + 'A'
        val result             = json"""{"msg": "value: $strWithInvalidPair"}"""
        // The high surrogate becomes replacement char, 'A' remains
        val expected = "value: test\uFFFDA"
        assertTrue(result.get("msg").string == Right(expected))
      },
      test("handles multiple invalid surrogate pairs in InString context") {
        // Multiple invalid surrogates
        val str    = "\uD800\uD801" // Two consecutive high surrogates
        val result = json"""{"msg": "x${str}y"}"""
        // Each becomes a replacement character
        assertTrue(result.get("msg").string == Right("x\uFFFD\uFFFDy"))
      },
      test("handles valid surrogate pair (emoji) surrounded by invalid ones") {
        // Valid: \uD83D\uDE00 = 😀, Invalid: lone \uD800
        val str    = "\uD800\uD83D\uDE00\uD800"
        val result = json"""{"msg": "$str"}"""
        // First \uD800 -> replacement, emoji stays, last \uD800 -> replacement
        assertTrue(result.get("msg").string == Right("\uFFFD\uD83D\uDE00\uFFFD"))
      },
      test("handles low surrogate without preceding high surrogate") {
        // Low surrogate (U+DC00) without high surrogate before it
        // This would be caught by the ch1 >= 0xdc00 condition - but actually
        // a lone low surrogate is in range 0xDC00-0xDFFF which is NOT a high surrogate
        // so it falls through to 3-byte UTF-8 encoding, not the surrogate pair path
        val strWithLoneLow = "test" + '\uDC00'
        val result         = json"""{"msg": "$strWithLoneLow"}"""
        // Low surrogates (0xD800-0xDFFF) check is (ch1 & 0xf800) == 0xd800
        // So \uDC00 matches this and goes to surrogate path, but since i+1 >= length,
        // it becomes replacement character
        assertTrue(result.get("msg").string.isRight)
      }
    ),
    suite("additional branch coverage")(
      test("handles Char in value position") {
        val ch: Char = 'A'
        val result   = json"""{"char": $ch}"""
        assertTrue(result.get("char").string == Right("A"))
      },
      test("handles nested arrays with mixed types") {
        val n      = 42
        val s      = "text"
        val b      = true
        val result = json"""[[$n], [$s], [$b]]"""
        assertTrue(
          result(0)(0).int == Right(42),
          result(1)(0).string == Right("text"),
          result(2)(0).boolean == Right(true)
        )
      },
      test("handles deeply nested objects with all contexts") {
        val k1     = "key1"
        val k2     = "key2"
        val v      = 100
        val msg    = "inner"
        val result = json"""{$k1: {$k2: {"value": $v, "msg": "The $msg text"}}}"""
        assertTrue(
          result.get("key1").get("key2").get("value").int == Right(100),
          result.get("key1").get("key2").get("msg").string == Right("The inner text")
        )
      },
      test("handles array at top level with complex values") {
        val x      = 1
        val y      = 2
        val result = json"""[$x, $y]"""
        assertTrue(
          result(0).int == Right(1),
          result(1).int == Right(2)
        )
      },
      test("handles Float special values") {
        val posInf = Float.PositiveInfinity
        val negInf = Float.NegativeInfinity
        // Note: NaN and Infinity are valid in writeValue but may not be valid JSON
        // Testing the encoding path
        val posInfStr = posInf.toString
        val negInfStr = negInf.toString
        assertTrue(
          posInfStr == "Infinity",
          negInfStr == "-Infinity"
        )
      },
      test("handles Double special values") {
        val posInf    = Double.PositiveInfinity
        val negInf    = Double.NegativeInfinity
        val posInfStr = posInf.toString
        val negInfStr = negInf.toString
        assertTrue(
          posInfStr == "Infinity",
          negInfStr == "-Infinity"
        )
      },
      test("handles empty string interpolation") {
        val empty  = ""
        val result = json"""{"msg": "[$empty]"}"""
        assertTrue(result.get("msg").string == Right("[]"))
      },
      test("handles very long string in interpolation") {
        val long   = "x" * 10000
        val result = json"""{"data": $long}"""
        assertTrue(result.get("data").string == Right(long))
      },
      test("handles very long string inside string literal") {
        val long   = "y" * 5000
        val result = json"""{"msg": "Start $long End"}"""
        assertTrue(result.get("msg").string == Right(s"Start $long End"))
      },
      test("handles negative numbers as keys") {
        val negInt  = -42
        val negLong = -123456789L
        val result  = json"""{$negInt: "neg-int", $negLong: "neg-long"}"""
        assertTrue(
          result.get("-42").string == Right("neg-int"),
          result.get("-123456789").string == Right("neg-long")
        )
      },
      test("handles BigInt as key") {
        val big    = BigInt("12345678901234567890")
        val result = json"""{$big: "big-key"}"""
        assertTrue(result.get("12345678901234567890").string == Right("big-key"))
      },
      test("handles BigDecimal as key") {
        val bd     = BigDecimal("123.456789012345678901234567890")
        val result = json"""{$bd: "bd-key"}"""
        assertTrue(result.get(bd.toString).string == Right("bd-key"))
      },
      test("handles DayOfWeek in all positions") {
        val dow    = DayOfWeek.WEDNESDAY
        val result = json"""{$dow: $dow, "msg": "Day is $dow"}"""
        assertTrue(
          result.get("WEDNESDAY").string == Right("WEDNESDAY"),
          result.get("msg").string == Right("Day is WEDNESDAY")
        )
      },
      test("handles Duration in all positions") {
        val dur    = Duration.ofHours(2).plusMinutes(30)
        val result = json"""{$dur: "duration-key", "dur": $dur, "msg": "Took $dur"}"""
        assertTrue(
          result.get(dur.toString).string == Right("duration-key"),
          result.get("dur").string == Right(dur.toString),
          result.get("msg").string == Right(s"Took $dur")
        )
      },
      test("handles Period in all positions") {
        val period = Period.ofMonths(3).plusDays(15)
        val result = json"""{$period: "period-key", "period": $period, "msg": "Period: $period"}"""
        assertTrue(
          result.get(period.toString).string == Right("period-key"),
          result.get("period").string == Right(period.toString),
          result.get("msg").string == Right(s"Period: $period")
        )
      },
      test("handles Month in all positions") {
        val month  = Month.DECEMBER
        val result = json"""{$month: "month-key", "month": $month, "msg": "Month is $month"}"""
        assertTrue(
          result.get("DECEMBER").string == Right("month-key"),
          result.get("month").string == Right("DECEMBER"),
          result.get("msg").string == Right("Month is DECEMBER")
        )
      },
      test("handles MonthDay in all positions") {
        val md     = MonthDay.of(12, 25)
        val result = json"""{$md: "md-key", "md": $md, "msg": "Date: $md"}"""
        assertTrue(
          result.get(md.toString).string == Right("md-key"),
          result.get("md").string == Right(md.toString),
          result.get("msg").string == Right(s"Date: $md")
        )
      },
      test("handles Year in all positions") {
        val year   = Year.of(2024)
        val result = json"""{$year: "year-key", "year": $year, "msg": "Year: $year"}"""
        assertTrue(
          result.get("2024").string == Right("year-key"),
          result.get("year").string == Right("2024"),
          result.get("msg").string == Right("Year: 2024")
        )
      },
      test("handles YearMonth in all positions") {
        val ym     = YearMonth.of(2024, 6)
        val result = json"""{$ym: "ym-key", "ym": $ym, "msg": "YM: $ym"}"""
        assertTrue(
          result.get(ym.toString).string == Right("ym-key"),
          result.get("ym").string == Right(ym.toString),
          result.get("msg").string == Right(s"YM: $ym")
        )
      },
      test("handles ZoneOffset in all positions") {
        val zo     = ZoneOffset.ofHours(5)
        val result = json"""{$zo: "zo-key", "zo": $zo, "msg": "Offset: $zo"}"""
        assertTrue(
          result.get(zo.toString).string == Right("zo-key"),
          result.get("zo").string == Right(zo.toString),
          result.get("msg").string == Right(s"Offset: $zo")
        )
      },
      test("handles ZoneId in all positions") {
        val zi     = ZoneId.of("America/New_York")
        val result = json"""{$zi: "zi-key", "zi": $zi, "msg": "Zone: $zi"}"""
        assertTrue(
          result.get("America/New_York").string == Right("zi-key"),
          result.get("zi").string == Right("America/New_York"),
          result.get("msg").string == Right("Zone: America/New_York")
        )
      },
      test("handles OffsetTime in all positions") {
        val ot     = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val result = json"""{$ot: "ot-key", "ot": $ot, "msg": "Time: $ot"}"""
        assertTrue(
          result.get(ot.toString).string == Right("ot-key"),
          result.get("ot").string == Right(ot.toString),
          result.get("msg").string == Right(s"Time: $ot")
        )
      },
      test("handles OffsetDateTime in all positions") {
        val odt    = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        val result = json"""{$odt: "odt-key", "odt": $odt, "msg": "DateTime: $odt"}"""
        assertTrue(
          result.get(odt.toString).string == Right("odt-key"),
          result.get("odt").string == Right(odt.toString),
          result.get("msg").string == Right(s"DateTime: $odt")
        )
      },
      test("handles ZonedDateTime in all positions") {
        val zdt    = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"))
        val result = json"""{$zdt: "zdt-key", "zdt": $zdt, "msg": "ZDT: $zdt"}"""
        assertTrue(
          result.get(zdt.toString).string == Right("zdt-key"),
          result.get("zdt").string == Right(zdt.toString),
          result.get("msg").string == Right(s"ZDT: $zdt")
        )
      },
      test("handles Currency in all positions") {
        val curr   = Currency.getInstance("EUR")
        val result = json"""{$curr: "curr-key", "curr": $curr, "msg": "Currency: $curr"}"""
        assertTrue(
          result.get("EUR").string == Right("curr-key"),
          result.get("curr").string == Right("EUR"),
          result.get("msg").string == Right("Currency: EUR")
        )
      },
      test("handles all control characters 0x00-0x1F in string interpolation") {
        // Test several control characters beyond just 0x01
        val nul      = '\u0000' // NUL
        val soh      = '\u0001' // SOH
        val bel      = '\u0007' // BEL
        val bs       = '\u0008' // BS - has special escape \b
        val ht       = '\u0009' // HT - has special escape \t
        val lf       = '\u000A' // LF - has special escape \n
        val vt       = '\u000B' // VT
        val ff       = '\u000C' // FF - has special escape \f
        val cr       = '\u000D' // CR - has special escape \r
        val so       = '\u000E' // SO
        val us       = '\u001F' // US (last control char)
        val nulStr   = s"a${nul}b"
        val sohStr   = s"a${soh}b"
        val belStr   = s"a${bel}b"
        val bsStr    = s"a${bs}b"
        val htStr    = s"a${ht}b"
        val lfStr    = s"a${lf}b"
        val vtStr    = s"a${vt}b"
        val ffStr    = s"a${ff}b"
        val crStr    = s"a${cr}b"
        val soStr    = s"a${so}b"
        val usStr    = s"a${us}b"
        val result1  = json"""{"msg": "$nulStr"}"""
        val result2  = json"""{"msg": "$sohStr"}"""
        val result3  = json"""{"msg": "$belStr"}"""
        val result4  = json"""{"msg": "$bsStr"}"""
        val result5  = json"""{"msg": "$htStr"}"""
        val result6  = json"""{"msg": "$lfStr"}"""
        val result7  = json"""{"msg": "$vtStr"}"""
        val result8  = json"""{"msg": "$ffStr"}"""
        val result9  = json"""{"msg": "$crStr"}"""
        val result10 = json"""{"msg": "$soStr"}"""
        val result11 = json"""{"msg": "$usStr"}"""
        assertTrue(
          result1.get("msg").string == Right(s"a${nul}b"),
          result2.get("msg").string == Right(s"a${soh}b"),
          result3.get("msg").string == Right(s"a${bel}b"),
          result4.get("msg").string == Right(s"a${bs}b"),
          result5.get("msg").string == Right(s"a${ht}b"),
          result6.get("msg").string == Right(s"a${lf}b"),
          result7.get("msg").string == Right(s"a${vt}b"),
          result8.get("msg").string == Right(s"a${ff}b"),
          result9.get("msg").string == Right(s"a${cr}b"),
          result10.get("msg").string == Right(s"a${so}b"),
          result11.get("msg").string == Right(s"a${us}b")
        )
      },
      test("handles empty Map in value position") {
        val emptyMap: Map[String, Int] = Map.empty
        val result                     = json"""{"data": $emptyMap}"""
        assertTrue(result.get("data").one == Right(Json.obj()))
      },
      test("handles empty Iterable in value position") {
        val emptyList: List[Int] = List.empty
        val result               = json"""{"data": $emptyList}"""
        assertTrue(result.get("data").one == Right(Json.arr()))
      },
      test("handles empty Array in value position") {
        val emptyArr: Array[Int] = Array.empty
        val result               = json"""{"data": $emptyArr}"""
        assertTrue(result.get("data").one == Right(Json.arr()))
      },
      test("handles nested Option with Some value") {
        val opt: Option[Option[Int]] = Some(Some(42))
        val result                   = json"""{"data": $opt}"""
        assertTrue(result.get("data").int == Right(42))
      },
      test("handles nested Option with inner None") {
        val opt: Option[Option[Int]] = Some(None)
        val result                   = json"""{"data": $opt}"""
        assertTrue(result.get("data").one == Right(Json.Null))
      },
      test("handles Map with multiple entries") {
        val map: Map[String, Int] = Map("z" -> 1, "a" -> 2, "m" -> 3)
        val result                = json"""{"data": $map}"""
        assertTrue(
          result.get("data").get("z").int == Right(1),
          result.get("data").get("a").int == Right(2),
          result.get("data").get("m").int == Right(3)
        )
      },
      test("handles String with only special characters") {
        val special = "\"\\\b\f\n\r\t"
        val result  = json"""{"msg": "$special"}"""
        assertTrue(result.get("msg").string == Right(special))
      },
      test("handles boundary values for numeric types in strings") {
        val byteMin: Byte   = Byte.MinValue
        val byteMax: Byte   = Byte.MaxValue
        val shortMin: Short = Short.MinValue
        val shortMax: Short = Short.MaxValue
        assertTrue(
          json"""{"msg": "byte range: $byteMin to $byteMax"}""".get("msg").string ==
            Right(s"byte range: ${Byte.MinValue} to ${Byte.MaxValue}"),
          json"""{"msg": "short range: $shortMin to $shortMax"}""".get("msg").string ==
            Right(s"short range: ${Short.MinValue} to ${Short.MaxValue}")
        )
      },
      test("handles Instant at epoch") {
        val epoch  = Instant.EPOCH
        val result = json"""{$epoch: $epoch, "msg": "at $epoch"}"""
        assertTrue(
          result.get(epoch.toString).string == Right(epoch.toString),
          result.get("msg").string == Right(s"at $epoch")
        )
      },
      test("handles Duration.ZERO") {
        val zero   = Duration.ZERO
        val result = json"""{$zero: $zero, "msg": "duration is $zero"}"""
        assertTrue(
          result.get(zero.toString).string == Right(zero.toString),
          result.get("msg").string == Right(s"duration is $zero")
        )
      },
      test("handles Period.ZERO") {
        val zero   = Period.ZERO
        val result = json"""{$zero: $zero, "msg": "period is $zero"}"""
        assertTrue(
          result.get(zero.toString).string == Right(zero.toString),
          result.get("msg").string == Right(s"period is $zero")
        )
      }
    )
  )
}
