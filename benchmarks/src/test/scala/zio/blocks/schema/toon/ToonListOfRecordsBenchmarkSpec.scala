package zio.blocks.schema.toon

import zio.blocks.schema.SchemaBaseSpec
import zio.test.*
import zio.test.Assertion.*

object ToonListOfRecordsBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ToonListOfRecordsBenchmarkSpec")(
    test("reading") {
      val benchmark = new ToonListOfRecordsBenchmark
      benchmark.setup()
      val toon4sOutput    = benchmark.readingToon4s
      val zioBlocksOutput = benchmark.readingZioBlocks
      assert(toon4sOutput)(equalTo(zioBlocksOutput))
    },
    test("writing") {
      val benchmark = new ToonListOfRecordsBenchmark
      benchmark.setup()
      val toon4sOutput    = benchmark.writingToon4s
      val zioBlocksOutput = benchmark.writingZioBlocks
      // println(s"zioBlocksOutput: " + new String(zioBlocksOutput, "UTF-8"))
      assert(new String(toon4sOutput))(equalTo(new String(zioBlocksOutput)))
    }
  )
}
