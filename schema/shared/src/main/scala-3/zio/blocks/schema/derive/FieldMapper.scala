package zio.blocks.schema.derive

import scala.quoted.*
import scala.util.boundary, boundary.break

/**
 * Field information for mapping algorithm.
 *
 * @param name
 *   Field name
 * @param tpe
 *   Field type representation (stored as string to avoid quote context issues)
 * @param position
 *   Field position in parameter list
 * @param hasDefault
 *   Whether this field has a default value in the constructor
 * @param defaultValue
 *   Optional default value term (for use in code generation)
 */
private[schema] case class FieldInfo(
  name: String,
  tpe: String,
  tpeRepr: Any, // Will be TypeRepr at runtime
  position: Int,
  hasDefault: Boolean = false,
  defaultValue: Option[Any] = None // Option[Term] in practice
)

private[schema] object FieldInfo {
  def apply(using Quotes)(name: String, tpe: quotes.reflect.TypeRepr, position: Int): FieldInfo =
    new FieldInfo(name, tpe.show, tpe, position, false, None)

  def apply(using
    Quotes
  )(
    name: String,
    tpe: quotes.reflect.TypeRepr,
    position: Int,
    hasDefault: Boolean,
    defaultValue: Option[Any]
  ): FieldInfo =
    new FieldInfo(name, tpe.show, tpe, position, hasDefault, defaultValue)
}

/**
 * Actions for field mapping in schema evolution. Represents different
 * strategies for mapping source to target fields.
 */
private[schema] sealed trait FieldMappingAction {
  def targetIdx: Int
}

private[schema] object FieldMappingAction {

  /**
   * Normal field mapping from source to target.
   */
  case class MapField(sourceIdx: Int, targetIdx: Int) extends FieldMappingAction

  /**
   * Inject None for Option[T] field when source doesn't have the field.
   */
  case class InjectNone(targetIdx: Int) extends FieldMappingAction

  /**
   * Use default value for field when source doesn't have the field.
   */
  case class UseDefault(targetIdx: Int, defaultValue: Any) extends FieldMappingAction // defaultValue is Term
}

/**
 * Algorithm for mapping fields between source and target types. Implements a
 * 5-level disambiguation strategy:
 *   1. Exact match: same name + same type
 *   2. Name match with coercion: same name + coercible type
 *   3. Unique type match: type appears exactly once in both source and target
 *   4. Position match: fallback to positional mapping
 *   5. Schema Evolution: Option injection or default values for unmapped target
 *      fields
 */
