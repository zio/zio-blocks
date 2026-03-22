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

object TuplesSpec extends ZIOSpecDefault {

  def spec = suite("Tuples")(
    suite("combine")(
      suite("Unit identity")(
        test("combine((), a) returns a") {
          val result = Tuples.combine((), 42)
          assertTrue(result == 42)
        },
        test("combine(a, ()) returns a") {
          val result = Tuples.combine("hello", ())
          assertTrue(result == "hello")
        }
      ),
      suite("Pair creation")(
        test("combine two non-tuple values into pair") {
          val result = Tuples.combine(1, "a")
          assertTrue(result == (1, "a"))
        }
      ),
      suite("Tuple flattening")(
        test("combine tuple with value appends to tuple (arity 3)") {
          val result = Tuples.combine((1, "a"), true)
          assertTrue(result == (1, "a", true))
        },
        test("combine tuple with value (arity 5)") {
          val result = Tuples.combine((1, "a", true, 2.0), 'x')
          assertTrue(result == (1, "a", true, 2.0, 'x'))
        },
        test("combine tuple with value (arity 10)") {
          val result = Tuples.combine((1, 2, 3, 4, 5, 6, 7, 8, 9), 10)
          assertTrue(result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        },
        test("combine tuple with value (arity 15)") {
          val result = Tuples.combine((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), 15)
          assertTrue(result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
        },
        test("combine tuple with value (arity 22)") {
          val result =
            Tuples.combine((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21), 22)
          assertTrue(
            result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
          )
        }
      )
    ),
    suite("separate")(
      test("separate 3-tuple peels last element") {
        val t      = implicitly[Tuples.Tuples[(Int, String), Boolean]]
        val result = t.separate(t.combine((1, "a"), true))
        assertTrue(result == ((1, "a"), true))
      },
      test("separate 5-tuple peels last element") {
        val t      = implicitly[Tuples.Tuples[(Int, String, Boolean, Double), Char]]
        val result = t.separate(t.combine((1, "a", true, 2.0), 'x'))
        assertTrue(result == ((1, "a", true, 2.0), 'x'))
      },
      test("separate 10-tuple peels last element") {
        val t      = implicitly[Tuples.Tuples[(Int, Int, Int, Int, Int, Int, Int, Int, Int), Int]]
        val result = t.separate(t.combine((1, 2, 3, 4, 5, 6, 7, 8, 9), 10))
        assertTrue(result == ((1, 2, 3, 4, 5, 6, 7, 8, 9), 10))
      }
    ),
    suite("Roundtrip")(
      test("roundtrip with tuple flattening (arity 3)") {
        val t         = implicitly[Tuples.Tuples[(Int, String), Boolean]]
        val combined  = t.combine((1, "a"), true)
        val separated = t.separate(combined)
        assertTrue(separated == ((1, "a"), true))
      },
      test("roundtrip with tuple flattening (arity 5)") {
        val t         = implicitly[Tuples.Tuples[(Int, String, Boolean, Double), Char]]
        val combined  = t.combine((1, "a", true, 2.0), 'x')
        val separated = t.separate(combined)
        assertTrue(separated == ((1, "a", true, 2.0), 'x'))
      },
      test("roundtrip with tuple flattening (arity 10)") {
        val t         = implicitly[Tuples.Tuples[(Int, Int, Int, Int, Int, Int, Int, Int, Int), Int]]
        val combined  = t.combine((1, 2, 3, 4, 5, 6, 7, 8, 9), 10)
        val separated = t.separate(combined)
        assertTrue(separated == ((1, 2, 3, 4, 5, 6, 7, 8, 9), 10))
      }
    ),
    suite("Precise output types")(
      test("combine tuple with value produces correct result (arity 3)") {
        val result = Tuples.combine((1, "a"), true)
        assertTrue(result == (1, "a", true))
      },
      test("combine two non-tuple values produces pair") {
        val result = Tuples.combine(1, "a")
        assertTrue(result == (1, "a"))
      }
    )
  )
}
