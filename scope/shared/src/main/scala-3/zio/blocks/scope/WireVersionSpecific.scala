package zio.blocks.scope

import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  def make(finalizer: Finalizer, ctx: Context[In]): Out
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
