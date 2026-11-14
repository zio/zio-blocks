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
 * The query parameters of a URL: an immutable map from each key to one or more
 * values, with keys in insertion order (`set` moves its key to the end). A
 * repeated key, as in `?tag=tea&tag=green`, keeps all its values under one
 * entry. Keys and values hold decoded text; [[encode]] percent-encodes them for
 * the wire and [[QueryParams.fromEncoded]] parses a query string. Keys are
 * compared exactly, including letter case.
 *
 * {{{
 * val params = QueryParams.fromEncoded("page=2&sort=price&tag=tea&tag=green")
 *
 * params.getFirst("page") // Some(2)
 * params.get("tag")       // Some(Chunk(tea,green))
 * params.get("Page")      // None
 * params.add("tag", "new").remove("page").encode // sort=price&tag=tea&tag=green&tag=new
 * }}}
 *
 * Equality compares [[toList]], so it depends on key order:
 * `QueryParams("a" -> "1", "b" -> "2")` and
 * `QueryParams("b" -> "2", "a" -> "1")` are not equal.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#queryparams QueryParams]]
 */
final class QueryParams private[http] (
  private val keys: Array[String],
  private val vals: Array[Chunk[String]],
  val size: Int
) {

  /** Whether there are no parameters. */
  def isEmpty: Boolean = size == 0

  /** Whether there is at least one parameter. */
  def nonEmpty: Boolean = size > 0

  /**
   * Returns every value of `key`, in order, or `None` when the key is absent.
   * The key must match exactly, including letter case.
   *
   * {{{
   * val params = QueryParams("id" -> "1", "id" -> "2", "id" -> "3")
   *
   * params.get("id")      // Some(Chunk(1,2,3))
   * params.getFirst("id") // Some(1)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/model/#multi-value-parameters Multi-Value Parameters]]
   */
  def get(key: String): Option[Chunk[String]] = {
    var i = 0
    while (i < size) {
      if (keys(i) == key) return Some(vals(i))
      i += 1
    }
    None
  }

  /**
   * Returns the first value of `key`, or `None` when the key is absent. A key
   * without a value, as in `?draft`, gives `Some("")`.
   */
  def getFirst(key: String): Option[String] = get(key).flatMap(_.headOption)

  /** Whether `key` is present, matching it exactly. */
  def has(key: String): Boolean = {
    var i = 0
    while (i < size) {
      if (keys(i) == key) return true
      i += 1
    }
    false
  }

  /**
   * Returns the key of the entry at `index`, for trusted same-package scans
   * that must not allocate. The index must be below [[size]].
   */
  private[http] def keyAt(index: Int): String = keys(index)

  /**
   * Returns how many values the entry at `index` holds, for trusted
   * same-package scans that must not allocate. The index must be below
   * [[size]].
   */
  private[http] def valueCountAt(index: Int): Int = vals(index).length

  /**
   * Returns the `valueIndex`-th value of the entry at `index`, for trusted
   * same-package scans that must not allocate. Both indexes must be in range.
   */
  private[http] def valueAt(index: Int, valueIndex: Int): String = vals(index)(valueIndex)

  /**
   * Adds `value` after the existing values of `key`, or adds the key at the end
   * when it is new.
   *
   * {{{
   * QueryParams("page" -> "1").add("page", "2").add("sort", "price").toList
   * // List((page,1), (page,2), (sort,price))
   * }}}
   */
  def add(key: String, value: String): QueryParams = {
    val builder = QueryParamsBuilder.make(size + 1)
    builder.addAll(this)
    builder.add(key, value)
    builder.build()
  }

  /**
   * Replaces every value of `key` with the single `value`. The key moves to the
   * end: `QueryParams("page" -> "1", "sort" -> "price").set("page", "2")`
   * encodes as `sort=price&page=2`.
   */
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

  /** Removes `key` and all its values; an absent key changes nothing. */
  def remove(key: String): QueryParams = {
    val builder = QueryParamsBuilder.make(size)
    var i       = 0
    while (i < size) {
      if (keys(i) != key) builder.addEntry(keys(i), vals(i))
      i += 1
    }
    builder.build()
  }

  /**
   * Renders the parameters as a query string without the leading `?`: one
   * `key=value` pair per value, joined with `&`, grouped by key in key order.
   * Keys and values are percent-encoded: a space becomes `%20`, `&` becomes
   * `%26`, `=` is encoded in keys but not in values, and `+`, `/` and `?` stay
   * as they are. A key without a value is written `key=`. The empty parameters
   * give the empty string.
   *
   * {{{
   * QueryParams("q" -> "tea cups", "filter" -> "a&b=c").encode // q=tea%20cups&filter=a%26b=c
   * QueryParams.fromEncoded("tag=a&page=2&tag=b").encode       // tag=a&tag=b&page=2
   * }}}
   */
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

  /**
   * Returns one (key, value) pair per value, grouped by key in key order.
   */
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

  /**
   * Adds every value of `other` to these parameters, as [[add]] does: values of
   * a key present in both are appended after this side's values.
   */
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

  /** Alias for `++`. */
  def addAll(other: QueryParams): QueryParams = this ++ other

  /** Returns the parameters as a `Map` from each key to its values. */
  def toMap: Map[String, Chunk[String]] = {
    val builder = Map.newBuilder[String, Chunk[String]]
    var i       = 0
    while (i < size) {
      builder += ((keys(i), vals(i)))
      i += 1
    }
    builder.result()
  }

  /**
   * Keeps the keys for which `f`, given the key and all its values, returns
   * `true`.
   */
  def filter(f: (String, Chunk[String]) => Boolean): QueryParams = {
    val builder = QueryParamsBuilder.make(size)
    var i       = 0
    while (i < size) {
      if (f(keys(i), vals(i))) builder.addEntry(keys(i), vals(i))
      i += 1
    }
    builder.build()
  }

  /**
   * Two `QueryParams` are equal when [[toList]] is equal, so key order matters.
   */
  override def equals(that: Any): Boolean = that match {
    case q: QueryParams => toList == q.toList
    case _              => false
  }

  /** A hash consistent with `equals`. */
  override def hashCode: Int = toList.hashCode

  /**
   * Renders the pairs of [[toList]], as in `QueryParams((page,2), (tag,tea))`.
   */
  override def toString: String = s"QueryParams(${toList.mkString(", ")})"
}

