package zio.blocks.schema

/**
 * Controls how patch operations handle precondition violations.
 */
sealed trait PatchMode

object PatchMode {
  
  /**
   * Fail on precondition violations (e.g., modifying non-existent key).
   * This is the default mode.
   */
  case object Strict extends PatchMode
  
  /**
   * Skip operations that fail preconditions.
   * Useful for best-effort patching.
   */
  case object Lenient extends PatchMode
  
  /**
   * Replace/overwrite on conflicts.
   * Forces operations even when preconditions aren't met.
   */
  case object Clobber extends PatchMode
}
