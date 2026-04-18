/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema.{Schema, SchemaExpr}

object MigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fieldPath     = extractFieldPathFromTerm(target.asTerm)
    val fieldNameType = ConstantType(StringConstant(fieldPath))
    val addedWrapped  = TypeRepr.of[Changeset.AddField].appliedTo(fieldNameType)
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
    val droppedWrapped = TypeRepr.of[Changeset.DropField].appliedTo(fieldNameType)
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
    val renamedWrapped = TypeRepr.of[Changeset.RenameField].appliedTo(List(fromFieldType, toFieldType))
    val newCSType      = AndType(TypeRepr.of[CS], renamedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.RenameField(fromPath, $toNameExpr)
          )
        }
    }
  }

  def renameCaseImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    from: Expr[String],
    to: Expr[String]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fromValue = from.valueOrAbort
    val toValue   = to.valueOrAbort
    val fromType  = ConstantType(StringConstant(fromValue))
    val toType    = ConstantType(StringConstant(toValue))
    val renamed   = TypeRepr.of[Changeset.RenameCase].appliedTo(List(fromType, toType))
    val newCSType  = AndType(TypeRepr.of[CS], renamed)

    newCSType.asType match {
      case '[newCs] =>
        '{
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.RenameCase(DynamicOptic.root, $from, $to)
          )
        }
    }
  }

  def transformCaseImpl[A: Type, B: Type, CaseA: Type, CaseB: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    caseName: Expr[String],
    caseSourceSchema: Expr[_root_.zio.blocks.schema.Schema[CaseA]],
    caseTargetSchema: Expr[_root_.zio.blocks.schema.Schema[CaseB]],
    caseMigration: Expr[MigrationBuilder[CaseA, CaseB, Any] => MigrationBuilder[CaseA, CaseB, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val caseNameValue = caseName.valueOrAbort
    val caseNameType  = ConstantType(StringConstant(caseNameValue))
    val transformed   = TypeRepr.of[Changeset.TransformCase].appliedTo(List(caseNameType))
    val newCSType     = AndType(TypeRepr.of[CS], transformed)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val innerBuilder = new MigrationBuilder[CaseA, CaseB, Any](
            $caseSourceSchema,
            $caseTargetSchema,
            Vector.empty
          )
          val builtInner = $caseMigration(innerBuilder)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformCase(DynamicOptic.root.caseOf($caseName), builtInner.actions)
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
    val transformedWrapped = TypeRepr.of[Changeset.TransformField].appliedTo(List(fromFieldType, toFieldType))
    val newCSType          = AndType(TypeRepr.of[CS], transformedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformField(fromPath, $transform.toDynamic)
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
    val mandatedWrapped = TypeRepr.of[Changeset.MandateField].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType       = AndType(TypeRepr.of[CS], mandatedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Option[?]]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.MandateField(sourcePath, $default.toDynamic)
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
    val optionalizedWrapped = TypeRepr.of[Changeset.OptionalizeField].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType           = AndType(TypeRepr.of[CS], optionalizedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.OptionalizeField(sourcePath)
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
    val typeChangedWrapped = TypeRepr.of[Changeset.ChangeFieldType].appliedTo(List(sourceFieldType, targetFieldType))
    val newCSType          = AndType(TypeRepr.of[CS], typeChangedWrapped)

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.ChangeFieldType(sourcePath, $converter.toDynamic)
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
    val migratedWrapped     = TypeRepr.of[Changeset.MigrateField].appliedTo(sourceFieldNameType)
    newCSType = AndType(newCSType, migratedWrapped)

    for (nestedField <- nestedSourceFields) {
      val dotPath             = s"$sourceFieldPath.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[Changeset.FieldName].appliedTo(dotPathType)
      newCSType = AndType(newCSType, dotPathFieldWrapped)
    }

    for (nestedField <- nestedTargetFields) {
      val dotPath             = s"$sourceFieldPath.$nestedField"
      val dotPathType         = ConstantType(StringConstant(dotPath))
      val dotPathFieldWrapped = TypeRepr.of[Changeset.FieldName].appliedTo(dotPathType)
      newCSType = AndType(newCSType, dotPathFieldWrapped)
    }

    newCSType.asType match {
      case '[newCs] =>
        '{
          val sourcePath = SelectorMacros.toPath[A, F1]($source)
          new MigrationBuilder[A, B, newCs](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.MigrateField(sourcePath, $migration.dynamicMigration)
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
