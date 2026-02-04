package zio.blocks.schema.migration

import scala.quoted.*

object MigrationValidationMacros {

  def validateMigration[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](using
    Quotes
  ): Expr[MigrationComplete[A, B, SH, TP]] = {
    import quotes.reflect.*

    val sourceFields   = extractFieldNames[A]
    val targetFields   = extractFieldNames[B]
    val handledFields  = extractTupleElements[SH]
    val providedFields = extractTupleElements[TP]

    val autoMapped = computeAutoMapped[A, B](sourceFields, targetFields)

    val coveredSource = handledFields ++ autoMapped
    val coveredTarget = providedFields ++ autoMapped

    val missingTarget   = targetFields -- coveredTarget
    val unhandledSource = sourceFields -- coveredSource

    if (missingTarget.nonEmpty || unhandledSource.nonEmpty) {
      val errors = new StringBuilder("Migration incomplete:\n")

      if (missingTarget.nonEmpty) {
        errors.append(s"\n  Target fields not provided: ${missingTarget.mkString(", ")}\n")
        errors.append("  Use addField, renameField, or transformField to provide these fields.\n")
      }

      if (unhandledSource.nonEmpty) {
        errors.append(s"\n  Source fields not handled: ${unhandledSource.mkString(", ")}\n")
        errors.append("  Use dropField, renameField, or transformField to handle these fields.\n")
      }

      errors.append("\n  Alternatively, use .buildPartial to skip validation.")

      report.errorAndAbort(errors.toString)
    }

    '{ MigrationComplete.unsafeCreate[A, B, SH, TP] }
  }

  private def extractFieldNames[T: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    if (tpe.typeSymbol.flags.is(Flags.Case) && tpe.typeSymbol.caseFields.nonEmpty) {
      tpe.typeSymbol.caseFields.map(_.name).toSet
    } else {
      Set.empty
    }
  }

  private def extractTupleElements[T <: Tuple: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    def extract(tpe: TypeRepr): Set[String] = {
      val dealiased = tpe.dealias
      dealiased match {
        case AppliedType(tycon, List(left, right))
            if tycon.typeSymbol.fullName == "scala.Tuple$package.Concat" ||
              tycon.typeSymbol.name == "Concat" =>
          extract(left) ++ extract(right)

        case AppliedType(tycon, args)
            if tycon.typeSymbol.fullName == "scala.Tuple$package.*:" ||
              tycon.typeSymbol.name == "*:" =>
          args match {
            case head :: tail :: Nil =>
              val headName = head.dealias match {
                case ConstantType(StringConstant(s)) => Set(s)
                case _                               => Set.empty[String]
              }
              headName ++ extract(tail)
            case _ => Set.empty
          }

        case tpe
            if tpe.typeSymbol.fullName == "scala.EmptyTuple" ||
              tpe.typeSymbol.fullName == "scala.Tuple$package.EmptyTuple" ||
              tpe.typeSymbol.name == "EmptyTuple" ||
              tpe =:= TypeRepr.of[EmptyTuple] =>
          Set.empty

        case AppliedType(tycon, args) if tycon.typeSymbol.fullName.startsWith("scala.Tuple") =>
          args.flatMap { arg =>
            arg.dealias match {
              case ConstantType(StringConstant(s)) => Some(s)
              case _                               => None
            }
          }.toSet

        case _ =>
          Set.empty
      }
    }

    extract(TypeRepr.of[T])
  }

  private def computeAutoMapped[A: Type, B: Type](sourceFields: Set[String], targetFields: Set[String])(using
    Quotes
  ): Set[String] = {
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

    val commonFields = sourceFields.intersect(targetFields)

    commonFields.filter { fieldName =>
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) =>
          srcType <:< tgtType || tgtType <:< srcType || srcType =:= tgtType
        case _ => false
      }
    }
  }
}
