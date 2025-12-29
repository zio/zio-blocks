package zio.blocks.schema

import scala.quoted.*

trait AsVersionSpecific {

  /**
   * Derives a bidirectional conversion As[A, B].
   *
   * This macro will:
   *   1. Verify that both Into[A, B] and Into[B, A] can be derived
   *   2. Check compatibility rules (no default values, consistent field
   *      mappings)
   *   3. Generate an As[A, B] that delegates to both derived Into instances
   */
  inline def derived[A, B]: As[A, B] = ${ AsVersionSpecificImpl.derived[A, B] }
}

private object AsVersionSpecificImpl {
  def derived[A: Type, B: Type](using Quotes): Expr[As[A, B]] =
    new AsVersionSpecificImpl().derive[A, B]
}

private class AsVersionSpecificImpl(using Quotes) extends MacroUtils {
  import quotes.reflect.*

  def derive[A: Type, B: Type]: Expr[As[A, B]] = {
    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    val aIsProduct   = aTpe.classSymbol.exists(isProductType)
    val bIsProduct   = bTpe.classSymbol.exists(isProductType)
    val aIsTuple     = isTupleType(aTpe)
    val bIsTuple     = isTupleType(bTpe)
    val aIsCoproduct = isCoproductType(aTpe)
    val bIsCoproduct = isCoproductType(bTpe)

    // Perform compatibility checks based on type category
    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct) match {
      case (true, true, _, _, _, _) =>
        // Case class to case class
        val aInfo = new ProductInfoCompat[A](aTpe)
        val bInfo = new ProductInfoCompat[B](bTpe)

        // Check no default values
        checkNoDefaultValues(aInfo, "source", aTpe, bTpe)
        checkNoDefaultValues(bInfo, "target", aTpe, bTpe)

        // Check field mapping consistency
        checkFieldMappingConsistency(aInfo, bInfo, aTpe, bTpe)

      case (true, _, _, true, _, _) | (_, true, true, _, _, _) =>
        // Case class to/from tuple
        if (aIsProduct) {
          val aInfo = new ProductInfoCompat[A](aTpe)
          checkNoDefaultValues(aInfo, "source", aTpe, bTpe)
        }
        if (bIsProduct) {
          val bInfo = new ProductInfoCompat[B](bTpe)
          checkNoDefaultValues(bInfo, "target", aTpe, bTpe)
        }

      case (_, _, true, true, _, _) =>
      // Tuple to tuple - no default value checks needed

      case (_, _, _, _, true, true) =>
      // Coproduct to coproduct - no additional checks needed

      case _ =>
      // Try to derive anyway - the Into macros will fail if not possible
    }

    // Use inline expansion to derive both Into instances
    '{
      val intoAB: Into[A, B] = Into.derived[A, B]
      val intoBA: Into[B, A] = Into.derived[B, A]

