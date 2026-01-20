package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.json.interpolators._

object JsonSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    test("is null") {
      val json = Json.Null
      assertTrue(json.isNull)
    },
    test("is boolean") {
      val json = Json.Boolean(true)
      assertTrue(json.isBoolean)
    },
    test("is number") {
      val json = Json.Number("1")
      assertTrue(json.isNumber)
    },
    test("is string") {
      val json = Json.String("foo")
      assertTrue(json.isString)
    },
    test("is array") {
      val json = Json.Array(Vector(Json.Number("1")))
      assertTrue(json.isArray)
    },
    test("is object") {
      val json = Json.Object(Vector("foo" -> Json.Number("1")))
      assertTrue(json.isObject)
    },
    test("get") {
      val json = Json.Object(Vector("foo" -> Json.Array(Vector(Json.Number("1")))))
      val optic = DynamicOptic.root.field("foo").at(0)
      assertTrue(json.get(optic).one == Right(Json.Number("1")))
    },
    suite("ADT Construction")(
      test("Construct Object") {
        val obj = Json.Object("foo" -> Json.String("bar"), "baz" -> Json.number(1))
        assert(obj.fields)(hasSize(equalTo(2))) &&
        assert(obj.isObject)(isTrue)
      },
      test("Construct Array") {
        val arr = Json.Array(Json.String("foo"), Json.number(1))
        assert(arr.elements)(hasSize(equalTo(2))) &&
        assert(arr.isArray)(isTrue)
      }
    ),
    suite("Navigation")(
      test("get field") {
        val obj: Json = Json.Object("foo" -> Json.String("bar"))
        val path = DynamicOptic.root.field("foo")
        assert(obj.get(path).one)(isRight(equalTo(Json.String("bar"))))
      },
      test("get index") {
        val arr: Json = Json.Array(Json.String("foo"), Json.String("bar"))
        val path = DynamicOptic.root.at(1)
        assert(arr.get(path).one)(isRight(equalTo(Json.String("bar"))))
      }
    ),
    suite("Interpolators")(
      test("path string") {
         // This tests the macro expansion (runtime or compile time)
         val path = p"foo.bar"
         val expected = DynamicOptic.root.field("foo").field("bar")
         // Since our macro implementation is basic, let's verify what it produces
         assert(path)(equalTo(expected))
      }
    ),
    suite("Modification")(
      test("modify field") {
        val obj: Json = Json.Object("foo" -> Json.String("bar"))
        val path = DynamicOptic.root.field("foo")
        val modified = obj.modify(path, _ => Json.String("baz"))
        assert(modified)(equalTo(Json.Object("foo" -> Json.String("baz"))))
      }
    ),
    suite("DynamicValue Interop")(
      test("Round trip") {
        val original = Json.Object("foo" -> Json.String("bar"), "num" -> Json.number(123))
        val dv = original.toDynamicValue
        val result = Json.fromDynamicValue(dv)
        assert(result)(equalTo(original))
      }
    )
  )
}
