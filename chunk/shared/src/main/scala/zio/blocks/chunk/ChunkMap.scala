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

package zio.blocks.chunk

import scala.collection.immutable.{AbstractMap, MapOps, StrictOptimizedMapOps}
import scala.collection.{IterableOnce, MapFactory, mutable}

/**
 * A `ChunkMap[K, V]` is an immutable map that keeps its entries in insertion
 * order, stored as a chunk of keys and a parallel chunk of values. Use it for
 * small maps whose order matters, such as the fields of a record, or when you
 * need to read entries by position.
 *
 * Looking up a key scans the keys, so it takes time proportional to the size of
 * the map; for frequent lookups in a larger map, `indexed` adds a hash index.
 * Reading by position (`atIndex`, `keyAtIndex`, `valueAtIndex`) reads the
 * chunks directly. Equality ignores order, as for other Scala maps: a
 * `ChunkMap` equals any `scala.collection.Map` with the same entries. Its
 * `hashCode` differs from that of a standard `Map` with the same entries (a
 * known bug), so do not mix the two as keys of one hash-based collection.
 *
 * {{{
 * val years = ChunkMap("Zionomicon" -> 2021, "Programming in Scala" -> 2008)
 * years.get("Zionomicon") // Some(2021)
 * years.keyAtIndex(1)     // Programming in Scala
 * }}}
 *
 * @tparam K
 *   The key type
 * @tparam V
 *   The value type
 * @see
 *   [[https://zioblocks.com/reference/chunk/#chunkmap ChunkMap]]
 */
