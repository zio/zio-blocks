package zio.blocks.avro

import zio.test._
import zio.test.Assertion._

object AvroListOfRecordsBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroListOfRecordsBenchmarkSpec")(
    test("reading") {
      val benchmark = new AvroListOfRecordsBenchmark
      benchmark.setup()
      val avro4sOutput    = benchmark.readingAvro4s
      val zioBlocksOutput = benchmark.readingZioBlocks
      val zioSchemaOutput = benchmark.readingZioSchema
      assert(avro4sOutput)(equalTo(zioBlocksOutput)) &&
      assert(zioSchemaOutput)(equalTo(zioBlocksOutput))
    },
    test("writing") {
      val benchmark = new AvroListOfRecordsBenchmark
      benchmark.setup()
      val avro4sOutput    = benchmark.writingAvro4s
      val zioBlocksOutput = benchmark.writingZioBlocks
      val zioSchemaOutput = benchmark.writingZioSchema
      assert(java.util.Arrays.compare(avro4sOutput, zioBlocksOutput))(equalTo(0)) &&
      assert(java.util.Arrays.compare(zioSchemaOutput, zioBlocksOutput))(equalTo(0))
    }
  )
}
