package zio.blocks.combinators

import zio.test._

object TuplesRecursiveFlatteningSpec extends ZIOSpecDefault {
  def spec = suite("Tuples Recursive Flattening")(
    suite("Combiner recursive flattening")(
      test("flattens left-nested tuple with right tuple") {
        // Input: ((Int, String), Boolean) with (Double, Char)
        // Expected: (Int, String, Boolean, Double, Char)
        val combiner    = summon[Tuples.Combiner[((Int, String), Boolean), (Double, Char)]]
        val result: Any = combiner.combine(((1, "a"), true), (3.0, 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("flattens right-nested tuple with left tuple") {
        // Input: (Int, String) with ((Boolean, Double), Char)
        // Expected: (Int, String, Boolean, Double, Char)
        val combiner    = summon[Tuples.Combiner[(Int, String), ((Boolean, Double), Char)]]
        val result: Any = combiner.combine((1, "a"), ((true, 3.0), 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("flattens both-sided nested tuples") {
        // Input: ((Int, String), Boolean) with ((Double, Char), Long)
        // Expected: (Int, String, Boolean, Double, Char, Long)
        val combiner    = summon[Tuples.Combiner[((Int, String), Boolean), ((Double, Char), Long)]]
        val result: Any = combiner.combine(((1, "a"), true), ((3.0, 'x'), 99L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 99L))
      },
      test("flattens deeply nested tuples - left side") {
        // When both sides are tuples, flattens fully
        val combiner    = summon[Tuples.Combiner[(((Int, String), Boolean), Double), (Char, Long)]]
        val result: Any = combiner.combine((((1, "a"), true), 3.0), ('x', 42L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 42L))
      },
      test("flattens deeply nested tuples - right side") {
        // When both sides are tuples, flattens fully
        val combiner    = summon[Tuples.Combiner[(Int, String), (((Boolean, Double), Char), Long)]]
        val result: Any = combiner.combine((1, "a"), (((true, 3.0), 'x'), 42L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 42L))
      },
      test("idempotent on already flat tuples") {
        // Combining flat tuples should still work correctly
        val combiner    = summon[Tuples.Combiner[(Int, String), (Boolean, Double)]]
        val result: Any = combiner.combine((1, "a"), (true, 3.0))
        assertTrue(result == (1, "a", true, 3.0))
      },
      test("flattens tuple with value") {
        // Input: (Int, String) with Boolean
        // Expected: (Int, String, Boolean)
        val combiner    = summon[Tuples.Combiner[(Int, String), Boolean]]
        val result: Any = combiner.combine((1, "a"), true)
        assertTrue(result == (1, "a", true))
      },
      test("flattens value with tuple") {
        // Input: Int with (String, Boolean)
        // Expected: (Int, String, Boolean)
        val combiner    = summon[Tuples.Combiner[Int, (String, Boolean)]]
        val result: Any = combiner.combine(1, ("a", true))
        assertTrue(result == (1, "a", true))
      }
    ),
    suite("Separator with explicit types")(
      test("separates flat 2-tuple correctly") {
        // Input: (Int, String)
        // Separates to: (Int, String)
        val separator = implicitly[Tuples.Separator.WithTypes[(Int, String), Int, String]]
        val result    = separator.separate((1, "a"))
        assertTrue(result == (1, "a"))
      },
      test("separates unit-pair correctly") {
        // Input: String
        // Separates to: (Unit, String)
        val separator = implicitly[Tuples.Separator.WithTypes[String, Unit, String]]
        val result    = separator.separate("hello")
        assertTrue(result == ((), "hello"))
      }
    ),
    suite("Round-trip consistency")(
      test("combine preserves values through multiple operations") {
        // Build up complex nested tuple and verify it flattens
        val combiner1      = implicitly[Tuples.Combiner[Int, String]]
        val combined1: Any = combiner1.combine(1, "a")
        val combiner2      = implicitly[Tuples.Combiner[(Int, String), Boolean]]
        val combined2: Any = combiner2.combine(combined1.asInstanceOf[(Int, String)], true)
        assertTrue(combined2 == (1, "a", true))
      }
    ),
    suite("Type precision with Combiner.WithOut")(
      test("combine result type is precisely flattened") {
        // This test verifies that the result type matches Combined[L, R]
        val combiner
          : Tuples.Combiner.WithOut[((Int, String), Boolean), (Double, Char), (Int, String, Boolean, Double, Char)] =
          summon[Tuples.Combiner[((Int, String), Boolean), (Double, Char)]]
        val result: (Int, String, Boolean, Double, Char) = combiner.combine(((1, "a"), true), (3.0, 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      }
    )
  )
}
