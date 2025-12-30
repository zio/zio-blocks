package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for nested product types to structural conversion.
 */
object NestedProductSpec extends ZIOSpecDefault {

  case class Address(street: String, city: String, zip: Int)
  case class Person(name: String, age: Int, address: Address)

  def spec = suite("NestedProductSpec")(
    test("nested case classes convert to nested structural") {
      val nominalSchema = Schema.derived[Person]
      val structuralSchema = nominalSchema.structural

      // Type name should contain nested structure
      val typeName = structuralSchema.reflect.typeName.name
      assertTrue(
        typeName.contains("name"),
        typeName.contains("age"),
        typeName.contains("address")
      )
    } @@ TestAspect.ignore,
    test("deeply nested structures convert correctly") {
      case class Inner(value: Int)
      case class Middle(inner: Inner)
      case class Outer(middle: Middle)

      val schema = Schema.derived[Outer]
      val structural = schema.structural

      assertTrue(structural.reflect.typeName.name.nonEmpty)
    } @@ TestAspect.ignore,
    test("nested structural preserves field hierarchy") {
      val schema = Schema.derived[Person]
      val structural = schema.structural

      val fieldNames = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
        case _                            => Set.empty[String]
      }

      assertTrue(
        fieldNames.contains("name"),
        fieldNames.contains("age"),
        fieldNames.contains("address")
      )
    } @@ TestAspect.ignore
  )
}

