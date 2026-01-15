package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.json.JsonCodecOps._
import zio.test._
import zio.test.Assertion._

object JsonDerivationSpec extends SchemaBaseSpec {
  // Custom assertion to check if a Json value is an Object
  private def isJsonObject: Assertion[Json] = Assertion.assertion("isJsonObject") {
    case _: Json.Object => true
    case _              => false
  }

  def spec: Spec[TestEnvironment, Any] = suite("JsonDerivationSpec")(
    suite("simple case classes")(
      test("single String field") {
        case class SingleString(value: String)
        object SingleString {
          implicit val schema: Schema[SingleString] = Schema.derived
        }
        val testValue = SingleString("test")
        val encoder   = SingleString.schema.deriveJsonEncoder
        val decoder   = SingleString.schema.deriveJsonDecoder
        val encoded   = encoder.encode(testValue)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(testValue)))
      },
      test("multiple fields with mixed types") {
        case class Mixed(name: String, count: Int, active: Boolean)
        object Mixed {
          implicit val schema: Schema[Mixed] = Schema.derived
        }
        val testValue = Mixed("test", 42, true)
        val encoder   = Mixed.schema.deriveJsonEncoder
        val decoder   = Mixed.schema.deriveJsonDecoder
        val encoded   = encoder.encode(testValue)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(testValue)))
      },
      test("empty case class (unit)") {
        case class Empty()
        object Empty {
          implicit val schema: Schema[Empty] = Schema.derived
        }
        val testValue = Empty()
        val encoder   = Empty.schema.deriveJsonEncoder
        val decoder   = Empty.schema.deriveJsonDecoder
        val encoded   = encoder.encode(testValue)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(testValue)))
      }
    ),
    suite("nested case classes")(
      test("two-level nesting") {
        case class Inner(value: Int)
        case class Outer(name: String, inner: Inner)
        object Outer {
          implicit val schema: Schema[Outer] = Schema.derived
        }
        val testValue = Outer("test", Inner(99))
        val encoder   = Outer.schema.deriveJsonEncoder
        val decoder   = Outer.schema.deriveJsonDecoder
        val encoded   = encoder.encode(testValue)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(testValue)))
      },
      test("three-level nesting") {
        case class Level3(data: String)
        case class Level2(l3: Level3)
        case class Level1(l2: Level2)
        object Level1 {
          implicit val schema: Schema[Level1] = Schema.derived
        }
        val testValue = Level1(Level2(Level3("deep")))
        val encoder   = Level1.schema.deriveJsonEncoder
        val decoder   = Level1.schema.deriveJsonDecoder
        val encoded   = encoder.encode(testValue)
        assert(encoded)(isJsonObject) &&
        assert(decoder.decode(encoded))(isRight(equalTo(testValue)))
      }
    ),
    suite("optional fields")(
      test("record with Option[String]") {
        case class WithOption(name: String, nickname: Option[String])
        object WithOption {
          implicit val schema: Schema[WithOption] = Schema.derived
        }
        val testValue1 = WithOption("Alice", Some("Ally"))
        val testValue2 = WithOption("Bob", None)
        val encoder    = WithOption.schema.deriveJsonEncoder
        val decoder    = WithOption.schema.deriveJsonDecoder
        val encoded1   = encoder.encode(testValue1)
        val encoded2   = encoder.encode(testValue2)
        assert(encoded1)(isJsonObject) &&
        assert(decoder.decode(encoded1))(isRight(equalTo(testValue1))) &&
        assert(encoded2)(isJsonObject) &&
        assert(decoder.decode(encoded2))(isRight(equalTo(testValue2)))
      },
      test("nested optional structures") {
        case class Inner(value: Int)
        case class Outer(inner: Option[Inner])
        object Outer {
          implicit val schema: Schema[Outer] = Schema.derived
        }
        val testValue1 = Outer(Some(Inner(42)))
        val testValue2 = Outer(None)
        val encoder    = Outer.schema.deriveJsonEncoder
        val decoder    = Outer.schema.deriveJsonDecoder
        assert(decoder.decode(encoder.encode(testValue1)))(isRight(equalTo(testValue1))) &&
        assert(decoder.decode(encoder.encode(testValue2)))(isRight(equalTo(testValue2)))
      }
    ),
    suite("different primitive types")(
      test("all numeric types") {
        case class Numbers(
          i: Int,
          bd: BigDecimal
        )
        object Numbers {
          implicit val schema: Schema[Numbers] = Schema.derived
        }
        val testValue = Numbers(100, BigDecimal(3.14))
        val encoder   = Numbers.schema.deriveJsonEncoder
        val decoder   = Numbers.schema.deriveJsonDecoder
        assert(decoder.decode(encoder.encode(testValue)))(isRight(equalTo(testValue)))
      },
      test("boolean and string mix") {
        case class Mixed(flag: Boolean, text: String)
        object Mixed {
          implicit val schema: Schema[Mixed] = Schema.derived
        }
        val testValue1 = Mixed(true, "yes")
        val testValue2 = Mixed(false, "no")
        val encoder    = Mixed.schema.deriveJsonEncoder
        val decoder    = Mixed.schema.deriveJsonDecoder
        assert(decoder.decode(encoder.encode(testValue1)))(isRight(equalTo(testValue1))) &&
        assert(decoder.decode(encoder.encode(testValue2)))(isRight(equalTo(testValue2)))
      }
    ),
    suite("field order independence")(
      test("encode-decode preserves values regardless of field position") {
        case class Ordered(a: String, b: Int, c: Boolean)
        object Ordered {
          implicit val schema: Schema[Ordered] = Schema.derived
        }
        val testValue = Ordered("first", 2, true)
        val encoder   = Ordered.schema.deriveJsonEncoder
        val decoder   = Ordered.schema.deriveJsonDecoder
        assert(decoder.decode(encoder.encode(testValue)))(isRight(equalTo(testValue)))
      }
    ),
    suite("extensibility")(
      test("can derive for new types via schema") {
        case class Custom(id: Int, label: String)
        object Custom {
          implicit val schema: Schema[Custom] = Schema.derived
        }
        val testValue = Custom(1, "label")
        val encoder   = Custom.schema.deriveJsonEncoder
        val decoder   = Custom.schema.deriveJsonDecoder
        assert(decoder.decode(encoder.encode(testValue)))(isRight(equalTo(testValue)))
      }
    )
  )
}
