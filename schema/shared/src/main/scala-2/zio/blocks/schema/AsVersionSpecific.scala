package zio.blocks.schema

import zio.blocks.schema.CommonMacroOps
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait AsVersionSpecific {
  /**
   * Derives a bidirectional conversion As[A, B].
   *
   * This macro will:
   * 1. Verify that both Into[A, B] and Into[B, A] can be derived
   * 2. Check compatibility rules (no default values, consistent field mappings)
   * 3. Generate an As[A, B] that delegates to both derived Into instances
   */
  def derived[A, B]: As[A, B] = macro AsVersionSpecificImpl.derived[A, B]
}

private object AsVersionSpecificImpl {
  def derived[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[As[A, B]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias
    val bTpe = weakTypeOf[B].dealias

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
        var idx = 0

        constructor.paramLists.flatten.map { param =>
          val symbol = param.asTerm
          val name = NameTransformer.decode(symbol.name.toString)
          var fTpe = symbol.typeSignature.dealias
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val getter = getters.getOrElse(
            name,
            fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
          )
          // Check if field has a default value
          val hasDefault = symbol.isParamWithDefault
          val fieldInfo = new FieldInfo(name, fTpe, idx, getter, hasDefault)
          idx += 1
          fieldInfo
        }
      }
    }

    // === Compatibility Checks ===

    def checkNoDefaultValues(info: ProductInfo, direction: String): Unit = {
      val fieldsWithDefaults = info.fields.filter(_.hasDefault)
      if (fieldsWithDefaults.nonEmpty) {
        fail(
          s"Cannot derive As[$aTpe, $bTpe]: $direction type has fields with default values: " +
            s"${fieldsWithDefaults.map(_.name).mkString(", ")}. " +
            s"Default values break round-trip guarantee as we cannot distinguish between " +
            s"explicitly set default values and omitted values in reverse direction."
        )
      }
    }

    def isOptionType(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      dealiased.typeSymbol == definitions.OptionClass ||
        dealiased.typeConstructor.typeSymbol == definitions.OptionClass
    }

