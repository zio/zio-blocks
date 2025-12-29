package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for direct structural type derivation in Scala 2. This tests
 * Schema.derived[StructuralRecord { def name: String; def age: Int }] and
 * similar refinement types.
 */
object DirectStructuralDerivationSpec extends ZIOSpecDefault {

  // Type aliases for structural types
  type PersonStructure   = StructuralRecord { def name: String; def age: Int }
  type IdStructure       = StructuralRecord { def value: String }
  type MixedStructure    = StructuralRecord { def name: String; def age: Int; def score: Double; def active: Boolean }
  type PointStructure    = StructuralRecord { def x: Int; def y: Int }
  type NumbersStructure  = StructuralRecord { def a: Int; def b: Long; def c: Double }
  type TeamStructure     = StructuralRecord { def name: String; def members: List[String] }
  type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }

  override def spec = suite("DirectStructuralDerivationSpec (Scala 2)")(
    suite("Simple structural types")(
      test("derive schema for simple two-field structural type") {
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
        val schema = Schema.derived[IdStructure]

        val record    = schema.reflect.asRecord.get
        val fieldName = record.fields.head.name
        assertTrue(record.fields.size == 1, fieldName == "value")
      },
      test("derive schema for multi-field structural type with different primitives") {
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
        val schema = Schema.derived[PersonStructure]

        val typeName = schema.reflect.typeName.name
        // Should be {age:Int,name:String} - alphabetically sorted
        assertTrue(typeName == "{age:Int,name:String}")
      },
      test("TypeName format is {field:Type,...}") {
        val schema = Schema.derived[PointStructure]

        // Check the .name property of TypeName, not the full toString
        val typeNameStr = schema.reflect.typeName.name
        assertTrue(typeNameStr == "{x:Int,y:Int}")
      },
      test("TypeName uses Namespace.empty") {
        val schema = Schema.derived[PersonStructure]

        assertTrue(schema.reflect.typeName.namespace == Namespace.empty)
      },
      test("TypeName has no type parameters") {
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
      }
    ),
    suite("Structural types with collections")(
      test("derive schema for structural type with List field") {
        val schema = Schema.derived[TeamStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "members"))
      },
      test("derive schema for structural type with Option field") {
        val schema = Schema.derived[OptionalStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "nickname"))
      }
    )
  )
}
