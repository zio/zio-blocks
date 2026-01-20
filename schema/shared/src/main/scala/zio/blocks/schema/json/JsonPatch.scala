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

import zio.blocks.schema.patch.DynamicPatch
import scala.annotation.unused

/**
 * Represents a patch that can be applied to JSON values.
 *
 * Supports RFC 6902 operations (add, remove, replace, move, copy, test) plus
 * extensions for LCS-based sequence diffs and string diffs.
 *
 * Placeholder - actual implementation TBD.
 */
sealed trait JsonPatch {

  /**
   * Converts this JSON patch to a [[DynamicPatch]].
   */
  def toDynamicPatch: DynamicPatch
}

object JsonPatch {
  private case object EmptyPatch extends JsonPatch {
    def toDynamicPatch: DynamicPatch = DynamicPatch.empty
  }

  /**
   * Creates an empty patch (no operations).
   */
  val empty: JsonPatch = EmptyPatch

  /**
   * Creates a patch from a [[DynamicPatch]].
   *
   * May fail if the DynamicPatch contains operations not representable in JSON.
   */
  def fromDynamicPatch(@unused patch: DynamicPatch): Either[JsonError, JsonPatch] =
    Right(empty) // Placeholder implementation
}
