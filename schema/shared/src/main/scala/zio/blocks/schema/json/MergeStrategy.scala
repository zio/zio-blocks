package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Strategy for merging two JSON values.
 */
sealed trait MergeStrategy

object MergeStrategy {

  /**
   * Automatically determines the best merge strategy based on the types of
   * values:
   *   - Objects are merged deeply (recursively)
   *   - Arrays are concatenated
   *   - Other values are replaced
   */
  case object Auto extends MergeStrategy

  /**
   * Recursively merges objects. For non-object values, the right-hand side
   * replaces the left-hand side. Arrays are concatenated.
   */
  case object Deep extends MergeStrategy

  /**
   * Performs a shallow merge of objects (only top-level keys). Nested objects
   * are replaced rather than merged. Arrays are concatenated.
   */
  case object Shallow extends MergeStrategy

  /**
   * The right-hand side value completely replaces the left-hand side value.
   */
  case object Replace extends MergeStrategy

  /**
   * For arrays, concatenates them. For objects, merges keys. For other types,
   * replaces with the right-hand side.
   */
  case object Concat extends MergeStrategy

  /**
   * Custom merge strategy that allows full control over how values are merged.
   * The function receives the path to the current location, the left value, and
   * the right value, and returns the merged result.
   *
   * @param f
   *   A function that takes (path, left, right) and returns the merged JSON
   *   value
   */
  final case class Custom(f: (DynamicOptic, Json, Json) => Json) extends MergeStrategy
}
