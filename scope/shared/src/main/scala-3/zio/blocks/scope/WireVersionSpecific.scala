package zio.blocks.scope

import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  def construct(using scope: Scope.Has[In]): Context[Out]
}

private[scope] trait WireCompanionVersionSpecific

private[scope] trait SharedVersionSpecific {

  def apply[In, Out](f: Scope.Has[In] ?=> Context[Out]): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out]((scope: Scope.Has[In]) => f(using scope))

  def fromFunction[In, Out](f: Scope.Has[In] => Context[Out]): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  def apply[In, Out](f: Scope.Has[In] ?=> Context[Out]): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out]((scope: Scope.Has[In]) => f(using scope))

  def fromFunction[In, Out](f: Scope.Has[In] => Context[Out]): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out](f)
}
