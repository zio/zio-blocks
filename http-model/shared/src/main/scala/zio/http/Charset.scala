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
 * A character set a text body can use: UTF-8, US-ASCII, ISO-8859-1, UTF-16,
 * UTF-16BE or UTF-16LE. `name` is the standard charset name, and `toString`
 * returns it. On the JVM, `toJava` converts a charset to a
 * `java.nio.charset.Charset`; Scala.js has no `toJava`.
 */
sealed abstract class Charset(val name: String) extends CharsetPlatformSpecific {
  override def toString: String = name
}

/** The [[Charset]] values and their name conversions. */
object Charset {

  /** UTF-8 (`UTF-8`). */
  case object UTF8 extends Charset("UTF-8")

  /** US-ASCII (`US-ASCII`). */
  case object ASCII extends Charset("US-ASCII")

  /** ISO-8859-1, also known as Latin-1 (`ISO-8859-1`). */
  case object ISO_8859_1 extends Charset("ISO-8859-1")

  /** UTF-16 (`UTF-16`). */
  case object UTF16 extends Charset("UTF-16")

  /** UTF-16, big-endian (`UTF-16BE`). */
  case object UTF16BE extends Charset("UTF-16BE")

  /** UTF-16, little-endian (`UTF-16LE`). */
  case object UTF16LE extends Charset("UTF-16LE")

  /** The six charsets, in declaration order. */
  val values: Chunk[Charset] = Chunk(UTF8, ASCII, ISO_8859_1, UTF16, UTF16BE, UTF16LE)

  /**
   * Looks up a charset by name, upper-casing `s` first. Accepted names are
   * `UTF-8` and `UTF8`; `US-ASCII` and `ASCII`; `ISO-8859-1`, `LATIN1` and
   * `LATIN-1`; `UTF-16`; `UTF-16BE`; `UTF-16LE`. Any other input, including
   * names with surrounding whitespace, `UTF_8`, `ISO_8859_1`, `windows-1252`
   * and the empty string, gives `None`.
   *
   * The upper-casing uses the JVM's default locale, so under a Turkish or
   * Azerbaijani locale `iso-8859-1` and `us-ascii` give `None`; this is a known
   * bug.
   */
  def fromString(s: String): Option[Charset] = s.toUpperCase match {
    case "UTF-8" | "UTF8"                    => Some(UTF8)
    case "US-ASCII" | "ASCII"                => Some(ASCII)
    case "ISO-8859-1" | "LATIN1" | "LATIN-1" => Some(ISO_8859_1)
    case "UTF-16"                            => Some(UTF16)
    case "UTF-16BE"                          => Some(UTF16BE)
    case "UTF-16LE"                          => Some(UTF16LE)
    case _                                   => None
  }

  /** Returns the `name` of `charset`. */
  def render(charset: Charset): String = charset.name
}
