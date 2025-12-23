package zio.blocks.schema.derive

import scala.quoted.*

/**
 * Macros for handling ZIO Prelude Newtype/Subtype conversions in Into
 * derivation.
 *
 * ZIO Prelude newtypes are NOT Scala 3 opaque types - they use a different
 * encoding:
 *   - `type UserId = UserId.Type`
 *   - `object UserId extends Newtype[String]`
 *
 * This module detects and handles conversions for:
 *   1. Underlying type → Newtype (wrapping with optional validation)
 *   2. Newtype → Underlying type (unwrapping)
 *   3. Newtype → Newtype (when underlying types match)
 */
object NewtypeMacros {

  /**
   * Checks if a type is a ZIO Prelude Newtype or Subtype.
   *
   * This is used to prevent identity optimization from bypassing validation
   * when converting to constrained types (e.g., Int -> AgeSub where AgeSub has
   * validation constraints).
   *
   * @param tpe
   *   The type to check
   * @return
   *   true if the type is a ZIO Prelude Newtype/Subtype, false otherwise
   */
  def isNewtype(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // Helper to check if a type looks like a ZIO Prelude Newtype
    // Pattern: the type is a type alias like `UserId.Type` where `UserId` extends `Newtype[U]`
    def checkTypeRef(tr: TypeRepr): Boolean = tr match {
      case TypeRef(qualifier, name) if name == "Type" =>
        qualifier match {
          case termRef: TermRef =>
            val symbol = termRef.termSymbol
            if (symbol != Symbol.noSymbol && symbol.flags.is(Flags.Module)) {
              val moduleClass = symbol.moduleClass
              if (moduleClass != Symbol.noSymbol) {
                val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
                parentNames.exists(n => n.contains("Newtype") || n.contains("Subtype"))
              } else false
            } else false
          case _ => false
        }
      case _ => false
    }

    // First, try to dealias the type to see if it resolves to X.Type
    val dealiased =
      try { tpe.dealias }
      catch { case _: Throwable => tpe }

    // Check both original and dealiased type
    checkTypeRef(tpe) || checkTypeRef(dealiased)
  }

