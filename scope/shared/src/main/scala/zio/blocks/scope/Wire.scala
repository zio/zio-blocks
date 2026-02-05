package zio.blocks.scope

import zio.blocks.context.{Context, IsNominalType}

/**
 * A recipe for constructing a service and its dependencies.
 *
 * Wires are the building blocks of dependency injection in Scope. They describe
 * how to construct a service given access to its dependencies via a scope.
 *
 * @example
 *   {{{
 *   // Scala 3: Manual wire with context function
 *   val dbWire: Wire.Shared[Config, Database] = Wire.Shared[Config, Database] {
 *     val config = $[Config]
 *     val db = Database.connect(config.url)
 *     defer(db.close())
 *     Context(db)
 *   }
 *
 *   // Scala 2: Manual wire with explicit scope
 *   val dbWire = Wire.Shared.fromFunction[Config, Database] { scope =>
 *     val config = scope.get[Config]
 *     val db = Database.connect(config.url)
 *     scope.defer(db.close())
 *     Context(db)
 *   }
 *   }}}
 *
 * @tparam In
 *   The dependencies required (contravariant)
 * @tparam Out
 *   The service(s) produced (covariant)
 */
sealed trait Wire[-In, +Out] extends WireVersionSpecific[In, Out] {

  /**
   * Returns true if this wire is shared (memoized within a single `injected`
   * call).
   */
  def isShared: Boolean

  /**
   * Returns true if this wire is unique (creates fresh instances each time).
   */
  final def isUnique: Boolean = !isShared

  /** Converts this wire to a shared wire. */
  def shared: Wire.Shared[In, Out]

  /** Converts this wire to a unique wire. */
  def unique: Wire.Unique[In, Out]
}

object Wire extends WireCompanionVersionSpecific {

  /**
   * A wire that produces a shared (memoized) instance within a single
   * `injected` call.
   *
   * When multiple services depend on the same shared wire, only one instance is
   * created and reused. This is the default wire type produced by `shared[T]`.
   */
  final class Shared[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Has[In] => Context[Out]
  ) extends Wire[In, Out] {

    def isShared: Boolean = true

    def shared: Shared[In, Out] = this

    def unique: Unique[In, Out] = new Unique[In, Out](constructFn)

    override def construct(implicit scope: Scope.Has[In]): Context[Out] = constructFn(scope)
  }

  object Shared extends SharedVersionSpecific

  /**
   * A wire that produces a fresh instance each time it's used.
   *
   * Unlike shared wires, unique wires create new instances for each dependent
   * service. Use for services that should not be shared, like request-scoped
   * resources.
   */
  final class Unique[-In, +Out] private[scope] (
    private[scope] val constructFn: Scope.Has[In] => Context[Out]
  ) extends Wire[In, Out] {

    def isShared: Boolean = false

    def shared: Shared[In, Out] = new Shared[In, Out](constructFn)

    def unique: Unique[In, Out] = this

    override def construct(implicit scope: Scope.Has[In]): Context[Out] = constructFn(scope)
  }

  object Unique extends UniqueVersionSpecific

  /**
   * Creates a wire that injects a pre-existing value.
   *
   * The value is wrapped in a shared wire with no dependencies. No cleanup is
   * registered.
   *
   * @example
   *   {{{
   *   val config = Config.load()
   *   Scope.global.injected[App](Wire(config)).run { ... }
   *   }}}
   */
  def apply[T](t: T)(implicit ev: IsNominalType[T]): Wire.Shared[Any, T] =
    new Shared[Any, T]((_: Scope.Has[Any]) => Context(t))
}
