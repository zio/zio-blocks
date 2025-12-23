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
    def isCaseObjectType(tpe: Type): Boolean = {
      tpe match {
        case SingleType(_, sym) =>
          sym.isModule && sym.asModule.moduleClass.asClass.isCaseClass
        case _ =>
          // Also check for constant types or other singleton representations
          val sym = tpe.typeSymbol
          sym.isModuleClass && sym.asClass.isCaseClass
      }
    }

    // Get the module symbol for a case object type
    def getCaseObjectModule(tpe: Type): Symbol = {
      tpe match {
        case SingleType(_, sym) => sym
        case _ => tpe.typeSymbol.asClass.module
      }
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
          val paramIdx = idx + 1 // Scala 2 uses 1-based index for default methods
          val defaultValue: Option[Tree] = companionSymbol.flatMap { companion =>
            val defaultMethodName = TermName(s"$$lessinit$$greater$$default$$$paramIdx")
            val defaultMethod = companion.info.member(defaultMethodName)
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
      val sourceField: FieldInfo,      // null means use default (None for Option types, or actual default value)
      val targetField: FieldInfo,
      val useDefaultValue: Boolean = false  // true if we should use the target field's default value
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

      // Priority 2: Name match with coercion - same name + coercible type, implicit Into available, or newtype conversion/unwrapping
      val nameWithCoercion = sourceInfo.fields.find { sourceField =>
        !usedSourceFields.contains(sourceField.index) &&
          sourceField.name == targetField.name &&
          (findImplicitInto(sourceField.tpe, targetField.tpe).isDefined || 
           requiresNewtypeConversion(sourceField.tpe, targetField.tpe) ||
           requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe))
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

        // Also check for unique coercible type match (including implicit Into and newtype conversion/unwrapping)
        val uniqueCoercibleMatch = sourceInfo.fields.find { sourceField =>
          if (usedSourceFields.contains(sourceField.index)) false
          else {
            val isSourceTypeUnique = sourceTypeFreq.getOrElse(sourceField.tpe.dealias.toString, 0) == 1
            isSourceTypeUnique && (findImplicitInto(sourceField.tpe, targetField.tpe).isDefined || 
              requiresNewtypeConversion(sourceField.tpe, targetField.tpe) ||
              requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe))
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
            // Also check coercible for positional (including implicit Into and newtype conversion/unwrapping)
            if (findImplicitInto(positionalField.tpe, targetField.tpe).isDefined || 
                requiresNewtypeConversion(positionalField.tpe, targetField.tpe) ||
                requiresNewtypeUnwrapping(positionalField.tpe, targetField.tpe)) {
              return Some(positionalField)
            }
          }
        }
      }

      // Fallback: No match found
      None
    }

    // === ZIO Prelude Newtype Support ===

    /**
     * Checks if a type is a ZIO Prelude newtype (Newtype[A] or Subtype[A])
     *
     * ZIO Prelude newtypes follow the pattern:
     *   object Age extends Subtype[Int]
     *   type Age = Age.Type
     *
     * We check if the type name is "Type" and if its owner extends Newtype or Subtype
     */
    def isZIONewtype(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      val typeSymbol = dealiased.typeSymbol

      // Check if the type symbol name is "Type" (the inner type alias pattern)
      val typeName = typeSymbol.name.decodedName.toString

      if (typeName == "Type") {
        // Get the owner (which should be the companion object/module)
        val owner = typeSymbol.owner
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

    /**
     * Checks if conversion from source to target requires ZIO Prelude newtype conversion
     */
    def requiresNewtypeConversion(sourceTpe: Type, targetTpe: Type): Boolean = {
      isZIONewtype(targetTpe) && !(sourceTpe =:= targetTpe)
    }

    /**
     * Checks if conversion from source to target requires unwrapping a ZIO Prelude newtype
     * (i.e., source is a newtype and target is its underlying type)
     */
    def requiresNewtypeUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean = {
      if (isZIONewtype(sourceTpe)) {
        getNewtypeUnderlying(sourceTpe) match {
          case Some(underlying) => targetTpe =:= underlying
          case None => false
        }
      } else {
        false
      }
    }

    /**
     * Gets the underlying type of a ZIO Prelude newtype
     */
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
                  case _ => None
                }
              }
            case _ => None
          }
        case _ => None
      }
    }

    /**
     * Generates code to convert a value to a ZIO Prelude newtype using runtime reflection
     * Returns code that evaluates to Either[SchemaError, NeType]
     */
    def convertToNewtypeEither(sourceExpr: Tree, sourceTpe: Type, targetTpe: Type, fieldName: String): Tree = {
      // Dealias the type to get the actual type (e.g., Age.Type)
      val dealiased = targetTpe.dealias

      // For ZIO Prelude newtypes, the type is like "SomeObject.Type"
      // We need to extract "SomeObject" from the type's prefix
      val companionPath = dealiased match {
        case TypeRef(pre, sym, _) if sym.name.toString == "Type" =>
          // The prefix contains the companion object
          // For example: TypeRef(SingleType(pre, Domain.Age), Type, Nil)
          // We want "Domain.Age"
          pre match {
            case SingleType(outerPre, companionSym) =>
              // Found it! companionSym is the Age object
              companionSym.fullName
            case other =>
              // Fallback to string manipulation
              val str = dealiased.toString
              if (str.endsWith(".Type")) str.stripSuffix(".Type") else str
          }
        case _ =>
          // Fallback to string manipulation
          val str = dealiased.toString
          if (str.endsWith(".Type")) str.stripSuffix(".Type") else str
      }


      val companionPathLiteral = Literal(Constant(companionPath))
      val fieldNameLiteral = Literal(Constant(fieldName))

      // Create the target type tree for type ascription
      val targetTypeTree = TypeTree(targetTpe)

      // Generate unique variable names to avoid conflicts when multiple newtype conversions
      // are generated in the same scope (e.g., case class with multiple newtype fields)
      val sourceValueName = TermName(c.freshName("sourceValue"))
      val companionClassNameName = TermName(c.freshName("companionClassName"))
      val basePathName = TermName(c.freshName("basePath"))
      val partsName = TermName(c.freshName("parts"))
      val packageEndName = TermName(c.freshName("packageEnd"))
      val packagePartName = TermName(c.freshName("packagePart"))
      val classPartName = TermName(c.freshName("classPart"))
      val pkgName = TermName(c.freshName("pkg"))
      val clsName = TermName(c.freshName("cls"))
      val companionObjClassName = TermName(c.freshName("companionObjClass"))
      val companionModuleName = TermName(c.freshName("companionModule"))
      val companionClassName = TermName(c.freshName("companionClass"))
      val assertionMethodName = TermName(c.freshName("assertionMethod"))
      val assertionName = TermName(c.freshName("assertion"))
      val methodName = TermName(c.freshName("method"))
      val resultName = TermName(c.freshName("result"))
      val eitherName = TermName(c.freshName("either"))

      q"""
        {
          val $sourceValueName = $sourceExpr
          try {
            // Convert the companion path to JVM class name
            // Input:  "zio.blocks.schema.IntoZIOPreludeNewtypeSpec.Domain.Email"
            // Output: "zio.blocks.schema.IntoZIOPreludeNewtypeSpec<dollar>Domain<dollar>Email<dollar>"
            val $companionClassNameName = {
              val $basePathName = $companionPathLiteral
              val $partsName = $basePathName.split('.').toList

              // Find where the package ends (first capital letter indicates class/object)
              val $packageEndName = $partsName.indexWhere(part => part.nonEmpty && part.head.isUpper)

              if ($packageEndName >= 0) {
                val $packagePartName = $partsName.take($packageEndName)
                val $classPartName = $partsName.drop($packageEndName)

                // Join package with dots
                val $pkgName = if ($packagePartName.isEmpty) "" else $packagePartName.mkString(".") + "."

                // For class/object parts: use dollar as separator and end with dollar
                // mkString uses the argument as the separator between elements
                val $clsName = if ($classPartName.isEmpty) {
                  ""
                } else {
                  // Use a char that will be replaced to avoid quasiquote issues
                  val dollarStr = java.lang.String.valueOf(36.toChar)
                  $classPartName.mkString(dollarStr) + dollarStr
                }

                $pkgName + $clsName
              } else {
                // All lowercase, likely all package - should not happen for newtypes
                val dollarStr = java.lang.String.valueOf(36.toChar)
                $basePathName + dollarStr
              }
            }

            val $companionObjClassName = _root_.java.lang.Class.forName($companionClassNameName)
            // Try both getField (public) and getDeclaredField (any visibility)
            val $companionModuleName = try {
              $companionObjClassName.getField("MODULE$$").get(null)
            } catch {
              case _: _root_.java.lang.NoSuchFieldException =>
                try {
                  val field = $companionObjClassName.getDeclaredField("MODULE$$")
                  field.setAccessible(true)
                  field.get(null)
                } catch {
                  case _: _root_.java.lang.NoSuchFieldException =>
                    // For some Scala object structures, try getting instance differently
                    // List all fields for debugging
                    val allFields = $companionObjClassName.getFields.map(_.getName).mkString(", ")
                    val declFields = $companionObjClassName.getDeclaredFields.map(_.getName).mkString(", ")
                    throw new _root_.java.lang.RuntimeException(
                      "Cannot access MODULE$$ on " + $companionClassNameName +
                      ". Public fields: [" + allFields + "]. Declared fields: [" + declFields + "]")
                }
            }

            // For ZIO Prelude newtypes:
            // - 'make' is a macro and does NOT exist at runtime (in Scala 2)
            // - We need to get the 'assertion' method which returns QuotedAssertion[A]
            // - Call assertion.run(value) which returns Either[AssertionError, Unit]
            // - If Right(()), cast the value directly (newtypes are just type aliases at runtime)
            // - If Left(error), return the error
            val $companionClassName = $companionModuleName.getClass

            // Get the assertion method - it's defined on Newtype/Subtype
            val $assertionMethodName = try {
              $companionClassName.getMethod("assertion")
            } catch {
              case _: _root_.java.lang.NoSuchMethodException =>
                // Try getDeclaredMethod as backup
                val m = $companionClassName.getDeclaredMethod("assertion")
                m.setAccessible(true)
                m
            }

            val $assertionName = $assertionMethodName.invoke($companionModuleName)

            // QuotedAssertion has a run method: def run(value: A): Either[AssertionError, Unit]
            // The method takes Object due to erasure. We need to find it by searching methods.
            val $methodName = {
              val assertionClass = $assertionName.getClass
              // Try to find 'run' method that takes one Object parameter
              val methods = assertionClass.getMethods.filter(m => m.getName == "run" && m.getParameterCount == 1)
              if (methods.isEmpty) {
                throw new _root_.java.lang.NoSuchMethodException(
                  "No 'run' method found on QuotedAssertion. Available methods: " +
                  assertionClass.getMethods.map(m => m.getName + "(" + m.getParameterTypes.map(_.getName).mkString(", ") + ")").mkString(", ")
                )
              }
              methods.head
            }

            val $resultName = $methodName.invoke($assertionName, $sourceValueName.asInstanceOf[_root_.java.lang.Object])
              .asInstanceOf[_root_.scala.Either[_root_.scala.Any, _root_.scala.Unit]]

            // Map the result: Right(()) means valid, so cast value; Left(err) means invalid
            val $eitherName = $resultName match {
              case _root_.scala.Right(_) =>
                // Validation passed - newtypes are just type aliases, so cast directly
                _root_.scala.Right($sourceValueName.asInstanceOf[$targetTypeTree])
              case _root_.scala.Left(err) =>
                _root_.scala.Left(
                  _root_.zio.blocks.schema.SchemaError.conversionFailed(
                    _root_.scala.Nil,
                    "Validation failed for field '" + $fieldNameLiteral + "': " + err.toString
                  )
                )
            }

            $eitherName.asInstanceOf[_root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]]
          } catch {
            case e: _root_.java.lang.ClassNotFoundException =>
              _root_.scala.Left(_root_.zio.blocks.schema.SchemaError.conversionFailed(_root_.scala.Nil, "Companion object not found for newtype: " + $companionPathLiteral + ": " + e.getMessage))
            case e: _root_.java.lang.NoSuchMethodException =>
              _root_.scala.Left(_root_.zio.blocks.schema.SchemaError.conversionFailed(_root_.scala.Nil, "Method not found for newtype: " + $companionPathLiteral + ": " + e.getMessage))
            case e: _root_.java.lang.Exception =>
              _root_.scala.Left(_root_.zio.blocks.schema.SchemaError.conversionFailed(_root_.scala.Nil, "Error converting to newtype: " + e.getClass.getName + ": " + e.getMessage))
          }
        }: _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $targetTypeTree]
      """
    }

    // Find or use cached Into instance
    // Also looks for As instances and extracts Into from them
    def findImplicitInto(sourceTpe: Type, targetTpe: Type): Option[Tree] = {
      // Check cache first
      intoRefs.get((sourceTpe, targetTpe)) match {
        case some @ Some(_) => some
        case None =>
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
          // No source field - use default value or None for Option types
          if (mapping.useDefaultValue && targetField.defaultValue.isDefined) {
            // Use the actual default value
            val defaultValue = targetField.defaultValue.get
            q"_root_.scala.Right($defaultValue)"
          } else {
            // Use None for Option types
            q"_root_.scala.Right(_root_.scala.None)"
          }
        } else {
          val sourceField = mapping.sourceField
          val getter = sourceField.getter
          val sourceTpe = sourceField.tpe
          val targetTpe = targetField.tpe

          // If types differ, try to find an implicit Into instance or handle newtype conversion
          if (!(sourceTpe =:= targetTpe)) {
            // Check if it's a newtype conversion (underlying -> newtype)
            val isNewtypeConversion = requiresNewtypeConversion(sourceTpe, targetTpe)
            // Check if it's a newtype unwrapping (newtype -> underlying)
            val isNewtypeUnwrapping = requiresNewtypeUnwrapping(sourceTpe, targetTpe)
            
            if (isNewtypeConversion) {
              // Generate runtime reflection code to call the newtype's make method
              convertToNewtypeEither(q"a.$getter", sourceTpe, targetTpe, sourceField.name)
            } else if (isNewtypeUnwrapping) {
              // Unwrap newtype to underlying type - newtypes are type aliases, so just cast
              q"_root_.scala.Right(a.$getter.asInstanceOf[$targetTpe])"
            } else {
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
          new _root_.zio.blocks.schema.Into[$aTpe, $bTpe] {
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
          // No source field - use default value or None for Option types
          if (mapping.useDefaultValue && targetField.defaultValue.isDefined) {
            // Use the actual default value
            val defaultValue = targetField.defaultValue.get
            q"_root_.scala.Right($defaultValue)"
          } else {
            // Use None for Option types
            q"_root_.scala.Right(_root_.scala.None)"
          }
        } else {
          val sourceField = mapping.sourceField
          val getter = sourceField.getter
          val sourceTpe = sourceField.tpe
          val targetTpe = targetField.tpe

          // If types differ, try to find an implicit Into instance or handle newtype conversion
          if (!(sourceTpe =:= targetTpe)) {
            // Check if it's a newtype conversion (underlying -> newtype)
            val isNewtypeConversion = requiresNewtypeConversion(sourceTpe, targetTpe)
            // Check if it's a newtype unwrapping (newtype -> underlying)
            val isNewtypeUnwrapping = requiresNewtypeUnwrapping(sourceTpe, targetTpe)
            
            if (isNewtypeConversion) {
              // Generate runtime reflection code to call the newtype's make method
              convertToNewtypeEither(q"$bindingName.$getter", sourceTpe, targetTpe, sourceField.name)
            } else if (isNewtypeUnwrapping) {
              // Unwrap newtype to underlying type - newtypes are type aliases, so just cast
              q"_root_.scala.Right($bindingName.$getter.asInstanceOf[$targetTpe])"
            } else {
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
      // Extract only the members declared in the refinement, not inherited ones
      def collectRefinementMembers(t: Type): List[(String, Type)] = t.dealias match {
        case RefinedType(parents, decls) =>
          // Get members declared in this refinement
          val declaredMembers = decls.collect {
            case m: MethodSymbol if !m.isConstructor && m.paramLists.isEmpty =>
              val name = NameTransformer.decode(m.name.toString)
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

    def deriveStructuralToProduct(): c.Expr[Into[A, B]] = {
      val structuralMembers = getStructuralMembers(aTpe)
      val targetInfo = new ProductInfo(bTpe)

      // For each target field, find matching structural member (by name or unique type)
      // or use default value/None for optional fields
      val fieldMappings: List[(Option[(String, Type)], FieldInfo)] = targetInfo.fields.map { targetField =>
        val matchingMember = structuralMembers.find { case (name, memberTpe) =>
          name == targetField.name && (memberTpe =:= targetField.tpe || findImplicitInto(memberTpe, targetField.tpe).isDefined)
        }.orElse {
          val uniqueTypeMatches = structuralMembers.filter { case (_, memberTpe) =>
            memberTpe =:= targetField.tpe || findImplicitInto(memberTpe, targetField.tpe).isDefined
          }
          if (uniqueTypeMatches.size == 1) Some(uniqueTypeMatches.head) else None
        }

        matchingMember match {
          case Some((memberName, memberTpe)) => (Some((memberName, memberTpe)), targetField)
          case None =>
            // No matching structural member - check for default value or Option type
            if (targetField.hasDefault && targetField.defaultValue.isDefined) {
              (None, targetField) // Will use default value
            } else if (isOptionType(targetField.tpe)) {
              (None, targetField) // Will use None
            } else {
              fail(
                s"Cannot derive Into[$aTpe, $bTpe]: no matching structural member found for field '${targetField.name}: ${targetField.tpe}'"
              )
            }
        }
      }

      // Generate code that uses reflection to access structural type members
      val args = fieldMappings.map { case (memberOpt, targetField) =>
        memberOpt match {
          case Some((memberName, memberTpe)) =>
            val methodName = TermName(memberName)
            if (memberTpe =:= targetField.tpe) {
              q"a.$methodName"
            } else {
              // Need conversion via implicit Into
              findImplicitInto(memberTpe, targetField.tpe) match {
                case Some(intoInstance) =>
                  q"""
                    $intoInstance.into(a.$methodName) match {
                      case _root_.scala.Right(v) => v
                      case _root_.scala.Left(_) => throw new _root_.java.lang.RuntimeException("Conversion failed")
                    }
                  """
                case None =>
                  q"a.$methodName"
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
              import scala.language.reflectiveCalls
              _root_.scala.Right(new $bTpe(..$args))
            }
          }
        """
      )
    }

    // === Derivation: Product to Structural Type ===

    def deriveProductToStructural(): c.Expr[Into[A, B]] = {
      // Product -> Structural type conversion is simple: since the product type has all the
      // required fields/methods that the structural type demands (otherwise we couldn't derive
      // this conversion), we can simply cast the product instance to the structural type.
      //
      // For example: case class Point(x: Int, y: Int) can be cast to { def x: Int; def y: Int }
      // because Point already has methods x and y that return Int.

      val sourceInfo = new ProductInfo(aTpe)
      val structuralMembers = getStructuralMembers(bTpe)

      // Validate that all structural type members exist in the source product
      structuralMembers.foreach { case (memberName, memberTpe) =>
        val matchingField = sourceInfo.fields.find { field =>
          field.name == memberName && (field.tpe =:= memberTpe || field.tpe <:< memberTpe)
        }
        if (matchingField.isEmpty) {
          fail(
            s"Cannot derive Into[$aTpe, $bTpe]: " +
              s"source type is missing member '$memberName: $memberTpe' required by structural type"
          )
        }
      }

      // The product instance already satisfies the structural type contract, just cast it
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
          fail(
            s"Cannot derive Into[$aTpe, $bTpe]: " +
              s"case object cannot be converted to case class with non-optional, non-default field '${field.name}: ${field.tpe}'"
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
        fail(s"Cannot derive Into[$aTpe, $bTpe]: no predefined conversion between these primitive types")
      }
    }

    // Check for container types (Option, Either, List, Set, Map, etc.)
    // These have predefined implicit instances that should be discovered
    def isContainerType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      val sym = dealiased.typeSymbol
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

    val aIsProduct = isProductType(aTpe)
    val bIsProduct = isProductType(bTpe)
    val aIsTuple = isTupleType(aTpe)
    val bIsTuple = isTupleType(bTpe)
    val aIsCoproduct = isSealedTrait(aTpe)
    val bIsCoproduct = isSealedTrait(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)
    val aIsCaseObject = isCaseObjectType(aTpe)
    val bIsCaseObject = isCaseObjectType(bTpe)

    // Handle case object conversions first (before product matching)
    (aIsCaseObject, bIsCaseObject, aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct, aIsStructural, bIsStructural) match {
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
        // Structural type -> Product (structural source to case class target)
        deriveStructuralToProduct()
      case (_, _, true, _, _, _, _, _, _, true) =>
        // Product -> Structural (case class source to structural target)
        deriveProductToStructural()
      case _ =>
        fail(s"Cannot derive Into[$aTpe, $bTpe]: unsupported type combination")
    }
  }
}
