package zio.blocks.schema

import scala.annotation.experimental
import scala.quoted.*
import zio.blocks.schema.derive.OpaqueMacros
import zio.blocks.schema.derive.NewtypeMacros
import zio.blocks.schema.derive.ProductMacros
import zio.blocks.schema.derive.CollectionMacros
import zio.blocks.schema.derive.StructuralMacros

/**
 * Type class for type-safe conversions from A to B with runtime validation.
 *
 * Into[A, B] provides a unidirectional conversion that may fail with a
 * SchemaError. It handles:
 *   - Numeric coercions (widening and narrowing with validation)
 *   - Product types (case classes, tuples)
 *   - Coproduct types (sealed traits, enums)
 *   - Collection types with element coercion
 *   - Schema evolution patterns (field reordering, renaming, optional fields)
 *   - Special types (opaque types, newtypes, structural types)
 *
 * Note: The trait is defined as invariant (Into[A, B]) rather than
 * contravariant/covariant (Into[-A, +B]) to ensure type safety with macro
 * derivation and avoid issues with type inference.
 *
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 */
trait Into[A, B] {

  /**
   * Convert a value of type A to type B.
   *
   * @param input
   *   The value to convert
   * @return
   *   Right(b) if conversion succeeds, Left(error) if it fails
   */
  def into(input: A): Either[SchemaError, B]

  /**
   * Convert a value of type A to type B, throwing on failure.
   *
   * @param input
   *   The value to convert
   * @return
   *   The converted value
   * @throws SchemaError
   *   if conversion fails
   */
  @inline final def intoOrThrow(input: A): B = into(input) match {
    case Right(b)  => b
    case Left(err) => throw err
  }
}

object Into {

  /**
   * Summon an Into[A, B] instance from implicit scope or derive it.
   */
  inline def apply[A, B](using into: Into[A, B]): Into[A, B] = into

  /**
   * Helper method to find self-reference during recursive derivation. First
   * tries summonFrom, then falls back to summonInline to find given lazy val
   * forward references.
   */
  inline def findSelf[A, B]: Into[A, B] =
    scala.compiletime.summonFrom {
      case self: Into[A, B] => self
      case _                =>
        // Fallback: try summonInline to find given lazy val forward references
        // These are created by IntoMacro when handling recursive types
        scala.compiletime.summonInline[Into[A, B]]
    }

  /**
   * Inline dispatcher: Attempts to find an existing implicit (to handle
   * recursion). Only if it doesn't find one, it expands the `derived` macro.
   * This breaks infinite expansion loops.
   */
  @experimental
  inline def attempt[A, B]: Into[A, B] =
    scala.compiletime.summonFrom {
      case i: Into[A, B] => i
      case _             => derived[A, B]
    }

  /**
   * Automatically derive Into[A, B] instances at compile time.
   */
  @experimental
  inline given derived[A, B]: Into[A, B] = ${ IntoMacro.deriveImpl[A, B] }

  /**
   * Identity conversion (A to A). Note: This is NOT a given to prevent it from
   * being found by Implicits.search inside the macro, which would bypass
   * validation for opaque types. The macro (deriveImpl) handles identity
   * optimization when safe.
   */
  def identity[A]: Into[A, A] = new Into[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
  }
}

object IntoMacro {
  // Compile-time cache to track types being derived (similar to Schema's schemaRefs)
  // Limit size to prevent memory issues
  private val intoRefs            = new scala.collection.mutable.HashMap[String, Any]()
  private val MAX_REFS            = 100
  private var recursionDepth      = 0
  private val MAX_RECURSION_DEPTH = 10

  // Forward references for recursive types (similar to Schema's schemaRefs pattern)
  // Maps typeKey -> Expr[Into[_, _]] (the forward reference)
  // Note: We use Any to avoid Quotes dependency at object level
  private val forwardRefs = new scala.collection.mutable.HashMap[String, Any]()

