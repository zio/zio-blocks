package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for round-trip conversions of coproduct types (sealed traits) using As.
 */
object RoundTripCoproductSpec extends ZIOSpecDefault {

  // === Simple Sealed Traits ===
  sealed trait ShapeA
  object ShapeA {
    case class Circle(radius: Int)                extends ShapeA
    case class Rectangle(width: Int, height: Int) extends ShapeA
  }

  sealed trait ShapeB
  object ShapeB {
    case class Circle(radius: Int)                extends ShapeB
    case class Rectangle(width: Int, height: Int) extends ShapeB
  }

  // === With Case Objects ===
  sealed trait StatusA
  object StatusA {
    case object Active                extends StatusA
    case object Inactive              extends StatusA
    case class Error(message: String) extends StatusA
  }

  sealed trait StatusB
  object StatusB {
    case object Active                extends StatusB
    case object Inactive              extends StatusB
    case class Error(message: String) extends StatusB
  }

  // === Nested Coproducts ===
  sealed trait OuterCoproductA
  object OuterCoproductA {
    case class Leaf(value: Int)      extends OuterCoproductA
    case class Branch(shape: ShapeA) extends OuterCoproductA
  }

  sealed trait OuterCoproductB
  object OuterCoproductB {
    case class Leaf(value: Int)      extends OuterCoproductB
    case class Branch(shape: ShapeB) extends OuterCoproductB
  }

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripCoproductSpec")(
    suite("Simple Coproduct Round-Trip")(
      test("Circle case round-trips") {
        val original: ShapeA = ShapeA.Circle(10)
        val as               = As.derived[ShapeA, ShapeB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Rectangle case round-trips") {
        val original: ShapeA = ShapeA.Rectangle(20, 30)
        val as               = As.derived[ShapeA, ShapeB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("B -> A -> B round-trip") {
        val original: ShapeB = ShapeB.Circle(15)
        val as               = As.derived[ShapeA, ShapeB]

        val roundTrip = as.from(original).flatMap(a => as.into(a))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("Coproduct with Case Objects")(
      test("Active case object round-trips") {
        val original: StatusA = StatusA.Active
        val as                = As.derived[StatusA, StatusB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Inactive case object round-trips") {
        val original: StatusA = StatusA.Inactive
        val as                = As.derived[StatusA, StatusB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Error case class round-trips") {
        val original: StatusA = StatusA.Error("Something went wrong")
        val as                = As.derived[StatusA, StatusB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("All Cases Round-Trip")(
      test("all cases of StatusA round-trip correctly") {
        val as = As.derived[StatusA, StatusB]

        val cases: List[StatusA] = List(
          StatusA.Active,
          StatusA.Inactive,
          StatusA.Error("error1"),
          StatusA.Error("error2")
        )

        val results = cases.map { original =>
          as.into(original).flatMap(as.from) == Right(original)
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("Nested Coproducts Round-Trip")(
      test("Leaf case round-trips") {
        implicit val shapeAs: As[ShapeA, ShapeB] = As.derived[ShapeA, ShapeB]
        val original: OuterCoproductA            = OuterCoproductA.Leaf(42)
        val as                                   = As.derived[OuterCoproductA, OuterCoproductB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Branch with Circle round-trips") {
        implicit val shapeAs: As[ShapeA, ShapeB] = As.derived[ShapeA, ShapeB]
        val original: OuterCoproductA            = OuterCoproductA.Branch(ShapeA.Circle(5))
        val as                                   = As.derived[OuterCoproductA, OuterCoproductB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Branch with Rectangle round-trips") {
        implicit val shapeAs: As[ShapeA, ShapeB] = As.derived[ShapeA, ShapeB]
        val original: OuterCoproductA            = OuterCoproductA.Branch(ShapeA.Rectangle(10, 20))
        val as                                   = As.derived[OuterCoproductA, OuterCoproductB]

        val roundTrip = as.into(original).flatMap(b => as.from(b))

        assert(roundTrip)(isRight(equalTo(original)))
      }
    )
  )
}
