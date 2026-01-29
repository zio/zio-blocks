package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

import TypeLevel._

/**
 * Type-level field and case path extraction for compile-time migration
 * validation (Scala 2).
 *
 * Provides typeclasses that extract field paths and case names from types at
 * compile time, encoding them as TList type members for type-level validation.
 */
object FieldExtraction {

  // ============================================================================
  // FieldPaths Typeclass
  // ============================================================================

  /**
   * Typeclass for extracting all field paths (including nested paths) from a
   * type at compile time. The Paths type member contains the paths as a TList
   * of string literal types.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * FieldPaths[Person].Paths =:= "address" :: "address.city" :: "address.street" :: "name" :: TNil
   * }}}
   */
  sealed trait FieldPaths[A] {
    type Paths <: TList
  }

  object FieldPaths {

    /** Concrete implementation of FieldPaths with the Paths type member. */
    class Impl[A, P <: TList] extends FieldPaths[A] {
      type Paths = P
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, P <: TList] = FieldPaths[A] { type Paths = P }

    /** Implicit derivation macro for FieldPaths. */
    implicit def derived[A]: FieldPaths[A] = macro FieldExtractionMacros.fieldPathsDerivedImpl[A]
  }

  // ============================================================================
  // CasePaths Typeclass
  // ============================================================================

  /**
   * Typeclass for extracting case names from a sealed trait at compile time.
   * The Cases type member contains the case names as a TList of string literal
   * types with "case:" prefix.
   *
   * For sealed traits:
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * CasePaths[Result].Cases =:= "case:Failure" :: "case:Success" :: TNil
   * }}}
   *
   * For non-sealed types, Cases is TNil.
   */
  sealed trait CasePaths[A] {
    type Cases <: TList
  }

  object CasePaths {

    /** Concrete implementation of CasePaths with the Cases type member. */
    class Impl[A, C <: TList] extends CasePaths[A] {
      type Cases = C
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, C <: TList] = CasePaths[A] { type Cases = C }

    /** Implicit derivation macro for CasePaths. */
    implicit def derived[A]: CasePaths[A] = macro FieldExtractionMacros.casePathsDerivedImpl[A]
  }

  // ============================================================================
  // FieldNames Typeclass (legacy, for top-level field names only)
  // ============================================================================

  /**
   * Typeclass for extracting field names from a type at compile time. The
   * Labels type member contains the field names as a TList of string literal
   * types.
   *
   * Note: This extracts only top-level field names. For full nested path
   * extraction, use FieldPaths instead.
   */
  sealed trait FieldNames[A] {
    type Labels <: TList
  }

  object FieldNames {

    /** Concrete implementation of FieldNames with the Labels type member. */
    class Impl[A, L <: TList] extends FieldNames[A] {
      type Labels = L
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, L <: TList] = FieldNames[A] { type Labels = L }

    /** Implicit derivation macro for FieldNames. */
    implicit def derived[A]: FieldNames[A] = macro FieldExtractionMacros.fieldNamesDerivedImpl[A]
  }
}

private[migration] object FieldExtractionMacros {

  // ============================================================================
  // FieldPaths Derivation
  // ============================================================================

  def fieldPathsDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[FieldExtraction.FieldPaths[A]] = {
    import c.universe._

    val tpe   = weakTypeOf[A].dealias
    val paths = extractFieldPathsFromType(c)(tpe, "", Set.empty).sorted

    // Build TList type from paths
    val tlistType = pathsToTListType(c)(paths)

    // Create the instance with correct type
    val implClass  = typeOf[FieldExtraction.FieldPaths.Impl[_, _]].typeSymbol
    val resultType = appliedType(implClass, List(tpe, tlistType))

    c.Expr[FieldExtraction.FieldPaths[A]](q"new $resultType")
  }

  // ============================================================================
  // CasePaths Derivation
  // ============================================================================

  def casePathsDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[FieldExtraction.CasePaths[A]] = {
    import c.universe._

    val tpe       = weakTypeOf[A].dealias
    val caseNames = extractCaseNamesFromType(c)(tpe).sorted.map(name => s"case:$name")

    // Build TList type from case names
    val tlistType = pathsToTListType(c)(caseNames)

    // Create the instance with correct type
    val implClass  = typeOf[FieldExtraction.CasePaths.Impl[_, _]].typeSymbol
    val resultType = appliedType(implClass, List(tpe, tlistType))

    c.Expr[FieldExtraction.CasePaths[A]](q"new $resultType")
  }

  // ============================================================================
  // FieldNames Derivation (legacy, top-level only)
  // ============================================================================

  def fieldNamesDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[FieldExtraction.FieldNames[A]] = {
    import c.universe._

    val tpe    = weakTypeOf[A].dealias
    val fields = getProductFieldNames(c)(tpe).sorted

    // Build TList type from field names
    val tlistType = pathsToTListType(c)(fields)

    // Create the instance with correct type
    val implClass  = typeOf[FieldExtraction.FieldNames.Impl[_, _]].typeSymbol
    val resultType = appliedType(implClass, List(tpe, tlistType))

    c.Expr[FieldExtraction.FieldNames[A]](q"new $resultType")
  }

  // ============================================================================
  // Type Construction Helpers
  // ============================================================================

  /**
   * Convert a list of path strings to a TList type. List("a", "b", "c") => "a"
   * :: "b" :: "c" :: TNil
   */
  private def pathsToTListType(c: blackbox.Context)(paths: List[String]): c.Type = {
    import c.universe._

    val tnilType  = typeOf[TNil]
    val tconsType = typeOf[TCons[_, _]].typeConstructor

    paths.foldRight(tnilType) { (path, acc) =>
      val pathType = c.internal.constantType(Constant(path))
      appliedType(tconsType, List(pathType, acc))
    }
  }

  // ============================================================================
  // Field Path Extraction (mirrors ShapeExtraction logic)
  // ============================================================================

  /**
   * Extract all field paths from a type, recursively descending into nested
   * case classes. This mirrors the logic in ShapeExtraction but is duplicated
   * to avoid macro dependency issues.
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
          s"Migration validation does not support recursive types. " +
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
   * Get field names only (top-level, no nested paths).
   */
  private def getProductFieldNames(c: blackbox.Context)(tpe: c.Type): List[String] = {
    import c.universe._

    if (!isProductType(c)(tpe.typeSymbol)) return Nil

    val constructor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }

    constructor match {
      case Some(ctor) =>
        val paramList = ctor.paramLists.headOption.getOrElse(Nil)
        paramList.map(_.name.decodedName.toString)
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
