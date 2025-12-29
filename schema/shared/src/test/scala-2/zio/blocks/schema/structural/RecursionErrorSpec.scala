package zio.blocks.schema.structural

import zio.test._
import zio.test.Assertion._

/**
 * Tests verifying that recursive types produce proper compile-time errors.
 */
object RecursionErrorSpec extends ZIOSpecDefault {

  def spec = suite("RecursionErrorSpec (Scala 2)")(
    suite("Direct Recursion")(
      test("direct recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Tree(child: Tree)
          ToStructural.derived[Tree]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("self-referencing field fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Node(value: Int, next: Node)
          ToStructural.derived[Node]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      }
    ),
    suite("Recursion Through Collections")(
      test("list recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeList(children: List[TreeList])
          ToStructural.derived[TreeList]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("option recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeOption(next: Option[TreeOption])
          ToStructural.derived[TreeOption]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("vector recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeVector(children: Vector[TreeVector])
          ToStructural.derived[TreeVector]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("set recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeSet(children: Set[TreeSet])
          ToStructural.derived[TreeSet]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("map value recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeMap(children: Map[String, TreeMap])
          ToStructural.derived[TreeMap]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      }
    ),
    suite("Mutual Recursion")(
      test("mutual recursion between two types fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class NodeA(b: NodeB)
          case class NodeB(a: NodeA)
          ToStructural.derived[NodeA]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      },
      test("mutual recursion through collections fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Parent(children: List[Child])
          case class Child(parent: Parent)
          ToStructural.derived[Parent]
        """)
        assertZIO(result)(isLeft(containsString("recursive")))
      }
    ),
    suite("Non-Case Class Detection")(
      test("regular class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class Regular(val x: Int)
          ToStructural.derived[Regular]
        """)
        assertZIO(result)(isLeft(containsString("case class")))
      },
      test("trait fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          trait SomeTrait { def x: Int }
          ToStructural.derived[SomeTrait]
        """)
        assertZIO(result)(isLeft(containsString("case class")))
      },
      test("abstract class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          abstract class AbstractBase(val x: Int)
          ToStructural.derived[AbstractBase]
        """)
        assertZIO(result)(isLeft(containsString("case class")))
      }
    )
  )
}
