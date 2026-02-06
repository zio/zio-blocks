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
                Scope.makeCloseable[inType, Scope](parentScope, Context.empty.asInstanceOf[Context[inType]], finalizers)
              val ctx = w.construct(using childScope)
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
      // Has dependencies - use Block-based code generation for arbitrary arity
      // This approach generates val definitions for each arg and uses foldLeft
      // to build the constructor call, similar to how WireCodeGen handles it.

      val parentScopeSym = Symbol.newVal(
        Symbol.spliceOwner,
        "parentScope",
        TypeRepr.of[Scope.Any],
        Flags.EmptyFlags,
        Symbol.noSymbol
      )
      val finalizersSym = Symbol.newVal(
        Symbol.spliceOwner,
        "finalizers",
        TypeRepr.of[Finalizers],
        Flags.EmptyFlags,
        Symbol.noSymbol
      )
      val wiresSeqSym = Symbol.newVal(
        Symbol.spliceOwner,
        "wiresSeq",
        TypeRepr.of[Seq[Wire[?, ?]]],
        Flags.EmptyFlags,
        Symbol.noSymbol
      )

      // Generate symbols and val definitions for each dependency argument
      val argSymsAndDefs: List[(Symbol, TypeRepr, ValDef)] = depTypes.zipWithIndex.map { case (depType, idx) =>
        val argSym = Symbol.newVal(
          Symbol.spliceOwner,
          s"arg$idx",
          depType,
          Flags.EmptyFlags,
          Symbol.noSymbol
        )

        val depName   = depType.show
        val idxLit    = Literal(IntConstant(idx))
        val wiresRef  = Ref(wiresSeqSym)
        val parentRef = Ref(parentScopeSym)

        // Build wire extraction expression:
        // val argN = {
        //   val wire = wiresSeq.lift(idx).getOrElse(...).asInstanceOf[Wire.Shared[Any, DepType]]
        //   val depScope = Scope.makeCloseable[Any, TNil](parentScope, Context.empty.asInstanceOf[Context[Any]], new Finalizers)
        //   val ctx = wire.constructFn(depScope.asInstanceOf[Scope.Has[Any]])
        //   ctx.get[DepType]
        // }
        val argRhs = depType.asType match {
          case '[d] =>
            '{
              val wire = ${
                wiresRef.asExprOf[Seq[Wire[?, ?]]]
              }
                .lift(${ idxLit.asExprOf[Int] })
                .getOrElse {
                  throw new IllegalStateException("Missing wire for dependency: " + ${ Expr(depName) })
                }
                .asInstanceOf[Wire.Shared[Any, d]]
              val depScope = Scope.makeCloseable[Any, Scope](
                ${ parentRef.asExprOf[Scope.Any] },
                Context.empty.asInstanceOf[Context[Any]],
                new Finalizers
              )
              val ctx = wire.constructFn(depScope)
              ctx.get[d](using summonInline[IsNominalType[d]])
            }.asTerm
        }

        val valDef = ValDef(argSym, Some(argRhs))
        (argSym, depType, valDef)
      }

      // Build the constructor call using foldLeft (like WireCodeGen)
      val ctorSym   = tpe.typeSymbol.primaryConstructor
      val ctorTerm  = Select(New(TypeTree.of[T]), ctorSym)
      val argTerms  = argSymsAndDefs.map { case (sym, _, _) => Ref(sym) }
      val parentRef = Ref(parentScopeSym)
      val finsRef   = Ref(finalizersSym)

      val ctorApplied = if (hasScopeParam) {
        Apply(Apply(ctorTerm, argTerms), List(parentRef))
      } else {
        Apply(ctorTerm, argTerms)
      }

      val instanceSym = Symbol.newVal(
        Symbol.spliceOwner,
        "instance",
        tpe,
        Flags.EmptyFlags,
        Symbol.noSymbol
      )
      val instanceDef = ValDef(instanceSym, Some(ctorApplied))
      val instanceRef = Ref(instanceSym)

      // Optionally register AutoCloseable cleanup
      val cleanupStmt: Option[Term] =
        if (isAutoCloseable && !hasScopeParam) {
          Some(
            '{
              ${ finsRef.asExprOf[Finalizers] }.add(
                ${ instanceRef.asExprOf[Any] }.asInstanceOf[AutoCloseable].close()
              )
            }.asTerm
          )
        } else None

      // Build context creation
      val ctxSym = Symbol.newVal(
        Symbol.spliceOwner,
        "ctx",
        TypeRepr.of[Context[T]],
        Flags.EmptyFlags,
        Symbol.noSymbol
      )
      val ctxRhs = '{
        Context[T](${ instanceRef.asExprOf[T] })(using summonInline[IsNominalType[T]])
      }.asTerm
      val ctxDef = ValDef(ctxSym, Some(ctxRhs))
      val ctxRef = Ref(ctxSym)

      // Build final return expression
      val result = '{
        Scope.makeCloseable[T, Scope](
          ${ parentRef.asExprOf[Scope] },
          ${ ctxRef.asExprOf[Context[T]] },
          ${ finsRef.asExprOf[Finalizers] }
        )
      }.asTerm

      // Assemble all statements in a Block
      val parentScopeDef = ValDef(parentScopeSym, Some(scopeExpr.asTerm))
      val finalizersDef  = ValDef(finalizersSym, Some('{ new Finalizers }.asTerm))
      val wiresSeqDef    = ValDef(wiresSeqSym, Some(wiresExpr.asTerm))

      val allValDefs = argSymsAndDefs.map(_._3)
      val allStmts   = List(parentScopeDef, finalizersDef, wiresSeqDef) ++
        allValDefs ++
        List(instanceDef) ++
        cleanupStmt.toList ++
        List(ctxDef)

      Block(allStmts, result).asExprOf[Scope.Closeable[T, ?]]
    }
  }
}
