package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Compile-time shape extraction for migration validation.
 *
 * Extracts the complete structure of a type at compile time, including:
 *   - All field paths (including nested paths like "address.street")
 *   - Sealed trait/enum case names
 *   - Field paths within each case
 *
 * This enables validation that migrations handle all structural changes between
 * source and target types.
 */
object ShapeExtraction {

  /**
   * Represents the complete shape of a type.
   *
   * @param fieldPaths
   *   All dot-separated field paths in sorted order (e.g., List("address",
   *   "address.city", "address.street", "name"))
   * @param caseNames
   *   Case names for sealed traits/enums in sorted order (e.g., List("Failure",
   *   "Success"))
   * @param caseFieldPaths
   *   Field paths for each case, keyed by case name (e.g., Map("Success" ->
   *   List("value"), "Failure" -> List("error")))
   */
  case class Shape(
    fieldPaths: List[String],
    caseNames: List[String],
    caseFieldPaths: Map[String, List[String]]
  )

  /**
   * Extract all field paths from a type at compile time.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * extractFieldPaths[Person]
   * // Returns: List("address", "address.city", "address.street", "name")
   * }}}
   *
   * For recursive types, produces a compile-time error.
   */
  inline def extractFieldPaths[A]: List[String] = ${ extractFieldPathsMacro[A] }

  /**
   * Extract case names from a sealed trait or enum at compile time.
   *
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * extractCaseNames[Result]
   * // Returns: List("Failure", "Success")
   * }}}
   *
   * For non-sealed types, returns an empty list.
   */
  inline def extractCaseNames[A]: List[String] = ${ extractCaseNamesMacro[A] }

  /**
   * Extract the complete shape of a type at compile time.
   *
   * Combines field paths, case names, and case field paths into a single Shape
   * object. For sealed traits, extracts both the case names and the field paths
   * within each case.
   *
   * {{{
   * sealed trait Payment
   * case class Card(number: String, expiry: String) extends Payment
   * case class Cash(amount: Int) extends Payment
   *
   * extractShape[Payment]
   * // Returns: Shape(
   * //   fieldPaths = List(),
   * //   caseNames = List("Card", "Cash"),
   * //   caseFieldPaths = Map(
   * //     "Card" -> List("expiry", "number"),
   * //     "Cash" -> List("amount")
   * //   )
   * // )
   * }}}
   */
  inline def extractShape[A]: Shape = ${ extractShapeMacro[A] }

  // ============ Macro Implementations ============

  private def extractFieldPathsMacro[A: Type](using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val tpe    = TypeRepr.of[A].dealias
    val paths  = extractFieldPathsFromType(tpe, "", Set.empty)
    val sorted = paths.sorted
    Expr(sorted)
  }

  private def extractCaseNamesMacro[A: Type](using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val names = extractCaseNamesFromType(tpe)
    Expr(names.sorted)
  }

  private def extractShapeMacro[A: Type](using q: Quotes): Expr[Shape] = {
    import q.reflect.*

    val tpe = TypeRepr.of[A].dealias

    // Extract field paths (for product types)
    val fieldPaths = extractFieldPathsFromType(tpe, "", Set.empty).sorted

    // Extract case names (for sum types)
    val caseNames = extractCaseNamesFromType(tpe).sorted

    // Extract field paths for each case
    val caseFieldPaths: Map[String, List[String]] =
      if (isSealedTraitOrEnum(tpe)) {
        val subTypes = directSubTypes(tpe)
        subTypes.map { subTpe =>
          val caseName  = getCaseName(subTpe)
          val casePaths = extractFieldPathsFromType(subTpe, "", Set.empty).sorted
          caseName -> casePaths
        }.toMap
      } else {
        Map.empty
      }

    val fieldPathsExpr     = Expr(fieldPaths)
    val caseNamesExpr      = Expr(caseNames)
    val caseFieldPathsExpr = Expr(caseFieldPaths)

    '{ Shape($fieldPathsExpr, $caseNamesExpr, $caseFieldPathsExpr) }
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
   * @return
   *   List of dot-separated field paths
   */
  private def extractFieldPathsFromType(using
    q: Quotes
  )(tpe: q.reflect.TypeRepr, prefix: String, visiting: Set[String]): List[String] = {
    import q.reflect.*

    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion
    if (visiting.contains(typeKey)) {
      report.errorAndAbort(
        s"Recursive type detected: ${dealiased.show}. " +
          s"Migration shape extraction does not support recursive types. " +
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
      val nestedPaths = extractFieldPathsFromType(fieldType, s"$fullPath.", newVisiting)
      fullPath :: nestedPaths
    }
  }

  /**
   * Extract case names from a sealed trait or enum.
   */
  private def extractCaseNamesFromType(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTraitOrEnum(dealiased)) {
      val subTypes = directSubTypes(dealiased)
      subTypes.map(getCaseName)
    } else {
      Nil
    }
  }

  /**
   * Get the name of a case from its type. Handles both regular case classes and
   * enum values.
   */
  private def getCaseName(using q: Quotes)(tpe: q.reflect.TypeRepr): String =
    // For enum values (simple cases like `case Red`), use termSymbol
    if (isEnumValue(tpe)) {
      tpe.termSymbol.name
    } else {
      tpe.typeSymbol.name
    }

  /**
   * Check if a type is an enum value (like `case Red` in an enum).
   */
  private def isEnumValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    tpe.termSymbol.flags.is(Flags.Enum)
  }

  /**
   * Check if a type is a sealed trait, abstract class, or enum.
   */
  private def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      (flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))) ||
      flags.is(Flags.Enum)
    }
  }

  /**
   * Check if a type is a product type (case class, but not abstract).
   */
  private def isProductType(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*

    symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
  }

  /**
   * Check if a type is a container type (Option, List, Vector, Set, Map, etc.)
   * that should not be recursed into.
   */
  private def isContainerType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    // Check common container types
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
   * Check if a type is a primitive type that should not be recursed into.
   */
  private def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
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
   * Get the fields of a product type as (name, type) pairs.
   */
  private def getProductFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr)] = {
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
   * Get the direct subtypes of a sealed trait or enum.
   */
  private def directSubTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
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
}
