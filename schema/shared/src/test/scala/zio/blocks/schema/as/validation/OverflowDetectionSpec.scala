package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for overflow detection in As bidirectional conversions. */
object OverflowDetectionSpec extends ZIOSpecDefault {

  case class LongValueA(value: Long)
  case class IntValueB(value: Int)

  def spec: Spec[TestEnvironment, Any] = suite("OverflowDetectionSpec")(
    test("into fails when Long exceeds Int.MaxValue") {
      val source = LongValueA(Int.MaxValue.toLong + 1L)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.into(source))(isLeft)
    },
    test("into fails when Long is below Int.MinValue") {
      val source = LongValueA(Int.MinValue.toLong - 1L)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.into(source))(isLeft)
    },
    test("into succeeds at Int boundary values") {
      val as     = As.derived[LongValueA, IntValueB]
      val resMax = as.into(LongValueA(Int.MaxValue.toLong))
      val resMin = as.into(LongValueA(Int.MinValue.toLong))
      assert(resMax)(isRight(equalTo(IntValueB(Int.MaxValue)))) &&
      assert(resMin)(isRight(equalTo(IntValueB(Int.MinValue))))
    },
    test("from always succeeds (widening)") {
      val source = IntValueB(Int.MaxValue)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.from(source))(isRight(equalTo(LongValueA(Int.MaxValue.toLong))))
    }
  )
}
