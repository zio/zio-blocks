package zio.blocks.scope

import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

/**
 * A recipe for constructing a service and its dependencies.
 *
 * Wires are the building blocks of dependency injection in Scope. They describe
 * how to construct a service given access to its dependencies via a scope.
 *
 * ==Wire Types==
 *
 *   - [[Wire.Shared]]: Memoized within a single `injected` call (default)
 *   - [[Wire.Unique]]: Creates fresh instances each time
 *
 * ==Creating Wires==
 *
 * Use the `shared[T]` and `unique[T]` macros for automatic derivation from
 * constructors, or create wires manually for custom construction logic.
 *
 * @example
 *   {{{
 *   // Scala 3: Manual wire with context function
 *   val dbWire: Wire.Shared[Config, Database] = Wire.Shared[Config, Database] {
 *     val config = $[Config]
 *     val db = Database.connect(config.url)
 *     defer(db.close())
 *     db
 *   }
 *
 *   // Scala 2: Manual wire with explicit scope
 *   val dbWire = Wire.Shared.fromFunction[Config, Database] { scope =>
 *     val config = scope.get[Config]
 *     val db = Database.connect(config.url)
 *     scope.defer(db.close())
 *     db
 *   }
 *   }}}
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
   * Returns true if this wire is shared (memoized within a single `injected`
   * call).
   *
   * Shared wires create one instance per `injected` call, reused across all
   * dependents within that call.
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
   * Converts this Wire to a Factory by providing resolved dependencies.
   *
   * @param deps
   *   Context containing all required dependencies
   * @return
   *   a Factory that creates Out values
   */
  def toFactory(deps: Context[In]): Factory[Out]
}

/**
 * Companion object providing wire factory methods and wire classes.
 */
object Wire extends WireCompanionVersionSpecific {

  /**
   * A wire that produces a shared (memoized) instance within a single
   * `injected` call.
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
    private[scope] val makeFn: Scope.Has[In] => Out
  ) extends Wire[In, Out] {

    def isShared: Boolean = true

    def shared: Shared[In, Out] = this

    def unique: Unique[In, Out] = new Unique[In, Out](makeFn)

    /**
     * Constructs the service using the given scope.
     *
     * @param scope
     *   the scope providing dependencies
     * @return
     *   the constructed service
     */
    override def make(scope: Scope.Has[In]): Out = makeFn(scope)

    def toFactory(deps: Context[In]): Factory[Out] = {
      val self = this
      new Factory.Shared[Out](scope => {
        val depScope = Scope.makeCloseable[In, Scope](scope, deps, new Finalizers)
        scope.defer(depScope.closeOrThrow())
        self.makeFn(depScope)
      })
    }
  }

  /**
   * Companion object for [[Wire.Shared]] providing factory methods.
   *
   * In Scala 3, use the context function syntax:
   * {{{
   * Wire.Shared[Config, Database] {
   *   val config = $[Config]
   *   val db = Database.connect(config.url)
   *   defer(db.close())
   *   db
   * }
   * }}}
   *
   * In Scala 2, use `fromFunction`:
   * {{{
   * Wire.Shared.fromFunction[Config, Database] { scope =>
   *   val config = scope.get[Config]
   *   val db = Database.connect(config.url)
   *   scope.defer(db.close())
   *   db
   * }
   * }}}
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
    private[scope] val makeFn: Scope.Has[In] => Out
  ) extends Wire[In, Out] {

    def isShared: Boolean = false

    def shared: Shared[In, Out] = new Shared[In, Out](makeFn)

    def unique: Unique[In, Out] = this

    /**
     * Constructs the service using the given scope.
     *
     * @param scope
     *   the scope providing dependencies
     * @return
     *   the constructed service
     */
    override def make(scope: Scope.Has[In]): Out = makeFn(scope)

    def toFactory(deps: Context[In]): Factory[Out] = {
      val self = this
      new Factory.Unique[Out](scope => {
        val depScope = Scope.makeCloseable[In, Scope](scope, deps, new Finalizers)
        scope.defer(depScope.closeOrThrow())
        self.makeFn(depScope)
      })
    }
  }

  /**
   * Companion object for [[Wire.Unique]] providing factory methods.
   *
   * In Scala 3, use the context function syntax:
   * {{{
   * Wire.Unique[Config, RequestId] {
   *   RequestId.generate()
   * }
   * }}}
   *
   * In Scala 2, use `fromFunction`:
   * {{{
   * Wire.Unique.fromFunction[Config, RequestId] { scope =>
   *   RequestId.generate()
   * }
   * }}}
   */
  object Unique extends UniqueVersionSpecific

  /**
   * Creates a wire that injects a pre-existing value.
   *
   * The value is wrapped in a shared wire with no dependencies. No cleanup is
   * registered because the value was created externally.
   *
   * @example
   *   {{{
   *   val config = Config.load()
   *   Scope.global.injected[App](Wire(config)).use { ... }
   *   }}}
   *
   * @param t
   *   the value to inject
   * @tparam T
   *   the service type
   * @return
   *   a shared wire that provides the value
   */
  def apply[T](t: T): Wire.Shared[Any, T] =
    new Shared[Any, T]((_: Scope.Has[Any]) => t)
}
