package zio.schema.migration

import scala.quoted._
import zio.schema.Schema

/**
 * Macro-based field selectors for type-safe migrations.
 *
 * This uses a different pattern to avoid nested inline issues.
 */
object MacroSelectors {

  /**
   * Extract field name from a selector function.
   *
   * Usage: fieldName((p: Person) => p.name) returns "name"
   *
   * Note: Requires compiler plugin. Plugin transforms to fieldNameWithString.
   */
  inline def fieldName[A](inline selector: A => Any): String =
    ${ fieldNameImpl[A]('selector) }

  /**
   * Plugin-facing version with extracted path string. Users should not call
   * this directly.
   */
  inline def fieldNameWithString[A](inline selector: A => Any, pathString: String): String =
    pathString

  def fieldNameImpl[A: Type](selector: Expr[A => Any])(using Quotes): Expr[String] = {
    import quotes.reflect._

    // Use HOAS pattern matching like PathMacros
    selector match {
      case '{ (x: A) => ($f(x): Any) } =>
        val fieldPath = PathMacros.extractFromBody(f.asTerm)
        '{ $fieldPath.serialize }

      case _ =>
        report.errorAndAbort(s"Could not extract field name from selector: ${selector.show}")
    }
  }

  /**
   * Type-safe field selector that returns FieldPath.
   *
   * Usage: field((p: Person) => p.name) returns FieldPath.Root("name")
   *
   * Note: Requires compiler plugin. Plugin transforms to fieldWithString.
   */
  inline def field[A](inline selector: A => Any): FieldPath =
    ${ fieldImpl[A]('selector) }

  /**
   * Plugin-facing version with extracted path string. Users should not call
   * this directly.
   */
  inline def fieldWithString[A](inline selector: A => Any, pathString: String): FieldPath =
    FieldPath
      .parse(pathString)
      .getOrElse(
        throw new IllegalArgumentException(s"Invalid field path: $pathString")
      )

  def fieldImpl[A: Type](selector: Expr[A => Any])(using Quotes): Expr[FieldPath] = {
    import quotes.reflect._

    // Use HOAS pattern matching
    selector match {
      case '{ (x: A) => ($f(x): Any) } =>
        PathMacros.extractFromBody(f.asTerm)

      case _ =>
        report.errorAndAbort(s"Could not extract field path from selector: ${selector.show}")
    }
  }
}

/**
 * Helper methods for using macro selectors with MigrationBuilder.
 *
 * These avoid nested inline by using MacroSelectors in a separate step.
 *
 * Usage: import MacroSelectors._
 *
 * val migration = MigrationBuilder[V1, V2] .addField(fieldName((v: V2) =>
 * v.age), 0) .renameField( fieldName((v: V1) => v.firstName), fieldName((v: V2) =>
 * v.fullName) )
 */
object MacroSelectorHelpers {
  // No extension methods needed - users can call fieldName() or field()
  // directly and pass the result to the existing string-based methods
}
