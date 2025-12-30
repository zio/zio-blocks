package zio.blocks.schema.into.structural

import zio.test._

/**
 * Tests that structural type conversions fail at compile time on Scala Native.
 */
object StructuralTypeCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("StructuralTypeCompileErrorSpec")(
    suite("Structural to Product - Compile Error on Native")(
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
            // Scala 2: "Non-Dynamic structural type conversions are not supported on Native"
            // Scala 3: "Structural type conversions are not supported on Native"
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on native"))
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
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on native"))
          )
        }
      }
    )
  )
}
