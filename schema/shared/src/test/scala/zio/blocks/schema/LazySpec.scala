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
      // BOOOOMMM
      // test("catchAll") {
      //   val lazyValue: Lazy[Int]    = Lazy(throw new Exception("error"))
      //   val lazyCatchAll: Lazy[Int] = lazyValue.catchAll(_ => Lazy(42))
      //   assert(Lazy(throw new Exception("error")).force)(equalTo(42))
      // },
      test("ensuring") {
        val lazyValue: Lazy[Int] = Lazy(42)
        // TODO test other values [Any]
        val lazyEnsuring: Lazy[Int] = lazyValue.ensuring(Lazy(42))
        assert(lazyEnsuring)(equalTo(Lazy(42)))
      },
      test("flatMap") {
        val lazyValue: Lazy[Int]   = Lazy(42)
        val lazyFlatMap: Lazy[Int] = lazyValue.flatMap(i => Lazy(i + 1))
        assert(lazyFlatMap)(equalTo(Lazy(43)))
      },
      test("flatten") {
        val lazyValue: Lazy[Lazy[Int]] = Lazy(Lazy(42))
        val lazyFlatten: Lazy[Int]     = lazyValue.flatten
        assert(lazyFlatten)(equalTo(Lazy(42)))
      },
      // TODO: test that force breaks graciously
      test("force") {
        val lazyValue: Lazy[Int] = Lazy(42)
        assert(lazyValue.force)(equalTo(42))
      },
      test("isEvaluated") {
        val lazyValue: Lazy[Int] = Lazy(42)
        assert(lazyValue.isEvaluated)(isFalse)
        val _ = lazyValue.force
        assert(lazyValue.isEvaluated)(isTrue)
      },
      test("map") {
        val lazyValue: Lazy[Int]   = Lazy(42)
        val lazyMap: Lazy[String]  = lazyValue.map(_.toString)
        assert(lazyMap)(equalTo(Lazy("42")))
      },
      test("equals") {
        assert(Lazy(42))(equalTo(Lazy(42)))
      },
      test("hashCode") {
        assert(Lazy(42).hashCode())(equalTo(Lazy(42).hashCode()))
      },
      test("zip") {
        val lazyValue1: Lazy[Int]   = Lazy(42)
        val lazyValue2: Lazy[Int]   = Lazy(43)
        val lazyZip: Lazy[(Int, Int)] = lazyValue1.zip(lazyValue2)
        assert(lazyZip)(equalTo(Lazy((42, 43))))
      },
    )
}
