package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import zio.prelude._

/**
 * Tests for round-trip conversions with ZIO Prelude newtypes using As.
 *
 * Newtypes with validation should work correctly in both directions when the
 * underlying values are valid.
 */
object NewtypeRoundTripSpec extends ZIOSpecDefault {

  // === Newtype Definitions ===
  object Types {
    object RtAge extends Subtype[Int] {
      override def assertion = assert {
        zio.prelude.Assertion.between(0, 150)
      }
    }
    type RtAge = RtAge.Type

    object RtEmail extends Newtype[String] {
      override def assertion = assert {
        zio.prelude.Assertion.contains("@")
      }
    }
    type RtEmail = RtEmail.Type

    object RtPositiveInt extends Subtype[Int] {
      override def assertion = assert {
        zio.prelude.Assertion.greaterThan(0)
      }
    }
    type RtPositiveInt = RtPositiveInt.Type
  }
  import Types._

  // Helper to create valid newtype values for testing (via make which validates)
  private def makeAge(value: Int): RtAge =
    RtAge.make(value).toEither.getOrElse(throw new IllegalArgumentException(s"Invalid age: $value"))

  // === Test Case Classes ===
  case class PersonRawA(name: String, age: Int)
  case class PersonValidatedB(name: String, age: RtAge)

  case class UserRawA(email: String, score: Int)
  case class UserValidatedB(email: RtEmail, score: RtPositiveInt)

  def spec = suite("NewtypeRoundTripSpec")(
    suite("Basic Newtype Round-Trip")(
      test("valid age round-trips through newtype") {
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
    suite("Multiple Newtypes Round-Trip")(
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
      test("from always succeeds with valid newtype values") {
        val validated = PersonValidatedB("Bob", makeAge(25))
        val as        = As.derived[PersonRawA, PersonValidatedB]

        val result = as.from(validated)

        assert(result)(isRight(equalTo(PersonRawA("Bob", 25))))
      },
      test("round-trip from validated to raw and back") {
        val validated = PersonValidatedB("Carol", makeAge(40))
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

        val validated = PersonValidatedB("Dave", makeAge(35))
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
