package zio.blocks.schema.derive

import scala.reflect.macros.blackbox.Context

/**
 * Field information for mapping algorithm.
 *
 * @param name
 *   Field name
 * @param tpe
 *   Field type representation (stored as string to avoid quote context issues)
 * @param tpeRepr
 *   Field type (stored as Any to be version-independent in signatures, but is
 *   c.Type at runtime)
 * @param position
 *   Field position in parameter list
 */
private[schema] case class FieldInfo(
  name: String,
  tpe: String,
  tpeRepr: Any,
  position: Int
)

/**
 * Algorithm for mapping fields between source and target types. Implements a
 * 5-level disambiguation strategy:
 *   1. Exact match: same name + same type
 *   2. Name match with coercion: same name + coercible type
 *   3. Unique type match: type appears exactly once in both source and target
 *   4. Position match: fallback to positional mapping
 *   5. Error: ambiguous or impossible mapping
 */
private[schema] object FieldMapper {

  /**
   * Map source fields to target fields.
   *
   * @param c
   *   Compilation context
   * @param sourceFields
   *   Fields from source type
   * @param targetFields
   *   Fields from target type
   * @return
   *   Right with mapping (sourceIndex -> targetIndex) or Left with error
   *   message
   */
  def mapFields(c: Context)(
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo]
  ): Either[String, Seq[(Int, Int)]] = {
    import c.universe._

    type Type = c.Type

    if (targetFields.isEmpty) {
      return Right(Seq.empty)
    }

    if (sourceFields.size < targetFields.size) {
      return Left(
        s"Cannot map: source has ${sourceFields.size} fields but target requires ${targetFields.size} fields"
      )
    }

    val mappings          = scala.collection.mutable.ArrayBuffer[(Int, Int)]()
    val usedSourceIndices = scala.collection.mutable.Set[Int]()

    /**
     * Check if source type can be coerced to target type.
     */
    def isCoercible(source: Type, target: Type): Boolean = {
      val intTpe    = typeOf[Int]
      val longTpe   = typeOf[Long]
      val floatTpe  = typeOf[Float]
      val doubleTpe = typeOf[Double]
      val byteTpe   = typeOf[Byte]
      val shortTpe  = typeOf[Short]

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

      // Collection conversions - List, Vector, Set, Seq with element coercion
      val iterableSym = typeOf[Iterable[Any]].typeConstructor
      if (source.typeConstructor =:= iterableSym && target.typeConstructor =:= iterableSym) {
        val sourceElem = getTypeArg(source, 0)
        val targetElem = getTypeArg(target, 0)
        // Recursively check if elements are coercible
        return sourceElem =:= targetElem || isCoercible(sourceElem, targetElem)
      }

      // Option conversions
      val optionSym = typeOf[Option[Any]].typeConstructor
      if (source.typeConstructor =:= optionSym && target.typeConstructor =:= optionSym) {
        val sourceElem = getTypeArg(source, 0)
        val targetElem = getTypeArg(target, 0)
        return sourceElem =:= targetElem || isCoercible(sourceElem, targetElem)
      }

      // Map conversions
      val mapSym = typeOf[Map[Any, Any]].typeConstructor
      if (source.typeConstructor =:= mapSym && target.typeConstructor =:= mapSym) {
        val sourceKey      = getTypeArg(source, 0)
        val sourceValue    = getTypeArg(source, 1)
        val targetKey      = getTypeArg(target, 0)
        val targetValue    = getTypeArg(target, 1)
        val keyCoercible   = sourceKey =:= targetKey || isCoercible(sourceKey, targetKey)
        val valueCoercible = sourceValue =:= targetValue || isCoercible(sourceValue, targetValue)
        return keyCoercible && valueCoercible
      }

      // Either conversions
      val eitherSym = typeOf[Either[Any, Any]].typeConstructor
      if (source.typeConstructor =:= eitherSym && target.typeConstructor =:= eitherSym) {
        val sourceLeft     = getTypeArg(source, 0)
        val sourceRight    = getTypeArg(source, 1)
        val targetLeft     = getTypeArg(target, 0)
        val targetRight    = getTypeArg(target, 1)
        val leftCoercible  = sourceLeft =:= targetLeft || isCoercible(sourceLeft, targetLeft)
        val rightCoercible = sourceRight =:= targetRight || isCoercible(sourceRight, targetRight)
        return leftCoercible && rightCoercible
      }

      false
    }

    def getTypeArg(tpe: Type, index: Int): Type =
      if (tpe.typeArgs.size > index) tpe.typeArgs(index)
      else typeOf[Any]

    // Level 1: Exact match (name + type)
    for (targetIdx <- targetFields.indices) {
      val targetField = targetFields(targetIdx)
      val targetType  = targetField.tpeRepr.asInstanceOf[Type]
      sourceFields.zipWithIndex.find { case (sourceField, sourceIdx) =>
        val sourceType = sourceField.tpeRepr.asInstanceOf[Type]
        !usedSourceIndices.contains(sourceIdx) &&
        sourceField.name == targetField.name &&
        sourceType =:= targetType
      } match {
        case Some((_, sourceIdx)) =>
          mappings.addOne((sourceIdx, targetIdx))
          usedSourceIndices.add(sourceIdx)
        case None => ()
      }
    }

    // Level 2: Name match with coercible type
    for (targetIdx <- targetFields.indices if !mappings.exists(_._2 == targetIdx)) {
      val targetField = targetFields(targetIdx)
      val targetType  = targetField.tpeRepr.asInstanceOf[Type]
      sourceFields.zipWithIndex.find { case (sourceField, sourceIdx) =>
        val sourceType = sourceField.tpeRepr.asInstanceOf[Type]
        !usedSourceIndices.contains(sourceIdx) &&
        sourceField.name == targetField.name &&
        isCoercible(sourceType, targetType)
      } match {
        case Some((_, sourceIdx)) =>
          mappings.addOne((sourceIdx, targetIdx))
          usedSourceIndices.add(sourceIdx)
        case None => ()
      }
    }

    // Level 3: Unique type match
    for (targetIdx <- targetFields.indices if !mappings.exists(_._2 == targetIdx)) {
      val targetField = targetFields(targetIdx)
      val targetType  = targetField.tpeRepr.asInstanceOf[Type]

      // Count how many times this type appears in target (excluding already mapped)
      val targetTypeCount = targetFields.zipWithIndex.count { case (f, idx) =>
        val fType = f.tpeRepr.asInstanceOf[Type]
        fType =:= targetType && !mappings.exists(_._2 == idx)
      }

      if (targetTypeCount == 1) {
        // Find matching source field with same unique type
        val candidateSources = sourceFields.zipWithIndex.filter { case (sourceField, sourceIdx) =>
          val sourceType = sourceField.tpeRepr.asInstanceOf[Type]
          !usedSourceIndices.contains(sourceIdx) && sourceType =:= targetType
        }

        val sourceTypeCount = sourceFields.count { f =>
          val fType = f.tpeRepr.asInstanceOf[Type]
          fType =:= targetType
        }

        if (candidateSources.size == 1 && sourceTypeCount == 1) {
          val (_, sourceIdx) = candidateSources.head
          mappings.addOne((sourceIdx, targetIdx))
          usedSourceIndices.add(sourceIdx)
        }
      }
    }

    // Level 4: Positional match (only if all previous levels didn't fully resolve)
    val unmappedTargetIndices = targetFields.indices.filterNot(idx => mappings.exists(_._2 == idx))
    if (unmappedTargetIndices.nonEmpty) {
      // Try positional matching for remaining fields
      val unmappedSourceIndices = sourceFields.indices.filterNot(usedSourceIndices.contains)

      if (unmappedSourceIndices.size >= unmappedTargetIndices.size) {
        for ((targetIdx, offset) <- unmappedTargetIndices.zipWithIndex) {
          val sourceIdx   = unmappedSourceIndices(offset)
          val sourceField = sourceFields(sourceIdx)
          val targetField = targetFields(targetIdx)
          val sourceType  = sourceField.tpeRepr.asInstanceOf[Type]
          val targetType  = targetField.tpeRepr.asInstanceOf[Type]

          // Check if types are compatible
          if (sourceType =:= targetType || isCoercible(sourceType, targetType)) {
            mappings.addOne((sourceIdx, targetIdx))
            usedSourceIndices.add(sourceIdx)
          } else {
            return Left(
              s"Positional mapping failed: field at position $targetIdx expects type ${targetField.tpe} " +
                s"but source position $sourceIdx has type ${sourceField.tpe}"
            )
          }
        }
      }
    }

    // Level 5: Error if not all target fields are mapped
    val stillUnmapped = targetFields.indices.filterNot(idx => mappings.exists(_._2 == idx))
    if (stillUnmapped.nonEmpty) {
      val unmappedNames = stillUnmapped.map(idx => targetFields(idx).name).mkString(", ")
      return Left(s"Cannot map target fields: $unmappedNames. Mapping is ambiguous or impossible.")
    }

    Right(mappings.toSeq.sortBy(_._2))
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
