package zio.blocks.scope

import scala.language.experimental.macros
import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  def make(finalizer: Finalizer, ctx: Context[In]): Out

  /**
   * Converts this Wire to a Resource by providing wires for all dependencies.
   *
   * The provided wires must cover all dependencies in the `In` type. A Context
   * is built internally from the wires at resource allocation time.
   *
   * @param wires
   *   wires that provide all required dependencies
   * @return
   *   a Resource that creates Out values
   */
  def toResource(wires: Wire[_, _]*): Resource[Out] = macro WireMacros.toResourceImpl[In, Out]
}

private[scope] trait WireCompanionVersionSpecific

private[scope] trait SharedVersionSpecific {

  def apply[In, Out](f: (Finalizer, Context[In]) => Out): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out](f)

  def fromFunction[In, Out](f: (Finalizer, Context[In]) => Out): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  def apply[In, Out](f: (Finalizer, Context[In]) => Out): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out](f)

  def fromFunction[In, Out](f: (Finalizer, Context[In]) => Out): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out](f)
}

private[scope] object WireMacros {
  import scala.reflect.macros.whitebox

  def toResourceImpl[In: c.WeakTypeTag, Out: c.WeakTypeTag](
    c: whitebox.Context
  )(wires: c.Expr[Wire[_, _]]*): c.Expr[Resource[Out]] = {
    import c.universe._

    val inType  = weakTypeOf[In]
    val outType = weakTypeOf[Out]

    // Get 'self' reference (the Wire instance)
    val selfExpr = c.prefix.tree

    // Flatten intersection type into component types
    def flattenIntersection(tpe: Type): List[Type] =
      tpe.dealias match {
        case RefinedType(parents, _) =>
          parents.flatMap(flattenIntersection)
        case t if t =:= typeOf[Any] => Nil
        case t                      => List(t)
      }

    val requiredDeps = flattenIntersection(inType)

    // If In is Any, no dependencies required
    if (requiredDeps.isEmpty) {
      val result = q"""
        {
          val wire = $selfExpr.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, $outType]]
          if (wire.isShared) {
            _root_.zio.blocks.scope.Resource.shared[$outType] { finalizer =>
              wire.make(finalizer, _root_.zio.blocks.context.Context.empty)
            }
          } else {
            _root_.zio.blocks.scope.Resource.unique[$outType] { finalizer =>
              wire.make(finalizer, _root_.zio.blocks.context.Context.empty)
            }
          }
        }
      """
      c.Expr[Resource[Out]](result)
    } else {
      // Extract output types from wires
      val wireOutTypes: List[Type] = wires.toList.map { wireExpr =>
        val wireTpe = wireExpr.actualType.dealias
        wireTpe.typeArgs match {
          case List(_, outType) => outType.dealias
          case _                =>
            c.abort(c.enclosingPosition, s"Cannot extract output type from wire: ${wireTpe}")
        }
      }

      // Check all deps are covered
      val (_, remainingDeps) = requiredDeps.partition { depType =>
        wireOutTypes.exists(outType => outType <:< depType)
      }

      if (remainingDeps.nonEmpty) {
        val missing  = remainingDeps.mkString(", ")
        val provided = wireOutTypes.mkString(", ")
        c.abort(
          c.enclosingPosition,
          s"Wire.toResource has unresolved dependencies: $missing. " +
            s"Provided wires produce: $provided. " +
            s"Add wires for the missing dependencies."
        )
      }

      // Build Context from wires at runtime, passing accumulated context to each wire
      val buildCtx: Tree = wires.toList
        .zip(wireOutTypes)
        .foldLeft[Tree](q"_root_.zio.blocks.context.Context.empty") { case (ctxExpr, (wireExpr, outType)) =>
          q"""
            {
              val ctx = $ctxExpr
              val wire = ${wireExpr.tree}.asInstanceOf[_root_.zio.blocks.scope.Wire[Any, $outType]]
              val value = wire.make(finalizer, ctx.asInstanceOf[_root_.zio.blocks.context.Context[Any]])
              ctx.add[$outType](value)
            }
          """
        }

      val result = q"""
        {
          val wire = $selfExpr.asInstanceOf[_root_.zio.blocks.scope.Wire[$inType, $outType]]
          if (wire.isShared) {
            _root_.zio.blocks.scope.Resource.shared[$outType] { finalizer =>
              val ctx = $buildCtx.asInstanceOf[_root_.zio.blocks.context.Context[$inType]]
              wire.make(finalizer, ctx)
            }
          } else {
            _root_.zio.blocks.scope.Resource.unique[$outType] { finalizer =>
              val ctx = $buildCtx.asInstanceOf[_root_.zio.blocks.context.Context[$inType]]
              wire.make(finalizer, ctx)
            }
          }
        }
      """
      c.Expr[Resource[Out]](result)
    }
  }
}
