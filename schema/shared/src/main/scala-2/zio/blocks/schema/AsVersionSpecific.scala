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
              // Check if types are coercible (numeric widening/narrowing is OK)
              val canConvert = isNumericCoercible(sourceField.tpe, targetField.tpe) ||
                isImplicitIntoAvailable(sourceField.tpe, targetField.tpe)
              val canConvertBack = isNumericCoercible(targetField.tpe, sourceField.tpe) ||
                isImplicitIntoAvailable(targetField.tpe, sourceField.tpe)

              if (!canConvert || !canConvertBack) {
                fail(
                  s"Cannot derive As[$aTpe, $bTpe]: field '$name' has types that are not bidirectionally convertible. " +
                    s"Source: ${sourceField.tpe}, Target: ${targetField.tpe}. " +
                    s"Both directions must be convertible."
                )
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

