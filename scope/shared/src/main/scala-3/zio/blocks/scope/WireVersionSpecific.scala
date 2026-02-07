package zio.blocks.scope

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  def make(scope: Scope.Has[In]): Out
}

private[scope] trait WireCompanionVersionSpecific

private[scope] trait SharedVersionSpecific {

  def apply[In, Out](f: Scope.Has[In] ?=> Out): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out]((scope: Scope.Has[In]) => f(using scope))

  def fromFunction[In, Out](f: Scope.Has[In] => Out): Wire.Shared[In, Out] =
    new Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  def apply[In, Out](f: Scope.Has[In] ?=> Out): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out]((scope: Scope.Has[In]) => f(using scope))

  def fromFunction[In, Out](f: Scope.Has[In] => Out): Wire.Unique[In, Out] =
    new Wire.Unique[In, Out](f)
}
