package zio.blocks.schema.convert

import scala.quoted.*
import zio.blocks.schema.{MacroUtils, SchemaError}

trait IntoVersionSpecific {
  inline def derived[A, B]: Into[A, B] = ${ IntoVersionSpecificImpl.derived[A, B] }
}

private object IntoVersionSpecificImpl {
  def derived[A: Type, B: Type](using Quotes): Expr[Into[A, B]] =
    new IntoVersionSpecificImpl().derive[A, B]
}

private class IntoVersionSpecificImpl(using Quotes) extends MacroUtils {
  import quotes.reflect.*

  // === Derivation logic ===

  def derive[A: Type, B: Type]: Expr[Into[A, B]] = {
    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    val aIsProduct = aTpe.classSymbol.exists(isProductType)
    val bIsProduct = bTpe.classSymbol.exists(isProductType)
    val aIsTuple = isTupleType(aTpe)
    val bIsTuple = isTupleType(bTpe)

    (aIsProduct, bIsProduct, aIsTuple, bIsTuple) match {
      case (true, true, _, _) =>
        // Case class to case class
        deriveProductToProduct[A, B](aTpe, bTpe)
      case (true, _, _, true) =>
        // Case class to tuple
        deriveCaseClassToTuple[A, B](aTpe, bTpe)
      case (_, true, true, _) =>
        // Tuple to case class
        deriveTupleToCaseClass[A, B](aTpe, bTpe)
      case (_, _, true, true) =>
        // Tuple to tuple
        deriveTupleToTuple[A, B](aTpe, bTpe)
      case _ =>
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: unsupported type combination")
    }
  }

