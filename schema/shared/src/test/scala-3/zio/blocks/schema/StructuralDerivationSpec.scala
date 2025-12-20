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
      test("derives schema for simple structural type") {
        val schema = Schema.derived[SimpleStructural]
        assert(schema)(anything)
      },
      test("structural schema has correct field count") {
        val schema     = Schema.derived[SimpleStructural]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(fieldCount == 2)
      },
      test("structural schema has correct field names") {
        val schema     = Schema.derived[SimpleStructural]
        val fieldNames = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(fieldNames == Set("name", "age"))
      },
      test("structural schema has normalized TypeName") {
        val schema = Schema.derived[SimpleStructural]
        // TypeName should be normalized with fields sorted alphabetically
        assertTrue(schema.reflect.typeName.name == "{age:Int,name:String}")
      },
      test("derives schema for single field structural type") {
        val schema     = Schema.derived[SingleField]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        val firstName = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.head.name
          case _                            => ""
        }
        assertTrue(fieldCount == 1) &&
        assertTrue(firstName == "value")
      },
      test("derives schema for structural type with boolean") {
        val schema     = Schema.derived[WithBoolean]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(fieldCount == 2)
      },
      test("derives schema for structural type with long") {
        val schema     = Schema.derived[WithLong]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(fieldCount == 2)
      },
      test("derives schema for all primitive types") {
        val schema     = Schema.derived[AllPrimitives]
        val fieldCount = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(fieldCount == 8)
      },
      test("derives schema for nested structural types") {
        type Point     = { val x: Int; val y: Int }
        type WithPoint = { val label: String; val point: Point }

        val pointSchema     = Schema.derived[Point]
        val withPointSchema = Schema.derived[WithPoint]

        val pointFieldCount = pointSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        val withPointFieldCount = withPointSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        val withPointFieldNames = withPointSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }

        assertTrue(pointFieldCount == 2) &&
        assertTrue(withPointFieldCount == 2) &&
        assertTrue(withPointFieldNames == Set("label", "point"))
      }
    ),
    suite("Structural type roundtrip")(
      test("construct and deconstruct simple structural type") {
        val schema = Schema.derived[SimpleStructural]
        schema.reflect match {
          case record: Reflect.Record[zio.blocks.schema.binding.Binding, SimpleStructural] @unchecked =>
            import zio.blocks.schema.binding._
            val binding     = record.recordBinding.asInstanceOf[Binding.Record[SimpleStructural]]
            val constructor = binding.constructor

            // Create registers and populate with test data
            val registers  = Registers(constructor.usedRegisters)
            val baseOffset = RegisterOffset.Zero

            // Set values: name (object) and age (int)
            registers.setObject(baseOffset, 0, "Alice")
            registers.setInt(baseOffset, 0, 30)

            // Construct the value
            val constructed = constructor.construct(registers, baseOffset)

            // Access via selectDynamic
            val sv = constructed.asInstanceOf[StructuralValue]
            assertTrue(sv.selectDynamic("name") == "Alice") &&
            assertTrue(sv.selectDynamic("age") == 30)
          case _ =>
            assertTrue(false)
        }
      },
      test("deconstruct structural value") {
        val schema = Schema.derived[SimpleStructural]
        schema.reflect match {
          case record: Reflect.Record[zio.blocks.schema.binding.Binding, SimpleStructural] @unchecked =>
            import zio.blocks.schema.binding._
            val binding       = record.recordBinding.asInstanceOf[Binding.Record[SimpleStructural]]
            val deconstructor = binding.deconstructor

            // Create a structural value
            val sv = makeStructural("name" -> "Bob", "age" -> 25).asInstanceOf[SimpleStructural]

            // Deconstruct it
            val registers  = Registers(deconstructor.usedRegisters)
            val baseOffset = RegisterOffset.Zero
            deconstructor.deconstruct(registers, baseOffset, sv)

            // Check we can read the values back
            assertTrue(registers.getObject(baseOffset, 0) == "Bob") &&
            assertTrue(registers.getInt(baseOffset, 0) == 25)
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("DynamicValue conversion")(
      test("structural type to DynamicValue and back") {
        val schema = Schema.derived[SimpleStructural]

        // Create a structural value
        val original = makeStructural("name" -> "Charlie", "age" -> 35).asInstanceOf[SimpleStructural]

        // Convert to DynamicValue
        val dynamicValue = schema.toDynamicValue(original)

        // Convert back
        val result = schema.fromDynamicValue(dynamicValue)

        result match {
          case Right(value) =>
            val sv = value.asInstanceOf[StructuralValue]
            assertTrue(sv.selectDynamic("name") == "Charlie") &&
            assertTrue(sv.selectDynamic("age") == 35)
          case Left(_) =>
            assertTrue(false)
        }
      },
      test("structural type with all primitives DynamicValue roundtrip") {
        val schema = Schema.derived[WithBoolean]

        val original     = makeStructural("flag" -> true, "count" -> 42).asInstanceOf[WithBoolean]
        val dynamicValue = schema.toDynamicValue(original)
        val result       = schema.fromDynamicValue(dynamicValue)

        result match {
          case Right(value) =>
            val sv = value.asInstanceOf[StructuralValue]
            assertTrue(sv.selectDynamic("flag") == true) &&
            assertTrue(sv.selectDynamic("count") == 42)
          case Left(_) =>
            assertTrue(false)
        }
      },
      test("structural type with long DynamicValue roundtrip") {
        val schema = Schema.derived[WithLong]

        val original     = makeStructural("id" -> 123456789L, "name" -> "test").asInstanceOf[WithLong]
        val dynamicValue = schema.toDynamicValue(original)
        val result       = schema.fromDynamicValue(dynamicValue)

        result match {
          case Right(value) =>
            val sv = value.asInstanceOf[StructuralValue]
            assertTrue(sv.selectDynamic("id") == 123456789L) &&
            assertTrue(sv.selectDynamic("name") == "test")
          case Left(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("Same structure different order")(
      test("type aliases with different field order have same normalized TypeName") {
        type Order1 = { val name: String; val age: Int }
        type Order2 = { val age: Int; val name: String }

        val schema1 = Schema.derived[Order1]
        val schema2 = Schema.derived[Order2]

        // Both should have the same normalized TypeName
        assertTrue(schema1.reflect.typeName.name == schema2.reflect.typeName.name)
      }
    )
  )
}
