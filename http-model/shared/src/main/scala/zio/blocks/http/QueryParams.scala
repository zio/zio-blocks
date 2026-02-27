package zio.blocks.http

import zio.blocks.chunk.Chunk

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

final class QueryParamsBuilder private (
  private var keys: Array[String],
  private var vals: Array[Chunk[String]],
  private var len: Int
) {

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

  private[http] def addEntry(key: String, values: Chunk[String]): Unit = {
    ensureCapacity()
    keys(len) = key
    vals(len) = values
    len += 1
  }

  def addAll(params: QueryParams): Unit =
    params.toList.foreach { case (k, v) => add(k, v) }

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
  def make(initialCapacity: Int = 8): QueryParamsBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new QueryParamsBuilder(new Array[String](cap), new Array[Chunk[String]](cap), 0)
  }
}
