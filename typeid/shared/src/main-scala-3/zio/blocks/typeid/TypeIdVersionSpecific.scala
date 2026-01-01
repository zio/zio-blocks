package zio.blocks.typeid

import scala.quoted._

trait TypeIdVersionSpecific {
  inline def derive[A <: AnyKind]: TypeId[A] =
    ${ TypeIdMacros.deriveMacro[A] }
}
