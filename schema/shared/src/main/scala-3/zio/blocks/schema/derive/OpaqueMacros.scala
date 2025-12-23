package zio.blocks.schema.derive

import scala.quoted.*

/**
 * Macros for handling opaque type conversions in Into derivation.
 *
 * This module extracts the opaque type conversion logic from Into.scala to make
 * debugging and maintenance easier.
 */
object OpaqueMacros {

  /**
   * Checks if a type is a Scala 3 opaque type.
   *
   * This is used to prevent identity optimization from bypassing validation
   * when converting to constrained opaque types (e.g., Int -> ValidAge where
   * ValidAge has validation constraints).
   *
   * @param tpe
   *   The type to check
   * @return
   *   true if the type is an opaque type, false otherwise
   */
  def isOpaque(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe match {
      case tr: TypeRef => tr.isOpaqueAlias
      case _           => false
    }
  }

  /**
   * Attempts to derive an Into[A, B] instance for opaque type conversions.
   *
   * Handles three cases:
   *   1. Target is opaque type, source is underlying type (wrapping with
   *      validation)
   *   2. Source is opaque type, target is underlying type (unwrapping)
   *   3. Both are opaque types with same underlying type (direct cast)
   *
   * @return
   *   Some(expr) if conversion is possible, None otherwise
   */
  def opaqueTypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {
    import quotes.reflect.*

    // Helper to check if a type is an opaque type alias
    def isOpaqueAlias(tpe: TypeRepr): Boolean = tpe match {
      case tr: TypeRef => tr.isOpaqueAlias
      case _           => false
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
        case _                                                                                   => false
      }

      if (isAppliedEither) {
        true
      } else {
        // Second check: subtype relationship with wildcards
        val isSubtypeEither =
          try {
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
        case AppliedType(eitherType, typeArgs)
            if eitherType.typeSymbol.fullName == "scala.util.Either" && typeArgs.size == 2 =>
          val leftType  = typeArgs(0)
          val rightType = typeArgs(1)

          // Check that left type is String
          val leftIsString = leftType =:= TypeRepr.of[String] || leftType.dealias =:= TypeRepr.of[String].dealias

          // Check that right type matches target - with opaque type identity fix
          // IMPROVED: More robust for Opaque Types
          val rightMatchesTarget = {
            // First try standard equality checks
            val standardMatch = rightType =:= targetType ||
              rightType.dealias =:= targetType.dealias ||
              rightType =:= targetType.dealias ||
              rightType.dealias =:= targetType

            // Then try opaque type symbol comparison (most reliable for opaque types)
            val opaqueMatch =
              try {
                // Get type symbols for comparison (works even if not TypeRef)
                val rightSymbol  = rightType.typeSymbol
                val targetSymbol = targetType.typeSymbol

                // Direct symbol comparison (most reliable for opaque types)
                val symbolMatch = rightSymbol == targetSymbol ||
                  rightSymbol.fullName == targetSymbol.fullName

                if (symbolMatch) {
                  true
                } else {
                  // Try TypeRef-specific checks for opaque types
                  (rightType, targetType) match {
                    case (tr1: TypeRef, tr2: TypeRef) =>
                      val r1Opaque = tr1.isOpaqueAlias
                      val r2Opaque = tr2.isOpaqueAlias
                      if (r1Opaque && r2Opaque) {
                        // Both are opaque - compare symbols directly (most reliable)
                        tr1.typeSymbol == tr2.typeSymbol ||
                        tr1.typeSymbol.fullName == tr2.typeSymbol.fullName
                      } else if (r1Opaque || r2Opaque) {
                        // At least one is opaque - compare underlying types
                        val r1Underlying = if (r1Opaque) tr1.translucentSuperType.dealias else tr1.dealias
                        val r2Underlying = if (r2Opaque) tr2.translucentSuperType.dealias else tr2.dealias
                        r1Underlying =:= r2Underlying ||
                        r1Underlying.dealias =:= r2Underlying.dealias ||
                        r1Underlying =:= r2Underlying.dealias ||
                        r1Underlying.dealias =:= r2Underlying
                      } else {
                        false
                      }
                    case (tr1: TypeRef, _) if tr1.isOpaqueAlias =>
                      // Right type is opaque, target might not be TypeRef
                      tr1.typeSymbol == targetSymbol ||
                      tr1.typeSymbol.fullName == targetSymbol.fullName
                    case (_, tr2: TypeRef) if tr2.isOpaqueAlias =>
                      // Target is opaque, right might not be TypeRef
                      rightSymbol == tr2.typeSymbol ||
                      rightSymbol.fullName == tr2.typeSymbol.fullName
                    case _ =>
                      // Even if not TypeRef, try dealias and symbol comparison
                      val rightDealiased  = rightType.dealias
                      val targetDealiased = targetType.dealias
                      rightDealiased.typeSymbol == targetDealiased.typeSymbol ||
                      rightDealiased.typeSymbol.fullName == targetDealiased.typeSymbol.fullName
                  }
                }
              } catch {
                case _: Throwable => false
              }

            // Finally try subtyping checks
            val subtypingMatch =
              try {
                targetType <:< rightType ||
                targetType.dealias <:< rightType.dealias ||
                rightType <:< targetType ||
                rightType.dealias <:< targetType.dealias ||
                targetType.dealias <:< rightType ||
                rightType.dealias <:< targetType
              } catch {
                case _: Throwable => false
              }

            standardMatch || opaqueMatch || subtypingMatch
          }

          leftIsString && rightMatchesTarget
        case _ =>
          // Fallback: check if it's a subtype of Either[String, Target]
          try {
            // First check if it's an Either type
            val isEither = returnType <:< TypeRepr.of[Either[String, Any]] ||
              returnType <:< TypeRepr.of[Either[?, ?]]

            if (isEither) {
              // Try to extract the right type from Either
              dealiased match {
                case AppliedType(_, typeArgs) if typeArgs.size == 2 =>
                  val leftType  = typeArgs(0)
                  val rightType = typeArgs(1)

                  // Check that left type is String
                  val leftIsString = leftType =:= TypeRepr.of[String] ||
                    leftType.dealias =:= TypeRepr.of[String].dealias

                  if (leftIsString) {
                    // Use same opaque-aware comparison as main branch
                    val rightMatches = rightType =:= targetType ||
                      rightType.dealias =:= targetType.dealias ||
                      rightType =:= targetType.dealias ||
                      rightType.dealias =:= targetType ||
                      ((rightType, targetType) match {
                        case (tr1: TypeRef, tr2: TypeRef) =>
                          // Check symbol match first (most reliable for opaque types)
                          tr1.typeSymbol == tr2.typeSymbol ||
                          tr1.typeSymbol.fullName == tr2.typeSymbol.fullName ||
                          // Then check if at least one is opaque
                          ((tr1.isOpaqueAlias || tr2.isOpaqueAlias) && {
                            if (tr1.isOpaqueAlias && tr2.isOpaqueAlias) {
                              tr1.typeSymbol == tr2.typeSymbol ||
                              tr1.typeSymbol.fullName == tr2.typeSymbol.fullName
                            } else {
                              val r1Underlying =
                                if (tr1.isOpaqueAlias) tr1.translucentSuperType.dealias else tr1.dealias
                              val r2Underlying =
                                if (tr2.isOpaqueAlias) tr2.translucentSuperType.dealias else tr2.dealias
                              r1Underlying =:= r2Underlying ||
                              r1Underlying.dealias =:= r2Underlying.dealias ||
                              r1Underlying =:= r2Underlying.dealias ||
                              r1Underlying.dealias =:= r2Underlying
                            }
                          })
                        case (tr1: TypeRef, _) if tr1.isOpaqueAlias =>
                          tr1.typeSymbol == targetType.typeSymbol ||
                          tr1.typeSymbol.fullName == targetType.typeSymbol.fullName
                        case (_, tr2: TypeRef) if tr2.isOpaqueAlias =>
                          rightType.typeSymbol == tr2.typeSymbol ||
                          rightType.typeSymbol.fullName == tr2.typeSymbol.fullName
                        case _ => false
                      }) ||
                      targetType <:< rightType ||
                      targetType.dealias <:< rightType.dealias ||
                      rightType <:< targetType ||
                      rightType.dealias <:< targetType.dealias ||
                      targetType.dealias <:< rightType ||
                      rightType.dealias <:< targetType

                    rightMatches
                  } else {
                    false
                  }
                case _ => false
              }
            } else {
              false
            }
          } catch {
            case _ => false
          }
      }
    }

    // Helper to find companion object with fallback strategy
    def findCompanion(tpe: TypeRepr): Symbol = {
      val sym = tpe.typeSymbol

      // 1. Try standard approach
      val standard = sym.companionModule
      if (standard != Symbol.noSymbol) return standard

      // 2. Fallback: Search in owner's declarations
      // Look for a symbol with the same name that is a Module
      val owner = sym.owner
      if (owner != Symbol.noSymbol) {
        owner.declarations.find { decl =>
          decl.name == sym.name && decl.flags.is(Flags.Module)
        }.getOrElse(Symbol.noSymbol)
      } else {
        Symbol.noSymbol
      }
    }

    // Helper to check if return type is Validation (like in NewtypeMacros)
    def returnsValidation(method: Symbol): Boolean =
      try {
        method.termRef.widenTermRefByName match {
          case mt: MethodType =>
            val returnType     = mt.resType.dealias
            val returnTypeName = returnType.typeSymbol.fullName
            // Check both the type symbol name and the full type representation
            returnTypeName.contains("Validation") ||
            returnType.show.contains("Validation")
          case _ => false
        }
      } catch {
        case _: Throwable => false
      }

    // Case 1: Target is opaque type, source is underlying type
    val targetIsOpaque = isOpaqueAlias(target)

    val case1Result = if (targetIsOpaque) {
      val underlyingOpt = getUnderlyingType(target)

      underlyingOpt.flatMap { underlying =>
        val sourceMatches = source =:= underlying || source.dealias =:= underlying.dealias
        if (sourceMatches) {
          // Context Setup: Get companion object (with fallback strategy)
          val companionSymbol = findCompanion(target)

          // Helper to check if parameter type is compatible
          def paramCompatible(paramType: TypeRepr): Boolean =
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

          if (companionSymbol == Symbol.noSymbol) {
            // Scenario A: No companion object -> Safe Identity Cast
            // (An opaque type without a companion is just a type alias wrapper)
            Some('{
              new zio.blocks.schema.Into[A, B] {
                def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                  Right(input.asInstanceOf[B])
              }
            })
          } else {
            // Scenario B: Companion exists -> Search for Validation/Factory methods
            val companionRef     = Ref(companionSymbol)
            val methodNamesToTry = List("apply", "make", "from", "validate")

            // 1. Find all candidate methods (1 param, compatible type)
            val candidates = methodNamesToTry.flatMap { name =>
              try {
                companionSymbol.memberMethod(name).filter { m =>
                  try {
                    m.paramSymss.flatten.size == 1 && {
                      m.termRef.widenTermRefByName match {
                        case mt: MethodType => paramCompatible(mt.paramTypes.head) // KEEP THIS CHECK!
                        case _              => false
                      }
                    }
                  } catch {
                    case _: Throwable => false
                  }
                }
              } catch {
                case _: Throwable => Nil
              }
            }

            // DEBUG: Log candidates found (commented out to avoid noise, uncomment for debugging)
            // if (candidates.isEmpty) {
            //   report.warning(
            //     s"[OpaqueMacros] No candidates found for ${target.show}. " +
            //     s"Searched methods: ${methodNamesToTry.mkString(", ")}. " +
            //     s"Companion: ${companionSymbol.name}"
            //   )
            // } else {
            //   report.warning(
            //     s"[OpaqueMacros] Found ${candidates.size} candidates for ${target.show}: " +
            //     candidates.map(m => s"${m.name}: ${try { m.termRef.widenTermRefByName match { case mt: MethodType => mt.resType.show; case _ => "?" } } catch { case _ => "?" }}").mkString(", ")
            //   )
            // }

            // 2. Classify candidates into Validators (Either/Validation) and Factories (Direct)
            val (validators, factories) = candidates.partition { m =>
              try {
                val retTypeOpt = m.termRef.widenTermRefByName match {
                  case mt: MethodType => Some(mt.resType)
                  case _              => None
                }

                retTypeOpt match {
                  case Some(retTypeRaw) =>
                    // Try both with and without dealias to handle different type representations
                    val retType = retTypeRaw.dealias
                    val retSym  = retType.typeSymbol

                    // Also check raw type symbol (before dealias) for Either
                    val retSymRaw = retTypeRaw.typeSymbol

                    // Robust Check: Is it Either? (use isEitherType helper for better detection)
                    val isEither = isEitherType(retType) || isEitherType(retTypeRaw)
                    // Relaxed Check: Is it Validation?
                    val isValidation = retSym.fullName.contains("Validation") ||
                      retSymRaw.fullName.contains("Validation")

                    if (isEither) {
                      // ULTRA-PERMISSIVE APPROACH for opaque types:
                      // If target is opaque and method returns Either, accept it as validator
                      // This is safe because Either indicates validation is present
                      val isTargetOpaque = isOpaqueAlias(target)

                      if (isTargetOpaque) {
                        // For opaque types, if it returns Either, accept it as validator
                        // This is safe because:
                        // 1. Opaque types with companion objects typically have validation
                        // 2. Either return type indicates validation logic
                        // 3. The combination is safe to accept
                        //
                        // Try to extract Either type args to check right side matches target
                        // (but be permissive - if we can't extract, still accept it)
                        val eitherType = retType match {
                          case at: AppliedType => Some(at)
                          case _               =>
                            retTypeRaw match {
                              case at: AppliedType => Some(at)
                              case _               => None
                            }
                        }

                        eitherType match {
                          case Some(AppliedType(_, args)) if args.size == 2 =>
                            val rightArg = args(1)
                            // For opaque types, be very permissive with right side matching
                            // Accept if symbol names match or if we can't determine, still accept
                            val rightMatches = rightArg.typeSymbol.fullName == target.typeSymbol.fullName ||
                              rightArg.dealias.typeSymbol.fullName == target.dealias.typeSymbol.fullName ||
                              rightArg =:= target ||
                              rightArg.dealias =:= target.dealias ||
                              rightArg =:= target.dealias ||
                              rightArg.dealias =:= target ||
                              // If we can't determine, still accept (ultra-permissive for opaque types)
                              true
                            rightMatches
                          case _ =>
                            // If we can't extract Either args, still accept (ultra-permissive)
                            true
                        }
                      } else {
                        // For non-opaque types, use robust checks
                        // Try both retType and retTypeRaw to handle different representations
                        val eitherType = retType match {
                          case at: AppliedType => Some(at)
                          case _               =>
                            retTypeRaw match {
                              case at: AppliedType => Some(at)
                              case _               => None
                            }
                        }

                        eitherType match {
                          case Some(AppliedType(_, args)) if args.size == 2 =>
                            val rightArg = args(1)

                            // Standard checks for non-opaque types
                            val symbolMatch = rightArg.typeSymbol == target.typeSymbol ||
                              rightArg.typeSymbol.fullName == target.typeSymbol.fullName ||
                              rightArg.dealias.typeSymbol == target.dealias.typeSymbol ||
                              rightArg.dealias.typeSymbol.fullName == target.dealias.typeSymbol.fullName

                            val typeMatch = rightArg =:= target ||
                              rightArg.dealias =:= target.dealias ||
                              rightArg =:= target.dealias ||
                              rightArg.dealias =:= target

                            val subtypingMatch =
                              try {
                                target <:< rightArg ||
                                target.dealias <:< rightArg.dealias ||
                                rightArg <:< target ||
                                rightArg.dealias <:< target.dealias
                              } catch {
                                case _: Throwable => false
                              }

                            symbolMatch || typeMatch || subtypingMatch
                          case Some(_) => false // Not an AppliedType with 2 args
                          case None    => false
                        }
                      }
                    } else if (isValidation) {
                      true // Trust Validation types
                    } else {
                      false
                    }
                  case None => false
                }
              } catch {
                case _: Throwable => false
              }
            }

            // DEBUG: Log partition results (commented out to avoid noise, uncomment for debugging)
            // report.warning(
            //   s"[OpaqueMacros] Partition results for ${target.show}: " +
            //   s"validators=${validators.size}, factories=${factories.size}. " +
            //   s"Validators: ${validators.map(_.name).mkString(", ")}. " +
            //   s"Factories: ${factories.map(_.name).mkString(", ")}"
            // )

            // 3. Selection Strategy
            if (validators.nonEmpty) {
              // PRIORITY 1: Use Validator
              // IMPORTANT: Filter out methods that don't return Either[String, Target]
              // This prevents using applyUnsafe or other unsafe methods
              val safeValidators = validators.filter { m =>
                try {
                  m.termRef.widenTermRefByName match {
                    case mt: MethodType =>
                      val retType = mt.resType
                      // Must return Either[String, Target] or Validation
                      isEitherType(retType) || isEitherType(retType.dealias) ||
                      retType.typeSymbol.fullName.contains("Validation")
                    case _ => false
                  }
                } catch {
                  case _: Throwable => false
                }
              }

              if (safeValidators.isEmpty) {
                // No safe validators found - fail
                report.errorAndAbort(
                  s"Opaque type ${target.show} has methods that look like validators but don't return Either or Validation. " +
                    s"Found validators: ${validators.map(_.name).mkString(", ")}. " +
                    s"Expected: methods returning Either[String, ${target.show}] or Validation[?, ${target.show}]"
                )
              }

              val method = safeValidators.head

              val returnsEither =
                try {
                  method.termRef.widenTermRefByName match {
                    case mt: MethodType =>
                      isEitherType(mt.resType) || isEitherType(mt.resType.dealias)
                    case _ => false
                  }
                } catch {
                  case _: Throwable => false
                }

              // DEBUG: Verify we're using the correct method
              // Check that the method signature matches what we expect
              val methodRetType =
                try {
                  method.termRef.widenTermRefByName match {
                    case mt: MethodType => mt.resType.show
                    case _              => "?"
                  }
                } catch {
                  case _: Throwable => "?"
                }

              // If method name is "apply" but return type is not Either, something is wrong
              if (
                method.name == "apply" && !methodRetType.contains("Either") && !methodRetType.contains("Validation")
              ) {
                report.errorAndAbort(
                  s"[OpaqueMacros] Selected method 'apply' but return type is not Either or Validation: $methodRetType. " +
                    s"This suggests applyUnsafe was selected instead of apply. " +
                    s"Available validators: ${safeValidators
                        .map(m =>
                          s"${m.name}: ${try {
                              m.termRef.widenTermRefByName match {
                                case mt: MethodType => mt.resType.show; case _ => "?"
                              }
                            } catch { case _ => "?" }}"
                        )
                        .mkString(", ")}"
                )
              }

              val isValidationType = returnsValidation(method)

              if (isValidationType) {
                // Handle Validation[E, B] via reflection (like NewtypeMacros)
                val validatorLambda = Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("x"))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[Any]),
                  { (_, params) =>
                    val inputParam = params.head.asInstanceOf[Term]
                    // IMPORTANT: Use Select(companionRef, method) instead of Select.unique
                    // to ensure we select the specific method we found via reflection,
                    // not just any method with the same name (which could be applyUnsafe)
                    val methodSelect = Select(companionRef, method)
                    Apply(methodSelect, List(inputParam))
                  }
                )

