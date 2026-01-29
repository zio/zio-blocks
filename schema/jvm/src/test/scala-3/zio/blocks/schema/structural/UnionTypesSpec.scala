package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 */
object UnionTypesSpec extends SchemaBaseSpec {

  sealed trait Result
  object Result {
    case class Success(value: Int)    extends Result
    case class Failure(error: String) extends Result
  }

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
  }

  def spec: Spec[Any, Nothing] = suite("UnionTypesSpec")(
    test("sealed trait with case classes converts to structural") {
      val schema     = Schema.derived[Result]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait with case objects converts to structural") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Result sealed trait converts to union structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.UnionTypesSpec._
        val schema = Schema.derived[Result]
        val structural: Schema[{def Failure: {def error: String}} | {def Success: {def value: Int}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("Status sealed trait converts to union structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.UnionTypesSpec._
        val schema = Schema.derived[Status]
        val structural: Schema[{def Active: {}} | {def Inactive: {}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
