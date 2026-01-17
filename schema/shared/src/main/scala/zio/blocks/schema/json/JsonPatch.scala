package zio.blocks.schema.json

import zio.blocks.schema.patch.DynamicPatch
import scala.annotation.unused

/**
 * Represents a patch that can be applied to JSON values.
 *
 * Supports RFC 6902 operations (add, remove, replace, move, copy, test) plus extensions for LCS-based sequence diffs
 * and string diffs.
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
