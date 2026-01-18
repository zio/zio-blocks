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

package zio.blocks.schema.binding

trait MapDeconstructor[M[_, _]] {
  type KeyValue[_, _]

  def deconstruct[K, V](m: M[K, V]): Iterator[KeyValue[K, V]]

  def size[K, V](m: M[K, V]): Int

  def get[K, V](m: M[K, V], k: K): Option[V]

  def getKey[K, V](kv: KeyValue[K, V]): K

  def getValue[K, V](kv: KeyValue[K, V]): V

  def getKeyValue[K, V](kv: KeyValue[K, V]): (K, V)
}

object MapDeconstructor {
  val map: MapDeconstructor[Map] = new MapDeconstructor[Map] {
    type KeyValue[K, V] = (K, V)

    def deconstruct[K, V](m: Map[K, V]): Iterator[(K, V)] = m.iterator

    def size[K, V](m: Map[K, V]): Int = m.size

    def get[K, V](m: Map[K, V], k: K): Option[V] = m.get(k)

    def getKey[K, V](kv: (K, V)): K = kv._1

    def getValue[K, V](kv: (K, V)): V = kv._2

    def getKeyValue[K, V](kv: (K, V)): (K, V) = kv
  }
}
