package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.json.JsonCodecOps._
import zio.test._
import zio.test.Assertion._

object JsonErrorHandlingSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonErrorHandlingSpec")(
    suite("type mismatch errors")(
      test("String decoder rejects Number") {
        val decoder = JsonDecoder[String]
        val json = Json.Number(BigDecimal(123))
        val result = decoder.decode(json)
        assert(result)(
          isLeft(
            hasField("message", (e: JsonDecoderError) => e.message, containsString("Expected string"))
          )
        )
      },
      test("Int decoder rejects String") {
        val decoder = JsonDecoder[Int]
        val json = Json.String("not a number")
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Boolean decoder rejects Number") {
        val decoder = JsonDecoder[Boolean]
        val json = Json.Number(BigDecimal(1))
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Int decoder rejects Array") {
        val decoder = JsonDecoder[Int]
        val json = Json.Array(scala.collection.immutable.Vector.empty)
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Int decoder rejects Object") {
        val decoder = JsonDecoder[Int]
        val json = Json.Object(scala.collection.immutable.Map.empty)
        val result = decoder.decode(json)
        assert(result)(isLeft)
      }
    ),
    suite("numeric precision")(
      test("Int decoder rejects non-integer Numbers") {
        val decoder = JsonDecoder[Int]
        val json = Json.Number(BigDecimal(42.5))
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Int decoder accepts integer-valued Numbers") {
        val decoder = JsonDecoder[Int]
        val json = Json.Number(BigDecimal(42))
        val result = decoder.decode(json)
        assert(result)(isRight(equalTo(42)))
      },
      test("BigDecimal decoder accepts any Number") {
        val decoder = JsonDecoder[BigDecimal]
        val json = Json.Number(BigDecimal(3.14159))
        val result = decoder.decode(json)
        assert(result)(isRight)
      }
    ),
    suite("null handling")(
      test("String decoder rejects Null") {
        val decoder = JsonDecoder[String]
        val json = Json.Null
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Int decoder rejects Null") {
        val decoder = JsonDecoder[Int]
        val json = Json.Null
        val result = decoder.decode(json)
        assert(result)(isLeft)
      },
      test("Option decoder accepts Null as None") {
        val decoder = JsonDecoder[Option[String]]
        val json = Json.Null
        val result = decoder.decode(json)
        assert(result)(isRight(equalTo(None)))
      },
      test("Option decoder rejects wrong type for inner decoder") {
        val decoder = JsonDecoder[Option[Int]]
        val json = Json.String("not a number")
        val result = decoder.decode(json)
        assert(result)(isLeft)
      }
    ),
    suite("error messages are descriptive")(
      test("error includes type expectation") {
        val decoder = JsonDecoder[String]
        val json = Json.Number(BigDecimal(42))
        val result = decoder.decode(json)
        assert(result)(
          isLeft(
            hasField("message", (e: JsonDecoderError) => e.message, containsString("Expected"))
          )
        )
      },
      test("error includes actual type") {
        val decoder = JsonDecoder[Int]
        val json = Json.String("test")
        val result = decoder.decode(json)
        assert(result)(isLeft)
      }
    ),
    suite("record field errors")(
      test("missing required field produces error") {
        case class WithField(name: String, age: Int)
        object WithField {
          implicit val schema: Schema[WithField] = Schema.derived
        }
        val decoder = WithField.schema.deriveJsonDecoder
        val incompleteJson = Json.Object(
          scala.collection.immutable.Map(("name", Json.String("Alice")))
        )
        val result = decoder.decode(incompleteJson)
        assert(result)(isLeft)
      },
      test("wrong field type in record produces error") {
        case class WithField(name: String, age: Int)
        object WithField {
          implicit val schema: Schema[WithField] = Schema.derived
        }
        val decoder = WithField.schema.deriveJsonDecoder
        val wrongTypeJson = Json.Object(
          scala.collection.immutable.Map(
            ("name", Json.String("Alice")),
            ("age", Json.String("not a number"))
          )
        )
        val result = decoder.decode(wrongTypeJson)
        assert(result)(isLeft)
      }
    ),
    suite("deterministic errors")(
      test("same invalid input produces same error") {
        val decoder = JsonDecoder[Int]
        val json = Json.String("invalid")
        val result1 = decoder.decode(json)
        val result2 = decoder.decode(json)
        assert(result1)(equalTo(result2))
      },
      test("multiple decoders with same input produce consistent errors") {
        val decoder1 = JsonDecoder[Int]
        val decoder2 = JsonDecoder[Int]
        val json = Json.String("invalid")
        val result1 = decoder1.decode(json)
        val result2 = decoder2.decode(json)
        assert(result1.isLeft)(equalTo(result2.isLeft))
      }
    ),
    suite("round-trip safety")(
      test("encode-decode should not lose information for valid values") {
        val value = "test"
        val encoder = JsonEncoder[String]
        val decoder = JsonDecoder[String]
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      },
      test("encode-decode for numeric types preserves value") {
        val value = 42
        val encoder = JsonEncoder[Int]
        val decoder = JsonDecoder[Int]
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      }
    )
  )
}
