package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for tuple to case class conversions. */
object TupleToCaseClassSpec extends ZIOSpecDefault {

  case class Point2D(x: Double, y: Double)
  case class Person(name: String, age: Int)

  def spec: Spec[TestEnvironment, Any] = suite("TupleToCaseClassSpec")(
    test("converts Tuple2 to 2-field case class") {
      val result = Into.derived[(Double, Double), Point2D].into((1.0, 2.0))
      assert(result)(isRight(equalTo(Point2D(1.0, 2.0))))
    },
    test("converts (String, Int) to Person") {
      val result = Into.derived[(String, Int), Person].into(("Alice", 30))
      assert(result)(isRight(equalTo(Person("Alice", 30))))
    },
    test("widens Int to Long in case class fields") {
      case class LongPair(a: Long, b: Long)
      val result = Into.derived[(Int, Int), LongPair].into((10, 20))
      assert(result)(isRight(equalTo(LongPair(10L, 20L))))
    }
  )
}
