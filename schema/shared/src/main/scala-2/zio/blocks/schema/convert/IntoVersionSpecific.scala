package zio.blocks.schema.convert

import zio.blocks.schema.CommonMacroOps
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait IntoVersionSpecific {
  def derived[A, B]: Into[A, B] = macro IntoVersionSpecificImpl.derived[A, B]
}

private object IntoVersionSpecificImpl {
  def derived[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[Into[A, B]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias
    val bTpe = weakTypeOf[B].dealias

    // Cache for Into instances to handle recursive resolution
    val intoRefs = scala.collection.mutable.HashMap[(Type, Type), Tree]()

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def isProductType(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    // === Field Info ===

    class FieldInfo(
      val name: String,
      val tpe: Type,
      val index: Int,
      val getter: MethodSymbol
    )

    class ProductInfo(tpe: Type) {
      val tpeTypeArgs: List[Type] = typeArgs(tpe)

      val fields: List[FieldInfo] = {
        var getters = Map.empty[String, MethodSymbol]
        tpe.members.foreach {
          case m: MethodSymbol if m.isParamAccessor =>
            getters = getters.updated(NameTransformer.decode(m.name.toString), m)
          case _ =>
        }
        val tpeTypeParams =
          if (tpeTypeArgs ne Nil) tpe.typeSymbol.asClass.typeParams
          else Nil
        var idx = 0

        primaryConstructor(tpe).paramLists.flatten.map { param =>
          val symbol = param.asTerm
          val name   = NameTransformer.decode(symbol.name.toString)
          var fTpe   = symbol.typeSignature.dealias
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val getter = getters.getOrElse(
            name,
            fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
          )
          val fieldInfo = new FieldInfo(name, fTpe, idx, getter)
          idx += 1
          fieldInfo
        }
      }
    }

    // === Field Mapping ===

    class FieldMapping(
      val sourceField: FieldInfo,
      val targetField: FieldInfo
    )

    def matchFields(
      sourceInfo: ProductInfo,
      targetInfo: ProductInfo
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
      val sourceTypeFreq = sourceInfo.fields.groupBy(_.tpe.dealias.toString).view.mapValues(_.size).toMap
      val targetTypeFreq = targetInfo.fields.groupBy(_.tpe.dealias.toString).view.mapValues(_.size).toMap

      // Track which source fields have been used
      val usedSourceFields = scala.collection.mutable.Set[Int]()

      targetInfo.fields.map { targetField =>
        findMatchingSourceField(
          targetField,
          sourceInfo,
          sourceTypeFreq,
          targetTypeFreq,
          usedSourceFields
        ) match {
          case Some(sourceField) =>
            usedSourceFields += sourceField.index
            new FieldMapping(sourceField, targetField)
          case None =>
            // If target field is Option[T] and no source field found, use None
            if (isOptionType(targetField.tpe)) {
              new FieldMapping(null, targetField) // null indicates to use None
            } else {
              fail(
                s"Cannot derive Into[$aTpe, $bTpe]: " +
                  s"no matching field found for '${targetField.name}: ${targetField.tpe}' in source type. " +
                  s"Fields must match by: (1) name+type, (2) name+coercible type, (3) unique type, or (4) position+unique type."
              )
            }
        }
      }
    }

    def isOptionType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == definitions.OptionClass ||
      dealiased.typeConstructor.typeSymbol == definitions.OptionClass
    }

    def findMatchingSourceField(
      targetField: FieldInfo,
      sourceInfo: ProductInfo,
      sourceTypeFreq: Map[String, Int],
      targetTypeFreq: Map[String, Int],
      usedSourceFields: scala.collection.mutable.Set[Int]
    ): Option[FieldInfo] = {
      // Priority 1: Exact match - same name + same type
      val exactMatch = sourceInfo.fields.find { sourceField =>
        !usedSourceFields.contains(sourceField.index) &&
          sourceField.name == targetField.name &&
          sourceField.tpe =:= targetField.tpe
      }
      if (exactMatch.isDefined) return exactMatch

      // Priority 2: Name match with coercion - same name + coercible type or implicit Into available
      val nameWithCoercion = sourceInfo.fields.find { sourceField =>
        !usedSourceFields.contains(sourceField.index) &&
          sourceField.name == targetField.name &&
          findImplicitInto(sourceField.tpe, targetField.tpe).isDefined
      }
      if (nameWithCoercion.isDefined) return nameWithCoercion

      // Priority 3: Unique type match - type appears only once in both source and target
      val targetTypeKey = targetField.tpe.dealias.toString
      val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1

      if (isTargetTypeUnique) {
        val uniqueTypeMatch = sourceInfo.fields.find { sourceField =>
          if (usedSourceFields.contains(sourceField.index)) false
          else {
            val sourceTypeKey = sourceField.tpe.dealias.toString
            val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceTypeKey, 0) == 1
            isSourceTypeUnique && sourceField.tpe =:= targetField.tpe
          }
        }
        if (uniqueTypeMatch.isDefined) return uniqueTypeMatch

        // Also check for unique coercible type match (including implicit Into)
        val uniqueCoercibleMatch = sourceInfo.fields.find { sourceField =>
          if (usedSourceFields.contains(sourceField.index)) false
          else {
            val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceField.tpe.dealias.toString, 0) == 1
            isSourceTypeUnique && findImplicitInto(sourceField.tpe, targetField.tpe).isDefined
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
          // Also check coercible for positional (including implicit Into)
          if (findImplicitInto(positionalField.tpe, targetField.tpe).isDefined) {
            return Some(positionalField)
          }
        }
      }

      // Fallback: No match found
      None
    }

    // isCoercible has been removed - implicit Into resolution now handles all type conversions
    // including numeric widening/narrowing

    // Find or use cached Into instance
    def findImplicitInto(sourceTpe: Type, targetTpe: Type): Option[Tree] = {
      // Check cache first
      intoRefs.get((sourceTpe, targetTpe)) match {
        case some @ Some(_) => some
        case None =>
          // Try to find implicit
          val intoType = c.universe.appliedType(
            c.universe.typeOf[Into[Any, Any]].typeConstructor,
            List(sourceTpe, targetTpe)
          )
          val intoInstance = c.inferImplicitValue(intoType, silent = true)

          if (intoInstance != EmptyTree) {
            // Cache it for future use
            intoRefs.update((sourceTpe, targetTpe), intoInstance)
            Some(intoInstance)
          } else {
            None
          }
      }
    }

    // === Tuple utilities ===

    def isTupleType(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName
      name.startsWith("scala.Tuple") && name != "scala.Tuple"
    }

    def getTupleTypeArgs(tpe: Type): List[Type] = typeArgs(tpe)

    // === Derivation: Case Class to Case Class ===

    def deriveProductToProduct(): c.Expr[Into[A, B]] = {
      val sourceInfo = new ProductInfo(aTpe)
      val targetInfo = new ProductInfo(bTpe)
      val fieldMappings = matchFields(sourceInfo, targetInfo)

      // Build field conversions that return Either[SchemaError, FieldType]
      val fieldEithers = fieldMappings.zip(targetInfo.fields).map { case (mapping, targetField) =>
        if (mapping.sourceField == null) {
          // No source field - target must be Option[T], provide None wrapped in Right
          q"_root_.scala.Right(_root_.scala.None)"
        } else {
          val sourceField = mapping.sourceField
          val getter = sourceField.getter
          val sourceTpe = sourceField.tpe
          val targetTpe = targetField.tpe

          // If types differ, try to find an implicit Into instance
          if (!(sourceTpe =:= targetTpe)) {
            findImplicitInto(sourceTpe, targetTpe) match {
              case Some(intoInstance) =>
                // Use Into instance, which already returns Either
                q"$intoInstance.into(a.$getter)"
              case None =>
                // No coercion available - fail at compile time
                fail(
                  s"Cannot find implicit Into[$sourceTpe, $targetTpe] for field '${sourceField.name}'. " +
                  s"Please provide an implicit Into instance in scope."
                )
            }
          } else {
            // Types match - wrap in Right
            q"_root_.scala.Right(a.$getter)"
          }
        }
      }

      // Build nested flatMap chain to sequence Either values
      def buildFlatMapChain(eithers: List[Tree], accumulatedVals: List[TermName]): Tree = {
        eithers match {
          case Nil =>
            // All fields sequenced - construct target with accumulated values
            val constructorArgs = accumulatedVals.reverse.map(v => q"$v")
            q"_root_.scala.Right(new $bTpe(..$constructorArgs))"

          case eitherExpr :: tail =>
            // Generate a unique variable name for this field
            val valName = TermName(c.freshName("field"))
            val restExpr = buildFlatMapChain(tail, valName :: accumulatedVals)

            q"""
              $eitherExpr.flatMap { case $valName =>
                $restExpr
              }
            """
        }
      }

      val sequencedExpr = buildFlatMapChain(fieldEithers, Nil)

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              $sequencedExpr
            }
          }
        """
      )
    }

    // === Derivation: Case Class to Tuple ===

    def deriveCaseClassToTuple(): c.Expr[Into[A, B]] = {
      val sourceInfo = new ProductInfo(aTpe)
      val targetTypeArgs = getTupleTypeArgs(bTpe)

      // Check field count matches
      if (sourceInfo.fields.size != targetTypeArgs.size) {
        fail(s"Cannot derive Into[$aTpe, $bTpe]: field count mismatch (${sourceInfo.fields.size} vs ${targetTypeArgs.size})")
      }

      // Check types match by position
      sourceInfo.fields.zip(targetTypeArgs).zipWithIndex.foreach { case ((field, targetTpe), idx) =>
        if (!(field.tpe =:= targetTpe) && findImplicitInto(field.tpe, targetTpe).isEmpty) {
          fail(s"Cannot derive Into[$aTpe, $bTpe]: type mismatch at position $idx: ${field.tpe} vs $targetTpe")
        }
      }

      // Build tuple arguments by reading from source using getters
      val args = sourceInfo.fields.map { field =>
        q"a.${field.getter}"
      }

      val tupleCompanion = bTpe.typeSymbol.companion
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right($tupleCompanion(..$args))
            }
          }
        """
      )
    }

    // === Derivation: Tuple to Case Class ===

    def deriveTupleToCaseClass(): c.Expr[Into[A, B]] = {
      val sourceTypeArgs = getTupleTypeArgs(aTpe)
      val targetInfo = new ProductInfo(bTpe)

      // Check field count matches
      if (sourceTypeArgs.size != targetInfo.fields.size) {
        fail(s"Cannot derive Into[$aTpe, $bTpe]: field count mismatch (${sourceTypeArgs.size} vs ${targetInfo.fields.size})")
      }

      // Check types match by position
      sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.foreach { case ((sourceTpe, field), idx) =>
        if (!(sourceTpe =:= field.tpe) && findImplicitInto(sourceTpe, field.tpe).isEmpty) {
          fail(s"Cannot derive Into[$aTpe, $bTpe]: type mismatch at position $idx: $sourceTpe vs ${field.tpe}")
        }
      }

      // Build case class arguments by reading from tuple using _1, _2, etc.
      val args = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
        val accessor = TermName(s"_${idx + 1}")
        q"a.$accessor"
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(new $bTpe(..$args))
            }
          }
        """
      )
    }

    // === Derivation: Tuple to Tuple ===

    def deriveTupleToTuple(): c.Expr[Into[A, B]] = {
      val sourceTypeArgs = getTupleTypeArgs(aTpe)
      val targetTypeArgs = getTupleTypeArgs(bTpe)

      // Check element count matches
      if (sourceTypeArgs.size != targetTypeArgs.size) {
        fail(s"Cannot derive Into[$aTpe, $bTpe]: element count mismatch (${sourceTypeArgs.size} vs ${targetTypeArgs.size})")
      }

      // Check types match by position
      sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.foreach { case ((sourceTpe, targetTpe), idx) =>
        if (!(sourceTpe =:= targetTpe) && findImplicitInto(sourceTpe, targetTpe).isEmpty) {
          fail(s"Cannot derive Into[$aTpe, $bTpe]: type mismatch at position $idx: $sourceTpe vs $targetTpe")
        }
      }

      // Build tuple arguments by reading from source tuple using _1, _2, etc.
      val args = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
        val accessor = TermName(s"_${idx + 1}")
        q"a.$accessor"
      }

      val tupleCompanion = bTpe.typeSymbol.companion
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right($tupleCompanion(..$args))
            }
          }
        """
      )
    }

    // === Coproduct utilities ===

    def isSealedTrait(tpe: Type): Boolean = {
      val sym = tpe.typeSymbol
      sym.isClass && sym.asClass.isSealed
    }

    def directSubTypes(tpe: Type): List[Type] = CommonMacroOps.directSubTypes(c)(tpe)

    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    /** Get the name of a subtype - handles case objects and case classes */
    def getSubtypeName(tpe: Type): String = {
      val sym = tpe.typeSymbol
      if (sym.isModuleClass) {
        // Case object - use the module name
        sym.name.decodedName.toString.stripSuffix("$")
      } else {
        sym.name.decodedName.toString
      }
    }

    /** Get the type signature of a case class/object - list of field types */
    def getTypeSignature(tpe: Type): List[Type] = {
      if (isEnumOrModuleValue(tpe)) {
        // Case object - no fields
        Nil
      } else if (isProductType(tpe)) {
        // Case class - get field types
        val info = new ProductInfo(tpe)
        info.fields.map(_.tpe)
      } else {
        Nil
      }
    }

    def signaturesMatch(source: List[Type], target: List[Type]): Boolean = {
      source.size == target.size && source.zip(target).forall { case (s, t) =>
        s =:= t || findImplicitInto(s, t).isDefined
      }
    }

    def findMatchingTargetSubtype(
      sourceSubtype: Type,
      targetSubtypes: List[Type],
      sourceSubtypes: List[Type]
    ): Option[Type] = {
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
      val sourceIdx = sourceSubtypes.indexOf(sourceSubtype)
      if (sourceIdx >= 0 && sourceIdx < targetSubtypes.size) {
        return Some(targetSubtypes(sourceIdx))
      }

      None
    }

    // === Derivation: Coproduct to Coproduct ===

    def deriveCoproductToCoproduct(): c.Expr[Into[A, B]] = {
      val sourceSubtypes = directSubTypes(aTpe)
      val targetSubtypes = directSubTypes(bTpe)

      // Build case mapping: for each source subtype, find matching target subtype
      val caseMappings = sourceSubtypes.map { sourceSubtype =>
        findMatchingTargetSubtype(sourceSubtype, targetSubtypes, sourceSubtypes) match {
          case Some(targetSubtype) => (sourceSubtype, targetSubtype)
          case None =>
            fail(
              s"Cannot derive Into[$aTpe, $bTpe]: " +
                s"no matching target case found for source case '${sourceSubtype.typeSymbol.name}'"
            )
        }
      }

      // Generate match cases
      val cases = caseMappings.map { case (sourceSubtype, targetSubtype) =>
        generateCaseClause(sourceSubtype, targetSubtype)
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              a match { case ..$cases }
            }
          }
        """
      )
    }

    def generateCaseClause(sourceSubtype: Type, targetSubtype: Type): CaseDef = {
      val sourceSym = sourceSubtype.typeSymbol
      val targetSym = targetSubtype.typeSymbol

      if (isEnumOrModuleValue(sourceSubtype) && isEnumOrModuleValue(targetSubtype)) {
        // Case object to case object
        val sourceModule = sourceSym.asClass.module
        val targetModule = targetSym.asClass.module
        cq"_: $sourceSubtype => _root_.scala.Right($targetModule)"
      } else {
        // Case class to case class
        generateCaseClassClause(sourceSubtype, targetSubtype)
      }
    }

    def generateCaseClassClause(sourceSubtype: Type, targetSubtype: Type): CaseDef = {
      val sourceInfo = new ProductInfo(sourceSubtype)
      val targetInfo = new ProductInfo(targetSubtype)

      // Match fields between source and target case classes
      val fieldMappings = matchFields(sourceInfo, targetInfo)

      // Generate binding pattern: case x: SourceType => ...
      val bindingName = TermName("x")

      // Build field conversions that return Either[SchemaError, FieldType]
      val fieldEithers = fieldMappings.zip(targetInfo.fields).map { case (mapping, targetField) =>
        if (mapping.sourceField == null) {
          // No source field - target must be Option[T], provide None wrapped in Right
          q"_root_.scala.Right(_root_.scala.None)"
        } else {
          val sourceField = mapping.sourceField
          val getter = sourceField.getter
          val sourceTpe = sourceField.tpe
          val targetTpe = targetField.tpe

          // If types differ, try to find an implicit Into instance
          if (!(sourceTpe =:= targetTpe)) {
            findImplicitInto(sourceTpe, targetTpe) match {
              case Some(intoInstance) =>
                // Use Into instance, which already returns Either
                q"$intoInstance.into($bindingName.$getter)"
              case None =>
                // No coercion available - fail at compile time
                fail(
                  s"Cannot find implicit Into[$sourceTpe, $targetTpe] for field in coproduct case. " +
                  s"Please provide an implicit Into instance in scope."
                )
            }
          } else {
            // Types match - wrap in Right
            q"_root_.scala.Right($bindingName.$getter)"
          }
        }
      }

      // Build nested flatMap chain to sequence Either values
      def buildFlatMapChain(eithers: List[Tree], accumulatedVals: List[TermName]): Tree = {
        eithers match {
          case Nil =>
            // All fields sequenced - construct target with accumulated values
            val constructorArgs = accumulatedVals.reverse.map(v => q"$v")
            q"_root_.scala.Right(new $targetSubtype(..$constructorArgs))"

          case eitherExpr :: tail =>
            // Generate a unique variable name for this field
            val valName = TermName(c.freshName("field"))
            val restExpr = buildFlatMapChain(tail, valName :: accumulatedVals)

            q"""
              $eitherExpr.flatMap { case $valName =>
                $restExpr
              }
            """
        }
      }

      val sequencedExpr = buildFlatMapChain(fieldEithers, Nil)

      cq"$bindingName: $sourceSubtype => $sequencedExpr"
    }

    // === Structural Type Support ===

    def isStructuralType(tpe: Type): Boolean = {
      tpe.dealias match {
        case RefinedType(_, _) => true
        case _ => false
      }
    }

    def getStructuralMembers(tpe: Type): List[(String, Type)] = {
      tpe.dealias.members.collect {
        case m: MethodSymbol if m.isPublic && !m.isConstructor && m.paramLists.isEmpty && !isSyntheticMethod(m) =>
          val name = NameTransformer.decode(m.name.toString)
          val returnType = m.returnType.dealias
          (name, returnType)
      }.toList.reverse
    }

    def isSyntheticMethod(m: MethodSymbol): Boolean = {
      val name = m.name.toString
      // Filter out synthetic methods that come from Any, AnyRef, or compiler-generated methods
      name == "isInstanceOf" || name == "asInstanceOf" || name == "==" || name == "!=" ||
      name == "##" || name == "hashCode" || name == "equals" || name == "toString" ||
      name == "getClass" || name == "ne" || name == "eq" || name == "notify" ||
      name == "notifyAll" || name == "wait" || name == "synchronized" || name == "clone" ||
      name == "finalize"
    }

    // === Derivation: Structural Type to Product ===

    def deriveStructuralToProduct(): c.Expr[Into[A, B]] = {
      val structuralMembers = getStructuralMembers(aTpe)
      val targetInfo = new ProductInfo(bTpe)

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
          case None =>
            fail(
              s"Cannot derive Into[$aTpe, $bTpe]: no matching structural member found for field '${targetField.name}: ${targetField.tpe}'"
            )
        }
      }

      // Generate code that uses reflection to access structural type members
      val args = fieldMappings.map { case (memberName, targetField) =>
        val methodName = TermName(memberName)
        q"a.$methodName"
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              import scala.language.reflectiveCalls
              _root_.scala.Right(new $bTpe(..$args))
            }
          }
        """
      )
    }

    // === Derivation: Product to Structural Type ===

    def deriveProductToStructural(): c.Expr[Into[A, B]] = {
      val sourceInfo = new ProductInfo(aTpe)
      val structuralMembers = getStructuralMembers(bTpe)

      // For each structural member, find matching source field
      val fieldMappings = structuralMembers.map { case (memberName, memberTpe) =>
        val matchingField = sourceInfo.fields.find { field =>
          field.name == memberName && field.tpe =:= memberTpe
        }.orElse {
          val uniqueTypeMatches = sourceInfo.fields.filter(_.tpe =:= memberTpe)
          if (uniqueTypeMatches.size == 1) Some(uniqueTypeMatches.head) else None
        }

        matchingField match {
          case Some(field) => (memberName, field)
          case None =>
            fail(
              s"Cannot derive Into[$aTpe, $bTpe]: no matching source field found for structural member '$memberName: $memberTpe'"
            )
        }
      }

      // Generate an anonymous class that implements the structural type
      // We create val definitions for each member
      val memberDefs = fieldMappings.map { case (memberName, sourceField) =>
        val methodName = TermName(memberName)
        val getter = sourceField.getter
        q"val $methodName = a.$getter"
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              import scala.language.reflectiveCalls
              _root_.scala.Right({
                new {
                  ..$memberDefs
                }
              }.asInstanceOf[$bTpe])
            }
          }
        """
      )
    }

    // === Main entry point ===

    val aIsProduct = isProductType(aTpe)
    val bIsProduct = isProductType(bTpe)
    val aIsTuple = isTupleType(aTpe)
    val bIsTuple = isTupleType(bTpe)
    val aIsCoproduct = isSealedTrait(aTpe)
    val bIsCoproduct = isSealedTrait(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)

    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct, aIsStructural, bIsStructural) match {
      case (true, true, _, _, _, _, _, _) =>
        // Case class to case class
        deriveProductToProduct()
      case (true, _, _, true, _, _, _, _) =>
        // Case class to tuple
        deriveCaseClassToTuple()
      case (_, true, true, _, _, _, _, _) =>
        // Tuple to case class
        deriveTupleToCaseClass()
      case (_, _, true, true, _, _, _, _) =>
        // Tuple to tuple
        deriveTupleToTuple()
      case (_, _, _, _, true, true, _, _) =>
        // Coproduct to coproduct (sealed trait to sealed trait)
        deriveCoproductToCoproduct()
      case (_, true, _, _, _, _, true, _) =>
        // Structural type -> Product (structural source to case class target)
        deriveStructuralToProduct()
      case (true, _, _, _, _, _, _, true) =>
        // Product -> Structural (case class source to structural target)
        deriveProductToStructural()
      case _ =>
        fail(s"Cannot derive Into[$aTpe, $bTpe]: unsupported type combination")
    }
  }
}
