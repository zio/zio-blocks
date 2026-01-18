package zio.blocks.schema.json

sealed trait JsonPatchMode

object JsonPatchMode {

  // Fail on precondition violations.
  case object Strict extends JsonPatchMode

  // Skip operations that fail preconditions.
  case object Lenient extends JsonPatchMode

  // Replace/overwrite on conflicts.
  case object Clobber extends JsonPatchMode
}
