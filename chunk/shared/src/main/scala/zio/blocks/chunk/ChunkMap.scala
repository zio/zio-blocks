/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import scala.collection.immutable.{AbstractMap, MapOps, StrictOptimizedMapOps}
import scala.collection.{IterableOnce, MapFactory, mutable}

/**
 * An order-preserving immutable map backed by `Chunk`. Keys and values are
 * stored in parallel `Chunk`s, maintaining insertion order.
 *
 * This map is optimized for small collections where ordering matters. For
 * larger collections requiring frequent lookups, consider using `indexed` to
 * create an optimized version with O(1) key lookup (at the cost of additional
 * memory).
 *
 * Key characteristics:
 *   - Preserves insertion order during iteration
 *   - O(n) key lookup by default
 *   - O(1) indexed access by position
 *   - Equality is order-independent (like standard Map)
 *
 * @tparam K
 *   The key type
 * @tparam V
 *   The value type
 */
final class ChunkMap[K, +V] private (
  private[chunk] val _keys: Chunk[K],
  private[chunk] val _values: Chunk[V]
) extends AbstractMap[K, V]
    with MapOps[K, V, ChunkMap, ChunkMap[K, V]]
    with StrictOptimizedMapOps[K, V, ChunkMap, ChunkMap[K, V]]
    with Serializable { self =>

  override def size: Int = _keys.length

  override def knownSize: Int = _keys.length

  override def isEmpty: Boolean = _keys.isEmpty

  override def get(key: K): Option[V] = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      if (_keys(idx) == key) return Some(_values(idx))
      idx += 1
    }
    None
  }

  override def apply(key: K): V = get(key) match {
    case Some(v) => v
    case None    => throw new NoSuchElementException(s"key not found: $key")
  }

  override def contains(key: K): Boolean = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      if (_keys(idx) == key) return true
      idx += 1
    }
    false
  }

  override def updated[V1 >: V](key: K, value: V1): ChunkMap[K, V1] = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      if (_keys(idx) == key) {
        return new ChunkMap(_keys, _values.updated(idx, value))
      }
      idx += 1
    }
    new ChunkMap(_keys.appended(key), _values.appended(value))
  }

  override def removed(key: K): ChunkMap[K, V] = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      if (_keys(idx) == key) {
        val newKeys   = _keys.take(idx) ++ _keys.drop(idx + 1)
        val newValues = _values.take(idx) ++ _values.drop(idx + 1)
        return new ChunkMap(newKeys, newValues)
      }
      idx += 1
    }
    this
  }

  override def iterator: Iterator[(K, V)] = new Iterator[(K, V)] {
    private var idx = 0
    private val len = _keys.length

    override def hasNext: Boolean = idx < len

    override def next(): (K, V) = {
      if (!hasNext) throw new NoSuchElementException("next on empty iterator")
      val result = (_keys(idx), _values(idx))
      idx += 1
      result
    }
  }

  override def keysIterator: Iterator[K] = _keys.iterator

  override def valuesIterator: Iterator[V] = _values.iterator

  override def mapFactory: MapFactory[ChunkMap] = ChunkMap

  override def empty: ChunkMap[K, V] = ChunkMap.empty

  override protected def fromSpecific(
    coll: IterableOnce[(K, V @scala.annotation.unchecked.uncheckedVariance)]
  ): ChunkMap[K, V] =
    ChunkMap.from(coll)

  override protected def newSpecificBuilder
    : mutable.Builder[(K, V @scala.annotation.unchecked.uncheckedVariance), ChunkMap[K, V]] =
    ChunkMap.newBuilder[K, V]

  override def concat[V2 >: V](suffix: IterableOnce[(K, V2)]): ChunkMap[K, V2] = {
    val builder = ChunkMap.newBuilder[K, V2]
    builder.addAll(this)
    builder.addAll(suffix)
    builder.result()
  }

  def toChunk: Chunk[(K, V)] = _keys.zip(_values)

  def keysChunk: Chunk[K] = _keys

  def valuesChunk: Chunk[V @scala.annotation.unchecked.uncheckedVariance] = _values

  def atIndex(idx: Int): (K, V) = (_keys(idx), _values(idx))

  def keyAtIndex(idx: Int): K = _keys(idx)

  def valueAtIndex(idx: Int): V = _values(idx)

  def indexed: ChunkMap.Indexed[K, V] = new ChunkMap.Indexed(this)

  def transformValues[V2](f: V => V2): ChunkMap[K, V2] =
    new ChunkMap(_keys, _values.map(f))

  override def map[K2, V2](f: ((K, V)) => (K2, V2)): ChunkMap[K2, V2] = {
    val builder = ChunkMap.newBuilder[K2, V2]
    builder.sizeHint(size)
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      builder.addOne(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  override def flatMap[K2, V2](f: ((K, V)) => IterableOnce[(K2, V2)]): ChunkMap[K2, V2] = {
    val builder = ChunkMap.newBuilder[K2, V2]
    var idx     = 0
    val len     = _keys.length
    while (idx < len) {
      builder.addAll(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  override def filter(pred: ((K, V)) => Boolean): ChunkMap[K, V] = {
    val keysBuilder   = Chunk.newBuilder[K]
    val valuesBuilder = Chunk.newBuilder[V]
    var idx           = 0
    val len           = _keys.length
    while (idx < len) {
      val k = _keys(idx)
      val v = _values(idx)
      if (pred((k, v))) {
        keysBuilder.addOne(k)
        valuesBuilder.addOne(v)
      }
      idx += 1
    }
    new ChunkMap(keysBuilder.result(), valuesBuilder.result())
  }

  override def foreach[U](f: ((K, V)) => U): Unit = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      f((_keys(idx), _values(idx)))
      idx += 1
    }
  }

  override def foreachEntry[U](f: (K, V) => U): Unit = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      f(_keys(idx), _values(idx))
      idx += 1
    }
  }

  override def equals(that: Any): Boolean = that match {
    case m: ChunkMap[_, _] =>
      (this eq m) || (size == m.size && {
        val otherMap = m.asInstanceOf[ChunkMap[Any, Any]]
        var idx      = 0
        val len      = _keys.length
        var eq       = true
        while (idx < len && eq) {
          val k = _keys(idx)
          otherMap.get(k) match {
            case Some(otherVal) => eq = _values(idx) == otherVal
            case None           => eq = false
          }
          idx += 1
        }
        eq
      })
    case m: scala.collection.Map[_, _] =>
      size == m.size && {
        val otherMap = m.asInstanceOf[scala.collection.Map[Any, Any]]
        var idx      = 0
        val len      = _keys.length
        var eq       = true
        while (idx < len && eq) {
          val k = _keys(idx)
          otherMap.get(k) match {
            case Some(otherVal) => eq = _values(idx) == otherVal
            case None           => eq = false
          }
          idx += 1
        }
        eq
      }
    case _ => false
  }

  override def hashCode(): Int = scala.util.hashing.MurmurHash3.unorderedHash(this, "ChunkMap".hashCode)

  override def toString(): String = mkString("ChunkMap(", ", ", ")")
}

