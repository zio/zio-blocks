package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * String interpolators for JSON paths and literals (Scala 2).
 */
object JsonInterpolators {

  /**
   * Path interpolator for creating [[DynamicOptic]] instances.
   *
   * {{{
   * val path = p"users[0].name"
   * }}}
   */
  implicit class PathInterpolator(val sc: StringContext) extends AnyVal {
    def p(args: Any*): DynamicOptic = macro PathInterpolatorMacro.p_impl
  }

  /**
   * JSON literal interpolator for creating [[Json]] instances.
   *
   * {{{
   * val json = j"""{"name": "Alice", "age": 30}"""
   * }}}
   */
  implicit class JsonInterpolator(val sc: StringContext) extends AnyVal {
    def j(args: Any*): Json = macro JsonInterpolatorMacro.j_impl
  }
}

/**
 * Macro implementations for path interpolator.
 */
object PathInterpolatorMacro {
  def p_impl(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._

    val Apply(_, List(Apply(_, rawParts))) = c.prefix.tree
    val parts                              = rawParts.map { case Literal(Constant(s: String)) => s }

    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "Path interpolator does not support interpolated variables")
    }

    if (parts.length != 1) {
      c.abort(c.enclosingPosition, "Path interpolator requires exactly one string part")
    }

    val pathString = parts.head

    c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic.parse($pathString)")
  }
}

/**
 * Macro implementations for JSON literal interpolator.
 */
object JsonInterpolatorMacro {
  def j_impl(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val Apply(_, List(Apply(_, rawParts))) = c.prefix.tree
    val parts                              = rawParts.map { case Literal(Constant(s: String)) => s }

    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "JSON interpolator does not support interpolated variables yet")
    }

    if (parts.length != 1) {
      c.abort(c.enclosingPosition, "JSON interpolator requires exactly one string part")
    }

    val jsonString = parts.head

    c.Expr[Json](q"_root_.zio.blocks.schema.json.Json.decode($jsonString).fold(throw _, identity)")
  }
}
