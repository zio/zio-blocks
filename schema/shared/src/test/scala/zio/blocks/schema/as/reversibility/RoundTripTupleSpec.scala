package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for round-trip conversions of tuple types using As.
 */
object RoundTripTupleSpec extends ZIOSpecDefault {

  // === Simple Tuples ===
  type Tuple2IntString  = (Int, String)
  type Tuple2IntStringB = (Int, String)

  // === Tuple to Case Class ===
  case class PairA(x: Int, y: String)
  case class PairB(x: Int, y: String)

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripTupleSpec")(
    suite("Tuple2 Round-Trip")(
      test("(Int, String) round-trips") {
        val original: (Int, String) = (42, "hello")
        val as                      = As.derived[(Int, String), (Int, String)]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("(String, Boolean) round-trips") {
        val original: (String, Boolean) = ("test", true)
        val as                          = As.derived[(String, Boolean), (String, Boolean)]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Tuple3 Round-Trip")(
      test("(Int, String, Boolean) round-trips") {
        val original: (Int, String, Boolean) = (1, "two", false)
        val as                               = As.derived[(Int, String, Boolean), (Int, String, Boolean)]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Tuple to Case Class Round-Trip")(
      test("tuple to case class and back") {
        val original: (Int, String) = (10, "test")
        val as                      = As.derived[(Int, String), PairA]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("case class to tuple and back") {
        val original = PairA(20, "value")
        val as       = As.derived[PairA, (Int, String)]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Nested Tuples Round-Trip")(
      test("nested tuple round-trips") {
        val original: ((Int, Int), String) = ((1, 2), "nested")
        val as                             = As.derived[((Int, Int), String), ((Int, Int), String)]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Empty Tuple (Unit) Round-Trip")(
      test("Tuple1 round-trips") {
        val original: Tuple1[Int] = Tuple1(42)
        val as                    = As.derived[Tuple1[Int], Tuple1[Int]]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    )
  )
}
