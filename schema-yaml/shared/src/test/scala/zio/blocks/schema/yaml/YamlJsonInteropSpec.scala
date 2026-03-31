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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.Json
import zio.test._

object YamlJsonInteropSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlJsonInterop")(
    suite("yamlToJson")(
      test("mapping to Json.Object") {
        val yaml = Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Alice"), "age" -> Yaml.Scalar("30"))
        val json = YamlJsonInterop.yamlToJson(yaml)
        assertTrue(json.isInstanceOf[Json.Object])
      },
      test("mapping with non-scalar key uses print") {
        val yaml = Yaml.Mapping(Chunk((Yaml.Sequence(Chunk(Yaml.Scalar("k"))), Yaml.Scalar("v"))))
        val json = YamlJsonInterop.yamlToJson(yaml)
        assertTrue(json.isInstanceOf[Json.Object])
      },
      test("sequence to Json.Array") {
        val yaml = Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        val json = YamlJsonInterop.yamlToJson(yaml)
        assertTrue(json.isInstanceOf[Json.Array])
      },
      test("scalar 'true' to Json.True") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("true")) == Json.True)
      },
      test("scalar 'True' to Json.True") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("True")) == Json.True)
      },
      test("scalar 'TRUE' to Json.True") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("TRUE")) == Json.True)
      },
      test("scalar 'false' to Json.False") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("false")) == Json.False)
      },
      test("scalar 'False' to Json.False") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("False")) == Json.False)
      },
      test("scalar 'FALSE' to Json.False") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("FALSE")) == Json.False)
      },
      test("scalar 'null' to Json.Null") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("null")) == Json.Null)
      },
      test("scalar '~' to Json.Null") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("~")) == Json.Null)
      },
      test("scalar 'Null' to Json.Null") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("Null")) == Json.Null)
      },
      test("scalar 'NULL' to Json.Null") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.Scalar("NULL")) == Json.Null)
      },
      test("numeric scalar to Json.Number") {
        val json = YamlJsonInterop.yamlToJson(Yaml.Scalar("42"))
        assertTrue(json == Json.Number(BigDecimal(42)))
      },
      test("non-numeric scalar to Json.String") {
        val json = YamlJsonInterop.yamlToJson(Yaml.Scalar("hello"))
        assertTrue(json == new Json.String("hello"))
      },
      test("NullValue to Json.Null") {
        assertTrue(YamlJsonInterop.yamlToJson(Yaml.NullValue) == Json.Null)
      }
    ),
    suite("jsonToYaml")(
      test("Json.Object to Yaml.Mapping") {
        val json = new Json.Object(Chunk(("name", new Json.String("Alice"))))
        val yaml = YamlJsonInterop.jsonToYaml(json)
        assertTrue(
          yaml == Yaml.Mapping(Chunk((Yaml.Scalar("name"), Yaml.Scalar("Alice"))))
        )
      },
      test("Json.Array to Yaml.Sequence") {
        val json = new Json.Array(Chunk(new Json.String("a"), new Json.String("b")))
        val yaml = YamlJsonInterop.jsonToYaml(json)
        assertTrue(
          yaml == Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        )
      },
      test("Json.String to Yaml.Scalar") {
        val json = new Json.String("hello")
        val yaml = YamlJsonInterop.jsonToYaml(json)
        assertTrue(yaml == Yaml.Scalar("hello"))
      },
      test("Json.Number to Yaml.Scalar with Float tag") {
        val json = Json.Number(BigDecimal("3.14"))
        val yaml = YamlJsonInterop.jsonToYaml(json)
        assertTrue(yaml == Yaml.Scalar("3.14", tag = Some(YamlTag.Float)))
      },
      test("Json.True to Yaml.Scalar with Bool tag") {
        val yaml = YamlJsonInterop.jsonToYaml(Json.True)
        assertTrue(yaml == Yaml.Scalar("true", tag = Some(YamlTag.Bool)))
      },
      test("Json.False to Yaml.Scalar with Bool tag") {
        val yaml = YamlJsonInterop.jsonToYaml(Json.False)
        assertTrue(yaml == Yaml.Scalar("false", tag = Some(YamlTag.Bool)))
      },
      test("Json.Null to Yaml.NullValue") {
        val yaml = YamlJsonInterop.jsonToYaml(Json.Null)
        assertTrue(yaml == Yaml.NullValue)
      }
    ),
    suite("round-trip yaml→json→yaml")(
      test("simple mapping") {
        val yaml         = Yaml.Mapping.fromStringKeys("key" -> Yaml.Scalar("value"))
        val roundTripped = YamlJsonInterop.jsonToYaml(YamlJsonInterop.yamlToJson(yaml))
        assertTrue(roundTripped == yaml)
      },
      test("sequence") {
        val yaml         = Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        val roundTripped = YamlJsonInterop.jsonToYaml(YamlJsonInterop.yamlToJson(yaml))
        assertTrue(roundTripped == yaml)
      },
      test("NullValue") {
        val yaml         = Yaml.NullValue
        val roundTripped = YamlJsonInterop.jsonToYaml(YamlJsonInterop.yamlToJson(yaml))
        assertTrue(roundTripped == yaml)
      }
    ),
    suite("Yaml.toJson and Yaml.fromJson convenience")(
      test("toJson method") {
        val yaml = Yaml.Mapping.fromStringKeys("key" -> Yaml.Scalar("value"))
        val json = yaml.toJson
        assertTrue(json.isInstanceOf[Json.Object])
      },
      test("fromJson method") {
        val json = new Json.Object(Chunk(("key", new Json.String("value"))))
        val yaml = Yaml.fromJson(json)
        assertTrue(yaml == Yaml.Mapping(Chunk((Yaml.Scalar("key"), Yaml.Scalar("value")))))
      }
    ),
    suite("Yaml.print and printPretty")(
      test("print produces default YAML output") {
        val yaml = Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("b"))
        assertTrue(yaml.print == "a: b")
      },
      test("printPretty produces document marker output") {
        val yaml = Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("b"))
        assertTrue(yaml.printPretty.startsWith("---\n"))
      }
    )
  )
}
