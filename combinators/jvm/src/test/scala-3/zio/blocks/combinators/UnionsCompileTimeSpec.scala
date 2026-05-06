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

import scala.compiletime.testing.typeCheckErrors

import zio.test._

object UnionsCompileTimeSpec extends ZIOSpecDefault {

  def spec = suite("Compile-time uniqueness check")(
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
  )
}
