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
    } @@ exceptNative,
    suite("String literal interpolation")(
      test("interpolates String inside string literal") {
        val name = "Alice"
        assertTrue(
          json"""{"message": "Hello, $name!"}""".get("message").string == Right("Hello, Alice!")
        )
      },
      test("interpolates Int inside string literal") {
        val age = 25
        assertTrue(
          json"""{"message": "You are $age years old"}""".get("message").string == Right("You are 25 years old")
        )
      },
      test("interpolates Boolean inside string literal") {
        val active = true
        assertTrue(
          json"""{"status": "Active: $active"}""".get("status").string == Right("Active: true")
        )
      },
      test("interpolates UUID inside string literal") {
        val id = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertTrue(
          json"""{"ref": "ID: $id"}""".get("ref").string == Right("ID: 123e4567-e89b-12d3-a456-426614174000")
        )
      },
      test("interpolates temporal types inside string literal") {
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(
          json"""{"event": "Meeting on $date"}""".get("event").string == Right("Meeting on 2024-01-15")
        )
      },
      test("escapes special characters in string literal interpolation") {
        val text = "line1\nline2\ttabbed"
        assertTrue(
          json"""{"content": "Text: $text"}""".get("content").string == Right("Text: line1\nline2\ttabbed")
        )
      },
      test("escapes quotes in string literal interpolation") {
        val quote = """He said "hello""""
        assertTrue(
          json"""{"speech": "$quote"}""".get("speech").string == Right("""He said "hello"""")
        )
      },
      test("interpolates at start of string literal") {
        val prefix = "Hello"
        assertTrue(
          json"""{"msg": "$prefix, World!"}""".get("msg").string == Right("Hello, World!")
        )
      },
      test("interpolates at end of string literal") {
        val suffix = "!"
        assertTrue(
          json"""{"msg": "Hello, World$suffix"}""".get("msg").string == Right("Hello, World!")
        )
      },
      test("interpolates whole string literal") {
        val whole = "Complete message"
        assertTrue(
          json"""{"msg": "$whole"}""".get("msg").string == Right("Complete message")
        )
      },
      test("mixes string literal and value interpolation") {
        val name = "Alice"
        val age  = 25
        assertTrue(
          json"""{"message": "Name: $name", "age": $age}""" ==
            Json.obj("message" -> Json.str("Name: Alice"), "age" -> Json.number(25))
        )
      },
      test("handles empty interpolated string in string literal") {
        val empty = ""
        assertTrue(
          json"""{"msg": "Hello$empty World"}""".get("msg").string == Right("Hello World")
        )
      },
      test("handles Unicode in string literal interpolation") {
        val emoji = "★🎸"
        assertTrue(
          json"""{"msg": "Stars: $emoji!"}""".get("msg").string == Right("Stars: ★🎸!")
        )
      },
      test("handles backslash in string literal interpolation") {
        val path = "C:\\Users\\Alice"
        assertTrue(
          json"""{"path": "Location: $path"}""".get("path").string == Right("Location: C:\\Users\\Alice")
        )
      },
      test("interpolates BigDecimal inside string literal") {
        val amount = BigDecimal("123.456")
        assertTrue(
          json"""{"price": "Cost: $amount USD"}""".get("price").string == Right("Cost: 123.456 USD")
        )
      },
      test("interpolates Currency inside string literal") {
        val currency = java.util.Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").string == Right("Price in USD")
        )
      },
      test("supports ${expr} syntax for expressions") {
        val x = 10
        assertTrue(
          json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").string == Right("20 to 30")
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
      test("compile fails for non-stringable types in string literals") {
        // Note: typeCheck requires a statically known string, so we can't test triple-quoted json strings
        // Instead, we test with a simple string interpolation that would fail
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.json._
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{ \"msg\": \"Point is $p\" }"
          """
        ).map(assert(_)(isLeft(containsString("string literal"))))
      } @@ exceptNative
    ),
    suite("Value position with Schema")(
      test("supports Json values from types with Schema") {
        case class Address(street: String, city: String)
        object Address { implicit val schema: Schema[Address] = Schema.derived }

        case class Person(name: String, age: Int, address: Address)
        object Person { implicit val schema: Schema[Person] = Schema.derived }

        val codec     = Person.schema.derive(JsonBinaryCodecDeriver)
        val alice     = Person("Alice", 30, Address("123 Main", "NYC"))
        val aliceJson = Json.jsonCodec.decode(codec.encode(alice)).toOption.get
        val result    = json"""{"employee": $aliceJson}"""

        assertTrue(
          result.get("employee").get("name").string == Right("Alice"),
          result.get("employee").get("age").int == Right(30),
          result.get("employee").get("address").get("city").string == Right("NYC")
        )
      },
      test("supports nested complex types as Json") {
        case class Inner(value: Int)
        object Inner { implicit val schema: Schema[Inner] = Schema.derived }

        case class Outer(inner: Inner, inners: List[Inner])
        object Outer { implicit val schema: Schema[Outer] = Schema.derived }

        val codec  = Outer.schema.derive(JsonBinaryCodecDeriver)
        val o      = Outer(Inner(1), List(Inner(2), Inner(3)))
        val oJson  = Json.jsonCodec.decode(codec.encode(o)).toOption.get
        val result = json"""{"data": $oJson}"""

        assertTrue(
          result.get("data").get("inner").get("value").int == Right(1),
          result.get("data").get("inners")(0).get("value").int == Right(2)
        )
      },
      test("supports Option of Json values") {
        val item               = Json.obj("name" -> Json.str("thing"))
        val some: Option[Json] = Some(item)
        val none: Option[Json] = None

        assertTrue(
          json"""{"item": $some}""".get("item").get("name").string == Right("thing"),
          json"""{"item": $none}""".get("item").one == Right(Json.Null)
        )
      },
      test("supports collections of Json values") {
        val point1 = Json.obj("x" -> Json.number(1), "y" -> Json.number(2))
        val point2 = Json.obj("x" -> Json.number(3), "y" -> Json.number(4))
        val points = List(point1, point2)

        assertTrue(
          json"""{"points": $points}""".get("points")(0).get("x").int == Right(1),
          json"""{"points": $points}""".get("points")(1).get("y").int == Right(4)
        )
      },
      test("supports Map with Json value types") {
        val stats1 = Json.obj("count" -> Json.number(10))
        val stats2 = Json.obj("count" -> Json.number(20))
        val data   = Map("a" -> stats1, "b" -> stats2)
        val result = json"""{"stats": $data}"""

        assertTrue(
          result.get("stats").get("a").get("count").int == Right(10),
          result.get("stats").get("b").get("count").int == Right(20)
        )
      }
    ),
    suite("Mixed interpolation contexts")(
      test("combines key, value, and string interpolation") {
        val key       = java.util.UUID.randomUUID()
        val data      = Json.obj("value" -> Json.number(42))
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
        val uuidKey = java.util.UUID.randomUUID()
        val dateKey = LocalDate.of(2024, 1, 15)

        val result1 = json"""{$intKey: "one"}"""
        val result2 = json"""{$uuidKey: "uuid"}"""
        val result3 = json"""{$dateKey: "date"}"""

        assertTrue(
          result1.get("1").string == Right("one"),
          result2.get(uuidKey.toString).string == Right("uuid"),
          result3.get("2024-01-15").string == Right("date")
        )
      },
      test("array with mixed value types") {
        val item = Json.obj("n" -> Json.number(1))
        val num  = 42
        val str  = "hello"

        val result = json"""[$item, $num, $str]"""

        assertTrue(
          result(0).get("n").int == Right(1),
          result(1).int == Right(42),
          result(2).string == Right("hello")
        )
      }
    ),
    suite("Compile-time error checks")(
      test("compile fails for non-stringable types in key position") {
        // Note: typeCheck requires a statically known string, so we can't test triple-quoted json strings
        // Instead, we test with a simple string interpolation that would fail
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.json._
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{ $p: \"value\" }"
          """
        ).map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative
    )
  )
}