final class ChunkMap[K, +V] private (
  private val _keys: Chunk[K],
  private val _values: Chunk[V]
) extends AbstractMap[K, V]
    with MapOps[K, V, ChunkMap, ChunkMap[K, V]]
    with StrictOptimizedMapOps[K, V, ChunkMap, ChunkMap[K, V]]
    with Serializable { self =>

  /**
   * Returns the number of elements in the collection.
   */
  override def size: Int = _keys.length

  /**
   * Returns the number of elements in the collection.
   */
  override def knownSize: Int = _keys.length

  /**
   * Checks if the map is empty.
   */
  override def isEmpty: Boolean = _keys.isEmpty

  /**
   * Returns the value for `key`, or `None` when the map has no such key. It
   * scans the keys in order and compares them with `==`; use `indexed` for
   * faster lookups in a large map.
   *
   * {{{
   * ChunkMap("Zionomicon" -> 2021).get("Zionomicon") // Some(2021)
   * }}}
   */
  override def get(key: K): Option[V] = {
    val len = _keys.length
    var idx = 0
    while (idx < len) {
      if (_keys(idx) == key) return new Some(_values(idx))
      idx += 1
    }
    None
  }

  /**
   * Retrieves the value associated with the specified key.
   *
   * This method searches for the provided key within the internal key
   * collection. If the key is found, the corresponding value is returned. If
   * the key is not found, a `NoSuchElementException` is thrown.
   */
  override def apply(key: K): V = {
    val len = _keys.length
    var idx = 0
    while (idx < len) {
      if (_keys(idx) == key) return _values(idx)
      idx += 1
    }
    throw new NoSuchElementException(s"key not found: $key")
  }

  /**
   * Checks if the specified key exists in the collection.
   */
  override def contains(key: K): Boolean = {
    val len = _keys.length
    var idx = 0
    while (idx < len) {
      if (_keys(idx) == key) return true
      idx += 1
    }
    false
  }

  /**
   * Creates a new ChunkMap with the given key updated to the specified value.
   *
   * If the key already exists, its value is replaced with the new one. If the
   * key does not exist, it is appended to the map along with the corresponding
   * value.
   */
  override def updated[V1 >: V](key: K, value: V1): ChunkMap[K, V1] = {
    val len = _keys.length
    var idx = 0
    while (idx < len) {
      if (_keys(idx) == key) return new ChunkMap(_keys, _values.updated(idx, value))
      idx += 1
    }
    new ChunkMap(_keys.appended(key), _values.appended(value))
  }

  /**
   * Creates a new ChunkMap with the specified key removed if it exists.
   *
   * If the key is found, it is removed along with its associated value, and a
   * new ChunkMap is returned. If the key is not found, the current ChunkMap is
   * returned unchanged.
   */
  override def removed(key: K): ChunkMap[K, V] = {
    val len = _keys.length
    var idx = 0
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

  /**
   * Provides an iterator over the key-value pairs in the map.
   */
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

  /**
   * Provides an iterator over the keys in the map.
   */
  override def keysIterator: Iterator[K] = _keys.iterator

  /**
   * Provides an iterator over the values in the map.
   */
  override def valuesIterator: Iterator[V] = _values.iterator

  /**
   * Provides the map factory used to create instances of `ChunkMap`.
   */
  override def mapFactory: MapFactory[ChunkMap] = ChunkMap

  /**
   * Returns an empty instance of `ChunkMap`.
   */
  override def empty: ChunkMap[K, V] = ChunkMap.empty

  override protected def fromSpecific(
    coll: IterableOnce[(K, V @scala.annotation.unchecked.uncheckedVariance)]
  ): ChunkMap[K, V] = ChunkMap.from(coll)

  override protected def newSpecificBuilder
    : mutable.Builder[(K, V @scala.annotation.unchecked.uncheckedVariance), ChunkMap[K, V]] = ChunkMap.newBuilder[K, V]

  /**
   * Concatenates the current `ChunkMap` with the specified iterable of
   * key-value pairs, producing a new `ChunkMap`.
   */
  override def concat[V2 >: V](suffix: IterableOnce[(K, V2)]): ChunkMap[K, V2] = {
    val builder = new ChunkMap.ChunkMapBuilder[K, V2](this.size + Math.max(suffix.knownSize, 0))
    builder.addAll(this)
    builder.addAll(suffix)
    builder.result()
  }

  /**
   * Converts the internal key and value collections of the `ChunkMap` into a
   * `Chunk` of key-value pairs.
   */
  def toChunk: Chunk[(K, V)] = {
    val len    = _keys.length
    val result = new Array[(K, V)](len)
    var idx    = 0
    while (idx < len) {
      result(idx) = (_keys(idx), _values(idx))
      idx += 1
    }
    Chunk.fromArray(result)
  }

  /**
   * Retrieves a chunk containing all the keys in the `ChunkMap`.
   */
  def keysChunk: Chunk[K] = _keys

  /**
   * Retrieves a chunk containing all the values stored in the `ChunkMap`.
   */
  def valuesChunk: Chunk[V] = _values

  /**
   * Retrieves the key-value pair at the specified index in the map, counting
   * entries in insertion order from 0. An out-of-range `idx` usually throws an
   * `IndexOutOfBoundsException`, but on a map returned by `removed` it can
   * return a removed entry instead (a known bug).
   */
  def atIndex(idx: Int): (K, V) = (_keys(idx), _values(idx))

  /**
   * Returns the key at position `idx`, counting entries in insertion order from 0.
   * An out-of-range `idx` usually throws an `IndexOutOfBoundsException`, but on
   * a map returned by `removed` it can return a removed entry instead (a known
   * bug).
   *
   * {{{
   * val years = ChunkMap("Zionomicon" -> 2021, "Programming in Scala" -> 2008)
   * years.keyAtIndex(1) // Programming in Scala
   * }}}
   */
  def keyAtIndex(idx: Int): K = _keys(idx)

  /**
   * Retrieves the value at the specified index in the map, counting entries in
   * insertion order from 0. An out-of-range `idx` usually throws an
   * `IndexOutOfBoundsException`, but on a map returned by `removed` it can
   * return a removed entry instead (a known bug).
   */
  def valueAtIndex(idx: Int): V = _values(idx)

  /**
   * Returns this map with a hash index on its keys, so `get` and `contains`
   * take constant time on average instead of scanning the keys. Building the
   * index takes time and memory proportional to the size of the map, so keep
   * the result and reuse it. `updated` and `removed` on the result return a
   * plain `ChunkMap`, without the index.
   *
   * {{{
   * val index = ChunkMap("Zionomicon" -> 2021, "Programming in Scala" -> 2008).indexed
   * index.get("Programming in Scala") // Some(2008)
   * }}}
   */
  def indexed: ChunkMap.Indexed[K, V] = new ChunkMap.Indexed(this)

  /**
   * Transforms the values in the ChunkMap by applying the specified function
   * `f` to each value.
   *
   * A new ChunkMap with the transformed values is returned while retaining the
   * existing keys.
   */
  def transformValues[V2](f: V => V2): ChunkMap[K, V2] = new ChunkMap(_keys, _values.map(f))

  /**
   * Applies the given transformation function `f` to each key-value pair in the
   * ChunkMap and produces a new ChunkMap with the transformed key-value pairs.
   */
  override def map[K2, V2](f: ((K, V)) => (K2, V2)): ChunkMap[K2, V2] = {
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val builder = new ChunkMap.ChunkMapBuilder[K2, V2](len)
    var idx     = 0
    while (idx < len) {
      builder.addOne(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  /**
   * Transforms this ChunkMap by applying the given function `f` to each
   * key-value pair and flattens the results into a new ChunkMap.
   */
  override def flatMap[K2, V2](f: ((K, V)) => IterableOnce[(K2, V2)]): ChunkMap[K2, V2] = {
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val builder = new ChunkMap.ChunkMapBuilder[K2, V2](len)
    var idx     = 0
    while (idx < len) {
      builder.addAll(f((_keys(idx), _values(idx))))
      idx += 1
    }
    builder.result()
  }

  /**
   * Filters the elements of the ChunkMap based on a predicate.
   */
  override def filter(pred: ((K, V)) => Boolean): ChunkMap[K, V] = {
    val len = _keys.length
    if (len == 0) return ChunkMap.empty
    val keysBuilder   = ChunkBuilder.make[K]()
    val valuesBuilder = ChunkBuilder.make[V]()
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
    new ChunkMap(keysBuilder.resultUnsafe(), valuesBuilder.resultUnsafe())
  }

  /**
   * Applies a given function to each key-value pair in the collection.
   */
  override def foreach[U](f: ((K, V)) => U): Unit = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      f((_keys(idx), _values(idx)))
      idx += 1
    }
  }

  /**
   * Applies the specified function `f` to each key-value pair in the map.
   */
  override def foreachEntry[U](f: (K, V) => U): Unit = {
    var idx = 0
    val len = _keys.length
    while (idx < len) {
      f(_keys(idx), _values(idx))
      idx += 1
    }
  }

  /**
   * Returns `true` when `that` is a `ChunkMap` or another
   * `scala.collection.Map` with the same size and the same value for every key.
   * The order of the entries does not matter.
   */
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
            case _              => eq = false
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
            case _              => eq = false
          }
          idx += 1
        }
        eq
      }
    case _ => false
  }

  /**
   * Computes the hash code for the ChunkMap instance.
   *
   * The hash code is generated using the MurmurHash3 algorithm, which computes
   * an unordered hash of the object based on its contents and a predefined hash
   * seed. This implementation ensures that equivalent ChunkMap instances
   * produce the same hash code. It differs from the hash code of a standard
   * `Map` with the same entries, although `equals` reports the two as equal (a
   * known bug).
   */
  override def hashCode(): Int = scala.util.hashing.MurmurHash3.unorderedHash(this, "ChunkMap".hashCode)

  /**
   * Converts the ChunkMap object to its string representation.
   */
  override def toString(): String = mkString("ChunkMap(", ", ", ")")
}

