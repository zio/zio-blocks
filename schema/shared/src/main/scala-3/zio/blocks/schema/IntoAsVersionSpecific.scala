package zio.blocks.schema

import scala.quoted.*
import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

trait IntoAsVersionSpecific {
  inline def derived[A, B]: Into[A, B]    = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
  inline def derivedInto[A, B]: Into[A, B] = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
  inline def derivedAs[A, B]: As[A, B]     = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
}

object IntoAsVersionSpecificImpl {

  def derivedAsImpl[A: Type, B: Type](using q: Quotes): Expr[As[A, B]] = {
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

    // 2. Opaque Types Check (BEFORE dealias)
    // Check if B is opaque type (UnderlyingType -> OpaqueType)
    if (isOpaqueType(using q)(bTpe)) {
       return generateOpaqueValidation[A, B](using q)(aTpe, bTpe)
    }
    
    // Check if A is opaque type (OpaqueType -> UnderlyingType)
    if (isOpaqueType(using q)(aTpe)) {
       return generateOpaqueToUnderlying[A, B](using q)(aTpe, bTpe)
    }

    // Dealias for other checks
        val aTpeDealiased = aTpe.dealias
        val bTpeDealiased = bTpe.dealias
        
    // PRIORITY 0: Primitives (Narrowing/Widening)
        derivePrimitiveInto[A, B](using q)(aTpeDealiased, bTpeDealiased) match {
      case Some(impl) => return impl
      case None => // continue
    }

    // PRIORITY 0.5: Either and Option (explicit handling to avoid GADT constraint issues)
    if (isEitherType(using q)(aTpeDealiased) && isEitherType(using q)(bTpeDealiased)) {
      return deriveEitherInto[A, B](using q)(aTpeDealiased, bTpeDealiased)
    }
    
    if (isOptionType(using q)(aTpeDealiased) && isOptionType(using q)(bTpeDealiased)) {
      return deriveOptionInto[A, B](using q)(aTpeDealiased, bTpeDealiased)
    }

    // PRIORITY 1: Collections
    (extractCollectionElementType(using q)(aTpeDealiased), extractCollectionElementType(using q)(bTpeDealiased)) match {
      case (Some(aElem), Some(bElem)) =>
        // FIX: deriveCollectionInto expects collection types [A, B], not element types
        // We need to call it with the actual collection types
        aElem.asType match { case '[ae] =>
          bElem.asType match { case '[be] =>
            // deriveCollectionInto[A, B] expects A and B to be collection types
            // So we pass the collection types, not element types
            val result = deriveCollectionInto[A, B](using q)(aTpeDealiased, bTpeDealiased, aElem, bElem)
            return result
          }
        }
      case _ => // continue
    }
    
    // Fallback to general recursive derivation
    findOrDeriveInto[A, B](using q)(aTpeDealiased, bTpeDealiased)
  }
  
  // -- START OF HELPERS --

  // --- Runtime Helpers ---

  private def fail(msg: String)(using q: Quotes): Nothing = {
    import q.reflect.*
    report.errorAndAbort(msg)
  }

  def sequenceEither[A, B](list: List[Either[A, B]]): Either[A, List[B]] = {
    val acc = new scala.collection.mutable.ListBuffer[B]
    val i = list.iterator
    while (i.hasNext) {
      i.next() match {
        case Right(b) => acc += b
        case Left(a) => return Left(a)
      }
    }
    Right(acc.toList)
  }

  def mapAndSequence[A, B](source: Iterable[A], f: A => Either[SchemaError, B]): Either[SchemaError, List[B]] = {
    val acc = new scala.collection.mutable.ListBuffer[B]
    val i = source.iterator
    while (i.hasNext) {
      f(i.next()) match {
        case Right(b) => acc += b
        case Left(e) => return Left(e)
      }
    }
    Right(acc.toList)
  }
  
  def getTupleElement[A](product: Product, index: Int): A = 
    product.productElement(index).asInstanceOf[A]

  def emptyNodeList: List[zio.blocks.schema.DynamicOptic.Node] = Nil

  // --- Primitive Derivation ---

