package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for recursive type conversions. */
object RecursiveTypeSpec extends ZIOSpecDefault {

  case class TreeA(value: Int, children: List[TreeA])
  case class TreeB(value: Long, children: List[TreeB])

  case class NodeA(value: Int, next: Option[NodeA])
  case class NodeB(value: Long, next: Option[NodeB])

  def spec: Spec[TestEnvironment, Any] = suite("RecursiveTypeSpec")(
    test("converts leaf node (empty children)") {
      implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]
      val result                                     = treeInto.into(TreeA(42, Nil))
      assert(result)(isRight(equalTo(TreeB(42L, Nil))))
    },
    test("converts tree with children") {
      implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]
      val source                                     = TreeA(1, List(TreeA(2, Nil), TreeA(3, List(TreeA(4, Nil)))))
      val result                                     = treeInto.into(source)
      assert(result)(isRight(equalTo(TreeB(1L, List(TreeB(2L, Nil), TreeB(3L, List(TreeB(4L, Nil))))))))
    },
    test("converts linked list") {
      implicit lazy val nodeInto: Into[NodeA, NodeB] = Into.derived[NodeA, NodeB]
      val source                                     = NodeA(1, Some(NodeA(2, Some(NodeA(3, None)))))
      val result                                     = nodeInto.into(source)
      assert(result)(isRight(equalTo(NodeB(1L, Some(NodeB(2L, Some(NodeB(3L, None))))))))
    }
  )
}
