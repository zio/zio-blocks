package zio.blocks.combinators

import zio.test._

object TuplesSpecExt extends ZIOSpecDefault {

  def spec = suite("Tuples - Precise output types")(
    test("combine two non-tuple values produces precise pair type via WithOut refinement") {
      val combiner: Tuples.Combiner.WithOut[Int, String, (Int, String)] =
        summon[Tuples.Combiner[Int, String]]
      val result: (Int, String) = combiner.combine(1, "a")
      assertTrue(result == (1, "a"))
    }
  )
}
