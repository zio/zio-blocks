package zio.blocks.schema.migration

import scala.compiletime.{summonFrom, summonInline}
import scala.quoted.*
import zio.blocks.schema.migration.TypeLevel._
import zio.blocks.schema.migration.FieldExtraction._

/**
 * Compile-time proof that a migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All "removed" field paths (in A but not in B) are handled
 *   - All "added" field paths (in B but not in A) are provided
 *   - All "removed" case names (in A but not in B) are handled
 *   - All "added" case names (in B but not in A) are provided
 *
 * Paths that exist in both A and B (unchanged paths) are automatically
 * handled/provided. This includes nested paths like "address.street" and
 * case names like "case:Success".
 *
 * Type parameters:
 *   - A: Source type
 *   - B: Target type
 *   - Handled: Tuple of field paths and case names that have been handled (from
 *     source). Case names are prefixed with "case:" (e.g., "case:OldCase").
 *   - Provided: Tuple of field paths and case names that have been provided
 *     (for target). Case names are prefixed with "case:" (e.g., "case:NewCase").
 *
 * == Error Messages ==
 *
 * When validation fails, the compiler produces a detailed error message showing:
 *   - Unhandled field paths from the source type
 *   - Unprovided field paths for the target type
 *   - Unhandled case names from the source type
 *   - Unprovided case names for the target type
 *   - Hints for which builder methods to use
 *
 * Example error:
 * {{{
 * Migration validation failed for PersonV1 => PersonV2:
 *
 * Unhandled paths from source (need dropField or renameField):
 *   - address.city
 *
 * Unprovided paths for target (need addField or renameField):
 *   - address.zip
 *
 * Hint: Use .dropField(_.address.city, default) to handle removed fields
 * Hint: Use .addField(_.address.zip, default) to provide new fields
 * }}}
 */
sealed trait ValidationProof[A, B, Handled <: Tuple, Provided <: Tuple]

object ValidationProof {

  /**
   * Implementation class for ValidationProof. Public because transparent inline
   * given instances require it.
   */
  final class Impl[A, B, Handled <: Tuple, Provided <: Tuple] extends ValidationProof[A, B, Handled, Provided]

  /**
   * Type alias for required paths to handle (paths in A that are not in B).
   */
  type RequiredHandled[A, B, PathsA <: Tuple, PathsB <: Tuple] =
    Difference[PathsA, PathsB]

  /**
   * Type alias for required paths to provide (paths in B that are not in A).
   */
  type RequiredProvided[A, B, PathsA <: Tuple, PathsB <: Tuple] =
    Difference[PathsB, PathsA]

  /**
   * Concatenate two tuples at the type level.
   */
  type Concat[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple => B
    case h *: t     => h *: Concat[t, B]
  }

  /**
   * Derive a ValidationProof when the migration is complete.
   *
   * This given instance only exists when:
   *   - All removed field paths are in Handled
   *   - All added field paths are in Provided
   *   - All removed case names are in Handled (prefixed with "case:")
   *   - All added case names are in Provided (prefixed with "case:")
   *
   * Uses FieldPaths to extract full nested paths (e.g., "address.street")
   * and CasePaths to extract case names (e.g., "case:Success").
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B],
    cpA: CasePaths[A],
    cpB: CasePaths[B]
  ): ValidationProof[A, B, Handled, Provided] =
    // Validate field paths
    summonFrom { case _: (IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true) =>
      summonFrom { case _: (IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true) =>
        // Validate case names
        summonFrom { case _: (IsSubset[Difference[cpA.Cases, cpB.Cases], Handled] =:= true) =>
          summonFrom { case _: (IsSubset[Difference[cpB.Cases, cpA.Cases], Provided] =:= true) =>
            new Impl[A, B, Handled, Provided]
          }
        }
      }
    }

  /**
   * Helper type to compute whether a migration is valid. Checks both field
   * paths and case names.
   */
  type IsValid[
    A,
    B,
    Handled <: Tuple,
    Provided <: Tuple,
    PathsA <: Tuple,
    PathsB <: Tuple,
    CasesA <: Tuple,
    CasesB <: Tuple
  ] =
    (
      IsSubset[Difference[PathsA, PathsB], Handled],
      IsSubset[Difference[PathsB, PathsA], Provided],
      IsSubset[Difference[CasesA, CasesB], Handled],
      IsSubset[Difference[CasesB, CasesA], Provided]
    ) match {
      case (true, true, true, true) => true
      case _                        => false
    }

