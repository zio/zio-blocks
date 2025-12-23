package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

// === Opaque Type Definitions ===
opaque type RoundTripAge = Int
object RoundTripAge {
  def apply(value: Int): Either[String, RoundTripAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")

  def unsafe(value: Int): RoundTripAge = value

  extension (age: RoundTripAge) def toInt: Int = age
}

opaque type RoundTripEmail = String
object RoundTripEmail {
  def apply(value: String): Either[String, RoundTripEmail] =
    if (value.contains("@")) Right(value)
    else Left(s"Invalid email: $value")

  def unsafe(value: String): RoundTripEmail = value

  extension (email: RoundTripEmail) def value: String = email
}

opaque type RoundTripPositiveInt = Int
object RoundTripPositiveInt {
  def apply(value: Int): Either[String, RoundTripPositiveInt] =
    if (value > 0) Right(value)
    else Left(s"Value must be positive: $value")

  def unsafe(value: Int): RoundTripPositiveInt = value

  extension (pi: RoundTripPositiveInt) def toInt: Int = pi
}

/**
 * Tests for round-trip conversions with Scala 3 opaque types using As.
 *
 * Opaque types with validation should work correctly in both directions when
 * the underlying values are valid.
 */
object OpaqueTypeRoundTripSpec extends ZIOSpecDefault {

  // === Test Case Classes ===
  case class PersonRawA(name: String, age: Int)
  case class PersonValidatedB(name: String, age: RoundTripAge)

  case class UserRawA(email: String, score: Int)
  case class UserValidatedB(email: RoundTripEmail, score: RoundTripPositiveInt)

  def spec: Spec[TestEnvironment, Any] = suite("OpaqueTypeRoundTripSpec")(
    suite("Basic Opaque Type Round-Trip")(
      test("valid age round-trips through opaque type") {
        val original = PersonRawA("Alice", 30)
        val as       = As.derived[PersonRawA, PersonValidatedB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("boundary age (0) round-trips") {
        val original = PersonRawA("Baby", 0)
        val as       = As.derived[PersonRawA, PersonValidatedB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("boundary age (150) round-trips") {
        val original = PersonRawA("Elder", 150)
        val as       = As.derived[PersonRawA, PersonValidatedB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Invalid Values Fail")(
      test("invalid age fails into direction") {
        val original = PersonRawA("Invalid", -5)
        val as       = As.derived[PersonRawA, PersonValidatedB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("age over 150 fails into direction") {
        val original = PersonRawA("TooOld", 200)
        val as       = As.derived[PersonRawA, PersonValidatedB]

        val result = as.into(original)

        assert(result)(isLeft)
      }
    ),
    suite("Multiple Opaque Types Round-Trip")(
      test("valid email and score round-trip") {
        val original = UserRawA("user@example.com", 100)
        val as       = As.derived[UserRawA, UserValidatedB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("invalid email fails") {
        val original = UserRawA("invalid-email", 100)
        val as       = As.derived[UserRawA, UserValidatedB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("invalid score (zero) fails") {
        val original = UserRawA("user@example.com", 0)
        val as       = As.derived[UserRawA, UserValidatedB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("invalid score (negative) fails") {
        val original = UserRawA("user@example.com", -10)
        val as       = As.derived[UserRawA, UserValidatedB]

        val result = as.into(original)

        assert(result)(isLeft)
      }
    ),
    suite("From Direction (Validated to Raw)")(
      test("from always succeeds with valid opaque values") {
        val validated = PersonValidatedB("Bob", RoundTripAge.unsafe(25))
        val as        = As.derived[PersonRawA, PersonValidatedB]

        val result = as.from(validated)

        assert(result)(isRight(equalTo(PersonRawA("Bob", 25))))
      },
      test("round-trip from validated to raw and back") {
        val validated = PersonValidatedB("Carol", RoundTripAge.unsafe(40))
        val as        = As.derived[PersonRawA, PersonValidatedB]

        // B -> A -> B
        val roundTrip = as.from(validated).flatMap(a => as.into(a))

        assert(roundTrip)(isRight(equalTo(validated)))
      }
    ),
    suite("Swap Direction")(
      test("swapped As works correctly") {
        val as      = As.derived[PersonRawA, PersonValidatedB]
        val swapped = as.reverse

        val validated = PersonValidatedB("Dave", RoundTripAge.unsafe(35))
        val result    = swapped.into(validated)

        assert(result)(isRight(equalTo(PersonRawA("Dave", 35))))
      },
      test("double swap returns to original") {
        val as            = As.derived[PersonRawA, PersonValidatedB]
        val doubleSwapped = as.reverse.reverse

        val original = PersonRawA("Eve", 28)
        val result   = doubleSwapped.into(original)

        assertTrue(result == as.into(original))
      }
    )
  )
}