private[schema] object FieldMapper {

  /**
   * Map source fields to target fields with schema evolution support.
   *
   * @param sourceFields
   *   Fields from source type
   * @param targetFields
   *   Fields from target type
   * @return
   *   Right with mapping actions or Left with error message
   */
  def mapFields(using
    Quotes
  )(
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo]
  ): Either[String, Seq[FieldMappingAction]] = {
    import quotes.reflect.*

    boundary {
      if (targetFields.isEmpty) {
        break(Right(Seq.empty))
      }

      val actions             = scala.collection.mutable.ArrayBuffer[FieldMappingAction]()
      val usedSourceIndices   = scala.collection.mutable.Set[Int]()
      val mappedTargetIndices = scala.collection.mutable.Set[Int]() // Optimized: use Set for O(1) lookup

      // Helper to check if a target index is already mapped (optimized with Set)
      def isTargetMapped(targetIdx: Int): Boolean = mappedTargetIndices.contains(targetIdx)

      // Helper to mark target as mapped
      def markTargetMapped(targetIdx: Int): Unit = mappedTargetIndices.add(targetIdx)

      // Build index maps for faster lookups (optimization)
      val sourceByNameAndType = scala.collection.mutable.Map[(String, String), Seq[(FieldInfo, Int)]]()
      sourceFields.zipWithIndex.foreach { case (field, idx) =>
        val key = (field.name, field.tpe)
        sourceByNameAndType.update(key, sourceByNameAndType.getOrElse(key, Seq.empty) :+ (field, idx))
      }

      // Level 1: Exact match (name + type) - optimized with index
      for (targetIdx <- targetFields.indices) {
        val targetField = targetFields(targetIdx)
        val key         = (targetField.name, targetField.tpe)

        sourceByNameAndType.get(key).flatMap { candidates =>
          candidates.find { case (_, sourceIdx) =>
            !usedSourceIndices.contains(sourceIdx)
          }
        } match {
          case Some((_, sourceIdx)) =>
            actions.addOne(FieldMappingAction.MapField(sourceIdx, targetIdx))
            usedSourceIndices.add(sourceIdx)
            markTargetMapped(targetIdx)
          case None => ()
        }
      }

      // Build index by name for Level 2 (optimization)
      val sourceByName = scala.collection.mutable.Map[String, Seq[(FieldInfo, Int)]]()
      sourceFields.zipWithIndex.foreach { case (field, idx) =>
        sourceByName.update(field.name, sourceByName.getOrElse(field.name, Seq.empty) :+ (field, idx))
      }

      // Level 2: Name match with coercible type - optimized with index
      for (targetIdx <- targetFields.indices if !isTargetMapped(targetIdx)) {
        val targetField = targetFields(targetIdx)
        val targetType  = targetField.tpeRepr.asInstanceOf[TypeRepr]

        sourceByName.get(targetField.name).flatMap { candidates =>
          candidates.find { case (sourceField, sourceIdx) =>
            val sourceType = sourceField.tpeRepr.asInstanceOf[TypeRepr]
            !usedSourceIndices.contains(sourceIdx) &&
            isCoercible(sourceType, targetType)
          }
        } match {
          case Some((_, sourceIdx)) =>
            actions.addOne(FieldMappingAction.MapField(sourceIdx, targetIdx))
            usedSourceIndices.add(sourceIdx)
            markTargetMapped(targetIdx)
          case None => ()
        }
      }

      // Build type count maps for Level 3 (optimization)
      val targetTypeCounts = scala.collection.mutable.Map[String, Int]()
      targetFields.zipWithIndex.foreach { case (field, idx) =>
        if (!isTargetMapped(idx)) {
          targetTypeCounts.update(field.tpe, targetTypeCounts.getOrElse(field.tpe, 0) + 1)
        }
      }

      val sourceTypeCounts = scala.collection.mutable.Map[String, Int]()
      sourceFields.zipWithIndex.foreach { case (field, idx) =>
        if (!usedSourceIndices.contains(idx)) {
          sourceTypeCounts.update(field.tpe, sourceTypeCounts.getOrElse(field.tpe, 0) + 1)
        }
      }

      // Level 3: Unique type match - optimized with pre-computed counts
      for (targetIdx <- targetFields.indices if !isTargetMapped(targetIdx)) {
        val targetField = targetFields(targetIdx)
        val targetType  = targetField.tpeRepr.asInstanceOf[TypeRepr]

        // Use pre-computed count (optimization)
        val targetTypeCount = targetTypeCounts.getOrElse(targetField.tpe, 0)

        if (targetTypeCount == 1) {
          // Find matching source field with same unique type
          val candidateSources = sourceFields.zipWithIndex.filter { case (sourceField, sourceIdx) =>
            val sourceType = sourceField.tpeRepr.asInstanceOf[TypeRepr]
            !usedSourceIndices.contains(sourceIdx) && sourceType =:= targetType
          }

          // Use pre-computed count (optimization)
          val sourceTypeCount = sourceTypeCounts.getOrElse(targetField.tpe, 0)

          if (candidateSources.size == 1 && sourceTypeCount == 1) {
            val (_, sourceIdx) = candidateSources.head
            actions.addOne(FieldMappingAction.MapField(sourceIdx, targetIdx))
            usedSourceIndices.add(sourceIdx)
            markTargetMapped(targetIdx)
            // Update counts for next iteration
            targetTypeCounts.update(targetField.tpe, 0)
            sourceTypeCounts.update(targetField.tpe, sourceTypeCounts.getOrElse(targetField.tpe, 1) - 1)
          } else if (candidateSources.size > 1) {
            // Ambiguous: multiple source fields match - choose deterministically
            // Prefer name match, then alphabetical order
            val chosen = candidateSources.find { case (f, _) => f.name == targetField.name }
              .getOrElse(candidateSources.sortBy { case (f, _) => f.name }.head)

            val (chosenField, sourceIdx) = chosen
            val candidateNames           = candidateSources.map { case (f, _) => s"${f.name}: ${f.tpe}" }.mkString(", ")

            // Emit warning instead of error
            report.warning(
              s"Ambiguous field mapping for target field '${targetField.name}' (type: ${targetField.tpe}). " +
                s"Multiple source fields match: $candidateNames. " +
                s"Selected '${chosenField.name}: ${chosenField.tpe}' " +
                (if (chosenField.name == targetField.name) "based on name match." else "based on alphabetical order.")
            )

            actions.addOne(FieldMappingAction.MapField(sourceIdx, targetIdx))
            usedSourceIndices.add(sourceIdx)
            markTargetMapped(targetIdx)
            // Update counts for next iteration
            targetTypeCounts.update(targetField.tpe, 0)
            sourceTypeCounts.update(targetField.tpe, sourceTypeCounts.getOrElse(targetField.tpe, 1) - 1)
          }
        }
      }

      // Level 4: Positional match (only if all previous levels didn't fully resolve)
      val unmappedTargetIndices = targetFields.indices.filterNot(isTargetMapped)
      if (unmappedTargetIndices.nonEmpty) {
        // Try positional matching for remaining fields
        val unmappedSourceIndices = sourceFields.indices.filterNot(usedSourceIndices.contains)

        if (unmappedSourceIndices.size >= unmappedTargetIndices.size) {
          for ((targetIdx, offset) <- unmappedTargetIndices.zipWithIndex) {
            val sourceIdx   = unmappedSourceIndices(offset)
            val sourceField = sourceFields(sourceIdx)
            val targetField = targetFields(targetIdx)
            val sourceType  = sourceField.tpeRepr.asInstanceOf[TypeRepr]
            val targetType  = targetField.tpeRepr.asInstanceOf[TypeRepr]

            // Check if types are compatible
            if (sourceType =:= targetType || isCoercible(sourceType, targetType)) {
              actions.addOne(FieldMappingAction.MapField(sourceIdx, targetIdx))
              usedSourceIndices.add(sourceIdx)
              markTargetMapped(targetIdx)
            } else {
              // Don't fail here - let Level 5 handle schema evolution
            }
          }
        }
      }

      // Level 5: Schema Evolution - handle unmapped target fields
      val stillUnmapped = targetFields.indices.filterNot(isTargetMapped)
      for (targetIdx <- stillUnmapped) {
        val targetField = targetFields(targetIdx)
        val targetType  = targetField.tpeRepr.asInstanceOf[TypeRepr]

        // Strategy 1: Option[T] -> InjectNone (highest priority)
        if (targetType <:< TypeRepr.of[Option[?]]) {
          actions.addOne(FieldMappingAction.InjectNone(targetIdx))
          markTargetMapped(targetIdx)
        }
        // Strategy 2: Has default value -> UseDefault
        else if (targetField.hasDefault && targetField.defaultValue.isDefined) {
          actions.addOne(FieldMappingAction.UseDefault(targetIdx, targetField.defaultValue.get))
          markTargetMapped(targetIdx)
        }
        // Strategy 3: Error - required field without source
        else {
          val sourceFieldNames       = sourceFields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
          val availableSourceIndices = sourceFields.indices.filterNot(usedSourceIndices.contains)
          val availableSourceFields  = availableSourceIndices.map(idx => sourceFields(idx))

          val suggestions = if (availableSourceFields.nonEmpty) {
            s"\nAvailable unmapped source fields: ${availableSourceFields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")}"
          } else {
            "\nAll source fields have been mapped."
          }

          break(
            Left(
              s"Cannot derive Into: Ambiguous field mapping\n\n" +
                s"Target field '${targetField.name}' (position ${targetIdx}, type: ${targetField.tpe}) " +
                s"cannot be mapped from source type.\n" +
                s"Source fields: $sourceFieldNames\n" +
                s"$suggestions\n\n" +
                s"Possible solutions:\n" +
                s"  1. Add field '${targetField.name}' to source type\n" +
                s"  2. Make target field optional: Option[${targetField.tpe}]\n" +
                s"  3. Add default value to target field: ${targetField.name}: ${targetField.tpe} = <default>\n" +
                s"  4. Provide explicit Into instance"
            )
          )
        }
      }

      // Sort by target index for consistent ordering
      Right(actions.toSeq.sortBy {
        case FieldMappingAction.MapField(_, tIdx)   => (0, tIdx)
        case FieldMappingAction.InjectNone(tIdx)    => (1, tIdx)
        case FieldMappingAction.UseDefault(tIdx, _) => (2, tIdx)
      })
    }
  }

  /**
   * Legacy method for backward compatibility. Converts new FieldMappingAction
   * results to old (Int, Int) format.
   */
  def mapFieldsLegacy(using
    Quotes
  )(
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo]
  ): Either[String, Seq[(Int, Int)]] =
    FieldMapper.mapFields(using quotes)(sourceFields, targetFields).map { actions =>
      actions.collect { case FieldMappingAction.MapField(sourceIdx, targetIdx) =>
        (sourceIdx, targetIdx)
      }
    }

  /**
   * Check if source type can be coerced to target type. Checks for:
   *   - Numeric widening/narrowing conversions
   *   - Collection type conversions (List[A] -> Vector[B] if A coercible to B)
   *   - Option conversions
   *   - Map/Either conversions
   *   - Product type conversions (case classes, tuples) - supports nested
   *     conversions
   */
  private def isCoercible(using Quotes)(source: quotes.reflect.TypeRepr, target: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    val intTpe    = TypeRepr.of[Int]
    val longTpe   = TypeRepr.of[Long]
    val floatTpe  = TypeRepr.of[Float]
    val doubleTpe = TypeRepr.of[Double]
    val byteTpe   = TypeRepr.of[Byte]
    val shortTpe  = TypeRepr.of[Short]

    // Numeric conversions
    val isNumericCoercible =
      // Widening conversions
      (source =:= byteTpe && (target =:= shortTpe || target =:= intTpe || target =:= longTpe)) ||
        (source =:= shortTpe && (target =:= intTpe || target =:= longTpe)) ||
        (source =:= intTpe && target =:= longTpe) ||
        (source =:= floatTpe && target =:= doubleTpe) ||
        // Narrowing conversions (with runtime validation)
        (source =:= longTpe && (target =:= intTpe || target =:= shortTpe || target =:= byteTpe)) ||
        (source =:= intTpe && (target =:= shortTpe || target =:= byteTpe)) ||
        (source =:= shortTpe && target =:= byteTpe) ||
        (source =:= doubleTpe && target =:= floatTpe)

    if (isNumericCoercible) return true

    // Product type conversions (case classes, tuples) - supports nested conversions
    // If both are product types, they are potentially coercible via recursive Into derivation
    if (isProductType(source) && isProductType(target)) {
      return true
    }

    // Collection conversions - List, Vector, Set, Seq with element coercion
    if ((source <:< TypeRepr.of[Iterable[?]]) && (target <:< TypeRepr.of[Iterable[?]])) {
      val sourceElem = getTypeArg(source, 0)
      val targetElem = getTypeArg(target, 0)
      // Recursively check if elements are coercible
      return sourceElem =:= targetElem || isCoercible(sourceElem, targetElem)
    }

    // Option conversions
    if ((source <:< TypeRepr.of[Option[?]]) && (target <:< TypeRepr.of[Option[?]])) {
      val sourceElem = getTypeArg(source, 0)
      val targetElem = getTypeArg(target, 0)
      return sourceElem =:= targetElem || isCoercible(sourceElem, targetElem)
    }

    // Map conversions
    if ((source <:< TypeRepr.of[Map[?, ?]]) && (target <:< TypeRepr.of[Map[?, ?]])) {
      val sourceKey      = getTypeArg(source, 0)
      val sourceValue    = getTypeArg(source, 1)
      val targetKey      = getTypeArg(target, 0)
      val targetValue    = getTypeArg(target, 1)
      val keyCoercible   = sourceKey =:= targetKey || isCoercible(sourceKey, targetKey)
      val valueCoercible = sourceValue =:= targetValue || isCoercible(sourceValue, targetValue)
      return keyCoercible && valueCoercible
    }

    // Either conversions
    if ((source <:< TypeRepr.of[Either[?, ?]]) && (target <:< TypeRepr.of[Either[?, ?]])) {
      val sourceLeft     = getTypeArg(source, 0)
      val sourceRight    = getTypeArg(source, 1)
      val targetLeft     = getTypeArg(target, 0)
      val targetRight    = getTypeArg(target, 1)
      val leftCoercible  = sourceLeft =:= targetLeft || isCoercible(sourceLeft, targetLeft)
      val rightCoercible = sourceRight =:= targetRight || isCoercible(sourceRight, targetRight)
      return leftCoercible && rightCoercible
    }

    // ZIO Prelude Newtype conversions
    // Check if target is a Newtype[T] where T matches source
    if (isZioNewtypePattern(target)) {
      val underlyingOpt = getNewtypeUnderlying(target)
      underlyingOpt match {
        case Some(underlying) if source =:= underlying || source.dealias =:= underlying.dealias =>
          return true
        case _ => ()
      }
    }

    // Check if source is a Newtype[T] where target is T
    if (isZioNewtypePattern(source)) {
      val underlyingOpt = getNewtypeUnderlying(source)
      underlyingOpt match {
        case Some(underlying) if target =:= underlying || target.dealias =:= underlying.dealias =>
          return true
        case _ => ()
      }
    }

    // Scala 3 opaque type conversions
    // Target is opaque type, source is underlying
    if (isOpaqueAlias(target)) {
      val underlyingOpt = getOpaqueUnderlying(target)
      underlyingOpt match {
        case Some(underlying) if source =:= underlying || source.dealias =:= underlying.dealias =>
          return true
        case _ => ()
      }
    }

    // Source is opaque type, target is underlying
    if (isOpaqueAlias(source)) {
      val underlyingOpt = getOpaqueUnderlying(source)
      underlyingOpt match {
        case Some(underlying) if target =:= underlying || target.dealias =:= underlying.dealias =>
          return true
        case _ => ()
      }
    }

    // Scala 3 enum conversions
    // If both are enum types, they are potentially coercible via coproduct conversion
    if (source.typeSymbol.flags.is(Flags.Enum) && target.typeSymbol.flags.is(Flags.Enum)) {
      return true
    }

    false
  }

  // Helper to check if a type is a ZIO Prelude Newtype pattern
  private def isZioNewtypePattern(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // First, try to dealias the type to see if it resolves to X.Type
    val dealiased =
      try { tpe.dealias }
      catch { case _: Throwable => tpe }

    def checkTypeRef(tr: TypeRepr): Boolean = tr match {
      case TypeRef(qualifier, name) if name == "Type" =>
        qualifier match {
          case termRef: TermRef =>
            val symbol = termRef.termSymbol
            if (symbol != Symbol.noSymbol && symbol.flags.is(Flags.Module)) {
              val moduleClass = symbol.moduleClass
              if (moduleClass != Symbol.noSymbol) {
                val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
                parentNames.exists(n => n.contains("Newtype") || n.contains("Subtype"))
              } else false
            } else false
          case _ => false
        }
      case _ => false
    }

    // Check both original and dealiased type
    checkTypeRef(tpe) || checkTypeRef(dealiased)
  }

  // Helper to get underlying type of ZIO Prelude Newtype
  private def getNewtypeUnderlying(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    // First, try to dealias the type
    val dealiased =
      try { tpe.dealias }
      catch { case _: Throwable => tpe }

    def extractUnderlying(tr: TypeRepr): Option[TypeRepr] = tr match {
      case TypeRef(qualifier, "Type") =>
        qualifier match {
          case termRef: TermRef =>
            val companionSymbol = termRef.termSymbol
            if (companionSymbol != Symbol.noSymbol) {
              val moduleClass = companionSymbol.moduleClass
              if (moduleClass != Symbol.noSymbol) {
                val baseTypes = moduleClass.typeRef.baseClasses.flatMap { cls =>
                  try {
                    Some(moduleClass.typeRef.baseType(cls))
                  } catch {
                    case _: Throwable => None
                  }
                }

                baseTypes.collectFirst {
                  case AppliedType(parent, List(underlying))
                      if parent.typeSymbol.fullName.contains("Newtype") ||
                        parent.typeSymbol.fullName.contains("Subtype") =>
                    underlying
                }
              } else None
            } else None
          case _ => None
        }
      case _ => None
    }

    // Try both original and dealiased
    extractUnderlying(tpe).orElse(extractUnderlying(dealiased))
  }

  // Helper to check if type is an opaque type alias
  private def isOpaqueAlias(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe match {
      case tr: TypeRef => tr.isOpaqueAlias
      case _           => false
    }
  }

  // Helper to get underlying type of opaque type
  private def getOpaqueUnderlying(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    tpe match {
      case tr: TypeRef if tr.isOpaqueAlias =>
        Some(tr.translucentSuperType.dealias)
      case _ => None
    }
  }

  /**
   * Check if a type is a product type (case class or tuple).
   */
  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // Check for case class
    val isCaseClass = tpe.classSymbol.exists(_.flags.is(Flags.Case))

    // Check for tuple (including generic tuples)
    val isTuple = tpe <:< TypeRepr.of[Product] && {
      val sym = tpe.typeSymbol
      sym.fullName.startsWith("scala.Tuple") ||
      sym.fullName == "scala.Product"
    }

    isCaseClass || isTuple
  }

  private def getTypeArg(using Quotes)(tpe: quotes.reflect.TypeRepr, index: Int): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    tpe match {
      case AppliedType(_, args) if args.size > index => args(index)
      case _                                         => TypeRepr.of[Any]
    }
  }

  /**
   * Generate a readable description of field mapping for error messages.
   */
  def describeMapping(
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo],
    mapping: Seq[(Int, Int)]
  ): String = {
    val lines = mapping.map { case (sourceIdx, targetIdx) =>
      val source = sourceFields(sourceIdx)
      val target = targetFields(targetIdx)
      s"  ${source.name}: ${source.tpe} -> ${target.name}: ${target.tpe}"
    }
    "Field mapping:\n" + lines.mkString("\n")
  }
}
