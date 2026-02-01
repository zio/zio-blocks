package zio.blocks.combinators

import zio.test._

object CombinerSpec extends ZIOSpecDefault {

  def spec = suite("Combiner")(
    suite("Unit identity laws")(
      test("left unit: combine((), a) == a") {
        val a        = 42
        val combiner = implicitly[Combiner[Unit, Int]]
        val combined = combiner.combine((), a)
        assertTrue(combined.asInstanceOf[Any] == a)
      },
      test("right unit: combine(a, ()) == a") {
        val a        = 42
        val combiner = implicitly[Combiner[Int, Unit]]
        val combined = combiner.combine(a, ())
        assertTrue(combined.asInstanceOf[Any] == a)
      }
    ),
    suite("Roundtrip laws")(
      test("separate(combine(a, b)) == (a, b) for primitives") {
        val a        = 1
        val b        = "hello"
        val combiner = implicitly[Combiner[Int, String]]
        val combined = combiner.combine(a, b)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == a && y == b)
      },
      test("separate(combine(a, b)) == (a, b) for Unit left") {
        val a        = ()
        val b        = "hello"
        val combiner = implicitly[Combiner[Unit, String]]
        val combined = combiner.combine(a, b)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == a && y == b)
      },
      test("separate(combine(a, b)) == (a, b) for Unit right") {
        val a        = 42
        val b        = ()
        val combiner = implicitly[Combiner[Int, Unit]]
        val combined = combiner.combine(a, b)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == a && y == b)
      },
      test("separate(combine(a, b)) == (a, b) for tuples") {
        val a        = (1, "hello")
        val b        = true
        val combiner = implicitly[Combiner[(Int, String), Boolean]]
        val combined = combiner.combine(a, b)
        val (x, y)   = combiner.separate(combined)
        assertTrue(x == a && y == b)
      }
    ),
    suite("Tuple flattening for arity 2")(
      test("combine(a, b) == (a, b)") {
        val a        = 1
        val b        = "hello"
        val combiner = implicitly[Combiner[Int, String]]
        val result   = combiner.combine(a, b)
        assertTrue(result.asInstanceOf[Any] == (1, "hello"))
      }
    ),
    suite("Tuple flattening for arity 3")(
      test("combine((a, b), c) == (a, b, c)") {
        val ab       = (1, "hello")
        val c        = true
        val combiner = implicitly[Combiner[(Int, String), Boolean]]
        val result   = combiner.combine(ab, c)
        assertTrue(result.asInstanceOf[Any] == (1, "hello", true))
      },
      test("separate((a, b, c)) == ((a, b), c)") {
        val abc      = (1, "hello", true)
        val combiner = implicitly[Combiner[(Int, String), Boolean]]
        val (ab, c)  = combiner.separate(abc.asInstanceOf[combiner.Out])
        assertTrue(ab == (1, "hello") && c == true)
      }
    ),
    suite("Tuple flattening for arity 5")(
      test("combine((a, b, c, d), e) == (a, b, c, d, e)") {
        val abcd     = (1, "hello", true, 3.14)
        val e        = 'x'
        val combiner = implicitly[Combiner[(Int, String, Boolean, Double), Char]]
        val result   = combiner.combine(abcd, e)
        assertTrue(result.asInstanceOf[Any] == (1, "hello", true, 3.14, 'x'))
      },
      test("separate((a, b, c, d, e)) == ((a, b, c, d), e)") {
        val abcde     = (1, "hello", true, 3.14, 'x')
        val combiner  = implicitly[Combiner[(Int, String, Boolean, Double), Char]]
        val (abcd, e) = combiner.separate(abcde.asInstanceOf[combiner.Out])
        assertTrue(abcd == (1, "hello", true, 3.14) && e == 'x')
      }
    ),
    suite("Tuple flattening for arity 10")(
      test("combine((a1,...,a9), a10) == (a1,...,a10)") {
        val a9       = (1, 2, 3, 4, 5, 6, 7, 8, 9)
        val a10      = 10
        val combiner = implicitly[Combiner[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = combiner.combine(a9, a10)
        assertTrue(result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
      },
      test("separate((a1,...,a10)) == ((a1,...,a9), a10)") {
        val a10      = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val combiner = implicitly[Combiner[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val (a9, a10val) = combiner.separate(a10.asInstanceOf[combiner.Out])
        assertTrue(a9 == (1, 2, 3, 4, 5, 6, 7, 8, 9) && a10val == 10)
      }
    ),
    suite("Type inference")(
      test("combiner instance exists for Int and String") {
        val combiner = implicitly[Combiner[Int, String]]
        assertTrue(combiner != null)
      },
      test("combiner instance exists for Unit and Int") {
        val combiner = implicitly[Combiner[Unit, Int]]
        assertTrue(combiner != null)
      },
      test("combiner instance exists for (Int, String) and Boolean") {
        val combiner = implicitly[Combiner[(Int, String), Boolean]]
        assertTrue(combiner != null)
      }
    ),
    suite("Complex tuple combinations")(
      test("combine nested tuples: ((a, b), c) with d") {
        val ab       = (1, "x")
        val c        = true
        val abc      = (ab._1, ab._2, c)
        val d        = 3.14
        val combiner = implicitly[Combiner[(Int, String, Boolean), Double]]
        val result   = combiner.combine(abc, d)
        assertTrue(result.asInstanceOf[Any] == (1, "x", true, 3.14))
      },
      test("roundtrip with multiple levels of nesting") {
        val initial       = ((1, 2), (3, 4))
        val combiner      = implicitly[Combiner[(Int, Int), (Int, Int)]]
        val combined      = combiner.combine(initial._1, initial._2)
        val (left, right) = combiner.separate(combined)
        assertTrue(left == (1, 2) && right == (3, 4))
      }
    ),
    suite("Edge cases")(
      test("combine with Unit preserves types") {
        val a                    = (1, "hello")
        val combiner             = implicitly[Combiner[(Int, String), Unit]]
        val result: combiner.Out = combiner.combine(a, ())
        assertTrue(result.asInstanceOf[Any] == (1, "hello"))
      },
      test("multiple unit combinations") {
        val combiner2 = implicitly[Combiner[Unit, Int]]
        val combiner3 = implicitly[Combiner[Int, Unit]]

        val r2: combiner2.Out = combiner2.combine((), 42)
        val r3: combiner3.Out = combiner3.combine(42, ())

        assertTrue(r2.asInstanceOf[Any] == 42 && r3.asInstanceOf[Any] == 42)
      }
    )
  )
}
