package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Full-featured path extraction from types at compile time.
 *
 * This object provides comprehensive extraction of:
 *   - All nested field paths (e.g., "address.street", "address.city")
 *   - Sealed trait/enum case names with "case:" prefix
 *   - Field paths within each case
 *
 * Unlike simpler extractors, this handles:
 *   - Recursive types with cycle detection
 *   - Deeply nested structures
 *   - Mixed record/enum hierarchies
 */
object PathExtractor {

  /**
   * Extract all field paths from a type, including nested paths.
   *
   * For a nested structure like:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   * }}}
   *
   * Returns: List("address", "address.city", "address.street", "name")
   */
  inline def extractAllPaths[A]: List[String] = ${ extractAllPathsImpl[A] }

  /**
   * Extract case names from a sealed trait or enum.
   *
   * Returns case names prefixed with "case:" for consistent handling with field
   * paths.
   */
  inline def extractCases[A]: List[String] = ${ extractCasesImpl[A] }

  /**
   * Extract both field paths and case names into a unified set.
   */
  inline def extractPathsAndCases[A]: List[String] = ${ extractPathsAndCasesImpl[A] }

  /**
   * Compute required operations between two types.
   *
   * Returns paths that need to be handled (in A but not B) and provided (in B
   * but not A).
   */
  inline def computeRequirements[A, B]: PathRequirements =
    ${ computeRequirementsImpl[A, B] }

  case class PathRequirements(
    needsHandling: List[String],
    needsProviding: List[String],
    unchanged: List[String]
  ) {
    def isComplete(handled: Set[String], provided: Set[String]): Boolean =
      needsHandling.forall(handled.contains) && needsProviding.forall(provided.contains)

    def missing(handled: Set[String], provided: Set[String]): MissingPaths =
      MissingPaths(
        unhandled = needsHandling.filterNot(handled.contains),
        unprovided = needsProviding.filterNot(provided.contains)
      )
  }

  case class MissingPaths(
    unhandled: List[String],
    unprovided: List[String]
  ) {
    def isEmpty: Boolean = unhandled.isEmpty && unprovided.isEmpty

    def errorMessage: String = {
      val sb = new StringBuilder
      if (unhandled.nonEmpty) {
        sb.append("Unhandled paths from source (need dropField or renameField):\n")
        unhandled.foreach(p => sb.append(s"  - $p\n"))
      }
      if (unprovided.nonEmpty) {
        sb.append("Unprovided paths for target (need addField or renameField):\n")
        unprovided.foreach(p => sb.append(s"  - $p\n"))
      }
      sb.toString
    }
  }

  private def extractAllPathsImpl[A: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val paths = extractPathsFromType(tpe, "", Set.empty)
    Expr(paths.sorted)
  }

  private def extractCasesImpl[A: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val cases = extractCaseNames(tpe).map(c => s"case:$c")
    Expr(cases.sorted)
  }

  private def extractPathsAndCasesImpl[A: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val paths = extractPathsFromType(tpe, "", Set.empty)
    val cases = extractCaseNames(tpe).map(c => s"case:$c")
    Expr((paths ++ cases).sorted)
  }

  private def computeRequirementsImpl[A: Type, B: Type](using
    Quotes
  ): Expr[PathRequirements] = {
    import quotes.reflect.*

    val tpeA   = TypeRepr.of[A].dealias
    val tpeB   = TypeRepr.of[B].dealias
    val pathsA = extractPathsFromType(tpeA, "", Set.empty).toSet
    val pathsB = extractPathsFromType(tpeB, "", Set.empty).toSet
    val casesA = extractCaseNames(tpeA).map(c => s"case:$c").toSet
    val casesB = extractCaseNames(tpeB).map(c => s"case:$c").toSet

    val allA = pathsA ++ casesA
    val allB = pathsB ++ casesB

    val needsHandling  = (allA -- allB).toList.sorted
    val needsProviding = (allB -- allA).toList.sorted
    val unchanged      = (allA.intersect(allB)).toList.sorted

    '{
      PathRequirements(
        needsHandling = ${ Expr(needsHandling) },
        needsProviding = ${ Expr(needsProviding) },
        unchanged = ${ Expr(unchanged) }
      )
    }
  }

  private def extractPathsFromType(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr, prefix: String, seen: Set[String]): List[String] = {
    import quotes.reflect.*

    val typeName = tpe.typeSymbol.fullName
    if (seen.contains(typeName)) {
      return Nil
    }
    val newSeen = seen + typeName

    tpe.dealias match {
      case ref: Refinement =>
        extractRefinementPaths(ref, prefix, newSeen)

      case tpe if tpe.typeSymbol.flags.is(Flags.Case) =>
        extractCaseClassPaths(tpe, prefix, newSeen)

      case tpe if tpe.typeSymbol.flags.is(Flags.Sealed) =>
        Nil

      case _ =>
        Nil
    }
  }

  private def extractRefinementPaths(using
    Quotes
  )(ref: quotes.reflect.Refinement, prefix: String, seen: Set[String]): List[String] = {
    import quotes.reflect.*

    def loop(tpe: TypeRepr, acc: List[String]): List[String] = tpe match {
      case Refinement(parent, name, info) if name != "Tag" =>
        val fieldPath = if (prefix.isEmpty) name else s"$prefix.$name"
        val fieldType = info match {
          case ByNameType(resultType)       => resultType
          case MethodType(_, _, resultType) => resultType
          case other                        => other
        }

        val nestedPaths =
          if (isPrimitive(fieldType)) Nil
          else extractPathsFromType(fieldType, fieldPath, seen)

        loop(parent, fieldPath :: (nestedPaths ++ acc))

      case Refinement(parent, _, _) =>
        loop(parent, acc)

      case _ => acc
    }

    loop(ref, Nil)
  }

  private def extractCaseClassPaths(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr, prefix: String, seen: Set[String]): List[String] = {
    val fields = tpe.typeSymbol.caseFields
    fields.flatMap { field =>
      val fieldName = field.name
      val fieldPath = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
      val fieldType = tpe.memberType(field)

      val nestedPaths =
        if (isPrimitive(fieldType)) Nil
        else extractPathsFromType(fieldType, fieldPath, seen)

      fieldPath :: nestedPaths
    }.toList
  }

  private def extractCaseNames(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*

    if (tpe.typeSymbol.flags.is(Flags.Sealed)) {
      tpe.typeSymbol.children.map(_.name)
    } else {
      Nil
    }
  }

  private def isPrimitive(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    val name = tpe.typeSymbol.fullName
    primitiveNames.exists(name.contains)
  }

  private val primitiveNames = Set(
    "scala.Boolean",
    "scala.Byte",
    "scala.Short",
    "scala.Int",
    "scala.Long",
    "scala.Float",
    "scala.Double",
    "scala.Char",
    "java.lang.String",
    "scala.Predef.String",
    "String",
    "BigInt",
    "BigDecimal",
    "java.util.UUID",
    "java.time.Instant",
    "java.time.LocalDate"
  )
}
