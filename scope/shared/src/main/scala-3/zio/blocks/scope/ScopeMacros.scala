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
        // Derive from constructor - supports arbitrary number of parameters
        generateInjectedN[T](allRegularParams, hasScopeParam, isAutoCloseable, wiresExpr, scopeExpr)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // injected[T] code generation - supports arbitrary arity
  // ─────────────────────────────────────────────────────────────────────────

  private def generateInjectedN[T: Type](using
    Quotes
  )(
    allRegularParams: List[quotes.reflect.Symbol],
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  ): Expr[Scope.Closeable[T, ?]] = {
    import quotes.reflect.*

    val tpe      = TypeRepr.of[T]
    val depTypes = allRegularParams.map(param => tpe.memberType(param))

    if (depTypes.isEmpty) {
      // No dependencies - just construct
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
    } else {
      // Has dependencies - generate code to extract all deps and construct
      // Build a Block with all wire extractions, then the constructor call
      val depNameExprs: List[Expr[String]] = depTypes.map(dt => Expr(dt.show))

      // Generate helper to create argument extraction expression
      def generateArgExpr(depType: TypeRepr, idx: Int, depName: Expr[String]): Expr[Any] = {
        val idxExpr = Expr(idx)
        depType.asType match {
          case '[d] =>
            '{
              val wire = $wiresExpr
                .lift($idxExpr)
                .getOrElse {
                  throw new IllegalStateException("Missing wire for dependency: " + $depName)
                }
                .asInstanceOf[Wire.Shared[Any, d]]
              val depCtx   = Context.empty.asInstanceOf[Context[Any]]
              val depScope = Scope.makeCloseable[Any, TNil]($scopeExpr, depCtx, new Finalizers)
              val ctx      = wire.constructFn(depScope.asInstanceOf[Scope.Has[Any]])
              ctx.get[d](using summonInline[IsNominalType[d]])
            }
        }
      }

      // Generate all argument expressions
      val argExprs: List[Expr[Any]] = depTypes.zipWithIndex.zip(depNameExprs).map { case ((dt, idx), name) =>
        generateArgExpr(dt, idx, name)
      }

      // Now create the final expression that evaluates args and constructs
      depTypes.length match {
        case 1 =>
          val arg0Expr = argExprs(0)
          depTypes(0).asType match {
            case '[d0] =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ arg0Expr.asExprOf[d0] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val arg0Ref  = '{ arg0 }.asTerm
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(Apply(ctor, List(arg0Ref)), List(scopeRef)).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    val arg0Ref = '{ arg0 }.asTerm
                    Apply(ctor, List(arg0Ref)).asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case 2 =>
          (depTypes(0).asType, depTypes(1).asType) match {
            case ('[d0], '[d1]) =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ argExprs(0).asExprOf[d0] }
                val arg1        = ${ argExprs(1).asExprOf[d1] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm)), List(scopeRef)).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm)).asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case 3 =>
          (depTypes(0).asType, depTypes(1).asType, depTypes(2).asType) match {
            case ('[d0], '[d1], '[d2]) =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ argExprs(0).asExprOf[d0] }
                val arg1        = ${ argExprs(1).asExprOf[d1] }
                val arg2        = ${ argExprs(2).asExprOf[d2] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(
                      Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm)),
                      List(scopeRef)
                    ).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm)).asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case 4 =>
          (depTypes(0).asType, depTypes(1).asType, depTypes(2).asType, depTypes(3).asType) match {
            case ('[d0], '[d1], '[d2], '[d3]) =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ argExprs(0).asExprOf[d0] }
                val arg1        = ${ argExprs(1).asExprOf[d1] }
                val arg2        = ${ argExprs(2).asExprOf[d2] }
                val arg3        = ${ argExprs(3).asExprOf[d3] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(
                      Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm, '{ arg3 }.asTerm)),
                      List(scopeRef)
                    ).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    Apply(ctor, List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm, '{ arg3 }.asTerm))
                      .asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case 5 =>
          (
            depTypes(0).asType,
            depTypes(1).asType,
            depTypes(2).asType,
            depTypes(3).asType,
            depTypes(4).asType
          ) match {
            case ('[d0], '[d1], '[d2], '[d3], '[d4]) =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ argExprs(0).asExprOf[d0] }
                val arg1        = ${ argExprs(1).asExprOf[d1] }
                val arg2        = ${ argExprs(2).asExprOf[d2] }
                val arg3        = ${ argExprs(3).asExprOf[d3] }
                val arg4        = ${ argExprs(4).asExprOf[d4] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(
                      Apply(
                        ctor,
                        List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm, '{ arg3 }.asTerm, '{ arg4 }.asTerm)
                      ),
                      List(scopeRef)
                    ).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    Apply(
                      ctor,
                      List('{ arg0 }.asTerm, '{ arg1 }.asTerm, '{ arg2 }.asTerm, '{ arg3 }.asTerm, '{ arg4 }.asTerm)
                    ).asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case 6 =>
          (
            depTypes(0).asType,
            depTypes(1).asType,
            depTypes(2).asType,
            depTypes(3).asType,
            depTypes(4).asType,
            depTypes(5).asType
          ) match {
            case ('[d0], '[d1], '[d2], '[d3], '[d4], '[d5]) =>
              '{
                val parentScope = $scopeExpr
                val finalizers  = new Finalizers
                val arg0        = ${ argExprs(0).asExprOf[d0] }
                val arg1        = ${ argExprs(1).asExprOf[d1] }
                val arg2        = ${ argExprs(2).asExprOf[d2] }
                val arg3        = ${ argExprs(3).asExprOf[d3] }
                val arg4        = ${ argExprs(4).asExprOf[d4] }
                val arg5        = ${ argExprs(5).asExprOf[d5] }
                val instance    = ${
                  if (hasScopeParam) {
                    val ctorSym  = tpe.typeSymbol.primaryConstructor
                    val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                    val scopeRef = '{ parentScope }.asTerm
                    Apply(
                      Apply(
                        ctor,
                        List(
                          '{ arg0 }.asTerm,
                          '{ arg1 }.asTerm,
                          '{ arg2 }.asTerm,
                          '{ arg3 }.asTerm,
                          '{ arg4 }.asTerm,
                          '{ arg5 }.asTerm
                        )
                      ),
                      List(scopeRef)
                    ).asExprOf[T]
                  } else {
                    val ctorSym = tpe.typeSymbol.primaryConstructor
                    val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                    Apply(
                      ctor,
                      List(
                        '{ arg0 }.asTerm,
                        '{ arg1 }.asTerm,
                        '{ arg2 }.asTerm,
                        '{ arg3 }.asTerm,
                        '{ arg4 }.asTerm,
                        '{ arg5 }.asTerm
                      )
                    ).asExprOf[T]
                  }
                }
                ${
                  if (isAutoCloseable && !hasScopeParam)
                    '{ finalizers.add(instance.asInstanceOf[AutoCloseable].close()) }
                  else '{ () }
                }
                val ctx = Context[T](instance)(using summonInline[IsNominalType[T]])
                Scope.makeCloseable(parentScope, ctx, finalizers)
              }
          }

        case n =>
          // For 7+ parameters, provide a compile-time error with helpful suggestion
          report.errorAndAbort(
            s"injected[${tpe.show}] has $n constructor parameters. " +
              s"Currently, up to 6 parameters are supported. " +
              s"For types with more parameters, use Wireable.from[${tpe.show}] to provide a custom construction strategy."
          )
      }
    }
  }
}
