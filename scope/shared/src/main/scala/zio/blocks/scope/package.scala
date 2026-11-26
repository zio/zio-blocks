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

package zio.blocks

/**
 * Resource management with compile-time checks: a [[scope.Scope]] owns
 * resources, gives them back to you as values of its own type `$[A]`, and
 * releases them in reverse order when it closes.
 *
 * {{{
 * final class Database extends AutoCloseable {
 *   def query(sql: String): Int = sql.length
 *   def close(): Unit           = println("database closed")
 * }
 *
 * val rows: Int = Scope.global.scoped { scope =>
 *   import scope._
 *   val db: $[Database] = allocate(Resource(new Database))
 *   $(db)(_.query("SELECT 1"))
 * }
 * // prints: database closed
 * // rows: 8
 * }}}
 *
 * ==Key Concepts==
 *
 *   - '''Scoped values''' (`scope.$[A]`): values owned by a scope, which the
 *     compiler keeps from leaving it
 *   - '''`import scope._`''': brings the scope's operations into lexical scope
 *   - '''`allocate(resource)`''': acquires a [[scope.Resource]] in the scope
 *   - '''`$(value)(f)`''': calls methods on a scoped value
 *   - '''`scoped { child => ... }`''': opens a child scope
 *   - '''`defer { ... }`''': registers cleanup to run when the scope closes
 *   - '''[[scope.Wire]] and `Resource.from[T]`''': build a service and its
 *     dependencies from constructors
 *
 * ==How It Works==
 *
 * Each scope has its own `$[A]` type, so values of sibling scopes cannot be
 * mixed. A child can use its parent's values after `lower(value)`. `scoped`
 * requires an [[scope.Unscoped]] instance for its result type, so only plain
 * data (not resources or closures) leaves a scope, except, on Scala 3, a case
 * class, sealed trait or enum that extends `AutoCloseable` (a known bug).
 *
 * @see
 *   [[https://zioblocks.com/reference/resource-management/ Resource Management]]
 */
package object scope {

  /**
   * Registers a finalizer with the `Finalizer` in implicit scope, to run when
   * that scope closes.
   *
   * Use it in a class that takes an implicit `Finalizer` (a `using` parameter
   * on Scala 3): `Resource.from` and `Wire` pass the scope that owns the
   * instance. Finalizers run in reverse order of registration, and if one
   * throws, the others still run.
   *
   * {{{
   * final class AuditLog(implicit finalizer: Finalizer) {
   *   private val lines = scala.collection.mutable.ListBuffer.empty[String]
   *   defer(println("flushed " + lines.size + " lines"))
   *   def write(line: String): Unit = lines += line
   * }
   *
   * Scope.global.scoped { scope =>
   *   import scope._
   *   val log = allocate(Resource.from[AuditLog])
   *   $(log)(_.write("user signed in"))
   * }
   * // prints: flushed 1 lines
   * }}}
   *
   * @param finalizer
   *   a by-name expression to execute on scope close
   * @param fin
   *   the finalizer capability to register cleanup with
   * @return
   *   a [[scope.DeferHandle]] that cancels the registration
   */
  def defer(finalizer: => Unit)(using fin: Finalizer): DeferHandle =
    fin.defer(finalizer)

}
