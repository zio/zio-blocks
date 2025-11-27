package zio.blocks.json

import zio.blocks.schema.json.JsonNestedRecordsBenchmark
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.test._
import zio.test.Assertion._

object JsonNestedRecordsBenchmarkSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonNestedRecordsBenchmarkSpec")(
    test("reading") {
      val benchmark = new JsonNestedRecordsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.readingZioBlocks
      assert(zioBlocksOutput)(equalTo(zioBlocksOutput))
    },
    test("reading error") {
      val benchmark = new JsonNestedRecordsBenchmark
      benchmark.setup()
      val trace =
        (1 to benchmark.size)
          .foldLeft[List[DynamicOptic.Node]](DynamicOptic.Node.Field("value") :: Nil) { (trace, _) =>
            DynamicOptic.Node
              .Field("next") :: DynamicOptic.Node.Case("Some") :: DynamicOptic.Node.Field("value") :: trace
          }
          .toIndexedSeq
      val message       = "illegal number"
      val expectedError =
        new SchemaError(new ::(new SchemaError.ExpectationMismatch(new DynamicOptic(trace), message), Nil))
      assert(benchmark.readingErrorZioBlocks)(isLeft(equalTo(expectedError)))
    },
    test("writing") {
      val benchmark = new JsonNestedRecordsBenchmark
      benchmark.setup()
      // println(new String(benchmark.brokenNestedRecords))
      val zioBlocksOutput = benchmark.writingZioBlocks
      assert(java.util.Arrays.compare(zioBlocksOutput, zioBlocksOutput))(equalTo(0))
    }
  )
}
