package zio.blocks.schema.as.edge

import zio.blocks.schema._
import zio.test._

/** Tests for round-trip conversions with recursive types using As. */
object RecursiveTypeRoundTripSpec extends ZIOSpecDefault {

  case class TreeNode(value: Int, children: List[TreeNode])
  case class LinkedNode(value: String, next: Option[LinkedNode])

  def spec: Spec[TestEnvironment, Any] = suite("RecursiveTypeRoundTripSpec")(
    test("leaf node round-trips correctly") {
      val original                                     = TreeNode(1, List.empty)
      implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived
      val forward                                      = treeAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(treeAs.from) == Right(original))
    },
    test("tree with children round-trips") {
      val original                                     = TreeNode(1, List(TreeNode(2, List.empty), TreeNode(3, List.empty)))
      implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived
      val forward                                      = treeAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(treeAs.from) == Right(original))
    },
    test("linked list round-trips") {
      val original                                           = LinkedNode("a", Some(LinkedNode("b", Some(LinkedNode("c", None)))))
      implicit lazy val linkedAs: As[LinkedNode, LinkedNode] = As.derived
      val forward                                            = linkedAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(linkedAs.from) == Right(original))
    }
  )
}
