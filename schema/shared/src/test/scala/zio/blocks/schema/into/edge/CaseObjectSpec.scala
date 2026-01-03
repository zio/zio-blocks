package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for case object conversions in Into derivation. */
object CaseObjectSpec extends ZIOSpecDefault {

  case object SingletonA
  case object SingletonB
  case class EmptyClass()

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
  }

  sealed trait StatusAlt
  object StatusAlt {
    case object Active   extends StatusAlt
    case object Inactive extends StatusAlt
  }

  def spec: Spec[TestEnvironment, Any] = suite("CaseObjectSpec")(
    test("case object to case object") {
      val result = Into.derived[SingletonA.type, SingletonB.type].into(SingletonA)
      assert(result)(isRight(equalTo(SingletonB)))
    },
    test("case object to empty case class") {
      val result = Into.derived[SingletonA.type, EmptyClass].into(SingletonA)
      assert(result)(isRight(equalTo(EmptyClass())))
    },
    test("case objects in sealed traits") {
      val source: Status = Status.Active
      val result         = Into.derived[Status, StatusAlt].into(source)
      assert(result)(isRight(equalTo(StatusAlt.Active: StatusAlt)))
    }
  )
}
