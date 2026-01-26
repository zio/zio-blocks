package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicValueToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueToStringSpec")(
    test("Primitive.toString") {
      assert(DynamicValue.Primitive(PrimitiveValue.Int(1)).toString)(equalTo("1"))
      assert(DynamicValue.Primitive(PrimitiveValue.String("foo")).toString)(equalTo("\"foo\""))
    },
    test("Record.toString") {
      val record = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
        "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
      ))
      assert(record.toString)(equalTo("""{"name": "Alice", "age": 30}"""))
    },
    test("Variant.toString") {
      val variant = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
      assert(variant.toString)(equalTo("""{"Some": 1}"""))
    },
    test("Sequence.toString") {
      val seq = DynamicValue.Sequence(Vector(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2))
      ))
      assert(seq.toString)(equalTo("[1, 2]"))
    },
    test("Map.toString") {
      val map = DynamicValue.Map(Vector(
        DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
      ))
      assert(map.toString)(equalTo("""{"a": 1}"""))
    }
  )
}
