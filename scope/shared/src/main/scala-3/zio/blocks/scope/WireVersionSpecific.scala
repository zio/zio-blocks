package zio.blocks.scope

import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  /**
   * Constructs the service using the given scope and context.
   *
   * The scope is used to register finalizers (e.g. for `AutoCloseable`
   * services), while the context provides access to dependencies required by
   * this wire.
   *
   * @param scope
   *   the scope for cleanup registration
   * @param ctx
   *   the context providing dependencies
   * @return
   *   the constructed service
   */
  def make(scope: Scope, ctx: Context[In]): Out
}

private[scope] trait WireCompanionVersionSpecific {

  /**
   * Derives a shared [[Wire]] for type `T` by inspecting its constructor.
   *
   * The macro inspects `T`'s primary constructor and generates a wire that:
   *   - Retrieves constructor parameters from the context
   *   - Passes a `Scope` parameter if present
   *   - Registers `close()` as a finalizer if `T` extends `AutoCloseable`
   *
   * Shared wires are memoized within a single scope, so multiple dependents
   * receive the same instance.
   *
   * @tparam T
   *   the service type to construct (must be a class, not a trait or abstract)
   * @return
   *   a shared wire for constructing `T`
   */
  transparent inline def shared[T]: Wire.Shared[?, T] = ${ ScopeMacros.sharedImpl[T] }

  /**
   * Derives a unique [[Wire]] for type `T` by inspecting its constructor.
   *
   * Like `shared[T]`, but the wire creates a fresh instance each time it's
   * used. Use for services that should not be shared across dependents.
   *
   * @tparam T
   *   the service type to construct (must be a class, not a trait or abstract)
   * @return
   *   a unique wire for constructing `T`
   */
  transparent inline def unique[T]: Wire.Unique[?, T] = ${ ScopeMacros.uniqueImpl[T] }
}

private[scope] trait SharedVersionSpecific {

  /**
   * Creates a [[Wire.Shared]] from a construction function.
   *
   * Use this to build a shared wire with custom construction logic rather than
   * relying on macro derivation via `Wire.shared[T]`.
   *
   * @param f
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   * @return
   *   a shared wire that memoizes the constructed instance within a scope
   */
  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Shared[In, Out] =
    Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  /**
   * Creates a [[Wire.Unique]] from a construction function.
   *
   * Use this to build a unique wire with custom construction logic rather than
   * relying on macro derivation via `Wire.unique[T]`.
   *
   * @param f
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   * @return
   *   a unique wire that creates a fresh instance on each use
   */
  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Unique[In, Out] =
    Wire.Unique[In, Out](f)
}
