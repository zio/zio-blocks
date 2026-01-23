package zio.blocks.schema.as.reversibility

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for round-trip conversions of coproducts using As. */
object RoundTripCoproductSpec extends ZIOSpecDefault {

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

  sealed trait StatusA
  object StatusA {
    case object Active   extends StatusA
    case object Inactive extends StatusA
  }

  sealed trait StatusB
  object StatusB {
    case object Active   extends StatusB
    case object Inactive extends StatusB
  }

  def spec: Spec[TestEnvironment, Any] = suite("RoundTripCoproductSpec")(
    test("Circle case round-trips") {
      val original: ShapeA = ShapeA.Circle(10)
      val as               = As.derived[ShapeA, ShapeB]
      val roundTrip        = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("Rectangle case round-trips") {
      val original: ShapeA = ShapeA.Rectangle(20, 30)
      val as               = As.derived[ShapeA, ShapeB]
      val roundTrip        = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("case object round-trips") {
      val original: StatusA = StatusA.Active
      val as                = As.derived[StatusA, StatusB]
      val roundTrip         = as.into(original).flatMap(b => as.from(b))
      assert(roundTrip)(isRight(equalTo(original)))
    }
  )
}
