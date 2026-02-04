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
        // Extract the In type from the Wireable to preserve it in the result
        // If Wireable.Typed[In, Out] is used, the In type is in the refinement
        val wireableTpe = wireableExpr.asTerm.tpe.widen.dealias

        // Wireable.Typed[In, Out] = Wireable[Out] { type In >: In0 }
        // Look for the refinement that constrains type In
        val inTypeRepr = wireableTpe match {
          case Refinement(_, "In", TypeBounds(lo, _)) =>
            // In a refinement like { type In >: Config }, lo is the concrete type
            lo
          case other =>
            // Fallback: try to get from member type
            val inMember = other.typeSymbol.typeMember("In")
            if (inMember != Symbol.noSymbol) {
              inMember.info match {
                case TypeBounds(lo, hi) if lo =:= hi => lo
                case TypeBounds(_, hi)               => hi
                case t                               => t
              }
            } else TypeRepr.of[Any]
        }

        inTypeRepr.asType match {
          case '[inType] =>
            '{
              $wireableExpr.wire.shared.asInstanceOf[Wire.Shared[inType, T]]
            }
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
        // Extract the In type from the Wireable to preserve it in the result
        // If Wireable.Typed[In, Out] is used, the In type is in the refinement
        val wireableTpe = wireableExpr.asTerm.tpe.widen.dealias

        // Wireable.Typed[In, Out] = Wireable[Out] { type In >: In0 }
        // Look for the refinement that constrains type In
        val inTypeRepr = wireableTpe match {
          case Refinement(_, "In", TypeBounds(lo, _)) =>
            // In a refinement like { type In >: Config }, lo is the concrete type
            lo
          case other =>
            // Fallback: try to get from member type
            val inMember = other.typeSymbol.typeMember("In")
            if (inMember != Symbol.noSymbol) {
              inMember.info match {
                case TypeBounds(lo, hi) if lo =:= hi => lo
                case TypeBounds(_, hi)               => hi
                case t                               => t
              }
            } else TypeRepr.of[Any]
        }

        inTypeRepr.asType match {
          case '[inType] =>
            '{
              $wireableExpr.wire.unique.asInstanceOf[Wire.Unique[inType, T]]
            }
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
        Wire.Shared[Any, T] {
          val scope    = summon[Scope.Has[Any]]
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
        Wire.Shared[Any, T] {
          val scope    = summon[Scope.Has[Any]]
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
        Wire.Shared[Any, T] {
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

    '{
      Wire.Shared[D1, T] {
        val scope = summon[Scope.Has[D1]]
        val arg1  = scope.get[D1]
        ${
          if (hasScopeParam) {
            '{
              val instance = ${
                val ctorSym  = tpe.typeSymbol.primaryConstructor
                val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                val arg1Ref  = '{ arg1 }.asTerm
                val scopeRef = '{ scope }.asTerm
                Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[T]
              }
              Context[T](instance)(using summonInline[IsNominalType[T]])
            }
          } else if (isAutoCloseable) {
            '{
              val instance = ${
                val ctorSym = tpe.typeSymbol.primaryConstructor
                val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                val arg1Ref = '{ arg1 }.asTerm
                Apply(ctor, List(arg1Ref)).asExprOf[T]
              }
              scope.defer(instance.asInstanceOf[AutoCloseable].close())
              Context[T](instance)(using summonInline[IsNominalType[T]])
            }
          } else {
            '{
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
    }
  }

  private def generateSharedWire2[T: Type, D1: Type, D2: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Shared[D1 & D2, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    '{
      Wire.Shared[D1 & D2, T] {
        val scope = summon[Scope.Has[D1 & D2]]
        val arg1  = scope.get[D1]
        val arg2  = scope.get[D2]
        ${
          if (hasScopeParam) {
            '{
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
          } else if (isAutoCloseable) {
            '{
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
          } else {
            '{
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
    }
  }

  private def generateUniqueWire0[T: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Unique[Any, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        Wire.Unique[Any, T] {
          val scope    = summon[Scope.Has[Any]]
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
        Wire.Unique[Any, T] {
          val scope    = summon[Scope.Has[Any]]
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
        Wire.Unique[Any, T] {
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

    '{
      Wire.Unique[D1, T] {
        val scope = summon[Scope.Has[D1]]
        val arg1  = scope.get[D1]
        ${
          if (hasScopeParam) {
            '{
              val instance = ${
                val ctorSym  = tpe.typeSymbol.primaryConstructor
                val ctor     = Select(New(TypeTree.of[T]), ctorSym)
                val arg1Ref  = '{ arg1 }.asTerm
                val scopeRef = '{ scope }.asTerm
                Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[T]
              }
              Context[T](instance)(using summonInline[IsNominalType[T]])
            }
          } else if (isAutoCloseable) {
            '{
              val instance = ${
                val ctorSym = tpe.typeSymbol.primaryConstructor
                val ctor    = Select(New(TypeTree.of[T]), ctorSym)
                val arg1Ref = '{ arg1 }.asTerm
                Apply(ctor, List(arg1Ref)).asExprOf[T]
              }
              scope.defer(instance.asInstanceOf[AutoCloseable].close())
              Context[T](instance)(using summonInline[IsNominalType[T]])
            }
          } else {
            '{
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
    }
  }

  private def generateUniqueWire2[T: Type, D1: Type, D2: Type](hasScopeParam: Boolean, isAutoCloseable: Boolean)(using
    Quotes
  ): Expr[Wire.Unique[D1 & D2, T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    '{
      Wire.Unique[D1 & D2, T] {
        val scope = summon[Scope.Has[D1 & D2]]
        val arg1  = scope.get[D1]
        val arg2  = scope.get[D2]
        ${
          if (hasScopeParam) {
            '{
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
          } else if (isAutoCloseable) {
            '{
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
          } else {
            '{
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
    }
  }

  def injectedImpl[T: Type](
    wiresExpr: Expr[Seq[Wire[?, ?]]],
    scopeExpr: Expr[Scope.Any]
  )(using Quotes): Expr[Scope.Closeable[T, ?]] = {
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
        // Extract the In type from the Wireable refinement
        val wireableTpe = wireableE.asTerm.tpe.widen.dealias
        val inTypeRepr  = wireableTpe match {
          case Refinement(_, "In", TypeBounds(lo, _)) => lo
          case other                                  =>
            val inMember = other.typeSymbol.typeMember("In")
            if (inMember != Symbol.noSymbol) {
              inMember.info match {
                case TypeBounds(lo, hi) if lo =:= hi => lo
                case TypeBounds(_, hi)               => hi
                case t                               => t
              }
            } else TypeRepr.of[Any]
        }

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
