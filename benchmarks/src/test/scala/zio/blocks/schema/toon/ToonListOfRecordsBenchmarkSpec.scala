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
