package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoSpec extends ZIOSpecDefault {

  // Priority 1: Exact match (same name + same type) with reordering
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  // Priority 3: Unique type match (each type appears once)
  case class Person(name: String, age: Int, active: Boolean)
  case class User(username: String, yearsOld: Int, enabled: Boolean)

  // Priority 4: Position + matching type (for tuple-like conversions)
  case class RGB(r: Int, g: Int, b: Int)
  case class ColorValues(red: Int, green: Int, blue: Int)

  // Combined: some fields match by name, others by unique type
  case class Employee(id: Long, name: String, department: Int)
  case class Worker(name: String, team: Int, identifier: Long)

  // Case class to case class with unique type matching
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int)

  // Case class to tuple
  case class PointDouble(x: Double, y: Double)

  // Tuple to case class
  case class RGBColor(red: Int, green: Int, blue: Int)

  def spec: Spec[TestEnvironment, Any] = suite("IntoSpec")(
    suite("Product to Product")(
      suite("Priority 1: Exact match (same name + same type)")(
        test("maps fields by name despite different ordering") {
          val point = Point(1, 2)
          val result = Into.derived[Point, Coord].into(point)

          // x→x, y→y (by name, not position)
          assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
        },
        test("maps fields when names and types match exactly") {
          case class Source(id: String, value: Int)
          case class Target(id: String, value: Int)
          
          val source = Source("abc", 42)
          val result = Into.derived[Source, Target].into(source)
          
          assert(result)(isRight(equalTo(Target("abc", 42))))
        }
      ),
      suite("Priority 3: Unique type match")(
        test("maps fields by unique type when names differ") {
          val person = Person(name = "Alice", age = 30, active = true)
          val result = Into.derived[Person, User].into(person)

          // Each type appears exactly once, so mapping is unambiguous:
          // String→String, Int→Int, Boolean→Boolean
          assert(result)(isRight(equalTo(User(username = "Alice", yearsOld = 30, enabled = true))))
        }
      ),
      suite("Priority 4: Position + matching type")(
        test("maps fields by position when types match but names differ (tuple-like)") {
          // r→red, g→green, b→blue by position (all Int, names don't match)
          val rgb = RGB(r = 255, g = 128, b = 64)
          val result = Into.derived[RGB, ColorValues].into(rgb)
          
          assert(result)(isRight(equalTo(ColorValues(red = 255, green = 128, blue = 64))))
        }
      ),
      suite("Combined: name match and unique type match")(
        test("uses name match for matching names and unique type for others") {
          // name→name (exact match), id→identifier (Long is unique), department→team (Int is unique)
          val employee = Employee(id = 123L, name = "Bob", department = 5)
          val result = Into.derived[Employee, Worker].into(employee)
          
          assert(result)(isRight(equalTo(Worker(name = "Bob", team = 5, identifier = 123L))))
        }
      ),
      suite("Case Class to Case Class")(
        test("maps by unique type when field names differ") {
          // 'name' is unique String in V1, 'fullName' is unique String in V2
          // 'age' matches by name
          val personV1 = PersonV1("Alice", 30)
          val result = Into.derived[PersonV1, PersonV2].into(personV1)
          
          assert(result)(isRight(equalTo(PersonV2("Alice", 30))))
        }
      ),
      suite("Case Class to Tuple")(
        test("maps case class fields to tuple by position") {
          val point = PointDouble(1.0, 2.0)
          val result = Into.derived[PointDouble, (Double, Double)].into(point)
          
          assert(result)(isRight(equalTo((1.0, 2.0))))
        }
      ),
      suite("Tuple to Case Class")(
        test("maps tuple elements to case class fields by position") {
          val tuple = (255, 128, 64)
          val result = Into.derived[(Int, Int, Int), RGBColor].into(tuple)
          
          assert(result)(isRight(equalTo(RGBColor(red = 255, green = 128, blue = 64))))
        }
      ),
      suite("Tuple to Tuple")(
        test("maps tuple to tuple by position with same types") {
          val tuple = (42, "hello")
          val result = Into.derived[(Int, String), (Int, String)].into(tuple)
          
          assert(result)(isRight(equalTo((42, "hello"))))
        }
        // TODO: Add test for coercion (Int -> Long) once coercion is implemented
        // test("maps tuple to tuple with type coercion") {
        //   val tuple = (42, "hello")
        //   val result = Into.derived[(Int, String), (Long, String)].into(tuple)
        //   assert(result)(isRight(equalTo((42L, "hello"))))
        // }
      )
    )
  )
}

