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
  private val _keys: Chunk[K],
  private val _values: Chunk[V]
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
      if (_keys(idx) == key) return new Some(_values(idx))
      idx += 1
    }
    None
  }

  override def apply(key: K): V = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      if (_keys(idx) == key) return _values(idx)
      idx += 1
    }
    throw new NoSuchElementException(s"key not found: $key")
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
      if (_keys(idx) == key) return new ChunkMap(_keys, _values.updated(idx, value))
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
    private[this] var idx = 0
    private[this] val len = _keys.length

    override def hasNext: Boolean = idx < len

    override def next(): (K, V) = {
      if (idx >= len) throw new NoSuchElementException("next on empty iterator")
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
  ): ChunkMap[K, V] = ChunkMap.from(coll)

  override protected def newSpecificBuilder
    : mutable.Builder[(K, V @scala.annotation.unchecked.uncheckedVariance), ChunkMap[K, V]] = ChunkMap.newBuilder[K, V]

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
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val builder = ChunkMap.newBuilder[K2, V2]
    builder.sizeHint(len)
    var idx = 0
    while (idx < len) {
      builder.addOne(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  override def flatMap[K2, V2](f: ((K, V)) => IterableOnce[(K2, V2)]): ChunkMap[K2, V2] = {
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val builder = ChunkMap.newBuilder[K2, V2]
    builder.sizeHint(len)
    var idx = 0
    while (idx < len) {
      builder.addAll(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  override def filter(pred: ((K, V)) => Boolean): ChunkMap[K, V] = {
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val keysBuilder   = Chunk.newBuilder[K]
    val valuesBuilder = Chunk.newBuilder[V]
    var idx           = 0
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
    case _                                        => newBuilder[K, V].addAll(it).result()
  }

  override def apply[K, V](elems: (K, V)*): ChunkMap[K, V] = from(elems)

  /**
   * Creates a ChunkMap from a Chunk of key-value pairs, handling duplicate keys
   * by keeping the last value for each key (preserving the position of the
   * first occurrence).
   */
  def fromChunk[K, V](chunk: Chunk[(K, V)]): ChunkMap[K, V] =
    from(chunk)

  /**
   * Creates a ChunkMap from parallel Chunks of keys and values.
   *
   * '''Precondition:''' The keys Chunk must not contain duplicate elements. If
   * duplicate keys are present, behavior is undefined (lookups may return any
   * of the duplicate values). Use `from()` or the builder if duplicate handling
   * is needed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if keys and values have different lengths
   */
  def fromChunks[K, V](keys: Chunk[K], values: Chunk[V]): ChunkMap[K, V] = {
    require(keys.length == values.length, "keys and values must have the same length")
    new ChunkMap(keys, values)
  }

  override def newBuilder[K, V]: mutable.Builder[(K, V), ChunkMap[K, V]] = new ChunkMapBuilder[K, V]

  final class ChunkMapBuilder[K, V] extends mutable.Builder[(K, V), ChunkMap[K, V]] {
    private[this] val seen: java.util.HashMap[K, Int] = new java.util.HashMap(4)
    private[this] var keysBuffer: Array[AnyRef]       = new Array[AnyRef](4)
    private[this] var valsBuffer: Array[AnyRef]       = new Array[AnyRef](4)
    private[this] var size                            = 0

    override def addOne(elem: (K, V)): this.type = {
      val (k, v) = elem
      val idx    = seen.getOrDefault(k, -1)
      if (idx >= 0) valsBuffer(idx) = v.asInstanceOf[AnyRef]
      else {
        val idx = size
        seen.put(k, idx)
        val len = keysBuffer.length
        if (idx == len) {
          val newLen = Math.max(len << 1, 4)
          keysBuffer = java.util.Arrays.copyOf(keysBuffer, newLen)
          valsBuffer = java.util.Arrays.copyOf(valsBuffer, newLen)
        }
        keysBuffer(idx) = k.asInstanceOf[AnyRef]
        valsBuffer(idx) = v.asInstanceOf[AnyRef]
        size += 1
      }
      this
    }

    override def clear(): Unit = {
      seen.clear()
      size = 0
    }

    override def result(): ChunkMap[K, V] = new ChunkMap(
      Chunk.fromArray(java.util.Arrays.copyOf(keysBuffer, size).asInstanceOf[Array[K]]),
      Chunk.fromArray(java.util.Arrays.copyOf(valsBuffer, size).asInstanceOf[Array[V]])
    )

    override def sizeHint(hint: Int): Unit = if (hint > keysBuffer.length) {
      keysBuffer = java.util.Arrays.copyOf(keysBuffer, size)
      valsBuffer = java.util.Arrays.copyOf(valsBuffer, size)
    }
  }

  final class Indexed[K, +V](val underlying: ChunkMap[K, V]) extends AbstractMap[K, V] with Serializable {
    private[this] val index: java.util.Map[K, Int] = {
      val keys = underlying._keys
      val len  = keys.length
      val map  = new java.util.HashMap[K, Int](len)
      var idx  = 0
      while (idx < len) {
        map.put(keys(idx), idx)
        idx += 1
      }
      map
    }

    override def size: Int = underlying.size

    override def knownSize: Int = underlying.size

    override def isEmpty: Boolean = underlying.isEmpty

    override def get(key: K): Option[V] = {
      val idx = index.getOrDefault(key, -1)
      if (idx >= 0) new Some(underlying._values(idx))
      else None
    }

    override def contains(key: K): Boolean = index.getOrDefault(key, -1) >= 0

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
