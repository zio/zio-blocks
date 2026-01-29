package zio.blocks.schema.migration

import scala.reflect.macros.blackbox

/**
 * Shared macro helper functions for compile-time type introspection.
 *
 * Note: This is implemented as a trait to be mixed in, which properly handles
 * Scala 2's path-dependent types in macros.
 */
private[migration] trait MacroHelpers {
  val c: blackbox.Context
  import c.universe._

  /**
   * Check if a type is a primitive type that should not be recursed into.
   */
  protected def isPrimitiveType(tpe: c.Type): Boolean = {
    val primitiveTypes = List(
      typeOf[Boolean],
      typeOf[Byte],
      typeOf[Short],
      typeOf[Int],
      typeOf[Long],
      typeOf[Float],
      typeOf[Double],
      typeOf[Char],
      typeOf[String],
      typeOf[java.math.BigInteger],
      typeOf[java.math.BigDecimal],
      typeOf[BigInt],
      typeOf[BigDecimal],
      typeOf[java.util.UUID],
      typeOf[java.time.Instant],
      typeOf[java.time.LocalDate],
      typeOf[java.time.LocalTime],
      typeOf[java.time.LocalDateTime],
      typeOf[java.time.OffsetDateTime],
      typeOf[java.time.ZonedDateTime],
      typeOf[java.time.Duration],
      typeOf[java.time.Period],
      typeOf[java.time.Year],
      typeOf[java.time.YearMonth],
      typeOf[java.time.MonthDay],
      typeOf[java.time.ZoneId],
      typeOf[java.time.ZoneOffset],
      typeOf[Unit],
      typeOf[Nothing]
    )

    primitiveTypes.exists(pt => tpe =:= pt)
  }

  /**
   * Check if a type is a container type (Option, List, Vector, Set, Map, etc.)
   * that should not be recursed into.
   */
  protected def isContainerType(tpe: c.Type): Boolean = {
    val containerTypes = List(
      typeOf[Option[_]],
      typeOf[List[_]],
      typeOf[Vector[_]],
      typeOf[Set[_]],
      typeOf[Seq[_]],
      typeOf[IndexedSeq[_]],
      typeOf[Iterable[_]],
      typeOf[Map[_, _]],
      typeOf[Array[_]]
    )

    containerTypes.exists(ct => tpe <:< ct)
  }

  /**
   * Check if a type is a product type (case class, but not abstract).
   */
  protected def isProductType(symbol: c.Symbol): Boolean =
    symbol.isClass && symbol.asClass.isCaseClass && !symbol.asClass.isAbstract

  /**
   * Get the fields of a product type as (name, type) pairs.
   */
  protected def getProductFields(tpe: c.Type): List[(String, c.Type)] = {
    // Get primary constructor
    val constructor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }

    constructor match {
      case Some(ctor) =>
        val paramList = ctor.paramLists.headOption.getOrElse(Nil)
        paramList.map { param =>
          val paramName = param.name.decodedName.toString
          // Get the field type by looking up the accessor method in the type
          val paramType = tpe.member(param.name).typeSignatureIn(tpe).dealias
          (paramName, paramType)
        }
      case None => Nil
    }
  }

  /**
   * Check if a type is a sealed trait or abstract class.
   */
  protected def isSealedTrait(tpe: c.Type): Boolean = {
    val sym = tpe.typeSymbol
    sym.isClass && sym.asClass.isSealed
  }

  /**
   * Get the name of a case from its type.
   */
  protected def getCaseName(tpe: c.Type): String = {
    val sym = tpe.typeSymbol
    // For case objects (modules), use the module symbol name
    if (sym.isModuleClass) {
      sym.asClass.module.name.decodedName.toString
    } else {
      sym.name.decodedName.toString
    }
  }

  /**
   * Get the direct subtypes of a sealed trait.
   */
  protected def directSubTypes(tpe: c.Type): List[c.Type] = {
    val tpeClass   = tpe.typeSymbol.asClass
    val subclasses = tpeClass.knownDirectSubclasses.toList.sortBy(_.name.toString)

    subclasses.map { symbol =>
      val classSymbol = symbol.asClass
      // For modules (case objects), use the singleton type
      if (classSymbol.isModuleClass) {
        classSymbol.module.typeSignature
      } else {
        classSymbol.toType
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
   *   Context string for error messages (e.g., "Migration validation")
   * @return
   *   List of dot-separated field paths
   */
  protected def extractFieldPathsFromType(
    tpe: c.Type,
    prefix: String,
    visiting: Set[String],
    errorContext: String = "Migration validation"
  ): List[String] = {
    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion
    if (visiting.contains(typeKey)) {
      c.abort(
        c.enclosingPosition,
        s"Recursive type detected: ${dealiased.typeSymbol.name}. " +
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
   * Extract case names from a sealed trait.
   */
  protected def extractCaseNamesFromType(tpe: c.Type): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTrait(dealiased)) {
      val subTypes = directSubTypes(dealiased)
      subTypes.map(getCaseName)
    } else {
      Nil
    }
  }
}
