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

    val aIsProduct   = aTpe.classSymbol.exists(isProductType)
    val bIsProduct   = bTpe.classSymbol.exists(isProductType)
    val aIsTuple     = isTupleType(aTpe)
    val bIsTuple     = isTupleType(bTpe)
    val aIsCoproduct = isCoproductType(aTpe)
    val bIsCoproduct = isCoproductType(bTpe)

    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct) match {
      case (true, true, _, _, _, _) =>
        // Case class to case class
        deriveProductToProduct[A, B](aTpe, bTpe)
      case (true, _, _, true, _, _) =>
        // Case class to tuple
        deriveCaseClassToTuple[A, B](aTpe, bTpe)
      case (_, true, true, _, _, _) =>
        // Tuple to case class
        deriveTupleToCaseClass[A, B](aTpe, bTpe)
      case (_, _, true, true, _, _) =>
        // Tuple to tuple
        deriveTupleToTuple[A, B](aTpe, bTpe)
      case (_, _, _, _, true, true) =>
        // Coproduct to coproduct (sealed trait/enum to sealed trait/enum)
        deriveCoproductToCoproduct[A, B](aTpe, bTpe)
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
        case None =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
              s"no matching target case found for source case '${sourceSubtype.typeSymbol.name}'"
          )
      }
    }

    generateCoproductConversion[A, B](aTpe, bTpe, caseMappings)
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

  /** Get the name of a subtype - handles enum values and case objects/classes */
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
  private def getTypeSignature(tpe: TypeRepr): List[TypeRepr] = {
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
  }

  private def signaturesMatch(source: List[TypeRepr], target: List[TypeRepr]): Boolean = {
    source.size == target.size && source.zip(target).forall { case (s, t) =>
      s =:= t || isCoercible(s, t)
    }
  }

  private def generateCoproductConversion[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    caseMappings: List[(TypeRepr, TypeRepr)]
  ): Expr[Into[A, B]] = {
    // Generate the match expression builder that will be called at runtime with 'a'
    // We need to build the CaseDef list inside the splice to avoid closure issues

    def buildMatchExpr(aExpr: Expr[A]): Expr[Either[SchemaError, B]] = {
      val cases = caseMappings.map { case (sourceSubtype, targetSubtype) =>
        generateCaseClause[B](sourceSubtype, targetSubtype, bTpe)
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
    bTpe: TypeRepr
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
    val args = fieldMappings.map { mapping =>
      Select(sourceRef, mapping.sourceField.getter)
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
    sourceField: FieldInfo,
    targetField: FieldInfo
  )

  private def matchFields(
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): List[FieldMapping] = {
    val sourceTypeFreq = sourceInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val targetTypeFreq = targetInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val usedSourceFields = scala.collection.mutable.Set[Int]()

    targetInfo.fields.map { targetField =>
      findMatchingSourceField(targetField, sourceInfo, sourceTypeFreq, targetTypeFreq, usedSourceFields, aTpe, bTpe) match {
        case Some(sourceField) =>
          usedSourceFields += sourceField.index
          FieldMapping(sourceField, targetField)
        case None =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
              s"no matching field found for '${targetField.name}: ${targetField.tpe.show}' in source type."
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
    val exactMatch = sourceInfo.fields.find { sf =>
      !usedSourceFields.contains(sf.index) && sf.name == targetField.name && sf.tpe =:= targetField.tpe
    }
    if (exactMatch.isDefined) return exactMatch

    // Priority 2: Name match with coercion
    val nameWithCoercion = sourceInfo.fields.find { sf =>
      !usedSourceFields.contains(sf.index) && sf.name == targetField.name && isCoercible(sf.tpe, targetField.tpe)
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

      val uniqueCoercibleMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && isCoercible(sf.tpe, targetField.tpe)
        }
      }
      if (uniqueCoercibleMatch.isDefined) return uniqueCoercibleMatch
    }

    // Priority 4: Position + matching type
    if (targetField.index < sourceInfo.fields.size) {
      val positionalField = sourceInfo.fields(targetField.index)
      if (!usedSourceFields.contains(positionalField.index)) {
        if (positionalField.tpe =:= targetField.tpe) return Some(positionalField)
        if (isCoercible(positionalField.tpe, targetField.tpe)) return Some(positionalField)
      }
    }

    None
  }

  private def isCoercible(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    sourceTpe =:= targetTpe

  private def generateProductConversion[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[Into[A, B]] = {
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ constructTarget[A, B](sourceInfo, targetInfo, fieldMappings, 'a) })
      }
    }
  }

  private def constructTarget[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping],
    aExpr: Expr[A]
  ): Expr[B] = {
    val args = fieldMappings.map { mapping =>
      sourceInfo.fieldGetter(aExpr.asTerm, mapping.sourceField)
    }
    targetInfo.construct(args).asExprOf[B]
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

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ constructTupleFromCaseClass[A, B](sourceInfo, bTpe, 'a) })
      }
    }
  }

  private def constructTupleFromCaseClass[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    bTpe: TypeRepr,
    aExpr: Expr[A]
  ): Expr[B] = {
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

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ constructCaseClassFromTuple[A, B](aTpe, targetInfo, 'a) })
      }
    }
  }

  private def constructCaseClassFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    targetInfo: ProductInfo[B],
    aExpr: Expr[A]
  ): Expr[B] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
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

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ constructTupleFromTuple[A, B](aTpe, bTpe, 'a) })
      }
    }
  }

  private def constructTupleFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    aExpr: Expr[A]
  ): Expr[B] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val args = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
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
}

