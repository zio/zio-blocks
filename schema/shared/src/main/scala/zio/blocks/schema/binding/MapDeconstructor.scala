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

/**
 * Reads a map of type `M[K, V]` back as its entries: the deconstruction half of
 * a [[Binding.Map]].
 *
 * @tparam M
 *   the map type constructor
 */
trait MapDeconstructor[M[_, _]] {

  /** The type of one entry; `(K, V)` for `chunkMap` and `map`. */
  type KeyValue[_, _]

  /**
   * Returns an iterator over the entries of `m`, in the map's iteration order.
   */
  def deconstruct[K, V](m: M[K, V]): Iterator[KeyValue[K, V]]

  /** Returns the number of entries in `m`. */
  def size[K, V](m: M[K, V]): Int

  /** Returns the value bound to `k` in `m`, or `None` when there is none. */
  def get[K, V](m: M[K, V], k: K): Option[V]

  /** Returns the key of the entry `kv`. */
  def getKey[K, V](kv: KeyValue[K, V]): K

  /** Returns the value of the entry `kv`. */
  def getValue[K, V](kv: KeyValue[K, V]): V

  /** Returns the entry `kv` as a pair. */
  def getKeyValue[K, V](kv: KeyValue[K, V]): (K, V)
}

/** The map deconstructors for the standard maps. */
object MapDeconstructor {

  /** Reads a `ChunkMap`. */
  implicit val chunkMap: MapDeconstructor[ChunkMap] = new MapDeconstructor[ChunkMap] {
    type KeyValue[K, V] = (K, V)

    def deconstruct[K, V](m: ChunkMap[K, V]): Iterator[(K, V)] = m.iterator

    def size[K, V](m: ChunkMap[K, V]): Int = m.size

    def get[K, V](m: ChunkMap[K, V], k: K): Option[V] = m.get(k)

    def getKey[K, V](kv: (K, V)): K = kv._1

    def getValue[K, V](kv: (K, V)): V = kv._2

    def getKeyValue[K, V](kv: (K, V)): (K, V) = kv
  }

  /** Reads an immutable `Map`. */
  implicit val map: MapDeconstructor[Map] = new MapDeconstructor[Map] {
    type KeyValue[K, V] = (K, V)

    def deconstruct[K, V](m: Map[K, V]): Iterator[(K, V)] = m.iterator

    def size[K, V](m: Map[K, V]): Int = m.size

    def get[K, V](m: Map[K, V], k: K): Option[V] = m.get(k)

    def getKey[K, V](kv: (K, V)): K = kv._1

    def getValue[K, V](kv: (K, V)): V = kv._2

    def getKeyValue[K, V](kv: (K, V)): (K, V) = kv
  }
}
