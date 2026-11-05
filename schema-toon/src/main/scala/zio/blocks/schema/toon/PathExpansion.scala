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
 * Chooses whether the `DynamicValue` codec reads a dot-separated key, such as
 * `net.port.number: 8080`, as nested records or as one key with dots in its
 * name. Set it with [[ReaderConfig.withExpandPaths]]. A codec derived for a
 * case class ignores it. To decode folded TOON into a case class, decode it as
 * a `DynamicValue` with [[PathExpansion.Safe]], then convert the result with
 * `Schema#fromDynamicValue`.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
sealed abstract class PathExpansion

object PathExpansion {

  /** Keeps a dotted key as one field name. The default. */
  case object Off extends PathExpansion

  /**
   * Expands an unquoted dotted key whose parts are all plain identifiers into
   * nested records (an identifier is ASCII letters, digits and `_`, not
   * starting with a digit), merging records that share a prefix. A quoted key
   * stays one field name. When an expanded key would replace a value that is
   * not a record, decoding fails in strict mode with `Path expansion conflict
   * at key 'a'`; with [[ReaderConfig.withStrict]] `false` the later value wins.
   */
  case object Safe extends PathExpansion
}
