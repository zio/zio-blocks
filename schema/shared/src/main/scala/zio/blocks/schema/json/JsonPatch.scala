package zio.blocks.schema.json

import zio.blocks.schema.patch.DynamicPatch

/**
 * Represents a patch that can be applied to JSON values.
 *
 * Supports RFC 6902 operations (add, remove, replace, move, copy, test)
 * plus extensions for LCS-based sequence diffs and string diffs.
 *
 * This is a placeholder implementation - actual implementation TBD.
 */
sealed trait JsonPatch {

  /**
   * Converts this JSON patch to a [[DynamicPatch]].
   */
  def toDynamicPatch: DynamicPatch
}

object JsonPatch {

  /**
   * Creates an empty patch (no operations).
   */
  val empty: JsonPatch = ???

  /**
   * Creates a patch from a [[DynamicPatch]].
   *
   * May fail if the DynamicPatch contains operations not representable in JSON.
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = ???
}
