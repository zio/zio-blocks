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
   * Derives a Resource[T] from T's constructor.
   *
   * Only works for types with no dependencies. If T has constructor parameters
   * (other than constructor parameters of type Scope/Finalizer), use
   * [[Wire]][T] and call `.toResource(deps)`.
   *
   * If T extends `AutoCloseable`, its `close()` method is automatically
   * registered as a finalizer.
   *
   * @tparam T
   *   the type to construct (must be a class with no dependencies)
   * @return
   *   a resource that creates T instances
   */
  inline def from[T]: Resource[T] = ${ ResourceMacros.deriveResourceImpl[T] }

  /**
   * Derives a Resource[T] from T's constructor with wire overrides for
   * dependencies.
   *
   * The provided wires act as overrides for constructor dependencies. Any
   * remaining dependencies are derived automatically when possible. If a
   * dependency cannot be satisfied (no override and not derivable), a
   * compile-time error is produced.
   *
   * This is useful when you want to create a standalone resource that fully
   * encapsulates its dependency graph.
   *
   * @example
   *   {{{
   *   class Service(db: Database, config: Config)
   *
   *   // Provide wires for all dependencies
   *   val resource = Resource.from[Service](
   *     Wire.shared[Database],
   *     Wire(Config("localhost", 8080))
   *   )
   *   }}}
   *
   * @tparam T
   *   the type to construct
   * @param wires
   *   wires that provide all required dependencies
   * @return
   *   a resource that creates T instances
   */
  inline def from[T](inline wires: Wire[?, ?]*): Resource[T] =
    ${ ResourceMacros.deriveResourceWithOverridesImpl[T]('wires) }
}
