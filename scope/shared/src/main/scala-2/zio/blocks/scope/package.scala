package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

package object scope {
  def defer(finalizer: => Unit)(implicit scope: Scope.Any): Unit =
    scope.defer(finalizer)

  def get[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T =
    scope.get[T]

  def injectedValue[T](t: T)(implicit scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[Context[T] :: ?] = {
    val ctx        = Context(t)
    val finalizers = new Finalizers
    if (t.isInstanceOf[AutoCloseable]) {
      finalizers.add(t.asInstanceOf[AutoCloseable].close())
    }
    Scope.makeCloseable(scope, ctx, finalizers)
  }
}