  /**
   * Helper method for macro clients to safely summon or derive Into instances.
   * This method checks if we're already in recursion for the given type pair.
   * If in recursion, it uses findSelf (summonFrom) to find the self-reference.
   * Otherwise, it directly calls deriveImpl.
   *
   * This avoids inline expansion loops by making the decision at macro
   * expansion time rather than generating code that calls inline methods.
   */
  @experimental
  def summonOrDerive[S: Type, T: Type](using Quotes): Expr[Into[S, T]] = {
    import quotes.reflect.*
    val typeKey = s"${TypeRepr.of[S].show} -> ${TypeRepr.of[T].show}"

    if (intoRefs.contains(typeKey)) {
      // We're already in recursion for this type - check for forward reference
      forwardRefs.get(typeKey) match {
        case Some(ref: Expr[?]) =>
          // Forward reference exists - return it directly
          ref.asInstanceOf[Expr[Into[S, T]]]
        case None =>
          // No forward reference - fall back to findSelf
          '{ Into.findSelf[S, T] }
      }
    } else {
      // Not in recursion - derive directly
      deriveImpl[S, T]
    }
  }

  @experimental
  def deriveImpl[A: Type, B: Type](using Quotes): Expr[Into[A, B]] = {
    import quotes.reflect.*

    val aType   = TypeRepr.of[A]
    val bType   = TypeRepr.of[B]
    val typeKey = s"${aType.show} -> ${bType.show}"

    // PRIORITY 1: Try numeric coercion FIRST (before recursion check)
    // This prevents false positives for primitive coercions like Int -> Long
    numericCoercion[A, B](aType, bType) match {
      case Some(intoExpr) =>
        return intoExpr
      case None =>
    }

    // PRIORITY 2: If types are the same, use identity (also before recursion check)
    // BUT: Skip identity optimization if target has a companion object, because it might be
    // an Opaque Type or Newtype with validation logic that must be executed.
    //
    // SAFETY CHECK: Even if types are equal (A =:= B), we cannot safely optimize to identity
    // if B has a companion object, because it might be an Opaque Type or Newtype with validation logic.
    // However, we allow the optimization for standard primitive types (Int, String, etc.) even if they
    // have companion objects, as these don't contain validation logic.
    val targetHasCompanion = bType.typeSymbol.companionModule != Symbol.noSymbol

    // Check if the target type is an opaque type (these MUST NOT use identity optimization)
    val isTargetOpaque = OpaqueMacros.isOpaque(bType)

    // Check if the type is a standard primitive type (these are safe to optimize even with companion objects)
    val isStandardPrimitive = {
      val typeName = bType.typeSymbol.fullName
      typeName == "scala.Int" || typeName == "scala.Long" || typeName == "scala.Short" ||
      typeName == "scala.Byte" || typeName == "scala.Float" || typeName == "scala.Double" ||
      typeName == "scala.Char" || typeName == "scala.Boolean" || typeName == "scala.String" ||
      typeName == "scala.Unit"
    }

    // Only apply identity optimization if types match AND (no companion object OR it's a standard primitive) AND target is NOT opaque.
    // Standard primitives are safe because their companion objects don't contain validation logic.
    // Opaque types MUST NOT use identity optimization because they may have validation logic.
    if (aType =:= bType && (!targetHasCompanion || isStandardPrimitive) && !isTargetOpaque) {
      return '{ Into.identity[A].asInstanceOf[Into[A, B]] }
    }

    // PRIORITY 3: Check if we're already deriving this type pair (recursion detection)
    // Only check recursion AFTER we've ruled out primitive coercions and identity
    if (intoRefs.contains(typeKey)) {
      // Guard against infinite recursion
      if (recursionDepth >= MAX_RECURSION_DEPTH) {
        report.errorAndAbort(
          s"Maximum recursion depth ($MAX_RECURSION_DEPTH) exceeded while deriving Into[${aType.show}, ${bType.show}]. " +
            s"This may indicate a circular dependency or infinite loop. " +
            s"Current recursion depth: $recursionDepth, intoRefs size: ${intoRefs.size}"
        )
      }

      // Try to summon existing implicit first
      // BUT: Skip if target is opaque type to prevent identity from bypassing validation
      val isTargetOpaque = OpaqueMacros.isOpaque(bType)
      val intoTpe        = TypeRepr.of[Into[A, B]]
      Implicits.search(intoTpe) match {
        case success: ImplicitSearchSuccess =>
          // CRITICAL: Don't use found implicit if target is opaque type
          // This prevents identity[Int] from being used as Into[Int, ValidAge]
          if (!isTargetOpaque) {
            return success.tree.asExpr.asInstanceOf[Expr[Into[A, B]]]
          }
        // If target is opaque, fall through to forward reference or findSelf
        case _ =>
          // Check if we have a forward reference for this type
          forwardRefs.get(typeKey) match {
            case Some(ref) =>
              // Forward reference exists - use it
              return ref.asInstanceOf[Expr[Into[A, B]]]
            case None =>
              // No forward reference yet - fall back to findSelf
              return '{ Into.findSelf[A, B] }
          }
      }
    }

    // Mark as being derived (only after we've checked for primitives)
    // Limit size to prevent memory issues - clear old entries if too large
    if (intoRefs.size >= MAX_REFS) {
      intoRefs.clear()
      forwardRefs.clear() // Also clear forward refs
      recursionDepth = 0  // Reset recursion depth when clearing
    }

    // Create forward reference BEFORE marking as being derived
    // This allows recursive calls to find the forward reference
    val intoTpe    = TypeRepr.of[Into[A, B]]
    val name       = s"into_${forwardRefs.size}"
    val flags      = Flags.Lazy | Flags.Given
    val symbol     = Symbol.newVal(Symbol.spliceOwner, name, intoTpe, flags, Symbol.noSymbol)
    val forwardRef =
      Ref(symbol).asExpr.asInstanceOf[Expr[Into[A, B]]]

    // Store the forward reference BEFORE adding to intoRefs
    // This ensures it's available when recursion is detected
    forwardRefs.update(typeKey, forwardRef)

    intoRefs.update(typeKey, ()) // Placeholder value
    recursionDepth += 1

    // Helper function to create ValDef and return forward reference wrapped in a block
    // The block ensures the ValDef is included in the generated code
    def createValDefAndReturnRef(intoExpr: Expr[Into[A, B]]): Expr[Into[A, B]] = {
      // Change owner of the expression tree to match the symbol
      val intoTerm = intoExpr.asTerm.changeOwner(symbol)
      val valDef   = ValDef(symbol, Some(intoTerm))

      // Create block: { given lazy val into_N = <expr>; into_N }
      // This ensures the ValDef is included in the generated code
      val refTerm = Ref(symbol) // Ref(symbol) already returns a Term
      val block   = Block(List(valDef), refTerm)
      block.asExpr.asInstanceOf[Expr[Into[A, B]]]
    }

    // Use try-finally to ensure cleanup even if an error occurs
    var derivationSucceeded = false
    try {
      // Try opaque type conversion (check early to handle validation)
      val opaqueResult = OpaqueMacros.opaqueTypeConversion[A, B](aType, bType)
      opaqueResult match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      // Try ZIO Prelude Newtype/Subtype conversion
      NewtypeMacros.newtypeConversion[A, B](aType, bType) match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      // Try collection conversion FIRST (before coproduct, since List/Vector are sealed traits)
      CollectionMacros.collectionConversion[A, B](aType, bType) match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      // Try structural type conversion (Selectable)
      StructuralMacros.structuralTypeConversion[A, B](aType, bType) match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      // Try product type conversion (case class, tuple)
      ProductMacros.productTypeConversion[A, B](aType, bType) match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      // Try coproduct type conversion (sealed trait, enum)
      coproductTypeConversion[A, B](aType, bType) match {
        case Some(intoExpr) =>
          derivationSucceeded = true
          return createValDefAndReturnRef(intoExpr)
        case None =>
      }

      report.errorAndAbort(
        s"Cannot derive Into[${aType.show}, ${bType.show}]. " +
          s"No conversion available between these types."
      )
    } finally {
      // Always clean up to prevent memory leaks
      intoRefs.remove(typeKey)
      recursionDepth = math.max(0, recursionDepth - 1)

      // If derivation failed, remove forward reference
      if (!derivationSucceeded) {
        forwardRefs.remove(typeKey)
      }
    }
  }

