package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.{Finalizers, ScopeImplScala2}
import scala.language.experimental.macros

private[scope] trait ScopeVersionSpecific[+Stack] { self: Scope[Stack] =>

  def injected[T]: Scope.Closeable[T, _] = macro ScopeMacros.injectedFromSelfNoArgsImpl[T]

  def injected[T](wires: Wire[_, _]*): Scope.Closeable[T, _] = macro ScopeMacros.injectedFromSelfImpl[T]
}

private[scope] trait CloseableVersionSpecific[+Head, +Tail] { self: Scope.Closeable[Head, Tail] =>

  def run[B](f: Scope.Has[Head] => B): B
}

private[scope] object ScopeFactory {
  def createScopeImpl[T, S](
    parent: Scope[_],
    context: Context[T],
    finalizers: Finalizers
  ): Scope.Closeable[T, S] =
    new ScopeImplScala2[T, S](parent, context, finalizers)
}
