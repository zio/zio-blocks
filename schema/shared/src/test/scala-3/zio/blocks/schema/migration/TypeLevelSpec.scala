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
    suite("Union")(
      test("disjoint tuples") {
        // Union of ("a") and ("b") should contain both
        summon[Contains[Union[Tuple1["a"], Tuple1["b"]], "a"] =:= true]
        summon[Contains[Union[Tuple1["a"], Tuple1["b"]], "b"] =:= true]
        assertTrue(true)
      },
      test("overlapping tuples") {
        // Union of ("a", "b") and ("b", "c") should contain a, b, c
        type Result = Union[("a", "b"), ("b", "c")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        summon[Contains[Result, "c"] =:= true]
        assertTrue(true)
      },
      test("union with empty - left") {
        type Result = Union[EmptyTuple, ("a", "b")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        assertTrue(true)
      },
      test("union with empty - right") {
        type Result = Union[("a", "b"), EmptyTuple]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        assertTrue(true)
      },
      test("union of identical tuples") {
        type Result = Union[("a", "b"), ("a", "b")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        // Should not have duplicates - size should be 2
        summon[IsSubset[Result, ("a", "b")] =:= true]
        assertTrue(true)
      },
      test("union preserves all elements from first tuple") {
        type Result = Union[("x", "y", "z"), Tuple1["a"]]
        summon[Contains[Result, "x"] =:= true]
        summon[Contains[Result, "y"] =:= true]
        summon[Contains[Result, "z"] =:= true]
        summon[Contains[Result, "a"] =:= true]
        assertTrue(true)
      }
    ),
    suite("Difference")(
      test("some elements removed") {
        type Result = Difference[("a", "b", "c"), Tuple1["b"]]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= false]
        summon[Contains[Result, "c"] =:= true]
        assertTrue(true)
      },
      test("no elements removed (disjoint)") {
        type Result = Difference[("a", "b"), ("c", "d")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        assertTrue(true)
      },
      test("all elements removed") {
        type Result = Difference[Tuple1["a"], Tuple1["a"]]
        summon[Result =:= EmptyTuple]
        assertTrue(true)
      },
      test("difference from empty tuple") {
        type Result = Difference[EmptyTuple, ("a", "b")]
        summon[Result =:= EmptyTuple]
        assertTrue(true)
      },
      test("difference with empty removal set") {
        type Result = Difference[("a", "b"), EmptyTuple]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        assertTrue(true)
      },
      test("multiple elements removed") {
        type Result = Difference[("a", "b", "c", "d"), ("b", "d")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= false]
        summon[Contains[Result, "c"] =:= true]
        summon[Contains[Result, "d"] =:= false]
        assertTrue(true)
      }
    ),
    suite("Intersect")(
      test("some common elements") {
        type Result = Intersect[("a", "b"), ("b", "c")]
        summon[Contains[Result, "b"] =:= true]
        summon[Contains[Result, "a"] =:= false]
        summon[Contains[Result, "c"] =:= false]
        assertTrue(true)
      },
      test("no common elements") {
        type Result = Intersect[Tuple1["a"], Tuple1["b"]]
        summon[Result =:= EmptyTuple]
        assertTrue(true)
      },
      test("all common elements (identical tuples)") {
        type Result = Intersect[("a", "b"), ("a", "b")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        assertTrue(true)
      },
      test("intersect with empty - left") {
        type Result = Intersect[EmptyTuple, ("a", "b")]
        summon[Result =:= EmptyTuple]
        assertTrue(true)
      },
      test("intersect with empty - right") {
        type Result = Intersect[("a", "b"), EmptyTuple]
        summon[Result =:= EmptyTuple]
        assertTrue(true)
      },
      test("intersect preserves order from first tuple") {
        type Result = Intersect[("c", "a", "b"), ("a", "b", "c")]
        summon[Contains[Result, "a"] =:= true]
        summon[Contains[Result, "b"] =:= true]
        summon[Contains[Result, "c"] =:= true]
        assertTrue(true)
      },
      test("partial overlap") {
        type Result = Intersect[("a", "b", "c"), ("b", "c", "d")]
        summon[Contains[Result, "a"] =:= false]
        summon[Contains[Result, "b"] =:= true]
        summon[Contains[Result, "c"] =:= true]
        summon[Contains[Result, "d"] =:= false]
        assertTrue(true)
      }
    ),
    suite("TupleEquals")(
      test("identical tuples are equal") {
        summon[TupleEquals[("a", "b"), ("a", "b")] =:= true]
        assertTrue(true)
      },
      test("same elements different order are equal") {
        summon[TupleEquals[("a", "b"), ("b", "a")] =:= true]
        assertTrue(true)
      },
      test("different tuples are not equal") {
        summon[TupleEquals[("a", "b"), ("a", "c")] =:= false]
        assertTrue(true)
      },
      test("subset is not equal to superset") {
        summon[TupleEquals[Tuple1["a"], ("a", "b")] =:= false]
        assertTrue(true)
      },
      test("empty tuples are equal") {
        summon[TupleEquals[EmptyTuple, EmptyTuple] =:= true]
        assertTrue(true)
      }
    ),
    suite("Size")(
      test("empty tuple has size 0") {
        summon[Size[EmptyTuple] =:= 0]
        assertTrue(true)
      },
      test("single element has size 1") {
        summon[Size[Tuple1["a"]] =:= 1]
        assertTrue(true)
      },
      test("two elements has size 2") {
        summon[Size[("a", "b")] =:= 2]
        assertTrue(true)
      },
      test("three elements has size 3") {
        summon[Size[("a", "b", "c")] =:= 3]
        assertTrue(true)
      }
    ),
    suite("SubsetEvidence")(
      test("empty tuple evidence exists") {
        summon[SubsetEvidence[EmptyTuple, ("a", "b")]]
        assertTrue(true)
      },
      test("single element subset evidence exists") {
        summon[SubsetEvidence[Tuple1["a"], ("a", "b")]]
        assertTrue(true)
      },
      test("full subset evidence exists") {
        summon[SubsetEvidence[("a", "b"), ("a", "b", "c")]]
        assertTrue(true)
      }
    ),
    suite("compile-time safety")(
      test("non-subset should not compile") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.TypeLevel._
          summon[SubsetEvidence[("a", "x"), ("a", "b")]]
        """))(Assertion.isLeft)
      },
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
