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

object ConcatSpec extends ZIOSpecDefault {

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal

  def spec = suite("Concat")(
    suite("same type")(
      test("Concat[Int, Int] resolves Out = Int") {
        def leftValue[O](implicit c: Concat.WithOut[Int, Int, O]): O  = c.left(42)
        def rightValue[O](implicit c: Concat.WithOut[Int, Int, O]): O = c.right(99)

        val left: Int  = leftValue
        val right: Int = rightValue

        assertTrue(left == 42, right == 99)
      }
    ),
    suite("subtype")(
      test("Concat[Dog, Animal] resolves Out = Animal (L <: R)") {
        def leftValue[O](d: Dog)(implicit c: Concat.WithOut[Dog, Animal, O]): O = c.left(d)

        val d            = Dog("Rex")
        val left: Animal = leftValue(d)

        assertTrue(left == (d: Animal))
      },
      test("Concat[Animal, Dog] resolves Out = Animal (R <: L)") {
        def rightValue[O](d: Dog)(implicit c: Concat.WithOut[Animal, Dog, O]): O = c.right(d)

        val d             = Dog("Rex")
        val right: Animal = rightValue(d)

        assertTrue(right == (d: Animal))
      },
      test("Concat[Nothing, Int] resolves Out = Int (Nothing <: Int)") {
        def rightValue[O](implicit c: Concat.WithOut[Nothing, Int, O]): O = c.right(42)

        val right: Int = rightValue

        assertTrue(right == 42)
      },
      test("Concat[String, Nothing] resolves Out = String (Nothing <: String)") {
        def leftValue[O](implicit c: Concat.WithOut[String, Nothing, O]): O = c.left("hello")

        val left: String = leftValue

        assertTrue(left == "hello")
      }
    ),
    suite("meaningful common supertype (siblings)")(
      test("Concat[Dog, Cat] resolves Out = Animal (shared sealed parent) and is identity-like") {
        def leftValue[O](d: Dog)(implicit c: Concat.WithOut[Dog, Cat, O]): O   = c.left(d)
        def rightValue[O](k: Cat)(implicit c: Concat.WithOut[Dog, Cat, O]): O  = c.right(k)
        def isIdentityFor[O](implicit c: Concat.WithOut[Dog, Cat, O]): Boolean = c.isIdentityLike

        val d             = Dog("Rex")
        val k             = Cat("Misty")
        val left: Animal  = leftValue(d)
        val right: Animal = rightValue(k)

        // Identity-like means concat reuses values bare (no Either wrapping).
        assertTrue(
          isIdentityFor,
          left.asInstanceOf[AnyRef] eq d,
          right.asInstanceOf[AnyRef] eq k
        )
      },
      test("Concat[Cat, Dog] resolves Out = Animal symmetrically") {
        def leftValue[O](k: Cat)(implicit c: Concat.WithOut[Cat, Dog, O]): O   = c.left(k)
        def rightValue[O](d: Dog)(implicit c: Concat.WithOut[Cat, Dog, O]): O  = c.right(d)
        def isIdentityFor[O](implicit c: Concat.WithOut[Cat, Dog, O]): Boolean = c.isIdentityLike

        val k             = Cat("Misty")
        val d             = Dog("Rex")
        val left: Animal  = leftValue(k)
        val right: Animal = rightValue(d)

        // Reference equality (eq) proves no wrapping/copy occurred.
        assertTrue(
          isIdentityFor,
          left.asInstanceOf[AnyRef] eq k,
          right.asInstanceOf[AnyRef] eq d
        )
      }
    ),
    suite("unrelated types")(
      test("Concat[String, Int] resolves Out = Either[String, Int] and wraps values") {
        def leftValue[O](implicit c: Concat.WithOut[String, Int, O]): O           = c.left("hello")
        def rightValue[O](implicit c: Concat.WithOut[String, Int, O]): O          = c.right(42)
        def isIdentityFor[O](implicit c: Concat.WithOut[String, Int, O]): Boolean = c.isIdentityLike

        val left: Either[String, Int]  = leftValue
        val right: Either[String, Int] = rightValue

        // Genuinely disjoint types must wrap via Either; identityLike is false.
        assertTrue(!isIdentityFor, left == Left("hello"), right == Right(42))
      }
    ),
    suite("two-implicit feasibility gate")(
      test("two WithOut implicits resolve simultaneously in one method signature") {
        def combine[A, B, C, AB, ABC](a: A, b: B, c: C)(implicit
          ab: Concat.WithOut[A, B, AB],
          abc: Concat.WithOut[AB, C, ABC]
        ): (ABC, ABC) = {
          val fromLeft  = abc.left(ab.left(a))
          val fromRight = abc.right(c)
          (fromLeft, fromRight)
        }

        val (fromA, fromC): (Either[Int, String], Either[Int, String]) = combine(42, 99, "hello")

        assertTrue(
          fromA == Left(42),
          fromC == Right("hello")
        )
      }
    )
  )
}
