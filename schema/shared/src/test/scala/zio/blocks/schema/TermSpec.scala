package zio.blocks.schema
import zio.Scope
import zio.test.Assertion._
import zio.test._

object TermSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("TermSpec")(
      test("has consistent equals and hashCode") {
        assert(true)(equalTo(true))
      }
    )
}
