package zio.blocks.schema
import zio.Scope
import zio.test.Assertion._
import zio.test._

object LazySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("LazySpec")(
      test("has consistent as") {
        assert(Lazy[Int](42).as[String]("42"))(equalTo(Lazy[String]("42")))
      },
      test("catchAll") {
        val lazyValue: Lazy[Int] = Lazy(throw new Exception("error"))
        val lazyCatchAll: Lazy[Int] = lazyValue.catchAll(_ => Lazy(42))
        assert(Lazy(throw new Exception("error")).force)(equalTo(42))
      },
      test("ensuring") {
        val lazyValue: Lazy[Int] = Lazy(42)
        // TODO test other values [Any]
        val lazyEnsuring: Lazy[Int] = lazyValue.ensuring(Lazy(42))
        assert(lazyEnsuring)(equalTo(Lazy(42)))
      }
    )
}
