package zio.blocks.combinators

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object UnionsSpec extends ZIOSpecDefault {

  def spec = suite("Unions")(
    suite("Combiner")(
      suite("Combine Either to union")(
        test("combine Left returns left value as union") {
          val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
          val input: Either[Int, String] = Left(42)
          val result: Int | String       = combiner.combine(input)
          assertTrue(result == 42)
        },
        test("combine Right returns right value as union") {
          val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
          val input: Either[Int, String] = Right("hello")
          val result: Int | String       = combiner.combine(input)
          assertTrue(result == "hello")
        },
        test("combine with Boolean types") {
          val combiner                            = summon[Unions.Combiner.WithOut[Boolean, Double, Boolean | Double]]
          val inputLeft: Either[Boolean, Double]  = Left(true)
          val inputRight: Either[Boolean, Double] = Right(3.14)
          val resultLeft: Boolean | Double        = combiner.combine(inputLeft)
          val resultRight: Boolean | Double       = combiner.combine(inputRight)
          assertTrue(resultLeft == true && resultRight == 3.14)
        }
      )
    ),
    suite("Separator")(
      suite("Separate union to Either")(
        test("separate discriminates Int from String union") {
          val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
          val intValue: Int | String = 42
          val strValue: Int | String = "hello"
          val resultInt              = separator.separate(intValue)
          val resultStr              = separator.separate(strValue)
          assertTrue(resultInt == Left(42) && resultStr == Right("hello"))
        },
        test("separate discriminates rightmost type in 3-way union") {
          val separator                         = summon[Unions.Separator.WithTypes[Int | String | Boolean, Int | String, Boolean]]
          val boolValue: Int | String | Boolean = true
          val intValue: Int | String | Boolean  = 42
          val strValue: Int | String | Boolean  = "hello"
          val resultBool                        = separator.separate(boolValue)
          val resultInt                         = separator.separate(intValue)
          val resultStr                         = separator.separate(strValue)
          assertTrue(
            resultBool == Right(true) &&
              resultInt == Left(42) &&
              resultStr == Left("hello")
          )
        }
      ),
      suite("Type discrimination")(
        test("discriminates case class types") {
          case class Foo(x: Int)
          case class Bar(y: String)
          val separator           = summon[Unions.Separator.WithTypes[Foo | Bar, Foo, Bar]]
          val fooValue: Foo | Bar = Foo(1)
          val barValue: Foo | Bar = Bar("test")
          val resultFoo           = separator.separate(fooValue)
          val resultBar           = separator.separate(barValue)
          assertTrue(resultFoo == Left(Foo(1)) && resultBar == Right(Bar("test")))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(Left(x))) == Left(x)") {
        val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator                  = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Either[Int, String] = Left(42)
        val combined                   = combiner.combine(input)
        val separated                  = separator.separate(combined)
        assertTrue(separated == Left(42))
      },
      test("separate(combine(Right(x))) == Right(x)") {
        val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator                  = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Either[Int, String] = Right("hello")
        val combined                   = combiner.combine(input)
        val separated                  = separator.separate(combined)
        assertTrue(separated == Right("hello"))
      },
      test("combine(separate(union)) == union for left value") {
        val combiner               = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val original: Int | String = 42
        val separated              = separator.separate(original)
        val recombined             = combiner.combine(separated)
        assertTrue(recombined == 42)
      },
      test("combine(separate(union)) == union for right value") {
        val combiner               = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val original: Int | String = "hello"
        val separated              = separator.separate(original)
        val recombined             = combiner.combine(separated)
        assertTrue(recombined == "hello")
      }
    ),
    suite("Top-level convenience methods")(
      test("Unions.combine works without explicit combiner") {
        val input: Either[Int, String] = Left(42)
        val result: Int | String       = Unions.combine(input)
        assertTrue(result == 42)
      },
      test("Unions.combine Right works without explicit combiner") {
        val input: Either[Int, String] = Right("hello")
        val result: Int | String       = Unions.combine(input)
        assertTrue(result == "hello")
      },
      test("Unions.separate works with explicit separator") {
        val separator           = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Int | String = "hello"
        val result              = separator.separate(input)
        assertTrue(result == Right("hello"))
      }
    ),
    suite("Compile-time uniqueness check")(
      test("Separator[Int | Int] fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[Int, Int, Int]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: Int. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator[String | String] with duplicate types fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[String | Int, String | Int, String]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator with three unique types compiles successfully") {
        val errors =
          typeCheckErrors("summon[Unions.Separator.WithTypes[Int | String | Boolean, Int | String, Boolean]]")
        assertTrue(errors.isEmpty)
      },
      test("Separator[String | Int, String | Int, String] fails when R is contained in L") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[String | Int, String | Int, String]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator[String | Int, Int, String | Int] fails when L is contained in R") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[String | Int, Int, String | Int]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: Int. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator with partial overlap fails (Int|String|Boolean vs Int|String|Char)") {
        val errors = typeCheckErrors(
          "summon[Unions.Separator.WithTypes[Int | String | Boolean | Char, Int | String | Boolean, Int | String | Char]]"
        )
        val expected =
          "Union types must contain unique types. Found overlapping types: Int, String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator with disjoint union types compiles successfully") {
        val errors =
          typeCheckErrors(
            "summon[Unions.Separator.WithTypes[Int | String | Boolean | Char, Int | String, Boolean | Char]]"
          )
        assertTrue(errors.isEmpty)
      }
    ),
    suite("Real-world API usage with type inference")(
      test("combine Either to union without type annotations") {
        // No explicit types - inference should work
        val either: Either[Int, String] = Right("hello")
        val union                       = Unions.combine(either)
        assertTrue(union == "hello")
      },
      test("separate union to Either with inferred Separator") {
        val union: Int | String = 42
        // Separator is inferred from the union type
        val either = Unions.separate(union)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        assertTrue(either == Left(42))
      },
      test("roundtrip with inferred typeclass") {
        val original: Either[Int, String] = Left(42)
        val union                         = Unions.combine(original)
        // Separator synthesized from union type
        val back = Unions.separate(union)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        assertTrue(back == Left(42))
      },
      test("generic function using Combiner with full inference") {
        def toUnion[L, R](e: Either[L, R])(using Unions.Combiner[L, R]): Unions.Combiner[L, R]#Out =
          Unions.combine(e)

        // Type parameters inferred from argument
        val result = toUnion(Right("test"): Either[Int, String])
        assertTrue(result == "test")
      },
      test("generic function using Separator with using clause") {
        def fromUnion[A](a: A)(using s: Unions.Separator[A]): Either[s.Left, s.Right] =
          Unions.separate(a)

        val input: Int | String = "hello"
        // Separator inferred from input type at call site
        val result = fromUnion(input)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        assertTrue(result == Right("hello"))
      },
      test("chained operations with type-annotated intermediates") {
        val step1: Either[Boolean, Double] = Right(3.14)
        val step2: Boolean | Double        = Unions.combine(step1)
        // Separator inferred from annotated type
        val step3 = Unions.separate(step2)(using summon[Unions.Separator.WithTypes[Boolean | Double, Boolean, Double]])
        assertTrue(step3 == Right(3.14))
      },
      test("3-way union with rightmost type discrimination") {
        // Start with 3-way union - Separator will discriminate Boolean (rightmost)
        val union: Int | String | Boolean = true
        val sep1                          = Unions.separate(union)(using
          summon[Unions.Separator.WithTypes[Int | String | Boolean, Int | String, Boolean]]
        )
        assertTrue(sep1 == Right(true))

        // Separate a left value from 2-way union
        val leftUnion: Int | String = 42
        val sep2                    = Unions.separate(leftUnion)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        assertTrue(sep2 == Left(42))
      },
      test("Combiner inference in higher-order functions") {
        def processEither[L, R](e: Either[L, R])(using
          c: Unions.Combiner.WithOut[L, R, L | R]
        )(
          f: (L | R) => String
        ): String = f(c.combine(e))

        val result = processEither(Left(42): Either[Int, String]) { union =>
          union match {
            case i: Int    => s"got int: $i"
            case s: String => s"got string: $s"
          }
        }

        assertTrue(result == "got int: 42")
      },
      test("Separator inference preserves union semantics") {
        def checkUnion[A](a: A)(using s: Unions.Separator[A]): String =
          Unions.separate(a).fold(l => "left", r => "right")

        val intVal: Int | String   = 42
        val strVal: Int | String   = "hello"
        val boolVal: Boolean | Int = true

        // Each call uses appropriate Separator synthesized from input type
        val r1 = checkUnion(intVal)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        val r2 = checkUnion(strVal)(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        val r3 = checkUnion(boolVal)(using summon[Unions.Separator.WithTypes[Boolean | Int, Boolean, Int]])

        assertTrue(r1 == "left" && r2 == "right" && r3 == "left")
      }
    )
  )
}
