package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object ReproductionSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class Company(name: String, ceo: Person)

  def spec: Spec[TestEnvironment, Any] = suite("ReproductionSpec")(
    test("Check if nested conversion is deep or shallow") {
      val ceo     = Person("Boss", 50)
      val company = Company("TechCorp", ceo)
      val ts      = ToStructural.derived[Company]

      val sValue = ts.toStructural(company)
      val sv     = sValue.asInstanceOf[StructuralValue]

      val ceoField = sv.selectDynamic("ceo")

      // Check if ceoField is a StructuralValue or a Person
      val isDeep    = ceoField.isInstanceOf[StructuralValue]
      val isShallow = ceoField.isInstanceOf[Person]

      assertTrue(isDeep) &&
      assertTrue(!isShallow) &&
      assertTrue(ceoField.asInstanceOf[StructuralValue].selectDynamic("name") == "Boss")
    }
  )
}
