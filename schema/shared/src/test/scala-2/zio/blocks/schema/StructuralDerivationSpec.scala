package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.binding.StructuralValue

object StructuralDerivationSpec extends ZIOSpecDefault {

  // Structural type definitions
  type SimpleStructural = { val name: String; val age: Int }
  type SingleField      = { val value: Int }
  type MultipleStrings  = { val first: String; val second: String }
  type WithBoolean      = { val flag: Boolean; val count: Int }
  type WithLong         = { val id: Long; val name: String }
  type AllPrimitives    = {
    val b: Boolean
    val by: Byte
    val s: Short
    val i: Int
    val l: Long
    val f: Float
    val d: Double
    val c: Char
  }

  // Helper to create structural values
  def makeStructural(values: (String, Any)*): StructuralValue =
    new StructuralValue(values.toMap)

  def spec: Spec[TestEnvironment, Any] = suite("StructuralDerivationSpec")(
    suite("Schema.derived for structural types")(
      test("derives schema for simple structural type (compilation check)") {
        // This test primarily checks that the macro expands successfully
        val schema = Schema.derived[SimpleStructural]
        assert(schema)(anything)
      }
    ),
    suite("Runtime behavior (Roundtrip via DynamicValue)")(
      test("SimpleStructural roundtrip") {
        val schema   = Schema.derived[SimpleStructural]
        val original = makeStructural("name" -> "Alice", "age" -> 30)

        // Convert to DynamicValue
        val dv = schema.toDynamicValue(original.asInstanceOf[SimpleStructural])

        // Convert back
        val result = schema.fromDynamicValue(dv)

        val value = result.toOption.get.asInstanceOf[StructuralValue]
        assertTrue(value == original)
      },
      test("AllPrimitives roundtrip") {
        val schema   = Schema.derived[AllPrimitives]
        val original = makeStructural(
          "b"  -> true,
          "by" -> 1.toByte,
          "s"  -> 2.toShort,
          "i"  -> 3,
          "l"  -> 4L,
          "f"  -> 5.5f,
          "d"  -> 6.6,
          "c"  -> 'x'
        )

        val dv     = schema.toDynamicValue(original.asInstanceOf[AllPrimitives])
        val result = schema.fromDynamicValue(dv)

        val value = result.toOption.get.asInstanceOf[StructuralValue]
        assertTrue(value == original)
      },
      test("Nested structural types roundtrip") {
        type Point = { val x: Int; val y: Int }
        type Shape = { val name: String; val center: Point }

        val schema = Schema.derived[Shape]

        val center   = makeStructural("x" -> 10, "y" -> 20)
        val original = makeStructural("name" -> "Circle", "center" -> center)

        val dv     = schema.toDynamicValue(original.asInstanceOf[Shape])
        val result = schema.fromDynamicValue(dv)

        // StructuralValue.equals checks underlying Map equality, which handles nested StructuralValues correctly
        val value = result.toOption.get.asInstanceOf[StructuralValue]
        assertTrue(value == original)
      }
    ),
    suite("TypeName Normalization")(
      test("Fields are sorted in TypeName") {
        val schema = Schema.derived[SimpleStructural]
        // implementation details: we expect "{age: Int, name: String}"
        // because "age" < "name"
        assertTrue(schema.reflect.typeName.name == "{age: Int, name: String}")
      },
      test("Different declaration order yields same TypeName") {
        type A = { val x: Int; val y: Int }
        type B = { val y: Int; val x: Int }

        val schemaA = Schema.derived[A]
        val schemaB = Schema.derived[B]

        assertTrue(schemaA.reflect.typeName.name == schemaB.reflect.typeName.name)
      }
    )
  )
}
