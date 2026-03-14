package zio.blocks.schema.migration

import scala.quoted.*

object MigrationValidationMacros {

  def validateMigration[A: Type, B: Type, SH: Type, TP: Type](using
    Quotes
  ): Expr[MigrationComplete[A, B, SH, TP]] = {
    import quotes.reflect.*

    val sourceFields   = extractFieldNamesWithNested[A]("")
    val targetFields   = extractFieldNamesWithNested[B]("")
    val handledFields  = extractIntersectionElements[SH]
    val providedFields = extractIntersectionElements[TP]

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

      errors.append("\n  Alternatively, use .buildPartial to skip validation.")

      report.errorAndAbort(errors.toString)
    }

    '{ MigrationComplete.unsafeCreate[A, B, SH, TP] }
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

  private def extractIntersectionElements[T: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    def extract(tpe: TypeRepr): Set[String] = {
      val dealiased = tpe.dealias
      dealiased match {
        case AndType(left, right)                                                    => extract(left) ++ extract(right)
        case ConstantType(StringConstant(s))                                         => Set(s)
        case t if t =:= TypeRepr.of[Any]                                             => Set.empty
        case AppliedType(tycon, List(field)) if tycon.typeSymbol.name == "FieldName" =>
          extract(field)
        case _ => Set.empty
      }
    }

    extract(TypeRepr.of[T])
  }

  private def computeAutoMappedWithNested[A: Type, B: Type](prefix: String)(using Quotes): Set[String] = {
    import quotes.reflect.*

    val sourceType = TypeRepr.of[A]
    val targetType = TypeRepr.of[B]

    val sourceFieldTypes: Map[String, TypeRepr] =
      if (sourceType.typeSymbol.flags.is(Flags.Case)) {
        sourceType.typeSymbol.caseFields.map { field =>
          field.name -> sourceType.memberType(field)
        }.toMap
      } else Map.empty

    val targetFieldTypes: Map[String, TypeRepr] =
      if (targetType.typeSymbol.flags.is(Flags.Case)) {
        targetType.typeSymbol.caseFields.map { field =>
          field.name -> targetType.memberType(field)
        }.toMap
      } else Map.empty

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
    import q.reflect.*

    if (srcType.typeSymbol.flags.is(Flags.Case) && tgtType.typeSymbol.flags.is(Flags.Case)) {
      val srcFields = srcType.typeSymbol.caseFields.map(f => f.name -> srcType.memberType(f)).toMap
      val tgtFields = tgtType.typeSymbol.caseFields.map(f => f.name -> tgtType.memberType(f)).toMap

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