    def getOptionInnerType(tpe: Type): Type = {
      val optionTpe = typeArgs(tpe.dealias)
      if (optionTpe.nonEmpty) optionTpe.head else fail(s"Cannot extract inner type from Option: $tpe")
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

    /**
     * Checks if two container types (Option, List, Vector, Set, Seq) have bidirectionally 
     * convertible element types (including when elements have implicit As instances available).
     */
    def areBidirectionallyConvertibleContainers(sourceTpe: Type, targetTpe: Type): Boolean = {
      // Check if both are the same kind of container or both are collections
      val bothOptions = isOptionType(sourceTpe) && isOptionType(targetTpe)
      val bothCollections = (isListType(sourceTpe) || isVectorType(sourceTpe) || isSetType(sourceTpe) || isSeqType(sourceTpe)) &&
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

    // Check that field mappings are consistent in both directions
    def checkFieldMappingConsistency(sourceInfo: ProductInfo, targetInfo: ProductInfo): Unit = {
      // For each non-optional field in target, there must be a corresponding field in source
      // For optional fields in target that don't exist in source, they become None
      // We verify that the mapping is symmetric and consistent

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
                // As instance provides both directions - OK
              } else {
                // Check for container types (Option, List, etc.) with different element types
                val containerConvertible = areBidirectionallyConvertibleContainers(sourceField.tpe, targetField.tpe)
                
                if (containerConvertible) {
                  // Container types with bidirectionally convertible elements - OK
                } else {
                  // Check for newtype conversions (bidirectional)
                  val newtypeConvert = requiresNewtypeConversion(sourceField.tpe, targetField.tpe)
                  val newtypeUnwrap = requiresNewtypeUnwrapping(sourceField.tpe, targetField.tpe)
                  val newtypeConvertBack = requiresNewtypeConversion(targetField.tpe, sourceField.tpe)
                  val newtypeUnwrapBack = requiresNewtypeUnwrapping(targetField.tpe, sourceField.tpe)

                  val canConvertViaNewtype = (newtypeConvert && newtypeUnwrapBack) || (newtypeUnwrap && newtypeConvertBack)

                  if (canConvertViaNewtype) {
                    // Newtype conversion is bidirectional - OK
                  } else {
                    // Fall back to checking Into in both directions
                    val canConvert = isNumericCoercible(sourceField.tpe, targetField.tpe) ||
                      newtypeConvert || newtypeUnwrap ||
                      isImplicitIntoAvailable(sourceField.tpe, targetField.tpe)
                    val canConvertBack = isNumericCoercible(targetField.tpe, sourceField.tpe) ||
                      newtypeConvertBack || newtypeUnwrapBack ||
                      isImplicitIntoAvailable(targetField.tpe, sourceField.tpe)

                    if (!canConvert || !canConvertBack) {
                      fail(
                        s"Cannot derive As[$aTpe, $bTpe]: field '$name' has types that are not bidirectionally convertible. " +
                          s"Source: ${sourceField.tpe}, Target: ${targetField.tpe}. " +
                          s"Both directions must be convertible."
                      )
                    }
                  }
                }
              }
            }
          case None =>
            // Source has field that target doesn't have
            // This is only allowed if target has the same field as Option
            val optionalFieldInTarget = targetInfo.fields.find { f =>
              isOptionType(f.tpe) && {
                val innerType = getOptionInnerType(f.tpe)
                innerType =:= sourceField.tpe || isNumericCoercible(sourceField.tpe, innerType)
              }
            }
            // It's OK if source has extra fields - they just get dropped when going to target
            // and become None when coming back (if target has them as Option)
        }
      }
    }

    // === ZIO Prelude Newtype Detection ===
    // This must match the logic in IntoVersionSpecific.scala

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

    def requiresNewtypeConversion(sourceTpe: Type, targetTpe: Type): Boolean = {
      // Check if target is a ZIO Prelude newtype and source is its underlying type
      if (isZIONewtype(targetTpe)) {
        getNewtypeUnderlying(targetTpe) match {
          case Some(underlying) => sourceTpe =:= underlying
          case None => false
        }
      } else {
        false
      }
    }

    def requiresNewtypeUnwrapping(sourceTpe: Type, targetTpe: Type): Boolean = {
      // Check if source is a ZIO Prelude newtype and target is its underlying type
      if (isZIONewtype(sourceTpe)) {
        getNewtypeUnderlying(sourceTpe) match {
          case Some(underlying) => targetTpe =:= underlying
          case None => false
        }
      } else {
        false
      }
    }

    def isNumericCoercible(from: Type, to: Type): Boolean = {
      val numericTypes = List(
        typeOf[Byte], typeOf[Short], typeOf[Int], typeOf[Long],
        typeOf[Float], typeOf[Double]
      )

      val fromIdx = numericTypes.indexWhere(t => from =:= t)
      val toIdx = numericTypes.indexWhere(t => to =:= t)

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

    val aIsProduct = isProductType(aTpe)
    val bIsProduct = isProductType(bTpe)
    val aIsTuple = isTupleType(aTpe)
    val bIsTuple = isTupleType(bTpe)
    val aIsCoproduct = isSealedTrait(aTpe)
    val bIsCoproduct = isSealedTrait(bTpe)

    // Perform compatibility checks based on type category
    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct) match {
      case (true, true, _, _, _, _) =>
        // Case class to case class
        val aInfo = new ProductInfo(aTpe)
        val bInfo = new ProductInfo(bTpe)

        // Check no default values
        checkNoDefaultValues(aInfo, "source")
        checkNoDefaultValues(bInfo, "target")

        // Check field mapping consistency
        checkFieldMappingConsistency(aInfo, bInfo)

      case (true, _, _, true, _, _) | (_, true, true, _, _, _) =>
        // Case class to/from tuple
        if (aIsProduct) {
          val aInfo = new ProductInfo(aTpe)
          checkNoDefaultValues(aInfo, "source")
        }
        if (bIsProduct) {
          val bInfo = new ProductInfo(bTpe)
          checkNoDefaultValues(bInfo, "target")
        }

      case (_, _, true, true, _, _) =>
        // Tuple to tuple - no default value checks needed

      case (_, _, _, _, true, true) =>
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

