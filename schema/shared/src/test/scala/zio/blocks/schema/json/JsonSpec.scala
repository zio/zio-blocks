package zio.blocks.schema.json

import zio.blocks.schema.{SchemaBaseSpec, DynamicValue, PrimitiveValue}
import zio.test._
import zio.test.Assertion._

object JsonSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSpec")(
    suite("Json ADT")(
      test("create null") {
        assert(Json.Null.isNull)(isTrue) &&
        assert(Json.Null.isObject)(isFalse) &&
        assert(Json.Null.isArray)(isFalse)
      },
      test("create boolean") {
        val t = Json.Boolean(true)
        val f = Json.Boolean(false)
        assert(t.isBoolean)(isTrue) &&
        assert(t.booleanValue)(isSome(isTrue)) &&
        assert(f.booleanValue)(isSome(isFalse))
      },
      test("create number") {
        val n = Json.number(42)
        assert(n.isNumber)(isTrue) &&
        assert(n.numberValue)(isSome(equalTo("42"))) &&
        assert(n.asInstanceOf[Json.Number].toInt)(isSome(equalTo(42)))
      },
      test("create string") {
        val s = Json.String("hello")
        assert(s.isString)(isTrue) &&
        assert(s.stringValue)(isSome(equalTo("hello")))
      },
      test("create array") {
        val arr = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        assert(arr.isArray)(isTrue) &&
        assert(arr.elements.size)(equalTo(3))
      },
      test("create object") {
        val obj = Json.Object(Vector("name" -> Json.String("Alice"), "age" -> Json.number(30)))
        assert(obj.isObject)(isTrue) &&
        assert(obj.fields.size)(equalTo(2)) &&
        assert(obj.get("name"))(isSome(equalTo(Json.String("Alice"))))
      }
    ),
    suite("Navigation")(
      test("navigate object by key") {
        val obj = Json.Object("name" -> Json.String("Bob"), "age" -> Json.number(25))
        val selection = obj("name")
        assert(selection.values.size)(equalTo(1)) &&
        assert(selection.values.head)(equalTo(Json.String("Bob")))
      },
      test("navigate array by index") {
        val arr = Json.Array(Vector(Json.number(10), Json.number(20), Json.number(30)))
        val selection = arr(1)
        assert(selection.values.size)(equalTo(1)) &&
        assert(selection.values.head)(equalTo(Json.number(20)))
      }
    ),
    suite("Type filtering")(
      test("filter objects") {
        val json = Json.Array(Vector(
          Json.Object("a" -> Json.number(1)),
          Json.number(2),
          Json.Object("b" -> Json.number(3))
        ))
        val selection = JsonSelection(json.asInstanceOf[Json.Array].elements)
        val objects = selection.objects
        assert(objects.values.size)(equalTo(2))
      },
      test("filter numbers") {
        val json = Json.Array(Vector(Json.number(1), Json.String("two"), Json.number(3)))
        val selection = JsonSelection(json.asInstanceOf[Json.Array].elements)
        val numbers = selection.numbers
        assert(numbers.values.size)(equalTo(2))
      }
    ),
    suite("DynamicValue conversion")(
      test("convert null to DynamicValue") {
        val dv = Json.Null.toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.Unit)))
      },
      test("convert boolean to DynamicValue") {
        val dv = Json.Boolean(true).toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("convert number to DynamicValue") {
        val dv = Json.number(42).toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42)))))
      },
      test("convert string to DynamicValue") {
        val dv = Json.String("hello").toDynamicValue
        assert(dv)(equalTo(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("convert array to DynamicValue") {
        val arr = Json.Array(Vector(Json.number(1), Json.number(2)))
        val dv = arr.toDynamicValue
        assert(dv)(isSubtype[DynamicValue.Sequence](anything))
      },
      test("convert object to DynamicValue") {
        val obj = Json.Object("name" -> Json.String("Alice"))
        val dv = obj.toDynamicValue
        assert(dv)(isSubtype[DynamicValue.Record](anything))
      },
      test("round-trip DynamicValue conversion") {
        val original = Json.Object(
          "name" -> Json.String("Alice"),
          "age" -> Json.number(30),
          "active" -> Json.Boolean(true)
        )
        val dv = original.toDynamicValue
        val restored = Json.fromDynamicValue(dv)
        assert(restored)(equalTo(original))
      }
    ),
    suite("Merge")(
      test("merge objects") {
        val obj1 = Json.Object("a" -> Json.number(1), "b" -> Json.number(2))
        val obj2 = Json.Object("b" -> Json.number(3), "c" -> Json.number(4))
        val merged = obj1.merge(obj2)
        val expected = Json.Object("a" -> Json.number(1), "b" -> Json.number(3), "c" -> Json.number(4))
        assert(merged)(equalTo(expected))
      },
      test("merge arrays") {
        val arr1 = Json.Array(Vector(Json.number(1), Json.number(2)))
        val arr2 = Json.Array(Vector(Json.number(3), Json.number(4)))
        val merged = arr1.merge(arr2)
        val expected = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3), Json.number(4)))
        assert(merged)(equalTo(expected))
      }
    ),
    suite("Comparison")(
      test("compare numbers") {
        assert(Json.number(1).compare(Json.number(2)))(isLessThan(0)) &&
        assert(Json.number(2).compare(Json.number(1)))(isGreaterThan(0)) &&
        assert(Json.number(1).compare(Json.number(1)))(equalTo(0))
      },
      test("compare strings") {
        assert(Json.String("a").compare(Json.String("b")))(isLessThan(0)) &&
        assert(Json.String("b").compare(Json.String("a")))(isGreaterThan(0))
      }
    )
  )
}

