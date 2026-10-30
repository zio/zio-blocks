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

import scala.quoted.*

private[scope] trait ResourceCompanionVersionSpecific {

  /**
   * Derives a `Resource[T]` from the primary constructor of `T`, for a class
   * without dependencies.
   *
   * The constructor may take only parameters of type `Finalizer` or `Scope` (in
   * any parameter list), which receive the scope that owns the instance; any
   * other parameter is a compile error that names it. For a class with
   * dependencies, use `Resource.from[T](wires*)`. If `T` extends
   * `AutoCloseable` and its constructor takes no `Finalizer` or `Scope`, its
   * `close()` is registered as a finalizer; with such a parameter, `close()` is
   * not registered (a known bug).
   *
   * The result is a shared resource (see `Resource.shared`): once the last
   * scope that allocated it closes, allocating it again throws
   * `IllegalStateException`. A `Resource.from[T]` stored in a `val` can
   * therefore be used only once (a known bug); write it as a `def`.
   *
   * {{{
   * final class Metrics extends AutoCloseable {
   *   def record(name: String): Unit = ()
   *   def close(): Unit              = println("metrics flushed")
   * }
   *
   * def metrics: Resource[Metrics] = Resource.from[Metrics]
   *
   * metrics.use(_.record("startup"))
   * // prints: metrics flushed
   * }}}
   *
   * @tparam T
   *   the class to construct; it must not be a trait or an abstract class
   * @return
   *   a resource that creates `T` instances
   */
  inline def from[T]: Resource[T] = ${ ResourceMacros.deriveResourceImpl[T] }

  /**
   * Derives a `Resource[T]` from the primary constructor of `T`, building its
   * dependencies from the given wires.
   *
   * The macro resolves the constructor parameters of `T`, and of every class it
   * needs, against the `wires`. A dependency without a wire is derived from its
   * own constructor as a shared wire, unless it is a trait, an abstract class,
   * or a type such as `String`, `Int`, `Option` or a function, which is a
   * compile error. Shared wires give every dependent the same instance; unique
   * wires give each dependent a fresh one, except on Scala 3, where an explicit
   * unique wire is treated as shared (a known bug). `Finalizer` and `Scope`
   * parameters receive the owning scope, and `AutoCloseable` classes derived by
   * the macro are closed when the scope closes.
   *
   * Known bugs: a dependency cycle crashes the macro instead of reporting the
   * cycle; a dependency on a Java class whose constructor takes parameters
   * crashes the macro; on Scala 3, two constructor parameters of the same type
   * receive the same instance instead of a compile error; and the result, like
   * `Resource.from[T]`, can be allocated only once if stored in a `val`.
   *
   * {{{
   * final case class DbConfig(url: String)
   *
   * final class Database(config: DbConfig) extends AutoCloseable {
   *   def query(sql: String): Int = sql.length
   *   def close(): Unit           = println("closed " + config.url)
   * }
   *
   * final class UserService(db: Database) {
   *   def count(): Int = db.query("SELECT count(*) FROM users")
   * }
   *
   * def service: Resource[UserService] = Resource.from[UserService](
   *   Wire(DbConfig("jdbc:h2:mem:shop"))
   * )
   *
   * val users: Int = service.use(_.count())
   * // prints: closed jdbc:h2:mem:shop
   * // users: 26
   * }}}
   *
   * @tparam T
   *   the class to construct
   * @param wires
   *   wires for dependencies that the macro cannot or should not derive
   * @return
   *   a resource that creates `T` instances
   */
  inline def from[T](inline wires: Wire[?, ?]*): Resource[T] =
    ${ ResourceMacros.deriveResourceWithOverridesImpl[T]('wires) }
}
