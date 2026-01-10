package zio.blocks.schema

/**
 * Controls how patches are applied when preconditions may not be met.
 */
sealed trait PatchMode

object PatchMode {

  /**
   * Fail on precondition violations (e.g. modifying non-existent key, inserting
   * at occupied index).
   */
  case object Strict extends PatchMode

  /**
   * Skip operations that fail preconditions, continuing with remaining ops.
   */
  case object Lenient extends PatchMode

  /**
   * Replace/overwrite on conflicts (e.g. overwrite existing keys).
   */
  case object Clobber extends PatchMode
}
