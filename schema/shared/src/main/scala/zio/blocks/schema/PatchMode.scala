package zio.blocks.schema

/**
 * PatchMode controls how patch operations handle conflicts and precondition violations.
 */
sealed trait PatchMode

object PatchMode {
  
  /**
   * Fail on precondition violations (e.g., modifying non-existent key,
   * inserting at occupied index).
   */
  case object Strict extends PatchMode
  
  /**
   * Skip operations that fail preconditions and continue with the rest.
   */
  case object Lenient extends PatchMode
  
  /**
   * Replace/overwrite on conflicts instead of failing.
   */
  case object Clobber extends PatchMode
}
