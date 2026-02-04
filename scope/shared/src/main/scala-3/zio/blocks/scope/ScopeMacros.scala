package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers
import scala.quoted.*
import scala.compiletime.summonInline

private[scope] object ScopeMacros {

  def sharedImpl[T: Type](using Quotes): Expr[Wire.Shared[?, T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        '{
          $wireableExpr.wire.asInstanceOf[Wire.Shared[?, T]]
        }
      case None =>
        if (!sym.isClassDef) {
          report.errorAndAbort(
            s"Cannot derive Wire for ${tpe.show}: not a class. " +
              s"Provide a Wireable[${tpe.show}] instance or use Wire.Shared directly."
          )
        }
        deriveSharedWire[T]
    }
  }

  def uniqueImpl[T: Type](using Quotes): Expr[Wire.Unique[?, T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        '{
          val w = $wireableExpr.wire
          Wire.Unique[Any, T]((scope: Scope.Any) =>
            (ctx: Context[Any]) => w.asInstanceOf[Wire.Shared[Any, T]].constructFn(scope)(ctx).asInstanceOf[Context[T]]
          )
        }
      case None =>
        if (!sym.isClassDef) {
          report.errorAndAbort(
            s"Cannot derive Wire for ${tpe.show}: not a class. " +
              s"Provide a Wireable[${tpe.show}] instance or use Wire.Unique directly."
          )
        }
        deriveUniqueWire[T]
    }
  }

  private def deriveSharedWire[T: Type](using Quotes): Expr[Wire.Shared[?, T]] = {
    import quotes.reflect.*

    val tpe  = TypeRepr.of[T]
    val sym  = tpe.typeSymbol
    val ctor = sym.primaryConstructor

    if (ctor == Symbol.noSymbol) {
      report.errorAndAbort(s"${tpe.show} has no primary constructor")
    }

    val paramLists                      = ctor.paramSymss
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten
    val hasScopeParam    = implicitParams.flatten.exists { param =>
      val paramType = tpe.memberType(param)
      paramType <:< TypeRepr.of[Scope.Any]
    }
    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val depTypes        = allRegularParams.map(param => tpe.memberType(param))

    depTypes match {
      case Nil =>
        generateSharedWire0[T](hasScopeParam, isAutoCloseable)
      case List(dep1Tpe) =>
        dep1Tpe.asType match {
          case '[d1] =>
            generateSharedWire1[T, d1](hasScopeParam, isAutoCloseable)
        }
      case List(dep1Tpe, dep2Tpe) =>
        (dep1Tpe.asType, dep2Tpe.asType) match {
          case ('[d1], '[d2]) =>
            generateSharedWire2[T, d1, d2](hasScopeParam, isAutoCloseable)
        }
      case _ =>
        report.errorAndAbort(s"shared[T] supports up to 2 constructor parameters, got ${depTypes.length}")
    }
  }

  private def deriveUniqueWire[T: Type](using Quotes): Expr[Wire.Unique[?, T]] = {
    import quotes.reflect.*

    val tpe  = TypeRepr.of[T]
    val sym  = tpe.typeSymbol
    val ctor = sym.primaryConstructor

    if (ctor == Symbol.noSymbol) {
      report.errorAndAbort(s"${tpe.show} has no primary constructor")
    }

    val paramLists                      = ctor.paramSymss
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten
    val hasScopeParam    = implicitParams.flatten.exists { param =>
      val paramType = tpe.memberType(param)
      paramType <:< TypeRepr.of[Scope.Any]
    }
    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val depTypes        = allRegularParams.map(param => tpe.memberType(param))

    depTypes match {
      case Nil =>
        generateUniqueWire0[T](hasScopeParam, isAutoCloseable)
      case List(dep1Tpe) =>
        dep1Tpe.asType match {
          case '[d1] =>
            generateUniqueWire1[T, d1](hasScopeParam, isAutoCloseable)
        }
      case List(dep1Tpe, dep2Tpe) =>
        (dep1Tpe.asType, dep2Tpe.asType) match {
          case ('[d1], '[d2]) =>
            generateUniqueWire2[T, d1, d2](hasScopeParam, isAutoCloseable)
        }
      case _ =>
        report.errorAndAbort(s"unique[T] supports up to 2 constructor parameters, got ${depTypes.length}")
    }
  }

  private def generateSharedWire0[T: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Shared[Any, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Shared[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, Nil), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Shared[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctor, Nil).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Shared[Any, T] { (_: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctor, Nil).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  private def generateSharedWire1[T: Type, D1: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Shared[D1, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Shared[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref  = '{ arg1 }.asTerm
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Shared[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            Apply(ctor, List(arg1Ref)).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Shared[D1, T] { (_: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            Apply(ctor, List(arg1Ref)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  private def generateSharedWire2[T: Type, D1: Type, D2: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Shared[D1 & D2, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Shared[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref  = '{ arg1 }.asTerm
            val arg2Ref  = '{ arg2 }.asTerm
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, List(arg1Ref, arg2Ref)), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Shared[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            val arg2Ref = '{ arg2 }.asTerm
            Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Shared[D1 & D2, T] { (_: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            val arg2Ref = '{ arg2 }.asTerm
            Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  private def generateUniqueWire0[T: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Unique[Any, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Unique[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, Nil), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Unique[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctor, Nil).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Unique[Any, T] { (_: Scope.Any) => (_: Context[Any]) =>
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctor, Nil).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  private def generateUniqueWire1[T: Type, D1: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Unique[D1, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Unique[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref  = '{ arg1 }.asTerm
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Unique[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            Apply(ctor, List(arg1Ref)).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Unique[D1, T] { (_: Scope.Any) => (ctx: Context[D1]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            Apply(ctor, List(arg1Ref)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  private def generateUniqueWire2[T: Type, D1: Type, D2: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Unique[D1 & D2, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Unique[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym  = tpe.typeSymbol.primaryConstructor
            val ctor     = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref  = '{ arg1 }.asTerm
            val arg2Ref  = '{ arg2 }.asTerm
            val scopeRef = '{ scope }.asTerm
            Apply(Apply(ctor, List(arg1Ref, arg2Ref)), List(scopeRef)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else if (isAutoCloseable) {
      '{
        Wire.Unique[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            val arg2Ref = '{ arg2 }.asTerm
            Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[T]
          }
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    } else {
      '{
        Wire.Unique[D1 & D2, T] { (_: Scope.Any) => (ctx: Context[D1 & D2]) =>
          val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
          val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
          val instance = ${
            val ctorSym = tpe.typeSymbol.primaryConstructor
            val ctor    = Select(New(TypeTree.of[T]), ctorSym)
            val arg1Ref = '{ arg1 }.asTerm
            val arg2Ref = '{ arg2 }.asTerm
            Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[T]
          }
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }
  }

  def injectedImpl[T: Type](
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[Context[T] :: ?]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    val wireableOpt = Expr.summon[Wireable[T]]

    if (!sym.isClassDef && wireableOpt.isEmpty) {
      report.errorAndAbort(
        s"Cannot inject ${tpe.show}: not a class and no Wireable[${tpe.show}] available. " +
          "Either use a concrete class or provide a Wireable instance."
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
        '{
          val parentScope = $scopeExpr
          val finalizers  = new Finalizers
          val w           = $wireableE.wire.asInstanceOf[Wire.Shared[Any, T]]
          val ctx         = w.constructFn(parentScope)(Context.empty.asInstanceOf[Context[Any]]).asInstanceOf[Context[T]]
          Scope.makeCloseable(parentScope, ctx, finalizers)
        }
      case _ =>
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
            report.errorAndAbort(s"injected[T] supports up to 2 constructor parameters, got ${depTypes.length}")
        }
    }
  }

  private def generateInjected0[T: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean,
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[Context[T] :: ?]] = {
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
  )(using Quotes): Expr[Scope.Closeable[Context[T] :: ?]] = {
    import quotes.reflect.*
    val tpe      = TypeRepr.of[T]
    val dep1Name = TypeRepr.of[D1].show

    '{
      val parentScope = $scopeExpr
      val finalizers  = new Finalizers
      val wires       = $wiresExpr

      val wire1 = wires.headOption.map(_.asInstanceOf[Wire.Shared[Any, D1]]).getOrElse {
        throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep1Name) })
      }

      val dep1Ctx = wire1.constructFn(parentScope)(Context.empty.asInstanceOf[Context[Any]])
      val arg1    = dep1Ctx.asInstanceOf[Context[D1]].get[D1](using summonInline[IsNominalType[D1]])

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
  )(using Quotes): Expr[Scope.Closeable[Context[T] :: ?]] = {
    import quotes.reflect.*
    val tpe      = TypeRepr.of[T]
    val dep1Name = TypeRepr.of[D1].show
    val dep2Name = TypeRepr.of[D2].show

    '{
      val parentScope = $scopeExpr
      val finalizers  = new Finalizers
      val wires       = $wiresExpr

      val wire1 = wires.lift(0).map(_.asInstanceOf[Wire.Shared[Any, D1]]).getOrElse {
        throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep1Name) })
      }
      val wire2 = wires.lift(1).map(_.asInstanceOf[Wire.Shared[Any, D2]]).getOrElse {
        throw new IllegalStateException(s"Missing wire for dependency: " + ${ Expr(dep2Name) })
      }

      val dep1Ctx = wire1.constructFn(parentScope)(Context.empty.asInstanceOf[Context[Any]])
      val arg1    = dep1Ctx.asInstanceOf[Context[D1]].get[D1](using summonInline[IsNominalType[D1]])

      val dep2Ctx = wire2.constructFn(parentScope)(dep1Ctx)
      val arg2    = dep2Ctx.asInstanceOf[Context[D2]].get[D2](using summonInline[IsNominalType[D2]])

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
