package zio.blocks.combinators

import zio.test._

object CombinerScala3Spec extends ZIOSpecDefault {

  def spec = suite("Combiner - Scala 3 only")(
    suite("TupleTupleCombiner (tuple + tuple flattening)")(
      test("combine two tuples flattens to single tuple") {
        val left     = (1, 2, 3)
        val right    = (4, 5)
        val combiner = implicitly[Combiner[(Int, Int, Int), (Int, Int)]]
        val combined = combiner.combine(left, right)
        assertTrue(combined.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      },
      test("separate flattened tuples back to original components") {
        val combined      = (1, 2, 3, 4, 5)
        val combiner      = implicitly[Combiner[(Int, Int, Int), (Int, Int)]]
        val (left, right) = combiner.separate(combined.asInstanceOf[combiner.Out])
        assertTrue(left == (1, 2, 3) && right == (4, 5))
      },
      test("roundtrip: separate(combine(a, b)) == (a, b) for tuples") {
        val left     = (1, "a", true)
        val right    = (2.0, 'x')
        val combiner = implicitly[Combiner[(Int, String, Boolean), (Double, Char)]]
        val combined = combiner.combine(left, right)
        val (l, r)   = combiner.separate(combined)
        assertTrue(l == left && r == right)
      }
    ),
    suite("EmptyTuple identity laws (Scala 3 only)")(
      test("combine(EmptyTuple, a) == a") {
        val a        = 42
        val combiner = implicitly[Combiner[EmptyTuple, Int]]
        val combined = combiner.combine(EmptyTuple, a)
        assertTrue(combined.asInstanceOf[Any] == a)
      },
      test("combine(a, EmptyTuple) == a") {
        val a        = 42
        val combiner = implicitly[Combiner[Int, EmptyTuple]]
        val combined = combiner.combine(a, EmptyTuple)
        assertTrue(combined.asInstanceOf[Any] == a)
      },
      test("separate(combine((), a)) == ((), a) for EmptyTuple left") {
        val a        = "hello"
        val combiner = implicitly[Combiner[EmptyTuple, String]]
        val combined = combiner.combine(EmptyTuple, a)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == EmptyTuple && y == a)
      },
      test("separate(combine(a, ())) == (a, ()) for EmptyTuple right") {
        val a        = 42
        val combiner = implicitly[Combiner[Int, EmptyTuple]]
        val combined = combiner.combine(a, EmptyTuple)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == a && y == EmptyTuple)
      }
    ),
    suite("Large tuple combinations (Scala 3 unbounded arity)")(
      test("combine tuples of different sizes") {
        val a3       = (1, 2, 3)
        val a2       = (4, 5)
        val combiner = implicitly[Combiner[(Int, Int, Int), (Int, Int)]]
        val a5       = combiner.combine(a3, a2)
        assertTrue(a5.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      },
      test("complex nested combination preserves all values") {
        val abc       = (1, 2, 3)
        val d         = 4
        val combiner1 = implicitly[Combiner[(Int, Int, Int), Int]]
        val abcd      = combiner1.combine(abc, d)

        val e         = 5
        val combiner2 = implicitly[Combiner[(Int, Int, Int, Int), Int]]
        val abcde     = combiner2.combine(abcd.asInstanceOf[(Int, Int, Int, Int)], e)

        assertTrue(abcde.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      }
    )
  )
}
