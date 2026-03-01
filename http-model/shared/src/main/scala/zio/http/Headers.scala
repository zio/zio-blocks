package zio.http

import zio.blocks.chunk.Chunk

/**
 * Immutable collection of HTTP headers, backed by parallel arrays.
 *
 * Header names are stored pre-lowercased for case-insensitive matching.
 * Multiple headers with the same name are allowed (multi-value). The `parsed`
 * array is populated lazily on the first typed `get` call and is monotonic,
 * making it thread-safe without locks. Parse failures are not cached: `get`
 * returns `None` but a subsequent call may succeed if the underlying raw value
 * has been corrected via `set`.
 */
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef],
  val size: Int
) {

  def isEmpty: Boolean  = size == 0
  def nonEmpty: Boolean = size > 0

  def get[H <: Header](headerType: Header.Typed[H]): Option[H] = {
    val target = headerType.name
    var i      = 0
    while (i < size) {
      if (names(i) == target) {
        val cached = parsed(i)
        if (cached ne null) return Some(cached.asInstanceOf[H])
        headerType.parse(rawValues(i)) match {
          case Right(h) =>
            parsed(i) = h.asInstanceOf[AnyRef]
            return Some(h)
          case Left(_) => // skip unparseable, continue scanning
        }
      }
      i += 1
    }
    None
  }

  def rawGet(name: String): Option[String] = {
    val target = name.toLowerCase
    var i      = 0
    while (i < size) {
      if (names(i) == target) return Some(rawValues(i))
      i += 1
    }
    None
  }

  def rawGetAll(name: String): Chunk[String] = {
    val target  = name.toLowerCase
    val builder = Chunk.newBuilder[String]
    var i       = 0
    while (i < size) {
      if (names(i) == target) builder += rawValues(i)
      i += 1
    }
    builder.result()
  }

  def getAll[H <: Header](headerType: Header.Typed[H]): Chunk[H] = {
    val target  = headerType.name
    val builder = Chunk.newBuilder[H]
    var i       = 0
    while (i < size) {
      if (names(i) == target) {
        val cached = parsed(i)
        if (cached ne null) {
          builder += cached.asInstanceOf[H]
        } else {
          headerType.parse(rawValues(i)) match {
            case Right(h) =>
              parsed(i) = h.asInstanceOf[AnyRef]
              builder += h
            case Left(_) => // skip unparseable entries
          }
        }
      }
      i += 1
    }
    builder.result()
  }

  def add(name: String, value: String): Headers = {
    val newSize      = size + 1
    val newNames     = new Array[String](newSize)
    val newRawValues = new Array[String](newSize)
    val newParsed    = new Array[AnyRef](newSize)
    System.arraycopy(names, 0, newNames, 0, size)
    System.arraycopy(rawValues, 0, newRawValues, 0, size)
    newNames(size) = name.toLowerCase
    newRawValues(size) = value
    new Headers(newNames, newRawValues, newParsed, newSize)
  }

  def set(name: String, value: String): Headers = {
    val lowerName = name.toLowerCase
    val builder   = HeadersBuilder.make(size)
    var i         = 0
    while (i < size) {
      if (names(i) != lowerName) builder.addRaw(names(i), rawValues(i))
      i += 1
    }
    builder.addRaw(lowerName, value)
    builder.build()
  }

  def remove(name: String): Headers = {
    val lowerName = name.toLowerCase
    val builder   = HeadersBuilder.make(size)
    var i         = 0
    while (i < size) {
      if (names(i) != lowerName) builder.addRaw(names(i), rawValues(i))
      i += 1
    }
    builder.build()
  }

  def has(name: String): Boolean = {
    val target = name.toLowerCase
    var i      = 0
    while (i < size) {
      if (names(i) == target) return true
      i += 1
    }
    false
  }

  def toList: List[(String, String)] = {
    val builder = List.newBuilder[(String, String)]
    var i       = 0
    while (i < size) {
      builder += ((names(i), rawValues(i)))
      i += 1
    }
    builder.result()
  }

  override def equals(that: Any): Boolean = that match {
    case h: Headers => toList == h.toList
    case _          => false
  }

  override def hashCode: Int = toList.hashCode

  override def toString: String = {
    val sb = new StringBuilder("Headers(")
    var i  = 0
    while (i < size) {
      if (i > 0) sb.append(", ")
      sb.append(names(i))
      sb.append(": ")
      sb.append(rawValues(i))
      i += 1
    }
    sb.append(')')
    sb.toString
  }
}

object Headers {
  val empty: Headers = new Headers(Array.empty, Array.empty, Array.empty, 0)

  def apply(pairs: (String, String)*): Headers = {
    val builder = HeadersBuilder.make(pairs.size)
    pairs.foreach { case (k, v) => builder.add(k, v) }
    builder.build()
  }
}

final class HeadersBuilder private (
  private var names: Array[String],
  private var rawValues: Array[String],
  private var len: Int
) {

  def add(name: String, value: String): Unit = {
    ensureCapacity()
    names(len) = name.toLowerCase
    rawValues(len) = value
    len += 1
  }

  private[http] def addRaw(lowerName: String, value: String): Unit = {
    ensureCapacity()
    names(len) = lowerName
    rawValues(len) = value
    len += 1
  }

  private def ensureCapacity(): Unit =
    if (len >= names.length) {
      val newCap       = Math.max(names.length * 2, 8)
      val newNames     = new Array[String](newCap)
      val newRawValues = new Array[String](newCap)
      System.arraycopy(names, 0, newNames, 0, len)
      System.arraycopy(rawValues, 0, newRawValues, 0, len)
      names = newNames
      rawValues = newRawValues
    }

  def build(): Headers = {
    val n = new Array[String](len)
    val v = new Array[String](len)
    val p = new Array[AnyRef](len)
    System.arraycopy(names, 0, n, 0, len)
    System.arraycopy(rawValues, 0, v, 0, len)
    new Headers(n, v, p, len)
  }
}

object HeadersBuilder {
  def make(initialCapacity: Int = 8): HeadersBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new HeadersBuilder(new Array[String](cap), new Array[String](cap), 0)
  }
}
