/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Strategy for merging JSON values.
 */
sealed trait MergeStrategy

object MergeStrategy {

  /**
   * Automatically determines merge behavior based on value types:
   *   - Objects: deep merge (recurse into matching keys)
   *   - Arrays: concatenate
   *   - Primitives: right wins
   */
  case object Auto extends MergeStrategy

  /**
   * Deep merge for objects; concatenate arrays.
   */
  case object Deep extends MergeStrategy

  /**
   * Shallow merge: right value wins for any key conflict.
   */
  case object Shallow extends MergeStrategy

  /**
   * Concatenate arrays; for objects and primitives, right wins.
   */
  case object Concat extends MergeStrategy

  /**
   * Right value always wins (replacement).
   */
  case object Replace extends MergeStrategy

  /**
   * Custom merge function.
   *
   * @param f
   *   A function receiving path and both values, returning merged result
   */
  final case class Custom(f: (DynamicOptic, Json, Json) => Json) extends MergeStrategy
}
