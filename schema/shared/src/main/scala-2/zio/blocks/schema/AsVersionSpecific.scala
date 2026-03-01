package zio.blocks.schema

import zio.blocks.schema.CommonMacroOps
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait AsVersionSpecific {

  /** Derives a bidirectional conversion As[A, B]. */
  def derived[A, B]: As[A, B] = macro AsVersionSpecificImpl.derived[A, B]
}

private object AsVersionSpecificImpl {
  def derived[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[As[A, B]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias
    val bTpe = weakTypeOf[B].dealias

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
        // Distinguish "not found" from "ambiguous" by re-running non-silently
        try {
          c.inferImplicitValue(schemaType, silent = false)
          // If we reach here, it's genuinely not found — fall back to derived
          q"_root_.zio.blocks.schema.Schema.derived[$tpe]"
        } catch {
          case e: Exception =>
            val msg = e.getMessage
            if (msg != null && (msg.contains("ambiguous") || msg.contains("diverging"))) {
              c.abort(
                c.enclosingPosition,
                s"Ambiguous implicit Schema[${tpe}] instances found. " +
                  s"Please provide an explicit Schema instance to disambiguate."
              )
            } else {
              // Genuinely not found — fall back to derived
              q"_root_.zio.blocks.schema.Schema.derived[$tpe]"
            }
        }
      }
    }

    def deriveToDynamicValue(): c.Expr[As[A, B]] = {
      val schema = findImplicitOrDeriveSchema(aTpe)
      c.Expr[As[A, B]](
        q"""
          new _root_.zio.blocks.schema.As[$aTpe, $bTpe] {
            private val _schema = $schema
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(_schema.toDynamicValue(a).asInstanceOf[$bTpe])
            }
            def from(dv: $bTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $aTpe] = {
              _schema.fromDynamicValue(dv.asInstanceOf[_root_.zio.blocks.schema.DynamicValue])
            }
          }
        """
      )
    }

    def deriveFromDynamicValue(): c.Expr[As[A, B]] = {
      val schema = findImplicitOrDeriveSchema(bTpe)
      c.Expr[As[A, B]](
        q"""
          new _root_.zio.blocks.schema.As[$aTpe, $bTpe] {
            private val _schema = $schema
            def into(dv: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _schema.fromDynamicValue(dv.asInstanceOf[_root_.zio.blocks.schema.DynamicValue])
            }
            def from(b: $bTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $aTpe] = {
              _root_.scala.Right(_schema.toDynamicValue(b).asInstanceOf[$aTpe])
            }
          }
        """
      )
    }

    if (bTpe =:= dynamicValueType) {
      return deriveToDynamicValue()
    }
    if (aTpe =:= dynamicValueType) {
      return deriveFromDynamicValue()
    }

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def isProductType(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    def isTupleType(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName
      name.startsWith("scala.Tuple") && name != "scala.Tuple"
    }

    def isSealedTrait(tpe: Type): Boolean = {
      val sym = tpe.typeSymbol
      sym.isClass && sym.asClass.isSealed
    }

    // === Field Info ===

    class FieldInfo(
      val name: String,
      val tpe: Type,
      val index: Int,
      val getter: MethodSymbol,
      val hasDefault: Boolean
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

        val constructor = primaryConstructor(tpe)
        var idx         = 0

        constructor.paramLists.flatten.map { param =>
          val symbol = param.asTerm
          val name   = NameTransformer.decode(symbol.name.toString)
          var fTpe   = symbol.typeSignature.dealias
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val getter = getters.getOrElse(
            name,
            fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
          )
          // Check if field has a default value
          val hasDefault = symbol.isParamWithDefault
          val fieldInfo  = new FieldInfo(name, fTpe, idx, getter, hasDefault)
          idx += 1
          fieldInfo
        }
      }
    }

    // === Compatibility Checks ===

