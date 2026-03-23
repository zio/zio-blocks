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
import zio.blocks.schema.json.Json

/**
 * A sealed trait representing a YAML node.
 *
 * The `Yaml` type provides a complete representation of YAML data with four
 * possible cases: [[Yaml.Mapping]], [[Yaml.Sequence]], [[Yaml.Scalar]], and
 * [[Yaml.NullValue]].
 */
sealed trait Yaml {

  /** Renders this YAML to a compact string (no document markers). */
  def print: String = YamlWriter.write(this, YamlOptions.default)

  /** Renders this YAML to a pretty-printed string (with document markers). */
  def printPretty: String = YamlWriter.write(this, YamlOptions.pretty)

  /**
   * Converts this YAML node to a [[zio.blocks.schema.json.Json]] equivalent.
   */
  def toJson: Json = YamlJsonInterop.yamlToJson(this)
}

/**
 * Companion object for [[Yaml]] providing factory methods and case class
 * definitions.
 */
object Yaml {

  /** A YAML mapping (key-value pairs). */
  final case class Mapping(entries: Chunk[(Yaml, Yaml)]) extends Yaml

  /** A YAML sequence (ordered list of nodes). */
  final case class Sequence(elements: Chunk[Yaml]) extends Yaml

  /** A YAML scalar (string value with optional tag). */
  final case class Scalar(value: String, tag: Option[YamlTag] = None) extends Yaml

  /** The YAML null value. */
  case object NullValue extends Yaml

  object Mapping {
    val empty: Mapping = Mapping(Chunk.empty)

    def apply(entries: (Yaml, Yaml)*): Mapping =
      new Mapping(Chunk.from(entries))

    /** Creates a [[Mapping]] from string-keyed entries. */
    def fromStringKeys(entries: (String, Yaml)*): Mapping =
      new Mapping(Chunk.from(entries.map { case (k, v) => (Scalar(k): Yaml, v) }))
  }

  object Sequence {
    val empty: Sequence = Sequence(Chunk.empty)

    def apply(elements: Yaml*): Sequence =
      new Sequence(Chunk.from(elements))
  }

  /** Converts a [[zio.blocks.schema.json.Json]] value to a [[Yaml]] node. */
  def fromJson(json: Json): Yaml = YamlJsonInterop.jsonToYaml(json)
}
