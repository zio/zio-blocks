package zio.blocks.schema

import zio.Scope
import zio.test._
import zio.test.Assertion._

object DiffSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("DiffSpec")(
    suite("Record")(
      test("recover original from patch") {
        case class Person(name: String, age: Int)

        object Person extends CompanionOptics[Person] {
          implicit val schema: Schema[Person] = Schema.derived
          val name: Lens[Person, String]      = field(_.name)
          val age: Lens[Person, Int]          = field(_.age)
        }

        val person  = Person("Alice", 30)
        val person2 = Person.name.replace(person, "Not-Alice")
        val person3 = Person.age.replace(person2, 99)
        val patch   = Person.schema.diff(person, person3)

        assert(patch.apply(person))(isSome(equalTo(person3)))
      },
      test("diff to self is no-op") {
        case class Person(name: String, age: Int)

        val schema: Schema[Person] = Schema.derived
        val person                 = Person("Bob", 25)
        val patch                  = schema.diff(person, person)

        assert(patch.apply(person))(isSome(equalTo(person)))
      }
    ),
    suite("Variant")(
      test("recover") {
        sealed trait A
        case class B(b: Int) extends A
        case class C(c: Int) extends A

        object B extends CompanionOptics[B] {
          implicit val schema: Schema[B] = Schema.derived
        }

        object C extends CompanionOptics[C] {
          implicit val schema: Schema[C] = Schema.derived
        }

        object A extends CompanionOptics[A] {
          implicit val schema: Schema[A] = Schema.derived
          val b: Prism[A, B]             = caseOf
          val c: Prism[A, C]             = caseOf
        }

        val b         = B(2)
        val c         = C(3)
        val diff      = A.schema.diff(b, C(3))
        val recovered = diff.apply(b)

        assert(recovered)(isSome(equalTo(c)))
      }
    ) @@ TestAspect.ignore
  )
}
