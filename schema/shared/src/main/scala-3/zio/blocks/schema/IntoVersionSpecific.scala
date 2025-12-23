package zio.blocks.schema

import scala.quoted.*

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
        name == targetField.name && (memberTpe =:= targetField.tpe || requiresOpaqueConversion(
          memberTpe,
          targetField.tpe
        ) || requiresNewtypeConversion(memberTpe, targetField.tpe) || findImplicitInto(
          memberTpe,
          targetField.tpe
        ).isDefined)
      }.orElse {
        val uniqueTypeMatches = structuralMembers.filter { case (_, memberTpe) =>
          memberTpe =:= targetField.tpe || requiresOpaqueConversion(
            memberTpe,
            targetField.tpe
          ) || requiresNewtypeConversion(memberTpe, targetField.tpe) || findImplicitInto(
            memberTpe,
            targetField.tpe
          ).isDefined
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
      s =:= t || requiresOpaqueConversion(s, t) || requiresNewtypeConversion(s, t) || findImplicitInto(s, t).isDefined
    }

  private def generateCoproductConversion[A: Type, B: Type](
    caseMappings: List[(TypeRepr, TypeRepr)]
  ): Expr[Into[A, B]] = {
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
    targetSubtype: TypeRepr
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

    // Build field conversions that return Either[SchemaError, FieldType]
    val fieldEithers = fieldMappings.zip(targetInfo.fields).map { case (mapping, targetFieldInfo) =>
      mapping.sourceField match {
        case Some(sourceField) =>
          val sourceValue = Select(sourceRef, sourceField.getter)
          val sourceTpe   = sourceField.tpe
          val targetTpe   = targetFieldInfo.tpe

          // Check if target is an opaque type with validation
          if (requiresOpaqueConversion(sourceTpe, targetTpe)) {
            convertToOpaqueTypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name).asTerm
          }
          // Check if target is a ZIO Prelude newtype with validation
          else if (requiresNewtypeConversion(sourceTpe, targetTpe)) {
            convertToNewtypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name).asTerm
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
            // Types match exactly - wrap in Right
            '{ Right(${ sourceValue.asExprOf[Any] }) }.asTerm
          }
        case None =>
          // No source field - target must be Option[T], provide None wrapped in Right
          '{ Right(None) }.asTerm
      }
    }

    // Build nested flatMap chain to sequence Either values - reuse the same logic as product-to-product
    buildSequencedConstruction[B](
      fieldEithers.zipWithIndex.map { case (term, idx) => (idx, term.asExprOf[Either[SchemaError, Any]]) },
      targetInfo.asInstanceOf[ProductInfo[B]]
    ).asTerm
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
    if (exactMatch.isDefined) {
      return exactMatch
    }

    // Priority 2: Name match with conversion - same name + implicit Into available or opaque type/newtype conversion
    val nameWithConversion = sourceInfo.fields.find { sf =>
      val nameMatches = !usedSourceFields.contains(sf.index) && sf.name == targetField.name
      if (nameMatches) {
        val opaqueConv   = requiresOpaqueConversion(sf.tpe, targetField.tpe)
        val newtypeConv  = requiresNewtypeConversion(sf.tpe, targetField.tpe)
        val implicitInto = findImplicitInto(sf.tpe, targetField.tpe).isDefined
        opaqueConv || newtypeConv || implicitInto
      } else {
        false
      }
    }
    if (nameWithConversion.isDefined) {
      return nameWithConversion
    }

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

      // Also check for unique type match with conversion (implicit Into, opaque type, or newtype)
      val uniqueConvertibleMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && (requiresOpaqueConversion(sf.tpe, targetField.tpe) || requiresNewtypeConversion(
            sf.tpe,
            targetField.tpe
          ) || findImplicitInto(sf.tpe, targetField.tpe).isDefined)
        }
      }
      if (uniqueConvertibleMatch.isDefined) return uniqueConvertibleMatch
    }

    // Priority 4: Position + matching type
    if (targetField.index < sourceInfo.fields.size) {
      val positionalField = sourceInfo.fields(targetField.index)
      if (!usedSourceFields.contains(positionalField.index)) {
        if (positionalField.tpe =:= targetField.tpe) return Some(positionalField)
        // Also check for positional conversion (implicit Into, opaque type, or newtype)
        if (
          requiresOpaqueConversion(positionalField.tpe, targetField.tpe) || requiresNewtypeConversion(
            positionalField.tpe,
            targetField.tpe
          ) || findImplicitInto(positionalField.tpe, targetField.tpe).isDefined
        ) {
          return Some(positionalField)
        }
      }
    }

    None
  }

  // isCoercible has been removed - implicit Into resolution now handles all type conversions
  // including numeric widening/narrowing and opaque types

  // Find or derive an Into instance, using cache to handle recursion
  private def findImplicitInto(sourceTpe: TypeRepr, targetTpe: TypeRepr): Option[Expr[Into[?, ?]]] =
    // Check cache first
    intoRefs.get((sourceTpe, targetTpe)) match {
      case some @ Some(_) => some
      case None           =>
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
        val eitherExpr      = mapping.sourceField match {
          case Some(sourceField) =>
            val sourceValue = sourceInfo.fieldGetter(aExpr.asTerm, sourceField)
            val sourceTpe   = sourceField.tpe
            val targetTpe   = targetFieldInfo.tpe

            // Check if target is an opaque type with validation
            if (requiresOpaqueConversion(sourceTpe, targetTpe)) {
              convertToOpaqueTypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name)
            }
            // Check if target is a ZIO Prelude newtype with validation
            else if (requiresNewtypeConversion(sourceTpe, targetTpe)) {
              convertToNewtypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name)
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
   * Build an expression that sequences field conversions and constructs the
   * target object
   */
  private def buildSequencedConstruction[B: Type](
    fieldConversions: List[(Int, Expr[Either[SchemaError, Any]])],
    targetInfo: ProductInfo[B]
  ): Expr[Either[SchemaError, B]] =
    fieldConversions match {
      case Nil =>
        // No fields - just construct empty object
        val emptyArgs = List.empty[Term]
        '{ Right(${ targetInfo.construct(emptyArgs).asExprOf[B] }) }

      case fields =>
        // Use nested flatMap to sequence Either values while preserving types
        // We know each field's exact type from targetInfo, so we can cast properly
        def buildSequence(
          remaining: List[(Int, Expr[Either[SchemaError, Any]])],
          constructorArgs: List[Term]
        ): Expr[Either[SchemaError, B]] =
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
                        ${ fieldEither.asExprOf[Either[SchemaError, t]] }.flatMap { value =>
                          // Cast value to the known field type
                          val typedValue = value.asInstanceOf[ft]
                          ${ buildSequence(tail, '{ typedValue }.asTerm :: constructorArgs) }
                        }
                      }
                  }
              }
          }

        buildSequence(fields, Nil)
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
      if (
        !(field.tpe =:= targetTpe) && !requiresOpaqueConversion(field.tpe, targetTpe) && !requiresNewtypeConversion(
          field.tpe,
          targetTpe
        ) && findImplicitInto(field.tpe, targetTpe).isEmpty
      ) {
        fail(
          s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx. No implicit Into[${field.tpe.show}, ${targetTpe.show}] found."
        )
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
    sourceInfo: ProductInfo[A]
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
      if (
        !(sourceTpe =:= field.tpe) && !requiresOpaqueConversion(sourceTpe, field.tpe) && !requiresNewtypeConversion(
          sourceTpe,
          field.tpe
        ) && findImplicitInto(sourceTpe, field.tpe).isEmpty
      ) {
        fail(
          s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx. No implicit Into[${sourceTpe.show}, ${field.tpe.show}] found."
        )
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
      if (
        !(sourceTpe =:= targetTpe) && !requiresOpaqueConversion(sourceTpe, targetTpe) && !requiresNewtypeConversion(
          sourceTpe,
          targetTpe
        ) && findImplicitInto(sourceTpe, targetTpe).isEmpty
      ) {
        fail(
          s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: type mismatch at position $idx. No implicit Into[${sourceTpe.show}, ${targetTpe.show}] found."
        )
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
  private def isOpaqueType(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Opaque)

  /**
   * Gets the underlying type of an opaque type
   */
  private def getOpaqueUnderlying(tpe: TypeRepr): TypeRepr =
    opaqueDealias(tpe)

  /**
   * Tries to find a validation method (apply) that returns Either[_,
   * OpaqueType] Returns (companionObject, applyMethod, errorType) if found
   */
  private def findOpaqueValidationMethod(tpe: TypeRepr): Option[(Term, Symbol, TypeRepr)] =
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
                  case AppliedType(eitherTpe, List(_, resultTpe))
                      if eitherTpe.typeSymbol.fullName == "scala.util.Either" =>
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

  /**
   * Tries to find an unsafe constructor method for opaque types without
   * validation Returns (companionObject, unsafeMethod) if found
   */
  private def findOpaqueUnsafeMethod(tpe: TypeRepr): Option[(Term, Symbol)] =
    getOpaqueCompanion(tpe).flatMap { case (companionRef, companion) =>
      val allMethods    = (companion.declaredMethods ++ companion.methodMembers).distinct
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

  /**
   * Helper to find the companion object for an opaque type
   */
  private def getOpaqueCompanion(tpe: TypeRepr): Option[(Term, Symbol)] = {
    val typeSym      = tpe.typeSymbol
    val companionSym = typeSym.companionModule

    val actualCompanion = if (companionSym == Symbol.noSymbol) {
      // Try to find companion object by constructing its expected module path
      val companionName = typeSym.name
      val owner         = typeSym.owner

      // The owner.fullName for objects ends with $, strip it for path construction
      val ownerPath      = owner.fullName
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
  private def convertToOpaqueTypeEither(
    sourceValue: Term,
    sourceTpe: TypeRepr,
    targetTpe: TypeRepr,
    fieldName: String
  ): Expr[Either[SchemaError, Any]] = {
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
                  ${ applyCall.asExprOf[Either[et, tt]] }.left.map { err =>
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
                  Right(${ unsafeCall.asExprOf[tt] }).asInstanceOf[Either[SchemaError, Any]]
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
  private def requiresOpaqueConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isOpaqueType(targetTpe) && {
      val underlying = getOpaqueUnderlying(targetTpe)
      sourceTpe =:= underlying
    }

  // === ZIO Prelude Newtype Support (Scala 3) ===

  /**
   * Checks if a type is a ZIO Prelude Newtype or Subtype Works without
   * requiring zio-prelude as a dependency Checks both the type and its
   * companion object for newtype markers
   */
  private def isZIONewtype(tpe: TypeRepr): Boolean = {
    val typeSym        = tpe.typeSymbol
    val typeSymbolName = typeSym.name
    val owner          = typeSym.owner

    // For ZIO Prelude newtypes: type Age = Age.Type where Age extends Subtype[Int]
    // The pattern is:
    // - typeSym.name is "Type"
    // - owner.fullName contains "zio.prelude.Subtype" or "zio.prelude.Newtype"
    //
    // We check the owner's fullName string to avoid loading ZIO Prelude's TASTy files
    // which can cause "Bad symbolic reference" errors with TASTy version mismatches

    if (typeSymbolName == "Type") {
      val ownerFullName       = owner.fullName
      val isZIOPreludeNewtype = ownerFullName.contains("zio.prelude.Subtype") ||
        ownerFullName.contains("zio.prelude.Newtype")

      if (isZIOPreludeNewtype) {
        return true
      }
    } else {}

    false
  }

  /**
   * Gets the underlying type of a ZIO Prelude newtype For Newtype[A] or
   * Subtype[A], returns A
   *
   * For ZIO Prelude, the pattern is: object Age extends Subtype[Int] type Age =
   * Age.Type
   *
   * When we receive Age.Type (which is an opaque type alias to Int), we need to
   * find the Age object (the actual owner in the enclosing scope, not the
   * Subtype parent) and check what type parameter it has.
   */
  private def getNewtypeUnderlying(tpe: TypeRepr): TypeRepr = {

    val typeSym = tpe.typeSymbol

    // For ZIO Prelude newtypes:
    // - Subtype[Int]: Type extends Int directly, so Int is in base types
    // - Newtype[String]: Type wraps String
    //
    // IMPORTANT: We cannot access baseClasses as it loads TASTy files and causes
    // "Bad symbolic reference" errors with version mismatches.
    //
    // Instead, we'll look at the type's direct structure. For ZIO Prelude:
    // - PositiveInt.Type where PositiveInt extends Subtype[Int]
    //   The Type itself has the shape that reflects Int (AnyVal hierarchy)
    // - Email.Type where Email extends Newtype[String]
    //   The Type wraps String

    if (typeSym.name == "Type") {
      val owner = typeSym.owner

      // Try to infer the underlying type by inspecting tpe structure
      // For now, we'll use a heuristic: look at the type's widen representation
      val widened = tpe.widen

      // Check if the widened type gives us something useful
      if (widened != tpe && !widened.typeSymbol.fullName.contains("zio.prelude")) {
        return widened
      }

      // Fallback: cannot extract underlying type without loading TASTy
      // Return the original type - field matching will fail and we'll rely on
      // implicit Into instances instead
      tpe
    } else {
      tpe
    }
  }

  /**
   * Helper to find the companion object for a ZIO Prelude newtype Uses the same
   * logic as getOpaqueCompanion
   */
  private def getNewtypeCompanion(tpe: TypeRepr): Option[(Term, Symbol)] = {
    val typeSym      = tpe.typeSymbol
    val companionSym = typeSym.companionModule

    val actualCompanion = if (companionSym == Symbol.noSymbol) {
      // Try to find companion object by constructing its expected module path
      val companionName = typeSym.name
      val owner         = typeSym.owner

      // The owner.fullName for objects ends with $, strip it for path construction
      val ownerPath      = owner.fullName
      val cleanOwnerPath = if (ownerPath.endsWith("$package$")) {
        ownerPath.stripSuffix("$package$").stripSuffix(".")
      } else if (ownerPath.endsWith("$")) {
        ownerPath.stripSuffix("$")
      } else {
        ownerPath
      }

      val companionPath = if (cleanOwnerPath.isEmpty) companionName else s"$cleanOwnerPath.$companionName"

      try {
        val loaded = Symbol.requiredModule(companionPath)
        Some(loaded)
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
   * Finds the 'make' method in a ZIO Prelude newtype companion ZIO Prelude
   * newtypes have a 'make' method that returns Validation[E, NewtypeType] We
   * can convert Validation to Either using .toEither Returns (companionRef,
   * method) if make method exists
   */
  private def findNewtypeMakeMethod(tpe: TypeRepr): Option[(Term, Symbol)] =
    getNewtypeCompanion(tpe).flatMap { case (companionRef, companion) =>
      // Look for 'make' method (standard ZIO Prelude newtype pattern)
      val allMethods = (companion.declaredMethods ++ companion.methodMembers).distinct

      val makeMethods = allMethods.filter(m => m.name == "make")

      makeMethods.find { method =>
        method.paramSymss match {
          case List(params) if params.size == 1 =>
            // The make method should return Validation[?, NewtypeType]
            method.tree match {
              case DefDef(_, _, returnTpt, _) =>
                val returnTypeName = returnTpt.tpe.typeSymbol.fullName
                // Check if it returns zio.prelude.Validation
                returnTypeName.contains("zio.prelude.Validation") ||
                returnTypeName.contains("zio.prelude.ZValidation")
              case _ => false
            }
          case _ => false
        }
      }.map { method =>
        (companionRef, method)
      }
    }

  /**
   * Finds the unsafe wrap method in a ZIO Prelude newtype companion
   */
  private def findNewtypeUnsafeMethod(tpe: TypeRepr): Option[(Term, Symbol)] = {
    val typeSym      = tpe.typeSymbol
    val companionSym = typeSym.companionModule

    if (companionSym == Symbol.noSymbol) return None

    val companionRef = Ref(companionSym)
    val companion    = companionSym

    val allMethods    = (companion.declaredMethods ++ companion.methodMembers).distinct
    val unsafeMethods = allMethods.filter(m => m.name == "unsafeWrap" || m.name == "unsafe" || m.name == "unsafeMake")

    unsafeMethods.find { method =>
      method.paramSymss match {
        case List(params) if params.size == 1 => true
        case _                                => false
      }
    }.map(method => (companionRef, method))
  }

  /**
   * Converts a value to a ZIO Prelude newtype, applying validation if available
   * Returns an Expr[Either[SchemaError, NewtypeType]]
   *
   * This uses runtime reflection to avoid loading ZIO Prelude's TASTy files at
   * compile time, which can cause "Bad symbolic reference" errors with version
   * mismatches.
   */
  private def convertToNewtypeEither(
    sourceValue: Term,
    sourceTpe: TypeRepr,
    targetTpe: TypeRepr,
    fieldName: String
  ): Expr[Either[SchemaError, Any]] = {
    // Get the companion object path for runtime reflection
    // For ZIO Prelude newtypes like Domain.PositiveInt.Type, we need to extract Domain.PositiveInt
    val fullTypeName  = targetTpe.show
    val companionPath = if (fullTypeName.endsWith(".Type")) {
      // Remove the .Type suffix to get the companion object path
      fullTypeName.stripSuffix(".Type")
    } else {
      fullTypeName
    }

    targetTpe.asType match {
      case '[tt] =>
        sourceValue.asExpr match {
          case '{ $src: s } =>
            '{
              // Runtime reflection to call the make method on ZIO Prelude newtypes
              // This avoids compile-time TASTy loading issues
              try {
                // Convert the companion path to JVM class name
                // For nested objects like Domain.Age, the JVM class is Domain$Age$
                val companionClassName = {
                  val basePath = ${ Expr(companionPath) }
                  // Split by dot and check each segment to see if it's an object
                  // For now, assume all segments except the package are objects (common pattern)
                  // We need to replace dots with $ for object nesting

                  // Simple heuristic: if path contains uppercase letters after a dot,
                  // those are likely objects/classes, not packages
                  val parts      = basePath.split('.')
                  val packageEnd = parts.indexWhere(part => part.nonEmpty && part.head.isUpper)

                  if (packageEnd >= 0) {
                    val packagePart = parts.take(packageEnd).mkString(".")
                    val objectPart  = parts.drop(packageEnd).mkString("$")
                    if (packagePart.isEmpty) objectPart + "$" else packagePart + "." + objectPart + "$"
                  } else {
                    basePath + "$"
                  }
                }

                val companionClass  = Class.forName(companionClassName)
                val companionModule = companionClass.getField("MODULE$").get(null)

                // ZIO Prelude newtypes always have:
                // - make(value: Object): Validation[String, Type] - with validation
                // - wrap(value: Object): Type - without validation (unsafe)
                //
                // We use Object as parameter type because of type erasure.
                // The 'apply' method is inline and won't be available at runtime.
                val paramClass = classOf[Object]

                // Try to find and call the 'make' method (returns Validation with validation)
                val makeMethodOpt =
                  try {
                    Some(companionClass.getMethod("make", paramClass))
                  } catch {
                    case _: NoSuchMethodException => None
                  }

                makeMethodOpt match {
                  case Some(makeMethod) =>
                    val validation = makeMethod.invoke(companionModule, $src.asInstanceOf[Object])

                    // Call toEither on the Validation result to get Either[NonEmptyChunk[String], Type]
                    val toEitherMethod = validation.getClass.getMethod("toEither")
                    val either         = toEitherMethod.invoke(validation).asInstanceOf[Either[Any, tt]]

                    either.left.map { err =>
                      SchemaError.conversionFailed(Nil, s"Validation failed for field '${${ Expr(fieldName) }}': $err")
                    }

                  case None =>
                    // Fall back to wrap if make not found (shouldn't happen for ZIO Prelude newtypes)
                    val wrapMethodOpt =
                      try {
                        Some(companionClass.getMethod("wrap", paramClass))
                      } catch {
                        case _: NoSuchMethodException => None
                      }

                    wrapMethodOpt match {
                      case Some(wrapMethod) =>
                        val result = wrapMethod.invoke(companionModule, $src.asInstanceOf[Object]).asInstanceOf[tt]
                        Right(result)
                      case None =>
                        Left(
                          SchemaError.conversionFailed(
                            Nil,
                            s"No 'make' or 'wrap' method found for newtype at $companionClassName"
                          )
                        )
                    }
                }
              } catch {
                case e: ClassNotFoundException =>
                  Left(
                    SchemaError.conversionFailed(
                      Nil,
                      s"Companion object not found for newtype: ${${ Expr(companionPath) }}: ${e.getMessage}"
                    )
                  )
                case e: Exception =>
                  Left(SchemaError.conversionFailed(Nil, s"Error converting to newtype: ${e.getMessage}"))
              }
            }
        }
    }
  }

  /**
   * Checks if conversion requires ZIO Prelude newtype handling
   */
  private def requiresNewtypeConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {

    // Check if the target is a ZIO Prelude newtype
    val isNewtype = isZIONewtype(targetTpe)

    if (!isNewtype) {
      return false
    }

    // Target is a newtype. We accept any source type and will handle conversion
    // via the newtype's make/wrap method. The actual validation happens at runtime.
    // For now, we just check that source and target are not identical.
    val result = sourceTpe != targetTpe
    result
  }

}
