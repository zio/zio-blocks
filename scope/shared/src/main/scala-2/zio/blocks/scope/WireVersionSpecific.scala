package zio.blocks.scope

import scala.language.experimental.macros
import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

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
  def shared[T]: Wire.Shared[_, T] = macro ScopeMacros.sharedImpl[T]

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
  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]
}

private[scope] trait SharedVersionSpecific {

  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Shared[In, Out] =
    Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Unique[In, Out] =
    Wire.Unique[In, Out](f)
}
