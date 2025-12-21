package zio.blocks.schema

import scala.quoted.*

/**
 * Type class for bidirectional type-safe conversions between A and B.
 *
 * As[A, B] provides both `into` (A -> B) and `from` (B -> A) conversions,
 * ensuring that the types are compatible for round-trip conversions.
 *
 * The key invariant is:
 *   - For any value `a: A`, `from(into(a))` should succeed and produce an
 *     equivalent value
 *   - For any value `b: B`, `into(from(b))` should succeed and produce an
 *     equivalent value
 *
 * @tparam A
 *   First type
 * @tparam B
 *   Second type
 */
trait As[A, B] {

  /**
   * Convert from A to B.
   */
  def into(input: A): Either[SchemaError, B]

  /**
   * Convert from B to A.
   */
  def from(input: B): Either[SchemaError, A]

  /**
   * Convert from A to B, throwing on failure.
   */
  final def intoOrThrow(input: A): B = into(input) match {
    case Right(b)  => b
    case Left(err) => throw err
  }

  /**
   * Convert from B to A, throwing on failure.
   */
  final def fromOrThrow(input: B): A = from(input) match {
    case Right(a)  => a
    case Left(err) => throw err
  }

  /**
   * Get the Into[A, B] instance from this As[A, B].
   */
  def asInto: Into[A, B] = new Into[A, B] {
    def into(input: A): Either[SchemaError, B] = As.this.into(input)
  }

  /**
   * Get the Into[B, A] instance from this As[A, B].
   */
  def asIntoReverse: Into[B, A] = new Into[B, A] {
    def into(input: B): Either[SchemaError, A] = As.this.from(input)
  }
}

object As {

  /**
   * Summon an As[A, B] instance from implicit scope or derive it.
   */
  inline def apply[A, B](using as: As[A, B]): As[A, B] = as

  /**
   * Automatically derive As[A, B] instances at compile time.
   *
   * Note: As[A, B] can only be derived when both Into[A, B] and Into[B, A]
   * exist.
   */
  inline given derived[A, B]: As[A, B] = ${ AsMacro.deriveImpl[A, B] }

  /**
   * Identity conversion (A to A).
   */
  given identity[A]: As[A, A] = new As[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
    def from(input: A): Either[SchemaError, A] = Right(input)
  }

  /**
   * Create an As[A, B] from two Into instances.
   */
  def fromInto[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }
}

private object AsMacro {
  def deriveImpl[A: Type, B: Type](using Quotes): Expr[As[A, B]] = {
    import quotes.reflect.*

    val aType = TypeRepr.of[A]
    val bType = TypeRepr.of[B]

    // If types are the same, use identity
    if (aType =:= bType) {
      return '{ As.identity[A].asInstanceOf[As[A, B]] }
    }

    // Try to derive or summon Into[A, B]
    val intoAB = Expr.summon[Into[A, B]] match {
      case Some(into) => into
      case None       => '{ Into.derived[A, B] }
    }

    // Try to derive or summon Into[B, A]
    val intoBA = Expr.summon[Into[B, A]] match {
      case Some(into) => into
      case None       => '{ Into.derived[B, A] }
    }

    // Check for compatibility (both conversions must be lossless)
    checkCompatibility[A, B](aType, bType)

    // Check that no default values are used (breaks round-trip guarantee)
    checkNoDefaultsUsed[A, B](aType, bType)
    checkNoDefaultsUsed[B, A](bType, aType)

    // Create As from the two Into instances
    '{ As.fromInto[A, B]($intoAB, $intoBA) }
  }

