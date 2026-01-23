package zio.blocks.typeid

trait TypeIdPlatformSpecific {
  inline def from[A]: TypeId[A] = ${ TypeIdMacros.fromImpl[A] }
  inline def of[A]: TypeId[A]   = ${ TypeIdMacros.fromImpl[A] }

  inline given derived[A]: TypeId[A] = ${ TypeIdMacros.fromImpl[A] }
}
