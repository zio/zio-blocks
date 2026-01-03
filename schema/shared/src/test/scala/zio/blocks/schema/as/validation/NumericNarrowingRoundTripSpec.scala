package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for numeric narrowing round-trips using As. */
object NumericNarrowingRoundTripSpec extends ZIOSpecDefault {

  case class WideA(value: Long)
  case class NarrowB(value: Int)

  def spec: Spec[TestEnvironment, Any] = suite("NumericNarrowingRoundTripSpec")(
    test("value within Int range round-trips") {
      val original  = WideA(1000L)
      val as        = As.derived[WideA, NarrowB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("Int.MaxValue round-trips") {
      val original  = WideA(Int.MaxValue.toLong)
      val as        = As.derived[WideA, NarrowB]
      val roundTrip = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("value above Int.MaxValue fails into") {
      val original = WideA(Int.MaxValue.toLong + 1L)
      val as       = As.derived[WideA, NarrowB]
      assert(as.into(original))(isLeft)
    },
    test("from always succeeds (widening)") {
      val b  = NarrowB(Int.MaxValue)
      val as = As.derived[WideA, NarrowB]
      assert(as.from(b))(isRight(equalTo(WideA(Int.MaxValue.toLong))))
    }
  )
}
