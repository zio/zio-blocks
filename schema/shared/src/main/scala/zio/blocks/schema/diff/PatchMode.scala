package zio.blocks.schema.diff

sealed trait PatchMode

object PatchMode {

  /**
   * Fail immediately on any mismatch or violation (e.g. index out of bounds,
   * missing key).
   */
  case object Strict extends PatchMode

  /** Skip operations that cannot be applied, continuing with the rest. */
  case object Lenient extends PatchMode

  /**
   * Force apply: overwrite values or create missing paths/keys regardless of
   * current state.
   */
  case object Clobber extends PatchMode
}
