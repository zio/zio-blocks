package zio.schema.migration

import zio._
import zio.test._
import zio.schema._

/**
 * Test to verify if macro-based field selectors work
 */
object MacroTest extends ZIOSpecDefault {

  case class TestPerson(name: String, age: Int)
  given Schema[TestPerson] = DeriveSchema.gen[TestPerson]

  def spec = suite("MacroTest")(
    test("PathMacros should extract simple field name") {
      // This will test if the macro can handle the lambda
      val fieldPath = PathMacros.extractPath[TestPerson](_.name)

      assertTrue(
        fieldPath.serialize == "name"
      )
    },
    test("MacroSelectors should extract field name") {
      val fieldName = MacroSelectors.fieldName((p: TestPerson) => p.age)

      assertTrue(
        fieldName == "age"
      )
    }
  )
}