      new As[A, B] {
        def into(input: A): Either[SchemaError, B] = intoAB.into(input)
        def from(input: B): Either[SchemaError, A] = intoBA.into(input)
      }
    }
  }

  private def isTupleType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)

  private def isCoproductType(tpe: TypeRepr): Boolean =
    isSealedTraitOrAbstractClass(tpe) || isEnum(tpe)

  private def isEnum(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Enum) && !tpe.typeSymbol.flags.is(Flags.Case)

  // === Field Info for compatibility checks ===

  private case class FieldInfoCompat(
    name: String,
    tpe: TypeRepr,
    index: Int,
    hasDefault: Boolean
  )

  private class ProductInfoCompat[T](tpe: TypeRepr)(using Type[T]) {
    val fields: List[FieldInfoCompat] = {
      val sym         = tpe.classSymbol.getOrElse(report.errorAndAbort(s"${tpe.show} is not a class"))
      val constructor = sym.primaryConstructor

      // Get type args from applied type
      val tpeTypeArgs = typeArgs(tpe)

      // Get parameter lists, filtering out type parameters
      val (tpeTypeParams, tpeParams) = constructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }

      var idx = 0
      tpeParams.flatten.map { paramSym =>
        val name     = paramSym.name
        var paramTpe = tpe.memberType(paramSym).dealias
        if (tpeTypeArgs.nonEmpty) {
          paramTpe = paramTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
        }
        val hasDefault = paramSym.flags.is(Flags.HasDefault)
        val field      = FieldInfoCompat(name, paramTpe, idx, hasDefault)
        idx += 1
        field
      }
    }
  }

  // === Compatibility Checks ===

  private def checkNoDefaultValues[A, B](
    info: ProductInfoCompat[?],
    direction: String,
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val fieldsWithDefaults = info.fields.filter(_.hasDefault)
    if (fieldsWithDefaults.nonEmpty) {
      val defaultFieldsStr = fieldsWithDefaults.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
      fail(
        s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Default values break round-trip guarantee
           |
           |$direction type has fields with default values: $defaultFieldsStr
           |
           |Default values break the round-trip guarantee because:
           |  - When converting A → B, we cannot distinguish between explicitly set default values
           |    and fields that were omitted
           |  - When converting B → A, we don't know if a value was originally a default or explicit
           |
           |Consider:
           |  - Removing default values from the type definition
           |  - Using Into[A, B] instead (one-way conversion allows defaults)
           |  - Making fields Option types instead of using defaults""".stripMargin
      )
    }
  }

  private def isOptionType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Option[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def isListType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[List[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def isVectorType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Vector[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def isSetType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Set[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def isSeqType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Seq[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def getContainerElementType(tpe: TypeRepr): Option[TypeRepr] =
    typeArgs(tpe.dealias).headOption

  /**
   * Checks if two container types (Option, List, Vector, Set, Seq) have
   * bidirectionally convertible element types (including when elements have
   * implicit As instances available).
   */
  private def areBidirectionallyConvertibleContainers(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {
    // Check if both are Options
    val bothOptions = isOptionType(sourceTpe) && isOptionType(targetTpe)

    // Check if both are collection types (can be different collection types)
    val bothCollections =
      (isListType(sourceTpe) || isVectorType(sourceTpe) || isSetType(sourceTpe) || isSeqType(sourceTpe)) &&
        (isListType(targetTpe) || isVectorType(targetTpe) || isSetType(targetTpe) || isSeqType(targetTpe))

    if (bothOptions || bothCollections) {
      (getContainerElementType(sourceTpe), getContainerElementType(targetTpe)) match {
        case (Some(sourceElem), Some(targetElem)) =>
          // Same element type - trivially convertible
          if (sourceElem =:= targetElem) {
            true
          } else {
            // Check if element types are bidirectionally convertible
            val hasAsInstance = isImplicitAsAvailable(sourceElem, targetElem)
            if (hasAsInstance) {
              true
            } else {
              // Check numeric coercion and Into instances
              val canConvertElems = isNumericCoercible(sourceElem, targetElem) ||
                isImplicitIntoAvailable(sourceElem, targetElem)
              val canConvertElemsBack = isNumericCoercible(targetElem, sourceElem) ||
                isImplicitIntoAvailable(targetElem, sourceElem)

              canConvertElems && canConvertElemsBack
            }
          }
        case _ => false
      }
    } else {
      false
    }
  }

  private def checkFieldMappingConsistency(
    sourceInfo: ProductInfoCompat[?],
    targetInfo: ProductInfoCompat[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
    val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

    // Check: fields that exist in both must have compatible types
    sourceFieldsByName.foreach { case (name, sourceField) =>
      targetFieldsByName.get(name) match {
        case Some(targetField) =>
          // Both have this field - types must be convertible in both directions
          if (!(sourceField.tpe =:= targetField.tpe)) {
            // Check if As[source, target] is available (bidirectional)
            val hasAsInstance = isImplicitAsAvailable(sourceField.tpe, targetField.tpe)

            if (hasAsInstance) {
              // As instance provides both directions
            } else {
              // Check for container types (Option, List, etc.) with different element types
              val containerConvertible = areBidirectionallyConvertibleContainers(sourceField.tpe, targetField.tpe)

              if (containerConvertible) {
                // Container types with bidirectionally convertible elements
              } else {
                // Fall back to checking Into in both directions
                val canConvert = isNumericCoercible(sourceField.tpe, targetField.tpe) ||
                  requiresOpaqueConversion(sourceField.tpe, targetField.tpe) ||
                  requiresOpaqueUnwrapping(sourceField.tpe, targetField.tpe) ||
                  requiresNewtypeConversion(sourceField.tpe, targetField.tpe) ||
                  requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe) ||
                  isImplicitIntoAvailable(sourceField.tpe, targetField.tpe)

                val canConvertBack = isNumericCoercible(targetField.tpe, sourceField.tpe) ||
                  requiresOpaqueConversion(targetField.tpe, sourceField.tpe) ||
                  requiresOpaqueUnwrapping(targetField.tpe, sourceField.tpe) ||
                  requiresNewtypeConversion(targetField.tpe, sourceField.tpe) ||
                  requiresNewtypeUnwrapping(targetField.tpe, sourceField.tpe) ||
                  isImplicitIntoAvailable(targetField.tpe, sourceField.tpe)

                if (!canConvert || !canConvertBack) {
                  val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
                  val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
                  val direction       = if (!canConvert) "A → B" else "B → A"
                  fail(
                    s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Field not bidirectionally convertible
                       |
                       |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                       |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                       |
                       |Field '$name' cannot be converted in both directions:
                       |  Source: ${sourceField.tpe.show}
                       |  Target: ${targetField.tpe.show}
                       |  Missing: $direction conversion
                       |
                       |As[A, B] requires:
                       |  - Into[A, B] for A → B conversion
                       |  - Into[B, A] for B → A conversion (round-trip)
                       |
                       |Consider:
                       |  - Using matching types on both sides
                       |  - Providing implicit As[${sourceField.tpe.show}, ${targetField.tpe.show}]
                       |  - Using Into[A, B] instead if one-way conversion is sufficient""".stripMargin
                  )
                }
              }
            }
          }
        case None =>
        // Source has field that target doesn't have - this is OK
        // Extra fields get dropped when going to target
      }
    }
  }

  private def isNumericCoercible(from: TypeRepr, to: TypeRepr): Boolean = {
    val numericTypes = List(
      TypeRepr.of[Byte],
      TypeRepr.of[Short],
      TypeRepr.of[Int],
      TypeRepr.of[Long],
      TypeRepr.of[Float],
      TypeRepr.of[Double]
    )

    val fromIdx = numericTypes.indexWhere(t => from =:= t)
    val toIdx   = numericTypes.indexWhere(t => to =:= t)

    // Any numeric type can convert to any other with runtime validation
    fromIdx >= 0 && toIdx >= 0
  }

  private def isImplicitIntoAvailable(from: TypeRepr, to: TypeRepr): Boolean = {
    val intoTpeApplied = TypeRepr.of[Into].typeSymbol.typeRef.appliedTo(List(from, to))
    Implicits.search(intoTpeApplied) match {
      case _: ImplicitSearchSuccess => true
      case _                        => false
    }
  }

  private def isImplicitAsAvailable(from: TypeRepr, to: TypeRepr): Boolean = {
    val asTpeApplied = TypeRepr.of[As].typeSymbol.typeRef.appliedTo(List(from, to))
    Implicits.search(asTpeApplied) match {
      case _: ImplicitSearchSuccess => true
      case _                        => false
    }
  }

  // === Opaque and Newtype Helpers ===

  private def requiresOpaqueConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isOpaque(targetTpe) && {
      val underlying = opaqueDealias(targetTpe)
      sourceTpe =:= underlying
    }

  private def requiresOpaqueUnwrapping(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isOpaque(sourceTpe) && {
      val underlying = opaqueDealias(sourceTpe)
      underlying =:= targetTpe
    }

  private def requiresNewtypeConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isZioPreludeNewtype(targetTpe) && {
      val underlying = zioPreludeNewtypeDealias(targetTpe)
      sourceTpe =:= underlying
    }

  private def requiresNewtypeUnwrapping(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isZioPreludeNewtype(sourceTpe) && {
      val underlying = zioPreludeNewtypeDealias(sourceTpe)
      underlying =:= targetTpe
    }
}
