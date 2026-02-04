package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import scala.quoted.*
import scala.compiletime.summonInline

private[scope] trait WireableVersionSpecific {

  transparent inline def from[T]: Wireable[T] = ${ WireableMacros.fromImpl[T] }
}

private[scope] object WireableMacros {

  def fromImpl[T: Type](using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef) {
      report.errorAndAbort(s"${tpe.show} is not a class")
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      report.errorAndAbort(s"${tpe.show} has no primary constructor")
    }

    val paramLists = ctor.paramSymss

    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(p => !p.flags.is(Flags.Given) && !p.flags.is(Flags.Implicit))
    }

    val allRegularParams  = regularParams.flatten
    val allImplicitParams = implicitParams.flatten

    val regularDepTypes = allRegularParams.map(param => tpe.memberType(param))

    val scopeHasTypes = allImplicitParams.flatMap { param =>
      val paramType = tpe.memberType(param)
      extractScopeHasType(paramType)
    }

    val hasScopeParam = allImplicitParams.exists { param =>
      val paramType = tpe.memberType(param)
      paramType <:< TypeRepr.of[Scope.Any]
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]

    val allDepTypes = regularDepTypes ++ scopeHasTypes

    allDepTypes match {
      case Nil =>
        generateWireable0[T](hasScopeParam, isAutoCloseable)
      case List(dep1Tpe) =>
        dep1Tpe.asType match {
          case '[d1] =>
            generateWireable1[T, d1](hasScopeParam, isAutoCloseable)
        }
      case List(dep1Tpe, dep2Tpe) =>
        (dep1Tpe.asType, dep2Tpe.asType) match {
          case ('[d1], '[d2]) =>
            generateWireable2[T, d1, d2](hasScopeParam, isAutoCloseable)
        }
      case _ =>
        report.errorAndAbort(s"Wireable.from supports up to 2 dependencies, got ${allDepTypes.length}")
    }
  }

  private def extractScopeHasType(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    val scopeTypeRepr = TypeRepr.of[Scope[?]]
    if (tpe <:< scopeTypeRepr && !(tpe <:< TypeRepr.of[Scope[TNil]])) {
      tpe match {
        case AppliedType(_, List(stackType)) =>
          stackType match {
            case AppliedType(cons, List(contextType, _)) if cons.typeSymbol.name == "::" =>
              contextType match {
                case AppliedType(_, List(innerType)) =>
                  if (innerType =:= TypeRepr.of[scala.Any]) None
                  else Some(innerType)
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    } else {
      None
    }
  }

  private def generateWireable0[T: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    if (hasScopeParam) {
      '{
        new Wireable[T] {
          type In = Any
          def wire: Wire[Any, T] = Wire.Shared[Any, T] {
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
      }
    } else if (isAutoCloseable) {
      '{
        new Wireable[T] {
          type In = Any
          def wire: Wire[Any, T] = Wire.Shared[Any, T] {
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
      }
    } else {
      '{
        new Wireable[T] {
          type In = Any
          def wire: Wire[Any, T] = Wire.Shared[Any, T] {
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
  }

  private def generateWireable1[T: Type, D1: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    '{
      new Wireable[T] {
        type In = D1

        def wire: Wire[D1, T] = Wire.Shared[D1, T] {
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
  }

  private def generateWireable2[T: Type, D1: Type, D2: Type](
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  )(using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    '{
      new Wireable[T] {
        type In = D1 & D2

        def wire: Wire[D1 & D2, T] = Wire.Shared[D1 & D2, T] {
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
  }
}
