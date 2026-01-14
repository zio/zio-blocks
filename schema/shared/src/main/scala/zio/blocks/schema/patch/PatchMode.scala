package zio.blocks.schema.patch

import zio.blocks.schema._

sealed trait PatchMode

object PatchMode {

  // Fail on precondition violations.
  case object Strict extends PatchMode

  // Skip operations that fail preconditions.
  case object Lenient extends PatchMode

  // Replace/overwrite on conflicts.

  case object Clobber extends PatchMode

  implicit lazy val schema: Schema[PatchMode] = Schema.derived

}
