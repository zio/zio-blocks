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
 * A YAML document as a tree: a [[Yaml.Mapping]], a [[Yaml.Sequence]], a
 * [[Yaml.Scalar]] or [[Yaml.NullValue]]. Build one by hand, read one from text
 * with `YamlReader.read`, or get one from a codec with `YamlCodec#encodeValue`;
 * write it as text with `print` or `YamlWriter.write`, or convert it with
 * `toJson`.
 *
 * The tree keeps no comments, anchors or styles. A scalar holds its text and,
 * optionally, a [[YamlTag]]: the writer quotes a scalar whose text starts like
 * a number or is a boolean keyword unless it has an `Int`, `Float` or `Bool`
 * tag, so `3` without a tag is written as `"3"`.
 *
 * {{{
 * val app = Yaml.Mapping.fromStringKeys(
 *   "service"  -> Yaml.Scalar("checkout"),
 *   "replicas" -> Yaml.Scalar("3", Some(YamlTag.Int)),
 *   "regions"  -> Yaml.Sequence(Yaml.Scalar("eu-west-1"), Yaml.Scalar("us-east-1"))
 * )
 *
 * app.print
 * // service: checkout
 * // replicas: 3
 * // regions:
 * //   - eu-west-1
 * //   - us-east-1
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/yaml/ YAML]]
 */
sealed trait Yaml {

  /**
   * Writes this tree as YAML text in block style with `YamlOptions.default`:
   * two-space indentation, no `---` line and no trailing line break. A scalar
   * is quoted when it is empty, when its text starts like a number (a digit;
   * `-` or `+` before a digit or `.`; or `.` before a digit) or is a boolean,
   * null or other YAML keyword or syntax, or when it contains a line break,
   * `: ` or ` #`; an `Int`, `Float`, `Bool` or `Null` tag turns quoting off for
   * non-empty text. A value that starts with `#` or `- `, ends with `:`, or
   * starts or ends with a space is left unquoted, so other YAML readers drop
   * it, reject it or trim it (a known bug).
   *
   * {{{
   * val sensor = Yaml.Mapping.fromStringKeys(
   *   "id"      -> Yaml.Scalar("t-101"),
   *   "celsius" -> Yaml.Scalar("21.5", Some(YamlTag.Float)),
   *   "online"  -> Yaml.Scalar("true")
   * )
   *
   * sensor.print
   * // id: t-101
   * // celsius: 21.5
   * // online: "true"
   * }}}
   */
  def print: String = YamlWriter.write(this, YamlOptions.default)

  /**
   * Writes this tree as `print` does, with a leading `---` line
   * (`YamlOptions.pretty`).
   *
   * {{{
   * Yaml.Mapping.fromStringKeys("env" -> Yaml.Scalar("staging")).printPretty
   * // ---
   * // env: staging
   * }}}
   */
  def printPretty: String = YamlWriter.write(this, YamlOptions.pretty)

  /**
   * Converts this tree to a [[zio.blocks.schema.json.Json]] value, guessing the
   * type of each scalar from its text. `true`, `True` and `TRUE` (and the same
   * forms of `false`) become booleans; `null`, `~`, `Null` and `NULL` become
   * null; text that starts with a digit or `-` and parses as a JSON number
   * becomes a number; any other text becomes a string. Tags are ignored, so a
   * scalar tagged `Str` that holds `1042` becomes a number, and a tree read by
   * `YamlReader` keeps no quotes, so a quoted `"true"` in the text also becomes
   * a boolean (a known bug). A mapping key that is not a scalar becomes its
   * printed text.
   *
   * {{{
   * val order = Yaml.Mapping.fromStringKeys(
   *   "id"    -> Yaml.Scalar("A-17"),
   *   "total" -> Yaml.Scalar("49.90"),
   *   "paid"  -> Yaml.Scalar("true"),
   *   "sku"   -> Yaml.Scalar("1042", Some(YamlTag.Str))
   * )
   *
   * order.toJson.print // {"id":"A-17","total":49.90,"paid":true,"sku":1042}
   * }}}
   */
  def toJson: Json = YamlJsonInterop.yamlToJson(this)
}

/** The cases of [[Yaml]], their constructors, and conversion from JSON. */
object Yaml {

  /**
   * A YAML mapping: key-value entries, kept in order. A key is any [[Yaml]]
   * node, but `YamlWriter` writes a key that is not a [[Yaml.Scalar]] as
   * `null`.
   *
   * {{{
   * val labels = Yaml.Mapping(
   *   Yaml.Scalar("app")  -> Yaml.Scalar("checkout"),
   *   Yaml.Scalar("tier") -> Yaml.Scalar("backend")
   * )
   *
   * labels.print
   * // app: checkout
   * // tier: backend
   * }}}
   */
  final case class Mapping(entries: Chunk[(Yaml, Yaml)]) extends Yaml

  /**
   * A YAML sequence: an ordered list of nodes.
   *
   * {{{
   * Yaml.Sequence(Yaml.Scalar("lint"), Yaml.Scalar("test"), Yaml.Scalar("deploy")).print
   * // - lint
   * // - test
   * // - deploy
   * }}}
   */
  final case class Sequence(elements: Chunk[Yaml]) extends Yaml

  /**
   * A YAML scalar: the text of a string, number, boolean or date, with an
   * optional [[YamlTag]]. The text is stored as it is. An `Int`, `Float`,
   * `Bool` or `Null` tag stops `YamlWriter` from quoting non-empty text; other
   * tags change nothing, and the writer never writes the tag itself.
   *
   * {{{
   * Yaml.Scalar("8080").print                    // "8080"
   * Yaml.Scalar("8080", Some(YamlTag.Int)).print // 8080
   * }}}
   */
  final case class Scalar(value: String, tag: Option[YamlTag] = None) extends Yaml

  /**
   * The YAML null value, written as `null`. `YamlReader` reads an empty value,
   * `null` and `~` as `NullValue`, but `Null` and `NULL` as a [[Yaml.Scalar]].
   *
   * {{{
   * Yaml.Mapping.fromStringKeys("proxy" -> Yaml.NullValue).print // proxy: null
   * }}}
   */
  case object NullValue extends Yaml

  /** Constructors for [[Yaml.Mapping]]. */
  object Mapping {

    /** The mapping with no entries, written as `{}`. */
    val empty: Mapping = Mapping(Chunk.empty)

    /** Creates a mapping from key-value pairs, in the given order. */
    def apply(entries: (Yaml, Yaml)*): Mapping = new Mapping(Chunk.from(entries))

    /**
     * Creates a mapping from string-keyed entries, wrapping each key in an
     * untagged [[Yaml.Scalar]].
     */
    def fromStringKeys(entries: (String, Yaml)*): Mapping =
      new Mapping(Chunk.from(entries.map { case (k, v) => (new Scalar(k), v) }))
  }

  /** Constructors for [[Yaml.Sequence]]. */
  object Sequence {

    /** The sequence with no elements, written as `[]`. */
    val empty: Sequence = Sequence(Chunk.empty)

    /** Creates a sequence from the given nodes, in order. */
    def apply(elements: Yaml*): Sequence = new Sequence(Chunk.from(elements))
  }

  /**
   * Converts a [[zio.blocks.schema.json.Json]] value to a tree. A whole number
   * gets the `Int` tag, any other number the `Float` tag and a boolean the
   * `Bool` tag, so they print without quotes; a string becomes an untagged
   * scalar, and JSON `null` becomes [[Yaml.NullValue]].
   *
   * {{{
   * import zio.blocks.schema.json.Json
   *
   * val json = Json.Object("name" -> Json.String("api"), "replicas" -> Json.Number(3), "canary" -> Json.Boolean(false))
   *
   * Yaml.fromJson(json).print
   * // name: api
   * // replicas: 3
   * // canary: false
   * }}}
   */
  def fromJson(json: Json): Yaml = YamlJsonInterop.jsonToYaml(json)
}
