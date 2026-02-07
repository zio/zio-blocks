package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

/**
 * Scala 3-specific extension methods for [[Scope]].
 *
 * Provides the `injected` method for creating child scopes with dependency
 * injection.
 */
private[scope] trait ScopeVersionSpecific { self: Scope =>

  /**
   * Creates a child scope containing an instance of `T` with no explicit wires.
   *
   * Dependencies are resolved from the parent scope's stack.
   *
   * @tparam T
   *   the service type to construct and add to the new scope
   * @return
   *   a closeable scope containing `T`
   */
  inline def injected[T]: Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('{ Seq.empty[Wire[?, ?]] }, 'self) }

  /**
   * Creates a child scope containing an instance of `T` and its dependencies.
   *
   * Dependencies are resolved from:
   *   1. The provided wires (in order)
   *   2. The parent scope's stack
   *
   * @param wires
   *   wires providing dependencies for constructing `T`
   * @tparam T
   *   the service type to construct and add to the new scope
   * @return
   *   a closeable scope containing `T`
   */
  inline def injected[T](inline wires: Wire[?, ?]*): Scope.Closeable[T, ?] =
    ${ ScopeMacros.injectedImpl[T]('wires, 'self) }
}

/**
 * Scala 3-specific abstract methods for [[Scope.Closeable]].
 *
 * Defines the `use` and `useWithErrors` methods that use context functions to
 * provide scope capabilities implicitly within the block.
 */
private[scope] trait CloseableVersionSpecific[+Head, +Tail <: Scope] { self: Scope.Closeable[Head, Tail] =>

  /**
   * Executes the given function within this scope, then closes the scope.
   *
   * The function receives three implicit (context) parameters:
   *   - `Scope.Permit[self.Tag]`: An unforgeable token proving we're inside
   *     `.use`
   *   - `Context[Head] @@ self.Tag`: The scoped context containing this scope's
   *     services
   *   - `self.type`: The scope itself (for `defer` and other operations)
   *
   * Inside the block, use `$[T]` to retrieve services and `defer { ... }` to
   * register cleanup actions.
   *
   * @note
   *   Can only be called once. Calling on an already-closed scope throws
   *   [[IllegalStateException]].
   * @note
   *   Finalizer errors are silently discarded. Use [[useWithErrors]] if you
   *   need to handle cleanup errors.
   *
   * @param f
   *   the function to execute within the scope context
   * @tparam B
   *   the result type
   * @return
   *   the result of the function
   */
  def use[B](f: (Scope.Permit[self.Tag], Context[Head] @@ self.Tag, self.type) ?=> B): B

  /**
   * Executes the given function within this scope, then closes the scope,
   * returning both the result and any finalizer errors.
   *
   * The function receives three implicit (context) parameters:
   *   - `Scope.Permit[self.Tag]`: An unforgeable token proving we're inside
   *     `.use`
   *   - `Context[Head] @@ self.Tag`: The scoped context containing this scope's
   *     services
   *   - `self.type`: The scope itself (for `defer` and other operations)
   *
   * @note
   *   Can only be called once. Calling on an already-closed scope throws
   *   [[IllegalStateException]].
   *
   * @param f
   *   the function to execute within the scope context
   * @tparam B
   *   the result type
   * @return
   *   a tuple of (result, errors) where errors is a [[zio.blocks.chunk.Chunk]]
   *   of exceptions thrown by finalizers
   */
  def useWithErrors[B](
    f: (Scope.Permit[self.Tag], Context[Head] @@ self.Tag, self.type) ?=> B
  ): (B, zio.blocks.chunk.Chunk[Throwable])
}

/**
 * Factory for creating scope instances (internal use).
 */
private[scope] object ScopeFactory {

  /**
   * Creates a new cons-cell scope with the given parent, context, and
   * finalizers.
   */
  def createScopeImpl[T, S <: Scope](
    parent: S,
    context: Context[T],
    finalizers: Finalizers
  ): Scope.::[T, S] = new Scope.::[T, S](context, parent, finalizers)
}

/**
 * Scala 3 implementation of `use` and `useWithErrors` using context functions.
 *
 * This trait provides the concrete implementations that:
 *   1. Create the implicit capabilities (`Access`, scoped `Context`, and
 *      `self`)
 *   2. Execute the user's function with those capabilities in scope
 *   3. Ensure finalizers run even if the function throws
 */
private[scope] trait ScopeConsVersionSpecific[+H, +T <: Scope] { self: Scope.::[H, T] =>

  def use[B](f: (Scope.Permit[self.Tag], Context[H] @@ self.Tag, self.type) ?=> B): B = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.use can only be called once")
    given Scope.Permit[self.Tag]   = Scope.Permit[self.Tag]
    given (Context[H] @@ self.Tag) = @@.scoped[Context[H], self.Tag](head)
    given self.type                = self
    try f
    finally { close(); () }
  }

  def useWithErrors[B](
    f: (Scope.Permit[self.Tag], Context[H] @@ self.Tag, self.type) ?=> B
  ): (B, zio.blocks.chunk.Chunk[Throwable]) = {
    if (finalizers.isClosed)
      throw new IllegalStateException("Scope.useWithErrors can only be called once")
    given Scope.Permit[self.Tag]                  = Scope.Permit[self.Tag]
    given (Context[H] @@ self.Tag)                = @@.scoped[Context[H], self.Tag](head)
    given self.type                               = self
    var errors: zio.blocks.chunk.Chunk[Throwable] = zio.blocks.chunk.Chunk.empty
    val result                                    =
      try f
      finally errors = close()
    (result, errors)
  }
}
