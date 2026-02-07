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
   * The function receives two implicit parameters:
   *   - `Scope.Access[self.Tag]`: An unforgeable token proving we're inside
   *     `.use`
   *   - `Context[Head] @@ self.Tag`: The scoped context containing this scope's
   *     services
   *
   * Can only be called once. Finalizer errors are silently discarded. Use
   * `useWithErrors` if you need to handle cleanup errors.
   */
  def use[B](f: (Scope.Access[self.Tag], Context[Head] @@ self.Tag, self.type) ?=> B): B

  /**
   * Uses this scope to execute the given function, then closes the scope,
   * returning both the result and any finalizer errors.
   *
   * The function receives three implicit parameters:
   *   - `Scope.Access[self.Tag]`: An unforgeable token proving we're inside
   *     `.use`
   *   - `Context[Head] @@ self.Tag`: The scoped context containing this scope's
   *     services
   *   - `self.type`: The scope itself (for `defer` and other operations)
   *
   * Can only be called once.
   */
  def useWithErrors[B](
    f: (Scope.Access[self.Tag], Context[Head] @@ self.Tag, self.type) ?=> B
  ): (B, zio.blocks.chunk.Chunk[Throwable])
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

  def use[B](f: (Scope.Access[self.Tag], Context[H] @@ self.Tag, self.type) ?=> B): B = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.use can only be called once")
    given Scope.Access[self.Tag]   = Scope.Access[self.Tag]
    given (Context[H] @@ self.Tag) = @@.scoped[Context[H], self.Tag](head)
    given self.type                = self
    try f
    finally { close(); () }
  }

  def useWithErrors[B](
    f: (Scope.Access[self.Tag], Context[H] @@ self.Tag, self.type) ?=> B
  ): (B, zio.blocks.chunk.Chunk[Throwable]) = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.useWithErrors can only be called once")
    given Scope.Access[self.Tag]                  = Scope.Access[self.Tag]
    given (Context[H] @@ self.Tag)                = @@.scoped[Context[H], self.Tag](head)
    given self.type                               = self
    var errors: zio.blocks.chunk.Chunk[Throwable] = zio.blocks.chunk.Chunk.empty
    val result                                    =
      try f
      finally errors = close()
    (result, errors)
  }
}
