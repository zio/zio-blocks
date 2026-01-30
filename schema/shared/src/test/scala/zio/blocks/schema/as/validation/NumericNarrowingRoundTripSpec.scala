package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for numeric narrowing round-trips using As.
 *
 * Narrowing conversions (e.g., Long -> Int) may fail during the narrowing
 * direction but should always succeed in the widening direction.
 */
object NumericNarrowingRoundTripSpec extends ZIOSpecDefault {

  // === Case Classes for Narrowing Tests ===
  case class WideA(value: Long)
  case class NarrowB(value: Int)

  case class WideMultiA(a: Long, b: Long, c: Long)
  case class NarrowMultiB(a: Int, b: Int, c: Int)

  case class MixedWideA(name: String, count: Long, score: Long)
  case class MixedNarrowB(name: String, count: Int, score: Int)

  def spec: Spec[TestEnvironment, Any] = suite("NumericNarrowingRoundTripSpec")(
    suite("Long to Int Round-Trip")(
      test("value within Int range round-trips") {
        val original = WideA(1000L)
        val as       = As.derived[WideA, NarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Int.MaxValue round-trips") {
        val original = WideA(Int.MaxValue.toLong)
        val as       = As.derived[WideA, NarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Int.MinValue round-trips") {
        val original = WideA(Int.MinValue.toLong)
        val as       = As.derived[WideA, NarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("zero round-trips") {
        val original = WideA(0L)
        val as       = As.derived[WideA, NarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("negative value in range round-trips") {
        val original = WideA(-12345L)
        val as       = As.derived[WideA, NarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Overflow Fails into Direction")(
      test("value above Int.MaxValue fails into") {
        val original = WideA(Int.MaxValue.toLong + 1L)
        val as       = As.derived[WideA, NarrowB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("value below Int.MinValue fails into") {
        val original = WideA(Int.MinValue.toLong - 1L)
        val as       = As.derived[WideA, NarrowB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("Long.MaxValue fails into") {
        val original = WideA(Long.MaxValue)
        val as       = As.derived[WideA, NarrowB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("Long.MinValue fails into") {
        val original = WideA(Long.MinValue)
        val as       = As.derived[WideA, NarrowB]

        val result = as.into(original)

        assert(result)(isLeft)
      }
    ),
    suite("Widening Direction Always Succeeds")(
      test("from direction (Int -> Long) always succeeds") {
        val narrow = NarrowB(Int.MaxValue)
        val as     = As.derived[WideA, NarrowB]

        val result = as.from(narrow)

        assert(result)(isRight(equalTo(WideA(Int.MaxValue.toLong))))
      },
      test("from with Int.MinValue succeeds") {
        val narrow = NarrowB(Int.MinValue)
        val as     = As.derived[WideA, NarrowB]

        val result = as.from(narrow)

        assert(result)(isRight(equalTo(WideA(Int.MinValue.toLong))))
      },
      test("round-trip from narrow to wide and back (widening first)") {
        val narrow = NarrowB(42)
        val as     = As.derived[WideA, NarrowB]

        // B -> A -> B (widening then narrowing)
        val roundTrip = as.from(narrow).flatMap(a => as.into(a))

        assert(roundTrip)(isRight(equalTo(narrow)))
      }
    ),
    suite("Multiple Fields Round-Trip")(
      test("all fields within range round-trip") {
        val original = WideMultiA(100L, 200L, 300L)
        val as       = As.derived[WideMultiA, NarrowMultiB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("first field overflow fails") {
        val original = WideMultiA(Long.MaxValue, 200L, 300L)
        val as       = As.derived[WideMultiA, NarrowMultiB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("second field overflow fails") {
        val original = WideMultiA(100L, Long.MaxValue, 300L)
        val as       = As.derived[WideMultiA, NarrowMultiB]

        val result = as.into(original)

        assert(result)(isLeft)
      },
      test("third field overflow fails") {
        val original = WideMultiA(100L, 200L, Long.MaxValue)
        val as       = As.derived[WideMultiA, NarrowMultiB]

        val result = as.into(original)

        assert(result)(isLeft)
      }
    ),
    suite("Mixed Fields Round-Trip")(
      test("string and numeric fields round-trip") {
        val original = MixedWideA("test", 100L, 200L)
        val as       = As.derived[MixedWideA, MixedNarrowB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("numeric overflow with valid string fails") {
        val original = MixedWideA("test", Long.MaxValue, 200L)
        val as       = As.derived[MixedWideA, MixedNarrowB]

        val result = as.into(original)

        assert(result)(isLeft)
      }
    ),
    suite("Swap with Narrowing")(
      test("swapped As narrows in from direction") {
        val as      = As.derived[WideA, NarrowB]
        val swapped = as.reverse // Now: into narrows (B -> A), from widens (A -> B)

        val narrow = NarrowB(42)
        val result = swapped.into(narrow) // This is widening

        assert(result)(isRight(equalTo(WideA(42L))))
      },
      test("swapped As may fail in from direction on overflow") {
        val as      = As.derived[WideA, NarrowB]
        val swapped = as.reverse

        val wide   = WideA(Long.MaxValue)
        val result = swapped.from(wide) // This is narrowing

        assert(result)(isLeft)
      }
    )
  )
}
