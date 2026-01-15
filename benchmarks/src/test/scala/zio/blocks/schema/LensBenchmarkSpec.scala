package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object LensBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("LensBenchmarkSpec")(
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
        assert((new LensReplaceBenchmark).quicklens)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).zioBlocks)(equalTo(A(B(C(D(E("test2")))))))
      }
    )
  )
}
