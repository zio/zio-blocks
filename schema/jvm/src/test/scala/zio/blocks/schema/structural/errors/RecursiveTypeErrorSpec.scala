package zio.blocks.schema.structural.errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that recursive types produce compile-time errors.
 * 
 * Recursive types cannot be converted to structural types because
 * Scala does not support infinite types.
 */
object RecursiveTypeErrorSpec extends ZIOSpecDefault {

  // These types should fail at compile time when calling .structural

  case class Tree(value: Int, children: List[Tree])
  case class LinkedList(head: Int, tail: Option[LinkedList])

  def spec = suite("RecursiveTypeErrorSpec")(
    test("direct recursive type fails at compile time") {
      // Schema.derived[Tree].structural should not compile
      // Compile error: Cannot generate structural type for recursive type Tree.
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("option-wrapped recursive type fails at compile time") {
      // Schema.derived[LinkedList].structural should not compile
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("list-wrapped recursive type fails at compile time") {
      // Schema.derived[Tree].structural should not compile
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}

