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

/**
 * The capability to register cleanup actions (finalizers) with a scope.
 *
 * Every [[Scope]] is a `Finalizer`. A class that needs cleanup takes an
 * implicit `Finalizer` instead of a `Scope`, so its type promises only `defer`,
 * not `allocate` or `$`. `Resource.from` and `Wire` pass the owning scope to
 * such a parameter, and the package-level `defer` registers with it. Finalizers
 * run in reverse order of registration (last in, first out) when the scope
 * closes.
 *
 * {{{
 * final class Cache(implicit finalizer: Finalizer) {
 *   private val entries = new java.util.concurrent.ConcurrentHashMap[String, String]()
 *   defer(println("cache cleared: " + entries.size + " entries"))
 *   def put(key: String, value: String): Unit = { entries.put(key, value); () }
 * }
 *
 * Scope.global.scoped { scope =>
 *   import scope._
 *   val cache = allocate(Resource.from[Cache])
 *   $(cache)(_.put("user:1", "Ada"))
 * }
 * // prints: cache cleared: 1 entries
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/resource-management/finalizer/ Finalizer]]
 */
trait Finalizer {

  /**
   * Registers a finalizer to run when the scope closes.
   *
   * Finalizers run in reverse order of registration, and if one throws, the
   * others still run. If the scope is already closed, `f` never runs and the
   * returned handle does nothing.
   *
   * @param f
   *   a by-name expression to execute during cleanup
   * @return
   *   a [[DeferHandle]] that can be used to cancel the registered finalizer
   *   before the scope closes
   */
  def defer(f: => Unit): DeferHandle
}
