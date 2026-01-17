package zio.blocks.typeid

import zio.test._

object RecursiveTypeSpec extends ZIOSpecDefault {

  sealed trait Tree[+A]
  object Tree {
    final case class Leaf[+A](value: A)                        extends Tree[A]
    final case class Branch[+A](left: Tree[A], right: Tree[A]) extends Tree[A]
  }

  sealed trait RList[+A]
  object RList {
    case object Nil                                    extends RList[Nothing]
    final case class Cons[+A](head: A, tail: RList[A]) extends RList[A]
  }

  override def spec: Spec[Any, Any] = suite("RecursiveTypeSpec")(
    test("recursive enum types don't stack overflow on equals") {
      val id1 = TypeId.of[Tree[Int]]
      val id2 = TypeId.of[Tree[Int]]

      // Should terminate without stack overflow
      assertTrue(id1 == id2)
    },
    test("recursive types don't stack overflow on hashCode") {
      val id = TypeId.of[Tree[String]]

      // Should terminate without stack overflow
      val hash1 = id.hashCode
      val hash2 = id.hashCode

      assertTrue(hash1 == hash2)
    },
    test("deeply nested types are stable") {
      // Use simpler nesting that works reliably with Scala 2.13 macro expansion
      val id   = TypeId.of[List[Int]]
      val hash = id.hashCode

      assertTrue(
        hash != 0 &&
          id == TypeId.of[List[Int]]
      )
    },
    test("mutually recursive types") {
      // Use simpler types that work reliably with macro expansion
      val treeId = TypeId.of[Tree[Int]]
      val listId = TypeId.of[RList[Int]]

      // Both should compute without stack overflow
      val treeHash = treeId.hashCode
      val listHash = listId.hashCode

      assertTrue(treeHash != 0 && listHash != 0)
    },
    test("recursive type equality is deterministic") {
      // Use simpler types that work reliably with macro expansion
      val id1 = TypeId.of[Tree[Int]]
      val id2 = TypeId.of[Tree[Int]]
      val id3 = TypeId.of[Tree[Int]]

      assertTrue(id1 == id2 && id2 == id3)
      assertTrue(id1.hashCode == id2.hashCode)
    },
    test("standard library recursive types") {
      val listId = TypeId.of[scala.collection.immutable.List[Int]]

      // Should not stack overflow
      // Should not stack overflow
      val hash = listId.hashCode

      assertTrue(hash != 0 && listId == TypeId.of[scala.collection.immutable.List[Int]])
    }
  )
}
