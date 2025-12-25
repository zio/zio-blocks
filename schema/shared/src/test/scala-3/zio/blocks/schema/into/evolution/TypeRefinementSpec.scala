package zio.blocks.schema.into.evolution

import zio.test._
import zio.blocks.schema._

// Opaque types for testing
opaque type PositiveInt = Int
object PositiveInt {
  def apply(n: Int): Either[String, PositiveInt] =
    if (n > 0) Right(n) else Left(s"Must be positive: $n")
}

opaque type NonEmptyString = String
object NonEmptyString {
  def apply(s: String): Either[String, NonEmptyString] =
    if (s.nonEmpty) Right(s) else Left("String must be non-empty")
}

opaque type Age = Int
object Age {
  def apply(value: Int): Either[String, Age] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")
}

object TypeRefinementSpec extends ZIOSpecDefault {

  def spec = suite("TypeRefinementSpec")(
    suite("Simple Type Refinement")(
      test("should refine Int to PositiveInt via opaque type") {
        case class V1(value: Int)
        case class V2(value: PositiveInt)

        val derivation = Into.derived[V1, V2]
        val input1     = V1(42)
        val input2     = V1(-5)

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      },
      test("should refine String to NonEmptyString") {
        case class V1(name: String)
        case class V2(name: NonEmptyString)

        val derivation = Into.derived[V1, V2]
        val input1     = V1("Alice")
        val input2     = V1("")

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      },
      test("should refine with coercion (Long -> PositiveInt)") {
        case class V1(value: Long)
        case class V2(value: PositiveInt)

        val derivation = Into.derived[V1, V2]
        val input1     = V1(42L)
        val input2     = V1(-5L)

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      }
    ),
    suite("Refinement in Nested Structures")(
      test("should refine field in nested case class") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: NonEmptyString, age: Age)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input1     = PersonV1("Alice", 30)
        val input2     = PersonV1("", 30)     // Invalid name
        val input3     = PersonV1("Bob", 200) // Invalid age

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft &&
            derivation.into(input3).isLeft
        )
      },
      test("should refine in nested case class with multiple refinements") {
        case class AddressV1(street: String, number: Int)
        case class AddressV2(street: NonEmptyString, number: PositiveInt)

        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: NonEmptyString, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input1     = PersonV1("Alice", AddressV1("Main St", 123))
        val input2     = PersonV1("Bob", AddressV1("", 123)) // Invalid street

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      }
    ),
    suite("Refinement with Coercion")(
      test("should refine with numeric coercion (Int -> Long -> PositiveInt)") {
        case class V1(value: Int)
        case class V2(value: PositiveInt)

        val derivation = Into.derived[V1, V2]
        val input      = V1(42)

        assertTrue(derivation.into(input).isRight)
      },
      test("should refine with collection element coercion") {
        case class V1(values: List[Int])
        case class V2(values: List[PositiveInt])

        val derivation = Into.derived[V1, V2]
        val input1     = V1(List(1, 2, 3))
        val input2     = V1(List(1, -2, 3)) // One invalid

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      }
    ),
    suite("Multiple Refinements")(
      test("should refine multiple fields independently") {
        case class V1(name: String, age: Int, score: Int)
        case class V2(name: NonEmptyString, age: Age, score: PositiveInt)

        val derivation = Into.derived[V1, V2]
        val input1     = V1("Alice", 30, 95)
        val input2     = V1("", 30, 95)         // Invalid name
        val input3     = V1("Bob", 200, 95)     // Invalid age
        val input4     = V1("Charlie", 25, -10) // Invalid score

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft &&
            derivation.into(input3).isLeft &&
            derivation.into(input4).isLeft
        )
      },
      test("should accumulate validation errors for multiple invalid fields") {
        case class V1(name: String, age: Int, score: Int)
        case class V2(name: NonEmptyString, age: Age, score: PositiveInt)

        val derivation = Into.derived[V1, V2]
        val input      = V1("", 200, -10) // All invalid

        // Should fail with first error encountered
        assertTrue(derivation.into(input).isLeft)
      }
    ),
    suite("Refinement in Collections")(
      test("should refine elements in List") {
        case class V1(items: List[Int])
        case class V2(items: List[PositiveInt])

        val derivation = Into.derived[V1, V2]
        val input1     = V1(List(1, 2, 3))
        val input2     = V1(List(1, -2, 3))

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft
        )
      },
      test("should refine elements in Option") {
        case class V1(value: Option[Int])
        case class V2(value: Option[PositiveInt])

        val derivation = Into.derived[V1, V2]
        val input1     = V1(Some(42))
        val input2     = V1(Some(-5))
        val input3     = V1(None)

        assertTrue(
          derivation.into(input1).isRight &&
            derivation.into(input2).isLeft &&
            derivation.into(input3).map(_.value) == Right(None)
        )
      }
    )
  )
}
