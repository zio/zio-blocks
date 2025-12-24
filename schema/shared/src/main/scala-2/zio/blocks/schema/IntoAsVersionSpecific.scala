package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Version-specific trait for Scala 2 macro implementations of `Into` and `As`
 * type classes.
 *
 * This trait provides the macro entry points for automatic derivation of
 * `Into[A, B]` and `As[A, B]` instances using Scala 2 macros.
 *
 * NOTE: Scala 2 macro support is not yet implemented. This trait provides
 * placeholder implementations that will fail at compile-time with a helpful
 * error message directing users to use Scala 3 or provide explicit instances.
 */
trait IntoAsVersionSpecific {
  def derived[A, B]: Into[A, B] = macro IntoAsVersionSpecificMacros.derivedIntoImpl[A, B]
  def derivedInto[A, B]: Into[A, B] = macro IntoAsVersionSpecificMacros.derivedIntoImpl[A, B]
  def derivedAs[A, B]: As[A, B] = macro IntoAsVersionSpecificMacros.derivedAsImpl[A, B]
}

object IntoAsVersionSpecificMacros {
  def derivedIntoImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  ): c.Expr[Into[A, B]] = {
    // TODO: Implement Scala 2 macro for Into derivation
    // For now, this is a placeholder that will fail at compile-time
    // indicating that Scala 2 support is not yet implemented
    import c.universe._
    c.abort(
      c.enclosingPosition,
      "Into[A, B] derivation is not yet implemented for Scala 2.13. " +
        "Please use Scala 3 for automatic derivation, or provide an explicit Into instance."
    )
  }

  def derivedAsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  ): c.Expr[As[A, B]] = {
    // TODO: Implement Scala 2 macro for As derivation
    // For now, this is a placeholder that will fail at compile-time
    // indicating that Scala 2 support is not yet implemented
    import c.universe._
    c.abort(
      c.enclosingPosition,
      "As[A, B] derivation is not yet implemented for Scala 2.13. " +
        "Please use Scala 3 for automatic derivation, or provide an explicit As instance."
    )
  }
}
