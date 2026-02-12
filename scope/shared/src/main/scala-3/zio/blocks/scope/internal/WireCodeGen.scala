package zio.blocks.scope.internal

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.{Finalizer, Wire}
import scala.quoted.*
import scala.compiletime.summonInline

/**
 * Unified code generation for Wire derivation (shared/unique).
 *
 * This consolidates the code generation that was previously duplicated across
 * deriveSharedWire and deriveUniqueWire.
 */
private[scope] object WireCodeGen {

  /** Wire kind: Shared or Unique */
  enum WireKind {
    case Shared, Unique
  }

  /**
   * Derive a Wire[In, T] from T's constructor.
   *
   * Returns the In type as a TypeRepr alongside the wire expression.
   *
   * All type analysis and code generation happens within the same Quotes
   * context.
   */
  def deriveWire[T: Type](using
    Quotes
  )(
    kind: WireKind
  ): (quotes.reflect.TypeRepr, Expr[Wire[?, T]]) = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abortNotAClass(tpe.show)
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abortNoPrimaryCtor(tpe.show)
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    val depTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType = tpe.memberType(param).dealias.simplified
      MacroCore.classifyParam(paramType)
    }

    MacroCore.checkSubtypeConflicts(depTypes) match {
      case Some((subtype, supertype)) => MacroCore.abortSubtypeConflict(tpe.show, subtype, supertype)
      case None                       => // ok
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val inType          = MacroCore.computeInType(depTypes)

    def generateArgTerm(
      paramType: TypeRepr,
      finalizerExpr: Expr[Finalizer],
      ctxExpr: Expr[Context[?]]
    ): Term =
      if (MacroCore.isFinalizerType(paramType)) {
        finalizerExpr.asTerm
      } else {
        paramType.asType match {
          case '[d] =>
            // Cast context to Context[d] so that get[d] satisfies the A >: R bound
            '{ ${ ctxExpr.asExprOf[Context[d]] }.get[d](using summonInline[IsNominalType[d]]) }.asTerm
        }
      }

    def generateWireBody[In: Type](finalizerExpr: Expr[Finalizer], ctxExpr: Expr[Context[In]]): Expr[T] = {
      val ctorSym = tpe.typeSymbol.primaryConstructor

      val argListTerms: List[List[Term]] = paramLists.map { params =>
        params.map { param =>
          val paramType = tpe.memberType(param).dealias.simplified
          generateArgTerm(paramType, finalizerExpr, ctxExpr.asExprOf[Context[?]])
        }
      }

      val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
      val applied  = argListTerms.foldLeft[Term](ctorTerm) { (fn, args) =>
        Apply(fn, args)
      }

      val instanceExpr = applied.asExprOf[T]

      if (isAutoCloseable) {
        '{
          val instance = $instanceExpr
          $finalizerExpr.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      } else {
        instanceExpr
      }
    }

    val wireExpr = inType.asType match {
      case '[inTpe] =>
        kind match {
          case WireKind.Shared =>
            '{
              Wire.Shared[inTpe, T] { (finalizer: Finalizer, ctx: Context[inTpe]) =>
                ${ generateWireBody[inTpe]('{ finalizer }, '{ ctx }) }
              }
            }

          case WireKind.Unique =>
            '{
              Wire.Unique[inTpe, T] { (finalizer: Finalizer, ctx: Context[inTpe]) =>
                ${ generateWireBody[inTpe]('{ finalizer }, '{ ctx }) }
              }
            }
        }
    }

    (inType, wireExpr)
  }
}
