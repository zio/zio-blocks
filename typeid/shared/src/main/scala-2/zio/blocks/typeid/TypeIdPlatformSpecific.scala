package zio.blocks.typeid

import scala.language.experimental.macros

trait TypeIdPlatformSpecific {
  def derive[A <: AnyKind]: TypeId[A] = macro TypeIdMacros.deriveMacro[A]
}
