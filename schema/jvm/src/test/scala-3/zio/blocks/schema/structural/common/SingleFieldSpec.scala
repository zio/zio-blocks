package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for single-field product types to structural conversion. */
object SingleFieldSpec extends ZIOSpecDefault {

  case class Id(value: String)
  case class Count(n: Int)

  def spec: Spec[Any, Nothing] = suite("SingleFieldSpec")(
    test("single String field converts correctly") {
      val schema     = Schema.derived[Id]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{value:String}")
    },
    test("single Int field converts correctly") {
      val schema     = Schema.derived[Count]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{n:Int}")
    },
    test("single field structural has one field") {
      val schema     = Schema.derived[Id]
      val structural = schema.structural
      val numFields  = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.size
      }
      assertTrue(numFields == 1)
    }
  )
}
