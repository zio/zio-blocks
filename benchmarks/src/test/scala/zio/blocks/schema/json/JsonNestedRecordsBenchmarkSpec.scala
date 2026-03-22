/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.json

import zio.blocks.schema.json.JsonNestedRecordsBenchmark
import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec, SchemaError}
import zio.test._
import zio.test.Assertion._

object JsonNestedRecordsBenchmarkSpec extends SchemaBaseSpec {
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
