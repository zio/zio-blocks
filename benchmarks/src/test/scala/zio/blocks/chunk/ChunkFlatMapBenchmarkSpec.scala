package zio.blocks.chunk

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object ChunkFlatMapBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ChunkFlatMapBenchmarkSpec")(
    test("has consistent output") {
      val benchmark = new ChunkFlatMapBenchmark
      benchmark.setup()
      val expected = (1 to benchmark.size).flatMap(i => Seq(i, i + 1))
      assert(benchmark.flatMapArraySeq)(equalTo(expected)) &&
      assert(benchmark.flatMapChunk)(equalTo(expected)) &&
      assert(benchmark.flatMapVector)(equalTo(expected))
    }
  )
}
