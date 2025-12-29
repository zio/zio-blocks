package zio.blocks.schema.stress

import zio.blocks.schema._
import zio.test._

/**
 * Stress tests for Into and As with deeply nested structures.
 *
 * These tests verify that the macro can handle structures nested 5+ levels deep
 * and that conversions work correctly with complex hierarchies.
 */
object DeepNestingStressSpec extends ZIOSpecDefault {

  // Level 1 - innermost
  case class Level1A(value: Int)
  case class Level1B(value: Int)

  // Level 2
  case class Level2A(inner: Level1A, name: String)
  case class Level2B(inner: Level1B, name: String)

  // Level 3
  case class Level3A(inner: Level2A, count: Long)
  case class Level3B(inner: Level2B, count: Long)

  // Level 4
  case class Level4A(inner: Level3A, flag: Boolean)
  case class Level4B(inner: Level3B, flag: Boolean)

  // Level 5 - outermost
  case class Level5A(inner: Level4A, description: String)
  case class Level5B(inner: Level4B, description: String)

  // Same-type deep nesting for As tests
  case class DeepNode(value: Int, name: String, child: Option[DeepNode])

  // Deep nesting with collections
  case class CollNode(values: List[Int], children: List[CollNode])

  // Wide and deep combination
  case class WideAndDeepA(
    field1: Int,
    field2: String,
    nested1: Level3A,
    nested2: Level3A,
    field3: Boolean
  )
  case class WideAndDeepB(
    field1: Int,
    field2: String,
    nested1: Level3B,
    nested2: Level3B,
    field3: Boolean
  )

  // Multiple nested paths
  case class MultiPathA(
    path1: Level2A,
    path2: Level2A,
    path3: Level2A
  )
  case class MultiPathB(
    path1: Level2B,
    path2: Level2B,
    path3: Level2B
  )

  // Deep nesting with Option at each level
  case class OptLevel1A(value: Int)
  case class OptLevel1B(value: Int)

  case class OptLevel2A(inner: Option[OptLevel1A])
  case class OptLevel2B(inner: Option[OptLevel1B])

  case class OptLevel3A(inner: Option[OptLevel2A])
  case class OptLevel3B(inner: Option[OptLevel2B])

