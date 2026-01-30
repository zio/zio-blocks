package zio.blocks.schema

import zio.test.*
import zio.test.Assertion.*

object IntoScala3Spec extends ZIOSpecDefault {

  enum Status {
    case Active, Inactive, Suspended
  }

  enum State {
    case Active, Inactive, Suspended
  }

  case class Point(x: Int, y: Int)
  case class Person(name: String, age: Int)

  type Coord      = { def x: Int; def y: Int }
  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[TestEnvironment, Any] = suite("IntoScala3Spec")(
    suite("Enum to Enum (Scala 3)")(
      test("maps enum values by matching names - Active") {
        val status: Status = Status.Active
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Active: State)))
      },
      test("maps Inactive status to Inactive state") {
        val status: Status = Status.Inactive
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Inactive: State)))
      },
      test("maps Suspended status to Suspended state") {
        val status: Status = Status.Suspended
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Suspended: State)))
      }
    ),
    suite("Structural Types")(
      // Product → Structural conversion requires experimental compiler features.
      // See IntoVersionSpecific.scala deriveProductToStructural for detailed explanation.

      suite("Structural Type to Product")(
        test("converts structural type to case class") {
          // Create a concrete class that matches the structural type signature
          class CoordImpl(val x: Int, val y: Int)
          val coord: Coord = new CoordImpl(5, 10).asInstanceOf[Coord]

          val result = Into.derived[Coord, Point].into(coord)

          assert(result)(isRight(equalTo(Point(5, 10))))
        },
        test("converts PersonLike structural type to Person case class") {
          // Create a concrete class that matches the structural type signature
          class PersonLikeImpl(val name: String, val age: Int)
          val personLike: PersonLike = new PersonLikeImpl("Bob", 25).asInstanceOf[PersonLike]

          val result = Into.derived[PersonLike, Person].into(personLike)

          assert(result)(isRight(equalTo(Person("Bob", 25))))
        }
      )
    )
  )
}
