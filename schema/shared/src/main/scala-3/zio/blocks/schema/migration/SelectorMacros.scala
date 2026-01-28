package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.DynamicOptic

/**
 * Macros for creating field selectors with compile-time field name extraction.
 *
 * The `select` macro is the ONLY inline part of the migration API. It extracts
 * the field name from a lambda expression like `_.fieldName` and captures it as
 * a singleton string type in the resulting FieldSelector.
 *
 * This enables type-level tracking of which fields have been handled during
 * migration construction, while keeping all builder methods non-inline.
 */
object SelectorMacros {

  /**
   * Create a field selector from a lambda expression.
   *
   * Example:
   * {{{
   * val selector = select[Person](_.name)
   * // Type: FieldSelector[Person, String, "name"]
   * }}}
   *
   * @tparam S
   *   The schema/record type to select from
   * @return
   *   A SelectBuilder that can be applied to a field accessor lambda
   */
  inline def select[S]: SelectBuilder[S] = new SelectBuilder[S]

  /**
   * Builder class that captures the schema type and provides the apply method.
   */
  class SelectBuilder[S] {

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
    inline def apply[F](inline selector: S => F): FieldSelector[S, F, ?] =
      ${ selectImpl[S, F]('selector) }
  }

  /**
   * Implementation of the select macro.
   *
   * Extracts the field name from the selector lambda and creates a
   * FieldSelector with the name as a singleton string type.
   */
  def selectImpl[S: Type, F: Type](selector: Expr[S => F])(using Quotes): Expr[FieldSelector[S, F, ?]] = {
    import quotes.reflect.*

    // Extract field name - defined as nested function so quotes is in scope
    def extractFieldName(term: Term): String = term match {
      // Pattern: Inlined(_, _, Block(List(DefDef(_, _, _, Some(Select(_, name)))), _))
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Select(_, name)))), _)) =>
        name
      // Pattern with method call: _.fieldName()
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Apply(Select(_, name), _)))), _)) =>
        name
      // Without Inlined wrapper
      case Block(List(DefDef(_, _, _, Some(Select(_, name)))), _) =>
        name
      case Block(List(DefDef(_, _, _, Some(Apply(Select(_, name), _)))), _) =>
        name
      // Lambda patterns
      case Lambda(_, Select(_, name)) =>
        name
      case Lambda(_, Apply(Select(_, name), _)) =>
        name
      // Recurse through Inlined
      case Inlined(_, _, inner) =>
        extractFieldName(inner)
      case other =>
        report.errorAndAbort(
          s"select() requires a simple field access like _.fieldName, got: ${other.show}"
        )
    }

    val fieldName = extractFieldName(selector.asTerm)

    // Create singleton type for the field name
    val nameType = ConstantType(StringConstant(fieldName))

    nameType.asType match {
      case '[n] =>
        '{
          new FieldSelector[S, F, n & String](
            ${ Expr(fieldName) }.asInstanceOf[n & String],
            DynamicOptic.root.field(${ Expr(fieldName) })
          )
        }
    }
  }
}
