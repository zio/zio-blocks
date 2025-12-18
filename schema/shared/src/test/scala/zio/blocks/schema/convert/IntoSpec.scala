package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoSpec extends ZIOSpecDefault {
  
  // Test types for field reordering by name
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  // Test types for unique type matching
  case class Person(name: String, age: Int, active: Boolean)
  case class User(username: String, yearsOld: Int, enabled: Boolean)

  def spec: Spec[TestEnvironment, Any] = suite("IntoSpec")(
    suite("Product to Product")(
      suite("Field reordering by name")(
        test("maps fields by name despite different ordering") {
          val point = Point(1, 2)
          val result = Into.derived[Point, Coord].into(point)
          
          // x→x, y→y (by name, not position)
          assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
        }
      ),
      suite("Unambiguous by unique types")(
        test("maps fields by unique type when names differ") {
          val person = Person(name = "Alice", age = 30, active = true)
          val result = Into.derived[Person, User].into(person)
          
          // Each type appears exactly once, so mapping is unambiguous:
          // String→String, Int→Int, Boolean→Boolean
          assert(result)(isRight(equalTo(User(username = "Alice", yearsOld = 30, enabled = true))))
        }
      )
    )
  )
}

