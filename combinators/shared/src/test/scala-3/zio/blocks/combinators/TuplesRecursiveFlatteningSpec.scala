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
    ),
    suite("Top-level convenience methods")(
      test("Tuples.combine works without explicit combiner") {
        val result = Tuples.combine((1, "a"), true)
        assertTrue(result == (1, "a", true))
      },
      test("Tuples.separate works without explicit separator") {
        val result = Tuples.separate((1, "a", true))
        assertTrue(result == ((1, "a"), true))
      },
      test("Tuples.combine with nested tuples flattens") {
        val result = Tuples.combine(((1, "a"), true), (3.0, 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("Tuples.separate canonicalizes nested input") {
        val result = Tuples.separate(((1, "a"), (true, 3.0)))
        assertTrue(result == ((1, "a", true), 3.0))
      }
    ),
    suite("Real-world API usage with type inference")(
      test("combine two values without type annotations") {
        val a    = 42
        val b    = "hello"
        val tuple = Tuples.combine(a, b)
        assertTrue(tuple == (42, "hello"))
      },
      test("separate tuple without type annotations - 3-tuple") {
        val tuple = (42, "hello", true)
        val (init, last) = Tuples.separate(tuple)
        assertTrue(last == true && init == (42, "hello"))
      },
      test("roundtrip with inferred types") {
        val a       = 1
        val b       = "two"
        val combined = Tuples.combine(a, b)
        val (init, last) = Tuples.separate(combined)
        assertTrue(last == "two" && init == Tuple1(1))
      },
      test("generic function using Combiner") {
        def pair[L, R](l: L, r: R)(using c: Tuples.Combiner[L, R]): c.Out =
          Tuples.combine(l, r)

        val result = pair(true, 3.14)
        assertTrue(result == (true, 3.14))
      },
      test("generic function using Separator") {
        def unpair[A](a: A)(using s: Tuples.Separator[A]): (s.Left, s.Right) =
          Tuples.separate(a)

        val result = unpair((1, "a", true))
        assertTrue(result == ((1, "a"), true))
      },
      test("chained operations with inference - building larger tuples") {
        val step1 = Tuples.combine(1, "a")       // (1, "a")
        val step2 = Tuples.combine(step1, true)  // (1, "a", true) - flattened!
        val step3 = Tuples.combine(step2, 3.14)  // (1, "a", true, 3.14)
        assertTrue(step3 == (1, "a", true, 3.14))
      },
      test("chained separations - decomposing tuples") {
        val tuple = (1, "a", true, 3.14)
        val (rest1, last1) = Tuples.separate(tuple)
        val (rest2, last2) = Tuples.separate(rest1)
        val (rest3, last3) = Tuples.separate(rest2)
        assertTrue(last1 == 3.14 && last2 == true && last3 == "a")
      },
      test("Combiner in higher-order function") {
        def zipWith[A, B, C](a: A, b: B, f: (A, B) => C)(using Tuples.Combiner[A, B]): C =
          f(a, b)

        val result = zipWith(1, 2, _ + _)
        assertTrue(result == 3)
      }
    )
  )
}
