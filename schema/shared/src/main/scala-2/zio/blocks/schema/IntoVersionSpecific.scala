package zio.blocks.schema

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

    // Check if type is a case object (singleton type)
    def isCaseObjectType(tpe: Type): Boolean =
      tpe match {
        case SingleType(_, sym) =>
          sym.isModule && sym.asModule.moduleClass.asClass.isCaseClass
        case _ =>
          // Also check for constant types or other singleton representations
          val sym = tpe.typeSymbol
          sym.isModuleClass && sym.asClass.isCaseClass
      }

    // Get the module symbol for a case object type
    def getCaseObjectModule(tpe: Type): Symbol =
      tpe match {
        case SingleType(_, sym) => sym
        case _                  => tpe.typeSymbol.asClass.module
      }

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    // === Field Info ===

    class FieldInfo(
      val name: String,
      val tpe: Type,
      val index: Int,
      val getter: MethodSymbol,
      val hasDefault: Boolean = false,
      val defaultValue: Option[Tree] = None
    )

    class ProductInfo(tpe: Type) {
      val tpeTypeArgs: List[Type] = typeArgs(tpe)

      // Companion module for accessing default values
      private lazy val companionSymbol: Option[Symbol] = {
        val companion = tpe.typeSymbol.companion
        if (companion != NoSymbol) Some(companion) else None
      }

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

          // Check for default value
          val paramIdx                   = idx + 1 // Scala 2 uses 1-based index for default methods
          val defaultValue: Option[Tree] = companionSymbol.flatMap { companion =>
            val defaultMethodName = TermName(s"$$lessinit$$greater$$default$$$paramIdx")
            val defaultMethod     = companion.info.member(defaultMethodName)
            if (defaultMethod != NoSymbol) {
              Some(q"${companion.asModule}.$defaultMethod")
            } else None
          }
          val hasDefault = defaultValue.isDefined

          val fieldInfo = new FieldInfo(name, fTpe, idx, getter, hasDefault, defaultValue)
          idx += 1
          fieldInfo
        }
      }
    }

    // === Field Mapping ===

    class FieldMapping(
      val sourceField: FieldInfo, // null means use default (None for Option types, or actual default value)
      val targetField: FieldInfo,
      val useDefaultValue: Boolean = false // true if we should use the target field's default value
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
          targetInfo,
          sourceTypeFreq,
          targetTypeFreq,
          usedSourceFields
        ) match {
          case Some(sourceField) =>
            usedSourceFields += sourceField.index
            new FieldMapping(sourceField, targetField)
          case None =>
            // No matching source field found - check for default value or Option type
            if (targetField.hasDefault && targetField.defaultValue.isDefined) {
              // Use the actual default value
              new FieldMapping(null, targetField, useDefaultValue = true)
            } else if (isOptionType(targetField.tpe)) {
              // Use None for Option types
              new FieldMapping(null, targetField, useDefaultValue = false)
            } else {
              val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
              val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
              fail(
                s"""Cannot derive Into[$aTpe, $bTpe]: Missing required field
                   |
                   |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                   |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                   |
                   |No source field found for target field '${targetField.name}: ${targetField.tpe}'.
                   |
                   |Fields are matched by:
                   |  1. Exact name + type match
                   |  2. Name match + coercible type (e.g., Int → Long)
                   |  3. Unique type (when only one field of that type exists)
                   |  4. Position + unique type (tuple-like matching)
                   |
                   |Consider:
                   |  - Adding field '${targetField.name}: ${targetField.tpe}' to ${aTpe.typeSymbol.name}
                   |  - Making '${targetField.name}' an Option[${targetField.tpe}] (defaults to None)
                   |  - Adding a default value for '${targetField.name}' in ${bTpe.typeSymbol.name}
                   |  - Providing an explicit Into[$aTpe, $bTpe] instance""".stripMargin
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
      targetInfo: ProductInfo,
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

      // Priority 2: Name match with coercion - same name + coercible type, implicit Into available, newtype, or single-field product conversion/unwrapping
      val nameWithCoercion = sourceInfo.fields.find { sourceField =>
        !usedSourceFields.contains(sourceField.index) &&
        sourceField.name == targetField.name &&
        (findImplicitInto(sourceField.tpe, targetField.tpe).isDefined ||
          requiresNewtypeConversion(sourceField.tpe, targetField.tpe) ||
          requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe) ||
          requiresSingleFieldProductConversion(sourceField.tpe, targetField.tpe) ||
          requiresSingleFieldProductUnwrapping(sourceField.tpe, targetField.tpe))
      }
      if (nameWithCoercion.isDefined) return nameWithCoercion

      // Priority 3: Unique type match - type appears only once in both source and target
      val targetTypeKey      = targetField.tpe.dealias.toString
      val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1

      if (isTargetTypeUnique) {
        val uniqueTypeMatch = sourceInfo.fields.find { sourceField =>
          if (usedSourceFields.contains(sourceField.index)) false
          else {
            val sourceTypeKey      = sourceField.tpe.dealias.toString
            val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceTypeKey, 0) == 1
            isSourceTypeUnique && sourceField.tpe =:= targetField.tpe
          }
        }
        if (uniqueTypeMatch.isDefined) return uniqueTypeMatch

        // Also check for unique coercible type match (including implicit Into, newtype, and single-field product conversion/unwrapping)
        val uniqueCoercibleMatch = sourceInfo.fields.find { sourceField =>
          if (usedSourceFields.contains(sourceField.index)) false
          else {
            val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceField.tpe.dealias.toString, 0) == 1
            isSourceTypeUnique && (findImplicitInto(sourceField.tpe, targetField.tpe).isDefined ||
              requiresNewtypeConversion(sourceField.tpe, targetField.tpe) ||
              requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe) ||
              requiresSingleFieldProductConversion(sourceField.tpe, targetField.tpe) ||
              requiresSingleFieldProductUnwrapping(sourceField.tpe, targetField.tpe))
          }
        }
        if (uniqueCoercibleMatch.isDefined) return uniqueCoercibleMatch
      }

      // Priority 4: Position + matching type - positional correspondence with matching type
      // BUT: Don't use positional matching if the source field has an exact name match with another target field
      if (targetField.index < sourceInfo.fields.size) {
        val positionalField = sourceInfo.fields(targetField.index)
        if (!usedSourceFields.contains(positionalField.index)) {
          // Check if this source field has an exact name match with some target field
          // If so, reserve it for that exact match instead of using it positionally
          val hasExactNameMatchElsewhere = targetInfo.fields.exists { otherTarget =>
            otherTarget.name == positionalField.name &&
            otherTarget.name != targetField.name &&
            (positionalField.tpe =:= otherTarget.tpe ||
              findImplicitInto(positionalField.tpe, otherTarget.tpe).isDefined)
          }

          if (!hasExactNameMatchElsewhere) {
            if (positionalField.tpe =:= targetField.tpe) {
              return Some(positionalField)
            }
            // Also check coercible for positional (including implicit Into, newtype, and single-field product conversion/unwrapping)
            if (
              findImplicitInto(positionalField.tpe, targetField.tpe).isDefined ||
              requiresNewtypeConversion(positionalField.tpe, targetField.tpe) ||
              requiresNewtypeUnwrapping(positionalField.tpe, targetField.tpe) ||
              requiresSingleFieldProductConversion(positionalField.tpe, targetField.tpe) ||
              requiresSingleFieldProductUnwrapping(positionalField.tpe, targetField.tpe)
            ) {
              return Some(positionalField)
            }
          }
        }
      }

      // Fallback: No match found
      None
    }

    // === ZIO Prelude Newtype Support ===

    /** Check if type is a ZIO Prelude newtype */
    def isZIONewtype(tpe: Type): Boolean = {
      val dealiased  = tpe.dealias
      val typeSymbol = dealiased.typeSymbol

      // Check if the type symbol name is "Type" (the inner type alias pattern)
      val typeName = typeSymbol.name.decodedName.toString

      if (typeName == "Type") {
        // Get the owner (which should be the companion object/module)
        val owner         = typeSymbol.owner
        val ownerFullName = owner.fullName

        // Check if owner.fullName contains ZIO Prelude newtype patterns
        // This avoids loading TASTy files which can cause version mismatches
        val isZIOPreludeNewtype = ownerFullName.contains("zio.prelude.Subtype") ||
          ownerFullName.contains("zio.prelude.Newtype")

        isZIOPreludeNewtype
      } else {
        false
      }
    }

    def requiresNewtypeConversion(sourceTpe: Type, targetTpe: Type): Boolean =
      isZIONewtype(targetTpe) && !(sourceTpe =:= targetTpe)

    def requiresNewtypeUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean =
      if (isZIONewtype(sourceTpe)) {
        getNewtypeUnderlying(sourceTpe) match {
          case Some(underlying) => targetTpe =:= underlying
          case None             => false
        }
      } else {
        false
      }

    // === Single-field Product Field Conversion Helpers (for use when converting fields) ===

    /**
     * Check if we need to convert a primitive field to a single-field product
     * wrapper field
     */
    def requiresSingleFieldProductConversion(sourceTpe: Type, targetTpe: Type): Boolean =
      // Target is a single-field product and source matches the single field's type exactly
      if (!isSingleFieldProduct(targetTpe)) false
      else {
        val fieldType = getSingleFieldInfo(targetTpe).tpe
        sourceTpe =:= fieldType
      }

    /**
     * Check if we need to unwrap a single-field product field to a primitive
     * field
     */
    def requiresSingleFieldProductUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean =
      // Source is a single-field product and target matches the single field's type exactly
      if (!isSingleFieldProduct(sourceTpe)) false
      else {
        val fieldType = getSingleFieldInfo(sourceTpe).tpe
        fieldType =:= targetTpe
      }

    /**
     * Check if a type is a single-field product (case class with exactly one
     * field)
     */
    def isSingleFieldProduct(tpe: Type): Boolean =
      isProductType(tpe) && {
        val info = new ProductInfo(tpe)
        info.fields.size == 1
      }

    /** Get the single field info for a single-field product type */
    def getSingleFieldInfo(tpe: Type): FieldInfo = {
      val info = new ProductInfo(tpe)
      info.fields.head
    }

    /**
     * Convert a value to a single-field product wrapper, returning
     * Either[SchemaError, Any]
     */
    def convertToSingleFieldProductEither(sourceExpr: Tree, targetTpe: Type): Tree = {
      val targetTypeTree = TypeTree(targetTpe)
      // Direct wrapping - types match exactly
      q"""
        _root_.scala.Right[_root_.zio.blocks.schema.SchemaError, $targetTypeTree](new $targetTpe($sourceExpr))
      """
    }

    /**
     * Unwrap a single-field product wrapper to its underlying value, returning
     * Either[SchemaError, Any]
     */
    def unwrapSingleFieldProductEither(sourceExpr: Tree, sourceTpe: Type, targetTpe: Type): Tree = {
      val fieldInfo      = getSingleFieldInfo(sourceTpe)
      val getter         = fieldInfo.getter
      val targetTypeTree = TypeTree(targetTpe)
      // Direct unwrapping - types match exactly
      q"""
        _root_.scala.Right[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]($sourceExpr.$getter)
      """
    }

    def getNewtypeUnderlying(tpe: Type): Option[Type] = {
      val dealiased = tpe.dealias

      dealiased match {
        case TypeRef(pre, sym, _) if sym.name.decodedName.toString == "Type" =>
          pre match {
            case SingleType(_, companionSym) if companionSym.isModule =>
              val companionType = companionSym.asModule.moduleClass.asType.toType

              // Find the Subtype or Newtype base class
              val baseClass = companionType.baseClasses.find { bc =>
                val name = bc.fullName
                name == "zio.prelude.Subtype" || name == "zio.prelude.Newtype"
              }

              baseClass.flatMap { cls =>
                val baseType = companionType.baseType(cls)
                baseType match {
                  case TypeRef(_, _, args) if args.nonEmpty => Some(args.head)
                  case _                                    => None
                }
              }
            case _ => None
          }
        case _ => None
      }
    }

    /** Convert value to ZIO Prelude newtype with validation */
    def convertToNewtypeEither(sourceExpr: Tree, sourceTpe: Type, targetTpe: Type, fieldName: String): Tree = {
      // First, try to find an implicit Into instance for this conversion
      val implicitInto = findImplicitInto(sourceTpe, targetTpe)

      val targetTypeTree = TypeTree(targetTpe)
      val fieldNameLit   = Literal(Constant(fieldName))

      implicitInto match {
        case Some(intoInstance) =>
          // Use the implicit Into instance (which may include validation)
          q"""$intoInstance.into($sourceExpr)"""

        case None =>
          // No implicit Into found - generate direct call to companion.make(value)
          // For ZIO Prelude newtypes, the type is like "SomeObject.Type"
          // We need to find the companion object (SomeObject) and call its make method

          val dealiased = targetTpe.dealias

          // Extract companion symbol from the type
          val companionOpt: Option[Symbol] = dealiased match {
            case TypeRef(pre, sym, _) if sym.name.toString == "Type" =>
              // The prefix contains the companion object
              pre match {
                case SingleType(_, companionSym) =>
                  Some(companionSym)
                case _ =>
                  // Try to find companion via the owner
                  val owner = sym.owner
                  if (owner.isModule) Some(owner) else None
              }
            case _ =>
              None
          }

          companionOpt match {
            case Some(companionSym) =>
              // Check if the companion has a `make` method
              val makeMethod = companionSym.typeSignature.member(TermName("make"))

              if (makeMethod != NoSymbol && makeMethod.isMethod) {
                // Generate: companion.make(value).toEither.left.map(err => SchemaError.conversionFailed(...))
                val companionRef = Ident(companionSym)

                q"""
                  {
                    val validation = $companionRef.make($sourceExpr)
                    val either = validation.toEither
                    either.left.map { err =>
                      _root_.zio.blocks.schema.SchemaError.conversionFailed(
                        _root_.scala.Nil,
                        "Validation failed for field '" + $fieldNameLit + "': " + err.toString
                      )
                    }: _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]
                  }
                """
              } else {
                // No make method found - fall back to asInstanceOf (no validation)
                q"""
                  _root_.scala.Right($sourceExpr.asInstanceOf[$targetTypeTree]): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]
                """
              }

            case None =>
              // Companion not found - fall back to asInstanceOf (no validation)
              q"""
                _root_.scala.Right($sourceExpr.asInstanceOf[$targetTypeTree]): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]
              """
          }
      }
    }

    // === Derivation: Newtype Conversion (primitive -> newtype) ===

    def deriveNewtypeConversion(): c.Expr[Into[A, B]] =
      // Delegate to convertToNewtypeEither for the actual conversion
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              ${convertToNewtypeEither(q"a", aTpe, bTpe, "value")}
            }
          }
        """
      )

    // === Derivation: Newtype Unwrapping (newtype -> primitive) ===

    def deriveNewtypeUnwrapping(): c.Expr[Into[A, B]] =
      // Newtype unwrapping is safe at runtime since they are the same type
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(a.asInstanceOf[$bTpe])
            }
          }
        """
      )

    // Find or use cached Into instance (also looks for As instances)
    def findImplicitInto(sourceTpe: Type, targetTpe: Type): Option[Tree] =
      // Check cache first
      intoRefs.get((sourceTpe, targetTpe)) match {
        case some @ Some(_) => some
        case None           =>
          // Try to find implicit Into first
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
            // Try to find implicit As[source, target] and extract Into from it
            val asType = c.universe.appliedType(
              c.universe.typeOf[As[Any, Any]].typeConstructor,
              List(sourceTpe, targetTpe)
            )
            val asInstance = c.inferImplicitValue(asType, silent = true)

            if (asInstance != EmptyTree) {
              // Found As[A, B], create Into[A, B] that delegates to as.into
              val intoFromAs = q"""
                new _root_.zio.blocks.schema.Into[$sourceTpe, $targetTpe] {
                  def into(input: $sourceTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTpe] =
                    $asInstance.into(input)
                }
              """
              intoRefs.update((sourceTpe, targetTpe), intoFromAs)
              Some(intoFromAs)
            } else {
              // Also try As[target, source] and extract reverse Into from it
              val asReverseType = c.universe.appliedType(
                c.universe.typeOf[As[Any, Any]].typeConstructor,
                List(targetTpe, sourceTpe)
              )
              val asReverseInstance = c.inferImplicitValue(asReverseType, silent = true)

              if (asReverseInstance != EmptyTree) {
                // Found As[B, A], create Into[A, B] that delegates to as.from
                val intoFromAsReverse = q"""
                  new _root_.zio.blocks.schema.Into[$sourceTpe, $targetTpe] {
                    def into(input: $sourceTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTpe] =
                      $asReverseInstance.from(input)
                  }
                """
                intoRefs.update((sourceTpe, targetTpe), intoFromAsReverse)
                Some(intoFromAsReverse)
              } else {
                None
              }
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
      val schemaErrorType = typeOf[SchemaError]
      val sourceInfo      = new ProductInfo(aTpe)
      val targetInfo      = new ProductInfo(bTpe)
      val fieldMappings   = matchFields(sourceInfo, targetInfo)

      val sourceTypeName = aTpe.typeSymbol.name.toString
      val targetTypeName = bTpe.typeSymbol.name.toString

      // Build field conversions that return Either[SchemaError, FieldType]
      // Also collect field names for error messages
      val fieldEithersWithNames: List[(Tree, String, String)] =
        fieldMappings.zip(targetInfo.fields).map { case (mapping, targetField) =>
          val targetFieldName = targetField.name
          if (mapping.sourceField == null) {
            // No source field - use default value or None for Option types
            val expr = if (mapping.useDefaultValue && targetField.defaultValue.isDefined) {
              // Use the actual default value
              val defaultValue = targetField.defaultValue.get
              q"_root_.scala.Right[$schemaErrorType, Any]($defaultValue)"
            } else {
              // Use None for Option types
              q"_root_.scala.Right[$schemaErrorType, Any](_root_.scala.None)"
            }
            (expr, "", targetFieldName)
          } else {
            val sourceField     = mapping.sourceField
            val getter          = sourceField.getter
            val sourceTpe       = sourceField.tpe
            val targetTpe       = targetField.tpe
            val sourceFieldName = sourceField.name

            // If types differ, try to find an implicit Into instance or handle newtype/single-field-product conversion
            val conversionExpr = if (!(sourceTpe =:= targetTpe)) {
              // Check if it's a newtype conversion (underlying -> newtype)
              val isNewtypeConversion = requiresNewtypeConversion(sourceTpe, targetTpe)
              // Check if it's a newtype unwrapping (newtype -> underlying)
              val isNewtypeUnwrapping = requiresNewtypeUnwrapping(sourceTpe, targetTpe)
              // Check if it's a single-field product conversion (primitive -> wrapper)
              val isSingleFieldConversion = requiresSingleFieldProductConversion(sourceTpe, targetTpe)
              // Check if it's a single-field product unwrapping (wrapper -> primitive)
              val isSingleFieldUnwrapping = requiresSingleFieldProductUnwrapping(sourceTpe, targetTpe)

              if (isNewtypeConversion) {
                // Generate code to convert to newtype, using implicit Into if available
                convertToNewtypeEither(q"a.$getter", sourceTpe, targetTpe, sourceField.name)
              } else if (isNewtypeUnwrapping) {
                // Unwrap newtype to underlying type - newtypes are type aliases, so just cast
                q"_root_.scala.Right[$schemaErrorType, Any](a.$getter.asInstanceOf[$targetTpe])"
              } else if (isSingleFieldConversion) {
                // Convert primitive to single-field product wrapper
                convertToSingleFieldProductEither(q"a.$getter", targetTpe)
              } else if (isSingleFieldUnwrapping) {
                // Unwrap single-field product to primitive
                unwrapSingleFieldProductEither(q"a.$getter", sourceTpe, targetTpe)
              } else {
                findImplicitInto(sourceTpe, targetTpe) match {
                  case Some(intoInstance) =>
                    // Use Into instance, which already returns Either
                    q"$intoInstance.into(a.$getter).asInstanceOf[_root_.scala.Either[$schemaErrorType, Any]]"
                  case None =>
                    // No coercion available - fail at compile time
                    fail(
                      s"""Cannot derive Into[$aTpe, $bTpe]: No implicit conversion for field
                         |
                         |  Field: ${sourceField.name}
                         |  Source type: $sourceTpe
                         |  Target type: $targetTpe
                         |
                         |No implicit Into[$sourceTpe, $targetTpe] was found in scope.
                         |
                         |Consider:
                         |  - Providing an implicit: implicit val ${sourceField.name}Into: Into[$sourceTpe, $targetTpe] = Into.derived
                         |  - Using Into.derived[$sourceTpe, $targetTpe] inline
                         |  - Changing the field types to be directly compatible
                         |  - Using numeric widening (Int → Long) or narrowing (Long → Int) if applicable""".stripMargin
                    )
                }
              }
            } else {
              // Types match - wrap in Right
              q"_root_.scala.Right[$schemaErrorType, Any](a.$getter)"
            }
            (conversionExpr, sourceFieldName, targetFieldName)
          }
        }

      val fieldEithers   = fieldEithersWithNames.map(_._1)
      val fieldNamePairs = fieldEithersWithNames.map { case (_, src, tgt) => (src, tgt) }

      // Build error-accumulating expression
      def buildAccumulatingExpr(eithers: List[Tree], targetTypes: List[Type], namePairs: List[(String, String)]): Tree =
        if (eithers.isEmpty) {
          // Empty case class
          q"_root_.scala.Right[$schemaErrorType, $bTpe](new $bTpe())"
        } else {
          val valNames    = eithers.indices.map(i => TermName(c.freshName(s"field$i"))).toList
          val evaluations = eithers.zip(valNames).map { case (expr, name) =>
            q"val $name: _root_.scala.Either[$schemaErrorType, Any] = $expr"
          }

          // Create properly typed constructor arguments
          val constructorArgs = valNames.zip(targetTypes).map { case (name, targetTpe) =>
            q"$name.toOption.get.asInstanceOf[$targetTpe]"
          }

          // Build error collection with field name enhancement
          val errorCollection = valNames.zip(namePairs).map { case (name, (srcField, tgtField)) =>
            val fieldDesc =
              if (srcField.nonEmpty) s"$sourceTypeName.$srcField to $targetTypeName.$tgtField"
              else s"$targetTypeName.$tgtField"
            val contextMsg = s"converting field $fieldDesc failed"
            q"""
              if ($name.isLeft) {
                _root_.scala.Some(_root_.zio.blocks.schema.SchemaError.conversionFailed($contextMsg, $name.swap.toOption.get))
              } else {
                _root_.scala.None
              }
            """
          }

          q"""
            ..$evaluations
            val _errorOptions: _root_.scala.List[_root_.scala.Option[$schemaErrorType]] = _root_.scala.List(..$errorCollection)
            val _errors: _root_.scala.List[$schemaErrorType] = _errorOptions.flatten
            if (_errors.isEmpty) {
              _root_.scala.Right[$schemaErrorType, $bTpe](new $bTpe(..$constructorArgs))
            } else {
              _root_.scala.Left[$schemaErrorType, $bTpe](_errors.reduceLeft((a, b) => a.++(b)))
            }
          """
        }

      val targetFieldTypes = targetInfo.fields.map(_.tpe)
      val sequencedExpr    = buildAccumulatingExpr(fieldEithers, targetFieldTypes, fieldNamePairs)

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              $sequencedExpr
            }
          }
        """
      )
    }

    // === Derivation: Case Class to Tuple ===

    def deriveCaseClassToTuple(): c.Expr[Into[A, B]] = {
      val sourceInfo     = new ProductInfo(aTpe)
      val targetTypeArgs = getTupleTypeArgs(bTpe)

      // Check field count matches
      if (sourceInfo.fields.size != targetTypeArgs.size) {
        val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
        val targetTypesStr  = targetTypeArgs.map(_.toString).mkString(", ")
        fail(
          s"""Cannot derive Into[$aTpe, $bTpe]: Arity mismatch
             |
             |  ${aTpe.typeSymbol.name}($sourceFieldsStr)  — ${sourceInfo.fields.size} fields
             |  Tuple${targetTypeArgs.size}[$targetTypesStr]  — ${targetTypeArgs.size} elements
             |
             |Case class has ${sourceInfo.fields.size} fields but target tuple has ${targetTypeArgs.size} elements.
             |
             |Consider:
             |  - Using a tuple with ${sourceInfo.fields.size} elements
             |  - Adding/removing fields to match the tuple size
             |  - Providing an explicit Into instance""".stripMargin
        )
      }

      // Check types match by position
      sourceInfo.fields.zip(targetTypeArgs).zipWithIndex.foreach { case ((field, targetTpe), idx) =>
        if (!(field.tpe =:= targetTpe) && findImplicitInto(field.tpe, targetTpe).isEmpty) {
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Type mismatch at position $idx
               |
               |  Field: ${field.name} (position $idx)
               |  Source type: ${field.tpe}
               |  Target type: $targetTpe
               |
               |No implicit Into[${field.tpe}, $targetTpe] found.
               |
               |Consider:
               |  - Providing an implicit Into[${field.tpe}, $targetTpe]
               |  - Changing the types to be compatible
               |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
          )
        }
      }

      // Build tuple arguments by reading from source using getters
      val args = sourceInfo.fields.map { field =>
        q"a.${field.getter}"
      }

      val tupleCompanion = bTpe.typeSymbol.companion
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
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
      val targetInfo     = new ProductInfo(bTpe)

      // Check field count matches
      if (sourceTypeArgs.size != targetInfo.fields.size) {
        val sourceTypesStr  = sourceTypeArgs.map(_.toString).mkString(", ")
        val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
        fail(
          s"""Cannot derive Into[$aTpe, $bTpe]: Arity mismatch
             |
             |  Tuple${sourceTypeArgs.size}[$sourceTypesStr]  — ${sourceTypeArgs.size} elements
             |  ${bTpe.typeSymbol.name}($targetFieldsStr)  — ${targetInfo.fields.size} fields
             |
             |Source tuple has ${sourceTypeArgs.size} elements but target case class has ${targetInfo.fields.size} fields.
             |
             |Consider:
             |  - Using a case class with ${sourceTypeArgs.size} fields
             |  - Using a tuple with ${targetInfo.fields.size} elements
             |  - Providing an explicit Into instance""".stripMargin
        )
      }

      // Check types match by position
      sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.foreach { case ((sourceTpe, field), idx) =>
        if (!(sourceTpe =:= field.tpe) && findImplicitInto(sourceTpe, field.tpe).isEmpty) {
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Type mismatch at position $idx
               |
               |  Tuple element $idx: $sourceTpe
               |  Target field: ${field.name}: ${field.tpe}
               |
               |No implicit Into[$sourceTpe, ${field.tpe}] found.
               |
               |Consider:
               |  - Providing an implicit Into[$sourceTpe, ${field.tpe}]
               |  - Changing the types to be compatible
               |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
          )
        }
      }

      // Build case class arguments by reading from tuple using _1, _2, etc.
      val args = sourceTypeArgs.zipWithIndex.map { case (_, idx) =>
        val accessor = TermName(s"_${idx + 1}")
        q"a.$accessor"
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
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
        val sourceTypesStr = sourceTypeArgs.map(_.toString).mkString(", ")
        val targetTypesStr = targetTypeArgs.map(_.toString).mkString(", ")
        fail(
          s"""Cannot derive Into[$aTpe, $bTpe]: Arity mismatch
             |
             |  Tuple${sourceTypeArgs.size}[$sourceTypesStr]  — ${sourceTypeArgs.size} elements
             |  Tuple${targetTypeArgs.size}[$targetTypesStr]  — ${targetTypeArgs.size} elements
             |
             |Source tuple has ${sourceTypeArgs.size} elements but target tuple has ${targetTypeArgs.size} elements.
             |
             |Consider:
             |  - Using tuples with the same number of elements
             |  - Providing an explicit Into instance""".stripMargin
        )
      }

      // Check types match by position
      sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.foreach { case ((sourceTpe, targetTpe), idx) =>
        if (!(sourceTpe =:= targetTpe) && findImplicitInto(sourceTpe, targetTpe).isEmpty) {
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Type mismatch at position $idx
               |
               |  Source element _${idx + 1}: $sourceTpe
               |  Target element _${idx + 1}: $targetTpe
               |
               |No implicit Into[$sourceTpe, $targetTpe] found.
               |
               |Consider:
               |  - Providing an implicit Into[$sourceTpe, $targetTpe]
               |  - Changing the types to be compatible
               |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
          )
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
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
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
    def getTypeSignature(tpe: Type): List[Type] =
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

    def signaturesMatch(source: List[Type], target: List[Type]): Boolean =
      source.size == target.size && source.zip(target).forall { case (s, t) =>
        s =:= t || findImplicitInto(s, t).isDefined
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
          case None                =>
            val sourceCases    = sourceSubtypes.map(_.typeSymbol.name).mkString(", ")
            val targetCases    = targetSubtypes.map(_.typeSymbol.name).mkString(", ")
            val sourceCaseName = sourceSubtype.typeSymbol.name
            fail(
              s"""Cannot derive Into[$aTpe, $bTpe]: No matching case
                 |
                 |  ${aTpe.typeSymbol.name}: $sourceCases
                 |  ${bTpe.typeSymbol.name}: $targetCases
                 |
                 |Source case '$sourceCaseName' has no matching target case.
                 |
                 |Cases are matched by:
                 |  1. Name (case object or case class name)
                 |  2. Field signature (same field types in same order)
                 |
                 |Consider:
                 |  - Adding case '$sourceCaseName' to ${bTpe.typeSymbol.name}
                 |  - Renaming a target case to '$sourceCaseName'
                 |  - Ensuring field signatures match for signature-based matching
                 |  - Providing an explicit Into[$aTpe, $bTpe] instance""".stripMargin
            )
        }
      }

      // Generate match cases
      val cases = caseMappings.map { case (sourceSubtype, targetSubtype) =>
        generateCaseClause(sourceSubtype, targetSubtype)
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              a match { case ..$cases }
            }
          }
        """
      )
    }

    def generateCaseClause(sourceSubtype: Type, targetSubtype: Type): CaseDef = {
      val targetSym = targetSubtype.typeSymbol

      if (isEnumOrModuleValue(sourceSubtype) && isEnumOrModuleValue(targetSubtype)) {
        // Case object to case object
        val targetModule = targetSym.asClass.module
        cq"_: $sourceSubtype => _root_.scala.Right($targetModule)"
      } else {
        // Case class to case class
        generateCaseClassClause(sourceSubtype, targetSubtype)
      }
    }

    def generateCaseClassClause(sourceSubtype: Type, targetSubtype: Type): CaseDef = {
      val schemaErrorType = typeOf[SchemaError]
      val sourceInfo      = new ProductInfo(sourceSubtype)
      val targetInfo      = new ProductInfo(targetSubtype)
      val sourceTypeName  = sourceSubtype.typeSymbol.name.toString
      val targetTypeName  = targetSubtype.typeSymbol.name.toString

      // Match fields between source and target case classes
      val fieldMappings = matchFields(sourceInfo, targetInfo)

      // Generate binding pattern: case x: SourceType => ...
      val bindingName = TermName("x")

      // Build field conversions that return Either[SchemaError, Any] with field name tracking
      val fieldEithersWithNames: List[(Tree, String, String)] =
        fieldMappings.zip(targetInfo.fields).map { case (mapping, targetField) =>
          val targetFieldName = targetField.name
          if (mapping.sourceField == null) {
            // No source field - use default value or None for Option types
            val expr = if (mapping.useDefaultValue && targetField.defaultValue.isDefined) {
              val defaultValue = targetField.defaultValue.get
              q"_root_.scala.Right[$schemaErrorType, Any]($defaultValue)"
            } else {
              q"_root_.scala.Right[$schemaErrorType, Any](_root_.scala.None)"
            }
            (expr, "", targetFieldName)
          } else {
            val sourceField     = mapping.sourceField
            val getter          = sourceField.getter
            val sourceTpe       = sourceField.tpe
            val targetTpe       = targetField.tpe
            val sourceFieldName = sourceField.name

            val conversionExpr = if (!(sourceTpe =:= targetTpe)) {
              val isNewtypeConversion     = requiresNewtypeConversion(sourceTpe, targetTpe)
              val isNewtypeUnwrapping     = requiresNewtypeUnwrapping(sourceTpe, targetTpe)
              val isSingleFieldConversion = requiresSingleFieldProductConversion(sourceTpe, targetTpe)
              val isSingleFieldUnwrapping = requiresSingleFieldProductUnwrapping(sourceTpe, targetTpe)

              if (isNewtypeConversion) {
                convertToNewtypeEither(q"$bindingName.$getter", sourceTpe, targetTpe, sourceField.name)
              } else if (isNewtypeUnwrapping) {
                q"_root_.scala.Right[$schemaErrorType, Any]($bindingName.$getter.asInstanceOf[$targetTpe])"
              } else if (isSingleFieldConversion) {
                // Convert primitive to single-field product wrapper
                convertToSingleFieldProductEither(q"$bindingName.$getter", targetTpe)
              } else if (isSingleFieldUnwrapping) {
                // Unwrap single-field product to primitive
                unwrapSingleFieldProductEither(q"$bindingName.$getter", sourceTpe, targetTpe)
              } else {
                findImplicitInto(sourceTpe, targetTpe) match {
                  case Some(intoInstance) =>
                    q"$intoInstance.into($bindingName.$getter).asInstanceOf[_root_.scala.Either[$schemaErrorType, Any]]"
                  case None =>
                    fail(
                      s"Cannot find implicit Into[$sourceTpe, $targetTpe] for field in coproduct case. " +
                        s"Please provide an implicit Into instance in scope."
                    )
                }
              }
            } else {
              q"_root_.scala.Right[$schemaErrorType, Any]($bindingName.$getter)"
            }
            (conversionExpr, sourceFieldName, targetFieldName)
          }
        }

      val fieldEithers   = fieldEithersWithNames.map(_._1)
      val fieldNamePairs = fieldEithersWithNames.map { case (_, src, tgt) => (src, tgt) }

      // Build error-accumulating expression for coproduct case
      def buildAccumulatingExpr(eithers: List[Tree], targetTypes: List[Type], namePairs: List[(String, String)]): Tree =
        if (eithers.isEmpty) {
          q"_root_.scala.Right[$schemaErrorType, $targetSubtype](new $targetSubtype())"
        } else {
          val valNames    = eithers.indices.map(i => TermName(c.freshName(s"field$i"))).toList
          val evaluations = eithers.zip(valNames).map { case (expr, name) =>
            q"val $name: _root_.scala.Either[$schemaErrorType, Any] = $expr"
          }

          val constructorArgs = valNames.zip(targetTypes).map { case (name, targetTpe) =>
            q"$name.toOption.get.asInstanceOf[$targetTpe]"
          }

          // Build error collection with field name enhancement
          val errorCollection = valNames.zip(namePairs).map { case (name, (srcField, tgtField)) =>
            val fieldDesc =
              if (srcField.nonEmpty) s"$sourceTypeName.$srcField to $targetTypeName.$tgtField"
              else s"$targetTypeName.$tgtField"
            val contextMsg = s"converting field $fieldDesc failed"
            q"""
              if ($name.isLeft) {
                _root_.scala.Some(_root_.zio.blocks.schema.SchemaError.conversionFailed($contextMsg, $name.swap.toOption.get))
              } else {
                _root_.scala.None
              }
            """
          }

          q"""
            ..$evaluations
            val _errorOptions: _root_.scala.List[_root_.scala.Option[$schemaErrorType]] = _root_.scala.List(..$errorCollection)
            val _errors: _root_.scala.List[$schemaErrorType] = _errorOptions.flatten
            if (_errors.isEmpty) {
              _root_.scala.Right[$schemaErrorType, $targetSubtype](new $targetSubtype(..$constructorArgs))
            } else {
              _root_.scala.Left[$schemaErrorType, $targetSubtype](_errors.reduceLeft((a, b) => a.++(b)))
            }
          """
        }

      val targetFieldTypes = targetInfo.fields.map(_.tpe)
      val sequencedExpr    = buildAccumulatingExpr(fieldEithers, targetFieldTypes, fieldNamePairs)

      cq"$bindingName: $sourceSubtype => $sequencedExpr"
    }

    // === Structural Type Support ===

    def isStructuralType(tpe: Type): Boolean =
      tpe.dealias match {
        case RefinedType(_, _) => true
        case _                 => false
      }

    def getStructuralMembers(tpe: Type): List[(String, Type)] = {
      // Extract only the members declared in the refinement, not inherited ones
      def collectRefinementMembers(t: Type): List[(String, Type)] = t.dealias match {
        case RefinedType(parents, decls) =>
          // Get members declared in this refinement
          val declaredMembers = decls.collect {
            case m: MethodSymbol if !m.isConstructor && m.paramLists.isEmpty =>
              val name       = NameTransformer.decode(m.name.toString)
              val returnType = m.returnType.dealias
              (name, returnType)
          }.toList

          // Also collect from parent refinements (in case of nested refinements)
          val parentMembers = parents.flatMap(p => collectRefinementMembers(p))

          parentMembers ++ declaredMembers
        case _ => Nil
      }
      collectRefinementMembers(tpe)
    }

    // === Derivation: Structural Type to Product ===

    // Check if a structural type can be converted to a product type (nested structural support)
    def canConvertStructuralToProduct(structuralTpe: Type, productTpe: Type): Boolean = {
      if (!isStructuralType(structuralTpe)) return false
      if (!isProductType(productTpe)) return false

      val structMembers = getStructuralMembers(structuralTpe)
      val productInfo   = new ProductInfo(productTpe)

      productInfo.fields.forall { field =>
        structMembers.exists { case (name, memberTpe) =>
          name == field.name && (
            memberTpe =:= field.tpe ||
              findImplicitInto(memberTpe, field.tpe).isDefined ||
              canConvertStructuralToProduct(memberTpe, field.tpe)
          )
        } || field.hasDefault || isOptionType(field.tpe)
      }
    }

    // Generate conversion code for nested structural to product
    def generateNestedStructuralConversion(structuralTpe: Type, productTpe: Type, accessExpr: Tree): Tree = {
      val structMembers = getStructuralMembers(structuralTpe)
      val productInfo   = new ProductInfo(productTpe)

      val args = productInfo.fields.map { field =>
        val matchingMember = structMembers.find(_._1 == field.name)
        matchingMember match {
          case Some((memberName, memberTpe)) =>
            val methodName   = TermName(memberName)
            val nestedAccess = q"$accessExpr.$methodName"
            if (memberTpe =:= field.tpe) {
              nestedAccess
            } else if (isStructuralType(memberTpe) && isProductType(field.tpe)) {
              generateNestedStructuralConversion(memberTpe, field.tpe, nestedAccess)
            } else {
              findImplicitInto(memberTpe, field.tpe) match {
                case Some(intoInstance) =>
                  q"""
                    $intoInstance.into($nestedAccess) match {
                      case _root_.scala.Right(v) => v
                      case _root_.scala.Left(_) => throw new _root_.java.lang.RuntimeException("Conversion failed")
                    }
                  """
                case None => nestedAccess
              }
            }
          case None =>
            if (field.hasDefault && field.defaultValue.isDefined) field.defaultValue.get
            else q"_root_.scala.None"
        }
      }
      q"new $productTpe(..$args)"
    }

    def deriveStructuralToProduct(): c.Expr[Into[A, B]] = {
      val structuralMembers = getStructuralMembers(aTpe)
      val targetInfo        = new ProductInfo(bTpe)

      // Track which structural members have been used to avoid duplicate mappings
      val usedMembers = scala.collection.mutable.Set[(String, Type)]()

      // For each target field, find matching structural member using priority algorithm:
      // 1. Exact match: Same name + same type
      // 2. Name match with coercion: Same name + coercible type
      // 3. Unique type match: Type appears only once in both source and target
      // 4. Use default value or None for optional fields
      val fieldMappings: List[(Option[(String, Type)], FieldInfo)] = targetInfo.fields.map { targetField =>
        // Priority 1: Exact name and type match
        val exactMatch: Option[(String, Type)] = structuralMembers.find { case (name, memberTpe) =>
          name == targetField.name && memberTpe =:= targetField.tpe && !usedMembers.contains((name, memberTpe))
        }

        // Priority 2: Name match with coercion (same name, convertible type)
        val nameMatchWithCoercion: Option[(String, Type)] = if (exactMatch.isEmpty) {
          structuralMembers.find { case (name, memberTpe) =>
            name == targetField.name &&
            (findImplicitInto(memberTpe, targetField.tpe).isDefined ||
              canConvertStructuralToProduct(memberTpe, targetField.tpe)) &&
            !usedMembers.contains((name, memberTpe))
          }
        } else None

        // Priority 3: Unique type match (type appears only once in both unused source and remaining target)
        val uniqueTypeMatch: Option[(String, Type)] = if (exactMatch.isEmpty && nameMatchWithCoercion.isEmpty) {
          val remainingTargetFields = targetInfo.fields.dropWhile(_ != targetField)
          val unusedMembers         = structuralMembers.filterNot(usedMembers.contains)

          // Find all members with compatible type
          val typeMatches = unusedMembers.filter { case (_, memberTpe) =>
            memberTpe =:= targetField.tpe ||
            findImplicitInto(memberTpe, targetField.tpe).isDefined ||
            canConvertStructuralToProduct(memberTpe, targetField.tpe)
          }

          // Also check if this type appears only once in remaining target fields
          val targetTypeCount = remainingTargetFields.count { f =>
            f.tpe =:= targetField.tpe
          }

          // Only use unique type match if type appears exactly once in both source and remaining target
          if (typeMatches.size == 1 && targetTypeCount == 1) Some(typeMatches.head) else None
        } else None

        val matchingMember = exactMatch.orElse(nameMatchWithCoercion).orElse(uniqueTypeMatch)

        matchingMember match {
          case Some((memberName, memberTpe)) =>
            usedMembers.add((memberName, memberTpe))
            (Some((memberName, memberTpe)), targetField)
          case None =>
            // No matching structural member - check for default value or Option type
            if (targetField.hasDefault && targetField.defaultValue.isDefined) {
              (None, targetField) // Will use default value
            } else if (isOptionType(targetField.tpe)) {
              (None, targetField) // Will use None
            } else {
              val sourceMembers = structuralMembers.map { case (n, t) => s"$n: $t" }.mkString(", ")
              val missingField  = s"${targetField.name}: ${targetField.tpe}"
              fail(
                s"""Cannot derive Into[$aTpe, $bTpe]: Missing required field
                   |
                   |  Source structural type: { $sourceMembers }
                   |  Missing field: $missingField
                   |
                   |The target type requires field '${targetField.name}' but no matching member was found in the structural type.
                   |
                   |Consider:
                   |  - Adding member '${targetField.name}: ${targetField.tpe}' to the structural type
                   |  - Making '${targetField.name}' an Option type (will default to None)
                   |  - Adding a default value for '${targetField.name}' in the target type""".stripMargin
              )
            }
        }
      }

      // Generate code that accesses structural type members using reflective calls (JVM only)
      val args = fieldMappings.map { case (memberOpt, targetField) =>
        memberOpt match {
          case Some((memberName, memberTpe)) =>
            // Use method call for structural types
            val methodName = TermName(memberName)
            val accessExpr = q"a.$methodName"

            if (memberTpe =:= targetField.tpe) {
              accessExpr
            } else if (isStructuralType(memberTpe) && isProductType(targetField.tpe)) {
              // Nested structural to product conversion
              generateNestedStructuralConversion(memberTpe, targetField.tpe, accessExpr)
            } else {
              // Need conversion via implicit Into
              findImplicitInto(memberTpe, targetField.tpe) match {
                case Some(intoInstance) =>
                  q"""
                    $intoInstance.into($accessExpr) match {
                      case _root_.scala.Right(v) => v
                      case _root_.scala.Left(_) => throw new _root_.java.lang.RuntimeException("Conversion failed")
                    }
                  """
                case None =>
                  accessExpr
              }
            }
          case None =>
            // Use default value or None
            if (targetField.hasDefault && targetField.defaultValue.isDefined) {
              targetField.defaultValue.get
            } else {
              // Option type - return None
              q"_root_.scala.None"
            }
        }
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              import _root_.scala.language.reflectiveCalls
              _root_.scala.Right(new $bTpe(..$args))
            }
          }
        """
      )
    }

    // === Derivation: Product to Structural Type ===

    // Check if a product type can be converted to a structural type (nested structural support)
    def canConvertProductToStructural(productTpe: Type, structuralTpe: Type): Boolean = {
      if (!isProductType(productTpe)) return false
      if (!isStructuralType(structuralTpe)) return false

      val productInfo   = new ProductInfo(productTpe)
      val structMembers = getStructuralMembers(structuralTpe)

      structMembers.forall { case (memberName, memberTpe) =>
        productInfo.fields.exists { field =>
          field.name == memberName && (
            field.tpe =:= memberTpe ||
              field.tpe <:< memberTpe ||
              canConvertProductToStructural(field.tpe, memberTpe)
          )
        }
      }
    }

    // Generate a nested structural type from a product
    def generateNestedProductToStructural(productTpe: Type, structuralTpe: Type, accessExpr: Tree): Tree = {
      val productInfo   = new ProductInfo(productTpe)
      val structMembers = getStructuralMembers(structuralTpe)

      val methodDefs = structMembers.map { case (memberName, memberTpe) =>
        val matchingField = productInfo.fields.find(_.name == memberName).get
        val fieldAccess   = q"$accessExpr.${TermName(matchingField.name)}"

        if (matchingField.tpe =:= memberTpe || matchingField.tpe <:< memberTpe) {
          q"def ${TermName(memberName)}: $memberTpe = $fieldAccess"
        } else if (isStructuralType(memberTpe) && isProductType(matchingField.tpe)) {
          val nestedExpr = generateNestedProductToStructural(matchingField.tpe, memberTpe, fieldAccess)
          q"def ${TermName(memberName)}: $memberTpe = $nestedExpr"
        } else {
          q"def ${TermName(memberName)}: $memberTpe = $fieldAccess"
        }
      }

      q"new { ..$methodDefs }"
    }

    def deriveProductToStructural(): c.Expr[Into[A, B]] = {
      val sourceInfo        = new ProductInfo(aTpe)
      val structuralMembers = getStructuralMembers(bTpe)

      // Check if we have nested structural types
      val hasNestedStructural = structuralMembers.exists { case (memberName, memberTpe) =>
        isStructuralType(memberTpe) && sourceInfo.fields.exists { field =>
          field.name == memberName && isProductType(field.tpe)
        }
      }

      // Validate that all structural type members can be satisfied
      structuralMembers.foreach { case (memberName, memberTpe) =>
        val matchingField = sourceInfo.fields.find { field =>
          field.name == memberName && (
            field.tpe =:= memberTpe ||
              field.tpe <:< memberTpe ||
              canConvertProductToStructural(field.tpe, memberTpe)
          )
        }
        if (matchingField.isEmpty) {
          fail(
            s"Cannot derive Into[$aTpe, $bTpe]: " +
              s"source type is missing member '$memberName: $memberTpe' required by structural type"
          )
        }
      }

      if (hasNestedStructural) {
        // Generate a proper anonymous class with nested structural types
        val methodDefs = structuralMembers.map { case (memberName, memberTpe) =>
          val matchingField = sourceInfo.fields.find(_.name == memberName).get
          val fieldAccess   = q"a.${TermName(matchingField.name)}"

          if (matchingField.tpe =:= memberTpe || matchingField.tpe <:< memberTpe) {
            q"def ${TermName(memberName)}: $memberTpe = $fieldAccess"
          } else if (isStructuralType(memberTpe) && isProductType(matchingField.tpe)) {
            val nestedExpr = generateNestedProductToStructural(matchingField.tpe, memberTpe, fieldAccess)
            q"def ${TermName(memberName)}: $memberTpe = $nestedExpr"
          } else {
            q"def ${TermName(memberName)}: $memberTpe = $fieldAccess"
          }
        }

        c.Expr[Into[A, B]](
          q"""
            new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
              def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                _root_.scala.Right((new { ..$methodDefs }).asInstanceOf[$bTpe])
              }
            }
          """
        )
      } else {
        // For simple structural types without nesting, use a simple cast
        c.Expr[Into[A, B]](
          q"""
            new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
              def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                _root_.scala.Right(a.asInstanceOf[$bTpe])
              }
            }
          """
        )
      }
    }

    // === Derivation: Case Object to Case Object ===

    def deriveCaseObjectToCaseObject(): c.Expr[Into[A, B]] = {
      // For case object to case object, we just return the target case object
      val targetModule = getCaseObjectModule(bTpe)
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right($targetModule)
            }
          }
        """
      )
    }

    // === Derivation: Case Object to Product (empty case class) ===

    def deriveCaseObjectToProduct(): c.Expr[Into[A, B]] = {
      // Case object to case class: target class fields must be satisfiable
      // (all optional or with defaults)
      val targetInfo = new ProductInfo(bTpe)

      // All target fields must have defaults or be Option types
      val fieldExprs = targetInfo.fields.map { field =>
        if (field.hasDefault && field.defaultValue.isDefined) {
          field.defaultValue.get
        } else if (isOptionType(field.tpe)) {
          q"_root_.scala.None"
        } else {
          val targetFieldsStr   = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
          val requiredFields    = targetInfo.fields.filterNot(f => f.hasDefault || isOptionType(f.tpe))
          val requiredFieldsStr = requiredFields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Case object to case class conversion
               |
               |  Source: case object ${aTpe.typeSymbol.name}
               |  Target: ${bTpe.typeSymbol.name}($targetFieldsStr)
               |
               |Case object cannot provide value for required field '${field.name}: ${field.tpe}'.
               |Required fields without defaults: $requiredFieldsStr
               |
               |Consider:
               |  - Making '${field.name}' an Option[${field.tpe}] (defaults to None)
               |  - Adding a default value for '${field.name}' in ${bTpe.typeSymbol.name}
               |  - Using a case class source instead of case object""".stripMargin
          )
        }
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(new $bTpe(..$fieldExprs))
            }
          }
        """
      )
    }

    // === Derivation: Product (empty case class) to Case Object ===

    def deriveProductToCaseObject(): c.Expr[Into[A, B]] = {
      // Any case class can be converted to a case object (fields are discarded)
      val targetModule = getCaseObjectModule(bTpe)
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right($targetModule)
            }
          }
        """
      )
    }

    // === Single-field Product (AnyVal wrapper) Top-Level Derivation ===

    def derivePrimitiveToSingleFieldProduct(): c.Expr[Into[A, B]] = {
      val fieldInfo = getSingleFieldInfo(bTpe)
      val fieldType = fieldInfo.tpe

      // Check if types are compatible (same or coercible)
      if (aTpe =:= fieldType) {
        // Direct wrapping - types match exactly
        c.Expr[Into[A, B]](
          q"""
            new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
              def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                _root_.scala.Right(new $bTpe(a))
              }
            }
          """
        )
      } else {
        // Try to find an implicit Into for the field type conversion
        findImplicitInto(aTpe, fieldType) match {
          case Some(fieldInto) =>
            c.Expr[Into[A, B]](
              q"""
                new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
                  def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                    $fieldInto.into(a).map(converted => new $bTpe(converted.asInstanceOf[$fieldType]))
                  }
                }
              """
            )
          case None =>
            fail(
              s"""Cannot derive Into[$aTpe, $bTpe]: No conversion available
                 |
                 |Source type: $aTpe
                 |Target field type: $fieldType
                 |
                 |The single field of $bTpe has type $fieldType, which is not compatible with $aTpe.
                 |
                 |Consider providing an implicit Into[$aTpe, $fieldType].""".stripMargin
            )
        }
      }
    }

    def deriveSingleFieldProductToPrimitive(): c.Expr[Into[A, B]] = {
      val fieldInfo = getSingleFieldInfo(aTpe)
      val fieldType = fieldInfo.tpe
      val getter    = fieldInfo.getter

      // Check if types are compatible (same or coercible)
      if (fieldType =:= bTpe) {
        // Direct unwrapping - types match exactly
        c.Expr[Into[A, B]](
          q"""
            new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
              def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                _root_.scala.Right(a.$getter)
              }
            }
          """
        )
      } else {
        // Try to find an implicit Into for the field type conversion
        findImplicitInto(fieldType, bTpe) match {
          case Some(fieldInto) =>
            c.Expr[Into[A, B]](
              q"""
                new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
                  def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
                    $fieldInto.into(a.$getter)
                  }
                }
              """
            )
          case None =>
            fail(
              s"""Cannot derive Into[$aTpe, $bTpe]: No conversion available
                 |
                 |Source field type: $fieldType
                 |Target type: $bTpe
                 |
                 |The single field of $aTpe has type $fieldType, which is not compatible with $bTpe.
                 |
                 |Consider providing an implicit Into[$fieldType, $bTpe].""".stripMargin
            )
        }
      }
    }

    // === Main entry point ===

    // Check if both types are primitives - if so, there should be a predefined instance
    def isPrimitiveOrBoxed(tpe: Type): Boolean = {
      val sym = tpe.typeSymbol
      sym == definitions.ByteClass || sym == definitions.ShortClass ||
      sym == definitions.IntClass || sym == definitions.LongClass ||
      sym == definitions.FloatClass || sym == definitions.DoubleClass ||
      sym == definitions.CharClass || sym == definitions.BooleanClass ||
      sym == definitions.StringClass
    }

    // For primitive-to-primitive conversions, look up the predefined implicit
    if (isPrimitiveOrBoxed(aTpe) && isPrimitiveOrBoxed(bTpe)) {
      val existingIntoType = c.universe.appliedType(
        c.universe.typeOf[Into[Any, Any]].typeConstructor,
        List(aTpe, bTpe)
      )
      val existingInto = c.inferImplicitValue(existingIntoType, silent = true, withMacrosDisabled = true)

      if (existingInto != EmptyTree) {
        return c.Expr[Into[A, B]](existingInto)
      } else {
        fail(
          s"""Cannot derive Into[$aTpe, $bTpe]: No primitive conversion
             |
             |No predefined conversion exists between '$aTpe' and '$bTpe'.
             |
             |Supported numeric conversions:
             |  - Widening: Byte → Short → Int → Long, Float → Double
             |  - Narrowing: Long → Int → Short → Byte (with runtime validation)
             |
             |Consider:
             |  - Using a supported numeric conversion path
             |  - Providing a custom implicit Into[$aTpe, $bTpe]""".stripMargin
        )
      }
    }

    // === DynamicValue Support ===

    val dynamicValueType = c.typeOf[DynamicValue]

    def findImplicitOrDeriveSchema(tpe: Type): Tree = {
      val schemaType = c.universe.appliedType(
        c.universe.typeOf[Schema[Any]].typeConstructor,
        List(tpe)
      )
      val implicitSchema = c.inferImplicitValue(schemaType, silent = true)
      if (implicitSchema != EmptyTree) {
        implicitSchema
      } else {
        q"_root_.zio.blocks.schema.Schema.derived[$tpe]"
      }
    }

    // Into[A, DynamicValue]: convert any A to DynamicValue using Schema[A].toDynamicValue
    def deriveToDynamicValue(): c.Expr[Into[A, B]] = {
      val schema = findImplicitOrDeriveSchema(aTpe)
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            private val _schema = $schema
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(_schema.toDynamicValue(a).asInstanceOf[$bTpe])
            }
          }
        """
      )
    }

    // Into[DynamicValue, A]: convert DynamicValue to any A using Schema[A].fromDynamicValue
    def deriveFromDynamicValue(): c.Expr[Into[A, B]] = {
      val schema = findImplicitOrDeriveSchema(bTpe)
      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
            private val _schema = $schema
            def into(dv: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _schema.fromDynamicValue(dv.asInstanceOf[_root_.zio.blocks.schema.DynamicValue])
            }
          }
        """
      )
    }

    // Check for DynamicValue conversions early
    if (bTpe =:= dynamicValueType) {
      return deriveToDynamicValue()
    }
    if (aTpe =:= dynamicValueType) {
      return deriveFromDynamicValue()
    }

    // Check for container types (Option, Either, List, Set, Map, etc.)
    // These have predefined implicit instances that should be discovered
    def isContainerType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      val sym       = dealiased.typeSymbol
      sym == definitions.OptionClass ||
      dealiased.typeConstructor =:= typeOf[Either[Any, Any]].typeConstructor ||
      dealiased.typeConstructor <:< typeOf[Iterable[Any]].typeConstructor ||
      dealiased.typeConstructor =:= typeOf[Array[Any]].typeConstructor ||
      dealiased <:< typeOf[Iterable[Any]] ||
      sym.fullName.startsWith("scala.collection")
    }

    // For container-to-container conversions, look up predefined implicit
    if (isContainerType(aTpe) || isContainerType(bTpe)) {
      val existingIntoType = c.universe.appliedType(
        c.universe.typeOf[Into[Any, Any]].typeConstructor,
        List(aTpe, bTpe)
      )
      val existingInto = c.inferImplicitValue(existingIntoType, silent = true, withMacrosDisabled = true)

      if (existingInto != EmptyTree) {
        return c.Expr[Into[A, B]](existingInto)
      }
      // If no predefined instance found, fall through to error or other handling
    }

    val aIsProduct    = isProductType(aTpe)
    val bIsProduct    = isProductType(bTpe)
    val aIsTuple      = isTupleType(aTpe)
    val bIsTuple      = isTupleType(bTpe)
    val aIsCoproduct  = isSealedTrait(aTpe)
    val bIsCoproduct  = isSealedTrait(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)
    val aIsCaseObject = isCaseObjectType(aTpe)
    val bIsCaseObject = isCaseObjectType(bTpe)

    // Handle case object conversions first (before product matching)
    (
      aIsCaseObject,
      bIsCaseObject,
      aIsProduct,
      bIsProduct,
      aIsTuple,
      bIsTuple,
      aIsCoproduct,
      bIsCoproduct,
      aIsStructural,
      bIsStructural
    ) match {
      case (true, true, _, _, _, _, _, _, _, _) =>
        // Case object to case object
        deriveCaseObjectToCaseObject()
      case (true, _, _, true, _, _, _, _, _, _) =>
        // Case object to case class
        deriveCaseObjectToProduct()
      case (_, true, true, _, _, _, _, _, _, _) =>
        // Case class to case object
        deriveProductToCaseObject()
      case (_, _, true, true, _, _, _, _, _, _) =>
        // Case class to case class
        deriveProductToProduct()
      case (_, _, true, _, _, true, _, _, _, _) =>
        // Case class to tuple
        deriveCaseClassToTuple()
      case (_, _, _, true, true, _, _, _, _, _) =>
        // Tuple to case class
        deriveTupleToCaseClass()
      case (_, _, _, _, true, true, _, _, _, _) =>
        // Tuple to tuple
        deriveTupleToTuple()
      case (_, _, _, _, _, _, true, true, _, _) =>
        // Coproduct to coproduct (sealed trait to sealed trait)
        deriveCoproductToCoproduct()
      case (_, _, _, true, _, _, _, _, true, _) =>
        // Structural type -> Product (JVM only - requires reflection)
        if (!Platform.supportsReflection) {
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Structural type conversions are not supported on ${Platform.name}.
               |
               |Structural types require reflection APIs (getClass.getMethod) which are only available on JVM.
               |
               |Consider:
               |  - Using a case class instead of a structural type
               |  - Using a tuple instead of a structural type
               |  - Only using structural type conversions in JVM-only code""".stripMargin
          )
        }
        deriveStructuralToProduct()
      case (_, _, true, _, _, _, _, _, _, true) =>
        // Product -> Structural (JVM only - requires reflection)
        if (!Platform.supportsReflection) {
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Structural types are not supported on ${Platform.name}.
               |
               |Structural types require reflection APIs which are only available on JVM.
               |
               |Consider:
               |  - Using a case class instead of a structural type
               |  - Only using structural type conversions in JVM-only code""".stripMargin
          )
        }
        deriveProductToStructural()
      case _ =>
        // Check for primitive <-> single-field product conversions
        val isPrimA         = isPrimitiveOrBoxed(aTpe)
        val isPrimB         = isPrimitiveOrBoxed(bTpe)
        val bIsSingleField  = bIsProduct && isSingleFieldProduct(bTpe)
        val aIsSingleField  = aIsProduct && isSingleFieldProduct(aTpe)
        val isNewtypeConv   = requiresNewtypeConversion(aTpe, bTpe)
        val isNewtypeUnwrap = requiresNewtypeUnwrapping(aTpe, bTpe)

        if (isNewtypeConv) {
          deriveNewtypeConversion()
        } else if (isNewtypeUnwrap) {
          deriveNewtypeUnwrapping()
        } else if (isPrimA && bIsSingleField) {
          // Primitive -> SingleFieldProduct (wrapping)
          derivePrimitiveToSingleFieldProduct()
        } else if (aIsSingleField && isPrimB) {
          // SingleFieldProduct -> Primitive (unwrapping)
          deriveSingleFieldProductToPrimitive()
        } else {
          val sourceKind =
            if (aIsProduct) "product" else if (aIsTuple) "tuple" else if (aIsCoproduct) "coproduct" else "other"
          val targetKind =
            if (bIsProduct) "product" else if (bIsTuple) "tuple" else if (bIsCoproduct) "coproduct" else "other"
          fail(
            s"""Cannot derive Into[$aTpe, $bTpe]: Unsupported type combination
               |
               |Source type: $aTpe ($sourceKind)
               |Target type: $bTpe ($targetKind)
               |
               |Into derivation supports:
               |  - Product → Product (case class to case class)
               |  - Product ↔ Tuple (case class to/from tuple)
               |  - Tuple → Tuple
               |  - Coproduct → Coproduct (sealed trait to sealed trait)
               |  - Structural ↔ Product
               |  - Primitive → Primitive (with coercion)
               |  - Primitive ↔ Single-field Product (wrapping/unwrapping)
               |  - Primitive ↔ ZIO Prelude Newtype (with validation)
               |
               |Consider:
               |  - Restructuring your types to fit a supported pattern
               |  - Providing an explicit Into instance""".stripMargin
          )
        }
    }
  }
}
