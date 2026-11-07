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
 * A list of name and value pairs in the `application/x-www-form-urlencoded`
 * format, in insertion order. A name can appear more than once.
 *
 * Build one with `Form.apply` or [[Form.fromString]], and write it with
 * `encode`.
 */
final case class Form(entries: Chunk[(String, String)]) {

  /**
   * Returns the value of the first entry named `key`, or `None` if there is
   * none. The name must match exactly, including letter case.
   */
  def get(key: String): Option[String] = {
    var i = 0
    while (i < entries.length) {
      val entry = entries(i)
      if (entry._1 == key) return Some(entry._2)
      i += 1
    }
    None
  }

  /** Returns the values of every entry named `key`, in order. */
  def getAll(key: String): Chunk[String] = {
    val builder = Chunk.newBuilder[String]
    var i       = 0
    while (i < entries.length) {
      val entry = entries(i)
      if (entry._1 == key) builder += entry._2
      i += 1
    }
    builder.result()
  }

  /** Returns a new form with the pair appended after the existing entries. */
  def add(key: String, value: String): Form =
    Form(entries :+ (key -> value))

  /** Whether the form has no entries. */
  def isEmpty: Boolean = entries.isEmpty

  /** Whether the form has at least one entry. */
  def nonEmpty: Boolean = entries.nonEmpty

  /**
   * Renders the entries as `name=value` pairs joined with `&`; an empty form
   * gives the empty string. A space becomes `+`. A literal `+`, and characters
   * such as `&`, `%`, `#` and non-ASCII ones (as UTF-8 bytes), are
   * percent-encoded; `=` is encoded in names but kept in values. For example,
   * `Form("q" -> "a b&c=d")` renders as `q=a+b%26c=d`.
   */
  def encode: String = {
    if (entries.isEmpty) return ""
    val sb = new StringBuilder
    var i  = 0
    while (i < entries.length) {
      if (i > 0) sb.append('&')
      val entry = entries(i)
      sb.append(Form.encodeKey(entry._1))
      sb.append('=')
      sb.append(Form.encodeValue(entry._2))
      i += 1
    }
    sb.toString
  }
}

/** Constructors and parsing for [[Form]]. */
object Form {

  /** A form with no entries. */
  val empty: Form = Form(Chunk.empty)

  /** Creates a form from the given pairs, keeping their order. */
  def apply(pairs: (String, String)*): Form =
    Form(Chunk.fromArray(pairs.toArray))

  /**
   * Parses an `application/x-www-form-urlencoded` string such as `a=1&b=x+y`.
   *
   * The input is split at `&`. In each part, the text before the first `=` is
   * the name and the rest is the value; a part without `=` becomes a name with
   * an empty value. `+` becomes a space, and percent-encoded sequences are
   * decoded (see [[PercentEncoder.decode]]). The empty string gives
   * [[Form.empty]]. Parts are not trimmed. Empty parts between `&` characters
   * become an empty name with an empty value, but empty parts at the end of the
   * input are dropped: `a=1&&b=2` has three entries, `a=1&` has one.
   */
  def fromString(s: String): Form = {
    if (s.isEmpty) return empty
    val parts   = s.split('&')
    val builder = Chunk.newBuilder[(String, String)]
    var i       = 0
    while (i < parts.length) {
      val part  = parts(i)
      val eqIdx = part.indexOf('=')
      if (eqIdx >= 0) {
        val key   = decodeComponent(part.substring(0, eqIdx))
        val value = decodeComponent(part.substring(eqIdx + 1))
        builder += (key -> value)
      } else {
        builder += (decodeComponent(part) -> "")
      }
      i += 1
    }
    Form(builder.result())
  }

  private def encodeKey(value: String): String =
    PercentEncoder.encode(value, PercentEncoder.ComponentType.QueryKey).replace("+", "%2B").replace("%20", "+")

  private def encodeValue(value: String): String =
    PercentEncoder.encode(value, PercentEncoder.ComponentType.QueryValue).replace("+", "%2B").replace("%20", "+")

  private def decodeComponent(value: String): String =
    PercentEncoder.decode(value.replace('+', ' '))
}
