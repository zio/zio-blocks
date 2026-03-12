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

trait MigrationSelectorSyntax {

  implicit class ValueExtension[A](private val value: A) {
    @compileTimeOnly("when[B] can only be used inside migration selector expressions")
    def when[B <: A]: B = throw new UnsupportedOperationException("compile-time only")

    @compileTimeOnly("wrapped[B] can only be used inside migration selector expressions")
    def wrapped[B]: B = throw new UnsupportedOperationException("compile-time only")
  }

  implicit class SequenceExtension[C[_], A](private val collection: C[A]) {
    @compileTimeOnly("each can only be used inside migration selector expressions")
    def each: A = throw new UnsupportedOperationException("compile-time only")

    @compileTimeOnly("at(index) can only be used inside migration selector expressions")
    def at(index: Int): A = throw new UnsupportedOperationException("compile-time only")
  }

  implicit class MapExtension[M[_, _], K, V](private val map: M[K, V]) {
    @compileTimeOnly("atKey(key) can only be used inside migration selector expressions")
    def atKey(key: K): V = throw new UnsupportedOperationException("compile-time only")

    @compileTimeOnly("eachKey can only be used inside migration selector expressions")
    def eachKey: K = throw new UnsupportedOperationException("compile-time only")

    @compileTimeOnly("eachValue can only be used inside migration selector expressions")
    def eachValue: V = throw new UnsupportedOperationException("compile-time only")
  }
}
