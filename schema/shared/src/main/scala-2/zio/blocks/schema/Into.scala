package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Type class for type-safe conversions from A to B with runtime validation.
 *
 * Into[A, B] provides a unidirectional conversion that may fail with a
 * SchemaError. It handles:
 *   - Numeric coercions (widening and narrowing with validation)
 *   - Product types (case classes, tuples)
 *   - Coproduct types (sealed traits)
 *   - Collection types with element coercion
 *   - Schema evolution patterns (field reordering, renaming, optional fields)
 *   - Special types (ZIO Prelude newtypes, structural types)
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
  def apply[A, B](implicit into: Into[A, B]): Into[A, B] = into

  /**
   * Automatically derive Into[A, B] instances at compile time.
   */
  implicit def derived[A, B]: Into[A, B] = macro IntoMacros.deriveInto[A, B]

  /**
   * Identity conversion (A to A).
   */
  implicit def identity[A]: Into[A, A] = new Into[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
  }
}

private object IntoMacros {
  def deriveInto[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[Into[A, B]] = {
    import c.universe._

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    // Try numeric coercion first
    def numericCoercion(source: Type, target: Type): Option[c.Expr[Into[_, _]]] = {
      val intTpe    = typeOf[Int]
      val longTpe   = typeOf[Long]
      val floatTpe  = typeOf[Float]
      val doubleTpe = typeOf[Double]
      val byteTpe   = typeOf[Byte]
      val shortTpe  = typeOf[Short]

      // Widening conversions (lossless)
      if (source =:= byteTpe && target =:= shortTpe) {
        Some(c.Expr[Into[Byte, Short]](q"""
          new _root_.zio.blocks.schema.Into[Byte, Short] {
            def into(input: Byte): Either[_root_.zio.blocks.schema.SchemaError, Short] = 
              Right(input.toShort)
          }
        """))
      } else if (source =:= byteTpe && target =:= intTpe) {
        Some(c.Expr[Into[Byte, Int]](q"""
          new _root_.zio.blocks.schema.Into[Byte, Int] {
            def into(input: Byte): Either[_root_.zio.blocks.schema.SchemaError, Int] = 
              Right(input.toInt)
          }
        """))
      } else if (source =:= byteTpe && target =:= longTpe) {
        Some(c.Expr[Into[Byte, Long]](q"""
          new _root_.zio.blocks.schema.Into[Byte, Long] {
            def into(input: Byte): Either[_root_.zio.blocks.schema.SchemaError, Long] = 
              Right(input.toLong)
          }
        """))
      } else if (source =:= shortTpe && target =:= intTpe) {
        Some(c.Expr[Into[Short, Int]](q"""
          new _root_.zio.blocks.schema.Into[Short, Int] {
            def into(input: Short): Either[_root_.zio.blocks.schema.SchemaError, Int] = 
              Right(input.toInt)
          }
        """))
      } else if (source =:= shortTpe && target =:= longTpe) {
        Some(c.Expr[Into[Short, Long]](q"""
          new _root_.zio.blocks.schema.Into[Short, Long] {
            def into(input: Short): Either[_root_.zio.blocks.schema.SchemaError, Long] = 
              Right(input.toLong)
          }
        """))
      } else if (source =:= intTpe && target =:= longTpe) {
        Some(c.Expr[Into[Int, Long]](q"""
          new _root_.zio.blocks.schema.Into[Int, Long] {
            def into(input: Int): Either[_root_.zio.blocks.schema.SchemaError, Long] = 
              Right(input.toLong)
          }
        """))
      } else if (source =:= floatTpe && target =:= doubleTpe) {
        Some(c.Expr[Into[Float, Double]](q"""
          new _root_.zio.blocks.schema.Into[Float, Double] {
            def into(input: Float): Either[_root_.zio.blocks.schema.SchemaError, Double] = 
              Right(input.toDouble)
          }
        """))
      }
      // Narrowing conversions (with validation)
      else if (source =:= longTpe && target =:= intTpe) {
        Some(c.Expr[Into[Long, Int]](q"""
          new _root_.zio.blocks.schema.Into[Long, Int] {
            def into(input: Long): Either[_root_.zio.blocks.schema.SchemaError, Int] = {
              if (input >= Int.MinValue.toLong && input <= Int.MaxValue.toLong) {
                Right(input.toInt)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Long value " + input + " is out of range for Int [" + Int.MinValue + ", " + Int.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= longTpe && target =:= shortTpe) {
        Some(c.Expr[Into[Long, Short]](q"""
          new _root_.zio.blocks.schema.Into[Long, Short] {
            def into(input: Long): Either[_root_.zio.blocks.schema.SchemaError, Short] = {
              if (input >= Short.MinValue.toLong && input <= Short.MaxValue.toLong) {
                Right(input.toShort)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Long value " + input + " is out of range for Short [" + Short.MinValue + ", " + Short.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= longTpe && target =:= byteTpe) {
        Some(c.Expr[Into[Long, Byte]](q"""
          new _root_.zio.blocks.schema.Into[Long, Byte] {
            def into(input: Long): Either[_root_.zio.blocks.schema.SchemaError, Byte] = {
              if (input >= Byte.MinValue.toLong && input <= Byte.MaxValue.toLong) {
                Right(input.toByte)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Long value " + input + " is out of range for Byte [" + Byte.MinValue + ", " + Byte.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= intTpe && target =:= shortTpe) {
        Some(c.Expr[Into[Int, Short]](q"""
          new _root_.zio.blocks.schema.Into[Int, Short] {
            def into(input: Int): Either[_root_.zio.blocks.schema.SchemaError, Short] = {
              if (input >= Short.MinValue.toInt && input <= Short.MaxValue.toInt) {
                Right(input.toShort)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Int value " + input + " is out of range for Short [" + Short.MinValue + ", " + Short.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= intTpe && target =:= byteTpe) {
        Some(c.Expr[Into[Int, Byte]](q"""
          new _root_.zio.blocks.schema.Into[Int, Byte] {
            def into(input: Int): Either[_root_.zio.blocks.schema.SchemaError, Byte] = {
              if (input >= Byte.MinValue.toInt && input <= Byte.MaxValue.toInt) {
                Right(input.toByte)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Int value " + input + " is out of range for Byte [" + Byte.MinValue + ", " + Byte.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= shortTpe && target =:= byteTpe) {
        Some(c.Expr[Into[Short, Byte]](q"""
          new _root_.zio.blocks.schema.Into[Short, Byte] {
            def into(input: Short): Either[_root_.zio.blocks.schema.SchemaError, Byte] = {
              if (input >= Byte.MinValue.toShort && input <= Byte.MaxValue.toShort) {
                Right(input.toByte)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Short value " + input + " is out of range for Byte [" + Byte.MinValue + ", " + Byte.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else if (source =:= doubleTpe && target =:= floatTpe) {
        Some(c.Expr[Into[Double, Float]](q"""
          new _root_.zio.blocks.schema.Into[Double, Float] {
            def into(input: Double): Either[_root_.zio.blocks.schema.SchemaError, Float] = {
              if (input.isNaN || input.isInfinite) {
                Right(input.toFloat)
              } else if (input >= -Float.MaxValue.toDouble && input <= Float.MaxValue.toDouble) {
                Right(input.toFloat)
              } else {
                Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Double value " + input + " is out of range for Float [" + (-Float.MaxValue) + ", " + Float.MaxValue + "]"
                ))
              }
            }
          }
        """))
      } else {
        None
      }
    }

    // If types are the same, use identity
    if (aType =:= bType) {
      return c.Expr[Into[A, B]](
        q"_root_.zio.blocks.schema.Into.identity[$aType].asInstanceOf[_root_.zio.blocks.schema.Into[$aType, $bType]]"
      )
    }

    // Try numeric coercion
    numericCoercion(aType, bType) match {
      case Some(expr) => return expr.asInstanceOf[c.Expr[Into[A, B]]]
      case None       => ()
    }

    // Try collection conversion
    if (aType <:< typeOf[Option[_]] && bType <:< typeOf[Option[_]]) {
      val sourceElemType = aType.typeArgs.head
      val targetElemType = bType.typeArgs.head

      if (sourceElemType =:= targetElemType) {
        return c.Expr[Into[A, B]](
          q"_root_.zio.blocks.schema.Into.identity[$aType].asInstanceOf[_root_.zio.blocks.schema.Into[$aType, $bType]]"
        )
      } else {
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              input match {
                case None => Right(None)
                case Some(value) =>
                  _root_.zio.blocks.schema.Into.derived[$sourceElemType, $targetElemType]
                    .into(value)
                    .map(v => Some(v))
              }
            }
          }
        """)
      }
    }

    // Try list/vector/set conversions
    val sourceIsIterable = aType <:< typeOf[Iterable[_]]
    val targetIsIterable = bType <:< typeOf[Iterable[_]]

    if (sourceIsIterable && targetIsIterable) {
      val sourceElemType = aType.typeArgs.headOption.getOrElse(typeOf[Any])
      val targetElemType = bType.typeArgs.headOption.getOrElse(typeOf[Any])

      val targetIsList   = bType <:< typeOf[List[_]]
      val targetIsVector = bType <:< typeOf[Vector[_]]
      val targetIsSet    = bType <:< typeOf[Set[_]]

      if (sourceElemType =:= targetElemType) {
        // Same element type - just convert collection structure
        val conversion =
          if (targetIsList) q"coll.toList"
          else if (targetIsVector) q"coll.toVector"
          else if (targetIsSet) q"coll.toSet"
          else q"coll.toSeq"

        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              val coll = input.asInstanceOf[Iterable[Any]]
              Right($conversion.asInstanceOf[$bType])
            }
          }
        """)
      } else {
        // Need element conversion
        val conversion =
          if (targetIsList) q"reversed"
          else if (targetIsVector) q"reversed.toVector"
          else if (targetIsSet) q"reversed.toSet"
          else q"reversed.toSeq"

        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              val coll = input.asInstanceOf[Iterable[$sourceElemType]]
              val elemInto = _root_.zio.blocks.schema.Into.derived[$sourceElemType, $targetElemType]
              
              val converted = coll.foldLeft[Either[_root_.zio.blocks.schema.SchemaError, List[$targetElemType]]](Right(Nil)) {
                case (Right(acc), elem) =>
                  elemInto.into(elem) match {
                    case Right(converted) => Right(converted :: acc)
                    case Left(err) => Left(err)
                  }
                case (left @ Left(_), _) => left
              }
              
              converted.map { list =>
                val reversed = list.reverse
                $conversion.asInstanceOf[$bType]
              }
            }
          }
        """)
      }
    }

    // Try Map conversion
    if (aType <:< typeOf[Map[_, _]] && bType <:< typeOf[Map[_, _]]) {
      val sourceKeyType   = aType.typeArgs(0)
      val sourceValueType = aType.typeArgs(1)
      val targetKeyType   = bType.typeArgs(0)
      val targetValueType = bType.typeArgs(1)

      val keySame   = sourceKeyType =:= targetKeyType
      val valueSame = sourceValueType =:= targetValueType

      if (keySame && valueSame) {
        return c.Expr[Into[A, B]](
          q"_root_.zio.blocks.schema.Into.identity[$aType].asInstanceOf[_root_.zio.blocks.schema.Into[$aType, $bType]]"
        )
      } else {
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              val map = input.asInstanceOf[Map[$sourceKeyType, $sourceValueType]]
              
              map.iterator.foldLeft[Either[_root_.zio.blocks.schema.SchemaError, Map[$targetKeyType, $targetValueType]]](Right(Map.empty)) {
                case (Right(acc), (k, v)) =>
                  for {
                    convertedKey <- ${
            if (keySame) q"Right(k)"
            else q"_root_.zio.blocks.schema.Into.derived[$sourceKeyType, $targetKeyType].into(k)"
          }
                    convertedValue <- ${
            if (valueSame) q"Right(v)"
            else q"_root_.zio.blocks.schema.Into.derived[$sourceValueType, $targetValueType].into(v)"
          }
                  } yield acc + (convertedKey -> convertedValue)
                case (left @ Left(_), _) => left
              }.asInstanceOf[Either[_root_.zio.blocks.schema.SchemaError, $bType]]
            }
          }
        """)
      }
    }

    // Try Either conversion
    if (aType <:< typeOf[Either[_, _]] && bType <:< typeOf[Either[_, _]]) {
      val sourceLeftType  = aType.typeArgs(0)
      val sourceRightType = aType.typeArgs(1)
      val targetLeftType  = bType.typeArgs(0)
      val targetRightType = bType.typeArgs(1)

      val leftSame  = sourceLeftType =:= targetLeftType
      val rightSame = sourceRightType =:= targetRightType

      if (leftSame && rightSame) {
        return c.Expr[Into[A, B]](
          q"_root_.zio.blocks.schema.Into.identity[$aType].asInstanceOf[_root_.zio.blocks.schema.Into[$aType, $bType]]"
        )
      } else {
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              input.asInstanceOf[Either[$sourceLeftType, $sourceRightType]] match {
                case Left(left) =>
                  ${
            if (leftSame) q"Right(Left(left))"
            else q"_root_.zio.blocks.schema.Into.derived[$sourceLeftType, $targetLeftType].into(left).map(l => Left(l))"
          }.asInstanceOf[Either[_root_.zio.blocks.schema.SchemaError, $bType]]
                case Right(right) =>
                  ${
            if (rightSame) q"Right(Right(right))"
            else
              q"_root_.zio.blocks.schema.Into.derived[$sourceRightType, $targetRightType].into(right).map(r => Right(r))"
          }.asInstanceOf[Either[_root_.zio.blocks.schema.SchemaError, $bType]]
              }
            }
          }
        """)
      }
    }

    // Try tuple to case class conversion
    val isSourceTuple             = aType <:< typeOf[Product] && aType.typeSymbol.name.toString.startsWith("Tuple")
    val isTargetCaseClassForTuple = bType.typeSymbol.isClass && bType.typeSymbol.asClass.isCaseClass

    if (isSourceTuple && isTargetCaseClassForTuple) {
      // Extract tuple elements and target case class fields
      val tupleArity =
        try {
          // Try to get arity from type name (Tuple2, Tuple3, etc.)
          val typeName = aType.typeSymbol.name.toString
          if (typeName.startsWith("Tuple")) {
            typeName.substring(5).toInt
          } else {
            // Fallback: count type arguments
            aType.typeArgs.length
          }
        } catch {
          case _: Throwable => aType.typeArgs.length
        }

      val targetParams = bType.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.toList

      if (tupleArity == targetParams.size) {
        // Extract tuple elements using ._1, ._2, etc.
        val tupleElements = (1 to tupleArity).map { i =>
          val methodName = TermName(s"_$i")
          q"input.$methodName"
        }.toList

        // Generate field conversions with recursive derivation when types don't match
        val fieldDefs   = scala.collection.mutable.ListBuffer[c.universe.Tree]()
        val fieldValues = targetParams.zipWithIndex.map { case (targetParam, idx) =>
          val tupleElement     = tupleElements(idx)
          val tupleElementType = aType.typeArgs(idx)
          val targetFieldType  = targetParam.returnType

          // If types match, use direct access, otherwise use recursive derivation
          if (tupleElementType =:= targetFieldType || tupleElementType.dealias =:= targetFieldType.dealias) {
            tupleElement
          } else {
            // Use recursive derivation for field conversion
            val fieldName = TermName(s"convertedField$idx")
            fieldDefs += q"""
              val $fieldName = _root_.zio.blocks.schema.Into.derived[$tupleElementType, $targetFieldType]
                .into($tupleElement) match {
                  case Right(value) => value
                  case Left(err) => return Left(err)
                }
            """
            q"$fieldName"
          }
        }

        val targetCompanion = bType.typeSymbol.companion
        val companionTree   = Ident(targetCompanion)
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              ..${fieldDefs.toList}
              Right($companionTree.apply(..$fieldValues))
            }
          }
        """)
      }
    }

    // Try ZIO Prelude Newtype/Subtype conversion FIRST (before case class conversion)
    // This prevents infinite loops when case classes contain newtype fields
    newtypeConversion(c)(aType, bType) match {
      case Some(expr) => return expr.asInstanceOf[c.Expr[Into[A, B]]]
      case None       => ()
    }

    // Try product type conversion (case class)
    val isSourceCaseClassForProduct = aType.typeSymbol.isClass && aType.typeSymbol.asClass.isCaseClass
    val isTargetCaseClassForProduct = bType.typeSymbol.isClass && bType.typeSymbol.asClass.isCaseClass

    if (isSourceCaseClassForProduct && isTargetCaseClassForProduct) {
      val sourceParams = aType.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.toList

      val targetParams = bType.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.toList

      if (sourceParams.size >= targetParams.size) {
        // Generate field conversions with recursive derivation when types don't match
        val fieldDefs   = scala.collection.mutable.ListBuffer[c.universe.Tree]()
        val fieldValues = targetParams.zipWithIndex.map { case (targetParam, idx) =>
          if (idx < sourceParams.size) {
            val sourceParam     = sourceParams(idx)
            val sourceFieldType = sourceParam.returnType
            val targetFieldType = targetParam.returnType

            // If types match, use direct access, otherwise use recursive derivation
            if (sourceFieldType =:= targetFieldType || sourceFieldType.dealias =:= targetFieldType.dealias) {
              q"input.${sourceParam.name.toTermName}"
            } else {
              // Use recursive derivation for field conversion
              val fieldName = TermName(s"convertedField$idx")
              fieldDefs += q"""
                val $fieldName = _root_.zio.blocks.schema.Into.derived[$sourceFieldType, $targetFieldType]
                  .into(input.${sourceParam.name.toTermName}) match {
                    case Right(value) => value
                    case Left(err) => return Left(err)
                  }
              """
              q"$fieldName"
            }
          } else {
            fail(s"Not enough source fields for target field ${targetParam.name}")
          }
        }

        val targetCompanion = bType.typeSymbol.companion
        val companionTree   = Ident(targetCompanion)
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              ..${fieldDefs.toList}
              Right($companionTree.apply(..$fieldValues))
            }
          }
        """)
      }
    }

    // Try coproduct type conversion (sealed trait)
    val isSourceSealed = aType.typeSymbol.isClass && aType.typeSymbol.asClass.isSealed
    val isTargetSealed = bType.typeSymbol.isClass && bType.typeSymbol.asClass.isSealed

    if (isSourceSealed && isTargetSealed) {
      val sourceCases =
        aType.typeSymbol.asClass.knownDirectSubclasses.toList.map(_.asType.toType.asSeenFrom(aType, aType.typeSymbol))
      val targetCases =
        bType.typeSymbol.asClass.knownDirectSubclasses.toList.map(_.asType.toType.asSeenFrom(bType, bType.typeSymbol))

      val cases = sourceCases.flatMap { sourceCase =>
        val sourceName = sourceCase.typeSymbol.name.toString.stripSuffix("$")
        targetCases.find(tc => tc.typeSymbol.name.toString.stripSuffix("$") == sourceName).map { targetCase =>
          val isSourceModule = sourceCase.typeSymbol.isModuleClass
          val isTargetModule = targetCase.typeSymbol.isModuleClass

          if (isSourceModule && isTargetModule) {
            cq"_: $sourceCase => Right(${targetCase.termSymbol})"
          } else {
            cq"""x: $sourceCase => 
              _root_.zio.blocks.schema.Into.derived[$sourceCase, $targetCase]
                .into(x)
                .map(_.asInstanceOf[$bType])
            """
          }
        }
      }

      if (cases.nonEmpty) {
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              (input: @unchecked) match {
                case ..$cases
                case _ => Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Unexpected case in sealed trait conversion"
                ))
              }
            }
          }
        """)
      }
    }

    // Try structural type conversion (Scala 2 - enhanced support)
    // Note: Scala 2 structural types use RefinedType and require runtime reflection
    val isSourceCaseClass  = aType.typeSymbol.isClass && aType.typeSymbol.asClass.isCaseClass
    val isSourceStructural = isStructuralType(c)(aType)
    val isTargetStructural = isStructuralType(c)(bType)
    val isTargetCaseClass  = bType.typeSymbol.isClass && bType.typeSymbol.asClass.isCaseClass

    // Case 1: Case class → Structural type
    if (isSourceCaseClass && isTargetStructural) {
      return generateCaseClassToStructural(c)(aType, bType).asInstanceOf[c.Expr[Into[A, B]]]
    }

    // Case 2: Structural type → Case class
    if (isSourceStructural && isTargetCaseClass) {
      return generateStructuralToCaseClass(c)(aType, bType).asInstanceOf[c.Expr[Into[A, B]]]
    }

    fail(s"Cannot derive Into[$aType, $bType]. No conversion available between these types.")
  }

  /**
   * Generate conversion from case class to structural type with validation.
   */
  private def generateCaseClassToStructural(c: blackbox.Context)(aType: c.Type, bType: c.Type): c.Expr[Into[_, _]] = {
    import c.universe._

    // Extract required methods from structural type
    val requiredMethods = extractStructuralMethods(c)(bType)

    // Extract source fields from case class
    val sourceFields = extractCaseClassFields(c)(aType)

    // Validate all required methods exist
    val missingMethods = requiredMethods.filter { methodName =>
      !sourceFields.exists(_.name.toString == methodName)
    }

    if (missingMethods.nonEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Cannot convert ${aType} to structural type ${bType}. " +
          s"Missing required methods: ${missingMethods.mkString(", ")}"
      )
    }

    // Generate code with validation
    c.Expr[Into[_, _]](q"""
      new _root_.zio.blocks.schema.Into[$aType, $bType] {
        def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
          try {
            // In Scala 2, structural types work via reflection at runtime
            // The case class already implements the required methods, so we can cast
            Right(input.asInstanceOf[$bType])
          } catch {
            case e: ClassCastException =>
              Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                Nil,
                "Failed to convert to structural type: " + e.getMessage
              ))
            case e: Exception =>
              Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                Nil,
                "Unexpected error during structural type conversion: " + e.getMessage
              ))
          }
        }
      }
    """)
  }

  /**
   * Generate conversion from structural type to case class.
   */
  private def generateStructuralToCaseClass(c: blackbox.Context)(aType: c.Type, bType: c.Type): c.Expr[Into[_, _]] = {
    import c.universe._

    // Extract target case class fields
    val targetFields = extractCaseClassFields(c)(bType)

    // Extract available methods from structural type
    val availableMethods = extractStructuralMethods(c)(aType)

    // Validate all target fields have corresponding methods
    val missingFields = targetFields.filter { field =>
      !availableMethods.contains(field.name.toString)
    }

    if (missingFields.nonEmpty) {
      val missingNames = missingFields.map(_.name.toString).mkString(", ")
      c.abort(
        c.enclosingPosition,
        s"Cannot convert structural type ${aType} to ${bType}. " +
          s"Missing required methods: $missingNames"
      )
    }

    // Get companion object for case class construction
    val targetClass = bType.typeSymbol.asClass
    val companion   = targetClass.companion

    // Generate code using runtime reflection to extract values and construct case class
    val fieldExtractions = targetFields.zipWithIndex.map { case (field, idx) =>
      val fieldName        = field.name.toString
      val fieldNameLiteral = Literal(Constant(fieldName))
      q"""
        val ${TermName(s"field$idx")} = try {
          input.getClass.getMethod($fieldNameLiteral).invoke(input).asInstanceOf[${field.returnType}]
        } catch {
          case e: Exception =>
            return Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
              Nil,
              "Failed to extract field '" + $fieldNameLiteral + "' from structural type: " + e.getMessage
            ))
        }
      """
    }

    val fieldValues = targetFields.indices.map(idx => q"${TermName(s"field$idx")}")

    c.Expr[Into[_, _]](q"""
      new _root_.zio.blocks.schema.Into[$aType, $bType] {
        def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
          try {
            ..$fieldExtractions
            val result = $companion.apply(..$fieldValues)
            Right(result)
          } catch {
            case e: Exception =>
              Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(
                Nil,
                "Failed to convert structural type to " + ${bType.toString} + ": " + e.getMessage
              ))
          }
        }
      }
    """)
  }

  /**
   * Extract method names from a structural type (refinement type).
   */
  private def extractStructuralMethods(c: blackbox.Context)(tpe: c.Type): List[String] = {
    import c.universe._

    def extractFromType(acc: List[String], current: Type): List[String] =
      current match {
        case RefinedType(parents, decls) =>
          val methodNames = decls.collect {
            case m: MethodSymbol if m.isMethod && m.paramLists.isEmpty =>
              m.name.toString
          }.toList
          // Process all parent types and accumulate methods
          parents.foldLeft(methodNames ::: acc) { (acc2, parent) =>
            extractFromType(acc2, parent)
          }
        case _ =>
          acc
      }

    extractFromType(Nil, tpe).distinct
  }

  /**
   * Extract field information from a case class.
   */
  private def extractCaseClassFields(c: blackbox.Context)(tpe: c.Type): List[c.universe.MethodSymbol] = {
    val classSymbol = tpe.typeSymbol.asClass
    if (!classSymbol.isCaseClass) {
      return Nil
    }

    val constructor = classSymbol.primaryConstructor.asMethod
    constructor.paramLists.flatten.map { param =>
      // Get the accessor method for this field
      val accessorName = param.name
      val accessor     = tpe.member(accessorName).asMethod
      accessor
    }
  }

  /**
   * Check if a type is a structural type (refinement type) in Scala 2.
   * Structural types in Scala 2 are identified by refinement types.
   */
  private def isStructuralType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._

    // Check if type is a refinement type (structural type)
    // In Scala 2, structural types are represented as refinement types
    def isRefinementType(t: Type): Boolean = t match {
      case RefinedType(_, _) => true
      case _                 => false
    }

    // Check if type has Dynamic marker (some structural types use this)
    val hasDynamic = tpe <:< typeOf[scala.Dynamic]

    isRefinementType(tpe) || hasDynamic
  }

  /**
   * OPTIMIZED: Fast pattern matching for ZIO Prelude Newtype without heavy
   * baseClasses traversal. Pattern: TypeRef(companion, "Type", Nil) where
   * companion is a module. Also handles type aliases: type UserId = UserId.Type
   */
  private def isZioPreludeNewtype(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    // First dealias to handle type aliases like "type UserId = UserId.Type"
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(qualifier, typeSym, Nil) if typeSym.name.toString == "Type" =>
        // Lightweight check: verify companion exists and has expected structure
        qualifier match {
          case SingleType(_, termSym) if termSym.isModule =>
            // Only check baseClasses if we really need to (lazy evaluation)
            try {
              termSym.typeSignature.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
            } catch {
              case _: Throwable => false
            }
          case _ => false
        }
      case _ => false
    }
  }

  /**
   * OPTIMIZED: Fast pattern matching for ZIO Prelude Subtype. Also handles type
   * aliases: type Salary = Salary.Type
   */
  private def isZioPreludeSubtype(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    // First dealias to handle type aliases
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(qualifier, typeSym, Nil) if typeSym.name.toString == "Type" =>
        qualifier match {
          case SingleType(_, termSym) if termSym.isModule =>
            try {
              termSym.typeSignature.baseClasses.exists(_.fullName == "zio.prelude.Subtype")
            } catch {
              case _: Throwable => false
            }
          case _ => false
        }
      case _ => false
    }
  }

  /**
   * OPTIMIZED: Extract underlying type with minimal overhead. Tries to get type
   * parameter directly from companion's type signature first, avoiding
   * expensive baseType operations when possible. Also handles type aliases:
   * type UserId = UserId.Type
   */
  private def getNewtypeUnderlying(c: blackbox.Context)(tpe: c.Type): Option[c.Type] = {
    import c.universe._
    // First dealias to handle type aliases
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(qualifier, _, _) =>
        qualifier match {
          case SingleType(_, termSym) if termSym.isModule =>
            try {
              val moduleType = termSym.typeSignature
              // First try: look for type parameters directly in the type signature
              moduleType match {
                case TypeRef(_, _, typeArgs) if typeArgs.nonEmpty =>
                  Some(typeArgs.head.dealias)
                case _ =>
                  // Fallback: try to find in base types (only if necessary)
                  val baseTypeOpt = moduleType.baseClasses.collectFirst {
                    case cls if cls.fullName.contains("Newtype") || cls.fullName.contains("Subtype") =>
                      try {
                        Some(moduleType.baseType(cls).typeArgs.head.dealias)
                      } catch {
                        case _: Throwable => None
                      }
                  }
                  baseTypeOpt.flatten
              }
            } catch {
              case _: Throwable => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * OPTIMIZED: Get companion with minimal overhead. Also handles type aliases:
   * type UserId = UserId.Type
   */
  private def getCompanionModule(c: blackbox.Context)(tpe: c.Type): Option[c.Symbol] = {
    import c.universe._
    // First dealias to handle type aliases
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(qualifier, _, _) =>
        qualifier match {
          case SingleType(_, termSym) if termSym.isModule => Some(termSym)
          case _                                          => None
        }
      case _ => None
    }
  }

  /**
   * OPTIMIZED: Type comparison with better dealias and widen handling. This is
   * more robust for type aliases and type parameters.
   */
  private def typesMatch(c: blackbox.Context)(source: c.Type, target: c.Type): Boolean = {
    def normalize(t: c.Type): c.Type = t.dealias.widen

    normalize(source) =:= normalize(target) ||
    source =:= target ||
    source.dealias =:= target.dealias ||
    source.widen =:= target.widen
  }

  /**
   * LIGHTWEIGHT version of newtypeConversion for Scala 2.
   *
   * Key optimizations:
   *   1. Minimal AST generation - direct method calls instead of runtime
   *      reflection
   *   2. Compile-time method resolution when possible
   *   3. Simple fallback chain: make -> apply -> direct cast
   *   4. Avoid heavy baseClasses/baseType operations
   */
  private def newtypeConversion(c: blackbox.Context)(aType: c.Type, bType: c.Type): Option[c.Expr[Into[_, _]]] = {
    import c.universe._

    val isSourceNewtype = isZioPreludeNewtype(c)(aType)
    val isTargetNewtype = isZioPreludeNewtype(c)(bType) || isZioPreludeSubtype(c)(bType)
    val isSourceSubtype = isZioPreludeSubtype(c)(aType)
    val isTargetSubtype = isZioPreludeSubtype(c)(bType)

    // Case 1: Source is underlying type, Target is Newtype/Subtype (wrap)
    if (!isSourceNewtype && !isSourceSubtype && isTargetNewtype) {
      val underlyingOpt = getNewtypeUnderlying(c)(bType)
      val companionOpt  = getCompanionModule(c)(bType)

      (underlyingOpt, companionOpt) match {
        case (Some(underlying), Some(companion)) if typesMatch(c)(aType, underlying) =>
          // LIGHTWEIGHT: Generate minimal AST with compile-time method selection
          // Strategy: Try make first, then apply, then direct cast
          // This generates ~10-20 lines instead of 70+ lines

          val companionTree = Ident(companion)

          // Try to find 'make' method at compile time
          val makeMethodOpt =
            try {
              companion.typeSignature.member(TermName("make")) match {
                case NoSymbol            => None
                case sym if sym.isMethod =>
                  val method = sym.asMethod
                  // Check if it takes one parameter matching our source type
                  method.paramLists match {
                    case List(List(param)) if typesMatch(c)(param.typeSignature, aType) => Some(sym)
                    case _                                                              => None
                  }
                case _ => None
              }
            } catch {
              case _: Throwable => None
            }

          makeMethodOpt match {
            case Some(_) =>
              // OPTIMIZED: Direct method call - minimal AST
              // Note: make() returns ZValidation, not Either, so we handle Validation first
              Some(c.Expr[Into[_, _]](q"""
                new _root_.zio.blocks.schema.Into[$aType, $bType] {
                  def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
                    try {
                      val result = $companionTree.make(input)
                      // Handle Validation (ZValidation) - this is what make() returns
                      // Use toEither() method which is available on Validation types
                      val either = result.toEither.asInstanceOf[Either[Any, $bType]]
                      either.fold(
                        errs => Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(Nil, errs.toString + " (input: " + input + ")")),
                        Right(_)
                      )
                    } catch {
                      case _: Throwable => Right(input.asInstanceOf[$bType])
                    }
                  }
                }
              """))

            case None =>
              // Try 'validate' method (custom validation, alternative to make)
              val validateMethodOpt =
                try {
                  companion.typeSignature.member(TermName("validate")) match {
                    case NoSymbol            => None
                    case sym if sym.isMethod =>
                      val method = sym.asMethod
                      // Check if it takes one parameter matching our source type
                      method.paramLists match {
                        case List(List(param)) if typesMatch(c)(param.typeSignature, aType) => Some(sym)
                        case _                                                              => None
                      }
                    case _ => None
                  }
                } catch {
                  case _: Throwable => None
                }

              validateMethodOpt match {
                case Some(_) =>
                  // Use validate() method for validation
                  Some(c.Expr[Into[_, _]](q"""
                    new _root_.zio.blocks.schema.Into[$aType, $bType] {
                      def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
                        try {
                          val result = $companionTree.validate(input)
                          // Handle Validation (ZValidation) - this is what validate() returns
                          val either = result.toEither.asInstanceOf[Either[Any, $bType]]
                          either.fold(
                            errs => {
                              // Extract error message from NonEmptyChunk if present
                              val errorMsg = try {
                                // Try to get first element if errs is a NonEmptyChunk
                                if (errs.getClass.getName.contains("NonEmptyChunk")) {
                                  val headMethod = errs.getClass.getMethod("head")
                                  headMethod.invoke(errs).toString
                                } else {
                                  errs.toString
                                }
                              } catch {
                                case _: Throwable => errs.toString
                              }
                              Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(Nil, errorMsg))
                            },
                            Right(_)
                          )
                        } catch {
                          case _: Throwable => Right(input.asInstanceOf[$bType])
                        }
                      }
                    }
                  """))

                case None =>
                  // Try 'apply' method
                  val applyMethodOpt =
                    try {
                      companion.typeSignature.member(TermName("apply")) match {
                        case NoSymbol            => None
                        case sym if sym.isMethod =>
                          val method = sym.asMethod
                          method.paramLists match {
                            case List(List(param)) if typesMatch(c)(param.typeSignature, aType) => Some(sym)
                            case _                                                              => None
                          }
                        case _ => None
                      }
                    } catch {
                      case _: Throwable => None
                    }

                  applyMethodOpt match {
                    case Some(_) =>
                      // OPTIMIZED: Direct apply call
                      // apply() might return Validation or direct value
                      Some(c.Expr[Into[_, _]](q"""
                    new _root_.zio.blocks.schema.Into[$aType, $bType] {
                      def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
                        try {
                          val result = $companionTree.apply(input)
                          // Check if result has toEither method (Validation)
                          if (result.getClass.getMethods.exists(_.getName == "toEither")) {
                            val either = result.getClass.getMethod("toEither").invoke(result).asInstanceOf[Either[Any, $bType]]
                            either.fold(
                              errs => Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(Nil, errs.toString + " (input: " + input + ")")),
                              Right(_)
                            )
                          } else {
                            // Direct value or Either
                            result match {
                              case e: Either[_, _] =>
                                e.asInstanceOf[Either[String, $bType]].fold(
                                  err => Left(_root_.zio.blocks.schema.SchemaError.expectationMismatch(Nil, err + " (input: " + input + ")")),
                                  Right(_)
                                )
                              case _ => Right(result.asInstanceOf[$bType])
                            }
                          }
                        } catch {
                          case _: Throwable => Right(input.asInstanceOf[$bType])
                        }
                      }
                    }
                  """))

                    case None =>
                      // ULTRA-LIGHTWEIGHT: Direct cast fallback (most newtypes support this)
                      Some(c.Expr[Into[_, _]](q"""
                    new _root_.zio.blocks.schema.Into[$aType, $bType] {
                      def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] =
                        Right(input.asInstanceOf[$bType])
                    }
                  """))
                  }
              }
          }

        case _ => None
      }
    }
    // Case 2: Source is Newtype/Subtype, Target is underlying type (unwrap)
    else if ((isSourceNewtype || isSourceSubtype) && !isTargetNewtype && !isTargetSubtype) {
      val underlyingOpt = getNewtypeUnderlying(c)(aType)

      underlyingOpt match {
        case Some(underlying) if typesMatch(c)(bType, underlying) =>
          // ULTRA-LIGHTWEIGHT: Direct cast
          Some(c.Expr[Into[_, _]](q"""
            new _root_.zio.blocks.schema.Into[$aType, $bType] {
              def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] =
                Right(input.asInstanceOf[$bType])
            }
          """))
        case _ => None
      }
    }
    // Case 3: Both are Newtypes with same underlying type
    else if ((isSourceNewtype || isSourceSubtype) && isTargetNewtype) {
      val sourceUnderlying = getNewtypeUnderlying(c)(aType)
      val targetUnderlying = getNewtypeUnderlying(c)(bType)

      (sourceUnderlying, targetUnderlying) match {
        case (Some(su), Some(tu)) if typesMatch(c)(su, tu) =>
          // ULTRA-LIGHTWEIGHT: Direct cast
          Some(c.Expr[Into[_, _]](q"""
            new _root_.zio.blocks.schema.Into[$aType, $bType] {
              def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] =
                Right(input.asInstanceOf[$bType])
            }
          """))
        case _ => None
      }
    } else {
      None
    }
  }
}
