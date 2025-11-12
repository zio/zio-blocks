package zio.blocks.json

import zio.blocks.schema.json.JsonListOfRecordsBenchmark
import zio.test._
import zio.test.Assertion._

object JsonListOfRecordsBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonListOfRecordsBenchmarkSpec")(
    test("reading") {
      val benchmark = new JsonListOfRecordsBenchmark
      benchmark.setup()
      val jsoniterScalaOutput = benchmark.readingJsoniterScala
      val zioBlocksOutput     = benchmark.readingZioBlocks
      val zioSchemaOutput     = benchmark.readingZioJson
      assert(jsoniterScalaOutput)(equalTo(zioBlocksOutput)) &&
      assert(zioSchemaOutput)(equalTo(zioBlocksOutput))
    },
    test("writing") {
      val benchmark = new JsonListOfRecordsBenchmark
      benchmark.setup()
      val jsoniterScalaOutput = benchmark.writingJsoniterScala
      val zioBlocksOutput     = benchmark.writingZioBlocks
      val zioJsonOutput       = benchmark.writingZioJson
      assert(new String(jsoniterScalaOutput))(equalTo(new String(zioBlocksOutput))) &&
      assert(new String(zioJsonOutput))(equalTo(new String(zioBlocksOutput)))
    }
  )
}