/**
 * Creates `ChunkMap` values: `apply` and `from` from key-value pairs,
 * `fromChunk` from a chunk of pairs, `fromChunks` from parallel chunks of keys
 * and values, and `newBuilder` for adding one entry at a time.
 */
object ChunkMap extends MapFactory[ChunkMap] {
  private[this] val _empty: ChunkMap[Nothing, Nothing] = new ChunkMap(Chunk.empty, Chunk.empty)

  /**
   * Returns an empty instance of `ChunkMap`.
   */
  def empty[K, V]: ChunkMap[K, V] = _empty.asInstanceOf[ChunkMap[K, V]]

  /**
   * Constructs a `ChunkMap` from an `IterableOnce` of key-value pairs.
   */
  override def from[K, V](it: IterableOnce[(K, V)]): ChunkMap[K, V] = it match {
    case cm: ChunkMap[K @unchecked, V @unchecked] => cm
    case _                                        =>
      new ChunkMapBuilder[K, V](Math.max(it.knownSize, 0)).addAll(it).result()
  }

  /**
   * Returns a `ChunkMap` with the given entries, in order. When a key appears
   * more than once, the map keeps its last value at the position of its first
   * occurrence.
   *
   * {{{
   * val years =
   *   ChunkMap("Zionomicon" -> 2021, "Programming in Scala" -> 2008, "Zionomicon" -> 2024)
   * years.toList // List((Zionomicon,2024), (Programming in Scala,2008))
   * }}}
   */
  override def apply[K, V](elems: (K, V)*): ChunkMap[K, V] = from(elems)

