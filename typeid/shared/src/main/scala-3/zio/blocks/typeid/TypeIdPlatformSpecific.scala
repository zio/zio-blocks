package zio.blocks.typeid

trait TypeIdPlatformSpecific {
  inline def derive[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.deriveMacro[A] }
}
