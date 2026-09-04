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
import zio.test._
import scala.util.Try

object YamlSupportMatrixSpec extends SchemaBaseSpec {

  private def failsClosed(input: String, fragment: String, line: Int): TestResult = {
    val failure = Try(YamlReader.read(input)).failed.toOption
    assertTrue(
      failure.exists(_.isInstanceOf[YamlCodecError]),
      failure.map(_.getMessage).exists(m => m.contains(fragment) && m.contains(s"(line: $line)"))
    )
  }

  def spec: Spec[TestEnvironment, Any] = suite("YamlSupportMatrix")(
    suite("rejected YAML 1.2 features fail closed with positioned errors")(
      test("anchor definitions are rejected") {
        failsClosed("base: &b\n  x: 1", "Anchors", 1)
      },
      test("aliases are rejected") {
        failsClosed("a: 1\nb: *a", "Aliases", 2)
      },
      test("top-level aliases are rejected") {
        failsClosed("*a", "Aliases", 1)
      },
      test("aliases in sequences are rejected") {
        failsClosed("- 1\n- *a", "Aliases", 2)
      },
      test("aliases in flow collections are rejected") {
        failsClosed("a: [*x, 1]", "Aliases", 1)
      },
      test("merge keys are rejected") {
        failsClosed("a: 1\n<<: b", "Merge keys", 2)
      },
      test("tags are rejected") {
        failsClosed("a: !str foo", "Tags", 1)
      },
      test("tags in sequences are rejected") {
        failsClosed("- !str foo", "Tags", 1)
      },
      test("tab indentation is rejected") {
        failsClosed("a: 1\n\tb: 2", "Tab", 2)
      },
      test("duplicate mapping keys are rejected") {
        failsClosed("a: 1\na: 2", "Duplicate mapping key", 2)
      },
      test("duplicate keys after quoting variations are rejected") {
        failsClosed("a: 1\n\"a\": 2", "Duplicate mapping key", 2)
      }
    ),
    suite("supported subset keeps parsing")(
      test("plain and quoted scalars") {
        assertTrue(
          YamlReader.read("a: 1") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("1")),
          YamlReader.read("a: \"x\"") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("x")),
          YamlReader.read("a: 'y'") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("y"))
        )
      },
      test("quoted stars and ampersands are plain strings, not references") {
        assertTrue(
          YamlReader.read("a: \"*x\"") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("*x")),
          YamlReader.read("a: '&y'") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("&y"))
        )
      },
      test("mid-token markers are plain strings") {
        assertTrue(
          YamlReader.read("a: a*b") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("a*b")),
          YamlReader.read("a: x&y") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("x&y"))
        )
      },
      test("nested mappings, sequences, flow, blocks, comments, markers") {
        assertTrue(
          YamlReader.read("outer:\n  inner: 1") ==
            Yaml.Mapping.fromStringKeys("outer" -> Yaml.Mapping.fromStringKeys("inner" -> Yaml.Scalar("1"))),
          YamlReader.read("- 1\n- 2") == Yaml.Sequence(Yaml.Scalar("1"), Yaml.Scalar("2")),
          YamlReader.read("a: {x: 1}") ==
            Yaml.Mapping.fromStringKeys("a" -> Yaml.Mapping(Chunk((Yaml.Scalar("x"), Yaml.Scalar("1"))))),
          YamlReader.read("a: [1, 2]") ==
            Yaml.Mapping.fromStringKeys("a" -> Yaml.Sequence(Yaml.Scalar("1"), Yaml.Scalar("2"))),
          YamlReader.read("# comment\na: 1") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("1")),
          YamlReader.read("---\na: 1") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("1")),
          YamlReader.read("a: |\n  line1\n  line2") ==
            Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("line1\nline2"))
        )
      },
      test("literal blocks keep star-led content literally") {
        assertTrue(
          YamlReader.read("a: |\n  *foo") == Yaml.Mapping.fromStringKeys("a" -> Yaml.Scalar("*foo"))
        )
      }
    ),
    suite("index-range parsing preserves output")(
      test("extra spacing around keys and values parses identically") {
        val expected = Yaml.Mapping(
          Chunk((Yaml.Scalar("a"), Yaml.Scalar("1")), (Yaml.Scalar("b"), Yaml.Scalar("2")))
        )
        assertTrue(
          YamlReader.read("a: 1\nb:   2  ") == expected,
          YamlReader.read("a  : 1\nb: 2") == expected,
          YamlReader.read("a: 1\nb: 2") == expected
        )
      },
      test("quoted keys with inner spacing parse identically") {
        assertTrue(
          YamlReader.read("\"a b\": 1") == Yaml.Mapping.fromStringKeys("a b" -> Yaml.Scalar("1"))
        )
      }
    ),
    suite("codec-level positioned errors")(
      test("unsupported input surfaces as a Left carrying the line") {
        val result = YamlCodec.stringCodec.decode("plain")
        assertTrue(result == Right("plain"))
        YamlCodec.stringCodec.decode("a: 1\nb: *x") match {
          case Left(err) =>
            assertTrue(err.message.contains("Aliases") && err.message.contains("(line: 2)"))
          case _ => assertTrue(false)
        }
      }
    )
  )
}
