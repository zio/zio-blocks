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

import zio.blocks.schema.json.{Json, JsonCodec}

import scala.util.control.NonFatal

object YamlJsonInterop {
  def yamlToJson(yaml: Yaml): Json = yaml match {
    case Yaml.Mapping(entries) =>
      new Json.Object(entries.map {
        case (Yaml.Scalar(key, _), value) => (key, yamlToJson(value))
        case (key, value)                 => (key.print, yamlToJson(value))
      })
    case Yaml.Sequence(elements) => new Json.Array(elements.map(yamlToJson))
    case Yaml.Scalar(value, _)   =>
      value match {
        case "true" | "True" | "TRUE"                                                  => Json.True
        case "false" | "False" | "FALSE"                                               => Json.False
        case "null" | "~" | "Null" | "NULL"                                            => Json.Null
        case s if s.nonEmpty && (Character.isDigit(s.charAt(0)) || s.charAt(0) == '-') =>
          try Json.Number(JsonCodec.bigDecimalCodec.decodeUnsafe(value))
          catch {
            case err if NonFatal(err) => new Json.String(value)
          }
        case _ => new Json.String(value)
      }
    case _ => Json.Null
  }

  def jsonToYaml(json: Json): Yaml = json match {
    case obj: Json.Object => new Yaml.Mapping(obj.value.map { case (k, v) => (Yaml.Scalar(k), jsonToYaml(v)) })
    case arr: Json.Array  => new Yaml.Sequence(arr.value.map(jsonToYaml))
    case str: Json.String => new Yaml.Scalar(str.value)
    case num: Json.Number =>
      if (num.value.isWhole) {
        new Yaml.Scalar(JsonCodec.bigIntCodec.encodeToString(num.value.toBigInt), tag = new Some(YamlTag.Int))
      } else new Yaml.Scalar(JsonCodec.bigDecimalCodec.encodeToString(num.value), tag = new Some(YamlTag.Float))
    case bool: Json.Boolean => new Yaml.Scalar(bool.value.toString, tag = Some(YamlTag.Bool))
    case _                  => Yaml.NullValue
  }
}
