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
    transparent inline def apply[F](inline selector: S => F): FieldSelector[S, F, ?] =
      ${ selectImpl[S, F]('selector) }
  }

  /**
   * Implementation of the select macro.
   *
   * Extracts the field name from the selector lambda and creates a
   * FieldSelector with the name as a singleton string type.
   *
   * Supports nested field access like _.address.street, building the full
   * DynamicOptic path.
   */
  def selectImpl[S: Type, F: Type](selector: Expr[S => F])(using Quotes): Expr[FieldSelector[S, F, ?]] = {
    import quotes.reflect.*

    // Extract full field path and return (leafName, fullPath)
    def extractFieldPath(term: Term): (String, List[String]) = {
      // Recursively extract the path from Select chains
      def extractPath(t: Term): List[String] = t match {
        // Nested select: inner.fieldName
        case Select(inner @ Select(_, _), name) =>
          extractPath(inner) :+ name
        // Base select on identifier: _.fieldName
        case Select(Ident(_), name) =>
          List(name)
        // Apply (method call): expr.fieldName()
        case Apply(Select(inner @ Select(_, _), name), _) =>
          extractPath(inner) :+ name
        case Apply(Select(Ident(_), name), _) =>
          List(name)
        // Single field select
        case Select(_, name) =>
          List(name)
        // Method call on single field
        case Apply(Select(_, name), _) =>
          List(name)
        case other =>
          report.errorAndAbort(
            s"select() requires a field access like _.field or _.a.b.c, got: ${other.show}"
          )
      }

      // Navigate through wrappers to find the actual selector
      def navigate(t: Term): (String, List[String]) = t match {
        // Pattern: Inlined(_, _, Block(List(DefDef(_, _, _, Some(body))), _))
        case Inlined(_, _, Block(List(DefDef(_, _, _, Some(body))), _)) =>
          val path = extractPath(body)
          (path.last, path)
        // Without Inlined wrapper
        case Block(List(DefDef(_, _, _, Some(body))), _) =>
          val path = extractPath(body)
          (path.last, path)
        // Lambda patterns
        case Lambda(_, body) =>
          val path = extractPath(body)
          (path.last, path)
        // Recurse through Inlined
        case Inlined(_, _, inner) =>
          navigate(inner)
        case other =>
          report.errorAndAbort(
            s"select() requires a simple field access like _.fieldName, got: ${other.show}"
          )
      }

      navigate(term)
    }

    val (fieldName, path) = extractFieldPath(selector.asTerm)

    // Create singleton type for the field name
    val nameType = ConstantType(StringConstant(fieldName))

    // Build the optic expression from the path
    val pathExpr = Expr(path)

    nameType.asType match {
      case '[n] =>
        '{
          new FieldSelector[S, F, n & String](
            ${ Expr(fieldName) }.asInstanceOf[n & String],
            ${ pathExpr }.foldLeft(DynamicOptic.root)((optic, field) => optic.field(field))
          )
        }
    }
  }
}
