package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for simple product type to structural conversion. */
object SimpleProductSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("SimpleProductSpec")(
    test("case class converts to structural with correct type name") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName == "{age:Int,name:String}")
    },
    test("structural schema has correct field names") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
      }
      assertTrue(fieldNames == Set("name", "age"))
    },
    test("direct structural type derivation works") {
      val schema   = Schema.derived[PersonLike]
      val typeName = schema.reflect.typeName.name
      assertTrue(typeName == "{age:Int,name:String}")
    }
  )
}
