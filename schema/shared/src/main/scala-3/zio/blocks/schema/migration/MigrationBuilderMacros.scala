package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema.{Schema, SchemaExpr}

object MigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, SH, ?]] = {
    import q.reflect.*

    val fieldName        = extractFieldNameFromTerm(target.asTerm)
    val fieldNameType    = ConstantType(StringConstant(fieldName))
    val fieldNameWrapped = TypeRepr.of[FieldName].appliedTo(fieldNameType)
    val newTPType        = AndType(TypeRepr.of[TP], fieldNameWrapped)

    newTPType.asType match {
      case '[newTp] =>
        '{
          val targetPath = SelectorMacros.toPath[B, Any]($target)
          new MigrationBuilder[A, B, SH, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.AddField(targetPath, $default.toDynamic)
          )
        }
    }
  }

  def dropFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect.*

    val fieldName        = extractFieldNameFromTerm(source.asTerm)
    val fieldNameType    = ConstantType(StringConstant(fieldName))
    val fieldNameWrapped = TypeRepr.of[FieldName].appliedTo(fieldNameType)
    val newSHType        = AndType(TypeRepr.of[SH], fieldNameWrapped)

    newSHType.asType match {
      case '[newSh] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newSh, TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.DropField(sourcePath, $defaultForReverse.toDynamic)
          )
        }
    }
  }

  def renameFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
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
    val fromFieldWrapped  = TypeRepr.of[FieldName].appliedTo(fromFieldNameType)
    val toFieldWrapped    = TypeRepr.of[FieldName].appliedTo(toFieldNameType)
    val newSHType         = AndType(TypeRepr.of[SH], fromFieldWrapped)
    val newTPType         = AndType(TypeRepr.of[TP], toFieldWrapped)

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Rename(fromPath, $toNameExpr)
          )
        }
    }
  }

  def transformFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
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
    val fromFieldWrapped  = TypeRepr.of[FieldName].appliedTo(fromFieldNameType)
    val toFieldWrapped    = TypeRepr.of[FieldName].appliedTo(toFieldNameType)
    val newSHType         = AndType(TypeRepr.of[SH], fromFieldWrapped)
    val newTPType         = AndType(TypeRepr.of[TP], toFieldWrapped)

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformValue(fromPath, $transform.toDynamic)
          )
        }
    }
  }

  def mandateFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
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
    val sourceFieldWrapped  = TypeRepr.of[FieldName].appliedTo(sourceFieldNameType)
    val targetFieldWrapped  = TypeRepr.of[FieldName].appliedTo(targetFieldNameType)
    val newSHType           = AndType(TypeRepr.of[SH], sourceFieldWrapped)
    val newTPType           = AndType(TypeRepr.of[TP], targetFieldWrapped)

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Option[?]]($source)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Mandate(sourcePath, $default.toDynamic)
          )
        }
    }
  }

  def optionalizeFieldImpl[A: Type, B: Type, SH: Type, TP: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    target: Expr[B => Option[?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val sourceFieldName     = extractFieldNameFromTerm(source.asTerm)
    val targetFieldName     = extractFieldNameFromTerm(target.asTerm)
    val sourceFieldNameType = ConstantType(StringConstant(sourceFieldName))
    val targetFieldNameType = ConstantType(StringConstant(targetFieldName))
    val sourceFieldWrapped  = TypeRepr.of[FieldName].appliedTo(sourceFieldNameType)
    val targetFieldWrapped  = TypeRepr.of[FieldName].appliedTo(targetFieldNameType)
    val newSHType           = AndType(TypeRepr.of[SH], sourceFieldWrapped)
    val newTPType           = AndType(TypeRepr.of[TP], targetFieldWrapped)

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Optionalize(sourcePath)
          )
        }
    }
  }

  def changeFieldTypeImpl[A: Type, B: Type, SH: Type, TP: Type](
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
    val sourceFieldWrapped  = TypeRepr.of[FieldName].appliedTo(sourceFieldNameType)
    val targetFieldWrapped  = TypeRepr.of[FieldName].appliedTo(targetFieldNameType)
    val newSHType           = AndType(TypeRepr.of[SH], sourceFieldWrapped)
    val newTPType           = AndType(TypeRepr.of[TP], targetFieldWrapped)

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.ChangeType(sourcePath, $converter.toDynamic)
          )
        }
    }
  }

  def transformNestedImpl[A: Type, B: Type, F1: Type, F2: Type, SH: Type, TP: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => F1],
    target: Expr[B => F2],
    nestedMigration: Expr[MigrationBuilder[F1, F2, Any, Any] => MigrationBuilder[F1, F2, ?, ?]],
    nestedSourceSchema: Expr[Schema[F1]],
    nestedTargetSchema: Expr[Schema[F2]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect.*

    val sourceFieldName = extractFieldNameFromTerm(source.asTerm)
    val targetFieldName = extractFieldNameFromTerm(target.asTerm)

    val nestedSourceFields = extractCaseClassFieldNames[F1]
    val nestedTargetFields = extractCaseClassFieldNames[F2]

    var newSHType              = TypeRepr.of[SH]
    val sourceFieldNameType    = ConstantType(StringConstant(sourceFieldName))
    val sourceFieldNameWrapped = TypeRepr.of[FieldName].appliedTo(sourceFieldNameType)
    newSHType = AndType(newSHType, sourceFieldNameWrapped)

    for (nestedField <- nestedSourceFields) {
      val dotPath             = s"$sourceFieldName.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[FieldName].appliedTo(dotPathType)
      newSHType = AndType(newSHType, dotPathFieldWrapped)
    }

    var newTPType              = TypeRepr.of[TP]
    val targetFieldNameType    = ConstantType(StringConstant(targetFieldName))
    val targetFieldNameWrapped = TypeRepr.of[FieldName].appliedTo(targetFieldNameType)
    newTPType = AndType(newTPType, targetFieldNameWrapped)

    for (nestedField <- nestedTargetFields) {
      val dotPath             = s"$targetFieldName.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[FieldName].appliedTo(dotPathType)
      newTPType = AndType(newTPType, dotPathFieldWrapped)
    }

    (newSHType.asType, newTPType.asType) match {
      case ('[newSh], '[newTp]) =>
        '{
          val sourcePath   = SelectorMacros.toPath[A, F1]($source)
          val innerBuilder = new MigrationBuilder[F1, F2, Any, Any](
            $nestedSourceSchema,
            $nestedTargetSchema,
            Vector.empty
          )
          val builtInner = $nestedMigration(innerBuilder)
          new MigrationBuilder[A, B, newSh, newTp](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformNested(sourcePath, builtInner.actions)
          )
        }
    }
  }

  private def extractCaseClassFieldNames[T: Type](using q: Quotes): List[String] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (sym.flags.is(Flags.Case) && sym.caseFields.nonEmpty) {
      sym.caseFields.map(_.name)
    } else {
      Nil
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
