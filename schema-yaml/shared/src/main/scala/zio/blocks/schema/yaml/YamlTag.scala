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

/**
 * A YAML tag on a [[Yaml.Scalar]], such as `!!int` or `!!str`. The `Int`,
 * `Float`, `Bool` and `Null` tags stop `YamlWriter` from quoting a scalar's
 * non-empty text; the writer never prints a tag, and `YamlReader` never sets
 * one.
 */
sealed trait YamlTag

/**
 * The standard YAML tags (`!!str`, `!!bool`, `!!int`, `!!float`, `!!null`,
 * `!!seq`, `!!map` and `!!timestamp`), [[YamlTag.Custom]] for any other tag,
 * and conversions to and from tag text.
 */
object YamlTag {

  /** The `!!str` tag, for text. */
  case object Str extends YamlTag

  /** The `!!bool` tag, for `true` and `false`. */
  case object Bool extends YamlTag

  /** The `!!int` tag, for integers. */
  case object Int extends YamlTag

  /** The `!!float` tag, for floating-point numbers. */
  case object Float extends YamlTag

  /** The `!!null` tag, for the null value. */
  case object Null extends YamlTag

  /** The `!!seq` tag, for sequences. */
  case object Seq extends YamlTag

  /** The `!!map` tag, for mappings. */
  case object Map extends YamlTag

  /** The `!!timestamp` tag, for dates and times. */
  case object Timestamp extends YamlTag

  /**
   * Any tag that is not a standard one; `uri` is the tag text, such as
   * `!!binary` or `!local`.
   */
  final case class Custom(uri: String) extends YamlTag

  /** The `!!str` tag, as a `YamlTag`. */
  val str: YamlTag = Str

  /** The `!!bool` tag, as a `YamlTag`. */
  val bool: YamlTag = Bool

  /** The `!!int` tag, as a `YamlTag`. */
  val int: YamlTag = Int

  /** The `!!float` tag, as a `YamlTag`. */
  val float: YamlTag = Float

  /** The `!!null` tag, as a `YamlTag`. */
  val `null`: YamlTag = Null

  /** The `!!seq` tag, as a `YamlTag`. */
  val seq: YamlTag = Seq

  /** The `!!map` tag, as a `YamlTag`. */
  val map: YamlTag = Map

  /** The `!!timestamp` tag, as a `YamlTag`. */
  val timestamp: YamlTag = Timestamp

  /**
   * Returns the tag for text such as `!!int`. Text that is not one of the
   * standard tags becomes `Custom(tag)`.
   */
  def fromString(tag: String): YamlTag = tag match {
    case "!!str"       => Str
    case "!!bool"      => Bool
    case "!!int"       => Int
    case "!!float"     => Float
    case "!!null"      => Null
    case "!!seq"       => Seq
    case "!!map"       => Map
    case "!!timestamp" => Timestamp
    case other         => Custom(other)
  }

  /**
   * Returns the text of a tag, such as `!!int`; a `Custom` tag returns its
   * `uri` unchanged.
   */
  def toTagString(tag: YamlTag): String = tag match {
    case Str         => "!!str"
    case Bool        => "!!bool"
    case Int         => "!!int"
    case Float       => "!!float"
    case Null        => "!!null"
    case Seq         => "!!seq"
    case Map         => "!!map"
    case Timestamp   => "!!timestamp"
    case Custom(uri) => uri
  }
}
