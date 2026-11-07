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

package zio.blocks.schema

/**
 * Evidence that `MapKV` has the shape `M[K, V]`, a type constructor with two
 * type arguments, with `M` as `Map`, `K` as `Key` and `V` as `Value`.
 * `Reflect#asMap` takes it to type the map node it returns. The compiler
 * provides it for any such type through `isMap`, map or not: for a type such as
 * `Either[String, Int]`, `asMap` returns `None`.
 */
trait IsMap[MapKV] {

  /** The type constructor of `MapKV`, such as `Map` for `Map[String, Int]`. */
  type Map[_, _]

  /** The key type of `MapKV`, such as `String` for `Map[String, Int]`. */
  type Key

  /** The value type of `MapKV`, such as `Int` for `Map[String, Int]`. */
  type Value

  /** Proves that `MapKV` is `Map[Key, Value]`. */
  def proof: MapKV =:= Map[Key, Value]
}

/** The instance of [[IsMap]] for every type of the form `M[K, V]`. */
object IsMap {

  /** An `IsMap[MapKV]` whose `Map`, `Key` and `Value` are `M`, `K` and `V`. */
  type Typed[MapKV, M[_, _], K, V] = IsMap[MapKV] {
    type Map[X, Y] = M[X, Y]
    type Key       = K
    type Value     = V
  }

  /**
   * Provides `IsMap` for `M[K, V]`, with `M`, `K` and `V` as its type members.
   */
  implicit def isMap[M[_, _], K, V]: Typed[M[K, V], M, K, V] =
    new IsMap[M[K, V]] {
      final type Map[X, Y] = M[X, Y]
      final type Key       = K
      final type Value     = V

      def proof: M[K, V] =:= Map[Key, Value] = implicitly[Map[Key, Value] =:= Map[Key, Value]]
    }
}