object ChunkMap extends MapFactory[ChunkMap] {

  private val _empty: ChunkMap[Nothing, Nothing] = new ChunkMap(Chunk.empty, Chunk.empty)

  def empty[K, V]: ChunkMap[K, V] = _empty.asInstanceOf[ChunkMap[K, V]]

  override def from[K, V](it: IterableOnce[(K, V)]): ChunkMap[K, V] = it match {
    case cm: ChunkMap[K @unchecked, V @unchecked] => cm
    case _                                        => (newBuilder[K, V] ++= it).result()
  }

  override def apply[K, V](elems: (K, V)*): ChunkMap[K, V] = from(elems)

  def fromChunk[K, V](chunk: Chunk[(K, V)]): ChunkMap[K, V] = {
    val len    = chunk.length
    val keys   = Chunk.newBuilder[K]
    val values = Chunk.newBuilder[V]
    keys.sizeHint(len)
    values.sizeHint(len)
    var idx = 0
    while (idx < len) {
      val (k, v) = chunk(idx)
      keys.addOne(k)
      values.addOne(v)
      idx += 1
    }
    new ChunkMap(keys.result(), values.result())
  }

  def fromChunks[K, V](keys: Chunk[K], values: Chunk[V]): ChunkMap[K, V] = {
    require(keys.length == values.length, "keys and values must have the same length")
    new ChunkMap(keys, values)
  }

  override def newBuilder[K, V]: mutable.Builder[(K, V), ChunkMap[K, V]] =
    new ChunkMapBuilder[K, V]

  final class ChunkMapBuilder[K, V] extends mutable.Builder[(K, V), ChunkMap[K, V]] {
    private val seen: mutable.LinkedHashMap[K, Int] = mutable.LinkedHashMap.empty
    private val keysBuffer: mutable.ArrayBuffer[K]  = mutable.ArrayBuffer.empty
    private val valsBuffer: mutable.ArrayBuffer[V]  = mutable.ArrayBuffer.empty

    override def addOne(elem: (K, V)): this.type = {
      val (k, v) = elem
      seen.get(k) match {
        case Some(idx) =>
          valsBuffer(idx) = v
        case None =>
          seen.put(k, keysBuffer.length)
          keysBuffer += k
          valsBuffer += v
      }
      this
    }

    override def clear(): Unit = {
      seen.clear()
      keysBuffer.clear()
      valsBuffer.clear()
    }

    override def result(): ChunkMap[K, V] = {
      val keys   = Chunk.fromIterable(keysBuffer)
      val values = Chunk.fromIterable(valsBuffer)
      new ChunkMap(keys, values)
    }

    override def sizeHint(size: Int): Unit = {
      keysBuffer.sizeHint(size)
      valsBuffer.sizeHint(size)
    }
  }

  final class Indexed[K, +V](private val underlying: ChunkMap[K, V]) extends AbstractMap[K, V] with Serializable {
    private val index: Map[K, Int] = {
      val builder = Map.newBuilder[K, Int]
      builder.sizeHint(underlying.size)
      var idx = 0
      val len = underlying._keys.length
      while (idx < len) {
        builder.addOne((underlying._keys(idx), idx))
        idx += 1
      }
      builder.result()
    }

    override def size: Int = underlying.size

    override def knownSize: Int = underlying.size

    override def isEmpty: Boolean = underlying.isEmpty

    override def get(key: K): Option[V] = index.get(key).map(underlying._values(_))

    override def contains(key: K): Boolean = index.contains(key)

    override def updated[V1 >: V](key: K, value: V1): Map[K, V1] = underlying.updated(key, value)

    override def removed(key: K): Map[K, V] = underlying.removed(key)

    override def iterator: Iterator[(K, V)] = underlying.iterator

    override def keysIterator: Iterator[K] = underlying.keysIterator

    override def valuesIterator: Iterator[V] = underlying.valuesIterator

    def toChunkMap: ChunkMap[K, V] = underlying

    def atIndex(idx: Int): (K, V) = underlying.atIndex(idx)

    def keyAtIndex(idx: Int): K = underlying.keyAtIndex(idx)

    def valueAtIndex(idx: Int): V = underlying.valueAtIndex(idx)
  }
}
