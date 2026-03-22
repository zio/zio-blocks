/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk
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
        val repr = SchemaRepr.Record(Chunk("name" -> SchemaRepr.Primitive("string")))
        assert(repr.fields)(equalTo(Chunk("name" -> SchemaRepr.Primitive("string"))))
      },
      test("Record creates structural record with multiple fields") {
        val repr = SchemaRepr.Record(
          Chunk(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        assert(repr.fields.length)(equalTo(2))
      },
      test("Variant creates structural variant with cases") {
        val repr = SchemaRepr.Variant(
          Chunk(
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
        val inner   = SchemaRepr.Record(Chunk("street" -> SchemaRepr.Primitive("string")))
        val outer   = SchemaRepr.Record(Chunk("address" -> inner))
        val address = outer.fields.head._2.asInstanceOf[SchemaRepr.Record]
        assert(address.fields.head._1)(equalTo("street"))
      },
      test("Sequence containing Record") {
        val record = SchemaRepr.Record(Chunk("name" -> SchemaRepr.Primitive("string")))
        val seq    = SchemaRepr.Sequence(record)
        assert(seq.element)(equalTo(record))
      },
      test("Map with complex value type") {
        val value = SchemaRepr.Record(Chunk("x" -> SchemaRepr.Primitive("int")))
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
        val repr = SchemaRepr.Record(Chunk("name" -> SchemaRepr.Primitive("string")))
        assert(repr.toString)(equalTo("record { name: string }"))
      },
      test("Record renders multiple fields comma-separated") {
        val repr = SchemaRepr.Record(
          Chunk(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        assert(repr.toString)(equalTo("record { name: string, age: int }"))
      },
      test("Variant renders with cases") {
        val repr = SchemaRepr.Variant(
          Chunk(
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
          Chunk(
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
        val a = SchemaRepr.Record(Chunk("x" -> SchemaRepr.Primitive("int")))
        val b = SchemaRepr.Record(Chunk("x" -> SchemaRepr.Primitive("int")))
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
          Chunk(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        val dv = Schema[SchemaRepr].toDynamicValue(repr)
        assert(Schema[SchemaRepr].fromDynamicValue(dv))(isRight(equalTo(repr)))
      },
      test("Variant roundtrips through DynamicValue") {
        val repr = SchemaRepr.Variant(
          Chunk(
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
          Chunk(
            "items" -> SchemaRepr.Sequence(
              SchemaRepr.Map(
                SchemaRepr.Primitive("string"),
                SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
              )
            ),
            "tag" -> SchemaRepr.Variant(
              Chunk(
                "A" -> SchemaRepr.Primitive("int"),
                "B" -> SchemaRepr.Record(Chunk("x" -> SchemaRepr.Wildcard))
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
