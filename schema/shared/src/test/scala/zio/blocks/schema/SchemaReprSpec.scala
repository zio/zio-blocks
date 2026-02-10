package zio.blocks.schema

import zio.test.Assertion.{equalTo, isRight}
import zio.test.{Spec, TestEnvironment, assert}

object SchemaReprSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaReprSpec")(
    suite("construction")(
      test("Nominal creates named type reference") {
        val repr = SchemaRepr.Nominal("Person")
        assert(repr)(equalTo(SchemaRepr.Nominal("Person")))
      },
      test("Primitive creates primitive type") {
        val repr = SchemaRepr.Primitive("string")
        assert(repr)(equalTo(SchemaRepr.Primitive("string")))
      },
      test("Record creates structural record with fields") {
        val repr = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        assert(repr.fields)(equalTo(Vector("name" -> SchemaRepr.Primitive("string"))))
      },
      test("Record creates structural record with multiple fields") {
        val repr = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        assert(repr.fields.length)(equalTo(2))
      },
      test("Variant creates structural variant with cases") {
        val repr = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        assert(repr.cases.length)(equalTo(2))
      },
      test("Sequence creates sequence type") {
        val repr = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        assert(repr.element)(equalTo(SchemaRepr.Primitive("string")))
      },
      test("Map creates map type with key and value") {
        val repr = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        assert(repr.key)(equalTo(SchemaRepr.Primitive("string"))) &&
        assert(repr.value)(equalTo(SchemaRepr.Primitive("int")))
      },
      test("Optional creates optional type") {
        val repr = SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
        assert(repr.inner)(equalTo(SchemaRepr.Nominal("Person")))
      },
      test("Wildcard is a singleton") {
        assert(SchemaRepr.Wildcard)(equalTo(SchemaRepr.Wildcard))
      }
    ),
    suite("nesting")(
      test("Record containing Record") {
        val inner   = SchemaRepr.Record(Vector("street" -> SchemaRepr.Primitive("string")))
        val outer   = SchemaRepr.Record(Vector("address" -> inner))
        val address = outer.fields.head._2.asInstanceOf[SchemaRepr.Record]
        assert(address.fields.head._1)(equalTo("street"))
      },
      test("Sequence containing Record") {
        val record = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val seq    = SchemaRepr.Sequence(record)
        assert(seq.element)(equalTo(record))
      },
      test("Map with complex value type") {
        val value = SchemaRepr.Record(Vector("x" -> SchemaRepr.Primitive("int")))
        val map   = SchemaRepr.Map(SchemaRepr.Primitive("string"), value)
        assert(map.value)(equalTo(value))
      },
      test("Optional containing Sequence") {
        val seq = SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        val opt = SchemaRepr.Optional(seq)
        assert(opt.inner)(equalTo(seq))
      }
    ),
    suite("toString rendering")(
      test("Nominal renders as name") {
        assert(SchemaRepr.Nominal("Person").toString)(equalTo("Person"))
      },
      test("Primitive renders as name") {
        assert(SchemaRepr.Primitive("string").toString)(equalTo("string"))
      },
      test("Record renders with braces and fields") {
        val repr = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        assert(repr.toString)(equalTo("record { name: string }"))
      },
      test("Record renders multiple fields comma-separated") {
        val repr = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        assert(repr.toString)(equalTo("record { name: string, age: int }"))
      },
      test("Variant renders with cases") {
        val repr = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        assert(repr.toString)(equalTo("variant { Left: int, Right: string }"))
      },
      test("Sequence renders as list(element)") {
        val repr = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        assert(repr.toString)(equalTo("list(string)"))
      },
      test("Map renders as map(key, value)") {
        val repr = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        assert(repr.toString)(equalTo("map(string, int)"))
      },
      test("Optional renders as option(inner)") {
        val repr = SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
        assert(repr.toString)(equalTo("option(Person)"))
      },
      test("Wildcard renders as underscore") {
        assert(SchemaRepr.Wildcard.toString)(equalTo("_"))
      },
      test("Nested structure renders correctly") {
        val repr = SchemaRepr.Record(
          Vector(
            "items" -> SchemaRepr.Sequence(SchemaRepr.Nominal("Person"))
          )
        )
        assert(repr.toString)(equalTo("record { items: list(Person) }"))
      }
    ),
    suite("equality")(
      test("Nominal equality by name") {
        assert(SchemaRepr.Nominal("Person"))(equalTo(SchemaRepr.Nominal("Person")))
      },
      test("Nominal inequality by different name") {
        val a = SchemaRepr.Nominal("Person")
        val b = SchemaRepr.Nominal("Address")
        assert(a == b)(equalTo(false))
      },
      test("Primitive equality by name") {
        assert(SchemaRepr.Primitive("string"))(equalTo(SchemaRepr.Primitive("string")))
      },
      test("Record equality by fields") {
        val a = SchemaRepr.Record(Vector("x" -> SchemaRepr.Primitive("int")))
        val b = SchemaRepr.Record(Vector("x" -> SchemaRepr.Primitive("int")))
        assert(a)(equalTo(b))
      },
      test("Wildcard equality") {
        assert(SchemaRepr.Wildcard)(equalTo(SchemaRepr.Wildcard))
      }
    ),
    suite("Schema roundtrip")(
      test("Nominal roundtrips through DynamicValue") {
        val repr = SchemaRepr.Nominal("Person")
        val dv   = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Primitive roundtrips through DynamicValue") {
        val repr = SchemaRepr.Primitive("string")
        val dv   = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Record roundtrips through DynamicValue") {
        val repr = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        val dv = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Variant roundtrips through DynamicValue") {
        val repr = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        val dv = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Sequence roundtrips through DynamicValue") {
        val repr = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val dv   = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Map roundtrips through DynamicValue") {
        val repr = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val dv   = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Optional roundtrips through DynamicValue") {
        val repr = SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
        val dv   = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Wildcard roundtrips through DynamicValue") {
        val repr: SchemaRepr = SchemaRepr.Wildcard
        val dv               = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("deeply nested SchemaRepr roundtrips through DynamicValue") {
        val repr = SchemaRepr.Record(
          Vector(
            "items" -> SchemaRepr.Sequence(
              SchemaRepr.Map(
                SchemaRepr.Primitive("string"),
                SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
              )
            ),
            "tag" -> SchemaRepr.Variant(
              Vector(
                "A" -> SchemaRepr.Primitive("int"),
                "B" -> SchemaRepr.Record(Vector("x" -> SchemaRepr.Wildcard))
              )
            )
          )
        )
        val dv = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      }
    )
  )
}
