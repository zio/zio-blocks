package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.macros.AccessorMacros

trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

object ToDynamicOptic {
  inline def derive[S, A](inline selector: S => A): ToDynamicOptic[S, A] = 
    AccessorMacros.derive(selector)
}