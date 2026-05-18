package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Macros for creating field selectors with compile-time field name extraction.
 *
 * The `select` macro is the ONLY inline part of the migration API. It extracts
 * the field name from a lambda expression like `_.fieldName` and captures it as
 * a singleton string type in the resulting FieldSelector.
 *
 * Supports nested field access like _.address.street, building the full
 * DynamicOptic path.
 */
object SelectorMacros {

  /**
   * Implementation of the select macro for Scala 2.
   *
   * Extracts the field name from the selector lambda and creates a
   * FieldSelector with the name as a singleton literal type.
   *
   * Supports nested field access like _.address.street, building the full
   * DynamicOptic path.
   */
  def selectImpl[S: c.WeakTypeTag, F: c.WeakTypeTag](c: whitebox.Context)(
    selector: c.Expr[S => F]
  ): c.Expr[FieldSelector[S, F, _]] = {
    import c.universe._

    // Recursively extract the full path from Select chains
    def extractPath(tree: Tree): List[String] = tree match {
      // Nested select: inner.fieldName
      case Select(inner @ Select(_, _), name) =>
        extractPath(inner) :+ name.decodedName.toString
      // Base select on identifier: _.fieldName or param.fieldName
      case Select(Ident(_), name) =>
        List(name.decodedName.toString)
      // Apply (method call): expr.fieldName()
      case Apply(Select(inner @ Select(_, _), name), _) =>
        extractPath(inner) :+ name.decodedName.toString
      case Apply(Select(Ident(_), name), _) =>
        List(name.decodedName.toString)
      // Single field select (fallback)
      case Select(_, name) =>
        List(name.decodedName.toString)
      // Method call on single field
      case Apply(Select(_, name), _) =>
        List(name.decodedName.toString)
      case other =>
        c.abort(
          c.enclosingPosition,
          s"select() requires a field access like _.field or _.a.b.c, got: ${showRaw(other)}"
        )
    }

    // Extract path from the selector
    val path: List[String] = selector.tree match {
      // Pattern: Function(List(param), body)
      case Function(_, body) =>
        extractPath(body)
      // Handle case where the function is wrapped in a Block
      case Block(_, Function(_, body)) =>
        extractPath(body)
      case other =>
        c.abort(
          c.enclosingPosition,
          s"select() requires a simple field access like _.fieldName, got: ${showRaw(other)}"
        )
    }

    val fieldName = path.last

    // Create singleton literal type for field name
    val nameLiteral = c.internal.constantType(Constant(fieldName))

    // Build the path list for runtime optic construction
    val pathLiteral = path.map(f => q"$f")

    c.Expr[FieldSelector[S, F, _]](q"""
      new _root_.zio.blocks.schema.migration.FieldSelector[${weakTypeOf[S]}, ${weakTypeOf[F]}, $nameLiteral](
        $fieldName.asInstanceOf[$nameLiteral],
        _root_.scala.List(..$pathLiteral).foldLeft(_root_.zio.blocks.schema.DynamicOptic.root)((optic, field) => optic.field(field))
      )
    """)
  }
}

/**
 * Builder class for ergonomic select syntax in Scala 2.
 *
 * Usage:
 * {{{
 * val selector = select[Person](_.name)
 * // Type: FieldSelector[Person, String, "name"]
 * }}}
 */
class SelectBuilder[S](private val dummy: Boolean = true) extends AnyVal {

  /**
   * Apply the selector to extract a field.
   *
   * @param selector
   *   A lambda like `_.fieldName` that accesses a field
   * @tparam F
   *   The field type
   * @return
   *   A FieldSelector with the field name captured as a type parameter
   */
  def apply[F](selector: S => F): FieldSelector[S, F, _] = macro SelectorMacros.selectImpl[S, F]
}
