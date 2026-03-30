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

package zio.blocks.schema.yaml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object YamlOptionsSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlOptions")(
    test("default options") {
      val opts = YamlOptions.default
      assertTrue(
        opts.indentStep == 2 &&
          opts.flowStyle == false &&
          opts.documentMarkers == false
      )
    },
    test("pretty options") {
      val opts = YamlOptions.pretty
      assertTrue(
        opts.indentStep == 2 &&
          opts.documentMarkers == true
      )
    },
    test("flow options") {
      val opts = YamlOptions.flow
      assertTrue(opts.flowStyle == true)
    },
    test("custom indent step") {
      val opts = YamlOptions(indentStep = 4)
      assertTrue(opts.indentStep == 4)
    },
    test("custom options") {
      val opts = YamlOptions(indentStep = 3, flowStyle = true, documentMarkers = true)
      assertTrue(
        opts.indentStep == 3 &&
          opts.flowStyle == true &&
          opts.documentMarkers == true
      )
    }
  )
}
