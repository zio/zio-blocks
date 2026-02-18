package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted._
import zio.blocks.schema._

private[migration] object MigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = selectorToOptic[B](target)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.AddField($path, $default),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def dropFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    defaultForReverse: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = selectorToOptic[A](source)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.DropField($path, $defaultForReverse),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def renameFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromPath = selectorToOptic[A](from)
    val toName   = lastFieldName[B](to)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.Rename($fromPath, $toName),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def transformFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any],
    transform: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromPath = selectorToOptic[A](from)
    val toName   = lastFieldName[B](to)
    val _        = transform // reserved for future SchemaExpr evaluation
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.Rename($fromPath, $toName),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def mandateFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = selectorToOptic[A](source)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.Mandate($path, $default),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def optionalizeFieldImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = selectorToOptic[A](source)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.Optionalize($path),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  def changeFieldTypeImpl[A: Type, B: Type](
    self: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    newDefault: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = selectorToOptic[A](source)
    '{
      new MigrationBuilder(
        $self.actions :+ MigrationAction.ChangeType($path, $newDefault),
        $self.sourceSchema,
        $self.targetSchema
      )
    }
  }

  // ── Selector extraction ────────────────────────────────────────────

  private def selectorToOptic[S: Type](
    selector: Expr[S => Any]
  )(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect._

    val body       = extractLambdaBody(selector.asTerm)
    val fieldNames = collectFieldNames(body)

    if (fieldNames.isEmpty)
      report.errorAndAbort("Migration selector must access at least one field (e.g. _.field or _.field.nested)")

    fieldNames.foldLeft('{ DynamicOptic.root }) { (acc, name) =>
      val nameExpr = Expr(name)
      '{ $acc.field($nameExpr) }
    }
  }

  private def lastFieldName[S: Type](
    selector: Expr[S => Any]
  )(using q: Quotes): Expr[String] = {
    import q.reflect._

    val body       = extractLambdaBody(selector.asTerm)
    val fieldNames = collectFieldNames(body)

    if (fieldNames.isEmpty)
      report.errorAndAbort("Migration selector must access at least one field")

    Expr(fieldNames.last)
  }

  @tailrec
  private def extractLambdaBody(using q: Quotes)(term: q.reflect.Term): q.reflect.Term = {
    import q.reflect._
    term match {
      case Inlined(_, _, inner)                        => extractLambdaBody(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _) => body
      case _                                           =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }
  }

  private def collectFieldNames(using q: Quotes)(term: q.reflect.Term): List[String] = {
    import q.reflect._

    def loop(t: Term): List[String] = t match {
      case Select(parent, fieldName) => loop(parent) :+ fieldName
      case _: Ident                  => Nil
      case Typed(inner, _)           => loop(inner)
      case _                         =>
        report.errorAndAbort(
          s"Migration selectors only support field access (e.g. _.field or _.a.b.c), got '${t.show}'"
        )
    }

    loop(term)
  }
}
