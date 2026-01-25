package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.{containsString, isLeft}
import zio.test.TestAspect.exceptNative
import java.time._

object JsonInterpolatorSpec extends SchemaBaseSpec {
  private sealed trait DerivedStatus
  private object DerivedStatus {
    case object Active                         extends DerivedStatus
    final case class Suspended(reason: String) extends DerivedStatus

    implicit val schema: Schema[DerivedStatus] = Schema.derived
  }

  // Avoid `Gen.uuid` on Scala.js (it can pull in `UUID.randomUUID` -> `SecureRandom` at link time).
  private val genUuid: Gen[Any, java.util.UUID] =
    (Gen.long <*> Gen.long).map { case (mostSigBits, leastSigBits) =>
      new java.util.UUID(mostSigBits, leastSigBits)
    }

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorSpec")(
    test("parses Json literal") {
      assertTrue(
        json""" "hello"""" == Json.str("hello"),
        json""""Привіт" """ == Json.str("Привіт"),
        json""" "★🎸🎧⋆｡°⋆" """ == Json.str("★🎸🎧⋆｡°⋆"),
        json"""42""" == Json.number(42),
        json"""true""" == Json.bool(true),
        json"""false""" == Json.bool(false),
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
      check(Gen.char.filter(x => x <= 0xd800 || x >= 0xdfff))(x =>
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

    // ==== Java Time types ====
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
<<<<<<< HEAD
          json"""{"x": $x}""".get("x").string.map { s =>
            // `YearMonth.toString` omits '+' for years > 9999, but `YearMonth.parse` requires it.
            val normalized = if (s.length > 7 && s.charAt(0).isDigit) "+" + s else s
            YearMonth.parse(normalized)
          } == Right(x)
=======
          json"""{"x": $x}""".get("x").as[String].map(YearMonth.parse) == Right(x)
>>>>>>> origin/main
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
      check(genUuid)(x =>
        assertTrue(
          json"""{"x": $x}""".get("x").as[String] == Right(x.toString),
          json"""{$x: "v"}""".get(x.toString).as[String] == Right("v")
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
      assertTrue(json"""{"x": $x}""".get("x").get("y").as[Int] == Right(1))
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
    test("supports interpolated Map values with BigDecimal keys") {
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
      check(genUuid)(x =>
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
    suite("key position interpolation")(
      test("supports all PrimitiveType types as keys") {
        val s: String                      = "key"
        val b: Boolean                     = true
        val byte: Byte                     = 1
        val short: Short                   = 2
        val int: Int                       = 3
        val long: Long                     = 4L
        val float: Float                   = 1.5f
        val double: Double                 = 2.5
        val char: Char                     = 'k'
        val bigInt: BigInt                 = BigInt("12345678901234567890")
        val bigDec: BigDecimal             = BigDecimal("123.456")
        val uuid: java.util.UUID           = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val instant: Instant               = Instant.now()
        val localDate: LocalDate           = LocalDate.of(2024, 1, 15)
        val localTime: LocalTime           = LocalTime.of(10, 30)
        val localDateTime: LocalDateTime   = LocalDateTime.of(2024, 1, 15, 10, 30)
        val offsetTime: OffsetTime         = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val offsetDateTime: OffsetDateTime = OffsetDateTime.now()
        val zonedDateTime: ZonedDateTime   = ZonedDateTime.now()
        val duration: Duration             = Duration.ofHours(1)
        val period: Period                 = Period.ofDays(30)
        val dayOfWeek: DayOfWeek           = DayOfWeek.MONDAY
        val month: Month                   = Month.JANUARY
        val monthDay: MonthDay             = MonthDay.of(1, 15)
        val year: Year                     = Year.of(2024)
        val yearMonth: YearMonth           = YearMonth.of(2024, 1)
        val zoneId: ZoneId                 = ZoneId.of("UTC")
        val zoneOffset: ZoneOffset         = ZoneOffset.UTC
        val currency: java.util.Currency   = java.util.Currency.getInstance("USD")

        assertTrue(
          json"""{$s: 1}""".get(s).int == Right(1),
          json"""{$b: 1}""".get("true").int == Right(1),
          json"""{$byte: 1}""".get("1").int == Right(1),
          json"""{$short: 1}""".get("2").int == Right(1),
          json"""{$int: 1}""".get("3").int == Right(1),
          json"""{$long: 1}""".get("4").int == Right(1),
          json"""{$float: 1}""".get(JsonBinaryCodec.floatCodec.encodeToString(float)).int == Right(1),
          json"""{$double: 1}""".get(JsonBinaryCodec.doubleCodec.encodeToString(double)).int == Right(1),
          json"""{$char: 1}""".get(char.toString).int == Right(1),
          json"""{$bigInt: 1}""".get(bigInt.toString).int == Right(1),
          json"""{$bigDec: 1}""".get(bigDec.toString).int == Right(1),
          json"""{$uuid: 1}""".get(uuid.toString).int == Right(1),
          json"""{$instant: 1}""".get(instant.toString).int == Right(1),
          json"""{$localDate: 1}""".get(localDate.toString).int == Right(1),
          json"""{$localTime: 1}""".get(localTime.toString).int == Right(1),
          json"""{$localDateTime: 1}""".get(localDateTime.toString).int == Right(1),
          json"""{$offsetTime: 1}""".get(offsetTime.toString).int == Right(1),
          json"""{$offsetDateTime: 1}""".get(offsetDateTime.toString).int == Right(1),
          json"""{$zonedDateTime: 1}""".get(zonedDateTime.toString).int == Right(1),
          json"""{$duration: 1}""".get(duration.toString).int == Right(1),
          json"""{$period: 1}""".get(period.toString).int == Right(1),
          json"""{$dayOfWeek: 1}""".get(dayOfWeek.toString).int == Right(1),
          json"""{$month: 1}""".get(month.toString).int == Right(1),
          json"""{$monthDay: 1}""".get(monthDay.toString).int == Right(1),
          json"""{$year: 1}""".get(year.toString).int == Right(1),
          json"""{$yearMonth: 1}""".get(yearMonth.toString).int == Right(1),
          json"""{$zoneId: 1}""".get(zoneId.toString).int == Right(1),
          json"""{$zoneOffset: 1}""".get(zoneOffset.toString).int == Right(1),
          json"""{$currency: 1}""".get("USD").int == Right(1)
        )
      },
      test("property: stringable types work as keys") {
        check(genUuid) { uuid =>
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
      test("compile fails for non-stringable types in key position") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.json._
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{$p: \"value\"}"
        """).map(assert(_)(isLeft(containsString("Key interpolation"))))
      } @@ exceptNative
    ),
    suite("value position interpolation")(
      test("supports types with Schema") {
        case class Address(street: String, city: String)
        object Address { implicit val schema: Schema[Address] = Schema.derived }

        case class Person(name: String, age: Int, address: Address)
        object Person { implicit val schema: Schema[Person] = Schema.derived }

        val alice  = Person("Alice", 30, Address("123 Main", "NYC"))
        val result = json"""{"employee": $alice}"""

        assertTrue(
          result.get("employee").get("name").string == Right("Alice"),
          result.get("employee").get("age").int == Right(30),
          result.get("employee").get("address").get("city").string == Right("NYC")
        )
      },
      test("supports nested complex types") {
        case class Inner(value: Int)
        object Inner { implicit val schema: Schema[Inner] = Schema.derived }

        case class Outer(inner: Inner, inners: List[Inner])
        object Outer { implicit val schema: Schema[Outer] = Schema.derived }

        val o      = Outer(Inner(1), List(Inner(2), Inner(3)))
        val result = json"""{"data": $o}"""

        assertTrue(
          result.get("data").get("inner").get("value").int == Right(1),
          result.get("data").get("inners")(0).get("value").int == Right(2)
        )
      },
      test("supports sealed traits") {
        val active: DerivedStatus    = DerivedStatus.Active
        val suspended: DerivedStatus = DerivedStatus.Suspended("Payment overdue")

        assertTrue(
          json"""{"status": $active}""".get("status").one.isRight,
          json"""{"status": $suspended}""".get("status").get("Suspended").get("reason").string == Right(
            "Payment overdue"
          )
        )
      },
      test("supports Option of complex types") {
        case class Item(name: String)
        object Item { implicit val schema: Schema[Item] = Schema.derived }

        val some: Option[Item] = Some(Item("thing"))
        val none: Option[Item] = None

        assertTrue(
          json"""{"item": $some}""".get("item").get("name").string == Right("thing"),
          json"""{"item": $none}""".get("item").one == Right(Json.Null)
        )
      },
      test("supports collections of complex types") {
        case class Point(x: Int, y: Int)
        object Point { implicit val schema: Schema[Point] = Schema.derived }

        val points   = List(Point(1, 2), Point(3, 4))
        val pointSet = Set(Point(5, 6))
        val pointVec = Vector(Point(7, 8))

        assertTrue(
          json"""{"points": $points}""".get("points")(0).get("x").int == Right(1),
          json"""{"points": $pointSet}""".get("points")(0).get("x").int == Right(5),
          json"""{"points": $pointVec}""".get("points")(0).get("x").int == Right(7)
        )
      },
      test("supports Map with complex value types") {
        case class Stats(count: Int)
        object Stats { implicit val schema: Schema[Stats] = Schema.derived }

        val data   = Map("a" -> Stats(10), "b" -> Stats(20))
        val result = json"""{"stats": $data}"""

        assertTrue(
          result.get("stats").get("a").get("count").int == Right(10),
          result.get("stats").get("b").get("count").int == Right(20)
        )
      },
      test("compile fails for types without JsonEncoder") {
        // Use a triple-quoted JSON literal in the snippet to avoid any confusion from
        // escaped quotes in string constants (we want the error to be about *value* interpolation).
        typeCheck(
          "import zio.blocks.schema.json._\n" +
            "val v = new java.lang.Object()\n" +
            "json\"\"\"{\"value\": $v}\"\"\"\n"
        ).map(assert(_)(isLeft(containsString("Value interpolation"))))
      } @@ exceptNative
    ),
    suite("string literal interpolation")(
      test("supports String interpolation in strings") {
        val name     = "Alice"
        val nameBang = s"$name!"
        assertTrue(
          json"""{"greeting": "Hello, $nameBang"}""".get("greeting").string == Right("Hello, Alice!")
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
      test("supports Currency in strings") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").string == Right("Price in USD")
        )
      },
      test("supports ${expr} syntax for expressions") {
        val x     = 10
        val items = List("a", "b", "c")

        assertTrue(
          json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").string == Right("20 to 30"),
          json"""{"count": "Found ${items.size} items"}""".get("count").string == Right("Found 3 items")
        )
      },
      test("supports multiple interpolations in one string") {
        val date    = LocalDate.of(2024, 1, 15)
        val version = 3
        val env     = "prod"

        assertTrue(
          json"""{"path": "/data/$env/$date/v$version/output.json"}""".get("path").string == Right(
            "/data/prod/2024-01-15/v3/output.json"
          )
        )
      },
      test("handles empty interpolation results") {
        val empty = ""
        assertTrue(
          json"""{"msg": "[$empty]"}""".get("msg").string == Right("[]")
        )
      },
      test("handles special characters in interpolated strings") {
        val path  = "foo/bar"
        val query = "a=1&b=2"

        assertTrue(
          json"""{"url": "http://example.com/$path?$query"}""".get("url").string == Right(
            "http://example.com/foo/bar?a=1&b=2"
          )
        )
      },
      test("compile fails for non-stringable types in string literals") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.json._
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{\"msg\": \"Point is $p\"}"
        """).map(assert(_)(isLeft(containsString("String-literal interpolation"))))
      } @@ exceptNative
    ),
    suite("mixed interpolation contexts")(
      test("combines key, value, and string interpolation") {
        case class Data(value: Int)
        object Data { implicit val schema: Schema[Data] = Schema.derived }

        val key       = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val data      = Data(42)
        val timestamp = Instant.now()

        val result = json"""{
          $key: {
            "data": $data,
            "note": "Recorded at $timestamp"
          }
        }"""

        assertTrue(
          result.get(key.toString).get("data").get("value").int == Right(42),
          result.get(key.toString).get("note").string == Right(s"Recorded at $timestamp")
        )
      },
      test("multiple keys with different stringable types") {
        val intKey  = 1
        val uuidKey = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
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
        case class Item(n: Int)
        object Item { implicit val schema: Schema[Item] = Schema.derived }

        val item = Item(1)
        val num  = 42
        val str  = "hello"

        val result = json"""[$item, $num, $str]"""

        assertTrue(
          result(0).get("n").int == Right(1),
          result(1).int == Right(42),
          result(2).string == Right("hello")
        )
      }
    )
  )
}
