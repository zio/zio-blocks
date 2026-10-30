/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope

import zio.blocks.context.Context

/**
 * A recipe for constructing a service from its dependencies: a function from a
 * scope and a `Context[In]` of dependencies to an `Out`, plus a sharing
 * strategy.
 *
 * Wires are the building blocks of dependency injection in `scope`. Pass them
 * to `Resource.from[T](wires*)`, which resolves the whole graph at compile
 * time, or turn one into a [[Resource]] yourself with `toResource`.
 *
 * ==Wire Types==
 *
 *   - [[Wire.Shared]]: becomes a reference-counted [[Resource.shared]], so all
 *     dependents in a graph get one instance
 *   - [[Wire.Unique]]: becomes a [[Resource.unique]], so each allocation gets a
 *     fresh instance (on Scala 3, `Resource.from` treats a unique wire as
 *     shared, a known bug)
 *
 * ==Creating Wires==
 *
 * `Wire.shared[T]` and `Wire.unique[T]` derive a wire from the constructor of
 * `T`. `Wire(value)` provides a value you already have, and
 * `Wire.Shared.fromFunction` / `Wire.Unique.fromFunction` take a construction
 * function.
 *
 * {{{
 * final case class DbConfig(url: String)
 *
 * final class Database(config: DbConfig) extends AutoCloseable {
 *   def query(sql: String): Int = sql.length
 *   def close(): Unit           = println("closed " + config.url)
 * }
 *
 * final class RequestHandler(db: Database) {
 *   def handle(): Int = db.query("SELECT 1")
 * }
 *
 * def handler: Resource[RequestHandler] = Resource.from[RequestHandler](
 *   Wire(DbConfig("jdbc:h2:mem:shop")),
 *   Wire.shared[Database],
 *   Wire.unique[RequestHandler]
 * )
 *
 * val result: Int = handler.use(_.handle())
 * // prints: closed jdbc:h2:mem:shop
 * // result: 8
 * }}}
 *
 * @tparam In
 *   the dependencies required; contravariant, so a wire that needs less can
 *   stand in for one that needs more
 * @tparam Out
 *   the service produced
 * @see
 *   [[https://zioblocks.com/reference/resource-management/wire/ Wire]]
 */
sealed trait Wire[-In, +Out] extends WireVersionSpecific[In, Out] {

  /**
   * Returns true if this wire is shared (reference-counted via
   * [[Resource.shared]]).
   *
   * Shared wires are converted to `Resource.shared`, so the allocations of the
   * resulting resource share one instance across scopes. The instance is
   * released when the last referencing scope is closed.
   *
   * @return
   *   true if this wire is shared, false if unique
   */
  def isShared: Boolean

  /**
   * Returns true if this wire is unique (creates fresh instances each time).
   *
   * Unique wires are converted to `Resource.unique`, which creates a new
   * instance on each allocation.
   *
   * @return
   *   true if this wire is unique, false if shared
   */
  final def isUnique: Boolean = !isShared

  /**
   * Converts this wire to a shared wire.
   *
   * If already shared, returns `this`. Otherwise, creates a new `Wire.Shared`
   * wire with the same construction function.
   *
   * @return
   *   a shared version of this wire
   */
  def shared: Wire.Shared[In, Out]

  /**
   * Converts this wire to a unique wire.
   *
   * If already unique, returns `this`. Otherwise, creates a new `Wire.Unique`
   * wire with the same construction function.
   *
   * @return
   *   a unique version of this wire
   */
  def unique: Wire.Unique[In, Out]

  /**
   * Converts this wire to a [[Resource]], given the dependencies it needs.
   *
   * A shared wire gives a [[Resource.shared]] and a unique wire a
   * [[Resource.unique]]. Each call creates a new resource.
   *
   * {{{
   * import zio.blocks.context.Context
   *
   * final case class DbConfig(url: String)
   * final class Database(config: DbConfig) {
   *   def url: String = config.url
   * }
   *
   * val database: Resource[Database] =
   *   Wire.unique[Database].toResource(Context(DbConfig("jdbc:h2:mem:shop")))
   *
   * database.use(_.url) // "jdbc:h2:mem:shop"
   * }}}
   *
   * @param deps
   *   Context containing all required dependencies
   * @return
   *   a Resource that creates Out values
   */
  def toResource(deps: Context[In]): Resource[Out]
}

/**
 * Wire factory methods and the two wire classes, [[Wire.Shared]] and
 * [[Wire.Unique]].
 */
