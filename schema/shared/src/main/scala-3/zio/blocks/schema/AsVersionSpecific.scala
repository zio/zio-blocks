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

    val aIsProduct   = aTpe.classSymbol.exists(isProductType)
    val bIsProduct   = bTpe.classSymbol.exists(isProductType)
    val aIsTuple     = isTupleType(aTpe)
    val bIsTuple     = isTupleType(bTpe)
    val aIsCoproduct = isCoproductType(aTpe)
    val bIsCoproduct = isCoproductType(bTpe)

    // Perform compatibility checks based on type category
    (aIsProduct, bIsProduct, aIsTuple, bIsTuple, aIsCoproduct, bIsCoproduct) match {
      case (true, true, _, _, _, _) =>
        // Case class to case class
        val aInfo = new ProductInfoCompat[A](aTpe)
        val bInfo = new ProductInfoCompat[B](bTpe)

        // Check no default values
        checkNoDefaultValues(aInfo, "source", aTpe, bTpe)
        checkNoDefaultValues(bInfo, "target", aTpe, bTpe)

        // Check field mapping consistency
        checkFieldMappingConsistency(aInfo, bInfo, aTpe, bTpe)

      case (true, _, _, true, _, _) | (_, true, true, _, _, _) =>
        // Case class to/from tuple
        if (aIsProduct) {
          val aInfo = new ProductInfoCompat[A](aTpe)
          checkNoDefaultValues(aInfo, "source", aTpe, bTpe)
        }
        if (bIsProduct) {
          val bInfo = new ProductInfoCompat[B](bTpe)
          checkNoDefaultValues(bInfo, "target", aTpe, bTpe)
        }

      case (_, _, true, true, _, _) =>
      // Tuple to tuple - no default value checks needed

      case (_, _, _, _, true, true) =>
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

  private def isTupleType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)

  private def isCoproductType(tpe: TypeRepr): Boolean =
    isSealedTraitOrAbstractClass(tpe) || isEnum(tpe)

  private def isEnum(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Enum) && !tpe.typeSymbol.flags.is(Flags.Case)

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
    direction: String,
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Unit = {
    val fieldsWithDefaults = info.fields.filter(_.hasDefault)
    if (fieldsWithDefaults.nonEmpty) {
      fail(
        s"Cannot derive As[${aTpe.show}, ${bTpe.show}]: $direction type has fields with default values: " +
          s"${fieldsWithDefaults.map(_.name).mkString(", ")}. " +
          s"Default values break round-trip guarantee as we cannot distinguish between " +
          s"explicitly set default values and omitted values in reverse direction."
      )
    }
  }

  private def isOptionType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Option[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def getOptionInnerType(tpe: TypeRepr): TypeRepr = {
    val args = typeArgs(tpe.dealias)
    if (args.nonEmpty) args.head else report.errorAndAbort(s"Cannot extract inner type from Option: ${tpe.show}")
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
            val canConvert = isNumericCoercible(sourceField.tpe, targetField.tpe) ||
              isImplicitIntoAvailable(sourceField.tpe, targetField.tpe)
            val canConvertBack = isNumericCoercible(targetField.tpe, sourceField.tpe) ||
              isImplicitIntoAvailable(targetField.tpe, sourceField.tpe)

            if (!canConvert || !canConvertBack) {
              fail(
                s"Cannot derive As[${aTpe.show}, ${bTpe.show}]: field '$name' has types that are not bidirectionally convertible. " +
                  s"Source: ${sourceField.tpe.show}, Target: ${targetField.tpe.show}. " +
                  s"Both directions must be convertible."
              )
            }
          }
        case None =>
        // Source has field that target doesn't have - this is OK
        // Extra fields get dropped when going to target
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
}
