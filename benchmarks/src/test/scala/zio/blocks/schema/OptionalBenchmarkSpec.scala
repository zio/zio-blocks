package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object OptionalBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("OptionalBenchmarkSpec")(
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
        assert((new OptionalReplaceBenchmark).quicklens)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).zioBlocks)(equalTo(A1(B1(C1(D1(E1("test2")))))))
      }
    )
  )
}
