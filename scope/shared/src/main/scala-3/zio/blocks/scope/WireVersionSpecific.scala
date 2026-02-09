package zio.blocks.scope

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
  inline def toResource(inline wires: Wire[?, ?]*): Resource[Out] =
    ${ WireMacros.toResourceImpl[In, Out]('self, 'wires) }
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
