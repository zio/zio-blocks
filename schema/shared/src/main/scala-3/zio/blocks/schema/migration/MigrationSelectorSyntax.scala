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
 * Selector syntax meant for migration builder selectors: `each` selects every
 * element of a collection, `eachKey` and `eachValue` every key or value of a
 * map, `at(index)` one element, `atKey(key)` one map value, `when[B]` the case
 * `B` of a variant, and `wrapped[B]` the value inside a wrapper type.
 *
 * Calling these methods outside a selector fails at compile time. They do not
 * compile inside builder selectors either, and the selector macros reject them
 * (see `docs/dev/issues.md`).
 */
transparent trait MigrationSelectorSyntax {

  extension [A](a: A) {

    /**
     * Selects the case `B` of a variant. It does not compile in any
     * `MigrationBuilder` method yet (a known bug).
     */
    inline def when[B <: A]: B = error("Can only be used inside migration selector macros")

    /**
     * Selects the value inside a wrapper type, as a `B`. It does not compile in
     * any `MigrationBuilder` method yet (a known bug).
     */
    inline def wrapped[B]: B = error("Can only be used inside migration selector macros")
  }

  extension [C[_], A](c: C[A]) {

    /**
     * Selects the element at `index` of a collection. It does not compile in
     * any `MigrationBuilder` method yet (a known bug).
     */
    inline def at(index: Int): A = error("Can only be used inside migration selector macros")

    /**
     * Selects every element of a collection. It does not compile in any
     * `MigrationBuilder` method yet (a known bug).
     */
    inline def each: A = error("Can only be used inside migration selector macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {

    /**
     * Selects the value at `key` of a map. It does not compile in any
     * `MigrationBuilder` method yet (a known bug).
     */
    inline def atKey(key: K): V = error("Can only be used inside migration selector macros")

    /**
     * Selects every key of a map. It does not compile in any `MigrationBuilder`
     * method yet (a known bug).
     */
    inline def eachKey: K = error("Can only be used inside migration selector macros")

    /**
     * Selects every value of a map. It does not compile in any
     * `MigrationBuilder` method yet (a known bug).
     */
    inline def eachValue: V = error("Can only be used inside migration selector macros")
  }
}

/** The selector syntax, for an explicit import. */
object MigrationSelectorSyntax extends MigrationSelectorSyntax
