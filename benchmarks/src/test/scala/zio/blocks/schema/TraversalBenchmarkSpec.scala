package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object TraversalBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("TraversalBenchmarkSpec")(
    suite("TraversalFoldBenchmark")(
      test("has consistent output") {
        assert((new TraversalFoldBenchmark).direct)(equalTo(55)) &&
        assert((new TraversalFoldBenchmark).zioBlocks)(equalTo(55))
      }
    ),
    suite("TraversalModifyBenchmark")(
      test("has consistent output") {
        assert((new TraversalModifyBenchmark).direct.toList)(equalTo((2 to 11).toList)) &&
        assert((new TraversalModifyBenchmark).quicklens.toList)(equalTo((2 to 11).toList)) &&
        assert((new TraversalModifyBenchmark).zioBlocks.toList)(equalTo((2 to 11).toList))
      }
    )
  )
}
