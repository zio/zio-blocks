package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 3 macros for MigrationBuilder to extract field names and paths from
 * lambda expressions at compile time.
 *
 * This enables type-safe, IDE-friendly migration building:
 * builder.addField(_.country, "USA") builder.renameField(_.name, _.fullName)
 * builder.dropField(_.oldField)
 */
object MigrationBuilderMacros {

  /**
   * Extract field name from a selector lambda like _.fieldName Returns the
   * field name as a string.
   */
  def extractFieldName[A: Type, F: Type](selector: Expr[A => F])(using Quotes): Expr[String] = {
    import quotes.reflect._

    def extractFromTerm(term: Term): String = term match {
      case Inlined(_, _, body)                         => extractFromTerm(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractFromTerm(body)
      case Lambda(_, body)                             => extractFromTerm(body)
      case Select(_, fieldName)                        => fieldName
      case Ident(name)                                 => name
      case _                                           =>
        report.errorAndAbort(s"Expected a field selector like _.fieldName, got: ${term.show}")
    }

    val fieldName = extractFromTerm(selector.asTerm)
    Expr(fieldName)
  }

  /**
   * Extract a path from a nested selector like _.address.street Returns a
   * DynamicOptic representing the path.
   */
  def extractPath[A: Type, F: Type](selector: Expr[A => F])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    def extractFields(term: Term): List[String] = term match {
      case Inlined(_, _, body)                         => extractFields(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractFields(body)
      case Lambda(_, body)                             => extractFields(body)
      case Select(qualifier, fieldName)                => extractFields(qualifier) :+ fieldName
      case Ident(_)                                    => Nil // Root parameter
      case _                                           =>
        report.errorAndAbort(s"Expected a field selector like _.address.street, got: ${term.show}")
    }

    val fields = extractFields(selector.asTerm)

    if (fields.isEmpty) {
      '{ DynamicOptic.root }
    } else {
      // Build path: root / "field1" / "field2" / ...
      val fieldExprs = fields.map(f => Expr(f))
      fieldExprs.foldLeft('{ DynamicOptic.root }) { (acc, fieldExpr) =>
        '{ $acc / $fieldExpr }
      }
    }
  }

  /**
   * Extract two field names from two selectors for rename operations. Returns a
   * tuple of (fromField, toField).
   */
  def extractTwoFieldNames[A: Type, B: Type, F1: Type, F2: Type](
    from: Expr[A => F1],
    to: Expr[B => F2]
  )(using Quotes): Expr[(String, String)] = {
    val fromName = extractFieldName(from)
    val toName   = extractFieldName(to)
    '{ ($fromName, $toName) }
  }

  /**
   * Validate that a selector points to a valid field in the schema. This is a
   * compile-time check to ensure type safety.
   */
  def validateFieldExists[A: Type](
    selector: Expr[A => Any],
    schema: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect._

    // For now, we just extract the field name
    // In a full implementation, we'd inspect the schema at compile time
    val _ = extractFieldName(selector)
    val _ = schema

    // TODO: Add runtime validation or compile-time schema inspection
    '{ () }
  }
}
