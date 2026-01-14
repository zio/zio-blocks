package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

object ToDynamicOptic {
  import scala.language.experimental.macros
  import scala.language.implicitConversions

  implicit def derive[S, A](selector: S => A): ToDynamicOptic[S, A] = 
    macro zio.blocks.schema.migration.macros.AccessorMacros.deriveImpl[S, A]
}