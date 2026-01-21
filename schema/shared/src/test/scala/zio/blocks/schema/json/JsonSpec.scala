package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.json.JsonGen._

object JsonSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("DynamicValue Conversion")(
      test("roundtrip toDynamicValue -> fromDynamicValue") {
        check(genJson(2)) { json =>
          val dynamicValue = json.toDynamicValue
          val result       = Json.fromDynamicValue(dynamicValue)
          assertTrue(result == json)
        }
      },
      test("roundtrip fromDynamicValue -> toDynamicValue") {
        check(zio.blocks.schema.DynamicValueGen.genDynamicValue) { dynamicValue =>
          val json   = Json.fromDynamicValue(dynamicValue)
          val result = json.toDynamicValue
          // Note: This might not be perfectly symmetric because Json doesn't distinguish all DynamicValue types (e.g. Byte vs Int)
          // But converting back to Json should yield the same Json
          assertTrue(Json.fromDynamicValue(result) == json)
        }
      }
    ),
    suite("Parsing & Encoding")(
      test("roundtrip encode -> parse") {
        check(genJson(2)) { json =>
          val encoded = Json.encode(json)
          val parsed  = Json.parse(encoded)
          assert(parsed)(isRight(equalTo(json)))
        }
      }
    ),
    suite("Typed API")(
      test("as[String]") {
        val json = Json.String("foo")
        assert(json.as[String])(isRight(equalTo("foo")))
      },
      test("as[Int]") {
        val json = Json.Number("123")
        assert(json.as[Int])(isRight(equalTo(123)))
      },
      test("from[String]") {
        val json = Json.from("foo")
        assertTrue(json == Json.String("foo"))
      },
      test("from[Int]") {
        val json = Json.from(123)
        assertTrue(json == Json.Number("123"))
      }
    ),
    suite("Navigation")(
      test("get field") {
        val json      = Json.Object(Vector("foo" -> Json.String("bar")))
        val selection = json.get(DynamicOptic(Vector(DynamicOptic.Node.Field("foo"))))
        assert(selection.first)(isRight(equalTo(Json.String("bar"))))
      },
      test("get index") {
        val json      = Json.Array(Vector(Json.String("foo"), Json.String("bar")))
        val selection = json.get(DynamicOptic(Vector(DynamicOptic.Node.AtIndex(1))))
        assert(selection.first)(isRight(equalTo(Json.String("bar"))))
      }
    )
  )
}
