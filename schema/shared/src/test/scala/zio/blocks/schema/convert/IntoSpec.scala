package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoSpec extends ZIOSpecDefault {
  
  // Test types for field reordering by name
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  def spec: Spec[TestEnvironment, Any] = suite("IntoSpec")(
    suite("Product to Product")(
      suite("Field reordering by name")(
        test("maps fields by name despite different ordering") {
          val point = Point(1, 2)
          val result = Into.derived[Point, Coord].into(point)
          
          // x→x, y→y (by name, not position)
          assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
        }
      )
    )
  )
}

