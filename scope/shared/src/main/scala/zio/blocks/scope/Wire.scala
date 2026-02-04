package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}

sealed trait Wire[-In, +Out]

object Wire {
  final class Shared[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Any => Context[Any] => Context[Any]
  ) extends Wire[In, Out]

  object Shared {
    def apply[In, Out](f: Scope.Any => Context[In] => Context[Out]): Shared[In, Out] =
      new Shared[In, Out](s => ctx => f(s)(ctx.asInstanceOf[Context[In]]).asInstanceOf[Context[Any]])
  }

  final class Unique[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Any => Context[Any] => Context[Any]
  ) extends Wire[In, Out]

  object Unique {
    def apply[In, Out](f: Scope.Any => Context[In] => Context[Out]): Unique[In, Out] =
      new Unique[In, Out](s => ctx => f(s)(ctx.asInstanceOf[Context[In]]).asInstanceOf[Context[Any]])
  }

  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire[Any, T] =
    new Shared[Any, T](_ => _ => Context(t).asInstanceOf[Context[Any]])
}
