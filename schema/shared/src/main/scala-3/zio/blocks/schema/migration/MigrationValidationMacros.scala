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

  def validateMigration[A: Type, B: Type, CS: Type](using
    Quotes
  ): Expr[MigrationComplete[A, B, CS]] = {
    import quotes.reflect.*

    val sourceFields = extractFieldNamesWithNested[A]("")
    val targetFields = extractFieldNamesWithNested[B]("")
    val sourceCases  = extractCaseNamesWithNested[A]("")
    val targetCases  = extractCaseNamesWithNested[B]("")

    val (handledFields, providedFields) = extractHandledAndProvided[CS]

    val autoMapped = computeAutoMappedWithNested[A, B]("")

    val allExplicitlyHandled = handledFields ++ providedFields
    val crossTypeAutoMapped  = computeCrossTypeAutoMapped[A, B](allExplicitlyHandled, "")

    var coveredSource = (handledFields ++ autoMapped ++ crossTypeAutoMapped) ++ sourceCases.intersect(targetCases)
    var coveredTarget = (providedFields ++ autoMapped ++ crossTypeAutoMapped) ++ sourceCases.intersect(targetCases)

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

      errors.append("\n  Alternatively, use .buildPartial to skip validation.")

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
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
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
            case "RenameCase" =>
              extractStringFromType(args.head).foreach(n => handled += n)
              extractStringFromType(args(1)).foreach(n => provided += n)
            case "TransformCase" =>
              extractStringFromType(args.head).foreach { n =>
                handled += n
                provided += n
              }
            case "TransformElements" | "TransformKeys" | "TransformValues" =>
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

    if (tpe.typeSymbol.flags.is(Flags.Case) && tpe.typeSymbol.caseFields.nonEmpty) {
      tpe.typeSymbol.caseFields.flatMap { field =>
        val fieldName = if (prefix.isEmpty) field.name else s"$prefix.${field.name}"
        val fieldType = tpe.memberType(field)

        if (fieldType.typeSymbol.flags.is(Flags.Case) && fieldType.typeSymbol.caseFields.nonEmpty) {
          Set(fieldName) ++ extractNestedFieldNames(fieldType, fieldName)
        } else {
          Set(fieldName)
        }
      }.toSet
    } else {
      Set.empty
    }
  }

  private def extractNestedFieldNames(using q: Quotes)(tpe: q.reflect.TypeRepr, prefix: String): Set[String] = {
    import q.reflect.*

    if (tpe.typeSymbol.flags.is(Flags.Case) && tpe.typeSymbol.caseFields.nonEmpty) {
      tpe.typeSymbol.caseFields.flatMap { field =>
        val fieldName = s"$prefix.${field.name}"
        val fieldType = tpe.memberType(field)

        if (fieldType.typeSymbol.flags.is(Flags.Case) && fieldType.typeSymbol.caseFields.nonEmpty) {
          Set(fieldName) ++ extractNestedFieldNames(fieldType, fieldName)
        } else {
          Set(fieldName)
        }
      }.toSet
    } else {
      Set.empty
    }
  }

  private def extractCaseNamesWithNested[T: Type](prefix: String)(using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T].dealias
    extractCaseNamesWithNested(tpe, prefix)
  }

  private def extractCaseNamesWithNested(tpe: quotes.reflect.TypeRepr, prefix: String)(using
    Quotes
  ): Set[String] = {
    import quotes.reflect.*

    if (isVariantType(tpe)) {
      variantChildren(tpe).map { child =>
        val caseName = child.name
        if (prefix.isEmpty) caseName else s"$prefix.$caseName"
      }.toSet
    } else {
      Set.empty
    }
  }

  private def isCaseClassType(tpe: quotes.reflect.TypeRepr)(using Quotes): Boolean = {
    import quotes.reflect.*
    val sym = tpe.dealias.typeSymbol
    sym.flags.is(Flags.Case) && sym.caseFields.nonEmpty
  }

  private def isVariantType(tpe: quotes.reflect.TypeRepr)(using Quotes): Boolean = {
    import quotes.reflect.*
    val sym = tpe.dealias.typeSymbol
    sym.children.nonEmpty && (sym.flags.is(Flags.Sealed) || sym.flags.is(Flags.Enum))
  }

  private def variantChildren(tpe: quotes.reflect.TypeRepr)(using Quotes): List[quotes.reflect.Symbol] = {
    import quotes.reflect.*
    tpe.dealias.typeSymbol.children.sortBy(_.name)
  }

  private def resolveChildType(parent: quotes.reflect.TypeRepr, child: quotes.reflect.Symbol)(using
    Quotes
  ): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    if (child.isType) parent.memberType(child)
    else child.termRef
  }

  private def computeAutoMappedWithNested[A: Type, B: Type](prefix: String)(using Quotes): Set[String] = {
    import quotes.reflect.*

    computeAutoMappedWithNestedImpl(TypeRepr.of[A].dealias, TypeRepr.of[B].dealias, prefix)
  }

  private def computeAutoMappedWithNestedImpl(using
    Quotes
  )(
    sourceType: quotes.reflect.TypeRepr,
    targetType: quotes.reflect.TypeRepr,
    prefix: String
  ): Set[String] = {
    import quotes.reflect.*

    val sourceSym = sourceType.dealias.typeSymbol
    val targetSym = targetType.dealias.typeSymbol

    if (isCaseClassType(sourceType) && isCaseClassType(targetType)) {
      val sourceFieldTypes: Map[String, TypeRepr] =
        sourceSym.caseFields.map(f => f.name -> sourceType.memberType(f).dealias).toMap
      val targetFieldTypes: Map[String, TypeRepr] =
        targetSym.caseFields.map(f => f.name -> targetType.memberType(f).dealias).toMap

      val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
        (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
          case (Some(srcType), Some(tgtType)) if srcType =:= tgtType =>
            Set(fullFieldName) ++ computeAutoMappedWithNestedImpl(srcType, tgtType, fullFieldName)
          case (Some(srcType), Some(tgtType)) if srcType <:< tgtType || tgtType <:< srcType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }
    } else if (isVariantType(sourceType) && isVariantType(targetType)) {
      val sourceCases = variantChildren(sourceType).map(sym => sym.name -> resolveChildType(sourceType, sym).dealias).toMap
      val targetCases = variantChildren(targetType).map(sym => sym.name -> resolveChildType(targetType, sym).dealias).toMap
      val commonCases = sourceCases.keySet.intersect(targetCases.keySet)

      commonCases.flatMap { caseName =>
        val fullName = if (prefix.isEmpty) caseName else s"$prefix.$caseName"
        (sourceCases.get(caseName), targetCases.get(caseName)) match {
          case (Some(srcCaseType), Some(tgtCaseType)) if srcCaseType =:= tgtCaseType =>
            Set(fullName) ++ computeAutoMappedWithNestedImpl(srcCaseType, tgtCaseType, fullName)
          case (Some(srcCaseType), Some(tgtCaseType)) if srcCaseType <:< tgtCaseType || tgtCaseType <:< srcCaseType =>
            Set(fullName)
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

    val sourceFieldTypes: Map[String, TypeRepr] =
      if (sourceType.typeSymbol.flags.is(Flags.Case) && sourceType.typeSymbol.caseFields.nonEmpty)
        sourceType.typeSymbol.caseFields.map(f => f.name -> sourceType.memberType(f)).toMap
      else Map.empty

    val targetFieldTypes: Map[String, TypeRepr] =
      if (targetType.typeSymbol.flags.is(Flags.Case) && targetType.typeSymbol.caseFields.nonEmpty)
        targetType.typeSymbol.caseFields.map(f => f.name -> targetType.memberType(f)).toMap
      else Map.empty

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
    import q.reflect.*

    val srcFields =
      if (srcType.typeSymbol.flags.is(Flags.Case) && srcType.typeSymbol.caseFields.nonEmpty)
        srcType.typeSymbol.caseFields.map(f => f.name -> srcType.memberType(f)).toMap
      else Map.empty

    val tgtFields =
      if (tgtType.typeSymbol.flags.is(Flags.Case) && tgtType.typeSymbol.caseFields.nonEmpty)
        tgtType.typeSymbol.caseFields.map(f => f.name -> tgtType.memberType(f)).toMap
      else Map.empty

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
