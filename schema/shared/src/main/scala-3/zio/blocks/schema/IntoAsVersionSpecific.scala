package zio.blocks.schema

import scala.quoted.*
import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

trait IntoAsVersionSpecific {
  inline def derived[A, B]: Into[A, B]     = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
  inline def derivedInto[A, B]: Into[A, B] = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
  inline def derivedAs[A, B]: As[A, B]     = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
}

object IntoAsVersionSpecificImpl {

  private class DerivationContext {
    val inProgress = scala.collection.mutable.Map[Any, Any]()
    val lazyDefs   = scala.collection.mutable.ListBuffer[Any]()
  }

  def derivedAsImpl[A: Type, B: Type](using q: Quotes): Expr[As[A, B]] = {
    import q.reflect.*

    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    // Check for default values (breaks round-trip guarantee)
    val aDefaults = getFieldsWithDefaultValues(using q)(aTpe)
    val bDefaults = getFieldsWithDefaultValues(using q)(bTpe)

    if (aDefaults.nonEmpty || bDefaults.nonEmpty) {
      val msgParts = List(
        if (aDefaults.nonEmpty) Some(s"${aTpe.show} has default values: ${aDefaults.mkString(", ")}")
        else None,
        if (bDefaults.nonEmpty) Some(s"${bTpe.show} has default values: ${bDefaults.mkString(", ")}")
        else None
      ).flatten

      val msg =
        s"Cannot derive As[${aTpe.show}, ${bTpe.show}]: Default values break round-trip guarantee. Fields with defaults: ${msgParts.mkString("; ")}"
      report.errorAndAbort(msg)
    }

    val intoAB = derivedIntoImpl[A, B]
    val intoBA = derivedIntoImpl[B, A]
    '{
      new As[A, B] {
        def into(a: A): Either[SchemaError, B] = $intoAB.into(a)
        def from(b: B): Either[SchemaError, A] = $intoBA.into(b)
      }
    }
  }

  def derivedIntoImpl[A: Type, B: Type](using q: Quotes): Expr[Into[A, B]] = {
    import q.reflect.*

    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    // 1. Identity Check
    if (aTpe =:= bTpe) {
      return '{
        new Into[A, B] {
          def into(a: A): Either[SchemaError, B] = Right(a.asInstanceOf[B])
        }
      }
    }

    // Create derivation context for cycle detection (early, needed for opaque types too)
    val ctx = DerivationContext()

    // 2. Opaque Types Check (BEFORE dealias)
    // Check if B is opaque type (UnderlyingType -> OpaqueType)
    if (isOpaqueType(using q)(bTpe)) {
      val result = generateOpaqueValidation[A, B](using q)(ctx, aTpe, bTpe)
      // Wrap in Block if needed
      if (ctx.lazyDefs.nonEmpty) {
        import q.reflect.*
        val lazyDefsTyped = ctx.lazyDefs.toList.map(_.asInstanceOf[ValDef])
        return Block(lazyDefsTyped, result.asTerm).asExprOf[Into[A, B]]
      } else {
        return result
      }
    }

    // Check if A is opaque type (OpaqueType -> UnderlyingType)
    if (isOpaqueType(using q)(aTpe)) {
      return generateOpaqueToUnderlying[A, B](using q)(aTpe, bTpe)
    }

    // PRIORITY 0.73: ZIO Prelude Newtypes (BEFORE dealias to preserve type structure)
    generateZioPreludeNewtypeConversion[A, B](using q)(ctx, aTpe, bTpe) match {
      case Some(impl) => return impl
      case None       => // continue
    }

    // Dealias for other checks
    val aTpeDealiased = aTpe.dealias
    val bTpeDealiased = bTpe.dealias

    // PRIORITY 0: Primitives (Narrowing/Widening)
    derivePrimitiveInto[A, B](using q)(aTpeDealiased, bTpeDealiased) match {
      case Some(impl) => return impl
      case None       => // continue
    }

    // PRIORITY 0.5: Either and Option (explicit handling to avoid GADT constraint issues)
    if (isEitherType(using q)(aTpeDealiased) && isEitherType(using q)(bTpeDealiased)) {
      return deriveEitherInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased)
    }

    if (isOptionType(using q)(aTpeDealiased) && isOptionType(using q)(bTpeDealiased)) {
      return deriveOptionInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased)
    }

    // PRIORITY 0.75: Maps (before Collections, since Map has 2 type parameters)
    (extractMapTypes(using q)(aTpeDealiased), extractMapTypes(using q)(bTpeDealiased)) match {
      case (Some((aKey, aValue)), Some((bKey, bValue))) =>
        aKey.asType match {
          case '[ak] =>
            bKey.asType match {
              case '[bk] =>
                aValue.asType match {
                  case '[av] =>
                    bValue.asType match {
                      case '[bv] =>
                        val result =
                          deriveMapInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased, aKey, aValue, bKey, bValue)
                        return result
                    }
                }
            }
        }
      case _ => // continue
    }

    // PRIORITY 1: Collections
    (extractCollectionElementType(using q)(aTpeDealiased), extractCollectionElementType(using q)(bTpeDealiased)) match {
      case (Some(aElem), Some(bElem)) =>
        // FIX: deriveCollectionInto expects collection types [A, B], not element types
        // We need to call it with the actual collection types
        aElem.asType match {
          case '[ae] =>
            bElem.asType match {
              case '[be] =>
                // deriveCollectionInto[A, B] expects A and B to be collection types
                // So we pass the collection types, not element types
                val result = deriveCollectionInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased, aElem, bElem)
                return result
            }
        }
      case _ => // continue
    }

    // Fallback to general recursive derivation
    val result = findOrDeriveInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased)

    // If we have lazy definitions, wrap in Block
    if (ctx.lazyDefs.nonEmpty) {
      import q.reflect.*
      val lazyDefsTyped = ctx.lazyDefs.toList.map(_.asInstanceOf[ValDef])
      Block(lazyDefsTyped, result.asTerm).asExprOf[Into[A, B]]
    } else {
      result
    }
  }

  // -- START OF HELPERS --

  // --- Runtime Helpers ---

  private def fail(msg: String)(using q: Quotes): Nothing = {
    import q.reflect.*
    report.errorAndAbort(msg)
  }

  def sequenceEither[A, B](list: List[Either[A, B]]): Either[A, List[B]] = {
    val acc = new scala.collection.mutable.ListBuffer[B]
    val i   = list.iterator
    while (i.hasNext) {
      i.next() match {
        case Right(b) => acc += b
        case Left(a)  => return Left(a)
      }
    }
    Right(acc.toList)
  }

  def mapAndSequence[A, B](source: Iterable[A], f: A => Either[SchemaError, B]): Either[SchemaError, List[B]] = {
    val acc = new scala.collection.mutable.ListBuffer[B]
    val i   = source.iterator
    while (i.hasNext) {
      f(i.next()) match {
        case Right(b) => acc += b
        case Left(e)  => return Left(e)
      }
    }
    Right(acc.toList)
  }

  def getTupleElement[A](product: Product, index: Int): A =
    product.productElement(index).asInstanceOf[A]

  def emptyNodeList: List[zio.blocks.schema.DynamicOptic.Node] = Nil

  // --- Primitive Derivation ---

  private def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr, primitive: q.reflect.TypeRepr): Boolean =
    tpe.dealias =:= primitive.dealias

  // Helper to check if a type is any primitive type
  private def isPrimitive(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    val dealiased   = tpe.dealias
    val intType     = TypeRepr.of[Int]
    val longType    = TypeRepr.of[Long]
    val doubleType  = TypeRepr.of[Double]
    val floatType   = TypeRepr.of[Float]
    val booleanType = TypeRepr.of[Boolean]
    val byteType    = TypeRepr.of[Byte]
    val shortType   = TypeRepr.of[Short]
    val charType    = TypeRepr.of[Char]
    val stringType  = TypeRepr.of[String]

    isPrimitiveType(using q)(dealiased, intType) ||
    isPrimitiveType(using q)(dealiased, longType) ||
    isPrimitiveType(using q)(dealiased, doubleType) ||
    isPrimitiveType(using q)(dealiased, floatType) ||
    isPrimitiveType(using q)(dealiased, booleanType) ||
    isPrimitiveType(using q)(dealiased, byteType) ||
    isPrimitiveType(using q)(dealiased, shortType) ||
    isPrimitiveType(using q)(dealiased, charType) ||
    isPrimitiveType(using q)(dealiased, stringType)
  }

  private def generatePrimitiveInto[A: Type, B: Type](
    conversion: Expr[A => Either[SchemaError, B]]
  )(using q: Quotes): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $conversion(a)
      }
    }

  // Helper to check if two types are compatible for position-based matching
  // Used in Priority 4: allows exact match or coercible primitives
  private def areTypesCompatibleForPositionMatch(using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Boolean = {
    import q.reflect.*

    val aDealiased = aTpe.dealias
    val bDealiased = bTpe.dealias

    // 1. Exact match
    if (aDealiased =:= bDealiased) return true

    // 2. Check if they are coercible primitives
    val intType    = TypeRepr.of[Int]
    val longType   = TypeRepr.of[Long]
    val doubleType = TypeRepr.of[Double]
    val floatType  = TypeRepr.of[Float]

    // Widening conversions (always safe)
    if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, longType)) return true
    if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, doubleType)) return true
    if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, floatType)) return true
    if (isPrimitiveType(using q)(aDealiased, longType) && isPrimitiveType(using q)(bDealiased, doubleType)) return true
    if (isPrimitiveType(using q)(aDealiased, floatType) && isPrimitiveType(using q)(bDealiased, doubleType)) return true

    // Narrowing conversions (require validation, but still compatible)
    if (isPrimitiveType(using q)(aDealiased, longType) && isPrimitiveType(using q)(bDealiased, intType)) return true
    if (isPrimitiveType(using q)(aDealiased, doubleType) && isPrimitiveType(using q)(bDealiased, floatType)) return true

    // 3. For other types, be permissive: if types are unique in both classes,
    // we can try the match and let findOrDeriveInto handle the coercion
    // (it will fail at compile-time if coercion is impossible)
    // This allows for more complex conversions (e.g., case classes, collections, etc.)
    true
  }

  private def derivePrimitiveInto[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Option[Expr[Into[A, B]]] = {
    import q.reflect.*

    val intType    = TypeRepr.of[Int]
    val longType   = TypeRepr.of[Long]
    val doubleType = TypeRepr.of[Double]
    val floatType  = TypeRepr.of[Float]

    // Widening (Safe)
    if (isPrimitiveType(using q)(aTpe, intType) && isPrimitiveType(using q)(bTpe, longType)) {
      (TypeRepr.of[Int].asType, TypeRepr.of[Long].asType) match {
        case ('[Int], '[Long]) => Some(generatePrimitiveInto[Int, Long]('{ a => Right(a.toLong) }).asExprOf[Into[A, B]])
        case _                 => None
      }
    } else if (isPrimitiveType(using q)(aTpe, intType) && isPrimitiveType(using q)(bTpe, doubleType)) {
      (TypeRepr.of[Int].asType, TypeRepr.of[Double].asType) match {
        case ('[Int], '[Double]) =>
          Some(generatePrimitiveInto[Int, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, intType) && isPrimitiveType(using q)(bTpe, floatType)) {
      (TypeRepr.of[Int].asType, TypeRepr.of[Float].asType) match {
        case ('[Int], '[Float]) =>
          Some(generatePrimitiveInto[Int, Float]('{ a => Right(a.toFloat) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, longType) && isPrimitiveType(using q)(bTpe, doubleType)) {
      (TypeRepr.of[Long].asType, TypeRepr.of[Double].asType) match {
        case ('[Long], '[Double]) =>
          Some(generatePrimitiveInto[Long, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, floatType) && isPrimitiveType(using q)(bTpe, doubleType)) {
      (TypeRepr.of[Float].asType, TypeRepr.of[Double].asType) match {
        case ('[Float], '[Double]) =>
          Some(generatePrimitiveInto[Float, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
        case _ => None
      }
    }
    // Narrowing (Validation)
    else if (isPrimitiveType(using q)(aTpe, longType) && isPrimitiveType(using q)(bTpe, intType)) {
      (TypeRepr.of[Long].asType, TypeRepr.of[Int].asType) match {
        case ('[Long], '[Int]) =>
          Some(generatePrimitiveInto[Long, Int]('{ a =>
            if (a >= Int.MinValue && a <= Int.MaxValue) Right(a.toInt)
            else Left(SchemaError.expectationMismatch(Nil, s"Long value $a out of Int range"))
          }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, doubleType) && isPrimitiveType(using q)(bTpe, floatType)) {
      (TypeRepr.of[Double].asType, TypeRepr.of[Float].asType) match {
        case ('[Double], '[Float]) =>
          Some(generatePrimitiveInto[Double, Float]('{ a =>
            val f = a.toFloat
            if (f.isInfinite && !a.isInfinite)
              Left(SchemaError.expectationMismatch(Nil, s"Double value $a out of Float range"))
            else Right(f)
          }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else None
  }

  // --- Opaque Types ---

  private def isOpaqueType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe match {
      case tr: TypeRef => tr.isOpaqueAlias || tr.typeSymbol.flags.is(Flags.Opaque)
      case _           => false
    }
  }

  private def isOptionType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(optionType, _) if optionType.typeSymbol.fullName == "scala.Option" => true
      case _                                                                              => false
    }
  }

  private def extractOptionElementType(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[q.reflect.TypeRepr] = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(optionType, List(elemType)) if optionType.typeSymbol.fullName == "scala.Option" =>
        Some(elemType)
      case _ => None
    }
  }

  private def isEitherType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(eitherType, _) if eitherType.typeSymbol.fullName == "scala.util.Either" => true
      case _                                                                                   => false
    }
  }

  private def extractEitherTypes(using
    q: Quotes
  )(tpe: q.reflect.TypeRepr): Option[(q.reflect.TypeRepr, q.reflect.TypeRepr)] = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(eitherType, List(leftType, rightType))
          if eitherType.typeSymbol.fullName == "scala.util.Either" =>
        Some((leftType, rightType))
      case _ => None
    }
  }

  private def findCompanionModule(using q: Quotes)(opaqueSymbol: q.reflect.Symbol): Option[q.reflect.Symbol] = {
    import q.reflect.*
    // Strategy 1: Direct
    var comp = opaqueSymbol.companionModule
    if (comp.exists) return Some(comp)
    // Strategy 2: Owner lookup
    val owner = opaqueSymbol.owner
    if (owner.exists) {
      try {
        val res = owner.declarations.find(m => m.name == opaqueSymbol.name && m.flags.is(Flags.Module))
        if (res.isDefined) return res
      } catch { case _: Exception => () }
    }
    // Strategy 3: FullName
    try {
      comp = Symbol.requiredModule(opaqueSymbol.fullName)
      if (comp.exists) return Some(comp)
    } catch { case _: Exception => () }
    None
  }

  private def extractUnderlyingType(using
    q: Quotes
  )(
    opaqueType: q.reflect.TypeRepr,
    companion: Option[q.reflect.Symbol]
  ): q.reflect.TypeRepr = {
    import q.reflect.*
    opaqueType match {
      case tr: TypeRef if tr.isOpaqueAlias => tr.translucentSuperType.dealias
      case tr: TypeRef                     =>
        // Strategy: Use companion object's apply method to extract underlying type
        companion match {
          case Some(comp) =>
            comp.memberMethod("apply").find(s => s.paramSymss.flatten.size == 1) match {
              case Some(applyMethod) =>
                // Extract type from first parameter of apply method
                applyMethod.paramSymss.flatten.headOption match {
                  case Some(param) =>
                    // Get the type of the parameter using memberType on the companion
                    val paramType = comp.termRef.memberType(param).dealias
                    paramType
                  case None => fail(s"Cannot extract underlying type from apply method of ${opaqueType.show}")
                }
              case None => fail(s"No apply/1 method found in companion of ${opaqueType.show}")
            }
          case None =>
            // Fallback: try to use translucentSuperType if available
            tr.translucentSuperType match {
              case t if t != tr => t.dealias
              case _            => fail(s"Cannot extract underlying from ${opaqueType.show} without companion")
            }
        }
      case _ => fail(s"Cannot extract underlying from ${opaqueType.show}")
    }
  }

  private def generateOpaqueValidation[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    val bSym        = bTpe.typeSymbol
    val companion   = findCompanionModule(using q)(bSym).getOrElse(fail(s"No companion for opaque ${bTpe.show}"))
    val applyMethod = companion
      .memberMethod("apply")
      .find(s => s.paramSymss.flatten.size == 1)
      .getOrElse(fail(s"No apply/1 in companion of ${bTpe.show}"))

    val underlyingType = extractUnderlyingType(using q)(bTpe, Some(companion))

    // Check coercion A -> Underlying
    val coercionIntoExpr: Expr[Into[A, Any]] =
      if (aTpe =:= underlyingType) '{
        new Into[A, Any] { def into(x: A) = Right(x: Any) }
      }
      else {
        aTpe.asType match {
          case '[a] =>
            underlyingType.asType match {
              case '[u] =>
                // FIX: We need to properly cast the result
                // findOrDeriveInto[a, u] returns Into[a, u], but we need Into[A, Any]
                // We wrap it in a new Into that does the casting
                val innerInto = findOrDeriveInto[a, u](using q)(ctx, aTpe, underlyingType)
                '{
                  new Into[A, Any] {
                    def into(x: A): Either[SchemaError, Any] =
                      ${ innerInto }.into(x.asInstanceOf[a]).map(_.asInstanceOf[Any])
                  }
                }
            }
        }
      }

    bTpe.asType match {
      case '[b] =>
        val returnType = TypeRepr.of[Either].appliedTo(List(TypeRepr.of[SchemaError], bTpe))
        val anyType    = TypeRepr.of[Any]

        // 1. AST for dynamic companion apply
        val companionRef = Ref(companion)
        val applySelect  = Select(companionRef, applyMethod)

        // FIX: Lambda must accept Any, not underlyingType, to match the expected type
        val lambda = Lambda(
          Symbol.spliceOwner,
          MethodType(List("u"))(_ => List(anyType), _ => returnType),
          (sym, params) => {
            val uTerm = params.head.asInstanceOf[Term]
            // Cast to underlying type before applying
            val uExpr = uTerm.asExprOf[Any]
            // companion.apply(u.asInstanceOf[Underlying])
            underlyingType.asType match {
              case '[u] =>
                val castTerm  = '{ $uExpr.asInstanceOf[u] }.asTerm.changeOwner(sym)
                val applyCall = Apply(applySelect, List(castTerm))
                val applyExpr = applyCall.asExprOf[Either[String, b]]

                '{
                  $applyExpr.left.map(msg =>
                    SchemaError.expectationMismatch(IntoAsVersionSpecificImpl.emptyNodeList, msg)
                  )
                }.asTerm.changeOwner(sym)
            }
          }
        )
        val validationFn = lambda.asExprOf[Any => Either[SchemaError, b]]

        '{
          new Into[A, B] {
            def into(a: A): Either[SchemaError, B] = {
              val uEither = ${ coercionIntoExpr }.asInstanceOf[Into[A, Any]].into(a)
              uEither.flatMap(u => $validationFn(u).asInstanceOf[Either[SchemaError, B]])
            }
          }
        }
    }
  }

  // Generate conversion from OpaqueType to UnderlyingType
  // This is the inverse of generateOpaqueValidation
  private def generateOpaqueToUnderlying[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {

    val aSym      = aTpe.typeSymbol
    val companion = findCompanionModule(using q)(aSym).getOrElse(fail(s"No companion for opaque ${aTpe.show}"))

    val underlyingType = extractUnderlyingType(using q)(aTpe, Some(companion))

    // Verify that B matches the underlying type
    if (!(bTpe =:= underlyingType)) {
      fail(s"Cannot convert opaque type ${aTpe.show} to ${bTpe.show}. Expected underlying type ${underlyingType.show}")
    }

    // Opaque types are runtime-compatible with their underlying type
    // We can safely cast: opaque.asInstanceOf[UnderlyingType]
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          // Safe cast: opaque types are runtime-compatible with underlying type
          // Cast through Any to avoid type mismatch issues
          Right(a.asInstanceOf[Any].asInstanceOf[B])
      }
    }
  }

  // --- Maps ---

  private def extractMapTypes(using
    q: Quotes
  )(tpe: q.reflect.TypeRepr): Option[(q.reflect.TypeRepr, q.reflect.TypeRepr)] = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(mapType, List(keyType, valueType)) =>
        // Check if it's a Map type (immutable.Map or any Map subtype)
        val mapSymbolFullName = mapType.typeSymbol.fullName
        if (
          mapSymbolFullName == "scala.collection.immutable.Map" ||
          mapType.baseClasses.exists(_.fullName == "scala.collection.Map")
        ) {
          Some((keyType, valueType))
        } else None
      case _ => None
    }
  }

  private def deriveMapInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    aKey: q.reflect.TypeRepr,
    aValue: q.reflect.TypeRepr,
    bKey: q.reflect.TypeRepr,
    bValue: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    // Capture lazy defs count before calling findOrDeriveInto
    val lazyDefsBefore = ctx.lazyDefs.length

    // Derive key conversion (if needed)
    val keyIntoOpt = if (aKey =:= bKey) {
      None // Keys are same type, no conversion needed
    } else {
      aKey.asType match {
        case '[ak] =>
          bKey.asType match {
            case '[bk] =>
              Some(findOrDeriveInto[ak, bk](using q)(ctx, aKey, bKey))
          }
      }
    }

    // Derive value conversion (supports nested collections!)
    aValue.asType match {
      case '[av] =>
        bValue.asType match {
          case '[bv] =>
            val valueInto      = findOrDeriveInto[av, bv](using q)(ctx, aValue, bValue)
            val valueIntoTyped = valueInto.asExprOf[Into[av, bv]]

            keyIntoOpt match {
              case Some(keyInto) =>
                // Both key and value need conversion
                aKey.asType match {
                  case '[ak] =>
                    bKey.asType match {
                      case '[bk] =>
                        val keyIntoTyped = keyInto.asExprOf[Into[ak, bk]]

                        val resultExpr = '{
                          new Into[A, B] {
                            def into(a: A): Either[SchemaError, B] = {
                              val sourceMap = a.asInstanceOf[Map[ak, av]]
                              val builder   = Map.newBuilder[bk, bv]

                              val results = sourceMap.map { case (k, v) =>
                                for {
                                  convertedKey   <- ${ keyIntoTyped }.into(k)
                                  convertedValue <- ${ valueIntoTyped }.into(v)
                                } yield (convertedKey, convertedValue)
                              }

                              IntoAsVersionSpecificImpl.sequenceEither(results.toList).map { pairs =>
                                builder.addAll(pairs)
                                builder.result().asInstanceOf[B]
                              }
                            }
                          }
                        }

                        // Handle lazy defs if needed
                        val lazyDefsAfter = ctx.lazyDefs.length
                        if (lazyDefsAfter > lazyDefsBefore) {
                          val newLazyDefs =
                            ctx.lazyDefs.slice(lazyDefsBefore, lazyDefsAfter).map(_.asInstanceOf[ValDef]).toList
                          ctx.lazyDefs.remove(lazyDefsBefore, lazyDefsAfter - lazyDefsBefore)
                          Block(newLazyDefs, resultExpr.asTerm).asExprOf[Into[A, B]]
                        } else {
                          resultExpr
                        }
                    }
                }
              case None =>
                // Only value needs conversion (keys are same type)
                aKey.asType match {
                  case '[ak] =>
                    val resultExpr = '{
                      new Into[A, B] {
                        def into(a: A): Either[SchemaError, B] = {
                          val sourceMap = a.asInstanceOf[Map[ak, av]]
                          val builder   = Map.newBuilder[ak, bv]

                          val results = sourceMap.map { case (k, v) =>
                            ${ valueIntoTyped }.into(v).map(v => (k, v))
                          }

                          IntoAsVersionSpecificImpl.sequenceEither(results.toList).map { pairs =>
                            builder.addAll(pairs)
                            builder.result().asInstanceOf[B]
                          }
                        }
                      }
                    }

                    // Handle lazy defs if needed
                    val lazyDefsAfter = ctx.lazyDefs.length
                    if (lazyDefsAfter > lazyDefsBefore) {
                      val newLazyDefs =
                        ctx.lazyDefs.slice(lazyDefsBefore, lazyDefsAfter).map(_.asInstanceOf[ValDef]).toList
                      ctx.lazyDefs.remove(lazyDefsBefore, lazyDefsAfter - lazyDefsBefore)
                      Block(newLazyDefs, resultExpr.asTerm).asExprOf[Into[A, B]]
                    } else {
                      resultExpr
                    }
                }
            }
        }
    }
  }

  // --- Collections ---

  private def extractCollectionElementType(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[q.reflect.TypeRepr] = {
    import q.reflect.*
    if (tpe <:< TypeRepr.of[Array[?]]) {
      tpe.asType match { case '[Array[t]] => Some(TypeRepr.of[t]); case _ => None }
    } else if (tpe <:< TypeRepr.of[Iterable[?]]) {
      tpe.baseType(Symbol.classSymbol("scala.collection.Iterable")) match {
        case AppliedType(_, List(arg)) => Some(arg)
        case _                         => None
      }
    } else None
  }

  private def getCollectionFactory(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect.*
    if (tpe <:< TypeRepr.of[List[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.List"))
    else if (tpe <:< TypeRepr.of[Vector[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Vector"))
    else if (tpe <:< TypeRepr.of[Set[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Set"))
    else if (tpe <:< TypeRepr.of[Seq[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Seq"))
    else
      tpe.typeSymbol.companionModule match {
        case m if m.exists => Ref(m)
        case _             => fail(s"Cannot find factory for ${tpe.show}")
      }
  }

  private def deriveCollectionInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    aElem: q.reflect.TypeRepr,
    bElem: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    // Recursively derive element conversion
    aElem.asType match {
      case '[ae] =>
        bElem.asType match {
          case '[be] =>
            // Capture lazy defs count before calling findOrDeriveInto
            val lazyDefsBefore = ctx.lazyDefs.length

            val elementInto = findOrDeriveInto[ae, be](using q)(ctx, aElem, bElem)
            // FIX: Remove redundant cast - elementInto is already Expr[Into[ae, be]]
            val elemIntoTyped: Expr[Into[ae, be]] = elementInto

            // Pre-computed Lambda Expression: build B from List[be]
            // This avoids ScopeException by defining the lambda BEFORE the quote block
            val buildBExpr: Expr[List[be] => B] =
              if (bTpe <:< TypeRepr.of[List[?]]) {
                '{ (list: List[be]) => list.asInstanceOf[B] }
              } else if (bTpe <:< TypeRepr.of[Array[?]]) {
                val ct = Expr.summon[ClassTag[be]].getOrElse(fail(s"No ClassTag for ${TypeRepr.of[be].show}"))
                '{ (list: List[be]) => Array.from(list)(using $ct).asInstanceOf[B] }
              } else {
                // Generic case: use companion.from via AST construction
                val companion  = getCollectionFactory(using q)(bTpe)
                val fromMethod = companion.symbol.memberMethod("from").head

                // Build lambda using AST to avoid scope issues
                val listBeType  = TypeRepr.of[List].appliedTo(List(TypeRepr.of[be]))
                val buildLambda = Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("list"))(_ => List(listBeType), _ => bTpe),
                  (sym, params) => {
                    val listParam  = params.head.asInstanceOf[Term]
                    val fromSelect = Select(companion, fromMethod)
                    val typeApply  = TypeApply(fromSelect, List(TypeTree.of[be]))
                    val applyCall  = Apply(typeApply, List(listParam))
                    applyCall.changeOwner(sym)
                  }
                )
                buildLambda.asExprOf[List[be] => B]
              }

            val resultExpr = '{
              new Into[A, B] {
                def into(a: A): Either[SchemaError, B] = {
                  // Convert A to Iterable[ae]
                  val source: Iterable[ae] = ${
                    if (aTpe <:< TypeRepr.of[Array[?]]) '{ ArraySeq.unsafeWrapArray(a.asInstanceOf[Array[ae]]) }
                    else '{ a.asInstanceOf[Iterable[ae]] }
                  }

                  // Map and sequence
                  IntoAsVersionSpecificImpl.mapAndSequence[ae, be](source, x => $elemIntoTyped.into(x)) match {
                    case Right(list) => Right($buildBExpr(list))
                    case Left(e)     => Left(e)
                  }
                }
              }
            }

            // FIX: If new lazy defs were added during findOrDeriveInto, wrap result in Block
            val lazyDefsAfter = ctx.lazyDefs.length
            if (lazyDefsAfter > lazyDefsBefore) {
              val newLazyDefs = ctx.lazyDefs.slice(lazyDefsBefore, lazyDefsAfter).map(_.asInstanceOf[ValDef]).toList
              // Remove the wrapped lazy defs from context to avoid double wrapping
              ctx.lazyDefs.remove(lazyDefsBefore, lazyDefsAfter - lazyDefsBefore)
              Block(newLazyDefs, resultExpr.asTerm).asExprOf[Into[A, B]]
            } else {
              resultExpr
            }
        }
    }
  }

  // --- Either & Option (Explicit handling to avoid GADT constraint issues) ---

  private def deriveEitherInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] =

    (extractEitherTypes(using q)(aTpe), extractEitherTypes(using q)(bTpe)) match {
      case (Some((aLeft, aRight)), Some((bLeft, bRight))) =>
        // Derive left type coercion (if needed)
        val leftIntoOpt = if (aLeft =:= bLeft) {
          None // No coercion needed
        } else {
          aLeft.asType match {
            case '[al] =>
              bLeft.asType match {
                case '[bl] =>
                  Some(findOrDeriveInto[al, bl](using q)(ctx, aLeft, bLeft))
              }
          }
        }

        // Derive right type coercion
        aRight.asType match {
          case '[ar] =>
            bRight.asType match {
              case '[br] =>
                val rightInto = findOrDeriveInto[ar, br](using q)(ctx, aRight, bRight)

                leftIntoOpt match {
                  case Some(leftInto) =>
                    // Both left and right need coercion
                    aLeft.asType match {
                      case '[al] =>
                        bLeft.asType match {
                          case '[bl] =>
                            val leftIntoTyped = leftInto.asExprOf[Into[al, bl]]
                            '{
                              new Into[A, B] {
                                def into(a: A): Either[SchemaError, B] =
                                  a.asInstanceOf[Either[al, ar]] match {
                                    case Left(l) =>
                                      ${ leftIntoTyped }.into(l).map((lt: bl) => Left(lt).asInstanceOf[B])
                                    case Right(r) =>
                                      ${ rightInto }.into(r).map((rt: br) => Right(rt).asInstanceOf[B])
                                  }
                              }
                            }
                        }
                    }
                  case None =>
                    // Only right needs coercion
                    aLeft.asType match {
                      case '[al] =>
                        bLeft.asType match {
                          case '[bl] =>
                            val rightIntoTyped = rightInto.asExprOf[Into[ar, br]]
                            '{
                              new Into[A, B] {
                                def into(a: A): Either[SchemaError, B] =
                                  a.asInstanceOf[Either[al, ar]] match {
                                    case Left(l) =>
                                      Right(Left(l).asInstanceOf[Either[bl, br]].asInstanceOf[B])
                                    case Right(r) =>
                                      ${ rightIntoTyped }.into(r).map((rt: br) => Right(rt).asInstanceOf[B])
                                  }
                              }
                            }
                        }
                    }
                }
            }
        }
      case _ => fail(s"Cannot extract Either types from ${aTpe.show} or ${bTpe.show}")
    }

  private def deriveOptionInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    (extractOptionElementType(using q)(aTpe), extractOptionElementType(using q)(bTpe)) match {
      case (Some(aElem), Some(bElem)) =>
        aElem.asType match {
          case '[ae] =>
            bElem.asType match {
              case '[be] =>
                // Check if implicit Into exists first (supports custom conversions)
                val intoType = TypeRepr.of[Into].appliedTo(List(aElem, bElem))
                Implicits.search(intoType) match {
                  case iss: ImplicitSearchSuccess =>
                    // Use implicit (custom conversion provided by user)
                    val implicitInto = iss.tree.asExpr.asInstanceOf[Expr[Into[ae, be]]]
                    '{
                      new Into[A, B] {
                        def into(a: A): Either[SchemaError, B] =
                          a.asInstanceOf[Option[ae]] match {
                            case None        => Right(None.asInstanceOf[B])
                            case Some(value) =>
                              ${ implicitInto }.into(value).map((v: be) => Some(v).asInstanceOf[B])
                          }
                      }
                    }
                  case _ =>
                    // Try automatic derivation (fail-fast if not possible)
                    val elemInto      = findOrDeriveInto[ae, be](using q)(ctx, aElem, bElem)
                    val elemIntoTyped = elemInto.asExprOf[Into[ae, be]]

                    '{
                      new Into[A, B] {
                        def into(a: A): Either[SchemaError, B] =
                          a.asInstanceOf[Option[ae]] match {
                            case None        => Right(None.asInstanceOf[B])
                            case Some(value) =>
                              ${ elemIntoTyped }.into(value).map((v: be) => Some(v).asInstanceOf[B])
                          }
                      }
                    }
                }
            }
        }
      case _ => fail(s"Cannot extract Option element types from ${aTpe.show} or ${bTpe.show}")
    }
  }

  // --- Tuples ---

  private def isTuple(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean =
    tpe.typeSymbol.fullName.startsWith("scala.Tuple")

  private def extractTupleFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] =
    tpe.asType match {
      case '[t] =>
        if (isTuple(using q)(tpe)) tpe.typeArgs
        else Nil
    }

  private def deriveTupleInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    aFields: List[q.reflect.TypeRepr],
    bFields: List[q.reflect.TypeRepr]
  ): Expr[Into[A, B]] = {

    if (aFields.size != bFields.size)
      fail(s"Arity mismatch: ${aTpe.show} has ${aFields.size}, ${bTpe.show} has ${bFields.size}")

    // Conversions for each element by position
    val fieldConversions = aFields.zip(bFields).zipWithIndex.map { case ((aField, bField), _) =>
      aField.asType match {
        case '[af] =>
          bField.asType match {
            case '[bf] =>
              val intoExpr = findOrDeriveInto[af, bf](using q)(ctx, aField, bField)
              '{ ${ intoExpr }.asInstanceOf[Into[Any, Any]] }
          }
      }
    }

    bTpe.asType match {
      case '[bTuple] =>
        // Generate construction: map fields and sequence
        val conversionExprs = fieldConversions.map { conv =>
          conv
        }

        generateTupleConstruction[B](using q)(conversionExprs, bTpe, bFields).asInstanceOf[Expr[Into[A, B]]]
    }
  }

  private def deriveCaseClassToTuple[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    val aFieldsInfo = extractCaseClassFields(using q)(aTpe)
    val bFieldTypes = extractTupleFields(using q)(bTpe)

    if (aFieldsInfo.size != bFieldTypes.size)
      fail(s"Arity mismatch: Case class ${aTpe.show} (${aFieldsInfo.size}) vs Tuple ${bTpe.show} (${bFieldTypes.size})")

    val conversions = aFieldsInfo.zip(bFieldTypes).map { case (aField, bType) =>
      aField.tpeRepr(using q).asType match {
        case '[af] =>
          bType.asType match {
            case '[bt] =>
              val intoExpr = findOrDeriveInto[af, bt](using q)(ctx, aField.tpeRepr(using q), bType)
              '{ ${ intoExpr }.asInstanceOf[Into[Any, Any]] }
          }
      }
    }

    generateTupleConstruction[B](using q)(conversions, bTpe, bFieldTypes).asInstanceOf[Expr[Into[A, B]]]
  }

  private def deriveTupleToCaseClass[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    val aFieldTypes = extractTupleFields(using q)(aTpe)
    val bFieldsInfo = extractCaseClassFields(using q)(bTpe)

    if (aFieldTypes.size != bFieldsInfo.size)
      fail(s"Arity mismatch: Tuple ${aTpe.show} (${aFieldTypes.size}) vs Case class ${bTpe.show} (${bFieldsInfo.size})")

    val conversions = aFieldTypes.zip(bFieldsInfo).map { case (aType, bField) =>
      aType.asType match {
        case '[at] =>
          bField.tpeRepr(using q).asType match {
            case '[bf] =>
              val intoExpr = findOrDeriveInto[at, bf](using q)(ctx, aType, bField.tpeRepr(using q))
              '{ ${ intoExpr }.asInstanceOf[Into[Any, Any]] }
          }
      }
    }

    generateEitherAccumulation[B](using q)(conversions, bTpe).asExprOf[Into[A, B]]
  }

  // --- Products (Case Classes) ---

  private class FieldInfo(val name: String, val tpe: Any) {
    def tpeRepr(using q: Quotes): q.reflect.TypeRepr = tpe.asInstanceOf[q.reflect.TypeRepr]
  }

  private def extractCaseClassFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[FieldInfo] = {
    val sym    = tpe.typeSymbol
    val fields = sym.primaryConstructor.paramSymss.flatten
    fields.map { field =>
      new FieldInfo(field.name, tpe.memberType(field).dealias)
    }
  }

  // Helper to detect fields with default values (for As round-trip safety)
  private def getFieldsWithDefaultValues(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    // Only check case classes (tuples and other types don't have default values in the same way)
    if (!isCaseClass(using q)(tpe)) return Nil

    val sym = tpe.typeSymbol
    sym.primaryConstructor.paramSymss.flatten
      .filter(_.flags.is(Flags.HasDefault))
      .map(_.name)
  }

  // --- ZIO Prelude Newtypes Support ---

  private def isZioPreludeNewtype(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe match {
      case TypeRef(compTpe, "Type") =>
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype") ||
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Subtype")
      case _ => false
    }
  }

  private def zioPreludeNewtypeDealias(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.TypeRepr = {
    import q.reflect.*
    tpe match {
      case TypeRef(compTpe, _) =>
        compTpe.baseClasses.find(cls =>
          cls.fullName == "zio.prelude.Newtype" || cls.fullName == "zio.prelude.Subtype"
        ) match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case _         => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}")
        }
      case _ => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}")
    }
  }

  private def findZioPreludeCompanion(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.Symbol = {
    import q.reflect.*
    tpe match {
      case TypeRef(compTpe, "Type") =>
        // compTpe is the companion object type, get its term symbol
        compTpe.termSymbol match {
          case sym if sym.exists => sym
          case _                 => fail(s"Cannot find companion for zio-prelude newtype: ${tpe.show}")
        }
      case _ => fail(s"Cannot find companion for zio-prelude newtype: ${tpe.show}")
    }
  }

  private def extractMethodReturnType(using
    q: Quotes
  )(
    companion: q.reflect.Symbol,
    method: q.reflect.Symbol
  ): q.reflect.TypeRepr = {
    import q.reflect.*

    // Strategia 1: Usa memberType sul companion per ottenere il tipo del metodo
    val companionType = companion.termRef
    val methodType    = companionType.memberType(method)

    methodType match {
      case mt: MethodType =>
        // Il tipo di ritorno è resType della MethodType
        mt.resType
      case pt: PolyType =>
        // Se è un PolyType, ritorna resType (gestisce metodi generici)
        pt.resType
      case other =>
        // Fallback: prova a ottenere il tipo dal tree del metodo
        method.tree match {
          case Some(DefDef(_, _, _, body)) =>
            // Estrai il tipo dal body se presente
            body match {
              case Some(b) => b.tpe
              case None    => methodType
            }
          case _ =>
            // Ultimo fallback: usa il tipo del metodo direttamente
            methodType
        }
    }
  }

  private def generateZioPreludeNewtypeConversion[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Option[Expr[Into[A, B]]] = {
    import q.reflect.*

    // Check if B is a ZIO Prelude newtype
    if (isZioPreludeNewtype(using q)(bTpe)) {
      val underlyingType = zioPreludeNewtypeDealias(using q)(bTpe)
      val companion      = findZioPreludeCompanion(using q)(bTpe)

      // Check if companion has 'make' method
      val hasMakeMethod =
        try {
          companion.memberMethod("make").exists(s => s.paramSymss.flatten.size == 1)
        } catch {
          case _: Exception => false
        }

      // Convert A -> Underlying -> B with validation
      if (aTpe =:= underlyingType) {
        // Direct conversion: A is already the underlying type
        bTpe.asType match {
          case '[b] =>
            underlyingType.asType match {
              case '[u] =>
                val companionRef = Ref(companion)

                if (hasMakeMethod) {
                  // CASO A: Use make method (preferred) - Quotes-based with standard pattern matching
                  import q.reflect.*
                  val makeMethod = companion.memberMethod("make").find(s => s.paramSymss.flatten.size == 1).get

                  // Estrai il tipo di ritorno di make
                  val makeReturnType = extractMethodReturnType(using q)(companion, makeMethod)

                  Some('{
                    new Into[A, B] {
                      def into(x: A): Either[SchemaError, B] = {
                        val uVal = x.asInstanceOf[u]
                        ${
                          val uTerm         = 'uVal.asTerm
                          val makeSelect    = Select(companionRef, makeMethod)
                          val makeCall      = Apply(makeSelect, List(uTerm))
                          val typedMakeCall = Typed(makeCall, Inferred(makeReturnType))
                          val toEitherCall  = Select.unique(typedMakeCall, "toEither").asExprOf[Either[Any, B]]

                          // Pattern matching standard di Scala - nessuna costruzione AST manuale!
                          '{
                            $toEitherCall match {
                              case Right(v) => Right(v.asInstanceOf[B])
                              case Left(e)  =>
                                Left(
                                  SchemaError.expectationMismatch(
                                    IntoAsVersionSpecificImpl.emptyNodeList,
                                    e.toString
                                  )
                                )
                            }
                          }
                        }
                      }
                    }
                  })
                } else {
                  // CASO B: Fallback to apply - Lambda Injection via AST
                  import q.reflect.*
                  val applyMethod = companion
                    .memberMethod("apply")
                    .find(s => s.paramSymss.flatten.size == 1)
                    .getOrElse(report.errorAndAbort(s"No apply/1 method found in companion of ${bTpe.show}"))

                  val applyLambdaType = MethodType(List("v"))(_ => List(underlyingType), _ => bTpe)
                  val applyLambda     = Lambda(
                    Symbol.spliceOwner,
                    applyLambdaType,
                    (owner, params) => {
                      val arg         = params.head.asInstanceOf[Term]
                      val applySelect = Select(companionRef, applyMethod)
                      Apply(applySelect, List(arg)).changeOwner(owner)
                    }
                  ).asExprOf[u => b]

                  Some('{
                    new Into[A, B] {
                      def into(x: A): Either[SchemaError, B] =
                        try {
                          val uVal = x.asInstanceOf[u]
                          Right($applyLambda(uVal).asInstanceOf[B])
                        } catch {
                          case e: Throwable =>
                            Left(
                              SchemaError.expectationMismatch(
                                IntoAsVersionSpecificImpl.emptyNodeList,
                                e.getMessage
                              )
                            )
                        }
                    }
                  })
                }
            }
        }
      } else {
        // Coercion needed: A -> Underlying -> B with validation
        aTpe.asType match {
          case '[a] =>
            underlyingType.asType match {
              case '[u] =>
                bTpe.asType match {
                  case '[b] =>
                    val innerInto      = findOrDeriveInto[a, u](using q)(ctx, aTpe, underlyingType)
                    val innerIntoTyped = innerInto.asExprOf[Into[a, u]]
                    val companionRef   = Ref(companion)

                    if (hasMakeMethod) {
                      // CASO A: Use make method (preferred) - Quotes-based with standard pattern matching
                      import q.reflect.*
                      val makeMethod = companion.memberMethod("make").find(s => s.paramSymss.flatten.size == 1).get

                      // Estrai il tipo di ritorno di make
                      val makeReturnType = extractMethodReturnType(using q)(companion, makeMethod)

                      Some('{
                        new Into[A, B] {
                          def into(x: A): Either[SchemaError, B] =
                            ${ innerIntoTyped }.into(x.asInstanceOf[a]).flatMap { (u: u) =>
                              ${
                                val uTerm         = 'u.asTerm
                                val makeSelect    = Select(companionRef, makeMethod)
                                val makeCall      = Apply(makeSelect, List(uTerm))
                                val typedMakeCall = Typed(makeCall, Inferred(makeReturnType))
                                val toEitherCall  = Select.unique(typedMakeCall, "toEither").asExprOf[Either[Any, B]]

                                // Pattern matching standard di Scala - nessuna costruzione AST manuale!
                                '{
                                  $toEitherCall match {
                                    case Right(v) => Right(v.asInstanceOf[B])
                                    case Left(e)  =>
                                      Left(
                                        SchemaError.expectationMismatch(
                                          IntoAsVersionSpecificImpl.emptyNodeList,
                                          e.toString
                                        )
                                      )
                                  }
                                }
                              }
                            }
                        }
                      })
                    } else {
                      // CASO B: Fallback to apply - Lambda Injection via AST (Coercion Path)
                      import q.reflect.*
                      val applyMethod = companion
                        .memberMethod("apply")
                        .find(s => s.paramSymss.flatten.size == 1)
                        .getOrElse(report.errorAndAbort(s"No apply/1 method found in companion of ${bTpe.show}"))

                      val applyLambdaType = MethodType(List("v"))(_ => List(underlyingType), _ => bTpe)
                      val applyLambda     = Lambda(
                        Symbol.spliceOwner,
                        applyLambdaType,
                        (owner, params) => {
                          val arg         = params.head.asInstanceOf[Term]
                          val applySelect = Select(companionRef, applyMethod)
                          Apply(applySelect, List(arg)).changeOwner(owner)
                        }
                      ).asExprOf[u => b]

                      Some('{
                        new Into[A, B] {
                          def into(x: A): Either[SchemaError, B] =
                            ${ innerIntoTyped }.into(x.asInstanceOf[a]).flatMap { (uVal: u) =>
                              try {
                                Right($applyLambda(uVal).asInstanceOf[B])
                              } catch {
                                case e: Throwable =>
                                  Left(
                                    SchemaError.expectationMismatch(
                                      IntoAsVersionSpecificImpl.emptyNodeList,
                                      e.getMessage
                                    )
                                  )
                              }
                            }
                        }
                      })
                    }
                }
            }
        }
      }
    } else if (isZioPreludeNewtype(using q)(aTpe)) {
      // A is newtype, B is underlying or coercible
      val underlyingType = zioPreludeNewtypeDealias(using q)(aTpe)

      // Convert A (newtype) -> Underlying -> B
      // ZIO Prelude newtypes are type aliases at runtime, unwrap directly
      if (underlyingType =:= bTpe) {
        // Direct unwrap: Underlying -> B
        Some('{
          new Into[A, B] {
            def into(x: A): Either[SchemaError, B] = Right(x.asInstanceOf[B])
          }
        })
      } else {
        // Coercion needed: A -> Underlying -> B
        underlyingType.asType match {
          case '[u] =>
            bTpe.asType match {
              case '[b] =>
                val innerInto      = findOrDeriveInto[u, b](using q)(ctx, underlyingType, bTpe)
                val innerIntoTyped = innerInto.asExprOf[Into[u, b]]
                // Unwrap newtype and convert
                Some('{
                  new Into[A, B] {
                    def into(x: A): Either[SchemaError, B] = {
                      val unwrapped: u = x.asInstanceOf[u]
                      ${ innerIntoTyped }.into(unwrapped).map(_.asInstanceOf[B])
                    }
                  }
                })
            }
        }
      }
    } else {
      None
    }
  }

  // Priority levels for field matching
  private enum PriorityLevel {
    case P1_ExactNameType      // Priority 1: Exact name + type match
    case P2_NameCoercible      // Priority 2: Exact name + coercible type
    case P3_UniqueType         // Priority 3: Unique type match
    case P4_PositionCompatible // Priority 4: Position + compatible type
  }

  // Match result with priority
  private case class MatchWithPriority(field: FieldInfo, priority: PriorityLevel)

  // Strict compatibility check for Priority 3 (Unique Type Match)
  // Separates Integrals from Fractionals to avoid false ambiguities
  private def isStrictlyCompatible(using
    q: Quotes
  )(
    tpeA: q.reflect.TypeRepr,
    tpeB: q.reflect.TypeRepr
  ): Boolean = {
    val a = tpeA.dealias
    val b = tpeB.dealias

    // 1. Identici
    if (a =:= b) return true

    val aIsPrim = isPrimitive(using q)(a)
    val bIsPrim = isPrimitive(using q)(b)

    // 2. Entrambi Primitivi: Check Numerico Separato (Integrals vs Fractionals)
    if (aIsPrim && bIsPrim) {
      val integrals   = Set("Int", "Long", "Short", "Byte", "scala.Int", "scala.Long", "scala.Short", "scala.Byte")
      val fractionals = Set("Double", "Float", "scala.Double", "scala.Float")

      val aName = a.typeSymbol.name
      val bName = b.typeSymbol.name

      // Se entrambi integrali -> Compatibili (Int->Long, Long->Int, ecc.)
      if (integrals.contains(aName) && integrals.contains(bName)) return true
      // Se entrambi frazionari -> Compatibili (Float->Double, Double->Float, ecc.)
      if (fractionals.contains(aName) && fractionals.contains(bName)) return true
      // Integral vs Fractional -> Non compatibili (per evitare ambiguità in Priority 3)
      if (
        (integrals.contains(aName) && fractionals.contains(bName)) ||
        (fractionals.contains(aName) && integrals.contains(bName))
      ) return false

      // Boolean/Char/String stretti (solo se stesso nome)
      if (aName == bName) return true
      return false
    }

    // 3. Mismatched Kinds (Primitive vs Complex) -> False
    if (aIsPrim != bIsPrim) return false

    // 4. Option wrapping (A -> Option[A])
    if (isOptionType(using q)(b)) {
      extractOptionElementType(using q)(b) match {
        case Some(optElemType) =>
          return isStrictlyCompatible(using q)(a, optElemType.dealias)
        case None => // continue
      }
    }

    // 5. Collections - Check element types recursively
    val aCollection = extractCollectionElementType(using q)(a)
    val bCollection = extractCollectionElementType(using q)(b)
    if (aCollection.isDefined && bCollection.isDefined) {
      val aElem = aCollection.get.dealias
      val bElem = bCollection.get.dealias
      return isStrictlyCompatible(using q)(aElem, bElem)
    }

    // 6. Complex Types (Case Class, Products, Tuples) -> Ottimistico
    // Assumiamo che la macro possa derivare la conversione ricorsivamente.
    // Blocchiamo solo ambiguità, non validità.
    true
  }

  // Loose compatibility check for Priority 4 (Position Match)
  // Allows all numeric types to be compatible (Int <-> Double, etc.)
  // Position disambiguates, so we can be more permissive
  private def isLooselyCompatible(using
    q: Quotes
  )(
    tpeA: q.reflect.TypeRepr,
    tpeB: q.reflect.TypeRepr
  ): Boolean = {
    val a = tpeA.dealias
    val b = tpeB.dealias

    // 1. Identici
    if (a =:= b) return true

    val aIsPrim = isPrimitive(using q)(a)
    val bIsPrim = isPrimitive(using q)(b)

    // 2. Entrambi Primitivi: Tutti i numerici sono compatibili
    if (aIsPrim && bIsPrim) {
      val allNumerics = Set(
        "Int",
        "Long",
        "Double",
        "Float",
        "Short",
        "Byte",
        "scala.Int",
        "scala.Long",
        "scala.Double",
        "scala.Float",
        "scala.Short",
        "scala.Byte"
      )

      val aName = a.typeSymbol.name
      val bName = b.typeSymbol.name

      // Tutti i numerici sono compatibili per Priority 4 (posizione disambigua)
      if (allNumerics.contains(aName) && allNumerics.contains(bName)) return true

      // Boolean/Char/String stretti (solo se stesso nome)
      if (aName == bName) return true
      return false
    }

    // 3. Mismatched Kinds (Primitive vs Complex) -> False
    if (aIsPrim != bIsPrim) return false

    // 4. Option wrapping (A -> Option[A])
    if (isOptionType(using q)(b)) {
      extractOptionElementType(using q)(b) match {
        case Some(optElemType) =>
          return isLooselyCompatible(using q)(a, optElemType.dealias)
        case None => // continue
      }
    }

    // 5. Collections - Check element types recursively
    val aCollection = extractCollectionElementType(using q)(a)
    val bCollection = extractCollectionElementType(using q)(b)
    if (aCollection.isDefined && bCollection.isDefined) {
      val aElem = aCollection.get.dealias
      val bElem = bCollection.get.dealias
      return isLooselyCompatible(using q)(aElem, bElem)
    }

    // 6. Complex Types (Case Class, Products, Tuples) -> Ottimistico
    // Assumiamo che la macro possa derivare la conversione ricorsivamente.
    // Blocchiamo solo ambiguità, non validità.
    true
  }

  // Legacy function for backward compatibility (delegates to isStrictlyCompatible)
  private def isPotentiallyCompatible(using
    q: Quotes
  )(
    tpeA: q.reflect.TypeRepr,
    tpeB: q.reflect.TypeRepr
  ): Boolean = isStrictlyCompatible(using q)(tpeA, tpeB)

  // Find all possible matches for a field with their priorities
  private def findAllMatches(using
    q: Quotes
  )(
    aFields: List[FieldInfo],
    bField: FieldInfo,
    bFieldIndex: Int,
    allBFields: List[FieldInfo],
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): List[MatchWithPriority] = {
    val matches = scala.collection.mutable.ListBuffer[MatchWithPriority]()

    // Priority 1: Exact Name + Type Match
    val nameMatches = aFields.filter(_.name == bField.name)
    nameMatches.find(_.tpeRepr(using q) =:= bField.tpeRepr(using q)) match {
      case Some(exact) =>
        matches += MatchWithPriority(exact, PriorityLevel.P1_ExactNameType)
        return matches.toList // Priority 1 is highest, return immediately
      case None => // continue
    }

    // Priority 2: Name Match with Coercion (allow any type if name matches)
    if (nameMatches.size == 1) {
      matches += MatchWithPriority(nameMatches.head, PriorityLevel.P2_NameCoercible)
      return matches.toList // Priority 2 is high, return immediately
    }

    // Priority 3: Unique Type Match (relaxed: unique in A OR in B)
    // Use STRICT compatibility check (Integrals vs Fractionals separated)
    val bTpeDealiased    = bField.tpeRepr(using q).dealias
    val exactTypeMatches = aFields.filter(_.tpeRepr(using q) =:= bField.tpeRepr(using q))

    // Count fields in B with strictly compatible types (for uniqueness check)
    // Use strict compatibility to avoid false ambiguities
    val isUniqueInB = {
      val bFieldTpe = bField.tpeRepr(using q)
      allBFields.count { bF =>
        isStrictlyCompatible(using q)(bF.tpeRepr(using q), bFieldTpe)
      } == 1
    }

    if (exactTypeMatches.size == 1) {
      // Single exact match in A: works if unique in A OR in B
      val isUniqueInA = true // exactTypeMatches.size == 1 implies unique in A
      if (isUniqueInA || isUniqueInB) {
        matches += MatchWithPriority(exactTypeMatches.head, PriorityLevel.P3_UniqueType)
        return matches.toList
      }
    } else if (exactTypeMatches.size > 1 && isUniqueInB) {
      // Multiple exact matches in A, but unique in B: use positional match
      if (bFieldIndex >= 0 && bFieldIndex < aFields.size) {
        val candidate    = aFields(bFieldIndex)
        val candidateTpe = candidate.tpeRepr(using q).dealias
        if (candidateTpe =:= bTpeDealiased) {
          matches += MatchWithPriority(candidate, PriorityLevel.P3_UniqueType)
          return matches.toList
        }
      }
    }

    // Check for strictly compatible types (Int -> Long OK, Int -> Double NOT OK for Priority 3)
    // This allows Int -> Long, AddressV1 -> AddressV2, etc., but avoids Int/Double ambiguity
    val strictlyCompatibleMatches = aFields.filter { aField =>
      isStrictlyCompatible(using q)(aField.tpeRepr(using q), bField.tpeRepr(using q))
    }

    // If we have strictly compatible matches and the type is unique in B, consider them
    if (strictlyCompatibleMatches.size == 1 && isUniqueInB) {
      matches += MatchWithPriority(strictlyCompatibleMatches.head, PriorityLevel.P3_UniqueType)
      return matches.toList
    }

    // PRIORITY 4: Position + Compatible Type Match
    // ENABLED for all Products (Case Classes & Tuples)
    // Position is the disambiguator when names don't match
    // Use LOOSE compatibility (all numerics compatible) since position disambiguates
    if (bFieldIndex >= 0 && bFieldIndex < aFields.size) {
      val candidate = aFields(bFieldIndex)

      // Use loose compatibility check (all numerics OK, Complex types OK)
      // Position disambiguates, so we can be more permissive
      if (isLooselyCompatible(using q)(candidate.tpeRepr(using q), bField.tpeRepr(using q))) {
        matches += MatchWithPriority(candidate, PriorityLevel.P4_PositionCompatible)
      }
    }

    matches.toList
  }

  // Validate field mappings for uniqueness and detect ambiguities
  private def validateMappings(using
    q: Quotes
  )(
    aFields: List[FieldInfo],
    bFields: List[FieldInfo],
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Either[String, Map[Int, Int]] = { // Map[bFieldIndex -> aFieldIndex]
    val mappings    = scala.collection.mutable.Map[Int, Int]()
    val ambiguities = scala.collection.mutable.ListBuffer[String]()

    bFields.zipWithIndex.foreach { case (bField, bIdx) =>
      val allMatches = findAllMatches(using q)(aFields, bField, bIdx, bFields, aTpe, bTpe)

      if (allMatches.isEmpty) {
        // No match found - check if optional
        if (!isOptionType(using q)(bField.tpeRepr(using q))) {
          ambiguities += s"Field '${bField.name}: ${bField.tpeRepr(using q).show}' has no compatible match in source. Available fields: ${aFields
              .map(f => s"${f.name}: ${f.tpeRepr(using q).show}")
              .mkString(", ")}"
        }
      } else {
        // Filter matches by highest priority
        val highestPriority = allMatches.map(_.priority).minBy(_.ordinal)
        val topMatches      = allMatches.filter(_.priority == highestPriority)

        if (topMatches.size == 1) {
          // Unique match - OK
          val aIdx = aFields.indexOf(topMatches.head.field)
          mappings(bIdx) = aIdx
        } else if (topMatches.size > 1) {
          // Ambiguity detected
          val candidateNames = topMatches.map(m => s"${m.field.name}: ${m.field.tpeRepr(using q).show}").mkString(", ")
          ambiguities += s"Field '${bField.name}: ${bField.tpeRepr(using q).show}' can map to multiple source fields with the same priority (${highestPriority}): $candidateNames. Please rename fields to disambiguate."
        }
      }
    }

    if (ambiguities.nonEmpty) {
      Left(ambiguities.mkString("\n"))
    } else {
      Right(mappings.toMap)
    }
  }

  // Disambiguation Logic (Phase 7 + Priority 4)
  private def findMatchingField(using
    q: Quotes
  )(
    aFields: List[FieldInfo],
    bField: FieldInfo,
    bFieldIndex: Int,           // Index for Priority 4
    allBFields: List[FieldInfo] // All B fields for uniqueness check
  ): Option[FieldInfo] = {

    // Priority 1: Exact Name Match
    val nameMatches = aFields.filter(_.name == bField.name)
    nameMatches.find(_.tpeRepr(using q) =:= bField.tpeRepr(using q)) match {
      case Some(exact) => return Some(exact)
      case None        => // continue
    }

    // Priority 2: Name Match with Coercion (handled by derivation check later, just return name match)
    if (nameMatches.size == 1) return Some(nameMatches.head)

    // Priority 3: Unique Type Match (relaxed: unique in A OR in B)
    val typeMatches   = aFields.filter(_.tpeRepr(using q) =:= bField.tpeRepr(using q))
    val bTpeDealiased = bField.tpeRepr(using q).dealias
    val isUniqueInB   = allBFields.count(_.tpeRepr(using q).dealias =:= bTpeDealiased) == 1

    if (typeMatches.size == 1) {
      // Single match in A: works if unique in A OR in B
      val isUniqueInA = true // typeMatches.size == 1 implies unique in A
      if (isUniqueInA || isUniqueInB) return Some(typeMatches.head)
    } else if (typeMatches.size > 1 && isUniqueInB) {
      // Multiple matches in A, but unique in B: use positional match
      if (bFieldIndex >= 0 && bFieldIndex < aFields.size) {
        val candidate    = aFields(bFieldIndex)
        val candidateTpe = candidate.tpeRepr(using q).dealias
        if (candidateTpe =:= bTpeDealiased) return Some(candidate)
      }
    }

    // PRIORITY 4: Position + Compatible Type Match (Pure Positional)
    // Condition:
    // 1. Position exists in A (bIdx valid)
    // 2. Types are Compatible (Equal OR Primitive Coercible OR Collection Types OR Nested Types OR Option Wrapping)
    // Position is the disambiguator - if types are compatible at same position, it's a match
    if (bFieldIndex >= 0 && bFieldIndex < aFields.size) {
      val candidate    = aFields(bFieldIndex) // Define candidate HERE
      val candidateTpe = candidate.tpeRepr(using q).dealias
      val bTpe         = bField.tpeRepr(using q).dealias

      // Enhanced Compatibility Check (Identity OR Primitives OR Collections OR Nested OR Option)
      val isCompatible =
        // 1. Exact type match
        (candidateTpe =:= bTpe) ||
          // 2. Primitive coercible types
          (isPrimitive(using q)(candidateTpe) && isPrimitive(using q)(bTpe)) ||
          // 3. Collection types compatibility (List[Int] <-> Vector[Int], etc.)
          {
            (extractCollectionElementType(using q)(candidateTpe), extractCollectionElementType(using q)(bTpe)) match {
              case (Some(aElem), Some(bElem)) =>
                val aElemDealiased = aElem.dealias
                val bElemDealiased = bElem.dealias
                // Elements are compatible (exact match or primitives)
                (aElemDealiased =:= bElemDealiased) ||
                (isPrimitive(using q)(aElemDealiased) && isPrimitive(using q)(bElemDealiased))
              case _ => false
            }
          } ||
          // 4. Nested case classes (basic check - full recursion handled by findOrDeriveInto)
          {
            if (candidateTpe.typeSymbol.isClassDef && bTpe.typeSymbol.isClassDef) {
              // Both are case classes - allow positional match (full compatibility checked recursively)
              true
            } else false
          } ||
          // 5. Option wrapping (A -> Option[A])
          {
            if (isOptionType(using q)(bField.tpeRepr(using q))) {
              extractOptionElementType(using q)(bField.tpeRepr(using q)) match {
                case Some(optElemType) => candidateTpe =:= optElemType.dealias
                case None              => false
              }
            } else false
          }

      // Position is enough if types are compatible
      if (isCompatible) {
        return Some(candidate)
      }
    }

    // Fallback: Return None (caller will check if field is optional)
    None
  }

  private def generateTupleConstruction[B: Type](using
    q: Quotes
  )(
    conversions: List[Expr[Into[Any, Any]]],
    bTpe: q.reflect.TypeRepr,
    bFieldTypes: List[q.reflect.TypeRepr]
  ): Expr[Into[Any, B]] = {

    val arity = bFieldTypes.length

    // Strategy: Use Quotes for type-safe construction (works for 1-22)
    // For > 22, fall back to TupleXXL (like ZIO Schema)
    val constructorLambda: Expr[List[Any] => B] =
      if (arity == 0) {
        // Empty tuple
        '{ (_: List[Any]) => EmptyTuple.asInstanceOf[B] }
      } else if (arity <= 22) {
        // Standard tuples (1-22): Build using Quotes with type-safe pattern
        // This is generic - we generate the code dynamically using Quotes
        buildTupleLambdaGeneric[B](using q)(bTpe, bFieldTypes, arity)
      } else {
        // Large tuples (> 22): Use TupleXXL.fromIArray (like ZIO Schema)
        buildTupleXXLLambda[B](using q)(bTpe, bFieldTypes, arity)
      }

    '{
      new Into[Any, B] {
        def into(a: Any): Either[SchemaError, B] = {
          val p       = a.asInstanceOf[Product]
          val results = ${
            Expr.ofList(conversions.zipWithIndex.map { case (c, i) =>
              '{ $c.into(IntoAsVersionSpecificImpl.getTupleElement[Any](p, ${ Expr(i) })) }
            })
          }

          IntoAsVersionSpecificImpl.sequenceEither(results).map { list =>
            $constructorLambda(list)
          }
        }
      }
    }
  }

  // Helper: Build tuple lambda generically using Quotes (for 1-22)
  private def buildTupleLambdaGeneric[B: Type](using
    q: Quotes
  )(
    bTpe: q.reflect.TypeRepr,
    bFieldTypes: List[q.reflect.TypeRepr],
    _arity: Int
  ): Expr[List[Any] => B] = {
    import q.reflect.*

    val tupleSymbol     = bTpe.typeSymbol
    val companionModule = tupleSymbol.companionModule

    // STRATEGIA 1: Companion Apply (Standard per Tuple 1-22)
    if (companionModule.exists) {
      val listAnyType     = TypeRepr.of[List[Any]]
      val constructLambda = Lambda(
        Symbol.spliceOwner,
        MethodType(List("list"))(_ => List(listAnyType), _ => bTpe),
        (sym, params) => {
          val listParam = params.head.asInstanceOf[Term]

          // Argomenti con cast type-safe
          val constructorArgs = bFieldTypes.zipWithIndex.map { case (fieldType, idx) =>
            val listAccess  = Select.unique(listParam, "apply")
            val indexExpr   = Expr(idx)
            val indexedTerm = Apply(listAccess, List(indexExpr.asTerm))
            val indexedExpr = indexedTerm.asExprOf[Any]
            fieldType.asType match {
              case '[ft] =>
                '{ $indexedExpr.asInstanceOf[ft] }.asTerm.changeOwner(sym)
            }
          }

          // TupleN.apply[T1, ...](args)
          val companionRef = Ref(companionModule)
          val applySelect  = Select.unique(companionRef, "apply")
          val typeApply    = TypeApply(applySelect, bFieldTypes.map(Inferred(_)))
          Apply(typeApply, constructorArgs).changeOwner(sym)
        }
      )
      return constructLambda.asExprOf[List[Any] => B]
    }

    // STRATEGIA 2: Fallback Primary Constructor (per casi rari/custom)
    val constructor = tupleSymbol.primaryConstructor
    if (!constructor.exists) {
      fail(s"Cannot find companion or constructor for tuple ${bTpe.show}")
    }

    val listAnyType     = TypeRepr.of[List[Any]]
    val constructLambda = Lambda(
      Symbol.spliceOwner,
      MethodType(List("list"))(_ => List(listAnyType), _ => bTpe),
      (sym, params) => {
        val listParam       = params.head.asInstanceOf[Term]
        val constructorArgs = bFieldTypes.zipWithIndex.map { case (fieldType, idx) =>
          val listAccess  = Select.unique(listParam, "apply")
          val indexExpr   = Expr(idx)
          val indexedTerm = Apply(listAccess, List(indexExpr.asTerm))
          val indexedExpr = indexedTerm.asExprOf[Any]
          fieldType.asType match {
            case '[ft] => '{ $indexedExpr.asInstanceOf[ft] }.asTerm.changeOwner(sym)
          }
        }
        val newInstance = Select(New(Inferred(bTpe)), constructor)
        Apply(newInstance, constructorArgs).changeOwner(sym)
      }
    )
    constructLambda.asExprOf[List[Any] => B]
  }

  // Helper: Build tuple lambda for large tuples (> 22) using TupleXXL
  private def buildTupleXXLLambda[B: Type](using
    q: Quotes
  )(
    bTpe: q.reflect.TypeRepr,
    bFieldTypes: List[q.reflect.TypeRepr],
    arity: Int
  ): Expr[List[Any] => B] = {
    import q.reflect.*

    // Use TupleXXL.fromIArray pattern (like ZIO Schema)
    val tupleXXLModule     = Symbol.requiredModule("scala.runtime.TupleXXL")
    val fromIArrayMethod   = Select.unique(Ref(tupleXXLModule), "fromIArray")
    val iArrayOfAnyRefTpe  = TypeRepr.of[IArray[AnyRef]]
    val anyTpe             = defn.AnyClass.typeRef
    val arrayClass         = defn.ArrayClass
    val arrayOfAnyTpe      = arrayClass.typeRef.appliedTo(anyTpe)
    val newArrayOfAny      = Select(New(TypeIdent(arrayClass)), arrayClass.primaryConstructor).appliedToType(anyTpe)
    val asInstanceOfMethod = anyTpe.typeSymbol.declaredMethod("asInstanceOf").head

    val listAnyType     = TypeRepr.of[List[Any]]
    val constructLambda = Lambda(
      Symbol.spliceOwner,
      MethodType(List("list"))(_ => List(listAnyType), _ => bTpe),
      (sym, params) => {
        val listParam = params.head.asInstanceOf[Term]

        // Create array symbol and reference
        val arraySymbol = Symbol.newVal(sym, "arr", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
        val arrayRef    = Ref(arraySymbol)

        // Initialize array: new Array[Any](arity)
        val arrayInit   = Apply(newArrayOfAny, List(Literal(IntConstant(arity))))
        val arrayValDef = ValDef(arraySymbol, Some(arrayInit))

        // Fill array: arr(i) = list(i).asInstanceOf[AnyRef]
        val update      = Select(arrayRef, defn.Array_update)
        val assignments = bFieldTypes.zipWithIndex.map { case (_, idx) =>
          val listAccess  = Select.unique(listParam, "apply")
          val indexTerm   = Literal(IntConstant(idx))
          val indexedTerm = Apply(listAccess, List(indexTerm))
          val asAnyRef    = Select(indexedTerm, asInstanceOfMethod)
            .appliedToType(TypeRepr.of[AnyRef])
          Apply(update, List(Literal(IntConstant(idx)), asAnyRef))
        }

        // Build: TupleXXL.fromIArray(arr.asInstanceOf[IArray[AnyRef]]).asInstanceOf[B]
        val block          = Block(arrayValDef :: assignments, arrayRef)
        val iArrayCast     = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
        val fromIArrayCall = Apply(fromIArrayMethod, List(iArrayCast))
        val finalCast      = Select(fromIArrayCall, asInstanceOfMethod).appliedToType(bTpe)
        finalCast.changeOwner(sym)
      }
    )

    constructLambda.asExprOf[List[Any] => B]
  }

  // Generic Accumulator for Products and Tuples
  private def generateEitherAccumulation[B: Type](using
    q: Quotes
  )(
    conversions: List[Expr[Into[Any, Any]]],
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[?, B]] = {
    import q.reflect.*

    // Check if B is a tuple or case class
    val isBTuple = isTuple(using q)(bTpe)

    if (isBTuple) {
      // Use tuple construction
      val bFieldTypes = extractTupleFields(using q)(bTpe)
      generateTupleConstruction[B](using q)(conversions, bTpe, bFieldTypes)
    } else {
      // FIX: Check if B is a singleton (case object or enum case without params)
      if (bTpe.isSingleton) {
        val termSym = bTpe.termSymbol
        if (!termSym.exists) {
          fail(s"Cannot find term symbol for singleton type ${bTpe.show}")
        }
        val singletonRef = Ref(termSym).asExprOf[B]
        return '{
          new Into[Any, B] {
            def into(a: Any): Either[SchemaError, B] = Right($singletonRef)
          }
        }
      }

      // Case class construction - use same approach as generateConversionBodyReal
      val bSym        = bTpe.typeSymbol
      val constructor = bSym.primaryConstructor

      // FIX: Check if constructor exists (singletons might not have primaryConstructor)
      if (!constructor.exists) {
        fail(s"Cannot find primary constructor for ${bTpe.show}. This might be a singleton type.")
      }

      val paramTypes = constructor.paramSymss.flatten.map { param =>
        bTpe.memberType(param).dealias
      }

      // Build lambda: (args: List[Any]) => new B(args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...)
      val listAnyType     = TypeRepr.of[List[Any]]
      val constructLambda = Lambda(
        Symbol.spliceOwner,
        MethodType(List("args"))(_ => List(listAnyType), _ => bTpe),
        (sym, params) => {
          val argsParam = params.head.asInstanceOf[Term]

          // Build constructor arguments: args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...
          val constructorArgs = paramTypes.zipWithIndex.map { case (paramType, idx) =>
            val listAccess  = Select.unique(argsParam, "apply")
            val indexExpr   = Expr(idx)
            val indexedTerm = Apply(listAccess, List(indexExpr.asTerm))

            // Convert Term to Expr before using in Quote
            val indexedExpr = indexedTerm.asExprOf[Any]

            // Cast to parameter type
            paramType.asType match {
              case '[pt] =>
                '{ $indexedExpr.asInstanceOf[pt] }.asTerm.changeOwner(sym)
            }
          }

          // Build: new B(...)
          val newInstance = Select(New(Inferred(bTpe)), constructor)
          Apply(newInstance, constructorArgs).changeOwner(sym)
        }
      )

      '{
        new Into[Any, B] {
          def into(a: Any): Either[SchemaError, B] = {
            val p       = a.asInstanceOf[Product]
            val results = ${
              Expr.ofList(conversions.zipWithIndex.map { case (c, i) =>
                '{ $c.into(p.productElement(${ Expr(i) })) }
              })
            }
            IntoAsVersionSpecificImpl.sequenceEither(results).map { args =>
              ${ constructLambda.asExprOf[List[Any] => B] }(args)
            }
          }
        }
      }
    }
  }

  // REAL IMPLEMENTATION OF generateConversionBody (Simplified for Compilation)
  // We used to have generateEitherAccumulation.
  // Let's restore the one that works with mapAndSequence runtime helper.

  private def generateConversionBodyReal[B: Type](using
    q: Quotes
  )(
    bTpe: q.reflect.TypeRepr,
    fieldConversions: List[(Expr[Into[Any, Any]], Int)]
  ): Expr[Into[Any, B]] = {
    import q.reflect.*

    // FIX: Check if B is a singleton (case object or enum case without params)
    if (bTpe.isSingleton) {
      val termSym = bTpe.termSymbol
      if (!termSym.exists) {
        fail(s"Cannot find term symbol for singleton type ${bTpe.show}")
      }
      val singletonRef = Ref(termSym).asExprOf[B]
      return '{
        new Into[Any, B] {
          def into(a: Any): Either[SchemaError, B] = Right($singletonRef)
        }
      }
    }

    // Get constructor and parameter types
    val bSym        = bTpe.typeSymbol
    val constructor = bSym.primaryConstructor

    // FIX: Check if constructor exists (singletons might not have primaryConstructor)
    if (!constructor.exists) {
      fail(s"Cannot find primary constructor for ${bTpe.show}. This might be a singleton type.")
    }

    val paramTypes = constructor.paramSymss.flatten.map { param =>
      bTpe.memberType(param).dealias
    }

    // Build lambda: (args: List[Any]) => new B(args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...)
    val listAnyType     = TypeRepr.of[List[Any]]
    val constructLambda = Lambda(
      Symbol.spliceOwner,
      MethodType(List("args"))(_ => List(listAnyType), _ => bTpe),
      (sym, params) => {
        val argsParam = params.head.asInstanceOf[Term]

        // Build constructor arguments: args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...
        val constructorArgs = paramTypes.zipWithIndex.map { case (paramType, idx) =>
          val listAccess  = Select.unique(argsParam, "apply")
          val indexExpr   = Expr(idx)
          val indexedTerm = Apply(listAccess, List(indexExpr.asTerm))

          // Convert Term to Expr before using in Quote
          val indexedExpr = indexedTerm.asExprOf[Any]

          // Cast to parameter type
          paramType.asType match {
            case '[pt] =>
              '{ $indexedExpr.asInstanceOf[pt] }.asTerm.changeOwner(sym)
          }
        }

        // Build: new B(...)
        val newInstance = Select(New(Inferred(bTpe)), constructor)
        Apply(newInstance, constructorArgs).changeOwner(sym)
      }
    )

    '{
      new Into[Any, B] {
        def into(a: Any): Either[SchemaError, B] = {
          val p       = a.asInstanceOf[Product]
          val results = ${
            Expr.ofList(fieldConversions.map { case (conv, idx) =>
              if (idx == -1) {
                // Optional field missing - inject None directly
                '{ $conv.into(()) }
              } else {
                '{ $conv.into(p.productElement(${ Expr(idx) })) }
              }
            })
          }

          IntoAsVersionSpecificImpl.sequenceEither(results).map { args =>
            ${ constructLambda.asExprOf[List[Any] => B] }(args)
          }
        }
      }
    }
  }

  // --- Coproduct Helpers ---

  private def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) || flags.is(Flags.Enum)
    }
  }

  private def isCaseClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      symbol.isClassDef &&
      !flags.is(Flags.Sealed) &&
      !flags.is(Flags.Enum) &&
      !flags.is(Flags.Abstract) &&
      !flags.is(Flags.Trait)
    }
  }

  private def detectPotentialRecursion(using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Boolean =
    if (isCaseClass(using q)(aTpe) || isCaseClass(using q)(bTpe)) {
      // Semplificazione sicura: se sono case class complesse, assumiamo potenziale ricorsione
      // per attivare il meccanismo lazy. L'overhead è minimo (lazy val wrapper).
      true
    } else if (isSealedTraitOrEnum(using q)(aTpe) && isSealedTraitOrEnum(using q)(bTpe)) {
      true
    } else {
      false
    }

  // --- Coproduct Derivation ---

  private def deriveCoproductInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import CommonMacroOps.directSubTypes

    // 1. Estrai sottotipi usando directSubTypes
    val aSubtypes = directSubTypes(using q)(aTpe)
    val bSubtypes = directSubTypes(using q)(bTpe)

    if (aSubtypes.isEmpty) {
      fail(s"Cannot find subtypes for coproduct ${aTpe.show}")
    }
    if (bSubtypes.isEmpty) {
      fail(s"Cannot find subtypes for coproduct ${bTpe.show}")
    }

    // Pre-compute error message (must be done before nested quotes)
    val defaultErrorMsg = s"No matching subtype for ${aTpe.show}"
    val defaultError    = Expr(defaultErrorMsg)

    // 2. COSTRUZIONE LAMBDA RICORSIVA
    // Costruiamo la catena dalla fine all'inizio (foldRight)
    // Base case: Funzione che restituisce sempre errore
    val baseErrorFn: Expr[A => Either[SchemaError, B]] = '{ (a: A) =>
      Left(SchemaError.expectationMismatch(IntoAsVersionSpecificImpl.emptyNodeList, $defaultError))
    }

    // Helper per estrarre il nome (gestisce Enum Cases / Singletons)
    def getSubtypeName(tpe: q.reflect.TypeRepr): String = {
      import q.reflect.*
      // Se è un singleton e ha un termSymbol valido (diverso da noSymbol), usa quello
      if (tpe.isSingleton && tpe.termSymbol != Symbol.noSymbol) {
        tpe.termSymbol.name
      } else {
        // Altrimenti usa il typeSymbol (es. nome della classe/trait)
        tpe.typeSymbol.name
      }
    }

    // Build the recursive chain of functions
    val matchFn = aSubtypes.foldRight(baseErrorFn) { (subA, nextFn) =>
      val subAName  = getSubtypeName(subA)
      val matchingB = bSubtypes.find(subB => getSubtypeName(subB) == subAName)

      matchingB match {
        case Some(subB) =>
          (subA.asType, subB.asType) match {
            case ('[sa], '[sb]) =>
              // Deriva la conversione specifica
              val derived = findOrDeriveInto[sa, sb](using q)(ctx, subA, subB)

              // Crea una nuova funzione che wrappa 'nextFn'
              '{ (a: A) =>
                if (a.isInstanceOf[sa]) {
                  ${ derived }.into(a.asInstanceOf[sa]).asInstanceOf[Either[SchemaError, B]]
                } else {
                  // Chiama la funzione successiva nella catena
                  $nextFn(a)
                }
              }
            case _ => nextFn
          }
        case None =>
          // Se non c'è match, genera errore per questo sottotipo
          val errorMsgStr =
            s"Unexpected subtype '${subAName}' in ${aTpe.show}. Available in ${bTpe.show}: ${bSubtypes.map(getSubtypeName).mkString(", ")}"
          val errorMsg = Expr(errorMsgStr)
          subA.asType match {
            case '[sa] =>
              '{ (a: A) =>
                if (a.isInstanceOf[sa]) {
                  Left(SchemaError.expectationMismatch(IntoAsVersionSpecificImpl.emptyNodeList, $errorMsg))
                } else {
                  $nextFn(a)
                }
              }
            case _ => nextFn
          }
      }
    }

    // 3. Genera il corpo finale applicando la funzione
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $matchFn(a)
      }
    }
  }

  // --- Case Class Derivation ---

  private def deriveCaseClassInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    val aFields = extractCaseClassFields(using q)(aTpe)
    val bFields = extractCaseClassFields(using q)(bTpe)

    // NEW: Validate mappings first to detect ambiguities
    validateMappings(using q)(aFields, bFields, aTpe, bTpe) match {
      case Left(errorMsg) =>
        report.errorAndAbort(errorMsg)
      case Right(validatedMappings) =>
        // Build conversions using validated mappings
        val conversionsWithIndex = bFields.zipWithIndex.map { case (bField, bIdx) =>
          validatedMappings.get(bIdx) match {
            case Some(aIdx) =>
              // Normal field mapping (validated)
              val aField = aFields(aIdx)
              val conv   = aField.tpeRepr(using q).asType match {
                case '[af] =>
                  bField.tpeRepr(using q).asType match {
                    case '[bf] =>
                      val intoExpr =
                        findOrDeriveInto[af, bf](using q)(ctx, aField.tpeRepr(using q), bField.tpeRepr(using q))
                      '{ ${ intoExpr }.asInstanceOf[Into[Any, Any]] }
                  }
              }
              (conv, aIdx)
            case None =>
              // Field not in mapping - must be optional
              if (isOptionType(using q)(bField.tpeRepr(using q))) {
                // Generate None for optional field
                extractOptionElementType(using q)(bField.tpeRepr(using q)) match {
                  case Some(elemType) =>
                    elemType.asType match {
                      case '[et] =>
                        val noneExpr: Expr[Into[Any, Any]] = '{
                          new Into[Any, Any] {
                            def into(a: Any): Either[SchemaError, Any] = Right(None: Option[et])
                          }
                        }
                        (noneExpr, -1) // -1 means "no source field, inject None"
                    }
                  case None =>
                    fail(s"Cannot extract element type from Option: ${bField.tpeRepr(using q).show}")
                }
              } else {
                // This should not happen if validation worked correctly
                fail(s"Field '${bField.name}: ${bField.tpeRepr(using q).show}' has no mapping (validation error)")
              }
          }
        }

        // Use Real implementation (simplified above)
        generateConversionBodyReal[B](using q)(bTpe, conversionsWithIndex).asInstanceOf[Expr[Into[A, B]]]
    }
  }

  // --- Main Dispatcher ---

  private def findOrDeriveInto[A: Type, B: Type](using
    q: Quotes
  )(
    ctx: DerivationContext,
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*

    // Step 1: Cycle Check - Check if we're already deriving this type pair
    val aTpeDealiased = aTpe.dealias
    val bTpeDealiased = bTpe.dealias
    val key           = (aTpeDealiased, bTpeDealiased)
    ctx.inProgress.get(key) match {
      case Some(lazySymbol: q.reflect.Symbol @unchecked) =>
        // Cycle detected - return reference to lazy val
        return Ref(lazySymbol).asExprOf[Into[A, B]]
      case None | Some(_) => // Continue with derivation (Some(_) handles non-Symbol values)
    }

    // PRIORITY 0.75: Opaque Types (Must be checked before dealias/primitives to preserve validation logic)
    // Check if B is opaque type (UnderlyingType -> OpaqueType)
    if (isOpaqueType(using q)(bTpe)) {
      return generateOpaqueValidation[A, B](using q)(ctx, aTpe, bTpe)
    }

    // PRIORITY 0.74: Opaque Type to Underlying (OpaqueType -> UnderlyingType)
    // Check if A is opaque type (OpaqueType -> UnderlyingType)
    if (isOpaqueType(using q)(aTpe)) {
      return generateOpaqueToUnderlying[A, B](using q)(aTpe, bTpe)
    }

    // PRIORITY 0.73: ZIO Prelude Newtypes (BEFORE dealias to preserve type structure)
    generateZioPreludeNewtypeConversion[A, B](using q)(ctx, aTpe, bTpe) match {
      case Some(impl) => return impl
      case None       => // continue
    }

    // 1. Primitives (Check first)
    derivePrimitiveInto[A, B](using q)(aTpeDealiased, bTpeDealiased) match {
      case Some(impl) => return impl
      case None       => // continue
    }

    // 2. Implicit Search
    val intoType = TypeRepr.of[Into].appliedTo(List(aTpe, bTpe))
    Implicits.search(intoType) match {
      case iss: ImplicitSearchSuccess => return iss.tree.asExpr.asInstanceOf[Expr[Into[A, B]]]
      case _                          => // continue
    }

    // 3. Singleton cases (Enums/Objects) - MUST be before Coproducts to avoid infinite recursion
    if (aTpe.isSingleton && bTpe.isSingleton) {
      // Both are singletons - check if they have matching term symbols
      val aTermSym = aTpe.termSymbol
      val bTermSym = bTpe.termSymbol
      if (aTermSym != Symbol.noSymbol && bTermSym != Symbol.noSymbol) {
        // Use term symbol names for matching (enum cases, case objects)
        if (aTermSym.name == bTermSym.name) {
          return '{
            new Into[A, B] {
              def into(a: A): Either[SchemaError, B] =
                Right(${ Ref(bTermSym).asExpr.asInstanceOf[Expr[B]] })
            }
          }
        }
      }
    }

    // PRIORITY 0.5: Either and Option (explicit handling to avoid GADT constraint issues)
    if (isEitherType(using q)(aTpeDealiased) && isEitherType(using q)(bTpeDealiased)) {
      return deriveEitherInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased)
    }

    if (isOptionType(using q)(aTpeDealiased) && isOptionType(using q)(bTpeDealiased)) {
      return deriveOptionInto[A, B](using q)(ctx, aTpeDealiased, bTpeDealiased)
    }

    // 4. Collections
    (extractCollectionElementType(using q)(aTpeDealiased), extractCollectionElementType(using q)(bTpeDealiased)) match {
      case (Some(aElem), Some(bElem)) =>
        // FIX: deriveCollectionInto expects collection types [A, B], not element types
        aElem.asType match {
          case '[ae] =>
            bElem.asType match {
              case '[be] =>
                // deriveCollectionInto[A, B] expects A and B to be collection types
                val result = deriveCollectionInto[A, B](using q)(ctx, aTpe, bTpe, aElem, bElem)
                return result
            }
        }
      case _ => // continue
    }

    // 5. Coproducts (Sealed Traits / Enums) - MUST be before Products
    // IMPORTANT: Skip if either type is a singleton (enum case/case object) to avoid infinite recursion
    if (
      !aTpe.isSingleton && !bTpe.isSingleton &&
      isSealedTraitOrEnum(using q)(aTpe) && isSealedTraitOrEnum(using q)(bTpe)
    ) {
      return deriveCoproductInto[A, B](using q)(ctx, aTpe, bTpe)
    }

    // 6. Tuples & Products
    val isATuple = isTuple(using q)(aTpeDealiased)
    val isBTuple = isTuple(using q)(bTpeDealiased)

    if (isATuple && isBTuple) {
      val aFields = extractTupleFields(using q)(aTpe)
      val bFields = extractTupleFields(using q)(bTpe)
      return deriveTupleInto[A, B](using q)(ctx, aTpe, bTpe, aFields, bFields)
    } else if (!isATuple && isBTuple) {
      return deriveCaseClassToTuple[A, B](using q)(ctx, aTpe, bTpe)
    } else if (isATuple && !isBTuple) {
      return deriveTupleToCaseClass[A, B](using q)(ctx, aTpe, bTpe)
    } else if (isCaseClass(using q)(aTpe) && isCaseClass(using q)(bTpe)) {
      // Step 4: Case Classes - Lazy Logic
      if (detectPotentialRecursion(using q)(aTpe, bTpe)) {
        // Potentially recursive - use lazy val pattern
        val lazySymbol = Symbol.newVal(
          Symbol.spliceOwner,
          s"into_${aTpe.typeSymbol.name}_${bTpe.typeSymbol.name}",
          TypeRepr.of[Into[A, B]],
          Flags.Lazy,
          Symbol.noSymbol
        )

        // Add to inProgress BEFORE derivation
        ctx.inProgress.put(key, lazySymbol)

        // Derive the actual implementation
        val derivedImpl = deriveCaseClassInto[A, B](using q)(ctx, aTpe, bTpe)

        // Create ValDef and add to lazyDefs
        // Change owner of the term to match the lazySymbol
        val derivedTerm = derivedImpl.asTerm.changeOwner(lazySymbol)
        val lazyValDef  = ValDef(lazySymbol, Some(derivedTerm))
        ctx.lazyDefs += lazyValDef

        // Return reference to lazy val
        return Ref(lazySymbol).asExprOf[Into[A, B]]
      } else {
        // Not recursive - derive directly
        return deriveCaseClassInto[A, B](using q)(ctx, aTpe, bTpe)
      }
    }

    fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]")
  }

} // End of IntoAsVersionSpecificImpl
