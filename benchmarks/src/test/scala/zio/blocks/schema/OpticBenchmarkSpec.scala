package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object OpticBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("OpticSpec")(
    suite("LensGetBenchmark")(
      test("has consistent output") {
        assert((new LensGetBenchmark).direct)(equalTo("test")) &&
        assert((new LensGetBenchmark).monocle)(equalTo("test")) &&
        assert((new LensGetBenchmark).zioBlocks)(equalTo("test"))
      }
    ),
    suite("LensReplaceBenchmark")(
      test("has consistent output") {
        import zio.blocks.schema.LensDomain._

        assert((new LensReplaceBenchmark).direct)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).monocle)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).zioBlocks)(equalTo(A(B(C(D(E("test2")))))))
      }
    ),
    suite("OptionalGetOptionBenchmark")(
      test("has consistent output") {
        assert((new OptionalGetOptionBenchmark).direct)(isSome(equalTo("test"))) &&
        assert((new OptionalGetOptionBenchmark).monocle)(isSome(equalTo("test"))) &&
        assert((new OptionalGetOptionBenchmark).zioBlocks)(isSome(equalTo("test")))
      }
    ),
    suite("OptionalReplaceBenchmark")(
      test("has consistent output") {
        import zio.blocks.schema.OptionalDomain._

        assert((new OptionalReplaceBenchmark).direct)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).monocle)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).zioBlocks)(equalTo(A1(B1(C1(D1(E1("test2")))))))
      }
    )
  )
}
