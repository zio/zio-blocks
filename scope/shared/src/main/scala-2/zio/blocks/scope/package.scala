package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers
import scala.language.experimental.macros

package object scope {
  def defer(finalizer: => Unit)(implicit scope: Scope.Any): Unit =
    scope.defer(finalizer)

  def $[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T =
    scope.get[T]

  def injectedValue[T](t: T)(implicit scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[T, _] = {
    val ctx        = Context(t)
    val finalizers = new Finalizers
    if (t.isInstanceOf[AutoCloseable]) {
      finalizers.add(t.asInstanceOf[AutoCloseable].close())
    }
    Scope.makeCloseable(scope, ctx, finalizers)
  }

  def shared[T]: Wire.Shared[_, T] = macro ScopeMacros.sharedImpl[T]

  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]

  def injected[T](wires: Wire[_, _]*)(implicit scope: Scope.Any): Scope.Closeable[T, _] =
    macro ScopeMacros.injectedImpl[T]
}
