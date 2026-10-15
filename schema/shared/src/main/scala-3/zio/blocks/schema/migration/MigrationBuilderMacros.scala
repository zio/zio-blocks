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

    val fieldPath = extractFieldPathFromTerm(target.asTerm)
    checkCollisionAtCallSite[A, B, CS]("addField", Nil, List(fieldPath), checkAutoMapped = false)
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

    val fieldPath = extractFieldPathFromTerm(source.asTerm)
    checkCollisionAtCallSite[A, B, CS]("dropField", List(fieldPath), Nil, checkAutoMapped = false)
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

    val fromFieldPath = extractFieldPathFromTerm(from.asTerm)
    val toFieldPath   = extractFieldPathFromTerm(to.asTerm)
    checkCollisionAtCallSite[A, B, CS]("renameField", List(fromFieldPath), List(toFieldPath), checkAutoMapped = true)
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
    checkCollisionAtCallSite[A, B, CS](
      "transformField",
      List(fromFieldPath),
      List(toFieldPath),
      checkAutoMapped = false
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
    checkCollisionAtCallSite[A, B, CS](
      "mandateField",
      List(sourceFieldPath),
      List(targetFieldPath),
      checkAutoMapped = false
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
    // Source-side check: reject already-Option source (Option -> Option would double-wrap at runtime).
    // Unresolvable member (e.g. structural selector) skips the check.
    val segments          = sourceFieldPath.split('.').toList
    var current: TypeRepr = TypeRepr.of[A].dealias
    var resolvable        = true
    var idx               = 0
    while (idx < segments.length && resolvable) {
      fieldTypes(current).get(segments(idx)) match {
        case Some(fieldType) => current = fieldType.dealias
        case None            => resolvable = false
      }
      idx += 1
    }
    if (resolvable && (current.dealias <:< TypeRepr.of[Option[Any]]))
      report.errorAndAbort(
        s"optionalizeField requires source field '$sourceFieldPath' to be a non-Option type, " +
          s"but found '${current.dealias.show}'. " +
          s"optionalizeField wraps the value in Option at runtime."
      )
    checkCollisionAtCallSite[A, B, CS](
      "optionalizeField",
      List(sourceFieldPath),
      List(targetFieldPath),
      checkAutoMapped = false
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

    val sourceFieldPath = extractFieldPathFromTerm(source.asTerm)
    checkCollisionAtCallSite[A, B, CS](
      "changeFieldType",
      List(sourceFieldPath),
      List(sourceFieldPath),
      checkAutoMapped = false
    )
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

    // Resolve the target prefix against B by walking B's fields for each selector
    // segment. Unresolvable segments (e.g. structural B types the S2 validator
    // cannot enumerate) fall back to the legacy source prefix instead of aborting,
    // so previously-compiling migrations keep compiling. The emitted MigrateField
    // keeps the source path (the runtime navigates the source value).
    // NOTE: when every segment resolves, targetFieldPrefix == sourceFieldPath; the
    // walk doubles as an early existence check for case-class B types.
    val targetFieldPrefix = {
      val segments          = sourceFieldPath.split('.').toList
      var current: TypeRepr = TypeRepr.of[B].dealias
      var resolvable        = true
      for (segment <- segments if resolvable) {
        fieldTypes(current).get(segment) match {
          case Some(fieldType) => current = fieldType.dealias
          case None            => resolvable = false
        }
      }
      if (resolvable) segments.mkString(".") else sourceFieldPath
    }

    checkCollisionAtCallSite[A, B, CS](
      "migrateField",
      sourceFieldPath :: nestedSourceFields.map(nestedField => s"$sourceFieldPath.$nestedField"),
      targetFieldPrefix :: nestedTargetFields.map(nestedField => s"$targetFieldPrefix.$nestedField"),
      checkAutoMapped = false
    )

    for (nestedField <- nestedTargetFields) {
      val dotPath             = s"$targetFieldPrefix.$nestedField"
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

  private def checkCollisionAtCallSite[A: Type, B: Type, CS: Type](
    opLabel: String,
    handledNames: List[String],
    providedNames: List[String],
    checkAutoMapped: Boolean
  )(using q: Quotes): Unit = {
    import q.reflect.*

    val (priorHandled, priorProvided) = extractHandledAndProvidedCounts[CS]

    def incremented(prior: Map[String, Int], names: List[String]): Map[String, Int] =
      names.foldLeft(prior)((counts, name) => counts.updated(name, counts.getOrElse(name, 0) + 1))

    val newHandled  = incremented(priorHandled, handledNames)
    val newProvided = incremented(priorProvided, providedNames)

    def countOf(counts: Map[String, Int], name: String): Int = counts.getOrElse(name, 0)

    (providedNames ++ newProvided.keySet).distinct.collectFirst {
      case n if countOf(newProvided, n) > 1 && countOf(newHandled, n) != 1 => n
    }.foreach { n =>
      report.errorAndAbort(
        s"$opLabel failed: field '$n' is already provided by a previous operation. " +
          s"Each target field must be provided exactly once."
      )
    }

    (handledNames ++ newHandled.keySet).distinct.collectFirst {
      case n if countOf(newHandled, n) > 1 && countOf(newProvided, n) != 1 => n
    }.foreach { n =>
      report.errorAndAbort(
        s"$opLabel failed: field '$n' is already handled/consumed by a previous operation. " +
          s"Each source field must be handled exactly once."
      )
    }

    if (checkAutoMapped) {
      val autoMapped = computeAutoMappedWithNested[A, B]("")
      providedNames.collectFirst { case n if autoMapped.contains(n) && countOf(newHandled, n) != 1 => n }.foreach { n =>
        report.errorAndAbort(
          s"$opLabel failed: field '$n' collides with an auto-mapped field that is already provided. " +
            s"Rename targets must not collide with existing fields."
        )
      }
    }
  }

  private def extractHandledAndProvidedCounts[CS: Type](using q: Quotes): (Map[String, Int], Map[String, Int]) = {
    import q.reflect.*

    var handled  = Map.empty[String, Int]
    var provided = Map.empty[String, Int]

    def count(m: Map[String, Int], n: String): Map[String, Int] =
      m.updated(n, m.getOrElse(n, 0) + 1)

    def extract(tpe: TypeRepr): Unit = {
      val dealiased = tpe.dealias
      dealiased match {
        case AndType(left, right) =>
          extract(left)
          extract(right)
        case t if t =:= TypeRepr.of[Any] => ()
        case AppliedType(tycon, args)    =>
          tycon.typeSymbol.name match {
            case "AddField" =>
              extractStringFromType(args.head).foreach(n => provided = count(provided, n))
            case "DropField" =>
              extractStringFromType(args.head).foreach(n => handled = count(handled, n))
            case "RenameField" =>
              extractStringFromType(args.head).foreach(n => handled = count(handled, n))
              extractStringFromType(args(1)).foreach(n => provided = count(provided, n))
            case "TransformField" =>
              extractStringFromType(args.head).foreach(n => handled = count(handled, n))
              extractStringFromType(args(1)).foreach(n => provided = count(provided, n))
            case "MandateField" =>
              extractStringFromType(args.head).foreach(n => handled = count(handled, n))
              extractStringFromType(args(1)).foreach(n => provided = count(provided, n))
            case "OptionalizeField" =>
              extractStringFromType(args.head).foreach(n => handled = count(handled, n))
              extractStringFromType(args(1)).foreach(n => provided = count(provided, n))
            case "ChangeFieldType" =>
              extractStringFromType(args.head).foreach { n =>
                handled = count(handled, n)
                provided = count(provided, n)
              }
            case "MigrateField" =>
              extractStringFromType(args.head).foreach { n =>
                handled = count(handled, n)
                provided = count(provided, n)
              }
            case "FieldName" =>
              extractStringFromType(args.head).foreach { n =>
                handled = count(handled, n)
                provided = count(provided, n)
              }
            case "RenameCase" | "TransformCase" | "TransformElements" | "TransformKeys" | "TransformValues" =>
              ()
            case _ => ()
          }
        case _ => ()
      }
    }

    extract(TypeRepr.of[CS])
    (handled, provided)
  }

  private def extractStringFromType(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[String] = {
    import q.reflect.*
    tpe.dealias match {
      case ConstantType(StringConstant(s)) => Some(s)
      case _                               => None
    }
  }

  private def computeAutoMappedWithNested[A: Type, B: Type](prefix: String)(using q: Quotes): Set[String] = {
    import q.reflect.*

    val sourceType = TypeRepr.of[A]
    val targetType = TypeRepr.of[B]

    val sourceFieldTypes: Map[String, TypeRepr] = fieldTypes(sourceType)
    val targetFieldTypes: Map[String, TypeRepr] = fieldTypes(targetType)

    val sourceFields = sourceFieldTypes.keySet
    val targetFields = targetFieldTypes.keySet
    val commonFields = sourceFields.intersect(targetFields)

    commonFields.flatMap { fieldName =>
      val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) if srcType =:= tgtType =>
          Set(fullFieldName) ++ computeAutoMappedNested(srcType, tgtType, fullFieldName)
        case (Some(srcType), Some(tgtType)) if srcType <:< tgtType || tgtType <:< srcType =>
          Set(fullFieldName)
        case _ => Set.empty[String]
      }
    }.toSet
  }

  private def computeAutoMappedNested(using
    q: Quotes
  )(srcType: q.reflect.TypeRepr, tgtType: q.reflect.TypeRepr, prefix: String): Set[String] = {
    val srcFields = fieldTypes(srcType)
    val tgtFields = fieldTypes(tgtType)

    if (srcFields.nonEmpty && tgtFields.nonEmpty) {
      val commonFields = srcFields.keySet.intersect(tgtFields.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = s"$prefix.$fieldName"
        (srcFields.get(fieldName), tgtFields.get(fieldName)) match {
          case (Some(srcFieldType), Some(tgtFieldType)) if srcFieldType =:= tgtFieldType =>
            Set(fullFieldName) ++ computeAutoMappedNested(srcFieldType, tgtFieldType, fullFieldName)
          case (Some(srcFieldType), Some(tgtFieldType))
              if srcFieldType <:< tgtFieldType || tgtFieldType <:< srcFieldType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }.toSet
    } else {
      Set.empty
    }
  }
}
