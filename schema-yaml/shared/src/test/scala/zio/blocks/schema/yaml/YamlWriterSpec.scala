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

import zio.test._

object YamlWriterSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlWriter")(
    suite("block mapping output")(
      test("simple mapping") {
        val yaml   = Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Alice"))
        val result = YamlWriter.write(yaml)
        assertTrue(result == "name: Alice")
      },
      test("multi-entry mapping") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "name" -> Yaml.Scalar("Alice"),
          "age"  -> Yaml.Scalar("30", tag = Some(YamlTag.Int))
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("name: Alice") && result.contains("age: 30"))
      },
      test("empty mapping renders as {}") {
        val yaml   = Yaml.Mapping.empty
        val result = YamlWriter.write(yaml)
        assertTrue(result == "{}")
      },
      test("nested mapping") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "outer" -> Yaml.Mapping.fromStringKeys("inner" -> Yaml.Scalar("value"))
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("outer:") && result.contains("inner: value"))
      },
      test("mapping with sequence value") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "items" -> Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("items:") && result.contains("- a") && result.contains("- b"))
      },
      test("mapping with null value") {
        val yaml   = Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue)
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("key: null"))
      },
      test("mapping with non-scalar key writes null for key") {
        val yaml   = Yaml.Mapping(Chunk((Yaml.Sequence(Chunk(Yaml.Scalar("a"))), Yaml.Scalar("v"))))
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("null: v"))
      }
    ),
    suite("block sequence output")(
      test("simple sequence") {
        val yaml   = Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("- a") && result.contains("- b"))
      },
      test("empty sequence renders as []") {
        val yaml   = Yaml.Sequence.empty
        val result = YamlWriter.write(yaml)
        assertTrue(result == "[]")
      },
      test("sequence of mappings") {
        val yaml = Yaml.Sequence(
          Chunk(
            Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("A")),
            Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("B"))
          )
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("- name: A") && result.contains("- name: B"))
      },
      test("sequence with nested mapping having sub-mapping") {
        val yaml = Yaml.Sequence(
          Chunk(
            Yaml.Mapping.fromStringKeys(
              "outer" -> Yaml.Mapping.fromStringKeys("inner" -> Yaml.Scalar("v"))
            )
          )
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("- outer:") && result.contains("inner: v"))
      },
      test("sequence with nested mapping having sub-sequence") {
        val yaml = Yaml.Sequence(
          Chunk(
            Yaml.Mapping.fromStringKeys(
              "items" -> Yaml.Sequence(Chunk(Yaml.Scalar("x")))
            )
          )
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("- items:") && result.contains("- x"))
      },
      test("sequence with empty mapping renders mapping entry as {}") {
        val yaml = Yaml.Sequence(
          Chunk(
            Yaml.Mapping.fromStringKeys("key" -> Yaml.Mapping.empty)
          )
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("key: {}"))
      },
      test("sequence with empty sequence renders as []") {
        val yaml = Yaml.Sequence(
          Chunk(
            Yaml.Mapping.fromStringKeys("key" -> Yaml.Sequence.empty)
          )
        )
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("key: []"))
      }
    ),
    suite("flow style output")(
      test("flow mapping") {
        val yaml   = Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("x"), "b" -> Yaml.Scalar("y"))
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "{a: x, b: y}")
      },
      test("flow sequence") {
        val yaml   = Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b")))
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "[a, b]")
      },
      test("empty flow mapping") {
        val yaml   = Yaml.Mapping.empty
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "{}")
      },
      test("empty flow sequence") {
        val yaml   = Yaml.Sequence.empty
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "[]")
      },
      test("nested flow") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "outer" -> Yaml.Mapping.fromStringKeys("inner" -> Yaml.Scalar("v"))
        )
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "{outer: {inner: v}}")
      },
      test("flow with null value") {
        val yaml   = Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue)
        val result = YamlWriter.write(yaml, YamlOptions.flow)
        assertTrue(result == "{key: null}")
      }
    ),
    suite("pretty output (document markers)")(
      test("document marker present") {
        val yaml   = Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Alice"))
        val result = YamlWriter.write(yaml, YamlOptions.pretty)
        assertTrue(result.startsWith("---\n"))
      }
    ),
    suite("scalar quoting")(
      test("empty string is quoted") {
        val yaml   = Yaml.Scalar("")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "\"\"")
      },
      test("null-like value is quoted") {
        val yaml   = Yaml.Scalar("null")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "\"null\"")
      },
      test("Null variant is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("Null")) == "\"Null\"")
      },
      test("NULL variant is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("NULL")) == "\"NULL\"")
      },
      test("tilde is quoted") {
        val yaml   = Yaml.Scalar("~")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "\"~\"")
      },
      test("true-like value is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("true")) == "\"true\"")
      },
      test("false-like value is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("false")) == "\"false\"")
      },
      test("True/False variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("True")) == "\"True\"" &&
            YamlWriter.write(Yaml.Scalar("False")) == "\"False\""
        )
      },
      test("TRUE/FALSE variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("TRUE")) == "\"TRUE\"" &&
            YamlWriter.write(Yaml.Scalar("FALSE")) == "\"FALSE\""
        )
      },
      test("yes/no variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("yes")) == "\"yes\"" &&
            YamlWriter.write(Yaml.Scalar("no")) == "\"no\""
        )
      },
      test("Yes/No variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("Yes")) == "\"Yes\"" &&
            YamlWriter.write(Yaml.Scalar("No")) == "\"No\""
        )
      },
      test("YES/NO variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("YES")) == "\"YES\"" &&
            YamlWriter.write(Yaml.Scalar("NO")) == "\"NO\""
        )
      },
      test("on/off variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("on")) == "\"on\"" &&
            YamlWriter.write(Yaml.Scalar("off")) == "\"off\""
        )
      },
      test("On/Off variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("On")) == "\"On\"" &&
            YamlWriter.write(Yaml.Scalar("Off")) == "\"Off\""
        )
      },
      test("ON/OFF variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar("ON")) == "\"ON\"" &&
            YamlWriter.write(Yaml.Scalar("OFF")) == "\"OFF\""
        )
      },
      test(".inf variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar(".inf")) == "\".inf\"" &&
            YamlWriter.write(Yaml.Scalar("-.inf")) == "\"-.inf\"" &&
            YamlWriter.write(Yaml.Scalar(".Inf")) == "\".Inf\"" &&
            YamlWriter.write(Yaml.Scalar("-.Inf")) == "\"-.Inf\"" &&
            YamlWriter.write(Yaml.Scalar(".INF")) == "\".INF\"" &&
            YamlWriter.write(Yaml.Scalar("-.INF")) == "\"-.INF\""
        )
      },
      test(".nan variants are quoted") {
        assertTrue(
          YamlWriter.write(Yaml.Scalar(".nan")) == "\".nan\"" &&
            YamlWriter.write(Yaml.Scalar(".NaN")) == "\".NaN\"" &&
            YamlWriter.write(Yaml.Scalar(".NAN")) == "\".NAN\""
        )
      },
      test("numeric-looking string is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("42")) == "\"42\"")
      },
      test("string starting with digit is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("3abc")) == "\"3abc\"")
      },
      test("string starting with + and digit is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("+5")) == "\"+5\"")
      },
      test("string starting with - and digit is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("-3")) == "\"-3\"")
      },
      test("string starting with + and dot is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("+.5")) == "\"+.5\"")
      },
      test("string starting with - and dot is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar("-.5")) == "\"-.5\"")
      },
      test("string starting with . and digit is quoted") {
        assertTrue(YamlWriter.write(Yaml.Scalar(".5")) == "\".5\"")
      },
      test("string with special first char is quoted") {
        val specialChars = List("'", "\"", "{", "[", "|", ">", "%", "@", "`", "&", "*", "!", "?")
        val results      = specialChars.map(c => YamlWriter.write(Yaml.Scalar(c + "test")))
        assertTrue(results.forall(r => r.startsWith("\"")))
      },
      test("string with colon-space is quoted") {
        val yaml   = Yaml.Scalar("key: value")
        val result = YamlWriter.write(yaml)
        assertTrue(result.startsWith("\""))
      },
      test("string with space-hash is quoted") {
        val yaml   = Yaml.Scalar("value #comment")
        val result = YamlWriter.write(yaml)
        assertTrue(result.startsWith("\""))
      },
      test("string with newline is quoted") {
        val yaml   = Yaml.Scalar("line1\nline2")
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("\\n"))
      },
      test("string with tab is quoted") {
        val yaml   = Yaml.Scalar("a\tb")
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("\\t") || result.contains("\t"))
      },
      test("string with carriage return is quoted") {
        val yaml   = Yaml.Scalar("a\rb")
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("\\r"))
      },
      test("string with control char is quoted with \\u escape") {
        val yaml   = Yaml.Scalar("a\u0001b")
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("\\u0001"))
      },
      test("string with backspace is quoted") {
        val yaml   = Yaml.Scalar("a\bb")
        val result = YamlWriter.write(yaml)
        assertTrue(result.contains("\\b"))
      },
      test("normal string is not quoted") {
        val yaml   = Yaml.Scalar("hello")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "hello")
      }
    ),
    suite("tag-based quoting bypass")(
      test("!!bool tag bypasses quoting for true") {
        val yaml = Yaml.Scalar("true", tag = Some(YamlTag.Bool))
        assertTrue(YamlWriter.write(yaml) == "true")
      },
      test("!!bool tag bypasses quoting for false") {
        val yaml = Yaml.Scalar("false", tag = Some(YamlTag.Bool))
        assertTrue(YamlWriter.write(yaml) == "false")
      },
      test("!!int tag bypasses quoting for number") {
        val yaml = Yaml.Scalar("42", tag = Some(YamlTag.Int))
        assertTrue(YamlWriter.write(yaml) == "42")
      },
      test("!!float tag bypasses quoting for number") {
        val yaml = Yaml.Scalar("3.14", tag = Some(YamlTag.Float))
        assertTrue(YamlWriter.write(yaml) == "3.14")
      },
      test("!!null tag bypasses quoting") {
        val yaml = Yaml.Scalar("null", tag = Some(YamlTag.Null))
        assertTrue(YamlWriter.write(yaml) == "null")
      },
      test("other tags do not bypass quoting") {
        val yaml = Yaml.Scalar("42", tag = Some(YamlTag.Str))
        assertTrue(YamlWriter.write(yaml) == "\"42\"")
      }
    ),
    suite("writeToBytes")(
      test("produces UTF-8 bytes") {
        val yaml  = Yaml.Scalar("hello")
        val bytes = YamlWriter.writeToBytes(yaml)
        assertTrue(new String(bytes, "UTF-8") == "hello")
      }
    ),
    suite("NullValue rendering")(
      test("NullValue renders as null") {
        assertTrue(YamlWriter.write(Yaml.NullValue) == "null")
      }
    ),
    suite("round-trip write→read")(
      test("simple mapping round-trips") {
        val yaml    = Yaml.Mapping.fromStringKeys("key" -> Yaml.Scalar("value"))
        val written = YamlWriter.write(yaml)
        val read    = YamlReader.read(written)
        assertTrue(read == Right(yaml))
      },
      test("nested structure round-trips") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "person" -> Yaml.Mapping.fromStringKeys(
            "name" -> Yaml.Scalar("Alice"),
            "age"  -> Yaml.Scalar("30", tag = Some(YamlTag.Int))
          )
        )
        val written = YamlWriter.write(yaml)
        val parsed  = YamlReader.read(written)
        assertTrue(parsed.isRight)
      }
    ),
    suite("looksNumeric edge cases")(
      test("single + is not numeric") {
        val yaml   = Yaml.Scalar("+")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "+")
      },
      test("single - is not numeric") {
        val yaml   = Yaml.Scalar("-")
        val result = YamlWriter.write(yaml)
        assertTrue(result == "-")
      },
      test("single . is not numeric") {
        val yaml   = Yaml.Scalar(".")
        val result = YamlWriter.write(yaml)
        assertTrue(result == ".")
      },
      test(".abc is not numeric") {
        val yaml   = Yaml.Scalar(".abc")
        val result = YamlWriter.write(yaml)
        assertTrue(result == ".abc")
      }
    ),
    suite("custom indent step")(
      test("indent step 4") {
        val yaml = Yaml.Mapping.fromStringKeys(
          "outer" -> Yaml.Mapping.fromStringKeys("inner" -> Yaml.Scalar("v"))
        )
        val result = YamlWriter.write(yaml, YamlOptions(indentStep = 4))
        assertTrue(result.contains("    inner: v"))
      }
    )
  )
}
