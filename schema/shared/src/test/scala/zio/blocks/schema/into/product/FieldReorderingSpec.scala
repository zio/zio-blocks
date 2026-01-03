package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for field reordering in Into conversions. */
object FieldReorderingSpec extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  case class Person(name: String, age: Int, email: String)
  case class PersonReordered(email: String, name: String, age: Int)

  def spec: Spec[TestEnvironment, Any] = suite("FieldReorderingSpec")(
    test("maps 2 fields by name despite reversed order") {
      val result = Into.derived[Point, Coord].into(Point(x = 1, y = 2))
      assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
    },
    test("maps 3 fields with complete reordering") {
      val result = Into.derived[Person, PersonReordered].into(Person("Alice", 30, "alice@test.com"))
      assert(result)(isRight(equalTo(PersonReordered("alice@test.com", "Alice", 30))))
    },
    test("partial reordering - some in order, some not") {
      case class Source(a: Int, b: String, c: Boolean)
      case class Target(a: Int, c: Boolean, b: String)

      val result = Into.derived[Source, Target].into(Source(1, "test", true))
      assert(result)(isRight(equalTo(Target(1, true, "test"))))
    }
  )
}
