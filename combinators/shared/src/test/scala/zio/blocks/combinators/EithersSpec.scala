package zio.blocks.combinators

import zio.test._

object EithersSpec extends ZIOSpecDefault {

  def spec = suite("Eithers")(
    suite("combine")(
      suite("Atomic Either (no nesting)")(
        test("combine Left returns Left") {
          val input: Either[Int, String] = Left(42)
          val result                     = Eithers.combine(input)
          assertTrue(result == Left(42))
        },
        test("combine Right returns Right") {
          val input: Either[Int, String] = Right("hello")
          val result                     = Eithers.combine(input)
          assertTrue(result == Right("hello"))
        }
      ),
      suite("Nested Either canonicalization")(
        test("combine Right(Left(x)) reassociates to left-nested form") {
          val input: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
          val result                                      = Eithers.combine(input)
          assertTrue(result == Left(Right("middle")))
        },
        test("combine Right(Right(x)) reassociates to left-nested form") {
          val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
          val result                                      = Eithers.combine(input)
          assertTrue(result == Right(true))
        },
        test("combine Left(x) reassociates to left-nested form") {
          val input: Either[Int, Either[String, Boolean]] = Left(42)
          val result                                      = Eithers.combine(input)
          assertTrue(result == Left(Left(42)))
        },
        test("deeply nested Either canonicalizes correctly") {
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
          val result                                                      = Eithers.combine(input)
          assertTrue(result == Right(3.14))
        },
        test("deeply nested Left canonicalizes correctly") {
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Left(1)
          val result                                                      = Eithers.combine(input)
          assertTrue(result == Left(Left(Left(1))))
        }
      ),
      suite("Idempotence")(
        test("combine on already-canonical Left preserves structure") {
          val input: Either[Either[Int, String], Boolean] = Left(Left(42))
          val result                                      = Eithers.combine(input)
          assertTrue(result == Left(Left(42)))
        },
        test("combine on already-canonical Right preserves structure") {
          val input: Either[Either[Int, String], Boolean] = Right(true)
          val result                                      = Eithers.combine(input)
          assertTrue(result == Right(true))
        },
        test("combine on already-canonical deeply nested Left preserves structure") {
          val input: Either[Either[Either[Int, String], Boolean], Double] = Left(Left(Left(42)))
          val result                                                      = Eithers.combine(input)
          assertTrue(result == Left(Left(Left(42))))
        },
        test("combine on already-canonical deeply nested Right preserves structure") {
          val input: Either[Either[Either[Int, String], Boolean], Double] = Right(3.14)
          val result                                                      = Eithers.combine(input)
          assertTrue(result == Right(3.14))
        }
      )
    ),
    suite("separate")(
      suite("Peeling rightmost alternative")(
        test("separate Left returns Left") {
          val e                          = implicitly[Eithers.Eithers[Int, String]]
          val input: Either[Int, String] = Left(42)
          val combined                   = e.combine(input)
          val result                     = e.separate(combined)
          assertTrue(result == Left(42))
        },
        test("separate Right returns Right") {
          val e                          = implicitly[Eithers.Eithers[Int, String]]
          val input: Either[Int, String] = Right("hello")
          val combined                   = e.combine(input)
          val result                     = e.separate(combined)
          assertTrue(result == Right("hello"))
        },
        test("separate nested Either peels rightmost") {
          val e                                           = implicitly[Eithers.Eithers[Either[Int, String], Boolean]]
          val input: Either[Either[Int, String], Boolean] = Right(true)
          val combined                                    = e.combine(input)
          val result                                      = e.separate(combined)
          assertTrue(result == Right(true))
        },
        test("separate nested Either Left case") {
          val e                                           = implicitly[Eithers.Eithers[Either[Int, String], Boolean]]
          val input: Either[Either[Int, String], Boolean] = Left(Left(42))
          val combined                                    = e.combine(input)
          val result                                      = e.separate(combined)
          assertTrue(result == Left(Left(42)))
        }
      ),
      suite("Canonicalization")(
        test("separate canonicalizes right-nested input") {
          val e                                           = implicitly[Eithers.Eithers[Int, Either[String, Boolean]]]
          val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
          val combined                                    = e.combine(input)
          val result                                      = e.separate(combined)
          assertTrue(result == Right(Right(true)))
        },
        test("separate canonicalizes right-nested Left") {
          val e                                           = implicitly[Eithers.Eithers[Int, Either[String, Boolean]]]
          val input: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
          val combined                                    = e.combine(input)
          val result                                      = e.separate(combined)
          assertTrue(result == Right(Left("middle")))
        },
        test("separate canonicalizes deeply nested input") {
          val e                                                           = implicitly[Eithers.Eithers[Int, Either[String, Either[Boolean, Double]]]]
          val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
          val combined                                                    = e.combine(input)
          val result                                                      = e.separate(combined)
          assertTrue(result == Right(Right(Right(3.14))))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(Left(x))) preserves structure for atomic Either") {
        val e                          = implicitly[Eithers.Eithers[Int, String]]
        val input: Either[Int, String] = Left(42)
        val combined                   = e.combine(input)
        val separated                  = e.separate(combined)
        assertTrue(separated == Left(42))
      },
      test("separate(combine(Right(x))) preserves structure for atomic Either") {
        val e                          = implicitly[Eithers.Eithers[Int, String]]
        val input: Either[Int, String] = Right("hello")
        val combined                   = e.combine(input)
        val separated                  = e.separate(combined)
        assertTrue(separated == Right("hello"))
      },
      test("roundtrip for right-nested Either preserves all branches") {
        val e                                           = implicitly[Eithers.Eithers[Int, Either[String, Boolean]]]
        val left: Either[Int, Either[String, Boolean]]  = Left(42)
        val mid: Either[Int, Either[String, Boolean]]   = Right(Left("hello"))
        val right: Either[Int, Either[String, Boolean]] = Right(Right(true))

        val rl = e.separate(e.combine(left))
        val rm = e.separate(e.combine(mid))
        val rr = e.separate(e.combine(right))
        assertTrue(
          rl == left &&
            rm == mid &&
            rr == right
        )
      },
      test("roundtrip for deeply nested Either (4 alternatives)") {
        val e                                                            = implicitly[Eithers.Eithers[Int, Either[String, Either[Boolean, Double]]]]
        val input1: Either[Int, Either[String, Either[Boolean, Double]]] = Left(1)
        val input2: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Left("a"))
        val input3: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Left(true)))
        val input4: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))

        val r1 = e.separate(e.combine(input1))
        val r2 = e.separate(e.combine(input2))
        val r3 = e.separate(e.combine(input3))
        val r4 = e.separate(e.combine(input4))
        assertTrue(
          r1 == input1 &&
            r2 == input2 &&
            r3 == input3 &&
            r4 == input4
        )
      }
    ),
    suite("Reassociation guarantees")(
      test("combine right-nested Either produces left-nested form with atomic Right") {
        val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
        val result                                      = Eithers.combine(input)
        assertTrue(result == Right(true))
      },
      test("separate right-nested Either first canonicalizes then peels") {
        val e                                           = implicitly[Eithers.Eithers[Int, Either[String, Boolean]]]
        val input: Either[Int, Either[String, Boolean]] = Right(Left("mid"))
        val result                                      = e.separate(e.combine(input))
        assertTrue(result == Right(Left("mid")))
      },
      test("deeply nested Either fully canonicalizes to left-nested form") {
        val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
        val result                                                      = Eithers.combine(input)
        assertTrue(result == Right(3.14))
      },
      test("deeply nested Left produces deeply left-nested Left") {
        val input: Either[Int, Either[String, Either[Boolean, Double]]] = Left(1)
        val result                                                      = Eithers.combine(input)
        assertTrue(result == Left(Left(Left(1))))
      },
      test("middle value ends up in correct canonical position") {
        val input: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Left(true)))
        val result                                                      = Eithers.combine(input)
        assertTrue(result == Left(Right(true)))
      }
    ),
    suite("Top-level convenience methods")(
      test("Eithers.combine works without explicit combiner") {
        val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
        val result                                      = Eithers.combine(input)
        assertTrue(result == Right(true))
      },
      test("Eithers.combine canonicalizes nested Left") {
        val input: Either[Int, Either[String, Boolean]] = Left(42)
        val result                                      = Eithers.combine(input)
        assertTrue(result == Left(Left(42)))
      }
    ),
    suite("Real-world API usage with type inference")(
      test("combine Either without explicit types") {
        val either: Either[Int, String] = Right("hello")
        val result                      = Eithers.combine(either)
        assertTrue(result == Right("hello"))
      },
      test("combine canonicalizes right-nested Either with inference") {
        val nested: Either[Int, Either[String, Boolean]] = Right(Right(true))
        val canonical                                    = Eithers.combine(nested)
        assertTrue(canonical == Right(true))
      },
      test("roundtrip with inferred types") {
        val e                             = implicitly[Eithers.Eithers[Int, String]]
        val original: Either[Int, String] = Left(42)
        val combined                      = e.combine(original)
        val separated                     = e.separate(combined)
        assertTrue(separated == Left(42))
      },
      test("generic function using Eithers typeclass") {
        def canonicalize[L, R](e: Either[L, R])(implicit c: Eithers.Eithers[L, R]): c.Out =
          Eithers.combine(e)

        val nested: Either[Int, Either[String, Boolean]] = Right(Left("middle"))
        val result                                       = canonicalize(nested)
        assertTrue(result == Left(Right("middle")))
      },
      test("chained operations - deep nesting canonicalization") {
        val deep: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
        val canonical                                                  = Eithers.combine(deep)
        assertTrue(canonical == Right(3.14))
      },
      test("Eithers in higher-order function") {
        def handleResult[L, R](e: Either[L, R])(implicit c: Eithers.Eithers[L, R]): String = {
          val canonical: c.Out = c.combine(e)
          canonical match {
            case _: Left[_, _]  => "left"
            case _: Right[_, _] => "right"
          }
        }

        val result = handleResult[Int, String](Right("test"))
        assertTrue(result == "right")
      }
    )
  )
}
