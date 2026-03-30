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

import zio.blocks.schema._
import zio.blocks.schema.json.JsonCodec
import zio.test._

object YamlInterpolatorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("YamlInterpolatorSpec")(
    test("parses Yaml literal") {
      assertTrue(
        yaml""" "hello"""" == Yaml.Scalar("hello"),
        yaml""""Привіт" """ == Yaml.Scalar("Привіт"),
        yaml""" "★🎸🎧⋆｡°⋆" """ == Yaml.Scalar("★🎸🎧⋆｡°⋆"),
        yaml"42" == Yaml.Scalar("42"),
        yaml"true" == Yaml.Scalar("true")
      )
    },
    test("supports interpolated String keys and values") {
      check(Gen.string(Gen.char.filter(x => x >= 0x20)).filter(_.nonEmpty))(x =>
        assertTrue(
          yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(x)),
          yaml"$x: v" == Yaml.Mapping(Yaml.Scalar(x) -> Yaml.Scalar("v"))
        )
      )
    },
    test("supports interpolated Boolean keys and values") {
      check(Gen.boolean)(x =>
        assertTrue(
          yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(x.toString)),
          yaml"$x: v" == Yaml.Mapping(Yaml.Scalar(x.toString) -> Yaml.Scalar("v"))
        )
      )
    },
    test("supports interpolated Int keys and values") {
      check(Gen.int)(x =>
        assertTrue(
          yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(x.toString)),
          yaml"$x: v" == Yaml.Mapping(Yaml.Scalar(x.toString) -> Yaml.Scalar("v"))
        )
      )
    },
    test("supports interpolated Double keys and values") {
      check(Gen.double)(x =>
        assertTrue(
          yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(JsonCodec.doubleCodec.encodeToString(x))),
          yaml"$x: v" == Yaml.Mapping(Yaml.Scalar(JsonCodec.doubleCodec.encodeToString(x)) -> Yaml.Scalar("v"))
        )
      )
    },
    test("supports interpolated UUID keys and values") {
      check(Gen.uuid)(x =>
        assertTrue(
          yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(x.toString)),
          yaml"$x: v" == Yaml.Mapping(Yaml.Scalar(x.toString) -> Yaml.Scalar("v")) ||
            yaml"$x: v" == Yaml.Mapping(Yaml.Scalar("\"" + x.toString + "\"") -> Yaml.Scalar("v"))
        )
      )
    },
    test("supports interpolated Option values") {
      val some: Option[String] = Some("Alice")
      val none: Option[String] = None
      assertTrue(
        yaml"k: $some" == Yaml.Mapping.fromStringKeys("k" -> Yaml.Scalar(some.get)),
        yaml"k: $none" == Yaml.Mapping.fromStringKeys("k" -> Yaml.NullValue)
      )
    },
    test("supports interpolated Null values") {
      val x: String = null
      assertTrue(yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.NullValue))
    },
    test("supports interpolated Unit values") {
      val x: Unit = ()
      assertTrue(yaml"k: $x" == Yaml.Mapping.fromStringKeys("k" -> Yaml.NullValue))
    }
  )
}
