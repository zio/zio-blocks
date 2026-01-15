package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.json.JsonCodecOps._
import zio.test._
import zio.test.Assertion._

object JsonEncoderDecoderSpec extends SchemaBaseSpec {
  // Custom assertion to check if a Json value is an Object
  private def isJsonObject: Assertion[Json] = Assertion.assertion("isJsonObject") {
    case _: Json.Object => true
    case _ => false
  }

  def spec: Spec[TestEnvironment, Any] = suite("JsonEncoderDecoderSpec")(
    suite("primitives")(
      test("String round-trip") {
        val value = "hello"
        val encoder = JsonEncoder[String]
        val decoder = JsonDecoder[String]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.String("hello"))) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Int round-trip") {
        val value = 42
        val encoder = JsonEncoder[Int]
        val decoder = JsonDecoder[Int]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.Number(BigDecimal(42)))) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Boolean round-trip") {
        val trueValue = true
        val falseValue = false
        val encoder = JsonEncoder[Boolean]
        val decoder = JsonDecoder[Boolean]
        assert(encoder.encode(trueValue))(equalTo(Json.Boolean(true))) &&
        assert(decoder.decode(Json.Boolean(true)))(isRight(equalTo(trueValue))) &&
        assert(encoder.encode(falseValue))(equalTo(Json.Boolean(false))) &&
        assert(decoder.decode(Json.Boolean(false)))(isRight(equalTo(falseValue)))
      },
      test("BigDecimal round-trip") {
        val value = BigDecimal(3.14)
        val encoder = JsonEncoder[BigDecimal]
        val decoder = JsonDecoder[BigDecimal]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.Number(BigDecimal(3.14)))) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      }
    ),
    suite("Option types")(
      test("Option[String] with Some") {
        val value = Some("hello")
        val encoder = JsonEncoder[Option[String]]
        val decoder = JsonDecoder[Option[String]]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.String("hello"))) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Option[String] with None") {
        val value: Option[String] = None
        val encoder = JsonEncoder[Option[String]]
        val decoder = JsonDecoder[Option[String]]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.Null)) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Option[Int] with Some") {
        val value = Some(42)
        val encoder = JsonEncoder[Option[Int]]
        val decoder = JsonDecoder[Option[Int]]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.Number(BigDecimal(42)))) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Option[Int] with None") {
        val value: Option[Int] = None
        val encoder = JsonEncoder[Option[Int]]
        val decoder = JsonDecoder[Option[Int]]
        val encoded = encoder.encode(value)
        assert(encoded)(equalTo(Json.Null)) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      }
    ),
    suite("simple record")(
      test("Person record round-trip") {
        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }
        val value = Person("Alice", 30)
        val encoder = Person.schema.deriveJsonEncoder
        val decoder = Person.schema.deriveJsonDecoder
        val encoded = encoder.encode(value)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      },
      test("Simple integer record round-trip") {
        case class Point(x: Int, y: Int)
        object Point {
          implicit val schema: Schema[Point] = Schema.derived
        }
        val value = Point(10, 20)
        val encoder = Point.schema.deriveJsonEncoder
        val decoder = Point.schema.deriveJsonDecoder
        val encoded = encoder.encode(value)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      }
    ),
    suite("nested structures")(
      test("nested case class round-trip") {
        case class Address(city: String, zip: Int)
        case class Person(name: String, address: Address)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }
        val value = Person("Bob", Address("NYC", 10001))
        val encoder = Person.schema.deriveJsonEncoder
        val decoder = Person.schema.deriveJsonDecoder
        val encoded = encoder.encode(value)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(value)))
      }
    ),
    suite("error cases")(
      test("String decoder rejects Number") {
        val decoder = JsonDecoder[String]
        val json = Json.Number(BigDecimal(123))
        assert(decoder.decode(json))(
          isLeft(
            hasField("message", (e: JsonDecoderError) => e.message, containsString("Expected string"))
          )
        )
      },
      test("Int decoder rejects String") {
        val decoder = JsonDecoder[Int]
        val json = Json.String("not a number")
        assert(decoder.decode(json))(isLeft)
      },
      test("Boolean decoder rejects Number") {
        val decoder = JsonDecoder[Boolean]
        val json = Json.Number(BigDecimal(1))
        assert(decoder.decode(json))(isLeft)
      },
      test("Int decoder rejects Array") {
        val decoder = JsonDecoder[Int]
        val json = Json.Array(scala.collection.immutable.Vector.empty)
        assert(decoder.decode(json))(isLeft)
      },
      test("Int decoder rejects Object") {
        val decoder = JsonDecoder[Int]
        val json = Json.Object(scala.collection.immutable.Map.empty)
        assert(decoder.decode(json))(isLeft)
      },
      test("Int decoder rejects non-integer Numbers") {
        val decoder = JsonDecoder[Int]
        val json = Json.Number(BigDecimal(42.5))
        assert(decoder.decode(json))(isLeft)
      },
      test("Int decoder accepts integer-valued Numbers") {
        val decoder = JsonDecoder[Int]
        val json = Json.Number(BigDecimal(42))
        assert(decoder.decode(json))(isRight(equalTo(42)))
      },
      test("Option decoder accepts Null as None") {
        val decoder = JsonDecoder[Option[String]]
        val json = Json.Null
        assert(decoder.decode(json))(isRight(equalTo(None)))
      }
    ),
    suite("round-trip consistency")(
      test("encode then decode returns original value (String)") {
        val value = "round-trip test"
        val encoder = JsonEncoder[String]
        val decoder = JsonDecoder[String]
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      },
      test("encode then decode returns original value (Int)") {
        val value = 12345
        val encoder = JsonEncoder[Int]
        val decoder = JsonDecoder[Int]
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      },
      test("encode then decode returns original value (BigDecimal)") {
        val value = BigDecimal(99.99)
        val encoder = JsonEncoder[BigDecimal]
        val decoder = JsonDecoder[BigDecimal]
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      }
    )
  )
}

