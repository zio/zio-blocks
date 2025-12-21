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
              
              map.foldLeft[Either[_root_.zio.blocks.schema.SchemaError, Map[$targetKeyType, $targetValueType]]](Right(Map.empty)) {
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
        // Simple positional mapping for now
        val fieldAssignments = targetParams.zipWithIndex.map { case (targetParam, idx) =>
          if (idx < sourceParams.size) {
            val sourceParam = sourceParams(idx)
            q"input.${sourceParam.name.toTermName}"
          } else {
            fail(s"Not enough source fields for target field ${targetParam.name}")
          }
        }

        val targetCompanion = bType.typeSymbol.companion
        return c.Expr[Into[A, B]](q"""
          new _root_.zio.blocks.schema.Into[$aType, $bType] {
            def into(input: $aType): Either[_root_.zio.blocks.schema.SchemaError, $bType] = {
              Right($targetCompanion(..$fieldAssignments))
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
    import c.universe._

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
}
