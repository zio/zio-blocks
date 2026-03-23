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

import zio.blocks.schema.Schema
import zio.test._

object YamlSyntaxSpec extends YamlBaseSpec {

  import YamlSyntax._

  case class Simple(value: String)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived
  }

  case class WithInt(name: String, age: Int)
  object WithInt {
    implicit val schema: Schema[WithInt] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("YamlSyntax")(
    suite("toYaml")(
      test("converts case class to Yaml") {
        val s    = Simple("hello")
        val yaml = s.toYaml
        assertTrue(yaml.isInstanceOf[Yaml.Mapping])
      }
    ),
    suite("toYamlString")(
      test("converts case class to YAML string") {
        val s   = Simple("test")
        val str = s.toYamlString
        assertTrue(str.contains("value:") && str.contains("test"))
      }
    ),
    suite("toYamlBytes")(
      test("converts case class to YAML bytes") {
        val s     = Simple("test")
        val bytes = s.toYamlBytes
        assertTrue(bytes.nonEmpty)
      }
    ),
    suite("fromYaml")(
      test("decodes YAML string to case class") {
        val yaml   = "value: hello"
        val result = yaml.fromYaml[Simple]
        assertTrue(result == Right(Simple("hello")))
      },
      test("returns error for invalid YAML decoding") {
        val yaml   = "invalid content that cannot decode to WithInt"
        val result = yaml.fromYaml[WithInt]
        assertTrue(result.isLeft)
      },
      test("returns error for unparseable YAML") {
        val yaml   = "{invalid: [yaml"
        val result = yaml.fromYaml[Simple]
        assertTrue(result.isLeft)
      },
      test("round-trip through YAML string") {
        val original = WithInt("Alice", 30)
        val yamlStr  = original.toYamlString
        val decoded  = yamlStr.fromYaml[WithInt]
        assertTrue(decoded == Right(original))
      }
    )
  )
}
