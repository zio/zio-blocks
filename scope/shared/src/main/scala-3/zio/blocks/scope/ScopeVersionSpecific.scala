package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

private[scope] trait ScopeVersionSpecific { self: Scope =>

  inline def injected[T]: Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('{ Seq.empty[Wire[?, ?]] }, 'self) }

  inline def injected[T](inline wires: Wire[?, ?]*): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'self) }
}

private[scope] trait CloseableVersionSpecific[+Head, +Tail <: Scope] { self: Scope.Closeable[Head, Tail] =>

  /**
   * Uses this scope to execute the given function, then closes the scope.
   *
   * Can only be called once. Finalizer errors are silently discarded. Use
   * `useWithErrors` if you need to handle cleanup errors.
   */
  def use[B](f: self.type ?=> B): B

  /**
   * Uses this scope to execute the given function, then closes the scope,
   * returning both the result and any finalizer errors.
   *
   * Can only be called once.
   */
  def useWithErrors[B](f: self.type ?=> B): (B, zio.blocks.chunk.Chunk[Throwable])
}

private[scope] object ScopeFactory {
  def createScopeImpl[T, S <: Scope](
    parent: S,
    context: Context[T],
    finalizers: Finalizers
  ): Scope.::[T, S] = new Scope.::[T, S](context, parent, finalizers)
}

/**
 * Scala 3 implementation of use/useWithErrors using context functions.
 */
private[scope] trait ScopeConsVersionSpecific[+H, +T <: Scope] { self: Scope.::[H, T] =>

  def use[B](f: self.type ?=> B): B = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.use can only be called once")
    given self.type = self
    try f
    finally { close(); () }
  }

  def useWithErrors[B](f: self.type ?=> B): (B, zio.blocks.chunk.Chunk[Throwable]) = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.useWithErrors can only be called once")
    given self.type                               = self
    var errors: zio.blocks.chunk.Chunk[Throwable] = zio.blocks.chunk.Chunk.empty
    val result                                    =
      try f
      finally errors = close()
    (result, errors)
  }
}
