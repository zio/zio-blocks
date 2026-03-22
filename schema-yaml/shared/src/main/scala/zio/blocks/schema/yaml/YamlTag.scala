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

/** Represents a YAML tag that annotates node types (e.g. `!!str`, `!!int`). */
sealed trait YamlTag

/**
 * Predefined YAML core schema tags and a [[Custom]] variant for user-defined
 * tags.
 */
object YamlTag {
  case object Str                      extends YamlTag
  case object Bool                     extends YamlTag
  case object Int                      extends YamlTag
  case object Float                    extends YamlTag
  case object Null                     extends YamlTag
  case object Seq                      extends YamlTag
  case object Map                      extends YamlTag
  case object Timestamp                extends YamlTag
  final case class Custom(uri: String) extends YamlTag

  val str: YamlTag       = Str
  val bool: YamlTag      = Bool
  val int: YamlTag       = Int
  val float: YamlTag     = Float
  val `null`: YamlTag    = Null
  val seq: YamlTag       = Seq
  val map: YamlTag       = Map
  val timestamp: YamlTag = Timestamp

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