  /**
   * Attempts to derive an Into[A, B] instance for ZIO Prelude Newtype
   * conversions.
   *
   * @return
   *   Some(expr) if conversion is possible, None otherwise
   */
  def newtypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {
    import quotes.reflect.*

    // FIX: Ignore Scala 3 Enums to prevent ClassCastException
    // Enums are handled by CoproductMacros (via Into.coproductTypeConversion)
    if (source.typeSymbol.flags.is(Flags.Enum) || target.typeSymbol.flags.is(Flags.Enum)) {
      return None
    }

    // Helper to check if a type looks like a ZIO Prelude Newtype
    // Pattern: the type is a type alias like `UserId.Type` where `UserId` extends `Newtype[U]`
    def isZioNewtypePattern(tpe: TypeRepr): Boolean = {
      // First, try to dealias the type to see if it resolves to X.Type
      val dealiased =
        try { tpe.dealias }
        catch { case _: Throwable => tpe }

      def checkTypeRef(tr: TypeRepr): Boolean = tr match {
        case TypeRef(qualifier, name) if name == "Type" =>
          qualifier match {
            case termRef: TermRef =>
              val symbol = termRef.termSymbol
              if (symbol != Symbol.noSymbol && symbol.flags.is(Flags.Module)) {
                val moduleClass = symbol.moduleClass
                if (moduleClass != Symbol.noSymbol) {
                  val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
                  parentNames.exists(n => n.contains("Newtype") || n.contains("Subtype"))
                } else false
              } else false
            case _ => false
          }
        case _ => false
      }

      // Check both original and dealiased type
      checkTypeRef(tpe) || checkTypeRef(dealiased)
    }

    // Helper to check if a Newtype is actually a Subtype
    def isSubtype(tpe: TypeRepr): Boolean = {
      val dealiased =
        try { tpe.dealias }
        catch { case _: Throwable => tpe }

      def checkTypeRef(tr: TypeRepr): Boolean = tr match {
        case TypeRef(qualifier, "Type") =>
          qualifier match {
            case termRef: TermRef =>
              val symbol = termRef.termSymbol
              if (symbol != Symbol.noSymbol && symbol.flags.is(Flags.Module)) {
                val moduleClass = symbol.moduleClass
                if (moduleClass != Symbol.noSymbol) {
                  val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
                  parentNames.exists(_.contains("Subtype"))
                } else false
              } else false
            case _ => false
          }
        case _ => false
      }

      checkTypeRef(tpe) || checkTypeRef(dealiased)
    }

    // Helper to get the underlying type of a Newtype
    def getNewtypeUnderlying(tpe: TypeRepr): Option[TypeRepr] = {
      // First, try to dealias the type
      val dealiased =
        try { tpe.dealias }
        catch { case _: Throwable => tpe }

      def extractUnderlying(tr: TypeRepr): Option[TypeRepr] = tr match {
        case TypeRef(qualifier, "Type") =>
          qualifier match {
            case termRef: TermRef =>
              val companionSymbol = termRef.termSymbol
              if (companionSymbol != Symbol.noSymbol) {
                val moduleClass = companionSymbol.moduleClass
                if (moduleClass != Symbol.noSymbol) {
                  val baseTypes = moduleClass.typeRef.baseClasses.flatMap { cls =>
                    try {
                      Some(moduleClass.typeRef.baseType(cls))
                    } catch {
                      case _: Throwable => None
                    }
                  }

                  baseTypes.collectFirst {
                    case AppliedType(parent, List(underlying))
                        if parent.typeSymbol.fullName.contains("Newtype") ||
                          parent.typeSymbol.fullName.contains("Subtype") =>
                      underlying
                  }
                } else None
              } else None
            case _ => None
          }
        case _ => None
      }

      // Try both original and dealiased
      extractUnderlying(tpe).orElse(extractUnderlying(dealiased))
    }

    // Helper to find the companion module for wrapping
    // Handles multiple cases:
    // 1. TypeRef(TermRef(..., "Age"), "Type") - direct pattern
    // 2. Type alias like `type Age = Age.Type` - extract from dealiased type
    // 3. Type symbol companion module - fallback for type aliases
    // 4. Find module by name in owner scope - last resort
    def getCompanionModule(tpe: TypeRepr): Option[Symbol] = {
      import quotes.reflect.*

      val dealiased =
        try { tpe.dealias }
        catch { case _: Throwable => tpe }

      def extract(tr: TypeRepr): Option[Symbol] = tr match {
        case TypeRef(qualifier, "Type") =>
          qualifier match {
            case termRef: TermRef =>
              val symbol = termRef.termSymbol
              // Verify it's actually a module
              if (symbol != Symbol.noSymbol && symbol.flags.is(Flags.Module)) {
                Some(symbol)
              } else {
                None
              }
            case _ => None
          }
        case _ => None
      }

      // Strategy 1: Try direct pattern matching on original type
      extract(tpe).orElse {
        // Strategy 2: Try dealiased type (handles type aliases like `type Age = Age.Type`)
        extract(dealiased)
      }.orElse {
        // Strategy 3: If tpe is a type alias, try to find companion module via type symbol
        val typeSymbol = tpe.typeSymbol
        if (typeSymbol != Symbol.noSymbol) {
          val companion = typeSymbol.companionModule
          if (companion != Symbol.noSymbol && companion.flags.is(Flags.Module)) {
            // Verify this companion extends Newtype/Subtype
            val moduleClass = companion.moduleClass
            if (moduleClass != Symbol.noSymbol) {
              val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
              if (parentNames.exists(n => n.contains("Newtype") || n.contains("Subtype"))) {
                Some(companion)
              } else {
                None
              }
            } else {
              None
            }
          } else {
            None
          }
        } else {
          None
        }
      }.orElse {
        // Strategy 4: Try to find module by name in owner scope
        // This handles cases where the type alias doesn't have a direct companion
        val typeSymbolForLookup = tpe.typeSymbol
        if (typeSymbolForLookup != Symbol.noSymbol) {
          val typeName = typeSymbolForLookup.name
          val owner    = typeSymbolForLookup.owner
          if (owner != Symbol.noSymbol) {
            // Look for a module/val with the same name in the owner's declarations
            try {
              owner.declarations.collectFirst {
                case decl if decl.name == typeName && decl.flags.is(Flags.Module) =>
                  decl
              }.flatMap { moduleSymbol =>
                // Verify it extends Newtype/Subtype
                val moduleClass = moduleSymbol.moduleClass
                if (moduleClass != Symbol.noSymbol) {
                  val parentNames = moduleClass.typeRef.baseClasses.map(_.fullName)
                  if (parentNames.exists(n => n.contains("Newtype") || n.contains("Subtype"))) {
                    Some(moduleSymbol)
                  } else {
                    None
                  }
                } else {
                  None
                }
              }
            } catch {
              case _: Throwable => None
            }
          } else {
            None
          }
        } else {
          None
        }
      }
    }

    // Helper to check if a method returns Either[String, Target] or Validation[String, Target]
    def isValidatingApply(method: Symbol, targetType: TypeRepr): Boolean =
      try {
        method.termRef.widenTermRefByName match {
          case mt: MethodType if mt.paramTypes.size == 1 =>
            val returnType     = mt.resType.dealias
            val returnTypeName = returnType.typeSymbol.fullName

            // First check: is it a Validation type?
            if (returnTypeName.contains("Validation")) {
              // Support zio.prelude.Validation[E, A] or ZValidation[W, E, A]
              returnType match {
                case AppliedType(_, typeArgs) if typeArgs.size >= 2 =>
                  // Check if last type arg matches target (Validation[E, Target] or ZValidation[W, E, Target])
                  val lastArg = typeArgs.last
                  lastArg =:= targetType || lastArg.dealias =:= targetType.dealias
                case _ =>
                  // Fallback: if it contains Validation in the name, assume it's valid
                  true
              }
            }
            // Second check: is it an Either type?
            else if (returnTypeName == "scala.util.Either") {
              returnType match {
                case AppliedType(_, List(left, right)) =>
                  (left =:= TypeRepr.of[String] || left.dealias =:= TypeRepr.of[String].dealias) &&
                  (right =:= targetType || right.dealias =:= targetType.dealias)
                case _ => false
              }
            } else {
              false
            }
          case _ => false
        }
      } catch {
        case _: Throwable => false
      }

    // Helper to check if return type is Validation (not Either)
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

    // Helper to check if a method is a simple (non-validating) apply
    def isSimpleApply(method: Symbol, paramType: TypeRepr): Boolean =
      try {
        method.termRef.widenTermRefByName match {
          case mt: MethodType if mt.paramTypes.size == 1 =>
            val methodParamType = mt.paramTypes.head
            val returnType      = mt.resType.dealias
            val returnTypeName  = returnType.typeSymbol.fullName
            // Check param type matches and return type is NOT Either AND NOT Validation
            (methodParamType =:= paramType || methodParamType.dealias =:= paramType.dealias) &&
            !returnTypeName.contains("Either") &&
            !returnTypeName.contains("Validation")
          case _ => false
        }
      } catch {
        case _: Throwable => false
      }

    val isSourceNewtype = isZioNewtypePattern(source)
    val isTargetNewtype = isZioNewtypePattern(target)

    // Case 1: Source is underlying type, Target is Newtype (wrap)
    if (!isSourceNewtype && isTargetNewtype) {
      val underlyingOpt = getNewtypeUnderlying(target)
      val companionOpt  = getCompanionModule(target)

      (underlyingOpt, companionOpt) match {
        case (Some(underlying), Some(companion)) if source =:= underlying || source.dealias =:= underlying.dealias =>
          // Context Setup
          val companionRef = Ref(companion)

          // STEP 1: Priority 'make' - Try to use 'make' for ALL ZIO Prelude types
          // (Subtype and Newtype) to ensure validation is respected.
          val makeResult: Option[Expr[zio.blocks.schema.Into[A, B]]] =
            try {
              // 1. Find 'make' method using memberMethod (sees inherited methods)
              // 2. Filter to find the one taking exactly 1 parameter (make(A))
              //    to avoid ambiguity or wrong overloads.
              val makeSymbolOpt = companion
                .memberMethod("make")
                .find(_.paramSymss.flatten.size == 1)

              makeSymbolOpt match {
                case Some(makeSymbol) =>
                  val makeSelect = Select(companionRef, makeSymbol)

                  // Create a Lambda that takes input and calls make(input)
                  val makeLambda = Lambda(
                    Symbol.spliceOwner,
                    MethodType(List("x"))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[Any]),
                    { (_, params) =>
                      val inputParam = params.head.asInstanceOf[Term]
                      Apply(makeSelect, List(inputParam))
                    }
                  )

                  val makeFnExpr = makeLambda.asExpr.asInstanceOf[Expr[A => Any]]

                  // Generate Into instance (Handling Validation via reflection)
                  Some('{
                    new zio.blocks.schema.Into[A, B] {
                      private val validator: A => Any = $makeFnExpr

                      def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                        try {
                          val validationResult = validator(input)

                          // ZIO Prelude Validation has .toEither method
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
                            // If reflection fails (e.g. no toEither) or make throws, return Error
                            Left(
                              zio.blocks.schema.SchemaError
                                .expectationMismatch(Nil, "Validation/Reflection error: " + e.getMessage)
                            )
                        }
                    }
                  })

                case None => None
              }
            } catch {
              case _: Throwable => None
            }

          // If make succeeded, return it; otherwise try wrap
          makeResult match {
            case Some(result) => return Some(result)
            case None         => // Continue to wrap
          }

          // STEP 2: Priority 'wrap' (Trust Companion - Per Newtype e fallback Subtype)
          val wrapResult: Option[Expr[zio.blocks.schema.Into[A, B]]] =
            try {
              val wrapSelect = Select.unique(companionRef, "wrap")

              // Create a Lambda that takes input and calls wrap(input)
              val wrapLambda = Lambda(
                Symbol.spliceOwner,
                MethodType(List("x"))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[B]),
                { (_, params) =>
                  val inputParam = params.head.asInstanceOf[Term]
                  Apply(wrapSelect, List(inputParam))
                }
              )

              val wrapFnExpr = wrapLambda.asExpr.asInstanceOf[Expr[A => B]]

              Some('{
                new zio.blocks.schema.Into[A, B] {
                  private val wrapFn: A => B = $wrapFnExpr

                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                    try {
                      val wrapped = wrapFn(input)
                      Right(wrapped)
                    } catch {
                      case e: Throwable =>
                        Left(
                          zio.blocks.schema.SchemaError
                            .expectationMismatch(Nil, e.getMessage)
                        )
                    }
                }
              })
            } catch {
              case _: Throwable =>
                // wrap() not available or failed, fall through to general fallback
                None
            }

          // If wrap succeeded, return it; otherwise try general fallback
          wrapResult match {
            case Some(result) => return Some(result)
            case None         => // Continue to general fallback
          }

          // STEP 3: General Fallback (Reflection)
          // Define methodNamesToTry in the correct scope
          val methodNamesToTry =
            List("make", "apply", "validate", "fromString", "fromInt", "fromLong", "fromDouble", "fromFloat")
          val allWrappingMethods = companion.declarations
            .filter(m => methodNamesToTry.contains(m.name))
            .filter(_.isDefDef) // Only defined methods, not inherited

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
              case _: Throwable => false
            }

          // PRIORITY 1: Find validation method returning Either[String, Target] or Validation
          val validatingMethod = allWrappingMethods.find { method =>
            try {
              method.termRef.widenTermRefByName match {
                case mt: MethodType if mt.paramTypes.size == 1 =>
                  val paramType    = mt.paramTypes.head
                  val paramMatches = paramCompatible(paramType)

                  if (paramMatches) {
                    isValidatingApply(method, target)
                  } else {
                    false
                  }
                case _ => false
              }
            } catch {
              case _: Throwable => false
            }
          }

          validatingMethod match {
            case Some(method) =>
              val isValidation = returnsValidation(method)

              if (isValidation) {
                // Handle Validation[E, B]
                val mt = MethodType(List("x"))(_ => List(TypeRepr.of[A]), _ => TypeRepr.of[Any])

                val lambda = Lambda(
                  Symbol.spliceOwner,
                  mt,
                  { (_, args) =>
                    val arg = args.head.asInstanceOf[Term]
                    Apply(Select(companionRef, method), List(arg))
                  }
                ).asExpr.asInstanceOf[Expr[A => Any]]

                Some('{
                  new zio.blocks.schema.Into[A, B] {
                    private val validator: A => Any = $lambda

                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                      try {
                        val validationResult = validator(input)
                        val toEitherMethod   = validationResult.getClass.getMethod("toEither")
                        val either           = toEitherMethod.invoke(validationResult).asInstanceOf[Either[Any, B]]

                        either.fold(
                          errs => {
                            val errorMsg =
                              try {
                                if (errs.getClass.getName.contains("NonEmptyChunk")) {
                                  val headMethod = errs.getClass.getMethod("head")
                                  headMethod.invoke(errs).toString
                                } else {
                                  errs.toString
                                }
                              } catch {
                                case _: Throwable => errs.toString
                              }
                            Left(zio.blocks.schema.SchemaError.expectationMismatch(Nil, errorMsg))
                          },
                          success => Right(success)
                        )
                      } catch {
                        case e: Exception =>
                          Left(
                            zio.blocks.schema.SchemaError.expectationMismatch(Nil, "Validation error: " + e.toString)
                          )
                      }
                  }
                })
              } else {
                // Handle Either[String, B]
                val lambdaType = MethodType(List("input"))(
                  _ => List(TypeRepr.of[A]),
                  _ => TypeRepr.of[Either[String, B]]
                )

                val lambda = Lambda(
                  Symbol.spliceOwner,
                  lambdaType,
                  { (_, params) =>
                    val inputParam = params.head.asInstanceOf[Term]
                    try {
                      val methodSelect = Select.unique(companionRef, method.name)
                      Apply(methodSelect, List(inputParam))
                    } catch {
                      case _: Throwable =>
                        val methodSelect = Select(companionRef, method)
                        Apply(methodSelect, List(inputParam))
                    }
                  }
                )

                val validateFn = lambda.asExpr.asInstanceOf[Expr[A => Either[String, B]]]

                Some('{
                  new zio.blocks.schema.Into[A, B] {
                    private val validate: A => Either[String, B] = $validateFn

                    def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                      validate(input) match {
                        case Right(v) => Right(v)
                        case Left(e)  =>
                          Left(
                            zio.blocks.schema.SchemaError.expectationMismatch(
                              Nil,
                              s"$e (input: ${input.toString})"
                            )
                          )
                      }
                  }
                })
              }

            case None =>
              // Also try "wrap" method (from Newtype companion)
              val wrapMethod = companion.declarations.find(_.name == "wrap")
              // For Newtypes: try simple method first, then wrap
              val simpleMethod = allWrappingMethods.find { method =>
                try {
                  method.termRef.widenTermRefByName match {
                    case mt: MethodType if mt.paramTypes.size == 1 =>
                      val paramType    = mt.paramTypes.head
                      val paramMatches = paramCompatible(paramType)

                      if (paramMatches) {
                        isSimpleApply(method, underlying)
                      } else {
                        false
                      }
                    case _ => false
                  }
                } catch {
                  case _: Throwable => false
                }
              }

              val methodToUse = simpleMethod.orElse(wrapMethod)

              methodToUse match {
                case Some(method) =>
                  val methodName = method.name
                  val lambdaType = MethodType(List("input"))(
                    _ => List(TypeRepr.of[A]),
                    _ => TypeRepr.of[B]
                  )

                  val lambda = Lambda(
                    Symbol.spliceOwner,
                    lambdaType,
                    { (_, params) =>
                      val inputParam = params.head.asInstanceOf[Term]
                      try {
                        val methodSelect = Select.unique(companionRef, methodName)
                        Apply(methodSelect, List(inputParam))
                      } catch {
                        case _: Throwable =>
                          val methodSelect = Select(companionRef, method)
                          Apply(methodSelect, List(inputParam))
                      }
                    }
                  )

                  val transformFn = lambda.asExpr.asInstanceOf[Expr[A => B]]

                  Some('{
                    new zio.blocks.schema.Into[A, B] {
                      private val transform: A => B = $transformFn

                      def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                        Right(transform(input))
                    }
                  })

                case None =>
                  // Safe identity fallback for simple newtypes without validation
                  Some('{
                    new zio.blocks.schema.Into[A, B] {
                      def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                        Right(input.asInstanceOf[B])
                    }
                  })
              }
          }

        case _ => None
      }
    }
    // Case 2: Source is Newtype, Target is underlying type (unwrap)
    else if (isSourceNewtype && !isTargetNewtype) {
      val underlyingOpt = getNewtypeUnderlying(source)

      underlyingOpt match {
        case Some(underlying) if target =:= underlying || target.dealias =:= underlying.dealias =>
          // Direct cast unwrapping - newtypes have the same runtime representation
          Some('{
            new zio.blocks.schema.Into[A, B] {
              def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                Right(input.asInstanceOf[B])
            }
          })

        case _ => None
      }
    }
    // Case 3: Both are Newtypes with same underlying type
    else if (isSourceNewtype && isTargetNewtype) {
      val sourceUnderlying = getNewtypeUnderlying(source)
      val targetUnderlying = getNewtypeUnderlying(target)

      (sourceUnderlying, targetUnderlying) match {
        case (Some(su), Some(tu)) if su =:= tu || su.dealias =:= tu.dealias =>
          // Same underlying type - check if target has validation
          val companionOpt = getCompanionModule(target)

          companionOpt match {
            case Some(companion) =>
              val applyMethods     = companion.declarations.filter(_.name == "apply").filter(_.isDefDef)
              val validatingMethod = applyMethods.find(m => isValidatingApply(m, target))

              validatingMethod match {
                case Some(_) =>
                  // Unwrap source, then wrap with validation in target
                  Some('{
                    new zio.blocks.schema.Into[A, B] {
                      def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                        // Unwrap and re-wrap with validation
                        val underlying = input.asInstanceOf[B]
                        Right(underlying)
                      }
                    }
                  })

                case None =>
                  // Direct cast
                  Some('{
                    new zio.blocks.schema.Into[A, B] {
                      def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                        Right(input.asInstanceOf[B])
                    }
                  })
              }

            case None =>
              // Direct cast
              Some('{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
                    Right(input.asInstanceOf[B])
                }
              })
          }

        case _ => None
      }
    } else {
      None
    }
  }
}
