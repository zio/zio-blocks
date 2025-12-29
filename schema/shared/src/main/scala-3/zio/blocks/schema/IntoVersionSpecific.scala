package zio.blocks.schema

import scala.annotation.tailrec
import scala.quoted.*

trait IntoVersionSpecific {
  inline def derived[A, B]: Into[A, B] = ${ IntoVersionSpecificImpl.derived[A, B] }
}

private object IntoVersionSpecificImpl {
  def derived[A: Type, B: Type](using Quotes): Expr[Into[A, B]] =
    new IntoVersionSpecificImpl().derive[A, B]
}

private class IntoVersionSpecificImpl(using Quotes) extends MacroUtils {
  import quotes.reflect.*

  // Cache for Into instances to handle recursive resolution
  private val intoRefs = scala.collection.mutable.HashMap[(TypeRepr, TypeRepr), Expr[Into[?, ?]]]()

  // === Derivation logic ===

  def derive[A: Type, B: Type]: Expr[Into[A, B]] = {
    val aTpe = TypeRepr.of[A]
    val bTpe = TypeRepr.of[B]

    // Check if both types are primitives - if so, there should be a predefined instance
    def isPrimitiveOrBoxed(tpe: TypeRepr): Boolean = {
      val sym = tpe.typeSymbol
      sym == defn.ByteClass || sym == defn.ShortClass ||
      sym == defn.IntClass || sym == defn.LongClass ||
      sym == defn.FloatClass || sym == defn.DoubleClass ||
      sym == defn.CharClass || sym == defn.BooleanClass ||
      sym == defn.StringClass
    }

    // For primitive-to-primitive conversions, look up the predefined implicit
    if (isPrimitiveOrBoxed(aTpe) && isPrimitiveOrBoxed(bTpe)) {
      Expr.summon[Into[A, B]] match {
        case Some(existingInto) =>
          return existingInto
        case None =>
          fail(noPrimitiveConversionError(aTpe, bTpe))
      }
    }

    // Check for container types (Option, Either, List, Set, Map, etc.)
    def isContainerType(tpe: TypeRepr): Boolean = {
      val dealiased = tpe.dealias
      dealiased <:< TypeRepr.of[Option[Any]] ||
      dealiased <:< TypeRepr.of[Either[Any, Any]] ||
      dealiased <:< TypeRepr.of[Iterable[Any]] ||
      dealiased <:< TypeRepr.of[Array[Any]] ||
      dealiased <:< TypeRepr.of[Map[Any, Any]]
    }

    // For container-to-container conversions, look up predefined implicit
    if (isContainerType(aTpe) || isContainerType(bTpe)) {
      Expr.summon[Into[A, B]] match {
        case Some(existingInto) =>
          return existingInto
        case None =>
        // If no predefined instance found, fall through to other handling
      }
    }

    // Check if type is a case object (singleton type)
    def isCaseObjectType(tpe: TypeRepr): Boolean =
      tpe match {
        case TermRef(_, _) =>
          val sym = tpe.termSymbol
          sym.flags.is(Flags.Case) && sym.flags.is(Flags.Module)
        case _ => false
      }

    val aIsCaseObject = isCaseObjectType(aTpe)
    val bIsCaseObject = isCaseObjectType(bTpe)
    val aIsProduct    = aTpe.classSymbol.exists(isProductType)
    val bIsProduct    = bTpe.classSymbol.exists(isProductType)
    val aIsTuple      = isTupleType(aTpe)
    val bIsTuple      = isTupleType(bTpe)
    val aIsCoproduct  = isCoproductType(aTpe)
    val bIsCoproduct  = isCoproductType(bTpe)
    val aIsStructural = isStructuralType(aTpe)
    val bIsStructural = isStructuralType(bTpe)

    // Handle case object conversions first (before product matching)
    (
      aIsCaseObject,
      bIsCaseObject,
      aIsProduct,
      bIsProduct,
      aIsTuple,
      bIsTuple,
      aIsCoproduct,
      bIsCoproduct,
      aIsStructural,
      bIsStructural
    ) match {
      case (true, true, _, _, _, _, _, _, _, _) =>
        // Case object to case object
        deriveCaseObjectToCaseObject[A, B](bTpe)
      case (true, _, _, true, _, _, _, _, _, _) =>
        // Case object to case class
        deriveCaseObjectToProduct[A, B](bTpe)
      case (_, true, true, _, _, _, _, _, _, _) =>
        // Case class to case object
        deriveProductToCaseObject[A, B](bTpe)
      // Handle structural types BEFORE product-to-product to handle refined types like `Record { def x: Int }`
      case (_, _, _, true, _, _, _, _, true, _) =>
        // Structural type -> Product (structural source to case class target)
        // Allow Selectable types on all platforms, but non-Selectable structural types only on JVM
        if (!Platform.supportsReflection && !isSelectableType(aTpe)) {
          fail(
            s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Structural type conversions are not supported on ${Platform.name}.
               |
               |Structural types require reflection APIs (getClass.getMethod) which are only available on JVM.
               |
               |Consider:
               |  - Implementing Selectable trait to enable cross-platform support
               |  - Using a case class instead of a structural type
               |  - Using a tuple instead of a structural type
               |  - Only using structural type conversions in JVM-only code""".stripMargin
          )
        }
        deriveStructuralToProduct[A, B](aTpe, bTpe)
      case (_, _, true, _, _, _, _, _, _, true) =>
        // Product -> Structural (case class source to structural target)
        // Check if the target is a Selectable type - if so, we can handle it on all platforms
        // as long as it has a Map constructor
        if (!Platform.supportsReflection && !isSelectableType(bTpe)) {
          fail(
            s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Structural type conversions are not supported on ${Platform.name}.
               |
               |Structural types require reflection APIs which are only available on JVM.
               |
               |Consider:
               |  - Using a Selectable type with a Map[String, Any] constructor for cross-platform support
               |  - Using a case class instead of a structural type
               |  - Using a tuple instead of a structural type
               |  - Only using structural type conversions in JVM-only code""".stripMargin
          )
        }
        deriveProductToStructural[A, B](aTpe, bTpe)
      case (_, _, true, true, _, _, _, _, _, _) =>
        // Case class to case class (no structural refinements)
        deriveProductToProduct[A, B](aTpe, bTpe)
      case (_, _, true, _, _, true, _, _, _, _) =>
        // Case class to tuple
        deriveCaseClassToTuple[A, B](aTpe, bTpe)
      case (_, _, _, true, true, _, _, _, _, _) =>
        // Tuple to case class
        deriveTupleToCaseClass[A, B](aTpe, bTpe)
      case (_, _, _, _, true, true, _, _, _, _) =>
        // Tuple to tuple
        deriveTupleToTuple[A, B](aTpe, bTpe)
      case (_, _, _, _, _, _, true, true, _, _) =>
        // Coproduct to coproduct (sealed trait/enum to sealed trait/enum)
        deriveCoproductToCoproduct[A, B](aTpe, bTpe)
      case _ =>
        // Check for opaque type conversions
        if (requiresOpaqueConversion(aTpe, bTpe)) {
          deriveOpaqueConversion[A, B](aTpe, bTpe)
        } else if (requiresOpaqueUnwrapping(aTpe, bTpe)) {
          deriveOpaqueUnwrapping[A, B]
        } else if (requiresNewtypeConversion(aTpe, bTpe)) {
          deriveNewtypeConversion[A, B](bTpe)
        } else if (requiresNewtypeUnwrapping(aTpe, bTpe)) {
          deriveNewtypeUnwrapping[A, B]
        } else {
          val sourceKind =
            if (aIsProduct) "product" else if (aIsTuple) "tuple" else if (aIsCoproduct) "coproduct" else "other"
          val targetKind =
            if (bIsProduct) "product" else if (bIsTuple) "tuple" else if (bIsCoproduct) "coproduct" else "other"
          fail(unsupportedTypeCombinationError(aTpe, bTpe, sourceKind, targetKind))
        }
    }
  }

