package zio.blocks.schema.derive

import scala.quoted.*

/**
 * Macros for handling opaque type conversions in Into derivation.
 * 
 * This module extracts the opaque type conversion logic from Into.scala
 * to make debugging and maintenance easier.
 */
object OpaqueMacros {
  
  /**
   * Attempts to derive an Into[A, B] instance for opaque type conversions.
   * 
   * Handles three cases:
   * 1. Target is opaque type, source is underlying type (wrapping with validation)
   * 2. Source is opaque type, target is underlying type (unwrapping)
   * 3. Both are opaque types with same underlying type (direct cast)
   * 
   * @return Some(expr) if conversion is possible, None otherwise
   */
  def opaqueTypeConversion[A: Type, B: Type](using Quotes)(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {
    import quotes.reflect.*
    
    // Helper to check if a type is an opaque type alias
    def isOpaqueAlias(tpe: TypeRepr): Boolean = tpe match {
      case tr: TypeRef => tr.isOpaqueAlias
      case _ => false
    }
    
    // Helper to get underlying type of opaque type
    def getUnderlyingType(tpe: TypeRepr): Option[TypeRepr] = tpe match {
      case tr: TypeRef if tr.isOpaqueAlias =>
        Some(tr.translucentSuperType.dealias)
      case _ => None
    }
    
    // Helper to robustly check if a type is Either[String, ?] or Either[?, ?]
    def isEitherType(tpe: TypeRepr): Boolean = {
      val dealiased = tpe.dealias
      
      // First check: is it an AppliedType with Either?
      val isAppliedEither = dealiased match {
        case AppliedType(eitherType, _) if eitherType.typeSymbol.fullName == "scala.util.Either" => true
        case _ => false
      }
      
      if (isAppliedEither) {
        true
      } else {
        // Second check: subtype relationship with wildcards
        val isSubtypeEither = try {
          dealiased <:< TypeRepr.of[Either[Any, Any]] ||
          dealiased <:< TypeRepr.of[Either[?, ?]] ||
          tpe <:< TypeRepr.of[Either[Any, Any]] ||
          tpe <:< TypeRepr.of[Either[?, ?]]
        } catch {
          case _ => false
        }
        
        isSubtypeEither
      }
    }
    
    // Helper to check if return type is Either[String, Target]
    // Fixed to handle opaque types correctly using typeSymbol comparison
    def isEitherStringTarget(returnType: TypeRepr, targetType: TypeRepr): Boolean = {
      val dealiased = returnType.dealias
      
      dealiased match {
        case AppliedType(eitherType, typeArgs) if eitherType.typeSymbol.fullName == "scala.util.Either" && typeArgs.size == 2 =>
          val leftType = typeArgs(0)
          val rightType = typeArgs(1)
          
          // Check that left type is String
          val leftIsString = leftType =:= TypeRepr.of[String] || leftType.dealias =:= TypeRepr.of[String].dealias
          
          // Check that right type matches target - with opaque type identity fix
          val rightMatchesTarget = {
            // Standard type equality checks
            rightType =:= targetType ||
            rightType.dealias =:= targetType.dealias ||
            // Opaque Type Identity Fix: compare typeSymbol for opaque types
            ((rightType, targetType) match {
              case (tr1: TypeRef, tr2: TypeRef) if tr1.isOpaqueAlias && tr2.isOpaqueAlias =>
                tr1.typeSymbol == tr2.typeSymbol ||
                tr1.typeSymbol.fullName == tr2.typeSymbol.fullName
              case _ => false
            }) ||
            // Subtyping checks
            targetType <:< rightType ||
            targetType.dealias <:< rightType.dealias ||
            rightType <:< targetType ||
            rightType.dealias <:< targetType.dealias
          }
          
          leftIsString && rightMatchesTarget
        case _ =>
          // Fallback: check if it's a subtype of Either[String, Target]
          try {
            returnType <:< TypeRepr.of[Either[String, Any]] ||
            (returnType <:< TypeRepr.of[Either[?, ?]] && {
              // Try to extract the right type
              dealiased match {
                case AppliedType(_, typeArgs) if typeArgs.size == 2 =>
                  val rightType = typeArgs(1)
                  // Use same opaque-aware comparison
                  rightType =:= targetType ||
                  rightType.dealias =:= targetType.dealias ||
                  ((rightType, targetType) match {
                    case (tr1: TypeRef, tr2: TypeRef) if tr1.isOpaqueAlias && tr2.isOpaqueAlias =>
                      tr1.typeSymbol == tr2.typeSymbol ||
                      tr1.typeSymbol.fullName == tr2.typeSymbol.fullName
                    case _ => false
                  }) ||
                  targetType <:< rightType ||
                  targetType.dealias <:< rightType.dealias ||
                  rightType <:< targetType ||
                  rightType.dealias <:< targetType.dealias
                case _ => false
              }
            })
          } catch {
            case _ => false
          }
      }
    }
    
    // Case 1: Target is opaque type, source is underlying type
    val case1Result = if (isOpaqueAlias(target)) {
      val underlyingOpt = getUnderlyingType(target)
      
      underlyingOpt.flatMap { underlying =>
        if (source =:= underlying || source.dealias =:= underlying.dealias) {
          // Try to find companion object for target type
          val companionSymbol = target.typeSymbol.companionModule
          
          if (companionSymbol != Symbol.noSymbol) {
            // Robust lookup: Get ALL apply methods from companion object
            // Don't use Select.unique as it fails with overloading
            val allApplyMethods = companionSymbol.declarations
              .filter(_.name == "apply")
              .filter(_.isDefDef) // Only defined methods, not inherited
            
            report.info(s"[OpaqueMacros] Companion: ${companionSymbol.name}, Found ${allApplyMethods.size} apply methods")
            
            // Helper to check if parameter type is compatible
            def paramCompatible(paramType: TypeRepr): Boolean = {
              try {
                source <:< paramType ||
                source.dealias <:< paramType.dealias ||
                paramType <:< source ||
                paramType.dealias <:< source.dealias ||
                paramType =:= source ||
                paramType.dealias =:= source.dealias
              } catch {
                case _ => false
              }
            }
            
            // PRIORITY 1: Find validation method returning Either[String, Target]
            val validationMethod = allApplyMethods.find { method =>
              try {
                method.termRef.widenTermRefByName match {
                  case mt: MethodType if mt.paramTypes.size == 1 =>
                    val paramType = mt.paramTypes.head
                    val paramMatches = paramCompatible(paramType)
                    
                    if (paramMatches) {
                      val returnType = mt.resType
                      val returnsEitherStringTarget = isEitherStringTarget(returnType, target)
                      
                      report.info(s"[OpaqueMacros] Method ${method.name}: paramMatches=$paramMatches, returnsEitherStringTarget=$returnsEitherStringTarget, returnType=${returnType.show}, target=${target.show}")
                      
                      returnsEitherStringTarget
                    } else {
                      false
                    }
                  case pt: PolyType =>
                    // Handle polymorphic methods - check result type
                    val resType = pt.resType
                    val returnsEither = isEitherType(resType)
                    // For polymorphic, we'd need type application - skip for now
                    false
                  case _ =>
                    // Fallback: try to construct Select and get type
                    try {
                      val companionRef = Ref(companionSymbol)
                      val methodSelect = Select.unique(companionRef, method.name)
                      methodSelect.tpe.widenTermRefByName match {
                        case mt: MethodType if mt.paramTypes.size == 1 =>
                          val paramType = mt.paramTypes.head
                          val paramMatches = paramCompatible(paramType)
                          
                          if (paramMatches) {
                            val returnType = mt.resType
                            isEitherStringTarget(returnType, target)
                          } else {
                            false
                          }
                        case _ => false
                      }
                    } catch {
                      case _ => false
                    }
                }
              } catch {
                case _ => false
              }
            }
            
            // PRIORITY 2: If validation method found, use it
            validationMethod match {
              case Some(method) =>
                // Found validation method - generate code that calls it
                report.info(s"[OpaqueMacros] Found validation method: ${method.name}")
                
                // Build the complete Into implementation
                val companionRef = Ref(companionSymbol)
                
                // Build a function that takes A and returns Either[String, B]
                val lambdaType = MethodType(List("input"))(
                  _ => List(TypeRepr.of[A]),
                  _ => TypeRepr.of[Either[String, B]]
                )
                
                val lambda = Lambda(
                  Symbol.spliceOwner,
                  lambdaType,
                  { (meth, params) =>
                    val inputParam = params.head.asInstanceOf[Term]
                    val methodSelect = Select.unique(companionRef, method.name)
                    Apply(methodSelect, List(inputParam))
                  }
                )
                
                val validationFn = lambda.asExpr.asInstanceOf[Expr[A => Either[String, B]]]
                
                Some('{
                  new zio.blocks.schema.Into[A, B] {
                    private val validate: A => Either[String, B] = $validationFn
                    
                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                      validate(input) match {
                        case Right(v) => Right(v.asInstanceOf[B])
                        case Left(e: String) => 
                          Left(zio.blocks.schema.SchemaError.expectationMismatch(
                            Nil,
                            s"$e (input: ${input.toString})"
                          ))
                        case Left(other) => 
                          Left(zio.blocks.schema.SchemaError.expectationMismatch(
                            Nil,
                            s"${other.toString} (input: ${input.toString})"
                          ))
                      }
                    }
                  }
                })
              
              case None =>
                // PRIORITY 3: Fallback to simple apply method (no validation)
                val simpleApplyMethod = allApplyMethods.find { method =>
                  try {
                    method.termRef.widenTermRefByName match {
                      case mt: MethodType if mt.paramTypes.size == 1 =>
                        val paramType = mt.paramTypes.head
                        val paramMatches = paramCompatible(paramType)
                        
                        if (paramMatches) {
                          // Check that return type is NOT Either (simple apply)
                          val returnType = mt.resType
                          val returnsNonEither = !isEitherType(returnType)
                          
                          report.info(s"[OpaqueMacros] Simple method ${method.name}: paramMatches=$paramMatches, returnsNonEither=$returnsNonEither, returnType=${returnType.show}")
                          
                          returnsNonEither
                        } else {
                          false
                        }
                      case _ => false
                    }
                  } catch {
                    case _ => false
                  }
                }
                
                simpleApplyMethod match {
                  case Some(method) =>
                    // Build a function that takes A and returns B
                    val companionRef = Ref(companionSymbol)
                    
                    val lambdaType = MethodType(List("input"))(
                      _ => List(TypeRepr.of[A]),
                      _ => TypeRepr.of[B]
                    )
                    
                    val lambda = Lambda(
                      Symbol.spliceOwner,
                      lambdaType,
                      { (meth, params) =>
                        val inputParam = params.head.asInstanceOf[Term]
                        val methodSelect = Select.unique(companionRef, method.name)
                        Apply(methodSelect, List(inputParam))
                      }
                    )
                    
                    val applyFn = lambda.asExpr.asInstanceOf[Expr[A => B]]
                    
                    Some('{
                      new zio.blocks.schema.Into[A, B] {
                        private val transform: A => B = $applyFn
                        
                        def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                          Right(transform(input))
                        }
                      }
                    })
                  case None => 
                    // Debug logging
                    report.info(s"[OpaqueMacros] No suitable apply method found")
                    None
                }
            }
          } else {
            None
          }
        } else {
          None
        }
      }
    } else {
      None
    }
    
    // Case 2: Source is opaque type, target is underlying type (unwrap)
    val case2Result = if (isOpaqueAlias(source)) {
      val underlyingOpt = getUnderlyingType(source)
      
      underlyingOpt.flatMap { underlying =>
        if (target =:= underlying || target.dealias =:= underlying.dealias) {
          Some('{
            new zio.blocks.schema.Into[A, B] {
              def into(input: A): Either[zio.blocks.schema.SchemaError, B] = 
                Right(input.asInstanceOf[B])
            }
          })
        } else {
          None
        }
      }
    } else {
      None
    }
    
    // Case 3: Both are opaque types with same underlying type
    val case3Result = if (isOpaqueAlias(source) && isOpaqueAlias(target)) {
      val sourceUnderlying = getUnderlyingType(source)
      val targetUnderlying = getUnderlyingType(target)
      
      (sourceUnderlying, targetUnderlying) match {
        case (Some(su), Some(tu)) if su =:= tu || su.dealias =:= tu.dealias =>
          // Same underlying type - direct cast
          Some('{
            new zio.blocks.schema.Into[A, B] {
              def into(input: A): Either[zio.blocks.schema.SchemaError, B] = 
                Right(input.asInstanceOf[B])
            }
          })
        case _ => None
      }
    } else {
      None
    }
    
    // Return first matching case, or None
    case1Result.orElse(case2Result).orElse(case3Result)
  }
}

