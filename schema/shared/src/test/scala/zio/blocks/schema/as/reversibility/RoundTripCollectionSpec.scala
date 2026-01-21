package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for round-trip conversions with collections using As. */
object RoundTripCollectionSpec extends ZIOSpecDefault {

  case class WithListA(items: List[Int])
  case class WithListB(items: List[Int])

  case class WithSetA(items: Set[Int])
  case class WithSetB(items: Set[Int])

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripCollectionSpec")(
    test("List[Int] round-trips") {
      val original  = WithListA(List(1, 2, 3))
      val as        = As.derived[WithListA, WithListB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("empty List round-trips") {
      val original  = WithListA(List.empty)
      val as        = As.derived[WithListA, WithListB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("Set[Int] round-trips") {
      val original  = WithSetA(Set(1, 2, 3))
      val as        = As.derived[WithSetA, WithSetB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    }
  )
}
