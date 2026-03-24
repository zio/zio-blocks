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

package zio.blocks.schema.migration

import scala.compiletime.error

/**
 * Scala 3 extension methods for selector syntax in migrations. These are
 * compile-time-only placeholders that get transformed by the macros.
 *
 * Provides syntax sugar for selector expressions like:
 *   - `_.field.each` for collection traversal
 *   - `_.variant.when[Type]` for case selection
 *   - `_.wrapper.wrapped[Inner]` for wrapper unwrapping
 *   - `_.seq.at(0)` for index access
 *   - `_.map.atKey(key)` for map key access
 */
transparent trait MigrationSelectorSyntax {

  extension [A](a: A) {
    inline def when[B <: A]: B = error("Can only be used inside migration selector macros")
    inline def wrapped[B]: B   = error("Can only be used inside migration selector macros")
  }

  extension [C[_], A](c: C[A]) {
    inline def at(index: Int): A = error("Can only be used inside migration selector macros")
    inline def each: A           = error("Can only be used inside migration selector macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    inline def atKey(key: K): V = error("Can only be used inside migration selector macros")
    inline def eachKey: K       = error("Can only be used inside migration selector macros")
    inline def eachValue: V     = error("Can only be used inside migration selector macros")
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
