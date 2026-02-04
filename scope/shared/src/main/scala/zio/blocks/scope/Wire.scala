package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}

sealed trait Wire[-In, +Out] extends WireVersionSpecific[In, Out] {
  def isShared: Boolean

  final def isUnique: Boolean = !isShared

  def shared: Wire.Shared[In, Out]

  def unique: Wire.Unique[In, Out]
}

object Wire extends WireCompanionVersionSpecific {

  final class Shared[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Has[In] => Context[Out]
  ) extends Wire[In, Out] {

    def isShared: Boolean = true

    def shared: Shared[In, Out] = this

    def unique: Unique[In, Out] = new Unique[In, Out](constructFn)

    override def construct(implicit scope: Scope.Has[In]): Context[Out] = constructFn(scope)
  }

  object Shared extends SharedVersionSpecific

  final class Unique[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Has[In] => Context[Out]
  ) extends Wire[In, Out] {

    def isShared: Boolean = false

    def shared: Shared[In, Out] = new Shared[In, Out](constructFn)

    def unique: Unique[In, Out] = this

    override def construct(implicit scope: Scope.Has[In]): Context[Out] = constructFn(scope)
  }

  object Unique extends UniqueVersionSpecific

  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire.Shared[Any, T] =
    new Shared[Any, T]((_: Scope.Has[Any]) => Context(t))
}
