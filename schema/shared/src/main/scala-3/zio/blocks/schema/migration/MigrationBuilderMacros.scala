package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}
import scala.quoted.*

/**
 * Scala 3 macros for converting selector lambdas to DynamicOptic instances, and
 * extension methods that provide the selector-based MigrationBuilder API.
 *
 * Selectors like `_.firstName` or `_.address.street` are parsed at compile time
 * into `DynamicOptic.root.field("firstName")` or
 * `DynamicOptic.root.field("address").field("street")`.
 */
object MigrationBuilderMacros {

  /**
   * Extracts a DynamicOptic from a selector lambda at compile time.
   *
   * Supported selector forms:
   *   - `_.field` → `DynamicOptic.root.field("field")`
   *   - `_.field.subfield` →
   *     `DynamicOptic.root.field("field").field("subfield")`
   */
  def selectorToOpticImpl[A: Type, B: Type](f: Expr[A => B])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def extractNodes(term: quotes.reflect.Term): List[String] =
      term match {
        case Select(qualifier, name) =>
          extractNodes(qualifier) :+ name
        case Ident(_) =>
          // Reached the lambda parameter — done
          Nil
        case Inlined(_, _, inner) =>
          extractNodes(inner)
        case Block(Nil, expr) =>
          extractNodes(expr)
        case Typed(inner, _) =>
          extractNodes(inner)
        case _ =>
          report.errorAndAbort(
            s"Unsupported selector expression. Expected simple field access like _.field or _.field.subfield, " +
              s"got: ${term.show} (tree: ${term.getClass.getSimpleName})",
            term.pos
          )
      }

    // Unwrap layers of Inlined, Block, and Typed to find the lambda body
    def unwrapToLambda(term: quotes.reflect.Term): quotes.reflect.Term = term match {
      case Inlined(_, _, inner)                       => unwrapToLambda(inner)
      case Block(Nil, inner)                          => unwrapToLambda(inner)
      case Typed(inner, _)                            => unwrapToLambda(inner)
      case Block(List(DefDef(_, _, _, Some(rhs))), _) => rhs
      case Lambda(_, body)                            => body
      case other                                      => other
    }

    val body = unwrapToLambda(f.asTerm)

    val fieldNames = extractNodes(body)
    if (fieldNames.isEmpty) {
      report.errorAndAbort(
        "Selector must access at least one field, e.g. _.firstName",
        f.asTerm.pos
      )
    }

    // Build the DynamicOptic expression by chaining .field() calls
    fieldNames.foldLeft('{ DynamicOptic.root }) { (optic, name) =>
      val nameExpr = Expr(name)
      '{ $optic.field($nameExpr) }
    }
  }

  inline def selectorToOptic[A, B](inline f: A => B): DynamicOptic =
    ${ selectorToOpticImpl[A, B]('f) }
}

/**
 * Extension methods for MigrationBuilder that accept selector lambdas instead
 * of explicit DynamicOptic paths.
 *
 * These compile-time macros convert `_.field` selectors to `DynamicOptic` and
 * delegate to the corresponding `*At` methods.
 */
extension [A, B](builder: MigrationBuilder[A, B]) {

  /** Adds a new field using a selector on the target type. */
  inline def addField[T](inline selector: B => T)(default: T)(using s: Schema[T]): MigrationBuilder[A, B] =
    builder.addFieldAt[T](MigrationBuilderMacros.selectorToOptic[B, T](selector), default)

  /** Drops a field using a selector on the source type. Lossy. */
  inline def dropField(inline selector: A => Any): MigrationBuilder[A, B] =
    builder.dropFieldAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector))

  /** Drops a field with a reverse default (lossless). */
  inline def dropField[T](inline selector: A => T, reverseDefault: T)(using s: Schema[T]): MigrationBuilder[A, B] =
    builder.dropFieldAt[T](MigrationBuilderMacros.selectorToOptic[A, T](selector), reverseDefault)

  /** Renames a field. */
  inline def renameField(inline from: A => Any, toName: String): MigrationBuilder[A, B] =
    builder.renameFieldAt(MigrationBuilderMacros.selectorToOptic[A, Any](from), toName)

  /** Transforms the value at a field. Lossy (no inverse). */
  inline def transformValue(inline selector: A => Any, transform: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    builder.transformValueAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform)

  /** Transforms the value at a field with an inverse (lossless). */
  inline def transformValue(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformValueAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform, inverse)

  /** Converts an optional field to mandatory. */
  inline def mandate[T](inline selector: A => Option[T])(default: T)(using s: Schema[T]): MigrationBuilder[A, B] =
    builder.mandateAt[T](MigrationBuilderMacros.selectorToOptic[A, Option[T]](selector), default)

  /** Wraps a mandatory field in Option. */
  inline def optionalize(inline selector: A => Any): MigrationBuilder[A, B] =
    builder.optionalizeAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector))

  /** Changes the type at a field. Lossy (no inverse). */
  inline def changeType(inline selector: A => Any, converter: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    builder.changeTypeAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), converter)

  /** Changes the type at a field with inverse (lossless). */
  inline def changeType(
    inline selector: A => Any,
    converter: SchemaExpr[Any, Any],
    inverseConverter: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.changeTypeAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), converter, inverseConverter)

  /** Renames a case in a variant. */
  inline def renameCase(inline selector: A => Any, from: String, to: String): MigrationBuilder[A, B] =
    builder.renameCaseAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), from, to)

  /** Transforms elements of a sequence. Lossy. */
  inline def transformElements(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformElementsAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform)

  /** Transforms elements of a sequence with inverse. */
  inline def transformElements(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformElementsAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform, inverse)

  /** Transforms map keys. Lossy. */
  inline def transformKeys(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformKeysAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform)

  /** Transforms map keys with inverse. */
  inline def transformKeys(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformKeysAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform, inverse)

  /** Transforms map values. Lossy. */
  inline def transformValues(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformValuesAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform)

  /** Transforms map values with inverse. */
  inline def transformValues(
    inline selector: A => Any,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    builder.transformValuesAt(MigrationBuilderMacros.selectorToOptic[A, Any](selector), transform, inverse)
}