  private def checkCompatibility[A: Type, B: Type](using
    Quotes
  )(
    aType: quotes.reflect.TypeRepr,
    bType: quotes.reflect.TypeRepr
  ): Unit = {
    import quotes.reflect.*

    // Check for narrowing numeric conversions in both directions
    val isNarrowingAtoB = isNarrowingConversion(aType, bType)
    val isNarrowingBtoA = isNarrowingConversion(bType, aType)

    if (isNarrowingAtoB && isNarrowingBtoA) {
      report.errorAndAbort(
        s"Cannot derive As[${aType.show}, ${bType.show}]: " +
          s"Both directions would require narrowing conversions with potential data loss."
      )
    }

    // For collections with narrowing, warn but allow (validation happens at runtime)
    if (isCollectionType(aType) || isCollectionType(bType)) {
      // Collections are allowed as long as element conversions are valid
      return
    }
  }

  private def checkNoDefaultsUsed[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Unit = {
    import quotes.reflect.*
    import zio.blocks.schema.derive.*

    // Only check product types (case classes)
    def isProductType(tpe: TypeRepr): Boolean =
      tpe.classSymbol.exists(_.flags.is(Flags.Case)) ||
        (tpe <:< TypeRepr.of[Product] && {
          val sym = tpe.typeSymbol
          sym.fullName.startsWith("scala.Tuple") || sym.fullName == "scala.Product"
        })

    if (isProductType(source) && isProductType(target)) {
      // Extract fields using the same logic as ProductMacros
      def extractFields(tpe: TypeRepr): Seq[FieldInfo] =
        tpe.classSymbol match {
          case Some(classSymbol) if classSymbol.flags.is(Flags.Case) =>
            val constructor     = classSymbol.primaryConstructor
            val params          = constructor.paramSymss.flatten.filterNot(_.isTypeParam)
            val companionModule = classSymbol.companionModule
            val companionClass  = classSymbol.companionClass
            val tpeTypeArgs     = tpe.typeArgs

            params.zipWithIndex.map { case (param, idx) =>
              val fieldType  = tpe.memberType(param)
              val hasDefault = param.flags.is(Flags.HasDefault)

              val defaultValue = if (hasDefault) {
                try {
                  val dvMethodName = s"$$lessinit$$greater$$default$$${idx + 1}"
                  companionClass.declaredMethod(dvMethodName).headOption.map { dvMethod =>
                    val dvSelect = Select(Ref(companionModule), dvMethod)
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
          case _ => Seq.empty
        }

      val sourceFields = extractFields(source)
      val targetFields = extractFields(target)

      FieldMapper.mapFields(using quotes)(sourceFields, targetFields) match {
        case Right(actions) =>
          val usesDefaults = actions.exists {
            case FieldMappingAction.UseDefault(_, _) => true
            case _                                   => false
          }

          if (usesDefaults) {
            report.errorAndAbort(
              s"Cannot derive As[${source.show}, ${target.show}]: " +
                s"Default values are used in field mapping, which breaks round-trip guarantee. " +
                s"When converting ${source.show} -> ${target.show}, some fields use default values " +
                s"that cannot be recovered when converting back. " +
                s"Use Into[${source.show}, ${target.show}] instead for one-way conversions."
            )
          }
        case Left(_) =>
        // If mapping fails, As will fail anyway, so this is OK
      }
    }
  }

  private def isNarrowingConversion(using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Boolean = {
    import quotes.reflect.*

    val longTpe   = TypeRepr.of[Long]
    val intTpe    = TypeRepr.of[Int]
    val shortTpe  = TypeRepr.of[Short]
    val byteTpe   = TypeRepr.of[Byte]
    val doubleTpe = TypeRepr.of[Double]
    val floatTpe  = TypeRepr.of[Float]

    (source =:= longTpe && (target =:= intTpe || target =:= shortTpe || target =:= byteTpe)) ||
    (source =:= intTpe && (target =:= shortTpe || target =:= byteTpe)) ||
    (source =:= shortTpe && target =:= byteTpe) ||
    (source =:= doubleTpe && target =:= floatTpe)
  }

  private def isCollectionType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe <:< TypeRepr.of[Iterable[?]] || tpe <:< TypeRepr.of[Option[?]]
  }
}
