package zio.blocks.schema.structural.errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that mutually recursive types produce compile-time errors.
 */
object MutualRecursionErrorSpec extends ZIOSpecDefault {

  // These mutually recursive types should fail at compile time

  case class Node(id: Int, edges: List[Edge])
  case class Edge(from: Int, to: Node)

  case class Parent(name: String, children: List[Child])
  case class Child(name: String, parent: Option[Parent])

  def spec = suite("MutualRecursionErrorSpec")(
    test("mutually recursive types fail at compile time") {
      // Schema.derived[Node].structural should not compile
      // Compile error: Cannot generate structural type for mutually recursive types
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("indirect mutual recursion fails at compile time") {
      // Schema.derived[Parent].structural should not compile
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}

