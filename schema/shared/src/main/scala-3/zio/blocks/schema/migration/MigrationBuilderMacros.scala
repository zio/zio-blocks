package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema.SchemaExpr

object MigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fieldPath     = extractFieldPathFromTerm(target.asTerm)
    val fieldNameType = ConstantType(StringConstant(fieldPath))
    val addedWrapped  = TypeRepr.of[Added].appliedTo(fieldNameType)
    val newCSType     = AndType(TypeRepr.of[CS], addedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val targetPath = SelectorMacros.toPath[B, Any]($target)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.AddField(targetPath, $default.toDynamic)
          )
        }
    }
  }

  def dropFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fieldPath      = extractFieldPathFromTerm(source.asTerm)
    val fieldNameType  = ConstantType(StringConstant(fieldPath))
    val droppedWrapped = TypeRepr.of[Dropped].appliedTo(fieldNameType)
    val newCSType      = AndType(TypeRepr.of[CS], droppedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.DropField(sourcePath, $defaultForReverse.toDynamic)
          )
        }
    }
  }

  def renameFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fromFieldPath  = extractFieldPathFromTerm(from.asTerm)
    val toFieldPath    = extractFieldPathFromTerm(to.asTerm)
    val toLeafName     = extractLeafFieldName(to.asTerm)
    val fromFieldType  = ConstantType(StringConstant(fromFieldPath))
    val toFieldType    = ConstantType(StringConstant(toFieldPath))
    val toNameExpr     = Expr(toLeafName)
    val renamedWrapped = TypeRepr.of[Renamed].appliedTo(List(fromFieldType, toFieldType))
    val newCSType      = AndType(TypeRepr.of[CS], renamedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Rename(fromPath, $toNameExpr)
          )
        }
    }
  }

  def transformFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    from: Expr[A => Any],
    to: Expr[B => Any],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fromFieldPath      = extractFieldPathFromTerm(from.asTerm)
    val toFieldPath        = extractFieldPathFromTerm(to.asTerm)
    val fromFieldType      = ConstantType(StringConstant(fromFieldPath))
    val toFieldType        = ConstantType(StringConstant(toFieldPath))
    val transformedWrapped = TypeRepr.of[Transformed].appliedTo(List(fromFieldType, toFieldType))
    val newCSType          = AndType(TypeRepr.of[CS], transformedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformValue(fromPath, $transform.toDynamic)
          )
        }
    }
  }

  def mandateFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => Option[?]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val sourceFieldPath = extractFieldPathFromTerm(source.asTerm)
    val targetFieldPath = extractFieldPathFromTerm(target.asTerm)
    val sourceFieldType = ConstantType(StringConstant(sourceFieldPath))
    val targetFieldType = ConstantType(StringConstant(targetFieldPath))
    val mandatedWrapped = TypeRepr.of[Mandated].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType       = AndType(TypeRepr.of[CS], mandatedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Option[?]]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Mandate(sourcePath, $default.toDynamic)
          )
        }
    }
  }

  def optionalizeFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => Any],
    target: Expr[B => Option[?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val sourceFieldPath     = extractFieldPathFromTerm(source.asTerm)
    val targetFieldPath     = extractFieldPathFromTerm(target.asTerm)
    val sourceFieldType     = ConstantType(StringConstant(sourceFieldPath))
    val targetFieldType     = ConstantType(StringConstant(targetFieldPath))
    val optionalizedWrapped = TypeRepr.of[Optionalized].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType           = AndType(TypeRepr.of[CS], optionalizedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Optionalize(sourcePath)
          )
        }
    }
  }

  def changeFieldTypeImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => Any],
    target: Expr[B => Any],
    converter: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val sourceFieldPath    = extractFieldPathFromTerm(source.asTerm)
    val targetFieldPath    = extractFieldPathFromTerm(target.asTerm)
    val sourceFieldType    = ConstantType(StringConstant(sourceFieldPath))
    val targetFieldType    = ConstantType(StringConstant(targetFieldPath))
    val typeChangedWrapped = TypeRepr.of[TypeChanged].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType          = AndType(TypeRepr.of[CS], typeChangedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.ChangeType(sourcePath, $converter.toDynamic)
          )
        }
    }
  }

  def migrateFieldExplicitImpl[A: Type, B: Type, F1: Type, F2: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => F1],
    migration: Expr[Migration[F1, F2]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val sourceFieldPath = extractFieldPathFromTerm(source.asTerm)

    val nestedSourceFields = extractCaseClassFieldNames[F1]
    val nestedTargetFields = extractCaseClassFieldNames[F2]

    var newCSType           = TypeRepr.of[CS]
    val sourceFieldNameType = ConstantType(StringConstant(sourceFieldPath))
    val migratedWrapped     = TypeRepr.of[Migrated].appliedTo(sourceFieldNameType)
    newCSType = AndType(newCSType, migratedWrapped)

    for (nestedField <- nestedSourceFields) {
      val dotPath             = s"$sourceFieldPath.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[FieldName].appliedTo(dotPathType)
      newCSType = AndType(newCSType, dotPathFieldWrapped)
    }

    for (nestedField <- nestedTargetFields) {
      val dotPath             = s"$sourceFieldPath.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[FieldName].appliedTo(dotPathType)
      newCSType = AndType(newCSType, dotPathFieldWrapped)
    }

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, F1]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.ApplyMigration(sourcePath, $migration.dynamicMigration)
          )
        }
    }
  }

  def migrateFieldImplicitImpl[A: Type, B: Type, F1: Type, F2: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    source: Expr[A => F1],
    migration: Expr[Migration[F1, F2]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] =
    migrateFieldExplicitImpl[A, B, F1, F2, CS](builder, source, migration)

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

  private def extractFieldPathFromTerm(term: Any)(using q: Quotes): String = {
    import q.reflect.*

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'")
    }

    def extractPath(t: Term): List[String] = t match {
      case Select(parent, fieldName) => extractPath(parent) :+ fieldName
      case Ident(_)                  => Nil
      case _                         => report.errorAndAbort(s"Cannot extract field path from: ${t.show}")
    }

    val pathBody = toPathBody(term.asInstanceOf[Term])
    val segments = extractPath(pathBody)
    if (segments.isEmpty) {
      report.errorAndAbort("Selector must access at least one field")
    }
    segments.mkString(".")
  }

  private def extractLeafFieldName(term: Any)(using q: Quotes): String = {
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
