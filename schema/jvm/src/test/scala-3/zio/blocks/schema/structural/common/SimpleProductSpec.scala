package zio.blocks.schema.structural.common
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for simple product type to structural conversion. */
object SimpleProductSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("SimpleProductSpec")(
    test("structural schema has correct field names") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
      }
      assertTrue(fieldNames == Set("name", "age"))
    },
    test("case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Person(name: String, age: Int)
        val schema: Schema[Person] = Schema.derived[Person]
        val structural: Schema[{def age: Int; def name: String}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
