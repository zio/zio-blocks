package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for empty product conversions. */
object EmptyProductSpec extends ZIOSpecDefault {

  case class EmptyA()
  case class EmptyB()
  case object EmptyObjA
  case object EmptyObjB

  def spec: Spec[TestEnvironment, Any] = suite("EmptyProductSpec")(
    test("empty case class to empty case class") {
      val result = Into.derived[EmptyA, EmptyB].into(EmptyA())
      assert(result)(isRight(equalTo(EmptyB())))
    },
    test("case object to case object") {
      val result = Into.derived[EmptyObjA.type, EmptyObjB.type].into(EmptyObjA)
      assert(result)(isRight(equalTo(EmptyObjB)))
    },
    test("case object to empty case class") {
      val result = Into.derived[EmptyObjA.type, EmptyA].into(EmptyObjA)
      assert(result)(isRight(equalTo(EmptyA())))
    }
  )
}