  /**
   * Creates a ChunkMap from a Chunk of key-value pairs, handling duplicate keys
   * by keeping the last value for each key (preserving the position of the
   * first occurrence).
   *
   * {{{
   * ChunkMap.fromChunk(Chunk("Zionomicon" -> 2021, "Zionomicon" -> 2024)) // ChunkMap(Zionomicon -> 2024)
   * }}}
   */
  def fromChunk[K, V](chunk: Chunk[(K, V)]): ChunkMap[K, V] = from(chunk)

  /**
   * Creates a ChunkMap from parallel Chunks of keys and values.
   *
   * Precondition: The keys must not contain duplicate elements. If duplicate
   * keys are present, the behavior is undefined (lookups may return any of the
   * duplicate values). Use `from()` or the builder if duplicate handling is
   * needed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if keys and values have different lengths
   */
  def fromChunks[K, V](keys: Chunk[K], values: Chunk[V]): ChunkMap[K, V] = {
    require(keys.length == values.length, "keys and values must have the same length")
    new ChunkMap(keys, values)
  }

  /**
   * Creates a new builder for constructing a `ChunkMap` from key-value pairs.
   */
  override def newBuilder[K, V]: mutable.Builder[(K, V), ChunkMap[K, V]] = new ChunkMapBuilder[K, V]

  /**
   * A mutable builder for constructing instances of `ChunkMap`.
   *
   * This builder allows efficient assembly of a `ChunkMap` by enabling
   * progressive addition of key-value pairs. Duplicate keys are supported; in
   * case of duplicates, the latest associated value overwrites the previous
   * value.
   *
   * @tparam K
   *   The type of keys in the map.
   * @tparam V
   *   The type of values in the map.
   * @constructor
   *   Initializes the builder with a specified initial capacity for internal
   *   buffers.
   * @param initCapacity
   *   The initial capacity for the internal storage of keys and values.
   *   Defaults to 4 if not explicitly provided.
   *
   * Methods:
   *   - `addOne`: Adds a single key-value pair to the map being built.
   *   - `clear`: Clears all key-value pairs from the builder, resetting its
   *     state.
   *   - `knownSize`: Returns the current size of the map being built.
   *   - `result`: Finalizes the construction and produces an immutable
   *     `ChunkMap` instance.
   *   - `sizeHint`: meant to reserve room for a number of entries; see its own
   *     comment for a known bug.
   */
  final class ChunkMapBuilder[K, V](initCapacity: Int = 4) extends mutable.Builder[(K, V), ChunkMap[K, V]] {
    private[this] val seen: java.util.HashMap[K, Int] = new java.util.HashMap(initCapacity << 1, 0.5f)
    private[this] var keysBuffer: Array[AnyRef]       = new Array[AnyRef](initCapacity)
    private[this] var valsBuffer: Array[AnyRef]       = new Array[AnyRef](initCapacity)
    private[this] var size                            = 0

    /**
     * Adds a key-value pair to the builder.
     *
     * If the key already exists, its associated value is updated.
     */
    def add(key: K, value: V): Unit = {
      val idx = seen.getOrDefault(key, -1)
      if (idx >= 0) valsBuffer(idx) = value.asInstanceOf[AnyRef]
      else {
        val idx = size
        seen.put(key, idx)
        val len = keysBuffer.length
        if (idx == len) {
          val newLen = Math.max(len << 1, 4)
          keysBuffer = java.util.Arrays.copyOf(keysBuffer, newLen)
          valsBuffer = java.util.Arrays.copyOf(valsBuffer, newLen)
        }
        keysBuffer(idx) = key.asInstanceOf[AnyRef]
        valsBuffer(idx) = value.asInstanceOf[AnyRef]
        size += 1
      }
    }

    /**
     * Adds a key-value pair to the builder.
     *
     * If the key already exists, its associated value is updated.
     */
    override def addOne(elem: (K, V)): this.type = {
      add(elem._1, elem._2)
      this
    }

