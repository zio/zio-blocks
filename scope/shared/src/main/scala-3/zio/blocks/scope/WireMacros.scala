package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.MacroCore
import scala.compiletime.summonInline
import scala.quoted.*

private[scope] object WireMacros {

  /**
   * Implements Wire#toResource(wires*) by building a Context from the provided
   * wires and calling toResource(Context[In]).
   */
  def toResourceImpl[In: Type, Out: Type](
    wireExpr: Expr[Wire[In, Out]],
    wiresExpr: Expr[Seq[Wire[?, ?]]]
  )(using Quotes): Expr[Resource[Out]] = {
    import quotes.reflect.*

    val inType = TypeRepr.of[In]

    // Extract the required dependency types from the In type (intersection type)
    val requiredDeps: List[TypeRepr] = MacroCore.flattenIntersection(inType)

    // If In is Any, no dependencies required
    if (requiredDeps.isEmpty || (requiredDeps.size == 1 && requiredDeps.head =:= TypeRepr.of[Any])) {
      // No dependencies - just call toResource with empty context
      '{
        $wireExpr.toResource(Context.empty.asInstanceOf[Context[In]])
      }
    } else {
      // Extract wire expressions from varargs
      val wireExprs: List[Expr[Wire[?, ?]]] = wiresExpr match {
        case Varargs(wires) => wires.toList
        case other          =>
          report.errorAndAbort(s"Expected varargs of Wire expressions, got: ${other.show}")
      }

      // Extract output types from each wire
      val wireOutTypes: List[TypeRepr] = wireExprs.map { wireExpr =>
        val wireTpe = wireExpr.asTerm.tpe.widen.dealias.simplified
        wireTpe match {
          case AppliedType(_, List(_, outType)) => outType.dealias.simplified
          case other                            =>
            report.errorAndAbort(s"Cannot extract output type from wire: ${other.show}")
        }
      }

      // Check all deps are covered
      val (_, remainingDeps) = requiredDeps.partition { depType =>
        wireOutTypes.exists(outType => outType <:< depType)
      }

      if (remainingDeps.nonEmpty) {
        val missing  = remainingDeps.map(_.show).mkString(", ")
        val provided = wireOutTypes.map(_.show).mkString(", ")
        report.errorAndAbort(
          s"Wire.toResource has unresolved dependencies: $missing. " +
            s"Provided wires produce: $provided. " +
            s"Add wires for the missing dependencies."
        )
      }

      // Build Context from wires at runtime, passing accumulated context to each wire
      def buildContext(finalizerExpr: Expr[Finalizer]): Expr[Context[In]] = {
        val ctxExpr = wireExprs.zip(wireOutTypes).foldLeft('{ Context.empty }: Expr[Context[?]]) {
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
        '{ $ctxExpr.asInstanceOf[Context[In]] }
      }

      // Check if wire is shared or unique to produce the right resource type
      '{
        val wire = $wireExpr
        if (wire.isShared) {
          Resource.shared[Out] { finalizer =>
            val ctx = ${ buildContext('{ finalizer }) }
            wire.make(finalizer, ctx)
          }
        } else {
          Resource.unique[Out] { finalizer =>
            val ctx = ${ buildContext('{ finalizer }) }
            wire.make(finalizer, ctx)
          }
        }
      }
    }
  }
}
