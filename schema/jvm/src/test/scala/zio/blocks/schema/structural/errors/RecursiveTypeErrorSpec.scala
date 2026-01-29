package zio.blocks.schema.structural.errors
import zio.blocks.schema.SchemaBaseSpec

import zio.test._

/**
 * Tests that recursive types produce compile-time errors when converting to
 * structural types.
 *
 * ==Overview==
 * Recursive types cannot be represented as structural types because Scala does
 * not support infinite types. The error message should clearly indicate:
 *   - What type is recursive
 *   - Why it cannot be converted
 *
 * ==Expected Error Format==
 * {{{
 * Cannot generate structural type for recursive type MyType.
 *
 * Structural types cannot represent recursive structures.
 * Scala's type system does not support infinite types.
 * }}}
 */
object RecursiveTypeErrorSpec extends SchemaBaseSpec {

  def spec = suite("RecursiveTypeErrorSpec")(
    suite("Direct Recursion Detection")(
      test("direct recursive type (LinkedList) fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class LinkedList(head: Int, tail: Option[LinkedList])

          val schema = Schema.derived[LinkedList]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            // Accept either our custom error message OR Scala 3's generic macro failure message
            result.left.exists(msg =>
              msg.toLowerCase.contains("recursive") ||
                msg.toLowerCase.contains("infinite") ||
                msg.toLowerCase.contains("macro expansion was stopped")
            )
          )
        }
      },
      test("self-referencing type fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class SelfRef(value: Int, next: SelfRef)

          val schema = Schema.derived[SelfRef]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Collection-Wrapped Recursion Detection")(
      test("list-wrapped recursive type (Tree) fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Tree(value: Int, children: List[Tree])

          val schema = Schema.derived[Tree]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            // Accept either our custom error message OR Scala 3's generic macro failure message
            result.left.exists(msg =>
              msg.toLowerCase.contains("recursive") ||
                msg.toLowerCase.contains("infinite") ||
                msg.toLowerCase.contains("macro expansion was stopped")
            )
          )
        }
      },
      test("option-wrapped recursive type fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Node(id: Int, next: Option[Node])

          val schema = Schema.derived[Node]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("set-wrapped recursive type fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Graph(id: Int, connections: Set[Graph])

          val schema = Schema.derived[Graph]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("vector-wrapped recursive type fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Sequence(value: Int, rest: Vector[Sequence])

          val schema = Schema.derived[Sequence]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Error Message Quality")(
      test("error message contains 'recursive' keyword") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Recursive(data: Int, self: List[Recursive])

          val schema = Schema.derived[Recursive]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists { msg =>
              msg.contains("recursive") ||
              msg.contains("Recursive") ||
              msg.contains("infinite") ||
              msg.contains("Infinite")
            }
          )
        }
      },
      test("error message mentions the type name") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class MyRecursiveType(value: Int, children: List[MyRecursiveType])

          val schema = Schema.derived[MyRecursiveType]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("MyRecursiveType"))
          )
        }
      }
    ),
    suite("Non-Recursive Types Still Work")(
      test("non-recursive case class converts to structural successfully") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Person(name: String, age: Int)

          val schema = Schema.derived[Person]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with nested non-recursive type works") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Inner(value: Int)
          case class Outer(name: String, inner: Inner)

          val schema = Schema.derived[Outer]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with collections works") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Container(items: List[Int], mapping: Map[String, Int])

          val schema = Schema.derived[Container]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    )
  )
}
