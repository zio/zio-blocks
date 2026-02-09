package zio.blocks.scope.internal

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.{Finalizer, Wire, Wireable}
import scala.quoted.*
import scala.compiletime.summonInline

/**
 * Unified code generation for Wire derivation (shared/unique/wireable).
 *
 * This consolidates the code generation that was previously duplicated across
 * deriveSharedWire, deriveUniqueWire, and WireableMacros.fromImpl.
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
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    val depTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType     = tpe.memberType(param).dealias.simplified
      val (_, maybeDep) = MacroCore.classifyParam(paramType)
      maybeDep
    }

    MacroCore.checkSubtypeConflicts(tpe.show, depTypes) match {
      case Some(error) => MacroCore.abort(error)
      case None        => // ok
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
              Wire.Shared[inTpe, T] { (finalizer, ctx) =>
                ${ generateWireBody[inTpe]('{ finalizer }, '{ ctx }) }
              }
            }

          case WireKind.Unique =>
            '{
              Wire.Unique[inTpe, T] { (finalizer, ctx) =>
                ${ generateWireBody[inTpe]('{ finalizer }, '{ ctx }) }
              }
            }
        }
    }

    (inType, wireExpr)
  }

  /**
   * Convert a Wire expression to Shared with preserved In type.
   */
  def wireFromWireable[T: Type](using
    Quotes
  )(
    wireableExpr: Expr[Wireable[T]],
    kind: WireKind
  ): Expr[Wire[?, T]] = {
    import quotes.reflect.*

    val wireableTpe = wireableExpr.asTerm.tpe
    val inTypeRepr  = MacroCore.extractWireableInType(wireableTpe)

    inTypeRepr.asType match {
      case '[inType] =>
        kind match {
          case WireKind.Shared =>
            '{ $wireableExpr.wire.shared.asInstanceOf[Wire.Shared[inType, T]] }
          case WireKind.Unique =>
            '{ $wireableExpr.wire.unique.asInstanceOf[Wire.Unique[inType, T]] }
        }
    }
  }

  /**
   * Generate a complete Wireable[T] from T's constructor.
   *
   * All type analysis and code generation happens within the same Quotes
   * context.
   */
  def deriveWireable[T: Type](using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    val depTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType     = tpe.memberType(param).dealias.simplified
      val (_, maybeDep) = MacroCore.classifyParam(paramType)
      maybeDep
    }

    MacroCore.checkSubtypeConflicts(tpe.show, depTypes) match {
      case Some(error) => MacroCore.abort(error)
      case None        => // ok
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

    inType.asType match {
      case '[inTpe] =>
        '{
          new Wireable[T] {
            type In = inTpe

            def wire: Wire[inTpe, T] = Wire.Shared[inTpe, T] { (finalizer, ctx) =>
              ${ generateWireBody[inTpe]('{ finalizer }, '{ ctx }) }
            }
          }
        }
    }
  }

  /**
   * Generate a Wireable[T] with wire overrides for some dependencies.
   *
   * The wires parameter provides overrides for some of T's dependencies.
   * Dependencies covered by overrides are resolved from the wires; remaining
   * dependencies become the In type.
   */
  def deriveWireableWithOverrides[T: Type](wiresExpr: Expr[Seq[Wire[?, ?]]])(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    val allDepTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType     = tpe.memberType(param).dealias.simplified
      val (_, maybeDep) = MacroCore.classifyParam(paramType)
      maybeDep
    }

    MacroCore.checkSubtypeConflicts(tpe.show, allDepTypes) match {
      case Some(error) => MacroCore.abort(error)
      case None        => // ok
    }

    val wireExprs: List[Expr[Wire[?, ?]]] = wiresExpr match {
      case Varargs(wires) => wires.toList
      case other          =>
        report.errorAndAbort(s"Expected varargs of Wire expressions, got: ${other.show}")
    }

    val wireOutTypes: List[TypeRepr] = wireExprs.map { wireExpr =>
      val wireTpe = wireExpr.asTerm.tpe.widen.dealias.simplified
      wireTpe match {
        case AppliedType(_, List(_, outType)) => outType.dealias.simplified
        case other                            =>
          report.errorAndAbort(s"Cannot extract output type from wire: ${other.show}")
      }
    }

    val (_, remainingDeps) = allDepTypes.partition { depType =>
      wireOutTypes.exists(outType => outType <:< depType)
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val inType          = MacroCore.computeInType(remainingDeps)

    def findWireForType(depType: TypeRepr): Option[(Expr[Wire[?, ?]], TypeRepr)] =
      wireExprs.zip(wireOutTypes).find { case (_, outType) =>
        outType <:< depType
      }

    def generateArgTermWithOverrides(
      paramType: TypeRepr,
      finalizerExpr: Expr[Finalizer],
      ctxExpr: Expr[Context[?]],
      overrideCtxExpr: Expr[Context[?]]
    ): Term =
      if (MacroCore.isFinalizerType(paramType)) {
        finalizerExpr.asTerm
      } else {
        findWireForType(paramType) match {
          case Some((_, outType)) =>
            outType.asType match {
              case '[d] =>
                '{ $overrideCtxExpr.asInstanceOf[Context[d]].get[d](using summonInline[IsNominalType[d]]) }.asTerm
            }
          case None =>
            paramType.asType match {
              case '[d] =>
                '{ $ctxExpr.asInstanceOf[Context[d]].get[d](using summonInline[IsNominalType[d]]) }.asTerm
            }
        }
      }

    def generateWireBodyWithOverrides[In: Type](
      finalizerExpr: Expr[Finalizer],
      ctxExpr: Expr[Context[In]],
      overrideCtxExpr: Expr[Context[?]]
    ): Expr[T] = {
      val ctorSym = tpe.typeSymbol.primaryConstructor

      val argListTerms: List[List[Term]] = paramLists.map { params =>
        params.map { param =>
          val paramType = tpe.memberType(param).dealias.simplified
          generateArgTermWithOverrides(paramType, finalizerExpr, ctxExpr.asExprOf[Context[?]], overrideCtxExpr)
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

    def buildOverrideContext(finalizerExpr: Expr[Finalizer]): Expr[Context[?]] =
      wireExprs.zip(wireOutTypes).foldLeft('{ Context.empty }: Expr[Context[?]]) {
        case (ctxExpr, (wireExpr, outType)) =>
          outType.asType match {
            case '[d] =>
              '{
                val ctx   = $ctxExpr
                val wire  = $wireExpr.asInstanceOf[Wire[Any, d]]
                val value = wire.make($finalizerExpr, ctx.asInstanceOf[Context[Any]])
                ctx.add[d](value)(using summonInline[IsNominalType[d]])
              }
          }
      }

    inType.asType match {
      case '[inTpe] =>
        '{
          new Wireable[T] {
            type In = inTpe

            def wire: Wire[inTpe, T] = Wire.Shared[inTpe, T] { (finalizer, ctx) =>
              val overrideCtx = ${ buildOverrideContext('{ finalizer }) }
              ${ generateWireBodyWithOverrides[inTpe]('{ finalizer }, '{ ctx }, '{ overrideCtx }) }
            }
          }
        }
    }
  }
}
