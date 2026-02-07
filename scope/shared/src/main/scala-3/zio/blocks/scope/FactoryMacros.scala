package zio.blocks.scope

import zio.blocks.scope.internal.MacroCore
import scala.quoted.*

private[scope] object FactoryMacros {

  def deriveFactoryImpl[T: Type](using Quotes): Expr[Factory[T]] = {
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
      paramType <:< TypeRepr.of[Scope]
    }

    // Collect dependencies (non-Scope regular params)
    val depTypes: List[TypeRepr] = allRegularParams.flatMap { param =>
      val paramType = tpe.memberType(param).dealias.simplified
      if (MacroCore.isScopeType(paramType)) None
      else Some(paramType)
    }

    if (depTypes.nonEmpty) {
      report.errorAndAbort(
        s"Factory[${tpe.show}] cannot be derived: ${tpe.show} has dependencies: ${depTypes.map(_.show).mkString(", ")}. " +
          s"Use Wire[${tpe.show}] instead and call .toFactory(...) to resolve dependencies."
      )
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val ctorSym         = sym.primaryConstructor

    // Generate factory body
    if (hasScopeParam) {
      // Constructor takes implicit Scope
      '{
        Factory.shared[T] { scope =>
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
        Factory.shared[T] { scope =>
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
        Factory.shared[T] { _ =>
          ${
            val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
            Apply(ctorTerm, Nil).asExprOf[T]
          }
        }
      }
    }
  }
}
