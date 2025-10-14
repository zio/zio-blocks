package zio.blocks.avro

import zio.test._
import zio.test.Assertion._

object ListOfRecordsBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ListOfRecordsBenchmarkSpec")(
    test("has consistent output") {
      val zioBlocksOutput = (new ListOfRecordsBenchmark).writingZioBlocks
      val zioSchemaOutput = (new ListOfRecordsBenchmark).writingZioSchema
      assert(java.util.Arrays.compare(zioBlocksOutput, zioSchemaOutput))(equalTo(0))
    }
  )
}
