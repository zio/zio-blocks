package zio.blocks.schema

import scala.quoted._
import scala.reflect.ClassTag
import zio.blocks.schema.CommonMacroOps

/**
 * Version-specific trait for Scala 3 macro implementations of `Into` and `As`
 * type classes.
 *
 * This trait provides the macro entry points for automatic derivation of
 * `Into[A, B]` and `As[A, B]` instances using Scala 3 Quotes & Splices.
 */
trait IntoAsVersionSpecific {
  inline def derived[A, B]: Into[A, B] = derivedInto[A, B]
  inline def derivedInto[A, B]: Into[A, B] = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
  inline def derivedAs[A, B]: As[A, B]     = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
}

private object IntoAsVersionSpecificImpl {
  import CommonMacroOps.fail

  /**
   * Runtime helper function to sequence a list of Either results. Returns the
   * first error if any, otherwise Right with the list of values.
   */
  def sequenceEither(
    results: List[Either[SchemaError, Any]]
  ): Either[SchemaError, List[Any]] = {
    val builder                    = List.newBuilder[Any]
    var error: Option[SchemaError] = None
    val it                         = results.iterator

    while (it.hasNext && error.isEmpty) {
      it.next() match {
        case Right(v) => builder += v
        case Left(e)  => error = Some(e)
      }
    }

    error match {
      case Some(e) => Left(e)
      case None    => Right(builder.result())
    }
  }

  /**
   * Runtime helper to sequence Either results from a collection. Returns first
   * error if any, otherwise Right with the list of values.
   *
   * This avoids generating complex loops in AST and works cross-platform. Uses
   * IterableOnce to support both Iterable collections and Iterator (from
   * Array).
   */
  def sequenceCollectionEither[A](
    results: IterableOnce[Either[SchemaError, A]]
  ): Either[SchemaError, List[A]] = {
    val builder                    = List.newBuilder[A]
    var error: Option[SchemaError] = None
    val it                         = results.iterator

    while (it.hasNext && error.isEmpty) {
      it.next() match {
        case Right(v) => builder += v
        case Left(e)  => error = Some(e)
      }
    }

    error match {
      case Some(e) => Left(e)
      case None    => Right(builder.result())
    }
  }

  /**
   * Runtime helper to map over a collection and sequence Either results.
   * Combines map and sequence operations to avoid fragile AST construction.
   *
   * This eliminates the need to construct `.map` via AST when the source is a
   * complex term (e.g., ArraySeq.unsafeWrapArray(...)).
   */
  def mapAndSequence[A, B](
    source: Iterable[A],
    f: A => Either[SchemaError, B]
  ): Either[SchemaError, List[B]] = {
    val builder                    = List.newBuilder[B]
    var error: Option[SchemaError] = None
    val it                         = source.iterator

    while (it.hasNext && error.isEmpty) {
      f(it.next()) match {
        case Right(b) => builder += b
        case Left(e)  => error = Some(e)
      }
    }

    error match {
      case Some(e) => Left(e)
      case None    => Right(builder.result())
    }
  }

  /**
   * Macro implementation for deriving `Into[A, B]` instances.
   *
   * This macro will:
   *   1. Analyze types A and B at compile-time
   *   2. Build field mapping using disambiguation algorithm (name, position,
   *      type)
   *   3. Detect opaque types and generate validation calls
   *   4. Generate narrowing validation for numeric coercions
   *   5. Handle collection type conversions
   *   6. Generate conversion code recursively
   */
  def derivedIntoImpl[A: Type, B: Type](using q: Quotes): Expr[Into[A, B]] = {
    import q.reflect._

    val aTpe = TypeRepr.of[A].dealias
    val bTpe = TypeRepr.of[B].dealias

    // Identity case: if types are identical, use Into.identity
    if (aTpe =:= bTpe) {
      aTpe.asType match {
        case '[a] =>
          '{ Into.identity[a].asInstanceOf[Into[A, B]] }
      }
    } else {
      // PRIORITY 1: Collection case (check before Coproduct and Product)
      (extractCollectionElementType(using q)(aTpe), extractCollectionElementType(using q)(bTpe)) match {
        case (Some(aElem), Some(bElem)) =>
          // Both are collections - derive element-wise conversion
          deriveCollectionInto[A, B](using q)(aTpe, bTpe, aElem, bElem)

        case _ =>
          // PRIORITY 2: Coproduct case (Sealed Traits / Enums)
          if (isSealedTraitOrEnum(using q)(aTpe) && isSealedTraitOrEnum(using q)(bTpe)) {
            deriveCoproductInto[A, B](using q)(aTpe, bTpe)
          }
          // PRIORITY 3: Product case (existing logic)
          else if (isCaseClass(using q)(aTpe) && isCaseClass(using q)(bTpe)) {
            // Extract fields from both types
            val aFields = extractCaseClassFields(using q)(aTpe)
            val bFields = extractCaseClassFields(using q)(bTpe)

            // Generate conversion code
            generateIntoInstance[A, B](using q)(aTpe, bTpe, aFields, bFields)
          } else {
            fail(
              s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
                s"Source type '${aTpe.show}' and target type '${bTpe.show}' must both be " +
                s"collections, sealed traits/enums, or case classes."
            )
          }
      }
    }
  }

