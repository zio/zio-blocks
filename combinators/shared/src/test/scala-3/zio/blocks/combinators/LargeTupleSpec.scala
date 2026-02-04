package zio.blocks.combinators

import zio.test._

/**
 * Tests for large tuple support (45+ elements) to verify the Flatten match type
 * handles deep type-level recursion without hitting compiler limits.
 */
object LargeTupleSpec extends ZIOSpecDefault {
  def spec = suite("LargeTupleSpec")(
    suite("Large tuple combining")(
      test("combine to 45-element tuple") {
        // Start with a 22-tuple and combine with a 23-tuple to get 45
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val combined = Tuples.combine(t22, t23)

        assertTrue(combined.productArity == 45)
      },
      test("combine to 50-element tuple") {
        val t25a = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
          23, 24, 25)
        val t25b = (26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
          46, 47, 48, 49, 50)

        val combined = Tuples.combine(t25a, t25b)

        assertTrue(combined.productArity == 50)
      },
      test("flatten deeply nested tuple structure") {
        // ((a, b, c, d, e), (f, g, h, i, j), (k, l, m, n, o)) should flatten
        val nested = ((1, 2, 3, 4, 5), (6, 7, 8, 9, 10), (11, 12, 13, 14, 15))

        // This tests the Flatten match type recursion
        val result = Tuples.combine(Tuples.combine(nested._1, nested._2), nested._3)

        assertTrue(result.productArity == 15)
      },
      test("verify 45-element tuple values are correct") {
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val combined = Tuples.combine(t22, t23)

        // Verify first, middle, and last elements
        assertTrue(
          combined.productElement(0) == 1 &&
            combined.productElement(22) == 23 &&
            combined.productElement(44) == 45
        )
      },
      test("chained combines to build large tuple incrementally") {
        // Build a 30-element tuple by chaining combines
        val t10a = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val t10b = (11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val t10c = (21, 22, 23, 24, 25, 26, 27, 28, 29, 30)

        val step1 = Tuples.combine(t10a, t10b)
        val step2 = Tuples.combine(step1, t10c)

        assertTrue(step2.productArity == 30)
      }
    ),
    suite("Separator with large tuples")(
      test("separate 45-element tuple") {
        val t22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val t23 =
          (23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45)

        val combined        = Tuples.combine(t22, t23)
        val (init, lastVal) = Tuples.separate(combined)

        assertTrue(lastVal == 45 && init.productArity == 44)
      }
    )
  )
}
