package zio.blocks.typeid

/**
 * Version-specific TypeId derivation support for Scala 3.
 */
trait TypeIdVersionSpecific {

  /**
   * Derive a TypeId for any type or type constructor.
   *
   * Example:
   * {{{
   * val stringId = TypeId.of[String]
   * val listId = TypeId.of[List]
   * val listIntId = TypeId.of[List[Int]]
   * }}}
   */
  transparent inline def of[A <: AnyKind]: TypeId[A] = TypeIdMacros.derive[A]

  /**
   * Implicit/given derivation for generic contexts.
   */
  inline given [A <: AnyKind]: TypeId[A] = TypeIdMacros.derive[A]
}