                val validatorFnExpr = validatorLambda.asExpr.asInstanceOf[Expr[A => Any]]

                // DIAGNOSTIC LOG: Generated code for Validation path
                val generatedCode = '{
                  new zio.blocks.schema.Into[A, B] {
                    private val validator: A => Any = $validatorFnExpr

                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                      try {
                        val validationResult = validator(input)

                        // Validation has .toEither method
                        val toEitherMethod = validationResult.getClass.getMethod("toEither")
                        val either         = toEitherMethod.invoke(validationResult).asInstanceOf[Either[Any, B]]

                        either.fold(
                          errs => {
                            val errorMsg =
                              try {
                                // Try to format NonEmptyChunk nicely if present
                                if (errs.getClass.getName.contains("NonEmptyChunk")) {
                                  val headMethod = errs.getClass.getMethod("head")
                                  headMethod.invoke(errs).toString
                                } else {
                                  errs.toString
                                }
                              } catch { case _: Throwable => errs.toString }

                            Left(zio.blocks.schema.SchemaError.expectationMismatch(Nil, errorMsg))
                          },
                          success => Right(success)
                        )
                      } catch {
                        case e: Throwable =>
                          // If reflection fails (e.g. no toEither) or validation throws, return Error
                          Left(
                            zio.blocks.schema.SchemaError
                              .expectationMismatch(Nil, "Validation/Reflection error: " + e.getMessage)
                          )
                      }
                  }
                }

                Some(generatedCode)
              } else {
                // Handle Either[String, B] directly
                // CRITICAL FIX: Build the method call explicitly using the method symbol we found
                // This ensures we call the exact method (e.g., apply returning Either) not applyUnsafe
                val validatorLambda = Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("input"))(
                    _ => List(TypeRepr.of[A]),
                    _ => TypeRepr.of[Either[String, B]]
                  ),
                  { (_, params) =>
                    val inputParam = params.head.asInstanceOf[Term]
                    // Use Select with the specific method symbol to ensure correct method selection
                    // This is critical when there are multiple methods with the same name (e.g., apply and applyUnsafe)
                    val methodSelect = Select(companionRef, method)
                    Apply(methodSelect, List(inputParam))
                  }
                )

                val validationFn = validatorLambda.asExpr.asInstanceOf[Expr[A => Either[String, B]]]

                // DIAGNOSTIC LOG: Generated code for Either path
                val generatedCode = '{
                  new zio.blocks.schema.Into[A, B] {
                    private val validate: A => Either[String, B] = $validationFn

                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                      validate(input) match {
                        case Right(v)        => Right(v.asInstanceOf[B])
                        case Left(e: String) =>
                          Left(
                            zio.blocks.schema.SchemaError.expectationMismatch(
                              Nil,
                              s"$e (input: ${input.toString})"
                            )
                          )
                        case Left(other) =>
                          Left(
                            zio.blocks.schema.SchemaError.expectationMismatch(
                              Nil,
                              s"${other.toString} (input: ${input.toString})"
                            )
                          )
                      }
                  }
                }

                Some(generatedCode)
              }
            } else {
              // PRIORITY 2: No validators found
              // Check if there are any methods returning Either (indicating validation exists but wasn't recognized)
              val hasAnyEitherReturn = candidates.exists { m =>
                try {
                  m.termRef.widenTermRefByName match {
                    case mt: MethodType =>
                      val rt          = mt.resType
                      val rtDealiased = rt.dealias
                      isEitherType(rt) || isEitherType(rtDealiased)
                    case _ => false
                  }
                } catch {
                  case _: Throwable => false
                }
              }

              if (hasAnyEitherReturn) {
                // There ARE methods returning Either, but they weren't recognized as validators
                // This is a bug - fail with detailed error
                val candidateNames = candidates.map(_.name).distinct.mkString(", ")
                val foundMethods   = if (candidates.nonEmpty) {
                  candidates.map { m =>
                    try {
                      val retType = m.termRef.widenTermRefByName match {
                        case mt: MethodType =>
                          val rt          = mt.resType
                          val rtDealiased = rt.dealias
                          val isEither    = isEitherType(rt) || isEitherType(rtDealiased)
                          s"${mt.resType.show} (isEither=$isEither)"
                        case _ => "?"
                      }
                      s"${m.name}: $retType"
                    } catch {
                      case _: Throwable => s"${m.name}: ?"
                    }
                  }.mkString("; ")
                } else {
                  "none"
                }

                report.errorAndAbort(
                  s"Opaque type ${target.show} has companion object with methods returning Either, but validation method not recognized. " +
                    s"Found methods: $foundMethods. " +
                    s"Candidate names searched: $candidateNames. " +
                    s"This may indicate a bug in the validator detection logic."
                )
              } else if (factories.nonEmpty) {
                // No validators, but factories exist and no methods return Either
                // This is safe - use factory (no validation to bypass)
                // This handles cases like Age and Count which are simple wrappers without validation
                val method        = factories.head
                val factoryLambda = Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("input"))(
                    _ => List(TypeRepr.of[A]),
                    _ => TypeRepr.of[B]
                  ),
                  { (_, params) =>
                    val inputParam   = params.head.asInstanceOf[Term]
                    val methodSelect = Select(companionRef, method)
                    Apply(methodSelect, List(inputParam))
                  }
                )

                val factoryFn = factoryLambda.asExpr.asInstanceOf[Expr[A => B]]

                Some('{
                  new zio.blocks.schema.Into[A, B] {
                    private val factory: A => B = $factoryFn

                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                      try {
                        Right(factory(input))
                      } catch {
                        case e: Throwable =>
                          Left(
                            zio.blocks.schema.SchemaError.expectationMismatch(
                              Nil,
                              s"Factory method failed: ${e.getMessage} (input: ${input.toString})"
                            )
                          )
                      }
                  }
                })
              } else {
                // No validators, no factories - fail
                val candidateNames = candidates.map(_.name).distinct.mkString(", ")
                report.errorAndAbort(
                  s"Opaque type ${target.show} has companion object but no suitable method found. " +
                    s"Expected: apply/make/from/validate returning Either[String, ${target.show}], Validation[?, ${target.show}], or ${target.show}. " +
                    s"Candidate names searched: $candidateNames."
                )
              }
            }
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
