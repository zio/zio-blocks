package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for single-field product types to structural conversion.
 */
object SingleFieldSpec extends ZIOSpecDefault {

  case class Id(value: String)
  case class Count(n: Int)
  case class Flag(enabled: Boolean)

  type SingleString = { def value: String }

  def spec = suite("SingleFieldSpec")(
    test("single String field converts correctly") {
      val schema = Schema.derived[Id]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName == "{value:String}")
    },
    test("single Int field converts correctly") {
      val schema = Schema.derived[Count]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName == "{n:Int}")
    },
    test("single Boolean field converts correctly") {
      val schema = Schema.derived[Flag]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName == "{enabled:Boolean}")
    },
    test("direct single field structural derivation") {
      val schema = Schema.derived[SingleString]

      val typeName = schema.reflect.typeName.name
      assertTrue(typeName == "{value:String}")
    },
    test("single field structural has one field") {
      val schema = Schema.derived[Id]
      val structural = schema.structural

      val numFields = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.size
        case _                            => -1
      }
      assertTrue(numFields == 1)
    }
  )
}

