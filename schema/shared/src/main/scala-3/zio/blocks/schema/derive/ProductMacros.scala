package zio.blocks.schema.derive

import scala.quoted.*
import scala.reflect.Selectable.reflectiveSelectable

/**
 * Macros for handling product type conversions (case classes and tuples) in
 * Into derivation.
 */
object ProductMacros {

  /**
   * Attempts to derive an Into[A, B] instance for product type conversions.
   *
   * Handles case classes and tuples with field mapping and nested conversions.
   *
   * @return
   *   Some(expr) if conversion is possible, None otherwise
   */
  def productTypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {
    import quotes.reflect.*

    // Check if both are product types (case classes or tuples)
    val isSourceProduct = isProductType(source)
    val isTargetProduct = isProductType(target)

    if (!isSourceProduct || !isTargetProduct) {
      return None
    }

    // Extract fields from both types
    val sourceFields = extractFields(source)
    val targetFields = extractFields(target)

    // Use FieldMapper to create mapping with schema evolution support
    FieldMapper.mapFields(using quotes)(sourceFields, targetFields) match {
      case Left(error) =>
        report.errorAndAbort(s"Cannot map product types: $error")
      case Right(actions) =>
        // Generate conversion code
        Some(generateProductConversion[A, B](target, sourceFields, targetFields, actions))
    }
  }

  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // Check for case class
    val isCaseClass = tpe.classSymbol.exists(_.flags.is(Flags.Case))

    // Check for tuple (including generic tuples)
    val isTuple = tpe <:< TypeRepr.of[Product] && {
      val sym = tpe.typeSymbol
      sym.fullName.startsWith("scala.Tuple") ||
      sym.fullName == "scala.Product"
    }

