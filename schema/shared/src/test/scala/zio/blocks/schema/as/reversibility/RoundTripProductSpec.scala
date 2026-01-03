package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for round-trip conversions of product types using As. */
object RoundTripProductSpec extends ZIOSpecDefault {

  case class PersonA(name: String, age: Int)
  case class PersonB(name: String, age: Int)

  case class WithOptionA(name: String, nickname: Option[String])
  case class WithOptionB(name: String, nickname: Option[String])

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripProductSpec")(
    test("round-trip: A -> B -> A") {
      val original  = PersonA("Alice", 30)
      val as        = As.derived[PersonA, PersonB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("round-trip: B -> A -> B") {
      val original  = PersonB("Bob", 25)
      val as        = As.derived[PersonA, PersonB]
      val roundTrip = as.from(original).flatMap(a => as.into(a))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("round-trip with Option[String] Some") {
      val original  = WithOptionA("Alice", Some("Ali"))
      val as        = As.derived[WithOptionA, WithOptionB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("round-trip with Option[String] None") {
      val original  = WithOptionA("Bob", None)
      val as        = As.derived[WithOptionA, WithOptionB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    }
  )
}
