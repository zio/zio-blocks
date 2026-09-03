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

package zio.http

import zio.blocks.chunk.Chunk

/**
 * Immutable multi-map of query parameters, backed by parallel arrays.
 *
 * Keys may appear at most once; each key maps to one or more values stored as a
 * `Chunk[String]`. Values are stored decoded; use `encode` to produce a
 * percent-encoded query string and `QueryParams.fromEncoded` to parse one.
 */
final class QueryParams private[http] (
  private val keys: Array[String],
  private val vals: Array[Chunk[String]],
  val size: Int
) {

  def isEmpty: Boolean  = size == 0
  def nonEmpty: Boolean = size > 0

  def get(key: String): Option[Chunk[String]] = {
    var i = 0
    while (i < size) {
      if (keys(i) == key) return Some(vals(i))
      i += 1
    }
    None
  }

  def getFirst(key: String): Option[String] = get(key).flatMap(_.headOption)

  def has(key: String): Boolean = {
    var i = 0
    while (i < size) {
      if (keys(i) == key) return true
      i += 1
    }
    false
  }

  /**
   * Returns a copy with one entry appended.
   *
   * Each `add` copies via a temporary builder, so appending `n` entries one by
   * one costs O(n²). For multi-add loops prefer [[QueryParamsBuilder]] and call
   * `build()` once.
   */
  def add(key: String, value: String): QueryParams = {
    val builder = QueryParamsBuilder.make(size + 1)
    builder.addAll(this)
    builder.add(key, value)
    builder.build()
  }

  def set(key: String, value: String): QueryParams = {
    val builder = QueryParamsBuilder.make(size)
    var i       = 0
    while (i < size) {
      if (keys(i) != key) builder.addEntry(keys(i), vals(i))
      i += 1
    }
    builder.add(key, value)
    builder.build()
  }

  def remove(key: String): QueryParams = {
    val builder = QueryParamsBuilder.make(size)
    var i       = 0
    while (i < size) {
      if (keys(i) != key) builder.addEntry(keys(i), vals(i))
      i += 1
    }
    builder.build()
  }

  def encode: String = {
    if (size == 0) return ""
    val sb = new StringBuilder
    var i  = 0
    while (i < size) {
      val values = vals(i)
      var j      = 0
      while (j < values.length) {
        if (sb.nonEmpty) sb.append('&')
        sb.append(PercentEncoder.encode(keys(i), PercentEncoder.ComponentType.QueryKey))
        sb.append('=')
        sb.append(PercentEncoder.encode(values(j), PercentEncoder.ComponentType.QueryValue))
        j += 1
      }
      i += 1
    }
    sb.toString
  }

  def toList: List[(String, String)] = {
    val builder = List.newBuilder[(String, String)]
    var i       = 0
    while (i < size) {
      val values = vals(i)
      var j      = 0
      while (j < values.length) {
        builder += ((keys(i), values(j)))
        j += 1
      }
      i += 1
    }
    builder.result()
  }

  def ++(other: QueryParams): QueryParams = {
    if (other.isEmpty) return this
    if (isEmpty) return other
    val builder = QueryParamsBuilder.make(size + other.size)
    var i       = 0
    while (i < size) {
      val values = vals(i)
      var j      = 0
      while (j < values.length) {
        builder.add(keys(i), values(j))
        j += 1
      }
      i += 1
    }
    i = 0
    while (i < other.size) {
      val values = other.vals(i)
      var j      = 0
      while (j < values.length) {
        builder.add(other.keys(i), values(j))
        j += 1
      }
      i += 1
    }
    builder.build()
  }

  def addAll(other: QueryParams): QueryParams = this ++ other

  def toMap: Map[String, Chunk[String]] = {
    val builder = Map.newBuilder[String, Chunk[String]]
    var i       = 0
    while (i < size) {
      builder += ((keys(i), vals(i)))
      i += 1
    }
    builder.result()
  }

  private[http] def forEachEntry(f: (String, Chunk[String]) => Unit): Unit = {
    var i = 0
    while (i < size) {
      f(keys(i), vals(i))
      i += 1
    }
  }

  def filter(f: (String, Chunk[String]) => Boolean): QueryParams = {
    val builder = QueryParamsBuilder.make(size)
    var i       = 0
    while (i < size) {
      if (f(keys(i), vals(i))) builder.addEntry(keys(i), vals(i))
      i += 1
    }
    builder.build()
  }

  override def equals(that: Any): Boolean = that match {
    case q: QueryParams => toList == q.toList
    case _              => false
  }

  override def hashCode: Int = toList.hashCode

  override def toString: String = s"QueryParams(${toList.mkString(", ")})"
}

