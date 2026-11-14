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
 * An immutable, ordered list of HTTP header fields, as raw name and value
 * strings. Names are stored in lower case, so every lookup ignores the letter
 * case of the name; values are stored as given. A name can appear more than
 * once, which multi-value headers such as `set-cookie` need.
 *
 * Read a value as a string with [[rawGet]], or as a typed value with [[get]]
 * and a [[Header.Codec]], such as `Header.ContentLength`. A typed read parses
 * an entry the first time it is asked for and caches the result in that entry;
 * the cache is not synchronized, so two threads reading the same `Headers` may
 * both parse the entry, with equal results. Writes (`add`, `set`, [[remove]])
 * return a new `Headers` and throw `IllegalArgumentException` on an invalid
 * name or value (see [[Headers.validateName]] and [[Headers.validateValue]]).
 *
 * {{{
 * val headers = Headers("Content-Type" -> "application/json", "content-length" -> "512")
 *
 * headers.toList                    // List((content-type,application/json), (content-length,512))
 * headers.rawGet("CONTENT-TYPE")    // Some(application/json)
 * headers.get(Header.ContentLength) // Some(ContentLength(512))
 * }}}
 *
 * `toString` prints every value, credentials in `authorization` and `cookie`
 * included; this is a known bug, so remove those headers before logging.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/headers/ Header]]
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#headers Headers]]
 */
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef],
  val size: Int
) {

  private def sameCodec(left: Header.Codec[_], right: Header.Codec[_]): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  /** Whether there are no header fields. */
  def isEmpty: Boolean = size == 0

  /** Whether there is at least one header field. */
  def nonEmpty: Boolean = size > 0

  /**
   * Returns the stored (lower-case) name of the entry at `index`, for trusted
   * same-package scans that must not allocate. The index must be below
   * [[size]].
   */
  private[http] def nameAt(index: Int): String = names(index)

  /**
   * Returns the raw value of the entry at `index`, for trusted same-package
   * scans that must not allocate. The index must be below [[size]].
   */
  private[http] def rawValueAt(index: Int): String = rawValues(index)

  /**
   * Returns the first entry named `headerCodec.name` that the codec parses,
   * ignoring the letter case of the name. An entry that fails to parse is
   * skipped as if it were absent, so a malformed value gives `None`, or the
   * next entry with that name that parses. To tell a missing header from a
   * malformed one, parse [[rawGet]] with the codec yourself.
   *
   * The parsed value is cached in the entry for the same codec instance,
   * compared by reference, so two codecs with the same name never see each
   * other's values.
   *
   * {{{
   * val headers = Headers("content-length" -> "huge", "content-length" -> "512")
   *
   * headers.get(Header.ContentLength)                                    // Some(ContentLength(512))
   * Headers("content-length" -> "huge").get(Header.ContentLength)        // None
   * headers.rawGet("content-length").map(Header.ContentLength.parse)     // Some(Left(Invalid content-length: huge))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#headersget--first-parseable-match Headers#get]]
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

  /**
   * Returns the value of the first entry named `name`, ignoring letter case, as
   * stored, without parsing it.
   *
   * {{{
   * val headers = Headers("x-trace" -> "a1", "X-Trace" -> "b2")
   *
   * headers.rawGet("X-TRACE")    // Some(a1)
   * headers.rawGetLast("x-trace") // Some(b2)
   * headers.rawGetAll("x-trace")  // Chunk(a1,b2)
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name (see [[Headers.validateName]]), even
   *   though the method only reads
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#raw-access Raw Access]]
   */
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

  /**
   * Returns the value of the last entry named `name`, ignoring letter case,
   * without parsing it.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
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

  /**
   * Returns the values of every entry named `name`, ignoring letter case, in
   * the order they were added, without parsing them.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
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
   * Returns every entry named `headerCodec.name` that the codec parses, in the
   * order they were added. Entries that fail to parse are skipped, so the
   * result can be shorter than [[rawGetAll]]. Parsed values are cached as in
   * [[get]].
   *
   * {{{
   * val headers = Headers("content-length" -> "100", "content-length" -> "oops", "content-length" -> "300")
   *
   * headers.getAll(Header.ContentLength)       // Chunk(ContentLength(100),ContentLength(300))
   * headers.rawGetAll("content-length").length // 3
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#headersgetall--every-parseable-match Headers#getAll]]
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

  /**
   * Returns the last entry named `headerType.name` that parses, skipping
   * entries that fail to parse. Use it when a later header overrides an earlier
   * one. It takes a [[Header.Typed]], so it works with the built-in headers but
   * not with a codec whose value is not a [[Header]].
   *
   * {{{
   * val headers = Headers("content-length" -> "100", "content-length" -> "200")
   *
   * headers.get(Header.ContentLength)     // Some(ContentLength(100))
   * headers.getLast(Header.ContentLength) // Some(ContentLength(200))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#headersgetlast--last-parseable-match Headers#getLast]]
   */
  def getLast[H <: Header](headerType: Header.Typed[H]): Option[H] = {
    val all = getAll(headerType)
    if (all.isEmpty) None else Some(all(all.length - 1))
  }

  /**
   * Appends `header`, rendered with its `headerName` and `renderedValue`, after
   * the existing entries. Entries with the same name are kept; use `set` to
   * replace them.
   *
   * {{{
   * val base = Headers.empty.add(Header.ContentLength(100))
   *
   * base.add(Header.ContentLength(200)).toList // List((content-length,100), (content-length,200))
   * base.set(Header.ContentLength(200)).toList // List((content-length,200))
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if the name or the value is invalid
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#appending-and-replacing Appending and Replacing]]
   */
  def add(header: Header): Headers = add(header.headerName, header.renderedValue)

  /**
   * Appends an entry with `name` in lower case and `value` as given, keeping
   * existing entries with the same name.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
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

  /**
   * Removes every entry named `name`, ignoring letter case, and appends one
   * entry with `value`. The new entry goes last, after the other headers.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
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

  /**
   * Replaces every entry with `header`'s name by one entry holding its rendered
   * value, placed last.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the name or the value is invalid
   */
  def set(header: Header): Headers = set(header.headerName, header.renderedValue)

  /**
   * Removes every entry named `name`, ignoring letter case.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
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

  /**
   * Whether an entry named `name` exists, ignoring letter case.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
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

  /**
   * Returns the entries as (lower-case name, raw value) pairs, in the order
   * they were added.
   */
  def toList: List[(String, String)] = {
    val builder = List.newBuilder[(String, String)]
    var i       = 0
    while (i < size) {
      builder += ((names(i), rawValues(i)))
      i += 1
    }
    builder.result()
  }

  /**
   * Two `Headers` are equal when [[toList]] is equal: the same names and values
   * in the same order. Names were lower-cased when stored, so their original
   * letter case does not matter, but the order does.
   */
  override def equals(that: Any): Boolean = that match {
    case h: Headers => toList == h.toList
    case _          => false
  }

  override def hashCode: Int = toList.hashCode

  /**
   * Returns the entries of this collection followed by those of `other`,
   * keeping duplicates from both sides.
   */
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

  /**
   * Alias for [[has]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
  def contains(name: String): Boolean = has(name)

  /** Returns the same pairs as [[toList]], as a `Chunk`. */
  def toChunk: Chunk[(String, String)] = {
    val builder = Chunk.newBuilder[(String, String)]
    var i       = 0
    while (i < size) {
      builder += ((names(i), rawValues(i)))
      i += 1
    }
    builder.result()
  }

  /**
   * Renders the entries as `Headers(name: value, ...)`. Every value is printed
   * in full, including `authorization`, `cookie` and `set-cookie` values; this
   * is a known bug, so remove or replace those entries before logging.
   */
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

/** Creates [[Headers]] and checks header names and values. */
object Headers {
  private final class ParsedValue(val codec: Header.Codec[_], val value: AnyRef)

  /** A `Headers` with no entries. */
  val empty: Headers = new Headers(Array.empty, Array.empty, Array.empty, 0)

  /**
   * Checks that `name` is a valid header name: a non-empty HTTP token of ASCII
   * letters, digits and the punctuation RFC 9110 allows in a token, such as
   * `-`, `_`, `.` and `!`. Every method of [[Headers]] and [[HeadersBuilder]]
   * that takes a name throws `IllegalArgumentException` where this returns
   * `Left`, so check names that come from outside the program first.
   *
   * {{{
   * Headers.validateName("x-trace") // Right(())
   * Headers.validateName("x trace") // Left(Invalid header name: x trace)
   * Headers.validateName("")        // Left(Header name cannot be empty)
   * }}}
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
   * Checks that `value` contains no carriage return or line feed. Rejecting
   * them prevents response splitting and header injection when the value is
   * written into an HTTP message. No other character is checked: leading and
   * trailing spaces, tabs and other control characters are accepted.
   *
   * {{{
   * Headers.validateValue("no-cache")                       // Right(())
   * Headers.validateValue("ok\r\nSet-Cookie: admin=true") // Left(Header value cannot contain CR or LF)
   * }}}
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

  /** Throws `IllegalArgumentException` where [[validateName]] gives `Left`. */
  private[http] def validateNameOrThrow(name: String): Unit =
    validateName(name) match {
      case Right(()) => ()
      case Left(err) => throw new IllegalArgumentException(err)
    }

  /** Throws `IllegalArgumentException` where [[validateValue]] gives `Left`. */
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

  /**
   * Creates a `Headers` from name and value pairs, in order, storing each name
   * in lower case.
   *
   * {{{
   * Headers("Accept" -> "text/html", "Cache-Control" -> "no-cache").toList
   * // List((accept,text/html), (cache-control,no-cache))
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name or a value is invalid
   */
  def apply(pairs: (String, String)*): Headers = {
    val builder = HeadersBuilder.make(pairs.size)
    pairs.foreach { case (k, v) => builder.add(k, v) }
    builder.build()
  }
}

/**
 * A mutable buffer that collects header entries and builds one [[Headers]] at
 * the end. Each `Headers#add` copies the whole collection, so use a builder
 * when adding many entries at once. It is not thread-safe.
 *
 * {{{
 * val builder = HeadersBuilder.make(4)
 * builder.add("Content-Type", "application/json")
 * builder.add("x-request-id", "req-7")
 *
 * builder.build().toList // List((content-type,application/json), (x-request-id,req-7))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/headers/#headersbuilder--batch-construction HeadersBuilder]]
 */
final class HeadersBuilder private (
  private var names: Array[String],
  private var rawValues: Array[String],
  private var len: Int
) {

  /**
   * Appends an entry with `name` in lower case and `value` as given.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
  def add(name: String, value: String): Unit = {
    Headers.validateNameOrThrow(name)
    Headers.validateValueOrThrow(value)
    ensureCapacity()
    names(len) = name.toLowerCase(Locale.ROOT)
    rawValues(len) = value
    len += 1
  }

  /**
   * Appends an entry whose name is already lower-cased and known to be a valid
   * header name, as the schema codecs precompute at derivation time. It skips
   * name validation and lower-casing, but still rejects values with CR or LF,
   * so header-injection protection is unchanged.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `value` contains CR or LF
   */
  private[http] def addTrustedName(lowerName: String, value: String): Unit = {
    Headers.validateValueOrThrow(value)
    ensureCapacity()
    names(len) = lowerName
    rawValues(len) = value
    len += 1
  }

  /**
   * Removes every entry, keeping the buffer's capacity for reuse. A `Headers`
   * built earlier is not affected.
   */
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

  /**
   * Returns a `Headers` holding a copy of the current entries. Later calls to
   * `add` or [[reset]] do not change it.
   */
  def build(): Headers = {
    val n = new Array[String](len)
    val v = new Array[String](len)
    val p = new Array[AnyRef](len)
    System.arraycopy(names, 0, n, 0, len)
    System.arraycopy(rawValues, 0, v, 0, len)
    new Headers(n, v, p, len)
  }
}

/** Creates [[HeadersBuilder]]s. */
object HeadersBuilder {

  /**
   * Creates an empty builder with room for `initialCapacity` entries, at least
   * four. The buffer doubles when it fills.
   */
  def make(initialCapacity: Int = 8): HeadersBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new HeadersBuilder(new Array[String](cap), new Array[String](cap), 0)
  }
}
