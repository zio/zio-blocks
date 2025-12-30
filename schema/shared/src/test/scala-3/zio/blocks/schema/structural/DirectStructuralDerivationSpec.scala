package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for direct structural type derivation. This tests Schema.derived[{ def
 * name: String; def age: Int }] and similar.
 */
object DirectStructuralDerivationSpec extends ZIOSpecDefault {
  override def spec = suite("DirectStructuralDerivationSpec")(
    suite("Simple structural types")(
      test("derive schema for simple two-field structural type") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        // Check that schema exists and has correct fields
        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name)

        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age"),
          record.fields.size == 2
        )
      },
      test("derive schema for single-field structural type") {
        type IdStructure = StructuralRecord { def value: String }
        val schema = Schema.derived[IdStructure]

        val record = schema.reflect.asRecord.get
        assertTrue(record.fields.size == 1, record.fields.head.name == "value")
      },
      test("derive schema for multi-field structural type with different primitives") {
        type MixedStructure = StructuralRecord {
          def name: String
          def age: Int
          def score: Double
          def active: Boolean
        }
        val schema = Schema.derived[MixedStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(
          fieldNames == Set("name", "age", "score", "active"),
          record.fields.size == 4
        )
      }
    ),
    suite("TypeName normalization")(
      test("TypeName is normalized with alphabetical field order") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        val typeName = schema.reflect.typeName.name
        // Should be {age:Int,name:String} - alphabetically sorted
        assertTrue(typeName == "{age:Int,name:String}")
      },
      test("TypeName format is {field:Type,...}") {
        type PointStructure = StructuralRecord { def x: Int; def y: Int }
        val schema = Schema.derived[PointStructure]

        // Check the .name property of TypeName, not the full toString
        val typeNameStr = schema.reflect.typeName.name
        assertTrue(typeNameStr == "{x:Int,y:Int}")
      },
      test("TypeName uses Namespace.empty") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        assertTrue(schema.reflect.typeName.namespace == Namespace.empty)
      },
      test("TypeName has no type parameters") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        assertTrue(schema.reflect.typeName.params.isEmpty)
      },
      test("Same structure produces same TypeName regardless of field declaration order") {
        // Fields declared in different order should produce same TypeName
        type PersonA = StructuralRecord { def name: String; def age: Int }
        type PersonB = StructuralRecord { def age: Int; def name: String }

        val schemaA = Schema.derived[PersonA]
        val schemaB = Schema.derived[PersonB]

        assertTrue(schemaA.reflect.typeName.name == schemaB.reflect.typeName.name)
      },
      test("TypeName with collection types shows proper format") {
        type TeamStructure = StructuralRecord { def members: List[String]; def name: String }
        val schema = Schema.derived[TeamStructure]

        val typeName = schema.reflect.typeName.name
        // Should have alphabetical order and proper collection format
        assertTrue(
          typeName.contains("members:List[String]"),
          typeName.contains("name:String"),
          typeName.startsWith("{"),
          typeName.endsWith("}")
        )
      }
    ),
    suite("Round-trip conversion")(
      test("toDynamicValue and fromDynamicValue work for simple structural type") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        // Create a StructuralRecord value
        val value: PersonStructure = new StructuralRecord(Map("name" -> "Alice", "age" -> 30))
          .asInstanceOf[PersonStructure]

        // Convert to DynamicValue
        val dv = schema.toDynamicValue(value)

        // Convert back
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Alice",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("age") == 30
        )
      },
      test("round-trip preserves primitive values") {
        type NumbersStructure = StructuralRecord { def a: Int; def b: Long; def c: Double }
        val schema = Schema.derived[NumbersStructure]

        val value: NumbersStructure = new StructuralRecord(Map("a" -> 42, "b" -> 123L, "c" -> 3.14))
          .asInstanceOf[NumbersStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("a") == 42,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("b") == 123L,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("c") == 3.14
        )
      },
      test("round-trip with enum field") {
        enum Gender { case Male, Female, Other }
        given Schema[Gender] = Schema.derived[Gender]

        type PersonStructure = StructuralRecord {
          def name: String
          def age: Int
          def gender: Gender
        }

        val schema                 = Schema.derived[PersonStructure]
        val value: PersonStructure =
          new StructuralRecord(Map("name" -> "Alice", "age" -> 30, "gender" -> Gender.Female))
            .asInstanceOf[PersonStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        val recovered = result.toOption.get.asInstanceOf[StructuralRecord]
        assertTrue(
          result.isRight,
          recovered.selectDynamic("name") == "Alice",
          recovered.selectDynamic("age") == 30,
          recovered.selectDynamic("gender") == Gender.Female
        )
      }
    ),
    suite("Structural types with collections")(
      test("derive schema for structural type with List field") {
        type TeamStructure = StructuralRecord { def name: String; def members: List[String] }
        val schema = Schema.derived[TeamStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "members"))
      },
      test("derive schema for structural type with Option field") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "nickname"))
      },
      test("derive schema for structural type with Vector field") {
        type VectorStructure = StructuralRecord { def items: Vector[Int] }
        val schema = Schema.derived[VectorStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("items"))
      },
      test("derive schema for structural type with Set field") {
        type SetStructure = StructuralRecord { def tags: Set[String] }
        val schema = Schema.derived[SetStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("tags"))
      },
      test("derive schema for structural type with Map field") {
        type MapStructure = StructuralRecord { def data: Map[String, Int] }
        val schema = Schema.derived[MapStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("data"))
      }
    ),
    suite("Collection field round-trips")(
      test("List field round-trip") {
        type TeamStructure = StructuralRecord { def name: String; def members: List[String] }
        val schema = Schema.derived[TeamStructure]

        val value: TeamStructure = new StructuralRecord(Map("name" -> "TeamA", "members" -> List("Alice", "Bob")))
          .asInstanceOf[TeamStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "TeamA",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("members") == List("Alice", "Bob")
        )
      },
      test("Option field round-trip - Some") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val value: OptionalStructure =
          new StructuralRecord(Map("name" -> "Alice", "nickname" -> Some("Ali")))
            .asInstanceOf[OptionalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Alice",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("nickname") == Some("Ali")
        )
      },
      test("Option field round-trip - None") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val value: OptionalStructure =
          new StructuralRecord(Map("name" -> "Bob", "nickname" -> None))
            .asInstanceOf[OptionalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Bob",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("nickname") == None
        )
      },
      test("Vector field round-trip") {
        type VectorStructure = StructuralRecord { def items: Vector[Int] }
        val schema = Schema.derived[VectorStructure]

        val value: VectorStructure = new StructuralRecord(Map("items" -> Vector(1, 2, 3, 4, 5)))
          .asInstanceOf[VectorStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("items") == Vector(1, 2, 3, 4, 5)
        )
      },
      test("Set field round-trip") {
        type SetStructure = StructuralRecord { def tags: Set[String] }
        val schema = Schema.derived[SetStructure]

        val value: SetStructure = new StructuralRecord(Map("tags" -> Set("a", "b", "c")))
          .asInstanceOf[SetStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("tags") == Set("a", "b", "c")
        )
      },
      test("Map field round-trip") {
        type MapStructure = StructuralRecord { def data: Map[String, Int] }
        val schema = Schema.derived[MapStructure]

        val value: MapStructure = new StructuralRecord(Map("data" -> Map("x" -> 1, "y" -> 2)))
          .asInstanceOf[MapStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("data") == Map("x" -> 1, "y" -> 2)
        )
      }
    ),
    suite("Nested structural types")(
      test("derive schema for nested structural type") {
        type AddressStructure = StructuralRecord { def city: String; def zip: Int }
        type PersonStructure  = StructuralRecord { def name: String; def address: AddressStructure }

        // Create schema for nested type - requires given for the nested type
        given Schema[AddressStructure] = Schema.derived[AddressStructure]
        val schema                     = Schema.derived[PersonStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "address"))
      },
      test("nested structural type round-trip") {
        type AddressStructure = StructuralRecord { def city: String; def zip: Int }
        type PersonStructure  = StructuralRecord { def name: String; def address: AddressStructure }

        given Schema[AddressStructure] = Schema.derived[AddressStructure]
        val schema                     = Schema.derived[PersonStructure]

        val addressValue                 = new StructuralRecord(Map("city" -> "NYC", "zip" -> 10001))
        val personValue: PersonStructure =
          new StructuralRecord(Map("name" -> "Alice", "address" -> addressValue))
            .asInstanceOf[PersonStructure]

        val dv     = schema.toDynamicValue(personValue)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val person  = result.toOption.get.asInstanceOf[StructuralRecord]
          val address = person.selectDynamic("address").asInstanceOf[StructuralRecord]
          person.selectDynamic("name") == "Alice" &&
          address.selectDynamic("city") == "NYC" &&
          address.selectDynamic("zip") == 10001
        }
      },
      test("deeply nested structural types") {
        type InnerStructure  = StructuralRecord { def value: String }
        type MiddleStructure = StructuralRecord { def inner: InnerStructure }
        type OuterStructure  = StructuralRecord { def middle: MiddleStructure }

        given Schema[InnerStructure]  = Schema.derived[InnerStructure]
        given Schema[MiddleStructure] = Schema.derived[MiddleStructure]
        val schema                    = Schema.derived[OuterStructure]

        val innerValue                 = new StructuralRecord(Map("value" -> "deep"))
        val middleValue                = new StructuralRecord(Map("inner" -> innerValue))
        val outerValue: OuterStructure =
          new StructuralRecord(Map("middle" -> middleValue))
            .asInstanceOf[OuterStructure]

        val dv     = schema.toDynamicValue(outerValue)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val outer  = result.toOption.get.asInstanceOf[StructuralRecord]
          val middle = outer.selectDynamic("middle").asInstanceOf[StructuralRecord]
          val inner  = middle.selectDynamic("inner").asInstanceOf[StructuralRecord]
          inner.selectDynamic("value") == "deep"
        }
      }
    ),
    suite("Structural types with sum types")(
      test("derive schema for structural type with Either field") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("result"))
      },
      // TODO STRUCT: disable until Either round-trip is fixed
      test("Either field round-trip - Left") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val eitherValue                = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
        val value: WithEitherStructure =
          new StructuralRecord(Map("result" -> eitherValue))
            .asInstanceOf[WithEitherStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[StructuralRecord]
          val eitherField = record.selectDynamic("result").asInstanceOf[StructuralRecord]
          eitherField.selectDynamic("Tag") == "Left" &&
          eitherField.selectDynamic("value") == "error"
        }
      },
      // TODO STRUCT: disable until Either round-trip is fixed
      test("Either field round-trip - Right") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val eitherValue                = new StructuralRecord(Map("Tag" -> "Right", "value" -> 42))
        val value: WithEitherStructure =
          new StructuralRecord(Map("result" -> eitherValue))
            .asInstanceOf[WithEitherStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[StructuralRecord]
          val eitherField = record.selectDynamic("result").asInstanceOf[StructuralRecord]
          eitherField.selectDynamic("Tag") == "Right" &&
          eitherField.selectDynamic("value") == 42
        }
      }
    ),
    suite("Structural types with tuples")(
      test("derive schema for structural type with tuple field") {
        type WithTupleStructure = StructuralRecord { def pair: (String, Int) }
        val schema = Schema.derived[WithTupleStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("pair"))
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("tuple field round-trip") {
        type WithTupleStructure = StructuralRecord { def pair: (String, Int) }
        val schema = Schema.derived[WithTupleStructure]

        val tupleValue                = new StructuralRecord(Map("_1" -> "hello", "_2" -> 42))
        val value: WithTupleStructure =
          new StructuralRecord(Map("pair" -> tupleValue))
            .asInstanceOf[WithTupleStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record     = result.toOption.get.asInstanceOf[StructuralRecord]
          val tupleField = record.selectDynamic("pair").asInstanceOf[StructuralRecord]
          tupleField.selectDynamic("_1") == "hello" &&
          tupleField.selectDynamic("_2") == 42
        }
      }
    ),
    suite("Extended primitives in structural types")(
      test("derive schema for structural type with BigDecimal field") {
        type WithBigDecimalStructure = StructuralRecord { def amount: BigDecimal }
        val schema = Schema.derived[WithBigDecimalStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("amount"))
      },
      test("BigDecimal field round-trip") {
        type WithBigDecimalStructure = StructuralRecord { def amount: BigDecimal }
        val schema = Schema.derived[WithBigDecimalStructure]

        val value: WithBigDecimalStructure =
          new StructuralRecord(Map("amount" -> BigDecimal("123456.789")))
            .asInstanceOf[WithBigDecimalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("amount") == BigDecimal("123456.789")
        )
      },
      test("derive schema for structural type with UUID field") {
        import java.util.UUID
        type WithUUIDStructure = StructuralRecord { def id: UUID }
        val schema = Schema.derived[WithUUIDStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("id"))
      },
      test("UUID field round-trip") {
        import java.util.UUID
        type WithUUIDStructure = StructuralRecord { def id: UUID }
        val schema = Schema.derived[WithUUIDStructure]

        val uuid                     = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val value: WithUUIDStructure =
          new StructuralRecord(Map("id" -> uuid))
            .asInstanceOf[WithUUIDStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("id") == uuid
        )
      }
    )
  )
}
