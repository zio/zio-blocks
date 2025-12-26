package zio.blocks.schema.into.structural

import zio.test._

/**
 * Tests that structural type conversions fail at compile time on Scala.js.
 * 
 * Structural types require reflection APIs (getClass.getMethod) which are
 * not available on Scala.js.
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
            result.swap.exists(_.contains("Structural type conversions are not supported on JS"))
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
            result.swap.exists(_.contains("Structural type conversions are not supported on JS"))
          )
        }
      }
    ),
    suite("Product to Structural - Compile Error on JS")(
      test("case class to structural type fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Point(x: Int, y: Int)
          type PointLike = { def x: Int; def y: Int }
          
          Into.derived[Point, PointLike]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(_.contains("Structural type conversions are not supported on JS"))
          )
        }
      },
      test("case class to structural type with subset of fields fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Person(name: String, age: Int, email: String)
          type NameOnly = { def name: String }
          
          Into.derived[Person, NameOnly]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(_.contains("Structural type conversions are not supported on JS"))
          )
        }
      }
    )
  )
}

