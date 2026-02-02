package zio.blocks.combinators

import zio.test._

object TuplesSpec extends ZIOSpecDefault {

  def spec = suite("Tuples")(
    suite("Combiner")(
      suite("Unit identity")(
        test("combine((), a) returns a") {
          val combiner    = implicitly[Tuples.Combiner[Unit, Int]]
          val result: Any = combiner.combine((), 42)
          assertTrue(result == 42)
        },
        test("combine(a, ()) returns a") {
          val combiner    = implicitly[Tuples.Combiner[String, Unit]]
          val result: Any = combiner.combine("hello", ())
          assertTrue(result == "hello")
        }
      ),
      suite("Pair creation")(
        test("combine two non-tuple values into pair") {
          val combiner    = implicitly[Tuples.Combiner[Int, String]]
          val result: Any = combiner.combine(1, "a")
          assertTrue(result == (1, "a"))
        }
      ),
      suite("Tuple flattening")(
        test("combine tuple with value appends to tuple (arity 3)") {
          val combiner    = implicitly[Tuples.Combiner[(Int, String), Boolean]]
          val result: Any = combiner.combine((1, "a"), true)
          assertTrue(result == (1, "a", true))
        },
        test("combine tuple with value (arity 5)") {
          val combiner    = implicitly[Tuples.Combiner[(Int, String, Boolean, Double), Char]]
          val result: Any = combiner.combine((1, "a", true, 2.0), 'x')
          assertTrue(result == (1, "a", true, 2.0, 'x'))
        },
        test("combine tuple with value (arity 10)") {
          val combiner = implicitly[Tuples.Combiner[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int),
            Int
          ]]
          val result: Any = combiner.combine((1, 2, 3, 4, 5, 6, 7, 8, 9), 10)
          assertTrue(result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        },
        test("combine tuple with value (arity 15)") {
          val combiner = implicitly[Tuples.Combiner[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
            Int
          ]]
          val result: Any = combiner.combine((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), 15)
          assertTrue(result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
        },
        test("combine tuple with value (arity 22)") {
          val combiner = implicitly[Tuples.Combiner[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
            Int
          ]]
          val result: Any =
            combiner.combine((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21), 22)
          assertTrue(result == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22))
        }
      )
    ),
    suite("Separator")(
      suite("Unit identity")(
        test("separate returns ((), a) for left-unit separator") {
          val separator = implicitly[Tuples.Separator.WithTypes[Int, Unit, Int]]
          val result    = separator.separate(42)
          assertTrue(result == ((), 42))
        },
        test("separate returns (a, ()) for right-unit separator") {
          val separator = implicitly[Tuples.Separator.WithTypes[String, String, Unit]]
          val result    = separator.separate("hello")
          assertTrue(result == ("hello", ()))
        }
      ),
      suite("Pair separation")(
        test("separate pair into values") {
          val separator = implicitly[Tuples.Separator.WithTypes[(Int, String), Int, String]]
          val result    = separator.separate((1, "a"))
          assertTrue(result == (1, "a"))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(a, b)) == (a, b) for non-tuple values") {
        val combiner      = implicitly[Tuples.Combiner[Int, String]]
        val separator     = implicitly[Tuples.Separator.WithTypes[(Int, String), Int, String]]
        val combined: Any = combiner.combine(1, "a")
        val separated     = separator.separate(combined.asInstanceOf[(Int, String)])
        assertTrue(separated == (1, "a"))
      },
      test("roundtrip with Unit identity") {
        val combiner      = implicitly[Tuples.Combiner[Unit, Int]]
        val separator     = implicitly[Tuples.Separator.WithTypes[Int, Unit, Int]]
        val combined: Any = combiner.combine((), 42)
        val separated     = separator.separate(combined.asInstanceOf[Int])
        assertTrue(separated == ((), 42))
      }
    )
  )
}
