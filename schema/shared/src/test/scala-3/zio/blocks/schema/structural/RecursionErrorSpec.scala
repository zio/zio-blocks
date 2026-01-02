package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._
import scala.compiletime.testing.typeCheckErrors

object RecursionErrorSpec extends ZIOSpecDefault {

  // Test types for compile-time error checking
  case class DirectRecursive(child: DirectRecursive)
  case class ListRecursive(children: List[ListRecursive])
  case class OptionRecursive(next: Option[OptionRecursive])

  case class MutualA(b: MutualB)
  case class MutualB(a: MutualA)

  sealed trait SealedStatus
  case object Active extends SealedStatus

  class NotCaseClass(val x: Int)

  def spec = suite("Recursion Detection")(
    test("direct recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[DirectRecursive]")
      val result        = errors
      val errorMsg      = result.headOption.map(_.message).getOrElse("")
      assertTrue(
        result.nonEmpty,
        errorMsg.contains("Cannot generate structural type"),
        errorMsg.contains("recursive type detected"),
        errorMsg.contains("DirectRecursive"),
        errorMsg.contains("Structural types cannot represent recursive structures")
      )
    },
    test("list recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[ListRecursive]")
      val result        = errors
      val errorMsg      = result.headOption.map(_.message).getOrElse("")
      assertTrue(
        result.nonEmpty,
        errorMsg.contains("Cannot generate structural type"),
        errorMsg.contains("recursive type detected"),
        errorMsg.contains("ListRecursive"),
        errorMsg.contains("Structural types cannot represent recursive structures")
      )
    },
    test("option recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[OptionRecursive]")
      val result        = errors
      val errorMsg      = result.headOption.map(_.message).getOrElse("")
      assertTrue(
        result.nonEmpty,
        errorMsg.contains("Cannot generate structural type"),
        errorMsg.contains("recursive type detected"),
        errorMsg.contains("OptionRecursive"),
        errorMsg.contains("Structural types cannot represent recursive structures")
      )
    },
    test("mutual recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[MutualA]")
      val result        = errors
      val errorMsg      = result.headOption.map(_.message).getOrElse("")
      assertTrue(
        result.nonEmpty,
        errorMsg.contains("Cannot generate structural type"),
        errorMsg.contains("mutually recursive types detected") || errorMsg.contains("recursive type detected"),
        errorMsg.contains("MutualA") || errorMsg.contains("MutualB"),
        errorMsg.contains("Structural types cannot represent") &&
          (errorMsg.contains("cyclic dependencies") || errorMsg.contains("recursive structures"))
      )
    },
    test("non-case class fails") {
      inline def errors = typeCheckErrors("ToStructural.derived[NotCaseClass]")
      val result        = errors
      val errorMsg      = result.headOption.map(_.message).getOrElse("")
      assertTrue(
        result.nonEmpty,
        errorMsg.contains("ToStructural derivation requires a case class or sealed trait"),
        errorMsg.contains("NotCaseClass")
      )
    }
  )
}
