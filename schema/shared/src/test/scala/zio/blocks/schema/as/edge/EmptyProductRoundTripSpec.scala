package zio.blocks.schema.as.edge

import zio.blocks.schema._
import zio.test._

/**
 * Tests for round-trip conversions with empty products using As.
 *
 * Empty products (case classes with no fields, case objects) should round-trip
 * perfectly.
 */
object EmptyProductRoundTripSpec extends ZIOSpecDefault {

  // Empty case classes
  case class EmptyA()
  case class EmptyB()

  // Empty to empty with different names
  case class UnitTypeA()
  case class UnitTypeB()

  // Empty nested in product (same empty type both sides)
  case class WrapperSameA(empty: EmptyA, name: String)
  case class WrapperSameB(empty: EmptyA, name: String) // Using same EmptyA to avoid nested derivation

  // Empty in Option (same empty type)
  case class OptionalSameA(maybeEmpty: Option[EmptyA])
  case class OptionalSameB(maybeEmpty: Option[EmptyA])

  // Multiple empty fields (same types)
  case class MultipleEmptySameA(first: EmptyA, second: EmptyA)
  case class MultipleEmptySameB(first: EmptyA, second: EmptyA)

  def spec = suite("EmptyProductRoundTripSpec")(
    suite("Empty Case Class Round-Trip")(
      test("empty to empty round-trips correctly") {
        val original                             = EmptyA()
        implicit val emptyAs: As[EmptyA, EmptyB] = As.derived[EmptyA, EmptyB]

        val forward = emptyAs.into(original)
        assertTrue(forward == Right(EmptyB())) &&
        assertTrue(forward.flatMap(emptyAs.from) == Right(original))
      },
      test("empty with different names round-trips correctly") {
        val original                                  = UnitTypeA()
        implicit val unitAs: As[UnitTypeA, UnitTypeB] = As.derived[UnitTypeA, UnitTypeB]

        val forward = unitAs.into(original)
        assertTrue(forward == Right(UnitTypeB())) &&
        assertTrue(forward.flatMap(unitAs.from) == Right(original))
      },
      test("same empty type round-trips to itself") {
        val original                            = EmptyA()
        implicit val sameAs: As[EmptyA, EmptyA] = As.derived[EmptyA, EmptyA]

        val forward = sameAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(sameAs.from) == Right(original))
      }
    ),
    suite("Nested Empty Product Round-Trip (Same Types)")(
      test("wrapper with empty field round-trips correctly") {
        val original                                           = WrapperSameA(EmptyA(), "test")
        implicit val wrapperAs: As[WrapperSameA, WrapperSameB] = As.derived[WrapperSameA, WrapperSameB]

        val forward = wrapperAs.into(original)
        assertTrue(forward == Right(WrapperSameB(EmptyA(), "test"))) &&
        assertTrue(forward.flatMap(wrapperAs.from) == Right(original))
      },
      test("optional empty with Some round-trips correctly") {
        val original                                         = OptionalSameA(Some(EmptyA()))
        implicit val optAs: As[OptionalSameA, OptionalSameB] = As.derived[OptionalSameA, OptionalSameB]

        val forward = optAs.into(original)
        assertTrue(forward == Right(OptionalSameB(Some(EmptyA())))) &&
        assertTrue(forward.flatMap(optAs.from) == Right(original))
      },
      test("optional empty with None round-trips correctly") {
        val original                                         = OptionalSameA(None)
        implicit val optAs: As[OptionalSameA, OptionalSameB] = As.derived[OptionalSameA, OptionalSameB]

        val forward = optAs.into(original)
        assertTrue(forward == Right(OptionalSameB(None))) &&
        assertTrue(forward.flatMap(optAs.from) == Right(original))
      }
    ),
    suite("Multiple Empty Fields Round-Trip")(
      test("multiple same empty fields round-trip correctly") {
        val original                                                     = MultipleEmptySameA(EmptyA(), EmptyA())
        implicit val multiAs: As[MultipleEmptySameA, MultipleEmptySameB] =
          As.derived[MultipleEmptySameA, MultipleEmptySameB]

        val forward = multiAs.into(original)
        assertTrue(forward == Right(MultipleEmptySameB(EmptyA(), EmptyA()))) &&
        assertTrue(forward.flatMap(multiAs.from) == Right(original))
      }
    ),
    suite("Swap Operation")(
      test("swapped As works for empty products") {
        val original                             = EmptyB()
        implicit val emptyAs: As[EmptyA, EmptyB] = As.derived[EmptyA, EmptyB]
        val swapped                              = emptyAs.reverse

        val forward = swapped.into(original)
        assertTrue(forward == Right(EmptyA())) &&
        assertTrue(forward.flatMap(swapped.from) == Right(original))
      },
      test("double swap returns original behavior for empty products") {
        val original                             = EmptyA()
        implicit val emptyAs: As[EmptyA, EmptyB] = As.derived[EmptyA, EmptyB]
        val doubleSwapped                        = emptyAs.reverse.reverse

        val forward = doubleSwapped.into(original)
        assertTrue(forward == Right(EmptyB())) &&
        assertTrue(forward.flatMap(doubleSwapped.from) == Right(original))
      }
    )
  )
}
