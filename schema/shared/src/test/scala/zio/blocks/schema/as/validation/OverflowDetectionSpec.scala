package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for overflow detection in As bidirectional conversions.
 *
 * When using As with numeric narrowing (e.g., Long ↔ Int), the conversion
 * should fail if the value is outside the target type's range.
 */
object OverflowDetectionSpec extends ZIOSpecDefault {

  // === Case Classes with Widening/Narrowing Types ===
  case class LongValueA(value: Long)
  case class IntValueB(value: Int)

  case class IntValueA(value: Int)
  case class ShortValueB(value: Short)

  case class MultiLongA(a: Long, b: Long)
  case class MultiIntB(a: Int, b: Int)

  def spec: Spec[TestEnvironment, Any] = suite("OverflowDetectionSpec")(
    suite("Long to Int Overflow")(
      test("into fails when Long exceeds Int.MaxValue") {
        val source = LongValueA(Int.MaxValue.toLong + 1L)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.into(source)

        assert(result)(isLeft)
      },
      test("into fails when Long is below Int.MinValue") {
        val source = LongValueA(Int.MinValue.toLong - 1L)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.into(source)

        assert(result)(isLeft)
      },
      test("into succeeds at Int.MaxValue boundary") {
        val source = LongValueA(Int.MaxValue.toLong)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.into(source)

        assert(result)(isRight(equalTo(IntValueB(Int.MaxValue))))
      },
      test("into succeeds at Int.MinValue boundary") {
        val source = LongValueA(Int.MinValue.toLong)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.into(source)

        assert(result)(isRight(equalTo(IntValueB(Int.MinValue))))
      }
    ),
    suite("Int to Long Widening (from direction)")(
      test("from always succeeds (widening)") {
        val source = IntValueB(Int.MaxValue)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.from(source)

        assert(result)(isRight(equalTo(LongValueA(Int.MaxValue.toLong))))
      },
      test("from with negative value succeeds") {
        val source = IntValueB(Int.MinValue)
        val as     = As.derived[LongValueA, IntValueB]

        val result = as.from(source)

        assert(result)(isRight(equalTo(LongValueA(Int.MinValue.toLong))))
      }
    ),
    suite("Int to Short Overflow")(
      test("into fails when Int exceeds Short.MaxValue") {
        val source = IntValueA(Short.MaxValue.toInt + 1)
        val as     = As.derived[IntValueA, ShortValueB]

        val result = as.into(source)

        assert(result)(isLeft)
      },
      test("into fails when Int is below Short.MinValue") {
        val source = IntValueA(Short.MinValue.toInt - 1)
        val as     = As.derived[IntValueA, ShortValueB]

        val result = as.into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Multiple Fields Overflow")(
      test("fails on first field overflow") {
        val source = MultiLongA(Long.MaxValue, 100L)
        val as     = As.derived[MultiLongA, MultiIntB]

        val result = as.into(source)

        assert(result)(isLeft)
      },
      test("fails on second field overflow") {
        val source = MultiLongA(100L, Long.MaxValue)
        val as     = As.derived[MultiLongA, MultiIntB]

        val result = as.into(source)

        assert(result)(isLeft)
      },
      test("succeeds when all fields fit") {
        val source = MultiLongA(100L, 200L)
        val as     = As.derived[MultiLongA, MultiIntB]

        val result = as.into(source)

        assert(result)(isRight(equalTo(MultiIntB(100, 200))))
      }
    ),
    suite("Round-Trip with Valid Values")(
      test("round-trip succeeds when value fits in both directions") {
        val original = LongValueA(1000L)
        val as       = As.derived[LongValueA, IntValueB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("round-trip from B to A always succeeds (widening)") {
        val original = IntValueB(42)
        val as       = As.derived[LongValueA, IntValueB]

        val roundTrip = as.from(original).flatMap(a => as.into(a))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    )
  )
}
