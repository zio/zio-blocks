package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for simple product type (case class) to structural conversion.
 */
object SimpleProductSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)
  case class Single(value: String)

  type PersonLike  = { def name: String; def age: Int }
  type PointLike   = { def x: Int; def y: Int }
  type SingleField = { def value: String }

  def spec = suite("SimpleProductSpec")(
    suite("Direct Structural Type Derivation")(
      test("derives schema for structural type with two fields") {
        val schema = Schema.derived[PersonLike]

        val typeName = schema.reflect.typeName.toString
        assertTrue(
          typeName.contains("age"),
          typeName.contains("name"),
          typeName.contains("Int"),
          typeName.contains("String")
        )
      },
      test("derives schema for structural type with Int fields") {
        val schema = Schema.derived[PointLike]

        val typeName = schema.reflect.typeName.toString
        assertTrue(
          typeName.contains("x"),
          typeName.contains("y"),
          typeName.contains("Int")
        )
      },
      test("derives schema for single field structural type") {
        val schema = Schema.derived[SingleField]

        val typeName = schema.reflect.typeName.toString
        assertTrue(
          typeName.contains("value"),
          typeName.contains("String")
        )
      }
    ),
    suite("Nominal to Structural Conversion")(
      test("case class schema can be converted to structural") {
        val nominalSchema: Schema[Person] = Schema.derived[Person]
        val structuralSchema              = nominalSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(typeName == "{age:Int,name:String}")
      },
      test("structural schema has correct field count") {
        val nominalSchema    = Schema.derived[Person]
        val structuralSchema = nominalSchema.structural

        val numFields = structuralSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 2)
      },
      test("structural schema has correct field names") {
        val nominalSchema    = Schema.derived[Person]
        val structuralSchema = nominalSchema.structural

        val fieldNames = structuralSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age")
        )
      },
      test("Point case class converts to structural") {
        val nominalSchema    = Schema.derived[Point]
        val structuralSchema = nominalSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(typeName == "{x:Int,y:Int}")
      },
      test("single field case class converts to structural") {
        val nominalSchema    = Schema.derived[Single]
        val structuralSchema = nominalSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(typeName == "{value:String}")
      }
    ),
    suite("Structural Schema Fields")(
      test("schema has correct number of fields") {
        val schema = Schema.derived[PersonLike]

        val numFields = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 2)
      },
      test("schema fields have correct names") {
        val schema = Schema.derived[PersonLike]

        val fieldNames = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age")
        )
      }
    ),
    suite("Round-Trip")(
      test("case class can be converted through structural schema") {
        val person = Person("Alice", 30)

        val structural: PersonLike = person.asInstanceOf[PersonLike]

        val schema = Schema.derived[PersonLike]

        val dynamic = schema.toDynamicValue(structural)

        val result = schema.fromDynamicValue(dynamic)

        assertTrue(result.isRight)
      },
      test("structural schema preserves field information") {
        val nominalSchema    = Schema.derived[Person]
        val structuralSchema = nominalSchema.structural

        val fieldNames = structuralSchema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }

        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age"),
          fieldNames.size == 2
        )
      }
    )
  )
}
