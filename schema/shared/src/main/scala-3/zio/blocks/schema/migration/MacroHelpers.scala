package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Shared macro helper functions for compile-time type introspection.
 */
private[migration] object MacroHelpers {

  /**
   * Check if a type is a primitive type that should not be recursed into.
   */
  def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
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

  /**
   * Check if a type is a container type (Option, List, Vector, Set, Map, etc.)
   * that should not be recursed into.
   */
  def isContainerType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
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

  /**
   * Check if a type is a product type (case class, but not abstract).
   */
  def isProductType(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
  }

  /**
   * Get the fields of a product type as (name, type) pairs.
   */
  def getProductFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr)] = {
    val symbol = tpe.typeSymbol

    // Get primary constructor
    val constructor = symbol.primaryConstructor
    if (constructor.isNoSymbol) return Nil

    // Get constructor parameter lists
    val paramLists = constructor.paramSymss

    // Filter to term parameters (not type parameters)
    val termParams = paramLists.flatten.filter(_.isTerm)

    termParams.map { param =>
      val paramName = param.name
      val paramType = tpe.memberType(param)
      (paramName, paramType.dealias)
    }
  }

  /**
   * Check if a type is a sealed trait, abstract class, or enum.
   */
  def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      (flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))) ||
      flags.is(Flags.Enum)
    }
  }

  /**
   * Check if a type is an enum value (like `case Red` in an enum).
   */
  def isEnumValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.termSymbol.flags.is(Flags.Enum)
  }

  /**
   * Get the name of a case from its type. Handles both regular case classes and
   * enum values.
   */
  def getCaseName(using q: Quotes)(tpe: q.reflect.TypeRepr): String =
    // For enum values (simple cases like `case Red`), use termSymbol
    if (isEnumValue(tpe)) {
      tpe.termSymbol.name
    } else {
      tpe.typeSymbol.name
    }

  /**
   * Get the direct subtypes of a sealed trait or enum.
   */
  def directSubTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect.*

    val symbol   = tpe.typeSymbol
    val children = symbol.children

    children.map { child =>
      if (child.isType) {
        child.typeRef
      } else {
        // For enum values (object cases)
        Ref(child).tpe
      }
    }
  }

  /**
   * Extract all field paths from a type, recursively descending into nested
   * case classes.
   *
   * @param tpe
   *   The type to extract paths from
   * @param prefix
   *   The current path prefix (e.g., "address." for nested fields)
   * @param visiting
   *   Set of type full names currently being visited (for recursion detection)
   * @param errorContext
   *   Context string for error messages (e.g., "Migration validation" or "Shape
   *   extraction")
   * @return
   *   List of dot-separated field paths
   */
  def extractFieldPathsFromType(using
    q: Quotes
  )(
    tpe: q.reflect.TypeRepr,
    prefix: String,
    visiting: Set[String],
    errorContext: String = "Migration validation"
  ): List[String] = {
    import q.reflect.*

    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion
    if (visiting.contains(typeKey)) {
      report.errorAndAbort(
        s"Recursive type detected: ${dealiased.show}. " +
          s"$errorContext does not support recursive types. " +
          s"Recursion path: ${visiting.mkString(" -> ")} -> $typeKey"
      )
    }

    // Skip extraction for container types and primitives
    if (isContainerType(dealiased) || isPrimitiveType(dealiased)) {
      return Nil
    }

    // Only extract fields from product types (case classes)
    if (!isProductType(dealiased.typeSymbol)) {
      return Nil
    }

    val newVisiting = visiting + typeKey
    val fields      = getProductFields(dealiased)

    fields.flatMap { case (fieldName, fieldType) =>
      val fullPath    = if (prefix.isEmpty) fieldName else s"$prefix$fieldName"
      val nestedPaths = extractFieldPathsFromType(fieldType, s"$fullPath.", newVisiting, errorContext)
      fullPath :: nestedPaths
    }
  }

  /**
   * Extract case names from a sealed trait or enum.
   */
  def extractCaseNamesFromType(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTraitOrEnum(dealiased)) {
      val subTypes = directSubTypes(dealiased)
      subTypes.map(getCaseName)
    } else {
      Nil
    }
  }
}
