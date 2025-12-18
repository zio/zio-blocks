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

    (aTpe.classSymbol, bTpe.classSymbol) match {
      case (Some(aClass), Some(bClass)) if isProductType(aClass) && isProductType(bClass) =>
        deriveProductToProduct[A, B](aTpe, bTpe)
      case _ =>
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: only case class to case class is currently supported")
    }
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


