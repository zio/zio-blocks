package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for round-trip conversions of tuples using As. */
object RoundTripTupleSpec extends ZIOSpecDefault {

  case class PairA(x: Int, y: String)

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripTupleSpec")(
    test("(Int, String) round-trips") {
      val original: (Int, String) = (42, "hello")
      val as                      = As.derived[(Int, String), (Int, String)]
      val roundTrip               = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("tuple to case class and back") {
      val original: (Int, String) = (10, "test")
      val as                      = As.derived[(Int, String), PairA]
      val roundTrip               = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("case class to tuple and back") {
      val original  = PairA(20, "value")
      val as        = As.derived[PairA, (Int, String)]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    }
  )
}
