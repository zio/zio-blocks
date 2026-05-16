/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.combinators

import zio.test._

object UnionsSpec extends ZIOSpecDefault {

  def spec = suite("Unions")(
    suite("combine")(
      suite("Combine Either to union")(
        test("combine Left returns left value as union") {
          val result = Unions.combine(Left(42): Either[Int, String])
          assertTrue(result == 42)
        },
        test("combine Right returns right value as union") {
          val result = Unions.combine(Right("hello"): Either[Int, String])
          assertTrue(result == "hello")
        },
        test("combine with Boolean types") {
          val inputLeft: Either[Boolean, Double]  = Left(true)
          val inputRight: Either[Boolean, Double] = Right(3.14)
          val resultLeft                          = Unions.combine(inputLeft)
          val resultRight                         = Unions.combine(inputRight)
          assertTrue(resultLeft == true && resultRight == 3.14)
        }
      )
    ),
    suite("separate")(
      suite("Separate union to Either")(
        test("separate discriminates Int from String union") {
          val u                      = summon[Unions.Unions.WithOut[Int, String, Int | String]]
          val intValue: Int | String = 42
          val strValue: Int | String = "hello"
          val resultInt              = u.separate(intValue)
          val resultStr              = u.separate(strValue)
          assertTrue(resultInt == Left(42) && resultStr == Right("hello"))
        },
        test("separate discriminates rightmost type in 3-way union") {
          val u                                 = summon[Unions.Unions.WithOut[Int | String, Boolean, Int | String | Boolean]]
          val boolValue: Int | String | Boolean = true
          val intValue: Int | String | Boolean  = 42
          val strValue: Int | String | Boolean  = "hello"
          val resultBool                        = u.separate(boolValue)
          val resultInt                         = u.separate(intValue)
          val resultStr                         = u.separate(strValue)
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
          val u                   = summon[Unions.Unions.WithOut[Foo, Bar, Foo | Bar]]
          val fooValue: Foo | Bar = Foo(1)
          val barValue: Foo | Bar = Bar("test")
          val resultFoo           = u.separate(fooValue)
          val resultBar           = u.separate(barValue)
          assertTrue(resultFoo == Left(Foo(1)) && resultBar == Right(Bar("test")))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(Left(x))) == Left(x)") {
        val input: Either[Int, String] = Left(42)
        val combined                   = Unions.combine(input)
        val u                          = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val separated                  = u.separate(combined)
        assertTrue(separated == Left(42))
      },
      test("separate(combine(Right(x))) == Right(x)") {
        val input: Either[Int, String] = Right("hello")
        val combined                   = Unions.combine(input)
        val u                          = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val separated                  = u.separate(combined)
        assertTrue(separated == Right("hello"))
      },
      test("combine(separate(union)) == union for left value") {
        val u                      = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val original: Int | String = 42
        val separated              = u.separate(original)
        val recombined             = Unions.combine(separated)
        assertTrue(recombined == 42)
      },
      test("combine(separate(union)) == union for right value") {
        val u                      = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val original: Int | String = "hello"
        val separated              = u.separate(original)
        val recombined             = Unions.combine(separated)
        assertTrue(recombined == "hello")
      }
    ),
    suite("Top-level convenience methods")(
      test("Unions.combine works without explicit combiner") {
        val input: Either[Int, String] = Left(42)
        val result                     = Unions.combine(input)
        assertTrue(result == 42)
      },
      test("Unions.combine Right works without explicit combiner") {
        val input: Either[Int, String] = Right("hello")
        val result                     = Unions.combine(input)
        assertTrue(result == "hello")
      },
      test("Unions.separate works with explicit instance") {
        val u                   = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val input: Int | String = "hello"
        val result              = u.separate(input)
        assertTrue(result == Right("hello"))
      }
    ),
    suite("Compile-time uniqueness check")(
      test("Unions[Int, Int] fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Unions.WithOut[Int, Int, Int | Int]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: Int. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions[String | Int, String | Int, String] with duplicate types fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Unions.WithOut[String | Int, String, String | Int | String]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions with three unique types compiles successfully") {
        val errors =
          typeCheckErrors("summon[Unions.Unions.WithOut[Int | String, Boolean, Int | String | Boolean]]")
        assertTrue(errors.isEmpty)
      },
      test("Unions[String | Int, String] fails when R is contained in L") {
        val errors   = typeCheckErrors("summon[Unions.Unions.WithOut[String | Int, String, String | Int | String]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions[Int, String | Int] fails when L is contained in R") {
        val errors   = typeCheckErrors("summon[Unions.Unions.WithOut[Int, String | Int, Int | String | Int]]")
        val expected =
          "Union types must contain unique types. Found overlapping types: Int. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions with partial overlap fails (Int|String|Boolean vs Int|String|Char)") {
        val errors = typeCheckErrors(
          "summon[Unions.Unions.WithOut[Int | String | Boolean, Int | String | Char, Int | String | Boolean | Int | String | Char]]"
        )
        val expected =
          "Union types must contain unique types. Found overlapping types: Int, String. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions rejects R that is a union type for disjoint unions") {
        val errors =
          typeCheckErrors(
            "summon[Unions.Unions.WithOut[Int | String, Boolean | Char, Int | String | Boolean | Char]]"
          )
        val expected =
          "The right type of a Unions.Unions must not be a union type. Use a simple (non-union) type for R to ensure the separator peels exactly one type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Unions rejects R that is a union type") {
        val errors =
          typeCheckErrors("summon[Unions.Unions.WithOut[Int, String | Boolean, Int | String | Boolean]]")
        val expected =
          "The right type of a Unions.Unions must not be a union type. Use a simple (non-union) type for R to ensure the separator peels exactly one type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      }
    ),
    suite("Real-world API usage with type inference")(
      test("combine Either to union without type annotations") {
        val either: Either[Int, String] = Right("hello")
        val union                       = Unions.combine(either)
        assertTrue(union == "hello")
      },
      test("separate union to Either with inferred instance") {
        val union: Int | String = 42
        val u                   = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val either              = u.separate(union)
        assertTrue(either == Left(42))
      },
      test("roundtrip with inferred typeclass") {
        val original: Either[Int, String] = Left(42)
        val union                         = Unions.combine(original)
        val u                             = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val back                          = u.separate(union)
        assertTrue(back == Left(42))
      },
      test("generic function using Unions with full inference") {
        def toUnion[L, R](e: Either[L, R])(using Unions.Unions[L, R]): Unions.Unions[L, R]#Out =
          Unions.combine(e)

        val result = toUnion(Right("test"): Either[Int, String])
        assertTrue(result == "test")
      },
      test("generic function using Unions with using clause") {
        def fromUnion[L, R](a: L | R)(using u: Unions.Unions.WithOut[L, R, L | R]): Either[L, R] =
          u.separate(a)

        val input: Int | String = "hello"
        val result              = fromUnion[Int, String](input)
        assertTrue(result == Right("hello"))
      },
      test("chained operations with type-annotated intermediates") {
        val step1: Either[Boolean, Double] = Right(3.14)
        val step2: Boolean | Double        = Unions.combine(step1)
        val u                              = summon[Unions.Unions.WithOut[Boolean, Double, Boolean | Double]]
        val step3                          = u.separate(step2)
        assertTrue(step3 == Right(3.14))
      },
      test("3-way union with rightmost type discrimination") {
        val union: Int | String | Boolean = true
        val u1                            = summon[Unions.Unions.WithOut[Int | String, Boolean, Int | String | Boolean]]
        val sep1                          = u1.separate(union)
        assertTrue(sep1 == Right(true))

        val leftUnion: Int | String = 42
        val u2                      = summon[Unions.Unions.WithOut[Int, String, Int | String]]
        val sep2                    = u2.separate(leftUnion)
        assertTrue(sep2 == Left(42))
      },
      test("Unions inference in higher-order functions") {
        def processEither[L, R](e: Either[L, R])(using
          c: Unions.Unions.WithOut[L, R, L | R]
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
      test("Unions inference preserves union semantics") {
        def checkUnion[L, R](a: L | R)(using u: Unions.Unions.WithOut[L, R, L | R]): String =
          u.separate(a).fold(l => "left", r => "right")

        val intVal: Int | String   = 42
        val strVal: Int | String   = "hello"
        val boolVal: Boolean | Int = true

        val r1 = checkUnion[Int, String](intVal)
        val r2 = checkUnion[Int, String](strVal)
        val r3 = checkUnion[Boolean, Int](boolVal)

        assertTrue(r1 == "left" && r2 == "right" && r3 == "left")
      }
    )
  )
}
