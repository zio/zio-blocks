package zio.blocks.combinators

import zio.test._

object EithersSpec extends ZIOSpecDefault {

  def spec = suite("Eithers")(
    suite("Combiner")(
      suite("Atomic Either (no nesting)")(
        test("combine Left returns Left") {
          val combiner = implicitly[Eithers.Combiner[Int, String]]
          val result   = combiner.combine(Left(42))
          assertTrue(result.asInstanceOf[Any] == Left(42))
        },
        test("combine Right returns Right") {
          val combiner = implicitly[Eithers.Combiner[Int, String]]
          val result   = combiner.combine(Right("hello"))
          assertTrue(result.asInstanceOf[Any] == Right("hello"))
        }
      ),
      suite("Nested Either canonicalization")(
        test("combine Right(Left(x)) reassociates to left-nested form") {
          val combiner                                    = implicitly[Eithers.Combiner[Int, Either[String, Boolean]]]
          val input: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
          val result                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Left(Right("middle")))
        },
        test("combine Right(Right(x)) reassociates to left-nested form") {
          val combiner                                    = implicitly[Eithers.Combiner[Int, Either[String, Boolean]]]
          val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
          val result                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Right(true))
        },
        test("combine Left(x) reassociates to left-nested form") {
          val combiner                                    = implicitly[Eithers.Combiner[Int, Either[String, Boolean]]]
          val input: Either[Int, Either[String, Boolean]] = Left(42)
          val result                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
        },
        test("deeply nested Either canonicalizes correctly") {
          val combiner                                                    = implicitly[Eithers.Combiner[Int, Either[String, Either[Boolean, Double]]]]
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
          val result                                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Right(3.14))
        },
        test("deeply nested Left canonicalizes correctly") {
          val combiner                                                    = implicitly[Eithers.Combiner[Int, Either[String, Either[Boolean, Double]]]]
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Left(1)
          val result                                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Left(Left(Left(1))))
        }
      ),
      suite("Idempotence")(
        test("combine on already-canonical Left preserves structure") {
          val combiner                                    = implicitly[Eithers.Combiner[Either[Int, String], Boolean]]
          val input: Either[Either[Int, String], Boolean] = Left(Left(42))
          val result                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
        },
        test("combine on already-canonical Right preserves structure") {
          val combiner                                    = implicitly[Eithers.Combiner[Either[Int, String], Boolean]]
          val input: Either[Either[Int, String], Boolean] = Right(true)
          val result                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Right(true))
        },
        test("combine on already-canonical deeply nested Left preserves structure") {
          val combiner                                                    = implicitly[Eithers.Combiner[Either[Either[Int, String], Boolean], Double]]
          val input: Either[Either[Either[Int, String], Boolean], Double] = Left(Left(Left(42)))
          val result                                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Left(Left(Left(42))))
        },
        test("combine on already-canonical deeply nested Right preserves structure") {
          val combiner                                                    = implicitly[Eithers.Combiner[Either[Either[Int, String], Boolean], Double]]
          val input: Either[Either[Either[Int, String], Boolean], Double] = Right(3.14)
          val result                                                      = combiner.combine(input)
          assertTrue(result.asInstanceOf[Any] == Right(3.14))
        }
      )
    ),
    suite("Separator")(
      suite("Peeling rightmost alternative")(
        test("separate Left returns Left") {
          val input: Either[Int, String] = Left(42)
          val result                     = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Left(42))
        },
        test("separate Right returns Right") {
          val input: Either[Int, String] = Right("hello")
          val result                     = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Right("hello"))
        },
        test("separate nested Either peels rightmost") {
          val input: Either[Either[Int, String], Boolean] = Right(true)
          val result                                      = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Right(true))
        },
        test("separate nested Either Left case") {
          val input: Either[Either[Int, String], Boolean] = Left(Left(42))
          val result                                      = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
        }
      ),
      suite("Canonicalization")(
        test("separate canonicalizes right-nested input") {
          val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
          val result                                      = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Right(true))
        },
        test("separate canonicalizes right-nested Left") {
          val input: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
          val result                                      = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Left(Right("middle")))
        },
        test("separate canonicalizes deeply nested input") {
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
          val result                                                      = Eithers.separate(input)
          assertTrue(result.asInstanceOf[Any] == Right(3.14))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(Left(x))) preserves structure for atomic Either") {
        val input: Either[Int, String] = Left(42)
        val combined                   = Eithers.combine(input)
        val separated                  = Eithers.separate(combined)
        assertTrue(separated.asInstanceOf[Any] == Left(42))
      },
      test("separate(combine(Right(x))) preserves structure for atomic Either") {
        val input: Either[Int, String] = Right("hello")
        val combined                   = Eithers.combine(input)
        val separated                  = Eithers.separate(combined)
        assertTrue(separated.asInstanceOf[Any] == Right("hello"))
      },
      test("roundtrip for right-nested Either preserves all branches") {
        val left: Either[Int, Either[String, Boolean]]  = Left(42)
        val mid: Either[Int, Either[String, Boolean]]   = Right(Left("hello"))
        val right: Either[Int, Either[String, Boolean]] = Right(Right(true))

        assertTrue(
          Eithers.separate(left).asInstanceOf[Any] == Eithers.combine(left).asInstanceOf[Any] &&
            Eithers.separate(mid).asInstanceOf[Any] == Eithers.combine(mid).asInstanceOf[Any] &&
            Eithers.separate(right).asInstanceOf[Any] == Eithers.combine(right).asInstanceOf[Any]
        )
      },
      test("roundtrip for deeply nested Either (4 alternatives)") {
        val input1: Either[Int, Either[String, Either[Boolean, Double]]] = Left(1)
        val input2: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Left("a"))
        val input3: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Left(true)))
        val input4: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))

        assertTrue(
          Eithers.combine(input1).asInstanceOf[Any] == Left(Left(Left(1))) &&
            Eithers.combine(input2).asInstanceOf[Any] == Left(Left(Right("a"))) &&
            Eithers.combine(input3).asInstanceOf[Any] == Left(Right(true)) &&
            Eithers.combine(input4).asInstanceOf[Any] == Right(3.14) &&
            Eithers.separate(input1).asInstanceOf[Any] == Left(Left(Left(1))) &&
            Eithers.separate(input2).asInstanceOf[Any] == Left(Left(Right("a"))) &&
            Eithers.separate(input3).asInstanceOf[Any] == Left(Right(true)) &&
            Eithers.separate(input4).asInstanceOf[Any] == Right(3.14)
        )
      }
    ),
    suite("Top-level convenience methods")(
      test("Eithers.combine works without explicit combiner") {
        val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
        val result                                      = Eithers.combine(input)
        assertTrue(result.asInstanceOf[Any] == Right(true))
      },
      test("Eithers.combine canonicalizes nested Left") {
        val input: Either[Int, Either[String, Boolean]] = Left(42)
        val result                                      = Eithers.combine(input)
        assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
      },
      test("Eithers.separate works without explicit separator") {
        val input: Either[Either[Int, String], Boolean] = Right(true)
        val result                                      = Eithers.separate(input)
        assertTrue(result.asInstanceOf[Any] == Right(true))
      },
      test("Eithers.separate peels Left correctly") {
        val input: Either[Either[Int, String], Boolean] = Left(Left(42))
        val result                                      = Eithers.separate(input)
        assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
      }
    ),
    suite("Real-world API usage with type inference")(
      test("combine Either without explicit types") {
        val either: Either[Int, String] = Right("hello")
        val result                      = Eithers.combine(either)
        assertTrue(result == Right("hello"))
      },
      test("combine canonicalizes right-nested Either with inference") {
        // Right-nested: Either[Int, Either[String, Boolean]]
        val nested: Either[Int, Either[String, Boolean]] = Right(Right(true))
        val canonical                                    = Eithers.combine(nested)
        // Should be left-nested: Either[Either[Int, String], Boolean]
        assertTrue(canonical == Right(true))
      },
      test("separate with inferred Separator") {
        val either: Either[Either[Int, String], Boolean] = Right(true)
        val result                                       = Eithers.separate(either)
        assertTrue(result.asInstanceOf[Any] == Right(true))
      },
      test("roundtrip with inferred types") {
        val original: Either[Int, String] = Left(42)
        val combined                      = Eithers.combine(original)
        val separated                     = Eithers.separate(combined)
        assertTrue(separated.asInstanceOf[Any] == Left(42))
      },
      test("generic function using Combiner") {
        def canonicalize[L, R](e: Either[L, R])(implicit c: Eithers.Combiner[L, R]): c.Out =
          Eithers.combine(e)

        val nested: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
        val result                                       = canonicalize(nested)
        assertTrue(result.asInstanceOf[Any] == Left(Right("middle")))
      },
      test("generic function using Separator") {
        def peel[A](a: A)(implicit s: Eithers.Separator[A]): Either[s.Left, s.Right] =
          Eithers.separate(a)

        val either: Either[Either[Int, String], Boolean] = Left(Left(42))
        val result                                       = peel(either)
        assertTrue(result.asInstanceOf[Any] == Left(Left(42)))
      },
      test("chained operations - deep nesting canonicalization") {
        // 4 levels deep: Either[Int, Either[String, Either[Boolean, Double]]]
        val deep: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
        val canonical                                                  = Eithers.combine(deep)
        // Should be: Either[Either[Either[Int, String], Boolean], Double]
        assertTrue(canonical.asInstanceOf[Any] == Right(3.14))
      },
      test("Combiner in higher-order function") {
        def handleResult[L, R](e: Either[L, R])(implicit c: Eithers.Combiner[L, R]): String = {
          val canonical: c.Out = c.combine(e)
          canonical match {
            case _: Left[?, ?]  => "left"
            case _: Right[?, ?] => "right"
          }
        }

        val result = handleResult[Int, String](Right("test"))
        assertTrue(result == "right")
      }
    )
  )
}
