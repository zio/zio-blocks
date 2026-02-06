package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.{Finalizers, ScopeImplScala2}
import scala.language.experimental.macros

private[scope] trait ScopeVersionSpecific[+Stack] { self: Scope[Stack] =>

  def injected[T]: Scope.Closeable[T, _] = macro ScopeMacros.injectedFromSelfNoArgsImpl[T]

  def injected[T](wires: Wire[_, _]*): Scope.Closeable[T, _] = macro ScopeMacros.injectedFromSelfImpl[T]
}

private[scope] trait CloseableVersionSpecific[+Head, +Tail] { self: Scope.Closeable[Head, Tail] =>

  /**
   * Executes the given function with this scope, then closes the scope.
   *
   * Finalizer errors are silently discarded. Use `runWithErrors` if you need to
   * handle cleanup errors.
   */
  def run[B](f: Scope.Has[Head] => B): B

  /**
   * Executes the given function with this scope, then closes the scope,
   * returning both the result and any finalizer errors.
   */
  def runWithErrors[B](f: Scope.Has[Head] => B): (B, zio.blocks.chunk.Chunk[Throwable])
}

private[scope] object ScopeFactory {
  def createScopeImpl[T, S](
    parent: Scope[_],
    context: Context[T],
    finalizers: Finalizers
  ): Scope.Closeable[T, S] =
    new ScopeImplScala2[T, S, parent.Tag](parent, context, finalizers)
}
