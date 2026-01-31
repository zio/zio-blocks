package zio.blocks.schema.migration

import scala.compiletime.summonFrom
import scala.quoted.*
import zio.blocks.schema.migration.TypeLevel._
import zio.blocks.schema.migration.ShapeExtraction._

/**
 * Compile-time proof that a migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All "removed" paths (in A but not in B, or type changed) are handled
 *   - All "added" paths (in B but not in A, or type changed) are provided
 *
 * The diff is computed using ShapeTree extraction and TreeDiff, which:
 *   - Handles both field paths and case names uniformly
 *   - Properly detects type changes (same path, different structure)
 *   - Supports nested structures including containers
 *
 * Paths that exist in both A and B with the same structure are automatically
 * handled/provided and don't require explicit handling.
 *
 * Type parameters:
 *   - A: Source type
 *   - B: Target type
 *   - Handled: Tuple of structured path tuples that have been handled.
 *     Field paths: (("field", "address"), ("field", "city"))
 *     Case paths: (("case", "CaseName"),)
 *   - Provided: Tuple of structured path tuples that have been provided.
 */
sealed trait ValidationProof[A, B, Handled <: Tuple, Provided <: Tuple]

object ValidationProof {

  /**
   * Implementation class for ValidationProof. Public because transparent inline
   * given instances require it.
   */
  final class Impl[A, B, Handled <: Tuple, Provided <: Tuple] extends ValidationProof[A, B, Handled, Provided]

  /**
   * Derive a ValidationProof when the migration is complete.
   *
   * This given instance only exists when:
   *   - All removed paths (in A but not B, or type changed) are in Handled
   *   - All added paths (in B but not A, or type changed) are in Provided
   *
   * Uses MigrationPaths which computes the diff using ShapeTree and TreeDiff.
   * This approach handles both field paths and case names uniformly, and properly
   * detects type changes (same path, different structure).
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    mp: MigrationPaths[A, B]
  ): ValidationProof[A, B, Handled, Provided] =
    summonFrom { case _: (IsSubset[mp.Removed, Handled] =:= true) =>
      summonFrom { case _: (IsSubset[mp.Added, Provided] =:= true) =>
        new Impl[A, B, Handled, Provided]
      }
    }

  /**
   * Validate a migration with detailed error messages.
   *
   * This macro provides helpful compile-time error messages when validation
   * fails, including:
   *   - Specific field paths that need handling
   *   - Specific field paths that need providing
   *   - Specific case names that need handling
   *   - Specific case names that need providing
   *   - Hints for which builder methods to use
   */
  inline def requireValidation[A, B, Handled <: Tuple, Provided <: Tuple]: ValidationProof[A, B, Handled, Provided] =
    ${ requireValidationImpl[A, B, Handled, Provided] }

