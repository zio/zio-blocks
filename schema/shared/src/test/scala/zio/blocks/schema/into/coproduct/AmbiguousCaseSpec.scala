package zio.blocks.schema.into.coproduct

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for coproduct case matching in Into derivation. */
object AmbiguousCaseSpec extends ZIOSpecDefault {

  sealed trait SourceADT
  object SourceADT {
    case class A(x: Int)    extends SourceADT
    case class B(y: String) extends SourceADT
  }

  sealed trait TargetADT
  object TargetADT {
    case class A(x: Long)   extends TargetADT
    case class B(y: String) extends TargetADT
  }

  sealed trait SourceObjs
  object SourceObjs {
    case object Active   extends SourceObjs
    case object Inactive extends SourceObjs
  }

  sealed trait TargetObjs
  object TargetObjs {
    case object Active   extends TargetObjs
    case object Inactive extends TargetObjs
  }

  def spec: Spec[TestEnvironment, Any] = suite("AmbiguousCaseSpec")(
    test("converts matching case classes with field coercion") {
      val source: SourceADT = SourceADT.A(42)
      val result            = Into.derived[SourceADT, TargetADT].into(source)
      assert(result)(isRight(equalTo(TargetADT.A(42L): TargetADT)))
    },
    test("converts case objects by name") {
      val s1: SourceObjs = SourceObjs.Active
      val s2: SourceObjs = SourceObjs.Inactive
      assert(Into.derived[SourceObjs, TargetObjs].into(s1))(isRight(equalTo(TargetObjs.Active: TargetObjs))) &&
      assert(Into.derived[SourceObjs, TargetObjs].into(s2))(isRight(equalTo(TargetObjs.Inactive: TargetObjs)))
    }
  )
}
