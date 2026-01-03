package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for case class to tuple conversions. */
object CaseClassToTupleSpec extends ZIOSpecDefault {

  case class Point2D(x: Double, y: Double)
  case class Person(name: String, age: Int)

  def spec: Spec[TestEnvironment, Any] = suite("CaseClassToTupleSpec")(
    test("converts 2-field case class to Tuple2") {
      val result = Into.derived[Point2D, (Double, Double)].into(Point2D(1.0, 2.0))
      assert(result)(isRight(equalTo((1.0, 2.0))))
    },
    test("converts Person to (String, Int)") {
      val result = Into.derived[Person, (String, Int)].into(Person("Alice", 30))
      assert(result)(isRight(equalTo(("Alice", 30))))
    },
    test("widens Int to Long in tuple elements") {
      case class IntPair(a: Int, b: Int)
      val result = Into.derived[IntPair, (Long, Long)].into(IntPair(10, 20))
      assert(result)(isRight(equalTo((10L, 20L))))
    }
  )
}
