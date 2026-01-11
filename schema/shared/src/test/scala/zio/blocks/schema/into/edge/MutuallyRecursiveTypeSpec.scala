package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for mutually recursive type conversions. */
object MutuallyRecursiveTypeSpec extends ZIOSpecDefault {

  case class PersonA(name: String, employer: Option[CompanyA])
  case class CompanyA(name: String, employees: List[PersonA])

  case class PersonB(name: String, employer: Option[CompanyB])
  case class CompanyB(name: String, employees: List[PersonB])

  def spec: Spec[TestEnvironment, Any] = suite("MutuallyRecursiveTypeSpec")(
    test("converts person with no employer") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

      val result = personInto.into(PersonA("Alice", None))
      assert(result)(isRight(equalTo(PersonB("Alice", None))))
    },
    test("converts company with employees") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

      val employees = List(PersonA("Alice", None), PersonA("Bob", None))
      val result    = companyInto.into(CompanyA("TechCorp", employees))
      assert(result)(isRight(equalTo(CompanyB("TechCorp", List(PersonB("Alice", None), PersonB("Bob", None))))))
    },
    test("converts one level of mutual reference") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

      val company = CompanyA("StartupInc", List(PersonA("Alice", None)))
      val result  = personInto.into(PersonA("Bob", Some(company)))
      assert(result)(isRight(equalTo(PersonB("Bob", Some(CompanyB("StartupInc", List(PersonB("Alice", None))))))))
    }
  )
}
