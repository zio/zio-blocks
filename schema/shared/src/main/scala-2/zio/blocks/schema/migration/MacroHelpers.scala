package zio.blocks.schema.migration

import scala.reflect.macros.blackbox

/**
 * Type category for classification during shape extraction.
 */
sealed trait TypeCategory
object TypeCategory {
  case object Primitive   extends TypeCategory
  case object Record      extends TypeCategory
  case object Sealed      extends TypeCategory
  case object OptionType  extends TypeCategory
  case object SeqType     extends TypeCategory
  case object MapType     extends TypeCategory
  case object EitherType  extends TypeCategory
  case object WrappedType extends TypeCategory
}

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
   * Check if a type is a value class (extends AnyVal with single field).
   */
  protected def isValueClass(tpe: c.Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol.isClass &&
    symbol.asClass.isCaseClass &&
    tpe <:< c.typeOf[AnyVal] &&
    symbol.asClass.primaryConstructor.asMethod.paramLists.flatten.filter(_.isTerm).length == 1
  }

  /**
   * Get the inner type of a value class. Returns Some(innerType) if this is a
   * value class, None otherwise.
   */
  protected def getWrappedInnerType(tpe: c.Type): Option[c.Type] =
    if (isValueClass(tpe)) {
      val fields = getProductFields(tpe)
      fields.headOption.map(_._2)
    } else {
      None
    }

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
   * Categorize a type into its structural category.
   *
   * This is used to determine how to extract the shape tree from a type. The
   * order of checks matters:
   *   1. Check for Either first (before sealed check, as Either is a sealed
   *      trait)
   *   2. Check for Option (specific container)
   *   3. Check for Map (specific container)
   *   4. Check for other sequences/collections
   *   5. Check for sealed traits
   *   6. Check for product types (case classes)
   *   7. Default to Primitive
   */
  protected def categorizeType(tpe: c.Type): TypeCategory = {
    val dealiased = tpe.dealias

    // Use baseClasses to check inheritance - more reliable than <:< with existentials
    // baseClasses returns all classes in the inheritance hierarchy
    val baseNames = dealiased.baseClasses.map(_.fullName).toSet

    def hasBase(name: String): Boolean = baseNames.contains(name)

    // Check for Either first (it's a sealed trait but we handle it specially)
    if (hasBase("scala.util.Either")) {
      TypeCategory.EitherType
    }
    // Check for Option
    else if (hasBase("scala.Option")) {
      TypeCategory.OptionType
    }
    // Check for Map
    else if (
      hasBase("scala.collection.Map") || hasBase("scala.collection.immutable.Map") ||
      hasBase("scala.collection.mutable.Map")
    ) {
      TypeCategory.MapType
    }
    // Check for sequences/collections
    else if (
      hasBase("scala.collection.Seq") || hasBase("scala.collection.immutable.Seq") ||
      hasBase("scala.collection.Iterable") || hasBase("scala.collection.immutable.Iterable") ||
      hasBase("scala.Array")
    ) {
      TypeCategory.SeqType
    }
    // Check for sealed traits (but not the containers we already handled)
    else if (isSealedTrait(dealiased)) {
      TypeCategory.Sealed
    }
    // Check for wrapped types (value classes) BEFORE product types
    // because value classes are also case classes
    else if (isValueClass(dealiased)) {
      TypeCategory.WrappedType
    }
    // Check for product types (case classes)
    else if (isProductType(dealiased.typeSymbol)) {
      TypeCategory.Record
    }
    // Default to primitive
    else {
      TypeCategory.Primitive
    }
  }

  /**
   * Extract a ShapeNode tree from a type.
   *
   * Recursively descends into the type structure to build a hierarchical
   * representation:
   *   - Product types (case classes) become RecordNode with field shapes
   *   - Sum types (sealed traits) become SealedNode with case shapes
   *   - Either[L, R] becomes SealedNode with "Left" -> L's shape, "Right" ->
   *     R's shape
   *   - Option[A] becomes OptionNode with A's shape
   *   - List/Vector/Set[A] become SeqNode with A's shape
   *   - Map[K, V] becomes MapNode with K's and V's shapes
   *   - Primitives become PrimitiveNode
   *
   * Recursion handling:
   *   - Any recursion (direct or through containers) produces a compile error
   *   - Container elements are fully explored to extract their complete shape
   *
   * @param tpe
   *   The type to extract the shape from
   * @param visiting
   *   Set of type full names currently being visited (for recursion detection)
   * @param errorContext
   *   Context string for error messages
   * @return
   *   The ShapeNode representing the type's structure
   */
  protected def extractShapeTree(
    tpe: c.Type,
    visiting: Set[String],
    errorContext: String = "Shape extraction"
  ): ShapeNode = {
    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion - always report as error
    if (visiting.contains(typeKey)) {
      c.abort(
        c.enclosingPosition,
        s"Recursive type detected: ${dealiased.typeSymbol.name}. " +
          s"$errorContext does not support recursive types. " +
          s"Recursion path: ${visiting.mkString(" -> ")} -> $typeKey"
      )
    }

    val newVisiting = visiting + typeKey

    categorizeType(dealiased) match {
      case TypeCategory.Primitive =>
        ShapeNode.PrimitiveNode

      case TypeCategory.Record =>
        val fields      = getProductFields(dealiased)
        val fieldShapes = fields.map { case (fieldName, fieldType) =>
          fieldName -> extractShapeTree(fieldType, newVisiting, errorContext)
        }.toMap
        ShapeNode.RecordNode(fieldShapes)

      case TypeCategory.Sealed =>
        val subTypes   = directSubTypes(dealiased)
        val caseShapes = subTypes.map { subTpe =>
          val caseName  = getCaseName(subTpe)
          val caseShape = extractShapeTree(subTpe, newVisiting, errorContext)
          caseName -> caseShape
        }.toMap
        ShapeNode.SealedNode(caseShapes)

      case TypeCategory.EitherType =>
        // Either[L, R] -> SealedNode with Left and Right cases
        // Use baseType to get proper type arguments
        val eitherBase = dealiased.baseType(typeOf[Either[_, _]].typeSymbol)
        val typeArgs   = eitherBase.typeArgs
        if (typeArgs.size == 2) {
          val leftShape  = extractShapeTree(typeArgs(0), newVisiting, errorContext)
          val rightShape = extractShapeTree(typeArgs(1), newVisiting, errorContext)
          ShapeNode.SealedNode(Map("Left" -> leftShape, "Right" -> rightShape))
        } else {
          ShapeNode.PrimitiveNode
        }

      case TypeCategory.OptionType =>
        // Use baseType to get proper type arguments
        val optionBase = dealiased.baseType(typeOf[Option[_]].typeSymbol)
        val typeArgs   = optionBase.typeArgs
        if (typeArgs.size == 1) {
          val elementShape = extractShapeTree(typeArgs.head, newVisiting, errorContext)
          ShapeNode.OptionNode(elementShape)
        } else {
          ShapeNode.PrimitiveNode
        }

      case TypeCategory.SeqType =>
        // Use baseType to get proper type arguments - try multiple base types
        val seqBase = {
          val bases = List(
            dealiased.baseType(typeOf[Seq[_]].typeSymbol),
            dealiased.baseType(typeOf[List[_]].typeSymbol),
            dealiased.baseType(typeOf[Vector[_]].typeSymbol),
            dealiased.baseType(typeOf[Set[_]].typeSymbol),
            dealiased.baseType(typeOf[Iterable[_]].typeSymbol)
          )
          bases.find(_ != NoType).getOrElse(dealiased)
        }
        val typeArgs = if (seqBase.typeArgs.nonEmpty) seqBase.typeArgs else dealiased.typeArgs
        if (typeArgs.size >= 1) {
          val elementShape = extractShapeTree(typeArgs.head, newVisiting, errorContext)
          ShapeNode.SeqNode(elementShape)
        } else {
          ShapeNode.PrimitiveNode
        }

      case TypeCategory.MapType =>
        // Use baseType to get proper type arguments - try multiple base types
        val mapBase = {
          val bases = List(
            dealiased.baseType(typeOf[Map[_, _]].typeSymbol),
            dealiased.baseType(typeOf[scala.collection.Map[_, _]].typeSymbol)
          )
          bases.find(_ != NoType).getOrElse(dealiased)
        }
        val typeArgs = if (mapBase.typeArgs.nonEmpty) mapBase.typeArgs else dealiased.typeArgs
        if (typeArgs.size >= 2) {
          val keyShape   = extractShapeTree(typeArgs(0), newVisiting, errorContext)
          val valueShape = extractShapeTree(typeArgs(1), newVisiting, errorContext)
          ShapeNode.MapNode(keyShape, valueShape)
        } else {
          ShapeNode.PrimitiveNode
        }

      case TypeCategory.WrappedType =>
        getWrappedInnerType(dealiased) match {
          case Some(innerType) =>
            val innerShape = extractShapeTree(innerType, newVisiting, errorContext)
            ShapeNode.WrappedNode(innerShape)
          case None =>
            // Fallback if inner type extraction fails
            ShapeNode.PrimitiveNode
        }
    }
  }
}
