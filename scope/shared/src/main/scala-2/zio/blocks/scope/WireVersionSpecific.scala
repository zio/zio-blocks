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

import scala.language.experimental.macros
import zio.blocks.context.Context

private[scope] trait WireVersionSpecific[-In, +Out] { self: Wire[In, Out] =>

  /**
   * Constructs the service using the given scope and context.
   *
   * The scope receives the service's finalizers (for example `close()` of an
   * `AutoCloseable` service), and the context provides the dependencies of this
   * wire. Unlike `toResource`, this builds a new instance on every call, even
   * for a shared wire.
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
   * The macro inspects the primary constructor of `T` and generates a wire
   * that:
   *   - takes every constructor parameter from the context, except parameters
   *     of type `Finalizer` or `Scope`, which receive the scope
   *   - registers `close()` as a finalizer if `T` extends `AutoCloseable`
   *
   * The wire's `In` type is the intersection of the dependency types. Two
   * dependencies where one is a subtype of the other are a compile error. When
   * converted to a `Resource` via `toResource`, a shared wire produces a
   * reference-counted `Resource.shared`, so dependents receive the same
   * instance. Known bugs: a Java class whose constructor takes parameters
   * crashes the macro, and on Scala 3 two parameters of the same type receive
   * the same instance instead of a compile error.
   *
   * {{{
   * final case class DbConfig(url: String)
   * final class Database(config: DbConfig) extends AutoCloseable {
   *   def close(): Unit = println("closed " + config.url)
   * }
   *
   * val database: Wire[DbConfig, Database] = Wire.shared[Database]
   * }}}
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
   * Like `shared[T]`, but the resource made from the wire creates a fresh
   * instance each time it is allocated, and `Resource.from` gives each
   * dependent its own instance. On Scala 3, `Resource.from` treats this wire as
   * shared instead, so all dependents get one instance (a known bug). Use it
   * for services that must not be shared, such as per-request state.
   *
   * @tparam T
   *   the service type to construct (must be a class, not a trait or abstract)
   * @return
   *   a unique wire for constructing `T`
   */
  def unique[T]: Wire.Unique[_, T] = macro ScopeMacros.uniqueImpl[T]
}

private[scope] trait SharedVersionSpecific {

  /**
   * Creates a [[Wire.Shared]] from a construction function.
   *
   * Use it when the constructor of the service is not enough, for example to
   * call a factory method. `f` receives the scope and the dependencies. Unlike
   * `Wire.shared[T]`, the wire does not register `close()` for an
   * `AutoCloseable` result (a known bug); register it in `f` with
   * `scope.defer`.
   *
   * {{{
   * import java.util.concurrent.{ExecutorService, Executors}
   *
   * final case class PoolSize(threads: Int)
   *
   * val workers: Wire.Shared[PoolSize, ExecutorService] =
   *   Wire.Shared.fromFunction[PoolSize, ExecutorService] { (scope, ctx) =>
   *     val pool = Executors.newFixedThreadPool(ctx.get[PoolSize].threads)
   *     scope.defer(pool.shutdown())
   *     pool
   *   }
   * }}}
   *
   * @param f
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   * @return
   *   a shared wire that constructs the service with `f`
   */
  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Shared[In, Out] =
    Wire.Shared[In, Out](f)
}

private[scope] trait UniqueVersionSpecific {

  /**
   * Creates a [[Wire.Unique]] from a construction function.
   *
   * Use it when the constructor of the service is not enough, for example to
   * call a factory method. `f` receives the scope and the dependencies. Unlike
   * `Wire.unique[T]`, the wire does not register `close()` for an
   * `AutoCloseable` result (a known bug); register it in `f` with
   * `scope.defer`.
   *
   * @param f
   *   the function that constructs the service given a scope and context
   * @tparam In
   *   the dependencies required to construct the service
   * @tparam Out
   *   the service type produced
   * @return
   *   a unique wire that creates a fresh instance with `f` on each use
   */
  def fromFunction[In, Out](f: (Scope, Context[In]) => Out): Wire.Unique[In, Out] =
    Wire.Unique[In, Out](f)
}