  private def requireValidationImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](using
    q: Quotes
  ): Expr[ValidationProof[A, B, Handled, Provided]] = {
    import q.reflect.*

    // Extract shape trees for both types
    val tpeA  = TypeRepr.of[A].dealias
    val tpeB  = TypeRepr.of[B].dealias
    val treeA = MacroHelpers.extractShapeTree(tpeA, Set.empty, "Migration validation")
    val treeB = MacroHelpers.extractShapeTree(tpeB, Set.empty, "Migration validation")

    // Compute diff using TreeDiff
    val (removed, added) = TreeDiff.diff(treeA, treeB)

    // Convert paths to flat strings for comparison with Handled/Provided
    val removedStrings = removed.map(MigrationPaths.pathToFlatString).sorted
    val addedStrings   = added.map(MigrationPaths.pathToFlatString).sorted

    // Extract handled and provided from tuple types
    val handled  = extractTupleStrings(TypeRepr.of[Handled])
    val provided = extractTupleStrings(TypeRepr.of[Provided])

    // Compute what's missing
    val unhandled  = removedStrings.diff(handled)
    val unprovided = addedStrings.diff(provided)

    // Categorize unhandled/unprovided into fields and cases
    val unhandledFields = unhandled.filterNot(_.startsWith("case:"))
    val unhandledCases  = unhandled.filter(_.startsWith("case:"))
    val unprovidedFields = unprovided.filterNot(_.startsWith("case:"))
    val unprovidedCases  = unprovided.filter(_.startsWith("case:"))

    // If there are issues, generate detailed error message
    if (unhandled.nonEmpty || unprovided.nonEmpty) {
      val sourceTypeName = TypeRepr.of[A].typeSymbol.name
      val targetTypeName = TypeRepr.of[B].typeSymbol.name

      val sb = new StringBuilder
      sb.append(s"Migration validation failed for $sourceTypeName => $targetTypeName:\n")

      if (unhandledFields.nonEmpty) {
        sb.append("\nUnhandled paths from source (need dropField, renameField, or transformField):\n")
        unhandledFields.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unprovidedFields.nonEmpty) {
        sb.append("\nUnprovided paths for target (need addField or renameField):\n")
        unprovidedFields.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unhandledCases.nonEmpty) {
        sb.append("\nUnhandled cases from source (need renameCase or transformCase):\n")
        unhandledCases.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("\nUnprovided cases for target (need renameCase):\n")
        unprovidedCases.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
      }

      // Add hints
      sb.append("\n")
      if (unhandledFields.nonEmpty) {
        val example      = unhandledFields.head
        val selectorPath = example.split("\\.").mkString("_.")
        sb.append(s"Hint: Use .dropField(_.$selectorPath, default) to handle removed fields\n")
      }
      if (unprovidedFields.nonEmpty) {
        val example      = unprovidedFields.head
        val selectorPath = example.split("\\.").mkString("_.")
        sb.append(s"Hint: Use .addField(_.$selectorPath, default) to provide new fields\n")
      }
      if (unhandledFields.nonEmpty && unprovidedFields.nonEmpty) {
        sb.append("Hint: Use .renameField(_.oldPath, _.newPath) when a field was renamed\n")
      }
      if (unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
        sb.append("Hint: Use .renameCase(_.when[OldCase], \"NewCase\") when a case was renamed\n")
      }

      report.errorAndAbort(sb.toString)
    }

    // Validation passed
    '{ new ValidationProof.Impl[A, B, Handled, Provided] }
  }

  /**
   * Extract structured path tuples from a Tuple type and convert them to flat strings.
   *
   * Each path in the tuple is a structured tuple like (("field", "address"), ("field", "city"))
   * which gets converted to "address.city" for comparison and error messages.
   */
  private def extractTupleStrings(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    /**
     * Extract a single path tuple and convert it to a flat string.
     * Path format: (("field", "address"), ("field", "city")) -> "address.city"
     *              (("case", "Success"),) -> "case:Success"
     */
    def pathTupleToString(pathType: TypeRepr): Option[String] = {
      val segments = extractPathSegments(pathType)
      if (segments.isEmpty) None
      else Some(segments.mkString("."))
    }

    /**
     * Extract segments from a path tuple type.
     * Each segment is either:
     * - ("field", "name") -> "name"
     * - ("case", "name") -> "case:name"
     * - "element" -> "element"
     * - etc.
     */
    def extractPathSegments(pathType: TypeRepr): List[String] = {
      val dealiased = pathType.dealias
      dealiased match {
        // Handle *: syntax for path segments
        case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
          segmentToString(head).toList ::: extractPathSegments(tail)
        // Handle EmptyTuple
        case _ if dealiased =:= TypeRepr.of[EmptyTuple] =>
          Nil
        // Handle Tuple2 for segment pairs (already a segment, not a path)
        case AppliedType(tycon, List(_, _))
            if tycon.typeSymbol.fullName == "scala.Tuple2" =>
          segmentToString(dealiased).toList
        case _ =>
          Nil
      }
    }

    /**
     * Convert a segment type to a string.
     * ("field", "name") -> "name"
     * ("case", "name") -> "case:name"
     * "element" literal -> "element"
     */
    def segmentToString(segType: TypeRepr): Option[String] = {
      val dealiased = segType.dealias
      dealiased match {
        // Handle ("field", "name") or ("case", "name") Tuple2
        case AppliedType(tycon, List(kindType, nameType)) if tycon.typeSymbol.fullName == "scala.Tuple2" =>
          (extractStringLiteral(kindType), extractStringLiteral(nameType)) match {
            case (Some("field"), Some(name)) => Some(name)
            case (Some("case"), Some(name))  => Some(s"case:$name")
            case _                           => None
          }
        // Handle single string literals like "element", "key", "value", "wrapped"
        case ConstantType(StringConstant(s)) =>
          Some(s)
        case _ =>
          None
      }
    }

    /**
     * Extract a string literal from a type.
     */
    def extractStringLiteral(t: TypeRepr): Option[String] = t.dealias match {
      case ConstantType(StringConstant(s)) => Some(s)
      case _                               => None
    }

    /**
     * Extract all paths from the Handled/Provided tuple.
     */
    def extractPaths(t: TypeRepr): List[String] = {
      val dealiased = t.dealias
      dealiased match {
        // Handle *: syntax: head *: tail (where head is a path tuple)
        case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
          pathTupleToString(head).toList ::: extractPaths(tail)
        // Handle EmptyTuple
        case _ if dealiased =:= TypeRepr.of[EmptyTuple] =>
          Nil
        // Handle Tuple.Append type (need to simplify)
        case AppliedType(tycon, _) if tycon.typeSymbol.fullName.contains("Tuple") =>
          // Try to extract from simplified/widened type
          extractPaths(dealiased.simplified)
        case _ =>
          Nil
      }
    }

    extractPaths(tpe)
  }
}
