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
    test("doesn't compile for invalid json") {
      typeCheck {
        """json"1e""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: unexpected end of input at: .")))) &&
      typeCheck {
        """json"[1,02]""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: illegal number with leading zero at: .at(1)"))))
    } @@ exceptNative
  )
}
