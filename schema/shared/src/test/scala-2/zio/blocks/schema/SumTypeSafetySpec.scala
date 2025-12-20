package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object SumTypeSafetySpec extends ZIOSpecDefault {
  def spec = suite("SumTypeSafetySpec")(
    test("compilation fails for sealed trait in Scala 2") {
      assertZIO(
        typeCheck(
          """
          import zio.blocks.schema._
          sealed trait Result
          case class Success(value: Int) extends Result
          val ts = ToStructural.derived[Result]
        """
        )
      )(
        isLeft(
          containsString("Cannot generate structural type for sum types in Scala 2")
        )
      )
    }
  )
}
