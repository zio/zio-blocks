package zio.blocks.schema.migration

import scala.quoted._

/**
 * Compile-time macros for validating schema migrations.
 *
 * These macros extract field names from case class types and validate that
 * migrations are complete — all source fields are handled and all target fields
 * are provided.
 */
object MigrationValidationMacros {

  /**
   * Validates that a migration from A to B is complete given the tracked
   * fields.
   *
   * Auto-mapping: fields with the same name in both source and target are
   * automatically mapped and don't need explicit handling.
   */
  def validateMigration[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](using
    q: Quotes
  ): Expr[MigrationComplete[A, B, SH, TP]] = {
    import q.reflect._

    val sourceFields   = extractFieldNames[A]
    val targetFields   = extractFieldNames[B]
    val handledFields  = extractTupleElements[SH]
    val providedFields = extractTupleElements[TP]

    val autoMapped             = sourceFields.intersect(targetFields)
    val sourceNeedingHandling  = sourceFields.diff(autoMapped)
    val targetNeedingProviding = targetFields.diff(autoMapped)

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
           |Auto-mapped: ${autoMapped.mkString(", ")}
           |Handled: ${handledFields.mkString(", ")}
           |Provided: ${providedFields.mkString(", ")}""".stripMargin
      )
    }

    '{ MigrationComplete.unsafePartial[A, B, SH, TP] }
  }

  /** Extract field names from a case class type's primary constructor. */
  private def extractFieldNames[T: Type](using q: Quotes): Set[String] = {
    import q.reflect._

    val tpe    = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    if (!symbol.flags.is(Flags.Case)) Set.empty
    else
      symbol.primaryConstructor.paramSymss.flatten
        .filter(_.isValDef)
        .map(_.name)
        .toSet
  }

  /** Extract string literal elements from a Tuple type. */
  private def extractTupleElements[T <: Tuple: Type](using q: Quotes): Set[String] = {
    import q.reflect._

    def extract(tpe: TypeRepr): Set[String] = tpe.dealias match {
      case ConstantType(StringConstant(s))                                       => Set(s)
      case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.name == "*:" =>
        extract(head) ++ extract(tail)
      case AppliedType(tycon, args) if tycon.typeSymbol.name.startsWith("Tuple") =>
        args.flatMap(extract).toSet
      case tpe if tpe =:= TypeRepr.of[EmptyTuple] => Set.empty
      case _                                      => Set.empty
    }

    extract(TypeRepr.of[T])
  }

  /**
   * Extract the last field name from a selector lambda (e.g. `_.name` →
   * "name").
   */
  def extractFieldNameFromSelector[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): String = {
    import q.reflect._

    def extractLastField(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case Ident(_)             => report.errorAndAbort("Selector must access at least one field")
      case _                    => report.errorAndAbort(s"Cannot extract field name from: ${term.show}")
    }

    @scala.annotation.tailrec
    def getBody(term: Term): Term = term match {
      case Inlined(_, _, inner)                        => getBody(inner)
      case Block(List(DefDef(_, _, _, Some(body))), _) => body
      case Lambda(_, body)                             => body
      case _                                           => report.errorAndAbort(s"Expected lambda, got: ${term.show}")
    }

    extractLastField(getBody(selector.asTerm))
  }
}
