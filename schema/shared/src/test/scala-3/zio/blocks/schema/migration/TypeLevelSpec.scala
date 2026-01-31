package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema.migration.TypeLevel._

object TypeLevelSpec extends ZIOSpecDefault {

  def spec = suite("TypeLevelSpec")(
    suite("Contains")(
      test("element present at head") {
        summon[Contains[("a", "b", "c"), "a"] =:= true]
        assertTrue(true)
      },
      test("element present in middle") {
        summon[Contains[("a", "b", "c"), "b"] =:= true]
        assertTrue(true)
      },
      test("element present at end") {
        summon[Contains[("a", "b", "c"), "c"] =:= true]
        assertTrue(true)
      },
      test("element absent") {
        summon[Contains[("a", "b", "c"), "d"] =:= false]
        assertTrue(true)
      },
      test("empty tuple contains nothing") {
        summon[Contains[EmptyTuple, "a"] =:= false]
        assertTrue(true)
      },
      test("single element tuple - present") {
        summon[Contains[Tuple1["x"], "x"] =:= true]
        assertTrue(true)
      },
      test("single element tuple - absent") {
        summon[Contains[Tuple1["x"], "y"] =:= false]
        assertTrue(true)
      }
    ),
    suite("IsSubset")(
      test("subset - true case") {
        summon[IsSubset[Tuple1["a"], ("a", "b")] =:= true]
        assertTrue(true)
      },
      test("subset - false case (element not in superset)") {
        summon[IsSubset[("a", "c"), ("a", "b")] =:= false]
        assertTrue(true)
      },
      test("empty is subset of all") {
        summon[IsSubset[EmptyTuple, ("a", "b")] =:= true]
        assertTrue(true)
      },
      test("empty is subset of empty") {
        summon[IsSubset[EmptyTuple, EmptyTuple] =:= true]
        assertTrue(true)
      },
      test("equal sets are subsets of each other") {
        summon[IsSubset[("a", "b"), ("a", "b")] =:= true]
        summon[IsSubset[("a", "b"), ("b", "a")] =:= true]
        assertTrue(true)
      },
      test("superset is not subset") {
        summon[IsSubset[("a", "b", "c"), ("a", "b")] =:= false]
        assertTrue(true)
      },
      test("disjoint sets - not subset") {
        summon[IsSubset[("x", "y"), ("a", "b")] =:= false]
        assertTrue(true)
      },
      test("single element subset") {
        summon[IsSubset[Tuple1["b"], ("a", "b", "c")] =:= true]
        assertTrue(true)
      }
    ),
    suite("compile-time safety")(
      test("wrong Contains result should not compile") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.TypeLevel._
          summon[Contains[("a", "b"), "c"] =:= true]
        """))(Assertion.isLeft)
      },
      test("wrong IsSubset result should not compile") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.TypeLevel._
          summon[IsSubset[("a", "c"), ("a", "b")] =:= true]
        """))(Assertion.isLeft)
      }
    )
  )
}
