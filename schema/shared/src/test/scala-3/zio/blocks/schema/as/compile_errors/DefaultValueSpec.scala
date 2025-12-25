package zio.blocks.schema.as.compile_errors

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object DefaultValueSpec extends ZIOSpecDefault {

  def spec = suite("DefaultValueSpec")(
    suite("Default Values Detection in As")(
      test("should fail compilation when target has default values") {
        typeCheck {
          """
          case class V1(name: String)
          case class V2(name: String, active: Boolean = true)
          
          As.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot derive As") &&
                containsString("default values") &&
                containsString("round-trip") &&
                containsString("active")
            )
          )
        )
      },
      test("should fail compilation when source has default values") {
        typeCheck {
          """
          case class V1(name: String, age: Int = 0)
          case class V2(name: String, age: Int)
          
          As.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot derive As") &&
                containsString("default values") &&
                containsString("round-trip") &&
                containsString("age")
            )
          )
        )
      },
      test("should fail compilation when both have default values") {
        typeCheck {
          """
          case class V1(name: String, count: Int = 0)
          case class V2(name: String, active: Boolean = true)
          
          As.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot derive As") &&
                containsString("default values") &&
                containsString("round-trip")
            )
          )
        )
      },
      test("should fail compilation with multiple default values") {
        typeCheck {
          """
          case class V1(name: String)
          case class V2(name: String, age: Int = 0, active: Boolean = true, count: Long = 0L)
          
          As.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot derive As") &&
                containsString("default values") &&
                containsString("age") &&
                containsString("active") &&
                containsString("count")
            )
          )
        )
      },
      test("should allow As derivation when no default values") {
        typeCheck {
          """
          case class V1(name: String, age: Int)
          case class V2(name: String, age: Int)
          
          As.derived[V1, V2]
          """
        }.map(assert(_)(isRight))
      },
      test("should allow As derivation with optional fields (Option)") {
        typeCheck {
          """
          case class V1(name: String)
          case class V2(name: String, age: Option[Int])
          
          As.derived[V1, V2]
          """
        }.map(assert(_)(isRight))
      }
    )
  )
}
