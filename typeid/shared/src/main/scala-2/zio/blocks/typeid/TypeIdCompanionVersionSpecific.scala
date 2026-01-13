package zio.blocks.typeid

import scala.language.experimental.macros

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
  def derive[A]: TypeId[A] = macro TypeIdMacros.deriveImpl[A]
}
