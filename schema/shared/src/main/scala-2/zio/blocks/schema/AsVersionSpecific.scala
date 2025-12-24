package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Version-specific trait for Scala 2 macro implementations of `As` type class.
 *
 * This trait provides the `derived` method for `As[A, B]` that delegates to
 * `derivedAs` macro implementation.
 */
trait AsVersionSpecific {
  def derived[A, B]: As[A, B] = macro IntoAsVersionSpecificMacros.derivedAsImpl[A, B]
  def derivedAs[A, B]: As[A, B] = macro IntoAsVersionSpecificMacros.derivedAsImpl[A, B]
}
