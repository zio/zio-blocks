/*
 * Copyright 2023 ZIO Blocks Maintainers
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

trait IsMap[MapKV] {
  type Map[_, _]
  type Key
  type Value

  def proof: MapKV =:= Map[Key, Value]
}

object IsMap {
  type Typed[MapKV, M[_, _], K, V] = IsMap[MapKV] {
    type Map[X, Y] = M[X, Y]
    type Key       = K
    type Value     = V
  }

  implicit def isMap[M[_, _], K, V]: Typed[M[K, V], M, K, V] =
    new IsMap[M[K, V]] {
      final type Map[X, Y] = M[X, Y]
      final type Key       = K
      final type Value     = V

      def proof: M[K, V] =:= Map[Key, Value] = implicitly[Map[Key, Value] =:= Map[Key, Value]]
    }
}
