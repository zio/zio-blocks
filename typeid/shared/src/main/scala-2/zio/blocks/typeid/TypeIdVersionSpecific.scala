package zio.blocks.typeid

import scala.language.experimental.macros

trait TypeIdVersionSpecific {
  def derive[A <: AnyKind]: TypeId[A] = macro TypeIdMacros.deriveMacro[A]
}
