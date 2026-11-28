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
 * The class is sealed: its concrete representations (an eager array, an overlay
 * of one appended entry, and a byte-slice view) are private to this file and
 * all behave the same. `add` on an overlay runs in constant time, while the
 * other representations materialise an eager array.
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
 *   [[https://zioblocks.com/reference/http-model/headers/ Headers]]
 */
sealed abstract class Headers private[http] () {

  /** The number of header fields, including duplicate names. */
  def size: Int

  /** The lower-case name of the entry at `i`. */
  private[http] def nameAt(i: Int): String

  /** The raw value of the entry at `i`. */
  private[http] def valueAt(i: Int): String

  /**
   * Parses the value of the entry at `i` with `headerCodec`, caching the result
   * in that entry for the same codec instance. Returns `None` when the value
   * does not parse.
   */
  private[http] def typedAt[A](i: Int, headerCodec: Header.Codec[A]): Option[A]

  /** Appends an already validated, lower-case name and value. */
  private[http] def addValidated(lowerName: String, value: String): Headers

  private[http] def sameCodec(left: Header.Codec[_], right: Header.Codec[_]): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  /** Whether there are no header fields. */
  def isEmpty: Boolean = size == 0

  /** Whether there is at least one header field. */
  def nonEmpty: Boolean = size > 0

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
      if (nameAt(i) == target) {
        typedAt(i, headerCodec) match {
          case some @ Some(_) => return some
          case None           => ()
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
      if (nameAt(i) == target) return Some(valueAt(i))
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
      if (nameAt(i) == target) return Some(valueAt(i))
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
      if (nameAt(i) == target) builder += valueAt(i)
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
      if (nameAt(i) == target) typedAt(i, headerCodec).foreach(builder += _)
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
    addValidated(name.toLowerCase(Locale.ROOT), value)
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
      val entryName = nameAt(i)
      if (entryName != lowerName) builder.add(entryName, valueAt(i))
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
      val entryName = nameAt(i)
      if (entryName != lowerName) builder.add(entryName, valueAt(i))
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
      if (nameAt(i) == target) return true
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
      builder += ((nameAt(i), valueAt(i)))
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
      builder.add(nameAt(i), valueAt(i))
      i += 1
    }
    i = 0
    while (i < other.size) {
      builder.add(other.nameAt(i), other.valueAt(i))
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
      builder += ((nameAt(i), valueAt(i)))
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
      sb.append(nameAt(i))
      sb.append(": ")
      sb.append(valueAt(i))
      i += 1
    }
    sb.append(')')
    sb.toString
  }
}

/** Creates [[Headers]] and checks header names and values. */
object Headers {
  private[http] final class ParsedValue(val codec: Header.Codec[_], val value: AnyRef)

  private[http] final val Utf8 = "UTF-8"

  /** A `Headers` with no entries. */
  val empty: Headers = new Eager(Array.empty[String], Array.empty[String], Array.empty[AnyRef])

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

  /**
   * Builds a `Headers` that reads its entries straight from `bytes`, without
   * copying or decoding them. `nameOffsets(i)` and `nameLengths(i)` locate the
   * raw name of entry `i`; `valueOffsets(i)` and `valueLengths(i)` locate its
   * raw value. All four arrays must have the same length, `size`.
   *
   * Header names must be ASCII tokens (the rule [[validateName]] enforces) and
   * are matched case-insensitively over the bytes; their original letter case
   * is not preserved when the names are materialised. Values are UTF-8 and must
   * not contain CR or LF (the rule [[validateValue]] enforces). Nothing is
   * decoded until a name or value is materialised.
   *
   * The caller transfers ownership of `bytes` and the offset arrays: they must
   * not be modified after this call, because `Headers` reads them in place.
   *
   * @return
   *   `Right` with a slice-backed `Headers`, or `Left` with a message when the
   *   slices are inconsistent or a name or value is invalid
   */
  def fromSlices(
    bytes: Array[Byte],
    nameOffsets: Array[Int],
    nameLengths: Array[Int],
    valueOffsets: Array[Int],
    valueLengths: Array[Int]
  ): Either[String, Headers] = {
    val size = nameOffsets.length
    if (
      nameLengths.length != size ||
      valueOffsets.length != size ||
      valueLengths.length != size
    ) return Left("Header slice arrays must have the same length")

    var i = 0
    while (i < size) {
      val nameOffset = nameOffsets(i)
      val nameLength = nameLengths(i)
      if (nameOffset < 0 || nameLength < 0 || nameOffset > bytes.length - nameLength)
        return Left("Header name slice out of bounds")
      if (nameLength == 0) return Left("Header name cannot be empty")

      var j = 0
      while (j < nameLength) {
        if (!isTokenByte(bytes(nameOffset + j))) return Left("Invalid header name")
        j += 1
      }

      val valueOffset = valueOffsets(i)
      val valueLength = valueLengths(i)
      if (valueOffset < 0 || valueLength < 0 || valueOffset > bytes.length - valueLength)
        return Left("Header value slice out of bounds")

      var k = 0
      while (k < valueLength) {
        val c = bytes(valueOffset + k) & 0xff
        if (c == '\r' || c == '\n') return Left("Header value cannot contain CR or LF")
        k += 1
      }

      i += 1
    }

    Right(new Slices(bytes, nameOffsets, nameLengths, valueOffsets, valueLengths))
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

  private def isTokenByte(b: Byte): Boolean = isTokenChar((b & 0xff).toChar)

  /** Copies `headers` into an [[Eager]] without re-validating its entries. */
  private[http] def materialize(headers: Headers): Eager = {
    val size   = headers.size
    val names  = new Array[String](size)
    val values = new Array[String](size)
    var i      = 0
    while (i < size) {
      names(i) = headers.nameAt(i)
      values(i) = headers.valueAt(i)
      i += 1
    }
    new Eager(names, values, new Array[AnyRef](size))
  }

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
 * A `Headers` backed by three parallel arrays, one entry per index. It is the
 * representation produced by [[Headers.empty]], [[HeadersBuilder#build]] and
 * every operation that has to rebuild the collection.
 */
private[http] final class Eager private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef]
) extends Headers {

  override val size: Int = names.length

  private[http] def nameAt(i: Int): String = names(i)

  private[http] def valueAt(i: Int): String = rawValues(i)

  private[http] def typedAt[A](i: Int, headerCodec: Header.Codec[A]): Option[A] =
    parsed(i) match {
      case cached: Headers.ParsedValue if sameCodec(cached.codec, headerCodec) =>
        Some(cached.value.asInstanceOf[A])
      case _ =>
        headerCodec.parse(rawValues(i)) match {
          case Right(value) =>
            parsed(i) = new Headers.ParsedValue(headerCodec, value.asInstanceOf[AnyRef])
            Some(value)
          case Left(_) => None
        }
    }

  private[http] def addValidated(lowerName: String, value: String): Headers = {
    val newSize      = size + 1
    val newNames     = new Array[String](newSize)
    val newRawValues = new Array[String](newSize)
    val newParsed    = new Array[AnyRef](newSize)
    System.arraycopy(names, 0, newNames, 0, size)
    System.arraycopy(rawValues, 0, newRawValues, 0, size)
    newNames(size) = lowerName
    newRawValues(size) = value
    new Eager(newNames, newRawValues, newParsed)
  }
}

/**
 * A `Headers` made of a parent plus one appended entry. Appending is constant
 * time: it wraps the current collection instead of copying it. Reads walk the
 * parent chain first, so first-match order is the same as an [[Eager]].
 */
private[http] final class Overlay private[http] (
  private val parent: Headers,
  private val name: String,
  private val value: String
) extends Headers {

  override val size: Int = parent.size + 1

  private var parsedEntry: Headers.ParsedValue = null

  private lazy val eager: Eager = Headers.materialize(this)

  private[http] def nameAt(i: Int): String =
    if (i < parent.size) parent.nameAt(i) else name

  private[http] def valueAt(i: Int): String =
    if (i < parent.size) parent.valueAt(i) else value

  private[http] def typedAt[A](i: Int, headerCodec: Header.Codec[A]): Option[A] =
    if (i < parent.size) parent.typedAt(i, headerCodec)
    else
      parsedEntry match {
        case cached: Headers.ParsedValue if sameCodec(cached.codec, headerCodec) =>
          Some(cached.value.asInstanceOf[A])
        case _ =>
          headerCodec.parse(value) match {
            case Right(parsed) =>
              parsedEntry = new Headers.ParsedValue(headerCodec, parsed.asInstanceOf[AnyRef])
              Some(parsed)
            case Left(_) => None
          }
      }

  private[http] def addValidated(lowerName: String, value: String): Headers =
    new Overlay(this, lowerName, value)

  override def get[A](headerCodec: Header.Codec[A]): Option[A] = {
    val fromParent = parent.get(headerCodec)
    if (fromParent.isDefined) fromParent
    else if (headerCodec.name.toLowerCase(Locale.ROOT) == name) typedAt(parent.size, headerCodec)
    else None
  }

  override def rawGet(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target     = name.toLowerCase(Locale.ROOT)
    val fromParent = parent.rawGet(target)
    if (fromParent.isDefined) fromParent
    else if (target == this.name) Some(value)
    else None
  }

  override def rawGetLast(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    if (target == this.name) Some(value) else parent.rawGetLast(target)
  }

  override def rawGetAll(name: String): Chunk[String] = {
    Headers.validateNameOrThrow(name)
    val target  = name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[String]
    builder ++= parent.rawGetAll(target)
    if (target == this.name) builder += value
    builder.result()
  }

  override def getAll[A](headerCodec: Header.Codec[A]): Chunk[A] = {
    val target  = headerCodec.name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[A]
    builder ++= parent.getAll(headerCodec)
    if (target == name) typedAt(parent.size, headerCodec).foreach(builder += _)
    builder.result()
  }

  override def has(name: String): Boolean = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    target == this.name || parent.has(target)
  }

  override def set(name: String, value: String): Headers = eager.set(name, value)

  override def remove(name: String): Headers = eager.remove(name)

  override def ++(other: Headers): Headers = eager ++ other

  override def toList: List[(String, String)] = eager.toList

  override def toChunk: Chunk[(String, String)] = eager.toChunk

  override def equals(that: Any): Boolean = eager.equals(that)

  override def hashCode: Int = eager.hashCode

  override def toString: String = eager.toString
}

/**
 * A `Headers` that reads names and values directly from a byte array. Names are
 * compared as ASCII bytes, case-insensitively, so lookups do not decode them;
 * values are decoded only when a caller needs the string.
 * [[Headers.fromSlices]] is the only way to build one.
 */
private[http] final class Slices private[http] (
  private val bytes: Array[Byte],
  private val nameOffsets: Array[Int],
  private val nameLengths: Array[Int],
  private val valueOffsets: Array[Int],
  private val valueLengths: Array[Int]
) extends Headers {

  override val size: Int = nameOffsets.length

  private var parsed: Array[AnyRef] = null

  private[http] def nameAt(i: Int): String =
    new String(bytes, nameOffsets(i), nameLengths(i), Headers.Utf8).toLowerCase(Locale.ROOT)

  private[http] def valueAt(i: Int): String =
    new String(bytes, valueOffsets(i), valueLengths(i), Headers.Utf8)

  private[http] def typedAt[A](i: Int, headerCodec: Header.Codec[A]): Option[A] = {
    if (parsed eq null) parsed = new Array[AnyRef](size)
    parsed(i) match {
      case cached: Headers.ParsedValue if sameCodec(cached.codec, headerCodec) =>
        Some(cached.value.asInstanceOf[A])
      case _ =>
        headerCodec.parse(valueAt(i)) match {
          case Right(value) =>
            parsed(i) = new Headers.ParsedValue(headerCodec, value.asInstanceOf[AnyRef])
            Some(value)
          case Left(_) => None
        }
    }
  }

  private[http] def addValidated(lowerName: String, value: String): Headers =
    new Overlay(this, lowerName, value)

  private def nameMatches(i: Int, lowerTarget: String): Boolean = {
    val length = nameLengths(i)
    if (length != lowerTarget.length) return false
    val offset = nameOffsets(i)
    var j      = 0
    while (j < length) {
      val c      = bytes(offset + j) & 0xff
      val folded = if (c >= 'A' && c <= 'Z') c + ('a' - 'A') else c
      if (folded.toChar != lowerTarget.charAt(j)) return false
      j += 1
    }
    true
  }

  override def get[A](headerCodec: Header.Codec[A]): Option[A] = {
    val target = headerCodec.name.toLowerCase(Locale.ROOT)
    var i      = 0
    while (i < size) {
      if (nameMatches(i, target)) {
        typedAt(i, headerCodec) match {
          case some @ Some(_) => return some
          case None           => ()
        }
      }
      i += 1
    }
    None
  }

  override def rawGet(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    var i      = 0
    while (i < size) {
      if (nameMatches(i, target)) return Some(valueAt(i))
      i += 1
    }
    None
  }

  override def rawGetLast(name: String): Option[String] = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    var i      = size - 1
    while (i >= 0) {
      if (nameMatches(i, target)) return Some(valueAt(i))
      i -= 1
    }
    None
  }

  override def rawGetAll(name: String): Chunk[String] = {
    Headers.validateNameOrThrow(name)
    val target  = name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[String]
    var i       = 0
    while (i < size) {
      if (nameMatches(i, target)) builder += valueAt(i)
      i += 1
    }
    builder.result()
  }

  override def getAll[A](headerCodec: Header.Codec[A]): Chunk[A] = {
    val target  = headerCodec.name.toLowerCase(Locale.ROOT)
    val builder = Chunk.newBuilder[A]
    var i       = 0
    while (i < size) {
      if (nameMatches(i, target)) typedAt(i, headerCodec).foreach(builder += _)
      i += 1
    }
    builder.result()
  }

  override def has(name: String): Boolean = {
    Headers.validateNameOrThrow(name)
    val target = name.toLowerCase(Locale.ROOT)
    var i      = 0
    while (i < size) {
      if (nameMatches(i, target)) return true
      i += 1
    }
    false
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
    new Eager(n, v, p)
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
