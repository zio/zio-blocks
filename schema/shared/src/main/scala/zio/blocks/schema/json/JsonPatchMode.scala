package zio.blocks.schema.json

import zio.blocks.schema.patch.PatchMode

/**
 * Controls how JsonPatch application handles failures.
 *
 * Mirrors [[zio.blocks.schema.patch.PatchMode]] semantics.
 */
sealed trait JsonPatchMode extends Product with Serializable

object JsonPatchMode {

  /** Fail on precondition violations. */
  case object Strict extends JsonPatchMode

  /** Skip operations that fail preconditions. */
  case object Lenient extends JsonPatchMode

  /** Replace/overwrite on conflicts. */
  case object Clobber extends JsonPatchMode

  private[json] def toPatchMode(mode: JsonPatchMode): PatchMode =
    mode match {
      case Strict  => PatchMode.Strict
      case Lenient => PatchMode.Lenient
      case Clobber => PatchMode.Clobber
    }
}
