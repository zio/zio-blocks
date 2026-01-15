package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.macros.AccessorMacros

trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

/**
 * Note: The @nowarn("msg=unused import") was removed because the compiler
 * detected that all imports inside this object are actually used, making the
 * suppression annotation redundant.
 */
object ToDynamicOptic {
  import scala.language.experimental.macros
  import scala.language.implicitConversions

  implicit def derive[S, A](selector: S => A): ToDynamicOptic[S, A] = macro AccessorMacros.deriveImpl[S, A]
}
