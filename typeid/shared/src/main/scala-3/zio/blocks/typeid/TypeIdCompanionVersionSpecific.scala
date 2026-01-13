package zio.blocks.typeid

trait TypeIdCompanionVersionSpecific {
  /**
   * Derive a TypeId for any type or type constructor using macros.
   * This is the primary way to obtain a TypeId.
   *
   * Example:
   * {{{
   * val stringId = TypeId.derive[String]
   * val listId = TypeId.derive[List]
   * val mapId = TypeId.derive[Map]
   * }}}
   */
  inline def derive[A <: AnyKind]: TypeId[A] = ${TypeIdMacros.deriveImpl[A]}
}
