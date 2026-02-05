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

  /**
   * The head service of this scope, tagged with the scope's identity.
   *
   * Use the `$` operator to access methods on the underlying value:
   * {{{
   * closeable.value $ (_.method())
   * }}}
   *
   * @return
   *   The head service tagged with this scope's Tag
   */
  def value: Head @@ self.Tag

  /**
   * Executes the given function with this scope, then closes the scope.
   *
   * Finalizer errors are silently discarded. Use `runWithErrors` if you need to
   * handle cleanup errors.
   */
  def run[B](f: Scope.Has[Head] ?=> B): B

  /**
   * Executes the given function with this scope, then closes the scope,
   * returning both the result and any finalizer errors.
   */
  def runWithErrors[B](f: Scope.Has[Head] ?=> B): (B, zio.blocks.chunk.Chunk[Throwable])
}

private[scope] object ScopeFactory {
  def createScopeImpl[T, S](
    parent: Scope[?],
    context: Context[T],
    finalizers: Finalizers
  ): Scope.Closeable[T, S] =
    new ScopeImplScala3[T, S, parent.Tag](parent, context, finalizers)
}
