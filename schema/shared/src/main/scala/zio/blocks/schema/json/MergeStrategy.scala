package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Strategy for merging two JSON values.
 *
 * Used by [[Json#merge]] to control how values are combined.
 */
sealed trait MergeStrategy

object MergeStrategy {

  /**
   * Automatically chooses an appropriate strategy based on value types:
   * - Objects: deep merge
   * - Arrays: concatenation
   * - Primitives: replacement (right-hand side wins)
   */
  case object Auto extends MergeStrategy

  /**
   * Deeply merges objects recursively, concatenates arrays, replaces primitives.
   */
  case object Deep extends MergeStrategy

  /**
   * Shallowly merges objects (no recursion), replaces all other types.
   */
  case object Shallow extends MergeStrategy

  /**
   * Always replaces left-hand side with right-hand side.
   */
  case object Replace extends MergeStrategy

  /**
   * Concatenates arrays if both are arrays, otherwise replaces.
   */
  case object Concat extends MergeStrategy

  /**
   * Custom merge function with full control.
   *
   * @param f Function receiving (path, left, right) => merged
   */
  final case class Custom(f: (DynamicOptic, Json, Json) => Json) extends MergeStrategy
}
