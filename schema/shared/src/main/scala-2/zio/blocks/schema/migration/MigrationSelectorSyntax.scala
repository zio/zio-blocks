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

import scala.annotation.compileTimeOnly

/**
 * Scala 2 extension methods for selector syntax in migrations. These are
 * compile-time-only placeholders that get transformed by the macros.
 *
 * Provides syntax sugar for selector expressions like:
 *   - `_.field.each` for collection traversal
 *   - `_.variant.when[Type]` for case selection
 *   - `_.wrapper.wrapped[Inner]` for wrapper unwrapping
 *   - `_.seq.at(0)` for index access
 *   - `_.map.atKey(key)` for map key access
 */
trait MigrationSelectorSyntax {

  implicit class ValueExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???
  }

  implicit class SequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
