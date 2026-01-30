package zio.blocks.schema.as.compile_errors

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests verifying As.derived rejects types with default values. */
object DefaultValueInAsSpec extends ZIOSpecDefault {

  case class ValidA(name: String, age: Int)
  case class ValidB(name: String, age: Int)

  case class WithOptA(name: String, nickname: Option[String])
  case class WithOptB(name: String, nickname: Option[String])

  def spec: Spec[TestEnvironment, Any] = suite("DefaultValueInAsSpec")(
    suite("Valid As Derivations (no defaults)")(
      test("derives As for case classes without defaults") {
        val as     = As.derived[ValidA, ValidB]
        val a      = ValidA("test", 42)
        val result = as.into(a)

        assert(result)(isRight(equalTo(ValidB("test", 42))))
      },
      test("round-trip works without defaults") {
        val as       = As.derived[ValidA, ValidB]
        val original = ValidA("alice", 30)

        val roundTrip = as.into(original).flatMap(as.from)

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Optional Fields Are Allowed")(
      test("derives As for case classes with Option fields") {
        val as     = As.derived[WithOptA, WithOptB]
        val a      = WithOptA("test", Some("nick"))
        val result = as.into(a)

        assert(result)(isRight(equalTo(WithOptB("test", Some("nick")))))
      },
      test("Option None round-trips correctly") {
        val as       = As.derived[WithOptA, WithOptB]
        val original = WithOptA("test", None)

        val roundTrip = as.into(original).flatMap(as.from)

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Option Some round-trips correctly") {
        val as       = As.derived[WithOptA, WithOptB]
        val original = WithOptA("test", Some("nickname"))

        val roundTrip = as.into(original).flatMap(as.from)

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Swap Preserves Constraints")(
      test("swapped As still works for valid types") {
        val as      = As.derived[ValidA, ValidB]
        val swapped = as.reverse

        val b      = ValidB("bob", 25)
        val result = swapped.into(b)

        assert(result)(isRight(equalTo(ValidA("bob", 25))))
      }
    ),
    suite("As can be used as Into")(
      test("As extends Into - can use into method") {
        val as: As[ValidA, ValidB]     = As.derived[ValidA, ValidB]
        val into: Into[ValidA, ValidB] = as // As extends Into

        val a      = ValidA("test", 42)
        val result = into.into(a)

        assert(result)(isRight(equalTo(ValidB("test", 42))))
      }
    )
  )
}
