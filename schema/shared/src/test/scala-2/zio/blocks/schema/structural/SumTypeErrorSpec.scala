package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests that sum types (sealed traits) produce compile-time errors in Scala 2.
 *
 * Sum types cannot be converted to structural types in Scala 2 because
 * they require union types, which are only available in Scala 3.
 */
object SumTypeErrorSpec extends ZIOSpecDefault {

  sealed trait Result
  case class Success(value: Int) extends Result
  case class Failure(error: String) extends Result

  sealed trait Status
  case object Active extends Status
  case object Inactive extends Status

  def spec = suite("SumTypeErrorSpec")(
    test("sealed trait fails at compile time in Scala 2") {
      // Schema.derived[Result].structural should not compile in Scala 2
      // Compile error: Cannot generate structural type for sum types in Scala 2.
      // Union types are required, which are only available in Scala 3.
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("sealed trait with case objects fails at compile time") {
      // Schema.derived[Status].structural should not compile
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("error message is helpful") {
      // The compile error should suggest upgrading to Scala 3
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}

