package zio.blocks.schema.as.edge

import zio.blocks.schema._
import zio.test._

/** Tests for round-trip conversions with empty products using As. */
object EmptyProductRoundTripSpec extends ZIOSpecDefault {

  case class EmptyA()
  case class EmptyB()

  def spec: Spec[TestEnvironment, Any] = suite("EmptyProductRoundTripSpec")(
    test("empty to empty round-trips correctly") {
      val original = EmptyA()
      val as       = As.derived[EmptyA, EmptyB]
      val forward  = as.into(original)
      assertTrue(forward == Right(EmptyB()), forward.flatMap(as.from) == Right(original))
    },
    test("same empty type round-trips to itself") {
      val original = EmptyA()
      val as       = As.derived[EmptyA, EmptyA]
      val forward  = as.into(original)
      assertTrue(forward == Right(original), forward.flatMap(as.from) == Right(original))
    },
    test("empty case class in Option round-trips") {
      case class OptA(maybeEmpty: Option[EmptyA])
      case class OptB(maybeEmpty: Option[EmptyA])

      val original = OptA(Some(EmptyA()))
      val as       = As.derived[OptA, OptB]
      val forward  = as.into(original)
      assertTrue(forward == Right(OptB(Some(EmptyA()))), forward.flatMap(as.from) == Right(original))
    }
  )
}
