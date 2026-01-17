package zio.blocks.typeid

import scala.language.experimental.macros

/**
 * Version-specific TypeId derivation support for Scala 2.13.
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
  def of[A]: TypeId[A] = macro TypeIdMacros.derive[A]

  /**
   * Implicit derivation for generic contexts.
   */
  implicit def deriveImplicit[A]: TypeId[A] = macro TypeIdMacros.derive[A]
}
