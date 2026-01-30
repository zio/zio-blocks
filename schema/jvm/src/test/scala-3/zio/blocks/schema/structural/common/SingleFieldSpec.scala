package zio.blocks.schema.structural.common
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for single-field product types to structural conversion. */
object SingleFieldSpec extends SchemaBaseSpec {

  case class Id(value: String)
  case class Count(n: Int)

  def spec: Spec[Any, Nothing] = suite("SingleFieldSpec")(
    test("single field structural has one field") {
      val schema     = Schema.derived[Id]
      val structural = schema.structural
      val numFields  = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.size
      }
      assertTrue(numFields == 1)
    },
    test("single field case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Id(value: String)
        val schema: Schema[Id] = Schema.derived[Id]
        val structural: Schema[{def value: String}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("single Int field case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Count(n: Int)
        val schema: Schema[Count] = Schema.derived[Count]
        val structural: Schema[{def n: Int}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
