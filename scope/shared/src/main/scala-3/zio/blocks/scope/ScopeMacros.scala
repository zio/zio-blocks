package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.{Finalizers, MacroCore, WireCodeGen}
import zio.blocks.scope.internal.WireCodeGen.WireKind
import scala.quoted.*
import scala.compiletime.summonInline

private[scope] object ScopeMacros {

  // ─────────────────────────────────────────────────────────────────────────
  // shared[T] / unique[T] implementations
  // ─────────────────────────────────────────────────────────────────────────

  def sharedImpl[T: Type](using Quotes): Expr[Wire.Shared[?, T]] = {
    import quotes.reflect.*

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        WireCodeGen.wireFromWireable(wireableExpr, WireKind.Shared).asExprOf[Wire.Shared[?, T]]

      case None =>
        val tpe = TypeRepr.of[T]
        val sym = tpe.typeSymbol

        if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
          MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
        }

        val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Shared)
        wireExpr.asExprOf[Wire.Shared[?, T]]
    }
  }

  def uniqueImpl[T: Type](using Quotes): Expr[Wire.Unique[?, T]] = {
    import quotes.reflect.*

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        WireCodeGen.wireFromWireable(wireableExpr, WireKind.Unique).asExprOf[Wire.Unique[?, T]]

      case None =>
        val tpe = TypeRepr.of[T]
        val sym = tpe.typeSymbol

        if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
          MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
        }

        val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Unique)
        wireExpr.asExprOf[Wire.Unique[?, T]]
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // injected[T] implementations
  // ─────────────────────────────────────────────────────────────────────────

  def injectedImpl[T: Type](
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[T, ?]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    val wireableOpt = Expr.summon[Wireable[T]]

    if (!sym.isClassDef && wireableOpt.isEmpty) {
      MacroCore.abort(
        MacroCore.ScopeMacroError.NotAClass(tpe.show)
      )
    }

    val ctor = if (sym.isClassDef) sym.primaryConstructor else Symbol.noSymbol

    val (allRegularParams, hasScopeParam, isAutoCloseable) = if (ctor != Symbol.noSymbol) {
      val paramLists                      = ctor.paramSymss
      val (regularParams, implicitParams) = paramLists.partition { params =>
        params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
      }
      val hasScope = implicitParams.flatten.exists { param =>
        val paramType = tpe.memberType(param)
        paramType <:< TypeRepr.of[Scope.Any]
      }
      val isAC = tpe <:< TypeRepr.of[AutoCloseable]
      (regularParams.flatten, hasScope, isAC)
    } else {
      (Nil, false, false)
    }

    val depTypes = allRegularParams.map(param => tpe.memberType(param))

    wireableOpt match {
      case Some(wireableE) if depTypes.isEmpty =>
        // Use Wireable directly
        val wireableTpe = wireableE.asTerm.tpe
        val inTypeRepr  = MacroCore.extractWireableInType(wireableTpe)

        inTypeRepr.asType match {
          case '[inType] =>
            '{
              val parentScope = $scopeExpr
              val finalizers  = new Finalizers
              val w           = $wireableE.wire.asInstanceOf[Wire.Shared[inType, T]]
              val childScope  =
                Scope.makeCloseable[inType, TNil](parentScope, Context.empty.asInstanceOf[Context[inType]], finalizers)
              val ctx = w.construct(using childScope.asInstanceOf[Scope.Has[inType]])
              Scope.makeCloseable(parentScope, ctx, finalizers)
            }
        }

      case _ =>
        // Derive from constructor
        depTypes match {
          case Nil =>
            generateInjected0[T](hasScopeParam, isAutoCloseable, scopeExpr)
          case List(dep1Tpe) =>
            dep1Tpe.asType match {
              case '[d1] =>
                generateInjected1[T, d1](hasScopeParam, isAutoCloseable, wiresExpr, scopeExpr)
            }
          case List(dep1Tpe, dep2Tpe) =>
            (dep1Tpe.asType, dep2Tpe.asType) match {
              case ('[d1], '[d2]) =>
                generateInjected2[T, d1, d2](hasScopeParam, isAutoCloseable, wiresExpr, scopeExpr)
            }
          case _ =>
            MacroCore.abort(
              MacroCore.ScopeMacroError.TooManyParams("injected", tpe.show, depTypes.length, 2)
            )
        }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // injected[T] code generation helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def generateInjected0[T: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[T, ?]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        val parentScope = $scopeExpr
        val finalizers  = new Finalizers
        val instance    = ${
          val ctorSym  = tpe.typeSymbol.primaryConstructor
          val ctor     = Select(New(TypeTree.of[T]), ctorSym)
          val scopeRef = '{ parentScope }.asTerm
          Apply(Apply(ctor, Nil), List(scopeRef)).asExprOf[T]
        }
        val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
        Scope.makeCloseable(parentScope, ctx, finalizers)
      }
    } else if (isAutoCloseable) {
      '{
        val parentScope = $scopeExpr
        val finalizers  = new Finalizers
        val instance    = ${
          val ctorSym = tpe.typeSymbol.primaryConstructor
          val ctor    = Select(New(TypeTree.of[T]), ctorSym)
          Apply(ctor, Nil).asExprOf[T]
        }
        finalizers.add(instance.asInstanceOf[AutoCloseable].close())
        val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
        Scope.makeCloseable(parentScope, ctx, finalizers)
      }
    } else {
      '{
        val parentScope = $scopeExpr
        val finalizers  = new Finalizers
        val instance    = ${
          val ctorSym = tpe.typeSymbol.primaryConstructor
          val ctor    = Select(New(TypeTree.of[T]), ctorSym)
          Apply(ctor, Nil).asExprOf[T]
        }
        val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
        Scope.makeCloseable(parentScope, ctx, finalizers)
      }
    }
  }

  private def generateInjected1[T: Type, D1: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[T, ?]] = {
    import quotes.reflect.*
    val tpe      = TypeRepr.of[T]
    val dep1Name = TypeRepr.of[D1].show

    '{
      val parentScope = $scopeExpr
      val finalizers  = new Finalizers
      val wires       = $wiresExpr

      val wire1 = wires.headOption.getOrElse {
        throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep1Name) })
      }.asInstanceOf[Wire.Shared[Any, D1]]

      val depCtx    = Context.empty.asInstanceOf[Context[Any]]
      val dep1Scope = Scope.makeCloseable[Any, TNil](parentScope, depCtx, finalizers)
      val dep1Ctx   = wire1.constructFn(dep1Scope.asInstanceOf[Scope.Has[Any]])
      val arg1      = dep1Ctx.get[D1](using summonInline[IsNominalType[D1]])

      val instance = ${
        if (hasScopeParam) {
          val ctorSym  = tpe.typeSymbol.primaryConstructor
          val ctor     = Select(New(TypeTree.of[T]), ctorSym)
          val arg1Ref  = '{ arg1 }.asTerm
          val scopeRef = '{ parentScope }.asTerm
          Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[T]
        } else {
          val ctorSym = tpe.typeSymbol.primaryConstructor
          val ctor    = Select(New(TypeTree.of[T]), ctorSym)
          val arg1Ref = '{ arg1 }.asTerm
          Apply(ctor, List(arg1Ref)).asExprOf[T]
        }
      }

      ${
        if (isAutoCloseable && !hasScopeParam) {
          '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
        } else {
          '{ () }
        }
      }

      val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
      Scope.makeCloseable(parentScope, ctx, finalizers)
    }
  }

  private def generateInjected2[T: Type, D1: Type, D2: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[T, ?]] = {
    import quotes.reflect.*
    val tpe      = TypeRepr.of[T]
    val dep1Name = TypeRepr.of[D1].show
    val dep2Name = TypeRepr.of[D2].show

    '{
      val parentScope = $scopeExpr
      val finalizers  = new Finalizers
      val wires       = $wiresExpr

      val wire1 = wires
        .lift(0)
        .getOrElse {
          throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep1Name) })
        }
        .asInstanceOf[Wire.Shared[Any, D1]]
      val wire2 = wires
        .lift(1)
        .getOrElse {
          throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep2Name) })
        }
        .asInstanceOf[Wire.Shared[Any, D2]]

      val depCtx    = Context.empty.asInstanceOf[Context[Any]]
      val dep1Scope = Scope.makeCloseable[Any, TNil](parentScope, depCtx, finalizers)
      val dep1Ctx   = wire1.constructFn(dep1Scope.asInstanceOf[Scope.Has[Any]])
      val arg1      = dep1Ctx.get[D1](using summonInline[IsNominalType[D1]])

      val dep2Scope = Scope.makeCloseable[D1, TNil](parentScope, dep1Ctx.asInstanceOf[Context[D1]], finalizers)
      val dep2Ctx   = wire2.constructFn(dep2Scope.asInstanceOf[Scope.Has[Any]])
      val arg2      = dep2Ctx.get[D2](using summonInline[IsNominalType[D2]])

      val instance = ${
        if (hasScopeParam) {
          val ctorSym  = tpe.typeSymbol.primaryConstructor
          val ctor     = Select(New(TypeTree.of[T]), ctorSym)
          val arg1Ref  = '{ arg1 }.asTerm
          val arg2Ref  = '{ arg2 }.asTerm
          val scopeRef = '{ parentScope }.asTerm
          Apply(Apply(ctor, List(arg1Ref, arg2Ref)), List(scopeRef)).asExprOf[T]
        } else {
          val ctorSym = tpe.typeSymbol.primaryConstructor
          val ctor    = Select(New(TypeTree.of[T]), ctorSym)
          val arg1Ref = '{ arg1 }.asTerm
          val arg2Ref = '{ arg2 }.asTerm
          Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[T]
        }
      }

      ${
        if (isAutoCloseable && !hasScopeParam) {
          '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
        } else {
          '{ () }
        }
      }

      val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
      Scope.makeCloseable(parentScope, ctx, finalizers)
    }
  }
}
