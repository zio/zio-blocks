package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import scala.quoted.*

/**
 * String interpolators for JSON paths and literals (Scala 3).
 */
object JsonInterpolators {

  /**
   * Path interpolator for creating [[DynamicOptic]] instances.
   *
   * {{{
   * val path = p"users[0].name"
   * }}}
   */
  extension (inline sc: StringContext) {
    inline def p(inline args: Any*): DynamicOptic = ${ PathInterpolatorMacro.p_impl('sc, 'args) }
  }

  /**
   * JSON literal interpolator for creating [[Json]] instances.
   *
   * {{{
   * val json = j"""{"name": "Alice", "age": 30}"""
   * }}}
   */
  extension (inline sc: StringContext) {
    inline def j(inline args: Any*): Json = ${ JsonInterpolatorMacro.j_impl('sc, 'args) }
  }
}

/**
 * Macro implementations for path interpolator.
 */
object PathInterpolatorMacro {
  def p_impl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // Extract the string parts
    val parts = sc match {
      case '{ StringContext(${ Varargs(Exprs(parts)) }: _*) } => parts
      case _                                                  => report.errorAndAbort("Invalid StringContext")
    }

    // Check for interpolation
    val argsList = args match {
      case Varargs(a) => a
      case _          => Seq.empty
    }

    if (argsList.nonEmpty) {
      report.errorAndAbort("Path interpolator does not support interpolated variables")
    }

    if (parts.length != 1) {
      report.errorAndAbort("Path interpolator requires exactly one string part")
    }

    val pathString = parts.head

    // Parse the path string and build a DynamicOptic
    '{ DynamicOptic.parse(${ Expr(pathString) }) }
  }
}

/**
 * Macro implementations for JSON literal interpolator.
 */
object JsonInterpolatorMacro {
  def j_impl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect.*

    // Extract the string parts
    val parts = sc match {
      case '{ StringContext(${ Varargs(Exprs(parts)) }: _*) } => parts
      case _                                                  => report.errorAndAbort("Invalid StringContext")
    }

    // Check for interpolation
    val argsList = args match {
      case Varargs(a) => a
      case _          => Seq.empty
    }

    if (argsList.nonEmpty) {
      report.errorAndAbort("JSON interpolator does not support interpolated variables yet")
    }

    if (parts.length != 1) {
      report.errorAndAbort("JSON interpolator requires exactly one string part")
    }

    val jsonString = parts.head

    // Parse the JSON string at compile time
    '{ Json.decode(${ Expr(jsonString) }).fold(throw _, identity) }
  }
}
