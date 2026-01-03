package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._
import scala.compiletime.testing.typeCheckErrors

/**
 * Tests verifying that unknown/unsupported types produce proper compile-time
 * errors. Unknown types include regular classes, traits (non-sealed), and other
 * types that are not primitives, case classes, sealed traits, collections, or
 * tuples.
 */
object UnknownTypeErrorSpec extends ZIOSpecDefault {

  // Test types - defined outside tests for Scala 3 macro visibility
  class RegularClass(val x: Int)
  class Inner(val value: String)
  case class OuterWithInner(inner: Inner)
  case class RootWithOuter(outer: OuterWithInner)
  case class WithRegularClassField(m: RegularClass)
  case class WithOptionalRegularClass(m: Option[RegularClass])
  case class WithListOfRegularClass(m: List[RegularClass])
  case class WithMapOfRegularClass(m: Map[String, RegularClass])

  trait UnsealedTrait { def x: Int }
  case class WithUnsealedTrait(m: UnsealedTrait)
  case class WithVectorOfUnsealedTrait(m: Vector[UnsealedTrait])

  abstract class AbstractBase(val x: Int)
  case class WithAbstractClass(m: AbstractBase)

  // Sealed trait with unknown type field
  sealed trait StatusWithUnknown
  case class ActiveWithRegular(since: String, data: RegularClass) extends StatusWithUnknown
  case object InactiveStatus                                      extends StatusWithUnknown

  def spec = suite("UnknownTypeErrorSpec (Scala 3)")(
    suite("Regular Classes (not case classes)")(
      test("regular class field fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithRegularClassField]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("RegularClass"),
          errorMsg.contains("ToStructural only supports"),
          errorMsg.contains("Case classes"),
          errorMsg.contains("consider converting it to a case class")
        )
      },
      test("nested regular class fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[RootWithOuter]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("Inner"),
          errorMsg.contains("ToStructural only supports")
        )
      },
      test("regular class in Option fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithOptionalRegularClass]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("RegularClass"),
          errorMsg.contains("ToStructural only supports")
        )
      },
      test("regular class in List fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithListOfRegularClass]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("RegularClass"),
          errorMsg.contains("ToStructural only supports")
        )
      },
      test("regular class in Map value fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithMapOfRegularClass]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("RegularClass"),
          errorMsg.contains("ToStructural only supports")
        )
      }
    ),
    suite("Non-Sealed Traits")(
      test("non-sealed trait field fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithUnsealedTrait]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("UnsealedTrait"),
          errorMsg.contains("ToStructural only supports"),
          errorMsg.contains("Sealed traits and enums")
        )
      },
      test("non-sealed trait in collection fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithVectorOfUnsealedTrait]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("UnsealedTrait"),
          errorMsg.contains("ToStructural only supports")
        )
      }
    ),
    suite("Abstract Classes (non-sealed)")(
      test("abstract class field fails to compile") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithAbstractClass]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("AbstractBase"),
          errorMsg.contains("ToStructural only supports")
        )
      }
    ),
    suite("Unknown Types in Sealed Traits (Scala 3 only)")(
      test("sealed trait with case class containing unknown type fails") {
        inline def errors = typeCheckErrors("ToStructural.derived[StatusWithUnknown]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Cannot generate structural type for unsupported type"),
          errorMsg.contains("RegularClass"),
          errorMsg.contains("ToStructural only supports")
        )
      }
    ),
    suite("Error Message Quality")(
      test("error message lists all supported types") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithRegularClassField]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("Primitive types (Int, String, Boolean, etc.)"),
          errorMsg.contains("Case classes"),
          errorMsg.contains("Sealed traits and enums"),
          errorMsg.contains("Either types"),
          errorMsg.contains("Collections (List, Vector, Set, Seq, Option, Map)"),
          errorMsg.contains("Tuples")
        )
      },
      test("error message includes full type name") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithRegularClassField]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("is not supported"),
          errorMsg.contains("RegularClass")
        )
      },
      test("error message provides actionable suggestion") {
        inline def errors = typeCheckErrors("ToStructural.derived[WithRegularClassField]")
        val result        = errors
        val errorMsg      = result.headOption.map(_.message).getOrElse("")
        assertTrue(
          result.nonEmpty,
          errorMsg.contains("If this is a regular class"),
          errorMsg.contains("consider converting it to a case class")
        )
      }
    )
  )
}