  private def deriveOpaqueConversion[A: Type, B: Type](aTpe: TypeRepr, bTpe: TypeRepr): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = ${
          convertToOpaqueTypeEither('a.asTerm, aTpe, bTpe, "value").asExprOf[Either[SchemaError, B]]
        }
      }
    }

  private def deriveOpaqueUnwrapping[A: Type, B: Type]: Expr[Into[A, B]] =
    // Opaque type unwrapping is safe at runtime since they are the same type
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = Right(a.asInstanceOf[B])
      }
    }

  private def deriveNewtypeConversion[A: Type, B: Type](bTpe: TypeRepr): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = ${
          convertToNewtypeEither('a.asTerm, bTpe, "value").asExprOf[Either[SchemaError, B]]
        }
      }
    }

  private def deriveNewtypeUnwrapping[A: Type, B: Type]: Expr[Into[A, B]] =
    // Newtype unwrapping is safe at runtime since they are the same type (for Subtype/Newtype)
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = Right(a.asInstanceOf[B])
      }
    }

  private def requiresOpaqueUnwrapping(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isOpaqueType(sourceTpe) && {
      val underlying = getOpaqueUnderlying(sourceTpe)
      underlying =:= targetTpe
    }

  private def requiresNewtypeUnwrapping(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isZIONewtype(sourceTpe) && {
      val underlying = getNewtypeUnderlying(sourceTpe)
      underlying =:= targetTpe
    }

  private def isTupleType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[Tuple] || defn.isTupleClass(tpe.typeSymbol)

  private def getTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] =
    if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
    else typeArgs(tpe)

  // === Unified Tuple Helpers ===

  /** Get a tuple element at the given index. Works for any tuple size. */
  private def tupleElement(tupleExpr: Term, tupleTpe: TypeRepr, index: Int): Term = {
    val tupleSize = getTupleTypeArgs(tupleTpe).size
    if (tupleSize <= 22) {
      val accessorName = s"_${index + 1}"
      val accessor     = tupleTpe.typeSymbol.methodMember(accessorName).head
      Select(tupleExpr, accessor)
    } else {
      val productElementSym = TypeRepr.of[Product].typeSymbol.methodMember("productElement").head
      Apply(Select(tupleExpr, productElementSym), List(Literal(IntConstant(index))))
    }
  }

  /** Build a tuple from element Terms. Works for any tuple size. */
  private def buildTuple[B: Type](elements: List[Term], targetTypeArgs: List[TypeRepr]): Expr[B] = {
    val tupleSize = elements.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod    = tupleCompanion.methodMember("apply").head
      Apply(
        Select(Ref(tupleCompanion), applyMethod).appliedToTypes(targetTypeArgs),
        elements
      ).asExprOf[B]
    } else {
      val anyElements: List[Expr[Any]] = elements.map(_.asExprOf[Any])
      val arrayExpr                    = '{ Array[Any](${ Varargs(anyElements) }: _*) }
      '{ scala.runtime.Tuples.fromArray($arrayExpr.asInstanceOf[Array[Object]]).asInstanceOf[B] }
    }
  }

  /** Build a tuple from element Exprs. Works for any tuple size. */
  private def buildTupleFromExprs[B: Type](elements: List[Expr[Any]], targetTypeArgs: List[TypeRepr]): Expr[B] = {
    val tupleSize = elements.size
    if (tupleSize <= 22) {
      val tupleCompanion = Symbol.requiredModule(s"scala.Tuple$tupleSize")
      val applyMethod    = tupleCompanion.methodMember("apply").head
      Apply(
        Select(Ref(tupleCompanion), applyMethod).appliedToTypes(targetTypeArgs),
        elements.map(_.asTerm)
      ).asExprOf[B]
    } else {
      val arrayExpr = '{ Array[Any](${ Varargs(elements) }: _*) }
      '{ scala.runtime.Tuples.fromArray($arrayExpr.asInstanceOf[Array[Object]]).asInstanceOf[B] }
    }
  }

  private def isCoproductType(tpe: TypeRepr): Boolean =
    isSealedTraitOrAbstractClass(tpe) || isEnum(tpe)

  private def isEnum(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Enum) && !tpe.typeSymbol.flags.is(Flags.Case)

  // === Structural helpers ===

  private def isStructuralType(tpe: TypeRepr): Boolean = {
    val dealised = tpe.dealias
    dealised match {
      case Refinement(_, _, _) => true
      case _                   => false
    }
  }

  private def isSelectableType(tpe: TypeRepr): Boolean =
    tpe <:< TypeRepr.of[scala.Selectable]

  private def getStructuralMembers(tpe: TypeRepr): List[(String, TypeRepr)] = {
    def collectMembers(t: TypeRepr): List[(String, TypeRepr)] = t match {
      case Refinement(parent, name, info) =>
        val memberType = info match {
          case MethodType(_, _, returnType) => returnType
          case ByNameType(underlying)       => underlying
          case other                        => other
        }
        (name, memberType) :: collectMembers(parent)
      case _ => Nil
    }
    collectMembers(tpe.dealias).reverse
  }

  // === Structural Type to Product ===

  private def deriveStructuralToProduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val structuralMembers  = getStructuralMembers(aTpe)
    val targetInfo         = new ProductInfo[B](bTpe)
    val sourceIsSelectable = isSelectableType(aTpe)

    // For each target field, find matching structural member (by name or unique type)
    // or use default value/None for optional fields
    val fieldMappings: List[(Option[(String, TypeRepr)], FieldInfo)] = targetInfo.fields.map { targetField =>
      val matchingMember = structuralMembers.find { case (name, memberTpe) =>
        name == targetField.name && (memberTpe =:= targetField.tpe || requiresOpaqueConversion(
          memberTpe,
          targetField.tpe
        ) || requiresNewtypeConversion(memberTpe, targetField.tpe) || findImplicitInto(
          memberTpe,
          targetField.tpe
        ).isDefined)
      }.orElse {
        val uniqueTypeMatches = structuralMembers.filter { case (_, memberTpe) =>
          memberTpe =:= targetField.tpe || requiresOpaqueConversion(
            memberTpe,
            targetField.tpe
          ) || requiresNewtypeConversion(memberTpe, targetField.tpe) || findImplicitInto(
            memberTpe,
            targetField.tpe
          ).isDefined
        }
        if (uniqueTypeMatches.size == 1) Some(uniqueTypeMatches.head) else None
      }

      matchingMember match {
        case Some((memberName, memberTpe)) => (Some((memberName, memberTpe)), targetField)
        case None                          =>
          // No matching structural member - check for default value or Option type
          if (targetField.hasDefault && targetField.defaultValue.isDefined) {
            (None, targetField) // Will use default value
          } else if (isOptionType(targetField.tpe)) {
            (None, targetField) // Will use None
          } else {
            val sourceMembers = structuralMembers.map { case (n, t) => s"$n: ${t.show}" }.mkString(", ")
            fail(
              s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Missing structural member
                 |
                 |  Source structural type has: { $sourceMembers }
                 |  Target field required: ${targetField.name}: ${targetField.tpe.show}
                 |
                 |No matching member found in structural type for target field '${targetField.name}'.
                 |
                 |Consider:
                 |  - Ensuring the structural type has member '${targetField.name}: ${targetField.tpe.show}'
                 |  - Making '${targetField.name}' an Option type (defaults to None)
                 |  - Adding a default value for '${targetField.name}' in the target type""".stripMargin
            )
          }
      }
    }

    if (sourceIsSelectable) {
      // For Selectable types, use selectDynamic which works on all platforms
      deriveSelectableToProduct[A, B](fieldMappings, targetInfo)
    } else {
      // For non-Selectable structural types, use reflection (JVM only)
      deriveReflectiveStructuralToProduct[A, B](fieldMappings, targetInfo)
    }
  }

  // Selectable-based structural type conversion - works on all platforms
  // For Selectable types, we call selectDynamic which is implemented by the class
  private def deriveSelectableToProduct[A: Type, B: Type](
    fieldMappings: List[(Option[(String, TypeRepr)], FieldInfo)],
    targetInfo: ProductInfo[B]
  ): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${
            val aExpr            = 'a
            val args: List[Term] = fieldMappings.map { case (memberOpt, targetField) =>
              memberOpt match {
                case Some((memberName, memberTpe)) =>
                  // Call selectDynamic on the Selectable instance
                  // The actual type A extends Selectable and has selectDynamic
                  targetField.tpe.asType match {
                    case '[t] =>
                      memberTpe.asType match {
                        case '[mt] =>
                          val memberNameExpr = Expr(memberName)
                          // Find the selectDynamic method on the type
                          val selectDynamicSym = TypeRepr
                            .of[A]
                            .typeSymbol
                            .methodMember("selectDynamic")
                            .headOption
                            .getOrElse(
                              report.errorAndAbort(
                                s"Type ${TypeRepr.of[A].show} extends Selectable but has no selectDynamic method"
                              )
                            )
                          val selectCall = Apply(
                            Select(aExpr.asTerm, selectDynamicSym),
                            List(memberNameExpr.asTerm)
                          )
                          if (memberTpe =:= targetField.tpe) {
                            '{ ${ selectCall.asExprOf[Any] }.asInstanceOf[t] }.asTerm
                          } else {
                            // Need conversion
                            findImplicitInto(memberTpe, targetField.tpe) match {
                              case Some(intoInstance) =>
                                val typedInto = intoInstance.asExprOf[Into[mt, t]]
                                '{
                                  val value = ${ selectCall.asExprOf[Any] }.asInstanceOf[mt]
                                  $typedInto.into(value) match {
                                    case Right(v) => v
                                    case Left(_)  => throw new RuntimeException("Conversion failed")
                                  }
                                }.asTerm
                              case None =>
                                '{ ${ selectCall.asExprOf[Any] }.asInstanceOf[t] }.asTerm
                            }
                          }
                      }
                  }
                case None =>
                  // Use default value or None
                  if (targetField.hasDefault && targetField.defaultValue.isDefined) {
                    targetField.defaultValue.get
                  } else {
                    '{ None }.asTerm
                  }
              }
            }
            val resultExpr: Expr[B] = targetInfo.construct(args).asExprOf[B]
            (resultExpr)
          })
      }
    }

  // Reflection-based structural type conversion - JVM only
  private def deriveReflectiveStructuralToProduct[A: Type, B: Type](
    fieldMappings: List[(Option[(String, TypeRepr)], FieldInfo)],
    targetInfo: ProductInfo[B]
  ): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${
            val args: List[Term] = fieldMappings.map { case (memberOpt, targetField) =>
              memberOpt match {
                case Some((memberName, memberTpe)) =>
                  // Get value from structural member using reflection
                  targetField.tpe.asType match {
                    case '[t] =>
                      memberTpe.asType match {
                        case '[mt] =>
                          val memberNameExpr = Expr(memberName)
                          if (memberTpe =:= targetField.tpe) {
                            '{
                              val method = a.getClass.getMethod($memberNameExpr)
                              method.invoke(a).asInstanceOf[t]
                            }.asTerm
                          } else {
                            // Need conversion
                            findImplicitInto(memberTpe, targetField.tpe) match {
                              case Some(intoInstance) =>
                                val typedInto = intoInstance.asExprOf[Into[mt, t]]
                                '{
                                  val method = a.getClass.getMethod($memberNameExpr)
                                  val value  = method.invoke(a).asInstanceOf[mt]
                                  $typedInto.into(value) match {
                                    case Right(v) => v
                                    case Left(_)  => throw new RuntimeException("Conversion failed")
                                  }
                                }.asTerm
                              case None =>
                                // Should not happen since we checked earlier
                                '{
                                  val method = a.getClass.getMethod($memberNameExpr)
                                  method.invoke(a).asInstanceOf[t]
                                }.asTerm
                            }
                          }
                      }
                  }
                case None =>
                  // Use default value or None
                  if (targetField.hasDefault && targetField.defaultValue.isDefined) {
                    targetField.defaultValue.get
                  } else {
                    // Option type - return None
                    '{ None }.asTerm
                  }
              }
            }
            val resultExpr: Expr[B] = targetInfo.construct(args).asExprOf[B]
            (resultExpr)
          })
      }
    }

  // === Product to Structural Type ===

  private def deriveProductToStructural[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val structuralMembers = getStructuralMembers(bTpe)
    val sourceInfo        = new ProductInfo[A](aTpe)

    // Validate that all structural type members exist in the source product
    structuralMembers.foreach { case (memberName, memberTpe) =>
      val matchingField = sourceInfo.fields.find { f =>
        f.name == memberName && (f.tpe =:= memberTpe || f.tpe <:< memberTpe)
      }
      if (matchingField.isEmpty) {
        val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
        fail(
          s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Missing required member
             |
             |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
             |  Structural type requires: $memberName: ${memberTpe.show}
             |
             |Source type is missing member '$memberName: ${memberTpe.show}' required by the structural type.
             |
             |Consider:
             |  - Adding field '$memberName: ${memberTpe.show}' to ${aTpe.typeSymbol.name}
             |  - Using a source type that already has this member
             |  - Providing an explicit Into instance""".stripMargin
        )
      }
    }

    // Check if target type extends Selectable - if so, we can create it properly on all platforms
    if (isSelectableType(bTpe)) {
      deriveProductToSelectable[A, B](aTpe, bTpe, structuralMembers, sourceInfo)
    } else {
      // For non-Selectable structural types, we can only use a simple cast (JVM only, checked earlier)
      '{
        new Into[A, B] {
          def into(a: A): Either[SchemaError, B] =
            Right(a.asInstanceOf[B])
        }
      }
    }
  }

  // Helper to get the base class of a refinement type (e.g., Record from Record { def x: Int })
  private def getSelectableBaseClass(tpe: TypeRepr): Option[TypeRepr] = {
    @tailrec
    def findBase(t: TypeRepr): Option[TypeRepr] = t.dealias match {
      case Refinement(parent, _, _)                 => findBase(parent)
      case base if base <:< TypeRepr.of[Selectable] => Some(base)
      case _                                        => None
    }
    findBase(tpe)
  }

  // Get parameter types from a constructor by looking at its method type
  private def getConstructorParamTypes(ctor: Symbol): List[TypeRepr] = {
    // Get the method type of the constructor
    val methodType = ctor.typeRef.dealias match {
      case mt: MethodType                 => mt.paramTypes
      case PolyType(_, _, mt: MethodType) => mt.paramTypes
      case _                              => Nil
    }
    methodType
  }

  // Derive conversion from Product to Selectable using Map constructor only
  // This works on all platforms because we generate the Map construction at compile time
  private def deriveProductToSelectable[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    structuralMembers: List[(String, TypeRepr)],
    sourceInfo: ProductInfo[A]
  ): Expr[Into[A, B]] = {
    // Get the base Selectable class
    val baseClassOpt = getSelectableBaseClass(bTpe)

    baseClassOpt match {
      case Some(baseClass) =>
        // Try to find a Map constructor or apply method
        findMapConstructorOrApply(baseClass) match {
          case Some((_, method, _)) =>
            // Use Map constructor/apply - this works on all platforms
            deriveProductToSelectableViaMap[A, B](baseClass, method, structuralMembers, sourceInfo)
          case None =>
            // No Map constructor found - fail with helpful error
            fail(
              s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Selectable target requires Map constructor.
                 |
                 |The target Selectable type '${baseClass.typeSymbol.name}' does not have:
                 |  - A constructor taking Map[String, Any]
                 |  - An apply method taking Map[String, Any]
                 |
                 |To enable cross-platform Into derivation to Selectable types, add a Map constructor:
                 |
                 |  class MyRecord(map: Map[String, Any]) extends Selectable {
                 |    def selectDynamic(name: String): Any = map(name)
                 |  }
                 |
                 |Or if you prefer varargs, add a secondary Map constructor:
                 |
                 |  class MyRecord(elems: (String, Any)*) extends Selectable {
                 |    private val fields = elems.toMap
                 |    def this(map: Map[String, Any]) = this(map.toSeq*)
                 |    def selectDynamic(name: String): Any = fields(name)
                 |  }""".stripMargin
            )
        }
      case None =>
        // No base Selectable class found - this shouldn't happen
        fail(
          s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Cannot find Selectable base class.
             |
             |The target type appears to be a structural type but no Selectable base class was found.""".stripMargin
        )
    }
  }

  // Find a Map[String, Any] constructor or companion apply method
  // Prefers companion apply method over constructor (as constructor may be private)
  private def findMapConstructorOrApply(baseTpe: TypeRepr): Option[(Term, Symbol, Boolean)] = {
    val baseClass = baseTpe.typeSymbol

    // First try to find companion object with apply(Map[String, Any])
    // For inner classes, companionModule may return NoSymbol, so we also check owner declarations
    val stdCompanion = baseClass.companionModule

    val companion = if (stdCompanion != Symbol.noSymbol) {
      stdCompanion
    } else {
      // For inner classes, look in owner's declarations for a module with the same name
      val owner     = baseClass.owner
      val className = baseClass.name

      val found = owner.declarations.find { s =>
        val isMatch = s.name == className && s.flags.is(Flags.Module)
        isMatch
      }
      found.getOrElse(Symbol.noSymbol)
    }

    if (companion != Symbol.noSymbol) {
      val companionRef = Ref(companion)
      // Get the module class - for objects, methodMembers are on the module class
      val moduleClass = companion.moduleClass

      // Look for apply methods - try multiple approaches
      val applyMethodsFromModuleClassDecl = if (moduleClass != Symbol.noSymbol) {
        val methods = moduleClass.declaredMethods.filter(_.name == "apply")
        methods
      } else Nil

      val applyMethodsFromCompanion = companion.methodMembers.filter(_.name == "apply")

      val applyMethodsFromModuleClass = if (moduleClass != Symbol.noSymbol) {
        val methods = moduleClass.methodMembers.filter(_.name == "apply")
        methods
      } else Nil

      val applyMethods =
        (applyMethodsFromModuleClassDecl ++ applyMethodsFromCompanion ++ applyMethodsFromModuleClass).distinct

      val mapApply = applyMethods.find { m =>
        // Get parameter types using the companion's type ref
        val memberType = companion.typeRef.memberType(m)
        val paramTypes = memberType match {
          case mt: MethodType =>
            mt.paramTypes
          case PolyType(_, _, mt: MethodType) =>
            mt.paramTypes
          case _ =>
            // Fallback: try to get from the method symbol directly
            val fromSymss = m.paramSymss.flatten.headOption.map(_.typeRef).toList
            fromSymss
        }
        val result = paramTypes.size == 1 && {
          val paramTpe = paramTypes.head
          val isMap    = paramTpe <:< TypeRepr.of[Map[String, Any]] ||
            paramTpe <:< TypeRepr.of[collection.Map[String, Any]]
          isMap
        }
        result
      }

      if (mapApply.isDefined) {
        return Some((companionRef, mapApply.get, true))
      }
    }

    // Fall back to looking for a public constructor taking Map[String, Any]
    val allConstructors = baseClass.declarations.filter { s =>
      (s.isClassConstructor || s.name == "<init>") && !s.flags.is(Flags.Private) && !s.flags.is(Flags.Protected)
    }.toList

    val constructors =
      if (
        baseClass.primaryConstructor != Symbol.noSymbol &&
        !baseClass.primaryConstructor.flags.is(Flags.Private) &&
        !baseClass.primaryConstructor.flags.is(Flags.Protected)
      ) {
        baseClass.primaryConstructor :: allConstructors.filterNot(_ == baseClass.primaryConstructor)
      } else {
        allConstructors
      }

    val mapCtor = constructors.find { ctor =>
      val paramTypes = getConstructorParamTypesRobust(ctor, baseTpe)
      paramTypes.size == 1 && {
        val paramTpe = paramTypes.head
        val isMap    = paramTpe <:< TypeRepr.of[Map[String, Any]] ||
          paramTpe <:< TypeRepr.of[collection.Map[String, Any]]
        isMap
      }
    }

    if (mapCtor.isDefined) {
      return Some((New(Inferred(baseTpe)), mapCtor.get, false))
    }

    None
  }

  // Get constructor parameter types - robust version that handles secondary constructors
  private def getConstructorParamTypesRobust(ctor: Symbol, classTpe: TypeRepr): List[TypeRepr] =
    // First try: use the symbol's tree if available
    try {
      // Get the method type from the class type's member
      val ctorType = classTpe.memberType(ctor)
      ctorType match {
        case mt: MethodType                 => mt.paramTypes
        case PolyType(_, _, mt: MethodType) => mt.paramTypes
        case _                              =>
          // Fallback to typeRef approach
          getConstructorParamTypes(ctor)
      }
    } catch {
      case _: Exception => getConstructorParamTypes(ctor)
    }

  // Derive using Map constructor or apply method - compile-time code generation
  private def deriveProductToSelectableViaMap[A: Type, B: Type](
    baseClass: TypeRepr,
    method: Symbol,
    structuralMembers: List[(String, TypeRepr)],
    sourceInfo: ProductInfo[A]
  ): Expr[Into[A, B]] =

    baseClass.asType match {
      case '[bc] =>
        // We need to generate code in a way that doesn't mix compile-time and runtime references
        // The approach: generate a list of field name -> getter pairs at compile time,
        // then at runtime build the map and call apply

        // Build the list of field extractors at compile time
        val fieldExtractors: List[Expr[A => (String, Any)]] = structuralMembers.map { case (memberName, _) =>
          val sourceField = sourceInfo.fields.find(_.name == memberName).get
          sourceField.tpe.asType match {
            case '[t] =>
              val nameExpr = Expr(memberName)
              val getter   = sourceField.getter
              '{ (a: A) => ($nameExpr, ${ Select('{ a }.asTerm, getter).asExprOf[t] }: Any) }
          }
        }

        val extractorsListExpr: Expr[List[A => (String, Any)]] = Expr.ofList(fieldExtractors)

        // Create the constructor/apply function
        val companionSym = method.owner.companionModule

        // Try to get the companion module type properly
        // The companion is a module (object), so its type is a singleton type
        val companionType = companionSym.typeRef

        val applyToMap: Expr[Map[String, Any] => bc] = companionType.asType match {
          case '[ct] =>
            '{ (m: Map[String, Any]) =>
              ${
                // Use the properly typed companion reference
                val companionRef = Ref(companionSym)
                val mTerm        = 'm.asTerm

                // Try using Apply with a specific method symbol rather than Select.overloaded
                // First select the specific method symbol
                val selectMethod = companionRef.select(method)

                // Then apply it
                val call = Apply(selectMethod, List(mTerm))

                call.asExprOf[bc]
              }
            }
        }

        '{
          new Into[A, B] {
            private val extractors = $extractorsListExpr
            private val applyFn    = $applyToMap

            def into(a: A): Either[SchemaError, B] = {
              val fieldMap: Map[String, Any] = extractors.map(_(a)).toMap
              Right(applyFn(fieldMap).asInstanceOf[B])
            }
          }
        }
    }

  // === Coproduct to Coproduct ===

  private def deriveCoproductToCoproduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceSubtypes = directSubTypes(aTpe)
    val targetSubtypes = directSubTypes(bTpe)

    // Build case mapping: for each source subtype, find matching target subtype
    val caseMappings = sourceSubtypes.map { sourceSubtype =>
      findMatchingTargetSubtype(sourceSubtype, targetSubtypes, aTpe, bTpe) match {
        case Some(targetSubtype) => (sourceSubtype, targetSubtype)
        case None                =>
          fail(noMatchingCaseError(aTpe, bTpe, sourceSubtype))
      }
    }

    generateCoproductConversion[A, B](caseMappings)
  }

  private def findMatchingTargetSubtype(
    sourceSubtype: TypeRepr,
    targetSubtypes: List[TypeRepr],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Option[TypeRepr] = {
    // Get the name - for enum values use termSymbol, for others use typeSymbol
    val sourceName = getSubtypeName(sourceSubtype)

    // Priority 1: Match by name
    val nameMatch = targetSubtypes.find { targetSubtype =>
      getSubtypeName(targetSubtype) == sourceName
    }
    if (nameMatch.isDefined) return nameMatch

    // Priority 2: Match by signature (field types) - only for non-empty signatures
    val sourceSignature = getTypeSignature(sourceSubtype)
    if (sourceSignature.nonEmpty) {
      val signatureMatch = targetSubtypes.find { targetSubtype =>
        val targetSignature = getTypeSignature(targetSubtype)
        targetSignature.nonEmpty && signaturesMatch(sourceSignature, targetSignature)
      }
      if (signatureMatch.isDefined) return signatureMatch
    }

    // Priority 3: Match by position if same count
    val sourceIdx = directSubTypes(aTpe).indexOf(sourceSubtype)
    if (sourceIdx >= 0 && sourceIdx < targetSubtypes.size) {
      return Some(targetSubtypes(sourceIdx))
    }

    None
  }

  /**
   * Get the name of a subtype - handles enum values and case objects/classes
   */
  private def getSubtypeName(tpe: TypeRepr): String = {
    // For enum values and case objects, the termSymbol has the correct name
    val termSym = tpe.termSymbol
    if (termSym.exists) {
      termSym.name
    } else {
      tpe.typeSymbol.name
    }
  }

  /** Get the type signature of a case class/object - list of field types */
  private def getTypeSignature(tpe: TypeRepr): List[TypeRepr] =
    if (isEnumOrModuleValue(tpe)) {
      // Case object / enum value - no fields
      Nil
    } else {
      // Case class - get field types
      tpe.classSymbol match {
        case Some(cls) if isProductType(cls) =>
          val info = new ProductInfo[Any](tpe)(using Type.of[Any])
          info.fields.map(_.tpe)
        case _ => Nil
      }
    }

  private def signaturesMatch(source: List[TypeRepr], target: List[TypeRepr]): Boolean =
    source.size == target.size && source.zip(target).forall { case (s, t) =>
      s =:= t || requiresOpaqueConversion(s, t) || requiresNewtypeConversion(s, t) || findImplicitInto(s, t).isDefined
    }

  private def generateCoproductConversion[A: Type, B: Type](
    caseMappings: List[(TypeRepr, TypeRepr)]
  ): Expr[Into[A, B]] = {
    // Generate the match expression builder that will be called at runtime with 'a'
    // We need to build the CaseDef list inside the splice to avoid closure issues

    def buildMatchExpr(aExpr: Expr[A]): Expr[Either[SchemaError, B]] = {
      val cases = caseMappings.map { case (sourceSubtype, targetSubtype) =>
        generateCaseClause[B](sourceSubtype, targetSubtype)
      }
      Match(aExpr.asTerm, cases).asExprOf[Either[SchemaError, B]]
    }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = ${
          buildMatchExpr('a)
        }
      }
    }
  }

  private def generateCaseClause[B: Type](
    sourceSubtype: TypeRepr,
    targetSubtype: TypeRepr
  ): CaseDef = {
    val bindingName = Symbol.newVal(Symbol.spliceOwner, "x", sourceSubtype, Flags.Case, Symbol.noSymbol)
    val bindingRef  = Ref(bindingName)

    val pattern = Bind(bindingName, Typed(Wildcard(), Inferred(sourceSubtype)))

    val conversion: Term = if (isEnumOrModuleValue(sourceSubtype) && isEnumOrModuleValue(targetSubtype)) {
      // Case object to case object / enum value to enum value
      // For enum values and case objects, directSubTypes returns Ref(symbol).tpe
      // The termSymbol gives us the actual value reference
      val targetRef: Term = targetSubtype match {
        case tref: TermRef =>
          // TermRef - use its term symbol directly
          Ref(tref.termSymbol)
        case _ =>
          // Try to get module for case objects
          val sym = targetSubtype.typeSymbol
          if (sym.flags.is(Flags.Module)) {
            Ref(sym.companionModule)
          } else {
            fail(s"Cannot get reference for singleton type: ${targetSubtype.show}")
          }
      }

      '{ Right(${ targetRef.asExprOf[B] }) }.asTerm
    } else {
      // Case class to case class - convert fields
      generateCaseClassConversion[B](sourceSubtype, targetSubtype, bindingRef)
    }

    CaseDef(pattern, None, conversion)
  }

  private def generateCaseClassConversion[B: Type](
    sourceSubtype: TypeRepr,
    targetSubtype: TypeRepr,
    sourceRef: Term
  ): Term = {
    val sourceInfo = new ProductInfo[Any](sourceSubtype)(using Type.of[Any])
    val targetInfo = new ProductInfo[Any](targetSubtype)(using Type.of[Any])

    // Match fields between source and target case classes
    val fieldMappings = matchFields(sourceInfo, targetInfo, sourceSubtype, targetSubtype)

    // Build field conversions that return Either[SchemaError, FieldType]
    val fieldEithers = fieldMappings.zip(targetInfo.fields).map { case (mapping, targetFieldInfo) =>
      mapping.sourceField match {
        case Some(sourceField) =>
          val sourceValue = Select(sourceRef, sourceField.getter)
          val sourceTpe   = sourceField.tpe
          val targetTpe   = targetFieldInfo.tpe

          // Check if target is an opaque type with validation
          if (requiresOpaqueConversion(sourceTpe, targetTpe)) {
            convertToOpaqueTypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name).asTerm
          }
          // Check if target is a ZIO Prelude newtype with validation
          else if (requiresNewtypeConversion(sourceTpe, targetTpe)) {
            convertToNewtypeEither(sourceValue, targetTpe, sourceField.name).asTerm
          }
          // If types differ, try to find an implicit Into instance
          else if (!(sourceTpe =:= targetTpe)) {
            findImplicitInto(sourceTpe, targetTpe) match {
              case Some(intoInstance) =>
                sourceTpe.asType match {
                  case '[st] =>
                    targetTpe.asType match {
                      case '[tt] =>
                        val typedInto = intoInstance.asExprOf[Into[st, tt]]
                        '{
                          $typedInto.into(${ sourceValue.asExprOf[st] }).asInstanceOf[Either[SchemaError, Any]]
                        }.asTerm
                    }
                }
              case None =>
                // No coercion available - fail at compile time
                report.errorAndAbort(
                  s"Cannot find implicit Into[${sourceTpe.show}, ${targetTpe.show}] for field in coproduct case. " +
                    s"Please provide an implicit Into instance in scope."
                )
            }
          } else {
            // Types match exactly - wrap in Right
            '{ Right(${ sourceValue.asExprOf[Any] }) }.asTerm
          }
        case None =>
          // No source field - target must be Option[T], provide None wrapped in Right
          '{ Right(None) }.asTerm
      }
    }

    // Build nested flatMap chain to sequence Either values - reuse the same logic as product-to-product
    buildSequencedConstruction[B](
      fieldEithers.zipWithIndex.map { case (term, idx) => (idx, term.asExprOf[Either[SchemaError, Any]]) },
      targetInfo.asInstanceOf[ProductInfo[B]]
    ).asTerm
  }

  // === Case Object Conversions ===

  private def deriveCaseObjectToCaseObject[A: Type, B: Type](
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    // For case object to case object, just reference the target case object
    val targetRef = Ref(bTpe.termSymbol)
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ targetRef.asExprOf[B] })
      }
    }
  }

  private def deriveCaseObjectToProduct[A: Type, B: Type](
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    // Case object to case class: target class fields must be satisfiable
    // (all optional or with defaults)
    val targetInfo = new ProductInfo[B](bTpe)

    // All target fields must have defaults or be Option types
    val fieldExprs: List[Term] = targetInfo.fields.map { field =>
      if (field.hasDefault && field.defaultValue.isDefined) {
        field.defaultValue.get
      } else if (isOptionType(field.tpe)) {
        '{ None }.asTerm
      } else {
        val targetFieldsStr   = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
        val requiredFields    = targetInfo.fields.filterNot(f => f.hasDefault || isOptionType(f.tpe))
        val requiredFieldsStr = requiredFields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
        fail(
          s"""Cannot derive Into[${TypeRepr.of[A].show}, ${bTpe.show}]: Case object to case class conversion
             |
             |  Source: case object ${TypeRepr.of[A].typeSymbol.name}
             |  Target: ${bTpe.typeSymbol.name}($targetFieldsStr)
             |
             |Case object cannot provide value for required field '${field.name}: ${field.tpe.show}'.
             |Required fields without defaults: $requiredFieldsStr
             |
             |Consider:
             |  - Making '${field.name}' an Option[${field.tpe.show}] (defaults to None)
             |  - Adding a default value for '${field.name}' in ${bTpe.typeSymbol.name}
             |  - Using a case class source instead of case object""".stripMargin
        )
      }
    }

    val constructExpr = targetInfo.construct(fieldExprs).asExprOf[B]
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right($constructExpr)
      }
    }
  }

  private def deriveProductToCaseObject[A: Type, B: Type](
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    // Any case class can be converted to a case object (fields are discarded)
    val targetRef = Ref(bTpe.termSymbol)
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          Right(${ targetRef.asExprOf[B] })
      }
    }
  }

  // === Product to Product ===

  private def deriveProductToProduct[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceInfo    = new ProductInfo[A](aTpe)
    val targetInfo    = new ProductInfo[B](bTpe)
    val fieldMappings = matchFields(sourceInfo, targetInfo, aTpe, bTpe)
    generateProductConversion[A, B](sourceInfo, targetInfo, fieldMappings)
  }

  private case class FieldMapping(
    sourceField: Option[FieldInfo], // None means use default (None for Option types, or actual default value)
    targetField: FieldInfo,
    useDefaultValue: Boolean = false // true if we should use the target field's default value
  )

  private def matchFields(
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): List[FieldMapping] = {
    val sourceTypeFreq   = sourceInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val targetTypeFreq   = targetInfo.fields.groupBy(_.tpe.dealias.show).view.mapValues(_.size).toMap
    val usedSourceFields = scala.collection.mutable.Set[Int]()

    targetInfo.fields.map { targetField =>
      findMatchingSourceField(
        targetField,
        sourceInfo,
        targetInfo,
        sourceTypeFreq,
        targetTypeFreq,
        usedSourceFields,
        aTpe,
        bTpe
      ) match {
        case Some(sourceField) =>
          usedSourceFields += sourceField.index
          FieldMapping(Some(sourceField), targetField)
        case None =>
          // No matching source field found - check for default value or Option type
          if (targetField.hasDefault && targetField.defaultValue.isDefined) {
            // Use the actual default value
            FieldMapping(None, targetField, useDefaultValue = true)
          } else if (isOptionType(targetField.tpe)) {
            // Use None for Option types
            FieldMapping(None, targetField, useDefaultValue = false)
          } else {
            fail(noMatchingFieldError(aTpe, bTpe, sourceInfo, targetInfo, targetField))
          }
      }
    }
  }

  private def isOptionType(tpe: TypeRepr): Boolean =
    tpe.dealias.baseType(TypeRepr.of[Option[?]].typeSymbol) != TypeRepr.of[Nothing]

  private def findMatchingSourceField(
    targetField: FieldInfo,
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    sourceTypeFreq: Map[String, Int],
    targetTypeFreq: Map[String, Int],
    usedSourceFields: scala.collection.mutable.Set[Int],
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Option[FieldInfo] = {
    // Priority 1: Exact match - same name + same type
    val exactMatch = sourceInfo.fields.find { sf =>
      !usedSourceFields.contains(sf.index) && sf.name == targetField.name && sf.tpe =:= targetField.tpe
    }
    if (exactMatch.isDefined) {
      return exactMatch
    }

    // Priority 2: Name match with conversion - same name + implicit Into available or opaque type/newtype conversion
    val nameWithConversion = sourceInfo.fields.find { sf =>
      val nameMatches = !usedSourceFields.contains(sf.index) && sf.name == targetField.name
      if (nameMatches) {
        val opaqueConv    = requiresOpaqueConversion(sf.tpe, targetField.tpe)
        val opaqueUnwrap  = requiresOpaqueUnwrapping(sf.tpe, targetField.tpe)
        val newtypeConv   = requiresNewtypeConversion(sf.tpe, targetField.tpe)
        val newtypeUnwrap = requiresNewtypeUnwrapping(sf.tpe, targetField.tpe)
        val implicitInto  = findImplicitInto(sf.tpe, targetField.tpe).isDefined
        opaqueConv || opaqueUnwrap || newtypeConv || newtypeUnwrap || implicitInto
      } else {
        false
      }
    }
    if (nameWithConversion.isDefined) {
      return nameWithConversion
    }

    // Priority 3: Unique type match
    val targetTypeKey      = targetField.tpe.dealias.show
    val isTargetTypeUnique = targetTypeFreq.getOrElse(targetTypeKey, 0) == 1
    if (isTargetTypeUnique) {
      val uniqueTypeMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && sf.tpe =:= targetField.tpe
        }
      }
      if (uniqueTypeMatch.isDefined) return uniqueTypeMatch

      // Also check for unique type match with conversion (implicit Into, opaque type, or newtype)
      val uniqueConvertibleMatch = sourceInfo.fields.find { sf =>
        !usedSourceFields.contains(sf.index) && {
          val isSourceTypeUnique = sourceTypeFreq.getOrElse(sf.tpe.dealias.show, 0) == 1
          isSourceTypeUnique && (
            requiresOpaqueConversion(sf.tpe, targetField.tpe) ||
              requiresOpaqueUnwrapping(sf.tpe, targetField.tpe) ||
              requiresNewtypeConversion(sf.tpe, targetField.tpe) ||
              requiresNewtypeUnwrapping(sf.tpe, targetField.tpe) ||
              findImplicitInto(sf.tpe, targetField.tpe).isDefined
          )
        }
      }
      if (uniqueConvertibleMatch.isDefined) return uniqueConvertibleMatch
    }

    // Priority 4: Position + matching type
    // BUT: Don't use positional matching if the source field has an exact name match with another target field
    if (targetField.index < sourceInfo.fields.size) {
      val positionalField = sourceInfo.fields(targetField.index)
      if (!usedSourceFields.contains(positionalField.index)) {
        // Check if this source field has an exact name match with some target field
        // If so, reserve it for that exact match instead of using it positionally
        val hasExactNameMatchElsewhere = targetInfo.fields.exists { otherTarget =>
          otherTarget.name == positionalField.name &&
          otherTarget.name != targetField.name &&
          (positionalField.tpe =:= otherTarget.tpe ||
            findImplicitInto(positionalField.tpe, otherTarget.tpe).isDefined)
        }

        if (!hasExactNameMatchElsewhere) {
          if (positionalField.tpe =:= targetField.tpe) return Some(positionalField)
          // Also check for positional conversion (implicit Into, opaque type, or newtype)
          if (
            requiresOpaqueConversion(positionalField.tpe, targetField.tpe) ||
            requiresOpaqueUnwrapping(positionalField.tpe, targetField.tpe) ||
            requiresNewtypeConversion(positionalField.tpe, targetField.tpe) ||
            requiresNewtypeUnwrapping(positionalField.tpe, targetField.tpe) ||
            findImplicitInto(positionalField.tpe, targetField.tpe).isDefined
          ) {
            return Some(positionalField)
          }
        }
      }
    }

    None
  }

  // isCoercible has been removed - implicit Into resolution now handles all type conversions
  // including numeric widening/narrowing and opaque types

  // Find or derive an Into instance, using cache to handle recursion
  // Also looks for As instances and extracts Into from them
  private def findImplicitInto(sourceTpe: TypeRepr, targetTpe: TypeRepr): Option[Expr[Into[?, ?]]] =
    // Check cache first
    intoRefs.get((sourceTpe, targetTpe)) match {
      case some @ Some(_) => some
      case None           =>
        // Try to find implicit Into first
        val intoTpeApplied = TypeRepr.of[Into].typeSymbol.typeRef.appliedTo(List(sourceTpe, targetTpe))
        Implicits.search(intoTpeApplied) match {
          case success: ImplicitSearchSuccess =>
            val expr = success.tree.asExpr.asInstanceOf[Expr[Into[?, ?]]]
            // Cache it for future use
            intoRefs.update((sourceTpe, targetTpe), expr)
            Some(expr)
          case _ =>
            // Try to find implicit As[source, target] and extract Into from it
            val asTpeApplied = TypeRepr.of[As].typeSymbol.typeRef.appliedTo(List(sourceTpe, targetTpe))
            Implicits.search(asTpeApplied) match {
              case success: ImplicitSearchSuccess =>
                // Found As[A, B], create Into[A, B] that delegates to as.into
                (sourceTpe.asType, targetTpe.asType) match {
                  case ('[s], '[t]) =>
                    val asExpr   = success.tree.asExpr.asInstanceOf[Expr[As[s, t]]]
                    val intoExpr = '{
                      new Into[s, t] {
                        def into(input: s): Either[SchemaError, t] = $asExpr.into(input)
                      }
                    }.asInstanceOf[Expr[Into[?, ?]]]
                    intoRefs.update((sourceTpe, targetTpe), intoExpr)
                    Some(intoExpr)
                  case _ => None // Shouldn't happen, but satisfies exhaustivity
                }
              case _ =>
                // Also try As[target, source] and extract reverse Into from it
                val asReverseTpeApplied = TypeRepr.of[As].typeSymbol.typeRef.appliedTo(List(targetTpe, sourceTpe))
                Implicits.search(asReverseTpeApplied) match {
                  case success: ImplicitSearchSuccess =>
                    // Found As[B, A], create Into[A, B] that delegates to as.from
                    (sourceTpe.asType, targetTpe.asType) match {
                      case ('[s], '[t]) =>
                        val asExpr   = success.tree.asExpr.asInstanceOf[Expr[As[t, s]]]
                        val intoExpr = '{
                          new Into[s, t] {
                            def into(input: s): Either[SchemaError, t] = $asExpr.from(input)
                          }
                        }.asInstanceOf[Expr[Into[?, ?]]]
                        intoRefs.update((sourceTpe, targetTpe), intoExpr)
                        Some(intoExpr)
                      case _ => None // Shouldn't happen, but satisfies exhaustivity
                    }
                  case _ =>
                    None
                }
            }
        }
    }

  private def generateProductConversion[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[Into[A, B]] = {
    val convertedExpr = constructTarget[A, B](sourceInfo, targetInfo, fieldMappings)
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          ${ convertedExpr('a) }
      }
    }
  }

  private def constructTarget[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetInfo: ProductInfo[B],
    fieldMappings: List[FieldMapping]
  ): Expr[A] => Expr[Either[SchemaError, B]] = (aExpr: Expr[A]) => {
    val sourceTypeName = TypeRepr.of[A].typeSymbol.name
    val targetTypeName = TypeRepr.of[B].typeSymbol.name

    // Helper to wrap an Either expression with field context on error
    def wrapWithFieldContext(
      eitherExpr: Expr[Either[SchemaError, Any]],
      sourceFieldName: String,
      targetFieldName: String
    ): Expr[Either[SchemaError, Any]] = {
      val fieldDesc =
        if (sourceFieldName.nonEmpty)
          s"$sourceTypeName.$sourceFieldName → $targetTypeName.$targetFieldName"
        else
          s"$targetTypeName.$targetFieldName"
      val contextMsg = Expr(s"converting field '$fieldDesc'")
      '{
        $eitherExpr.left.map(err => SchemaError.conversionFailed($contextMsg, err))
      }
    }

    // Build list of field conversion expressions
    val fieldConversions: List[(Int, Expr[Either[SchemaError, Any]])] = fieldMappings.zipWithIndex.map {
      case (mapping, idx) =>
        val targetFieldInfo = targetInfo.fields(idx)
        val eitherExpr      = mapping.sourceField match {
          case Some(sourceField) =>
            val sourceValue     = sourceInfo.fieldGetter(aExpr.asTerm, sourceField)
            val sourceTpe       = sourceField.tpe
            val targetTpe       = targetFieldInfo.tpe
            val sourceFieldName = sourceField.name
            val targetFieldName = targetFieldInfo.name

            val rawExpr: Expr[Either[SchemaError, Any]] =
              // Check if target is an opaque type with validation
              if (requiresOpaqueConversion(sourceTpe, targetTpe)) {
                convertToOpaqueTypeEither(sourceValue, sourceTpe, targetTpe, sourceField.name)
              }
              // Check if source is an opaque type that needs unwrapping
              else if (requiresOpaqueUnwrapping(sourceTpe, targetTpe)) {
                // Opaque unwrapping is safe at runtime
                '{ Right(${ sourceValue.asExpr }.asInstanceOf[Any]) }
              }
              // Check if target is a ZIO Prelude newtype with validation
              else if (requiresNewtypeConversion(sourceTpe, targetTpe)) {
                convertToNewtypeEither(sourceValue, targetTpe, sourceField.name)
              }
              // Check if source is a ZIO Prelude newtype that needs unwrapping
              else if (requiresNewtypeUnwrapping(sourceTpe, targetTpe)) {
                // Newtype unwrapping is safe at runtime
                '{ Right(${ sourceValue.asExpr }.asInstanceOf[Any]) }
              }
              // If types differ, try to find an implicit Into instance
              else if (!(sourceTpe =:= targetTpe)) {
                findImplicitInto(sourceTpe, targetTpe) match {
                  case Some(intoInstance) =>
                    sourceTpe.asType match {
                      case '[st] =>
                        targetTpe.asType match {
                          case '[tt] =>
                            val typedInto = intoInstance.asExprOf[Into[st, tt]]
                            '{
                              $typedInto.into(${ sourceValue.asExprOf[st] }).asInstanceOf[Either[SchemaError, Any]]
                            }
                        }
                    }
                  case None =>
                    report.errorAndAbort(
                      noImplicitIntoError(
                        TypeRepr.of[A],
                        TypeRepr.of[B],
                        sourceTpe,
                        targetTpe,
                        sourceField.name
                      )
                    )
                }
              } else {
                // Types match exactly - wrap in Right
                '{ Right(${ sourceValue.asExprOf[Any] }) }
              }

            // Wrap with field context for error messages
            wrapWithFieldContext(rawExpr, sourceFieldName, targetFieldName)

          case None =>
            // No source field - use default value or None for Option types
            if (mapping.useDefaultValue && targetFieldInfo.defaultValue.isDefined) {
              // Use the actual default value
              val defaultTerm = targetFieldInfo.defaultValue.get
              '{ Right(${ defaultTerm.asExprOf[Any] }) }
            } else {
              // Use None for Option types
              '{ Right(None) }
            }
        }
        (idx, eitherExpr)
    }

    // Generate code that sequences the Either values and constructs the target
    // We need to build the args list at runtime based on the sequencing result
    buildSequencedConstruction[B](fieldConversions, targetInfo)
  }

  /**
   * Sequence a list of SchemaError Either expression.
   */
  inline private def sequenceEithers(
    conversions: List[Expr[Either[SchemaError, Any]]]
  )(using Quotes): Expr[Either[SchemaError, List[Any]]] = {
    val initial: Expr[Either[SchemaError, List[Any]]] = '{ Right(Nil) }
    conversions.foldRight(initial) { (convExpr, accExpr) =>
      '{
        $accExpr match {
          case Right(list) =>
            $convExpr match {
              case Right(value) => Right(value :: list)
              case Left(err)    => Left(err)
            }
          case Left(err) =>
            $convExpr match {
              case Right(_)   => Left(err)
              case Left(err2) => Left(err ++ err2)
            }
        }
      }
    }
  }

  /**
   * Build an expression that sequences field conversions and constructs the
   * target object with error accumulation
   */
  private def buildSequencedConstruction[B: Type](
    fieldConversions: List[(Int, Expr[Either[SchemaError, Any]])],
    targetInfo: ProductInfo[B]
  ): Expr[Either[SchemaError, B]] =
    fieldConversions match {
      case Nil =>
        // No fields - just construct empty object
        val emptyArgs = List.empty[Term]
        '{ Right(${ targetInfo.construct(emptyArgs).asExprOf[B] }) }

      case fields =>
        // Extract just the Either expressions in index order
        val orderedEithers = fields.sortBy(_._1).map(_._2)
        val sequenced      = sequenceEithers(orderedEithers)

        '{
          $sequenced match {
            case Right(values) =>
              val arr = values.toArray
              Right(${
                val args = targetInfo.fields.zipWithIndex.map { case (field, idx) =>
                  field.tpe.asType match {
                    case '[t] =>
                      '{ arr(${ Expr(idx) }).asInstanceOf[t] }.asTerm
                  }
                }
                targetInfo.construct(args).asExprOf[B]
              })
            case Left(err) => Left(err)
          }
        }
    }

  // === Case Class to Tuple ===

  private def deriveCaseClassToTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceInfo     = new ProductInfo[A](aTpe)
    val targetTypeArgs = getTupleTypeArgs(bTpe)

    if (sourceInfo.fields.size != targetTypeArgs.size) {
      val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
      val targetTypesStr  = targetTypeArgs.map(_.show).mkString(", ")
      fail(
        s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Arity mismatch
           |
           |  ${aTpe.typeSymbol.name}($sourceFieldsStr)  — ${sourceInfo.fields.size} fields
           |  Tuple${targetTypeArgs.size}[$targetTypesStr]  — ${targetTypeArgs.size} elements
           |
           |Case class has ${sourceInfo.fields.size} fields but target tuple has ${targetTypeArgs.size} elements.
           |
           |Consider:
           |  - Using a tuple with ${sourceInfo.fields.size} elements
           |  - Adding/removing fields to match the tuple size
           |  - Providing an explicit Into instance""".stripMargin
      )
    }

    // Check if any conversion needs an implicit Into instance (or opaque/newtype)
    val needsFailableConversions = sourceInfo.fields.zip(targetTypeArgs).exists { case (field, targetTpe) =>
      !(field.tpe =:= targetTpe) && (
        requiresOpaqueConversion(field.tpe, targetTpe) ||
          requiresNewtypeConversion(field.tpe, targetTpe) ||
          findImplicitInto(field.tpe, targetTpe).isDefined
      )
    }

    sourceInfo.fields.zip(targetTypeArgs).zipWithIndex.foreach { case ((field, targetTpe), idx) =>
      if (
        !(field.tpe =:= targetTpe) && !requiresOpaqueConversion(field.tpe, targetTpe) && !requiresNewtypeConversion(
          field.tpe,
          targetTpe
        ) && findImplicitInto(field.tpe, targetTpe).isEmpty
      ) {
        fail(
          s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Type mismatch at position $idx
             |
             |  Field: ${field.name} (position $idx)
             |  Source type: ${field.tpe.show}
             |  Target type: ${targetTpe.show}
             |
             |No implicit Into[${field.tpe.show}, ${targetTpe.show}] found.
             |
             |Consider:
             |  - Providing an implicit Into[${field.tpe.show}, ${targetTpe.show}]
             |  - Changing the types to be compatible
             |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
        )
      }
    }

    if (needsFailableConversions) {
      // Use failable path that sequences Either results
      buildCaseClassToTupleWithConversions[A, B](sourceInfo, targetTypeArgs)
    } else {
      // All types are exactly the same - no conversion needed
      val buildTuple = constructTupleFromCaseClass[A, B](sourceInfo, targetTypeArgs)
      '{
        new Into[A, B] {
          def into(a: A): Either[SchemaError, B] =
            Right(${ buildTuple('a) })
        }
      }
    }
  }

  private def buildCaseClassToTupleWithConversions[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetTypeArgs: List[TypeRepr]
  ): Expr[Into[A, B]] = {
    val tupleSize = targetTypeArgs.size

    // Pre-compute all implicit lookups at compile time
    val conversionInfo: List[(FieldInfo, TypeRepr, Option[Expr[Into[?, ?]]])] =
      sourceInfo.fields.zip(targetTypeArgs).map { case (field, targetTpe) =>
        val implicitInto =
          if (field.tpe =:= targetTpe) None
          else if (requiresOpaqueConversion(field.tpe, targetTpe) || requiresNewtypeConversion(field.tpe, targetTpe))
            None
          else findImplicitInto(field.tpe, targetTpe)
        (field, targetTpe, implicitInto)
      }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          ${
            val conversions: List[Expr[Either[SchemaError, Any]]] = conversionInfo.map {
              case (field, targetTpe, implicitIntoOpt) =>
                val fieldValue = sourceInfo.fieldGetter('a.asTerm, field)

                implicitIntoOpt match {
                  case Some(intoInstance) =>
                    // Use the implicit Into instance
                    field.tpe.asType match {
                      case '[st] =>
                        targetTpe.asType match {
                          case '[tt] =>
                            val typedInto = intoInstance.asExprOf[Into[st, tt]]
                            '{ $typedInto.into(${ fieldValue.asExprOf[st] }).map(_.asInstanceOf[Any]) }
                        }
                    }
                  case None if field.tpe =:= targetTpe =>
                    // Same type - no conversion needed
                    '{ Right(${ fieldValue.asExprOf[Any] }) }
                  case None if requiresOpaqueConversion(field.tpe, targetTpe) =>
                    // Opaque type conversion
                    convertToOpaqueTypeEither(fieldValue, field.tpe, targetTpe, field.name)
                      .asExprOf[Either[SchemaError, Any]]
                  case None if requiresNewtypeConversion(field.tpe, targetTpe) =>
                    // Newtype conversion
                    convertToNewtypeEither(fieldValue, targetTpe, field.name).asExprOf[Either[SchemaError, Any]]
                  case None =>
                    // Fallback: just cast (for reference types that are subtypes)
                    targetTpe.asType match {
                      case '[t] =>
                        '{ Right(${ fieldValue.asExprOf[Any] }.asInstanceOf[t].asInstanceOf[Any]) }
                    }
                }
            }

            val sequenced = sequenceEithers(conversions)

            // Build the target tuple
            '{
              $sequenced match {
                case Right(values) =>
                  val arr = values.toArray
                  Right(${
                    if (tupleSize <= 22) {
                      // Small tuple: use TupleN.apply
                      val args = targetTypeArgs.zipWithIndex.map { case (tpe, idx) =>
                        tpe.asType match {
                          case '[t] => '{ arr(${ Expr(idx) }).asInstanceOf[t] }.asTerm
                        }
                      }
                      buildTuple[B](args, targetTypeArgs)
                    } else {
                      // Large tuple: use Tuples.fromArray
                      '{ scala.runtime.Tuples.fromArray(arr.asInstanceOf[Array[Object]]).asInstanceOf[B] }
                    }
                  })
                case Left(err) => Left(err)
              }
            }
          }
      }
    }
  }

  // NOTE: This method is only called when ALL source types exactly match target types
  // (no conversions needed). For any type conversions, use buildCaseClassToTupleWithConversions.
  private def constructTupleFromCaseClass[A: Type, B: Type](
    sourceInfo: ProductInfo[A],
    targetTypeArgs: List[TypeRepr]
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val tupleSize = sourceInfo.fields.size
    if (tupleSize <= 22) {
      // Small tuple: use direct field access and TupleN.apply
      val args = sourceInfo.fields.map(field => sourceInfo.fieldGetter(aExpr.asTerm, field))
      buildTuple[B](args, targetTypeArgs)
    } else {
      // Large tuple: use Tuples.fromArray (no coercion since types match)
      val fieldsAsExprs = sourceInfo.fields.map { field =>
        sourceInfo.fieldGetter(aExpr.asTerm, field).asExprOf[Any]
      }
      buildTupleFromExprs[B](fieldsAsExprs, targetTypeArgs)
    }
  }

  // === Tuple to Case Class ===

  private def deriveTupleToCaseClass[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val targetInfo     = new ProductInfo[B](bTpe)

    if (sourceTypeArgs.size != targetInfo.fields.size) {
      val sourceTypesStr  = sourceTypeArgs.map(_.show).mkString(", ")
      val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
      fail(
        s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Arity mismatch
           |
           |  Tuple${sourceTypeArgs.size}[$sourceTypesStr]  — ${sourceTypeArgs.size} elements
           |  ${bTpe.typeSymbol.name}($targetFieldsStr)  — ${targetInfo.fields.size} fields
           |
           |Source tuple has ${sourceTypeArgs.size} elements but target case class has ${targetInfo.fields.size} fields.
           |
           |Consider:
           |  - Using a case class with ${sourceTypeArgs.size} fields
           |  - Using a tuple with ${targetInfo.fields.size} elements
           |  - Providing an explicit Into instance""".stripMargin
      )
    }

    sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.foreach { case ((sourceTpe, field), idx) =>
      if (
        !(sourceTpe =:= field.tpe) && !requiresOpaqueConversion(sourceTpe, field.tpe) && !requiresNewtypeConversion(
          sourceTpe,
          field.tpe
        ) && findImplicitInto(sourceTpe, field.tpe).isEmpty
      ) {
        fail(
          s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Type mismatch at position $idx
             |
             |  Tuple element $idx: ${sourceTpe.show}
             |  Target field: ${field.name}: ${field.tpe.show}
             |
             |No implicit Into[${sourceTpe.show}, ${field.tpe.show}] found.
             |
             |Consider:
             |  - Providing an implicit Into[${sourceTpe.show}, ${field.tpe.show}]
             |  - Changing the types to be compatible
             |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
        )
      }
    }

    // Check if any conversion needs an implicit Into instance (or opaque/newtype conversion)
    val needsFailableConversions = sourceTypeArgs.zip(targetInfo.fields).exists { case (sourceTpe, field) =>
      !(sourceTpe =:= field.tpe) && (
        requiresOpaqueConversion(sourceTpe, field.tpe) ||
          requiresNewtypeConversion(sourceTpe, field.tpe) ||
          findImplicitInto(sourceTpe, field.tpe).isDefined
      )
    }

    if (needsFailableConversions) {
      // Use failable path that sequences Either results for implicit/opaque/newtype conversions
      buildTupleToCaseClassWithConversions[A, B](aTpe, sourceTypeArgs, targetInfo)
    } else {
      // All types are exactly the same - no conversion needed
      val buildCaseClass = constructCaseClassFromTuple[A, B](aTpe, targetInfo)
      '{
        new Into[A, B] {
          def into(a: A): Either[SchemaError, B] =
            Right(${ buildCaseClass('a) })
        }
      }
    }
  }

  private def buildTupleToCaseClassWithConversions[A: Type, B: Type](
    aTpe: TypeRepr,
    sourceTypeArgs: List[TypeRepr],
    targetInfo: ProductInfo[B]
  ): Expr[Into[A, B]] = {
    // Pre-compute all implicit lookups at compile time
    val conversionInfo: List[(TypeRepr, FieldInfo, Int, Option[Expr[Into[?, ?]]])] =
      sourceTypeArgs.zip(targetInfo.fields).zipWithIndex.map { case ((sourceTpe, field), idx) =>
        val implicitInto =
          if (sourceTpe =:= field.tpe) None
          else if (requiresOpaqueConversion(sourceTpe, field.tpe) || requiresNewtypeConversion(sourceTpe, field.tpe))
            None
          else findImplicitInto(sourceTpe, field.tpe)
        (sourceTpe, field, idx, implicitInto)
      }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          ${
            // Build conversions that yield Either[SchemaError, Any] for each field
            val conversions: List[Expr[Either[SchemaError, Any]]] = conversionInfo.map {
              case (sourceTpe, field, idx, implicitIntoOpt) =>
                val elem      = tupleElement('a.asTerm, aTpe, idx)
                val targetTpe = field.tpe

                implicitIntoOpt match {
                  case Some(intoInstance) =>
                    // Use the implicit Into instance
                    sourceTpe.asType match {
                      case '[st] =>
                        targetTpe.asType match {
                          case '[tt] =>
                            val typedInto = intoInstance.asExprOf[Into[st, tt]]
                            '{ $typedInto.into(${ elem.asExprOf[Any] }.asInstanceOf[st]).map(_.asInstanceOf[Any]) }
                        }
                    }
                  case None if sourceTpe =:= targetTpe =>
                    // Same type - no conversion needed
                    '{ Right(${ elem.asExprOf[Any] }) }
                  case None if requiresOpaqueConversion(sourceTpe, targetTpe) =>
                    // Opaque type conversion
                    convertToOpaqueTypeEither(elem, sourceTpe, targetTpe, field.name).asExprOf[Either[SchemaError, Any]]
                  case None if requiresNewtypeConversion(sourceTpe, targetTpe) =>
                    // Newtype conversion
                    convertToNewtypeEither(elem, targetTpe, field.name).asExprOf[Either[SchemaError, Any]]
                  case None =>
                    // Should not reach here - we validated that implicit exists earlier
                    // Fallback: just cast (for reference types that are subtypes)
                    targetTpe.asType match {
                      case '[t] =>
                        '{ Right(${ elem.asExprOf[Any] }.asInstanceOf[t].asInstanceOf[Any]) }
                    }
                }
            }

            val sequenced = sequenceEithers(conversions)

            // Construct the case class from the list of values
            '{
              $sequenced match {
                case Right(values) =>
                  val arr = values.toArray
                  Right(${
                    // Construct case class using compile-time generated code
                    val args = targetInfo.fields.zipWithIndex.map { case (field, idx) =>
                      field.tpe.asType match {
                        case '[t] =>
                          '{ arr(${ Expr(idx) }).asInstanceOf[t] }.asTerm
                      }
                    }
                    targetInfo.construct(args).asExprOf[B]
                  })
                case Left(err) => Left(err)
              }
            }
          }
      }
    }
  }

  // NOTE: This method is only called when ALL source types exactly match target types
  // (no conversions needed). For any type conversions, use buildTupleToCaseClassWithConversions.
  private def constructCaseClassFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    targetInfo: ProductInfo[B]
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val tupleSize = getTupleTypeArgs(aTpe).size

    val args = targetInfo.fields.zipWithIndex.map { case (targetField, idx) =>
      val elem = tupleElement(aExpr.asTerm, aTpe, idx)
      // For small tuples, the accessor returns the correct type
      // For large tuples (productElement returns Any), cast to target type
      if (tupleSize <= 22) elem
      else
        TypeApply(
          Select(elem, TypeRepr.of[Any].typeSymbol.methodMember("asInstanceOf").head),
          List(Inferred(targetField.tpe))
        )
    }
    targetInfo.construct(args).asExprOf[B]
  }

  // === Tuple to Tuple ===

  private def deriveTupleToTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[Into[A, B]] = {
    val sourceTypeArgs = getTupleTypeArgs(aTpe)
    val targetTypeArgs = getTupleTypeArgs(bTpe)

    if (sourceTypeArgs.size != targetTypeArgs.size) {
      val sourceTypesStr = sourceTypeArgs.map(_.show).mkString(", ")
      val targetTypesStr = targetTypeArgs.map(_.show).mkString(", ")
      fail(
        s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Arity mismatch
           |
           |  Tuple${sourceTypeArgs.size}[$sourceTypesStr]  — ${sourceTypeArgs.size} elements
           |  Tuple${targetTypeArgs.size}[$targetTypesStr]  — ${targetTypeArgs.size} elements
           |
           |Source tuple has ${sourceTypeArgs.size} elements but target tuple has ${targetTypeArgs.size} elements.
           |
           |Consider:
           |  - Using tuples with the same number of elements
           |  - Providing an explicit Into instance""".stripMargin
      )
    }

    sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.foreach { case ((sourceTpe, targetTpe), idx) =>
      if (
        !(sourceTpe =:= targetTpe) && !requiresOpaqueConversion(sourceTpe, targetTpe) && !requiresNewtypeConversion(
          sourceTpe,
          targetTpe
        ) && findImplicitInto(sourceTpe, targetTpe).isEmpty
      ) {
        fail(
          s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Type mismatch at position $idx
             |
             |  Source element _${idx + 1}: ${sourceTpe.show}
             |  Target element _${idx + 1}: ${targetTpe.show}
             |
             |No implicit Into[${sourceTpe.show}, ${targetTpe.show}] found.
             |
             |Consider:
             |  - Providing an implicit Into[${sourceTpe.show}, ${targetTpe.show}]
             |  - Changing the types to be compatible
             |  - Using numeric coercion (Int → Long) if applicable""".stripMargin
        )
      }
    }

    // Check if any conversion needs an implicit Into instance (or opaque/newtype)
    val needsFailableConversions = sourceTypeArgs.zip(targetTypeArgs).exists { case (sourceTpe, targetTpe) =>
      !(sourceTpe =:= targetTpe) && (
        requiresOpaqueConversion(sourceTpe, targetTpe) ||
          requiresNewtypeConversion(sourceTpe, targetTpe) ||
          findImplicitInto(sourceTpe, targetTpe).isDefined
      )
    }

    if (needsFailableConversions) {
      // Use failable path that sequences Either results
      buildTupleToTupleWithConversions[A, B](aTpe, sourceTypeArgs, targetTypeArgs)
    } else {
      // All types are exactly the same - no conversion needed
      val buildTupleExpr = constructTupleFromTuple[A, B](aTpe, bTpe)
      '{
        new Into[A, B] {
          def into(a: A): Either[SchemaError, B] =
            Right(${ buildTupleExpr('a) })
        }
      }
    }
  }

  private def buildTupleToTupleWithConversions[A: Type, B: Type](
    aTpe: TypeRepr,
    sourceTypeArgs: List[TypeRepr],
    targetTypeArgs: List[TypeRepr]
  ): Expr[Into[A, B]] = {
    val tupleSize = sourceTypeArgs.size

    // Pre-compute all implicit lookups at compile time
    val conversionInfo: List[(TypeRepr, TypeRepr, Int, Option[Expr[Into[?, ?]]])] =
      sourceTypeArgs.zip(targetTypeArgs).zipWithIndex.map { case ((sourceTpe, targetTpe), idx) =>
        val implicitInto =
          if (sourceTpe =:= targetTpe) None
          else if (requiresOpaqueConversion(sourceTpe, targetTpe) || requiresNewtypeConversion(sourceTpe, targetTpe))
            None
          else findImplicitInto(sourceTpe, targetTpe)
        (sourceTpe, targetTpe, idx, implicitInto)
      }

    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] =
          ${
            val conversions: List[Expr[Either[SchemaError, Any]]] = conversionInfo.map {
              case (sourceTpe, targetTpe, idx, implicitIntoOpt) =>
                val elem = tupleElement('a.asTerm, aTpe, idx)

                implicitIntoOpt match {
                  case Some(intoInstance) =>
                    // Use the implicit Into instance
                    sourceTpe.asType match {
                      case '[st] =>
                        targetTpe.asType match {
                          case '[tt] =>
                            val typedInto = intoInstance.asExprOf[Into[st, tt]]
                            '{ $typedInto.into(${ elem.asExprOf[Any] }.asInstanceOf[st]).map(_.asInstanceOf[Any]) }
                        }
                    }
                  case None if sourceTpe =:= targetTpe =>
                    // Same type - no conversion needed
                    '{ Right(${ elem.asExprOf[Any] }) }
                  case None if requiresOpaqueConversion(sourceTpe, targetTpe) =>
                    // Opaque type conversion
                    convertToOpaqueTypeEither(elem, sourceTpe, targetTpe, s"_${idx + 1}")
                      .asExprOf[Either[SchemaError, Any]]
                  case None if requiresNewtypeConversion(sourceTpe, targetTpe) =>
                    // Newtype conversion
                    convertToNewtypeEither(elem, targetTpe, s"_${idx + 1}").asExprOf[Either[SchemaError, Any]]
                  case None =>
                    // Fallback: just cast (for reference types that are subtypes)
                    targetTpe.asType match {
                      case '[t] =>
                        '{ Right(${ elem.asExprOf[Any] }.asInstanceOf[t].asInstanceOf[Any]) }
                    }
                }
            }

            val sequenced = sequenceEithers(conversions)

            // Build the target tuple
            '{
              $sequenced match {
                case Right(values) =>
                  val arr = values.toArray
                  Right(${
                    if (tupleSize <= 22) {
                      // Small tuple: use TupleN.apply
                      val args = targetTypeArgs.zipWithIndex.map { case (tpe, idx) =>
                        tpe.asType match {
                          case '[t] => '{ arr(${ Expr(idx) }).asInstanceOf[t] }.asTerm
                        }
                      }
                      buildTuple[B](args, targetTypeArgs)
                    } else {
                      // Large tuple: use Tuples.fromArray
                      '{ scala.runtime.Tuples.fromArray(arr.asInstanceOf[Array[Object]]).asInstanceOf[B] }
                    }
                  })
                case Left(err) => Left(err)
              }
            }
          }
      }
    }
  }

  // NOTE: This method is only called when ALL source types exactly match target types
  // (no conversions needed). For any type conversions, use buildTupleToTupleWithConversions.
  private def constructTupleFromTuple[A: Type, B: Type](
    aTpe: TypeRepr,
    bTpe: TypeRepr
  ): Expr[A] => Expr[B] = (aExpr: Expr[A]) => {
    val targetTypeArgs = getTupleTypeArgs(bTpe)
    val tupleSize      = targetTypeArgs.size

    if (tupleSize <= 22) {
      // Small tuple: use direct accessors
      val args = targetTypeArgs.indices.map { idx =>
        tupleElement(aExpr.asTerm, aTpe, idx)
      }.toList
      buildTuple[B](args, targetTypeArgs)
    } else {
      // Large tuple: copy via toArray/fromArray (no coercion needed since types match)
      val tuplesModule    = Symbol.requiredModule("scala.runtime.Tuples")
      val toArrayMethod   = tuplesModule.methodMember("toArray").head
      val fromArrayMethod = tuplesModule.methodMember("fromArray").head

      val toArrayCall   = Apply(Select(Ref(tuplesModule), toArrayMethod), List(aExpr.asTerm))
      val fromArrayCall = Apply(Select(Ref(tuplesModule), fromArrayMethod), List(toArrayCall))

      TypeApply(
        Select(fromArrayCall, TypeRepr.of[Any].typeSymbol.methodMember("asInstanceOf").head),
        List(Inferred(bTpe))
      ).asExprOf[B]
    }
  }

  // === Opaque Type and Newtype Support ===

  /**
   * Checks if the target type is an opaque type
   */
  private def isOpaqueType(tpe: TypeRepr): Boolean =
    tpe.typeSymbol.flags.is(Flags.Opaque)

  /**
   * Gets the underlying type of an opaque type
   */
  private def getOpaqueUnderlying(tpe: TypeRepr): TypeRepr =
    opaqueDealias(tpe)

  /**
   * Tries to find a validation method (apply) that returns Either[_,
   * OpaqueType] Returns (companionObject, applyMethod, errorType) if found
   */
  private def findOpaqueValidationMethod(tpe: TypeRepr): Option[(Term, Symbol, TypeRepr)] =
    getOpaqueCompanion(tpe).flatMap { case (companionRef, companion) =>
      val applyMethods = companion.methodMembers.filter(_.name == "apply")

      applyMethods.find { method =>
        method.paramSymss match {
          case List(params) if params.size == 1 =>
            // Check if return type is Either[ErrorType, OpaqueType]
            method.tree match {
              case DefDef(_, _, returnTpt, _) =>
                val returnType = returnTpt.tpe
                returnType.dealias match {
                  case AppliedType(eitherTpe, List(_, resultTpe))
                      if eitherTpe.typeSymbol.fullName == "scala.util.Either" =>
                    // Check if result type matches our opaque type (use direct match for opaque types)
                    resultTpe =:= tpe
                  case _ => false
                }
              case _ => false
            }
          case _ => false
        }
      }.map { method =>
        // Extract error type from Either[ErrorType, OpaqueType]
        val errorTpe = method.tree match {
          case DefDef(_, _, returnTpt, _) =>
            returnTpt.tpe.dealias match {
              case AppliedType(_, List(errorTpe, _)) => errorTpe
              case _                                 => TypeRepr.of[String]
            }
          case _ => TypeRepr.of[String]
        }
        (companionRef, method, errorTpe)
      }
    }

  /**
   * Tries to find an unsafe constructor method for opaque types without
   * validation Returns (companionObject, unsafeMethod) if found
   */
  private def findOpaqueUnsafeMethod(tpe: TypeRepr): Option[(Term, Symbol)] =
    getOpaqueCompanion(tpe).flatMap { case (companionRef, companion) =>
      val allMethods    = (companion.declaredMethods ++ companion.methodMembers).distinct
      val unsafeMethods = allMethods.filter(m => m.name == "unsafe" || m.name == "unsafeWrap")

      unsafeMethods.find { method =>
        method.paramSymss match {
          case List(params) if params.size == 1 =>
            method.tree match {
              case DefDef(_, _, returnTpt, _) =>
                // For opaque types, use direct match (not dealiased)
                returnTpt.tpe =:= tpe
              case _ => false
            }
          case _ => false
        }
      }.map(method => (companionRef, method))
    }

  /**
   * Helper to find the companion object for an opaque type
   *
   * Note: Package-level opaque types are special. The type itself gets moved to
   * the package object (e.g., `pkg$package.MyType` or `File$package.MyType`),
   * but the companion object stays at the regular package level (e.g.,
   * `pkg.MyType`). We need to handle this case specially.
   */
  private def getOpaqueCompanion(tpe: TypeRepr): Option[(Term, Symbol)] = {
    val typeSym      = tpe.typeSymbol
    val companionSym = typeSym.companionModule

    val actualCompanion = if (companionSym == Symbol.noSymbol) {
      // Try to find companion object by constructing its expected module path
      val companionName = typeSym.name
      val owner         = typeSym.owner

      // The owner.fullName for objects ends with $, strip it for path construction
      val ownerPath = owner.fullName

      // Check if this is a package-level type (owner is a package object)
      // Package objects have names ending in "$package" or "$package$"
      val isPackageLevelType = ownerPath.contains("$package")

      val cleanOwnerPath = if (isPackageLevelType) {
        // For package-level types, the companion object is NOT in the package object
        // but at the regular package level.
        // Example: zio.blocks.schema.as.validation.OpaqueTypeRoundTripSpec$package
        // Should become: zio.blocks.schema.as.validation
        // We need to find the last dot before "$package" or the segment containing "$package"
        val idx = ownerPath.indexOf("$package")
        if (idx > 0) {
          // Find the last dot before $package
          val lastDotBeforePackage = ownerPath.lastIndexOf('.', idx)
          if (lastDotBeforePackage > 0) {
            ownerPath.substring(0, lastDotBeforePackage)
          } else {
            "" // Top-level package
          }
        } else {
          ownerPath.stripSuffix("$").stripSuffix("$package")
        }
      } else if (ownerPath.endsWith("$")) {
        ownerPath.stripSuffix("$")
      } else {
        ownerPath
      }

      val companionPath = if (cleanOwnerPath.isEmpty) companionName else s"$cleanOwnerPath.$companionName"

      try {
        val loaded = Symbol.requiredModule(companionPath)
        Some(loaded)
      } catch {
        case _: Exception =>
          // Last resort: look in owner declarations
          // For package objects, we need to look in the actual package (owner.owner)
          val searchScope = if (isPackageLevelType) owner.owner else owner
          searchScope.declarations.find { s =>
            s.name == companionName && s.flags.is(Flags.Module)
          }
      }
    } else {
      Some(companionSym)
    }

    actualCompanion.map(companion => (Ref(companion), companion))
  }

  /**
   * Converts a value to an opaque type, applying validation if available
   * Returns an Expr[Either[SchemaError, OpaqueType]]
   */
  private def convertToOpaqueTypeEither(
    sourceValue: Term,
    sourceTpe: TypeRepr,
    targetTpe: TypeRepr,
    fieldName: String
  ): Expr[Either[SchemaError, Any]] = {
    val underlying = getOpaqueUnderlying(targetTpe)

    // Check if source type matches the underlying type
    if (!(sourceTpe =:= underlying)) {
      report.errorAndAbort(
        s"Cannot convert field '$fieldName' from ${sourceTpe.show} to opaque type ${targetTpe.show} " +
          s"(underlying type: ${underlying.show}). Types must match."
      )
    }

    // Priority 1: Try validation method (apply) first
    findOpaqueValidationMethod(targetTpe) match {
      case Some((companionRef, applyMethod, errorTpe)) =>
        // Generate validation call that returns Either
        val applyCall = Select(companionRef, applyMethod).appliedTo(sourceValue)

        // Map the error type to SchemaError
        errorTpe.asType match {
          case '[et] =>
            targetTpe.asType match {
              case '[tt] =>
                '{
                  ${ applyCall.asExprOf[Either[et, tt]] }.left.map { err =>
                    SchemaError.conversionFailed(Nil, s"Validation failed for field '${${ Expr(fieldName) }}': $err")
                  }.asInstanceOf[Either[SchemaError, Any]]
                }
            }
        }

      case None =>
        // Priority 2: Try unsafe constructor as fallback
        findOpaqueUnsafeMethod(targetTpe) match {
          case Some((companionRef, unsafeMethod)) =>
            // Use unsafe constructor (no validation) - wrap in Right
            targetTpe.asType match {
              case '[tt] =>
                val unsafeCall = Select(companionRef, unsafeMethod).appliedTo(sourceValue)
                '{
                  Right(${ unsafeCall.asExprOf[tt] }).asInstanceOf[Either[SchemaError, Any]]
                }
            }

          case None =>
            // No validation or unsafe method found - report error
            report.errorAndAbort(opaqueTypeNoCompanionError(targetTpe.show))
        }
    }
  }

  /**
   * Checks if conversion requires opaque type handling
   */
  private def requiresOpaqueConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean =
    isOpaqueType(targetTpe) && {
      val underlying = getOpaqueUnderlying(targetTpe)
      sourceTpe =:= underlying
    }

  // === ZIO Prelude Newtype Support (Scala 3) ===

  /**
   * Checks if a type is a ZIO Prelude Newtype or Subtype Works without
   * requiring zio-prelude as a dependency Checks both the type and its
   * companion object for newtype markers
   */
  private def isZIONewtype(tpe: TypeRepr): Boolean = {
    val typeSym        = tpe.typeSymbol
    val typeSymbolName = typeSym.name
    val owner          = typeSym.owner

    // For ZIO Prelude newtypes: type Age = Age.Type where Age extends Subtype[Int]
    // The pattern is:
    // - typeSym.name is "Type"
    // - owner.fullName contains "zio.prelude.Subtype" or "zio.prelude.Newtype"
    //
    // We check the owner's fullName string to avoid loading ZIO Prelude's TASTy files
    // which can cause "Bad symbolic reference" errors with TASTy version mismatches

    if (typeSymbolName == "Type") {
      val ownerFullName       = owner.fullName
      val isZIOPreludeNewtype = ownerFullName.contains("zio.prelude.Subtype") ||
        ownerFullName.contains("zio.prelude.Newtype")

      if (isZIOPreludeNewtype) {
        return true
      }
    } else {}

    false
  }

  /**
   * Gets the underlying type of a ZIO Prelude newtype For Newtype[A] or
   * Subtype[A], returns A
   *
   * For ZIO Prelude, the pattern is: object Age extends Subtype[Int] type Age =
   * Age.Type
   *
   * When we receive Age.Type (which is an opaque type alias to Int), we need to
   * find the Age object (the actual owner in the enclosing scope, not the
   * Subtype parent) and check what type parameter it has.
   */
  private def getNewtypeUnderlying(tpe: TypeRepr): TypeRepr = {

    val typeSym = tpe.typeSymbol

    // For ZIO Prelude newtypes:
    // - Subtype[Int]: Type extends Int directly, so Int is in base types
    // - Newtype[String]: Type wraps String
    //
    // IMPORTANT: We cannot access baseClasses as it loads TASTy files and causes
    // "Bad symbolic reference" errors with version mismatches.
    //
    // Instead, we'll look at the type's direct structure. For ZIO Prelude:
    // - PositiveInt.Type where PositiveInt extends Subtype[Int]
    //   The Type itself has the shape that reflects Int (AnyVal hierarchy)
    // - Email.Type where Email extends Newtype[String]
    //   The Type wraps String

    if (typeSym.name == "Type") {

      // Try to infer the underlying type by inspecting tpe structure
      // For now, we'll use a heuristic: look at the type's widen representation
      val widened = tpe.widen

      // Check if the widened type gives us something useful
      if (widened != tpe && !widened.typeSymbol.fullName.contains("zio.prelude")) {
        return widened
      }

      // Fallback: cannot extract underlying type without loading TASTy
      // Return the original type - field matching will fail and we'll rely on
      // implicit Into instances instead
      tpe
    } else {
      tpe
    }
  }

  /**
   * Converts a value to a ZIO Prelude newtype, applying validation via the
   * companion's `make` method. Returns an Expr[Either[SchemaError,
   * NewtypeType]]
   *
   * This uses compile-time code generation (quotes/splices) to generate direct
   * calls to the companion object's `make` method, avoiding runtime reflection
   * entirely. This allows the code to work on all platforms (JVM, JS, Native).
   *
   * For ZIO Prelude newtypes:
   *   - `object Age extends Subtype[Int]` has
   *     `make(value: Int): Validation[String, Age]`
   *   - We generate:
   *     `Age.make(value).toEither.left.map(err => SchemaError.conversionFailed(...))`
   */
  private def convertToNewtypeEither(
    sourceValue: Term,
    targetTpe: TypeRepr,
    fieldName: String
  ): Expr[Either[SchemaError, Any]] = {
    // First, try to find an implicit Into instance for this conversion
    val implicitInto = findImplicitInto(sourceValue.tpe, targetTpe)

    targetTpe.asType match {
      case '[tt] =>
        sourceValue.asExpr match {
          case '{ $src: s } =>
            implicitInto match {
              case Some(intoExpr) =>
                // Use the implicit Into instance (which may include validation)
                val typedInto = intoExpr.asExprOf[Into[s, tt]]
                '{ $typedInto.into($src).asInstanceOf[Either[SchemaError, Any]] }
              case None =>
                // No implicit Into found - generate direct call to companion.make(value)
                // For ZIO Prelude newtypes like Age.Type where `object Age extends Subtype[Int]`:
                // - targetTpe is something like `Age.Type`
                // - The TypeRef has a prefix that points to the Age object
                // - We need to extract that prefix to get the companion object

                val companionOpt: Option[Symbol] = targetTpe match {
                  case TypeRef(prefix, _) =>
                    // The prefix is the path to the companion object (e.g., Age)
                    prefix match {
                      case TermRef(_, _) =>
                        // Try to find the module symbol
                        try {
                          val prefixSym = prefix.termSymbol
                          if (prefixSym.flags.is(Flags.Module)) Some(prefixSym)
                          else None
                        } catch {
                          case _: Exception => None
                        }
                      case ThisType(_) =>
                        // For types defined in the same scope
                        val typeSym = targetTpe.typeSymbol
                        val owner   = typeSym.owner
                        if (owner.flags.is(Flags.Module)) Some(owner)
                        else None
                      case _ =>
                        None
                    }
                  case _ =>
                    None
                }

                companionOpt match {
                  case Some(companionSym) if companionSym.flags.is(Flags.Module) =>
                    // Find the `make` method on the companion
                    val makeMethods = companionSym.methodMembers.filter(_.name == "make")

                    makeMethods.find { m =>
                      m.paramSymss match {
                        case List(List(_)) => true // Single parameter
                        case _             => false
                      }
                    } match {
                      case Some(makeMethod) =>
                        // Generate: companion.make(value).toEither.left.map(...)
                        val companionRef  = Ref(companionSym)
                        val makeCall      = Apply(Select(companionRef, makeMethod), List(sourceValue))
                        val fieldNameExpr = Expr(fieldName)

                        // The result is Validation[String, tt], we need to convert to Either[SchemaError, tt]
                        // Find the toEither method on the Validation result
                        val validationTpe  = makeCall.tpe
                        val toEitherMethod = validationTpe.typeSymbol.methodMembers.find(_.name == "toEither")

                        toEitherMethod match {
                          case Some(toEitherSym) =>
                            // Generate: makeCall.toEither.left.map(err => SchemaError.conversionFailed(...))
                            val toEitherCall = Select(makeCall, toEitherSym)

                            toEitherCall.tpe.asType match {
                              case '[Either[err, result]] =>
                                val toEitherExpr = toEitherCall.asExprOf[Either[err, result]]
                                '{
                                  $toEitherExpr.left.map { err =>
                                    SchemaError.conversionFailed(
                                      Nil,
                                      s"Validation failed for field '${$fieldNameExpr}': $err"
                                    )
                                  }.asInstanceOf[Either[SchemaError, Any]]
                                }
                              case _ =>
                                // Fallback if we can't match the Either type
                                '{ Right($src.asInstanceOf[tt].asInstanceOf[Any]) }
                            }

                          case None =>
                            // No toEither method - fall back to asInstanceOf
                            '{ Right($src.asInstanceOf[tt].asInstanceOf[Any]) }
                        }

                      case None =>
                        // No make method found - fall back to asInstanceOf (unsafe, no validation)
                        '{ Right($src.asInstanceOf[tt].asInstanceOf[Any]) }
                    }

                  case _ =>
                    // Companion not found - fall back to asInstanceOf
                    '{ Right($src.asInstanceOf[tt].asInstanceOf[Any]) }
                }
            }
        }
    }
  }

  /**
   * Checks if conversion requires ZIO Prelude newtype handling
   */
  private def requiresNewtypeConversion(sourceTpe: TypeRepr, targetTpe: TypeRepr): Boolean = {

    // Check if the target is a ZIO Prelude newtype
    val isNewtype = isZIONewtype(targetTpe)

    if (!isNewtype) {
      return false
    }

    // Target is a newtype. We accept any source type and will handle conversion
    // via the newtype's make/wrap method. The actual validation happens at runtime.
    // For now, we just check that source and target are not identical.
    val result = sourceTpe != targetTpe
    result
  }

  // === Error Message Formatting ===

  private def formatCoproductCases(tpe: TypeRepr): String = {
    val subtypes = directSubTypes(tpe)
    subtypes.map(_.typeSymbol.name).mkString(", ")
  }

  private def noMatchingFieldError(
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    sourceInfo: ProductInfo[?],
    targetInfo: ProductInfo[?],
    targetField: FieldInfo
  ): String = {
    val sourceFieldsStr = sourceInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")
    val targetFieldsStr = targetInfo.fields.map(f => s"${f.name}: ${f.tpe.show}").mkString(", ")

    s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Missing required field
       |
       |  ${aTpe.typeSymbol.name}($sourceFieldsStr)
       |  ${bTpe.typeSymbol.name}($targetFieldsStr)
       |
       |No source field found for target field '${targetField.name}: ${targetField.tpe.show}'.
       |
       |Fields are matched by:
       |  1. Exact name + type match
       |  2. Name match + coercible type (e.g., Int → Long)
       |  3. Unique type (when only one field of that type exists)
       |  4. Position + unique type (tuple-like matching)
       |
       |Consider:
       |  - Adding field '${targetField.name}: ${targetField.tpe.show}' to ${aTpe.typeSymbol.name}
       |  - Making '${targetField.name}' an Option[${targetField.tpe.show}] (defaults to None)
       |  - Adding a default value for '${targetField.name}' in ${bTpe.typeSymbol.name}
       |  - Providing an explicit Into[${aTpe.show}, ${bTpe.show}] instance""".stripMargin
  }

  private def noMatchingCaseError(
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    sourceCase: TypeRepr
  ): String = {
    val sourceCases    = formatCoproductCases(aTpe)
    val targetCases    = formatCoproductCases(bTpe)
    val sourceCaseName = sourceCase.typeSymbol.name

    s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: No matching case
       |
       |  ${aTpe.typeSymbol.name}: $sourceCases
       |  ${bTpe.typeSymbol.name}: $targetCases
       |
       |Source case '$sourceCaseName' has no matching target case.
       |
       |Cases are matched by:
       |  1. Name (case object or case class name)
       |  2. Field signature (same field types in same order)
       |
       |Consider:
       |  - Adding case '$sourceCaseName' to ${bTpe.typeSymbol.name}
       |  - Renaming a target case to '$sourceCaseName'
       |  - Ensuring field signatures match for signature-based matching
       |  - Providing an explicit Into[${aTpe.show}, ${bTpe.show}] instance""".stripMargin
  }

  private def noImplicitIntoError(
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    sourceFieldType: TypeRepr,
    targetFieldType: TypeRepr,
    fieldName: String
  ): String =
    s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: No implicit conversion for field
       |
       |  Field: $fieldName
       |  Source type: ${sourceFieldType.show}
       |  Target type: ${targetFieldType.show}
       |
       |No implicit Into[${sourceFieldType.show}, ${targetFieldType.show}] was found in scope.
       |
       |Consider:
       |  - Providing an implicit: implicit val ${fieldName}Into: Into[${sourceFieldType.show}, ${targetFieldType.show}] = Into.derived
       |  - Using Into.derived[${sourceFieldType.show}, ${targetFieldType.show}] inline
       |  - Changing the field types to be directly compatible
       |  - Using numeric widening (Int → Long) or narrowing (Long → Int) if applicable""".stripMargin

  private def noPrimitiveConversionError(aTpe: TypeRepr, bTpe: TypeRepr): String =
    s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: No primitive conversion
       |
       |No predefined conversion exists between '${aTpe.show}' and '${bTpe.show}'.
       |
       |Supported numeric conversions:
       |  - Widening: Byte → Short → Int → Long, Float → Double
       |  - Narrowing: Long → Int → Short → Byte (with runtime validation)
       |
       |Consider:
       |  - Using a supported numeric conversion path
       |  - Providing a custom implicit Into[${aTpe.show}, ${bTpe.show}]""".stripMargin

  private def unsupportedTypeCombinationError(
    aTpe: TypeRepr,
    bTpe: TypeRepr,
    sourceKind: String,
    targetKind: String
  ): String =
    s"""Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Unsupported type combination
       |
       |Source type: ${aTpe.show} ($sourceKind)
       |Target type: ${bTpe.show} ($targetKind)
       |
       |Into derivation supports:
       |  - Product → Product (case class to case class)
       |  - Product ↔ Tuple (case class to/from tuple)
       |  - Tuple → Tuple
       |  - Coproduct → Coproduct (sealed trait to sealed trait)
       |  - Structural ↔ Product
       |  - Primitive → Primitive (with coercion)
       |
       |Consider:
       |  - Restructuring your types to fit a supported pattern
       |  - Providing an explicit Into instance""".stripMargin

  private def opaqueTypeNoCompanionError(typeName: String): String =
    s"""Cannot convert to opaque type $typeName: no 'apply' or 'unsafe' method found in companion object
       |
       |Opaque types require a companion object with either:
       |  - An 'apply' method: def apply(value: Underlying): $typeName
       |  - An 'unsafe' method: def unsafe(value: Underlying): $typeName
       |
       |Consider:
       |  - Adding an 'apply' or 'unsafe' method to the companion object
       |  - Using a different type that doesn't require validation""".stripMargin

}