  /**
   * Checks if a type is a collection (Iterable, Iterator, Array, IArray).
   * Collections take precedence over Product types.
   */
  private def isCollection(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    val wildcard           = TypeBounds.empty
    val iterableOfWildcard = Symbol.requiredClass("scala.collection.Iterable").typeRef.appliedTo(wildcard)
    val iteratorOfWildcard = Symbol.requiredClass("scala.collection.Iterator").typeRef.appliedTo(wildcard)
    val arrayOfWildcard    = TypeRepr.of[Array[?]]
    val iArrayFullName     = "scala.IArray$package$.IArray"

    tpe <:< iterableOfWildcard ||
    tpe <:< iteratorOfWildcard ||
    tpe <:< arrayOfWildcard ||
    tpe.typeSymbol.fullName == iArrayFullName
  }

  /**
   * Checks if a type is specifically an Array (not IArray).
   */
  private def isArray(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._
    tpe <:< TypeRepr.of[Array[?]] && tpe.typeSymbol.fullName == "scala.Array"
  }

  /**
   * Extracts element type from a collection type. Returns None if not a
   * collection or if element type cannot be extracted.
   */
  private def extractCollectionElementType(using q: Quotes)(tpe: q.reflect.TypeRepr): Option[q.reflect.TypeRepr] =

    if (isCollection(using q)(tpe)) {
      CommonMacroOps.typeArgs(using q)(tpe).headOption
    } else {
      None
    }