object QueryParams {
  val empty: QueryParams = new QueryParams(Array.empty, Array.empty, 0)

  def apply(pairs: (String, String)*): QueryParams = {
    val builder = QueryParamsBuilder.make(pairs.size)
    pairs.foreach { case (k, v) => builder.add(k, v) }
    builder.build()
  }

  def fromEncoded(s: String): QueryParams = {
    if (s.isEmpty) return empty
    val builder = QueryParamsBuilder.make(8)
    val pairs   = s.split('&')
    var i       = 0
    while (i < pairs.length) {
      val pair  = pairs(i)
      val eqIdx = pair.indexOf('=')
      if (eqIdx >= 0) {
        val key   = PercentEncoder.decode(pair.substring(0, eqIdx))
        val value = PercentEncoder.decode(pair.substring(eqIdx + 1))
        builder.add(key, value)
      } else {
        builder.add(PercentEncoder.decode(pair), "")
      }
      i += 1
    }
    builder.build()
  }
}

/**
 * Amortized multi-add builder for [[QueryParams]].
 *
 * `add` scans the accumulated keys linearly up to a small size threshold
 * ([[QueryParamsBuilder.IndexThreshold]]), past which a `HashMap` key-to-index
 * table keeps lookups O(1). Prefer this over chained [[QueryParams.add]] calls
 * when adding more than a couple of entries.
 */
final class QueryParamsBuilder private (
  private var keys: Array[String],
  private var vals: Array[Chunk[String]],
  private var len: Int
) {
  private var index: java.util.HashMap[String, Integer] = null

  def add(key: String, value: String): Unit = {
    val found = findKey(key)
    if (found >= 0) {
      vals(found) = vals(found) :+ value
      return
    }
    ensureCapacity()
    keys(len) = key
    vals(len) = Chunk.single(value)
    len += 1
    if (index != null) index.put(key, Integer.valueOf(len - 1))
    else if (len >= QueryParamsBuilder.IndexThreshold) rebuildIndex()
  }

  private def findKey(key: String): Int =
    if (index != null) {
      val found = index.get(key)
      if (found == null) -1 else found.intValue()
    } else {
      var i = 0
      while (i < len) {
        if (keys(i) == key) return i
        i += 1
      }
      -1
    }

  private def rebuildIndex(): Unit = {
    val table = new java.util.HashMap[String, Integer](Math.max(len * 2, 16))
    var i     = 0
    while (i < len) {
      table.put(keys(i), Integer.valueOf(i))
      i += 1
    }
    index = table
  }

  private[http] def addEntry(key: String, values: Chunk[String]): Unit = {
    ensureCapacity()
    keys(len) = key
    vals(len) = values
    len += 1
    if (index != null) index.put(key, Integer.valueOf(len - 1))
    else if (len >= QueryParamsBuilder.IndexThreshold) rebuildIndex()
  }

  def addAll(params: QueryParams): Unit =
    params.forEachEntry { case (k, vs) =>
      var j = 0
      while (j < vs.length) {
        add(k, vs(j))
        j += 1
      }
    }

  def reset(): Unit = {
    java.util.Arrays.fill(keys.asInstanceOf[Array[AnyRef]], 0, len, null)
    java.util.Arrays.fill(vals.asInstanceOf[Array[AnyRef]], 0, len, null)
    len = 0
    index = null
  }

  private def ensureCapacity(): Unit =
    if (len >= keys.length) {
      val newCap  = Math.max(keys.length * 2, 8)
      val newKeys = new Array[String](newCap)
      val newVals = new Array[Chunk[String]](newCap)
      System.arraycopy(keys, 0, newKeys, 0, len)
      System.arraycopy(vals, 0, newVals, 0, len)
      keys = newKeys
      vals = newVals
    }

  def build(): QueryParams = {
    val k = new Array[String](len)
    val v = new Array[Chunk[String]](len)
    System.arraycopy(keys, 0, k, 0, len)
    System.arraycopy(vals, 0, v, 0, len)
    new QueryParams(k, v, len)
  }
}

object QueryParamsBuilder {
  private val IndexThreshold: Int = 8

  def make(initialCapacity: Int = 8): QueryParamsBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new QueryParamsBuilder(new Array[String](cap), new Array[Chunk[String]](cap), 0)
  }
}
