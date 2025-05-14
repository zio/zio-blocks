package zio.blocks.schema
import zio.Scope
import zio.test.Assertion._
import zio.test._

object LazySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("LazySpec")(
      test("has consistent equals and hashCode") {
        assert(Lazy[Int](42).as[String]("42"))(equalTo(Lazy[String]("42")))
      }
    )
}
