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

package zio.blocks.schema.binding

import zio.blocks.chunk.ChunkMap

trait MapConstructor[M[_, _]] {
  type ObjectBuilder[_, _]

  def newObjectBuilder[K, V](sizeHint: Int = -1): ObjectBuilder[K, V]

  def addObject[K, V](builder: ObjectBuilder[K, V], k: K, v: V): Unit

  def resultObject[K, V](builder: ObjectBuilder[K, V]): M[K, V]

  def emptyObject[K, V]: M[K, V]

  def updated[K, V](map: M[K, V], key: K, value: V): M[K, V]
}

object MapConstructor {
  def apply[M[_, _]](implicit mc: MapConstructor[M]): MapConstructor[M] = mc

  implicit val chunkMap: MapConstructor[ChunkMap] = new MapConstructor[ChunkMap] {
    type ObjectBuilder[K, V] = ChunkMap.ChunkMapBuilder[K, V]

    def newObjectBuilder[K, V](sizeHint: Int): ObjectBuilder[K, V] = new ChunkMap.ChunkMapBuilder

    def addObject[K, V](builder: ObjectBuilder[K, V], k: K, v: V): Unit = builder.add(k, v)

    def resultObject[K, V](builder: ObjectBuilder[K, V]): ChunkMap[K, V] = builder.result()

    def emptyObject[K, V]: ChunkMap[K, V] = ChunkMap.empty

    def updated[K, V](map: ChunkMap[K, V], key: K, value: V): ChunkMap[K, V] = map.updated(key, value)
  }

  implicit val map: MapConstructor[Map] = new MapConstructor[Map] {
    type ObjectBuilder[K, V] = scala.collection.mutable.Builder[(K, V), Map[K, V]]

    def newObjectBuilder[K, V](sizeHint: Int): ObjectBuilder[K, V] = Map.newBuilder[K, V]

    def addObject[K, V](builder: ObjectBuilder[K, V], k: K, v: V): Unit = builder.addOne((k, v))

    def resultObject[K, V](builder: ObjectBuilder[K, V]): Map[K, V] = builder.result()

    def emptyObject[K, V]: Map[K, V] = Map.empty

    def updated[K, V](map: Map[K, V], key: K, value: V): Map[K, V] = map.updated(key, value)
  }
}
