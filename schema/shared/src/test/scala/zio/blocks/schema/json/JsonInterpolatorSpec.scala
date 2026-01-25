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
    test("doesn't compile for non-stringable types in key position") {
      // This test verifies that non-stringable types fail at compile time in key position
      typeCheck(
        """import zio.blocks.schema._
           import zio.blocks.schema.json._
           case class Point(x: Int, y: Int)
           val p = Point(1, 2)
           StringContext("{", ": 1}").json(p)"""
      ).map(assert(_)(isLeft))
    } @@ exceptNative,
    suite("String literal interpolation")(
      test("interpolates String inside JSON string") {
        val name = "Alice"
        assertTrue(
          json"""{"greeting": "Hello $name!"}""".get("greeting").string == Right("Hello Alice!")
        )
      },
      test("interpolates Int inside JSON string") {
        val count = 42
        assertTrue(
          json"""{"message": "You have $count items"}""".get("message").string == Right("You have 42 items")
        )
      },
      test("interpolates multiple values inside JSON string") {
        val name  = "Bob"
        val count = 5
        assertTrue(
          json"""{"message": "Hello $name, you have $count items"}""".get("message").string == Right(
            "Hello Bob, you have 5 items"
          )
        )
      },
      test("interpolates UUID inside JSON string") {
        check(Gen.uuid)(uuid =>
          assertTrue(
            json"""{"id": "User-$uuid"}""".get("id").string == Right(s"User-$uuid")
          )
        )
      },
      test("interpolates LocalDate inside JSON string") {
        check(genLocalDate)(date =>
          assertTrue(
            json"""{"log": "Created on $date"}""".get("log").string == Right(s"Created on $date")
          )
        )
      },
      test("interpolates LocalTime inside JSON string") {
        check(genLocalTime)(time =>
          assertTrue(
            json"""{"log": "Event at $time"}""".get("log").string == Right(s"Event at $time")
          )
        )
      },
      test("interpolates Instant inside JSON string") {
        check(genInstant)(instant =>
          assertTrue(
            json"""{"timestamp": "Recorded: $instant"}""".get("timestamp").string == Right(s"Recorded: $instant")
          )
        )
      },
      test("interpolates Currency inside JSON string") {
        check(Gen.currency)(currency =>
          assertTrue(
            json"""{"price": "Amount in $currency"}""".get("price").string == Right(s"Amount in $currency")
          )
        )
      },
      test("interpolates Double inside JSON string") {
        val value = 3.14159
        assertTrue(
          json"""{"result": "Pi is approximately $value"}""".get("result").string == Right(
            "Pi is approximately 3.14159"
          )
        )
      },
      test("interpolates BigDecimal inside JSON string") {
        val amount = BigDecimal("12345.67")
        assertTrue(
          json"""{"total": "Total: $amount"}""".get("total").string == Right("Total: 12345.67")
        )
      },
      test("interpolates expression syntax inside JSON string") {
        val x = 10
        val y = 20
        assertTrue(
          json"""{"sum": "Result: ${x + y}"}""".get("sum").string == Right("Result: 30")
        )
      },
      test("interpolates empty string") {
        val empty = ""
        assertTrue(
          json"""{"value": "before${empty}after"}""".get("value").string == Right("beforeafter")
        )
      },
      test("interpolates at beginning of string") {
        val prefix = "START"
        assertTrue(
          json"""{"value": "$prefix-end"}""".get("value").string == Right("START-end")
        )
      },
      test("interpolates at end of string") {
        val suffix = "END"
        assertTrue(
          json"""{"value": "start-$suffix"}""".get("value").string == Right("start-END")
        )
      },
      test("interpolates entire string content") {
        val content = "entire content"
        assertTrue(
          json"""{"value": "$content"}""".get("value").string == Right("entire content")
        )
      },
      test("interpolates Boolean inside JSON string") {
        val flag = true
        assertTrue(
          json"""{"status": "Active: $flag"}""".get("status").string == Right("Active: true")
        )
      },
      test("interpolates Char inside JSON string") {
        val grade = 'A'
        assertTrue(
          json"""{"grade": "Grade: $grade"}""".get("grade").string == Right("Grade: A")
        )
      },
      test("interpolates Long inside JSON string") {
        val bigNum = 9223372036854775807L
        assertTrue(
          json"""{"id": "ID-$bigNum"}""".get("id").string == Right(s"ID-$bigNum")
        )
      },
      test("interpolates path-like string with multiple interpolations") {
        val env     = "production"
        val date    = LocalDate.of(2024, 1, 15)
        val version = 3
        assertTrue(
          json"""{"path": "/data/$env/$date/v$version"}""".get("path").string == Right("/data/production/2024-01-15/v3")
        )
      }
    ),
    suite("Value position with Schema-derived types")(
      test("interpolates case class with Schema") {
        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }
        val person = Person("Alice", 30)
        val result = json"""{"person": $person}"""
        assertTrue(
          result.get("person").get("name").string == Right("Alice"),
          result.get("person").get("age").int == Right(30)
        )
      },
      test("interpolates nested case classes with Schema") {
        case class Address(city: String, zip: String)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }
        case class Employee(name: String, address: Address)
        object Employee {
          implicit val schema: Schema[Employee] = Schema.derived
        }
        val employee = Employee("Bob", Address("NYC", "10001"))
        val result   = json"""{"employee": $employee}"""
        assertTrue(
          result.get("employee").get("name").string == Right("Bob"),
          result.get("employee").get("address").get("city").string == Right("NYC"),
          result.get("employee").get("address").get("zip").string == Right("10001")
        )
      },
      test("interpolates Option of complex type") {
        case class Item(id: Int, name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }
        val someItem: Option[Item] = Some(Item(1, "Widget"))
        val noneItem: Option[Item] = None
        assertTrue(
          json"""{"item": $someItem}""".get("item").get("id").int == Right(1),
          json"""{"item": $noneItem}""".get("item").one == Right(Json.Null)
        )
      },
      test("interpolates List of complex types") {
        case class Point(x: Int, y: Int)
        object Point {
          implicit val schema: Schema[Point] = Schema.derived
        }
        val points = List(Point(1, 2), Point(3, 4))
        val result = json"""{"points": $points}"""
        assertTrue(
          result.get("points").one == Right(
            Json.arr(
              Json.obj("x" -> Json.number(1), "y" -> Json.number(2)),
              Json.obj("x" -> Json.number(3), "y" -> Json.number(4))
            )
          )
        )
      },
      test("interpolates Set of complex types") {
        case class Tag(name: String)
        object Tag {
          implicit val schema: Schema[Tag] = Schema.derived
        }
        val tag    = Tag("scala")
        val tags   = Set(tag)
        val result = json"""{"tags": $tags}"""
        assertTrue(
          result.get("tags").one == Right(Json.arr(Json.obj("name" -> Json.str("scala"))))
        )
      },
      test("interpolates Vector of complex types") {
        case class Score(value: Int)
        object Score {
          implicit val schema: Schema[Score] = Schema.derived
        }
        val scores = Vector(Score(100), Score(200))
        val result = json"""{"scores": $scores}"""
        assertTrue(
          result.get("scores").one == Right(
            Json.arr(
              Json.obj("value" -> Json.number(100)),
              Json.obj("value" -> Json.number(200))
            )
          )
        )
      },
      test("interpolates Map with complex value types") {
        case class Config(enabled: Boolean, threshold: Int)
        object Config {
          implicit val schema: Schema[Config] = Schema.derived
        }
        val configs = Map("feature1" -> Config(true, 10), "feature2" -> Config(false, 20))
        val result  = json"""{"configs": $configs}"""
        assertTrue(
          result.get("configs").get("feature1").get("enabled").boolean == Right(true),
          result.get("configs").get("feature2").get("threshold").int == Right(20)
        )
      }
    ),
    suite("Mixed context tests")(
      test("array with mixed value types") {
        val str    = "hello"
        val num    = 42
        val bool   = true
        val result = json"""{"mixed": [$str, $num, $bool]}"""
        assertTrue(
          result.get("mixed").one == Right(
            Json.arr(
              Json.str("hello"),
              Json.number(42),
              Json.bool(true)
            )
          )
        )
      },
      test("multiple values in object") {
        val name   = "Alice"
        val age    = 30
        val result = json"""{"name": $name, "age": $age}"""
        assertTrue(
          result.get("name").string == Right("Alice"),
          result.get("age").int == Right(30)
        )
      }
    ),
    suite("Compile-time error tests")(
      test("doesn't compile for non-stringable type in string literal position") {
        // This test verifies that non-stringable types fail at compile time in string literal position
        typeCheck(
          """import zio.blocks.schema._
             import zio.blocks.schema.json._
             case class Point(x: Int, y: Int)
             val p = Point(1, 2)
             StringContext("{\"message\": \"Point is ", "\"}").json(p)"""
        ).map(assert(_)(isLeft))
      } @@ exceptNative,
      test("doesn't compile for type without JsonEncoder in value position") {
        // This test verifies that types without JsonEncoder fail at compile time in value position
        typeCheck(
          """import zio.blocks.schema._
             import zio.blocks.schema.json._
             class NoEncoder(val x: Int)
             val obj = new NoEncoder(42)
             StringContext("{\"value\": ", "}").json(obj)"""
        ).map(assert(_)(isLeft))
      } @@ exceptNative
    ),
    suite("Edge cases & robustness")(
      test("handles empty string values") {
        val empty = ""
        assertTrue(
          json"""{"key": $empty}""".get("key").string == Right(""),
          json"""{"$empty": "value"}""".get("").string == Right("value")
        )
      },
      test("handles empty collections") {
        val emptyList: List[Int]       = List.empty
        val emptyMap: Map[String, Int] = Map.empty
        val emptySet: Set[String]      = Set.empty
        assertTrue(
          json"""{"list": $emptyList}""".get("list").one == Right(Json.arr()),
          json"""{"map": $emptyMap}""".get("map").one == Right(Json.obj()),
          json"""{"set": $emptySet}""".get("set").one == Right(Json.arr())
        )
      },
      test("handles large collections") {
        val largeList = (1 to 100).toList
        val result    = json"""{"numbers": $largeList}"""
        assertTrue(
          result.get("numbers").one.map(_.elements.length) == Right(100)
        )
      },
      test("handles special float values") {
        // Note: Infinity and NaN are not valid JSON numbers
        // Test with valid extreme double values instead
        val maxDouble = Double.MaxValue
        val minDouble = Double.MinValue
        assertTrue(
          json"""{"max": $maxDouble}""".get("max").double == Right(maxDouble),
          json"""{"min": $minDouble}""".get("min").double == Right(minDouble)
        )
      },
      test("handles extreme integer values") {
        val minInt  = Int.MinValue
        val maxInt  = Int.MaxValue
        val minLong = Long.MinValue
        val maxLong = Long.MaxValue
        assertTrue(
          json"""{"minInt": $minInt}""".get("minInt").int == Right(minInt),
          json"""{"maxInt": $maxInt}""".get("maxInt").int == Right(maxInt),
          json"""{"minLong": $minLong}""".get("minLong").long == Right(minLong),
          json"""{"maxLong": $maxLong}""".get("maxLong").long == Right(maxLong)
        )
      },
      test("handles deeply nested JSON objects") {
        case class Level5(value: String)
        object Level5 { implicit val schema: Schema[Level5] = Schema.derived }
        case class Level4(child: Level5)
        object Level4 { implicit val schema: Schema[Level4] = Schema.derived }
        case class Level3(child: Level4)
        object Level3 { implicit val schema: Schema[Level3] = Schema.derived }
        case class Level2(child: Level3)
        object Level2 { implicit val schema: Schema[Level2] = Schema.derived }
        case class Level1(child: Level2)
        object Level1 { implicit val schema: Schema[Level1] = Schema.derived }

        val nested = Level1(Level2(Level3(Level4(Level5("deep")))))
        val result = json"""{"root": $nested}"""
        assertTrue(
          result
            .get("root")
            .get("child")
            .get("child")
            .get("child")
            .get("child")
            .get("value")
            .string == Right("deep")
        )
      },
      test("handles long strings") {
        val longString = "x" * 10000
        assertTrue(
          json"""{"long": $longString}""".get("long").string == Right(longString)
        )
      },
      test("handles unicode edge cases") {
        val unicode = "Hello 世界 🌍 émojis \u0000 \uFFFF"
        assertTrue(
          json"""{"unicode": $unicode}""".get("unicode").string == Right(unicode)
        )
      },
      test("handles special JSON characters in strings") {
        val special = "quote:\" backslash:\\ newline:\n tab:\t"
        assertTrue(
          json"""{"special": $special}""".get("special").string == Right(special)
        )
      },
      test("handles whitespace variations") {
        val name = "Alice"
        val age  = 30
        assertTrue(
          json"""{  "name"  :  $name  ,  "age"  :  $age  }""".get("name").string == Right("Alice"),
          json"""{"name":$name,"age":$age}""".get("age").int == Right(30)
        )
      },
      test("handles multiple interpolations in sequence") {
        val a = 1
        val b = 2
        val c = 3
        val d = 4
        val e = 5
        assertTrue(
          json"""{"values": [$a, $b, $c, $d, $e]}""".get("values").one == Right(
            Json.arr(Json.number(1), Json.number(2), Json.number(3), Json.number(4), Json.number(5))
          )
        )
      }
    ),
    suite("Advanced Schema-derived types")(
      test("interpolates high arity case class") {
        case class HighArity(
          f1: Int,
          f2: Int,
          f3: Int,
          f4: Int,
          f5: Int,
          f6: Int,
          f7: Int,
          f8: Int,
          f9: Int,
          f10: Int
        )
        object HighArity {
          implicit val schema: Schema[HighArity] = Schema.derived
        }
        val value  = HighArity(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = json"""{"record": $value}"""
        assertTrue(
          result.get("record").get("f1").int == Right(1),
          result.get("record").get("f10").int == Right(10)
        )
      },
      test("interpolates recursive type") {
        case class TreeNode(value: Int, children: List[TreeNode])
        object TreeNode {
          implicit val schema: Schema[TreeNode] = Schema.derived
        }
        val tree   = TreeNode(1, List(TreeNode(2, List()), TreeNode(3, List(TreeNode(4, List())))))
        val result = json"""{"tree": $tree}"""
        assertTrue(
          result.get("tree").get("value").int == Right(1),
          result.get("tree").get("children").one.map(_.elements.length) == Right(2)
        )
      },
      test("interpolates sealed trait with case classes") {
        sealed trait Shape
        case class Circle(radius: Double)          extends Shape
        case class Square(side: Double)            extends Shape
        case class Rectangle(w: Double, h: Double) extends Shape
        object Shape {
          implicit val schema: Schema[Shape] = Schema.derived
        }

        val circle: Shape    = Circle(5.0)
        val square: Shape    = Square(4.0)
        val rectangle: Shape = Rectangle(3.0, 2.0)

        assertTrue(
          json"""{"shape": $circle}""".get("shape").get("Circle").get("radius").double == Right(5.0),
          json"""{"shape": $square}""".get("shape").get("Square").get("side").double == Right(4.0),
          json"""{"shape": $rectangle}""".get("shape").get("Rectangle").get("w").double == Right(3.0)
        )
      },
      test("interpolates case class with Option fields") {
        case class User(name: String, email: Option[String], age: Option[Int])
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }
        val userWithAll = User("Alice", Some("alice@example.com"), Some(30))
        val userPartial = User("Bob", None, Some(25))
        val userMinimal = User("Charlie", None, None)

        // Option[A] with Some is encoded directly as the value, None fields are omitted
        assertTrue(
          json"""{"user": $userWithAll}""".get("user").get("email").string == Right("alice@example.com"),
          json"""{"user": $userWithAll}""".get("user").get("age").int == Right(30),
          json"""{"user": $userPartial}""".get("user").get("name").string == Right("Bob"),
          json"""{"user": $userPartial}""".get("user").get("age").int == Right(25),
          json"""{"user": $userMinimal}""".get("user").get("name").string == Right("Charlie")
        )
      },
      test("interpolates nested collections") {
        case class Matrix(rows: List[List[Int]])
        object Matrix {
          implicit val schema: Schema[Matrix] = Schema.derived
        }
        val matrix = Matrix(List(List(1, 2, 3), List(4, 5, 6), List(7, 8, 9)))
        val result = json"""{"matrix": $matrix}"""
        assertTrue(
          result.get("matrix").get("rows").one.map(_.elements.length) == Right(3)
        )
      },
      test("interpolates Map with complex values") {
        case class Value(data: Int)
        object Value { implicit val schema: Schema[Value] = Schema.derived }

        val complexMap = Map("key1" -> Value(100), "key2" -> Value(200))
        val result     = json"""{"data": $complexMap}"""
        assertTrue(
          result.get("data").get("key1").get("data").int == Right(100),
          result.get("data").get("key2").get("data").int == Right(200)
        )
      }
    ),
    suite("Key position type safety")(
      test("all stringable types work as keys") {
        val strKey     = "stringKey"
        val uuidKey    = java.util.UUID.randomUUID()
        val instantKey = java.time.Instant.now()
        // Note: All JSON keys must be strings, so stringable types are converted to string
        assertTrue(
          json"""{$strKey: 1}""".get(strKey).int == Right(1),
          json"""{$uuidKey: 4}""".get(uuidKey.toString).int == Right(4),
          json"""{$instantKey: 5}""".get(instantKey.toString).int == Right(5)
        )
      },
      test("dynamic key with value interpolation") {
        val key   = "dynamicKey"
        val value = Map("nested" -> 42)
        assertTrue(
          json"""{$key: $value}""".get(key).get("nested").int == Right(42)
        )
      }
    ),
    suite("String literal interpolation edge cases")(
      test("handles special characters around interpolation") {
        val name = "Alice"
        // Test with characters that don't need escaping in JSON strings
        assertTrue(
          json"""{"message": "Hello ($name)!"}""".get("message").string == Right("Hello (Alice)!"),
          json"""{"message": "Hello [$name]!"}""".get("message").string == Right("Hello [Alice]!")
        )
      },
      test("handles multiple lines with interpolation") {
        val line1 = "first"
        val line2 = "second"
        // The \n in the string literal is a literal backslash-n, not a newline
        // Test combining two variables with a separator
        assertTrue(
          json"""{"text": "$line1 and $line2"}""".get("text").string == Right("first and second")
        )
      },
      test("handles mixed key and string literal interpolation") {
        val key  = "greeting"
        val name = "World"
        assertTrue(
          json"""{$key: "Hello $name!"}""".get(key).string == Right("Hello World!")
        )
      },
      test("handles adjacent string literal interpolations") {
        val a = "A"
        val b = "B"
        val c = "C"
        assertTrue(
          json"""{"value": "$a$b$c"}""".get("value").string == Right("ABC")
        )
      }
    ),
    test("supports interpolated Map values with ZonedDateTime keys") {
      check(genZonedDateTime)(x =>
        assertTrue(
          json"""{"x": ${Map(x -> null)}}""".get("x").one == Right(Json.obj(x.toString -> Json.Null))
        )
      )
    }
  )
}