  private def isTupleType(tpe: TypeRepr): Boolean = {
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)
  }

  private def getTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] = {
    if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
    else typeArgs(tpe)
  }

  private def deriveProductToProduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    // Get product info for source and target types
    val sourceInfo = new ProductInfo[A](aTpe)
    val targetInfo = new ProductInfo[B](bTpe)

    // Match fields using priority: name+type, then unique type
    val fieldMappings = matchFields(sourceInfo, targetInfo, aTpe, bTpe)

    // Generate the conversion function
    generateProductConversion[A, B](sourceInfo, targetInfo, fieldMappings)
  }

  private case class FieldMapping(
    sourceField: FieldInfo,
    targetField: FieldInfo
  )

  private def matchFields(
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): List[FieldMapping] = {
    // The macro establishes field mappings using three attributes:
    // - Field name (identifier in source code)
    // - Field position (ordinal position in declaration)
    // - Field type (including coercible types)
    //
    // Priority for disambiguation:
    // 1. Exact match: Same name + same type
    // 2. Name match with coercion: Same name + coercible type
    // 3. Unique type match: Type appears only once in both source and target
    // 4. Position + unique type: Positional correspondence with unambiguous type
    // 5. Fallback: If no unambiguous mapping exists, derivation fails at compile-time

    // Pre-compute type frequencies for unique type matching
    val sourceTypeFreq = sourceInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val targetTypeFreq = targetInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap

    // Track which source fields have been used
    val usedSourceFields = scala.collection.mutable.Set[Int]()

    targetInfo.fields.map { targetField =>
      findMatchingSourceField(
        targetField,
        sourceInfo,
        sourceTypeFreq,
        targetTypeFreq,
        usedSourceFields,
        aTpe,
        bTpe
      ) match {
        case Some(sourceField) =>
          usedSourceFields += sourceField.index
          FieldMapping(sourceField, targetField)
        case None =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
              s"no matching field found for '${targetField.name}: ${targetField.tpe.show}' in source type. " +
              s"Fields must match by: (1) name+type, (2) name+coercible type, (3) unique type, or (4) position+unique type."
          )
      }
    }
  }

  private def findMatchingSourceField(
    targetField: FieldInfo,
    sourceInfo: ProductInfo[?],
    sourceTypeFreq: Map[String, Int],
    targetTypeFreq: Map[String, Int],
    usedSourceFields: scala.collection.mutable.Set[Int],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Option[FieldInfo] = {
    // Priority 1: Exact match - same name + same type
    val exactMatch = sourceInfo.fields.find { sourceField =>
      !usedSourceFields.contains(sourceField.index) &&
        sourceField.name == targetField.name &&
        sourceField.tpe =:= targetField.tpe
    }
    if (exactMatch.isDefined) return exactMatch

    // Priority 2: Name match with coercion - same name + coercible type
    val nameWithCoercion = sourceInfo.fields.find { sourceField =>
      !usedSourceFields.contains(sourceField.index) &&
        sourceField.name == targetField.name &&
        isCoercible(sourceField.tpe, targetField.tpe)
    }
    if (nameWithCoercion.isDefined) return nameWithCoercion

    // Priority 3: Unique type match - type appears only once in both source and target
    val targetTypeKey = targetField.tpe.dealias.show
    val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1

    if (isTargetTypeUnique) {
      val uniqueTypeMatch = sourceInfo.fields.find { sourceField =>
        if (usedSourceFields.contains(sourceField.index)) false
        else {
          val sourceTypeKey = sourceField.tpe.dealias.show
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceTypeKey, 0) == 1
          isSourceTypeUnique && sourceField.tpe =:= targetField.tpe
        }
      }
      if (uniqueTypeMatch.isDefined) return uniqueTypeMatch

      // Also check for unique coercible type match
      val uniqueCoercibleMatch = sourceInfo.fields.find { sourceField =>
        if (usedSourceFields.contains(sourceField.index)) false
        else {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceField.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && isCoercible(sourceField.tpe, targetField.tpe)
        }
      }
      if (uniqueCoercibleMatch.isDefined) return uniqueCoercibleMatch
    }

    // Priority 4: Position + matching type - positional correspondence with matching type
    if (targetField.index < sourceInfo.fields.size) {
      val positionalField = sourceInfo.fields(targetField.index)
      if (!usedSourceFields.contains(positionalField.index)) {
        if (positionalField.tpe =:= targetField.tpe) {
          return Some(positionalField)
        }
        // Also check coercible for positional
        if (isCoercible(positionalField.tpe, targetField.tpe)) {
          return Some(positionalField)
        }
      }
    }

    // Fallback: No match found
    None
  }

  /** Check if source type can be coerced to target type (e.g., Int -> Long) */
  private def isCoercible(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {
    // For now, only exact type match is supported
    // TODO: Add numeric widening (Byte->Short->Int->Long, Float->Double)
    // TODO: Add collection coercion (List[Int] -> List[Long])
    sourceTpe =:= targetTpe
  }

  // === Case Class to Tuple ===

  private def deriveCaseClassToTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceInfo = new ProductInfo[A](aTpe)
    val targetTypeArgs = getTupleTypeArgs(bTpe)

    // Check field count matches
    if (sourceInfo.fields.size != targetTypeArgs.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: field count mismatch (${sourceInfo.fields.size} vs ${targetTypeArgs.size})")
    }

    // Check types match by position
    sourceInfo.fields.zip(targetTypeArgs).zipWithIndex.foreach { case ((field, targetTpe), idx) =>
      if (!(field.tpe =:= targetTpe) && !isCoercible(field.tpe, targetTpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx: ${field.tpe.show} vs ${targetTpe.show}")
      }
    }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = {
          Right(${ constructTupleFromCaseClass[A, B](sourceInfo, bTpe, 'a) })
        }
      }
    }
  }

  private def constructTupleFromCaseClass[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    bTpe: TypeRepr,
    aExpr: Expr[A]
  ): Expr[B] = {
    val args = sourceInfo.fields.map { field =>
      sourceInfo.fieldGetter(aExpr.asTerm, field)
    }

    // Create tuple using Tuple.apply or TupleN constructor
    val tupleSize = args.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod = tupleCompanion.methodMember("apply").head
      Apply(
        Select(Ref(tupleCompanion), applyMethod).appliedToTypes(sourceInfo.fields.map(_.tpe)),
        args
      ).asExprOf[B]
    } else {
      fail(s"Tuples with more than 22 elements are not supported")
    }
  }

  // === Tuple to Case Class ===

  private def deriveTupleToCaseClass[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val targetInfo = new ProductInfo[B](bTpe)

    // Check field count matches
    if (sourceTypeArgs.size != targetInfo.fields.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: field count mismatch (${sourceTypeArgs.size} vs ${targetInfo.fields.size})")
    }

    // Check types match by position
    sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.foreach { case ((sourceTpe, field), idx) =>
      if (!(sourceTpe =:= field.tpe) && !isCoercible(sourceTpe, field.tpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx: ${sourceTpe.show} vs ${field.tpe.show}")
      }
    }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = {
          Right(${ constructCaseClassFromTuple[A, B](aTpe, targetInfo, 'a) })
        }
      }
    }
  }

  private def constructCaseClassFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    targetInfo: ProductInfo[B],
    aExpr: Expr[A]
  ): Expr[B] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args = sourceTypeArgs.zipWithIndex.map { case (tpe, idx) =>
      // Access tuple element using _1, _2, etc.
      val accessorName = s"_${idx + 1}"
      val accessor = aTpe.typeSymbol.methodMember(accessorName).head
      Select(aExpr.asTerm, accessor)
    }

    targetInfo.construct(args).asExprOf[B]
  }

  // === Tuple to Tuple ===

  private def deriveTupleToTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val targetTypeArgs = getTupleTypeArgs(bTpe)

    // Check element count matches
    if (sourceTypeArgs.size != targetTypeArgs.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: element count mismatch (${sourceTypeArgs.size} vs ${targetTypeArgs.size})")
    }

    // Check types match by position
    sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.foreach { case ((sourceTpe, targetTpe), idx) =>
      if (!(sourceTpe =:= targetTpe) && !isCoercible(sourceTpe, targetTpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx: ${sourceTpe.show} vs ${targetTpe.show}")
      }
    }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = {
          Right(${ constructTupleFromTuple[A, B](aTpe, bTpe, 'a) })
        }
      }
    }
  }

  private def constructTupleFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    aExpr: Expr[A]
  ): Expr[B] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args = sourceTypeArgs.zipWithIndex.map { case (tpe, idx) =>
      // Access tuple element using _1, _2, etc.
      val accessorName = s"_${idx + 1}"
      val accessor = aTpe.typeSymbol.methodMember(accessorName).head
      Select(aExpr.asTerm, accessor)
    }

    val tupleSize = args.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod = tupleCompanion.methodMember("apply").head
      val targetTypeArgs = getTupleTypeArgs(bTpe)
      Apply(
        Select(Ref(tupleCompanion), applyMethod).appliedToTypes(targetTypeArgs),
        args
      ).asExprOf[B]
    } else {
      fail(s"Tuples with more than 22 elements are not supported")
    }
  }

  // === Case Class to Case Class ===

  private def generateProductConversion[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[Into[A, B]] = {
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = {
          Right(${ constructTarget[A, B](sourceInfo, targetInfo, fieldMappings, 'a) })
        }
      }
    }
  }

  private def constructTarget[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping],
    aExpr: Expr[A]
  ): Expr[B] = {
    // Build arguments in target field order by reading from source using getters
    val args = fieldMappings.map { mapping =>
      sourceInfo.fieldGetter(aExpr.asTerm, mapping.sourceField)
    }

    targetInfo.construct(args).asExprOf[B]
  }
}


