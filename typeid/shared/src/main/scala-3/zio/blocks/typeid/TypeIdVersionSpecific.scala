package zio.blocks.typeid

import scala.quoted.*

/**
 * Scala 3.5+ macro implementation for TypeId derivation.
 *
 * This object provides compile-time derivation of TypeId instances using Scala
 * 3's metaprogramming capabilities.
 */
object TypeIdMacros {

  // Import our types with unambiguous names
  import zio.blocks.typeid.{TypeRepr => ZTypeRepr, TypeId => ZTypeId, Owner => ZOwner}

  /**
   * Derives a TypeId for type A at compile time.
   */
  inline def derive[A]: ZTypeId[A] = ${ deriveMacro[A] }

  /**
   * The macro implementation for TypeId derivation.
   */
  def deriveMacro[A: Type](using Quotes): Expr[ZTypeId[A]] = {
    import quotes.reflect.*

    val tpe        = TypeRepr.of[A]
    val typeSymbol = tpe.typeSymbol

    // Build owner chain from symbol's owner hierarchy
    def ownerChain(sym: Symbol): List[Symbol] = {
      def loop(s: Symbol, acc: List[Symbol]): List[Symbol] =
        if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass) acc
        else loop(s.owner, s :: acc)
      loop(sym.owner, Nil)
    }

    // Convert symbol to owner segment expression
    def symbolToSegment(sym: Symbol): Expr[ZOwner.Segment] = {
      val name = Expr(sym.name)
      if (sym.isPackageDef) {
        '{ ZOwner.Package($name) }
      } else if (sym.flags.is(Flags.Module)) {
        '{ ZOwner.Term($name) }
      } else if (sym.isClassDef || sym.isTypeDef) {
        '{ ZOwner.Type($name) }
      } else {
        '{ ZOwner.Term($name) }
      }
    }

