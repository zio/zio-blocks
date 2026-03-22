/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.combinators

import zio.test._

object TuplesRecursiveFlatteningSpec extends ZIOSpecDefault {
  def spec = suite("Tuples Recursive Flattening")(
    suite("combine recursive flattening")(
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
    suite("separate")(
      test("separates flat 3-tuple correctly") {
        val t      = summon[Tuples.Tuples[(Int, String), Boolean]]
        val result = t.separate(t.combine((1, "a"), true))
        assertTrue(result == ((1, "a"), true))
      },
      test("separates 4-tuple correctly") {
        val t      = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
        val result = t.separate(t.combine((1, "a", true), 3.0))
        assertTrue(result == ((1, "a", true), 3.0))
      }
    ),
    suite("Round-trip consistency")(
      test("combine then separate preserves structure") {
        val t         = summon[Tuples.Tuples[(Int, String), Boolean]]
        val combined  = t.combine((1, "a"), true)
        val separated = t.separate(combined)
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
      test("Tuples.separate works via typeclass instance") {
        val t      = summon[Tuples.Tuples[(Int, String), Boolean]]
        val result = t.separate(t.combine((1, "a"), true))
        assertTrue(result == ((1, "a"), true))
      },
      test("Tuples.combine with nested tuples flattens") {
        val result = Tuples.combine(((1, "a"), true), (3.0, 'x'))
        assertTrue(result == (1, "a", true, 3.0, 'x'))
      },
      test("Tuples.separate canonicalizes nested input") {
        val tc     = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
        val result = tc.separate(tc.combine((1, "a", true), 3.0))
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
        val t            = summon[Tuples.Tuples[(Int, String), Boolean]]
        val (init, last) = t.separate(t.combine((42, "hello"), true))
        assertTrue(last == true && init == (42, "hello"))
      },
      test("roundtrip with inferred types") {
        val t            = summon[Tuples.Tuples[Int, String]]
        val combined     = t.combine(1, "two")
        val (init, last) = t.separate(combined)
        assertTrue(last == "two" && init == 1)
      },
      test("generic function using Tuples") {
        def pair[L, R](l: L, r: R)(using c: Tuples.Tuples[L, R]): c.Out =
          c.combine(l, r)

        val result = pair(true, 3.14)
        assertTrue(result == (true, 3.14))
      },
      test("generic function using Tuples for separate") {
        val t        = summon[Tuples.Tuples[(Int, String), Boolean]]
        val combined = t.combine((1, "a"), true)
        val result   = t.separate(combined)
        assertTrue(result == ((1, "a"), true))
      },
      test("chained operations with inference - building larger tuples") {
        val step1 = Tuples.combine(1, "a")
        val step2 = Tuples.combine(step1, true)
        val step3 = Tuples.combine(step2, 3.14)
        assertTrue(step3 == (1, "a", true, 3.14))
      },
      test("chained separations - decomposing tuples") {
        val t4             = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
        val t3             = summon[Tuples.Tuples[(Int, String), Boolean]]
        val t2             = summon[Tuples.Tuples[Int, String]]
        val tuple          = t4.combine(t3.combine(t2.combine(1, "a"), true), 3.14)
        val (rest1, last1) = t4.separate(tuple)
        val (rest2, last2) = t3.separate(rest1)
        val (rest3, last3) = t2.separate(rest2)
        assertTrue(last1 == 3.14 && last2 == true && last3 == "a")
      },
      test("Tuples in higher-order function") {
        def zipWith[A, B, C](a: A, b: B, f: (A, B) => C)(using Tuples.Tuples[A, B]): C =
          f(a, b)

        val result = zipWith(1, 2, _ + _)
        assertTrue(result == 3)
      }
    )
  )
}