  private def numericCoercion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[Into[A, B]]] = {
    import quotes.reflect.*

    val intTpe    = TypeRepr.of[Int]
    val longTpe   = TypeRepr.of[Long]
    val floatTpe  = TypeRepr.of[Float]
    val doubleTpe = TypeRepr.of[Double]
    val byteTpe   = TypeRepr.of[Byte]
    val shortTpe  = TypeRepr.of[Short]

    // Widening conversions (lossless) - optimized with inline Right
    // Performance: Use direct Right construction to avoid method call overhead
    if (source =:= byteTpe && target =:= shortTpe) {
      Some('{
        new Into[Byte, Short] {
          @inline def into(input: Byte): Either[SchemaError, Short] = Right(input.toShort)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= byteTpe && target =:= intTpe) {
      Some('{
        new Into[Byte, Int] {
          @inline def into(input: Byte): Either[SchemaError, Int] = Right(input.toInt)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= byteTpe && target =:= longTpe) {
      Some('{
        new Into[Byte, Long] {
          @inline def into(input: Byte): Either[SchemaError, Long] = Right(input.toLong)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= shortTpe && target =:= intTpe) {
      Some('{
        new Into[Short, Int] {
          @inline def into(input: Short): Either[SchemaError, Int] = Right(input.toInt)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= shortTpe && target =:= longTpe) {
      Some('{
        new Into[Short, Long] {
          @inline def into(input: Short): Either[SchemaError, Long] = Right(input.toLong)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= intTpe && target =:= longTpe) {
      Some('{
        new Into[Int, Long] {
          @inline def into(input: Int): Either[SchemaError, Long] = Right(input.toLong)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= floatTpe && target =:= doubleTpe) {
      Some('{
        new Into[Float, Double] {
          @inline def into(input: Float): Either[SchemaError, Double] = Right(input.toDouble)
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    }
    // Narrowing conversions (with validation) - optimized with @inline and early validation
    else if (source =:= longTpe && target =:= intTpe) {
      Some('{
        new Into[Long, Int] {
          @inline def into(input: Long): Either[SchemaError, Int] =
            // Performance: Early validation, avoid string interpolation unless error
            if (input >= Int.MinValue.toLong && input <= Int.MaxValue.toLong) {
              Right(input.toInt)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Long value $input is out of range for Int [${Int.MinValue}, ${Int.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= longTpe && target =:= shortTpe) {
      Some('{
        new Into[Long, Short] {
          @inline def into(input: Long): Either[SchemaError, Short] =
            if (input >= Short.MinValue.toLong && input <= Short.MaxValue.toLong) {
              Right(input.toShort)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Long value $input is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= longTpe && target =:= byteTpe) {
      Some('{
        new Into[Long, Byte] {
          @inline def into(input: Long): Either[SchemaError, Byte] =
            if (input >= Byte.MinValue.toLong && input <= Byte.MaxValue.toLong) {
              Right(input.toByte)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Long value $input is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= intTpe && target =:= shortTpe) {
      Some('{
        new Into[Int, Short] {
          @inline def into(input: Int): Either[SchemaError, Short] =
            if (input >= Short.MinValue.toInt && input <= Short.MaxValue.toInt) {
              Right(input.toShort)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Int value $input is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= intTpe && target =:= byteTpe) {
      Some('{
        new Into[Int, Byte] {
          @inline def into(input: Int): Either[SchemaError, Byte] =
            if (input >= Byte.MinValue.toInt && input <= Byte.MaxValue.toInt) {
              Right(input.toByte)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Int value $input is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= shortTpe && target =:= byteTpe) {
      Some('{
        new Into[Short, Byte] {
          @inline def into(input: Short): Either[SchemaError, Byte] =
            if (input >= Byte.MinValue.toShort && input <= Byte.MaxValue.toShort) {
              Right(input.toByte)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Short value $input is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else if (source =:= doubleTpe && target =:= floatTpe) {
      Some('{
        new Into[Double, Float] {
          @inline def into(input: Double): Either[SchemaError, Float] =
            if (input.isNaN || input.isInfinite) {
              Right(input.toFloat)
            } else if (input >= -Float.MaxValue.toDouble && input <= Float.MaxValue.toDouble) {
              Right(input.toFloat)
            } else {
              Left(
                SchemaError.expectationMismatch(
                  Nil,
                  s"Double value $input is out of range for Float [${-Float.MaxValue}, ${Float.MaxValue}]"
                )
              )
            }
        }
      }.asInstanceOf[Expr[Into[A, B]]])
    } else {
      None
    }
  }

  @experimental
  private def coproductTypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[Into[A, B]]] = {
    import quotes.reflect.*

    // Check if both are sealed traits, abstract classes, or enums
    val isSourceSealed = isSealedTraitOrAbstractClass(source) || source.typeSymbol.flags.is(Flags.Enum)
    val isTargetSealed = isSealedTraitOrAbstractClass(target) || target.typeSymbol.flags.is(Flags.Enum)

    if (!isSourceSealed || !isTargetSealed) {
      return None
    }

    // Get subtypes (cases) of both sealed traits
    val sourceCases = getDirectSubTypes(source)
    val targetCases = getDirectSubTypes(target)

    if (sourceCases.isEmpty || targetCases.isEmpty) {
      return None
    }

    // Map source cases to target cases
    // Strategy 1: Match by name
    // Strategy 2: Match by constructor signature (if name match fails)
    val usedTargetIndices = scala.collection.mutable.Set[Int]()
    val caseMapping       = sourceCases.flatMap { sourceCase =>
      val sourceName      = getTypeName(sourceCase)
      val sourceSignature = getConstructorSignature(sourceCase)

      // First try: match by name
      targetCases.zipWithIndex.find { case (targetCase, idx) =>
        !usedTargetIndices.contains(idx) && getTypeName(targetCase) == sourceName
      } match {
        case Some((targetCase, idx)) =>
          usedTargetIndices.add(idx)
          Some((sourceCase, targetCase))
        case None =>
          // Second try: match by constructor signature
          targetCases.zipWithIndex.find { case (targetCase, idx) =>
            !usedTargetIndices.contains(idx) && {
              val targetSignature = getConstructorSignature(targetCase)
              sourceSignature == targetSignature && sourceSignature.nonEmpty
            }
          } match {
            case Some((targetCase, idx)) =>
              usedTargetIndices.add(idx)
              Some((sourceCase, targetCase))
            case None =>
              None
          }
      }
    }

    if (caseMapping.size != sourceCases.size) {
      val unmapped = sourceCases.filterNot { sc =>
        caseMapping.exists(_._1 =:= sc)
      }
      val unmappedNames = unmapped.map(getTypeName).mkString(", ")
      report.errorAndAbort(
        s"Cannot map all cases from ${source.show} to ${target.show}. " +
          s"Mapped ${caseMapping.size}/${sourceCases.size} cases.\n" +
          s"Unmapped source cases: $unmappedNames\n" +
          s"Matching strategies attempted:\n" +
          s"  1. Name matching\n" +
          s"  2. Constructor signature matching"
      )
    }

    // Generate pattern matching conversion
    Some(generateCoproductConversion[A, B](source, target, caseMapping))
  }

  private def isSealedTraitOrAbstractClass(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }
  }

  private def getDirectSubTypes(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    // For enums, we need to get term symbols (enum values) instead of type symbols
    if (tpe.typeSymbol.flags.is(Flags.Enum)) {
      // Get enum values (term symbols) and convert them to TypeRepr
      tpe.typeSymbol.children.filter(_.flags.is(Flags.Enum)).map(_.typeRef)
    } else {
      // For sealed traits, get type symbols (case classes/objects)
      tpe.typeSymbol.children.map(_.typeRef)
    }
  }

  private def getTypeName(using Quotes)(tpe: quotes.reflect.TypeRepr): String = {
    import quotes.reflect.*
    val sym = tpe.typeSymbol
    if (sym.flags.is(Flags.Module)) {
      sym.name.stripSuffix("$")
    } else {
      sym.name
    }
  }

  /**
   * Get constructor signature as a list of parameter types. Returns empty list
   * for case objects or if no constructor found.
   */
  private def getConstructorSignature(using Quotes)(tpe: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*

    // Case objects have no constructor parameters
    if (tpe.typeSymbol.flags.is(Flags.Module)) {
      return Nil
    }

    // Try to get primary constructor
    tpe.classSymbol.flatMap { classSymbol =>
      val constructor = classSymbol.primaryConstructor
      constructor.paramSymss.flatten.filterNot(_.isTypeParam) match {
        case Nil    => Some(Nil)
        case params =>
          Some(params.map { param =>
            tpe.memberType(param).show
          })
      }
    }.getOrElse(Nil)
  }

  @experimental
  private def generateCoproductConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr,
    caseMapping: List[(quotes.reflect.TypeRepr, quotes.reflect.TypeRepr)]
  ): Expr[Into[A, B]] = {
    val _ = (source, target) // Suppress unused parameter warnings
    import quotes.reflect.*

    '{
      new Into[A, B] {
        def into(input: A): Either[SchemaError, B] =
          ${
            // Generate pattern matching for each case
            val inputTerm = '{ input }.asTerm
            val cases     = caseMapping.map { case (sourceCase, targetCase) =>
              sourceCase.asType match {
                case '[s] =>
                  targetCase.asType match {
                    case '[t] =>
                      // Check if source case is an object (case object or enum value)
                      // For enums, we need to check if the parent is an enum and get term symbols
                      val isSourceEnum = source.typeSymbol.flags.is(Flags.Enum)
                      val isTargetEnum = target.typeSymbol.flags.is(Flags.Enum)

                      // Try to get term symbol for enum cases (only for enum values without parameters)
                      val sourceTermSymbol = if (isSourceEnum) {
                        source.typeSymbol.children.find { child =>
                          child.name == sourceCase.typeSymbol.name && child.flags.is(Flags.Enum)
                        }
                      } else {
                        None
                      }
                      val targetTermSymbol = if (isTargetEnum) {
                        target.typeSymbol.children.find { child =>
                          child.name == targetCase.typeSymbol.name && child.flags.is(Flags.Enum)
                        }
                      } else {
                        None
                      }

                      // Check if both are simple enum values (term symbols) or case objects
                      // For enum cases with parameters, they are classes, not objects
                      val isSourceSimpleEnum = sourceTermSymbol.exists(_.isTerm)
                      val isTargetSimpleEnum = targetTermSymbol.exists(_.isTerm)
                      val isSourceObject     = sourceCase.typeSymbol.flags.is(Flags.Module) || isSourceSimpleEnum
                      val isTargetObject     = targetCase.typeSymbol.flags.is(Flags.Module) || isTargetSimpleEnum

                      if (isSourceObject && isTargetObject) {
                        // Both are objects - simple reference
                        // For enum values (term symbols), use Ref directly
                        // For case objects, use companion module
                        val targetRef = targetTermSymbol match {
                          case Some(termSym) if termSym.isTerm =>
                            // For enum values, we reference them directly using Ref
                            // The term symbol should be accessible in the enum's scope
                            Ref(termSym)
                          case _ =>
                            Ref(targetCase.typeSymbol.companionModule)
                        }
                        // For enum values, match on the specific VALUE (term symbol), not the type
                        // This ensures each case is matched correctly
                        // Using Ref(sourceTermSymbol) generates: case Status1.Active => ...
                        val pattern = if (isSourceSimpleEnum && sourceTermSymbol.isDefined) {
                          // Match on the specific enum value using Ref
                          sourceTermSymbol match {
                            case Some(srcTermSym) if srcTermSym.isTerm =>
                              Ref(srcTermSym)
                            case _ =>
                              Typed(Wildcard(), Inferred(sourceCase))
                          }
                        } else {
                          // For case objects, use type match
                          Typed(Wildcard(), Inferred(sourceCase))
                        }
                        CaseDef(
                          pattern,
                          None,
                          '{ Right(${ targetRef.asExpr.asInstanceOf[Expr[B]] }) }.asTerm
                        )
                      } else if (!isSourceObject && !isTargetObject) {
                        // Both are case classes - need recursive conversion
                        // Use a simpler approach: check at runtime and delegate to derived Into

                        CaseDef(
                          Typed(Wildcard(), Inferred(sourceCase)),
                          None,
                          '{
                            val x = input.asInstanceOf[s]
                            ${
                              // Try to summon or derive Into for the case classes
                              Expr.summon[Into[s, t]] match {
                                case Some(intoExpr) =>
                                  '{ $intoExpr.into(x).map(_.asInstanceOf[B]) }
                                case None =>
                                  // Try to derive it on-the-fly safely (without calling inline methods in generated code)
                                  if (sourceCase =:= targetCase) {
                                    '{ Right(x.asInstanceOf[B]) }
                                  } else {
                                    // Use IntoMacro.summonOrDerive to decide at compile-time how to obtain Into[s, t]
                                    val innerInto = IntoMacro.summonOrDerive[s, t]
                                    '{ $innerInto.into(x).map(_.asInstanceOf[B]) }
                                  }
                              }
                            }
                          }.asTerm
                        )
                      } else {
                        report.errorAndAbort(
                          s"Cannot convert between object and case class: ${sourceCase.show} -> ${targetCase.show}"
                        )
                      }
                  }
              }
            }

            // Add a default case for exhaustiveness (should never happen if mapping is complete)
            val defaultCase = CaseDef(
              Wildcard(),
              None,
              '{
                Left(
                  SchemaError.expectationMismatch(
                    Nil,
                    s"Unexpected case in sealed trait conversion"
                  )
                )
              }.asTerm
            )

            Match(inputTerm, cases :+ defaultCase).asExpr.asInstanceOf[Expr[Either[SchemaError, B]]]
          }
      }
    }
  }

}