    def checkNoDefaultValues(info: ProductInfo, otherInfo: ProductInfo, direction: String): Unit = {
      val otherFieldNames    = otherInfo.fields.map(_.name).toSet
      val fieldsWithDefaults = info.fields.filter(f => f.hasDefault && !otherFieldNames.contains(f.name))
      if (fieldsWithDefaults.nonEmpty) {
        val defaultFieldsStr = fieldsWithDefaults.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
        fail(
          s"""Cannot derive As[$aTpe, $bTpe]: Default values break round-trip guarantee
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

    def isOptionType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == definitions.OptionClass ||
      dealiased.typeConstructor.typeSymbol == definitions.OptionClass
    }

    def isListType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == symbolOf[List[_]] ||
      dealiased.typeConstructor.typeSymbol == symbolOf[List[_]]
    }

    def isVectorType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == symbolOf[Vector[_]] ||
      dealiased.typeConstructor.typeSymbol == symbolOf[Vector[_]]
    }

    def isSetType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == symbolOf[Set[_]] ||
      dealiased.typeConstructor.typeSymbol == symbolOf[Set[_]]
    }

    def isSeqType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == symbolOf[Seq[_]] ||
      dealiased.typeConstructor.typeSymbol == symbolOf[Seq[_]]
    }

    def getContainerElementType(tpe: Type): Option[Type] = {
      val args = typeArgs(tpe.dealias)
      if (args.nonEmpty) Some(args.head) else None
    }

    def areBidirectionallyConvertibleContainers(sourceTpe: Type, targetTpe: Type): Boolean = {
      // Check if both are the same kind of container or both are collections
      val bothOptions     = isOptionType(sourceTpe) && isOptionType(targetTpe)
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

    def checkFieldMappingConsistency(sourceInfo: ProductInfo, targetInfo: ProductInfo): Unit = {
      val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
      val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

      sourceFieldsByName.foreach { case (name, sourceField) =>
        targetFieldsByName.get(name) match {
          case Some(targetField) =>
            // Both have this field - types must be convertible in both directions
            if (!(sourceField.tpe =:= targetField.tpe)) {
              // Check if As[source, target] is available (bidirectional)
              val hasAsInstance = isImplicitAsAvailable(sourceField.tpe, targetField.tpe)

              if (hasAsInstance) {
                // As instance provides both directions - OK
              } else {
                // Check for container types (Option, List, etc.) with different element types
                val containerConvertible = areBidirectionallyConvertibleContainers(sourceField.tpe, targetField.tpe)

                if (containerConvertible) {
                  // Container types with bidirectionally convertible elements - OK
                } else {
                  // Check for newtype conversions (bidirectional)
                  val newtypeConvert     = requiresNewtypeConversion(sourceField.tpe, targetField.tpe)
                  val newtypeUnwrap      = requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe)
                  val newtypeConvertBack = requiresNewtypeConversion(targetField.tpe, sourceField.tpe)
                  val newtypeUnwrapBack  = requiresNewtypeUnwrapping(targetField.tpe, sourceField.tpe)

                  val canConvertViaNewtype =
                    (newtypeConvert && newtypeUnwrapBack) || (newtypeUnwrap && newtypeConvertBack)

                  // Check for single-field product (AnyVal wrapper) conversions (bidirectional)
                  val singleFieldConvert     = requiresSingleFieldProductConversion(sourceField.tpe, targetField.tpe)
                  val singleFieldUnwrap      = requiresSingleFieldProductUnwrapping(sourceField.tpe, targetField.tpe)
                  val singleFieldConvertBack = requiresSingleFieldProductConversion(targetField.tpe, sourceField.tpe)
                  val singleFieldUnwrapBack  = requiresSingleFieldProductUnwrapping(targetField.tpe, sourceField.tpe)

                  val canConvertViaSingleField =
                    (singleFieldConvert && singleFieldUnwrapBack) || (singleFieldUnwrap && singleFieldConvertBack)

                  if (canConvertViaNewtype || canConvertViaSingleField) {
                    // Newtype or single-field product conversion is bidirectional - OK
                  } else {
                    // Fall back to checking Into in both directions
                    val canConvert = isNumericCoercible(sourceField.tpe, targetField.tpe) ||
                      newtypeConvert || newtypeUnwrap ||
                      singleFieldConvert || singleFieldUnwrap ||
                      isImplicitIntoAvailable(sourceField.tpe, targetField.tpe)
                    val canConvertBack = isNumericCoercible(targetField.tpe, sourceField.tpe) ||
                      newtypeConvertBack || newtypeUnwrapBack ||
                      singleFieldConvertBack || singleFieldUnwrapBack ||
                      isImplicitIntoAvailable(targetField.tpe, sourceField.tpe)

                    if (!canConvert || !canConvertBack) {
                      val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
                      val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
                      val direction       = if (!canConvert) "A → B" else "B → A"
                      fail(
                        s"""Cannot derive As[$aTpe, $bTpe]: Field not bidirectionally convertible
                           |
                           |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                           |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                           |
                           |Field '$name' cannot be converted in both directions:
                           |  Source: ${sourceField.tpe}
                           |  Target: ${targetField.tpe}
                           |  Missing: $direction conversion
                           |
                           |As[A, B] requires:
                           |  - Into[A, B] for A → B conversion
                           |  - Into[B, A] for B → A conversion (round-trip)
                           |
                           |Consider:
                           |  - Using matching types on both sides
                           |  - Providing implicit As[${sourceField.tpe}, ${targetField.tpe}]
                           |  - Using Into[A, B] instead if one-way conversion is sufficient""".stripMargin
                      )
                    }
                  }
                }
              }
            }
          case None =>
          // Source has field that target doesn't have
          // It's OK if source has extra fields - they just get dropped when going to target
          // and become None when coming back (if target has them as Option)
        }
      }

      // Check: fields that exist in target but NOT in source (the reverse direction)
      // For As, only Optional fields are allowed (defaults break round-trip guarantee)
      targetFieldsByName.foreach { case (name, targetField) =>
        if (!sourceFieldsByName.contains(name)) {
          // Target has a field that source doesn't have
          // When going B → A, this field will be missing
          // For As, only Option fields are allowed
          val isOptional = isOptionType(targetField.tpe)

          if (!isOptional) {
            val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            fail(
              s"""Cannot derive As[$aTpe, $bTpe]: Missing required field breaks round-trip
                 |
                 |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                 |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                 |
                 |Field '$name: ${targetField.tpe}' exists in ${bTpe.typeSymbol.name} but not in ${aTpe.typeSymbol.name}
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
            val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            fail(
              s"""Cannot derive As[$aTpe, $bTpe]: Missing required field breaks round-trip
                 |
                 |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                 |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                 |
                 |Field '$name: ${sourceField.tpe}' exists in ${aTpe.typeSymbol.name} but not in ${bTpe.typeSymbol.name}
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

    // === ZIO Prelude Newtype Detection ===

    def isZIONewtype(tpe: Type): Boolean = {
      val dealiased = tpe.dealias

      // For ZIO Prelude newtypes, the type is TypeRef with prefix pointing to the companion
      // e.g., Types.RtAge.Type has prefix Types.RtAge (the companion object)
      dealiased match {
        case TypeRef(pre, sym, _) if sym.name.decodedName.toString == "Type" =>
          // The prefix should be a SingleType or ThisType pointing to the companion object
          pre match {
            case SingleType(_, companionSym) =>
              if (companionSym.isModule) {
                val companionType = companionSym.asModule.moduleClass.asType.toType
                companionType.baseClasses.exists { bc =>
                  val name = bc.fullName
                  name == "zio.prelude.Subtype" || name == "zio.prelude.Newtype"
                }
              } else {
                false
              }
            case _ =>
              false
          }
        case _ =>
          false
      }
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

    def requiresNewtypeConversion(sourceTpe: Type, targetTpe: Type): Boolean =
      // Check if target is a ZIO Prelude newtype and source is its underlying type
      if (isZIONewtype(targetTpe)) {
        getNewtypeUnderlying(targetTpe) match {
          case Some(underlying) => sourceTpe =:= underlying
          case None             => false
        }
      } else {
        false
      }

    def requiresNewtypeUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean =
      // Check if source is a ZIO Prelude newtype and target is its underlying type
      if (isZIONewtype(sourceTpe)) {
        getNewtypeUnderlying(sourceTpe) match {
          case Some(underlying) => targetTpe =:= underlying
          case None             => false
        }
      } else {
        false
      }

    // === Single-field Product (AnyVal wrapper) Detection ===

    def isSingleFieldProduct(tpe: Type): Boolean =
      isProductType(tpe) && {
        val info = new ProductInfo(tpe)
        info.fields.size == 1
      }

    def getSingleFieldType(tpe: Type): Option[Type] =
      if (isSingleFieldProduct(tpe)) {
        val info = new ProductInfo(tpe)
        Some(info.fields.head.tpe)
      } else {
        None
      }

    def requiresSingleFieldProductConversion(sourceTpe: Type, targetTpe: Type): Boolean =
      // Check if target is a single-field product and source is its underlying type
      if (isSingleFieldProduct(targetTpe)) {
        getSingleFieldType(targetTpe) match {
          case Some(fieldType) => sourceTpe =:= fieldType
          case None            => false
        }
      } else {
        false
      }

    def requiresSingleFieldProductUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean =
      // Check if source is a single-field product and target is its underlying type
      if (isSingleFieldProduct(sourceTpe)) {
        getSingleFieldType(sourceTpe) match {
          case Some(fieldType) => targetTpe =:= fieldType
          case None            => false
        }
      } else {
        false
      }

    def isNumericCoercible(from: Type, to: Type): Boolean = {
      val numericTypes = List(
        typeOf[Byte],
        typeOf[Short],
        typeOf[Int],
        typeOf[Long],
        typeOf[Float],
        typeOf[Double]
      )

      val fromIdx = numericTypes.indexWhere(t => from =:= t)
      val toIdx   = numericTypes.indexWhere(t => to =:= t)

      // Any numeric type can convert to any other with runtime validation
      fromIdx >= 0 && toIdx >= 0
    }

    def isImplicitIntoAvailable(from: Type, to: Type): Boolean = {
      val intoType = c.universe.appliedType(
        c.universe.typeOf[Into[Any, Any]].typeConstructor,
        List(from, to)
      )
      val intoInstance = c.inferImplicitValue(intoType, silent = true)
      intoInstance != EmptyTree
    }

    def isImplicitAsAvailable(from: Type, to: Type): Boolean = {
      val asType = c.universe.appliedType(
        c.universe.typeOf[As[Any, Any]].typeConstructor,
        List(from, to)
      )
      val asInstance = c.inferImplicitValue(asType, silent = true)
      asInstance != EmptyTree
    }

    // === Main Derivation Logic ===

    def isStructuralType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased match {
        case RefinedType(parents, decls) =>
          parents.forall(p => p =:= typeOf[AnyRef] || p =:= typeOf[Any] || p =:= typeOf[Object]) &&
          decls.nonEmpty
        case _ => false
      }
    }

    class StructuralFieldInfo(val name: String, val tpe: Type)

    class StructuralInfo(tpe: Type) {
      val fields: List[StructuralFieldInfo] =
        tpe.dealias match {
          case RefinedType(_, decls) =>
            decls.toList.collect {
              case m: MethodSymbol if m.isMethod && m.paramLists.flatten.isEmpty =>
                new StructuralFieldInfo(m.name.decodedName.toString, m.returnType)
            }
          case _ => Nil
        }
    }

    def checkStructuralFieldMappingConsistency(sourceInfo: ProductInfo, targetInfo: StructuralInfo): Unit = {
      val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
      val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

      // Check: fields in source that don't exist in target
      // For As, only Optional fields are allowed (defaults break round-trip guarantee)
      sourceFieldsByName.foreach { case (name, sourceField) =>
        if (!targetFieldsByName.contains(name)) {
          val isOptional = isOptionType(sourceField.tpe)

          if (!isOptional) {
            val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            fail(
              s"""Cannot derive As[$aTpe, $bTpe]: Missing required field breaks round-trip
                 |
                 |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
                 |  Structural($targetFieldsStr)
                 |
                 |Field '$name: ${sourceField.tpe}' exists in ${aTpe.typeSymbol.name} but not in the structural type.
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

    def checkStructuralFieldMappingConsistencyReverse(sourceInfo: StructuralInfo, targetInfo: ProductInfo): Unit = {
      val sourceFieldsByName = sourceInfo.fields.map(f => f.name -> f).toMap
      val targetFieldsByName = targetInfo.fields.map(f => f.name -> f).toMap

      // Check: fields in target that don't exist in source
      // For As, only Optional fields are allowed (defaults break round-trip guarantee)
      targetFieldsByName.foreach { case (name, targetField) =>
        if (!sourceFieldsByName.contains(name)) {
          val isOptional = isOptionType(targetField.tpe)

          if (!isOptional) {
            val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe}").mkString(", ")
            fail(
              s"""Cannot derive As[$aTpe, $bTpe]: Missing required field breaks round-trip
                 |
                 |  Structural($sourceFieldsStr)
                 |  ${bTpe.typeSymbol.name}($targetFieldsStr)
                 |
                 |Field '$name: ${targetField.tpe}' exists in ${bTpe.typeSymbol.name} but not in the structural type.
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

    val aIsProduct    = isProductType(aTpe)
    val bIsProduct    = isProductType(bTpe)
    val aIsTuple      = isTupleType(aTpe)
    val bIsTuple      = isTupleType(bTpe)
    val aIsCoproduct  = isSealedTrait(aTpe)
    val bIsCoproduct  = isSealedTrait(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)

    // Perform compatibility checks based on type category
    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct, aIsStructural, bIsStructural) match {
      case (_, _, true, true, _, _, _, _) =>
      // Tuple to tuple - no default value checks needed, positional matching

      case (true, _, _, true, _, _, _, _) | (_, true, true, _, _, _, _, _) =>
        if (aIsProduct && !aIsTuple && bIsProduct && !bIsTuple) {
          val aInfo = new ProductInfo(aTpe)
          val bInfo = new ProductInfo(bTpe)
          checkNoDefaultValues(aInfo, bInfo, "source")
          checkNoDefaultValues(bInfo, aInfo, "target")
        } else if (aIsProduct && !aIsTuple) {
          val aInfo = new ProductInfo(aTpe)
          val bInfo = new ProductInfo(bTpe)
          checkNoDefaultValues(aInfo, bInfo, "source")
        } else if (bIsProduct && !bIsTuple) {
          val aInfo = new ProductInfo(aTpe)
          val bInfo = new ProductInfo(bTpe)
          checkNoDefaultValues(bInfo, aInfo, "target")
        }

      case (true, true, _, _, _, _, _, _) =>
        val aInfo = new ProductInfo(aTpe)
        val bInfo = new ProductInfo(bTpe)

        checkNoDefaultValues(aInfo, bInfo, "source")
        checkNoDefaultValues(bInfo, aInfo, "target")

        checkFieldMappingConsistency(aInfo, bInfo)

      case (true, _, _, _, _, _, _, true) =>
        val aInfo = new ProductInfo(aTpe)
        val bInfo = new StructuralInfo(bTpe)

        checkStructuralFieldMappingConsistency(aInfo, bInfo)

      case (_, true, _, _, _, _, true, _) =>
        val aInfo = new StructuralInfo(aTpe)
        val bInfo = new ProductInfo(bTpe)

        checkStructuralFieldMappingConsistencyReverse(aInfo, bInfo)

      case (_, _, _, _, true, true, _, _) =>
      // Coproduct to coproduct - no additional checks needed

      case _ =>
      // Try to derive anyway - the Into macros will fail if not possible
    }

    // Now try to derive both Into instances using the existing Into.derived macro
    // We use c.typecheck to ensure the macros expand correctly

    val intoABExpr = q"_root_.zio.blocks.schema.Into.derived[$aTpe, $bTpe]"
    val intoBAExpr = q"_root_.zio.blocks.schema.Into.derived[$bTpe, $aTpe]"

    c.Expr[As[A, B]](
      q"""
        {
          val intoAB: _root_.zio.blocks.schema.Into[$aTpe, $bTpe] = $intoABExpr
          val intoBA: _root_.zio.blocks.schema.Into[$bTpe, $aTpe] = $intoBAExpr

          new _root_.zio.blocks.schema.As[$aTpe, $bTpe] {
            def into(input: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] =
              intoAB.into(input)
            def from(input: $bTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $aTpe] =
              intoBA.into(input)
          }
        }
      """
    )
  }
}
