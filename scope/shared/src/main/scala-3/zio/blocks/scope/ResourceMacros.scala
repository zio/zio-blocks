package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.MacroCore
import scala.compiletime.summonInline
import scala.quoted.*

private[scope] object ResourceMacros {

  def deriveResourceImpl[T: Type](using Quotes): Expr[Resource[T]] = {
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

    val paramLists = ctor.paramSymss

    // Separate regular params from implicit/given params
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten

    // Check for Scope parameter in implicits
    val hasScopeParam = implicitParams.flatten.exists { param =>
      val paramType = tpe.memberType(param)
      paramType <:< TypeRepr.of[Scope[?, ?]]
    }

    // Collect dependencies (non-Finalizer regular params)
    val depTypes: List[TypeRepr] = allRegularParams.flatMap { param =>
      val paramType = tpe.memberType(param).dealias.simplified
      if (MacroCore.isFinalizerType(paramType)) None
      else Some(paramType)
    }

    if (depTypes.nonEmpty) {
      report.errorAndAbort(
        s"Resource.from[${tpe.show}] cannot be derived: ${tpe.show} has dependencies: ${depTypes.map(_.show).mkString(", ")}. " +
          s"Use Resource.from[${tpe.show}](wire1, wire2, ...) to provide wires for all dependencies."
      )
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val ctorSym         = sym.primaryConstructor

    // Generate resource body
    if (hasScopeParam) {
      // Constructor takes implicit Scope
      '{
        Resource.shared[T] { scope =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            val applied  = Apply(Apply(ctorTerm, Nil), List('{ scope }.asTerm))
            applied.asExprOf[T]
          }
        }
      }
    } else if (isAutoCloseable) {
      // AutoCloseable - register finalizer
      '{
        Resource.shared[T] { scope =>
          val instance = ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctorTerm, Nil).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          instance
        }
      }
    } else {
      // Simple case - no dependencies, no cleanup
      '{
        Resource.shared[T] { _ =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctorTerm, Nil).asExprOf[T]
          }
        }
      }
    }
  }

  def deriveResourceWithOverridesImpl[T: Type](wiresExpr: Expr[Seq[Wire[?, ?]]])(using Quotes): Expr[Resource[T]] = {
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

    if (remainingDeps.nonEmpty) {
      val missing  = remainingDeps.map(_.show).mkString(", ")
      val provided = wireOutTypes.map(_.show).mkString(", ")
      report.errorAndAbort(
        s"Resource.from[${tpe.show}] has unresolved dependencies: $missing. " +
          s"Provided wires produce: $provided. " +
          s"Add wires for the missing dependencies."
      )
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]

    def findWireForType(depType: TypeRepr): Option[(Expr[Wire[?, ?]], TypeRepr)] =
      wireExprs.zip(wireOutTypes).find { case (_, outType) =>
        outType <:< depType
      }

    def generateArgTerm(
      paramType: TypeRepr,
      finalizerExpr: Expr[Finalizer],
      ctxExpr: Expr[Context[?]]
    ): Term =
      if (MacroCore.isFinalizerType(paramType)) {
        finalizerExpr.asTerm
      } else {
        findWireForType(paramType) match {
          case Some((_, outType)) =>
            outType.asType match {
              case '[d] =>
                '{ $ctxExpr.asInstanceOf[Context[d]].get[d](using summonInline[IsNominalType[d]]) }.asTerm
            }
          case None =>
            report.errorAndAbort(s"No wire found for type ${paramType.show}")
        }
      }

    def generateResourceBody(finalizerExpr: Expr[Finalizer], ctxExpr: Expr[Context[?]]): Expr[T] = {
      val ctorSym = tpe.typeSymbol.primaryConstructor

      val argListTerms: List[List[Term]] = paramLists.map { params =>
        params.map { param =>
          val paramType = tpe.memberType(param).dealias.simplified
          generateArgTerm(paramType, finalizerExpr, ctxExpr)
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

    '{
      Resource.shared[T] { finalizer =>
        val ctx = ${ buildOverrideContext('{ finalizer }) }
        ${ generateResourceBody('{ finalizer }, '{ ctx }) }
      }
    }
  }
}
