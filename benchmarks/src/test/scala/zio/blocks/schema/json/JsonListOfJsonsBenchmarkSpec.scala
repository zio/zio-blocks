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

import zio.blocks.schema.SchemaBaseSpec
import zio.test.*
import zio.test.Assertion.*

object JsonListOfJsonsBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonListOfJsonsBenchmarkSpec")(
    test("reading") {
      val benchmark = new JsonListOfJsonsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.readingZioBlocks
      val zioJsonOutput   = benchmark.readingZioJson
      assert(zioJsonOutput.toString())(equalTo(zioBlocksOutput.print))
    },
    test("writing") {
      val benchmark = new JsonListOfJsonsBenchmark
      benchmark.setup()
      val zioBlocksOutput = benchmark.writingZioBlocks
      val zioJsonOutput   = benchmark.writingZioJson
      assert(new String(zioJsonOutput))(equalTo(new String(zioBlocksOutput)))
    }
  )
}
