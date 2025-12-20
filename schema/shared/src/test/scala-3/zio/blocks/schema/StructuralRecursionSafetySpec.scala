package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object StructuralRecursionSafetySpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("StructuralRecursionSafetySpec")(
    test("fails compilation for directly recursive type") {
      assertZIO(
        typeCheck(
          """
import zio.blocks.schema._
case class Tree(value: Int, children: List[Tree])
val ts = ToStructural.derived[Tree]
          """
        )
      )(
        isLeft(
          containsString("Cannot generate structural type for recursive type Tree")
        )
      )
    },
    test("fails compilation for mutually recursive types") {
      assertZIO(
        typeCheck(
          """
import zio.blocks.schema._
case class Node(id: Int, edges: List[Edge])
case class Edge(from: Int, to: Node)
val ts = ToStructural.derived[Node]
          """
        )
      )(
        isLeft(
          containsString("mutually recursive types") &&
            containsString("Node") &&
            containsString("Edge")
        )
      )
    },
    test("fails compilation for non-sealed trait") {
      assertZIO(
        typeCheck(
          """
import zio.blocks.schema._
trait OpenTrait { def x: Int }
val ts = ToStructural.derived[OpenTrait]
          """
        )
      )(
        isLeft(anything) // Should produce some error for unsupported type
      )
    },
    test("fails compilation for abstract class") {
      assertZIO(
        typeCheck(
          """
import zio.blocks.schema._
abstract class AbstractClass(val x: Int)
val ts = ToStructural.derived[AbstractClass]
          """
        )
      )(
        isLeft(anything) // Should produce some error for unsupported type
      )
    }
  )
}
