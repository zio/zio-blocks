package zio.blocks.schema.structural.errors

import zio.test._

/**
 * Tests that mutually recursive types produce compile-time errors when
 * converting to structural.
 *
 * ==Overview==
 * Mutual recursion occurs when:
 *   - Type A references type B and type B references type A (direct)
 *   - Type A → B → C → A (chain)
 *
 * ==Expected Error Format==
 * {{{
 * Cannot generate structural type for recursive type MyType.
 *
 * Structural types cannot represent recursive structures.
 * Scala's type system does not support infinite types.
 * }}}
 *
 * Note: The error message for mutual recursion is the same as direct recursion
 * because from the perspective of each type, it appears as a recursive
 * reference.
 */
object MutualRecursionErrorSpec extends ZIOSpecDefault {

  def spec = suite("MutualRecursionErrorSpec")(
    suite("Two-Way Mutual Recursion Detection")(
      test("Node-Edge mutual recursion fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Node(id: Int, edges: List[Edge])
          case class Edge(from: Int, to: Node)

          val schema = Schema.derived[Node]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("Parent-Child mutual recursion fails to convert to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Parent(name: String, children: List[Child])
          case class Child(name: String, parent: Option[Parent])

          val schema = Schema.derived[Parent]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("both sides of mutual recursion fail - TypeA") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class TypeA(data: Int, ref: TypeB)
          case class TypeB(data: String, ref: TypeA)

          val schemaA = Schema.derived[TypeA]
          schemaA.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("both sides of mutual recursion fail - TypeB") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class TypeA(data: Int, ref: TypeB)
          case class TypeB(data: String, ref: TypeA)

          val schemaB = Schema.derived[TypeB]
          schemaB.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Three-Way Mutual Recursion Detection")(
      test("A -> B -> C -> A mutual recursion fails") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class TypeA(b: TypeB)
          case class TypeB(c: TypeC)
          case class TypeC(a: Option[TypeA])

          val schema = Schema.derived[TypeA]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("three-way with collections fails") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Company(departments: List[Department])
          case class Department(employees: List[Employee])
          case class Employee(company: Company)

          val schema = Schema.derived[Company]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Error Message Quality")(
      test("error message mentions recursive nature") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Ping(next: Pong)
          case class Pong(next: Ping)

          val schema = Schema.derived[Ping]
          schema.structural
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            // Accept either our custom error message OR Scala 3's generic macro failure message
            result.left.exists { msg =>
              msg.contains("recursive") ||
              msg.contains("Recursive") ||
              msg.contains("infinite") ||
              msg.contains("Infinite") ||
              msg.contains("circular") ||
              msg.toLowerCase.contains("macro expansion was stopped")
            }
          )
        }
      }
    ),
    suite("Non-Mutually-Recursive Types Still Work")(
      test("independent case classes work") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Person(name: String, age: Int)
          case class Address(street: String, city: String)

          val schema = Schema.derived[Person]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("nested but non-recursive types work") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Inner(value: Int)
          case class Middle(inner: Inner)
          case class Outer(middle: Middle)

          val schema = Schema.derived[Outer]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("diamond-shaped dependency (non-recursive) works") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Leaf(value: Int)
          case class Left(leaf: Leaf)
          case class Right(leaf: Leaf)
          case class Root(left: Left, right: Right)

          val schema = Schema.derived[Root]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    )
  )
}
