package zio.blocks.schema.migration

import scala.quoted.*

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
 */
private[migration] object MacroHelpers {

  /**
   * Check if a type is a container type (Option, List, Vector, Set, Map, etc.)
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
   * Check if a type is a value class (extends AnyVal with a single field).
   */
  def isValueClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    val symbol = tpe.typeSymbol
    // Value classes extend AnyVal and have exactly one field
    symbol.flags.is(Flags.Case) &&
    tpe <:< TypeRepr.of[AnyVal] &&
    symbol.primaryConstructor.paramSymss.flatten.filter(_.isTerm).length == 1
  }

  /**
   * Check if a type is an opaque type alias.
   */
  def isOpaqueType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.typeSymbol.flags.is(Flags.Opaque)
  }

  /**
   * Get the inner type of a wrapped type (value class or opaque type).
   *
   * For value classes, returns the type of the single field. For opaque types,
   * returns the underlying type from bounds.
   */
  def getWrappedInnerType(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[q.reflect.TypeRepr] = {
    import q.reflect.*

    if (isValueClass(tpe)) {
      // For value classes - get the single field type
      val fields = getProductFields(tpe)
      fields.headOption.map(_._2)
    } else if (isOpaqueType(tpe)) {
      // For opaque types - try to get underlying type from bounds
      val symbol = tpe.typeSymbol
      val bounds = symbol.typeRef.dealias
      // If dealias gives us a different type, use it
      if (!(bounds =:= tpe)) Some(bounds)
      else {
        // Try getting from the type bounds
        tpe match {
          case TypeBounds(_, hi) if !(hi =:= tpe) => Some(hi)
          case _                                  => None
        }
      }
    } else {
      None
    }
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
   * Get the name of a case from its type. Handles both regular case classes,
   * case objects, and enum values.
   */
  def getCaseName(using q: Quotes)(tpe: q.reflect.TypeRepr): String = {
    val name =
      // For enum values (simple cases like `case Red`), use termSymbol
      if (isEnumValue(tpe)) {
        tpe.termSymbol.name
      } else {
        tpe.typeSymbol.name
      }
    // Case objects have a trailing $ in their type symbol name, strip it
    if (name.endsWith("$")) name.dropRight(1) else name
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
   * Categorize a type into its structural category.
   *
   * This is used to determine how to extract the shape tree from a type. The
   * order of checks matters:
   *   1. Check for Either first (before sealed check, as Either is a sealed
   *      trait)
   *   2. Check for Option (specific container)
   *   3. Check for Map (specific container)
   *   4. Check for other sequences/collections
   *   5. Check for sealed traits/enums
   *   6. Check for product types (case classes)
   *   7. Default to Primitive
   */
  def categorizeType(using q: Quotes)(tpe: q.reflect.TypeRepr): TypeCategory = {
    import q.reflect.*

    val dealiased = tpe.dealias

    // Check for Either first (it's a sealed trait but we handle it specially)
    if (dealiased <:< TypeRepr.of[Either[?, ?]]) {
      TypeCategory.EitherType
    }
    // Check for Option
    else if (dealiased <:< TypeRepr.of[Option[?]]) {
      TypeCategory.OptionType
    }
    // Check for Map
    else if (dealiased <:< TypeRepr.of[Map[?, ?]]) {
      TypeCategory.MapType
    }
    // Check for sequences/collections (but not Map)
    else if (isContainerType(dealiased) && !(dealiased <:< TypeRepr.of[Map[?, ?]])) {
      TypeCategory.SeqType
    }
    // Check for sealed traits/enums
    else if (isSealedTraitOrEnum(dealiased)) {
      TypeCategory.Sealed
    }
    // Check for wrapped types (value classes, opaque types) BEFORE product types
    // because value classes are also case classes
    else if (isValueClass(dealiased) || isOpaqueType(dealiased)) {
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
  def extractShapeTree(using
    q: Quotes
  )(
    tpe: q.reflect.TypeRepr,
    visiting: Set[String],
    errorContext: String = "Shape extraction"
  ): ShapeNode = {
    import q.reflect.*

    val dealiased = tpe.dealias
    val typeKey   = dealiased.typeSymbol.fullName

    // Check for recursion - always report as error
    if (visiting.contains(typeKey)) {
      report.errorAndAbort(
        s"Recursive type detected: ${dealiased.show}. " +
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
          val caseShape =
            if (isEnumValue(subTpe)) {
              // Simple enum cases (like `case Red`) have no fields
              // and their type resolves to the parent enum, so we skip recursion
              ShapeNode.RecordNode(Map.empty)
            } else {
              extractShapeTree(subTpe, newVisiting, errorContext)
            }
          caseName -> caseShape
        }.toMap
        ShapeNode.SealedNode(caseShapes)

      case TypeCategory.EitherType =>
        // Either[L, R] -> SealedNode with Left and Right cases
        dealiased match {
          case AppliedType(_, List(leftType, rightType)) =>
            val leftShape  = extractShapeTree(leftType, newVisiting, errorContext)
            val rightShape = extractShapeTree(rightType, newVisiting, errorContext)
            ShapeNode.SealedNode(Map("Left" -> leftShape, "Right" -> rightShape))
          case _ =>
            // Fallback for unexpected Either structure
            ShapeNode.PrimitiveNode
        }

      case TypeCategory.OptionType =>
        dealiased match {
          case AppliedType(_, List(elementType)) =>
            val elementShape = extractShapeTree(elementType, newVisiting, errorContext)
            ShapeNode.OptionNode(elementShape)
          case _ =>
            ShapeNode.PrimitiveNode
        }

      case TypeCategory.SeqType =>
        dealiased match {
          case AppliedType(_, List(elementType)) =>
            val elementShape = extractShapeTree(elementType, newVisiting, errorContext)
            ShapeNode.SeqNode(elementShape)
          case _ =>
            ShapeNode.PrimitiveNode
        }

      case TypeCategory.MapType =>
        dealiased match {
          case AppliedType(_, List(keyType, valueType)) =>
            val keyShape   = extractShapeTree(keyType, newVisiting, errorContext)
            val valueShape = extractShapeTree(valueType, newVisiting, errorContext)
            ShapeNode.MapNode(keyShape, valueShape)
          case _ =>
            ShapeNode.PrimitiveNode
        }

      case TypeCategory.WrappedType =>
        getWrappedInnerType(dealiased) match {
          case Some(innerType) =>
            val innerShape = extractShapeTree(innerType, newVisiting, errorContext)
            ShapeNode.WrappedNode(innerShape)
          case None =>
            // Fallback to primitive if we can't extract inner type
            ShapeNode.PrimitiveNode
        }
    }
  }

  /**
   * Extract paths from a Handled/Provided tuple type as List[List[Segment]].
   * Preserves full structural information for comparison.
   *
   * Example input type: (("field", "address"), ("field", "city")) *: (("field",
   * "name"),) *: EmptyTuple Returns: List(List(Field("address"),
   * Field("city")), List(Field("name")))
   */
  def extractTuplePaths(using q: Quotes)(tpe: q.reflect.TypeRepr): List[List[Segment]] = {
    import q.reflect.*

    /**
     * Extract a single path tuple and convert it to List[Segment]. Path format:
     * (("field", "address"), ("field", "city")) -> List(Field("address"),
     * Field("city")) (("case", "Success"),) -> List(Case("Success"))
     */
    def pathTupleToSegments(pathType: TypeRepr): Option[List[Segment]] = {
      val segments = extractPathSegments(pathType)
      if (segments.isEmpty) None
      else Some(segments)
    }

    /**
     * Extract segments from a path tuple type. Each segment is either:
     *   - ("field", "name") -> Field("name")
     *   - ("case", "name") -> Case("name")
     *   - "element" -> Element
     *   - "key" -> Key
     *   - "value" -> Value
     *   - "wrapped" -> Wrapped
     */
    def extractPathSegments(pathType: TypeRepr): List[Segment] = {
      val dealiased = pathType.dealias
      dealiased match {
        // Handle *: syntax for path segments
        case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
          segmentFromType(head).toList ::: extractPathSegments(tail)
        // Handle EmptyTuple
        case _ if dealiased =:= TypeRepr.of[EmptyTuple] =>
          Nil
        // Handle Tuple2 for segment pairs (already a segment, not a path)
        case AppliedType(tycon, List(_, _)) if tycon.typeSymbol.fullName == "scala.Tuple2" =>
          segmentFromType(dealiased).toList
        case _ =>
          Nil
      }
    }

    /**
     * Convert a segment type to a Segment. ("field", "name") -> Field("name")
     * ("case", "name") -> Case("name") "element" literal -> Element
     */
    def segmentFromType(segType: TypeRepr): Option[Segment] = {
      val dealiased = segType.dealias
      dealiased match {
        // Handle ("field", "name") or ("case", "name") Tuple2
        case AppliedType(tycon, List(kindType, nameType)) if tycon.typeSymbol.fullName == "scala.Tuple2" =>
          (extractStringLiteral(kindType), extractStringLiteral(nameType)) match {
            case (Some("field"), Some(name)) => Some(Segment.Field(name))
            case (Some("case"), Some(name))  => Some(Segment.Case(name))
            case _                           => None
          }
        // Handle single string literals like "element", "key", "value", "wrapped"
        case ConstantType(StringConstant("element")) => Some(Segment.Element)
        case ConstantType(StringConstant("key"))     => Some(Segment.Key)
        case ConstantType(StringConstant("value"))   => Some(Segment.Value)
        case ConstantType(StringConstant("wrapped")) => Some(Segment.Wrapped)
        case _                                       => None
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
    def extractPaths(t: TypeRepr): List[List[Segment]] = {
      val dealiased = t.dealias
      dealiased match {
        // Handle *: syntax: head *: tail (where head is a path tuple)
        case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
          pathTupleToSegments(head).toList ::: extractPaths(tail)
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
