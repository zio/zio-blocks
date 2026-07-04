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

import java.util.Locale

import zio.blocks.chunk.Chunk

/**
 * Immutable collection of HTTP headers, backed by parallel arrays.
 *
 * Header names are stored pre-lowercased for case-insensitive matching.
 * Multiple headers with the same name are allowed (multi-value). The `parsed`
 * array is populated lazily on typed `get` calls. The cache is an optimization
 * only: duplicate benign parses are possible if the same `Headers` is read
 * concurrently.
 */
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef],
  val size: Int
) {

  private def sameCodec(left: Header.Codec[_], right: Header.Codec[_]): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  def isEmpty: Boolean  = size == 0
  def nonEmpty: Boolean = size > 0

  /**
   * Decodes the first header matching the supplied codec.
   *
   * Matching is case-insensitive by header name. Parsed values are cached per
   * header entry and codec instance so different codecs with the same name do
   * not reuse each other's cached values.
   */
  def get[A](headerCodec: Header.Codec[A]): Option[A] = {
    val target = headerCodec.name.toLowerCase(Locale.ROOT)
    var i      = 0
    while (i < size) {
      if (names(i) == target) {
        parsed(i) match {
          case cached: Headers.ParsedValue if sameCodec(cached.codec, headerCodec) =>
            return Some(cached.value.asInstanceOf[A])
          case _ =>
        }
        headerCodec.parse(rawValues(i)) match {
          case Right(value) =>
            parsed(i) = new Headers.ParsedValue(headerCodec, value.asInstanceOf[AnyRef])
            return Some(value)
          case Left(_) => // skip unparseable, continue scanning
        }
      }
      i += 1
    }
    None
  }

  def rawGet(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    var i      = 0
    while (i < size) {
      if (names(i) == target) return Some(rawValues(i))
      i += 1
    }
    None
  }

  def rawGetLast(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    var i      = size - 1
    while (i >= 0) {
      if (names(i) == target) return Some(rawValues(i))
      i -= 1
    }
    None
  }

  def rawGetAll(name: String): Chunk[String] = {
    Headers.validateNameOrThrow(name)
    val target  = name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[String]
    var i       = 0
    while (i < size) {
      if (names(i) == target) builder += rawValues(i)
      i += 1
    }
    builder.result()
  }

  /**
   * Decodes all headers matching the supplied codec.
   *
   * Values are returned in header order. Entries that fail to parse for the
   * requested codec are skipped, and cached values are reused only when they
   * were produced by the same codec instance.
   */
  def getAll[A](headerCodec: Header.Codec[A]): Chunk[A] = {
    val target  = headerCodec.name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[A]
    var i       = 0
    while (i < size) {
      if (names(i) == target) {
        parsed(i) match {
          case cached: Headers.ParsedValue if sameCodec(cached.codec, headerCodec) =>
            builder += cached.value.asInstanceOf[A]
          case _ =>
            headerCodec.parse(rawValues(i)) match {
              case Right(value) =>
                parsed(i) = new Headers.ParsedValue(headerCodec, value.asInstanceOf[AnyRef])
                builder += value
              case Left(_) => // skip unparseable entries
            }
        }
      }
      i += 1
    }
    builder.result()
  }

  def getLast[H <: Header](headerType: Header.Typed[H]): Option[H] = {
    val all = getAll(headerType)
    if (all.isEmpty) None else Some(all(all.length - 1))
  }

  def add(header: Header): Headers = add(header.headerName, header.renderedValue)

  def add(name: String, value: String): Headers = {
    Headers.validateNameOrThrow(name)
    Headers.validateValueOrThrow(value)
    val newSize      = size + 1
    val newNames     = new Array[String](newSize)
    val newRawValues = new Array[String](newSize)
    val newParsed    = new Array[AnyRef](newSize)
    System.arraycopy(names, 0, newNames, 0, size)
    System.arraycopy(rawValues, 0, newRawValues, 0, size)
    newNames(size) = name.toLowerCase(Locale.ROOT)
    newRawValues(size) = value
    new Headers(newNames, newRawValues, newParsed, newSize)
  }

  def set(name: String, value: String): Headers = {
    Headers.validateNameOrThrow(name)
    Headers.validateValueOrThrow(value)
    val lowerName = name.toLowerCase(Locale.ROOT)
    val builder   = HeadersBuilder.make(size)
    var i         = 0
    while (i < size) {
      if (names(i) != lowerName) builder.add(names(i), rawValues(i))
      i += 1
    }
    builder.add(lowerName, value)
    builder.build()
  }

  def set(header: Header): Headers = set(header.headerName, header.renderedValue)

  def remove(name: String): Headers = {
    Headers.validateNameOrThrow(name)
    val lowerName = name.toLowerCase(Locale.ROOT)
    val builder   = HeadersBuilder.make(size)
    var i         = 0
    while (i < size) {
      if (names(i) != lowerName) builder.add(names(i), rawValues(i))
      i += 1
    }
    builder.build()
  }

  def has(name: String): Boolean = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
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

  def ++(other: Headers): Headers = {
    val builder = HeadersBuilder.make(size + other.size)
    var i       = 0
    while (i < size) {
      builder.add(names(i), rawValues(i))
      i += 1
    }
    i = 0
    while (i < other.size) {
      builder.add(other.names(i), other.rawValues(i))
      i += 1
    }
    builder.build()
  }

  def contains(name: String): Boolean = has(name)

  def toChunk: Chunk[(String, String)] = {
    val builder = Chunk.newBuilder[(String, String)]
    var i       = 0
    while (i < size) {
      builder += ((names(i), rawValues(i)))
      i += 1
    }
    builder.result()
  }

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
  private final class ParsedValue(val codec: Header.Codec[_], val value: AnyRef)

  val empty: Headers = new Headers(Array.empty, Array.empty, Array.empty, 0)

  /**
   * Validates a header field name.
   *
   * Header names must be non-empty HTTP token strings. This method is suitable
   * for checking user-supplied names before constructing or mutating
   * [[Headers]]; the mutating helpers enforce the same invariant.
   */
  def validateName(name: String): Either[String, Unit] = {
    if (name.isEmpty) return Left("Header name cannot be empty")
    var i = 0
    while (i < name.length) {
      val c = name.charAt(i)
      if (!isTokenChar(c)) return Left(s"Invalid header name: $name")
      i += 1
    }
    Right(())
  }

  /**
   * Validates a raw header field value.
   *
   * Values may not contain carriage return or line feed characters. Rejecting
   * CR/LF prevents response/request splitting and header-injection attacks when
   * values are rendered into an HTTP message.
   */
  def validateValue(value: String): Either[String, Unit] = {
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '\r' || c == '\n') return Left("Header value cannot contain CR or LF")
      i += 1
    }
    Right(())
  }

  private[http] def validateNameOrThrow(name: String): Unit =
    validateName(name) match {
      case Right(()) => ()
      case Left(err) => throw new IllegalArgumentException(err)
    }

  private[http] def validateValueOrThrow(value: String): Unit =
    validateValue(value) match {
      case Right(()) => ()
      case Left(err) => throw new IllegalArgumentException(err)
    }

  private def isTokenChar(c: Char): Boolean =
    (c >= 'A' && c <= 'Z') ||
      (c >= 'a' && c <= 'z') ||
      (c >= '0' && c <= '9') ||
      c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\'' ||
      c == '*' || c == '+' || c == '-' || c == '.' || c == '^' || c == '_' ||
      c == '`' || c == '|' || c == '~'

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
    Headers.validateNameOrThrow(name)
    Headers.validateValueOrThrow(value)
    ensureCapacity()
    names(len) = name.toLowerCase(Locale.ROOT)
    rawValues(len) = value
    len += 1
  }

  def reset(): Unit = {
    java.util.Arrays.fill(names.asInstanceOf[Array[AnyRef]], 0, len, null)
    java.util.Arrays.fill(rawValues.asInstanceOf[Array[AnyRef]], 0, len, null)
    len = 0
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
