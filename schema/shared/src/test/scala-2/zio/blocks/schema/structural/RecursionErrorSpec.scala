package zio.blocks.schema.structural

import zio.test._
import zio.test.Assertion._


object RecursionErrorSpec extends ZIOSpecDefault {

  def spec = suite("RecursionErrorSpec (Scala 2)")(
    suite("Direct Recursion")(
      test("direct recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Tree(child: Tree)
          ToStructural.derived[Tree]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("Tree") &&
            containsString("cannot represent recursive structures")
          )
        )
      },
      test("self-referencing field fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Node(value: Int, next: Node)
          ToStructural.derived[Node]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("Node") &&
            containsString("cannot represent recursive structures")
          )
        )
      }
    ),
    suite("Recursion Through Collections")(
      test("list recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeList(children: List[TreeList])
          ToStructural.derived[TreeList]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("TreeList")
          )
        )
      },
      test("option recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeOption(next: Option[TreeOption])
          ToStructural.derived[TreeOption]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("TreeOption")
          )
        )
      },
      test("vector recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeVector(children: Vector[TreeVector])
          ToStructural.derived[TreeVector]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("TreeVector")
          )
        )
      },
      test("set recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeSet(children: Set[TreeSet])
          ToStructural.derived[TreeSet]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("TreeSet")
          )
        )
      },
      test("map value recursion fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class TreeMap(children: Map[String, TreeMap])
          ToStructural.derived[TreeMap]
        """)
        assertZIO(result)(
          isLeft(
            containsString("recursive type detected") &&
            containsString("TreeMap")
          )
        )
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
        assertZIO(result)(
          isLeft(
            containsString("mutually recursive types detected") &&
            (containsString("NodeA") || containsString("NodeB")) &&
            containsString("cyclic dependencies")
          )
        )
      },
      test("mutual recursion through collections fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Parent(children: List[Child])
          case class Child(parent: Parent)
          ToStructural.derived[Parent]
        """)
        assertZIO(result)(
          isLeft(
            containsString("mutually recursive types detected") &&
            (containsString("Parent") || containsString("Child")) &&
            containsString("cyclic dependencies")
          )
        )
      }
    ),
    suite("Non-Case Class Detection")(
      test("regular class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class Regular(val x: Int)
          ToStructural.derived[Regular]
        """)
        assertZIO(result)(
          isLeft(
            containsString("only supports case classes") &&
            containsString("Regular")
          )
        )
      },
      test("trait fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          trait SomeTrait { def x: Int }
          ToStructural.derived[SomeTrait]
        """)
        assertZIO(result)(
          isLeft(
            containsString("only supports case classes") &&
            containsString("SomeTrait")
          )
        )
      },
      test("abstract class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          abstract class AbstractBase(val x: Int)
          ToStructural.derived[AbstractBase]
        """)
        assertZIO(result)(
          isLeft(
            containsString("only supports case classes") &&
            containsString("AbstractBase")
          )
        )
      }
    )
  )
}
