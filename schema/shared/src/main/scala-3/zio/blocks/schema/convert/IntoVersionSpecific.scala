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

  // Cache for Into instances to handle recursive resolution
  private val intoRefs = scala.collection.mutable.HashMap[(TypeRepr, TypeRepr), Expr[Into[?, ?]]]()

  // === Derivation logic ===

  def derive[A: Type, B: Type]: Expr[Into[A, B]] = {
    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    val aIsProduct    = aTpe.classSymbol.exists(isProductType)
    val bIsProduct    = bTpe.classSymbol.exists(isProductType)
    val aIsTuple      = isTupleType(aTpe)
    val bIsTuple      = isTupleType(bTpe)
    val aIsCoproduct  = isCoproductType(aTpe)
    val bIsCoproduct  = isCoproductType(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)

    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct, aIsStructural, bIsStructural) match {
      case (true, true, _, _, _, _, _, _) =>
        // Case class to case class
        deriveProductToProduct[A, B](aTpe, bTpe)
      case (true, _, _, true, _, _, _, _) =>
        // Case class to tuple
        deriveCaseClassToTuple[A, B](aTpe, bTpe)
      case (_, true, true, _, _, _, _, _) =>
        // Tuple to case class
        deriveTupleToCaseClass[A, B](aTpe, bTpe)
      case (_, _, true, true, _, _, _, _) =>
        // Tuple to tuple
        deriveTupleToTuple[A, B](aTpe, bTpe)
      case (_, _, _, _, true, true, _, _) =>
        // Coproduct to coproduct (sealed trait/enum to sealed trait/enum)
        deriveCoproductToCoproduct[A, B](aTpe, bTpe)
      case (_, true, _, _, _, _, true, _) =>
        // Structural type -> Product (structural source to case class target)
        deriveStructuralToProduct[A, B](aTpe, bTpe)
      case (true, _, _, _, _, _, _, true) =>
        // Product -> Structural (case class source to structural target)
        deriveProductToStructural[A, B](aTpe, bTpe)
      case _ =>
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: unsupported type combination")
    }
  }

  private def isTupleType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)

  private def getTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] =
    if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
    else typeArgs(tpe)

  private def isCoproductType(tpe: TypeRepr): Boolean =
    isSealedTraitOrAbstractClass(tpe) || isEnum(tpe)

  private def isEnum(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Enum) && !tpe.typeSymbol.flags.is(Flags.Case)

  // === Structural helpers ===

  private def isStructuralType(tpe: TypeRepr): Boolean = {
    val dealised = tpe.dealias
    dealised match {
      case Refinement(_, _, _) => true
      case _                   => false
    }
  }

  private def getStructuralMembers(tpe: TypeRepr): List[(String, TypeRepr)] = {
    def collectMembers(t: TypeRepr): List[(String, TypeRepr)] = t match {
      case Refinement(parent, name, info) =>
        val memberType = info match {
          case MethodType(_, _, returnType) => returnType
          case ByNameType(underlying)       => underlying
          case other                        => other
        }
        (name, memberType) :: collectMembers(parent)
      case _ => Nil
    }
    collectMembers(tpe.dealias).reverse
  }

  // === Structural Type to Product ===

  private def deriveStructuralToProduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val structuralMembers = getStructuralMembers(aTpe)
    val targetInfo        = new ProductInfo[B](bTpe)

    // For each target field, find matching structural member (by name or unique type)
    val fieldMappings = targetInfo.fields.map { targetField =>
      val matchingMember = structuralMembers.find { case (name, memberTpe) =>
        name == targetField.name && memberTpe =:= targetField.tpe
      }.orElse {
        val uniqueTypeMatches = structuralMembers.filter { case (_, memberTpe) =>
          memberTpe =:= targetField.tpe
        }
        if (uniqueTypeMatches.size == 1) Some(uniqueTypeMatches.head) else None
      }

      matchingMember match {
        case Some((memberName, _)) => (memberName, targetField)
        case None                  =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: no matching structural member found for field '${targetField.name}: ${targetField.tpe.show}'"
          )
      }
    }

    // Generate conversion using reflection to access structural type members
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${
            val args: List[Term] = fieldMappings.map { case (memberName, targetField) =>
              targetField.tpe.asType match {
                case '[t] =>
                  '{
                    // Use reflection to call the method on the structural type
                    val method = a.getClass.getMethod(${ Expr(memberName) })
                    method.invoke(a).asInstanceOf[t]
                  }.asTerm
              }
            }
            val resultExpr: Expr[B] = targetInfo.construct(args).asExprOf[B]
            (resultExpr)
          })
      }
    }
  }

  // === Product to Structural Type ===

  private def deriveProductToStructural[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] =
    // Product → Structural type conversion requires experimental Symbol.newClass API
    //
    // EXPERIMENTAL PROOF OF CONCEPT STATUS:
    // We attempted to implement this using experimental Scala 3 APIs to generate anonymous
    // classes with concrete getter methods. The approach is theoretically sound but the
    // experimental API is complex and unstable:
    //
    // 1. Symbol.newClass can create anonymous classes with Object + Selectable parents
    // 2. DefDef can create method implementations for field getters
    // 3. The generated class would have concrete methods that Scala's reflection can find
    //
    // However, the experimental API has issues:
    // - Complex parameter handling in DefDef lambdas
    // - Unstable API signatures that may change between Scala versions
    // - Limited documentation and examples
    // - The -experimental flag is required
    //
    // ALTERNATIVES:
    // 1. Wait for Scala 3's macro APIs to stabilize (recommended)
    // 2. Accept that Structural → Product works but not the reverse
    //
    // Note: Structural → Product conversion (reading from structural types) works fine
    // using reflective access with selectDynamic fallback.

    fail(
      s"Product → Structural type conversion requires experimental compiler features. " +
        s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]. " +
        s"Structural → Product conversion IS supported. " +
        s"Consider using case classes instead of structural types for the target."
    )

  // === Coproduct to Coproduct ===

  private def deriveCoproductToCoproduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceSubtypes = directSubTypes(aTpe)
    val targetSubtypes = directSubTypes(bTpe)

    // Build case mapping: for each source subtype, find matching target subtype
    val caseMappings = sourceSubtypes.map { sourceSubtype =>
      findMatchingTargetSubtype(sourceSubtype, targetSubtypes, aTpe, bTpe) match {
        case Some(targetSubtype) => (sourceSubtype, targetSubtype)
        case None                =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
              s"no matching target case found for source case '${sourceSubtype.typeSymbol.name}'"
          )
      }
    }

    generateCoproductConversion[A, B](caseMappings)
  }

  private def findMatchingTargetSubtype(
    sourceSubtype: TypeRepr,
    targetSubtypes: List[TypeRepr],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Option[TypeRepr] = {
    // Get the name - for enum values use termSymbol, for others use typeSymbol
    val sourceName = getSubtypeName(sourceSubtype)

    // Priority 1: Match by name
    val nameMatch = targetSubtypes.find { targetSubtype =>
      getSubtypeName(targetSubtype) == sourceName
    }
    if (nameMatch.isDefined) return nameMatch

    // Priority 2: Match by signature (field types) - only for non-empty signatures
    val sourceSignature = getTypeSignature(sourceSubtype)
    if (sourceSignature.nonEmpty) {
      val signatureMatch = targetSubtypes.find { targetSubtype =>
        val targetSignature = getTypeSignature(targetSubtype)
        targetSignature.nonEmpty && signaturesMatch(sourceSignature, targetSignature)
      }
      if (signatureMatch.isDefined) return signatureMatch
    }

    // Priority 3: Match by position if same count
    val sourceIdx = directSubTypes(aTpe).indexOf(sourceSubtype)
    if (sourceIdx >= 0 && sourceIdx < targetSubtypes.size) {
      return Some(targetSubtypes(sourceIdx))
    }

    None
  }

  /**
   * Get the name of a subtype - handles enum values and case objects/classes
   */
  private def getSubtypeName(tpe: TypeRepr): String = {
    // For enum values and case objects, the termSymbol has the correct name
    val termSym = tpe.termSymbol
    if (termSym.exists) {
      termSym.name
    } else {
      tpe.typeSymbol.name
    }
  }

  /** Get the type signature of a case class/object - list of field types */
  private def getTypeSignature(tpe: TypeRepr): List[TypeRepr] =
    if (isEnumOrModuleValue(tpe)) {
      // Case object / enum value - no fields
      Nil
    } else {
      // Case class - get field types
      tpe.classSymbol match {
        case Some(cls) if isProductType(cls) =>
          val info = new ProductInfo[Any](tpe)(using Type.of[Any])
          info.fields.map(_.tpe)
        case _ => Nil
      }
    }

  private def signaturesMatch(source: List[TypeRepr], target: List[TypeRepr]): Boolean =
    source.size == target.size && source.zip(target).forall { case (s, t) =>
      s =:= t || isCoercible(s, t)
    }

  private def generateCoproductConversion[A: Type, B: Type](caseMappings: List[(TypeRepr, TypeRepr)]): Expr[Into[A, B]] = {
    // Generate the match expression builder that will be called at runtime with 'a'
    // We need to build the CaseDef list inside the splice to avoid closure issues

    def buildMatchExpr(aExpr: Expr[A]): Expr[Either[SchemaError, B]] = {
      val cases = caseMappings.map { case (sourceSubtype, targetSubtype) =>
        generateCaseClause[B](sourceSubtype, targetSubtype)
      }
      Match(aExpr.asTerm, cases).asExprOf[Either[SchemaError, B]]
    }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = ${
          buildMatchExpr('a)
        }
      }
    }
  }

  private def generateCaseClause[B: Type](
    sourceSubtype: TypeRepr,
    targetSubtype: TypeRepr,
  ): CaseDef = {
    val bindingName = Symbol.newVal(Symbol.spliceOwner, "x", sourceSubtype, Flags.Case, Symbol.noSymbol)
    val bindingRef  = Ref(bindingName)

    val pattern = Bind(bindingName, Typed(Wildcard(), Inferred(sourceSubtype)))

    val conversion: Term = if (isEnumOrModuleValue(sourceSubtype) && isEnumOrModuleValue(targetSubtype)) {
      // Case object to case object / enum value to enum value
      // For enum values and case objects, directSubTypes returns Ref(symbol).tpe
      // The termSymbol gives us the actual value reference
      val targetRef: Term = targetSubtype match {
        case tref: TermRef =>
          // TermRef - use its term symbol directly
          Ref(tref.termSymbol)
        case _ =>
          // Try to get module for case objects
          val sym = targetSubtype.typeSymbol
          if (sym.flags.is(Flags.Module)) {
            Ref(sym.companionModule)
          } else {
            fail(s"Cannot get reference for singleton type: ${targetSubtype.show}")
          }
      }

      '{ Right(${ targetRef.asExprOf[B] }) }.asTerm
    } else {
      // Case class to case class - convert fields
      generateCaseClassConversion[B](sourceSubtype, targetSubtype, bindingRef)
    }

    CaseDef(pattern, None, conversion)
  }

  private def generateCaseClassConversion[B: Type](
    sourceSubtype: TypeRepr,
    targetSubtype: TypeRepr,
    sourceRef: Term
  ): Term = {
    val sourceInfo = new ProductInfo[Any](sourceSubtype)(using Type.of[Any])
    val targetInfo = new ProductInfo[Any](targetSubtype)(using Type.of[Any])

    // Match fields between source and target case classes
    val fieldMappings = matchFields(sourceInfo, targetInfo, sourceSubtype, targetSubtype)

    // Build constructor arguments
    val args = fieldMappings.zip(targetInfo.fields).map { case (mapping, targetFieldInfo) =>
      mapping.sourceField match {
        case Some(sourceField) =>
          val sourceValue = Select(sourceRef, sourceField.getter)
          val sourceTpe   = sourceField.tpe
          val targetTpe   = targetFieldInfo.tpe

          // If types differ, try to find an implicit Into instance
          if (!(sourceTpe =:= targetTpe)) {
            // Try to find implicit Into instance for nested conversions
            findImplicitInto(sourceTpe, targetTpe) match {
              case Some(intoInstance) =>
                sourceTpe.asType match {
                  case '[st] =>
                    targetTpe.asType match {
                      case '[tt] =>
                        val typedInto = intoInstance.asExprOf[Into[st, tt]]
                        '{
                          $typedInto.into(${ sourceValue.asExprOf[st] }) match {
                            case Right(v)  => v
                            case Left(err) => throw new RuntimeException(s"Coercion failed: $err")
                          }
                        }.asTerm
                    }
                }
              case None =>
                // No coercion available - fail at compile time
                report.errorAndAbort(
                  s"Cannot find implicit Into[${sourceTpe.show}, ${targetTpe.show}] for field in coproduct case. " +
                  s"Please provide an implicit Into instance in scope."
                )
            }
          } else {
            sourceValue
          }
        case None =>
          // No source field - target must be Option[T], provide None
          '{ None }.asTerm
      }
    }

    // Construct target case class and wrap in Right
    val targetConstruction = targetInfo.construct(args)
    '{ Right(${ targetConstruction.asExprOf[B] }) }.asTerm
  }

  // === Product to Product ===

  private def deriveProductToProduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceInfo    = new ProductInfo[A](aTpe)
    val targetInfo    = new ProductInfo[B](bTpe)
    val fieldMappings = matchFields(sourceInfo, targetInfo, aTpe, bTpe)
    generateProductConversion[A, B](sourceInfo, targetInfo, fieldMappings)
  }

  private case class FieldMapping(
    sourceField: Option[FieldInfo], // None means use default (None for Option types)
    targetField: FieldInfo
  )

  private def matchFields(
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): List[FieldMapping] = {
    val sourceTypeFreq   = sourceInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val targetTypeFreq   = targetInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
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
          FieldMapping(Some(sourceField), targetField)
        case None =>
          // If target field is Option[T] and no source field found, use None
          if (isOptionType(targetField.tpe)) {
            FieldMapping(None, targetField) // None indicates to use None as the value
          } else {
            fail(
              s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
                s"no matching field found for '${targetField.name}: ${targetField.tpe.show}' in source type."
            )
          }
      }
    }
  }

  private def isOptionType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Option[?]].typeSymbol) != TypeRepr.of[Nothing]

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
    val exactMatch = sourceInfo.fields.find { sf =>
      !usedSourceFields.contains(sf.index) && sf.name == targetField.name && sf.tpe =:= targetField.tpe
    }
    if (exactMatch.isDefined) return exactMatch

    // Priority 2: Name match with coercion - same name + coercible type or implicit Into available
    val nameWithCoercion = sourceInfo.fields.find { sf =>
      !usedSourceFields.contains(sf.index) && sf.name == targetField.name &&
      (isCoercible(sf.tpe, targetField.tpe) || findImplicitInto(sf.tpe, targetField.tpe).isDefined)
    }
    if (nameWithCoercion.isDefined) return nameWithCoercion

    // Priority 3: Unique type match
    val targetTypeKey      = targetField.tpe.dealias.show
    val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1
    if (isTargetTypeUnique) {
      val uniqueTypeMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && sf.tpe =:= targetField.tpe
        }
      }
      if (uniqueTypeMatch.isDefined) return uniqueTypeMatch

      // Also check for unique coercible type match (including implicit Into)
      val uniqueCoercibleMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && (isCoercible(sf.tpe, targetField.tpe) || findImplicitInto(sf.tpe, targetField.tpe).isDefined)
        }
      }
      if (uniqueCoercibleMatch.isDefined) return uniqueCoercibleMatch
    }

    // Priority 4: Position + matching type
    if (targetField.index < sourceInfo.fields.size) {
      val positionalField = sourceInfo.fields(targetField.index)
      if (!usedSourceFields.contains(positionalField.index)) {
        if (positionalField.tpe =:= targetField.tpe) return Some(positionalField)
        // Also check coercible for positional (including implicit Into)
        if (isCoercible(positionalField.tpe, targetField.tpe) || findImplicitInto(positionalField.tpe, targetField.tpe).isDefined) {
          return Some(positionalField)
        }
      }
    }

    None
  }

  private def isCoercible(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {
    if (sourceTpe =:= targetTpe) return true

    // Check if target is an opaque type and source matches its underlying type
    if (requiresOpaqueConversion(sourceTpe, targetTpe)) return true

    // Only check for numeric widening conversions (lossless)
    // Note: We don't check for implicit Into instances here as it causes compiler issues
    // Instead, we check for implicits lazily when generating code
    val source = sourceTpe.dealias.typeSymbol
    val target = targetTpe.dealias.typeSymbol

    // Byte -> Short, Int, Long, Float, Double
    if (source == TypeRepr.of[Byte].typeSymbol) {
      target == TypeRepr.of[Short].typeSymbol ||
      target == TypeRepr.of[Int].typeSymbol ||
      target == TypeRepr.of[Long].typeSymbol ||
      target == TypeRepr.of[Float].typeSymbol ||
      target == TypeRepr.of[Double].typeSymbol
    }
    // Short -> Int, Long, Float, Double
    else if (source == TypeRepr.of[Short].typeSymbol) {
      target == TypeRepr.of[Int].typeSymbol ||
      target == TypeRepr.of[Long].typeSymbol ||
      target == TypeRepr.of[Float].typeSymbol ||
      target == TypeRepr.of[Double].typeSymbol
    }
    // Int -> Long, Float, Double
    else if (source == TypeRepr.of[Int].typeSymbol) {
      target == TypeRepr.of[Long].typeSymbol ||
      target == TypeRepr.of[Float].typeSymbol ||
      target == TypeRepr.of[Double].typeSymbol
    }
    // Long -> Float, Double
    else if (source == TypeRepr.of[Long].typeSymbol) {
      target == TypeRepr.of[Float].typeSymbol ||
      target == TypeRepr.of[Double].typeSymbol
    }
    // Float -> Double
    else if (source == TypeRepr.of[Float].typeSymbol) {
      target == TypeRepr.of[Double].typeSymbol
    } else {
      false
    }
  }

  // Find or derive an Into instance, using cache to handle recursion
  private def findImplicitInto(sourceTpe: TypeRepr, targetTpe: TypeRepr): Option[Expr[Into[?, ?]]] = {
    // Check cache first
    intoRefs.get((sourceTpe, targetTpe)) match {
      case some @ Some(_) => some
      case None =>
        // Try to find implicit
        val intoTpeApplied = TypeRepr.of[Into].typeSymbol.typeRef.appliedTo(List(sourceTpe, targetTpe))
        Implicits.search(intoTpeApplied) match {
          case success: ImplicitSearchSuccess =>
            val expr = success.tree.asExpr.asInstanceOf[Expr[Into[?, ?]]]
            // Cache it for future use
            intoRefs.update((sourceTpe, targetTpe), expr)
            Some(expr)
          case _ =>
            None
        }
    }
  }

  private def generateProductConversion[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[Into[A, B]] = {
    val convertedExpr = constructTarget[A, B](sourceInfo, targetInfo, fieldMappings)
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          ${ convertedExpr('a) }
      }
    }
  }

  private def constructTarget[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[A] => Expr[Either[SchemaError, B]] = (aExpr: Expr[A]) => {

    // Build list of field conversion expressions
    val fieldConversions: List[(Int, Expr[Either[SchemaError, Any]])] = fieldMappings.zipWithIndex.map {
      case (mapping, idx) =>
        val targetFieldInfo = targetInfo.fields(idx)
        val eitherExpr = mapping.sourceField match {
          case Some(sourceField) =>
            val sourceValue = sourceInfo.fieldGetter(aExpr.asTerm, sourceField)
            val sourceTpe   = sourceField.tpe
            val targetTpe   = targetFieldInfo.tpe

            // Check if target is an opaque type with validation
            if (requiresOpaqueConversion(sourceTpe, targetTpe)) {
              convertToOpaqueTypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name)
            }
            // If types differ, try to find an implicit Into instance
            else if (!(sourceTpe =:= targetTpe)) {
              findImplicitInto(sourceTpe, targetTpe) match {
                case Some(intoInstance) =>
                  sourceTpe.asType match {
                    case '[st] =>
                      targetTpe.asType match {
                        case '[tt] =>
                          val typedInto = intoInstance.asExprOf[Into[st, tt]]
                          '{
                            $typedInto.into(${ sourceValue.asExprOf[st] }).asInstanceOf[Either[SchemaError, Any]]
                          }
                      }
                  }
                case None =>
                  report.errorAndAbort(
                    s"Cannot find implicit Into[${sourceTpe.show}, ${targetTpe.show}] for field '${sourceField.name}'. " +
                    s"Please provide an implicit Into instance in scope."
                  )
              }
            } else {
              // Types match exactly - wrap in Right
              '{ Right(${ sourceValue.asExprOf[Any] }) }
            }
          case None =>
            // No source field - target must be Option[T], provide None wrapped in Right
            '{ Right(None) }
        }
        (idx, eitherExpr)
    }

    // Generate code that sequences the Either values and constructs the target
    // We need to build the args list at runtime based on the sequencing result
    buildSequencedConstruction[B](fieldConversions, targetInfo)
  }

  /**
   * Build an expression that sequences field conversions and constructs the target object
   */
  private def buildSequencedConstruction[B: Type](
    fieldConversions: List[(Int, Expr[Either[SchemaError, Any]])],
    targetInfo: ProductInfo[B]
  ): Expr[Either[SchemaError, B]] = {
    fieldConversions match {
      case Nil =>
        // No fields - just construct empty object
        val emptyArgs = List.empty[Term]
        '{ Right(${ targetInfo.construct(emptyArgs).asExprOf[B] }) }

      case fields =>
        // Use nested flatMap to sequence Either values while preserving types
        // We know each field's exact type from targetInfo, so we can cast properly
        def buildSequence(remaining: List[(Int, Expr[Either[SchemaError, Any]])], constructorArgs: List[Term]): Expr[Either[SchemaError, B]] = {
          remaining match {
            case Nil =>
              // All fields collected - construct the target
              '{ Right(${ targetInfo.construct(constructorArgs.reverse).asExprOf[B] }) }

            case (idx, fieldEither) :: tail =>
              // We know the exact field type from targetInfo
              val fieldType = targetInfo.fields(idx).tpe
              fieldEither.asTerm.tpe.asType match {
                case '[Either[SchemaError, t]] =>
                  fieldType.asType match {
                    case '[ft] =>
                      '{
                        ${fieldEither.asExprOf[Either[SchemaError, t]]}.flatMap { value =>
                          // Cast value to the known field type
                          val typedValue = value.asInstanceOf[ft]
                          ${ buildSequence(tail, '{ typedValue }.asTerm :: constructorArgs) }
                        }
                      }
                  }
              }
          }
        }

        buildSequence(fields, Nil)
    }
  }

  // === Case Class to Tuple ===

  private def deriveCaseClassToTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceInfo     = new ProductInfo[A](aTpe)
    val targetTypeArgs = getTupleTypeArgs(bTpe)

    if (sourceInfo.fields.size != targetTypeArgs.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: field count mismatch")
    }

    sourceInfo.fields.zip(targetTypeArgs).zipWithIndex.foreach { case ((field, targetTpe), idx) =>
      if (!(field.tpe =:= targetTpe) && !isCoercible(field.tpe, targetTpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx")
      }
    }

    val buildTuple = constructTupleFromCaseClass[A, B](sourceInfo)

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ buildTuple('a) })
      }
    }
  }

  private def constructTupleFromCaseClass[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val args      = sourceInfo.fields.map(field => sourceInfo.fieldGetter(aExpr.asTerm, field))
    val tupleSize = args.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod    = tupleCompanion.methodMember("apply").head
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
    val targetInfo     = new ProductInfo[B](bTpe)

    if (sourceTypeArgs.size != targetInfo.fields.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: field count mismatch")
    }

    sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.foreach { case ((sourceTpe, field), idx) =>
      if (!(sourceTpe =:= field.tpe) && !isCoercible(sourceTpe, field.tpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx")
      }
    }

    val buildCaseClass = constructCaseClassFromTuple[A, B](aTpe, targetInfo)

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ buildCaseClass('a) })
      }
    }
  }

  private def constructCaseClassFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    targetInfo: ProductInfo[B]
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args           = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
      val accessorName = s"_${idx + 1}"
      val accessor     = aTpe.typeSymbol.methodMember(accessorName).head
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

    if (sourceTypeArgs.size != targetTypeArgs.size) {
      fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: element count mismatch")
    }

    sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.foreach { case ((sourceTpe, targetTpe), idx) =>
      if (!(sourceTpe =:= targetTpe) && !isCoercible(sourceTpe, targetTpe)) {
        fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx")
      }
    }

    val buildTuple = constructTupleFromTuple[A, B](aTpe, bTpe)

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ buildTuple('a) })
      }
    }
  }

  private def constructTupleFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args           = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
      val accessorName = s"_${idx + 1}"
      val accessor     = aTpe.typeSymbol.methodMember(accessorName).head
      Select(aExpr.asTerm, accessor)
    }

    val tupleSize = args.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod    = tupleCompanion.methodMember("apply").head
      val targetTypeArgs = getTupleTypeArgs(bTpe)
      Apply(
        Select(Ref(tupleCompanion), applyMethod).appliedToTypes(targetTypeArgs),
        args
      ).asExprOf[B]
    } else {
      fail(s"Tuples with more than 22 elements are not supported")
    }
  }

  // === Opaque Type and Newtype Support ===

  /**
   * Checks if the target type is an opaque type
   */
  private def isOpaqueType(tpe: TypeRepr): Boolean = {
    tpe.typeSymbol.flags.is(Flags.Opaque)
  }

  /**
   * Gets the underlying type of an opaque type
   */
  private def getOpaqueUnderlying(tpe: TypeRepr): TypeRepr = {
    opaqueDealias(tpe)
  }

  /**
   * Tries to find a validation method (apply) that returns Either[_, OpaqueType]
   * Returns (companionObject, applyMethod, errorType) if found
   */
  private def findOpaqueValidationMethod(tpe: TypeRepr): Option[(Term, Symbol, TypeRepr)] = {
    getOpaqueCompanion(tpe).flatMap { case (companionRef, companion) =>
      val applyMethods = companion.methodMembers.filter(_.name == "apply")

      applyMethods.find { method =>
        method.paramSymss match {
          case List(params) if params.size == 1 =>
            // Check if return type is Either[ErrorType, OpaqueType]
            method.tree match {
              case DefDef(_, _, returnTpt, _) =>
                val returnType = returnTpt.tpe
                returnType.dealias match {
                  case AppliedType(eitherTpe, List(_, resultTpe)) if eitherTpe.typeSymbol.fullName == "scala.util.Either" =>
                    // Check if result type matches our opaque type (use direct match for opaque types)
                    resultTpe =:= tpe
                  case _ => false
                }
              case _ => false
            }
          case _ => false
        }
      }.map { method =>
        // Extract error type from Either[ErrorType, OpaqueType]
        val errorTpe = method.tree match {
          case DefDef(_, _, returnTpt, _) =>
            returnTpt.tpe.dealias match {
              case AppliedType(_, List(errorTpe, _)) => errorTpe
              case _                                 => TypeRepr.of[String]
            }
          case _ => TypeRepr.of[String]
        }
        (companionRef, method, errorTpe)
      }
    }
  }

  /**
   * Tries to find an unsafe constructor method for opaque types without validation
   * Returns (companionObject, unsafeMethod) if found
   */
  private def findOpaqueUnsafeMethod(tpe: TypeRepr): Option[(Term, Symbol)] = {
    getOpaqueCompanion(tpe).flatMap { case (companionRef, companion) =>
      val allMethods = (companion.declaredMethods ++ companion.methodMembers).distinct
      val unsafeMethods = allMethods.filter(m => m.name == "unsafe" || m.name == "unsafeWrap")

      unsafeMethods.find { method =>
        method.paramSymss match {
          case List(params) if params.size == 1 =>
            method.tree match {
              case DefDef(_, _, returnTpt, _) =>
                // For opaque types, use direct match (not dealiased)
                returnTpt.tpe =:= tpe
              case _ => false
            }
          case _ => false
        }
      }.map(method => (companionRef, method))
    }
  }

  /**
   * Helper to find the companion object for an opaque type
   */
  private def getOpaqueCompanion(tpe: TypeRepr): Option[(Term, Symbol)] = {
    val typeSym = tpe.typeSymbol
    val companionSym = typeSym.companionModule

    val actualCompanion = if (companionSym == Symbol.noSymbol) {
      // Try to find companion object by constructing its expected module path
      val companionName = typeSym.name
      val owner = typeSym.owner

      // The owner.fullName for objects ends with $, strip it for path construction
      val ownerPath = owner.fullName
      val cleanOwnerPath = if (ownerPath.endsWith("$package$")) {
        ownerPath.stripSuffix("$package$").stripSuffix(".")
      } else if (ownerPath.endsWith("$")) {
        ownerPath.stripSuffix("$")
      } else {
        ownerPath
      }

      val companionPath = if (cleanOwnerPath.isEmpty) companionName else s"$cleanOwnerPath.$companionName"

      try {
        Some(Symbol.requiredModule(companionPath))
      } catch {
        case _: Exception =>
          // Last resort: look in owner declarations
          owner.declarations.find { s =>
            s.name == companionName && s.flags.is(Flags.Module)
          }
      }
    } else {
      Some(companionSym)
    }

    actualCompanion.map(companion => (Ref(companion), companion))
  }

  /**
   * Converts a value to an opaque type, applying validation if available
   * Returns an Expr[Either[SchemaError, OpaqueType]]
   */
  private def convertToOpaqueTypeEither(sourceValue: Term, sourceTpe: TypeRepr, targetTpe: TypeRepr, fieldName: String): Expr[Either[SchemaError, Any]] = {
    val underlying = getOpaqueUnderlying(targetTpe)

    // Check if source type matches the underlying type
    if (!(sourceTpe =:= underlying)) {
      report.errorAndAbort(
        s"Cannot convert field '$fieldName' from ${sourceTpe.show} to opaque type ${targetTpe.show} " +
        s"(underlying type: ${underlying.show}). Types must match."
      )
    }

    // Priority 1: Try validation method (apply) first
    findOpaqueValidationMethod(targetTpe) match {
      case Some((companionRef, applyMethod, errorTpe)) =>
        // Generate validation call that returns Either
        val applyCall = Select(companionRef, applyMethod).appliedTo(sourceValue)

        // Map the error type to SchemaError
        errorTpe.asType match {
          case '[et] =>
            targetTpe.asType match {
              case '[tt] =>
                '{
                  ${applyCall.asExprOf[Either[et, tt]]}.left.map { err =>
                    SchemaError.conversionFailed(Nil, s"Validation failed for field '${${ Expr(fieldName) }}': $err")
                  }.asInstanceOf[Either[SchemaError, Any]]
                }
            }
        }

      case None =>
        // Priority 2: Try unsafe constructor as fallback
        findOpaqueUnsafeMethod(targetTpe) match {
          case Some((companionRef, unsafeMethod)) =>
            // Use unsafe constructor (no validation) - wrap in Right
            targetTpe.asType match {
              case '[tt] =>
                val unsafeCall = Select(companionRef, unsafeMethod).appliedTo(sourceValue)
                '{
                  Right(${unsafeCall.asExprOf[tt]}).asInstanceOf[Either[SchemaError, Any]]
                }
            }

          case None =>
            // No validation or unsafe method found - report error
            report.errorAndAbort(
              s"Cannot convert to opaque type ${targetTpe.show}: no 'apply' or 'unsafe' method found in companion object"
            )
        }
    }
  }

  /**
   * Checks if conversion requires opaque type handling
   */
  private def requiresOpaqueConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {
    isOpaqueType(targetTpe) && {
      val underlying = getOpaqueUnderlying(targetTpe)
      sourceTpe =:= underlying
    }
  }

}
