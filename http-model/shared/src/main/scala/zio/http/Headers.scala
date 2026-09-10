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
import zio.blocks.maybe.Maybe

/**
 * Immutable collection of HTTP headers, backed by parallel arrays.
 *
 * Header names are stored pre-lowercased for case-insensitive matching.
 * Multiple headers with the same name are allowed (multi-value).
 * `parsedCodecs`/`parsedValues` are populated lazily on typed `get` calls: a
 * non-null codec marks its entry as parsed by that exact codec instance. The
 * cache is an optimization only: duplicate benign parses are possible if the
 * same `Headers` is read concurrently.
 *
 * Typed reads come in two flavors. The lenient [[get]] / [[getAll]] skip
 * entries that fail to parse and continue scanning, so a wrong-typed header
 * reads as absent ([[Maybe.absent]] / empty). Use the strict [[getStrict]] /
 * [[getAllStrict]] when you need to distinguish "header absent" from "header
 * present but unparseable": they report the parse error as `Left`.
 */
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsedCodecs: Array[Header.Codec[_]],
  private val parsedValues: Array[AnyRef],
  val size: Int
) {

  private def sameCodec(left: Header.Codec[_], right: Header.Codec[_]): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

  /**
   * Value cached for entry `i` when it was parsed by this exact codec instance,
   * or `null` on a cache miss. A `null` value is treated as a miss (matching
   * the previous behaviour where a `null` parse result never hit).
   */
  private def cachedValue[A](i: Int, headerCodec: Header.Codec[A]): AnyRef =
    if (sameCodec(parsedCodecs(i), headerCodec)) parsedValues(i) else null

  private def storeParsed[A](i: Int, headerCodec: Header.Codec[A], value: A): Unit = {
    parsedCodecs(i) = headerCodec
    parsedValues(i) = value.asInstanceOf[AnyRef]
  }

  /** Index of the first entry named `target` in `[0, size)`, or `-1`. */
  private def indexOf(target: String): Int = {
    var i = 0
    while (i < size) {
      if (names(i) == target) return i
      i += 1
    }
    -1
  }

  /** Index of the last entry named `target` in `[0, size)`, or `-1`. */
  private def lastIndexOf(target: String): Int = {
    var i = size - 1
    while (i >= 0) {
      if (names(i) == target) return i
      i -= 1
    }
    -1
  }

  def isEmpty: Boolean  = size == 0
  def nonEmpty: Boolean = size > 0

  /**
   * Decodes the first header matching the supplied codec.
   *
   * Matching is case-insensitive by header name. Parsed values are cached per
   * header entry and codec instance so different codecs with the same name do
   * not reuse each other's cached values.
   *
   * This is the lenient read: entries that fail to parse are skipped and
   * scanning continues, so a present-but-wrong-typed header reads as absent
   * ([[Maybe.absent]]). See [[getStrict]] for the error-reporting variant.
   *
   * The result is a [[Maybe]] rather than an `Option` so the hot path (e.g. a
   * middleware checking a header on every request) pays no `Some` allocation on
   * Scala 3: a present value is returned raw, absence is the `Absent`
   * singleton.
   */
  def get[A](headerCodec: Header.Codec[A]): Maybe[A] = {
    val target = Headers.lowerName(headerCodec.name)
    var i      = 0
    while (i < size) {
      if (names(i) == target) {
        val cached = cachedValue(i, headerCodec)
        if (cached != null) return Maybe.present(cached.asInstanceOf[A])
        headerCodec.parse(rawValues(i)) match {
          case Right(value) =>
            storeParsed(i, headerCodec, value)
            return Maybe.present(value)
          case Left(_) => // skip unparseable, continue scanning
        }
      }
      i += 1
    }
    Maybe.absent
  }

  /**
   * Strict variant of [[get]]: distinguishes "header absent" from "header
   * present but unparseable".
   *
   * Returns `Right` of the first entry that parses, `Right` of [[Maybe.absent]]
   * when no entry matches the codec name, and `Left(error)` carrying the first
   * parse error when entries match but none parses. Like [[get]], scanning
   * continues past unparseable entries while a later entry may still parse; the
   * error is reported only when nothing parseable is found.
   */
  def getStrict[A](headerCodec: Header.Codec[A]): Either[String, Maybe[A]] = {
    val target             = Headers.lowerName(headerCodec.name)
    var i                  = 0
    var firstError: String = null
    while (i < size) {
      if (names(i) == target) {
        val cached = cachedValue(i, headerCodec)
        if (cached != null) return Right(Maybe.present(cached.asInstanceOf[A]))
        headerCodec.parse(rawValues(i)) match {
          case Right(value) =>
            storeParsed(i, headerCodec, value)
            return Right(Maybe.present(value))
          case Left(err) =>
            if (firstError == null) firstError = err
        }
      }
      i += 1
    }
    if (firstError == null) Right(Maybe.absent) else Left(firstError)
  }

  def rawGet(name: String): Maybe[String] = {
    val idx = indexOf(Headers.validatedLowerName(name))
    if (idx < 0) Maybe.absent else Maybe.present(rawValues(idx))
  }

  def rawGetLast(name: String): Maybe[String] = {
    val idx = lastIndexOf(Headers.validatedLowerName(name))
    if (idx < 0) Maybe.absent else Maybe.present(rawValues(idx))
  }

  def rawGetAll(name: String): Chunk[String] = {
    val target  = Headers.validatedLowerName(name)
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
   * were produced by the same codec instance. See [[getAllStrict]] for the
   * fail-fast variant.
   */
  def getAll[A](headerCodec: Header.Codec[A]): Chunk[A] = {
    val target  = Headers.lowerName(headerCodec.name)
    val builder = Chunk.newBuilder[A]
    var i       = 0
    while (i < size) {
      if (names(i) == target) {
        val cached = cachedValue(i, headerCodec)
        if (cached != null) builder += cached.asInstanceOf[A]
        else
          headerCodec.parse(rawValues(i)) match {
            case Right(value) =>
              storeParsed(i, headerCodec, value)
              builder += value
            case Left(_) => // skip unparseable entries
          }
      }
      i += 1
    }
    builder.result()
  }

  /**
   * Strict variant of [[getAll]]: fails fast with the first parse error instead
   * of skipping unparseable entries.
   */
  def getAllStrict[A](headerCodec: Header.Codec[A]): Either[String, Chunk[A]] = {
    val target  = Headers.lowerName(headerCodec.name)
    val builder = Chunk.newBuilder[A]
    var i       = 0
    while (i < size) {
      if (names(i) == target) {
        val cached = cachedValue(i, headerCodec)
        if (cached != null) builder += cached.asInstanceOf[A]
        else
          headerCodec.parse(rawValues(i)) match {
            case Right(value) =>
              storeParsed(i, headerCodec, value)
              builder += value
            case Left(err) => return Left(err)
          }
      }
      i += 1
    }
    Right(builder.result())
  }

  /**
   * Decodes the last header matching the supplied codec.
   *
   * Scans backwards so only entries down to the last parseable one are parsed;
   * unlike `getAll(...).lastOption` this never parses the whole header list to
   * return a single value. Unparseable entries are skipped like in [[get]].
   */
  def getLast[H <: Header](headerType: Header.Typed[H]): Maybe[H] = {
    val target = Headers.lowerName(headerType.name)
    var i      = size - 1
    while (i >= 0) {
      if (names(i) == target) {
        val cached = cachedValue(i, headerType)
        if (cached != null) return Maybe.present(cached.asInstanceOf[H])
        headerType.parse(rawValues(i)) match {
          case Right(value) =>
            storeParsed(i, headerType, value)
            return Maybe.present(value)
          case Left(_) => // skip unparseable, keep scanning backwards
        }
      }
      i -= 1
    }
    Maybe.absent
  }

  def add(header: Header): Headers = add(header.headerName, header.renderedValue)

  def add(name: String, value: String): Headers = {
    Headers.validateValueOrThrow(value)
    val newSize         = size + 1
    val newNames        = new Array[String](newSize)
    val newRawValues    = new Array[String](newSize)
    val newParsedCodecs = new Array[Header.Codec[_]](newSize)
    val newParsedValues = new Array[AnyRef](newSize)
    System.arraycopy(names, 0, newNames, 0, size)
    System.arraycopy(rawValues, 0, newRawValues, 0, size)
    newNames(size) = Headers.validatedLowerName(name)
    newRawValues(size) = value
    new Headers(newNames, newRawValues, newParsedCodecs, newParsedValues, newSize)
  }

  def set(name: String, value: String): Headers = {
    val builder = HeadersBuilder.make(size)
    var i       = 0
    // Carried entries are already validated and lowercased: copy unchecked.
    // The new pair goes through the validating `add`, which also enforces the
    // name/value invariants for this call.
    val target = Headers.lowerName(name)
    while (i < size) {
      if (names(i) != target) builder.addUnchecked(names(i), rawValues(i))
      i += 1
    }
    builder.add(name, value)
    builder.build()
  }

  def set(header: Header): Headers = set(header.headerName, header.renderedValue)

  def remove(name: String): Headers = {
    val lowerName = Headers.validatedLowerName(name)
    val builder   = HeadersBuilder.make(size)
    var i         = 0
    while (i < size) {
      if (names(i) != lowerName) builder.addUnchecked(names(i), rawValues(i))
      i += 1
    }
    builder.build()
  }

  def has(name: String): Boolean =
    indexOf(Headers.validatedLowerName(name)) >= 0

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
    case h: Headers =>
      if (size != h.size) false
      else {
        var i = 0
        while (i < size) {
          if (names(i) != h.names(i) || rawValues(i) != h.rawValues(i)) return false
          i += 1
        }
        true
      }
    case _ => false
  }

  override def hashCode: Int = {
    var h = 1
    var i = 0
    while (i < size) {
      h = 31 * h + names(i).hashCode
      h = 31 * h + rawValues(i).hashCode
      i += 1
    }
    h
  }

  def ++(other: Headers): Headers = {
    val builder = HeadersBuilder.make(size + other.size)
    var i       = 0
    while (i < size) {
      builder.addUnchecked(names(i), rawValues(i))
      i += 1
    }
    i = 0
    while (i < other.size) {
      builder.addUnchecked(other.names(i), other.rawValues(i))
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
  val empty: Headers =
    new Headers(Array.empty, Array.empty, Array.empty, Array.empty, 0)

  /**
   * Lowercases a header name without validating it, allocating only when the
   * name actually contains an uppercase ASCII character (or a non-ASCII
   * character, which falls back to the JDK converter). Used for codec names,
   * which were never validated on the read path.
   */
  private[http] def lowerName(name: String): String = {
    var i = 0
    while (i < name.length) {
      val c = name.charAt(i)
      if ((c >= 'A' && c <= 'Z') || c > 127) return name.toLowerCase(Locale.ROOT)
      i += 1
    }
    name
  }

  /**
   * Validates a header field name and returns its lowercase form in a single
   * scan, allocating only when the name actually contains an uppercase ASCII
   * character. Already-lowercase names — the common case — cost one cheap pass;
   * this fuses the validation and case-normalization scans that raw reads would
   * otherwise pay separately.
   */
  private[http] def validatedLowerName(name: String): String = {
    if (name.isEmpty) throw new IllegalArgumentException("Header name cannot be empty")
    var i          = 0
    var needsLower = false
    while (i < name.length) {
      val c = name.charAt(i)
      if (!isTokenChar(c)) throw new IllegalArgumentException(s"Invalid header name: $name")
      if (c >= 'A' && c <= 'Z') needsLower = true
      i += 1
    }
    if (needsLower) name.toLowerCase(Locale.ROOT) else name
  }

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

  /**
   * Validates a header field name, throwing on failure. This is the hot-path
   * entry point used by every read and write: unlike [[validateName]] it never
   * allocates an `Either`, so a valid name costs a single scan.
   */
  private[http] def validateNameOrThrow(name: String): Unit = {
    if (name.isEmpty) throw new IllegalArgumentException("Header name cannot be empty")
    var i = 0
    while (i < name.length) {
      if (!isTokenChar(name.charAt(i))) throw new IllegalArgumentException(s"Invalid header name: $name")
      i += 1
    }
  }

  /**
   * Validates a raw header field value, throwing on failure. Allocation-free on
   * success, like [[validateNameOrThrow]].
   */
  private[http] def validateValueOrThrow(value: String): Unit = {
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '\r' || c == '\n') throw new IllegalArgumentException("Header value cannot contain CR or LF")
      i += 1
    }
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
    Headers.validateValueOrThrow(value)
    ensureCapacity()
    names(len) = Headers.validatedLowerName(name)
    rawValues(len) = value
    len += 1
  }

  /**
   * Adds an entry whose name is already validated and lowercased, skipping both
   * checks. Used when copying entries between collections, where every stored
   * name already satisfies the invariants.
   */
  private[http] def addUnchecked(lowercasedName: String, value: String): Unit = {
    ensureCapacity()
    names(len) = lowercasedName
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
    val n  = new Array[String](len)
    val v  = new Array[String](len)
    val pc = new Array[Header.Codec[_]](len)
    val pv = new Array[AnyRef](len)
    System.arraycopy(names, 0, n, 0, len)
    System.arraycopy(rawValues, 0, v, 0, len)
    new Headers(n, v, pc, pv, len)
  }
}

object HeadersBuilder {
  def make(initialCapacity: Int = 8): HeadersBuilder = {
    val cap = Math.max(initialCapacity, 4)
    new HeadersBuilder(new Array[String](cap), new Array[String](cap), 0)
  }
}
