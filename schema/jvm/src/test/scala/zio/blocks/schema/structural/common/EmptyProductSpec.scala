package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for empty product types (case classes with no fields) and case objects.
 */
object EmptyProductSpec extends ZIOSpecDefault {

  case class Empty()
  case object Singleton

  // Empty structural type alias
  type EmptyStructural = {}

  def spec = suite("EmptyProductSpec")(
    suite("Empty Case Class")(
      test("empty case class converts to structural") {
        val schema = Schema.derived[Empty]
        val structural = schema.structural

        // Empty case class becomes empty structural type with type name "{}"
        val typeName = structural.reflect.typeName.name
        assertTrue(typeName == "{}")
      },
      test("empty structural has zero fields") {
        val schema = Schema.derived[Empty]
        val structural: Schema[{}] = schema.structural

        val numFields = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 0)
      },
      test("empty case class round-trip works") {
        val schema = Schema.derived[Empty]
        val structural = schema.structural

        val original = Empty()
        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(original)
        val result = structural.fromDynamicValue(dynamic)

        assertTrue(result.isRight)
      }
    ),
    suite("Case Object")(
      test("case object converts to structural") {
        val schema = Schema.derived[Singleton.type]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName == "{}")
      },
      test("case object structural has zero fields") {
        val schema = Schema.derived[Singleton.type]
        val structural = schema.structural

        val numFields = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 0)
      }
    ),
    suite("Direct Empty Structural Type Derivation")(
      test("Schema.derived for empty structural type {}") {
        val schema = Schema.derived[EmptyStructural]

        val typeName = schema.reflect.typeName.name
        assertTrue(typeName == "{}")
      },
      test("empty structural type has zero fields") {
        val schema = Schema.derived[EmptyStructural]

        val numFields = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }
        assertTrue(numFields == 0)
      },
      test("empty structural type round-trip works") {
        val schema = Schema.derived[EmptyStructural]

        // Create an empty selectable value
        val emptyValue: EmptyStructural = new scala.Selectable {
          def selectDynamic(name: String): Any =
            throw new NoSuchMethodException(s"No field '$name' in empty structural type")
        }.asInstanceOf[EmptyStructural]

        val dynamic = schema.toDynamicValue(emptyValue)
        val result = schema.fromDynamicValue(dynamic)

        assertTrue(result.isRight)
      }
    )
  )
}

