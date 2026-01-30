package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for recursive type conversions.
 *
 * Covers:
 *   - Self-referential case classes (e.g., Tree)
 *   - Recursive types with collections
 *   - Type coercion within recursive structures
 */
object RecursiveTypeSpec extends ZIOSpecDefault {

  // === Simple recursive type (Tree) ===
  case class TreeA(value: Int, children: List[TreeA])
  case class TreeB(value: Long, children: List[TreeB])

  // === Linked list style recursive type ===
  case class NodeA(value: Int, next: Option[NodeA])
  case class NodeB(value: Long, next: Option[NodeB])

  // === Binary tree recursive type ===
  case class BinaryTreeA(value: Int, left: Option[BinaryTreeA], right: Option[BinaryTreeA])
  case class BinaryTreeB(value: Long, left: Option[BinaryTreeB], right: Option[BinaryTreeB])

  // === Recursive type with additional fields ===
  case class DirEntryA(name: String, size: Int, children: List[DirEntryA])
  case class DirEntryB(name: String, size: Long, children: List[DirEntryB])

  def spec: Spec[TestEnvironment, Any] = suite("RecursiveTypeSpec")(
    suite("Simple Recursive Tree")(
      test("converts leaf node (empty children)") {
        implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]

        val source = TreeA(42, Nil)
        val result = treeInto.into(source)

        assert(result)(isRight(equalTo(TreeB(42L, Nil))))
      },
      test("converts tree with one level of children") {
        implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]

        val source = TreeA(1, List(TreeA(2, Nil), TreeA(3, Nil)))
        val result = treeInto.into(source)

        assert(result)(isRight(equalTo(TreeB(1L, List(TreeB(2L, Nil), TreeB(3L, Nil))))))
      },
      test("converts tree with multiple levels") {
        implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]

        val source = TreeA(
          1,
          List(
            TreeA(2, List(TreeA(4, Nil), TreeA(5, Nil))),
            TreeA(3, List(TreeA(6, Nil)))
          )
        )
        val result = treeInto.into(source)

        assert(result)(
          isRight(
            equalTo(
              TreeB(
                1L,
                List(
                  TreeB(2L, List(TreeB(4L, Nil), TreeB(5L, Nil))),
                  TreeB(3L, List(TreeB(6L, Nil)))
                )
              )
            )
          )
        )
      }
    ),
    suite("Linked List Recursive Type")(
      test("converts single node (no next)") {
        implicit lazy val nodeInto: Into[NodeA, NodeB] = Into.derived[NodeA, NodeB]

        val source = NodeA(1, None)
        val result = nodeInto.into(source)

        assert(result)(isRight(equalTo(NodeB(1L, None))))
      },
      test("converts linked list of nodes") {
        implicit lazy val nodeInto: Into[NodeA, NodeB] = Into.derived[NodeA, NodeB]

        val source = NodeA(1, Some(NodeA(2, Some(NodeA(3, None)))))
        val result = nodeInto.into(source)

        assert(result)(isRight(equalTo(NodeB(1L, Some(NodeB(2L, Some(NodeB(3L, None))))))))
      }
    ),
    suite("Binary Tree Recursive Type")(
      test("converts leaf node") {
        implicit lazy val btreeInto: Into[BinaryTreeA, BinaryTreeB] = Into.derived[BinaryTreeA, BinaryTreeB]

        val source = BinaryTreeA(1, None, None)
        val result = btreeInto.into(source)

        assert(result)(isRight(equalTo(BinaryTreeB(1L, None, None))))
      },
      test("converts binary tree with left child only") {
        implicit lazy val btreeInto: Into[BinaryTreeA, BinaryTreeB] = Into.derived[BinaryTreeA, BinaryTreeB]

        val source = BinaryTreeA(1, Some(BinaryTreeA(2, None, None)), None)
        val result = btreeInto.into(source)

        assert(result)(isRight(equalTo(BinaryTreeB(1L, Some(BinaryTreeB(2L, None, None)), None))))
      },
      test("converts binary tree with right child only") {
        implicit lazy val btreeInto: Into[BinaryTreeA, BinaryTreeB] = Into.derived[BinaryTreeA, BinaryTreeB]

        val source = BinaryTreeA(1, None, Some(BinaryTreeA(3, None, None)))
        val result = btreeInto.into(source)

        assert(result)(isRight(equalTo(BinaryTreeB(1L, None, Some(BinaryTreeB(3L, None, None))))))
      },
      test("converts complete binary tree") {
        implicit lazy val btreeInto: Into[BinaryTreeA, BinaryTreeB] = Into.derived[BinaryTreeA, BinaryTreeB]

        val source = BinaryTreeA(
          1,
          Some(BinaryTreeA(2, Some(BinaryTreeA(4, None, None)), Some(BinaryTreeA(5, None, None)))),
          Some(BinaryTreeA(3, Some(BinaryTreeA(6, None, None)), Some(BinaryTreeA(7, None, None))))
        )
        val result = btreeInto.into(source)

        assert(result)(
          isRight(
            equalTo(
              BinaryTreeB(
                1L,
                Some(BinaryTreeB(2L, Some(BinaryTreeB(4L, None, None)), Some(BinaryTreeB(5L, None, None)))),
                Some(BinaryTreeB(3L, Some(BinaryTreeB(6L, None, None)), Some(BinaryTreeB(7L, None, None))))
              )
            )
          )
        )
      }
    ),
    suite("Directory Entry Recursive Type")(
      test("converts single directory entry with no children") {
        implicit lazy val dirInto: Into[DirEntryA, DirEntryB] = Into.derived[DirEntryA, DirEntryB]

        val source = DirEntryA("file.txt", 100, Nil)
        val result = dirInto.into(source)

        assert(result)(isRight(equalTo(DirEntryB("file.txt", 100L, Nil))))
      },
      test("converts directory structure") {
        implicit lazy val dirInto: Into[DirEntryA, DirEntryB] = Into.derived[DirEntryA, DirEntryB]

        val source = DirEntryA(
          "root",
          0,
          List(
            DirEntryA(
              "src",
              0,
              List(
                DirEntryA("main.scala", 500, Nil),
                DirEntryA("test.scala", 300, Nil)
              )
            ),
            DirEntryA("build.sbt", 100, Nil)
          )
        )
        val result = dirInto.into(source)

        assert(result)(
          isRight(
            equalTo(
              DirEntryB(
                "root",
                0L,
                List(
                  DirEntryB(
                    "src",
                    0L,
                    List(
                      DirEntryB("main.scala", 500L, Nil),
                      DirEntryB("test.scala", 300L, Nil)
                    )
                  ),
                  DirEntryB("build.sbt", 100L, Nil)
                )
              )
            )
          )
        )
      }
    ),
    suite("Identity on Recursive Types")(
      test("converts recursive tree to itself") {
        implicit lazy val treeInto: Into[TreeA, TreeA] = Into.derived[TreeA, TreeA]

        val source = TreeA(1, List(TreeA(2, Nil), TreeA(3, Nil)))
        val result = treeInto.into(source)

        assert(result)(isRight(equalTo(source)))
      }
    )
  )
}
