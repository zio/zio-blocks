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

import scala.quoted.*

object MigrationValidationMacros {

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

  def validateMigration[A: Type, B: Type, CS: Type](using
    Quotes
  ): Expr[MigrationComplete[A, B, CS]] = {
    import quotes.reflect.*

    val sourceFields = extractFieldNamesWithNested[A]("")
    val targetFields = extractFieldNamesWithNested[B]("")

    val (handledFields, providedFields) = extractHandledAndProvided[CS]

    val autoMapped = computeAutoMappedWithNested[A, B]("")

    val allExplicitlyHandled = handledFields ++ providedFields
    val crossTypeAutoMapped  = computeCrossTypeAutoMapped[A, B](allExplicitlyHandled, "")

    var coveredSource = handledFields ++ autoMapped ++ crossTypeAutoMapped
    var coveredTarget = providedFields ++ autoMapped ++ crossTypeAutoMapped

    coveredSource = inferParentCoverage(sourceFields, coveredSource)
    coveredTarget = inferParentCoverage(targetFields, coveredTarget)

    val missingTarget   = targetFields -- coveredTarget
    val unhandledSource = sourceFields -- coveredSource

    if (missingTarget.nonEmpty || unhandledSource.nonEmpty) {
      val errors = new StringBuilder("Migration incomplete:\n")

      if (missingTarget.nonEmpty) {
        errors.append(s"\n  Target fields not provided: ${missingTarget.mkString(", ")}\n")
        errors.append("  Use addField, renameField, transformField, or migrateField to provide these fields.\n")
      }

      if (unhandledSource.nonEmpty) {
        errors.append(s"\n  Source fields not handled: ${unhandledSource.mkString(", ")}\n")
        errors.append("  Use dropField, renameField, transformField, or migrateField to handle these fields.\n")
      }

      report.errorAndAbort(errors.toString)
    }

    '{ MigrationComplete.unsafeCreate[A, B, CS] }
  }

  private def extractHandledAndProvided[CS: Type](using Quotes): (Set[String], Set[String]) = {
    import quotes.reflect.*

    var handled  = Set.empty[String]
    var provided = Set.empty[String]

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
              extractStringFromType(args.head).foreach(n => provided += n)
            case "DropField" =>
              extractStringFromType(args.head).foreach(n => handled += n)
            case "RenameField" =>
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
            case "TransformField" =>
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
            case "MandateField" =>
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
            case "OptionalizeField" =>
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
            case "ChangeFieldType" =>
              extractStringFromType(args.head).foreach { n =>
                handled += n
                provided += n
              }
            case "MigrateField" =>
              extractStringFromType(args.head).foreach { n =>
                handled += n
                provided += n
              }
            case "FieldName" =>
              extractStringFromType(args.head).foreach { n =>
                handled += n
                provided += n
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

  private def extractFieldNamesWithNested[T: Type](prefix: String)(using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    fieldTypes(tpe).flatMap { case (name, fieldType) =>
      val fieldName = if (prefix.isEmpty) name else s"$prefix.$name"
      if (fieldTypes(fieldType).nonEmpty)
        Set(fieldName) ++ extractNestedFieldNames(fieldType, fieldName)
      else
        Set(fieldName)
    }.toSet
  }

  private def extractNestedFieldNames(using q: Quotes)(tpe: q.reflect.TypeRepr, prefix: String): Set[String] =
    fieldTypes(tpe).flatMap { case (name, fieldType) =>
      val fieldName = s"$prefix.$name"
      if (fieldTypes(fieldType).nonEmpty)
        Set(fieldName) ++ extractNestedFieldNames(fieldType, fieldName)
      else
        Set(fieldName)
    }.toSet

  private def computeAutoMappedWithNested[A: Type, B: Type](prefix: String)(using Quotes): Set[String] = {
    import quotes.reflect.*

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
    }
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
      }
    } else {
      Set.empty
    }
  }

  private def computeCrossTypeAutoMapped[A: Type, B: Type](explicitlyHandled: Set[String], prefix: String)(using
    Quotes
  ): Set[String] =
    computeCrossTypeAutoMappedImpl(
      quotes.reflect.TypeRepr.of[A],
      quotes.reflect.TypeRepr.of[B],
      explicitlyHandled,
      prefix
    )

  private def computeCrossTypeAutoMappedImpl(using
    q: Quotes
  )(
    sourceType: q.reflect.TypeRepr,
    targetType: q.reflect.TypeRepr,
    explicitlyHandled: Set[String],
    prefix: String
  ): Set[String] = {
    import q.reflect.*

    val sourceFieldTypes: Map[String, TypeRepr] = fieldTypes(sourceType)
    val targetFieldTypes: Map[String, TypeRepr] = fieldTypes(targetType)

    val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

    commonFields.flatMap { fieldName =>
      val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) if !(srcType =:= tgtType) =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullFieldName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(srcType, tgtType, explicitlyHandled, fullFieldName)
          else Set.empty[String]
        case _ => Set.empty[String]
      }
    }
  }

  private def crossTypeAutoMapLeaves(using
    q: Quotes
  )(
    srcType: q.reflect.TypeRepr,
    tgtType: q.reflect.TypeRepr,
    explicitlyHandled: Set[String],
    prefix: String
  ): Set[String] = {
    val srcFields = fieldTypes(srcType)
    val tgtFields = fieldTypes(tgtType)

    val common = srcFields.keySet.intersect(tgtFields.keySet)

    common.flatMap { fieldName =>
      val fullName = s"$prefix.$fieldName"
      (srcFields(fieldName), tgtFields(fieldName)) match {
        case (s, t) if s =:= t => Set(fullName)
        case (s, t)            =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(s, t, explicitlyHandled, fullName)
          else Set.empty[String]
      }
    }
  }

  private def inferParentCoverage(allFields: Set[String], covered: Set[String]): Set[String] = {
    val parents = allFields.flatMap { f =>
      val parts = f.split('.')
      if (parts.length > 1) Some(parts.init.mkString(".")) else None
    }
    var result  = covered
    var changed = true
    while (changed) {
      changed = false
      for (parent <- parents) {
        if (!result.contains(parent)) {
          val children = allFields.filter(_.startsWith(s"$parent."))
          if (children.nonEmpty && children.forall(result.contains)) {
            result = result + parent
            changed = true
          }
        }
      }
    }
    result
  }
}
