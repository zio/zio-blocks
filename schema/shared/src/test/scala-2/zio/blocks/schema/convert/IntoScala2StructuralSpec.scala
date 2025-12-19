package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoScala2StructuralSpec extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)

  def spec = suite("IntoScala2StructuralSpec")(
    suite("Structural Types - Scala 2 only")(
      test("Structural Type to Product") {
        import scala.language.reflectiveCalls

        class CoordImpl(val x: Int, val y: Int)
        type CoordStructural = { def x: Int; def y: Int }
        val coord: CoordStructural = new CoordImpl(5, 10)

        val result = Into.derived[CoordStructural, Point].into(coord)

        assert(result)(isRight(equalTo(Point(5, 10))))
      },
      test("Product to Structural Type") {
        import scala.language.reflectiveCalls

        type CoordStructural = { def x: Int; def y: Int }
        val point = Point(7, 14)

        val result = Into.derived[Point, CoordStructural].into(point)

        assertTrue(result.isRight) && {
          val coord = result.toOption.get
          assertTrue(coord.x == 7, coord.y == 14)
        }
      }
    )
  )
}

