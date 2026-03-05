package zio.blocks.combinators

import zio.test._

object TuplesRecursiveFlatteningSpec extends ZIOSpecDefault {
  def spec = suite("Tuples Recursive Flattening")(
    suite("Combiner recursive flattening")(
      test("flattens left-nested tuple with right tuple") {
        val result = Tuples.combine(((1, "a"), true), (3.0, 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("flattens right-nested tuple with left tuple") {
        val result = Tuples.combine((1, "a"), ((true, 3.0), 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("flattens both-sided nested tuples") {
        val result = Tuples.combine(((1, "a"), true), ((3.0, 'x'), 99L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 99L))
      },
      test("flattens deeply nested tuples - left side") {
        val result = Tuples.combine((((1, "a"), true), 3.0), ('x', 42L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 42L))
      },
      test("flattens deeply nested tuples - right side") {
        val result = Tuples.combine((1, "a"), (((true, 3.0), 'x'), 42L))
        assertTrue(result == (1, "a", true, 3.0, 'x', 42L))
      },
      test("idempotent on already flat tuples") {
        val result = Tuples.combine((1, "a"), (true, 3.0))
        assertTrue(result == (1, "a", true, 3.0))
      },
      test("flattens tuple with value") {
        val result = Tuples.combine((1, "a"), true)
        assertTrue(result == (1, "a", true))
      },
      test("flattens value with tuple") {
        val result = Tuples.combine(1, ("a", true))
        assertTrue(result == (1, "a", true))
      }
    ),
    suite("Separator")(
      test("separates flat 3-tuple correctly") {
        val result = Tuples.separate((1, "a", true))
        assertTrue(result == ((1, "a"), true))
      },
      test("separates 4-tuple correctly") {
        val result = Tuples.separate((1, "a", true, 3.0))
        assertTrue(result == ((1, "a", true), 3.0))
      }
    ),
    suite("Round-trip consistency")(
      test("combine then separate preserves structure") {
        val combined  = Tuples.combine((1, "a"), true)
        val separated = Tuples.separate(combined)
        assertTrue(separated == ((1, "a"), true))
      }
    ),
    suite("Type precision")(
      test("combine result type is precisely flattened") {
        val result = Tuples.combine(((1, "a"), true), (3.0, 'x'))
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
        val a     = 42
        val b     = "hello"
        val tuple = Tuples.combine(a, b)
        assertTrue(tuple == (42, "hello"))
      },
      test("separate tuple without type annotations - 3-tuple") {
        val tuple        = (42, "hello", true)
        val (init, last) = Tuples.separate(tuple)
        assertTrue(last == true && init == (42, "hello"))
      },
      test("roundtrip with inferred types") {
        val a            = 1
        val b            = "two"
        val combined     = Tuples.combine(a, b)
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
        val step1 = Tuples.combine(1, "a")      // (1, "a")
        val step2 = Tuples.combine(step1, true) // (1, "a", true) - flattened!
        val step3 = Tuples.combine(step2, 3.14) // (1, "a", true, 3.14)
        assertTrue(step3 == (1, "a", true, 3.14))
      },
      test("chained separations - decomposing tuples") {
        val tuple          = (1, "a", true, 3.14)
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
