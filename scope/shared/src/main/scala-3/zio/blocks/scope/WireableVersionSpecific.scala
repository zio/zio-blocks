package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import scala.quoted.*
import scala.compiletime.summonInline

private[scope] trait WireableVersionSpecific {
  inline def from[Impl <: T, T]: Wireable[T] = ${ WireableMacros.fromImpl[Impl, T] }
}

private[scope] object WireableMacros {
  def fromImpl[Impl <: T: Type, T: Type](using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val implTpe = TypeRepr.of[Impl]
    val implSym = implTpe.typeSymbol

    if (!implSym.isClassDef) {
      report.errorAndAbort(s"${implTpe.show} is not a class")
    }

    val ctor = implSym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      report.errorAndAbort(s"${implTpe.show} has no primary constructor")
    }

    val paramLists = ctor.paramSymss

    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams = regularParams.flatten

    val hasScopeParam = implicitParams.flatten.exists { param =>
      val paramType = implTpe.memberType(param)
      paramType <:< TypeRepr.of[Scope.Any]
    }

    val isAutoCloseable = implTpe <:< TypeRepr.of[AutoCloseable]

    val depTypes = allRegularParams.map { param =>
      implTpe.memberType(param)
    }

    depTypes match {
      case Nil =>
        generateWireable0[Impl, T](hasScopeParam, isAutoCloseable)
      case List(dep1Tpe) =>
        dep1Tpe.asType match {
          case '[d1] =>
            generateWireable1[Impl, T, d1](hasScopeParam, isAutoCloseable)
        }
      case List(dep1Tpe, dep2Tpe) =>
        (dep1Tpe.asType, dep2Tpe.asType) match {
          case ('[d1], '[d2]) =>
            generateWireable2[Impl, T, d1, d2](hasScopeParam, isAutoCloseable)
        }
      case _ =>
        report.errorAndAbort(s"Wireable.from supports up to 2 constructor parameters, got ${depTypes.length}")
    }
  }

  private def generateWireable0[Impl: Type, T: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val implTpe = TypeRepr.of[Impl]

    if (hasScopeParam) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
            val instance = ${
              val ctorSym  = implTpe.typeSymbol.primaryConstructor
              val ctor     = Select(New(TypeTree.of[Impl]), ctorSym)
              val scopeRef = '{ scope }.asTerm
              Apply(Apply(ctor, Nil), List(scopeRef)).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else if (isAutoCloseable) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[Any, T] { (scope: Scope.Any) => (_: Context[Any]) =>
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              Apply(ctor, Nil).asExprOf[Impl]
            }
            scope.defer(instance.asInstanceOf[AutoCloseable].close())
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[Any, T] { (_: Scope.Any) => (_: Context[Any]) =>
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              Apply(ctor, Nil).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    }
  }

  private def generateWireable1[Impl: Type, T: Type, D1: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val implTpe = TypeRepr.of[Impl]

    if (hasScopeParam) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val instance = ${
              val ctorSym  = implTpe.typeSymbol.primaryConstructor
              val ctor     = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref  = '{ arg1 }.asTerm
              val scopeRef = '{ scope }.asTerm
              Apply(Apply(ctor, List(arg1Ref)), List(scopeRef)).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else if (isAutoCloseable) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1, T] { (scope: Scope.Any) => (ctx: Context[D1]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref = '{ arg1 }.asTerm
              Apply(ctor, List(arg1Ref)).asExprOf[Impl]
            }
            scope.defer(instance.asInstanceOf[AutoCloseable].close())
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1, T] { (_: Scope.Any) => (ctx: Context[D1]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref = '{ arg1 }.asTerm
              Apply(ctor, List(arg1Ref)).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    }
  }

  private def generateWireable2[Impl: Type, T: Type, D1: Type, D2: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val implTpe = TypeRepr.of[Impl]

    if (hasScopeParam) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
            val instance = ${
              val ctorSym  = implTpe.typeSymbol.primaryConstructor
              val ctor     = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref  = '{ arg1 }.asTerm
              val arg2Ref  = '{ arg2 }.asTerm
              val scopeRef = '{ scope }.asTerm
              Apply(Apply(ctor, List(arg1Ref, arg2Ref)), List(scopeRef)).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else if (isAutoCloseable) {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1 & D2, T] { (scope: Scope.Any) => (ctx: Context[D1 & D2]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref = '{ arg1 }.asTerm
              val arg2Ref = '{ arg2 }.asTerm
              Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[Impl]
            }
            scope.defer(instance.asInstanceOf[AutoCloseable].close())
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    } else {
      '{
        new Wireable[T] {
          def wire: Wire[?, T] = Wire.Shared[D1 & D2, T] { (_: Scope.Any) => (ctx: Context[D1 & D2]) =>
            val arg1     = ctx.get[D1](using summonInline[IsNominalType[D1]])
            val arg2     = ctx.get[D2](using summonInline[IsNominalType[D2]])
            val instance = ${
              val ctorSym = implTpe.typeSymbol.primaryConstructor
              val ctor    = Select(New(TypeTree.of[Impl]), ctorSym)
              val arg1Ref = '{ arg1 }.asTerm
              val arg2Ref = '{ arg2 }.asTerm
              Apply(ctor, List(arg1Ref, arg2Ref)).asExprOf[Impl]
            }
            Context[T](instance.asInstanceOf[T])(using summonInline[IsNominalType[T]])
          }
        }
      }
    }
  }
}
