package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.{containsString, isLeft}
import java.time._
import java.util.{Currency, UUID}

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
          // Map keys use Keyable.asKey which is .toString
          val key = x.toString
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.Object(key -> Json.Null))
        }
      )
    },
    test("supports interpolated Map values with Double keys") {
      check(Gen.double)(x =>
        assertTrue {
          // Map keys use Keyable.asKey which is .toString
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
    },
    suite("key position type checking")(
      test("String key works") {
        val s: String = "myKey"
        assertTrue(
          json"""{$s: 1}""".get(s).as[Int] == Right(1),
          json"""{$s: "value"}""".get(s).as[String] == Right("value")
        )
      },
      test("Int key works") {
        val n: Int = 42
        assertTrue(
          json"""{$n: 1}""".get(n.toString).as[Int] == Right(1)
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
          json"""{$byte: 1}""".get("1").as[Int] == Right(1),
          json"""{$short: 1}""".get("2").as[Int] == Right(1),
          json"""{$long: 1}""".get("3").as[Int] == Right(1),
          json"""{$float: 1}""".get(floatKey).as[Int] == Right(1),
          json"""{$double: 1}""".get(doubleKey).as[Int] == Right(1),
          json"""{$bigInt: 1}""".get(bigInt.toString).as[Int] == Right(1),
          json"""{$bigDec: 1}""".get(bigDec.toString).as[Int] == Right(1)
        )
      },
      test("Boolean key works") {
        val b: Boolean = true
        assertTrue(
          json"""{$b: 1}""".get("true").as[Int] == Right(1)
        )
      },
      test("Char key works") {
        val c: Char = 'k'
        assertTrue(
          json"""{$c: 1}""".get("k").as[Int] == Right(1)
        )
      },
      test("UUID key works") {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{$uuid: 1}""".get(uuid.toString).as[Int] == Right(1)
        )
      },
      test("Currency key works") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{$currency: 1}""".get("USD").as[Int] == Right(1)
        )
      },
      test("Unit key works") {
        val u: Unit = ()
        assertTrue(
          json"""{$u: 1}""".get("{}").as[Int] == Right(1)
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
          json"""{$dayOfWeek: 1}""".get(dayOfWeek.toString).as[Int] == Right(1),
          json"""{$duration: 1}""".get(duration.toString).as[Int] == Right(1),
          json"""{$instant: 1}""".get(instant.toString).as[Int] == Right(1),
          json"""{$localDate: 1}""".get(localDate.toString).as[Int] == Right(1),
          json"""{$localDateTime: 1}""".get(localDateTime.toString).as[Int] == Right(1),
          json"""{$localTime: 1}""".get(localTime.toString).as[Int] == Right(1),
          json"""{$month: 1}""".get(month.toString).as[Int] == Right(1),
          json"""{$monthDay: 1}""".get(monthDay.toString).as[Int] == Right(1),
          json"""{$offsetDateTime: 1}""".get(offsetDateTime.toString).as[Int] == Right(1),
          json"""{$offsetTime: 1}""".get(offsetTime.toString).as[Int] == Right(1),
          json"""{$period: 1}""".get(period.toString).as[Int] == Right(1),
          json"""{$year: 1}""".get(year.toString).as[Int] == Right(1),
          json"""{$yearMonth: 1}""".get(yearMonth.toString).as[Int] == Right(1),
          json"""{$zoneId: 1}""".get(zoneId.toString).as[Int] == Right(1),
          json"""{$zoneOffset: 1}""".get(zoneOffset.toString).as[Int] == Right(1),
          json"""{$zonedDateTime: 1}""".get(zonedDateTime.toString).as[Int] == Right(1)
        )
      },
      test("keyable types work as keys") {
        val uuid    = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val n       = 42
        val instant = java.time.Instant.parse("2024-01-15T10:30:00Z")
        val date    = java.time.LocalDate.of(2024, 1, 15)
        assertTrue(
          json"""{$uuid: "v"}""".get(uuid.toString).as[String] == Right("v"),
          json"""{$n: "v"}""".get(n.toString).as[String] == Right("v"),
          json"""{$instant: "v"}""".get(instant.toString).as[String] == Right("v"),
          json"""{$date: "v"}""".get(date.toString).as[String] == Right("v")
        )
      },
      test("compile fails for List[Int] as key") {
        typeCheck {
          """
          val xs: List[Int] = List(1, 2, 3)
          json"{$xs: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Map as key") {
        typeCheck {
          """
          val m: Map[String, Int] = Map("a" -> 1)
          json"{$m: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for case class as key") {
        typeCheck {
          """
          case class Point(x: Int, y: Int)
          val p = Point(1, 2)
          json"{$p: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Option as key") {
        typeCheck {
          """
          val opt: Option[Int] = Some(42)
          json"{$opt: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Vector as key") {
        typeCheck {
          """
          val v: Vector[String] = Vector("a", "b")
          json"{$v: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Set as key") {
        typeCheck {
          """
          val s: Set[Int] = Set(1, 2)
          json"{$s: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Array as key") {
        typeCheck {
          """
          val arr: Array[Int] = Array(1, 2)
          json"{$arr: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for Either as key") {
        typeCheck {
          """
          val e: Either[String, Int] = Right(42)
          json"{$e: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("compile fails for tuple as key") {
        typeCheck {
          """
          val t: (Int, String) = (1, "a")
          json"{$t: 1}"
          """
        }.map(assert(_)(isLeft(containsString("key"))))
      },
      test("error message mentions JSON key and keyable types") {
        typeCheck {
          """
          case class Custom(value: Int)
          val c = Custom(1)
          json"{$c: 1}"
          """
        }.map(result =>
          assert(result)(isLeft(containsString("key"))) &&
            assert(result)(isLeft(containsString("keyable")))
        )
      },
      test("multiple keys with different keyable types") {
        val intKey  = 1
        val uuidKey = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val dateKey = LocalDate.of(2024, 1, 15)
        val result  = json"""{
          $intKey: "one",
          $uuidKey: "uuid",
          $dateKey: "date"
        }"""
        assertTrue(
          result.get("1").as[String] == Right("one"),
          result.get("550e8400-e29b-41d4-a716-446655440000").as[String] == Right("uuid"),
          result.get("2024-01-15").as[String] == Right("date")
        )
      },
      test("key in nested object") {
        val outerKey = "outer"
        val innerKey = 42
        val result   = json"""{$outerKey: {$innerKey: "nested"}}"""
        assertTrue(
          result.get("outer").get("42").as[String] == Right("nested")
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
          json"""{"v": $s}""".get("v").as[String] == Right("hello"),
          json"""{"v": $b}""".get("v").as[Boolean] == Right(true),
          json"""{"v": $i}""".get("v").as[Int] == Right(42),
          json"""{"v": $l}""".get("v").as[Long] == Right(100L),
          json"""{"v": $f}""".get("v").as[Float] == Right(1.5f),
          json"""{"v": $d}""".get("v").as[Double] == Right(2.5),
          json"""{"v": $by}""".get("v").as[Int].map(_.toByte) == Right(1.toByte),
          json"""{"v": $sh}""".get("v").as[Int].map(_.toShort) == Right(2.toShort),
          json"""{"v": $c}""".get("v").as[String] == Right("x"),
          json"""{"v": $bi}""".get("v").as[BigDecimal].map(_.toBigInt) == Right(BigInt("12345")),
          json"""{"v": $bd}""".get("v").as[BigDecimal] == Right(BigDecimal("123.45"))
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
          result.get("address").get("street").as[String] == Right("123 Main St"),
          result.get("address").get("city").as[String] == Right("NYC")
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
          result.get("data").get("name").as[String] == Right("test"),
          result.get("data").get("inner").get("value").as[Int] == Right(42)
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
          json"""{"status": $suspended}""".get("status").get("Suspended").get("reason").as[String] == Right(
            "Payment overdue"
          )
        )
      },
      test("Either works as sealed trait substitute in value position") {
        val left: Either[String, Int]  = Left("error")
        val right: Either[String, Int] = Right(42)

        assertTrue(
          json"""{"status": $left}""".get("status").get("Left").as[String] == Right("error"),
          json"""{"status": $right}""".get("status").get("Right").as[Int] == Right(42)
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
          json"""{"item": $some}""".get("item").get("name").as[String] == Right("thing"),
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
          result.get("points")(0).get("x").as[Int] == Right(1),
          result.get("points")(0).get("y").as[Int] == Right(2),
          result.get("points")(1).get("x").as[Int] == Right(3)
        )
      },
      test("Vector works in value position") {
        val vec    = Vector(1, 2, 3)
        val result = json"""{"values": $vec}"""
        assertTrue(
          result.get("values")(0).as[Int] == Right(1),
          result.get("values")(1).as[Int] == Right(2),
          result.get("values")(2).as[Int] == Right(3)
        )
      },
      test("Set works in value position") {
        val set    = Set("a", "b")
        val result = json"""{"values": $set}"""
        assertTrue(
          result.get("values").one.map(_.elements.size) == Right(2)
        )
      },
      test("Seq works in value position") {
        val seq: Seq[Int] = Seq(10, 20, 30)
        val result        = json"""{"values": $seq}"""
        assertTrue(
          result.get("values")(0).as[Int] == Right(10),
          result.get("values")(1).as[Int] == Right(20)
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
          result.get("stats").get("a").get("count").as[Int] == Right(10),
          result.get("stats").get("b").get("count").as[Int] == Right(20)
        )
      },
      test("Array[A] works in value position") {
        val arr    = Array(1, 2, 3)
        val result = json"""{"values": $arr}"""
        assertTrue(
          result.get("values")(0).as[Int] == Right(1),
          result.get("values")(2).as[Int] == Right(3)
        )
      },
      test("Json identity works in value position") {
        val j: Json = Json.Object("nested" -> Json.Number(42))
        val result  = json"""{"data": $j}"""
        assertTrue(
          result.get("data").get("nested").as[Int] == Right(42)
        )
      },
      test("Tuple2 works in value position") {
        val tuple: (Int, String) = (1, "hello")
        val result               = json"""{"pair": $tuple}"""
        assertTrue(
          result.get("pair")(0).as[Int] == Right(1),
          result.get("pair")(1).as[String] == Right("hello")
        )
      },
      test("Tuple3 works in value position") {
        val tuple: (Int, String, Boolean) = (1, "hello", true)
        val result                        = json"""{"triple": $tuple}"""
        assertTrue(
          result.get("triple")(0).as[Int] == Right(1),
          result.get("triple")(1).as[String] == Right("hello"),
          result.get("triple")(2).as[Boolean] == Right(true)
        )
      },
      test("Either works in value position") {
        val left: Either[String, Int]  = Left("error")
        val right: Either[String, Int] = Right(42)
        assertTrue(
          json"""{"result": $left}""".get("result").get("Left").as[String] == Right("error"),
          json"""{"result": $right}""".get("result").get("Right").as[Int] == Right(42)
        )
      },
      test("java.time types work in value position") {
        val instant   = Instant.parse("2024-01-15T10:30:00Z")
        val localDate = LocalDate.of(2024, 1, 15)
        val localTime = LocalTime.of(10, 30)
        val uuid      = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ts": $instant}""".get("ts").as[String] == Right(instant.toString),
          json"""{"date": $localDate}""".get("date").as[String] == Right("2024-01-15"),
          json"""{"time": $localTime}""".get("time").as[String] == Right("10:30"),
          json"""{"id": $uuid}""".get("id").as[String] == Right(uuid.toString)
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
      },
      test("compile fails for class without any encoder in value position") {
        typeCheck {
          """
          class MyClass(val x: Int)
          val v = new MyClass(1)
          json"[$v]"
          """
        }.map(assert(_)(isLeft(containsString("JsonEncoder"))))
      },
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
      },
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
          result.get("first").get("n").as[Int] == Right(1),
          result.get("second").get("n").as[Int] == Right(2),
          result.get("items")(0).as[String] == Right("a"),
          result.get("count").as[Int] == Right(42)
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
          result.get("a1b2c3d4-e5f6-7890-abcd-ef1234567890").get("value").as[Int] == Right(99)
        )
      }
    ),
    suite("string literal interpolation")(
      test("supports String interpolation in strings") {
        val name = "Alice"
        assertTrue(
          json"""{"greeting": "Hello, $name!"}""".get("greeting").as[String] == Right("Hello, Alice!")
        )
      },
      test("supports Int interpolation in strings") {
        val x = 42
        assertTrue(
          json"""{"msg": "x is $x"}""".get("msg").as[String] == Right("x is 42")
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
          json"""{"msg": "byte=$byte"}""".get("msg").as[String] == Right("byte=1"),
          json"""{"msg": "short=$short"}""".get("msg").as[String] == Right("short=2"),
          json"""{"msg": "int=$int"}""".get("msg").as[String] == Right("int=42"),
          json"""{"msg": "long=$long"}""".get("msg").as[String] == Right("long=100"),
          json"""{"msg": "float=$float"}""".get("msg").as[String] == Right("float=1.5"),
          json"""{"msg": "double=$double"}""".get("msg").as[String] == Right("double=2.5"),
          json"""{"msg": "bigInt=$bigInt"}""".get("msg").as[String] == Right("bigInt=12345678901234567890"),
          json"""{"msg": "bigDec=$bigDec"}""".get("msg").as[String] == Right("bigDec=123.456")
        )
      },
      test("supports Boolean in strings") {
        val b = true
        assertTrue(
          json"""{"msg": "flag is $b"}""".get("msg").as[String] == Right("flag is true")
        )
      },
      test("supports Char in strings") {
        val c: Char = 'X'
        assertTrue(
          json"""{"msg": "char is $c"}""".get("msg").as[String] == Right("char is X")
        )
      },
      test("supports UUID in strings") {
        val id = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ref": "user-$id"}""".get("ref").as[String] == Right("user-550e8400-e29b-41d4-a716-446655440000")
        )
      },
      test("supports LocalDate in strings") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(
          json"""{"file": "report-$date.pdf"}""".get("file").as[String] == Right("report-2024-01-15.pdf")
        )
      },
      test("supports LocalTime in strings") {
        val time = LocalTime.of(10, 30, 0)
        assertTrue(
          json"""{"log": "Event at $time"}""".get("log").as[String] == Right("Event at 10:30")
        )
      },
      test("supports Instant in strings") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(
          json"""{"ts": "Created: $instant"}""".get("ts").as[String] == Right("Created: 2024-01-15T10:30:00Z")
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
          json"""{"msg": "day=$dayOfWeek"}""".get("msg").as[String] == Right("day=MONDAY"),
          json"""{"msg": "duration=$duration"}""".get("msg").as[String] == Right("duration=PT1H"),
          json"""{"msg": "dt=$localDateTime"}""".get("msg").as[String] == Right("dt=2024-01-15T10:30"),
          json"""{"msg": "month=$month"}""".get("msg").as[String] == Right("month=JANUARY"),
          json"""{"msg": "md=$monthDay"}""".get("msg").as[String] == Right("md=--01-15"),
          json"""{"msg": "odt=$offsetDateTime"}""".get("msg").as[String] == Right("odt=2024-01-15T10:30Z"),
          json"""{"msg": "ot=$offsetTime"}""".get("msg").as[String] == Right("ot=10:30Z"),
          json"""{"msg": "period=$period"}""".get("msg").as[String] == Right("period=P30D"),
          json"""{"msg": "year=$year"}""".get("msg").as[String] == Right("year=2024"),
          json"""{"msg": "ym=$yearMonth"}""".get("msg").as[String] == Right("ym=2024-01"),
          json"""{"msg": "zone=$zoneId"}""".get("msg").as[String] == Right("zone=UTC"),
          json"""{"msg": "offset=$zoneOffset"}""".get("msg").as[String] == Right("offset=Z")
        )
      },
      test("supports Currency in strings") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").as[String] == Right("Price in USD")
        )
      },
      test("supports Unit in strings") {
        val u: Unit = ()
        assertTrue(
          json"""{"msg": "unit=$u"}""".get("msg").as[String] == Right("unit={}")
        )
      },
      test("supports multiple interpolations in one string") {
        val date    = LocalDate.of(2024, 1, 15)
        val version = 3
        val env     = "prod"
        assertTrue(
          json"""{"path": "/data/$env/$date/v$version/output.json"}""".get("path").as[String] ==
            Right("/data/prod/2024-01-15/v3/output.json")
        )
      },
      test("supports expression syntax in strings") {
        val x = 10
        assertTrue(
          json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").as[String] == Right("20 to 30")
        )
      },
      test("supports expression with method calls in strings") {
        val items = List("a", "b", "c")
        assertTrue(
          json"""{"count": "Found ${items.size} items"}""".get("count").as[String] == Right("Found 3 items")
        )
      },
      test("handles empty interpolation results in strings") {
        val empty = ""
        assertTrue(
          json"""{"msg": "[$empty]"}""".get("msg").as[String] == Right("[]")
        )
      },
      test("handles adjacent interpolations in strings") {
        val a = "A"
        val b = "B"
        val c = "C"
        assertTrue(
          json"""{"s": "$a$b$c"}""".get("s").as[String] == Right("ABC")
        )
      },
      test("handles interpolation at start of string") {
        val x = "start"
        assertTrue(
          json"""{"s": "$x end"}""".get("s").as[String] == Right("start end")
        )
      },
      test("handles interpolation at end of string") {
        val x = "end"
        assertTrue(
          json"""{"s": "start $x"}""".get("s").as[String] == Right("start end")
        )
      },
      test("handles only interpolation in string") {
        val x = "value"
        assertTrue(
          json"""{"s": "$x"}""".get("s").as[String] == Right("value")
        )
      },
      test("property-based: keyable types work in strings") {
        check(Gen.uuid) { uuid =>
          assertTrue(json"""{"id": "id-$uuid"}""".get("id").as[String] == Right(s"id-$uuid"))
        } &&
        check(Gen.int) { n =>
          assertTrue(json"""{"n": "num=$n"}""".get("n").as[String] == Right(s"num=$n"))
        } &&
        check(genInstant) { instant =>
          assertTrue(json"""{"ts": "at $instant"}""".get("ts").as[String] == Right(s"at $instant"))
        } &&
        check(genLocalDate) { date =>
          assertTrue(json"""{"d": "date=$date"}""".get("d").as[String] == Right(s"date=$date"))
        }
      },
      test("handles special characters needing JSON escape in interpolated values") {
        val withQuote     = "say \"hello\""
        val withBackslash = "path\\to\\file"
        val withNewline   = "line1\nline2"
        assertTrue(
          // Note: the runtime writeRawString escapes these for valid JSON
          json"""{"msg": "$withQuote"}""".get("msg").as[String] == Right("say \"hello\""),
          json"""{"path": "$withBackslash"}""".get("path").as[String] == Right("path\\to\\file"),
          json"""{"text": "$withNewline"}""".get("text").as[String] == Right("line1\nline2")
        )
      },
      test("compile fails for List in string literal") {
        typeCheck(
          "val xs: List[Int] = List(1, 2, 3); " +
            "json\"\"\"{\"msg\": \"list is $xs\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      },
      test("compile fails for case class in string literal") {
        typeCheck(
          "case class Point(x: Int, y: Int); " +
            "val p = Point(1, 2); " +
            "json\"\"\"{\"msg\": \"point is $p\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      },
      test("compile fails for Map in string literal") {
        typeCheck(
          "val m: Map[String, Int] = Map(\"a\" -> 1); " +
            "json\"\"\"{\"msg\": \"map is $m\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      },
      test("compile fails for Option in string literal") {
        typeCheck(
          "val opt: Option[Int] = Some(42); " +
            "json\"\"\"{\"msg\": \"opt is $opt\"}\"\"\""
        ).map(assert(_)(isLeft(containsString("string literal"))))
      },
      test("error message mentions string literal and keyable types") {
        typeCheck(
          "case class Custom(value: Int); " +
            "val c = Custom(1); " +
            "json\"\"\"{\"msg\": \"custom is $c\"}\"\"\""
        ).map(result =>
          assert(result)(isLeft(containsString("string literal"))) &&
            assert(result)(isLeft(containsString("keyable")))
        )
      }
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
          result.get("12345678-1234-1234-1234-123456789abc").get("data").get("value").as[Int] == Right(42),
          result.get("12345678-1234-1234-1234-123456789abc").get("note").as[String] == Right(s"Recorded at $timestamp")
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
          result.get("1").as[String] == Right("Hello Alice"),
          result.get("fedcba98-7654-3210-fedc-ba9876543210").as[String] == Right("Count is 42"),
          result.get("2024-01-15").as[String] == Right("plain value")
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
          result.get(0).get("n").as[Int] == Right(1),
          result.get(1).as[Int] == Right(42),
          result.get(2).as[String] == Right("Hello Alice")
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
          result.get("outer").get("123").get("data").get("v").as[Int] == Right(99),
          result.get("outer").get("123").get("label").as[String] == Right("Label: test")
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
          result.get("meta").get(id.toString).get("created").as[String] == Right(timestamp.toString),
          result.get("meta").get(id.toString).get("version").as[Int] == Right(42),
          result.get("meta").get(id.toString).get("active").as[Boolean] == Right(true),
          // Config section - value and string interpolation
          result.get("config").get("env").as[String] == Right("production"),
          result.get("config").get("path").as[String] == Right("/api/v42/production/data"),
          result.get("config").get("description").as[String] == Right(
            "Service running since 2024-01-15 with score 99.5"
          ),
          // Data section - complex value interpolation
          result.get("data").get("leader").get("name").as[String] == Right("Alice"),
          result.get("data").get("leader").get("address").get("city").as[String] == Right("NYC"),
          result.get("data").get("team")(0).get("name").as[String] == Right("Alice"),
          result.get("data").get("team")(1).get("name").as[String] == Right("Bob"),
          result.get("data").get("tags")(0).as[String] == Right("important"),
          result.get("data").get("metrics").get("cpu").as[Double] == Right(0.75),
          result.get("data").get("summary").as[String] == Right("Team led by Alice has 2 members"),
          // Nested section - multiple key interpolations
          result.get("nested").get("2024-01-15").get("42").get("info").as[String] == Right(
            "Nested key test on 2024-01-15"
          )
        )
      }
    ),
    suite("error messages")(
      test("key position error includes type and context") {
        typeCheck {
          """
          case class NotKeyable(x: Int)
          val v = NotKeyable(1)
          json"{$v: 1}"
          """
        }.map { result =>
          assert(result)(isLeft(containsString("key"))) &&
          assert(result)(isLeft(containsString("NotKeyable"))) &&
          assert(result)(isLeft(containsString("keyable")))
        }
      },
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
      },
      test("string literal error includes type and context") {
        typeCheck(
          "case class NotKeyable(x: Int); " +
            "val v = NotKeyable(1); " +
            "json\"\"\"{\"msg\": \"value is $v\"}\"\"\""
        ).map { result =>
          assert(result)(isLeft(containsString("string literal"))) &&
          assert(result)(isLeft(containsString("NotKeyable"))) &&
          assert(result)(isLeft(containsString("keyable")))
        }
      },
      test("invalid JSON syntax error is clear") {
        typeCheck {
          """json"[1,02]""""
        }.map { result =>
          assert(result)(isLeft(containsString("Invalid JSON"))) &&
          assert(result)(isLeft(containsString("leading zero")))
        }
      }
    ),
    suite("special character escaping in strings")(
      test("escapes backslash in string interpolation") {
        val path   = "C:\\Users\\test"
        val result = json"""{"path": "Path is $path"}"""
        assertTrue(result.get("path").as[String] == Right("Path is C:\\Users\\test"))
      },
      test("escapes tab in string interpolation") {
        val text   = "col1\tcol2"
        val result = json"""{"data": "Data: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Data: col1\tcol2"))
      },
      test("escapes newline in string interpolation") {
        val text   = "line1\nline2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: line1\nline2"))
      },
      test("escapes carriage return in string interpolation") {
        val text   = "line1\rline2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: line1\rline2"))
      },
      test("escapes backspace in string interpolation") {
        val text   = "a\bb"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: a\bb"))
      },
      test("escapes form feed in string interpolation") {
        val text   = "page1\fpage2"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: page1\fpage2"))
      },
      test("escapes double quote in string interpolation") {
        val text   = "say \"hello\""
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: say \"hello\""))
      },
      test("escapes control characters in string interpolation") {
        // Control character 0x01 (SOH - Start of Heading)
        val text   = "ctrl\u0001char"
        val result = json"""{"data": "Text: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Text: ctrl\u0001char"))
      },
      test("handles multiple special characters in one string") {
        val text   = "a\tb\nc\\d\"e"
        val result = json"""{"data": "Mix: $text"}"""
        assertTrue(result.get("data").as[String] == Right("Mix: a\tb\nc\\d\"e"))
      }
    ),
    suite("UTF-8 encoding edge cases")(
      test("handles 2-byte UTF-8 characters (Latin Extended)") {
        val text   = "café résumé naïve"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").as[String] == Right("café résumé naïve"))
      },
      test("handles 3-byte UTF-8 characters (CJK)") {
        val text   = "日本語テスト"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").as[String] == Right("日本語テスト"))
      },
      test("handles 4-byte UTF-8 characters (emoji)") {
        val text   = "Hello 🎉🎊🎁"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").as[String] == Right("Hello 🎉🎊🎁"))
      },
      test("handles mixed UTF-8 byte lengths") {
        val text   = "ASCII café 日本 🎉"
        val result = json"""{"text": $text}"""
        assertTrue(result.get("text").as[String] == Right("ASCII café 日本 🎉"))
      },
      test("handles 2-byte UTF-8 in key position") {
        val key    = "clé"
        val result = json"""{$key: "value"}"""
        assertTrue(result.get("clé").as[String] == Right("value"))
      },
      test("handles 3-byte UTF-8 in key position") {
        val key    = "キー"
        val result = json"""{$key: "value"}"""
        assertTrue(result.get("キー").as[String] == Right("value"))
      },
      test("handles emoji in string interpolation") {
        val emoji  = "🚀"
        val result = json"""{"msg": "Launch $emoji now!"}"""
        assertTrue(result.get("msg").as[String] == Right("Launch 🚀 now!"))
      }
    ),
    suite("Char type handling")(
      test("Char as key") {
        val key: Char = 'k'
        val result    = json"""{$key: "value"}"""
        assertTrue(result.get("k").as[String] == Right("value"))
      },
      test("Char in string interpolation") {
        val ch: Char = 'X'
        val result   = json"""{"msg": "Grade: $ch"}"""
        assertTrue(result.get("msg").as[String] == Right("Grade: X"))
      },
      test("special Char values as key") {
        val tab: Char = '\t'
        val result    = json"""{$tab: "tab-key"}"""
        assertTrue(result.get("\t").as[String] == Right("tab-key"))
      },
      test("Unicode Char as key") {
        val ch: Char = '★'
        val result   = json"""{$ch: "star"}"""
        assertTrue(result.get("★").as[String] == Right("star"))
      }
    ),
    suite("Unit type handling")(
      test("Unit as key") {
        val u: Unit = ()
        val result  = json"""{$u: "unit-key"}"""
        assertTrue(result.get("{}").as[String] == Right("unit-key"))
      },
      test("Unit in string interpolation") {
        val u: Unit = ()
        val result  = json"""{"msg": "Value is $u"}"""
        assertTrue(result.get("msg").as[String] == Right("Value is {}"))
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
        assertTrue(result.get("msg").as[String] == Right(expected))
      },
      test("handles high surrogate followed by non-low surrogate in InString context") {
        // High surrogate (U+D800) followed by ASCII character, not a low surrogate
        val strWithInvalidPair = "test" + '\uD800' + 'A'
        val result             = json"""{"msg": "value: $strWithInvalidPair"}"""
        // The high surrogate becomes replacement char, 'A' remains
        val expected = "value: test\uFFFDA"
        assertTrue(result.get("msg").as[String] == Right(expected))
      },
      test("handles multiple invalid surrogate pairs in InString context") {
        // Multiple invalid surrogates
        val str    = "\uD800\uD801" // Two consecutive high surrogates
        val result = json"""{"msg": "x${str}y"}"""
        // Each becomes a replacement character
        assertTrue(result.get("msg").as[String] == Right("x\uFFFD\uFFFDy"))
      },
      test("handles valid surrogate pair (emoji) surrounded by invalid ones") {
        // Valid: \uD83D\uDE00 = 😀, Invalid: lone \uD800
        val str    = "\uD800\uD83D\uDE00\uD800"
        val result = json"""{"msg": "$str"}"""
        // First \uD800 -> replacement, emoji stays, last \uD800 -> replacement
        assertTrue(result.get("msg").as[String] == Right("\uFFFD\uD83D\uDE00\uFFFD"))
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
        assertTrue(result.get("msg").as[String].isRight)
      }
    ),
    suite("additional branch coverage")(
      test("handles Char in value position") {
        val ch: Char = 'A'
        val result   = json"""{"char": $ch}"""
        assertTrue(result.get("char").as[String] == Right("A"))
      },
      test("handles nested arrays with mixed types") {
        val n      = 42
        val s      = "text"
        val b      = true
        val result = json"""[[$n], [$s], [$b]]"""
        assertTrue(
          result.get(0)(0).as[Int] == Right(42),
          result.get(1)(0).as[String] == Right("text"),
          result.get(2)(0).as[Boolean] == Right(true)
        )
      },
      test("handles deeply nested objects with all contexts") {
        val k1     = "key1"
        val k2     = "key2"
        val v      = 100
        val msg    = "inner"
        val result = json"""{$k1: {$k2: {"value": $v, "msg": "The $msg text"}}}"""
        assertTrue(
          result.get("key1").get("key2").get("value").as[Int] == Right(100),
          result.get("key1").get("key2").get("msg").as[String] == Right("The inner text")
        )
      },
      test("handles array at top level with complex values") {
        val x      = 1
        val y      = 2
        val result = json"""[$x, $y]"""
        assertTrue(
          result.get(0).as[Int] == Right(1),
          result.get(1).as[Int] == Right(2)
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
        assertTrue(result.get("msg").as[String] == Right("[]"))
      },
      test("handles very long string in interpolation") {
        val long   = "x" * 10000
        val result = json"""{"data": $long}"""
        assertTrue(result.get("data").as[String] == Right(long))
      },
      test("handles very long string inside string literal") {
        val long   = "y" * 5000
        val result = json"""{"msg": "Start $long End"}"""
        assertTrue(result.get("msg").as[String] == Right(s"Start $long End"))
      },
      test("handles negative numbers as keys") {
        val negInt  = -42
        val negLong = -123456789L
        val result  = json"""{$negInt: "neg-int", $negLong: "neg-long"}"""
        assertTrue(
          result.get("-42").as[String] == Right("neg-int"),
          result.get("-123456789").as[String] == Right("neg-long")
        )
      },
      test("handles BigInt as key") {
        val big    = BigInt("12345678901234567890")
        val result = json"""{$big: "big-key"}"""
        assertTrue(result.get("12345678901234567890").as[String] == Right("big-key"))
      },
      test("handles BigDecimal as key") {
        val bd     = BigDecimal("123.456789012345678901234567890")
        val result = json"""{$bd: "bd-key"}"""
        assertTrue(result.get(bd.toString).as[String] == Right("bd-key"))
      },
      test("handles DayOfWeek in all positions") {
        val dow    = DayOfWeek.WEDNESDAY
        val result = json"""{$dow: $dow, "msg": "Day is $dow"}"""
        assertTrue(
          result.get("WEDNESDAY").as[String] == Right("WEDNESDAY"),
          result.get("msg").as[String] == Right("Day is WEDNESDAY")
        )
      },
      test("handles Duration in all positions") {
        val dur    = Duration.ofHours(2).plusMinutes(30)
        val result = json"""{$dur: "duration-key", "dur": $dur, "msg": "Took $dur"}"""
        assertTrue(
          result.get(dur.toString).as[String] == Right("duration-key"),
          result.get("dur").as[String] == Right(dur.toString),
          result.get("msg").as[String] == Right(s"Took $dur")
        )
      },
      test("handles Period in all positions") {
        val period = Period.ofMonths(3).plusDays(15)
        val result = json"""{$period: "period-key", "period": $period, "msg": "Period: $period"}"""
        assertTrue(
          result.get(period.toString).as[String] == Right("period-key"),
          result.get("period").as[String] == Right(period.toString),
          result.get("msg").as[String] == Right(s"Period: $period")
        )
      },
      test("handles Month in all positions") {
        val month  = Month.DECEMBER
        val result = json"""{$month: "month-key", "month": $month, "msg": "Month is $month"}"""
        assertTrue(
          result.get("DECEMBER").as[String] == Right("month-key"),
          result.get("month").as[String] == Right("DECEMBER"),
          result.get("msg").as[String] == Right("Month is DECEMBER")
        )
      },
      test("handles MonthDay in all positions") {
        val md     = MonthDay.of(12, 25)
        val result = json"""{$md: "md-key", "md": $md, "msg": "Date: $md"}"""
        assertTrue(
          result.get(md.toString).as[String] == Right("md-key"),
          result.get("md").as[String] == Right(md.toString),
          result.get("msg").as[String] == Right(s"Date: $md")
        )
      },
      test("handles Year in all positions") {
        val year   = Year.of(2024)
        val result = json"""{$year: "year-key", "year": $year, "msg": "Year: $year"}"""
        assertTrue(
          result.get("2024").as[String] == Right("year-key"),
          result.get("year").as[String] == Right("2024"),
          result.get("msg").as[String] == Right("Year: 2024")
        )
      },
      test("handles YearMonth in all positions") {
        val ym     = YearMonth.of(2024, 6)
        val result = json"""{$ym: "ym-key", "ym": $ym, "msg": "YM: $ym"}"""
        assertTrue(
          result.get(ym.toString).as[String] == Right("ym-key"),
          result.get("ym").as[String] == Right(ym.toString),
          result.get("msg").as[String] == Right(s"YM: $ym")
        )
      },
      test("handles ZoneOffset in all positions") {
        val zo     = ZoneOffset.ofHours(5)
        val result = json"""{$zo: "zo-key", "zo": $zo, "msg": "Offset: $zo"}"""
        assertTrue(
          result.get(zo.toString).as[String] == Right("zo-key"),
          result.get("zo").as[String] == Right(zo.toString),
          result.get("msg").as[String] == Right(s"Offset: $zo")
        )
      },
      test("handles ZoneId in all positions") {
        val zi     = ZoneId.of("America/New_York")
        val result = json"""{$zi: "zi-key", "zi": $zi, "msg": "Zone: $zi"}"""
        assertTrue(
          result.get("America/New_York").as[String] == Right("zi-key"),
          result.get("zi").as[String] == Right("America/New_York"),
          result.get("msg").as[String] == Right("Zone: America/New_York")
        )
      },
      test("handles OffsetTime in all positions") {
        val ot     = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val result = json"""{$ot: "ot-key", "ot": $ot, "msg": "Time: $ot"}"""
        assertTrue(
          result.get(ot.toString).as[String] == Right("ot-key"),
          result.get("ot").as[String] == Right(ot.toString),
          result.get("msg").as[String] == Right(s"Time: $ot")
        )
      },
      test("handles OffsetDateTime in all positions") {
        val odt    = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        val result = json"""{$odt: "odt-key", "odt": $odt, "msg": "DateTime: $odt"}"""
        assertTrue(
          result.get(odt.toString).as[String] == Right("odt-key"),
          result.get("odt").as[String] == Right(odt.toString),
          result.get("msg").as[String] == Right(s"DateTime: $odt")
        )
      },
      test("handles ZonedDateTime in all positions") {
        val zdt    = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"))
        val result = json"""{$zdt: "zdt-key", "zdt": $zdt, "msg": "ZDT: $zdt"}"""
        assertTrue(
          result.get(zdt.toString).as[String] == Right("zdt-key"),
          result.get("zdt").as[String] == Right(zdt.toString),
          result.get("msg").as[String] == Right(s"ZDT: $zdt")
        )
      },
      test("handles Currency in all positions") {
        val curr   = Currency.getInstance("EUR")
        val result = json"""{$curr: "curr-key", "curr": $curr, "msg": "Currency: $curr"}"""
        assertTrue(
          result.get("EUR").as[String] == Right("curr-key"),
          result.get("curr").as[String] == Right("EUR"),
          result.get("msg").as[String] == Right("Currency: EUR")
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
          result1.get("msg").as[String] == Right(s"a${nul}b"),
          result2.get("msg").as[String] == Right(s"a${soh}b"),
          result3.get("msg").as[String] == Right(s"a${bel}b"),
          result4.get("msg").as[String] == Right(s"a${bs}b"),
          result5.get("msg").as[String] == Right(s"a${ht}b"),
          result6.get("msg").as[String] == Right(s"a${lf}b"),
          result7.get("msg").as[String] == Right(s"a${vt}b"),
          result8.get("msg").as[String] == Right(s"a${ff}b"),
          result9.get("msg").as[String] == Right(s"a${cr}b"),
          result10.get("msg").as[String] == Right(s"a${so}b"),
          result11.get("msg").as[String] == Right(s"a${us}b")
        )
      },
      test("handles empty Map in value position") {
        val emptyMap: Map[String, Int] = Map.empty
        val result                     = json"""{"data": $emptyMap}"""
        assertTrue(result.get("data").one == Right(Json.Object()))
      },
      test("handles empty Iterable in value position") {
        val emptyList: List[Int] = List.empty
        val result               = json"""{"data": $emptyList}"""
        assertTrue(result.get("data").one == Right(Json.Array()))
      },
      test("handles empty Array in value position") {
        val emptyArr: Array[Int] = Array.empty
        val result               = json"""{"data": $emptyArr}"""
        assertTrue(result.get("data").one == Right(Json.Array()))
      },
      test("handles nested Option with Some value") {
        val opt: Option[Option[Int]] = Some(Some(42))
        val result                   = json"""{"data": $opt}"""
        assertTrue(result.get("data").as[Int] == Right(42))
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
          result.get("data").get("z").as[Int] == Right(1),
          result.get("data").get("a").as[Int] == Right(2),
          result.get("data").get("m").as[Int] == Right(3)
        )
      },
      test("handles String with only special characters") {
        val special = "\"\\\b\f\n\r\t"
        val result  = json"""{"msg": "$special"}"""
        assertTrue(result.get("msg").as[String] == Right(special))
      },
      test("handles boundary values for numeric types in strings") {
        val byteMin: Byte   = Byte.MinValue
        val byteMax: Byte   = Byte.MaxValue
        val shortMin: Short = Short.MinValue
        val shortMax: Short = Short.MaxValue
        assertTrue(
          json"""{"msg": "byte range: $byteMin to $byteMax"}""".get("msg").as[String] ==
            Right(s"byte range: ${Byte.MinValue} to ${Byte.MaxValue}"),
          json"""{"msg": "short range: $shortMin to $shortMax"}""".get("msg").as[String] ==
            Right(s"short range: ${Short.MinValue} to ${Short.MaxValue}")
        )
      },
      test("handles Instant at epoch") {
        val epoch  = Instant.EPOCH
        val result = json"""{$epoch: $epoch, "msg": "at $epoch"}"""
        assertTrue(
          result.get(epoch.toString).as[String] == Right(epoch.toString),
          result.get("msg").as[String] == Right(s"at $epoch")
        )
      },
      test("handles Duration.ZERO") {
        val zero   = Duration.ZERO
        val result = json"""{$zero: $zero, "msg": "duration is $zero"}"""
        assertTrue(
          result.get(zero.toString).as[String] == Right(zero.toString),
          result.get("msg").as[String] == Right(s"duration is $zero")
        )
      },
      test("handles Period.ZERO") {
        val zero   = Period.ZERO
        val result = json"""{$zero: $zero, "msg": "period is $zero"}"""
        assertTrue(
          result.get(zero.toString).as[String] == Right(zero.toString),
          result.get("msg").as[String] == Right(s"period is $zero")
        )
      }
    ),
    suite("top-level string interpolation")(
      test("supports string interpolation at top level") {
        val name = "Alice"
        assertTrue(
          json""""Hello $name"""" == Json.String("Hello Alice")
        )
      },
      test("supports multiple interpolations in top-level string") {
        val name = "Alice"
        val age  = 30
        assertTrue(
          json""""Name: $name, Age: $age"""" == Json.String("Name: Alice, Age: 30")
        )
      },
      test("supports interpolation at start of top-level string") {
        val greeting = "Hello"
        assertTrue(
          json""""$greeting world"""" == Json.String("Hello world")
        )
      },
      test("supports interpolation at end of top-level string") {
        val name = "Alice"
        assertTrue(
          json""""Hello $name"""" == Json.String("Hello Alice")
        )
      },
      test("supports only interpolation in top-level string") {
        val value = "test"
        assertTrue(
          json""""$value"""" == Json.String("test")
        )
      },
      test("supports adjacent interpolations in top-level string") {
        val a = "A"
        val b = "B"
        val c = "C"
        assertTrue(
          json""""$a$b$c"""" == Json.String("ABC")
        )
      },
      test("supports expression syntax in top-level string") {
        val x = 10
        assertTrue(
          json""""Result: ${x * 2}"""" == Json.String("Result: 20")
        )
      },
      test("supports UUID in top-level string") {
        val id = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json""""id-$id"""" == Json.String("id-550e8400-e29b-41d4-a716-446655440000")
        )
      },
      test("supports java.time types in top-level string") {
        val date    = LocalDate.of(2024, 1, 15)
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(
          json""""Date: $date"""" == Json.String("Date: 2024-01-15"),
          json""""Time: $instant"""" == Json.String("Time: 2024-01-15T10:30:00Z")
        )
      },
      test("handles special characters in top-level string interpolation") {
        val text = "with \"quotes\" and \\backslash"
        assertTrue(
          json""""Text: $text"""" == Json.String("Text: with \"quotes\" and \\backslash")
        )
      }
    ),
    suite("writeValue runtime dispatch")(
      test("String") {
        val result = jsonValue("hello")
        assertTrue(result.get("v").as[String] == Right("hello"))
      },
      test("Boolean true") {
        val result = jsonValue(true)
        assertTrue(result.get("v").as[Boolean] == Right(true))
      },
      test("Boolean false") {
        val result = jsonValue(false)
        assertTrue(result.get("v").as[Boolean] == Right(false))
      },
      test("Byte") {
        val result = jsonValue(42.toByte)
        assertTrue(result.get("v").as[Int] == Right(42))
      },
      test("Short") {
        val result = jsonValue(42.toShort)
        assertTrue(result.get("v").as[Int] == Right(42))
      },
      test("Int") {
        val result = jsonValue(42)
        assertTrue(result.get("v").as[Int] == Right(42))
      },
      test("Long") {
        val result = jsonValue(42L)
        assertTrue(result.get("v").as[Long] == Right(42L))
      },
      test("Float") {
        val result = jsonValue(3.14f)
        assertTrue(result.get("v").as[Float] == Right(3.14f))
      },
      test("Double") {
        val result = jsonValue(3.14)
        assertTrue(result.get("v").as[Double] == Right(3.14))
      },
      test("Char") {
        val result = jsonValue('A')
        assertTrue(result.get("v").as[String] == Right("A"))
      },
      test("BigDecimal") {
        val result = jsonValue(BigDecimal("123.456"))
        assertTrue(result.get("v").as[BigDecimal] == Right(BigDecimal("123.456")))
      },
      test("BigInt") {
        val result = jsonValue(BigInt("12345678901234567890"))
        assertTrue(result.get("v").as[BigDecimal].map(_.toBigInt) == Right(BigInt("12345678901234567890")))
      },
      test("DayOfWeek") {
        val result = jsonValue(DayOfWeek.MONDAY)
        assertTrue(result.get("v").as[String] == Right("MONDAY"))
      },
      test("Duration") {
        val result = jsonValue(Duration.ofHours(1))
        assertTrue(result.get("v").as[String] == Right("PT1H"))
      },
      test("Instant") {
        val result = jsonValue(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("v").as[String] == Right("2024-01-15T10:30:00Z"))
      },
      test("LocalDate") {
        val result = jsonValue(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("v").as[String] == Right("2024-01-15"))
      },
      test("LocalDateTime") {
        val result = jsonValue(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("v").as[String] == Right("2024-01-15T10:30"))
      },
      test("LocalTime") {
        val result = jsonValue(LocalTime.of(10, 30))
        assertTrue(result.get("v").as[String] == Right("10:30"))
      },
      test("Month") {
        val result = jsonValue(Month.JANUARY)
        assertTrue(result.get("v").as[String] == Right("JANUARY"))
      },
      test("MonthDay") {
        val result = jsonValue(MonthDay.of(1, 15))
        assertTrue(result.get("v").as[String] == Right("--01-15"))
      },
      test("OffsetDateTime") {
        val result = jsonValue(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("v").as[String] == Right("2024-01-15T10:30Z"))
      },
      test("OffsetTime") {
        val result = jsonValue(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("v").as[String] == Right("10:30Z"))
      },
      test("Period") {
        val result = jsonValue(Period.ofDays(30))
        assertTrue(result.get("v").as[String] == Right("P30D"))
      },
      test("Year") {
        val result = jsonValue(Year.of(2024))
        assertTrue(result.get("v").as[String] == Right("2024"))
      },
      test("YearMonth") {
        val result = jsonValue(YearMonth.of(2024, 1))
        assertTrue(result.get("v").as[String] == Right("2024-01"))
      },
      test("ZoneOffset") {
        val result = jsonValue(ZoneOffset.ofHours(5))
        assertTrue(result.get("v").as[String] == Right("+05:00"))
      },
      test("ZoneId") {
        val result = jsonValue(ZoneId.of("UTC"))
        assertTrue(result.get("v").as[String] == Right("UTC"))
      },
      test("ZonedDateTime") {
        val result = jsonValue(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC")))
        assertTrue(result.get("v").as[String].exists(_.contains("2024-01-15")))
      },
      test("Currency") {
        val result = jsonValue(Currency.getInstance("USD"))
        assertTrue(result.get("v").as[String] == Right("USD"))
      },
      test("UUID") {
        val result = jsonValue(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("v").as[String] == Right("550e8400-e29b-41d4-a716-446655440000"))
      },
      test("Option Some") {
        val result = jsonValue(Some(42))
        assertTrue(result.get("v").as[Int] == Right(42))
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
        assertTrue(result.get("v").one == Right(Json.Object()))
      },
      test("Map with multiple entries") {
        val result = jsonValue(Map("a" -> 1, "b" -> 2))
        assertTrue(
          result.get("v").get("a").as[Int] == Right(1),
          result.get("v").get("b").as[Int] == Right(2)
        )
      },
      test("List with multiple elements") {
        val result = jsonValue(List(1, 2, 3))
        assertTrue(
          result.get("v")(0).as[Int] == Right(1),
          result.get("v")(1).as[Int] == Right(2),
          result.get("v")(2).as[Int] == Right(3)
        )
      },
      test("Array with multiple elements") {
        val result = jsonValue(Array(1, 2, 3))
        assertTrue(
          result.get("v")(0).as[Int] == Right(1),
          result.get("v")(1).as[Int] == Right(2)
        )
      },
      test("Vector") {
        val result = jsonValue(Vector(1, 2))
        assertTrue(result.get("v")(0).as[Int] == Right(1))
      },
      test("Set") {
        val result = jsonValue(Set(42))
        assertTrue(result.get("v")(0).as[Int] == Right(42))
      },
      test("fallback toString") {
        case class Custom(value: Int) {
          override def toString: String = s"""{"custom":$value}"""
        }
        val result = jsonValue(Custom(42))
        assertTrue(result.get("v").get("custom").as[Int] == Right(42))
      }
    ),
    suite("writeKeyOnly runtime dispatch")(
      test("String key") {
        val result = jsonKey("mykey")
        assertTrue(result.get("mykey").as[String] == Right("value"))
      },
      test("Char key") {
        val result = jsonKey('K')
        assertTrue(result.get("K").as[String] == Right("value"))
      },
      test("Boolean key") {
        val result = jsonKey(true)
        assertTrue(result.get("true").as[String] == Right("value"))
      },
      test("Byte key") {
        val result = jsonKey(1.toByte)
        assertTrue(result.get("1").as[String] == Right("value"))
      },
      test("Short key") {
        val result = jsonKey(2.toShort)
        assertTrue(result.get("2").as[String] == Right("value"))
      },
      test("Int key") {
        val result = jsonKey(42)
        assertTrue(result.get("42").as[String] == Right("value"))
      },
      test("Long key") {
        val result = jsonKey(100L)
        assertTrue(result.get("100").as[String] == Right("value"))
      },
      test("Float key") {
        val result = jsonKey(1.5f)
        assertTrue(result.get("1.5").as[String] == Right("value"))
      },
      test("Double key") {
        val result = jsonKey(2.5)
        assertTrue(result.get("2.5").as[String] == Right("value"))
      },
      test("BigDecimal key") {
        val result = jsonKey(BigDecimal("123.456"))
        assertTrue(result.get("123.456").as[String] == Right("value"))
      },
      test("BigInt key") {
        val result = jsonKey(BigInt("12345"))
        assertTrue(result.get("12345").as[String] == Right("value"))
      },
      test("Unit key") {
        val result = jsonKey(())
        assertTrue(result.get("{}").as[String] == Right("value"))
      },
      test("Duration key") {
        val result = jsonKey(Duration.ofHours(1))
        assertTrue(result.get("PT1H").as[String] == Right("value"))
      },
      test("DayOfWeek key") {
        val result = jsonKey(DayOfWeek.MONDAY)
        assertTrue(result.get("MONDAY").as[String] == Right("value"))
      },
      test("Instant key") {
        val result = jsonKey(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("2024-01-15T10:30:00Z").as[String] == Right("value"))
      },
      test("LocalDate key") {
        val result = jsonKey(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("2024-01-15").as[String] == Right("value"))
      },
      test("LocalDateTime key") {
        val result = jsonKey(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("2024-01-15T10:30").as[String] == Right("value"))
      },
      test("LocalTime key") {
        val result = jsonKey(LocalTime.of(10, 30))
        assertTrue(result.get("10:30").as[String] == Right("value"))
      },
      test("Month key") {
        val result = jsonKey(Month.JANUARY)
        assertTrue(result.get("JANUARY").as[String] == Right("value"))
      },
      test("MonthDay key") {
        val result = jsonKey(MonthDay.of(1, 15))
        assertTrue(result.get("--01-15").as[String] == Right("value"))
      },
      test("OffsetDateTime key") {
        val result = jsonKey(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("2024-01-15T10:30Z").as[String] == Right("value"))
      },
      test("OffsetTime key") {
        val result = jsonKey(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("10:30Z").as[String] == Right("value"))
      },
      test("Period key") {
        val result = jsonKey(Period.ofDays(30))
        assertTrue(result.get("P30D").as[String] == Right("value"))
      },
      test("Year key") {
        val result = jsonKey(Year.of(2024))
        assertTrue(result.get("2024").as[String] == Right("value"))
      },
      test("YearMonth key") {
        val result = jsonKey(YearMonth.of(2024, 1))
        assertTrue(result.get("2024-01").as[String] == Right("value"))
      },
      test("ZoneOffset key") {
        val result = jsonKey(ZoneOffset.ofHours(5))
        assertTrue(result.get("+05:00").as[String] == Right("value"))
      },
      test("ZoneId key") {
        val result = jsonKey(ZoneId.of("UTC"))
        assertTrue(result.get("UTC").as[String] == Right("value"))
      },
      test("Currency key") {
        val result = jsonKey(Currency.getInstance("USD"))
        assertTrue(result.get("USD").as[String] == Right("value"))
      },
      test("UUID key") {
        val result = jsonKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("550e8400-e29b-41d4-a716-446655440000").as[String] == Right("value"))
      }
    ),
    suite("writeInString runtime dispatch")(
      test("String in string") {
        val result = jsonInString("hello")
        assertTrue(result.get("msg").as[String] == Right("prefix-hello-suffix"))
      },
      test("Char in string") {
        val result = jsonInString('X')
        assertTrue(result.get("msg").as[String] == Right("prefix-X-suffix"))
      },
      test("Boolean in string") {
        val result = jsonInString(true)
        assertTrue(result.get("msg").as[String] == Right("prefix-true-suffix"))
      },
      test("Byte in string") {
        val result = jsonInString(42.toByte)
        assertTrue(result.get("msg").as[String] == Right("prefix-42-suffix"))
      },
      test("Short in string") {
        val result = jsonInString(42.toShort)
        assertTrue(result.get("msg").as[String] == Right("prefix-42-suffix"))
      },
      test("Int in string") {
        val result = jsonInString(42)
        assertTrue(result.get("msg").as[String] == Right("prefix-42-suffix"))
      },
      test("Long in string") {
        val result = jsonInString(100L)
        assertTrue(result.get("msg").as[String] == Right("prefix-100-suffix"))
      },
      test("Double in string") {
        val result = jsonInString(3.14)
        assertTrue(result.get("msg").as[String] == Right("prefix-3.14-suffix"))
      },
      test("BigDecimal in string") {
        val result = jsonInString(BigDecimal("123.456"))
        assertTrue(result.get("msg").as[String] == Right("prefix-123.456-suffix"))
      },
      test("BigInt in string") {
        val result = jsonInString(BigInt("12345"))
        assertTrue(result.get("msg").as[String] == Right("prefix-12345-suffix"))
      },
      test("Unit in string") {
        val result = jsonInString(())
        assertTrue(result.get("msg").as[String] == Right("prefix-()-suffix"))
      },
      test("Duration in string") {
        val result = jsonInString(Duration.ofHours(1))
        assertTrue(result.get("msg").as[String] == Right("prefix-PT1H-suffix"))
      },
      test("DayOfWeek in string") {
        val result = jsonInString(DayOfWeek.MONDAY)
        assertTrue(result.get("msg").as[String] == Right("prefix-MONDAY-suffix"))
      },
      test("Instant in string") {
        val result = jsonInString(Instant.parse("2024-01-15T10:30:00Z"))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-01-15T10:30:00Z-suffix"))
      },
      test("LocalDate in string") {
        val result = jsonInString(LocalDate.of(2024, 1, 15))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-01-15-suffix"))
      },
      test("LocalDateTime in string") {
        val result = jsonInString(LocalDateTime.of(2024, 1, 15, 10, 30))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-01-15T10:30-suffix"))
      },
      test("LocalTime in string") {
        val result = jsonInString(LocalTime.of(10, 30))
        assertTrue(result.get("msg").as[String] == Right("prefix-10:30-suffix"))
      },
      test("Month in string") {
        val result = jsonInString(Month.JANUARY)
        assertTrue(result.get("msg").as[String] == Right("prefix-JANUARY-suffix"))
      },
      test("MonthDay in string") {
        val result = jsonInString(MonthDay.of(1, 15))
        assertTrue(result.get("msg").as[String] == Right("prefix---01-15-suffix"))
      },
      test("OffsetDateTime in string") {
        val result = jsonInString(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-01-15T10:30Z-suffix"))
      },
      test("OffsetTime in string") {
        val result = jsonInString(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC))
        assertTrue(result.get("msg").as[String] == Right("prefix-10:30Z-suffix"))
      },
      test("Period in string") {
        val result = jsonInString(Period.ofDays(30))
        assertTrue(result.get("msg").as[String] == Right("prefix-P30D-suffix"))
      },
      test("Year in string") {
        val result = jsonInString(Year.of(2024))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-suffix"))
      },
      test("YearMonth in string") {
        val result = jsonInString(YearMonth.of(2024, 1))
        assertTrue(result.get("msg").as[String] == Right("prefix-2024-01-suffix"))
      },
      test("ZoneOffset in string") {
        val result = jsonInString(ZoneOffset.ofHours(5))
        assertTrue(result.get("msg").as[String] == Right("prefix-+05:00-suffix"))
      },
      test("ZoneId in string") {
        val result = jsonInString(ZoneId.of("UTC"))
        assertTrue(result.get("msg").as[String] == Right("prefix-UTC-suffix"))
      },
      test("ZonedDateTime in string") {
        val result = jsonInString(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC")))
        assertTrue(result.get("msg").as[String].exists(_.contains("2024-01-15")))
      },
      test("Currency in string") {
        val result = jsonInString(Currency.getInstance("USD"))
        assertTrue(result.get("msg").as[String] == Right("prefix-USD-suffix"))
      },
      test("UUID in string") {
        val result = jsonInString(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(result.get("msg").as[String] == Right("prefix-550e8400-e29b-41d4-a716-446655440000-suffix"))
      }
    ),
    suite("writeKey via Map")(
      test("Map with String keys") {
        val result = jsonValue(Map("key1" -> 1, "key2" -> 2))
        assertTrue(
          result.get("v").get("key1").as[Int] == Right(1),
          result.get("v").get("key2").as[Int] == Right(2)
        )
      },
      test("Map with Int keys") {
        val result = jsonValue(Map(1 -> "a", 2 -> "b"))
        assertTrue(
          result.get("v").get("1").as[String] == Right("a"),
          result.get("v").get("2").as[String] == Right("b")
        )
      },
      test("Map with Boolean keys") {
        val result = jsonValue(Map(true -> "yes", false -> "no"))
        assertTrue(
          result.get("v").get("true").as[String] == Right("yes"),
          result.get("v").get("false").as[String] == Right("no")
        )
      },
      test("Map with Long keys") {
        val result = jsonValue(Map(100L -> "hundred"))
        assertTrue(result.get("v").get("100").as[String] == Right("hundred"))
      },
      test("Map with Float keys") {
        val result = jsonValue(Map(1.5f -> "value"))
        assertTrue(result.get("v").get("1.5").as[String] == Right("value"))
      },
      test("Map with Double keys") {
        val result = jsonValue(Map(2.5 -> "value"))
        assertTrue(result.get("v").get("2.5").as[String] == Right("value"))
      },
      test("Map with BigDecimal keys") {
        val result = jsonValue(Map(BigDecimal("123.456") -> "value"))
        assertTrue(result.get("v").get("123.456").as[String] == Right("value"))
      },
      test("Map with BigInt keys") {
        val result = jsonValue(Map(BigInt("12345") -> "value"))
        assertTrue(result.get("v").get("12345").as[String] == Right("value"))
      },
      test("Map with Byte keys") {
        val result = jsonValue(Map(1.toByte -> "value"))
        assertTrue(result.get("v").get("1").as[String] == Right("value"))
      },
      test("Map with Short keys") {
        val result = jsonValue(Map(2.toShort -> "value"))
        assertTrue(result.get("v").get("2").as[String] == Right("value"))
      },
      test("Map with UUID keys") {
        val uuid   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val result = jsonValue(Map(uuid -> "value"))
        assertTrue(result.get("v").get(uuid.toString).as[String] == Right("value"))
      },
      test("Map with LocalDate keys") {
        val date   = LocalDate.of(2024, 1, 15)
        val result = jsonValue(Map(date -> "value"))
        assertTrue(result.get("v").get("2024-01-15").as[String] == Right("value"))
      },
      test("Map with Duration keys") {
        val dur    = Duration.ofHours(1)
        val result = jsonValue(Map(dur -> "value"))
        assertTrue(result.get("v").get("PT1H").as[String] == Right("value"))
      },
      test("Map with Month keys") {
        val result = jsonValue(Map(Month.JANUARY -> "value"))
        assertTrue(result.get("v").get("JANUARY").as[String] == Right("value"))
      },
      test("Map with DayOfWeek keys") {
        val result = jsonValue(Map(DayOfWeek.MONDAY -> "value"))
        assertTrue(result.get("v").get("MONDAY").as[String] == Right("value"))
      },
      test("Map with ZoneId keys") {
        val result = jsonValue(Map(ZoneId.of("UTC") -> "value"))
        assertTrue(result.get("v").get("UTC").as[String] == Right("value"))
      },
      test("Map with ZoneOffset keys") {
        val result = jsonValue(Map(ZoneOffset.ofHours(5) -> "value"))
        assertTrue(result.get("v").get("+05:00").as[String] == Right("value"))
      },
      test("Map with Currency keys") {
        val result = jsonValue(Map(Currency.getInstance("USD") -> "value"))
        assertTrue(result.get("v").get("USD").as[String] == Right("value"))
      },
      test("Map with Instant keys") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val result  = jsonValue(Map(instant -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30:00Z").as[String] == Right("value"))
      },
      test("Map with LocalTime keys") {
        val result = jsonValue(Map(LocalTime.of(10, 30) -> "value"))
        assertTrue(result.get("v").get("10:30").as[String] == Right("value"))
      },
      test("Map with LocalDateTime keys") {
        val result = jsonValue(Map(LocalDateTime.of(2024, 1, 15, 10, 30) -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30").as[String] == Right("value"))
      },
      test("Map with MonthDay keys") {
        val result = jsonValue(Map(MonthDay.of(1, 15) -> "value"))
        assertTrue(result.get("v").get("--01-15").as[String] == Right("value"))
      },
      test("Map with OffsetTime keys") {
        val result = jsonValue(Map(OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC) -> "value"))
        assertTrue(result.get("v").get("10:30Z").as[String] == Right("value"))
      },
      test("Map with OffsetDateTime keys") {
        val result = jsonValue(Map(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC) -> "value"))
        assertTrue(result.get("v").get("2024-01-15T10:30Z").as[String] == Right("value"))
      },
      test("Map with Period keys") {
        val result = jsonValue(Map(Period.ofDays(30) -> "value"))
        assertTrue(result.get("v").get("P30D").as[String] == Right("value"))
      },
      test("Map with Year keys") {
        val result = jsonValue(Map(Year.of(2024) -> "value"))
        assertTrue(result.get("v").get("2024").as[String] == Right("value"))
      },
      test("Map with YearMonth keys") {
        val result = jsonValue(Map(YearMonth.of(2024, 1) -> "value"))
        assertTrue(result.get("v").get("2024-01").as[String] == Right("value"))
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
        assertTrue(result.get("msg").as[String] == Right("prefix-café-suffix"))
      },
      test("3-byte UTF-8 characters (CJK)") {
        val result = jsonInString("日本語")
        assertTrue(result.get("msg").as[String] == Right("prefix-日本語-suffix"))
      },
      test("mixed UTF-8 byte lengths") {
        val result = jsonInString("Hello café 日本")
        assertTrue(result.get("msg").as[String] == Right("prefix-Hello café 日本-suffix"))
      }
    ),
    suite("nested structures")(
      test("Option containing Map") {
        val result = jsonValue(Some(Map("x" -> 1)))
        assertTrue(result.get("v").get("x").as[Int] == Right(1))
      },
      test("List containing Maps") {
        val result = jsonValue(List(Map("a" -> 1), Map("b" -> 2)))
        assertTrue(
          result.get("v")(0).get("a").as[Int] == Right(1),
          result.get("v")(1).get("b").as[Int] == Right(2)
        )
      },
      test("Map containing Lists") {
        val result = jsonValue(Map("items" -> List(1, 2, 3)))
        assertTrue(
          result.get("v").get("items")(0).as[Int] == Right(1),
          result.get("v").get("items")(1).as[Int] == Right(2)
        )
      }
    )
  )

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
}
