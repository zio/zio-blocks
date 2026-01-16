package zio.blocks.schema.avro

import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec, SchemaError}
import zio.test._
import zio.test.Assertion._

object AvroNestedRecordsBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("AvroNestedRecordsBenchmarkSpec")(
    test("reading") {
      val benchmark = new AvroNestedRecordsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.readingZioBlocks
      assert(zioBlocksOutput)(equalTo(zioBlocksOutput))
    },
    test("reading error") {
      val benchmark = new AvroNestedRecordsBenchmark
      benchmark.setup()
      val trace =
        (1 to benchmark.size)
          .foldLeft[List[DynamicOptic.Node]](DynamicOptic.Node.Field("next") :: Nil) { (trace, _) =>
            DynamicOptic.Node
              .Field("next") :: DynamicOptic.Node.Case("Some") :: DynamicOptic.Node.Field("value") :: trace
          }
          .toIndexedSeq
      val message       = "Expected enum index from 0 to 1, got -1"
      val expectedError =
        new SchemaError(new ::(new SchemaError.ExpectationMismatch(new DynamicOptic(trace), message), Nil))
      assert(benchmark.readingErrorZioBlocks)(isLeft(equalTo(expectedError)))
    },
    test("writing") {
      val benchmark = new AvroNestedRecordsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.writingZioBlocks
      assert(java.util.Arrays.compare(zioBlocksOutput, zioBlocksOutput))(equalTo(0))
    }
  )
}
