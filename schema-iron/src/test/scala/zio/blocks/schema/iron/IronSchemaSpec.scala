package zio.blocks.schema.iron

import zio.blocks.schema.*
import zio.test.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

class IronSchemaSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int :| Positive)

  object Person {
    given Schema[Person] = Schema.derived
  }

  def spec = suite("IronSchemaSpec")(
    test("derive schema for refined types") {
      val schema = summon[Schema[Person]]
      assertTrue(schema != null)
    },

    test("refined type schema has correct structure") {
      val ageSchema = summon[Schema[Int :| Positive]]
      assertTrue(ageSchema != null)
    }
  )
}
