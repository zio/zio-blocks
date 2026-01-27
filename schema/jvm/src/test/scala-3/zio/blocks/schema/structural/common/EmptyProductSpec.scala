package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for empty product types to structural conversion. */
object EmptyProductSpec extends ZIOSpecDefault {

  case class Empty()
  case object Singleton

  def spec: Spec[Any, Nothing] = suite("EmptyProductSpec")(
    test("empty case class converts to structural") {
      val schema     = Schema.derived[Empty]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{}")
    },
    test("case object converts to structural") {
      val schema     = Schema.derived[Singleton.type]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{}")
    },
    test("empty structural has zero fields") {
      val schema     = Schema.derived[Empty]
      val structural = schema.structural
      val numFields  = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.size
        case _                            => -1
      }
      assertTrue(numFields == 0)
    },
    test("empty case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Empty()
        val schema = Schema.derived[Empty]
        val structural: Schema[{}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
