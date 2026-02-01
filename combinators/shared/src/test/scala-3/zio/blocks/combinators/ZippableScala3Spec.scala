package zio.blocks.combinators

import zio.test._

object ZippableScala3Spec extends ZIOSpecDefault {

  def spec = suite("Zippable - Scala 3 only")(
    suite("TupleTupleCombiner equivalence (tuple + tuple flattening)")(
      test("zip two tuples flattens to single tuple") {
        val left     = (1, 2, 3)
        val right    = (4, 5)
        val zippable = implicitly[Zippable[(Int, Int, Int), (Int, Int)]]
        val zipped   = zippable.zip(left, right)
        assertTrue(zipped.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      },
      test("zippable and combiner both flatten tuples identically") {
        val left     = (1, 2, 3)
        val right    = (4, 5)
        val zippable = implicitly[Zippable[(Int, Int, Int), (Int, Int)]]
        val combiner = implicitly[Combiner[(Int, Int, Int), (Int, Int)]]

        val zipped   = zippable.zip(left, right)
        val combined = combiner.combine(left, right)

        assertTrue(zipped.asInstanceOf[Any] == combined.asInstanceOf[Any])
      }
    ),
    suite("EmptyTuple identity laws (Scala 3 only)")(
      test("zip(EmptyTuple, a) == a") {
        val a        = 42
        val zippable = implicitly[Zippable[EmptyTuple, Int]]
        val zipped   = zippable.zip(EmptyTuple, a)
        assertTrue(zipped.asInstanceOf[Any] == a)
      },
      test("zip(a, EmptyTuple) == a") {
        val a        = 42
        val zippable = implicitly[Zippable[Int, EmptyTuple]]
        val zipped   = zippable.zip(a, EmptyTuple)
        assertTrue(zipped.asInstanceOf[Any] == a)
      },
      test("EmptyTuple left zippable has discardsLeft flag") {
        val zippable = implicitly[Zippable[EmptyTuple, Int]]
        assertTrue(zippable.discardsLeft)
      },
      test("EmptyTuple right zippable has discardsRight flag") {
        val zippable = implicitly[Zippable[Int, EmptyTuple]]
        assertTrue(zippable.discardsRight)
      }
    ),
    suite("Large tuple combinations (Scala 3 unbounded arity)")(
      test("zip tuples of different sizes") {
        val a3       = (1, 2, 3)
        val a2       = (4, 5)
        val zippable = implicitly[Zippable[(Int, Int, Int), (Int, Int)]]
        val a5       = zippable.zip(a3, a2)
        assertTrue(a5.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      },
      test("complex nested zipping preserves all values") {
        val abc       = (1, 2, 3)
        val d         = 4
        val zippable1 = implicitly[Zippable[(Int, Int, Int), Int]]
        val abcd      = zippable1.zip(abc, d)

        val e         = 5
        val zippable2 = implicitly[Zippable[(Int, Int, Int, Int), Int]]
        val abcde     = zippable2.zip(abcd.asInstanceOf[(Int, Int, Int, Int)], e)

        assertTrue(abcde.asInstanceOf[Any] == (1, 2, 3, 4, 5))
      }
    )
  )
}
