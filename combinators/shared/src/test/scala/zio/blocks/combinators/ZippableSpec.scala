package zio.blocks.combinators

import zio.test._

object ZippableSpec extends ZIOSpecDefault {

  def spec = suite("Zippable")(
    suite("Unit identity laws")(
      test("left unit: zip((), a) == a") {
        val a        = 42
        val zippable = implicitly[Zippable[Unit, Int]]
        val zipped   = zippable.zip((), a)
        assertTrue(zipped.asInstanceOf[Any] == a)
      },
      test("right unit: zip(a, ()) == a") {
        val a        = 42
        val zippable = implicitly[Zippable[Int, Unit]]
        val zipped   = zippable.zip(a, ())
        assertTrue(zipped.asInstanceOf[Any] == a)
      }
    ),
    suite("discardsLeft flag")(
      test("left unit zippable discards left") {
        val zippable = implicitly[Zippable[Unit, Int]]
        assertTrue(zippable.discardsLeft)
      },
      test("right unit zippable does not discard left") {
        val zippable = implicitly[Zippable[Int, Unit]]
        assertTrue(!zippable.discardsLeft)
      },
      test("fallback zippable does not discard left") {
        val zippable = implicitly[Zippable[Int, String]]
        assertTrue(!zippable.discardsLeft)
      },
      test("tuple zippable does not discard left") {
        val zippable = implicitly[Zippable[(Int, String), Boolean]]
        assertTrue(!zippable.discardsLeft)
      }
    ),
    suite("discardsRight flag")(
      test("right unit zippable discards right") {
        val zippable = implicitly[Zippable[Int, Unit]]
        assertTrue(zippable.discardsRight)
      },
      test("left unit zippable does not discard right") {
        val zippable = implicitly[Zippable[Unit, Int]]
        assertTrue(!zippable.discardsRight)
      },
      test("fallback zippable does not discard right") {
        val zippable = implicitly[Zippable[Int, String]]
        assertTrue(!zippable.discardsRight)
      },
      test("tuple zippable does not discard right") {
        val zippable = implicitly[Zippable[(Int, String), Boolean]]
        assertTrue(!zippable.discardsRight)
      }
    ),
    suite("Tuple flattening for arity 2")(
      test("zip(a, b) == (a, b)") {
        val a        = 1
        val b        = "hello"
        val zippable = implicitly[Zippable[Int, String]]
        val result   = zippable.zip(a, b)
        assertTrue(result.asInstanceOf[Any] == (1, "hello"))
      }
    ),
    suite("Tuple flattening for arity 3")(
      test("zip((a, b), c) == (a, b, c)") {
        val ab       = (1, "hello")
        val c        = true
        val zippable = implicitly[Zippable[(Int, String), Boolean]]
        val result   = zippable.zip(ab, c)
        assertTrue(result.asInstanceOf[Any] == (1, "hello", true))
      }
    ),
    suite("Tuple flattening for arity 5")(
      test("zip((a, b, c, d), e) == (a, b, c, d, e)") {
        val abcd     = (1, "hello", true, 3.14)
        val e        = 'x'
        val zippable = implicitly[Zippable[(Int, String, Boolean, Double), Char]]
        val result   = zippable.zip(abcd, e)
        assertTrue(result.asInstanceOf[Any] == (1, "hello", true, 3.14, 'x'))
      }
    ),
    suite("Tuple flattening for arity 10")(
      test("zip((a1,...,a9), a10) == (a1,...,a10)") {
        val a9       = (1, 2, 3, 4, 5, 6, 7, 8, 9)
        val a10      = 10
        val zippable = implicitly[Zippable[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = zippable.zip(a9, a10)
        assertTrue(result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
      }
    ),
    suite("Tuple flattening for arity 11")(
      test("zip((a1,...,a10), a11) == (a1,...,a11)") {
        val a10      = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val a11      = 11
        val zippable = implicitly[Zippable[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = zippable.zip(a10, a11)
        assertTrue(result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
      }
    ),
    suite("Tuple flattening for arity 15")(
      test("zip((a1,...,a14), a15) == (a1,...,a15)") {
        val a14      = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        val a15      = 15
        val zippable = implicitly[Zippable[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = zippable.zip(a14, a15)
        assertTrue(result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
      }
    ),
    suite("Tuple flattening for arity 20")(
      test("zip((a1,...,a19), a20) == (a1,...,a20)") {
        val a19      = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
        val a20      = 20
        val zippable = implicitly[Zippable[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = zippable.zip(a19, a20)
        assertTrue(result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20))
      }
    ),
    suite("Tuple flattening for arity 22 (max Scala 2 arity)")(
      test("zip((a1,...,a21), a22) == (a1,...,a22)") {
        val a21      = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
        val a22      = 22
        val zippable = implicitly[Zippable[
          (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int),
          Int
        ]]
        val result = zippable.zip(a21, a22)
        assertTrue(
          result.asInstanceOf[Any] == (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        )
      }
    ),
    suite("Type inference")(
      test("zippable instance exists for Int and String") {
        val zippable = implicitly[Zippable[Int, String]]
        assertTrue(zippable != null)
      },
      test("zippable instance exists for Unit and Int") {
        val zippable = implicitly[Zippable[Unit, Int]]
        assertTrue(zippable != null)
      },
      test("zippable instance exists for (Int, String) and Boolean") {
        val zippable = implicitly[Zippable[(Int, String), Boolean]]
        assertTrue(zippable != null)
      }
    ),
    suite("Complex tuple combinations")(
      test("zip nested tuples: ((a, b), c) with d") {
        val ab       = (1, "x")
        val c        = true
        val abc      = (ab._1, ab._2, c)
        val d        = 3.14
        val zippable = implicitly[Zippable[(Int, String, Boolean), Double]]
        val result   = zippable.zip(abc, d)
        assertTrue(result.asInstanceOf[Any] == (1, "x", true, 3.14))
      },
      test("zip tuples with tuples produces a tuple") {
        val left     = (1, 2)
        val right    = (3, 4)
        val zippable = implicitly[Zippable[(Int, Int), (Int, Int)]]
        val result   = zippable.zip(left, right)
        // Scala 3 fully flattens: (1, 2, 3, 4)
        // Scala 2 partially flattens: (1, 2, (3, 4))
        // Both are valid - just verify it's a tuple containing all values
        val resultAny = result.asInstanceOf[Any]
        assertTrue(
          resultAny == (1, 2, 3, 4) || resultAny == (1, 2, (3, 4))
        )
      }
    ),
    suite("Edge cases")(
      test("zip with Unit preserves types") {
        val a                    = (1, "hello")
        val zippable             = implicitly[Zippable[(Int, String), Unit]]
        val result: zippable.Out = zippable.zip(a, ())
        assertTrue(result.asInstanceOf[Any] == (1, "hello"))
      },
      test("multiple unit zips") {
        val zippable2 = implicitly[Zippable[Unit, Int]]
        val zippable3 = implicitly[Zippable[Int, Unit]]

        val r2: zippable2.Out = zippable2.zip((), 42)
        val r3: zippable3.Out = zippable3.zip(42, ())

        assertTrue(r2.asInstanceOf[Any] == 42 && r3.asInstanceOf[Any] == 42)
      }
    ),
    suite("Consistency between discard flags and behavior")(
      test("when discardsLeft is true, left value is not in output") {
        val zippable             = implicitly[Zippable[Unit, String]]
        val result: zippable.Out = zippable.zip((), "hello")
        assertTrue(zippable.discardsLeft && result.asInstanceOf[Any] == "hello")
      },
      test("when discardsRight is true, right value is not in output") {
        val zippable             = implicitly[Zippable[String, Unit]]
        val result: zippable.Out = zippable.zip("hello", ())
        assertTrue(zippable.discardsRight && result.asInstanceOf[Any] == "hello")
      },
      test("when neither flag is true, both values are in output") {
        val zippable             = implicitly[Zippable[Int, String]]
        val result: zippable.Out = zippable.zip(1, "hello")
        assertTrue(!zippable.discardsLeft && !zippable.discardsRight && result.asInstanceOf[Any] == (1, "hello"))
      }
    ),
    suite("Comparison with Combiner behavior")(
      test("zippable and combiner produce same result for simple types") {
        val a        = 1
        val b        = "hello"
        val zippable = implicitly[Zippable[Int, String]]
        val combiner = implicitly[Combiner[Int, String]]

        val zipped: zippable.Out   = zippable.zip(a, b)
        val combined: combiner.Out = combiner.combine(a, b)

        assertTrue(zipped.asInstanceOf[Any] == combined.asInstanceOf[Any])
      },
      test("zippable and combiner both handle Unit left") {
        val a        = ()
        val b        = 42
        val zippable = implicitly[Zippable[Unit, Int]]
        val combiner = implicitly[Combiner[Unit, Int]]

        val zipped: zippable.Out   = zippable.zip(a, b)
        val combined: combiner.Out = combiner.combine(a, b)

        assertTrue(zipped.asInstanceOf[Any] == combined.asInstanceOf[Any] && zipped.asInstanceOf[Any] == 42)
      },
      test("zippable and combiner both handle Unit right") {
        val a        = 42
        val b        = ()
        val zippable = implicitly[Zippable[Int, Unit]]
        val combiner = implicitly[Combiner[Int, Unit]]

        val zipped: zippable.Out   = zippable.zip(a, b)
        val combined: combiner.Out = combiner.combine(a, b)

        assertTrue(zipped.asInstanceOf[Any] == combined.asInstanceOf[Any] && zipped.asInstanceOf[Any] == 42)
      },
      test("zippable and combiner produce same result for tuple flattening") {
        val ab       = (1, "hello")
        val c        = true
        val zippable = implicitly[Zippable[(Int, String), Boolean]]
        val combiner = implicitly[Combiner[(Int, String), Boolean]]

        val zipped: zippable.Out   = zippable.zip(ab, c)
        val combined: combiner.Out = combiner.combine(ab, c)

        assertTrue(
          zipped.asInstanceOf[Any] == combined.asInstanceOf[Any] && zipped.asInstanceOf[Any] == (1, "hello", true)
        )
      }
    )
  )
}
