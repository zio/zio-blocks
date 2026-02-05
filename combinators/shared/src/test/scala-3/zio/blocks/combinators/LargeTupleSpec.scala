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
        val t25a = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val t25b = (26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50)

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
    ),
    suite("Deep match type recursion (n=50)")(
      // These tests force Flatten[T] to recurse n times through h *: Flatten[t]
      // by appending a single element to an n-element tuple

      test("Flatten recursion depth 50: append to 50-tuple") {
        // Create a 50-element tuple, then combine with a single element
        // This forces Flatten to recurse 50 times on the left tuple
        val t50 = (
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
          31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50
        )

        // combine(t50, 51) computes Combined[T50, Int] = Flatten[T50 *: Int *: EmptyTuple]
        // Flatten must recurse 50 times through the left tuple
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
