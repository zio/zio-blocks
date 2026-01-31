package zio.blocks.schema.patch

sealed trait PatchMode

object PatchMode extends PatchModeCompanionVersionSpecific {

  // Fail on precondition violations.
  case object Strict extends PatchMode

  // Skip operations that fail preconditions.
  case object Lenient extends PatchMode

  // Replace/overwrite on conflicts.
  case object Clobber extends PatchMode
}
