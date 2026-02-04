package zio.blocks.schema.migration

import scala.quoted._

/**
 * Compile-time macros for validating schema migrations.
 *
 * These macros extract field names from types and validate that migrations are
 * complete (all source fields handled, all target fields provided).
 */
object MigrationValidationMacros {

  /**
   * Validates that a migration is complete.
   *
   * A migration is complete when:
   *   - All source fields are handled (renamed, dropped, transformed) OR
   *     auto-mapped
   *   - All target fields are provided (added, renamed to) OR auto-mapped
   *
   * Auto-mapping: fields with the same name in source and target are
   * automatically mapped.
   */
  def validateMigration[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](using
    q: Quotes
  ): Expr[MigrationComplete[A, B, SH, TP]] = {
    import q.reflect._

    val sourceFields   = extractFieldNames[A]
    val targetFields   = extractFieldNames[B]
    val handledFields  = extractTupleElements[SH]
    val providedFields = extractTupleElements[TP]

    // Auto-mapped fields are those with the same name in both source and target
    val autoMapped = sourceFields.intersect(targetFields)

    // Source fields that need handling: all source fields minus auto-mapped
    val sourceNeedingHandling = sourceFields.diff(autoMapped)
    // Target fields that need providing: all target fields minus auto-mapped
    val targetNeedingProviding = targetFields.diff(autoMapped)

    // Check which required fields are missing
    val unhandledSource  = sourceNeedingHandling.diff(handledFields)
    val unprovidedTarget = targetNeedingProviding.diff(providedFields)

    if (unhandledSource.nonEmpty || unprovidedTarget.nonEmpty) {
      val errors = List(
        if (unhandledSource.nonEmpty)
          Some(s"Unhandled source fields: ${unhandledSource.mkString(", ")}")
        else None,
        if (unprovidedTarget.nonEmpty)
          Some(s"Unprovided target fields: ${unprovidedTarget.mkString(", ")}")
        else None
      ).flatten.mkString("; ")

      val hints = List(
        if (unhandledSource.nonEmpty)
          Some(s"Use .dropField or .renameField for: ${unhandledSource.mkString(", ")}")
        else None,
        if (unprovidedTarget.nonEmpty)
          Some(s"Use .addField or .renameField for: ${unprovidedTarget.mkString(", ")}")
        else None
      ).flatten.mkString("; ")

      report.errorAndAbort(
        s"""Migration is incomplete.
           |
           |$errors
           |
           |Hints: $hints
           |
           |Source fields: ${sourceFields.mkString(", ")}
           |Target fields: ${targetFields.mkString(", ")}
           |Auto-mapped fields: ${autoMapped.mkString(", ")}
           |Handled fields: ${handledFields.mkString(", ")}
           |Provided fields: ${providedFields.mkString(", ")}
           |""".stripMargin
      )
    }

    // Validation passed - return the instance
    '{ MigrationComplete.unsafePartial[A, B, SH, TP] }
  }

  /**
   * Extract field names from a case class type.
   */
  private def extractFieldNames[T: Type](using q: Quotes): Set[String] = {
    import q.reflect._

    val tpe    = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    // Check if it's a case class
    if (!symbol.flags.is(Flags.Case)) {
      // Not a case class - might be a primitive or other type
      Set.empty
    } else {
      // Get the primary constructor parameters (case class fields)
      symbol.primaryConstructor.paramSymss.flatten
        .filter(_.isValDef)
        .map(_.name)
        .toSet
    }
  }

  /**
   * Extract string literal elements from a tuple type.
   *
   * For example, `("a", "b", "c")` -> `Set("a", "b", "c")`
   */
  private def extractTupleElements[T <: Tuple: Type](using q: Quotes): Set[String] = {
    import q.reflect._

    def extractFromType(tpe: TypeRepr): Set[String] = tpe.dealias match {
      case ConstantType(StringConstant(s)) =>
        Set(s)

      case AppliedType(tycon, args) if tycon.typeSymbol.name.startsWith("Tuple") =>
        args.flatMap(extractFromType).toSet

      case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.name == "*:" =>
        extractFromType(head) ++ extractFromType(tail)

      case tpe if tpe =:= TypeRepr.of[EmptyTuple] =>
        Set.empty

      case other =>
        // Try to match literal type
        other match {
          case ConstantType(StringConstant(s)) => Set(s)
          case _                               => Set.empty
        }
    }

    extractFromType(TypeRepr.of[T])
  }

  /**
   * Extract a field name from a selector term at compile time.
   *
   * Used by MigrationBuilderMacros to get field names for type-level tracking.
   */
  def extractFieldNameFromSelector[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): String = {
    import q.reflect._

    def extractLastFieldName(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case Ident(_)             => report.errorAndAbort("Selector must access at least one field")
      case _                    => report.errorAndAbort(s"Cannot extract field name from: ${term.show}")
    }

    @scala.annotation.tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    val pathBody = toPathBody(selector.asTerm)
    extractLastFieldName(pathBody)
  }
}
