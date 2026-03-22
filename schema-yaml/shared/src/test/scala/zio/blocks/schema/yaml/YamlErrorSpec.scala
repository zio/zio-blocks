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

import zio.blocks.schema.DynamicOptic
import zio.test._

object YamlErrorSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlError")(
    test("message with line and column") {
      val err = YamlError("test error", 5, 10)
      assertTrue(err.getMessage == "test error (at line 5, column 10)")
    },
    test("message with line only") {
      val err = new YamlError(Nil, "test error", line = Some(5))
      assertTrue(err.getMessage == "test error (at line 5)")
    },
    test("message without position") {
      val err = YamlError("test error")
      assertTrue(err.getMessage == "test error")
    },
    test("parseError") {
      val err = YamlError.parseError("bad syntax", 1, 2)
      assertTrue(err.getMessage.contains("Parse error"))
    },
    test("validationError") {
      val err = YamlError.validationError("bad value")
      assertTrue(err.getMessage.contains("Validation error"))
    },
    test("encodingError") {
      val err = YamlError.encodingError("can't encode")
      assertTrue(err.getMessage.contains("Encoding error"))
    },
    test("apply with spans") {
      val err = YamlError("test", List(DynamicOptic.Node.Field("field1")))
      assertTrue(err.spans.length == 1)
    },
    test("path returns DynamicOptic") {
      val err   = YamlError("test", List(DynamicOptic.Node.Field("a"), DynamicOptic.Node.Field("b")))
      val optic = err.path
      assertTrue(optic.nodes.length == 2)
    },
    test("atSpan adds span") {
      val err  = YamlError("test")
      val err2 = err.atSpan(DynamicOptic.Node.Field("f"))
      assertTrue(err2.spans.length == 1)
    }
  )
}
