package zio.blocks.schema.as.reversibility

import zio.test._
import zio.blocks.schema._

// Opaque types for testing - must be at package level
opaque type TestId = String
object TestId {
  def apply(value: String): Either[String, TestId] =
    if (value.nonEmpty && value.length <= 50) Right(value)
    else Left(s"Id must be non-empty and at most 50 characters: $value")
}

opaque type TestAge = Int
object TestAge {
  def apply(value: Int): Either[String, TestAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Age must be between 0 and 150: $value")
}

object OpaqueTypeRoundTripSpec extends ZIOSpecDefault {

  def spec = suite("OpaqueTypeRoundTripSpec")(
    suite("Round Trip for Opaque Types")(
      test("should round trip String -> TestId -> String") {
        val as = As.derived[String, TestId]
        val input = "alice123"

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip TestId -> String -> TestId") {
        val as = As.derived[String, TestId]
        val id = TestId.apply("bob456").toOption.get

        val result = as.from(id).flatMap(as.into)

        assertTrue(result.isRight)
        result.map { idResult =>
          assertTrue(idResult.toString == "bob456")
        }
      },
      test("should fail round trip with invalid String -> TestId") {
        val as = As.derived[String, TestId]
        val invalidInput = "" // Empty string should fail validation

        val result = as.into(invalidInput)

        assertTrue(result.isLeft)
      },
      test("should round trip with case class containing opaque type") {
        case class User(name: String, id: String)
        case class ValidatedUser(name: String, id: TestId)

        val as = As.derived[User, ValidatedUser]
        val input = User("Alice", "alice123")

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip ValidatedUser -> User -> ValidatedUser") {
        case class User(name: String, id: String)
        case class ValidatedUser(name: String, id: TestId)

        val as = As.derived[User, ValidatedUser]
        val validated = ValidatedUser("Bob", TestId.apply("bob456").toOption.get)

        val result = as.from(validated).flatMap(as.into)

        assertTrue(result.isRight)
        result.map { validatedResult =>
          assertTrue(validatedResult.name == "Bob" && validatedResult.id.toString == "bob456")
        }
      }
    ),
    suite("Round Trip with Multiple Opaque Types")(
      test("should round trip with multiple opaque type fields") {
        case class Person(name: String, age: Int, email: String)
        case class ValidatedPerson(name: String, age: TestAge, email: String)

        val as = As.derived[Person, ValidatedPerson]
        val input = Person("Charlie", 30, "charlie@example.com")

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      }
    )
  )
}