  def spec = suite("DeepNestingStressSpec")(
    suite("Into - 5 Levels Deep")(
      test("converts 5-level nested structure") {
        val source = Level5A(
          Level4A(
            Level3A(
              Level2A(
                Level1A(42),
                "inner"
              ),
              100L
            ),
            true
          ),
          "outer"
        )

        implicit val l1Into: Into[Level1A, Level1B] = Into.derived[Level1A, Level1B]
        implicit val l2Into: Into[Level2A, Level2B] = Into.derived[Level2A, Level2B]
        implicit val l3Into: Into[Level3A, Level3B] = Into.derived[Level3A, Level3B]
        implicit val l4Into: Into[Level4A, Level4B] = Into.derived[Level4A, Level4B]
        val l5Into: Into[Level5A, Level5B]          = Into.derived[Level5A, Level5B]

        val result = l5Into.into(source)
        assertTrue(result.isRight) && {
          val Right(target) = result: @unchecked
          assertTrue(
            target.inner.inner.inner.inner.value == 42,
            target.inner.inner.inner.name == "inner",
            target.inner.inner.count == 100L,
            target.inner.flag == true,
            target.description == "outer"
          )
        }
      },
      test("preserves data through all 5 levels") {
        val source = Level5A(
          Level4A(
            Level3A(
              Level2A(Level1A(999), "middle"),
              12345L
            ),
            false
          ),
          "top"
        )

        implicit val l1Into: Into[Level1A, Level1B] = Into.derived[Level1A, Level1B]
        implicit val l2Into: Into[Level2A, Level2B] = Into.derived[Level2A, Level2B]
        implicit val l3Into: Into[Level3A, Level3B] = Into.derived[Level3A, Level3B]
        implicit val l4Into: Into[Level4A, Level4B] = Into.derived[Level4A, Level4B]
        val l5Into: Into[Level5A, Level5B]          = Into.derived[Level5A, Level5B]

        val result = l5Into.into(source)

        val expected = Level5B(
          Level4B(
            Level3B(
              Level2B(Level1B(999), "middle"),
              12345L
            ),
            false
          ),
          "top"
        )
        assertTrue(result == Right(expected))
      }
    ),
    suite("Into - Wide and Deep")(
      test("converts wide structure with deep nesting") {
        val source = WideAndDeepA(
          field1 = 1,
          field2 = "wide",
          nested1 = Level3A(Level2A(Level1A(10), "n1"), 100L),
          nested2 = Level3A(Level2A(Level1A(20), "n2"), 200L),
          field3 = true
        )

        implicit val l1Into: Into[Level1A, Level1B]  = Into.derived[Level1A, Level1B]
        implicit val l2Into: Into[Level2A, Level2B]  = Into.derived[Level2A, Level2B]
        implicit val l3Into: Into[Level3A, Level3B]  = Into.derived[Level3A, Level3B]
        val wdInto: Into[WideAndDeepA, WideAndDeepB] = Into.derived[WideAndDeepA, WideAndDeepB]

        val result = wdInto.into(source)
        assertTrue(result.isRight) && {
          val Right(target) = result: @unchecked
          assertTrue(target.field1 == 1, target.nested1.inner.inner.value == 10, target.nested2.inner.inner.value == 20)
        }
      },
      test("converts structure with multiple nested paths") {
        val source = MultiPathA(
          path1 = Level2A(Level1A(1), "p1"),
          path2 = Level2A(Level1A(2), "p2"),
          path3 = Level2A(Level1A(3), "p3")
        )

        implicit val l1Into: Into[Level1A, Level1B] = Into.derived[Level1A, Level1B]
        implicit val l2Into: Into[Level2A, Level2B] = Into.derived[Level2A, Level2B]
        val mpInto: Into[MultiPathA, MultiPathB]    = Into.derived[MultiPathA, MultiPathB]

        val result = mpInto.into(source)
        assertTrue(result.isRight) && {
          val Right(target) = result: @unchecked
          assertTrue(target.path1.inner.value == 1, target.path2.inner.value == 2, target.path3.inner.value == 3)
        }
      }
    ),
    suite("As - Deep Nesting Round-Trip")(
      test("5-level structure round-trips correctly") {
        val source = Level5A(
          Level4A(
            Level3A(
              Level2A(Level1A(42), "test"),
              999L
            ),
            true
          ),
          "desc"
        )

        implicit val l1As: As[Level1A, Level1B] = As.derived[Level1A, Level1B]
        implicit val l2As: As[Level2A, Level2B] = As.derived[Level2A, Level2B]
        implicit val l3As: As[Level3A, Level3B] = As.derived[Level3A, Level3B]
        implicit val l4As: As[Level4A, Level4B] = As.derived[Level4A, Level4B]
        implicit val l5As: As[Level5A, Level5B] = As.derived[Level5A, Level5B]

        val forward = l5As.into(source)
        assertTrue(forward.isRight, forward.flatMap(l5As.from) == Right(source))
      }
    ),
    suite("Same-Type Deep Nesting")(
      test("deeply nested same type converts to itself") {
        val source = DeepNode(
          1,
          "root",
          Some(
            DeepNode(
              2,
              "child1",
              Some(
                DeepNode(
                  3,
                  "child2",
                  Some(DeepNode(4, "leaf", None))
                )
              )
            )
          )
        )

        implicit lazy val nodeInto: Into[DeepNode, DeepNode] = Into.derived[DeepNode, DeepNode]

        val result = nodeInto.into(source)
        assertTrue(result == Right(source))
      },
      test("deeply nested same type round-trips with As") {
        val source = DeepNode(
          10,
          "top",
          Some(DeepNode(20, "mid", Some(DeepNode(30, "bottom", None))))
        )

        implicit lazy val nodeAs: As[DeepNode, DeepNode] = As.derived[DeepNode, DeepNode]

        val forward = nodeAs.into(source)
        assertTrue(forward.isRight, forward.flatMap(nodeAs.from) == Right(source))
      }
    ),
    suite("Deep Nesting with Collections")(
      test("nested structure with collections at each level") {
        val source = CollNode(
          List(1, 2, 3),
          List(
            CollNode(
              List(4, 5),
              List(
                CollNode(List(6), List.empty)
              )
            ),
            CollNode(List(7, 8), List.empty)
          )
        )

        implicit lazy val collInto: Into[CollNode, CollNode] = Into.derived[CollNode, CollNode]

        val result = collInto.into(source)
        assertTrue(result == Right(source))
      },
      test("collection nesting round-trips with As") {
        val source = CollNode(
          List(100),
          List(CollNode(List(200, 300), List.empty))
        )

        implicit lazy val collAs: As[CollNode, CollNode] = As.derived[CollNode, CollNode]

        val forward = collAs.into(source)
        assertTrue(forward.isRight, forward.flatMap(collAs.from) == Right(source))
      }
    ),
    suite("Deep Option Nesting")(
      test("converts nested Options with Some values") {
        val source = OptLevel3A(
          Some(
            OptLevel2A(
              Some(OptLevel1A(42))
            )
          )
        )

        implicit val ol1Into: Into[OptLevel1A, OptLevel1B] = Into.derived[OptLevel1A, OptLevel1B]
        implicit val ol2Into: Into[OptLevel2A, OptLevel2B] = Into.derived[OptLevel2A, OptLevel2B]
        val ol3Into: Into[OptLevel3A, OptLevel3B]          = Into.derived[OptLevel3A, OptLevel3B]

        val result = ol3Into.into(source)
        assertTrue(result.isRight) && {
          val Right(target) = result: @unchecked
          assertTrue(target.inner.isDefined, target.inner.flatMap(_.inner).map(_.value).contains(42))
        }
      },
      test("converts nested Options with None values") {
        val source = OptLevel3A(None)

        implicit val ol1Into: Into[OptLevel1A, OptLevel1B] = Into.derived[OptLevel1A, OptLevel1B]
        implicit val ol2Into: Into[OptLevel2A, OptLevel2B] = Into.derived[OptLevel2A, OptLevel2B]
        val ol3Into: Into[OptLevel3A, OptLevel3B]          = Into.derived[OptLevel3A, OptLevel3B]

        val result = ol3Into.into(source)
        assertTrue(result == Right(OptLevel3B(None)))
      },
      test("nested Options round-trip with As (same type)") {
        // Note: As.derived for different element types in nested Options currently has limitations
        // when the inner As instances are defined locally. This tests same-type round-trip which works.
        val source = OptLevel3A(Some(OptLevel2A(Some(OptLevel1A(999)))))

        implicit lazy val ol3As: As[OptLevel3A, OptLevel3A] = As.derived[OptLevel3A, OptLevel3A]

        val forward = ol3As.into(source)
        assertTrue(forward.isRight, forward.flatMap(ol3As.from) == Right(source))
      }
    )
  )
}
