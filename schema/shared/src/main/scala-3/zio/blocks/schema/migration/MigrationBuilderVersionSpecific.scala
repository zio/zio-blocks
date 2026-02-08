package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import scala.annotation.tailrec

trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  /**
   * Rename a field using a selector. Example:
   * `builder.renameField(_.name, "fullName")`
   */
  inline def renameField(inline from: A => Any, toName: String): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('{ self }, 'from, 'toName) }

  /**
   * Drop a field using a selector. Example:
   * `builder.dropField(_.email, reverseDefault)`
   */
  inline def dropField(inline from: A => Any, reverseDefault: MigrationExpr): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropFieldImpl[A, B]('{ self }, 'from, 'reverseDefault) }

  /**
   * Transform a field using a selector. Example:
   * `builder.transformField(_.age, expr, reverseExpr)`
   */
  inline def transformField(
    inline field: A => Any,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformFieldImpl[A, B]('{ self }, 'field, 'expr, 'reverseExpr) }

  /** Make an optional field required using a selector. */
  inline def mandateField(inline field: A => Any, default: MigrationExpr): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.mandateFieldImpl[A, B]('{ self }, 'field, 'default) }

  /** Make a required field optional using a selector. */
  inline def optionalizeField(
    inline field: A => Any,
    defaultForNone: MigrationExpr
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B]('{ self }, 'field, 'defaultForNone) }

  /** Change the type of a field using a selector. */
  inline def changeFieldType(
    inline field: A => Any,
    coercion: MigrationExpr,
    reverseCoercion: MigrationExpr
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.changeFieldTypeImpl[A, B]('{ self }, 'field, 'coercion, 'reverseCoercion) }
}

private object MigrationBuilderMacros {
  import scala.quoted._

  private def extractFieldInfo(selector: Expr[?])(using Quotes): List[String] = {
    import quotes.reflect._

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def loop(term: Term, acc: List[String]): List[String] = term match {
      case Select(qualifier, name) => loop(qualifier, name :: acc)
      case _: Ident                => acc
      case _                       =>
        report.errorAndAbort(
          s"Expected a simple field selector like _.fieldName, got '${term.show}'"
        )
    }

    val fieldNames = loop(toPathBody(selector.asTerm), Nil)
    if (fieldNames.isEmpty) {
      report.errorAndAbort("Selector must reference at least one field")
    }
    fieldNames
  }

  private def buildPathAndField(fieldNames: List[String])(using Quotes): (Expr[DynamicOptic], Expr[String]) =
    if (fieldNames.size == 1) {
      ('{ DynamicOptic.root }, Expr(fieldNames.head))
    } else {
      val pathExpr = fieldNames.init.foldLeft('{ DynamicOptic.root }: Expr[DynamicOptic]) { (acc, name) =>
        '{ $acc.field(${ Expr(name) }) }
      }
      (pathExpr, Expr(fieldNames.last))
    }

  def renameFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    toName: Expr[String]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(from))
    '{ $self.renameFieldAt($path, $fieldName, $toName) }
  }

  def dropFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    reverseDefault: Expr[MigrationExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(from))
    '{ $self.dropFieldAt($path, $fieldName, $reverseDefault) }
  }

  def transformFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    expr: Expr[MigrationExpr],
    reverseExpr: Expr[MigrationExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(field))
    '{ $self.transformFieldAt($path, $fieldName, $expr, $reverseExpr) }
  }

  def mandateFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    default: Expr[MigrationExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(field))
    '{ $self.mandateFieldAt($path, $fieldName, $default) }
  }

  def optionalizeFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    defaultForNone: Expr[MigrationExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(field))
    '{ $self.optionalizeFieldAt($path, $fieldName, $defaultForNone) }
  }

  def changeFieldTypeImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    coercion: Expr[MigrationExpr],
    reverseCoercion: Expr[MigrationExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val (path, fieldName) = buildPathAndField(extractFieldInfo(field))
    '{ $self.changeFieldTypeAt($path, $fieldName, $coercion, $reverseCoercion) }
  }
}
