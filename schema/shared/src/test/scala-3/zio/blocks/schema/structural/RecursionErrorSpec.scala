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
      assertTrue(
        result.nonEmpty,
        result.exists(_.message.toLowerCase.contains("recursive"))
      )
    },
    test("list recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[ListRecursive]")
      val result        = errors
      assertTrue(
        result.nonEmpty,
        result.exists(_.message.toLowerCase.contains("recursive"))
      )
    },
    test("option recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[OptionRecursive]")
      val result        = errors
      assertTrue(
        result.nonEmpty,
        result.exists(_.message.toLowerCase.contains("recursive"))
      )
    },
    test("mutual recursion fails to compile") {
      inline def errors = typeCheckErrors("ToStructural.derived[MutualA]")
      val result        = errors
      assertTrue(
        result.nonEmpty,
        result.exists(e => e.message.toLowerCase.contains("recursive") || e.message.toLowerCase.contains("mutual"))
      )
    },
    test("non-case class fails") {
      inline def errors = typeCheckErrors("ToStructural.derived[NotCaseClass]")
      val result        = errors
      assertTrue(
        result.nonEmpty,
        result.exists(_.message.toLowerCase.contains("case class"))
      )
    }
  )
}
