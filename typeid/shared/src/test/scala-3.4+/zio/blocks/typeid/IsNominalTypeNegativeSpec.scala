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

package zio.blocks.typeid

import scala.compiletime.testing.{Error, typeCheckErrors, typeChecks}
import zio.blocks.chunk.Chunk
import zio.test._

// Scala 3 only fixtures: an opaque type and a type alias are nominal.
object NominalScala3Fixtures {
  opaque type BookId = Int
  object BookId {
    def apply(value: Int): BookId = value
  }

  type Title = String

  type Pair[X] = (X, X)
  val pair: Pair[Int] = (1, 2)

  val title: String = "Zionomicon"
}

/**
 * Negative compile-time tests for [[IsNominalType]] using Scala 3's
 * [[scala.compiletime.testing.typeCheckErrors]], which evaluates whether a code
 * snippet type-checks at compile time (not runtime).
 *
 * This proves the core guarantee from Github issue #1172: `IsNominalType[A]`
 * cannot be satisfied when `A` is an unresolved type parameter, so a DSL method
 * like `def contains[A: IsNominalType](a: A)` cannot be called from an
 * unconstrained generic context.
 *
 * Each negative test asserts the macro's own error message, not just that the
 * snippet fails: a snippet can fail for an unrelated reason (it did under
 * `-experimental`, with a cyclic-reference error), and a bare `!typeChecks`
 * would then pass while the macro accepts the type.
 */
object IsNominalTypeNegativeSpec extends ZIOSpecDefault {

  private def nominalTypeError(typeName: String): String =
    s"Cannot derive IsNominalType for non-nominal type: $typeName. " +
      "Only classes, traits, objects, enums, and applied types are supported. " +
      "Intersection types (A & B), union types (A | B), structural types, and type lambdas are not allowed."

  private def messages(errors: List[Error]): List[String] = errors.map(_.message)

  def spec = suite("IsNominalType — Scala 3 negative compile-time proofs")(
    test("unconstrained generic caller cannot forward abstract B to IsNominalType-constrained method") {
      // typeCheckErrors runs at compile time and returns the errors the snippet
      // would report. This is the definitive proof that IsNominalType blocks
      // abstract type parameters at compile time.
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalType
        def constrained[A: IsNominalType](a: A): String =
          IsNominalType[A].typeId.fullName
        def unconstrained[B](b: B): String = constrained(b)
        """
      )
      assertTrue(messages(errors) == List(nominalTypeError("B")))
    },
    test("IsNominalType cannot be summoned for a bare type parameter") {
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalType
        def foo[A]: IsNominalType[A] = summon[IsNominalType[A]]
        """
      )
      assertTrue(messages(errors) == List(nominalTypeError("A")))
    },
    test("IsNominalType cannot be summoned for an abstract type member") {
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalType
        trait Repo {
          type Key
          def keyType: IsNominalType[Key] = summon[IsNominalType[Key]]
        }
        """
      )
      assertTrue(messages(errors) == List(nominalTypeError("Repo.this.Key")))
    },
    test("IsNominalType cannot be summoned for a type parameter applied to arguments") {
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalType
        def foo[F[_]]: IsNominalType[F[Int]] = summon[IsNominalType[F[Int]]]
        """
      )
      assertTrue(messages(errors) == List(nominalTypeError("F[scala.Int]")))
    },
    test("IsNominalType cannot be summoned for the singleton type of a type parameter's value") {
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalType
        def foo[A](a: A): IsNominalType[a.type] = summon[IsNominalType[a.type]]
        """
      )
      assertTrue(messages(errors) == List(nominalTypeError("a")))
    },
    test("IsNominalIntersection cannot be summoned when a member is a type parameter") {
      val errors: List[Error] = typeCheckErrors(
        """
        import zio.blocks.typeid.IsNominalIntersection
        def foo[B]: IsNominalIntersection[String & B] = summon[IsNominalIntersection[String & B]]
        """
      )
      assertTrue(
        messages(errors) == List(
          "Cannot derive IsNominalIntersection: member type B is not a nominal type. " +
            "All intersection members must be classes, traits, objects, enums, or applied types."
        )
      )
    },
    test("IsNominalType CAN be summoned when the caller also requires it") {
      val compiles: Boolean = typeChecks(
        """
        import zio.blocks.typeid.IsNominalType
        def constrained[A: IsNominalType](a: A): String =
          IsNominalType[A].typeId.fullName
        def alsoConstrained[A: IsNominalType](a: A): String =
          constrained(a)
        """
      )
      assertTrue(compiles)
    },
    test("IsNominalType derives for an opaque type") {
      val ev = IsNominalType[NominalScala3Fixtures.BookId]
      assertTrue(ev.typeId.name == "BookId", ev.typeId.isOpaque)
    },
    test("IsNominalType derives for a type alias of a class") {
      val ev = IsNominalType[NominalScala3Fixtures.Title]
      assertTrue(ev.typeId.fullName == "java.lang.String")
    },
    test("IsNominalType derives for the singleton type of an object") {
      val ev = IsNominalType[NominalScala3Fixtures.BookId.type]
      assertTrue(ev.typeId.name == "BookId", ev.typeId.isObject)
    },
    test("IsNominalType derives for the singleton type of a value whose type is an applied alias") {
      val ev = IsNominalType[NominalScala3Fixtures.pair.type]
      assertTrue(
        ev.typeId.toString == "Pair[+T1, +T2]",
        ev.typeId.fullName == "zio.blocks.typeid.NominalScala3Fixtures.Pair"
      )
    },
    test("IsNominalType derives for the singleton type of a String value") {
      val ev = IsNominalType[NominalScala3Fixtures.title.type]
      assertTrue(ev.typeId.fullName == "scala.Predef.String")
    },
    test("IsNominalIntersection derives when every member is nominal") {
      val ev = IsNominalIntersection[NominalScala3Fixtures.BookId & NominalScala3Fixtures.Title]
      assertTrue(
        ev.typeIdsErased == Chunk(
          IsNominalType[NominalScala3Fixtures.BookId].typeIdErased,
          IsNominalType[String].typeIdErased
        )
      )
    }
  )
}
