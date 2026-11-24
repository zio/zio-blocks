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

object LargeTupleSpec extends ZIOSpecDefault {
  def spec = suite("LargeTupleSpec")(
    suite("Large tuple combining")(
      test("combine to 45-element tuple") {
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val combined = Tuples.combine(t22, t23)

        assertTrue(combined.productArity == 45)
      },
      test("combine to 50-element tuple") {
        val t25a = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val t25b = (26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50)

        val combined = Tuples.combine(t25a, t25b)

        assertTrue(combined.productArity == 50)
      },
      test("flatten deeply nested tuple structure") {
        val nested = ((1, 2, 3, 4, 5), (6, 7, 8, 9, 10), (11, 12, 13, 14, 15))

        val result = Tuples.combine(Tuples.combine(nested._1, nested._2), nested._3)

        assertTrue(result.productArity == 15)
      },
      test("verify 45-element tuple values are correct") {
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val combined = Tuples.combine(t22, t23)

        assertTrue(
          combined.productElement(0) == 1 &&
            combined.productElement(22) == 23 &&
            combined.productElement(44) == 45
        )
      },
      test("chained combines to build large tuple incrementally") {
        val t10a = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val t10b = (11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val t10c = (21, 22, 23, 24, 25, 26, 27, 28, 29, 30)

        val step1 = Tuples.combine(t10a, t10b)
        val step2 = Tuples.combine(step1, t10c)

        assertTrue(step2.productArity == 30)
      }
    ),
    suite("separate with large tuples")(
      test("separate 45-element tuple") {
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val t = summon[Tuples.Tuples[
          (
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int
          ),
          (
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int
          )
        ]]
        val combined        = t.combine(t22, t23)
        val (init, lastVal) = t.separate(combined)

        assertTrue(lastVal.productArity == 23 && init.productArity == 22)
      }
    ),
    suite("Deep match type recursion (n=50)")(
      test("Flatten recursion depth 50: append to 50-tuple") {
        val t50 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50
        )

        val combined = Tuples.combine(t50, 51)

        assertTrue(
          combined.productArity == 51 &&
            combined.productElement(0) == 1 &&
            combined.productElement(49) == 50 &&
            combined.productElement(50) == 51
        )
      },

      test("Flatten recursion depth 60: append to 60-tuple") {
        val t60 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57,
          58, 59, 60
        )

        val combined = Tuples.combine(t60, 61)

        assertTrue(
          combined.productArity == 61 &&
            combined.productElement(0) == 1 &&
            combined.productElement(59) == 60 &&
            combined.productElement(60) == 61
        )
      },

      test("Flatten recursion depth 80: append to 80-tuple") {
        val t80 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57,
          58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80
        )

        val combined = Tuples.combine(t80, 81)

        assertTrue(
          combined.productArity == 81 &&
            combined.productElement(0) == 1 &&
            combined.productElement(79) == 80 &&
            combined.productElement(80) == 81
        )
      },

      test("Flatten recursion depth 100: append to 100-tuple") {
        val t100 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57,
          58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84,
          85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100
        )

        val combined = Tuples.combine(t100, 101)

        assertTrue(
          combined.productArity == 101 &&
            combined.productElement(0) == 1 &&
            combined.productElement(99) == 100 &&
            combined.productElement(100) == 101
        )
      }
    )
  )
}
