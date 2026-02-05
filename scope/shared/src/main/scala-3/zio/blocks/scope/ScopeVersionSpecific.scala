package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.{Finalizers, ScopeImplScala3}

private[scope] trait ScopeVersionSpecific[+Stack] { self: Scope[Stack] =>

  inline def injected[T]: Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('{ Seq.empty[Wire[?, ?]] }, 'self) }

  inline def injected[T](inline wires: Wire[?, ?]*): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'self) }
}

private[scope] trait CloseableVersionSpecific[+Head, +Tail] { self: Scope.Closeable[Head, Tail] =>

  def run[B](f: Scope.Has[Head] ?=> B): B
}

private[scope] object ScopeFactory {
  def createScopeImpl[T, S](
    parent: Scope[?],
    context: Context[T],
    finalizers: Finalizers
  ): Scope.Closeable[T, S] =
    new ScopeImplScala3[T, S](parent, context, finalizers)
}
