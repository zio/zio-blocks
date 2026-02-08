package zio.blocks.scope

import zio.blocks.context.Context

/**
 * A recipe for constructing a service and its dependencies.
 *
 * Wires are the building blocks of dependency injection in Scope. They describe
 * how to construct a service given access to its dependencies via a context.
 *
 * ==Wire Types==
 *
 *   - [[Wire.Shared]]: Memoized within a single scope (default)
 *   - [[Wire.Unique]]: Creates fresh instances each time
 *
 * ==Creating Wires==
 *
 * Use the `shared[T]` and `unique[T]` macros for automatic derivation from
 * constructors, or create wires manually for custom construction logic.
 *
 * @tparam In
 *   the dependencies required (contravariant - accepts supertypes)
 * @tparam Out
 *   the service(s) produced (covariant - produces subtypes)
 *
 * @see
 *   [[Wireable]] for defining wires in companion objects
 */
sealed trait Wire[-In, +Out] extends WireVersionSpecific[In, Out] {

  /**
   * Returns true if this wire is shared (memoized within a single scope).
   *
   * Shared wires create one instance per scope, reused across all dependents
   * within that scope.
   */
  def isShared: Boolean

  /**
   * Returns true if this wire is unique (creates fresh instances each time).
   *
   * Unique wires create a new instance for each dependent service.
   */
  final def isUnique: Boolean = !isShared

  /**
   * Converts this wire to a shared wire.
   *
   * If already shared, returns `this`. Otherwise, creates a new [[Shared]] wire
   * with the same construction function.
   */
  def shared: Wire.Shared[In, Out]

  /**
   * Converts this wire to a unique wire.
   *
   * If already unique, returns `this`. Otherwise, creates a new [[Unique]] wire
   * with the same construction function.
   */
  def unique: Wire.Unique[In, Out]

  /**
   * Converts this Wire to a Resource by providing resolved dependencies.
   *
   * @param deps
   *   Context containing all required dependencies
   * @return
   *   a Resource that creates Out values
   */
  def toResource(deps: Context[In]): Resource[Out]
}

/**
 * Companion object providing wire factory methods and wire classes.
 */
object Wire extends WireCompanionVersionSpecific {

  /**
   * A wire that produces a shared (memoized) instance within a single scope.
   *
   * When multiple services depend on the same shared wire, only one instance is
   * created and reused. This is the default wire type produced by `shared[T]`.
   *
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   */
  final class Shared[-In, +Out] private[scope] (
    private[scope] val makeFn: (Finalizer, Context[In]) => Out
  ) extends Wire[In, Out] {

    def isShared: Boolean = true

    def shared: Shared[In, Out] = this

    def unique: Unique[In, Out] = new Unique[In, Out](makeFn)

    /**
     * Constructs the service using the given finalizer and context.
     *
     * @param finalizer
     *   the finalizer for cleanup registration
     * @param ctx
     *   the context providing dependencies
     * @return
     *   the constructed service
     */
    def make(finalizer: Finalizer, ctx: Context[In]): Out = makeFn(finalizer, ctx)

    def toResource(deps: Context[In]): Resource[Out] = {
      val self = this
      new Resource.Shared[Out](finalizer => self.makeFn(finalizer, deps))
    }
  }

  /**
   * Companion object for [[Wire.Shared]] providing factory methods.
   */
  object Shared extends SharedVersionSpecific

  /**
   * A wire that produces a fresh instance each time it's used.
   *
   * Unlike shared wires, unique wires create new instances for each dependent
   * service. Use for services that should not be shared, like request-scoped
   * resources or per-call state.
   *
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   */
  final class Unique[-In, +Out] private[scope] (
    private[scope] val makeFn: (Finalizer, Context[In]) => Out
  ) extends Wire[In, Out] {

    def isShared: Boolean = false

    def shared: Shared[In, Out] = new Shared[In, Out](makeFn)

    def unique: Unique[In, Out] = this

    /**
     * Constructs the service using the given finalizer and context.
     *
     * @param finalizer
     *   the finalizer for cleanup registration
     * @param ctx
     *   the context providing dependencies
     * @return
     *   the constructed service
     */
    def make(finalizer: Finalizer, ctx: Context[In]): Out = makeFn(finalizer, ctx)

    def toResource(deps: Context[In]): Resource[Out] = {
      val self = this
      new Resource.Unique[Out](finalizer => self.makeFn(finalizer, deps))
    }
  }

  /**
   * Companion object for [[Wire.Unique]] providing factory methods.
   */
  object Unique extends UniqueVersionSpecific

  /**
   * Creates a wire that injects a pre-existing value.
   *
   * The value is wrapped in a shared wire with no dependencies. No cleanup is
   * registered because the value was created externally.
   *
   * @param t
   *   the value to inject
   * @tparam T
   *   the service type
   * @return
   *   a shared wire that provides the value
   */
  def apply[T](t: T): Wire.Shared[Any, T] =
    new Shared[Any, T]((_, _) => t)
}