    isCaseClass || isTuple
  }

  def extractFields(using Quotes)(tpe: quotes.reflect.TypeRepr): Seq[FieldInfo] = {
    import quotes.reflect.*

    // Try to get case class fields
    tpe.classSymbol match {
      case Some(classSymbol) if classSymbol.flags.is(Flags.Case) =>
        // Case class
        val constructor = classSymbol.primaryConstructor
        val params      = constructor.paramSymss.flatten.filterNot(_.isTypeParam)

        // Optimization: batch extract type args and companion info once
        val tpeTypeArgs     = tpe.typeArgs
        val companionModule = classSymbol.companionModule
        val companionClass  = classSymbol.companionClass

        // Optimization: pre-compute default value method names to avoid string concatenation in loop
        val defaultMethodNames = (1 to params.size).map(idx => s"$$lessinit$$greater$$default$$$idx")

        params.zipWithIndex.map { case (param, idx) =>
          // Optimization: cache memberType call result
          val fieldType  = tpe.memberType(param)
          val hasDefault = param.flags.is(Flags.HasDefault)

          val defaultValue = if (hasDefault) {
            try {
              // Optimization: use pre-computed method name
              val dvMethodName = defaultMethodNames(idx)
              companionClass.declaredMethod(dvMethodName).headOption.map { dvMethod =>
                val dvSelect = Select(Ref(companionModule), dvMethod)
                // Handle type parameters if necessary
                dvMethod.paramSymss match {
                  case Nil                                                                          => dvSelect
                  case List(typeParams) if typeParams.exists(_.isTypeParam) && tpeTypeArgs.nonEmpty =>
                    dvSelect.appliedToTypes(tpeTypeArgs)
                  case _ => dvSelect
                }
              }
            } catch {
              case _ => None
            }
          } else {
            None
          }

          FieldInfo(param.name, fieldType, idx, hasDefault, defaultValue)
        }

      case _ =>
        // Try tuple
        if (tpe <:< TypeRepr.of[Product]) {
          val typeArgs = tpe.baseType(TypeRepr.of[Product].typeSymbol) match {
            case AppliedType(_, args) => args
            case _                    =>
              // Try to extract from tuple structure
              tpe match {
                case AppliedType(_, args) => args
                case _                    => Nil
              }
          }

          if (typeArgs.isEmpty) {
            // Fallback: try to get from tuple type directly
            tpe match {
              case AppliedType(tycon, args) if tycon.typeSymbol.fullName.startsWith("scala.Tuple") =>
                args.zipWithIndex.map { case (argType, idx) =>
                  FieldInfo(s"_${idx + 1}", argType, idx)
                }
              case _ => Seq.empty
            }
          } else {
            typeArgs.zipWithIndex.map { case (argType, idx) =>
              FieldInfo(s"_${idx + 1}", argType, idx)
            }
          }
        } else {
          Seq.empty
        }
    }
  }

  private def generateProductConversion[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo],
    actions: Seq[FieldMappingAction]
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Separate actions by type
    val mapActions        = actions.collect { case a: FieldMappingAction.MapField => a }
    val injectNoneActions = actions.collect { case a: FieldMappingAction.InjectNone => a }
    val useDefaultActions = actions.collect { case a: FieldMappingAction.UseDefault => a }

    // Analyze field conversions - determine which need nested Into instances
    val conversions = mapActions.map { case FieldMappingAction.MapField(sourceIdx, targetIdx) =>
      val sourceField = sourceFields(sourceIdx)
      val targetField = targetFields(targetIdx)
      val sourceType  = sourceField.tpeRepr.asInstanceOf[TypeRepr]
      val targetType  = targetField.tpeRepr.asInstanceOf[TypeRepr]

      (sourceIdx, targetIdx, sourceType, targetType, sourceType =:= targetType)
    }

    // Generate constructor call
    target.classSymbol match {
      case Some(targetClassSymbol) =>
        // Target is a case class
        val constructor    = targetClassSymbol.primaryConstructor
        val orderedActions = actions.sortBy(_.targetIdx)

        // Check if we need nested conversions or schema evolution actions
        val needsNestedConversion = conversions.exists { case (_, _, sourceType, targetType, same) =>
          !same
        }
        val hasSchemaEvolution = injectNoneActions.nonEmpty || useDefaultActions.nonEmpty

        if (needsNestedConversion || hasSchemaEvolution) {
          // Generate generic N-field conversion with error accumulation and schema evolution
          generateNFieldConversionWithEvolution[A, B](
            target,
            constructor,
            orderedActions,
            conversions,
            sourceFields,
            targetFields
          )
        } else {
          // Simple case - all types match, no schema evolution
          // Performance optimization: Inline Right and avoid intermediate allocations
          '{
            new zio.blocks.schema.Into[A, B] {
              @inline def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                val prod   = input.asInstanceOf[Product]
                val result = ${
                  val newExpr    = Select(New(Inferred(target)), constructor)
                  val fieldExprs = orderedActions.map {
                    case FieldMappingAction.MapField(sourceIdx, targetIdx) =>
                      val targetField     = targetFields(targetIdx)
                      val targetFieldType = targetField.tpeRepr.asInstanceOf[TypeRepr]
                      targetFieldType.asType match {
                        case '[t] =>
                          '{ prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[t] }.asTerm
                      }
                    case _ => report.errorAndAbort("Unexpected action in simple conversion")
                  }
                  Apply(newExpr, fieldExprs.toList).asExpr.asInstanceOf[Expr[B]]
                }
                Right(result)
              }
            }
          }
        }
      case None =>
        // Tuples don't support schema evolution (no Option or defaults)
        val mapOnly = mapActions
        if (injectNoneActions.nonEmpty || useDefaultActions.nonEmpty) {
          report.errorAndAbort(
            "Schema evolution (Option injection, default values) is not supported for tuple conversions"
          )
        }

        val orderedMapping = mapOnly.map { case FieldMappingAction.MapField(s, t) => (s, t) }.sortBy(_._2)
        val size           = orderedMapping.size
        val indices        = orderedMapping.map(_._1)

        if (size <= 5) {
          '{
            new zio.blocks.schema.Into[A, B] {
              def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                val prod   = input.asInstanceOf[Product]
                val result = ${
                  size match {
                    case 0 => '{ EmptyTuple }
                    case 1 => '{ Tuple1(prod.productElement(${ Expr(indices(0)) })) }
                    case 2 =>
                      '{ (prod.productElement(${ Expr(indices(0)) }), prod.productElement(${ Expr(indices(1)) })) }
                    case _ =>
                      // For size 3-5, build tuple from elements
                      report.errorAndAbort(s"Tuples with $size elements not yet fully implemented")
                  }
                }
                Right(result.asInstanceOf[B])
              }
            }
          }
        } else {
          report.errorAndAbort(s"Tuples with more than 5 elements are not yet supported")
        }
    }
  }

  /**
   * Generate conversion code for N fields with error accumulation and schema
   * evolution support. This function handles:
   *   1. Normal field mappings (with nested conversions)
   *   2. Option injection (InjectNone)
   *   3. Default value usage (UseDefault)
   *   4. Error accumulation
   *
   * Note: This uses inline code generation to avoid Quotes context and type
   * issues.
   */
  private def generateNFieldConversionWithEvolution[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    constructor: quotes.reflect.Symbol,
    orderedActions: Seq[FieldMappingAction],
    conversions: Seq[(Int, Int, quotes.reflect.TypeRepr, quotes.reflect.TypeRepr, Boolean)],
    sourceFields: Seq[FieldInfo],
    targetFields: Seq[FieldInfo]
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Analyze what conversions we need
    case class FieldConversionInfo(
      targetIdx: Int,
      conversionType: String, // "direct", "into", "none", "default"
      sourceIdx: Option[Int],
      sourceTypeTag: Option[Type[?]],
      targetTypeTag: Type[?],
      defaultTerm: Option[Term]
    )

    val fieldInfos = orderedActions.map { action =>
      action match {
        case FieldMappingAction.MapField(sourceIdx, targetIdx) =>
          val targetField     = targetFields(targetIdx)
          val targetFieldType = targetField.tpeRepr.asInstanceOf[TypeRepr]

          val conversionInfo = conversions.find(c => c._1 == sourceIdx && c._2 == targetIdx).getOrElse {
            val sourceField = sourceFields
              .find(_.position == sourceIdx)
              .getOrElse(
                report.errorAndAbort(s"Source field at index $sourceIdx not found")
              )
            val sourceType = sourceField.tpeRepr.asInstanceOf[TypeRepr]
            (sourceIdx, targetIdx, sourceType, targetFieldType, sourceType =:= targetFieldType)
          }

          val (_, _, sourceType, _, same) = conversionInfo

          targetFieldType.asType match {
            case '[t] =>
              sourceType.asType match {
                case '[s] =>
                  if (same) {
                    FieldConversionInfo(targetIdx, "direct", Some(sourceIdx), Some(Type.of[s]), Type.of[t], None)
                  } else {
                    FieldConversionInfo(targetIdx, "into", Some(sourceIdx), Some(Type.of[s]), Type.of[t], None)
                  }
              }
          }

        case FieldMappingAction.InjectNone(targetIdx) =>
          val targetField     = targetFields(targetIdx)
          val targetFieldType = targetField.tpeRepr.asInstanceOf[TypeRepr]
          targetFieldType.asType match {
            case '[t] =>
              FieldConversionInfo(targetIdx, "none", None, None, Type.of[t], None)
          }

        case FieldMappingAction.UseDefault(targetIdx, defaultValueTerm) =>
          val targetField     = targetFields(targetIdx)
          val targetFieldType = targetField.tpeRepr.asInstanceOf[TypeRepr]
          targetFieldType.asType match {
            case '[t] =>
              FieldConversionInfo(
                targetIdx,
                "default",
                None,
                None,
                Type.of[t],
                Some(defaultValueTerm.asInstanceOf[Term])
              )
          }
      }
    }

    // Generate inline conversion code - no arrays of functions, just direct code
    // This avoids the "Malformed tree" issue with function type parameters

    val numFields = fieldInfos.size

    // Generate code to convert each field and store in a tuple-like structure
    // We'll generate: val r0 = ...; val r1 = ...; etc.
    // Then combine errors and construct the result

    def generateFieldConversion(
      inputExpr: Expr[A],
      info: FieldConversionInfo
    ): Expr[Either[zio.blocks.schema.SchemaError, Any]] =
      info.conversionType match {
        case "direct" =>
          val sourceIdx = info.sourceIdx.get
          info.targetTypeTag match {
            case '[t] =>
              '{
                val prod = $inputExpr.asInstanceOf[Product]
                Right(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[t])
              }
          }

        case "into" =>
          val sourceIdx = info.sourceIdx.get
          (info.sourceTypeTag.get, info.targetTypeTag) match {
            case ('[s], '[t]) =>
              val intoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
                case Some(into) => into
                case None       => '{ zio.blocks.schema.Into.derived[s, t] }
              }
              '{
                val prod = $inputExpr.asInstanceOf[Product]
                $intoExpr
                  .into(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[s])
                  .asInstanceOf[Either[zio.blocks.schema.SchemaError, Any]]
              }
          }

        case "none" =>
          info.targetTypeTag match {
            case '[t] =>
              '{ Right(None.asInstanceOf[t]) }
          }

        case "default" =>
          info.targetTypeTag match {
            case '[t] =>
              val defaultExpr = info.defaultTerm.get.asExpr.asInstanceOf[Expr[t]]
              '{ Right($defaultExpr) }
          }
      }

    // For small number of fields, generate specialized code
    numFields match {
      case 0 =>
        // Empty product
        '{
          new zio.blocks.schema.Into[A, B] {
            def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
              Right(${
                val newExpr = Select(New(Inferred(target)), constructor)
                Apply(newExpr, Nil).asExpr.asInstanceOf[Expr[B]]
              })
          }
        }

      case 1 =>
        val info0 = fieldInfos(0)
        generateSingleFieldConversion[A, B](target, constructor, info0)

      case 2 =>
        val info0 = fieldInfos(0)
        val info1 = fieldInfos(1)
        generateTwoFieldConversion[A, B](target, constructor, info0, info1)

      case 3 =>
        val info0 = fieldInfos(0)
        val info1 = fieldInfos(1)
        val info2 = fieldInfos(2)
        generateThreeFieldConversion[A, B](target, constructor, info0, info1, info2)

      case _ =>
        // For larger products, use a more general approach with explicit field handling
        generateMultiFieldConversion[A, B](target, constructor, fieldInfos)
    }
  }

  // Helper case class for field info (needs to be at object level for reuse)
  private case class FieldConvInfo(
    targetIdx: Int,
    conversionType: String,
    sourceIdx: Option[Int],
    sourceTypeTag: Option[Type[?]],
    targetTypeTag: Type[?],
    defaultTerm: Option[Any] // Using Any to store Term
  )

  private def generateSingleFieldConversion[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    constructor: quotes.reflect.Symbol,
    info0: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    info0.conversionType match {
      case "direct" =>
        val sourceIdx = info0.sourceIdx.get
        info0.targetTypeTag match {
          case '[t] =>
            '{
              new zio.blocks.schema.Into[A, B] {
                def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                  val prod = input.asInstanceOf[Product]
                  val v0   = prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[t]
                  Right(${
                    val newExpr = Select(New(Inferred(target)), constructor)
                    Apply(newExpr, List('{ v0 }.asTerm)).asExpr.asInstanceOf[Expr[B]]
                  })
                }
              }
            }
        }

      case "into" =>
        val sourceIdx = info0.sourceIdx.get
        (info0.sourceTypeTag.get, info0.targetTypeTag) match {
          case ('[s], '[t]) =>
            val intoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
              case Some(into) => into
              case None       => '{ zio.blocks.schema.Into.derived[s, t] }
            }
            '{
              new zio.blocks.schema.Into[A, B] {
                private val conv0: zio.blocks.schema.Into[s, t] = $intoExpr

                def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                  val prod = input.asInstanceOf[Product]
                  conv0.into(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[s]) match {
                    case Right(v0) =>
                      Right(${
                        val newExpr = Select(New(Inferred(target)), constructor)
                        Apply(newExpr, List('{ v0 }.asTerm)).asExpr.asInstanceOf[Expr[B]]
                      })
                    case Left(err) => Left(err)
                  }
                }
              }
            }
        }

      case "none" =>
        info0.targetTypeTag match {
          case '[t] =>
            '{
              new zio.blocks.schema.Into[A, B] {
                def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                  val v0: t = None.asInstanceOf[t]
                  Right(${
                    val newExpr = Select(New(Inferred(target)), constructor)
                    Apply(newExpr, List('{ v0 }.asTerm)).asExpr.asInstanceOf[Expr[B]]
                  })
                }
              }
            }
        }

      case "default" =>
        info0.targetTypeTag match {
          case '[t] =>
            val defaultExpr = info0.defaultTerm.get.asExpr.asInstanceOf[Expr[t]]
            '{
              new zio.blocks.schema.Into[A, B] {
                def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                  val v0: t = $defaultExpr
                  Right(${
                    val newExpr = Select(New(Inferred(target)), constructor)
                    Apply(newExpr, List('{ v0 }.asTerm)).asExpr.asInstanceOf[Expr[B]]
                  })
                }
              }
            }
        }
    }
  }

  private def generateTwoFieldConversion[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    constructor: quotes.reflect.Symbol,
    info0: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    },
    info1: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Generate field extractors for each field
    def extractField(info: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }): (Expr[Product => Either[zio.blocks.schema.SchemaError, Any]], Type[?]) =
      info.conversionType match {
        case "direct" =>
          val sourceIdx = info.sourceIdx.get
          info.targetTypeTag match {
            case '[t] =>
              ('{ (prod: Product) => Right(prod.productElement(${ Expr(sourceIdx) })) }, Type.of[t])
          }

        case "into" =>
          val sourceIdx = info.sourceIdx.get
          (info.sourceTypeTag.get, info.targetTypeTag) match {
            case ('[s], '[t]) =>
              val intoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
                case Some(into) => into
                case None       => '{ zio.blocks.schema.Into.derived[s, t] }
              }
              (
                '{ (prod: Product) => $intoExpr.into(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[s]) },
                Type.of[t]
              )
          }

        case "none" =>
          info.targetTypeTag match {
            case '[t] =>
              ('{ (_: Product) => Right(None) }, Type.of[t])
          }

        case "default" =>
          info.targetTypeTag match {
            case '[t] =>
              val defaultExpr = info.defaultTerm.get.asExpr.asInstanceOf[Expr[t]]
              ('{ (_: Product) => Right($defaultExpr) }, Type.of[t])
          }
      }

    (info0.targetTypeTag, info1.targetTypeTag) match {
      case ('[t0], '[t1]) =>
        val (extract0, _) = extractField(info0)
        val (extract1, _) = extractField(info1)

        '{
          new zio.blocks.schema.Into[A, B] {
            private val f0 = $extract0
            private val f1 = $extract1

            def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
              val prod = input.asInstanceOf[Product]
              val r0   = f0(prod)
              val r1   = f1(prod)

              (r0, r1) match {
                case (Right(v0), Right(v1)) =>
                  Right(${
                    val newExpr = Select(New(Inferred(target)), constructor)
                    Apply(
                      newExpr,
                      List(
                        '{ v0.asInstanceOf[t0] }.asTerm,
                        '{ v1.asInstanceOf[t1] }.asTerm
                      )
                    ).asExpr.asInstanceOf[Expr[B]]
                  })
                case _ =>
                  val errors = List(r0, r1).collect { case Left(e) => e }
                  Left(errors.reduce(_ ++ _))
              }
            }
          }
        }
    }
  }

  private def generateThreeFieldConversion[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    constructor: quotes.reflect.Symbol,
    info0: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    },
    info1: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    },
    info2: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    def extractField(info: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }): Expr[Product => Either[zio.blocks.schema.SchemaError, Any]] =
      info.conversionType match {
        case "direct" =>
          val sourceIdx = info.sourceIdx.get
          '{ (prod: Product) => Right(prod.productElement(${ Expr(sourceIdx) })) }

        case "into" =>
          val sourceIdx = info.sourceIdx.get
          (info.sourceTypeTag.get, info.targetTypeTag) match {
            case ('[s], '[t]) =>
              val intoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
                case Some(into) => into
                case None       => '{ zio.blocks.schema.Into.derived[s, t] }
              }
              '{ (prod: Product) => $intoExpr.into(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[s]) }
          }

        case "none" =>
          '{ (_: Product) => Right(None) }

        case "default" =>
          info.targetTypeTag match {
            case '[t] =>
              val defaultExpr = info.defaultTerm.get.asExpr.asInstanceOf[Expr[t]]
              '{ (_: Product) => Right($defaultExpr) }
          }
      }

    (info0.targetTypeTag, info1.targetTypeTag, info2.targetTypeTag) match {
      case ('[t0], '[t1], '[t2]) =>
        val extract0 = extractField(info0)
        val extract1 = extractField(info1)
        val extract2 = extractField(info2)

        '{
          new zio.blocks.schema.Into[A, B] {
            private val f0 = $extract0
            private val f1 = $extract1
            private val f2 = $extract2

            def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
              val prod = input.asInstanceOf[Product]
              val r0   = f0(prod)
              val r1   = f1(prod)
              val r2   = f2(prod)

              (r0, r1, r2) match {
                case (Right(v0), Right(v1), Right(v2)) =>
                  Right(${
                    val newExpr = Select(New(Inferred(target)), constructor)
                    Apply(
                      newExpr,
                      List(
                        '{ v0.asInstanceOf[t0] }.asTerm,
                        '{ v1.asInstanceOf[t1] }.asTerm,
                        '{ v2.asInstanceOf[t2] }.asTerm
                      )
                    ).asExpr.asInstanceOf[Expr[B]]
                  })
                case _ =>
                  val errors = List(r0, r1, r2).collect { case Left(e) => e }
                  Left(errors.reduce(_ ++ _))
              }
            }
          }
        }
    }
  }

  private def generateMultiFieldConversion[A: Type, B: Type](using
    Quotes
  )(
    target: quotes.reflect.TypeRepr,
    constructor: quotes.reflect.Symbol,
    fieldInfos: Seq[{
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }]
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // For larger products, we need to generate code that handles each field
    // We'll use a simpler approach: generate direct inline code without arrays

    val numFields = fieldInfos.size

    // Build the conversion logic as a single expression
    // We generate:
    // val r0 = ...; val r1 = ...; ...
    // if all Right then construct, else collect errors

    def extractField(info: {
      val targetIdx: Int; val conversionType: String; val sourceIdx: Option[Int]; val sourceTypeTag: Option[Type[?]];
      val targetTypeTag: Type[?]; val defaultTerm: Option[quotes.reflect.Term]
    }): Expr[Product => Either[zio.blocks.schema.SchemaError, Any]] =
      info.conversionType match {
        case "direct" =>
          val sourceIdx = info.sourceIdx.get
          '{ (prod: Product) => Right(prod.productElement(${ Expr(sourceIdx) })) }

        case "into" =>
          val sourceIdx = info.sourceIdx.get
          (info.sourceTypeTag.get, info.targetTypeTag) match {
            case ('[s], '[t]) =>
              val intoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
                case Some(into) => into
                case None       => '{ zio.blocks.schema.Into.derived[s, t] }
              }
              '{ (prod: Product) => $intoExpr.into(prod.productElement(${ Expr(sourceIdx) }).asInstanceOf[s]) }
          }

        case "none" =>
          '{ (_: Product) => Right(None) }

        case "default" =>
          info.targetTypeTag match {
            case '[t] =>
              val defaultExpr = info.defaultTerm.get.asExpr.asInstanceOf[Expr[t]]
              '{ (_: Product) => Right($defaultExpr) }
          }
      }

    // Create list of extractors
    val extractors: List[Expr[Product => Either[zio.blocks.schema.SchemaError, Any]]] =
      fieldInfos.map(extractField).toList

    // Get target types for casting
    val targetTypes: List[Type[?]] = fieldInfos.map(_.targetTypeTag).toList

    // Generate the Into instance
    val extractorsExpr =
      Expr.ofList(extractors.toSeq)(using Type.of[Product => Either[zio.blocks.schema.SchemaError, Any]])
    '{
      new zio.blocks.schema.Into[A, B] {
        private val extractors: List[Product => Either[zio.blocks.schema.SchemaError, Any]] =
          ${ extractorsExpr }

        def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
          val prod    = input.asInstanceOf[Product]
          val results = extractors.map(_(prod))

          val errors = results.collect { case Left(e) => e }
          if (errors.nonEmpty) {
            Left(errors.reduce(_ ++ _))
          } else {
            val values = results.collect { case Right(v) => v }
            Right(${
              val newExpr = Select(New(Inferred(target)), constructor)
              val args    = (0 until numFields)
                .zip(targetTypes)
                .map { case (i, tpe) =>
                  tpe match {
                    case '[t] =>
                      '{ values(${ Expr(i) }).asInstanceOf[t] }.asTerm
                  }
                }
                .toList
              Apply(newExpr, args).asExpr.asInstanceOf[Expr[B]]
            })
          }
        }
      }
    }
  }

}