    // Build the owner segments
    val owners                                        = ownerChain(typeSymbol)
    val ownerSegmentExprs: List[Expr[ZOwner.Segment]] = owners.map(symbolToSegment)
    val ownerExpr: Expr[ZOwner]                       = '{
      ZOwner(${ Expr.ofList(ownerSegmentExprs) })
    }

    // Build type parameter info
    def extractTypeParams(tpe: TypeRepr): List[Expr[TypeParam]] =
      tpe match {
        case TypeLambda(paramNames, _, _) =>
          paramNames.zipWithIndex.map { case (name, index) =>
            val nameExpr  = Expr(name)
            val indexExpr = Expr(index)
            '{ TypeParam($nameExpr, $indexExpr) }
          }
        case _ =>
          typeSymbol.typeMembers.filter(_.isTypeParam).zipWithIndex.map { case (param, index) =>
            val nameExpr  = Expr(param.name)
            val indexExpr = Expr(index)

            // Determine variance
            val varianceExpr: Expr[Variance] =
              if (param.flags.is(Flags.Covariant))
                '{ Variance.Covariant }
              else if (param.flags.is(Flags.Contravariant))
                '{ Variance.Contravariant }
              else
                '{ Variance.Invariant }

            '{ new TypeParam($nameExpr, $indexExpr, $varianceExpr, TypeParam.Bounds.Unbounded, false) }
          }
      }

    val typeParamExprs                        = extractTypeParams(tpe)
    val typeParamsExpr: Expr[List[TypeParam]] = Expr.ofList(typeParamExprs)

    val typeNameExpr = Expr(typeSymbol.name)

    // Check if this is an alias or opaque type
    val result: Expr[ZTypeId[A]] =
      if (typeSymbol.isAliasType) {
        // Type alias
        val aliased         = tpe.dealias
        val aliasedReprExpr = typeToReprExpr(aliased)
        '{
          ZTypeId.alias[A](
            $typeNameExpr,
            $ownerExpr,
            $typeParamsExpr,
            $aliasedReprExpr
          )
        }
      } else if (typeSymbol.flags.is(Flags.Opaque)) {
        // Opaque type - try to get the underlying representation
        // Note: Opaque types hide their representation, so we just record it as opaque
        val reprExpr = '{ ZTypeRepr.AnyType } // Can't access the true representation
        '{
          ZTypeId.opaque[A](
            $typeNameExpr,
            $ownerExpr,
            $typeParamsExpr,
            $reprExpr
          )
        }
      } else {
        // Nominal type
        '{
          ZTypeId.nominal[A](
            $typeNameExpr,
            $ownerExpr,
            $typeParamsExpr
          )
        }
      }

    result
  }

  /**
   * Converts a compile-time TypeRepr to a runtime ZTypeRepr expression.
   */
  private def typeToReprExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[ZTypeRepr] = {
    import quotes.reflect.*

    // Match well-known types first
    tpe.asType match {
      case '[Unit]    => '{ ZTypeRepr.Ref(ZTypeId.unit) }
      case '[Boolean] => '{ ZTypeRepr.Ref(ZTypeId.boolean) }
      case '[Byte]    => '{ ZTypeRepr.Ref(ZTypeId.byte) }
      case '[Short]   => '{ ZTypeRepr.Ref(ZTypeId.short) }
      case '[Int]     => '{ ZTypeRepr.Ref(ZTypeId.int) }
      case '[Long]    => '{ ZTypeRepr.Ref(ZTypeId.long) }
      case '[Float]   => '{ ZTypeRepr.Ref(ZTypeId.float) }
      case '[Double]  => '{ ZTypeRepr.Ref(ZTypeId.double) }
      case '[Char]    => '{ ZTypeRepr.Ref(ZTypeId.char) }
      case '[String]  => '{ ZTypeRepr.Ref(ZTypeId.string) }
      case '[t]       =>
        tpe match {
          case AppliedType(tycon, args) =>
            // Applied type like List[Int]
            val tyconRepr = typeToReprExpr(tycon)
            val argReprs  = Expr.ofList(args.map(typeToReprExpr))
            '{ ZTypeRepr.Applied($tyconRepr, $argReprs) }

          case AndType(left, right) =>
            val leftRepr  = typeToReprExpr(left)
            val rightRepr = typeToReprExpr(right)
            '{ ZTypeRepr.Intersection($leftRepr, $rightRepr) }

          case OrType(left, right) =>
            val leftRepr  = typeToReprExpr(left)
            val rightRepr = typeToReprExpr(right)
            '{ ZTypeRepr.Union($leftRepr, $rightRepr) }

          case ConstantType(const) =>
            const match {
              case IntConstant(value)     => '{ ZTypeRepr.Constant(${ Expr(value) }) }
              case StringConstant(value)  => '{ ZTypeRepr.Constant(${ Expr(value) }) }
              case BooleanConstant(value) => '{ ZTypeRepr.Constant(${ Expr(value) }) }
              case _                      => '{ ZTypeRepr.Ref(ZTypeId.nominal[t](${ Expr(tpe.typeSymbol.name) }, ZOwner.Root, Nil)) }
            }

          case _ =>
            // Default: create a nominal type reference
            val nameExpr = Expr(tpe.typeSymbol.name)
            '{ ZTypeRepr.Ref(ZTypeId.nominal[t]($nameExpr, ZOwner.Root, Nil)) }
        }
    }
  }
}

/**
 * Scala 3.5+ specific extension for TypeId companion.
 */
trait TypeIdVersionSpecific {

  /**
   * Derives a TypeId for type A at compile time.
   *
   * ==Example==
   * {{{
   * val listId: TypeId[List] = TypeId.derive[List]
   * val myClassId: TypeId[MyClass] = TypeId.derive[MyClass]
   * }}}
   *
   * @tparam A
   *   The type to derive a TypeId for
   * @return
   *   A TypeId representing type A
   */
  inline def derive[A]: TypeId[A] = TypeIdMacros.derive[A]
}
