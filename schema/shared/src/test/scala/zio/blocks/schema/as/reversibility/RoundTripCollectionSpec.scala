package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for round-trip conversions with collection type changes using As.
 *
 * Note: Some collection conversions (e.g., List -> Set) may be lossy due to
 * duplicate removal. These tests focus on cases where round-trip is safe.
 */
object RoundTripCollectionSpec extends ZIOSpecDefault {

  // === Case Classes with Collections ===
  case class WithListA(items: List[Int])
  case class WithListB(items: List[Int])

  case class WithVectorA(items: Vector[String])
  case class WithVectorB(items: Vector[String])

  case class WithSetA(items: Set[Int])
  case class WithSetB(items: Set[Int])

  case class WithMapA(items: Map[String, Int])
  case class WithMapB(items: Map[String, Int])

  case class WithOptionA(value: Option[Int])
  case class WithOptionB(value: Option[Int])

  // === Nested Collections ===
  case class NestedListA(items: List[List[Int]])
  case class NestedListB(items: List[List[Int]])

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripCollectionSpec")(
    suite("List Round-Trip")(
      test("List[Int] round-trips") {
        val original = WithListA(List(1, 2, 3, 4, 5))
        val as       = As.derived[WithListA, WithListB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty List round-trips") {
        val original = WithListA(List.empty)
        val as       = As.derived[WithListA, WithListB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("single element List round-trips") {
        val original = WithListA(List(42))
        val as       = As.derived[WithListA, WithListB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Vector Round-Trip")(
      test("Vector[String] round-trips") {
        val original = WithVectorA(Vector("a", "b", "c"))
        val as       = As.derived[WithVectorA, WithVectorB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty Vector round-trips") {
        val original = WithVectorA(Vector.empty)
        val as       = As.derived[WithVectorA, WithVectorB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Set Round-Trip")(
      test("Set[Int] round-trips") {
        val original = WithSetA(Set(1, 2, 3))
        val as       = As.derived[WithSetA, WithSetB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty Set round-trips") {
        val original = WithSetA(Set.empty)
        val as       = As.derived[WithSetA, WithSetB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Map Round-Trip")(
      test("Map[String, Int] round-trips") {
        val original = WithMapA(Map("a" -> 1, "b" -> 2))
        val as       = As.derived[WithMapA, WithMapB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty Map round-trips") {
        val original = WithMapA(Map.empty)
        val as       = As.derived[WithMapA, WithMapB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Option Round-Trip")(
      test("Some round-trips") {
        val original = WithOptionA(Some(42))
        val as       = As.derived[WithOptionA, WithOptionB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("None round-trips") {
        val original = WithOptionA(None)
        val as       = As.derived[WithOptionA, WithOptionB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Nested Collections Round-Trip")(
      test("List[List[Int]] round-trips") {
        val original = NestedListA(List(List(1, 2), List(3, 4, 5)))
        val as       = As.derived[NestedListA, NestedListB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty nested List round-trips") {
        val original = NestedListA(List(List.empty, List.empty))
        val as       = As.derived[NestedListA, NestedListB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    )
  )
}
