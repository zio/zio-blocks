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
import zio.blocks.schema.SchemaExpr

object MigrationBuilderMacros {

  private def refinementFieldTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): Map[String, q.reflect.TypeRepr] = {
    import q.reflect.*

    def loop(current: TypeRepr, acc: List[(String, TypeRepr)]): List[(String, TypeRepr)] =
      current.dealias match {
        case Refinement(parent, fieldName, fieldInfo) if fieldName != "<none>" =>
          loop(parent, (fieldName -> fieldInfo) :: acc)
        case _ => acc
      }

    loop(tpe, Nil).reverse.toMap
  }

  private def fieldTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): Map[String, q.reflect.TypeRepr] = {
    import q.reflect.*

    val dealiased = tpe.dealias
    val sym       = dealiased.typeSymbol

    if (sym.flags.is(Flags.Case) && sym.caseFields.nonEmpty)
      sym.caseFields.map(field => field.name -> dealiased.memberType(field)).toMap
    else
      refinementFieldTypes(dealiased)
  }

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

  def transformFieldImpl[A: Type, B: Type, CS: Type](
    builder: Expr[MigrationBuilder[A, B, CS]],
    from: Expr[A => Any],
    to: Expr[B => Any],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val fromFieldPath = extractFieldPathFromTerm(from.asTerm)
    val toFieldPath   = extractFieldPathFromTerm(to.asTerm)
    if (fromFieldPath != toFieldPath)
      report.errorAndAbort(
        s"transformField requires the same field name in source and target, " +
          s"got '$fromFieldPath' and '$toFieldPath'. " +
          s"Use renameField followed by transformField for rename-and-transform."
      )
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
    if (sourceFieldPath != targetFieldPath)
      report.errorAndAbort(
        s"mandateField requires the same field name in source and target, " +
          s"got '$sourceFieldPath' and '$targetFieldPath'. " +
          s"Use renameField followed by mandateField for rename-and-mandate."
      )
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

    val sourceFieldPath = extractFieldPathFromTerm(source.asTerm)
    val targetFieldPath = extractFieldPathFromTerm(target.asTerm)
    if (sourceFieldPath != targetFieldPath)
      report.errorAndAbort(
        s"optionalizeField requires the same field name in source and target, " +
          s"got '$sourceFieldPath' and '$targetFieldPath'. " +
          s"Use renameField followed by optionalizeField for rename-and-optionalize."
      )
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
    converter: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?]] = {
    import q.reflect.*

    val sourceFieldPath    = extractFieldPathFromTerm(source.asTerm)
    val sourceFieldType    = ConstantType(StringConstant(sourceFieldPath))
    val typeChangedWrapped = TypeRepr.of[Changeset.ChangeFieldType].appliedTo(List(sourceFieldType))
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

    val nestedSourceFields = extractNestedFieldNames[F1]
    val nestedTargetFields = extractNestedFieldNames[F2]

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

  private def extractNestedFieldNames[T: Type](using q: Quotes): List[String] = {
    import q.reflect.*

    def loop(tpe: TypeRepr, prefix: String): List[String] =
      fieldTypes(tpe).toList.flatMap { case (name, fieldType) =>
        val fieldName = if (prefix.isEmpty) name else s"$prefix.$name"
        val nested    = loop(fieldType, fieldName)
        fieldName :: nested
      }

    loop(TypeRepr.of[T], "")
  }

  private def extractFieldPathFromTerm(term: Any)(using q: Quotes): String = {
    import q.reflect.*

    def structuralFieldAccess(t: Term): Option[(Term, String)] = SelectorMacros.extractSelectDynamic(t)

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'")
    }

    def extractPath(t: Term): List[String] = t match {
      case Select(parent, fieldName)                       => extractPath(parent) :+ fieldName
      case other if structuralFieldAccess(other).isDefined =>
        val (parent, fieldName) = structuralFieldAccess(other).get
        extractPath(parent) :+ fieldName
      case Ident(_) => Nil
      case _        => report.errorAndAbort(s"Cannot extract field path from: ${t.show}")
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

    def structuralFieldAccess(t: Term): Option[(Term, String)] = SelectorMacros.extractSelectDynamic(t)

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'")
    }

    @tailrec
    def extractLastFieldName(t: Term): String = t match {
      case Select(_, fieldName)                            => fieldName
      case other if structuralFieldAccess(other).isDefined => structuralFieldAccess(other).get._2
      case Inlined(_, _, inner)                            => extractLastFieldName(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _)     => extractLastFieldName(body)
      case Ident(_)                                        => report.errorAndAbort("Selector must access at least one field")
      case _                                               => report.errorAndAbort(s"Cannot extract field name from: ${t.show}")
    }

    val pathBody = toPathBody(term.asInstanceOf[Term])
    extractLastFieldName(pathBody)
  }
}
