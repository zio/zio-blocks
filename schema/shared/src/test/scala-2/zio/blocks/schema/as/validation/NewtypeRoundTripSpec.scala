package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import zio.prelude._

/** Tests for round-trip conversions with ZIO Prelude newtypes using As. */
object NewtypeRoundTripSpec extends ZIOSpecDefault {

  object Types {
    object RtAge extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type RtAge = RtAge.Type
  }
  import Types._

  case class PersonRawA(name: String, age: Int)
  case class PersonValidatedB(name: String, age: RtAge)

  def spec = suite("NewtypeRoundTripSpec")(
    test("round-trip with newtype field") {
      val original = PersonRawA("Alice", 30)
      val as       = As.derived[PersonRawA, PersonValidatedB]

      val roundTrip = as.into(original).flatMap(b => as.from(b))

      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("reverse works with newtype") {
      val as       = As.derived[PersonRawA, PersonValidatedB]
      val reversed = as.reverse

      val validated = PersonValidatedB("Bob", RtAge.make(25).toEither.toOption.get)
      val result    = reversed.into(validated)

      assert(result)(isRight(equalTo(PersonRawA("Bob", 25))))
    }
  )
}
