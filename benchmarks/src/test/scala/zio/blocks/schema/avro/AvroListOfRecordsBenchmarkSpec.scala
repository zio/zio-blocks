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

package zio.blocks.schema.avro

import zio.blocks.schema.SchemaBaseSpec
import zio.test._
import zio.test.Assertion._

object AvroListOfRecordsBenchmarkSpec extends SchemaBaseSpec {
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
