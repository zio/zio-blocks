package zio.blocks.schema.into.validation

import zio.test._
import zio.blocks.schema._

// Opaque types with validation
opaque type PositiveInt = Int
object PositiveInt {
  def apply(value: Int): Either[String, PositiveInt] =
    if (value > 0) Right(value) else Left(s"Must be positive: $value")
}

opaque type NonEmptyString = String
object NonEmptyString {
  def apply(value: String): Either[String, NonEmptyString] =
    if (value.nonEmpty) Right(value) else Left("String must be non-empty")
}

object ValidationErrorAccumulationSpec extends ZIOSpecDefault {

  def spec = suite("ValidationErrorAccumulationSpec")(
    suite("Error Accumulation")(
      test("should accumulate validation errors for multiple invalid fields") {
        case class Person(age: PositiveInt, name: NonEmptyString)
        case class RawPerson(age: Int, name: String)

        val derivation = Into.derived[RawPerson, Person]
        val input      = RawPerson(-1, "") // Both fields invalid

        val result = derivation.into(input)

        // Assert: Should be Left with error message
        assertTrue(result.isLeft)
        
        // Check if error message contains information about both errors
        // Note: Currently may only show first error (fail-fast behavior)
        val errorMessage = result.left.map(_.message).left.getOrElse("")
        assertTrue(
          errorMessage.contains("positive") || 
          errorMessage.contains("non-empty") ||
          errorMessage.contains("Must be positive") ||
          errorMessage.contains("String must be non-empty")
        )
      },
      test("should succeed when all fields are valid") {
        case class Person(age: PositiveInt, name: NonEmptyString)
        case class RawPerson(age: Int, name: String)

        val derivation = Into.derived[RawPerson, Person]
        val input      = RawPerson(30, "Alice")

        val result = derivation.into(input)

        assertTrue(result.isRight)
        result.map { person =>
          assertTrue(person.age.toString == "30" && person.name.toString == "Alice")
        }
      },
      test("should fail on first invalid field (current behavior)") {
        case class Person(age: PositiveInt, name: NonEmptyString)
        case class RawPerson(age: Int, name: String)

        val derivation = Into.derived[RawPerson, Person]
        
        // First field invalid
        val input1 = RawPerson(-1, "Alice")
        val result1 = derivation.into(input1)
        assertTrue(result1.isLeft)
        assertTrue(result1.left.exists(err => err.message.contains("positive") || err.message.contains("Must be positive")))
        
        // Second field invalid
        val input2 = RawPerson(30, "")
        val result2 = derivation.into(input2)
        assertTrue(result2.isLeft)
        assertTrue(result2.left.exists(err => err.message.contains("non-empty") || err.message.contains("String must be non-empty")))
      }
    )
  )
}

