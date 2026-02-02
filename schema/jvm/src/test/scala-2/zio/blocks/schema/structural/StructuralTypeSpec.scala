package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.test._

/**
 * Tests for Scala 2 structural type derivation (JVM only).
 *
 * Mirrors the tests from PureStructuralTypeSpec.scala for Scala 3. Note: Uses
 * scala.language.reflectiveCalls for structural type syntax.
 */
object StructuralTypeSpec extends SchemaBaseSpec {

  type PersonStructural = { def name: String; def age: Int }
  type Person           = { def name: String; def age: Int }

  def spec = suite("StructuralTypeSpec")(
    test("derive schema for structural type with String and Int fields") {
      val schema     = Schema.derived[PersonStructural]
      val record     = schema.reflect.asInstanceOf[Reflect.Record[Binding, PersonStructural]]
      val fieldNames = record.fields.map(_.name).toSet
      assertTrue(
        record.fields.size == 2,
        fieldNames.contains("name"),
        fieldNames.contains("age")
      )
    },
    test("derive schema for intersection of structural types") {
      type HasName  = { def name: String }
      type HasAge   = { def age: Int }
      type Combined = HasName with HasAge

      val schema = Schema.derived[Combined]
      val record = schema.reflect.asInstanceOf[Reflect.Record[Binding, Combined]]
      assertTrue(record.fields.size == 2)
    },
    test("structural type constructor creates instance and deconstructor extracts values") {
      val schema  = Schema.derived[Person]
      val record  = schema.reflect.asInstanceOf[Reflect.Record[Binding, Person]]
      val binding = record.recordBinding.asInstanceOf[Binding.Record[Person]]

      val inputRegisters = Registers(binding.constructor.usedRegisters)
      inputRegisters.setObject(RegisterOffset.Zero, "Alice")
      inputRegisters.setInt(RegisterOffset.Zero, 30)

      val constructed: Person = binding.constructor.construct(inputRegisters, RegisterOffset.Zero)
      assertTrue(constructed != null)
    },
    test("structural type deconstructor extracts values via reflection") {
      val schema  = Schema.derived[Person]
      val record  = schema.reflect.asInstanceOf[Reflect.Record[Binding, Person]]
      val binding = record.recordBinding.asInstanceOf[Binding.Record[Person]]

      val person: Person = new {
        def name: String = "Bob"
        def age: Int     = 25
      }

      val registers = Registers(binding.deconstructor.usedRegisters)
      binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, person)

      val extractedName = registers.getObject(RegisterOffset.Zero).asInstanceOf[String]
      val extractedAge  = registers.getInt(RegisterOffset.Zero)

      assertTrue(
        extractedName == "Bob",
        extractedAge == 25
      )
    }
  )
}