  private def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr, primitive: q.reflect.TypeRepr): Boolean = {
    tpe.dealias =:= primitive.dealias
  }

  // Helper to check if a type is any primitive type
  private def isPrimitive(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    val dealiased = tpe.dealias
    val intType    = TypeRepr.of[Int]
    val longType   = TypeRepr.of[Long]
    val doubleType = TypeRepr.of[Double]
    val floatType  = TypeRepr.of[Float]
    val booleanType = TypeRepr.of[Boolean]
    val byteType   = TypeRepr.of[Byte]
    val shortType  = TypeRepr.of[Short]
    val charType   = TypeRepr.of[Char]
    val stringType = TypeRepr.of[String]
    
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
  )(using q: Quotes): Expr[Into[A, B]] = {
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $conversion(a)
      }
    }
  }

  // Helper to check if two types are compatible for position-based matching
  // Used in Priority 4: allows exact match or coercible primitives
  private def areTypesCompatibleForPositionMatch(using q: Quotes)(
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

  private def derivePrimitiveInto[A: Type, B: Type](using q: Quotes)(
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
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, intType) && isPrimitiveType(using q)(bTpe, doubleType)) {
      (TypeRepr.of[Int].asType, TypeRepr.of[Double].asType) match {
        case ('[Int], '[Double]) => Some(generatePrimitiveInto[Int, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, intType) && isPrimitiveType(using q)(bTpe, floatType)) {
       (TypeRepr.of[Int].asType, TypeRepr.of[Float].asType) match {
        case ('[Int], '[Float]) => Some(generatePrimitiveInto[Int, Float]('{ a => Right(a.toFloat) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, longType) && isPrimitiveType(using q)(bTpe, doubleType)) {
       (TypeRepr.of[Long].asType, TypeRepr.of[Double].asType) match {
        case ('[Long], '[Double]) => Some(generatePrimitiveInto[Long, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
        case _ => None
      }
    } else if (isPrimitiveType(using q)(aTpe, floatType) && isPrimitiveType(using q)(bTpe, doubleType)) {
       (TypeRepr.of[Float].asType, TypeRepr.of[Double].asType) match {
        case ('[Float], '[Double]) => Some(generatePrimitiveInto[Float, Double]('{ a => Right(a.toDouble) }).asExprOf[Into[A, B]])
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
             if (f.isInfinite && !a.isInfinite) Left(SchemaError.expectationMismatch(Nil, s"Double value $a out of Float range"))
             else Right(f)
           }).asExprOf[Into[A, B]])
        case _ => None
      }
    }
    else None
  }

  // --- Opaque Types ---

  private def isOpaqueType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe match {
      case tr: TypeRef => tr.isOpaqueAlias || tr.typeSymbol.flags.is(Flags.Opaque)
      case _ => false
    }
  }

  private def isOptionType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.dealias match {
      case AppliedType(optionType, _) if optionType.typeSymbol.fullName == "scala.Option" => true
      case _ => false
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
      case _ => false
    }
  }

  private def extractEitherTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[(q.reflect.TypeRepr, q.reflect.TypeRepr)] = {
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

  private def extractUnderlyingType(using q: Quotes)(
    opaqueType: q.reflect.TypeRepr,
    companion: Option[q.reflect.Symbol]
  ): q.reflect.TypeRepr = {
    import q.reflect.*
    opaqueType match {
      case tr: TypeRef if tr.isOpaqueAlias => tr.translucentSuperType.dealias
      case tr: TypeRef => 
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
               case _ => fail(s"Cannot extract underlying from ${opaqueType.show} without companion")
             }
         }
      case _ => fail(s"Cannot extract underlying from ${opaqueType.show}")
    }
  }

  private def generateOpaqueValidation[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*
    
    val bSym = bTpe.typeSymbol
    val companion = findCompanionModule(using q)(bSym).getOrElse(fail(s"No companion for opaque ${bTpe.show}"))
    val applyMethod = companion.memberMethod("apply").find(s => s.paramSymss.flatten.size == 1)
        .getOrElse(fail(s"No apply/1 in companion of ${bTpe.show}"))
    
    val underlyingType = extractUnderlyingType(using q)(bTpe, Some(companion))
    
    // Check coercion A -> Underlying
    val coercionIntoExpr: Expr[Into[A, Any]] = 
      if (aTpe =:= underlyingType) '{ 
        new Into[A, Any] { def into(x: A) = Right(x: Any) } 
      } else {
        aTpe.asType match {
          case '[a] =>
            underlyingType.asType match {
              case '[u] =>
                // FIX: We need to properly cast the result
                // findOrDeriveInto[a, u] returns Into[a, u], but we need Into[A, Any]
                // We wrap it in a new Into that does the casting
                val innerInto = findOrDeriveInto[a, u](using q)(aTpe, underlyingType)
                '{
                  new Into[A, Any] {
                    def into(x: A): Either[SchemaError, Any] = {
                      ${innerInto}.into(x.asInstanceOf[a]).map(_.asInstanceOf[Any])
                    }
                  }
                }
            }
        }
      }

    bTpe.asType match {
      case '[b] =>
        val returnType = TypeRepr.of[Either].appliedTo(List(TypeRepr.of[SchemaError], bTpe))
        val anyType = TypeRepr.of[Any]
        
        // 1. AST for dynamic companion apply
        val companionRef = Ref(companion)
        val applySelect = Select(companionRef, applyMethod)
        
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
                  val castTerm = '{ $uExpr.asInstanceOf[u] }.asTerm.changeOwner(sym)
                  val applyCall = Apply(applySelect, List(castTerm))
                  val applyExpr = applyCall.asExprOf[Either[String, b]]
                  
                  '{
                     $applyExpr.left.map(msg => SchemaError.expectationMismatch(IntoAsVersionSpecificImpl.emptyNodeList, msg))
                  }.asTerm.changeOwner(sym)
              }
           }
        )
        val validationFn = lambda.asExprOf[Any => Either[SchemaError, b]]
        
        '{
          new Into[A, B] {
             def into(a: A): Either[SchemaError, B] = {
               val uEither = ${coercionIntoExpr}.asInstanceOf[Into[A, Any]].into(a)
               uEither.flatMap(u => $validationFn(u).asInstanceOf[Either[SchemaError, B]])
             }
           }
        }
    }
  }

  // Generate conversion from OpaqueType to UnderlyingType
  // This is the inverse of generateOpaqueValidation
  private def generateOpaqueToUnderlying[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    
    val aSym = aTpe.typeSymbol
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
        def into(a: A): Either[SchemaError, B] = {
          // Safe cast: opaque types are runtime-compatible with underlying type
          // Cast through Any to avoid type mismatch issues
          Right(a.asInstanceOf[Any].asInstanceOf[B])
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
         case _ => None
       }
    } else None
  }
  
  private def getCollectionFactory(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect.*
    if (tpe <:< TypeRepr.of[List[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.List"))
    else if (tpe <:< TypeRepr.of[Vector[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Vector"))
    else if (tpe <:< TypeRepr.of[Set[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Set"))
    else if (tpe <:< TypeRepr.of[Seq[?]]) Ref(Symbol.requiredModule("scala.collection.immutable.Seq"))
    else tpe.typeSymbol.companionModule match {
       case m if m.exists => Ref(m)
       case _ => fail(s"Cannot find factory for ${tpe.show}")
    }
  }

  private def deriveCollectionInto[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr,
    aElem: q.reflect.TypeRepr, bElem: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*
    
    // Recursively derive element conversion
    aElem.asType match { case '[ae] =>
      bElem.asType match { case '[be] =>
         val elementInto = findOrDeriveInto[ae, be](using q)(aElem, bElem)
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
             val companion = getCollectionFactory(using q)(bTpe)
             val fromMethod = companion.symbol.memberMethod("from").head
             
             // Build lambda using AST to avoid scope issues
             val listBeType = TypeRepr.of[List].appliedTo(List(TypeRepr.of[be]))
             val buildLambda = Lambda(
               Symbol.spliceOwner,
               MethodType(List("list"))(_ => List(listBeType), _ => bTpe),
               (sym, params) => {
                 val listParam = params.head.asInstanceOf[Term]
                 val fromSelect = Select(companion, fromMethod)
                 val typeApply = TypeApply(fromSelect, List(TypeTree.of[be]))
                 val applyCall = Apply(typeApply, List(listParam))
                 applyCall.changeOwner(sym)
               }
             )
             buildLambda.asExprOf[List[be] => B]
           }

         '{
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
                 case Left(e) => Left(e)
               }
             }
           }
         }
      }
    }
  }

  // --- Either & Option (Explicit handling to avoid GADT constraint issues) ---

  private def deriveEitherInto[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    
    (extractEitherTypes(using q)(aTpe), extractEitherTypes(using q)(bTpe)) match {
      case (Some((aLeft, aRight)), Some((bLeft, bRight))) =>
        // Derive left type coercion (if needed)
        val leftIntoOpt = if (aLeft =:= bLeft) {
          None // No coercion needed
        } else {
          aLeft.asType match { case '[al] =>
            bLeft.asType match { case '[bl] =>
              Some(findOrDeriveInto[al, bl](using q)(aLeft, bLeft))
            }
          }
        }
        
        // Derive right type coercion
        aRight.asType match { case '[ar] =>
          bRight.asType match { case '[br] =>
            val rightInto = findOrDeriveInto[ar, br](using q)(aRight, bRight)
            
            leftIntoOpt match {
              case Some(leftInto) =>
                // Both left and right need coercion
                aLeft.asType match { case '[al] =>
                  bLeft.asType match { case '[bl] =>
                    val leftIntoTyped = leftInto.asExprOf[Into[al, bl]]
                    '{
                      new Into[A, B] {
                        def into(a: A): Either[SchemaError, B] = {
                          a.asInstanceOf[Either[al, ar]] match {
                            case Left(l) => 
                              ${leftIntoTyped}.into(l).map((lt: bl) => Left(lt).asInstanceOf[B])
                            case Right(r) => 
                              ${rightInto}.into(r).map((rt: br) => Right(rt).asInstanceOf[B])
                          }
                        }
                      }
                    }
                  }
                }
              case None =>
                // Only right needs coercion
                aLeft.asType match { case '[al] =>
                  bLeft.asType match { case '[bl] =>
                    val rightIntoTyped = rightInto.asExprOf[Into[ar, br]]
                    '{
                      new Into[A, B] {
                        def into(a: A): Either[SchemaError, B] = {
                          a.asInstanceOf[Either[al, ar]] match {
                            case Left(l) => 
                              Right(Left(l).asInstanceOf[Either[bl, br]].asInstanceOf[B])
                            case Right(r) => 
                              ${rightIntoTyped}.into(r).map((rt: br) => Right(rt).asInstanceOf[B])
                          }
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
  }

  private def deriveOptionInto[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*
    
    (extractOptionElementType(using q)(aTpe), extractOptionElementType(using q)(bTpe)) match {
      case (Some(aElem), Some(bElem)) =>
        aElem.asType match { case '[ae] =>
          bElem.asType match { case '[be] =>
            // Check if implicit Into exists first (supports custom conversions)
            val intoType = TypeRepr.of[Into].appliedTo(List(aElem, bElem))
            Implicits.search(intoType) match {
              case iss: ImplicitSearchSuccess =>
                // Use implicit (custom conversion provided by user)
                val implicitInto = iss.tree.asExpr.asInstanceOf[Expr[Into[ae, be]]]
                '{
                  new Into[A, B] {
                    def into(a: A): Either[SchemaError, B] = {
                      a.asInstanceOf[Option[ae]] match {
                        case None => Right(None.asInstanceOf[B])
                        case Some(value) => 
                          ${implicitInto}.into(value).map((v: be) => Some(v).asInstanceOf[B])
                      }
                    }
                  }
                }
              case _ =>
                // Try automatic derivation (fail-fast if not possible)
                val elemInto = findOrDeriveInto[ae, be](using q)(aElem, bElem)
                val elemIntoTyped = elemInto.asExprOf[Into[ae, be]]
                
                '{
                  new Into[A, B] {
                    def into(a: A): Either[SchemaError, B] = {
                      a.asInstanceOf[Option[ae]] match {
                        case None => Right(None.asInstanceOf[B])
                        case Some(value) => 
                          ${elemIntoTyped}.into(value).map((v: be) => Some(v).asInstanceOf[B])
                      }
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

  private def extractTupleFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    tpe.asType match {
      case '[t] =>
        if (isTuple(using q)(tpe)) tpe.typeArgs
        else Nil
    }
  }

  private def deriveTupleInto[A: Type, B: Type](using q: Quotes)(
      aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr,
      aFields: List[q.reflect.TypeRepr], bFields: List[q.reflect.TypeRepr]
  ): Expr[Into[A, B]] = {
    
    if (aFields.size != bFields.size) 
       fail(s"Arity mismatch: ${aTpe.show} has ${aFields.size}, ${bTpe.show} has ${bFields.size}")

    // Conversions for each element by position
    val fieldConversions = aFields.zip(bFields).zipWithIndex.map { case ((aField, bField), _) =>
       aField.asType match {
         case '[af] =>
           bField.asType match {
             case '[bf] =>
               val intoExpr = findOrDeriveInto[af, bf](using q)(aField, bField)
               '{ ${intoExpr}.asInstanceOf[Into[Any, Any]] }
           }
       }
    }

    bTpe.asType match { case '[bTuple] =>
      // Generate construction: map fields and sequence
      val conversionExprs = fieldConversions.map { conv =>
         conv
      }
      
      generateTupleConstruction[B](using q)(conversionExprs, bTpe, bFields).asInstanceOf[Expr[Into[A, B]]]
    }
  }

  private def deriveCaseClassToTuple[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
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
               val intoExpr = findOrDeriveInto[af, bt](using q)(aField.tpeRepr(using q), bType)
               '{ ${intoExpr}.asInstanceOf[Into[Any, Any]] }
           }
       }
    }
    
    generateTupleConstruction[B](using q)(conversions, bTpe, bFieldTypes).asInstanceOf[Expr[Into[A, B]]]
  }

  private def deriveTupleToCaseClass[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
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
               val intoExpr = findOrDeriveInto[at, bf](using q)(aType, bField.tpeRepr(using q))
               '{ ${intoExpr}.asInstanceOf[Into[Any, Any]] }
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
    val sym = tpe.typeSymbol
    val fields = sym.primaryConstructor.paramSymss.flatten
    fields.map { field =>
      new FieldInfo(field.name, tpe.memberType(field).dealias)
    }
  }

  // Disambiguation Logic (Phase 7 + Priority 4)
  private def findMatchingField(using q: Quotes)(
      aFields: List[FieldInfo], 
      bField: FieldInfo,
      bFieldIndex: Int, // Index for Priority 4
      allBFields: List[FieldInfo] // All B fields for uniqueness check
  ): Option[FieldInfo] = {
    
    // Priority 1: Exact Name Match
    val nameMatches = aFields.filter(_.name == bField.name)
    nameMatches.find(_.tpeRepr(using q) =:= bField.tpeRepr(using q)) match {
      case Some(exact) => return Some(exact)
      case None => // continue
    }
    
    // Priority 2: Name Match with Coercion (handled by derivation check later, just return name match)
    if (nameMatches.size == 1) return Some(nameMatches.head)
    
    // Priority 3: Unique Type Match (relaxed: unique in A OR in B)
    val typeMatches = aFields.filter(_.tpeRepr(using q) =:= bField.tpeRepr(using q))
    val bTpeDealiased = bField.tpeRepr(using q).dealias
    val isUniqueInB = allBFields.count(_.tpeRepr(using q).dealias =:= bTpeDealiased) == 1
    
    if (typeMatches.size == 1) {
      // Single match in A: works if unique in A OR in B
      val isUniqueInA = true // typeMatches.size == 1 implies unique in A
      if (isUniqueInA || isUniqueInB) return Some(typeMatches.head)
    } else if (typeMatches.size > 1 && isUniqueInB) {
      // Multiple matches in A, but unique in B: use positional match
      if (bFieldIndex >= 0 && bFieldIndex < aFields.size) {
        val candidate = aFields(bFieldIndex)
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
      val candidate = aFields(bFieldIndex) // Define candidate HERE
      val candidateTpe = candidate.tpeRepr(using q).dealias
      val bTpe = bField.tpeRepr(using q).dealias
      
      // Enhanced Compatibility Check (Identity OR Primitives OR Collections OR Nested OR Option)
      val isCompatible = {
        // 1. Exact type match
        (candidateTpe =:= bTpe) ||
        // 2. Primitive coercible types
        (isPrimitive(using q)(candidateTpe) && isPrimitive(using q)(bTpe)) ||
        // 3. Collection types compatibility (List[Int] <-> Vector[Int], etc.)
        {
          (extractCollectionElementType(using q)(candidateTpe), 
           extractCollectionElementType(using q)(bTpe)) match {
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
              case None => false
            }
          } else false
        }
      }
      
      // Position is enough if types are compatible
      if (isCompatible) {
        return Some(candidate)
      }
    }
    
    // Fallback: Return None (caller will check if field is optional)
    None
  }

  private def generateTupleConstruction[B: Type](using q: Quotes)(
      conversions: List[Expr[Into[Any, Any]]],
      bTpe: q.reflect.TypeRepr,
      bFieldTypes: List[q.reflect.TypeRepr]
  ): Expr[Into[Any, B]] = {
    
    // Strategia F: Costruzione inline della lambda nel quote per evitare scope issues
    // La lambda viene definita come Expr[List[Any] => B] e usata direttamente nel quote principale
    val arity = bFieldTypes.length
    
    // Definisci la lambda constructor basata su arity
    val constructorLambda: Expr[List[Any] => B] = arity match {
      case 1 => '{ (list: List[Any]) => Tuple1(list(0)).asInstanceOf[B] }
      case 2 => '{ (list: List[Any]) => (list(0), list(1)).asInstanceOf[B] }
      case 3 => '{ (list: List[Any]) => (list(0), list(1), list(2)).asInstanceOf[B] }
      case 4 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3)).asInstanceOf[B] }
      case 5 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4)).asInstanceOf[B] }
      case 6 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5)).asInstanceOf[B] }
      case 7 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6)).asInstanceOf[B] }
      case 8 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7)).asInstanceOf[B] }
      case 9 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8)).asInstanceOf[B] }
      case 10 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9)).asInstanceOf[B] }
      case 11 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10)).asInstanceOf[B] }
      case 12 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11)).asInstanceOf[B] }
      case 13 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12)).asInstanceOf[B] }
      case 14 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13)).asInstanceOf[B] }
      case 15 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14)).asInstanceOf[B] }
      case 16 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15)).asInstanceOf[B] }
      case 17 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16)).asInstanceOf[B] }
      case 18 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16), list(17)).asInstanceOf[B] }
      case 19 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16), list(17), list(18)).asInstanceOf[B] }
      case 20 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16), list(17), list(18), list(19)).asInstanceOf[B] }
      case 21 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16), list(17), list(18), list(19), list(20)).asInstanceOf[B] }
      case 22 => '{ (list: List[Any]) => (list(0), list(1), list(2), list(3), list(4), list(5), list(6), list(7), list(8), list(9), list(10), list(11), list(12), list(13), list(14), list(15), list(16), list(17), list(18), list(19), list(20), list(21)).asInstanceOf[B] }
      case n => fail(s"Tuple arity $n not supported (max 22)")
    }
    
    '{
      new Into[Any, B] {
        def into(a: Any): Either[SchemaError, B] = {
          val p = a.asInstanceOf[Product]
          val results = ${Expr.ofList(conversions.zipWithIndex.map { case (c, i) =>
            '{ $c.into(IntoAsVersionSpecificImpl.getTupleElement[Any](p, ${Expr(i)})) }
          })}
          
          IntoAsVersionSpecificImpl.sequenceEither(results).map { list =>
            $constructorLambda(list)
          }
        }
      }
    }
  }

  // Generic Accumulator for Products and Tuples
  private def generateEitherAccumulation[B: Type](using q: Quotes)(
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
      val bSym = bTpe.typeSymbol
      val constructor = bSym.primaryConstructor
      
      // FIX: Check if constructor exists (singletons might not have primaryConstructor)
      if (!constructor.exists) {
        fail(s"Cannot find primary constructor for ${bTpe.show}. This might be a singleton type.")
      }
      
      val paramTypes = constructor.paramSymss.flatten.map { param =>
        bTpe.memberType(param).dealias
      }
      
      // Build lambda: (args: List[Any]) => new B(args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...)
      val listAnyType = TypeRepr.of[List[Any]]
      val constructLambda = Lambda(
        Symbol.spliceOwner,
        MethodType(List("args"))(_ => List(listAnyType), _ => bTpe),
        (sym, params) => {
          val argsParam = params.head.asInstanceOf[Term]
          
          // Build constructor arguments: args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...
          val constructorArgs = paramTypes.zipWithIndex.map { case (paramType, idx) =>
            val listAccess = Select.unique(argsParam, "apply")
            val indexExpr = Expr(idx)
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
            val p = a.asInstanceOf[Product]
            val results = ${Expr.ofList(conversions.zipWithIndex.map { case (c, i) =>
               '{ $c.into(p.productElement(${Expr(i)})) }
            })}
            IntoAsVersionSpecificImpl.sequenceEither(results).map { args =>
               ${constructLambda.asExprOf[List[Any] => B]}(args)
            }
          }
        }
      }
    }
  }

  // REAL IMPLEMENTATION OF generateConversionBody (Simplified for Compilation)
  // We used to have generateEitherAccumulation.
  // Let's restore the one that works with mapAndSequence runtime helper.
  
  private def generateConversionBodyReal[B: Type](using q: Quotes)(
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
    val bSym = bTpe.typeSymbol
    val constructor = bSym.primaryConstructor
    
    // FIX: Check if constructor exists (singletons might not have primaryConstructor)
    if (!constructor.exists) {
      fail(s"Cannot find primary constructor for ${bTpe.show}. This might be a singleton type.")
    }
    
    val paramTypes = constructor.paramSymss.flatten.map { param =>
      bTpe.memberType(param).dealias
    }
    
    // Build lambda: (args: List[Any]) => new B(args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...)
    val listAnyType = TypeRepr.of[List[Any]]
    val constructLambda = Lambda(
      Symbol.spliceOwner,
      MethodType(List("args"))(_ => List(listAnyType), _ => bTpe),
      (sym, params) => {
        val argsParam = params.head.asInstanceOf[Term]
        
        // Build constructor arguments: args(0).asInstanceOf[Type1], args(1).asInstanceOf[Type2], ...
        val constructorArgs = paramTypes.zipWithIndex.map { case (paramType, idx) =>
          val listAccess = Select.unique(argsParam, "apply")
          val indexExpr = Expr(idx)
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
           val p = a.asInstanceOf[Product]
           val results = ${Expr.ofList(fieldConversions.map { case (conv, idx) =>
              if (idx == -1) {
                // Optional field missing - inject None directly
                '{ $conv.into(()) }
              } else {
                '{ $conv.into(p.productElement(${Expr(idx)})) }
              }
           })}
           
           IntoAsVersionSpecificImpl.sequenceEither(results).map { args =>
              ${constructLambda.asExprOf[List[Any] => B]}(args)
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

  // --- Coproduct Derivation ---

  private def deriveCoproductInto[A: Type, B: Type](using q: Quotes)(
    aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
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
    val defaultError = Expr(defaultErrorMsg)
    
    // 2. COSTRUZIONE LAMBDA RICORSIVA
    // Costruiamo la catena dalla fine all'inizio (foldRight)
    // Base case: Funzione che restituisce sempre errore
    val baseErrorFn: Expr[A => Either[SchemaError, B]] = '{ 
      (a: A) => Left(SchemaError.expectationMismatch(IntoAsVersionSpecificImpl.emptyNodeList, $defaultError))
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
      val subAName = getSubtypeName(subA)
      val matchingB = bSubtypes.find(subB => getSubtypeName(subB) == subAName)
      
      matchingB match {
        case Some(subB) =>
          (subA.asType, subB.asType) match {
            case ('[sa], '[sb]) =>
              // Deriva la conversione specifica
              val derived = findOrDeriveInto[sa, sb](using q)(subA, subB)
              
              // Crea una nuova funzione che wrappa 'nextFn'
              '{
                (a: A) =>
                  if (a.isInstanceOf[sa]) {
                    ${derived}.into(a.asInstanceOf[sa]).asInstanceOf[Either[SchemaError, B]]
                  } else {
                    // Chiama la funzione successiva nella catena
                    $nextFn(a)
                  }
              }
            case _ => nextFn
          }
        case None =>
          // Se non c'è match, genera errore per questo sottotipo
          val errorMsgStr = s"Unexpected subtype '${subAName}' in ${aTpe.show}. Available in ${bTpe.show}: ${bSubtypes.map(getSubtypeName).mkString(", ")}"
          val errorMsg = Expr(errorMsgStr)
          subA.asType match {
            case '[sa] =>
              '{
                (a: A) =>
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

  // --- Main Dispatcher ---

  private def findOrDeriveInto[A: Type, B: Type](using q: Quotes)(
      aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect.*
    
    // PRIORITY 0.75: Opaque Types (Must be checked before dealias/primitives to preserve validation logic)
    // Check if B is opaque type (UnderlyingType -> OpaqueType)
    if (isOpaqueType(using q)(bTpe)) {
      return generateOpaqueValidation[A, B](using q)(aTpe, bTpe)
    }
    
    // PRIORITY 0.74: Opaque Type to Underlying (OpaqueType -> UnderlyingType)
    // Check if A is opaque type (OpaqueType -> UnderlyingType)
    if (isOpaqueType(using q)(aTpe)) {
      return generateOpaqueToUnderlying[A, B](using q)(aTpe, bTpe)
    }
    
    // Dealias for other checks
    val aTpeDealiased = aTpe.dealias
    val bTpeDealiased = bTpe.dealias
    
    // 1. Primitives (Check first)
    derivePrimitiveInto[A, B](using q)(aTpeDealiased, bTpeDealiased) match {
      case Some(impl) => return impl
      case None => // continue
    }

    // 2. Implicit Search
    val intoType = TypeRepr.of[Into].appliedTo(List(aTpe, bTpe))
    Implicits.search(intoType) match {
      case iss: ImplicitSearchSuccess => return iss.tree.asExpr.asInstanceOf[Expr[Into[A, B]]]
      case _ => // continue
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
                Right(${Ref(bTermSym).asExpr.asInstanceOf[Expr[B]]})
            }
          }
        }
      }
    }

    // PRIORITY 0.5: Either and Option (explicit handling to avoid GADT constraint issues)
    if (isEitherType(using q)(aTpeDealiased) && isEitherType(using q)(bTpeDealiased)) {
      return deriveEitherInto[A, B](using q)(aTpeDealiased, bTpeDealiased)
    }
    
    if (isOptionType(using q)(aTpeDealiased) && isOptionType(using q)(bTpeDealiased)) {
      return deriveOptionInto[A, B](using q)(aTpeDealiased, bTpeDealiased)
    }

    // 4. Collections
    (extractCollectionElementType(using q)(aTpeDealiased), extractCollectionElementType(using q)(bTpeDealiased)) match {
      case (Some(aElem), Some(bElem)) =>
        // FIX: deriveCollectionInto expects collection types [A, B], not element types
        aElem.asType match { case '[ae] =>
          bElem.asType match { case '[be] =>
            // deriveCollectionInto[A, B] expects A and B to be collection types
            val result = deriveCollectionInto[A, B](using q)(aTpe, bTpe, aElem, bElem)
            return result
          }
        }
      case _ => // continue
    }

    // 5. Coproducts (Sealed Traits / Enums) - MUST be before Products
    // IMPORTANT: Skip if either type is a singleton (enum case/case object) to avoid infinite recursion
    if (!aTpe.isSingleton && !bTpe.isSingleton && 
        isSealedTraitOrEnum(using q)(aTpe) && isSealedTraitOrEnum(using q)(bTpe)) {
      return deriveCoproductInto[A, B](using q)(aTpe, bTpe)
    }

    // 6. Tuples & Products
    val isATuple = isTuple(using q)(aTpeDealiased)
    val isBTuple = isTuple(using q)(bTpeDealiased)
    
    if (isATuple && isBTuple) {
      val aFields = extractTupleFields(using q)(aTpe)
      val bFields = extractTupleFields(using q)(bTpe)
      return deriveTupleInto[A, B](using q)(aTpe, bTpe, aFields, bFields)
    } else if (!isATuple && isBTuple) {
      return deriveCaseClassToTuple[A, B](using q)(aTpe, bTpe)
    } else if (isATuple && !isBTuple) {
      return deriveTupleToCaseClass[A, B](using q)(aTpe, bTpe)
    } else if (isCaseClass(using q)(aTpe) && isCaseClass(using q)(bTpe)) {
       // Case Class to Case Class
       val aFields = extractCaseClassFields(using q)(aTpe)
       val bFields = extractCaseClassFields(using q)(bTpe)
       
       val conversionsWithIndex = bFields.zipWithIndex.map { case (bField, bIdx) =>
          findMatchingField(using q)(aFields, bField, bIdx, bFields) match {
            case Some(aField) =>
              // Normal field mapping
              val aIdx = aFields.indexOf(aField)
              val conv = aField.tpeRepr(using q).asType match {
                case '[af] =>
                  bField.tpeRepr(using q).asType match {
                    case '[bf] =>
                      val intoExpr = findOrDeriveInto[af, bf](using q)(aField.tpeRepr(using q), bField.tpeRepr(using q))
                      '{ ${intoExpr}.asInstanceOf[Into[Any, Any]] }
                  }
              }
              (conv, aIdx)
            case None =>
              // Field not found - check if it's optional
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
                // Required field missing - fail
                fail(s"Cannot find unique mapping for field '${bField.name}: ${bField.tpeRepr(using q).show}'. Available: ${aFields.map(f => s"${f.name}: ${f.tpeRepr(using q).show}").mkString(", ")}")
              }
          }
       }
       
       // Use Real implementation (simplified above)
       return generateConversionBodyReal[B](using q)(bTpe, conversionsWithIndex).asInstanceOf[Expr[Into[A, B]]]
    }

    fail(s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]")
  }

} // End of IntoAsVersionSpecificImpl