/**
 * Creates [[QueryParams]].
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#creating-queryparams Creating QueryParams]]
 */
object QueryParams {

  /** No parameters. */
  val empty: QueryParams = new QueryParams(Array.empty, Array.empty, 0)

  /**
   * Creates parameters from decoded key and value pairs, in order. Pairs with
   * the same key are collected under that key.
   *
   * {{{
   * QueryParams("page" -> "2", "tag" -> "tea", "tag" -> "green").get("tag") // Some(Chunk(tea,green))
   * }}}
   */
  def apply(pairs: (String, String)*): QueryParams = {
    val builder = QueryParamsBuilder.make(pairs.size)
    pairs.foreach { case (k, v) => builder.add(k, v) }
    builder.build()
  }

  /**
   * Parses a query string without the leading `?` (a leading `?` becomes part
   * of the first key). It splits at `&`, takes the text before the first `=` as
   * the key and the rest as the value, and percent-decodes both; a part without
   * `=` is a key with the empty value. An empty part between two `&` becomes an
   * empty key with an empty value, while empty parts at the end are dropped.
   * Nothing is trimmed.
   *
   * A `+` stays a plus sign rather than becoming a space, so a query from an
   * HTML form sent with `GET` (`q=tea+cups`) reads as `tea+cups`; this is a
   * known bug.
   *
   * {{{
   * QueryParams.fromEncoded("q=tea%20cups&page=2").getFirst("q") // Some(tea cups)
   * QueryParams.fromEncoded("draft&page=").toList               // List((draft,), (page,))
   * QueryParams.fromEncoded("q=tea+cups").getFirst("q")         // Some(tea+cups)
   * }}}
   */
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
 * A mutable buffer that collects query parameters and builds one
 * [[QueryParams]] at the end, collecting values of a repeated key under that
 * key. It is not thread-safe.
 *
 * {{{
 * val builder = QueryParamsBuilder.make()
 * builder.add("tag", "tea")
 * builder.add("page", "2")
 * builder.add("tag", "green")
 *
 * builder.build().encode // tag=tea&tag=green&page=2
 * }}}
 */
final class QueryParamsBuilder private (
  private var keys: Array[String],
  private var vals: Array[Chunk[String]],
  private var len: Int
) {

  /** Adds `value` after the existing values of `key`, or adds a new key. */
  def add(key: String, value: String): Unit = {
    var i = 0
    while (i < len) {
      if (keys(i) == key) {
        vals(i) = vals(i) :+ value
        return
      }
      i += 1
    }
    ensureCapacity()
    keys(len) = key
    vals(len) = Chunk.single(value)
    len += 1
  }

  /** Appends a new key with `values`, without checking for an existing key. */
  private[http] def addEntry(key: String, values: Chunk[String]): Unit = {
    ensureCapacity()
    keys(len) = key
    vals(len) = values
    len += 1
  }

  /** Adds every value of `params`, as [[add]] does. */
  def addAll(params: QueryParams): Unit =
    params.toList.foreach { case (k, v) => add(k, v) }

  /**
   * Removes every parameter, keeping the buffer's capacity for reuse. A
   * `QueryParams` built earlier is not affected.
   */
  def reset(): Unit = {
    java.util.Arrays.fill(keys.asInstanceOf[Array[AnyRef]], 0, len, null)
    java.util.Arrays.fill(vals.asInstanceOf[Array[AnyRef]], 0, len, null)
    len = 0
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

  /**
   * Returns a `QueryParams` holding the current parameters. Later calls to
   * `add` or `reset` do not change it.
   */
  def build(): QueryParams = {
    val k = new Array[String](len)
    val v = new Array[Chunk[String]](len)
    System.arraycopy(keys, 0, k, 0, len)
    System.arraycopy(vals, 0, v, 0, len)
    new QueryParams(k, v, len)
  }
}

/** Creates [[QueryParamsBuilder]]s. */
object QueryParamsBuilder {

  /**
   * Creates an empty builder with room for `initialCapacity` keys, at least
   * four. The buffer doubles when it fills.
   */
  def make(initialCapacity: Int = 8): QueryParamsBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new QueryParamsBuilder(new Array[String](cap), new Array[Chunk[String]](cap), 0)
  }
}
