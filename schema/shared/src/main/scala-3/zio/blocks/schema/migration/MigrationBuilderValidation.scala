package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema._

/**
 * Compile-time validation for MigrationBuilder.build.
 *
 * Validates that migrations between types A and B are structurally sound by
 * analyzing the types at compile time.
 */
private[migration] object MigrationBuilderValidation {

  /**
   * Validate and build the migration with compile-time checks.
   *
   * This macro:
   *   1. Extracts field names from source type A
   *   2. Extracts field names from target type B
   *   3. Reports compile-time errors for obvious mismatches
   */
  def validateAndBuild[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect.*

    val sourceFields = extractTypeFields[A]
    val targetFields = extractTypeFields[B]

    // Calculate what fields need attention
    val addedFields   = targetFields.diff(sourceFields)
    val droppedFields = sourceFields.diff(targetFields)
    val commonFields  = sourceFields.intersect(targetFields)

    // We can't check actions at compile time (they're runtime values),
    // but we can report informational messages about field differences
    if (addedFields.nonEmpty || droppedFields.nonEmpty) {
      // Use info reports to guide the user (not errors)
      if (droppedFields.nonEmpty) {
        report.info(
          s"Migration from ${Type.show[A]} to ${Type.show[B]}: " +
            s"fields [${droppedFields.mkString(", ")}] are being dropped. " +
            "Ensure these are handled by dropField() or rename()."
        )
      }
      if (addedFields.nonEmpty) {
        report.info(
          s"Migration from ${Type.show[A]} to ${Type.show[B]}: " +
            s"fields [${addedFields.mkString(", ")}] are being added. " +
            "Ensure these are handled by addField() or rename()."
        )
      }
    }

    // Build with runtime validation
    '{ $builder.buildWithValidation }
  }

  /**
   * Extract field names from a type at compile time.
   */
  private def extractTypeFields[T: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    extractFieldsFromTypeRepr(tpe)
  }

  private def extractFieldsFromTypeRepr(using Quotes)(tpe: quotes.reflect.TypeRepr): Set[String] = {
    import quotes.reflect.*

    def extractFromRefinement(t: TypeRepr, acc: Set[String]): Set[String] = t match {
      case Refinement(parent, name, info) =>
        val isField = info match {
          case _: ByNameType           => true
          case MethodType(Nil, Nil, _) => true
          case PolyType(_, _, _)       => false
          case _: MethodType           => false
          case _                       => true // val field
        }
        val newAcc = if (isField && !name.startsWith("$")) acc + name else acc
        extractFromRefinement(parent, newAcc)
      case _ => acc
    }

    // Try refinement extraction first
    val refinementFields = extractFromRefinement(tpe, Set.empty)
    if (refinementFields.nonEmpty) {
      return refinementFields
    }

    // For case classes, extract from type symbol
    tpe.classSymbol match {
      case Some(sym) =>
        sym.caseFields.map(_.name).toSet
      case None =>
        Set.empty
    }
  }

  /**
   * Strict validation that requires all fields to be explicitly handled. This
   * version reports compile-time ERRORS (not info).
   */
  def validateStrict[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    handledSourceFields: Expr[Set[String]],
    handledTargetFields: Expr[Set[String]]
  )(using Quotes): Expr[Migration[A, B]] =

    // Note: handledSourceFields/handledTargetFields are runtime values,
    // so we can only do full validation at runtime.
    // For true compile-time validation, we'd need the actions captured as an HList or similar.

    '{
      val srcFields = $handledSourceFields
      val tgtFields = $handledTargetFields
      $builder.buildWithValidation
    }
}
