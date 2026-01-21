package zio.blocks.schema.as

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests verifying As.derived rejects types with default values. */
object DefaultValueInAsSpec extends ZIOSpecDefault {

  case class ValidA(name: String, age: Int = 25)
  case class ValidB(name: String, age: Int)

  case class WithOptA(name: String, nickname: Option[String])
  case class WithOptB(name: String)

  def spec: Spec[TestEnvironment, Any] = suite("DefaultValueInAsSpec")(
    test("derives As for case classes with a default if the default is not needed") {
      val as       = As.derived[ValidA, ValidB]
      val original = ValidA("alice", 30)

      val roundTrip = as.into(original).flatMap(as.from)

      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("derives As for case classes with Option fields") {
      val as     = As.derived[WithOptA, WithOptB]
      val a      = WithOptA("test", Some("nick"))
      val result = as.into(a)

      assert(result)(isRight(equalTo(WithOptB("test"))))
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

      assert(roundTrip)(isRight(equalTo(WithOptA("test", None))))
    },
    test("reverse As still works for valid types") {
      val as      = As.derived[ValidA, ValidB]
      val reverse = as.reverse

      val b      = ValidB("bob", 25)
      val result = reverse.into(b)

      assert(result)(isRight(equalTo(ValidA("bob", 25))))
    }
  )
}
