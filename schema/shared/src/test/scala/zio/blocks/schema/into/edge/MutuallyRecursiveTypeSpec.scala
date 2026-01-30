package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for mutually recursive type conversions.
 *
 * Covers:
 *   - Two types that reference each other (A contains B, B contains A)
 *   - Type coercion within mutually recursive structures
 *   - Multiple levels of mutual recursion
 */
object MutuallyRecursiveTypeSpec extends ZIOSpecDefault {

  // === Mutually recursive types: Person and Company ===
  case class PersonA(name: String, employer: Option[CompanyA])
  case class CompanyA(name: String, employees: List[PersonA])

  case class PersonB(name: String, employer: Option[CompanyB])
  case class CompanyB(name: String, employees: List[PersonB])

  // === Mutually recursive with type coercion ===
  case class PersonWithAge(name: String, age: Int, employer: Option[CompanyWithRevenue])
  case class CompanyWithRevenue(name: String, revenue: Long, employees: List[PersonWithAge])

  case class PersonWithAgeAlt(name: String, age: Long, employer: Option[CompanyWithRevenueAlt])
  case class CompanyWithRevenueAlt(name: String, revenue: Long, employees: List[PersonWithAgeAlt])

  // === Three-way mutual recursion ===
  case class DeptA(name: String, manager: ManagerA, projects: List[ProjectA])
  case class ManagerA(name: String, department: Option[DeptA])
  case class ProjectA(name: String, department: DeptA, lead: ManagerA)

  case class DeptB(name: String, manager: ManagerB, projects: List[ProjectB])
  case class ManagerB(name: String, department: Option[DeptB])
  case class ProjectB(name: String, department: DeptB, lead: ManagerB)

  def spec: Spec[TestEnvironment, Any] = suite("MutuallyRecursiveTypeSpec")(
    suite("Two-Way Mutual Recursion")(
      test("converts person with no employer") {
        implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
        implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

        val source = PersonA("Alice", None)
        val result = personInto.into(source)

        assert(result)(isRight(equalTo(PersonB("Alice", None))))
      },
      test("converts company with no employees") {
        implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
        implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

        val source = CompanyA("TechCorp", Nil)
        val result = companyInto.into(source)

        assert(result)(isRight(equalTo(CompanyB("TechCorp", Nil))))
      },
      test("converts person with employer (no circular reference)") {
        implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
        implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

        val company = CompanyA("StartupInc", Nil)
        val source  = PersonA("Bob", Some(company))
        val result  = personInto.into(source)

        assert(result)(isRight(equalTo(PersonB("Bob", Some(CompanyB("StartupInc", Nil))))))
      },
      test("converts company with employees (no circular reference)") {
        implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
        implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

        val employees = List(PersonA("Alice", None), PersonA("Bob", None))
        val source    = CompanyA("BigCorp", employees)
        val result    = companyInto.into(source)

        assert(result)(isRight(equalTo(CompanyB("BigCorp", List(PersonB("Alice", None), PersonB("Bob", None))))))
      },
      test("converts one level of mutual reference") {
        implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
        implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]

        // Company references Person, but those Persons don't reference back
        val alice   = PersonA("Alice", None)
        val bob     = PersonA("Bob", None)
        val company = CompanyA("Corp", List(alice, bob))
        val charlie = PersonA("Charlie", Some(company))

        val result = personInto.into(charlie)

        assert(result)(
          isRight(
            equalTo(
              PersonB("Charlie", Some(CompanyB("Corp", List(PersonB("Alice", None), PersonB("Bob", None)))))
            )
          )
        )
      }
    ),
    suite("Mutual Recursion with Type Coercion")(
      test("converts with coercion in mutually recursive types") {
        implicit lazy val personInto: Into[PersonWithAge, PersonWithAgeAlt] =
          Into.derived[PersonWithAge, PersonWithAgeAlt]
        implicit lazy val companyInto: Into[CompanyWithRevenue, CompanyWithRevenueAlt] =
          Into.derived[CompanyWithRevenue, CompanyWithRevenueAlt]

        val alice   = PersonWithAge("Alice", 30, None)
        val company = CompanyWithRevenue("TechCorp", 1000000L, List(alice))
        val bob     = PersonWithAge("Bob", 25, Some(company))

        val result = personInto.into(bob)

        assert(result)(
          isRight(
            equalTo(
              PersonWithAgeAlt(
                "Bob",
                25L,
                Some(CompanyWithRevenueAlt("TechCorp", 1000000L, List(PersonWithAgeAlt("Alice", 30L, None))))
              )
            )
          )
        )
      },
      test("converts company with coercion") {
        implicit lazy val personInto: Into[PersonWithAge, PersonWithAgeAlt] =
          Into.derived[PersonWithAge, PersonWithAgeAlt]
        implicit lazy val companyInto: Into[CompanyWithRevenue, CompanyWithRevenueAlt] =
          Into.derived[CompanyWithRevenue, CompanyWithRevenueAlt]

        val employees = List(
          PersonWithAge("Alice", 30, None),
          PersonWithAge("Bob", 25, None)
        )
        val source = CompanyWithRevenue("BigCorp", 5000000L, employees)
        val result = companyInto.into(source)

        assert(result)(
          isRight(
            equalTo(
              CompanyWithRevenueAlt(
                "BigCorp",
                5000000L,
                List(
                  PersonWithAgeAlt("Alice", 30L, None),
                  PersonWithAgeAlt("Bob", 25L, None)
                )
              )
            )
          )
        )
      }
    ),
    suite("Identity on Mutually Recursive Types")(
      test("converts mutually recursive type to itself") {
        implicit lazy val personInto: Into[PersonA, PersonA] = Into.derived[PersonA, PersonA]

        val company = CompanyA("SameCorp", List(PersonA("Same", None)))
        val source  = PersonA("Person", Some(company))
        val result  = personInto.into(source)

        assert(result)(isRight(equalTo(source)))
      }
    )
  )
}