object Wire extends WireCompanionVersionSpecific {

  /**
   * A wire that produces a reference-counted shared instance via
   * [[Resource.shared]].
   *
   * The underlying instance may be shared across multiple allocating scopes. A
   * reference count tracks usage, and the instance is released when the last
   * referencing scope is closed. `Wire.shared[T]` and `Wire(value)` produce
   * this kind, and `Resource.from` uses it for the dependencies it derives.
   *
   * @param makeFn
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   */
  final case class Shared[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out] {

    /**
     * Returns true since this is a shared wire.
     *
     * @return
     *   always true for shared wires
     */
    def isShared: Boolean = true

    /**
     * Returns this wire since it is already shared.
     *
     * @return
     *   this wire unchanged
     */
    def shared: Shared[In, Out] = this

    /**
     * Converts this shared wire to a unique wire.
     *
     * @return
     *   a new unique wire with the same construction function
     */
    def unique: Unique[In, Out] = Unique[In, Out](makeFn)

    /**
     * Constructs the service using the given scope and context.
     *
     * @param scope
     *   the scope for cleanup registration
     * @param ctx
     *   the context providing dependencies
     * @return
     *   the constructed service
     */
    def make(scope: Scope, ctx: Context[In]): Out = makeFn(scope, ctx)

    /**
     * Converts this Wire to a Resource by providing resolved dependencies.
     *
     * @param deps
     *   Context containing all required dependencies
     * @return
     *   a Resource that creates Out values with memoization
     */
    def toResource(deps: Context[In]): Resource[Out] =
      Resource.shared[Out](scope => this.makeFn(scope, deps))
  }

  /**
   * Companion object for [[Wire.Shared]] providing factory methods.
   */
  object Shared extends SharedVersionSpecific

  /**
   * A wire that produces a fresh instance each time it's used.
   *
   * Unlike shared wires, unique wires create a new instance for each dependent
   * service. On Scala 3, `Resource.from` treats an explicit unique wire as
   * shared, so its dependents get one instance (a known bug). Use for services
   * that should not be shared, like request-scoped resources or per-call state.
   *
   * @param makeFn
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   */
  final case class Unique[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out] {

    /**
     * Returns false since this is a unique wire.
     *
     * @return
     *   always false for unique wires
     */
    def isShared: Boolean = false

    /**
     * Converts this unique wire to a shared wire.
     *
     * @return
     *   a new shared wire with the same construction function
     */
    def shared: Shared[In, Out] = Shared[In, Out](makeFn)

    /**
     * Returns this wire since it is already unique.
     *
     * @return
     *   this wire unchanged
     */
    def unique: Unique[In, Out] = this

    /**
     * Constructs the service using the given scope and context.
     *
     * @param scope
     *   the scope for cleanup registration
     * @param ctx
     *   the context providing dependencies
     * @return
     *   the constructed service
     */
    def make(scope: Scope, ctx: Context[In]): Out = makeFn(scope, ctx)

    /**
     * Converts this Wire to a Resource by providing resolved dependencies.
     *
     * @param deps
     *   Context containing all required dependencies
     * @return
     *   a Resource that creates fresh Out values on each use
     */
    def toResource(deps: Context[In]): Resource[Out] =
      Resource.unique[Out](scope => this.makeFn(scope, deps))
  }

  /**
   * Companion object for [[Wire.Unique]] providing factory methods.
   */
  object Unique extends UniqueVersionSpecific

  /**
   * Creates a wire that provides a value you already have.
   *
   * The value is wrapped in a shared wire with no dependencies. If the value is
   * `AutoCloseable`, its `close()` is registered when a scope allocates the
   * wire's resource. The argument is evaluated once, when `Wire(t)` is called,
   * not when the wire is used, so `Wire(new Database)` opens the database right
   * away (a known bug: the parameter is not by-name).
   *
   * {{{
   * final case class ServerConfig(host: String, port: Int)
   *
   * val config: Wire.Shared[Any, ServerConfig] =
   *   Wire(ServerConfig("localhost", 8080))
   * }}}
   *
   * @param t
   *   the value to inject
   * @tparam T
   *   the service type
   * @return
   *   a shared wire that provides the value
   */
  def apply[T](t: T): Wire.Shared[Any, T] =
    new Shared[Any, T]((scope, _) => {
      t match {
        case c: AutoCloseable => scope.defer(c.close())
        case _                => ()
      }
      t
    })
}
