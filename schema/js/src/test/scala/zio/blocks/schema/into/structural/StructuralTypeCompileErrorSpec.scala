package zio.blocks.schema.into.structural

import zio.test._

/**
 * Tests for structural type conversions on Scala.js.
 *
 *   - Structural → Product: Requires reflection, fails at compile time on JS
 */
object StructuralTypeCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("StructuralTypeCompileErrorSpec")(
    suite("Structural to Product - Compile Error on JS")(
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
            // Scala 2: "Non-Dynamic structural type conversions are not supported on JS"
            // Scala 3: "Structural type conversions are not supported on JS"
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on js"))
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
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on js"))
          )
        }
      }
    )
  )
}
