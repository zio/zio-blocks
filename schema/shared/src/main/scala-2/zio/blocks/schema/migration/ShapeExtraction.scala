package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Compile-time shape extraction for migration validation.
 *
 * Extracts the complete structure of a type at compile time, including:
 *   - All field paths (including nested paths like "address.street")
 *   - Sealed trait case names
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
   *   Case names for sealed traits in sorted order (e.g., List("Failure",
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
  def extractFieldPaths[A]: List[String] = macro ShapeExtractionMacros.extractFieldPathsMacro[A]

  /**
   * Extract case names from a sealed trait at compile time.
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
  def extractCaseNames[A]: List[String] = macro ShapeExtractionMacros.extractCaseNamesMacro[A]

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
  def extractShape[A]: Shape = macro ShapeExtractionMacros.extractShapeMacro[A]
}

private[migration] object ShapeExtractionMacros {

  def extractFieldPathsMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[List[String]] = {
    import c.universe._

    val tpe    = weakTypeOf[A].dealias
    val paths  = extractFieldPathsFromType(c)(tpe, "", Set.empty)
    val sorted = paths.sorted

    c.Expr[List[String]](q"${sorted.toList}")
  }

  def extractCaseNamesMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[List[String]] = {
    import c.universe._

    val tpe   = weakTypeOf[A].dealias
    val names = extractCaseNamesFromType(c)(tpe)

    c.Expr[List[String]](q"${names.sorted.toList}")
  }

  def extractShapeMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._

    val tpe = weakTypeOf[A].dealias

    // Extract field paths (for product types)
    val fieldPaths = extractFieldPathsFromType(c)(tpe, "", Set.empty).sorted.toList

    // Extract case names (for sum types)
    val caseNames = extractCaseNamesFromType(c)(tpe).sorted.toList

    // Extract field paths for each case
    val caseFieldPaths: Map[String, List[String]] =
      if (isSealedTrait(c)(tpe)) {
        val subTypes = directSubTypes(c)(tpe)
        subTypes.map { subTpe =>
          val caseName  = getCaseName(c)(subTpe)
          val casePaths = extractFieldPathsFromType(c)(subTpe, "", Set.empty).sorted.toList
          caseName -> casePaths
        }.toMap
      } else {
        Map.empty
      }

    // Build literal map entries for caseFieldPaths
    val mapEntries = caseFieldPaths.toList.map { case (k, v) =>
      q"($k, ${v.toList})"
    }

    q"_root_.zio.blocks.schema.migration.ShapeExtraction.Shape($fieldPaths, $caseNames, _root_.scala.collection.immutable.Map(..$mapEntries))"
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
  private def extractFieldPathsFromType(
    c: blackbox.Context
  )(tpe: c.Type, prefix: String, visiting: Set[String]): List[String] = {
    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion
    if (visiting.contains(typeKey)) {
      c.abort(
        c.enclosingPosition,
        s"Recursive type detected: ${dealiased.typeSymbol.name}. " +
          s"Migration shape extraction does not support recursive types. " +
          s"Recursion path: ${visiting.mkString(" -> ")} -> $typeKey"
      )
    }

    // Skip extraction for container types and primitives
    if (isContainerType(c)(dealiased) || isPrimitiveType(c)(dealiased)) {
      return Nil
    }

    // Only extract fields from product types (case classes)
    if (!isProductType(c)(dealiased.typeSymbol)) {
      return Nil
    }

    val newVisiting = visiting + typeKey
    val fields      = getProductFields(c)(dealiased)

    fields.flatMap { case (fieldName, fieldType) =>
      val fullPath    = if (prefix.isEmpty) fieldName else s"$prefix$fieldName"
      val nestedPaths = extractFieldPathsFromType(c)(fieldType, s"$fullPath.", newVisiting)
      fullPath :: nestedPaths
    }
  }

  /**
   * Extract case names from a sealed trait.
   */
  private def extractCaseNamesFromType(c: blackbox.Context)(tpe: c.Type): List[String] = {
    val dealiased = tpe.dealias

    if (isSealedTrait(c)(dealiased)) {
      val subTypes = directSubTypes(c)(dealiased)
      subTypes.map(getCaseName(c))
    } else {
      Nil
    }
  }

  /**
   * Get the name of a case from its type.
   */
  private def getCaseName(c: blackbox.Context)(tpe: c.Type): String = {
    val sym = tpe.typeSymbol
    // For case objects (modules), use the module symbol name
    if (sym.isModuleClass) {
      sym.asClass.module.name.decodedName.toString
    } else {
      sym.name.decodedName.toString
    }
  }

  /**
   * Check if a type is a sealed trait or abstract class.
   */
  private def isSealedTrait(c: blackbox.Context)(tpe: c.Type): Boolean = {
    val sym = tpe.typeSymbol
    sym.isClass && sym.asClass.isSealed
  }

  /**
   * Check if a type is a product type (case class, but not abstract).
   */
  private def isProductType(c: blackbox.Context)(symbol: c.Symbol): Boolean =
    symbol.isClass && symbol.asClass.isCaseClass && !symbol.asClass.isAbstract

  /**
   * Check if a type is a container type (Option, List, Vector, Set, Map, etc.)
   * that should not be recursed into.
   */
  private def isContainerType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._

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
   * Check if a type is a primitive type that should not be recursed into.
   */
  private def isPrimitiveType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._

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
   * Get the fields of a product type as (name, type) pairs.
   */
  private def getProductFields(c: blackbox.Context)(tpe: c.Type): List[(String, c.Type)] = {
    import c.universe._

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
   * Get the direct subtypes of a sealed trait.
   */
  private def directSubTypes(c: blackbox.Context)(tpe: c.Type): List[c.Type] = {
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
}
