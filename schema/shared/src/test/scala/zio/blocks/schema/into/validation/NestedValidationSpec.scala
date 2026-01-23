package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for nested validation scenarios. */
object NestedValidationSpec extends ZIOSpecDefault {

  case class Inner(value: Long)
  case class InnerInt(value: Int)

  case class Middle(inner: Inner, name: String)
  case class MiddleInt(inner: InnerInt, name: String)

  case class SourceList(items: List[Long])
  case class TargetList(items: List[Int])

  case class SourceOpt(value: Option[Long])
  case class TargetOpt(value: Option[Int])

  implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]

  def spec: Spec[TestEnvironment, Any] = suite("NestedValidationSpec")(
    test("succeeds when inner value fits") {
      val result = Into.derived[Middle, MiddleInt].into(Middle(Inner(100L), "test"))
      assert(result)(isRight(equalTo(MiddleInt(InnerInt(100), "test"))))
    },
    test("fails when inner value overflows") {
      val result = Into.derived[Middle, MiddleInt].into(Middle(Inner(Long.MaxValue), "test"))
      assert(result)(isLeft)
    },
    test("fails when list element overflows") {
      val result = Into.derived[SourceList, TargetList].into(SourceList(List(1L, Long.MaxValue)))
      assert(result)(isLeft)
    },
    test("fails when Option value overflows") {
      val result = Into.derived[SourceOpt, TargetOpt].into(SourceOpt(Some(Long.MaxValue)))
      assert(result)(isLeft)
    }
  )
}
