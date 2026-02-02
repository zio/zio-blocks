package zio.blocks.combinators

import zio.test._

object TuplesSpecExt extends ZIOSpecDefault {

  def spec = suite("Tuples - Precise output types")(
    test("combine tuple with value produces precise tuple type (arity 3)") {
      val combiner = implicitly[Tuples.Combiner.WithOut[(Int, String), Boolean, (Int, String, Boolean)]]
      val result: (Int, String, Boolean) = combiner.combine((1, "a"), true)
      assertTrue(result == (1, "a", true))
    },
    test("combine two non-tuple values produces precise pair type") {
      val combiner = implicitly[Tuples.Combiner.WithOut[Int, String, (Int, String)]]
      val result: (Int, String) = combiner.combine(1, "a")
      assertTrue(result == (1, "a"))
    }
  )
}
