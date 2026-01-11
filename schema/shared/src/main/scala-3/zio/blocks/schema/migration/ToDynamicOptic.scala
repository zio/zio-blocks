package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.macros.AccessorMacros

trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

object ToDynamicOptic {
  // Scala 3 এর জন্য ইনলাইন এন্ট্রি পয়েন্ট
  inline def derive[S, A](inline selector: S => A): ToDynamicOptic[S, A] = 
    AccessorMacros.derive(selector)
}