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

    // Handle DynamicValue conversions first - skip all compatibility checks
    val dynamicValueTpe = TypeRepr.of[DynamicValue]
    if (bTpe =:= dynamicValueTpe) {
      return deriveToDynamicValue[A, B]
    }
    if (aTpe =:= dynamicValueTpe) {
      return deriveFromDynamicValue[A, B]
    }

    val aIsProduct    = aTpe.classSymbol.exists(isProductType)
    val bIsProduct    = bTpe.classSymbol.exists(isProductType)
    val aIsTuple      = isTupleType(aTpe)
    val bIsTuple      = isTupleType(bTpe)
    val aIsCoproduct  = isCoproductType(aTpe)
    val bIsCoproduct  = isCoproductType(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)

    // Perform compatibility checks based on type category
    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct, aIsStructural, bIsStructural) match {
      case (_, _, true, true, _, _, _, _) =>
      // Tuple to tuple - no default value checks needed, positional matching

      case (true, _, _, true, _, _, _, _) | (_, true, true, _, _, _, _, _) =>
      // Product to tuple or tuple to product - use positional matching, no field name checks needed
      // Tuples use positional matching so field names (_1, _2, etc.) don't need to match case class field names

      case (true, true, _, _, _, _, _, _) =>
        val aInfo = new ProductInfoCompat[A](aTpe)
        val bInfo = new ProductInfoCompat[B](bTpe)

        checkNoDefaultValues(aInfo, bInfo, "source", aTpe, bTpe)
        checkNoDefaultValues(bInfo, aInfo, "target", aTpe, bTpe)

        checkFieldMappingConsistency(aInfo, bInfo, aTpe, bTpe)

      case (true, _, _, _, _, _, _, true) =>
        // Case class to structural type
        val aInfo = new ProductInfoCompat[A](aTpe)
        val bInfo = new StructuralInfoCompat(bTpe)

        // Check field mapping consistency for structural types
        checkStructuralFieldMappingConsistency(aInfo, bInfo, aTpe, bTpe)

      case (_, true, _, _, _, _, true, _) =>
        // Structural type to case class
        val aInfo = new StructuralInfoCompat(aTpe)
        val bInfo = new ProductInfoCompat[B](bTpe)

        // Check field mapping consistency for structural types
        checkStructuralFieldMappingConsistencyReverse(aInfo, bInfo, aTpe, bTpe)

      case (_, _, _, _, true, true, _, _) =>
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

  private def deriveToDynamicValue[A: Type, B: Type]: Expr[As[A, B]] = {
    val aTpe   = TypeRepr.of[A]
    val schema = findImplicitOrDeriveSchema[A](aTpe)
    '{
      new As[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right($schema.toDynamicValue(a).asInstanceOf[B])
        def from(b: B): Either[SchemaError, A] =
          $schema.fromDynamicValue(b.asInstanceOf[DynamicValue])
      }
    }
  }

  private def deriveFromDynamicValue[A: Type, B: Type]: Expr[As[A, B]] = {
    val bTpe   = TypeRepr.of[B]
    val schema = findImplicitOrDeriveSchema[B](bTpe)
    '{
      new As[A, B] {
        def into(a: A): Either[SchemaError, B] =
          $schema.fromDynamicValue(a.asInstanceOf[DynamicValue])
        def from(b: B): Either[SchemaError, A] =
          Right($schema.toDynamicValue(b).asInstanceOf[A])
      }
    }
  }

  private def findImplicitOrDeriveSchema[T: Type](tpe: TypeRepr): Expr[Schema[T]] = {
    val schemaTpe = TypeRepr.of[Schema].appliedTo(tpe)
    Implicits.search(schemaTpe) match {
      case success: ImplicitSearchSuccess =>
        success.tree.asExprOf[Schema[T]]
      case failure: ImplicitSearchFailure =>
        val explanation = failure.explanation.toLowerCase
        if (explanation.contains("ambiguous") || explanation.contains("diverging")) {
          report.errorAndAbort(
            s"Failed to summon Schema[${tpe.show}]: ${failure.explanation}"
          )
        } else {
          '{ Schema.derived[T] }
        }
    }
  }

  private def isTupleType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)

  private def isCoproductType(tpe: TypeRepr): Boolean =
    isSealedTraitOrAbstractClass(tpe) || isEnum(tpe)

  private def isEnum(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Enum) && !tpe.typeSymbol.flags.is(Flags.Case)

  private def isStructuralType(tpe: TypeRepr): Boolean = {
    def findBaseParent(t: TypeRepr): TypeRepr = t match {
      case Refinement(parent, _, _) => findBaseParent(parent)
      case other                    => other
    }
    val dealiased = tpe.dealias
    dealiased match {
      case Refinement(_, _, _) =>
        val base = findBaseParent(dealiased)
        // Check for various representations of Object/AnyRef
        base =:= TypeRepr.of[AnyRef] ||
        base =:= TypeRepr.of[Any] ||
        base =:= TypeRepr.of[Object] ||
        base.typeSymbol.fullName == "java.lang.Object" ||
        base.typeSymbol.fullName == "scala.AnyRef"
      case _ => false
    }
  }

  // === Structural Type Info ===

  private case class StructuralFieldInfo(
    name: String,
    tpe: TypeRepr
  )

  private class StructuralInfoCompat(tpe: TypeRepr) {
    val fields: List[StructuralFieldInfo] = {
      def collectRefinements(t: TypeRepr): List[StructuralFieldInfo] = t match {
        case Refinement(parent, name, info) =>
          val parentFields = collectRefinements(parent)
          info match {
            case MethodType(Nil, Nil, resultTpe) =>
              parentFields :+ StructuralFieldInfo(name, resultTpe)
            case ByNameType(resultTpe) =>
              parentFields :+ StructuralFieldInfo(name, resultTpe)
            case _ =>
              parentFields :+ StructuralFieldInfo(name, info)
          }
        case _ => Nil
      }
      collectRefinements(tpe.dealias)
    }
  }

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
    otherInfo: ProductInfoCompat[?],
    direction: String,
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val otherFieldNames    = otherInfo.fields.map(_.name).toSet
    val fieldsWithDefaults = info.fields.filter(f => f.hasDefault && !otherFieldNames.contains(f.name))
    if (fieldsWithDefaults.nonEmpty) {
      val defaultFieldsStr = fieldsWithDefaults.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
      fail(
        s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Default values break round-trip guarantee
           |
           |$direction type has fields with default values that don't exist in the other type: $defaultFieldsStr
           |
           |Default values break the round-trip guarantee because:
           |  - When converting A → B, we cannot distinguish between explicitly set default values
           |    and fields that were omitted
           |  - When converting B → A, we don't know if a value was originally a default or explicit
           |
           |Note: Default values are allowed on fields that exist in both types.
           |
           |Consider:
           |  - Removing default values from fields that don't exist in the other type
           |  - Using Into[A, B] instead (one-way conversion allows defaults)
           |  - Making these fields Option types instead of using defaults""".stripMargin
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

    // Check: fields that exist in target but NOT in source (the reverse direction)
    // These must be either Optional or have defaults, otherwise the round-trip fails
    targetFieldsByName.foreach { case (name, targetField) =>
      if (!sourceFieldsByName.contains(name)) {
        // Target has a field that source doesn't have
        // When going B → A, this field will be missing
        // For As, only Option fields are allowed (defaults break round-trip guarantee)
        val isOptional = isOptionType(targetField.tpe)

        if (!isOptional) {
          val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          fail(
            s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Missing required field breaks round-trip
               |
               |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
               |  ${bTpe.typeSymbol.name}($targetFieldsStr)
               |
               |Field '$name: ${targetField.tpe.show}' exists in ${bTpe.typeSymbol.name} but not in ${aTpe.typeSymbol.name}
               |
               |When converting B → A (from method), the '$name' field cannot be populated.
               |
               |For As[A, B] to work, missing fields must be Optional (Option[T]).
               |Default values are NOT allowed as they break the round-trip guarantee.
               |
               |Consider:
               |  - Making '$name' an Option type in both A and B
               |  - Using Into[A, B] instead (one-way conversion)""".stripMargin
          )
        }
      }
    }

    // Also check the reverse: fields in source that don't exist in target
    // When going A → B, these get dropped. When coming back B → A, they can't be restored
    // For As, only Optional fields are allowed (defaults break round-trip guarantee)
    sourceFieldsByName.foreach { case (name, sourceField) =>
      if (!targetFieldsByName.contains(name)) {
        // Source has a field that target doesn't have
        // When going A → B → A, this field will be lost
        // For As, only Option fields are allowed
        val isOptional = isOptionType(sourceField.tpe)

        if (!isOptional) {
          val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          fail(
            s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Missing required field breaks round-trip
               |
               |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
               |  ${bTpe.typeSymbol.name}($targetFieldsStr)
               |
               |Field '$name: ${sourceField.tpe.show}' exists in ${aTpe.typeSymbol.name} but not in ${bTpe.typeSymbol.name}
               |
               |When converting A → B → A (round-trip), the '$name' field value will be lost
               |because it cannot be stored in B and restored back.
               |
               |For As[A, B] to work, fields that don't exist in the other type must be Optional (Option[T]).
               |Default values are NOT allowed as they break the round-trip guarantee.
               |
               |Consider:
               |  - Making '$name' an Option type
               |  - Adding the field to ${bTpe.typeSymbol.name}
               |  - Using Into[A, B] instead (one-way conversion)""".stripMargin
          )
        }
      }
    }
  }

  // Check field mapping when source is a case class and target is a structural type
  private def checkStructuralFieldMappingConsistency(
    sourceInfo: ProductInfoCompat[?],
    targetInfo: StructuralInfoCompat,
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
    val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

    // Check: fields in source that don't exist in target
    // When going A → B → A, this field will be lost
    // For As, only Optional fields are allowed (defaults break round-trip guarantee)
    sourceFieldsByName.foreach { case (name, sourceField) =>
      if (!targetFieldsByName.contains(name)) {
        val isOptional = isOptionType(sourceField.tpe)

        if (!isOptional) {
          val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          fail(
            s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Missing required field breaks round-trip
               |
               |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
               |  Structural($targetFieldsStr)
               |
               |Field '$name: ${sourceField.tpe.show}' exists in ${aTpe.typeSymbol.name} but not in the structural type.
               |
               |When converting A → B → A (round-trip), the '$name' field value will be lost
               |because it cannot be stored in the structural type and restored back.
               |
               |For As[A, B] to work, fields that don't exist in the other type must be Optional (Option[T]).
               |Default values are NOT allowed as they break the round-trip guarantee.
               |
               |Consider:
               |  - Making '$name' an Option type
               |  - Adding the field to the structural type
               |  - Using Into[A, B] instead (one-way conversion)""".stripMargin
          )
        }
      }
    }
  }

  // Check field mapping when source is a structural type and target is a case class
  private def checkStructuralFieldMappingConsistencyReverse(
    sourceInfo: StructuralInfoCompat,
    targetInfo: ProductInfoCompat[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
    val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

    // Check: fields in target that don't exist in source
    // When going B → A, this field will be missing
    // For As, only Optional fields are allowed (defaults break round-trip guarantee)
    targetFieldsByName.foreach { case (name, targetField) =>
      if (!sourceFieldsByName.contains(name)) {
        val isOptional = isOptionType(targetField.tpe)

        if (!isOptional) {
          val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
          fail(
            s"""Cannot derive As[${aTpe.show}, ${bTpe.show}]: Missing required field breaks round-trip
               |
               |  Structural($sourceFieldsStr)
               |  ${bTpe.typeSymbol.name}($targetFieldsStr)
               |
               |Field '$name: ${targetField.tpe.show}' exists in ${bTpe.typeSymbol.name} but not in the structural type.
               |
               |When converting B → A (from method), the '$name' field cannot be populated.
               |
               |For As[A, B] to work, missing fields must be Optional (Option[T]).
               |Default values are NOT allowed as they break the round-trip guarantee.
               |
               |Consider:
               |  - Making '$name' an Option type in both types
               |  - Using Into[A, B] instead (one-way conversion)""".stripMargin
          )
        }
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
