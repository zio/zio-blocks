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
    // For each target field, find a matching source field using priority:
    // 1. Exact match: same name + same type
    // 2. Name match with same type (already covered by #1)
    // 3. Unique type match: type appears exactly once in both source and target
    
    // Pre-compute type frequencies for unique type matching
    val sourceTypeFreq = sourceInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val targetTypeFreq = targetInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    
    // Track which source fields have been used (for unique type matching)
    val usedSourceFields = scala.collection.mutable.Set[Int]()
    
    targetInfo.fields.map { targetField =>
      // Priority 1: Exact match by name and type
      val exactMatch = sourceInfo.fields.find { sourceField =>
        sourceField.name == targetField.name && sourceField.tpe =:= targetField.tpe
      }
      
      exactMatch match {
        case Some(sourceField) => 
          usedSourceFields += sourceField.index
          FieldMapping(sourceField, targetField)
        case None =>
          // Priority 2: Unique type match
          val targetTypeKey = targetField.tpe.dealias.show
          val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1
          
          val uniqueTypeMatch = if (isTargetTypeUnique) {
            sourceInfo.fields.find { sourceField =>
              val sourceTypeKey = sourceField.tpe.dealias.show
              val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceTypeKey, 0) == 1
              isSourceTypeUnique && 
                sourceField.tpe =:= targetField.tpe && 
                !usedSourceFields.contains(sourceField.index)
            }
          } else None
          
          uniqueTypeMatch match {
            case Some(sourceField) =>
              usedSourceFields += sourceField.index
              FieldMapping(sourceField, targetField)
            case None =>
              fail(
                s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
                s"no matching field found for '${targetField.name}: ${targetField.tpe.show}' in source type. " +
                s"Fields must match by name+type or have unique types."
              )
          }
      }
    }
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

