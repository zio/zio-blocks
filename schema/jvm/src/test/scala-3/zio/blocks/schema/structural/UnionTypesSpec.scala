package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 */
object UnionTypesSpec extends ZIOSpecDefault {

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
    test("structural type name contains all cases with Tag fields") {
      val schema     = Schema.derived[Result]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      val caseTypes  = typeName.split('|').map(_.trim).toSet

      assertTrue(
        caseTypes.size == 2,
        caseTypes.exists(t => t.contains("Tag:\"Success\"")),
        caseTypes.exists(t => t.contains("Tag:\"Failure\""))
      )
    }
  )
}
