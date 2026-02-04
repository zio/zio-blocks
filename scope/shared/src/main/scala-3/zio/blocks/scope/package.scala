package zio.blocks

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.internal.Finalizers

package object scope {
  def defer(finalizer: => Unit)(using scope: Scope.Any): Unit =
    scope.defer(finalizer)

  def $[T](using scope: Scope.Has[T], nom: IsNominalType[T]): T =
    scope.get[T]

  def injectedValue[T](t: T)(using scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[T, ?] = {
    val ctx        = Context(t)
    val finalizers = new Finalizers
    if (t.isInstanceOf[AutoCloseable]) {
      finalizers.add(t.asInstanceOf[AutoCloseable].close())
    }
    Scope.makeCloseable(scope, ctx, finalizers)
  }

  transparent inline def shared[T]: Wire.Shared[?, T] = ${ ScopeMacros.sharedImpl[T] }

  transparent inline def unique[T]: Wire.Unique[?, T] = ${ ScopeMacros.uniqueImpl[T] }

  inline def injected[T](inline wires: Wire[?, ?]*)(using scope: Scope.Any): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'scope) }

  extension [Stack](scope: Scope[Stack]) {
    inline def injected[T](inline wires: Wire[?, ?]*): Scope.Closeable[T, ?] =
      ${ ScopeMacros.injectedImpl[T]('wires, 'scope) }
  }
}
