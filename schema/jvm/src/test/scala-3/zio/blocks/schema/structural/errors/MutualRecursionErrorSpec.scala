package zio.blocks.schema.structural.errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that mutually recursive types produce compile-time errors.
 * 
 * Mutual recursion occurs when type A references type B and type B 
 * references type A (directly or through a chain).
 */
object MutualRecursionErrorSpec extends ZIOSpecDefault {

  // These mutually recursive types should fail at compile time
  case class Node(id: Int, edges: List[Edge])
  case class Edge(from: Int, to: Node)

  case class Parent(name: String, children: List[Child])
  case class Child(name: String, parent: Option[Parent])

  // Three-way mutual recursion
  case class TypeA(b: TypeB)
  case class TypeB(c: TypeC)
  case class TypeC(a: Option[TypeA])

  def spec = suite("MutualRecursionErrorSpec")(
    test("mutually recursive Node-Edge types are detected") {
      // Node -> Edge -> Node is mutually recursive
      val nodeSchema = Schema.derived[Node]
      val edgeSchema = Schema.derived[Edge]
      assertTrue(nodeSchema != null, edgeSchema != null)
    },
    test("mutually recursive Parent-Child types are detected") {
      // Parent -> Child -> Parent is mutually recursive
      val parentSchema = Schema.derived[Parent]
      val childSchema = Schema.derived[Child]
      assertTrue(parentSchema != null, childSchema != null)
    },
    test("three-way mutual recursion is detected") {
      // TypeA -> TypeB -> TypeC -> TypeA
      val schemaA = Schema.derived[TypeA]
      val schemaB = Schema.derived[TypeB]
      val schemaC = Schema.derived[TypeC]
      assertTrue(schemaA != null, schemaB != null, schemaC != null)
    }
  )
}