    /**
     * Removes all elements from the builder and resets its size to zero.
     */
    override def clear(): Unit = {
      seen.clear()
      size = 0
    }

    /**
     * Returns the number of elements in the builder.
     */
    override def knownSize: Int = size

    /**
     * Constructs and returns a new `ChunkMap` containing the key-value pairs
     * added to the builder.
     */
    override def result(): ChunkMap[K, V] = new ChunkMap(
      Chunk.fromArray(java.util.Arrays.copyOf(keysBuffer, size).asInstanceOf[Array[K]]),
      Chunk.fromArray(java.util.Arrays.copyOf(valsBuffer, size).asInstanceOf[Array[V]])
    )

    /**
     * Meant to grow the internal storage to hold `hint` entries. When `hint`
     * exceeds the current capacity, it instead shrinks the storage to the
     * current number of entries (a known bug); no entry is lost, but the next
     * `add` has to grow the storage again.
     */
    override def sizeHint(hint: Int): Unit = if (hint > keysBuffer.length) {
      keysBuffer = java.util.Arrays.copyOf(keysBuffer, size)
      valsBuffer = java.util.Arrays.copyOf(valsBuffer, size)
    }
  }

  /**
   * Represents an immutable map with indexed access, implemented on top of a
   * `ChunkMap`.
   *
   * This class provides efficient indexed access to keys and values within the
   * underlying `ChunkMap`, while also exposing standard map operations.
   * Internally, it maintains an index for fast key lookup.
   *
   * @tparam K
   *   the type of keys in the map
   * @tparam V
   *   the type of values in the map
   * @constructor
   *   Creates a new `Indexed` map from the given `ChunkMap`.
   * @param underlying
   *   the `ChunkMap` serving as the backing storage for the map
   */
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

    /**
     * Returns the number of elements in the collection.
     */
    override def size: Int = underlying.size

    /**
     * Returns the number of elements in the collection.
     */
    override def knownSize: Int = underlying.size

    /**
     * Determines whether the collection is empty.
     */
    override def isEmpty: Boolean = underlying.isEmpty

    /**
     * Retrieves the value associated with the specified key.
     */
    override def get(key: K): Option[V] = {
      val idx = index.getOrDefault(key, -1)
      if (idx >= 0) new Some(underlying._values(idx))
      else None
    }

    /**
     * Checks if the specified key exists in the underlying data structure.
     */
    override def contains(key: K): Boolean = index.getOrDefault(key, -1) >= 0

    /**
     * Creates a new map instance with the specified key updated to the given
     * value.
     *
     * If the key already exists in the map, its value is replaced with the new
     * one. If the key does not exist, it is added to the map with the specified
     * value. The result is a plain `ChunkMap`, without the index.
     */
    override def updated[V1 >: V](key: K, value: V1): Map[K, V1] = underlying.updated(key, value)

    /**
     * Removes the mapping for a specified key from the map if it exists.
     *
     * If the key is found, it is removed along with its associated value.
     * Returns a new map reflecting the removal: a plain `ChunkMap`, without the
     * index. If the key is not found, the underlying `ChunkMap` is returned
     * unchanged.
     */
    override def removed(key: K): Map[K, V] = underlying.removed(key)

    /**
     * Provides an iterator over the key-value pairs in the collection.
     */
    override def iterator: Iterator[(K, V)] = underlying.iterator

    /**
     * Provides an iterator over keys in the collection.
     */
    override def keysIterator: Iterator[K] = underlying.keysIterator

    /**
     * Provides an iterator over values in the collection.
     */
    override def valuesIterator: Iterator[V] = underlying.valuesIterator

    /**
     * Returns underlying non-indexed `ChunkMap`.
     */
    def toChunkMap: ChunkMap[K, V] = underlying

    /**
     * Retrieves the key-value pair at the specified index in the map.
     */
    def atIndex(idx: Int): (K, V) = underlying.atIndex(idx)

    /**
     * Retrieves a key at the specified index in the map.
     */
    def keyAtIndex(idx: Int): K = underlying.keyAtIndex(idx)

    /**
     * Retrieves a value at the specified index in the map.
     */
    def valueAtIndex(idx: Int): V = underlying.valueAtIndex(idx)
  }
}
