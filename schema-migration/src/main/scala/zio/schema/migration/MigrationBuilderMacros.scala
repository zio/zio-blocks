package zio.schema.migration

import scala.quoted._
import zio.schema.Schema

/**
 * Macro implementations for MigrationBuilder.
 *
 * These macros extract field paths from lambda expressions and delegate to the
 * string-based methods, avoiding nested inline complexity.
 */
object MigrationBuilderMacros {

  /**
   * Macro implementation for addFieldMacro
   */
  def addFieldImpl[A: Type, B: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[B => T],
    defaultValue: Expr[T]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect._

    val fieldPath  = PathMacros.extractPathImpl[B](selector)
    val pathString = extractPathString(fieldPath)

    // We need to get the Schema[T] from implicit scope at the call site
    Expr.summon[Schema[T]] match {
      case Some(schemaExpr) =>
        '{ $builder.addField[T]($pathString, $defaultValue)(using $schemaExpr) }
      case None =>
        report.errorAndAbort(s"No Schema found for type ${Type.show[T]}")
    }
  }

  /**
   * Macro implementation for dropFieldMacro
   */
  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect._

    val fieldPath  = PathMacros.extractPathImpl[A](selector)
    val pathString = extractPathString(fieldPath)

    '{ $builder.dropField($pathString) }
  }

  /**
   * Macro implementation for renameFieldMacro
   */
  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    oldSelector: Expr[A => Any],
    newSelector: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect._

    val oldPath       = PathMacros.extractPathImpl[A](oldSelector)
    val newPath       = PathMacros.extractPathImpl[B](newSelector)
    val oldPathString = extractPathString(oldPath)
    val newPathString = extractPathString(newPath)

    '{ $builder.renameField($oldPathString, $newPathString) }
  }

  /**
   * Macro implementation for transformFieldMacro
   */
  def transformFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transformation: Expr[SerializableTransformation]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect._

    val fieldPath  = PathMacros.extractPathImpl[A](selector)
    val pathString = extractPathString(fieldPath)

    '{ $builder.transformField($pathString, $transformation) }
  }

  /**
   * Helper to extract string representation from FieldPath expression
   */
  private def extractPathString(fieldPath: Expr[FieldPath])(using Quotes): Expr[String] = {
    import quotes.reflect._

    fieldPath.asTerm match {
      // FieldPath.Root(name)
      case Apply(Select(_, "Root"), List(Literal(StringConstant(name)))) =>
        Expr(name)

      // More complex nested paths - fallback to serialize
      case _ =>
        '{ $fieldPath.serialize }
    }
  }
}
