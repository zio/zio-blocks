package zio.blocks.typeid

trait TypeIdPlatformSpecific {
  inline def from[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.fromImpl[A] }
  inline def of[A <: AnyKind]: TypeId[A]   = ${ TypeIdMacros.fromImpl[A] }

  inline given derived[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.fromImpl[A] }
}
