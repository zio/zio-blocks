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

import zio.blocks.schema.json.Json

object YamlJsonInterop {

  def yamlToJson(yaml: Yaml): Json = yaml match {
    case Yaml.Mapping(entries) =>
      val fields = entries.map {
        case (Yaml.Scalar(key, _), value) => (key, yamlToJson(value))
        case (key, value)                 => (key.print, yamlToJson(value))
      }
      new Json.Object(fields)
    case Yaml.Sequence(elements) =>
      new Json.Array(elements.map(yamlToJson))
    case Yaml.Scalar(value, _) =>
      value match {
        case "true" | "True" | "TRUE"       => Json.True
        case "false" | "False" | "FALSE"    => Json.False
        case "null" | "~" | "Null" | "NULL" => Json.Null
        case _                              =>
          try Json.Number(BigDecimal(value))
          catch { case _: NumberFormatException => new Json.String(value) }
      }
    case _: Yaml.NullValue.type => Json.Null
  }

  def jsonToYaml(json: Json): Yaml = json match {
    case obj: Json.Object =>
      Yaml.Mapping(obj.value.map { case (key, value) => (Yaml.Scalar(key): Yaml, jsonToYaml(value)) })
    case arr: Json.Array =>
      Yaml.Sequence(arr.value.map(jsonToYaml))
    case str: Json.String =>
      Yaml.Scalar(str.value)
    case num: Json.Number =>
      val tag = if (num.value.isWhole) Some(YamlTag.Int) else Some(YamlTag.Float)
      Yaml.Scalar(num.value.toString, tag = tag)
    case bool: Json.Boolean =>
      Yaml.Scalar(bool.value.toString, tag = Some(YamlTag.Bool))
    case _: Json.Null.type =>
      Yaml.NullValue
  }
}
