package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test.*
import zio.test.Assertion.*

object JsonListOfJsonsBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonListOfJsonsBenchmarkSpec")(
    test("reading") {
      val benchmark = new JsonListOfJsonsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.readingZioBlocks
      val zioJsonOutput   = benchmark.readingZioJson
      assert(zioJsonOutput.toString())(equalTo(zioBlocksOutput.print))
    },
    test("writing") {
      val benchmark = new JsonListOfJsonsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.writingZioBlocks
      val zioJsonOutput   = benchmark.writingZioJson
      assert(new String(zioJsonOutput))(equalTo(new String(zioBlocksOutput)))
    }
  )
}
