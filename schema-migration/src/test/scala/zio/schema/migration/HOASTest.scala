package zio.schema.migration

import zio._
import zio.test._
import zio.schema._

/**
 * Test HOAS (Higher-Order Abstract Syntax) approach for field path extraction.
 */
object HOASTest extends ZIOSpecDefault {

  case class TestPerson(name: String, age: Int)
  given Schema[TestPerson] = DeriveSchema.gen[TestPerson]

  def spec = suite("HOASTest")(
    test("HOAS should extract simple field name") {
      val fieldPath = HOASPathMacros.extractPathHOAS[TestPerson](_.name)

      assertTrue(
        fieldPath.serialize == "name"
      )
    },
    test("HOAS should extract with explicit lambda") {
      val fieldPath = HOASPathMacros.extractPathHOAS[TestPerson](p => p.age)

      assertTrue(
        fieldPath.serialize == "age"
      )
    }
  )
}