  /**
   * Checks if a type is a case class (non-abstract Scala class).
   */
  private def isCaseClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._
    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
    }
  }

  /**
   * Checks if a type is a sealed trait, abstract class, or enum. Used to
   * identify coproduct types (sum types).
   */
  private def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._
    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (
        flags.is(Flags.Abstract) ||
          flags.is(Flags.Trait) ||
          flags.is(Flags.Enum)
      )
    }
  }

  /**
   * Checks if a type is a singleton case (case object or enum value). These are
   * subtypes that don't have parameters and are represented as modules.
   */
  private def isCaseObjectOrEnumVal(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._
    // Check if it's an enum value (termSymbol with Enum flag)
    val termSym = tpe.termSymbol
    (termSym.exists && termSym.flags.is(Flags.Enum)) ||
    // Check if it's a module (case object)
    tpe.typeSymbol.flags.is(Flags.Module)
  }

  /**
   * Finds a matching subtype in the target type's subtypes.
   *
   * Matching strategy:
   *   1. Exact name match (current implementation)
   *      - Subtypes must have identical names (e.g., `Red → Red`,
   *        `Created → Created`)
   *      - Works for both enum values/case objects and case classes
   *      - Limitation: Different names (e.g., `RedColor → RedHue`) will not
   *        match
   *   2. TODO: Structural match (for case classes with same field structure)
   *      - Future enhancement: match by field structure when names differ
   *
   * @param aSubtype
   *   The source subtype to match
   * @param bSubtypes
   *   The list of target subtypes to search
   * @return
   *   Some(bSubtype) if a match is found, None otherwise
   *
   * @note
   *   For coproduct conversions, ensure subtype names match exactly. If
   *   subtypes have different names, rename them or use wrapper types.
   */
  private def findMatchingSubtype(using
    q: Quotes
  )(
    aSubtype: q.reflect.TypeRepr,
    bSubtypes: List[q.reflect.TypeRepr]
  ): Option[q.reflect.TypeRepr] = {

    // Get the name of aSubtype (handles both enum values and case classes)
    val aName = {
      val termSym = aSubtype.termSymbol
      if (termSym.exists) {
        // Enum value or case object: use termSymbol name
        termSym.name
      } else {
        // Case class: use typeSymbol name
        aSubtype.typeSymbol.name
      }
    }

    // PRIORITY 1: Exact name match
    val nameMatch = bSubtypes.find { bSubtype =>
      val bName = {
        val termSym = bSubtype.termSymbol
        if (termSym.exists) {
          termSym.name
        } else {
          bSubtype.typeSymbol.name
        }
      }
      aName == bName
    }

    if (nameMatch.isDefined) {
      return nameMatch
    }

    // PRIORITY 2: Structural match (TODO - to be implemented)
    // This would check if both are case classes with compatible field structures
    // For now, return None if name match fails

    None
  }

  /**
   * Derives Into[A, B] for coproduct types (sealed traits, enums). Generates a
   * Match expression that dispatches to subtype-specific conversions.
   */
  private def deriveCoproductInto[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect._

    // 1. Extract subtypes
    val aSubtypes = CommonMacroOps.directSubTypes(using q)(aTpe)
    val bSubtypes = CommonMacroOps.directSubTypes(using q)(bTpe)

    if (aSubtypes.isEmpty) {
      fail(
        s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
          s"Source type '${aTpe.show}' has no subtypes."
      )
    }
    if (bSubtypes.isEmpty) {
      fail(
        s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
          s"Target type '${bTpe.show}' has no subtypes."
      )
    }

    // 2. Find matching for each subtype of A
    // Allow some subtypes to not have matches (they will hit the catch-all case at runtime)
    val matches = aSubtypes.flatMap { aSubtype =>
      findMatchingSubtype(using q)(aSubtype, bSubtypes) match {
        case Some(bSubtype) =>
          // Derive recursive conversion
          val subInto = findOrDeriveInto(using q)(aSubtype, bSubtype)
          Some((aSubtype, bSubtype, subInto))
        case None =>
          // No match found - this subtype will hit the catch-all case at runtime
          // Don't fail at compile-time, allow it to be handled by the wildcard case
          None
      }
    }

    // Ensure at least one match was found (otherwise derivation doesn't make sense)
    if (matches.isEmpty) {
      fail(
        s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
          s"No matching subtypes found between '${aTpe.show}' and '${bTpe.show}'."
      )
    }

    // 3. Build conversion function: (a: A) => Either[SchemaError, B]
    val methodType = MethodType(List("a"))(
      _ => List(aTpe),
      _ => TypeRepr.of[Either[SchemaError, B]]
    )

    val lambda = Lambda(
      Symbol.spliceOwner,
      methodType,
      (owner, params) => {
        val aParam = params.head.asInstanceOf[Term]

        // 4. Generate Match expression with cases
        val cases = matches.map { case (aSubtype, _, subIntoExpr) =>
          // Create a bind symbol for the captured value
          // Use getSubtypeName to handle both enum values (termSymbol) and case classes (typeSymbol)
          val bindName   = s"val${getSubtypeName(using q)(aSubtype)}"
          val bindSymbol = Symbol.newBind(
            owner,
            bindName,
            Flags.EmptyFlags,
            aSubtype
          )

          // Pattern: Bind(bindSymbol, Typed(Wildcard(), Inferred(aSubtype)))
          val pattern = Bind(
            bindSymbol,
            Typed(Wildcard(), Inferred(aSubtype))
          )

          // Body: subInto.into(Ref(bindSymbol))
          val subIntoTerm = subIntoExpr.asTerm
          val intoMethod  = Select.unique(subIntoTerm, "into")
          val bindRef     = Ref(bindSymbol)
          val intoCall    = Apply(intoMethod, List(bindRef))

          // The result is Either[SchemaError, bSubtype], which is compatible with Either[SchemaError, B]
          // due to covariance. We may need to cast if the compiler complains.
          val body = intoCall.asExprOf[Either[SchemaError, B]].asTerm

          CaseDef(pattern, None, body.changeOwner(owner))
        }

        // 5. Add catch-all case for error handling
        val errorMessage = Expr(s"Unexpected subtype of ${aTpe.show}")
        val errorCase    = CaseDef(
          Wildcard(),
          None,
          '{
            Left(
              SchemaError.expectationMismatch(
                Nil,
                $errorMessage
              )
            )
          }.asTerm.changeOwner(owner)
        )

        Match(aParam, cases :+ errorCase).changeOwner(owner)
      }
    )

    // 6. Wrap conversion function in Into instance
    val conversionFunction = lambda.asExprOf[A => Either[SchemaError, B]]
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $conversionFunction(a)
      }
    }
  }

  /**
   * Builds a Term representing the construction of a subtype value.
   *
   * For singleton cases (case objects, enum values): returns a reference to the
   * module. For class cases (case classes): TODO - will be implemented with
   * recursive field conversion.
   *
   * @param bSubtype
   *   The target subtype to construct
   * @return
   *   A Term representing the subtype value
   */
  private def buildSubtypeConstruction(using
    q: Quotes
  )(
    bSubtype: q.reflect.TypeRepr
  ): q.reflect.Term = {
    import q.reflect._

    if (isCaseObjectOrEnumVal(using q)(bSubtype)) {
      // Singleton case: return reference to module
      val termSym = bSubtype.termSymbol
      if (termSym.exists && termSym.flags.is(Flags.Enum)) {
        // Enum value: use termSymbol directly
        Ref(termSym)
      } else {
        // Case object: typeSymbol is a module class, need to get the companion module
        val typeSym = bSubtype.typeSymbol
        if (typeSym.flags.is(Flags.Module)) {
          // For case objects, typeSymbol is the module class, companionModule is the term symbol
          val moduleTerm = typeSym.companionModule
          if (moduleTerm.exists) {
            Ref(moduleTerm)
          } else {
            // Fallback: try using typeSymbol directly (may work in some cases)
            Ref(typeSym)
          }
        } else {
          fail(
            s"Cannot build construction for subtype ${bSubtype.show}: " +
              s"Expected module class for case object, but got ${typeSym.flags.show}."
          )
        }
      }
    } else {
      // Class case: TODO - will be implemented with recursive field conversion
      // This will construct the case class using field conversions
      fail(
        s"Cannot build construction for class subtype ${bSubtype.show}: " +
          s"Class case construction not yet implemented. " +
          s"Only singleton cases (case objects, enum values) are supported."
      )
    }
  }

  /**
   * Case class field information. TypeRepr and Symbol are path-dependent types,
   * stored as Any and cast when used.
   */
  private class FieldInfo(val name: String, val tpe: Any, val getter: Any) {
    def tpeRepr(using q: Quotes): q.reflect.TypeRepr    = tpe.asInstanceOf[q.reflect.TypeRepr]
    def getterSymbol(using q: Quotes): q.reflect.Symbol = getter.asInstanceOf[q.reflect.Symbol]
  }

  /**
   * Extracts field information from a case class type.
   */
  private def extractCaseClassFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[FieldInfo] = {
    import q.reflect._

    val primaryCtor = tpe.classSymbol.fold(Symbol.noSymbol)(_.primaryConstructor)
    if (primaryCtor.exists) {
      val (tpeTypeParams, tpeParams) = primaryCtor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val tpeTypeArgs = CommonMacroOps.typeArgs(using q)(tpe)
      val caseFields  = tpe.classSymbol.get.caseFields

      tpeParams.flatten.map { param =>
        var fieldTpe = tpe.memberType(param).dealias
        if (tpeTypeArgs.nonEmpty && tpeTypeParams.nonEmpty) {
          fieldTpe = fieldTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
        }

        val fieldName = param.name
        val getter    = caseFields
          .find(_.name == fieldName)
          .getOrElse(Symbol.noSymbol)

        if (!getter.exists) {
          fail(s"Field '$fieldName' of '${tpe.show}' should be accessible.")
        }

        new FieldInfo(fieldName, fieldTpe, getter)
      }
    } else {
      fail(s"Cannot extract fields from '${tpe.show}': No primary constructor found.")
    }
  }

  /**
   * Generates the Into[A, B] instance expression for case classes.
   */
  private def generateIntoInstance[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    aFields: List[FieldInfo],
    bFields: List[FieldInfo]
  ): Expr[Into[A, B]] = {

    // Build field conversions: for each field in B, find matching field in A and generate conversion
    val fieldConversions = bFields.map { bField =>
      // Find matching field in A by name
      aFields.find(_.name == bField.name) match {
        case Some(aField) =>
          // Generate recursive Into[FieldA, FieldB] instance
          val fieldInto = findOrDeriveInto(using q)(aField.tpeRepr(using q), bField.tpeRepr(using q))
          (bField.name, aField.getterSymbol(using q), fieldInto)
        case None =>
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: Field '${bField.name}' not found in source type '${aTpe.show}'."
          )
      }
    }

    // Generate the conversion function
    val conversionFunction = generateConversionBody[A, B](using q)(aTpe, bTpe, fieldConversions)

    // Build the Into instance
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $conversionFunction(a)
      }
    }
  }

  /**
   * Helper to check if a TypeRepr matches a specific primitive type. Handles
   * type aliases via dealias for robustness.
   */
  private def isPrimitiveType(using
    q: Quotes
  )(
    tpe: q.reflect.TypeRepr,
    target: q.reflect.TypeRepr
  ): Boolean =
    tpe.dealias =:= target.dealias

  /**
   * Generates an Into instance from a conversion function. Safe for macro
   * context (owner chain handled by Quotes).
   */
  private def generatePrimitiveInto[A: Type, B: Type](
    conversion: Expr[A => Either[SchemaError, B]]
  )(using q: Quotes): Expr[Into[A, B]] =
    '{
      new Into[A, B] {
        def into(a: A): Either[SchemaError, B] = $conversion(a)
      }
    }

  /**
   * Extracts the name of a subtype (handles both enum values/case objects and
   * case classes). For enum values and case objects, uses termSymbol.name. For
   * case classes, uses typeSymbol.name.
   */
  private def getSubtypeName(using q: Quotes)(tpe: q.reflect.TypeRepr): String = {
    val termSym = tpe.termSymbol
    if (termSym.exists) {
      termSym.name // Enum value or case object
    } else {
      tpe.typeSymbol.name // Case class
    }
  }

  /**
   * Generates an Into instance for singleton case conversion (enum values, case
   * objects). Creates a constant conversion function that always returns the
   * target singleton value.
   */
  private def generateSingletonInto[A: Type, B: Type](using
    q: Quotes
  )(
    bTpe: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    val bTerm      = buildSubtypeConstruction(using q)(bTpe)
    val conversion = '{ (_: A) => Right(${ bTerm.asExprOf[B] }) }
    generatePrimitiveInto[A, B](conversion)
  }

  /**
   * Finds an existing Into instance or derives one recursively.
   */
  private def findOrDeriveInto(using
    q: Quotes
  )(aTpe: q.reflect.TypeRepr, bTpe: q.reflect.TypeRepr): Expr[Into[?, ?]] = {
    import q.reflect._

    // Identity case: if types are identical, use Into.identity
    if (aTpe =:= bTpe) {
      aTpe.asType match {
        case '[a] =>
          '{ Into.identity[a] }
      }
    } else {
      // PRIORITY 0: Primitive conversions (before Implicits.search)
      // Use dealias + =:= for robustness (handles type aliases)
      val intType    = TypeRepr.of[Int]
      val longType   = TypeRepr.of[Long]
      val doubleType = TypeRepr.of[Double]
      val floatType  = TypeRepr.of[Float]

      val aDealiased = aTpe.dealias
      val bDealiased = bTpe.dealias

      // Safe widening conversions (always Right)
      if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, longType)) {
        intType.asType match {
          case '[Int] =>
            longType.asType match {
              case '[Long] => generatePrimitiveInto[Int, Long]('{ (a: Int) => Right(a.toLong) })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, doubleType)) {
        intType.asType match {
          case '[Int] =>
            doubleType.asType match {
              case '[Double] => generatePrimitiveInto[Int, Double]('{ (a: Int) => Right(a.toDouble) })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, intType) && isPrimitiveType(using q)(bDealiased, floatType)) {
        intType.asType match {
          case '[Int] =>
            floatType.asType match {
              case '[Float] => generatePrimitiveInto[Int, Float]('{ (a: Int) => Right(a.toFloat) })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, longType) && isPrimitiveType(using q)(bDealiased, doubleType)) {
        longType.asType match {
          case '[Long] =>
            doubleType.asType match {
              case '[Double] => generatePrimitiveInto[Long, Double]('{ (a: Long) => Right(a.toDouble) })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, floatType) && isPrimitiveType(using q)(bDealiased, doubleType)) {
        floatType.asType match {
          case '[Float] =>
            doubleType.asType match {
              case '[Double] => generatePrimitiveInto[Float, Double]('{ (a: Float) => Right(a.toDouble) })
            }
        }
      }
      // Narrowing conversions (with validation)
      else if (isPrimitiveType(using q)(aDealiased, longType) && isPrimitiveType(using q)(bDealiased, intType)) {
        longType.asType match {
          case '[Long] =>
            intType.asType match {
              case '[Int] =>
                generatePrimitiveInto[Long, Int]('{ (a: Long) =>
                  if (a >= Int.MinValue && a <= Int.MaxValue)
                    Right(a.toInt)
                  else
                    Left(
                      SchemaError.expectationMismatch(
                        Nil,
                        s"Long value $a cannot be safely converted to Int (out of range [${Int.MinValue}, ${Int.MaxValue}])"
                      )
                    )
                })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, doubleType) && isPrimitiveType(using q)(bDealiased, floatType)) {
        doubleType.asType match {
          case '[Double] =>
            floatType.asType match {
              case '[Float] =>
                generatePrimitiveInto[Double, Float]('{ (a: Double) =>
                  val floatValue = a.toFloat
                  if (floatValue.isInfinite && !a.isInfinite)
                    Left(SchemaError.expectationMismatch(Nil, s"Double value $a is too large for Float"))
                  else
                    Right(floatValue)
                })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, doubleType) && isPrimitiveType(using q)(bDealiased, longType)) {
        doubleType.asType match {
          case '[Double] =>
            longType.asType match {
              case '[Long] =>
                generatePrimitiveInto[Double, Long]('{ (a: Double) =>
                  if (a.isWhole && a >= Long.MinValue && a <= Long.MaxValue)
                    Right(a.toLong)
                  else {
                    val reason =
                      if (!a.isWhole) s"Double value $a is not a whole number"
                      else s"Double value $a is out of range [${Long.MinValue}, ${Long.MaxValue}]"
                    Left(SchemaError.expectationMismatch(Nil, s"Cannot convert Double to Long: $reason"))
                  }
                })
            }
        }
      } else if (isPrimitiveType(using q)(aDealiased, doubleType) && isPrimitiveType(using q)(bDealiased, intType)) {
        doubleType.asType match {
          case '[Double] =>
            intType.asType match {
              case '[Int] =>
                generatePrimitiveInto[Double, Int]('{ (a: Double) =>
                  if (a.isWhole && a >= Int.MinValue && a <= Int.MaxValue)
                    Right(a.toInt)
                  else {
                    val reason =
                      if (!a.isWhole) s"Double value $a is not a whole number"
                      else s"Double value $a is out of range [${Int.MinValue}, ${Int.MaxValue}]"
                    Left(SchemaError.expectationMismatch(Nil, s"Cannot convert Double to Int: $reason"))
                  }
                })
            }
        }
      }
      // PRIORITY 0.5: Singleton cases (Enum values, Case Objects)
      else if (isCaseObjectOrEnumVal(using q)(aTpe) && isCaseObjectOrEnumVal(using q)(bTpe)) {
        val aName = getSubtypeName(using q)(aTpe)
        val bName = getSubtypeName(using q)(bTpe)

        if (aName == bName) {
          aTpe.asType match {
            case '[a] =>
              bTpe.asType match {
                case '[b] =>
                  generateSingletonInto[a, b](using q)(bTpe)
              }
          }
        } else {
          fail(
            s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: " +
              s"Singleton names don't match: '${aName}' != '${bName}'."
          )
        }
      } else {
        // FALLBACK: Implicits.search and derivation
        val intoType = TypeRepr.of[Into[?, ?]].appliedTo(List(aTpe, bTpe))

        Implicits.search(intoType) match {
          case success: ImplicitSearchSuccess =>
            success.tree.asExpr.asInstanceOf[Expr[Into[?, ?]]]
          case _ =>
            // PRIORITY 1: Collection case (check before Product)
            (extractCollectionElementType(using q)(aTpe), extractCollectionElementType(using q)(bTpe)) match {
              case (Some(aElem), Some(bElem)) =>
                // Both are collections - derive element-wise conversion
                // Recursively find element conversion (which may use primitives)
                deriveCollectionInto(using q)(aTpe, bTpe, aElem, bElem)

              case _ =>
                // PRIORITY 2: Recursive derivation: if both are case classes, derive recursively
                if (isCaseClass(using q)(aTpe) && isCaseClass(using q)(bTpe)) {
                  aTpe.asType match {
                    case '[a] =>
                      bTpe.asType match {
                        case '[b] =>
                          derivedIntoImpl(using Type.of[a], Type.of[b], q)
                      }
                  }
                } else {
                  fail(
                    s"Cannot derive Into[${aTpe.show}, ${bTpe.show}]: No implicit instance found and types are not both collections or case classes."
                  )
                }
            }
        }
      }
    }
  }

  /**
   * Generates the conversion function body: converts A to B by mapping fields.
   */
  private def generateConversionBody[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    fieldConversions: List[(String, q.reflect.Symbol, Expr[Into[?, ?]])]
  ): Expr[A => Either[SchemaError, B]] = {
    import q.reflect._

    // Build the function as a Lambda using the correct API
    val methodType = MethodType(List("a"))(
      _ => List(aTpe),
      _ => TypeRepr.of[Either[SchemaError, B]]
    )

    val lambda = Lambda(
      Symbol.spliceOwner,
      methodType,
      (owner, params) => {
        val aParam    = params.head
        val aParamRef = aParam.asExprOf[A]

        // Generate field conversions using the lambda parameter
        // Convert to List[Expr[Either[SchemaError, Any]]] for the high-level approach
        val fieldConversionExprs = fieldConversions.map { case (_, getter, intoExpr) =>
          // Access field: a.fieldName
          val fieldAccess = Select(aParamRef.asTerm, getter)

          // Convert field: intoInstance.into(a.fieldName)
          val fieldResult = intoExpr.asTerm.tpe.asType match {
            case '[Into[fa, fb]] =>
              fieldAccess.tpe.asType match {
                case '[fa] =>
                  '{
                    $intoExpr.asInstanceOf[Into[fa, fb]].into(${ fieldAccess.asExprOf[fa] })
                  }
              }
          }
          // Cast to Either[SchemaError, Any] for uniform handling
          fieldResult.asExprOf[Either[SchemaError, Any]]
        }

        // Extract B fields for construction
        val bFields = extractCaseClassFields(using q)(bTpe)

        // Generate code that accumulates Either results and builds B
        val conversionBody = generateEitherAccumulation[B](using q)(fieldConversionExprs, bTpe, bFields)
        conversionBody.asTerm.changeOwner(owner)
      }
    )

    lambda.asExprOf[A => Either[SchemaError, B]]
  }

  /**
   * Generates code that accumulates Either results: if all succeed, build B,
   * otherwise return first error. Uses pure AST construction to avoid scope
   * issues.
   */
  private def generateEitherAccumulation[B: Type](using
    q: Quotes
  )(
    fieldConversions: List[Expr[Either[SchemaError, Any]]],
    bTpe: q.reflect.TypeRepr,
    bFields: List[FieldInfo]
  ): Expr[Either[SchemaError, B]] = {
    import q.reflect._

    // 1. Convert List[Expr] -> Expr[List]
    val listExpr = Expr.ofList(fieldConversions)

    // 2. Prepare constructor for B
    val bClassSymbol = bTpe.classSymbol.get
    val constructor  = bClassSymbol.primaryConstructor
    if (!constructor.exists) {
      fail(s"Cannot construct '${bTpe.show}': No primary constructor found.")
    }

    val bTypeArgs = CommonMacroOps.typeArgs(using q)(bTpe)

    // 3. Build Lambda: (args: List[Any]) => B
    val lambdaMethodType = MethodType(List("args"))(
      _ => List(TypeRepr.of[List[Any]]),
      _ => bTpe
    )

    val mapLambda = Lambda(
      Symbol.spliceOwner,
      lambdaMethodType,
      (owner, params) => {
        val argsParam = params.head.asInstanceOf[Term]

        // 4. Build constructor arguments: args(i).asInstanceOf[Type]
        val typedArgs = bFields.zipWithIndex.map { case (fieldInfo, idx) =>
          val fieldType = fieldInfo.tpeRepr(using q)

          // args.apply(idx) - use Select.unique to access the apply method
          val indexLiteral = Literal(IntConstant(idx))
          val listApply    = Apply(
            Select.unique(argsParam, "apply"),
            List(indexLiteral)
          )

          // .asInstanceOf[T]
          val asInstanceOfSelect = Select.unique(listApply, "asInstanceOf")
          TypeApply(
            asInstanceOfSelect,
            List(Inferred(fieldType))
          )
        }

        // 5. Build 'new B(...)'
        val newB = if (bTypeArgs.isEmpty) {
          Select(New(Inferred(bTpe)), constructor)
        } else {
          Select(New(Inferred(bTpe)), constructor).appliedToTypes(bTypeArgs)
        }

        val applied = Apply(newB, typedArgs)
        applied.changeOwner(owner)
      }
    )

    // 6. Build final call: sequenceEither(list).map(lambda)
    // sequenceEither(listExpr)
    val sequenceModule = Ref(Symbol.requiredModule("zio.blocks.schema.IntoAsVersionSpecificImpl"))
    val sequenceMethod = Select.unique(sequenceModule, "sequenceEither")
    val sequenceCall   = Apply(sequenceMethod, List(listExpr.asTerm))

    // .map[B](lambda)
    // Either.map ha firma: [B1](f: A => B1): Either[E, B1]
    val mapSelect    = Select.unique(sequenceCall, "map")
    val mapTypeApply = TypeApply(mapSelect, List(Inferred(bTpe)))
    val mapCall      = Apply(mapTypeApply, List(mapLambda))

    mapCall.asExprOf[Either[SchemaError, B]]
  }

  /**
   * Summons or generates ClassTag for a type. Uses Expr.summon which works
   * cross-platform.
   */
  private def summonClassTag(using q: Quotes)(elemType: q.reflect.TypeRepr): Expr[ClassTag[?]] =

    elemType.asType match {
      case '[et] =>
        Expr.summon[ClassTag[et]].getOrElse {
          fail(
            s"Cannot derive Into for Array: ClassTag[${elemType.show}] not available. " +
              s"Make sure the element type is concrete and not a type parameter."
          )
        }
    }

  /**
   * Gets the factory/companion object for building a collection type. Returns a
   * Term that can be used to construct the collection.
   */
  private def getCollectionFactory(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect._

    val typeName = tpe.typeSymbol.fullName

    typeName match {
      case "scala.collection.immutable.List" =>
        Ref(Symbol.requiredModule("scala.collection.immutable.List"))

      case "scala.collection.immutable.Vector" =>
        Ref(Symbol.requiredModule("scala.collection.immutable.Vector"))

      case "scala.collection.immutable.Seq" =>
        Ref(Symbol.requiredModule("scala.collection.immutable.Seq"))

      case "scala.collection.immutable.Set" =>
        Ref(Symbol.requiredModule("scala.collection.immutable.Set"))

      case "scala.collection.immutable.IndexedSeq" =>
        Ref(Symbol.requiredModule("scala.collection.immutable.IndexedSeq"))

      case "scala.Array" =>
        Ref(Symbol.requiredModule("scala.Array"))

      case _ =>
        // Try generic approach: look for Factory or from method
        // For now, fail with helpful message
        fail(
          s"Cannot find factory for collection type: ${tpe.show}. " +
            s"Supported: List, Vector, Seq, Set, IndexedSeq, Array"
        )
    }
  }

  /**
   * Builds code to construct target collection from List[A]. Handles Array
   * specially (requires ClassTag).
   */
  private def buildCollectionFromList(using
    q: Quotes
  )(
    listExpr: q.reflect.Term,
    targetType: q.reflect.TypeRepr,
    elemType: q.reflect.TypeRepr
  ): q.reflect.Term = {
    import q.reflect._

    if (isArray(using q)(targetType)) {
      // Array case: need ClassTag and use Array.from
      val classTag = summonClassTag(using q)(elemType)
      elemType.asType match {
        case '[et] =>
          // Build: Array.from[et](list)(using classTag)
          val arrayModule   = Ref(Symbol.requiredModule("scala.Array"))
          val fromMethod    = Select.unique(arrayModule, "from")
          val fromTypeApply = TypeApply(fromMethod, List(Inferred(elemType)))
          // Array.from takes (it: IterableOnce[A])(using ClassTag[A])
          // We pass listExpr and classTag as implicit parameter
          Apply(
            Apply(fromTypeApply, List(listExpr)),
            List(classTag.asTerm)
          )
      }
    } else {
      // Other collections: use companion.from pattern
      // Check if target is List - if so, list is already List[be], so we can use it directly
      // Otherwise, use companion.from (e.g., Vector.from, Set.from)
      val targetTypeName = targetType.typeSymbol.fullName

      if (targetTypeName == "scala.collection.immutable.List") {
        // Target is List, and listExpr is already List[be], so use it directly
        listExpr
      } else {
        // For other collections, use companion.from
        // Get the companion object (e.g., Vector, Set)
        val factory = getCollectionFactory(using q)(targetType)

        // Call companion.from[et](list)
        // Most collection companion objects have a 'from' method that takes IterableOnce[A]
        val fromMethod = Select.unique(factory, "from")
        Apply(
          TypeApply(fromMethod, List(Inferred(elemType))),
          List(listExpr)
        )
      }
    }
  }

  /**
   * Derives Into[A, B] for collection types where A and B are collections.
   * Handles element conversion and container conversion. Uses Quotes with
   * .asType for type-safe code generation.
   */
  private def deriveCollectionInto[A: Type, B: Type](using
    q: Quotes
  )(
    aTpe: q.reflect.TypeRepr,
    bTpe: q.reflect.TypeRepr,
    aElem: q.reflect.TypeRepr,
    bElem: q.reflect.TypeRepr
  ): Expr[Into[A, B]] = {
    import q.reflect._

    // 1. Derive element conversion recursively
    val elemInto = findOrDeriveInto(using q)(aElem, bElem)

    // 2. Convert dynamic TypeRepr to static types using .asType
    aElem.asType match {
      case '[ae] =>
        bElem.asType match {
          case '[be] =>
            // Now we have static types [ae] and [be]

            // 3. Build conversion function: (a: A) => Either[SchemaError, B]
            val methodType = MethodType(List("a"))(
              _ => List(aTpe),
              _ => TypeRepr.of[Either[SchemaError, B]]
            )

            val lambda = Lambda(
              Symbol.spliceOwner,
              methodType,
              (owner, params) => {
                val aParam = params.head.asInstanceOf[Term]

                // 4. Handle Array: convert to ArraySeq if needed
                val iterableSourceTerm = if (isArray(using q)(aTpe)) {
                  // Convert Array to ArraySeq which implements Iterable
                  val arraySeqModule        = Ref(Symbol.requiredModule("scala.collection.immutable.ArraySeq"))
                  val unsafeWrapArrayMethod = Select.unique(arraySeqModule, "unsafeWrapArray")
                  Apply(
                    TypeApply(unsafeWrapArrayMethod, List(Inferred(aElem))),
                    List(aParam)
                  )
                } else {
                  // Other collections: use directly (they implement Iterable)
                  aParam
                }

                // 5. Convert Term to Expr (safe: iterableSourceTerm is Iterable[ae])
                val iterableSourceExpr = iterableSourceTerm.asExprOf[Iterable[ae]]

                // 6. Convert elemInto to typed Expr
                val elemIntoExpr = elemInto.asExprOf[Into[ae, be]]

                // 7. Build element conversion function using Quotes
                val mapLambda = '{ (elem: ae) =>
                  $elemIntoExpr.into(elem)
                }

                // 8. Call runtime helper using typed Quotes
                val sequenceResultExpr = '{
                  IntoAsVersionSpecificImpl.mapAndSequence[ae, be](
                    $iterableSourceExpr,
                    $mapLambda
                  )
                }

                // 9. Map over Either to build target collection
                // sequenceResultExpr is Expr[Either[SchemaError, List[be]]]
                // We need to map it to Either[SchemaError, B]
                bTpe.asType match {
                  case '[b] =>
                    // Map over Either to build collection from List[be]
                    '{
                      $sequenceResultExpr.map { (list: List[be]) =>
                        ${
                          // Convert list Expr to Term, build collection, convert back to Expr
                          val listTerm  = 'list.asTerm
                          val builtTerm = buildCollectionFromList(using q)(listTerm, bTpe, bElem)
                          builtTerm.asExprOf[b]
                        }
                      }
                    }.asTerm.changeOwner(owner)
                }
              }
            )

            // 10. Wrap conversion function in Into instance
            val conversionFunction = lambda.asExprOf[A => Either[SchemaError, B]]
            '{
              new Into[A, B] {
                def into(a: A): Either[SchemaError, B] = $conversionFunction(a)
              }
            }
        }
    }
  }

  /**
   * Macro implementation for deriving `As[A, B]` instances.
   *
   * This macro uses a composition approach: `As[A, B]` is implemented by
   * composing two `Into` instances: `Into[A, B]` and `Into[B, A]`.
   *
   * This approach:
   *   - Reuses all existing `Into` derivation logic (DRY principle)
   *   - Avoids code duplication
   *   - Maintains cross-platform compatibility
   *   - Has no risk of infinite loops (explicit calls, not implicit resolution)
   *
   * Note: `As` represents a "partial equivalence", not a perfect isomorphism.
   * Round-trip conversions may lose information (e.g., List ↔ Set loses
   * order/duplicates), which is acceptable and documented.
   */
  def derivedAsImpl[A: Type, B: Type](using q: Quotes): Expr[As[A, B]] = {
    // Generate Into[A, B] instance
    val intoAB = derivedIntoImpl[A, B]

    // Generate Into[B, A] instance
    val intoBA = derivedIntoImpl[B, A]

    // Compose both Into instances into As[A, B]
    '{
      new As[A, B] {
        def into(a: A): Either[SchemaError, B] = $intoAB.into(a)
        def from(b: B): Either[SchemaError, A] = $intoBA.into(b)
      }
    }
  }
}
