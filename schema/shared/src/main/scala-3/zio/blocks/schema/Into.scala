package zio.blocks.schema

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
  final def intoOrThrow(input: A): B = into(input) match {
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
   * Automatically derive Into[A, B] instances at compile time.
   */
  inline given derived[A, B]: Into[A, B] = ${ IntoMacro.deriveImpl[A, B] }

  /**
   * Identity conversion (A to A).
   */
  given identity[A]: Into[A, A] = new Into[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
  }
}

private object IntoMacro {
  def deriveImpl[A: Type, B: Type](using Quotes): Expr[Into[A, B]] = {
    import quotes.reflect.*

    val aType = TypeRepr.of[A]
    val bType = TypeRepr.of[B]

    // Try numeric coercion first
    numericCoercion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // If types are the same, use identity
    if (aType =:= bType) {
      return '{ Into.identity[A].asInstanceOf[Into[A, B]] }
    }

    // Try opaque type conversion (check early to handle validation)
    OpaqueMacros.opaqueTypeConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // Try ZIO Prelude Newtype/Subtype conversion
    NewtypeMacros.newtypeConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // Try collection conversion FIRST (before coproduct, since List/Vector are sealed traits)
    CollectionMacros.collectionConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // Try structural type conversion (Selectable)
    StructuralMacros.structuralTypeConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // Try product type conversion (case class, tuple)
    ProductMacros.productTypeConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    // Try coproduct type conversion (sealed trait, enum)
    coproductTypeConversion[A, B](aType, bType) match {
      case Some(intoExpr) => return intoExpr
      case None           => ()
    }

    report.errorAndAbort(
      s"Cannot derive Into[${aType.show}, ${bType.show}]. " +
        s"No conversion available between these types."
    )
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

  private def coproductTypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[Into[A, B]]] = {
    import quotes.reflect.*

    // Check if both are sealed traits or enums
    val isSourceSealed = isSealedTraitOrAbstractClass(source)
    val isTargetSealed = isSealedTraitOrAbstractClass(target)

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

  private def getDirectSubTypes(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] =
    tpe.typeSymbol.children.map(_.typeRef)

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

  private def generateCoproductConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr,
    caseMapping: List[(quotes.reflect.TypeRepr, quotes.reflect.TypeRepr)]
  ): Expr[Into[A, B]] = {
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
                      val isSourceObject = sourceCase.typeSymbol.flags.is(Flags.Module) ||
                        sourceCase.termSymbol.flags.is(Flags.Enum)
                      val isTargetObject = targetCase.typeSymbol.flags.is(Flags.Module) ||
                        targetCase.termSymbol.flags.is(Flags.Enum)

                      if (isSourceObject && isTargetObject) {
                        // Both are objects - simple reference
                        val targetRef = if (targetCase.termSymbol.flags.is(Flags.Enum)) {
                          Ref(targetCase.termSymbol)
                        } else {
                          Ref(targetCase.typeSymbol.companionModule)
                        }
                        CaseDef(
                          Typed(Wildcard(), Inferred(sourceCase)),
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
                                  // Try to derive it on the fly
                                  if (sourceCase =:= targetCase) {
                                    '{ Right(x.asInstanceOf[B]) }
                                  } else {
                                    // Use product conversion if both are product types
                                    '{ Into.derived[s, t].into(x).map(_.asInstanceOf[B]) }
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
