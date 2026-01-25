package zio.blocks.schema.into.structural

import zio.test._

/**
 * Tests for structural type conversions on Scala.js.
 *
 *   - Structural types require reflection and fail at compile time on JS
 */
object StructuralTypeCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("StructuralTypeCompileErrorSpec")(
    suite("Structural types - Compile Error on JS")(
      test("structural type to case class fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          type PointLike = { def x: Int; def y: Int }
          case class Point(x: Int, y: Int)

          Into.derived[PointLike, Point]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            // Structural types require reflection, not supported on JS
            result.swap.exists(_.toLowerCase.contains("structural types require reflection"))
          )
        }
      },
      test("structural type with multiple fields fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          type PersonLike = { def name: String; def age: Int; def active: Boolean }
          case class Person(name: String, age: Int, active: Boolean)

          Into.derived[PersonLike, Person]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(_.toLowerCase.contains("structural types require reflection"))
          )
        }
      }
    )
  )
}
