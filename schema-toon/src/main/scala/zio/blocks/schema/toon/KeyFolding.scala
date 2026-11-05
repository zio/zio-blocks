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

package zio.blocks.schema.toon

/**
 * Chooses whether the `DynamicValue` codec writes a chain of nested records
 * that each have one field as one dot-separated key, such as
 * `net.port.number: 8080`. Set it with [[WriterConfig.withKeyFolding]]. A codec
 * derived for a case class ignores it and writes nested records as indented
 * blocks. [[PathExpansion]] is the matching setting for reading.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
sealed abstract class KeyFolding

/** The cases of `KeyFolding`: `KeyFolding.Off` and `KeyFolding.Safe`. */
object KeyFolding {

  /** Writes every nested record as an indented block. The default. */
  case object Off extends KeyFolding

  /**
   * Folds a chain of single-field records into one dotted key, joining at most
   * [[WriterConfig.flattenDepth]] keys. A key that is not a plain identifier
   * (ASCII letters, digits and `_`, not starting with a digit) ends the chain,
   * and a chain whose dotted key would clash with another key of the record or
   * with a dotted key already in the value is written nested instead.
   */
  case object Safe extends KeyFolding
}
