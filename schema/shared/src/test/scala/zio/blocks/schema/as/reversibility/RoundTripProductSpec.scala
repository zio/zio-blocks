package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for round-trip conversions of product types (case classes) using As.
 *
 * As[A, B] provides bidirectional conversion where:
 *   - into(a: A) => Either[SchemaError, B]
 *   - from(b: B) => Either[SchemaError, A]
 *
 * Round-trip tests verify that: from(into(a)) == Right(a) and into(from(b)) ==
 * Right(b)
 */
object RoundTripProductSpec extends ZIOSpecDefault {

  // === Simple Case Classes ===
  case class PersonA(name: String, age: Int)
  case class PersonB(name: String, age: Int)

  case class PointA(x: Int, y: Int)
  case class PointB(x: Int, y: Int)

  // === Different Field Order ===
  case class OrderedA(first: String, second: Int, third: Boolean)
  case class OrderedB(first: String, second: Int, third: Boolean)

  // === With Options (both sides must have same optional fields for As) ===
  case class WithOptionA(name: String, nickname: Option[String])
  case class WithOptionB(name: String, nickname: Option[String])

  // === Nested Products ===
  case class InnerA(value: Int)
  case class InnerB(value: Int)
  case class OuterA(inner: InnerA, label: String)
  case class OuterB(inner: InnerB, label: String)

  // === Multiple Fields ===
  case class MultiA(a: Int, b: String, c: Boolean, d: Long)
  case class MultiB(a: Int, b: String, c: Boolean, d: Long)

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripProductSpec")(
    suite("Simple Product Round-Trip")(
      test("round-trip: A -> B -> A") {
        val original = PersonA("Alice", 30)
        val as       = As.derived[PersonA, PersonB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("round-trip: B -> A -> B") {
        val original = PersonB("Bob", 25)
        val as       = As.derived[PersonA, PersonB]

        val roundTrip = as.from(original).flatMap(a => as.into(a))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("into and from both succeed independently") {
        val a  = PersonA("Carol", 35)
        val b  = PersonB("Dave", 40)
        val as = As.derived[PersonA, PersonB]

        assertTrue(
          as.into(a).isRight,
          as.from(b).isRight
        )
      }
    ),
    suite("Point Round-Trip")(
      test("preserves x and y coordinates") {
        val original = PointA(10, 20)
        val as       = As.derived[PointA, PointB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("negative values round-trip correctly") {
        val original = PointA(-5, -10)
        val as       = As.derived[PointA, PointB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Multiple Fields Round-Trip")(
      test("all four fields preserved") {
        val original = MultiA(1, "test", true, 100L)
        val as       = As.derived[MultiA, MultiB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("different values round-trip correctly") {
        val original = MultiA(42, "hello", false, Long.MaxValue)
        val as       = As.derived[MultiA, MultiB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Option Fields Round-Trip")(
      test("Some value round-trips") {
        val original = WithOptionA("Alice", Some("Ali"))
        val as       = As.derived[WithOptionA, WithOptionB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("None value round-trips") {
        val original = WithOptionA("Bob", None)
        val as       = As.derived[WithOptionA, WithOptionB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Nested Products Round-Trip")(
      test("nested case class round-trips") {
        implicit val innerAs: As[InnerA, InnerB] = As.derived[InnerA, InnerB]
        val original                             = OuterA(InnerA(42), "outer")
        val as                                   = As.derived[OuterA, OuterB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("nested with different inner values") {
        implicit val innerAs: As[InnerA, InnerB] = As.derived[InnerA, InnerB]
        val original                             = OuterA(InnerA(-100), "label")
        val as                                   = As.derived[OuterA, OuterB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Empty and Single Field Products")(
      test("single field round-trips") {
        case class SingleA(value: String)
        case class SingleB(value: String)

        val original = SingleA("test")
        val as       = As.derived[SingleA, SingleB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Consistency Checks")(
      test("multiple round-trips produce same result") {
        val original = PersonA("Test", 50)
        val as       = As.derived[PersonA, PersonB]

        val trip1 = as.into(original).flatMap(as.from)
        val trip2 = as.into(original).flatMap(as.from).flatMap(as.into).flatMap(as.from)

        assertTrue(
          trip1 == trip2,
          trip1 == Right(original)
        )
      }
    )
  )
}
