package zio.blocks.schema.into.structural

import zio.test._

/**
 * Tests that structural types fail at compile time on Scala Native.
 */
object StructuralTypeCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("StructuralTypeCompileErrorSpec")(
    suite("Structural types - Compile Error on Native")(
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
            // Structural types require reflection, not supported on Native
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
