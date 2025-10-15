package zio.blocks.avro

import zio.test._
import zio.test.Assertion._

object ListOfRecordsBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ListOfRecordsBenchmarkSpec")(
    test("reading") {
      val zioBlocksOutput = (new ListOfRecordsBenchmark).readingZioBlocks
      val zioSchemaOutput = (new ListOfRecordsBenchmark).readingZioSchema
      assert(zioBlocksOutput)(equalTo(zioSchemaOutput))
    },
    test("writing") {
      val zioBlocksOutput = (new ListOfRecordsBenchmark).writingZioBlocks
      val zioSchemaOutput = (new ListOfRecordsBenchmark).writingZioSchema
      assert(java.util.Arrays.compare(zioBlocksOutput, zioSchemaOutput))(equalTo(0))
    }
  )
}
