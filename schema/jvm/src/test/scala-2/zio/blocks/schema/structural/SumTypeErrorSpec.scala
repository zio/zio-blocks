package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.test._

/**
 * Tests that sum types (sealed traits) produce compile-time errors in Scala 2.
 *
 * Sum types cannot be converted to structural types in Scala 2 because they
 * require union types, which are only available in Scala 3.
 */
object SumTypeErrorSpec extends SchemaBaseSpec {

  def spec = suite("SumTypeErrorSpec")(
    suite("Sealed Trait Structural Conversion")(
      test("sealed trait with case classes fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          sealed trait Result
          case class Success(value: Int) extends Result
          case class Failure(error: String) extends Result

          val schema = Schema.derived[Result]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.toLowerCase.contains("sum type") || msg.toLowerCase.contains("structural"))
          )
        }
      },
      test("sealed trait with case objects fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          sealed trait Status
          case object Active extends Status
          case object Inactive extends Status

          val schema = Schema.derived[Status]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("nested sealed trait fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          sealed trait Outer
          case class Inner(value: Int) extends Outer
          sealed trait NestedSum extends Outer
          case object A extends NestedSum
          case object B extends NestedSum

          val schema = Schema.derived[Outer]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Error Message Quality")(
      test("error message mentions sum types or union types") {
        typeCheck {
          """
          import zio.blocks.schema._

          sealed trait MySum
          case class CaseA(x: Int) extends MySum
          case class CaseB(y: String) extends MySum

          val schema = Schema.derived[MySum]
          schema.structural
          """
        }.map { result =>
          // The error should mention that sum types require Scala 3 union types
          assertTrue(
            result.isLeft,
            result.left.exists { msg =>
              msg.contains("sum") ||
              msg.contains("Sum") ||
              msg.contains("union") ||
              msg.contains("sealed") ||
              msg.contains("Scala 3")
            }
          )
        }
      }
    ),
    suite("Product Types Still Work")(
      test("case class converts to structural successfully") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Person(name: String, age: Int)

          val schema = Schema.derived[Person]
          val structural = schema.structural
          structural != null
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("tuple converts to structural successfully") {
        typeCheck {
          """
          import zio.blocks.schema._

          val schema = Schema.derived[(String, Int)]
          val structural = schema.structural
          structural != null
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    )
  )
}
