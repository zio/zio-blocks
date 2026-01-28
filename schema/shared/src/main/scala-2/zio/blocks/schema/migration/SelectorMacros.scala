package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Macros for creating field selectors with compile-time field name extraction.
 *
 * The `select` macro is the ONLY inline part of the migration API. It extracts
 * the field name from a lambda expression like `_.fieldName` and captures it as
 * a singleton string type in the resulting FieldSelector.
 */
object SelectorMacros {

  /**
   * Implementation of the select macro for Scala 2.
   *
   * Extracts the field name from the selector lambda and creates a
   * FieldSelector with the name as a singleton literal type.
   */
  def selectImpl[S: c.WeakTypeTag, F: c.WeakTypeTag](c: whitebox.Context)(
    selector: c.Expr[S => F]
  ): c.Expr[FieldSelector[S, F, _]] = {
    import c.universe._

    // Extract field name from the selector
    val fieldName: String = selector.tree match {
      // Pattern: Function(List(param), Select(Ident(param), fieldName))
      // This matches: _.fieldName
      case Function(_, Select(_, name)) =>
        name.decodedName.toString

      // Pattern: Function(List(param), Apply(Select(Ident(param), fieldName), List()))
      // This matches: _.fieldName() (method call)
      case Function(_, Apply(Select(_, name), _)) =>
        name.decodedName.toString

      // Handle case where the function is wrapped
      case Block(_, Function(_, Select(_, name))) =>
        name.decodedName.toString

      case Block(_, Function(_, Apply(Select(_, name), _))) =>
        name.decodedName.toString

      case other =>
        c.abort(
          c.enclosingPosition,
          s"select() requires a simple field access like _.fieldName, got: ${showRaw(other)}"
        )
    }

    // Create singleton literal type for field name
    val nameLiteral = c.internal.constantType(Constant(fieldName))

    c.Expr[FieldSelector[S, F, _]](q"""
      new _root_.zio.blocks.schema.migration.FieldSelector[${weakTypeOf[S]}, ${weakTypeOf[F]}, $nameLiteral](
        $fieldName.asInstanceOf[$nameLiteral],
        _root_.zio.blocks.schema.DynamicOptic.root.field($fieldName)
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