  /**
   * Explicitly summon a validation proof with better error messages.
   *
   * This is an alternative way to get a proof that provides more helpful
   * compile error messages when validation fails. Use this via the
   * `requireValidation` macro for the best error messages.
   */
  inline def require[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B],
    cpA: CasePaths[A],
    cpB: CasePaths[B]
  ): ValidationProof[A, B, Handled, Provided] = {
    summonInline[IsSubset[Difference[fpA.Paths, fpB.Paths], Handled] =:= true]
    summonInline[IsSubset[Difference[fpB.Paths, fpA.Paths], Provided] =:= true]
    summonInline[IsSubset[Difference[cpA.Cases, cpB.Cases], Handled] =:= true]
    summonInline[IsSubset[Difference[cpB.Cases, cpA.Cases], Provided] =:= true]
    new Impl[A, B, Handled, Provided]
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
   *
   * Use this when you want clear feedback about what's missing in a migration.
   */
  inline def requireValidation[A, B, Handled <: Tuple, Provided <: Tuple]: ValidationProof[A, B, Handled, Provided] =
    ${ requireValidationImpl[A, B, Handled, Provided] }

  private def requireValidationImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](using
    q: Quotes
  ): Expr[ValidationProof[A, B, Handled, Provided]] = {
    import q.reflect.*

    // Extract paths from source type A
    val pathsA = extractFieldPathsForValidation(TypeRepr.of[A])
    // Extract paths from target type B
    val pathsB = extractFieldPathsForValidation(TypeRepr.of[B])
    // Extract cases from source type A
    val casesA = extractCaseNamesForValidation(TypeRepr.of[A])
    // Extract cases from target type B
    val casesB = extractCaseNamesForValidation(TypeRepr.of[B])

    // Extract handled and provided from tuple types
    val handled  = extractTupleStrings(TypeRepr.of[Handled])
    val provided = extractTupleStrings(TypeRepr.of[Provided])

    // Compute what's missing
    val requiredHandledFields  = pathsA.diff(pathsB)
    val requiredProvidedFields = pathsB.diff(pathsA)
    val requiredHandledCases   = casesA.diff(casesB)
    val requiredProvidedCases  = casesB.diff(casesA)

    val unhandledFields   = requiredHandledFields.diff(handled)
    val unprovidedFields  = requiredProvidedFields.diff(provided)
    val unhandledCases    = requiredHandledCases.diff(handled)
    val unprovidedCases   = requiredProvidedCases.diff(provided)

    // If there are issues, generate detailed error message
    if (unhandledFields.nonEmpty || unprovidedFields.nonEmpty || unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
      val sourceTypeName = TypeRepr.of[A].typeSymbol.name
      val targetTypeName = TypeRepr.of[B].typeSymbol.name

      val sb = new StringBuilder
      sb.append(s"Migration validation failed for $sourceTypeName => $targetTypeName:\n")

      if (unhandledFields.nonEmpty) {
        sb.append("\nUnhandled paths from source (need dropField or renameField):\n")
        unhandledFields.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unprovidedFields.nonEmpty) {
        sb.append("\nUnprovided paths for target (need addField or renameField):\n")
        unprovidedFields.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unhandledCases.nonEmpty) {
        sb.append("\nUnhandled cases from source (need renameCase or transformCase):\n")
        unhandledCases.sorted.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("\nUnprovided cases for target (need renameCase):\n")
        unprovidedCases.sorted.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
      }

      // Add hints
      sb.append("\n")
      if (unhandledFields.nonEmpty) {
        val example = unhandledFields.head
        val selectorPath = example.split("\\.").mkString("_.")
        sb.append(s"Hint: Use .dropField(_.$selectorPath, default) to handle removed fields\n")
      }
      if (unprovidedFields.nonEmpty) {
        val example = unprovidedFields.head
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
   * Extract field paths from a type for validation error messages.
   * This mirrors the logic in FieldPaths but returns a runtime List[String].
   */
  private def extractFieldPathsForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extract(t: TypeRepr, prefix: String, visiting: Set[String]): List[String] = {
      val dealiased = t.dealias
      val typeKey   = dealiased.typeSymbol.fullName

      if (visiting.contains(typeKey)) return Nil // Recursion - stop
      if (isContainerTypeForValidation(dealiased)) return Nil
      if (isPrimitiveTypeForValidation(dealiased)) return Nil
      if (!isProductTypeForValidation(dealiased.typeSymbol)) return Nil

      val newVisiting = visiting + typeKey
      val fields      = getProductFieldsForValidation(dealiased)

      fields.flatMap { case (fieldName, fieldType) =>
        val fullPath    = if (prefix.isEmpty) fieldName else s"$prefix$fieldName"
        val nestedPaths = extract(fieldType, s"$fullPath.", newVisiting)
        fullPath :: nestedPaths
      }
    }

    extract(tpe, "", Set.empty).sorted
  }

  /**
   * Extract case names from a type for validation error messages.
   */
  private def extractCaseNamesForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTraitOrEnumForValidation(dealiased)) {
      val symbol   = dealiased.typeSymbol
      val children = symbol.children
      children.map { child =>
        val name =
          if (child.isType) child.name
          else child.name
        s"case:$name"
      }.sorted
    } else {
      Nil
    }
  }

  /**
   * Extract string literals from a Tuple type.
   * Handles both Tuple1/Tuple2/etc. syntax and *: syntax.
   */
  private def extractTupleStrings(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extractString(t: TypeRepr): Option[String] = t.dealias match {
      case ConstantType(StringConstant(s)) => Some(s)
      case _                               => None
    }

    def extract(t: TypeRepr): List[String] = {
      val dealiased = t.dealias
      dealiased match {
        // Handle *: syntax: head *: tail
        case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
          extractString(head).toList ::: extract(tail)
        // Handle Tuple1, Tuple2, etc. - extract all type args as strings
        case AppliedType(tycon, args) if tycon.typeSymbol.fullName.startsWith("scala.Tuple") =>
          args.flatMap(extractString)
        // Handle EmptyTuple
        case _ if dealiased =:= TypeRepr.of[EmptyTuple] =>
          Nil
        case _ =>
          Nil
      }
    }

    extract(tpe)
  }

  // Helper methods for validation - mirror those in FieldPaths

  private def isContainerTypeForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    val containerTypes = List(
      TypeRepr.of[Option[?]],
      TypeRepr.of[List[?]],
      TypeRepr.of[Vector[?]],
      TypeRepr.of[Set[?]],
      TypeRepr.of[Seq[?]],
      TypeRepr.of[IndexedSeq[?]],
      TypeRepr.of[Iterable[?]],
      TypeRepr.of[Map[?, ?]],
      TypeRepr.of[Array[?]]
    )

    containerTypes.exists(ct => tpe <:< ct)
  }

  private def isPrimitiveTypeForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    val primitiveTypes = List(
      TypeRepr.of[Boolean],
      TypeRepr.of[Byte],
      TypeRepr.of[Short],
      TypeRepr.of[Int],
      TypeRepr.of[Long],
      TypeRepr.of[Float],
      TypeRepr.of[Double],
      TypeRepr.of[Char],
      TypeRepr.of[String],
      TypeRepr.of[java.math.BigInteger],
      TypeRepr.of[java.math.BigDecimal],
      TypeRepr.of[BigInt],
      TypeRepr.of[BigDecimal],
      TypeRepr.of[java.util.UUID],
      TypeRepr.of[java.time.Instant],
      TypeRepr.of[java.time.LocalDate],
      TypeRepr.of[java.time.LocalTime],
      TypeRepr.of[java.time.LocalDateTime],
      TypeRepr.of[java.time.OffsetDateTime],
      TypeRepr.of[java.time.ZonedDateTime],
      TypeRepr.of[java.time.Duration],
      TypeRepr.of[java.time.Period],
      TypeRepr.of[java.time.Year],
      TypeRepr.of[java.time.YearMonth],
      TypeRepr.of[java.time.MonthDay],
      TypeRepr.of[java.time.ZoneId],
      TypeRepr.of[java.time.ZoneOffset],
      TypeRepr.of[Unit],
      TypeRepr.of[Nothing]
    )

    primitiveTypes.exists(pt => tpe =:= pt)
  }

  private def isProductTypeForValidation(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
  }

  private def isSealedTraitOrEnumForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
  import q.reflect.* 

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      (flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))) ||
      flags.is(Flags.Enum)
    }
  }

  private def getProductFieldsForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr)] = {

    val symbol = tpe.typeSymbol

    val constructor = symbol.primaryConstructor
    if (constructor.isNoSymbol) return Nil

    val paramLists = constructor.paramSymss
    val termParams = paramLists.flatten.filter(_.isTerm)

    termParams.map { param =>
      val paramName = param.name
      val paramType = tpe.memberType(param)
      (paramName, paramType.dealias)
    }
  }
}
