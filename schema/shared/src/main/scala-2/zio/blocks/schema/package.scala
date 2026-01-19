package zio.blocks

import scala.language.experimental.macros

/**
 * Package object for schema module containing the path interpolator.
 */
package object schema {

  /**
   * Path interpolator extension for StringContext.
   * 
   * Provides the p"..." syntax for compile-time path parsing.
   * Only literal strings are allowed - interpolated arguments are rejected.
   * 
   * Example usage:
   *   p"foo.bar[0]{key}<Case>"
   */
  implicit class PathInterpolator(private val sc: StringContext) extends AnyVal {
    def p(args: Any*): DynamicOptic = macro PathMacros.pImpl
  }
}
