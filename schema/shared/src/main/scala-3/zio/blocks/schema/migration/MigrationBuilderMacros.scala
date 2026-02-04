package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema.SchemaExpr

object MigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, SH, ?]] = {
    import q.reflect.*

    val fieldName     = extractFieldNameFromTerm(target.asTerm)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val targetPath = SelectorMacros.toPath[B, Any]($target)
          new MigrationBuilder[A, B, SH, Tuple.Concat[TP, fn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.AddField(targetPath, $default)
          )
        }
    }
  }

  def dropFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect.*

    val fieldName     = extractFieldNameFromTerm(source.asTerm)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.DropField(sourcePath, $defaultForReverse)
          )
        }
    }
  }

  def renameFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val fromFieldName     = extractFieldNameFromTerm(from.asTerm)
    val toFieldName       = extractFieldNameFromTerm(to.asTerm)
    val fromFieldNameType = ConstantType(StringConstant(fromFieldName))
    val toFieldNameType   = ConstantType(StringConstant(toFieldName))
    val toNameExpr        = Expr(toFieldName)

    (fromFieldNameType.asType, toFieldNameType.asType) match {
      case ('[fromFn], '[toFn]) =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fromFn *: EmptyTuple], Tuple.Concat[TP, toFn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Rename(fromPath, $toNameExpr)
          )
        }
    }
  }

  def transformFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    from: Expr[A => Any],
    to: Expr[B => Any],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val fromFieldName     = extractFieldNameFromTerm(from.asTerm)
    val toFieldName       = extractFieldNameFromTerm(to.asTerm)
    val fromFieldNameType = ConstantType(StringConstant(fromFieldName))
    val toFieldNameType   = ConstantType(StringConstant(toFieldName))

    (fromFieldNameType.asType, toFieldNameType.asType) match {
      case ('[fromFn], '[toFn]) =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fromFn *: EmptyTuple], Tuple.Concat[TP, toFn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformValue(fromPath, $transform)
          )
        }
    }
  }

  def mandateFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Option[?]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val sourceFieldName     = extractFieldNameFromTerm(source.asTerm)
    val targetFieldName     = extractFieldNameFromTerm(target.asTerm)
    val sourceFieldNameType = ConstantType(StringConstant(sourceFieldName))
    val targetFieldNameType = ConstantType(StringConstant(targetFieldName))

    (sourceFieldNameType.asType, targetFieldNameType.asType) match {
      case ('[srcFn], '[tgtFn]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Option[?]]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, srcFn *: EmptyTuple], Tuple.Concat[TP, tgtFn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Mandate(sourcePath, $default)
          )
        }
    }
  }

  def optionalizeFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    target: Expr[B => Option[?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val sourceFieldName     = extractFieldNameFromTerm(source.asTerm)
    val targetFieldName     = extractFieldNameFromTerm(target.asTerm)
    val sourceFieldNameType = ConstantType(StringConstant(sourceFieldName))
    val targetFieldNameType = ConstantType(StringConstant(targetFieldName))

    (sourceFieldNameType.asType, targetFieldNameType.asType) match {
      case ('[srcFn], '[tgtFn]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, srcFn *: EmptyTuple], Tuple.Concat[TP, tgtFn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Optionalize(sourcePath)
          )
        }
    }
  }

  def changeFieldTypeImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    target: Expr[B => Any],
    converter: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val sourceFieldName     = extractFieldNameFromTerm(source.asTerm)
    val targetFieldName     = extractFieldNameFromTerm(target.asTerm)
    val sourceFieldNameType = ConstantType(StringConstant(sourceFieldName))
    val targetFieldNameType = ConstantType(StringConstant(targetFieldName))

    (sourceFieldNameType.asType, targetFieldNameType.asType) match {
      case ('[srcFn], '[tgtFn]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, srcFn *: EmptyTuple], Tuple.Concat[TP, tgtFn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.ChangeType(sourcePath, $converter)
          )
        }
    }
  }

  private def extractFieldNameFromTerm(term: Any)(using q: Quotes): String = {
    import q.reflect.*

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'")
    }

    @tailrec
    def extractLastFieldName(t: Term): String = t match {
      case Select(_, fieldName)                        => fieldName
      case Inlined(_, _, inner)                        => extractLastFieldName(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractLastFieldName(body)
      case Ident(_)                                    => report.errorAndAbort("Selector must access at least one field")
      case _                                           => report.errorAndAbort(s"Cannot extract field name from: ${t.show}")
    }

    val pathBody = toPathBody(term.asInstanceOf[Term])
    extractLastFieldName(pathBody)
  }
}
